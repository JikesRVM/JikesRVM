/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * The machine state comprising a thread's execution context.
 *
 * @author Bowen Alpern
 * @author David Grove
 */
public class VM_Registers implements VM_Constants, Uninterruptible {

  // The following are used both for thread context switching
  // and for software/hardware exception reporting/delivery.
  //
  public WordArray gprs; // general purpose registers
  public double fprs[]; // floating point registers
  public Address ip;     // instruction address register
  public Address fp;     // frame pointer
  
  // set by C hardware exception handler and VM_Runtime.athrow 
  // and reset by each implementation of VM_ExceptionDeliverer.deliverException
  //
  public boolean inuse; // do exception registers currently contain live values?
  
  public VM_Registers() {
    gprs = WordArray.create(NUM_GPRS);
    fprs = new double[NUM_FPRS];
  }
  
  /**
   * Return framepointer for the deepest stackframe
   */
  public final Address getInnermostFramePointer() {
    return fp;
  }
  
  /**
   * Return next instruction address for the deepest stackframe
   */
  public final Address getInnermostInstructionAddress() {
    return ip;
  }

  /**
   * update the machine state as if the stackframe were unwound.
   */
  public final void unwindStackFrame() {
    ip = VM_Magic.getReturnAddress(fp);
    fp = VM_Magic.getCallerFramePointer(fp);
  }

  /**
   * set ip & fp. used to control the stack frame at which a scan of
   * the stack during GC will start, for ex., the top java frame for
   * a thread that is blocked in native code during GC.
   */
  public final void setInnermost(Address newip, Address newfp) {
    ip = newip;
    fp = newfp;
  }

  /**
   * set ip and fp values to those of the caller. used just prior to entering
   * sigwait to set fp & ip so that GC will scan the threads stack
   * starting at the frame of the method that called sigwait.
   */
  public final void setInnermost() {
    Address current_fp = VM_Magic.getFramePointer();
    ip = VM_Magic.getReturnAddress(current_fp);
    fp = VM_Magic.getCallerFramePointer(current_fp);
  }

  public final Address getIPLocation() {
    int ipOffset = VM_Entrypoints.registersIPField.getOffset();
    return VM_Magic.objectAsAddress(this).add(ipOffset);
  }
}
