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
package org.jikesrvm.mm.mminterface;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.ArchitectureSpecific.BaselineGCMapIterator;
import org.jikesrvm.ArchitectureSpecific.JNIGCMapIterator;
import org.jikesrvm.ArchitectureSpecificOpt.OptGCMapIterator;
import org.jikesrvm.VM;
import org.jikesrvm.SizeConstants;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.HardwareTrapGCMapIterator;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.WordArray;

/**
 * Maintains a collection of compiler specific GCMapIterators that are used
 * by collection threads when scanning thread stacks to locate object references
 * in those stacks. Each collector thread has its own GCMapIteratorGroup.
 * <p>
 * The group contains a GCMapIterator for each type of stack frame that
 * may be found while scanning a stack during garbage collection, including
 * frames for baseline compiled methods, OPT compiled methods, and frames
 * for transitions from Java into JNI native code. These iterators are
 * responsible for reporting the location of references in the stack or
 * register save areas.
 *
 * @see GCMapIterator
 * @see CompiledMethod
 * @see CollectorThread
 */
public final class GCMapIteratorGroup implements SizeConstants {

  /** current location (memory address) of each gpr register */
  private final WordArray registerLocations;

  /** iterator for baseline compiled frames */
  private final GCMapIterator baselineIterator;

  /** iterator for opt compiled frames */
  private final GCMapIterator optIterator;

  /** iterator for HardwareTrap stackframes */
  private final GCMapIterator hardwareTrapIterator;

  /** iterator for JNI Java -> C  stackframes */
  private final GCMapIterator jniIterator;

  public GCMapIteratorGroup() {
    registerLocations = WordArray.create(ArchitectureSpecific.ArchConstants.NUM_GPRS);

    baselineIterator = new BaselineGCMapIterator(registerLocations);
    if (VM.BuildForOptCompiler) {
      optIterator = new OptGCMapIterator(registerLocations);
    } else {
      optIterator = null;
    }
    jniIterator = new JNIGCMapIterator(registerLocations);
    hardwareTrapIterator = new HardwareTrapGCMapIterator(registerLocations);
  }

  /**
   * Prepare to scan a thread's stack for object references.
   * Called by collector threads when beginning to scan a threads stack.
   * Calls newStackWalk for each of the contained GCMapIterators.
   * <p>
   * Assumption:  the thread is currently suspended, ie. its saved gprs[]
   * contain the thread's full register state.
   * <p>
   * Side effect: registerLocations[] initialized with pointers to the
   * thread's saved gprs[] (in thread.contextRegisters.gprs)
   * <p>
   * @param thread  Thread whose registers and stack are to be scanned
   */
  @Uninterruptible
  public void newStackWalk(RVMThread thread, Address registerLocation) {
    for (int i = 0; i < ArchitectureSpecific.ArchConstants.NUM_GPRS; ++i) {
      registerLocations.set(i, registerLocation.toWord());
      registerLocation = registerLocation.plus(BYTES_IN_ADDRESS);
    }
    baselineIterator.newStackWalk(thread);
    if (VM.BuildForOptCompiler) {
      optIterator.newStackWalk(thread);
    }
    hardwareTrapIterator.newStackWalk(thread);
    jniIterator.newStackWalk(thread);
  }

  /**
   * Select iterator for scanning for object references in a stackframe.
   * Called by collector threads while scanning a threads stack.
   *
   * @param compiledMethod  CompiledMethod for the method executing
   *                        in the stack frame
   *
   * @return GCMapIterator to use
   */
  @Uninterruptible
  public GCMapIterator selectIterator(CompiledMethod compiledMethod) {
    switch (compiledMethod.getCompilerType()) {
      case CompiledMethod.TRAP:
        return hardwareTrapIterator;
      case CompiledMethod.BASELINE:
        return baselineIterator;
      case CompiledMethod.OPT:
        return optIterator;
      case CompiledMethod.JNI:
        return jniIterator;
    }
    if (VM.VerifyAssertions) {
      VM._assert(VM.NOT_REACHED, "GCMapIteratorGroup.selectIterator: Unknown type of compiled method");
    }
    return null;
  }

  /**
   * get the GCMapIterator used for scanning JNI native stack frames.
   *
   * @return jniIterator
   */
  @Uninterruptible
  public GCMapIterator getJniIterator() {
    if (VM.VerifyAssertions) VM._assert(jniIterator != null);
    return jniIterator;
  }
}
