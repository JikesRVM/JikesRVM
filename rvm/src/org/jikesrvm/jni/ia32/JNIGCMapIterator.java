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
package org.jikesrvm.jni.ia32;

import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.ia32.BaselineConstants;
import org.jikesrvm.jni.JNIEnvironment;
import org.jikesrvm.mm.mminterface.GCMapIterator;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.AddressArray;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.WordArray;

/**
 * Iterator for stack frames inserted at the transition from Java to
 * JNI Native C.  It will report JREFs associated with the executing
 * C frames which are in the "JREFs stack" attached to the executing
 * Threads JNIEnvironment.  It will update register location addresses
 * for the non-volatile registers to point to the registers saved
 * in the transition frame.
 *
 * @see JNICompiler
 */
@Uninterruptible
public abstract class JNIGCMapIterator extends GCMapIterator implements BaselineConstants {

  // Java to Native C transition frame...(see JNICompiler)
  //
  //  0         + saved FP   + <---- FP for Java to Native C glue frame
  // -4         | methodID   |
  // -8         | saved EDI  |  non-volatile GPR
  // -C         | saved EBX  |  non-volatile GPR
  // -10        | saved EBP  |  non-volatile GPR
  // -14        | returnAddr |  (for return from OutOfLineMachineCode)
  // -18        | saved PR   |
  // -1C        | arg n-1    |  reordered arguments to native method
  // -20        |  ...       |  ...
  // -24        | arg 1      |  ...
  // -28        | arg 0      |  ...
  // -2C        | class/obj  |  required 2nd argument to all native methods
  // -30        | jniEnv     |  required 1st argument to all native methods
  // -34        | returnAddr |  return address pushed by call to native method
  //            + saved FP   +  <---- FP for called native method

  // additional instance fields added by this subclass of GCMapIterator
  AddressArray jniRefs;
  int jniNextRef;
  int jniFramePtr;

  public JNIGCMapIterator(WordArray registerLocations) {
    this.registerLocations = registerLocations;
  }

  // Override newStackWalk() in parent class GCMapIterator to
  // initialize iterator for scan of JNI JREFs stack of refs
  // Taken:    thread
  // Returned: nothing
  //
  public void newStackWalk(RVMThread thread) {
    super.newStackWalk(thread);   // sets this.thread, inits registerLocations[]
    JNIEnvironment env = this.thread.getJNIEnv();
    // the "primordial" thread, created by JDK in the bootimage, does not have
    // a JniEnv object, all threads created by the VM will.
    if (env != null) {
      this.jniRefs = env.refsArray();
      this.jniNextRef = env.refsTop();
      this.jniFramePtr = env.savedRefsFP();
    }
  }

  public void setupIterator(CompiledMethod compiledMethod, Offset instructionOffset, Address framePtr) {
    this.framePtr = framePtr;
  }

  // return (address of) next ref in the current "frame" on the
  // threads JNIEnvironment stack of refs
  // When at the end of the current frame, update register locations to point
  // to the non-volatile registers saved in the JNI transition frame.
  //
  public Address getNextReferenceAddress() {
    // first report jni refs in the current frame in the jniRef side stack
    // until all in the frame are reported
    //
    if (jniNextRef > jniFramePtr) {
      Address ref_address = Magic.objectAsAddress(jniRefs).plus(jniNextRef);
      jniNextRef -= BYTES_IN_ADDRESS;
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
    // the save non-volatiles are EBX EBP and EDI.
    //
    registerLocations.set(EDI.value(), framePtr.plus(JNICompiler.EDI_SAVE_OFFSET).toWord());
    registerLocations.set(EBX.value(), framePtr.plus(JNICompiler.EBX_SAVE_OFFSET).toWord());
    registerLocations.set(EBP.value(), framePtr.plus(JNICompiler.EBP_SAVE_OFFSET).toWord());

    return Address.zero();  // no more refs to report
  }

  public Address getNextReturnAddressAddress() {
    return Address.zero();
  }

  public void reset() { }

  public void cleanupPointers() { }

  public int getType() {
    return CompiledMethod.JNI;
  }
}
