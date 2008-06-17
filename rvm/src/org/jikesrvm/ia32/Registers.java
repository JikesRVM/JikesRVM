/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.ia32;

import org.jikesrvm.runtime.ArchEntrypoints;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.memorymanagers.mminterface.MM_Interface;
import org.vmmagic.pragma.NonMoving;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Untraced;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.WordArray;

/**
 * The machine state comprising a thread's execution context.
 */
@Uninterruptible
@NonMoving
public abstract class Registers implements RegisterConstants {

  // The following are used both for thread context switching
  // and for software/hardware exception reporting/delivery.
  //
  @Untraced
  public final WordArray gprs; // general purpose registers
  @Untraced
  public final double[] fprs; // floating point registers
  public final WordArray gprsShadow;
  public final double[] fprsShadow;
  public Address ip;     // instruction address register
  public Address fp;     // frame pointer

  // set by C hardware exception handler and RuntimeEntrypoints.athrow
  // and reset by each implementation of ExceptionDeliverer.deliverException
  //
  public boolean inuse; // do exception registers currently contain live values?

  public Registers() {
    gprs = gprsShadow = MM_Interface.newNonMovingWordArray(NUM_GPRS);
    fprs = fprsShadow = MM_Interface.newNonMovingDoubleArray(NUM_FPRS);
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
    ip = Magic.getReturnAddress(fp);
    fp = Magic.getCallerFramePointer(fp);
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
    Address current_fp = Magic.getFramePointer();
    ip = Magic.getReturnAddress(current_fp);
    fp = Magic.getCallerFramePointer(current_fp);
  }

  public final Address getIPLocation() {
    Offset ipOffset = ArchEntrypoints.registersIPField.getOffset();
    return Magic.objectAsAddress(this).plus(ipOffset);
  }
}
