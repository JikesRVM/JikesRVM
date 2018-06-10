/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.runtime;

import static org.jikesrvm.VM.NOT_REACHED;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.MethodReference;
import org.jikesrvm.compilers.common.CodeArray;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.vmmagic.pragma.DynamicBridge;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * Implement lazy compilation.
 */
@DynamicBridge
public class DynamicLinker {

  /**
   * Resolve, compile if necessary, and invoke a method.
   * <pre>
   *  Taken:    nothing (calling context is implicit)
   *  Returned: does not return (method dispatch table is updated and method is executed)
   * </pre>
   */
  @Entrypoint
  static void lazyMethodInvoker() {
    DynamicLink dl = DL_Helper.resolveDynamicInvocation();
    RVMMethod targMethod = DL_Helper.resolveMethodRef(dl);
    DL_Helper.compileMethod(dl, targMethod);
    CodeArray code = targMethod.getCurrentEntryCodeArray();
    Magic.dynamicBridgeTo(code);                   // restore parameters and invoke
    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);  // does not return here
  }

  /**
   * Report unimplemented native method error.
   * <pre>
   *  Taken:    nothing (calling context is implicit)
   *  Returned: does not return (throws UnsatisfiedLinkError)
   * </pre>
   */
  @Entrypoint
  static void unimplementedNativeMethod() {
    DynamicLink dl = DL_Helper.resolveDynamicInvocation();
    RVMMethod targMethod = DL_Helper.resolveMethodRef(dl);
    if (!VM.fullyBooted) {
      VM.sysWriteln("Unimplemented native method: " + targMethod);
    }
    throw new UnsatisfiedLinkError(targMethod.toString());
  }

  /**
   * Report a magic SysCall has been mistakenly invoked
   */
  @Entrypoint
  static void sysCallMethod() {
    DynamicLink dl = DL_Helper.resolveDynamicInvocation();
    RVMMethod targMethod = DL_Helper.resolveMethodRef(dl);
    throw new UnsatisfiedLinkError(targMethod.toString() + " which is a SysCall");
  }

  /**
   * Helper class that does the real work of resolving method references
   * and compiling a lazy method invocation.  In separate class so
   * that it doesn't implement DynamicBridge magic.
   */
  private static class DL_Helper {

    /**
     * Discovers a method reference to be invoked via dynamic bridge. The call
     * stack is examined to find the invocation site.
     *
     * @return an DynamicLink describing the call site
     */
    @NoInline
    static DynamicLink resolveDynamicInvocation() {

      // find call site
      //
      VM.disableGC();
      Address callingFrame = Magic.getCallerFramePointer(Magic.getFramePointer());
      Address returnAddress = Magic.getReturnAddressUnchecked(callingFrame);
      callingFrame = Magic.getCallerFramePointer(callingFrame);
      int callingCompiledMethodId = Magic.getCompiledMethodID(callingFrame);
      CompiledMethod callingCompiledMethod = CompiledMethods.getCompiledMethod(callingCompiledMethodId);
      Offset callingInstructionOffset = callingCompiledMethod.getInstructionOffset(returnAddress);
      VM.enableGC();

      // obtain symbolic method reference
      //
      DynamicLink dynamicLink = new DynamicLink();
      callingCompiledMethod.getDynamicLink(dynamicLink, callingInstructionOffset);

      return dynamicLink;
    }

    /**
     * Resolves a method ref into appropriate RVMMethod.
     *
     * @param dynamicLink a DynamicLink that describes call site
     * @return the RVMMethod that should be invoked.
     */
    @NoInline
    static RVMMethod resolveMethodRef(DynamicLink dynamicLink) {
      // resolve symbolic method reference into actual method
      //
      MethodReference methodRef = dynamicLink.methodRef();
      if (dynamicLink.isInvokeSpecial()) {
        return methodRef.resolveInvokeSpecial();
      } else if (dynamicLink.isInvokeStatic()) {
        return methodRef.resolve();
      } else {
        // invokevirtual or invokeinterface
        VM.disableGC();
        Object targetObject;
        if (VM.BuildForIA32) {
          targetObject = org.jikesrvm.ia32.DynamicLinkerHelper.getReceiverObject();
        } else {
          if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
          targetObject = org.jikesrvm.ppc.DynamicLinkerHelper.getReceiverObject();
        }
        VM.enableGC();
        RVMClass targetClass = Magic.getObjectType(targetObject).asClass();
        RVMMethod targetMethod = targetClass.findVirtualMethod(methodRef.getName(), methodRef.getDescriptor());
        if (targetMethod == null) {
          throw new IncompatibleClassChangeError(targetClass.getDescriptor().classNameFromDescriptor());
        }
        return targetMethod;
      }
    }

    /**
     * Compile (if necessary) targetMethod and patch the appropriate dispatch tables.
     *
     * @param dynamicLink a DynamicLink that describes call site.
     * @param targetMethod the RVMMethod to compile (if not already compiled)
     */
    @NoInline
    static void compileMethod(DynamicLink dynamicLink, RVMMethod targetMethod) {

      RVMClass targetClass = targetMethod.getDeclaringClass();

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
        Object targetObject;
        if (VM.BuildForIA32) {
          targetObject = org.jikesrvm.ia32.DynamicLinkerHelper.getReceiverObject();
        } else {
          if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
          targetObject = org.jikesrvm.ppc.DynamicLinkerHelper.getReceiverObject();
        }
        VM.enableGC();
        RVMClass recvClass = (RVMClass) Magic.getObjectType(targetObject);
        recvClass.updateTIBEntry(targetMethod);
      }
    }
  }
}
