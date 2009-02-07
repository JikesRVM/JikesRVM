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

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.VM;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.mm.mminterface.Selected;
import org.jikesrvm.mm.mminterface.CollectorThread;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.scheduler.Processor;
import org.jikesrvm.scheduler.RVMThread;
import org.jikesrvm.scheduler.Scheduler;
import org.mmtk.plan.CollectorContext;
import org.mmtk.plan.MutatorContext;
import org.mmtk.plan.Plan;
import org.mmtk.utility.options.Options;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Unpreemptible;
import org.vmmagic.unboxed.Address;

@Uninterruptible
public class Collection extends org.mmtk.vm.Collection implements org.mmtk.utility.Constants,
                                                                  org.jikesrvm.Constants {

  /****************************************************************************
   *
   * Class variables
   */

  /** The fully qualified name of the collector thread. */
  private static Atom collectorThreadAtom;
  /** The string "run". */
  private static Atom runAtom;

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
   * This is called from MemoryManager.
   */
  @Interruptible
  public static void init() {
    collectorThreadAtom = Atom.findOrCreateAsciiAtom("Lorg/jikesrvm/mm/mminterface/CollectorThread;");
    runAtom = Atom.findOrCreateAsciiAtom("run");
  }

  /**
   * Triggers a collection.
   *
   * @param why the reason why a collection was triggered.  0 to
   * <code>TRIGGER_REASONS - 1</code>.
   */
  @Unpreemptible("Becoming another thread interrupts the current thread, avoid preemption in the process")
  public final void triggerCollection(int why) {
    triggerCollectionStatic(why);
  }

  /**
   * Joins a collection.
   */
  @Unpreemptible("Becoming another thread interrupts the current thread, avoid preemption in the process")
  public final void joinCollection() {
    if (Options.verbose.getValue() >= 4) {
      VM.sysWriteln("Entered Collection.joinCollection().  Stack:");
      Scheduler.dumpStack();
    }

    while (Plan.isCollectionTriggered()) {
      /* allow a gc thread to run */
      Scheduler.yield();
    }
    checkForOutOfMemoryError(true);
  }

  /**
   * Triggers a collection.
   *
   * @param why the reason why a collection was triggered.  0 to
   * <code>TRIGGER_REASONS - 1</code>.
   */
  @Unpreemptible("Change state of thread possibly context switching if generating exception")
  public static void triggerCollectionStatic(int why) {
    if (VM.VerifyAssertions) VM._assert((why >= 0) && (why < TRIGGER_REASONS));

    if (Options.verbose.getValue() >= 4) {
      VM.sysWriteln("Entered Collection.triggerCollection().  Stack:");
      Scheduler.dumpStack();
    }

    checkForOutOfMemoryError(false);

    Plan.setCollectionTriggered();
    if (why == EXTERNAL_GC_TRIGGER) {
      if (Options.verbose.getValue() == 1 || Options.verbose.getValue() == 2)
        VM.sysWrite("[Forced GC]");
    } else if (why == INTERNAL_PHASE_GC_TRIGGER) {
      if (Options.verbose.getValue() == 1 || Options.verbose.getValue() == 2)
        VM.sysWrite("[Phase GC]");
    } else {
      Scheduler.getCurrentThread().reportCollectionAttempt();
    }

    CollectorThread.collect(CollectorThread.handshake, why);
    checkForOutOfMemoryError(true);

    if (Options.verbose.getValue() >= 4) {
      VM.sysWriteln("Leaving Collection.triggerCollection().");
    }
  }

  /**
   * Check if there is an out of memory error waiting.
   */
  @Inline
  @Unpreemptible("Exceptions may possibly cause yields")
  private static void checkForOutOfMemoryError(boolean afterCollection) {
    RVMThread myThread = Scheduler.getCurrentThread();
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
    for(int t=0; t <= Scheduler.getThreadHighWatermark(); t++) {
      RVMThread thread = Scheduler.threads[t];
      if (thread != null) {
        int current = thread.getCollectionAttempt();
        if (current > max) max = current;
      }
    }
    return max + CollectorThread.collectionAttemptBase;
  }

  /**
   * Report that the the physical allocation has succeeded.
   */
  public void reportAllocationSuccess() {
    RVMThread myThread = Scheduler.getCurrentThread();
    myThread.clearOutOfMemoryError();
    myThread.resetCollectionAttempts();
    myThread.clearPhysicalAllocationFailed();
  }

  /**
   * Report that a physical allocation has failed.
   */
  public void reportPhysicalAllocationFailed() {
    Scheduler.getCurrentThread().setPhysicalAllocationFailed();
  }

  /**
   * Does the VM consider this an emergency allocation, where the normal
   * heap size rules can be ignored.
   */
  public boolean isEmergencyAllocation() {
    return Scheduler.getCurrentThread().emergencyAllocation();
  }

  /**
   * Trigger an asynchronous collection, checking for memory
   * exhaustion first.
   */
  @Unpreemptible("Becoming another thread interrupts the current thread, avoid preemption in the process")
  public final void triggerAsyncCollection(int why) {
    Plan.setCollectionTriggered();
    if (Options.verbose.getValue() >= 1) {
      if (why == INTERNAL_PHASE_GC_TRIGGER) {
        VM.sysWrite("[Async-Phase GC]");
      } else {
        VM.sysWrite("[Async GC]");
      }
    }
    CollectorThread.asyncCollect(CollectorThread.handshake, why);
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
    return CollectorThread.noThreadsInGC();
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
    Processor vp = ((Selected.Mutator) m).getProcessor();
    int vpStatus = vp.vpStatus;
    if (vpStatus == Processor.BLOCKED_IN_NATIVE) {

      /* processor & its running thread are blocked in C for this GC.
       Its stack needs to be scanned, starting from the "top" java
       frame, which has been saved in the running threads JNIEnv.  Put
       the saved frame pointer into the threads saved context regs,
       which is where the stack scan starts. */
      RVMThread t = vp.activeThread;
      t.contextRegisters.setInnermost(Address.zero(), t.jniEnv.topJavaFP());
    }
  }

  /**
   * Prepare a collector for a collection.
   *
   * @param c the collector to prepare
   */
  public final void prepareCollector(CollectorContext c) {
    Processor vp = ((Selected.Collector) c).getProcessor();
    int vpStatus = vp.vpStatus;
    if (VM.VerifyAssertions) VM._assert(vpStatus != Processor.BLOCKED_IN_NATIVE);
    RVMThread t = Scheduler.getCurrentThread();
    Address fp = Magic.getFramePointer();
    while (true) {
      Address caller_ip = Magic.getReturnAddress(fp);
      Address caller_fp = Magic.getCallerFramePointer(fp);
      if (Magic.getCallerFramePointer(caller_fp).EQ(ArchitectureSpecific.StackframeLayoutConstants.STACKFRAME_SENTINEL_FP))
        VM.sysFail("prepareMutator (participating): Could not locate CollectorThread.run");
      int compiledMethodId = Magic.getCompiledMethodID(caller_fp);
      CompiledMethod compiledMethod = CompiledMethods.getCompiledMethod(compiledMethodId);
      RVMMethod method = compiledMethod.getMethod();
      Atom cls = method.getDeclaringClass().getDescriptor();
      Atom name = method.getName();
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
    return CollectorThread.gcBarrier.rendezvous(where);
  }

  /** @return The number of active collector threads */
  public final int activeGCThreads() {
    return CollectorThread.numCollectors();
  }

  /**
   * @return The ordinal ID of the running collector thread w.r.t.
   * the set of active collector threads (zero based)
   */
  public final int activeGCThreadOrdinal() {
    return Magic.threadAsCollectorThread(Scheduler.getCurrentThread()).getGCOrdinal() - CollectorThread.GC_ORDINAL_BASE;
  }

  /**
   * Request each mutator flush remembered sets. This method
   * will trigger the flush and then yield until all processors have
   * flushed.
   */
  public void requestMutatorFlush() {
    Scheduler.requestMutatorFlush();
  }

  /**
   * Possibly yield the current concurrent collector thread. Return
   * true if yielded.
   */
  @Inline
  @Unpreemptible("Becoming another thread interrupts the current thread, avoid preemption in the process")
  public boolean yieldpoint() {
    if (Processor.getCurrentProcessor().takeYieldpoint != 0) {
      RVMThread.yieldpointFromBackedge();
      return true;
    }
    return false;
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
  public static void scheduleFinalizerThread() {
    int finalizedCount = FinalizableProcessor.countReadyForFinalize();
    if (finalizedCount > 0) {
      Scheduler.scheduleFinalizer();
    }
  }
}
