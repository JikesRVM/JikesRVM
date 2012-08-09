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
package org.jikesrvm.util;

import static org.junit.Assert.*;

import org.junit.Test;

public class PairTest {

  @Test
  public void testHashCode() {
    Pair<Integer, Integer> pair1 = new Pair<Integer, Integer>(3, 4);
    Pair<Integer, Integer> pair2 = new Pair<Integer, Integer>(3, 4);
    assertEquals(pair1.hashCode(), pair2.hashCode());
  }

  @Test
  public void testEquals() {
    Pair<Integer, Integer> p1 = new Pair<Integer, Integer>(1, 1);
    Pair<Integer, Integer> p2 = new Pair<Integer, Integer>(2, 2);
    Pair<Integer, Integer> p3 = new Pair<Integer, Integer>(2, 2);
    assertTrue(p2.equals(p3));
    assertFalse(p1.equals(p2));
  }
}
