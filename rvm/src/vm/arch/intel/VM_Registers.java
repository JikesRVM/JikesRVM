/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * The machine state comprising a thread's execution context.
 *
 * @author Bowen Alpern
 * @author David Grove
 */
class VM_Registers implements VM_Constants, VM_Uninterruptible {

  // The following are used both for thread context switching
  // and for software/hardware exception reporting/delivery.
  //
  int    gprs[]; // general purpose registers
  double fprs[]; // floating point registers
  int    ip;     // instruction address register
  int    fp;     // frame pointer
  
  // set by C hardware exception handler and VM_Runtime.athrow 
  // and reset by each implementation of VM_ExceptionDeliverer.deliverException
  //
  boolean inuse; // do exception registers currently contain live values?
  
  VM_Registers() {
    gprs = new int[NUM_GPRS];
    fprs = new double[NUM_FPRS];
  }
  
  /**
   * Return framepointer for the deepest stackframe
   */
  final int getInnermostFramePointer() {
    return fp;
  }
  
  /**
   * Return next instruction address for the deepest stackframe
   */
  final int getInnermostInstructionAddress() {
    return ip;
  }

  /**
   * update the machine state as if the stackframe were unwound.
   */
  final void unwindStackFrame() {
    ip = VM_Magic.getReturnAddress(fp);
    fp = VM_Magic.getCallerFramePointer(fp);
    gprs[VM_BaselineConstants.FP] = fp;
  }

  /**
   * set ip & fp. used to control the stack frame at which a scan of
   * the stack during GC will start, for ex., the top java frame for
   * a thread that is blocked in native code during GC.
   */
  final void setInnermost(int newip, int newfp) {
    ip = newip;
    fp = newfp;
    // fix VM_Thread.adjustRegisters when line below is deleted!
    gprs[VM_BaselineConstants.FP] = newfp;
  }

  /**
   * set ip and fp values to those of the caller. used just prior to entering
   * sigwait to set fp & ip so that GC will scan the threads stack
   * starting at the frame of the method that called sigwait.
   */
  final void setInnermost() {
    int current_fp = VM_Magic.getFramePointer();
    ip = VM_Magic.getReturnAddress(current_fp);
    fp = VM_Magic.getCallerFramePointer(current_fp);
    gprs[VM_BaselineConstants.FP] = fp;
  }

}
