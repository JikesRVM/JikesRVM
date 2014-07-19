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

import java.util.concurrent.BlockingQueue;

import org.mmtk.harness.Mutator;
import org.mmtk.harness.Mutators;
import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.TransitiveClosure;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.harness.Clock;

@Uninterruptible
public class Scanning extends org.mmtk.vm.Scanning {

  /**
   * {@inheritDoc}
   */
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

  /**
   * {@inheritDoc}
   */
  @Override
  public void specializedScanObject(int id, TransitiveClosure trace, ObjectReference object) {
    scanObject(trace, object);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public synchronized void resetThreadCounter() {
    assert mutatorsToScan.size() == 0;
    mutatorsToScan = null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void notifyInitialThreadScanComplete(boolean partialScan) {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void computeStaticRoots(TraceLocal trace) {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void computeGlobalRoots(TraceLocal trace) {
    // none
  }

  /**
   * Queue of mutators to scan.  At the start of the parallel scan of thread roots
   * this is (atomically) initialized to the set of all mutator threads.  As each
   * collector thread proceeds, it removes a thread from the queue and scans it,
   * exiting when the queue is empty.
   */
  private BlockingQueue<Mutator> mutatorsToScan = null;

  /**
   * {@inheritDoc}
   */
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
      Clock.start();
      m.prepare();
      m.computeThreadRoots(trace);
      Clock.stop();
    }
    Clock.start();
  }

  public static void releaseThreads() {
    for (Mutator m : Mutators.getAll()) {
      m.release();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void computeBootImageRoots(TraceLocal trace) {
    /* None */
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void computeNewThreadRoots(TraceLocal trace) {
    /*
     * For now the harness doesn't support partial stack scanning, so do a complete scan
     */
    computeThreadRoots(trace);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean supportsReturnBarrier() {
    return false;
  }
}
