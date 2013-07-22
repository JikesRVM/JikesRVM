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
import org.vmmagic.unboxed.harness.Clock;

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
    if (Harness.allocDuringCollection.getValue() && threadIteratorTable == null) {
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

  @Override
  public void scanObject(TransitiveClosure trace, ObjectReference object) {
    if (Trace.isEnabled(Item.SCAN)) {
      Clock.stop();
      Trace.trace(Item.SCAN, "Scanning object %s", ObjectModel.addressAndSpaceString(object));
      Clock.start();
    }
    int refs = ObjectModel.getRefs(object);

    Address first = object.toAddress().plus(ObjectModel.REFS_OFFSET);
    for (int i=0; i < refs; i++) {
      if (Trace.isEnabled(Item.SCAN)) {
        Clock.stop();
        Trace.trace(Item.SCAN, "  Edge %s", first.plus(i << LOG_BYTES_IN_ADDRESS).loadObjectReference());
        Clock.start();
      }
      trace.processEdge(object, first.plus(i << LOG_BYTES_IN_ADDRESS));
    }
  }

  @Override
  public void specializedScanObject(int id, TransitiveClosure trace, ObjectReference object) {
    scanObject(trace, object);
  }

  @Override
  public synchronized void resetThreadCounter() {
    assert mutatorsToScan.size() == 0;
    mutatorsToScan = null;
    ObjectValue.startRootDiscoveryPhase();
  }

  @Override
  public void notifyInitialThreadScanComplete() {
  }

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

  @Override
  public void computeGlobalRoots(TraceLocal trace) {
    // none
  }

  private BlockingQueue<Mutator> mutatorsToScan = null;

  @Override
  public void computeThreadRoots(TraceLocal trace) {
    Clock.stop();
    Trace.trace(Item.COLLECT,"Computing roots for mutators");
    synchronized(this) {
      if (mutatorsToScan == null) {
        mutatorsToScan = Mutators.getAll();
      }
    }
    while(true) {
      Trace.trace(Item.COLLECT,"mutators to scan: %d",mutatorsToScan.size());
      Mutator m = mutatorsToScan.poll();
      if (m == null) {
        break;
      }
      Trace.trace(Item.COLLECT,"Computing roots for mutator");
      if (Harness.allocDuringCollection.getValue()) {
        Trace.trace(Item.COLLECT,"Allocating thread iterator object");
        Clock.start();
        setThreadIteratorObject(m, m.alloc(0, 0, false, AllocationSite.INTERNAL_SITE_ID));
        Clock.stop();
      }
      Clock.start();
      m.prepare();
      m.computeThreadRoots(trace);
      Clock.stop();
      if (Harness.allocDuringCollection.getValue()) {
        Clock.start();
        setThreadIteratorObject(m, ObjectReference.nullReference());
        Clock.stop();
      }
    }
    Clock.start();
  }

  public static void releaseThreads() {
    for (Mutator m : Mutators.getAll()) {
      m.release();
    }
  }

  @Override
  public void computeBootImageRoots(TraceLocal trace) {
    /* None */
  }
}
