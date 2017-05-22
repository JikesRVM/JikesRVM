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
package org.jikesrvm.ppc;

import static org.jikesrvm.ppc.RegisterConstants.FRAME_POINTER;
import static org.jikesrvm.ppc.StackframeLayoutConstants.INVISIBLE_METHOD_ID;
import static org.jikesrvm.ppc.StackframeLayoutConstants.STACKFRAME_ALIGNMENT;
import static org.jikesrvm.ppc.StackframeLayoutConstants.STACKFRAME_FRAME_POINTER_OFFSET;
import static org.jikesrvm.ppc.StackframeLayoutConstants.STACKFRAME_HEADER_SIZE;
import static org.jikesrvm.ppc.StackframeLayoutConstants.STACKFRAME_METHOD_ID_OFFSET;
import static org.jikesrvm.ppc.StackframeLayoutConstants.STACKFRAME_RETURN_ADDRESS_OFFSET;
import static org.jikesrvm.ppc.StackframeLayoutConstants.STACKFRAME_SENTINEL_FP;

import org.jikesrvm.VM;
import org.jikesrvm.architecture.AbstractRegisters;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Memory;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.NonMoving;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * The machine state comprising a thread's execution context.
 */
@Uninterruptible
@NonMoving
public final class Registers extends AbstractRegisters {
  // The following are used by exception delivery.
  // They are set by either Runtime.athrow or the C hardware exception
  // handler and restored by "Magic.restoreHardwareExceptionState".
  // They are not used for context switching.
  //
  /** link register */
  @Entrypoint
  public Address lr;
  /** do exception registers currently contain live values? */

  private static final Address invalidIP = Address.max();

  public Registers() {
    ip = invalidIP;
  }

  @Override
  public void clear() {
    lr = Address.zero();
    super.clear();
  }

  @Override
  public void dump() {
    super.dump();
    VM.sysWriteln("lr = ", lr);
  }

  /** @return framepointer for the deepest stackframe */
  @Override
  public Address getInnermostFramePointer() {
    return getGPRs().get(FRAME_POINTER.value()).toAddress();
  }

  /** @return next instruction address for the deepest stackframe */
  @Override
  public Address getInnermostInstructionAddress() {
    if (ip.NE(invalidIP)) return ip; // ip set by hardware exception handler or Magic.threadSwitch
    return Magic.getNextInstructionAddress(getInnermostFramePointer()); // ip set to -1 because we're unwinding
  }

  /** Update the machine state to unwind the deepest stackframe. */
  @Override
  public void unwindStackFrame() {
    ip = invalidIP; // if there was a valid value in ip, it ain't valid anymore
    getGPRs().set(FRAME_POINTER.value(), Magic.getCallerFramePointer(getInnermostFramePointer()).toWord());
  }

  /**
   * Set ip &amp; fp. used to control the stack frame at which a scan of
   * the stack during GC will start, for ex., the top java frame for
   * a thread that is blocked in native code during GC.
   */
  @Override
  public void setInnermost(Address newip, Address newfp) {
    ip = newip;
    getGPRs().set(FRAME_POINTER.value(), newfp.toWord());
  }

  /**
   * Set ip and fp values to those of the caller. used just prior to entering
   * sigwait to set fp &amp; ip so that GC will scan the threads stack
   * starting at the frame of the method that called sigwait.
   */
  public void setInnermost() {
    Address fp = Magic.getFramePointer();
    ip = Magic.getReturnAddress(fp);
    getGPRs().set(FRAME_POINTER.value(), Magic.getCallerFramePointer(fp).toWord());
  }

 /**
   * The following method initializes a thread stack as if
   * "startoff" method had been called by an empty baseline-compiled
   *  "sentinel" frame with one local variable
   *
   * @param ip The instruction pointer for the "startoff" method
   * @param sp The base of the stack
   */
  @Override
  @Uninterruptible
  public void initializeStack(Address ip, Address sp) {
    Address fp;
    // align stack frame
    int INITIAL_FRAME_SIZE = STACKFRAME_HEADER_SIZE;
    fp = Memory.alignDown(sp.minus(INITIAL_FRAME_SIZE), STACKFRAME_ALIGNMENT);
    fp.plus(STACKFRAME_FRAME_POINTER_OFFSET).store(STACKFRAME_SENTINEL_FP);
    fp.plus(STACKFRAME_RETURN_ADDRESS_OFFSET).store(ip); // need to fix
    fp.plus(STACKFRAME_METHOD_ID_OFFSET).store(INVISIBLE_METHOD_ID);

    getGPRs().set(FRAME_POINTER.value(), fp.toWord());
    this.ip = ip;
  }

  @Uninterruptible
  @Override
  public void adjustESP(Offset delta, boolean traceAdjustments) {
    Word old = getGPRs().get(FRAME_POINTER.value());
    getGPRs().set(FRAME_POINTER.value(), old.plus(delta));
    if (traceAdjustments) {
      VM.sysWrite(" esp =");
      VM.sysWrite(getGPRs().get(FRAME_POINTER.value()));
    }
  }
}
