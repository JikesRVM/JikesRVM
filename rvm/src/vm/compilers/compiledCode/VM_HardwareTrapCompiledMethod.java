/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.VM_Type;
import com.ibm.JikesRVM.classloader.VM_Method;

/**
 * Information associated with artifical stackframe inserted by hardware 
 * trap handler.
 *
 * @author Derek Lieber
 * @date 02 Jun 1999 
 */
final class VM_HardwareTrapCompiledMethod extends VM_CompiledMethod {

  public VM_HardwareTrapCompiledMethod(int id, VM_Method m) {
    super(id,m);    
  }

  public final int getCompilerType() throws VM_PragmaUninterruptible { 
    return TRAP; 
  }

  public final String getCompilerName() {
    return "<hardware trap>";
  }

  public final VM_ExceptionDeliverer getExceptionDeliverer() throws VM_PragmaUninterruptible {
    // this method should never get called, because exception delivery begins
    // at site of exception, which is one frame above artificial "trap" frame
    // corresponding to this compiler-info object
    //
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return null;
  }
      
  public final VM_Offset findCatchBlockForInstruction(VM_Offset instructionOffset, VM_Type exceptionType) {
    return VM_Offset.fromInt(-1);
  }
   
  public final void getDynamicLink(VM_DynamicLink dynamicLink, VM_Offset instructionOffset) throws VM_PragmaUninterruptible {
    // this method should never get called, because exception delivery begins
    // at site of exception, which is one frame above artificial "trap" frame
    // corresponding to this compiler-info object
    //
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  public final void printStackTrace(VM_Offset instructionOffset, java.io.PrintStream out) {
    out.println("\tat <hardware trap>");
  }

  public final void printStackTrace(VM_Offset instructionOffset, java.io.PrintWriter out) {
    out.println("\tat <hardware trap>");
  }

  /**
   * Set the stack browser to the innermost logical stack frame of this method
   */
  public final void set(VM_StackBrowser browser, VM_Offset instr) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }
       
  /**
   * Advance the VM_StackBrowser up one internal stack frame, if possible
   */
  public final boolean up(VM_StackBrowser browser) {
    return false;
  }

}
