/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Implement lazy compilation.
 *
 * @author Bowen Alpern 
 * @author Dave Grove
 * @author Derek Lieber
 * @date 17 Sep 1999  
 */
class VM_DynamicLinker implements VM_DynamicBridge, VM_Constants {

  /**
   * Resolve and call a non-interface method.
   *  Taken:    nothing (calling context is implicit)
   *  Returned: does not return (method dispatch table is updated and method is executed)
   */
  static void lazyMethodInvoker() throws VM_ResolutionException {
    VM_Method targMethod = DL_Helper.resolveDynamicInvocation();

    // We want to make sure that GC does not happen between getting code and
    // invoking it. If it's not on a stack during GC, it can be marked
    // obsolete and reclaimed.
    VM.disableGC();
    INSTRUCTION[] instructions =
			targMethod.getMostRecentlyGeneratedInstructions();
    VM.enableGC();

    VM_Magic.dynamicBridgeTo(instructions);           // restore parameters and invoke
    if (VM.VerifyAssertions) VM.assert(NOT_REACHED);  // does not return here
  }

  /**
   * Helper class that does the real work of resolving and
   * compiling a lazy method invocation.  In separate class so
   * that it doesn't implement VM_DynamicBridge magic.
   */
  private static class DL_Helper {

    /**
     * Discover method to be invoked via dynamic bridge, compile it, and patch its dispatch table.
     * 
     * Taken:       nothing (call stack is examined to find invocation site)
     * Returned:    code for method to be invoked
     * Side effect: method is compiled and dispatch table is patched with pointer to generated code
     */
    static VM_Method resolveDynamicInvocation() throws VM_ResolutionException {
      VM_Magic.pragmaNoInline();

      // find call site 
      //
      VM.disableGC();
      int callingFrame = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer());
      int returnAddress = VM_Magic.getReturnAddress(callingFrame);
      callingFrame = VM_Magic.getCallerFramePointer(callingFrame);
      int callingCompiledMethodId  = VM_Magic.getCompiledMethodID(callingFrame);
      VM_CompiledMethod callingCompiledMethod = VM_CompiledMethods.getCompiledMethod(callingCompiledMethodId);
      VM_CompilerInfo callingCompilerInfo = callingCompiledMethod.getCompilerInfo();
      int callingInstructionOffset = returnAddress - VM_Magic.objectAsAddress(callingCompiledMethod.getInstructions());
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
      VM_Class  targetClass = null;
      if (dynamicLink.isInvokeSpecial()) {
	targetMethod = VM_Class.findSpecialMethod(methodRef);
      } else if (dynamicLink.isInvokeStatic()) { 
	targetMethod = methodRef;
      } else { // invokevirtual or invokeinterface
	VM.disableGC();
	targetObject = VM_DynamicLinkerHelper.getReceiverObject();
	VM.enableGC();
	targetClass = VM_Magic.getObjectType(targetObject).asClass();
	targetMethod = targetClass.findVirtualMethod(methodRef.getName(), methodRef.getDescriptor());
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

      if (targetMethod.isObjectInitializer() || targetMethod.isStatic()) { 
	targetMethod.getDeclaringClass().resetStaticMethod(targetMethod);
      } else if (dynamicLink.isInvokeSpecial()) { 
	targetMethod.getDeclaringClass().resetTIBEntry(targetMethod);
      } else {
	VM_Class recvClass = (VM_Class)VM_Magic.getObjectType(targetObject);
	recvClass.resetTIBEntry(targetMethod);
      }

      if (VM_BaselineCompiler.options.hasMETHOD_TO_BREAK() &&
	  VM_BaselineCompiler.options.fuzzyMatchMETHOD_TO_BREAK(targetMethod.toString())) {
	VM_Services.breakStub();  // invoke stub used for breaking in jdp
      }
     
      return targetMethod;
    }
  }

}
