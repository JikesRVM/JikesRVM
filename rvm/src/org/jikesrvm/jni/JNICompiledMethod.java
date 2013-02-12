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
package org.jikesrvm.jni;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.runtime.DynamicLink;
import org.jikesrvm.runtime.ExceptionDeliverer;
import org.jikesrvm.runtime.StackBrowser;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Unpreemptible;
import org.vmmagic.unboxed.Offset;

/**
 * Information associated with artifical stackframe inserted at the
 * transition from Jave to JNI Native C.
 * <p>
 * Exception delivery should never see Native C frames, or the Java to C
 * transition frame.  Native C code is redispatched during exception
 * handling to either process/handle and clear the exception or to return
 * to Java leaving the exception pending.  If it returns to the transition
 * frame with a pending exception. JNI causes an athrow to happen as if it
 * was called at the call site of the call to the native method.
 */
public final class JNICompiledMethod extends CompiledMethod {

  /** Architecture specific deliverer of exceptions */
  private static final ExceptionDeliverer deliverer;

  static {
    if (VM.BuildForIA32) {
      try {
        deliverer =
         (ExceptionDeliverer)Class.forName("org.jikesrvm.jni.ia32.JNIExceptionDeliverer").newInstance();
      } catch (Exception e) {
        throw new Error(e);
      }
    } else {
      deliverer = null;
    }
  }

  public JNICompiledMethod(int id, RVMMethod m) {
    super(id, m);
  }

  @Override
  @Uninterruptible
  public int getCompilerType() {
    return JNI;
  }

  @Override
  public String getCompilerName() {
    return "JNI compiler";
  }

  @Override
  @Uninterruptible
  public ExceptionDeliverer getExceptionDeliverer() {
    // this method should never get called on PPC
    if (VM.VerifyAssertions) VM._assert(VM.BuildForIA32);
    return deliverer;
  }

  @Override
  @Uninterruptible
  public void getDynamicLink(DynamicLink dynamicLink, Offset instructionOffset) {
    // this method should never get called.
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  @Override
  public boolean isWithinUninterruptibleCode(Offset instructionOffset) {
    return false;
  }

  @Override
  @Unpreemptible
  public int findCatchBlockForInstruction(Offset instructionOffset, RVMType exceptionType) {
    return -1;
  }

  @Override
  public void printStackTrace(Offset instructionOffset, org.jikesrvm.PrintLN out) {
    if (method != null) {
      // print name of native method
      out.print("\tat ");
      out.print(method.getDeclaringClass());
      out.print(".");
      out.print(method.getName());
      out.println(" (native method)");
    } else {
      out.println("\tat <native method>");
    }
  }

  @Override
  public void set(StackBrowser browser, Offset instr) {
    browser.setBytecodeIndex(-1);
    browser.setCompiledMethod(this);
    browser.setMethod(method);
  }
}
