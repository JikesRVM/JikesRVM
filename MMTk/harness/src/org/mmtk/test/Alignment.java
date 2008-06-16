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

/**
 * This is a simple test that allocates aligned and unaligned objects.
 */
public class Alignment extends Test {
  /** Size of linked list to create */
  private static final int LIST_SIZE = 200;

  /** The frequency at which objects are 8 byte aligned */
  private static final int ALIGN_EVERY = 3;

  /**
   * Create a linked list, then verify it.
   */
  protected void main(Mutator m) {
    buildList(m);
    verifyList(m);
    System.err.println("Triggering GC...");
    m.muGC();
    verifyList(m);
    m.muEnd();
  }

  /**
   * Verify the linked list.
   */
  private void verifyList(Mutator m) {
    System.err.println("Checking list...");
    int found = 0;
    m.muCopy("current", "head");
    while (!m.muIsNull("current")) {
      boolean doubleAlign = (found++ % ALIGN_EVERY) == 0;
      int mask = doubleAlign ? 0x7 : 0x3;
      if ((m.muAddress("current").toInt() & mask) != 0) {
        System.err.println("ERROR: Misaligned object " + m.muAddress("current") + (doubleAlign ? " [8B]" : " [4B]"));
      }

      // Follow the linked list
      m.muLoad("next", "current", 0);
      m.muCopy("current", "next");
    }
    if (found != LIST_SIZE) {
      System.err.println("ERROR: expected " + LIST_SIZE + " objects but found only " + found);
    }
  }

  /**
   * Build a linked list
   */
  private void buildList(Mutator m) {
    System.err.println("Building list...");
    // Build the linked list
    m.muAlloc("head", 1, 1);
    m.muStoreData("head", 0, m.muHashCode("head"));
    m.muCopy("last", "head");
    for(int j=1; j < LIST_SIZE; j++) {
      boolean doubleAlign = (j % ALIGN_EVERY) == 0;
      m.muAlloc("current", 1, 1, doubleAlign);
      m.muStore("last", 0, "current");
      m.muCopy("last", "current");
    }
  }
}
