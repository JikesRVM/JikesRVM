/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * The machine state comprising a thread's execution context.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
class VM_Registers implements VM_Constants, VM_Uninterruptible {
  // The following are used both for thread context switching
  // and for hardware exception reporting/delivery.
  //
  int    gprs[]; // 32-bit general purpose registers
  double fprs[]; // 64-bit floating point registers
  
  // The following are used by exception delivery.
  // They are set by either VM_Runtime.athrow or the C hardware exception 
  // handler and restored by "VM_Magic.restoreHardwareExceptionState".
  // They are not used for context switching.
  //
  int    ip;     // instruction address register
  int    lr;     // link register
  boolean inuse; // do exception registers currently contain live values?
  
  VM_Registers() {
    gprs = new int[NUM_GPRS];
    fprs = new double[NUM_FPRS];
    ip = -1;
  }

  // Return framepointer for the deepest stackframe
  //
  final int getInnermostFramePointer () {
    return gprs[FRAME_POINTER];
  }

  // Return next instruction address for the deepest stackframe
  //
  final int getInnermostInstructionAddress () {
    if (ip != -1) return ip; // ip set by hardware exception handler
    return VM_Magic.getNextInstructionAddress(gprs[FRAME_POINTER]); // ip set to -1 by thread switch or athrow
  }

  // update the machine state to unwind the deepest stackframe.
  // 
  final void unwindStackFrame() {
    ip = -1; // if there was a valid value in ip, it ain't valid anymore!
    gprs[FRAME_POINTER] = VM_Magic.getCallerFramePointer(gprs[FRAME_POINTER]);
  }

  // set ip & fp. used to control the stack frame at which a scan of
  // the stack during GC will start, for ex., the top java frame for
  // a thread that is blocked in native code during GC.
  //
  final void setInnermost( int newip, int newfp ) {
    ip = newip;
    gprs[FRAME_POINTER] = newfp;
  }

  // set ip and fp values to those of the caller. used just prior to entering
  // sigwait to set fp & ip so that GC will scan the threads stack
  // starting at the frame of the method that called sigwait.
  //
  final void setInnermost() {
    int fp = VM_Magic.getFramePointer();
    ip = VM_Magic.getReturnAddress(fp);
    gprs[FRAME_POINTER] = VM_Magic.getCallerFramePointer(fp);
  }
}
