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

public class PriorityQueueRVMTest {

  @Test
  public void testInsertAndNumElements() {
    PriorityQueueRVM queue = new PriorityQueueRVM();
    queue.insert(1, new Integer(1));
    queue.insert(1, new Integer(2));
    assertEquals(2, queue.numElements());
  }

  @Test
  public void testIsEmpty() {
    PriorityQueueRVM queue = new PriorityQueueRVM();
    assertTrue(queue.isEmpty());
    queue.insert(1, new Integer(1));
    assertFalse(queue.isEmpty());
  }

  @Test
  public void testDeleteMin() {
    PriorityQueueRVM queue = new PriorityQueueRVM();
    queue.insert(1, new Integer(1));
    queue.insert(1, new Integer(2));
    queue.insert(2, new Integer(3));
    queue.deleteMin();
    assertEquals(1.0, queue.rootValue(),0.0);
  }

  @Test
  public void testRootValue() {
    PriorityQueueRVM queue = new PriorityQueueRVM();
    queue.insert(1, new Integer(1));
    queue.insert(2, new Integer(2));
    queue.insert(3, new Integer(3));
    queue.insert(3, new Integer(4));
    assertEquals(3.0, queue.rootValue(),0.0);
  }
}
