/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

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
  static void lazyMethodInvoker() {
    VM_DynamicLink dl = DL_Helper.resolveDynamicInvocation();
    VM_Method targMethod = DL_Helper.resolveMethodRef(dl);
    DL_Helper.compileMethod(dl, targMethod);
    VM_CodeArray code = targMethod.getCurrentInstructions();
    VM_Magic.dynamicBridgeTo(code);                   // restore parameters and invoke
    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);  // does not return here
  }


  /**
   * Report unimplemented native method error.
   *  Taken:    nothing (calling context is implicit)
   *  Returned: does not return (throws UnsatisfiedLinkError)
   */
  static void unimplementedNativeMethod() {
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
    static VM_DynamicLink resolveDynamicInvocation() throws NoInlinePragma {

      // find call site 
      //
      VM.disableGC();
      Address callingFrame = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer());
      Address returnAddress = VM_Magic.getReturnAddress(callingFrame);
      callingFrame = VM_Magic.getCallerFramePointer(callingFrame);
      int callingCompiledMethodId  = VM_Magic.getCompiledMethodID(callingFrame);
      VM_CompiledMethod callingCompiledMethod = VM_CompiledMethods.getCompiledMethod(callingCompiledMethodId);
      Offset callingInstructionOffset = callingCompiledMethod.getInstructionOffset(returnAddress);
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
    static VM_Method resolveMethodRef(VM_DynamicLink dynamicLink) 
      throws NoInlinePragma {
      // resolve symbolic method reference into actual method
      //
      VM_MethodReference methodRef = dynamicLink.methodRef();
      if (dynamicLink.isInvokeSpecial()) {
        return methodRef.resolveInvokeSpecial();
      } else if (dynamicLink.isInvokeStatic()) {
        return methodRef.resolve();
      } else {
        // invokevirtual or invokeinterface
        VM.disableGC();
        Object targetObject = VM_DynamicLinkerHelper.getReceiverObject();
        VM.enableGC();
        VM_Class targetClass = VM_Magic.getObjectType(targetObject).asClass();
        VM_Method targetMethod = targetClass.findVirtualMethod(methodRef.getName(), methodRef.getDescriptor());
        if (targetMethod == null) {
          throw new IncompatibleClassChangeError(targetClass.getDescriptor().classNameFromDescriptor());
        }
        return targetMethod;
      }
    }


    /**
     * Compile (if necessary) targetMethod and patch the appropriate disaptch tables
     * @param targetMethod the VM_Method to compile (if not already compiled)
     */
    static void compileMethod(VM_DynamicLink dynamicLink, VM_Method targetMethod) throws NoInlinePragma {

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
