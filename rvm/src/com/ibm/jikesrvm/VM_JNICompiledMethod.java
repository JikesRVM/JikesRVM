/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001, 2003
 */
package com.ibm.jikesrvm;

import com.ibm.jikesrvm.classloader.*;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.Offset;

/**
 * Information associated with artifical stackframe inserted at the
 * transition from Jave to JNI Native C.  
 *
 * Exception delivery should never see Native C frames, or the Java to C 
 * transition frame.  Native C code is redispatched during exception
 * handling to either process/handle and clear the exception or to return
 * to Java leaving the exception pending.  If it returns to the transition
 * frame with a pending exception. JNI causes an athrow to happen as if it
 * was called at the call site of the call to the native method.
 *
 * @author Ton Ngo
 */
public final class VM_JNICompiledMethod extends VM_CompiledMethod {

  public VM_JNICompiledMethod(int id, VM_Method m) {
    super(id,m);    
  }

  @Uninterruptible
  public final int getCompilerType() { 
    return JNI; 
  }

  public final String getCompilerName() {
    return "JNI compiler";
  }

  @Uninterruptible
  public final VM_ExceptionDeliverer getExceptionDeliverer() { 
    // this method should never get called.
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return null;
  }
      
  @Uninterruptible
  public final void getDynamicLink(VM_DynamicLink dynamicLink, Offset instructionOffset) { 
    // this method should never get called.
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  public final int findCatchBlockForInstruction(Offset instructionOffset, VM_Type exceptionType) {
    return -1;
  }
   
  public final void printStackTrace(Offset instructionOffset, com.ibm.jikesrvm.PrintLN out) {
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

  public final void set(VM_StackBrowser browser, Offset instr) {
    browser.setBytecodeIndex(-1);
    browser.setCompiledMethod(this);
    browser.setMethod(method);
  }
}
