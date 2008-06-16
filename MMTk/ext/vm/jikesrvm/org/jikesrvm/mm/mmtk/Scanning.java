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
import org.mmtk.plan.TransitiveClosure;
import org.mmtk.utility.Constants;

import org.jikesrvm.jni.VM_JNIEnvironment;
import org.jikesrvm.memorymanagers.mminterface.MM_Constants;
import org.jikesrvm.memorymanagers.mminterface.Selected;
import org.jikesrvm.memorymanagers.mminterface.VM_CollectorThread;
import org.jikesrvm.memorymanagers.mminterface.VM_SpecializedScanMethod;
import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.objectmodel.VM_ObjectModel;
import org.jikesrvm.runtime.VM_Entrypoints;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.scheduler.VM_Processor;
import org.jikesrvm.scheduler.VM_Scheduler;
import org.jikesrvm.scheduler.RVMThread;
import org.jikesrvm.scheduler.greenthreads.VM_GreenScheduler;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

@Uninterruptible
public final class Scanning extends org.mmtk.vm.Scanning implements Constants {
  /****************************************************************************
   *
   * Class variables
   */
  private static final boolean TRACE_PRECOPY = false; // DEBUG

  /** Counter to track index into thread table for root tracing.  */
  private static final SynchronizedCounter threadCounter = new SynchronizedCounter();

  /** Status flag used to determine if stacks were scanned in this collection increment */
  private static boolean threadStacksScanned = false;

  /**
   * Were thread stacks scanned in this collection increment.
   */
  public static boolean threadStacksScanned() {
    return threadStacksScanned;
  }

  /**
   * Clear the flag that indicates thread stacks have been scanned.
   */
  public static void clearThreadStacksScanned() {
    threadStacksScanned = false;
  }

  /**
   * Scanning of a object, processing each pointer field encountered.
   *
   * @param trace The closure being used.
   * @param object The object to be scanned.
   */
  @Inline
  public void scanObject(TransitiveClosure trace, ObjectReference object) {
    VM_SpecializedScanMethod.fallback(object.toObject(), trace);
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
    if (VM_SpecializedScanMethod.ENABLED) {
      VM_SpecializedScanMethod.invoke(id, object.toObject(), trace);
    } else {
      VM_SpecializedScanMethod.fallback(object.toObject(), trace);
    }
  }



  /**
   * Precopying of a object's fields, processing each pointer field encountered.
   *
   * @param trace The trace being used.
   * @param object The object to be scanned.
   */
  @Inline
  public void precopyChildren(TraceLocal trace, ObjectReference object) {
    RVMType type = VM_ObjectModel.getObjectType(object.toObject());
    if (type.isClassType()) {
      RVMClass klass = type.asClass();
      int[] offsets = klass.getReferenceOffsets();
      for(int i=0; i < offsets.length; i++) {
        trace.processPrecopyEdge(object.toAddress().plus(offsets[i]), false);
      }
    } else if (type.isArrayType() && type.asArray().getElementType().isReferenceType()) {
      for(int i=0; i < VM_ObjectModel.getArrayLength(object.toObject()); i++) {
        trace.processPrecopyEdge(object.toAddress().plus(i << LOG_BYTES_IN_ADDRESS), false);
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
  public void preCopyGCInstances(TraceLocal trace) {
    int chunkSize = 2;
    int threadIndex, start, end, stride;
    VM_CollectorThread ct;

    stride = chunkSize * VM_CollectorThread.numCollectors();
    ct = VM_Magic.threadAsCollectorThread(VM_Scheduler.getCurrentThread());
    start = (ct.getGCOrdinal() - 1) * chunkSize;

    int numThreads = VM_Scheduler.getThreadHighWatermark()+1;
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
        RVMThread thread = VM_Scheduler.threads[threadIndex];
        if (thread != null) {
          /* Copy the thread object - use address arithmetic to get the address
           * of the array entry */
          if (TRACE_PRECOPY) {
            VM.sysWriteln(ct.getGCOrdinal()," Forwarding thread ",threadIndex);
            VM.sysWrite(ct.getGCOrdinal()," Old address ");
            VM.sysWriteln(ObjectReference.fromObject(thread).toAddress());
          }
          Address threadTableSlot = threadTable.toAddress().plus(threadIndex<<LOG_BYTES_IN_ADDRESS);
          if (VM.VerifyAssertions) {
            Address a = ObjectReference.fromObject(thread).toAddress();
            Address b = Selected.Collector.get().loadObjectReference(threadTableSlot).toAddress();
            VM._assert(a.EQ(b), "Thread table address arithmetic is wrong!");
          }
          trace.processPrecopyEdge(threadTableSlot, false);
          thread = VM_Scheduler.threads[threadIndex];  // reload  it - it just moved!
          if (TRACE_PRECOPY) {
            VM.sysWrite(ct.getGCOrdinal()," New address ");
            VM.sysWriteln(ObjectReference.fromObject(thread).toAddress());
          }
          precopyChildren(trace, ObjectReference.fromObject(thread));

          /* Registers */
          trace.processPrecopyEdge(VM_Magic.objectAsAddress(thread).plus(VM_Entrypoints.threadContextRegistersField.getOffset()), true);

          trace.processPrecopyEdge(VM_Magic.objectAsAddress(thread).plus(VM_Entrypoints.threadExceptionRegistersField.getOffset()), true);

          if (thread.getJNIEnv() != null) {
            // Right now, jniEnv are Java-visible objects (not C-visible)
            // if (VM.VerifyAssertions)
            //   VM._assert(Plan.willNotMove(VM_Magic.objectAsAddress(thread.jniEnv)));
            trace.processPrecopyEdge(VM_Magic.objectAsAddress(thread).plus(VM_Entrypoints.jniEnvField.getOffset()), true);
            trace.processPrecopyEdge(VM_Magic.objectAsAddress(thread.getJNIEnv()).plus(VM_Entrypoints.JNIRefsField.getOffset()), true);
            trace.processPrecopyEdge(VM_Magic.objectAsAddress(thread.getJNIEnv()).plus(VM_Entrypoints.JNIEnvSavedPRField.getOffset()), true);
            trace.processPrecopyEdge(VM_Magic.objectAsAddress(thread.getJNIEnv()).plus(VM_Entrypoints.JNIPendingExceptionField.getOffset()), true);
          }
        }
      } // end of for loop
      start = start + stride;
    }
  }

  /**
   * Computes static roots.  This method establishes all such roots for
   * collection and places them in the root locations queue.  This method
   * should not have side effects (such as copying or forwarding of
   * objects).  There are a number of important preconditions:
   *
   * <ul>
   * <li> All objects used in the course of GC (such as the GC thread
   * objects) need to be "pre-copied" prior to calling this method.
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
   * <li> All objects used in the course of GC (such as the GC thread
   * objects) need to be "pre-copied" prior to calling this method.
   * <li> The <code>threadCounter</code> must be reset so that load
   * balancing parallel GC can share the work of scanning threads.
   * </ul>
   *
   * @param trace The trace to use for computing roots.
   */
  public void computeGlobalRoots(TraceLocal trace) {
    /* scan jni functions */
    VM_CollectorThread ct = VM_Magic.threadAsCollectorThread(VM_Scheduler.getCurrentThread());
    Address jniFunctions = VM_Magic.objectAsAddress(VM_JNIEnvironment.JNIFunctions);
    int threads = VM_CollectorThread.numCollectors();
    int size = VM_JNIEnvironment.JNIFunctions.length();
    int chunkSize = size / threads;
    int start = (ct.getGCOrdinal() - 1) * chunkSize;
    int end = (ct.getGCOrdinal() == threads) ? size : ct.getGCOrdinal() * chunkSize;

    for(int i=start; i < end; i++) {
      trace.processRootEdge(jniFunctions.plus(i << LOG_BYTES_IN_ADDRESS), true);
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
   * <li> All objects used in the course of GC (such as the GC thread
   * objects) need to be "pre-copied" prior to calling this method.
   * <li> The <code>threadCounter</code> must be reset so that load
   * balancing parallel GC can share the work of scanning threads.
   * </ul>
   *
   * TODO rewrite to avoid the per-thread synchronization, like precopy.
   *
   * @param trace The trace to use for computing roots.
   */
  public void computeThreadRoots(TraceLocal trace) {
    boolean processCodeLocations = MM_Constants.MOVES_CODE;

    /* Set status flag */
    threadStacksScanned = true;

    /* scan (small) set of processor roots */
    VM_CollectorThread ct = VM_Magic.threadAsCollectorThread(VM_Scheduler.getCurrentThread());
    if (ct.getGCOrdinal() == 1) {
      Address processors = VM_Magic.objectAsAddress(VM_GreenScheduler.processors);
      for(int i = VM_Scheduler.getFirstProcessorId(); i < VM_Scheduler.getLastProcessorId(); i++) {
        trace.reportDelayedRootEdge(processors.plus(i << LOG_BYTES_IN_ADDRESS));
        VM_Processor processorObject = VM_Scheduler.getProcessor(i);
        Address processorAddress = VM_Magic.objectAsAddress(processorObject);
        trace.reportDelayedRootEdge(processorAddress.plus(VM_Entrypoints.activeThreadField.getOffset()));
      }
    }

    /* scan all threads */
    while (true) {
      int threadIndex = threadCounter.increment();
      if (threadIndex > VM_Scheduler.getThreadHighWatermark()) break;

      RVMThread thread = VM_Scheduler.threads[threadIndex];
      if (thread == null) continue;

      /* scan the thread (stack etc.) */
      ScanThread.scanThread(thread, trace, processCodeLocations);

      /* identify this thread as a root */
      trace.processRootEdge(VM_Magic.objectAsAddress(VM_Scheduler.threads).plus(threadIndex<<LOG_BYTES_IN_ADDRESS), false);
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
