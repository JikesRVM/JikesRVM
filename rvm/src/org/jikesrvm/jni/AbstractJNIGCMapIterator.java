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
package org.jikesrvm.jni;

import static org.jikesrvm.runtime.UnboxedSizeConstants.BYTES_IN_ADDRESS;
import static org.jikesrvm.runtime.UnboxedSizeConstants.LOG_BYTES_IN_ADDRESS;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.mm.mminterface.GCMapIterator;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.AddressArray;
import org.vmmagic.unboxed.Offset;

/**
 * This class implements the architecture-independent functionality that is used
 * by all currently implemented JNI GC map iterators.
 */
@Uninterruptible
public abstract class AbstractJNIGCMapIterator extends GCMapIterator {

  protected AddressArray jniRefs;
  protected int jniNextRef;
  protected int jniFramePtr;

  public static final int verbose = 0;

  public AbstractJNIGCMapIterator(AddressArray registerLocations) {
    super(registerLocations);
  }

  @Override
  public final void newStackWalk(RVMThread thread) {
    super.newStackWalk(thread);
    JNIEnvironment env = this.thread.getJNIEnv();
    // the "primordial" thread, created by JDK in the bootimage, does not have
    // a JniEnv object, all threads created by the VM will.
    if (env != null) {
      this.jniRefs = env.refsArray();
      this.jniNextRef = env.refsTop();
      this.jniFramePtr = env.savedRefsFP();
    }
  }

  @Override
  public final void setupIterator(CompiledMethod compiledMethod, Offset instructionOffset, Address framePtr) {
    this.framePtr = framePtr;
    setupIteratorForArchitecture();
  }

  protected abstract void setupIteratorForArchitecture();

  /**
   * Returns (address of) next ref in the current "frame" on the
   * threads JNIEnvironment stack of refs. When at the end of the current frame,
   * update register locations to point to the non-volatile registers saved in
   * the JNI transition frame.
   */
  @Override
  public final Address getNextReferenceAddress() {
    // first report JNI refs in the current frame in the jniRef side stack
    // until all in the frame are reported
    //
    if (jniNextRef > jniFramePtr) {
      Address ref_address = Magic.objectAsAddress(jniRefs).plus(jniNextRef);
      jniNextRef -= BYTES_IN_ADDRESS;
      if (verbose > 0) VM.sysWriteln("JNI iterator returning JNI ref: ", ref_address);
      return ref_address;
    }

    // no more refs to report, before returning 0, setup for processing
    // the next JNI frame, if any

    // jniNextRef -> savedFramePtr for another "frame" of refs for another
    // sequence of Native C frames lower in the stack, or to 0 if this is the
    // last JNI frame in the JNIRefs stack.  If more frames, initialize for a
    // later scan of those refs.
    //
    if (jniFramePtr > 0) {
      jniFramePtr = jniRefs.get(jniFramePtr >> LOG_BYTES_IN_ADDRESS).toInt();
      jniNextRef = jniNextRef - BYTES_IN_ADDRESS;
    }

    setRegisterLocations();

    if (verbose > 1) VM.sysWriteln("JNI iterator returning 0");
    return Address.zero();  // no more refs to report
  }

  protected abstract void setRegisterLocations();

  @Override
  public final void reset() {
    // resetting is completely handled by iterator setup for the
    // currently implemented architectures
  }

  @Override
  public final void cleanupPointers() {
    // no cleanup necessary for current architectures
  }

  /**
   * @return {@link CompiledMethod#JNI JNI}
   */
  @Override
  public final int getType() {
    return CompiledMethod.JNI;
  }

}
