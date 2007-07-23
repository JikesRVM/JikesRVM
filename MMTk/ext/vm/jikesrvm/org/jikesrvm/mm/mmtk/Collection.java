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
package org.jikesrvm.mm.mmtk;

import org.mmtk.plan.Plan;
import org.mmtk.plan.CollectorContext;
import org.mmtk.plan.MutatorContext;
import org.mmtk.utility.Constants;
import org.mmtk.utility.Finalizer;
import org.mmtk.utility.options.Options;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.compilers.common.VM_CompiledMethods;
import org.jikesrvm.VM_Constants;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.scheduler.VM_Processor;
import org.jikesrvm.scheduler.VM_Scheduler;
import org.jikesrvm.scheduler.VM_Thread;
import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.classloader.VM_Atom;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.memorymanagers.mminterface.VM_CollectorThread;
import org.jikesrvm.memorymanagers.mminterface.Selected;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

@Uninterruptible
public class Collection extends org.mmtk.vm.Collection implements Constants, VM_Constants {

  /****************************************************************************
   *
   * Class variables
   */

  /** The fully qualified name of the collector thread. */
  private static VM_Atom collectorThreadAtom;
  /** The string "run". */
  private static VM_Atom runAtom;

  /***********************************************************************
   *
   * Initialization
   */

  /**
   * Initialization that occurs at <i>build</i> time.  The values of
   * statics at the completion of this routine will be reflected in
   * the boot image.  Any objects referenced by those statics will be
   * transitively included in the boot image.
   *
   * This is called from MM_Interface.
   */
  @Interruptible
  public static void init() {
    collectorThreadAtom = VM_Atom.findOrCreateAsciiAtom("Lorg/jikesrvm/memorymanagers/mminterface/VM_CollectorThread;");
    runAtom = VM_Atom.findOrCreateAsciiAtom("run");
  }

  /**
   * Triggers a collection.
   *
   * @param why the reason why a collection was triggered.  0 to
   * <code>TRIGGER_REASONS - 1</code>.
   */
  @LogicallyUninterruptible
  public final void triggerCollection(int why) {
    triggerCollectionStatic(why);
  }

  /**
   * Joins a collection.
   */
  @LogicallyUninterruptible
  public final void joinCollection() {
    if (Options.verbose.getValue() >= 4) {
      VM.sysWriteln("Entered Collection.joinCollection().  Stack:");
      VM_Scheduler.dumpStack();
    }

    while (Plan.isCollectionTriggered()) {
      /* allow a gc thread to run */
      VM_Scheduler.yield();
    }
    checkForOutOfMemoryError(true);
  }

  /**
   * Triggers a collection.
   *
   * @param why the reason why a collection was triggered.  0 to
   * <code>TRIGGER_REASONS - 1</code>.
   */
  @LogicallyUninterruptible
  public static void triggerCollectionStatic(int why) {
    if (VM.VerifyAssertions) VM._assert((why >= 0) && (why < TRIGGER_REASONS));

    if (Options.verbose.getValue() >= 4) {
      VM.sysWriteln("Entered Collection.triggerCollection().  Stack:");
      VM_Scheduler.dumpStack();
    }

    checkForOutOfMemoryError(false);

    Plan.setCollectionTriggered();
    if (why == EXTERNAL_GC_TRIGGER) {
      if (Options.verbose.getValue() == 1 || Options.verbose.getValue() == 2)
        VM.sysWrite("[Forced GC]");
    } else {
      VM_Scheduler.getCurrentThread().reportCollectionAttempt();
    }

    VM_CollectorThread.collect(VM_CollectorThread.handshake, why);
    checkForOutOfMemoryError(true);

    if (Options.verbose.getValue() >= 4) {
      VM.sysWriteln("Leaving Collection.triggerCollection().");
    }
  }

  /**
   * Check if there is an out of memory error waiting.
   */
  @Inline
  @LogicallyUninterruptible
  private static void checkForOutOfMemoryError(boolean afterCollection) {
    VM_Thread myThread = VM_Scheduler.getCurrentThread();
    OutOfMemoryError oome = myThread.getOutOfMemoryError();
    if (oome != null && (!afterCollection || !myThread.physicalAllocationFailed())) {
      if (Options.verbose.getValue() >= 4) {
        VM.sysWriteln("Throwing OutOfMemoryError in Collection.triggerCollection().");
      }
      myThread.clearOutOfMemoryError();
      myThread.resetCollectionAttempts();
      throw oome;
    }
  }

  /**
   * The maximum number collection attempts across threads.
   */
  public int maximumCollectionAttempt() {
    int max = 1;
    for(int t=0; t <= VM_Scheduler.getThreadHighWatermark(); t++) {
      VM_Thread thread = VM_Scheduler.threads[t];
      if (thread != null) {
        int current = thread.getCollectionAttempt();
        if (current > max) max = current;
      }
    }
    return max + VM_CollectorThread.collectionAttemptBase;
  }

  /**
   * Report that the the physical allocation has succeeded.
   */
  public void reportAllocationSuccess() {
    VM_Thread myThread = VM_Scheduler.getCurrentThread();
    myThread.clearOutOfMemoryError();
    myThread.resetCollectionAttempts();
    myThread.clearPhysicalAllocationFailed();
  }

  /**
   * Report that a physical allocation has failed.
   */
  public void reportPhysicalAllocationFailed() {
    VM_Scheduler.getCurrentThread().setPhysicalAllocationFailed();
  }

  /**
   * Does the VM consider this an emergency allocation, where the normal
   * heap size rules can be ignored.
   */
  public boolean isEmergencyAllocation() {
    return VM_Scheduler.getCurrentThread().emergencyAllocation();
  }

  /**
   * Trigger an asynchronous collection, checking for memory
   * exhaustion first.
   */
  public final void triggerAsyncCollection(int why) {
    Plan.setCollectionTriggered();
    if (Options.verbose.getValue() >= 1) VM.sysWrite("[Async GC]");
    VM_CollectorThread.asyncCollect(VM_CollectorThread.handshake, why);
  }

  /**
   * Determine whether a collection cycle has fully completed (this is
   * used to ensure a GC is not in the process of completing, to
   * avoid, for example, an async GC being triggered on the switch
   * from GC to mutator thread before all GC threads have switched.
   *
   * @return True if GC is not in progress.
   */
  @Uninterruptible
  public final boolean noThreadsInGC() {
    return VM_CollectorThread.noThreadsInGC();
  }

  /**
   * Prepare a mutator for a collection.
   *
   * @param m the mutator to prepare
   */
  public final void prepareMutator(MutatorContext m) {
    /*
     * The collector threads of processors currently running threads
     * off in JNI-land cannot run.
     */
    VM_Processor vp = ((Selected.Mutator) m).getProcessor();
    int vpStatus = vp.vpStatus;
    if (vpStatus == VM_Processor.BLOCKED_IN_NATIVE) {

      /* processor & its running thread are blocked in C for this GC.
       Its stack needs to be scanned, starting from the "top" java
       frame, which has been saved in the running threads JNIEnv.  Put
       the saved frame pointer into the threads saved context regs,
       which is where the stack scan starts. */
      VM_Thread t = vp.activeThread;
      t.contextRegisters.setInnermost(Address.zero(), t.jniEnv.topJavaFP());
    }
  }

  /**
   * Prepare a collector for a collection.
   *
   * @param c the collector to prepare
   */
  public final void prepareCollector(CollectorContext c) {
    VM_Processor vp = ((Selected.Collector) c).getProcessor();
    int vpStatus = vp.vpStatus;
    if (VM.VerifyAssertions) VM._assert(vpStatus != VM_Processor.BLOCKED_IN_NATIVE);
    VM_Thread t = VM_Scheduler.getCurrentThread();
    Address fp = VM_Magic.getFramePointer();
    while (true) {
      Address caller_ip = VM_Magic.getReturnAddress(fp);
      Address caller_fp = VM_Magic.getCallerFramePointer(fp);
      if (VM_Magic.getCallerFramePointer(caller_fp).EQ(ArchitectureSpecific.VM_StackframeLayoutConstants.STACKFRAME_SENTINEL_FP))
        VM.sysFail("prepareMutator (participating): Could not locate VM_CollectorThread.run");
      int compiledMethodId = VM_Magic.getCompiledMethodID(caller_fp);
      VM_CompiledMethod compiledMethod = VM_CompiledMethods.getCompiledMethod(compiledMethodId);
      VM_Method method = compiledMethod.getMethod();
      VM_Atom cls = method.getDeclaringClass().getDescriptor();
      VM_Atom name = method.getName();
      if (name == runAtom && cls == collectorThreadAtom) {
        t.contextRegisters.setInnermost(caller_ip, caller_fp);
        break;
      }
      fp = caller_fp;
    }
  }

  /**
   * Rendezvous with all other processors, returning the rank
   * (that is, the order this processor arrived at the barrier).
   */
  public final int rendezvous(int where) {
    return VM_CollectorThread.gcBarrier.rendezvous(where);
  }

  /***********************************************************************
   *
   * Finalizers
   */

  /**
   * Schedule the finalizerThread, if there are objects to be
   * finalized and the finalizerThread is on its queue (ie. currently
   * idle).  Should be called at the end of GC after moveToFinalizable
   * has been called, and before mutators are allowed to run.
   */
  @Uninterruptible
  public static void scheduleFinalizerThread () {
    int finalizedCount = Finalizer.countToBeFinalized();
    if (finalizedCount > 0) {
      VM_Scheduler.scheduleFinalizer();
    }
  }
}
