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
package org.mmtk.harness.vm;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

import org.mmtk.harness.Harness;
import org.mmtk.harness.Mutator;
import org.mmtk.harness.Mutators;
import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.lang.runtime.AllocationSite;
import org.mmtk.harness.lang.runtime.ObjectValue;
import org.mmtk.harness.lang.runtime.StackFrame;
import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.TransitiveClosure;

import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.*;

/**
 *
 *
 */
@Uninterruptible
public class Scanning extends org.mmtk.vm.Scanning {
  public static final int THREAD_ITERATOR_TABLE_ENTRIES = 2048;

  /**
   * Table of thread iterator objects (to simulate JikesRVM thread iterator objects)
   */
  private static volatile ObjectValue threadIteratorTable = null;

  /**
   * Initialize the thread iterator table
   * @param m Calling mutator
   */
  public static void initThreadIteratorTable(Mutator m) {
    if (threadIteratorTable == null) {
      threadIteratorTable = new ObjectValue(m.allocThreadIteratorTable());
    }
  }

  /**
   * @param m Mutator
   * @param obj The iterator object
   */
  public static void setThreadIteratorObject(Mutator m, ObjectReference obj) {
    m.storeReferenceField(threadIteratorTable.getObjectValue(), m.getContext().getId() % THREAD_ITERATOR_TABLE_ENTRIES, obj);
  }

  /**
   * Internal harness method to get all non-stack roots
   * @return The set of roots
   */
  public static Set<ObjectValue> getRoots() {
    if (threadIteratorTable == null)
      return Collections.emptySet();
    return new HashSet<ObjectValue>(Arrays.asList(threadIteratorTable));
  }

  /**
   * Delegated scanning of a object, processing each pointer field
   * encountered.
   * @param trace The trace
   *
   * @param object The object to be scanned.
   */
  @Override
  public void scanObject(TransitiveClosure trace, ObjectReference object) {
    Trace.trace(Item.SCAN, "Scanning object %s", ObjectModel.addressAndSpaceString(object));
    int refs = ObjectModel.getRefs(object);

    Address first = object.toAddress().plus(ObjectModel.REFS_OFFSET);
    for (int i=0; i < refs; i++) {
      Trace.trace(Item.SCAN, "  Edge %s", first.plus(i << LOG_BYTES_IN_ADDRESS).loadObjectReference());
      trace.processEdge(object, first.plus(i << LOG_BYTES_IN_ADDRESS));
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
  @Override
  public void specializedScanObject(int id, TransitiveClosure trace, ObjectReference object) {
    scanObject(trace, object);
  }

  /**
   * Delegated precopying of a object's children, processing each pointer field
   * encountered.
   *
   * @param trace The trace object to use for precopying.
   * @param object The object to be scanned.
   */
  @Override
  public void precopyChildren(TraceLocal trace, ObjectReference object) {
    scanObject(trace, object);
  }

  private BlockingQueue<Mutator> mutatorsToScan = null;

  /**
   * Prepares for using the <code>computeAllRoots</code> method.  The
   * thread counter allows multiple GC threads to co-operatively
   * iterate through the thread data structure (if load balancing
   * parallel GC threads were not important, the thread counter could
   * simply be replaced by a for loop).
   */
  @Override
  public synchronized void resetThreadCounter() {
    assert mutatorsToScan.size() == 0;
    mutatorsToScan = null;
    ObjectValue.startRootDiscoveryPhase();
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
   * @param trace The MMTk trace object
   */
  @Override
  public void preCopyGCInstances(TraceLocal trace) {
    /* None */
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
  @Override
  public void computeStaticRoots(TraceLocal trace) {
    if (threadIteratorTable != null) {
      if (Trace.isEnabled(Item.ROOTS)) {
        Trace.trace(Item.ROOTS, "Tracing root %s", ObjectModel.getString(threadIteratorTable.getObjectValue()));
      }
      threadIteratorTable.traceObject(trace);
      if (StackFrame.ASSERT_WILL_NOT_MOVE) {
        assert trace.willNotMoveInCurrentCollection(threadIteratorTable.getObjectValue()) :
          threadIteratorTable.getObjectValue()+" has been traced but willNotMoveInCurrentCollection is still false";
      }
      if (Trace.isEnabled(Item.ROOTS)) {
        Trace.trace(Item.ROOTS, "new value of %s", ObjectModel.getString(threadIteratorTable.getObjectValue()));
      }
    }
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
  @Override
  public void computeGlobalRoots(TraceLocal trace) {
    // none
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
   * @param trace The trace to use for computing roots.
   */
  @Override
  public void computeThreadRoots(TraceLocal trace) {
    Trace.trace(Item.COLLECT,"Computing roots for mutators");
    synchronized(this) {
      if (mutatorsToScan == null) {
        mutatorsToScan = Mutators.getAll();
      }
    }
    while(true) {
      Trace.trace(Item.COLLECT,"mutators to scan: %d",mutatorsToScan.size());
      Mutator m = mutatorsToScan.poll();
      if (m == null)
        break;
      if (Harness.allocDuringCollection.getValue()) {
        Trace.trace(Item.COLLECT,"Allocating thread iterator object");
        setThreadIteratorObject(m, m.alloc(0, 0, false, AllocationSite.INTERNAL_SITE_ID));
      }
      Trace.trace(Item.COLLECT,"Computing roots for mutator");
      m.computeThreadRoots(trace);
      if (Harness.allocDuringCollection.getValue()) {
        setThreadIteratorObject(m, ObjectReference.nullReference());
      }
    }
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
  @Override
  public void computeBootImageRoots(TraceLocal trace) {
    /* None */
  }
}
