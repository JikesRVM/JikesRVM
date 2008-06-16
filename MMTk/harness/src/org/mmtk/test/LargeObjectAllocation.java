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
package org.mmtk.test;

import org.mmtk.harness.Mutator;
import org.mmtk.plan.Plan;
import org.mmtk.utility.heap.HeapGrowthManager;
import org.vmmagic.unboxed.SimulatedMemory;

/**
 * This is a simple test that allocates large objects.
 */
public class LargeObjectAllocation extends Test {
  /**
   * Allocate large objects, triggering a gc between each
   */
  protected void main(Mutator m) {
    int limitPages = HeapGrowthManager.getMaxHeapSize().toWord().rshl(SimulatedMemory.LOG_BYTES_IN_PAGE + 1).toInt();
    int startPages = Plan.LOS_SIZE_THRESHOLD >>> SimulatedMemory.LOG_BYTES_IN_PAGE;
    System.err.println("Allocating from " + startPages + " to " + limitPages + " page objects");
    for (int p = startPages; p <= limitPages; p <<= 1) {
      System.err.println(p + " pages");
      m.muAlloc("temp", p << SimulatedMemory.LOG_BYTES_IN_PAGE);
      m.muGC();
    }
    System.err.println("Finished");
    m.muEnd();
  }
}
