/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Implement lazy compilation.
 *
 * @author Bowen Alpern 
 * @author Dave Grove
 * @author Derek Lieber
 * @date 17 Sep 1999  
 */
public class VM_DynamicLinker implements VM_DynamicBridge, VM_Constants {

  /**
   * Resolve, compile if necessary, and invoke a method.
   *  Taken:    nothing (calling context is implicit)
   *  Returned: does not return (method dispatch table is updated and method is executed)
   */
  static void lazyMethodInvoker() throws VM_ResolutionException {
    VM_DynamicLink dl = DL_Helper.resolveDynamicInvocation();
    VM_Method targMethod = DL_Helper.resolveMethodRef(dl);
    DL_Helper.compileMethod(dl, targMethod);
    INSTRUCTION[] code = targMethod.getCurrentInstructions();
    VM_Magic.dynamicBridgeTo(code);                   // restore parameters and invoke
    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);  // does not return here
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
    static VM_DynamicLink resolveDynamicInvocation() throws VM_ResolutionException, VM_PragmaNoInline {

      // find call site 
      //
      VM.disableGC();
      VM_Address callingFrame = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer());
      VM_Address returnAddress = VM_Magic.getReturnAddress(callingFrame);
      callingFrame = VM_Magic.getCallerFramePointer(callingFrame);
      int callingCompiledMethodId  = VM_Magic.getCompiledMethodID(callingFrame);
      VM_CompiledMethod callingCompiledMethod = VM_CompiledMethods.getCompiledMethod(callingCompiledMethodId);
      int callingInstructionOffset = returnAddress.diff(VM_Magic.objectAsAddress(callingCompiledMethod.getInstructions())).toInt();
      VM.enableGC();     

      // obtain symbolic method reference
      //
      VM_DynamicLink dynamicLink = new VM_DynamicLink();
      callingCompiledMethod.getDynamicLink(dynamicLink, callingInstructionOffset);

      return dynamicLink;
    }

    /**
     * Resolve method ref into appropriate VM_Method 
     * 
     * Taken:       VM_DynamicLink that describes call site.
     * Returned:    VM_Method that should be invoked.
     */
    static VM_Method resolveMethodRef(VM_DynamicLink dynamicLink) throws VM_ResolutionException, VM_PragmaNoInline {

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
	if (targetMethod == null) {
	  VM.sysWrite("Could not find method ");
	  VM.sysWrite(VM_Magic.objectAsAddress(methodRef.getName()));  	     VM.sysWrite("  ");
	  VM.sysWrite(methodRef.getName());	                             VM.sysWrite("  ");
	  VM.sysWrite(VM_Magic.objectAsAddress(methodRef.getDescriptor()));  VM.sysWrite("  ");
	  VM.sysWrite(methodRef.getDescriptor());                            VM.sysWrite("  ");
	  VM.sysWrite(" in class ");
	  VM.sysWrite(targetClass.getName());
	  VM.sysWrite(" of object at ");
	  VM.sysWrite(VM_Magic.objectAsAddress(targetObject));
	  VM.sysWriteln();
	  VM_Method methods[] = targetClass.getVirtualMethods();
	  VM.sysWriteln("targetClass = ", VM_Magic.objectAsAddress(targetClass));
	  VM.sysWriteln("methods = ", VM_Magic.objectAsAddress(methods));
	  for (int i = 0, n = methods.length; i < n; ++i) {
	    VM_Method method = methods[i];
	    boolean same = (method.getName() == methodRef.getName() && 
			    method.getDescriptor() == methodRef.getDescriptor());
	    VM.sysWrite(same ? "HIT  " : "MISS ");
	    VM.sysWrite("Method ", i); VM.sysWrite(":   "); 
	    VM.sysWrite(VM_Magic.objectAsAddress(method.getName()));  	    VM.sysWrite("   ");  
	    VM.sysWrite(method.getName());  	    VM.sysWrite("   ");  
	    VM.sysWrite(VM_Magic.objectAsAddress(method.getDescriptor()));  	    VM.sysWrite("   ");  
	    VM.sysWrite(method.getDescriptor());  	    VM.sysWrite("   ");  
	    VM.sysWriteln();
	  }
	  VM_Type types[] = VM_TypeDictionary.getValuesPointer();
	  for (int i=0; i<types.length; i++) {
	    VM_Type t = types[i];
	    if (t == null) continue;
	    VM.sysWrite("Type at ", VM_Magic.objectAsAddress(t));
	    VM.sysWrite(t.getName());
	    VM.sysWriteln();
	  }
	  throw new VM_ResolutionException(targetClass.getDescriptor(), 
					   new IncompatibleClassChangeError(targetClass.getDescriptor().classNameFromDescriptor()));
	}
      }
      targetMethod = targetMethod.resolve();

      return targetMethod;
    }


    /**
     * Compile (if necessary) targetMethod and patch the appropriate disaptch tables
     * @param targetMethod the VM_Method to compile (if not already compiled)
     */
    static void compileMethod(VM_DynamicLink dynamicLink, VM_Method targetMethod) throws VM_ResolutionException, VM_PragmaNoInline {

      VM_Class targetClass = targetMethod.getDeclaringClass();

      // if necessary, compile method
      //
      if (!targetMethod.isCompiled()) {
	targetMethod.compile();

	// If targetMethod is a virtual method, then eagerly patch tib of declaring class.
	// (we need to do this to get the method test used by opt to work with lazy compilation).
	if (!(targetMethod.isObjectInitializer() || targetMethod.isStatic())) {
	  targetClass.updateTIBEntry(targetMethod);
	}
      }
      
      // patch appropriate dispatch table
      //
      if (targetMethod.isObjectInitializer() || targetMethod.isStatic()) { 
	targetClass.updateJTOCEntry(targetMethod);
      } else if (dynamicLink.isInvokeSpecial()) { 
	targetClass.updateTIBEntry(targetMethod);
      } else {
	VM.disableGC();
	Object targetObject = VM_DynamicLinkerHelper.getReceiverObject();
	VM.enableGC();
	VM_Class recvClass = (VM_Class)VM_Magic.getObjectType(targetObject);
	recvClass.updateTIBEntry(targetMethod);
      }
    }
  }
}
