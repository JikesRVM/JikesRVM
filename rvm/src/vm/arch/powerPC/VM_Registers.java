/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * The machine state comprising a thread's execution context.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
public class VM_Registers implements VM_Constants, VM_Uninterruptible {
  // The following are used both for thread context switching
  // and for hardware exception reporting/delivery.
  //
  public int    gprs[]; // 32-bit general purpose registers
  public double fprs[]; // 64-bit floating point registers
  public VM_Address ip; // instruction address register
  
  // The following are used by exception delivery.
  // They are set by either VM_Runtime.athrow or the C hardware exception 
  // handler and restored by "VM_Magic.restoreHardwareExceptionState".
  // They are not used for context switching.
  //
  public VM_Address lr;     // link register
  public boolean inuse; // do exception registers currently contain live values?

  static VM_Address invalidIP = VM_Address.fromInt(-1);
  
  VM_Registers() {
    gprs = new int[NUM_GPRS];
    fprs = new double[NUM_FPRS];
    ip = invalidIP;
  }

  // Return framepointer for the deepest stackframe
  //
  public final VM_Address getInnermostFramePointer () {
      return VM_Address.fromInt(gprs[FRAME_POINTER]);
  }

  // Return next instruction address for the deepest stackframe
  //
  public final VM_Address getInnermostInstructionAddress () {
    if (ip.NE(invalidIP)) return ip; // ip set by hardware exception handler or VM_Magic.threadSwitch
    return VM_Magic.getNextInstructionAddress(getInnermostFramePointer()); // ip set to -1 because we're unwinding
  }

  // update the machine state to unwind the deepest stackframe.
  // 
  public final void unwindStackFrame() {
    ip = invalidIP; // if there was a valid value in ip, it ain't valid anymore
    gprs[FRAME_POINTER] = VM_Magic.getCallerFramePointer(getInnermostFramePointer()).toInt();
  }

  // set ip & fp. used to control the stack frame at which a scan of
  // the stack during GC will start, for ex., the top java frame for
  // a thread that is blocked in native code during GC.
  //
  public final void setInnermost( VM_Address newip, VM_Address newfp ) {
    ip = newip;
    gprs[FRAME_POINTER] = newfp.toInt();
  }

  // set ip and fp values to those of the caller. used just prior to entering
  // sigwait to set fp & ip so that GC will scan the threads stack
  // starting at the frame of the method that called sigwait.
  //
  public final void setInnermost() {
    VM_Address fp = VM_Magic.getFramePointer();
    ip = VM_Magic.getReturnAddress(fp);
    gprs[FRAME_POINTER] = VM_Magic.getCallerFramePointer(fp).toInt();
  }
}
