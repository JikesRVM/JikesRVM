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
   * Resolve, compile if necessary, and invoke a method.
   *  Taken:    nothing (calling context is implicit)
   *  Returned: does not return (method dispatch table is updated and method is executed)
   */
  static void lazyMethodInvoker() throws VM_ResolutionException {
    VM_DynamicLink dl = DL_Helper.resolveDynamicInvocation();
    VM_Method targMethod = DL_Helper.resolveMethodRef(dl);
    DL_Helper.compileMethod(dl, targMethod);

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
   * Report unimplemented native method error.
   *  Taken:    nothing (calling context is implicit)
   *  Returned: does not return (throws UnsatisfiedLinkError)
   */
  static void unimplementedNativeMethod() throws VM_ResolutionException {
    VM_DynamicLink dl = DL_Helper.resolveDynamicInvocation();
    VM_Method targMethod = DL_Helper.resolveMethodRef(dl);
    throw new UnsatisfiedLinkError(targMethod.toString());
  }


  /**
   * Helper class that does the real work of resolving method references
   * and compiling a lazy method invocation.  In separate class so
   * that it doesn't implement VM_DynamicBridge magic.
   */
  private static class DL_Helper {
    
    /**
     * Discover method reference to be invoked via dynamic bridge.
     * 
     * Taken:       nothing (call stack is examined to find invocation site)
     * Returned:    VM_DynamicLink that describes call site.
     */
    static VM_DynamicLink resolveDynamicInvocation() throws VM_ResolutionException {
      VM_Magic.pragmaNoInline(); // required since we are doing stackframe inspection

      // find call site 
      //
      VM.disableGC();
      VM_Address callingFrame = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer());
      VM_Address returnAddress = VM_Magic.getReturnAddress(callingFrame);
      callingFrame = VM_Magic.getCallerFramePointer(callingFrame);
      int callingCompiledMethodId  = VM_Magic.getCompiledMethodID(callingFrame);
      VM_CompiledMethod callingCompiledMethod = VM_CompiledMethods.getCompiledMethod(callingCompiledMethodId);
      VM_CompilerInfo callingCompilerInfo = callingCompiledMethod.getCompilerInfo();
      int callingInstructionOffset = returnAddress.diff(VM_Magic.objectAsAddress(callingCompiledMethod.getInstructions()));
      VM.enableGC();     

      // obtain symbolic method reference
      //
      VM_DynamicLink dynamicLink = new VM_DynamicLink();
      callingCompilerInfo.getDynamicLink(dynamicLink, callingInstructionOffset);
      return dynamicLink;
    }

    /**
     * Resolve method ref into appropriate VM_Method 
     * 
     * Taken:       VM_DynamicLink that describes call site.
     * Returned:    VM_Method that should be invoked.
     */
    static VM_Method resolveMethodRef(VM_DynamicLink dynamicLink) throws VM_ResolutionException {
      VM_Magic.pragmaNoInline(); // required since we are doing stackframe inspection

      // resolve symbolic method reference into actual method
      //
      VM_Method methodRef = dynamicLink.methodRef();
      VM_Method targetMethod = null;
      if (dynamicLink.isInvokeSpecial()) {
	targetMethod = VM_Class.findSpecialMethod(methodRef);
      } else if (dynamicLink.isInvokeStatic()) {
	targetMethod = methodRef;
      } else { // invokevirtual or invokeinterface
	VM.disableGC();
	Object targetObject = VM_DynamicLinkerHelper.getReceiverObject();
	VM.enableGC();
	VM_Class targetClass = VM_Magic.getObjectType(targetObject).asClass();
	targetMethod = targetClass.findVirtualMethod(methodRef.getName(), methodRef.getDescriptor());
	if (targetMethod == null)
	  throw new VM_ResolutionException(targetClass.getDescriptor(), 
					   new IncompatibleClassChangeError(targetClass.getDescriptor().classNameFromDescriptor()));
      }
      targetMethod = targetMethod.resolve();

      return targetMethod;
    }


    /**
     * Compile (if necessary) targetMethod and patch the appropriate disaptch tables
     * @param targetMethod the VM_Method to compile (if not already compiled)
     */
    static void compileMethod(VM_DynamicLink dynamicLink, VM_Method targetMethod) throws VM_ResolutionException {
      VM_Magic.pragmaNoInline(); // required since we are doing stackframe inspection

      VM_Class targetClass = targetMethod.getDeclaringClass();

      // if necessary, compile method
      //
      if (!targetMethod.isCompiled()) {
	if (!targetClass.isInitialized()) {
	  if (!targetClass.isInstantiated()) {
	    VM.sysWrite("Attempted to compile a method of a uninstantiated class!\n");
	    VM.sysWrite("  Class was"); VM.sysWrite(targetClass.getDescriptor()); VM.sysWrite("\n");
	    VM.sysWrite("  Target was"); VM.sysWrite(targetMethod); VM.sysWrite("\n");
	    VM.sysWrite("  Method was"); VM.sysWrite(dynamicLink.methodRef()); VM.sysWrite("\n");
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
	  targetClass.resetTIBEntry(targetMethod);
	}
      }
      
      // patch appropriate dispatch table
      //
      if (targetMethod.isObjectInitializer() || targetMethod.isStatic()) { 
	targetClass.resetStaticMethod(targetMethod);
      } else if (dynamicLink.isInvokeSpecial()) { 
	targetClass.resetTIBEntry(targetMethod);
      } else {
	VM.disableGC();
	Object targetObject = VM_DynamicLinkerHelper.getReceiverObject();
	VM.enableGC();
	VM_Class recvClass = (VM_Class)VM_Magic.getObjectType(targetObject);
	recvClass.resetTIBEntry(targetMethod);
      }

      // check to see if we need to breakpoint for jdp
      if (VM_BaselineCompiler.options.hasMETHOD_TO_BREAK() &&
	  VM_BaselineCompiler.options.fuzzyMatchMETHOD_TO_BREAK(targetMethod.toString())) {
	VM_Services.breakStub();  // invoke stub used for breaking in jdp
      }
    }
  }
}
