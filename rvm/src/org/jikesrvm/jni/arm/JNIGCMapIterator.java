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
package org.jikesrvm.jni.arm;

import static org.jikesrvm.runtime.UnboxedSizeConstants.BYTES_IN_ADDRESS;
import static org.jikesrvm.runtime.UnboxedSizeConstants.LOG_BYTES_IN_ADDRESS;
import static org.jikesrvm.arm.RegisterConstants.FIRST_NONVOLATILE_GPR;
import static org.jikesrvm.arm.RegisterConstants.LAST_NONVOLATILE_GPR;

import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.jni.JNIEnvironment;
import org.jikesrvm.mm.mminterface.GCMapIterator;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.AddressArray;
import org.vmmagic.unboxed.Offset;
import org.jikesrvm.runtime.Magic;

/**
 * Iterator for stack frames inserted at the transition from Java to
 * JNI Native C.  It will report JREFs associated with the executing
 * C frames which are in the "JREFs stack" attached to the executing
 * Threads JNIEnvironment.  It will update register location addresses
 * for the non-votatile registers to point to the register save area
 * in the transition frame.
 * <p>
 * If GC happens, the saved non-volatile regs may get modified (ex. a ref
 * to a live object that gets moved), and a restore flag in the frame is
 * set to cause the returning Native code to restore those registers from
 * this save area.  If GC does not occur, the Native C code has restored
 * these regs, and the transition return code does not do the restore.
 */
@Uninterruptible
public final class JNIGCMapIterator extends GCMapIterator {

  /*  Java to Native C transition frame...(see JNICompiler)
   *
   *                 java caller stack      <--- SP at the point when control passes to the callee
   *            +-------------------------+
   *            |     Saved LR            | <--- Callee's FP + 8     (STACKFRAME_RETURN_ADDRESS_OFFSET = 8)
   *            +-------------------------+
   *            |   Compiled method ID    | <--- Callee's FP + 4     (STACKFRAME_METHOD_ID_OFFSET = 4)
   *            +-------------------------+
   *            |     Saved R11 (FP)      | <--- Callee's FP         (STACKFRAME_FRAME_POINTER_OFFSET = 0)
   *            +-------------------------+
   *            |                         | <--- Highest saved core register (R8)   (STACKFRAME_SAVED_REGISTER_OFFSET = 0, meaning the top of the slot)
   *            |                         |
   *            |     Saved Registers     |
   *            |                         |
   *            |                         | <--- Lowest saved core register (R4)
   *            +-------------------------+
   *            |     JNIEnvironment      |
   *            +-------------------------+
   *            |    native stack frame   |
   */

  // additional instance fields added by this subclass of GCMapIterator
  private AddressArray jniRefs;
  private int jniNextRef;
  private int jniFramePtr;

  public JNIGCMapIterator(AddressArray registerLocations) {
    super(registerLocations);
  }

  // Override newStackWalk() in parent class GCMapIterator to
  // initialize iterator for scan of JNI JREFs stack of refs
  // Taken:    thread
  // Returned: nothing
  //
  @Override
  public void newStackWalk(RVMThread thread) {
    super.newStackWalk(thread);   // sets this.thread
    JNIEnvironment env = this.thread.getJNIEnv();
    // the "primordial" thread, created by JDK in the bootimage, does not have
    // a JniEnv object, all threads created by the VM will.
    if (env != null) {
      this.jniRefs = env.JNIRefs;
      this.jniNextRef = env.JNIRefsTop;
      this.jniFramePtr = env.JNIRefsSavedFP;
    }
  }

  @Override
  public void setupIterator(CompiledMethod compiledMethod, Offset instructionOffset, Address framePtr) {
    this.framePtr = framePtr;
  }

  // return (address of) next ref in the current "frame" on the
  // threads JNIEnvironment stack of refs
  // When at the end of the current frame, update register locations to point
  // to the non-volatile registers saved in the JNI transition frame.
  //
  @Override
  public Address getNextReferenceAddress() {
    // first report jni refs in the current frame in the jniRef side stack
    // until all in the frame are reported
    //
    if (jniNextRef > jniFramePtr) {
      Address ref_address = Magic.objectAsAddress(jniRefs).plus(jniNextRef);
      jniNextRef = jniNextRef - BYTES_IN_ADDRESS;
      return ref_address;
    }

    // no more refs to report, before returning 0, setup for processing
    // the next jni frame, if any

    // jniNextRef -> savedFramePtr for another "frame" of refs for another
    // sequence of Native C frames lower in the stack, or to 0 if this is the
    // last jni frame in the JNIRefs stack.  If more frames, initialize for a
    // later scan of those refs.
    //
    if (jniFramePtr > 0) {
      jniFramePtr = jniRefs.get(jniFramePtr >> LOG_BYTES_IN_ADDRESS).toInt();
      jniNextRef = jniNextRef - BYTES_IN_ADDRESS;
    }

    // set register locations for non-volatiles to point to registers saved in
    // the JNI transition frame at a fixed negative offset from the callers FP.
    Address registerLocation = this.framePtr;

    for (int i = LAST_NONVOLATILE_GPR.value(); i >= FIRST_NONVOLATILE_GPR.value(); i--) {
      registerLocation = registerLocation.minus(BYTES_IN_ADDRESS);
      registerLocations.set(i, registerLocation);
    }

    return Address.zero();  // no more refs to report
  }

  @Override
  public Address getNextReturnAddressAddress() {
    return Address.zero();
  }

  @Override
  public void reset() {}

  @Override
  public void cleanupPointers() {}

  @Override
  public int getType() {
    return CompiledMethod.JNI;
  }
}
