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
package org.mmtk.harness.sanity;

import java.util.Map;
import java.util.Set;

import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.vm.ObjectModel;
import org.mmtk.policy.Space;
import org.vmmagic.unboxed.ObjectReference;


/**
 * Collection sanity checker for the MMTk Harness.  This class is used to
 * take heap snapshots before and after a collection, and ensure that the
 * objects live after the collection exactly those live before collection.
 *
 * Note that this is in no way problematic for partial-heap collectors that
 * only collect part of the heap - uncollected objects in the mature space
 * don't affect the live set either way.
 *
 * We also check:
 * - That no objects are duplicated
 */
public class GcSanity {

  private HeapSnapshot before;
  private HeapSnapshot after = null;

  /**
   * Maximum #errors of each type that will be reported
   */
  private static final int ERROR_LIMIT = 10;

  /** Take a snapshot of the heap before collection */
  public void snapshotBefore() {
    before = new HeapSnapshot();
  }

  /** Take a snapshot of the heap after collection */
  public void snapshotAfter() {
    after = new HeapSnapshot();
  }

  /** @return The set of live objects */
  public Set<ObjectReference> liveSet() {
    return (after == null ? before : after).getLiveObjects();
  }

  /**
   * Assert that the heap state is sane (immediately after a collection)
   */
  public void assertSanity() {
    Trace.trace(Item.SANITY,"Heap size by ID   before: %d, after: %d", before.sizeById(), after.sizeById());
    Trace.trace(Item.SANITY,"Heap size by Addr before: %d, after: %d", before.sizeByAddress(), after.sizeByAddress());

    /*
     * Assert that there are no duplicated objects, ie objects with the same
     * object ID in more than one place.
     */
    if (after.getDuplicates().size() > 0) {
      int errors = 0;
      for (Set<HeapEntry> aliasSet : after.getDuplicates()) {
        HeapEntry first = aliasSet.iterator().next();
        boolean firstDup = true;
        System.err.printf("### Object %s is duplicated in the heap: ",first.getId());
        for (HeapEntry entry : aliasSet) {
          if (firstDup) {
            firstDup = false;
          } else {
            System.err.printf(", ");
          }
          ObjectReference object = entry.getObject();
          System.err.printf("%s/%s",object,Space.getSpaceForObject(object).getName());
        }
        System.err.printf("%n");
        if (errors++ > ERROR_LIMIT) {
          System.err.printf("[SANITY] Error limit reached...%n");
          break;
        }
      }
      throw new AssertionError("Duplicate objects found in heap");
    }

    /*
     * Assert that collection preserved the number of objects in the heap
     */
    if (before.sizeById() != after.sizeById()) {
      System.err.printf("[SANITY] ERROR: Live object population has changed, before : %d objects, after: %d objects%n",
          before.sizeById(),after.sizeById());
      dumpDifferencesAndDie();
    }
    assert before.sizeByAddress() == after.sizeByAddress() :
      "before : "+before.sizeByAddress()+" objects, after: "+after.sizeByAddress()+" objects";

    /*
     * Assert that the collection preserved the live set
     */
    if (!before.getLive().equals(after.getLive())) {
      System.err.printf("[SANITY] ERROR: Live object set has changed%n");
      dumpDifferencesAndDie();
    }
    if (Trace.isEnabled(Item.SANITY)) {
      printSpaceStats("before", before);
      printSpaceStats("after", after);
    }
  }

  private void dumpDifferencesAndDie() throws AssertionError {
    /* Dump any objects killed by the collector */
    int errors = 0;
    for (int id : before.getLive()) {
      if (!after.getLive().contains(id)) {
        System.err.printf("[SANITY] Object %d was killed by the collector%n",id);
        HeapEntry entry = before.getEntry(id);
        System.err.printf("[SANITY] %s%n",ObjectModel.getString(entry.getObject()));
        if (errors++ > ERROR_LIMIT) {
          System.err.printf("[SANITY] Error limit reached...%n");
          break;
        }
      }
    }
    /* Dump any objects created by the collector */
    errors = 0;
    for (int id : after.getLive()) {
      if (!before.getLive().contains(id)) {
        System.err.printf("[SANITY] Object %d was created by the collector%n",id);
        HeapEntry entry = after.getEntry(id);
        System.err.printf("[SANITY] %s%n",ObjectModel.getString(entry.getObject()));
        if (errors++ > ERROR_LIMIT) {
          System.err.printf("[SANITY] Error limit reached...%n");
          break;
        }
      }
    }
    throw new AssertionError("Live set has changed");
  }

  private void printSpaceStats(String tag, HeapSnapshot snapshot) {
    Trace.printf(Item.SANITY,tag+" ");
    for (Map.Entry<String,Integer> statistic : snapshot.getSpaceStats().entrySet()) {
      String name = statistic.getKey();
      int count = statistic.getValue();
      if (count > 0)
        Trace.printf("%-8s:%6d ",name,count);
    }
    Trace.printf("%n");
  }
}
