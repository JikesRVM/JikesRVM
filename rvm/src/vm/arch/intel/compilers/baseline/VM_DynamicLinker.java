/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * 
 * @author Bowen Alpern
 * @author Maria Butrico
 * @author Anthony Cocchi
 */
class VM_DynamicLinker implements VM_DynamicBridge, VM_Constants {

  // Resolve and call a non-interface method.
  // Taken:    nothing (calling context is implicit)
  // Returned: does not return (method dispatch table is updated and method is
  //           executed)
  //      
  static void lazyMethodInvoker() throws VM_ResolutionException {
    VM_Magic.pragmaNoInline();

    INSTRUCTION[] instructions = resolveDynamicInvocation();

    // restore parameters and branch
    VM_Magic.dynamicBridgeTo(instructions);

    if (VM.VerifyAssertions) VM.assert(NOT_REACHED);
  }
  

  // Discover method to be invoked via dynamic bridge, compile it,
  // and patch its dispatch table.
  //
  // Taken:       nothing (call stack is examined to find
  //                       invocation site)
  // Returned:    code for method to be invoked
  // Side effect: method is compiled and dispatch table is
  //              patched with pointer to code
  //
  private static INSTRUCTION[] resolveDynamicInvocation()
	  throws VM_ResolutionException { 

    VM_Magic.pragmaNoInline();

    /* find call site
     */   
    // caller frame, ie, lazyMethodInvoker's frame
    int callingFrame;
    callingFrame = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer()); 
    // info for caller of caller, ie, the caller of the lazyMethodInvoker
    int returnAddress = VM_Magic.getReturnAddress(callingFrame);
    callingFrame = VM_Magic.getCallerFramePointer(callingFrame);

    // method id of the caller of the lazily compiled method
    int callingCompiledMethodId = VM_Magic.getCompiledMethodID(callingFrame);
    VM_CompiledMethod callingCompiledMethod;
    callingCompiledMethod = VM_ClassLoader.getCompiledMethod(callingCompiledMethodId);
    VM_CompilerInfo callingCompilerInfo = callingCompiledMethod.getCompilerInfo();    
    int callingInstructionOffset = returnAddress - VM_Magic.objectAsAddress(callingCompiledMethod.getInstructions()); 


    /* obtain symbolic method reference
     */
    VM_DynamicLink dynamicLink = new VM_DynamicLink();
    callingCompilerInfo.getDynamicLink(dynamicLink, callingInstructionOffset);
    VM_Method methodRef = dynamicLink.methodRef();

    // resolve symbolic method reference into actual method
    //
    VM_Method	targetMethod	= null;
    Object	targetObject	= null;
    VM_Class	targetClass	= null;
    if (dynamicLink.isInvokeSpecial()) {
      targetMethod = VM_Class.findSpecialMethod(methodRef);
    } else if (dynamicLink.isInvokeStatic()) {
      targetMethod = methodRef;
    } else { // invokevirtual or invokeinterface

      /* reach into register save area and fetch "this" parameter
       */
      // frame of the caller, ie., lazyMethodInvoker
      callingFrame = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer());
      int location;
      if (0 < NUM_PARAMETER_GPRS)
        location = VM_Magic.getMemoryWord(callingFrame + 
					  VM_BaselineConstants.STACKFRAME_FIRST_PARAMETER_OFFSET);
      else
        {
	if (VM.VerifyAssertions) VM.assert(NOT_REACHED);
        location = 0;
        //location = VM_Magic.getMemoryWord(callingFrame + 
		// (methodRef.getParamaterWords() + 1) * 4 +
		// 4; // size of return @
        }
 
      targetObject = VM_Magic.addressAsObject(location);
      targetClass = VM_Magic.getObjectType(targetObject).asClass();
 
      targetMethod = targetClass.findVirtualMethod(methodRef.getName(),
						   methodRef.getDescriptor());
      if (targetMethod == null)
        throw new VM_ResolutionException(targetClass.getDescriptor(),
		 new IncompatibleClassChangeError(targetClass.getDescriptor().classNameFromDescriptor()));
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
