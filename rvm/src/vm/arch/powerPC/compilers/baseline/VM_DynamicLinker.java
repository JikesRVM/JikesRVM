/*
 * (C) Copyright IBM Corp. 2001
 */
// Compiler-independent dynamic linker.
// Eventually this will be used for all dynamic linking:
//    - unresolved field & method accesses
//    - lazy method compilation
//    - interface invocation
// This may eventually replace:
//    VM_Linker
//    VM_OptLinker
// 17 Sep 1999  Bowen Alpern & Derek Lieber
//
class VM_DynamicLinker implements VM_DynamicBridge, VM_Constants {

  // Resolve and call a non-interface method.
  // Taken:    nothing (calling context is implicit)
  // Returned: does not return (method dispatch table is updated and method is executed)
  //
  static void lazyMethodInvoker() throws VM_ResolutionException {
    VM_Magic.pragmaNoInline();
      
    INSTRUCTION[] instructions = resolveDynamicInvocation();
      
    VM_Magic.dynamicBridgeTo(instructions);           // restore parameters and branch
    if (VM.VerifyAssertions) VM.assert(NOT_REACHED);  // does not return here
  }

  // Discover method to be invoked via dynamic bridge, compile it, and patch its dispatch table.
  //
  // Taken:       nothing (call stack is examined to find invocation site)
  // Returned:    code for method to be invoked
  // Side effect: method is compiled and dispatch table is patched with pointer to generated code
  //
  private static INSTRUCTION[] resolveDynamicInvocation() throws VM_ResolutionException {
    VM_Magic.pragmaNoInline();

    // find call site
    //
    VM.disableGC();  // prevent code from moving while finding it
    int               callingFrame             = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer());
    callingFrame             = VM_Magic.getCallerFramePointer(callingFrame);
    int               callingCompiledMethodId  = VM_Magic.getCompiledMethodID(callingFrame);
    VM_CompiledMethod callingCompiledMethod    = VM_ClassLoader.getCompiledMethod(callingCompiledMethodId);
    VM_CompilerInfo   callingCompilerInfo      = callingCompiledMethod.getCompilerInfo();
    
    callingFrame             = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer());
    callingFrame             = VM_Magic.getCallerFramePointer(callingFrame);
    int               callingInstruction       = VM_Magic.getNextInstructionAddress(callingFrame);
    int               callingInstructionOffset = callingInstruction - VM_Magic.objectAsAddress(callingCompiledMethod.getInstructions());
    VM.enableGC();     
    // obtain symbolic method reference
    //
    VM_DynamicLink dynamicLink = new VM_DynamicLink();
    callingCompilerInfo.getDynamicLink(dynamicLink, callingInstructionOffset);
    VM_Method methodRef = dynamicLink.methodRef();

    // resolve symbolic method reference into actual method
    //
    VM_Method targetMethod = null;
    Object    targetObject = null;
    VM_Class targetClass = null;
    if (dynamicLink.isInvokeSpecial()) {
      targetMethod = VM_Class.findSpecialMethod(methodRef);
    } else if (dynamicLink.isInvokeStatic()) { 
      targetMethod = methodRef;
    } else { // invokevirtual or invokeinterface
      VM.disableGC();  // prevent frame from moving while operating on it
      // reach into register save area and fetch "this" parameter
      callingFrame = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer());
      int location = VM_Magic.getCallerFramePointer(callingFrame)
	- (LAST_NONVOLATILE_FPR - FIRST_VOLATILE_FPR + 1) * 8  // skip fprs
	- (LAST_NONVOLATILE_GPR - FIRST_VOLATILE_GPR + 1) * 4; // skip gprs
         
      targetObject = VM_Magic.addressAsObject(VM_Magic.getMemoryWord(location));
      VM.enableGC();
      targetClass = VM_Magic.getObjectType(targetObject).asClass();
         
      targetMethod = targetClass.findVirtualMethod(methodRef.getName(), methodRef.getDescriptor());
      if (targetMethod == null)
	throw new VM_ResolutionException(targetClass.getDescriptor(), new IncompatibleClassChangeError(targetClass.getDescriptor().classNameFromDescriptor()));
    }
    targetMethod = targetMethod.resolve();

    // compile method
    //
    if (!targetMethod.isCompiled()) {
      targetClass = targetMethod.getDeclaringClass();
      if (!targetClass.isInitialized()) {
	if (!targetClass.isInstantiated()) {
	  VM.sysWrite("Attempted to compile a method of a uninstantiated class!\n");
	  VM.sysWrite("  Class was"); VM.sysWrite(targetClass.getDescriptor()); VM.sysWrite("\n");
	  VM.sysWrite("  Target was"); VM.sysWrite(targetMethod); VM.sysWrite("\n");
	  VM.sysWrite("  Method was"); VM.sysWrite(methodRef); VM.sysWrite("\n");
	}
	targetClass.initialize();
      }

      synchronized(VM_ClassLoader.lock) {
	targetMethod.compile();
      }

      // If targetMethod is a virtual method, then
      // eagerly patch tib of declaring class
      // (we need to do this to get the method test 
      // used by opt to work with lazy compilation).
      if (!(targetMethod.isObjectInitializer() || targetMethod.isStatic())) {
	Object[] declClassTIB = targetClass.getTypeInformationBlock();
	INSTRUCTION[] instructions = targetMethod.getMostRecentlyGeneratedInstructions();
	declClassTIB[targetMethod.getOffset() >>> 2] = instructions;
      }
    }
      
    // patch appropriate dispatching table
    //
    INSTRUCTION[] instructions = targetMethod.getMostRecentlyGeneratedInstructions();

    if (targetMethod.isObjectInitializer() || targetMethod.isStatic()) { 
      targetMethod.getDeclaringClass().resetStaticMethod(targetMethod, instructions);
    } else if (dynamicLink.isInvokeSpecial()) { 
      targetMethod.getDeclaringClass().resetTIBEntry(targetMethod, instructions);
    } else {
      VM_Class recvClass = (VM_Class)VM_Magic.getObjectType(targetObject);
      recvClass.resetTIBEntry(targetMethod, instructions);
    }

    return instructions;
  }
}
