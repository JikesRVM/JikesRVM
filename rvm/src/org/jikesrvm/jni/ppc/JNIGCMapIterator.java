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
package org.jikesrvm.jni.ppc;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.jni.JNIEnvironment;
import org.jikesrvm.mm.mminterface.GCMapIterator;
import org.jikesrvm.ppc.BaselineConstants;
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
 * for the non-votatile registers to point to the register save area
 * in the transition frame.
 *
 * If GC happens, the saved non-volatile regs may get modified (ex. a ref
 * to a live object that gets moved), and a restore flag in the frame is
 * set to cause the returning Native code to restore those registers from
 * this save area.  If GC does not occur, the Native C code has restored
 * these regs, and the transition return code does not do the restore.
 */
@Uninterruptible
public abstract class JNIGCMapIterator extends GCMapIterator
    implements BaselineConstants, JNIStackframeLayoutConstants {

  // non-volitile regs are saved at the end of the transition frame,
  // after the saved JTOC and SP, and preceeded by a GC flag.
  //
  // JNI Java to Native C transition frame...
  //
  //     <-- | saved FP       |  <- this.framePtr
  //     |   |    ...         |
  //     |   |    ...         |
  //     |   | GC flag        |
  //     |   | saved affinity |
  //     |   | proc reg       |
  //     |   | non vol 17     |
  //     |   |    ...         |
  //     |   | non vol 31     |
  //     |   | saved SP       |
  //     |   | saved JTOC     |
  //     --> |                |  <- callers FP
  //
  // The following constant is the offset from the callers FP to
  // the GC flag at the beginning of this area.
  //

  public static final int verbose = 0;

  // additional instance fields added by this subclass of GCMapIterator
  private AddressArray jniRefs;
  private int jniNextRef;
  private int jniFramePtr;

  public JNIGCMapIterator(WordArray registerLocations) {
    this.registerLocations = registerLocations;
  }

  // Override newStackWalk() in parent class GCMapIterator to
  // initialize iterator for scan of JNI JREFs stack of refs
  // Taken:    thread
  // Returned: nothing
  //
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

  public void setupIterator(CompiledMethod compiledMethod, Offset instructionOffset, Address framePtr) {
    this.framePtr = framePtr;
    Address callers_fp = this.framePtr.loadAddress();

    // set the GC flag in the Java to C frame to indicate GC occurred
    // this forces saved non volatile regs to be restored from save area
    // where those containing refs have been relocated if necessary
    //
    callers_fp.minus(JNI_GC_FLAG_OFFSET).store(1);
  }

  // return (address of) next ref in the current "frame" on the
  // threads JNIEnvironment stack of refs
  // When at the end of the current frame, update register locations to point
  // to the non-volatile registers saved in the JNI transition frame.
  //
  public Address getNextReferenceAddress() {
    if (jniNextRef > jniFramePtr) {
      Address ref_address = Magic.objectAsAddress(jniRefs).plus(jniNextRef);
      jniNextRef = jniNextRef - BYTES_IN_ADDRESS;
      if (verbose > 0) VM.sysWriteln("JNI iterator returning JNI ref: ", ref_address);
      return ref_address;
    }

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
    Address registerLocation = this.framePtr.loadAddress().minus(JNI_RVM_NONVOLATILE_OFFSET);

    for (int i = LAST_NONVOLATILE_GPR; i >= FIRST_NONVOLATILE_GPR - 1; --i) {
      registerLocations.set(i, registerLocation.toWord());
      registerLocation = registerLocation.minus(BYTES_IN_ADDRESS);
    }

    if (verbose > 1) VM.sysWriteln("JNI iterator returning 0");
    return Address.zero();  // no more refs to report
  }

  public Address getNextReturnAddressAddress() {
    return Address.zero();
  }

  public void reset() {}

  public void cleanupPointers() {}

  public int getType() {
    return CompiledMethod.JNI;
  }
}
