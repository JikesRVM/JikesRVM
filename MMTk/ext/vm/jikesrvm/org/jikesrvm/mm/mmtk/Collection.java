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
package org.jikesrvm.mm.mmtk;

import org.mmtk.plan.CollectorContext;
import org.mmtk.plan.MutatorContext;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.VM;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.mm.mminterface.Selected;
import org.jikesrvm.mm.mminterface.CollectorThread;
import org.jikesrvm.runtime.SysCall;
import org.jikesrvm.scheduler.RVMThread;
import org.jikesrvm.scheduler.FinalizerThread;

import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.UninterruptibleNoWarn;
import org.vmmagic.pragma.Unpreemptible;
import org.vmmagic.unboxed.Address;

@Uninterruptible
public class Collection extends org.mmtk.vm.Collection implements org.mmtk.utility.Constants,
                                                                  org.jikesrvm.Constants {

  /****************************************************************************
   *
   * Class variables
   */

  /**
   * Spawn a thread to execute the supplied collector context.
   */
  @Interruptible
  public void spawnCollectorContext(CollectorContext context) {
    byte[] stack = MemoryManager.newStack(ArchitectureSpecific.StackframeLayoutConstants.STACK_SIZE_COLLECTOR);
    CollectorThread t = new CollectorThread(stack, context);
    t.start();
  }

  /**
   * @return The default number of collector threads to use.
   */
  public int getDefaultThreads() {
    return SysCall.sysCall.sysNumProcessors();
  }

  /**
   * @return The number of active threads.
   *
   */
  public int getActiveThreads() {
    return RVMThread.getNumActiveThreads() - RVMThread.getNumActiveDaemons();
  }

  /**
   * Block for the garbage collector.
   */
  @Unpreemptible
  public void blockForGC() {
    RVMThread t=RVMThread.getCurrentThread();
    t.assertAcceptableStates(RVMThread.IN_JAVA, RVMThread.IN_JAVA_TO_BLOCK);
    RVMThread.observeExecStatusAtSTW(t.getExecStatus());
    RVMThread.getCurrentThread().block(RVMThread.gcBlockAdapter);
  }

  /***********************************************************************
   *
   * Initialization
   */

  /**
   * Fail with an out of memory error.
   */
  @UninterruptibleNoWarn
  public void outOfMemory() {
    throw RVMThread.getOutOfMemoryError();
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
    RVMThread t = ((Selected.Mutator) m).getThread();
    t.monitor().lockNoHandshake();
    // are these the only unexpected states?
    t.assertUnacceptableStates(RVMThread.IN_JNI,RVMThread.IN_NATIVE);
    int execStatus = t.getExecStatus();
    // these next assertions are not redundant given the ability of the
    // states to change asynchronously, even when we're holding the lock, since
    // the thread may change its own state.  of course that shouldn't happen,
    // but having more assertions never hurts...
    if (VM.VerifyAssertions) VM._assert(execStatus != RVMThread.IN_JNI);
    if (VM.VerifyAssertions) VM._assert(execStatus != RVMThread.IN_NATIVE);
    if (execStatus == RVMThread.BLOCKED_IN_JNI) {
      if (false) {
        VM.sysWriteln("for thread #",t.getThreadSlot()," setting up JNI stack scan");
        VM.sysWriteln("thread #",t.getThreadSlot()," has top java fp = ",t.getJNIEnv().topJavaFP());
      }

      /* thread is blocked in C for this GC.
       Its stack needs to be scanned, starting from the "top" java
       frame, which has been saved in the running threads JNIEnv.  Put
       the saved frame pointer into the threads saved context regs,
       which is where the stack scan starts. */
      t.contextRegisters.setInnermost(Address.zero(), t.getJNIEnv().topJavaFP());
    }
    t.monitor().unlock();
  }

  /**
   * Stop all mutator threads. This is current intended to be run by a single thread.
   *
   * Fixpoint until there are no threads that we haven't blocked. Fixpoint is needed to
   * catch the (unlikely) case that a thread spawns another thread while we are waiting.
   */
  @Unpreemptible
  public void stopAllMutators() {
    RVMThread.blockAllMutatorsForGC();
  }

  /**
   * Resume all mutators blocked for GC.
   */
  @Unpreemptible
  public void resumeAllMutators() {
    RVMThread.unblockAllMutatorsForGC();
  }

  private static RVMThread.SoftHandshakeVisitor mutatorFlushVisitor =
    new RVMThread.SoftHandshakeVisitor() {
      @Uninterruptible
      public boolean checkAndSignal(RVMThread t) {
        t.flushRequested = true;
        return true;
      }
      @Uninterruptible
      public void notifyStuckInNative(RVMThread t) {
        t.flush();
        t.flushRequested = false;
      }
      @Uninterruptible
      public boolean includeThread(RVMThread t) {
        return !t.isCollectorThread();
      }
    };

  /**
   * Request each mutator flush remembered sets. This method
   * will trigger the flush and then yield until all processors have
   * flushed.
   */
  @UninterruptibleNoWarn("This method is really unpreemptible, since it involves blocking")
  public void requestMutatorFlush() {
    Selected.Mutator.get().flush();
    RVMThread.softHandshake(mutatorFlushVisitor);
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
      FinalizerThread.schedule();
    }
  }
}

