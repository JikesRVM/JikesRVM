/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

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

  public final VM_ExceptionDeliverer getExceptionDeliverer() throws VM_PragmaUninterruptible {
    // this method should never get called, because exception delivery begins
    // at site of exception, which is one frame above artificial "trap" frame
    // corresponding to this compiler-info object
    //
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);
    return null;
  }
      
  public final int findCatchBlockForInstruction(int instructionOffset, VM_Type exceptionType) {
    return -1;
  }
   
  public final void getDynamicLink(VM_DynamicLink dynamicLink, int instructionOffset) throws VM_PragmaUninterruptible {
    // this method should never get called, because exception delivery begins
    // at site of exception, which is one frame above artificial "trap" frame
    // corresponding to this compiler-info object
    //
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);
  }

  public final void printStackTrace(int instructionOffset, java.io.PrintStream out) {
    out.println("\tat <hardware trap>");
  }

  public final void printStackTrace(int instructionOffset, java.io.PrintWriter out) {
    out.println("\tat <hardware trap>");
  }

  /**
   * Set the stack browser to the innermost logical stack frame of this method
   */
  public final void set(VM_StackBrowser browser, int instr) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);
  }
       
  /**
   * Advance the VM_StackBrowser up one internal stack frame, if possible
   */
  public final boolean up(VM_StackBrowser browser) {
    return false;
  }

}
