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

import org.mmtk.policy.Space;
import org.vmmagic.unboxed.ObjectReference;


/**
 * Collection sanity checker for the MMTk Harness.
 */
public class Sanity {

  private static boolean VERBOSE = false;

  private HeapSnapshot before;
  private HeapSnapshot after;

  /** Take a snapshot of the heap before collection */
  public void snapshotBefore() {
    before = new HeapSnapshot();
  }

  /** Take a snapshot of the heap after collection */
  public void snapshotAfter() {
    after = new HeapSnapshot();
  }

  /**
   * Assert that the heap state is sane (immediately after a collection)
   */
  public void assertSanity() {
    if (VERBOSE) {
      System.err.printf("Heap size by ID   before: %d, after: %d%n", before.sizeById(), after.sizeById());
      System.err.printf("Heap size by Addr before: %d, after: %d%n", before.sizeByAddress(), after.sizeByAddress());
    }

    /*
     * Assert that there are no duplicated objects, ie objects with the same
     * object ID in more than one place.
     */
    if (after.getDuplicates().size() > 0) {
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
      }
    }

    /*
     * Assert that collection preserved the number of objects in the heap
     */
    assert before.sizeById() == after.sizeById() :
      "before : "+before.sizeById()+" objects, after: "+after.sizeById()+" objects";
    assert before.sizeByAddress() == after.sizeByAddress() :
      "before : "+before.sizeByAddress()+" objects, after: "+after.sizeByAddress()+" objects";

    /*
     * Assert that the collection preserved the live set
     */
    assert before.getLive().equals(after.getLive());
    if (VERBOSE) {
      printSpaceStats("before", before);
      printSpaceStats("after", after);
    }
  }



  private void printSpaceStats(String tag, HeapSnapshot snapshot) {
    System.out.printf(tag+" ");
    for (Map.Entry<String,Integer> statistic : snapshot.getSpaceStats().entrySet()) {
      String name = statistic.getKey();
      int count = statistic.getValue();
      if (count > 0)
        System.out.printf("%-8s:%6d ",name,count);
    }
    System.out.printf("%n");
  }
}
