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
 * This is a simple test that forces an out of memory condition by creating an infinite linked list.
 */
public class OutOfMemory extends Test {
  /**
   * Create an infinite linked list.
   */
  protected void main(Mutator m) {
    try {
      m.muAlloc("current", 1, 8);
      while(true) {
        m.muCopy("last", "current");
        m.muAlloc("current", 1, 8);
        m.muStore("current", 0, "last");
      }
    } catch (Mutator.OutOfMemory oom) {
      System.err.println("OutOfMemory");
      return;
    }
  }
}
