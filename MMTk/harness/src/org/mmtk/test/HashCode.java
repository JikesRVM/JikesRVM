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
 * This is a simple test that checks hashCode values persist across a collection.
 */
public class HashCode extends Test {
  /** Size of linked list to create */
  private static final int LIST_SIZE = 200;

  /** The frequency at which objects are hashed */
  private static final int HASH_EVERY = 20;

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
      // Check the hash codes
      int dataValue = m.muLoadData("current", 0);

      if (dataValue != 0) {
        // Looks like an object that was hashed
        found++;
        int hashValue = m.muHashCode("current");

        if (hashValue != dataValue) {
          System.err.println("ERROR: hash and remembered hash mismatch " + dataValue  + " != " + hashValue);
        }
      }

      // Follow the linked list
      m.muLoad("next", "current", 0);
      m.muCopy("current", "next");
    }
    if (found != (LIST_SIZE / HASH_EVERY)) {
      System.err.println("WARNING: expected " + (LIST_SIZE / HASH_EVERY) + "hashed objects but found only " + found);
    }
  }

  /**
   * Build a linked list of hashed objects.
   */
  private void buildList(Mutator m) {
    System.err.println("Building list...");
    // Build the linked list
    m.muAlloc("head", 1, 1);
    m.muStoreData("head", 0, m.muHashCode("head"));
    m.muCopy("last", "head");
    for(int i=1; i < LIST_SIZE; i++) {
      m.muAlloc("current", 1, 1);
      if ((i % HASH_EVERY) == 0) {
        int hashValue = m.muHashCode("current");
        if (hashValue == 0) {
          System.err.println("WARNING: hashCode of 0 found");
        }
        m.muStoreData("current", 0, hashValue);
      }
      m.muStore("last", 0, "current");
      m.muCopy("last", "current");
    }
  }
}
