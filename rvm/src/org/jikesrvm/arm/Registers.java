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
package org.jikesrvm.arm;

import static org.jikesrvm.arm.RegisterConstants.FP;
import static org.jikesrvm.arm.RegisterConstants.SP;
import static org.jikesrvm.arm.StackframeLayoutConstants.STACKFRAME_PARAMETER_OFFSET;
import static org.jikesrvm.arm.StackframeLayoutConstants.STACKFRAME_FRAME_POINTER_OFFSET;
import static org.jikesrvm.arm.StackframeLayoutConstants.STACKFRAME_SENTINEL_FP;
import static org.jikesrvm.arm.StackframeLayoutConstants.STACKFRAME_RETURN_ADDRESS_OFFSET;
import static org.jikesrvm.arm.StackframeLayoutConstants.STACKFRAME_METHOD_ID_OFFSET;
import static org.jikesrvm.arm.StackframeLayoutConstants.INVISIBLE_METHOD_ID;
import static org.jikesrvm.arm.StackframeLayoutConstants.STACKFRAME_END_OF_FRAME_OFFSET;

import org.jikesrvm.runtime.Magic;
import org.jikesrvm.VM;
import org.jikesrvm.architecture.AbstractRegisters;
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

  /** link register */
  @Entrypoint
  public Address lr;

  private static final Address invalid = Address.max();

  public Registers() {
    ip = lr = invalid;
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
    return getGPRs().get(FP.value()).toAddress();
  }

  /** @return next instruction address for the deepest stackframe */
  @Override
  public Address getInnermostInstructionAddress() {
    if (VM.VerifyAssertions) VM._assert(ip.NE(invalid));
    return ip;
  }

  /** Update the machine state to unwind the deepest stackframe. */
  @Override
  public void unwindStackFrame() {
    ip = Magic.getNextInstructionAddress(getInnermostFramePointer());                            // Set ip to the return address of current function
    getGPRs().set(FP.value(), Magic.getCallerFramePointer(getInnermostFramePointer()).toWord()); // Set fp to caller's fp
  }

  /**
   * Set pc &amp; fp. used to control the stack frame at which a scan of
   * the stack during GC will start, for ex., the top java frame for
   * a thread that is blocked in native code during GC.
   */
  @Override
  public void setInnermost(Address newip, Address newfp) {
    ip = newip;
    getGPRs().set(FP.value(), newfp.toWord());
  }

  /**
   * Set pc and fp values to those of the caller. used just prior to entering
   * sigwait to set fp &amp; ip so that GC will scan the threads stack
   * starting at the frame of the method that called sigwait.
   */
  public void setInnermost() {
    Address fp = Magic.getFramePointer();
    ip = Magic.getReturnAddress(fp);
    getGPRs().set(FP.value(), Magic.getCallerFramePointer(fp).toWord());
  }

  // Initialize a thread stack as if "startoff" method had been called
  // by an empty baseline-compiled "sentinel" frame
  @Uninterruptible
  @Override
  public void initializeStack(Address ip, Address sp) {
    Address fp = sp.minus(STACKFRAME_PARAMETER_OFFSET);
    fp.plus(STACKFRAME_FRAME_POINTER_OFFSET).store(STACKFRAME_SENTINEL_FP);
    fp.plus(STACKFRAME_RETURN_ADDRESS_OFFSET).store(Address.zero());
    fp.plus(STACKFRAME_METHOD_ID_OFFSET).store(INVISIBLE_METHOD_ID);
    sp = fp.plus(STACKFRAME_END_OF_FRAME_OFFSET);

    getGPRs().set(FP.value(), fp.toWord());
    getGPRs().set(SP.value(), sp.toWord()); // Note: this value gets ignored. See sysThreadStartup() in sysThread.c, which recalculates it from the fp
    this.lr = Address.zero();
    this.ip = ip;
  }

  @Uninterruptible
  @Override
  public void adjustStackPointer(Offset delta, boolean traceAdjustments) {
    Word old = getGPRs().get(SP.value());
    getGPRs().set(SP.value(), old.plus(delta));
    if (traceAdjustments) {
      VM.sysWrite(" sp =");
      VM.sysWrite(getGPRs().get(SP.value()));
    }
  }

}
