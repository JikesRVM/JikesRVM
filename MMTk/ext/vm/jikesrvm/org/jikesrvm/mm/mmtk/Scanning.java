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

import org.jikesrvm.VM;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.jni.JNIEnvironment;
import org.jikesrvm.jni.JNIGlobalRefTable;
import org.jikesrvm.mm.mminterface.AlignmentEncoding;
import org.jikesrvm.mm.mminterface.HandInlinedScanning;
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
  @Override
  @Inline
  public void scanObject(TransitiveClosure trace, ObjectReference object) {
    if (HandInlinedScanning.ENABLED) {
      int tibCode = AlignmentEncoding.getTibCode(object);
      HandInlinedScanning.scanObject(tibCode, object.toObject(), trace);
    } else {
      SpecializedScanMethod.fallback(object.toObject(), trace);
    }
  }

  @Override
  @Inline
  public void specializedScanObject(int id, TransitiveClosure trace, ObjectReference object) {
    if (HandInlinedScanning.ENABLED) {
      int tibCode = AlignmentEncoding.getTibCode(object);
      HandInlinedScanning.scanObject(tibCode, id, object.toObject(), trace);
    } else {
      if (SpecializedScanMethod.ENABLED) {
        SpecializedScanMethod.invoke(id, object.toObject(), trace);
      } else {
        SpecializedScanMethod.fallback(object.toObject(), trace);
      }
    }
  }

  @Override
  public void resetThreadCounter() {
    threadCounter.reset();
  }

  @Override
  public void notifyInitialThreadScanComplete(boolean partialScan) {
    if (!partialScan)
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
  @Override
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
  @Override
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
   * {@inheritDoc}
   */
  @Override
  public void computeThreadRoots(TraceLocal trace) {
    computeThreadRoots(trace, false);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void computeNewThreadRoots(TraceLocal trace) {
    computeThreadRoots(trace, true);
  }

  /**
   * Compute roots pointed to by threads.
   *
   * @param trace The trace to use for computing roots.
   * @param newRootsSufficient  True if it sufficient for this method to only
   * compute those roots that are new since the previous stack scan.   If false
   * then all roots must be computed (both new and preexisting).
   */
  private void computeThreadRoots(TraceLocal trace, boolean newRootsSufficient) {
    boolean processCodeLocations = MemoryManagerConstants.MOVES_CODE;

    /* scan all threads */
    while (true) {
      int threadIndex = threadCounter.increment();
      if (threadIndex > RVMThread.numThreads) break;

      RVMThread thread = RVMThread.threads[threadIndex];
      if (thread == null || thread.isCollectorThread()) continue;

      /* scan the thread (stack etc.) */
      ScanThread.scanThread(thread, trace, processCodeLocations, newRootsSufficient);
    }

    /* flush out any remset entries generated during the above activities */
    Selected.Mutator.get().flushRememberedSets();
  }

  @Override
  public void computeBootImageRoots(TraceLocal trace) {
    ScanBootImage.scanBootImage(trace);
  }

  @Override
  public boolean supportsReturnBarrier() {
    return VM.BuildForIA32;
  }
}
