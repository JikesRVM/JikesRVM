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
package org.jikesrvm.scheduler;

import org.jikesrvm.ArchitectureSpecific.ProcessorLocalState;
import org.jikesrvm.VM;
import org.jikesrvm.Constants;
import org.jikesrvm.mm.mminterface.ProcessorContext;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.NonMoving;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Unpreemptible;
import org.vmmagic.pragma.Untraced;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * The context a thread runs within. For green threads we multiplex execution of
 * large number of Threads on small number of native kernel threads. For
 * native threads the mapping is one-to-one.
 */
@Uninterruptible
@NonMoving
public abstract class Processor extends ProcessorContext implements Constants {
  /*
   * definitions for VP status for implementation of jni
   */
  /** VP is in Java code */
  public static final int IN_JAVA = 1;
  /** VP is in native code */
  public static final int IN_NATIVE = 2;
  /** VP is blocked in native code */
  public static final int BLOCKED_IN_NATIVE = 3;

  /*
   * NOTE: The order of field declarations determines
   *       the layout of the fields in the processor object
   *       For IA32, it is valuable (saves code space) to
   *       declare the most frequently used fields first so that
   *       they can be accessed with 8 bit immediates.
   *       On PPC, we have plenty of bits of immediates in
   *       load/store instructions, so it doesn't matter.
   */

  /*
   * BEGIN FREQUENTLY ACCESSED INSTANCE FIELDS
   */

  /**
   * Should the next executed yieldpoint be taken?
   * Can be true for a variety of reasons. See Thread.yieldpoint
   * <p>
   * To support efficient sampling of only prologue/epilogues
   * we also encode some extra information into this field.
   *   0  means that the yieldpoint should not be taken.
   *   >0 means that the next yieldpoint of any type should be taken
   *   <0 means that the next prologue/epilogue yieldpoint should be taken
   */
  @Entrypoint
  public int takeYieldpoint;

  /**
   * Thread currently running on this processor. NB for native threads this
   * field could be final
   */
  @Entrypoint
  @Untraced
  public RVMThread activeThread;

  /**
   * cached activeThread.stackLimit;
   * removes 1 load from stackoverflow sequence.
   */
  @Entrypoint
  public Address activeThreadStackLimit;

  /**
   * Cache the results of activeThread.getLockingId()
   * for use in monitor operations.
   */
  @Entrypoint
  public int threadId;

  /* --------- BEGIN IA-specific fields. NOTE: NEED TO REFACTOR --------- */
  // On powerpc, these values are in dedicated registers,
  // we don't have registers to burn on IA32, so we indirect
  // through the PR register to get them instead.
  /**
   * FP for current frame, saved in the prologue of every method
   */
  Address framePointer;
  /**
   * "hidden parameter" for interface invocation thru the IMT
   */
  int hiddenSignatureId;
  /**
   * "hidden parameter" from ArrayIndexOutOfBounds trap to C trap handler
   */
  int arrayIndexTrapParam;
  /* --------- END IA-specific fields. NOTE: NEED TO REFACTOR --------- */

  // More GC fields
  //
  /** count live objects during gc */
  public int large_live;
  /** count live objects during gc */
  public int small_live;
  /** used for instrumentation in allocators */
  public long totalBytesAllocated;
  /** used for instrumentation in allocators */
  public long totalObjectsAllocated;
  /** used for instrumentation in allocators */
  public long synchronizedObjectsAllocated;

  /**
   * Has the current time slice expired for this virtual processor?
   * This is set by the C time slicing code which is driven either
   * by a timer interrupt or by a dedicated pthread in a nanosleep loop.
   * Is set approximately once every VM.interruptQuantum ms except when
   * GC is in progress.
   */
  @Entrypoint
  public int timeSliceExpired;

  /**
   * Is the next taken yieldpoint in response to a request to
   * schedule a GC?
   */
  public boolean yieldToGCRequested;

  /**
   * Is the next taken yieldpoint in response to a request to perform OSR?
   */
  public boolean yieldToOSRRequested;

  /**
   * Is CBS enabled for 'call' yieldpoints?
   */
  public boolean yieldForCBSCall;

  /**
   * Is CBS enabled for 'method' yieldpoints?
   */
  public boolean yieldForCBSMethod;

  /**
   * Should we threadswitch when all CBS samples are taken for this window?
   */
  public boolean threadSwitchWhenCBSComplete;

  /**
   * Number of CBS samples to take in this window
   */
  public int numCBSCallSamples;

  /**
   * Number of call yieldpoints between CBS samples
   */
  public int countdownCBSCall;

  /**
   * round robin starting point for CBS samples
   */
  public int firstCBSCallSample;

  /**
   * Number of CBS samples to take in this window
   */
  public int numCBSMethodSamples;

  /**
   * Number of counter ticks between CBS samples
   */
  public int countdownCBSMethod;

  /**
   * round robin starting point for CBS samples
   */
  public int firstCBSMethodSample;

  /* --------- BEGIN PPC-specific fields. NOTE: NEED TO REFACTOR --------- */
  /**
   * flag indicating this processor needs to execute a memory synchronization sequence
   * Used for code patching on SMP PowerPCs.
   */
  public boolean codePatchSyncRequested;
  /* --------- END PPC-specific fields. NOTE: NEED TO REFACTOR --------- */

  /**
   * For builds using counter-based sampling.  This field holds a
   * processor-specific counter so that it can be updated efficiently
   * on SMP's.
   */
  public int processor_cbs_counter;

  // How many times timer interrupt has occurred since last thread switch
  public int interruptQuantumCounter = 0;

  /**
   * END FREQUENTLY ACCESSED INSTANCE FIELDS
   */

  /**
   * Identity of this processor.
   * Note: 1. Scheduler.processors[id] == this processor
   *      2. id must be non-zero because it is used in
   *      ProcessorLock ownership tests
   */
  public final int id;

  /**
   * Has this processor's pthread initialization completed yet?
   * A value of:
   *   false means "cpu is still executing C code (on a C stack)"
   *   true  means "cpu is now executing vm code (on a vm stack)"
   */
  public boolean isInitialized;

  /**
   * number of processor locks currently held (for assertion checking)
   */
  private int lockCount;

  private final String[] lockReasons = VM.VerifyAssertions ? new String[100] : null;

  public void registerLock(String reason) {
    Magic.setObjectAtOffset(lockReasons, Offset.fromIntSignExtend(lockCount<<VM.LOG_BITS_IN_ADDRESS), reason);
    lockCount ++;
  }
  public void registerUnlock() {
    lockCount --;
    VM._assert(lockCount >= 0);
  }
  protected void checkLockCount(int i) {
    if (lockCount != i) {
      VM.sysWrite("Error lock count not ", i);
      VM.sysWriteln(" but ", lockCount);
      for (int j=0; j < lockCount; j++) {
        VM.sysWrite("Processor lock ", j);
        VM.sysWriteln(" acquired for ", lockReasons[j]);
      }
      Scheduler.dumpStack();
    }
  }
  /**
   * Status of the processor.
   * Always one of IN_JAVA, IN_NATIVE or BLOCKED_IN_NATIVE.
   */
  public int vpStatus;

  /**
   * pthread_id (AIX's) for use by signal to wakeup
   * sleeping idle thread (signal accomplished by pthread_kill call)
   *
   * CRA, Maria
   */
  public int pthread_id;

  // to handle contention for processor locks
  //
  ProcessorLock awaitingProcessorLock;
  Processor contenderLink;

  /**
   * Scratch area for use for gpr <=> fpr transfers by PPC baseline compiler.
   * Used to transfer x87 to SSE registers on IA32
   */
  @SuppressWarnings({"unused", "CanBeFinal", "UnusedDeclaration"})
  //accessed via EntryPoints
  private double scratchStorage;

  /**
   * Create data object to be associated with an o/s kernel thread
   * (aka "virtual cpu" or "pthread").
   * @param id id that will be returned by getCurrentProcessorId() for
   * this processor.
   */
  protected Processor(int id) {
    this.id = id;
    this.vpStatus = IN_JAVA;
  }

  /**
   * Request the thread executing on the processor to take the next executed yieldpoint
   * and initiate a GC
   */
  public void requestYieldToGC() {
    takeYieldpoint = 1;
    yieldToGCRequested = true;
  }

  /**
   * Request the thread executing on the processor to take the next executed yieldpoint
   * and issue memory synchronization instructions
   */
  public void requestPostCodePatchSync() {
    if (VM.BuildForPowerPC) {
      takeYieldpoint = 1;
      codePatchSyncRequested = true;
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    }
  }

  /**
   * Get processor that's being used to run the current java thread.
   */
  @Inline
  public static Processor getCurrentProcessor() {
    return ProcessorLocalState.getCurrentProcessor();
  }

  /**
   * Get processor that's being used to run the current java thread.
   */
  @Inline
  public static RVMThread getCurrentThread() {
    return getCurrentProcessor().activeThread;
  }

  /**
   * Is it ok to switch to a new Thread in this processor? NB only has
   * meaning with green threads
   */
  public abstract boolean threadSwitchingEnabled();

  /**
   * Become next "ready" thread.
   * Note: This method is ONLY intended for use by Thread.
   * @param timerTick timer interrupted if true
   */
  @Unpreemptible("Becoming another thread interrupts the current thread, avoid preemption in the process")
  public abstract void dispatch(boolean timerTick);

  /**
   * Get id of processor that's being used to run the current java thread.
   */
  @Inline
  public static int getCurrentProcessorId() {
    return getCurrentProcessor().id;
  }

  //---------------------//
  // Garbage Collection  //
  //---------------------//

  public boolean unblockIfBlockedInC() {
    int newState, oldState;
    boolean result = true;
    Offset offset = Entrypoints.vpStatusField.getOffset();
    do {
      oldState = Magic.prepareInt(this, offset);
      if (oldState != BLOCKED_IN_NATIVE) {
        result = false;
        break;
      }
      newState = IN_NATIVE;
    } while (!(Magic.attemptInt(this, offset, oldState, newState)));
    return result;
  }

  /**
   * sets the VP status to BLOCKED_IN_NATIVE if it is currently IN_NATIVE (ie C)
   * returns true if BLOCKED_IN_NATIVE
   */
  public boolean lockInCIfInC() {
    int oldState;
    Offset offset = Entrypoints.vpStatusField.getOffset();
    do {
      oldState = Magic.prepareInt(this, offset);
      if (VM.VerifyAssertions) VM._assert(oldState != BLOCKED_IN_NATIVE);
      if (oldState != IN_NATIVE) {
        if (VM.VerifyAssertions) VM._assert(oldState == IN_JAVA);
        return false;
      }
    } while (!(Magic.attemptInt(this, offset, oldState, BLOCKED_IN_NATIVE)));
    return true;
  }
  /**
   * Disable thread switching in this processor.
   * @param reason for disabling thread switching
   */
  public abstract void disableThreadSwitching(String reason);

  /**
   * Enable thread switching in this processor.
   */
  public abstract void enableThreadSwitching();

  /**
   * Fail if thread switching is disabled on this processor
   */
  public abstract void failIfThreadSwitchingDisabled();
}
