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

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.TraceStep;
import org.mmtk.utility.scan.Scan;
import org.mmtk.utility.Constants;

import org.jikesrvm.memorymanagers.mminterface.MM_Constants;
import org.jikesrvm.memorymanagers.mminterface.VM_CollectorThread;
import org.jikesrvm.VM;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.scheduler.VM_Scheduler;
import org.jikesrvm.scheduler.VM_Thread;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

@Uninterruptible public class Scanning extends org.mmtk.vm.Scanning implements Constants {
  /****************************************************************************
   *
   * Class variables
   */
  private static final boolean TRACE_PRECOPY = false; // DEBUG

  /** Counter to track index into thread table for root tracing.  */
  private static SynchronizedCounter threadCounter = new SynchronizedCounter();

  /**
   * Delegated scanning of a object, processing each pointer field
   * encountered. <b>Jikes RVM never delegates, so this is never
   * executed</b>.
   *
   * @param object The object to be scanned.
   */
  @Inline
  @Uninterruptible
  public final void scanObject(TraceStep trace, ObjectReference object) {
    // Never reached
    if (VM.VerifyAssertions) VM._assert(false);
  }

  /**
   * Delegated precopying of a object's children, processing each pointer field
   * encountered. <b>Jikes RVM never delegates, so this is never
   * executed</b>.
   *
   * @param object The object to be scanned.
   */
  @Inline
  @Uninterruptible
  public final void precopyChildren(TraceLocal trace, ObjectReference object) {
    // Never reached
    if (VM.VerifyAssertions) VM._assert(false);
  }

  /**
   * Prepares for using the <code>computeAllRoots</code> method.  The
   * thread counter allows multiple GC threads to co-operatively
   * iterate through the thread data structure (if load balancing
   * parallel GC threads were not important, the thread counter could
   * simply be replaced by a for loop).
   */
  public final void resetThreadCounter() {
    threadCounter.reset();
  }

  /**
   * Pre-copy all potentially movable instances used in the course of
   * GC.  This includes the thread objects representing the GC threads
   * themselves.  It is crucial that these instances are forwarded
   * <i>prior</i> to the GC proper.  Since these instances <i>are
   * not</i> enqueued for scanning, it is important that when roots
   * are computed the same instances are explicitly scanned and
   * included in the set of roots.  The existence of this method
   * allows the actions of calculating roots and forwarding GC
   * instances to be decoupled.
   *
   * The thread table is scanned in parallel by each processor, by striding
   * through the table at a gap of chunkSize*numProcs.  Feel free to adjust
   * chunkSize if you want to tune a parallel collector.
   *
   * Explicitly no-inlined to prevent over-inlining of collectionPhase.
   *
   * TODO Experiment with specialization to remove virtual dispatch ?
   */
  @NoInline
  public final void preCopyGCInstances(TraceLocal trace) {
    int chunkSize = 2;
    int threadIndex, start, end, stride;
    VM_CollectorThread ct;

    stride = chunkSize * VM_CollectorThread.numCollectors();
    ct = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());
    start = (ct.getGCOrdinal() - 1) * chunkSize;

    int numThreads = VM_Scheduler.threadHighWatermark+1;
    if (TRACE_PRECOPY)
      VM.sysWriteln(ct.getGCOrdinal()," preCopying ",numThreads," threads");

    ObjectReference threadTable = ObjectReference.fromObject(VM_Scheduler.threads);
    while (start < numThreads) {
      end = start + chunkSize;
      if (end > numThreads)
        end = numThreads;      // End of the table - partial chunk
      if (TRACE_PRECOPY) {
        VM.sysWriteln(ct.getGCOrdinal()," Chunk start",start);
        VM.sysWriteln(ct.getGCOrdinal()," Chunk end  ",end);
      }
      for (threadIndex = start; threadIndex < end; threadIndex++) {
        VM_Thread thread = VM_Scheduler.threads[threadIndex];
        if (thread != null) {
          /* Copy the thread object - use address arithmetic to get the address
           * of the array entry */
          if (TRACE_PRECOPY) {
            VM.sysWriteln(ct.getGCOrdinal()," Forwarding thread ",threadIndex);
            VM.sysWrite(ct.getGCOrdinal()," Old address ");
            VM.sysWriteln(ObjectReference.fromObject(thread).toAddress());
          }
          Address threadTableSlot = threadTable.toAddress().plus(threadIndex<<LOG_BYTES_IN_ADDRESS);
          if (VM.VerifyAssertions)
            VM._assert(ObjectReference.fromObject(thread).toAddress().EQ(
                threadTableSlot.loadObjectReference().toAddress()),
            "Thread table address arithmetic is wrong!");
          trace.precopyObjectLocation(threadTableSlot);
          thread = VM_Scheduler.threads[threadIndex];  // reload  it - it just moved!
          if (TRACE_PRECOPY) {
            VM.sysWrite(ct.getGCOrdinal()," New address ");
            VM.sysWriteln(ObjectReference.fromObject(thread).toAddress());
          }
          precopyChildren(trace,thread);
          precopyChildren(trace,thread.contextRegisters);
          precopyChildren(trace,thread.hardwareExceptionRegisters);
          if (thread.jniEnv != null) {
            // Right now, jniEnv are Java-visible objects (not C-visible)
            // if (VM.VerifyAssertions)
            //   VM._assert(Plan.willNotMove(VM_Magic.objectAsAddress(thread.jniEnv)));
            precopyChildren(trace,thread.jniEnv);
          }
        }
      } // end of for loop
      start = start + stride;
    }
  }


  /**
   * Enumerator the pointers in an object, calling back to a given plan
   * for each pointer encountered. <i>NOTE</i> that only the "real"
   * pointer fields are enumerated, not the TIB.
   *
   * @param trace The trace object to use to report precopy objects.
   * @param object The object to be scanned.
   */
  @Inline
  @Uninterruptible
  private static void precopyChildren(TraceLocal trace, Object object) {
    Scan.precopyChildren(trace,ObjectReference.fromObject(object));
  }

 /**
   * Computes all roots.  This method establishes all roots for
   * collection and places them in the root values, root locations and
   * interior root locations queues.  This method should not have side
   * effects (such as copying or forwarding of objects).  There are a
   * number of important preconditions:
   *
   * <ul>
   * <li> All objects used in the course of GC (such as the GC thread
   * objects) need to be "pre-copied" prior to calling this method.
   * <li> The <code>threadCounter</code> must be reset so that load
   * balancing parallel GC can share the work of scanning threads.
   * </ul>
   *
   * TODO rewrite to avoid the per-thread synchronization, like precopy.
   *
   * @param trace The trace object to use to report root locations.
   */
  public final void computeAllRoots(TraceLocal trace) {
    boolean processCodeLocations = MM_Constants.MOVES_OBJECTS;
     /* scan statics */
    ScanStatics.scanStatics(trace);

    /* scan all threads */
    while (true) {
      int threadIndex = threadCounter.increment();
      if (threadIndex > VM_Scheduler.threadHighWatermark) break;

      VM_Thread thread = VM_Scheduler.threads[threadIndex];
      if (thread == null) continue;

      /* scan the thread (stack etc.) */
      ScanThread.scanThread(thread, trace, processCodeLocations);

      /* identify this thread as a root */
      trace.addRootLocation(VM_Magic.objectAsAddress(VM_Scheduler.threads).plus(threadIndex<<LOG_BYTES_IN_ADDRESS));
    }
    /* flush out any remset entries generated during the above activities */
    ActivePlan.flushRememberedSets();
    VM_CollectorThread.gcBarrier.rendezvous(4200);
  }

  /**
   * Compute all roots out of the VM's boot image (if any).  This method is a no-op
   * in the case where the VM does not maintain an MMTk-visible Java space.   However,
   * when the VM does maintain a space (such as a boot image) which is visible to MMTk,
   * that space could either be scanned by MMTk as part of its transitive closure over
   * the whole heap, or as a (considerable) performance optimization, MMTk could avoid
   * scanning the space if it is aware of all pointers out of that space.  This method
   * is used to establish the root set out of the scannable space in the case where
   * such a space exists.
   *
   * @param trace The trace object to use to report root locations.
   */
  public void computeBootImageRoots(TraceLocal trace) {
    ScanBootImage.scanBootImage(trace);
  }
}
