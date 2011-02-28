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
import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.TransitiveClosure;
import org.mmtk.utility.Constants;

import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.jni.JNIEnvironment;
import org.jikesrvm.jni.JNIGlobalRefTable;
import org.jikesrvm.mm.mminterface.AlignmentEncoding;
import org.jikesrvm.mm.mminterface.HandInlignedScanning;
import org.jikesrvm.mm.mminterface.Selected;
import org.jikesrvm.mm.mminterface.MemoryManagerConstants;
import org.jikesrvm.mm.mminterface.SpecializedScanMethod;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.scheduler.RVMThread;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

@Uninterruptible
public final class Scanning extends org.mmtk.vm.Scanning implements Constants {
  /****************************************************************************
   *
   * Class variables
   */
  /** Counter to track index into thread table for root tracing.  */
  private static final SynchronizedCounter threadCounter = new SynchronizedCounter();

  /**
   * Scanning of a object, processing each pointer field encountered.
   *
   * @param trace The closure being used.
   * @param object The object to be scanned.
   */
  @Inline
  public void scanObject(TransitiveClosure trace, ObjectReference object) {
    if (HandInlignedScanning.ENABLED) {
      int tibCode = AlignmentEncoding.getTibCode(object);
      HandInlignedScanning.scanObject(tibCode, object.toObject(), trace);
    } else {
      SpecializedScanMethod.fallback(object.toObject(), trace);
    }
  }

  /**
   * Invoke a specialized scan method. Note that these methods must have been allocated
   * explicitly through Plan and PlanConstraints.
   *
   * @param id The specialized method id
   * @param trace The trace the method has been specialized for
   * @param object The object to be scanned
   */
  @Inline
  public void specializedScanObject(int id, TransitiveClosure trace, ObjectReference object) {
    if (HandInlignedScanning.ENABLED) {
      int tibCode = AlignmentEncoding.getTibCode(object);
      HandInlignedScanning.scanObject(tibCode, id, object.toObject(), trace);
    } else {
      if (SpecializedScanMethod.ENABLED) {
        SpecializedScanMethod.invoke(id, object.toObject(), trace);
      } else {
        SpecializedScanMethod.fallback(object.toObject(), trace);
      }
    }
  }

  /**
   * Prepares for using the <code>computeAllRoots</code> method.  The
   * thread counter allows multiple GC threads to co-operatively
   * iterate through the thread data structure (if load balancing
   * parallel GC threads were not important, the thread counter could
   * simply be replaced by a for loop).
   */
  public void resetThreadCounter() {
    threadCounter.reset();
  }

  /**
   * Called the first time during a collection that thread's stacks
   * have been scanned. This can be used (for example) to clean up
   * obsolete compiled methods that are no longer being executed.
   */
  public void notifyInitialThreadScanComplete() {
    CompiledMethods.snipObsoleteCompiledMethods();
    /* flush out any remset entries generated during the above activities */
    Selected.Mutator.get().flushRememberedSets();
  }

  /**
   * Computes static roots.  This method establishes all such roots for
   * collection and places them in the root locations queue.  This method
   * should not have side effects (such as copying or forwarding of
   * objects).  There are a number of important preconditions:
   *
   * <ul>
   * <li> The <code>threadCounter</code> must be reset so that load
   * balancing parallel GC can share the work of scanning threads.
   * </ul>
   *
   * @param trace The trace to use for computing roots.
   */
  public void computeStaticRoots(TraceLocal trace) {
    /* scan statics */
    ScanStatics.scanStatics(trace);
  }

  /**
   * Computes global roots.  This method establishes all such roots for
   * collection and places them in the root locations queue.  This method
   * should not have side effects (such as copying or forwarding of
   * objects).  There are a number of important preconditions:
   *
   * <ul>
   * <li> The <code>threadCounter</code> must be reset so that load
   * balancing parallel GC can share the work of scanning threads.
   * </ul>
   *
   * @param trace The trace to use for computing roots.
   */
  public void computeGlobalRoots(TraceLocal trace) {
    /* scan jni functions */
    CollectorContext cc = RVMThread.getCurrentThread().getCollectorContext();
    Address jniFunctions = Magic.objectAsAddress(JNIEnvironment.JNIFunctions);
    int threads = cc.parallelWorkerCount();
    int size = JNIEnvironment.JNIFunctions.length();
    int chunkSize = size / threads;
    int start = cc.parallelWorkerOrdinal() * chunkSize;
    int end = (cc.parallelWorkerOrdinal()+1 == threads) ? size : threads * chunkSize;

    for(int i=start; i < end; i++) {
      trace.processRootEdge(jniFunctions.plus(i << LOG_BYTES_IN_ADDRESS), true);
    }

    Address linkageTriplets = Magic.objectAsAddress(JNIEnvironment.LinkageTriplets);
    if (linkageTriplets != null) {
      for(int i=start; i < end; i++) {
        trace.processRootEdge(linkageTriplets.plus(i << LOG_BYTES_IN_ADDRESS), true);
      }
    }

    /* scan jni global refs */
    Address jniGlobalRefs = Magic.objectAsAddress(JNIGlobalRefTable.JNIGlobalRefs);
    size = JNIGlobalRefTable.JNIGlobalRefs.length();
    chunkSize = size / threads;
    start = cc.parallelWorkerOrdinal() * chunkSize;
    end = (cc.parallelWorkerOrdinal()+1 == threads) ? size : threads * chunkSize;

    for(int i=start; i < end; i++) {
      trace.processRootEdge(jniGlobalRefs.plus(i << LOG_BYTES_IN_ADDRESS), true);
    }
  }

  /**
   * Computes roots pointed to by threads, their associated registers
   * and stacks.  This method places these roots in the root values,
   * root locations and interior root locations queues.  This method
   * should not have side effects (such as copying or forwarding of
   * objects).  There are a number of important preconditions:
   *
   * <ul>
   * <li> The <code>threadCounter</code> must be reset so that load
   * balancing parallel GC can share the work of scanning threads.
   * </ul>
   *
   * TODO try to rewrite using chunking to avoid the per-thread synchronization?
   *
   * @param trace The trace to use for computing roots.
   */
  public void computeThreadRoots(TraceLocal trace) {
    boolean processCodeLocations = MemoryManagerConstants.MOVES_CODE;

    /* scan all threads */
    while (true) {
      int threadIndex = threadCounter.increment();
      if (threadIndex > RVMThread.numThreads) break;

      RVMThread thread = RVMThread.threads[threadIndex];
      if (thread == null || thread.isCollectorThread()) continue;

      /* scan the thread (stack etc.) */
      ScanThread.scanThread(thread, trace, processCodeLocations);
    }

    /* flush out any remset entries generated during the above activities */
    Selected.Mutator.get().flushRememberedSets();
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

