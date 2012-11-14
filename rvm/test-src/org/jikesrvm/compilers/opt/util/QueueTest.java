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
package org.jikesrvm.compilers.opt.util;

import static org.junit.Assert.*;

import java.util.Iterator;

import org.junit.Test;

public class QueueTest {

  @Test
  public void testInsert() {
    Queue<Integer> q = new Queue<Integer>();
    q.insert(1);
    q.insert(2);
    assertFalse(q.isEmpty());
  }

  @Test
  public void testRemove() {
    Queue<Integer> q = new Queue<Integer>();
    q.insert(1);
    q.insert(2);
    q.remove();
    assertFalse(q.isEmpty());
    q.remove();
    assertTrue(q.isEmpty());
  }

  @Test
  public void testIsEmpty() {
    Queue<Integer> q = new Queue<Integer>();
    q.insert(1);
    assertFalse(q.isEmpty());
    q.remove();
    assertTrue(q.isEmpty());
  }

  @Test
  public void testIterator() {
    Queue<Integer> q = new Queue<Integer>();
    q.insert(1);
    q.insert(2);
    Iterator<Integer> x = q.iterator();
    assertTrue(x.hasNext());
    x.next();
    assertTrue(x.hasNext());
    x.next();
    assertFalse(x.hasNext());
  }
}
