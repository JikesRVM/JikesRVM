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
package org.jikesrvm.ppc;

import org.jikesrvm.mm.mminterface.MM_Interface;
import org.jikesrvm.runtime.ArchEntrypoints;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Untraced;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.WordArray;

/**
 * The machine state comprising a thread's execution context.
 */
@Uninterruptible
public abstract class Registers implements ArchConstants {
  // The following are used both for thread context switching
  // and for hardware exception reporting/delivery.
  //
  @Untraced
  public final WordArray gprs; // word size general purpose registers (either 32 or 64 bit)
  @Untraced
  public final double[] fprs; // 64-bit floating point registers
  public final WordArray gprsShadow;
  public final double[] fprsShadow;
  public Address ip; // instruction address register

  // The following are used by exception delivery.
  // They are set by either Runtime.athrow or the C hardware exception
  // handler and restored by "Magic.restoreHardwareExceptionState".
  // They are not used for context switching.
  //
  public Address lr;     // link register
  public boolean inuse; // do exception registers currently contain live values?

  static Address invalidIP = Address.max();

  public Registers() {
    gprs = gprsShadow = MM_Interface.newNonMovingWordArray(NUM_GPRS);
    fprs = fprsShadow = MM_Interface.newNonMovingDoubleArray(NUM_FPRS);
    ip = invalidIP;
  }

  // Return framepointer for the deepest stackframe
  //
  public final Address getInnermostFramePointer() {
    return gprs.get(FRAME_POINTER).toAddress();
  }

  // Return next instruction address for the deepest stackframe
  //
  public final Address getInnermostInstructionAddress() {
    if (ip.NE(invalidIP)) return ip; // ip set by hardware exception handler or Magic.threadSwitch
    return Magic.getNextInstructionAddress(getInnermostFramePointer()); // ip set to -1 because we're unwinding
  }

  // update the machine state to unwind the deepest stackframe.
  //
  public final void unwindStackFrame() {
    ip = invalidIP; // if there was a valid value in ip, it ain't valid anymore
    gprs.set(FRAME_POINTER, Magic.getCallerFramePointer(getInnermostFramePointer()).toWord());
  }

  // set ip & fp. used to control the stack frame at which a scan of
  // the stack during GC will start, for ex., the top java frame for
  // a thread that is blocked in native code during GC.
  //
  public final void setInnermost(Address newip, Address newfp) {
    ip = newip;
    gprs.set(FRAME_POINTER, newfp.toWord());
  }

  // set ip and fp values to those of the caller. used just prior to entering
  // sigwait to set fp & ip so that GC will scan the threads stack
  // starting at the frame of the method that called sigwait.
  //
  public final void setInnermost() {
    Address fp = Magic.getFramePointer();
    ip = Magic.getReturnAddress(fp);
    gprs.set(FRAME_POINTER, Magic.getCallerFramePointer(fp).toWord());
  }

  public final Address getIPLocation() {
    Offset ipOffset = ArchEntrypoints.registersIPField.getOffset();
    return Magic.objectAsAddress(this).plus(ipOffset);
  }

}
