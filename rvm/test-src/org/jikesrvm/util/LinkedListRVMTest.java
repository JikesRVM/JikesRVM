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

import java.util.Iterator;

import static org.junit.Assert.*;

import org.junit.Test;

public class LinkedListRVMTest {

  @Test
  public void testAdd() {
    LinkedListRVM<Integer> list = new LinkedListRVM<Integer>();
    list.add(1);
    list.add(2);
    assertEquals(1, list.get(0), 0);
    assertEquals(2, list.get(1), 0);
  }

  @Test
  public void testClearAndSize() {
    LinkedListRVM<Integer> list = new LinkedListRVM<Integer>();
    list.add(3);
    list.add(5);
    list.clear();
    assertEquals(0, list.size());
  }

  @Test
  public void testContains() {
    LinkedListRVM<Integer> list = new LinkedListRVM<Integer>();
    list.add(1);
    list.add(2);
    assertTrue(list.contains(1) && list.contains(2));
  }

  @Test
  public void testContainsAll() {
    LinkedListRVM<Integer> list1 = new LinkedListRVM<Integer>();
    LinkedListRVM<Integer> list2 = new LinkedListRVM<Integer>();
    list1.add(1);
    list1.add(2);
    list2.add(1);
    list2.add(2);
    assertTrue(list1.containsAll(list2));
    assertTrue(list2.containsAll(list1));
  }

  @Test
  public void testGet() {
    LinkedListRVM<Integer> list = new LinkedListRVM<Integer>();
    list.add(1);
    list.add(2);
    assertEquals(1, (int) list.get(0));
    assertEquals(2, (int) list.get(1));
  }

  @Test
  public void testIndexOf() {
    LinkedListRVM<Integer> list = new LinkedListRVM<Integer>();
    list.add(1);
    list.add(2);
    list.add(3);
    list.add(3);
    list.add(4);
    assertEquals(0, list.indexOf(1));
    assertEquals(2, list.indexOf(3));
  }

  @Test
  public void testIsEmpty() {
    LinkedListRVM<Integer> list = new LinkedListRVM<Integer>();
    list.add(1);
    list.add(2);
    assertFalse(list.isEmpty());
    list.clear();
    assertTrue(list.isEmpty());
  }

  @Test
  public void testIterator() {
    LinkedListRVM<Integer> list = new LinkedListRVM<Integer>();
    list.add(1);
    list.add(2);
    Iterator<Integer> y = list.iterator();
    assertEquals(1, y.next(), 0);
    assertEquals(2, y.next(), 0);
  }

  @Test
  public void testListIterator() {
    LinkedListRVM<Integer> list = new LinkedListRVM<Integer>();
    list.add(1);
    list.add(2);
    Iterator<Integer> y = list.listIterator();
    assertEquals(1, y.next(), 0);
    assertEquals(2, y.next(), 0);
  }

  @Test
  public void testRemoveInt() {
    LinkedListRVM<Integer> list = new LinkedListRVM<Integer>();
    list.add(1);
    list.add(2);
    list.add(3);
    list.remove(0);
    assertEquals(-1, list.indexOf(1));
    assertEquals(1, list.indexOf(3));
    list.add(4);
    list.add(5);
    list.remove(0);
    assertEquals(0, list.indexOf(3));
  }

  @Test
  public void testRemoveObject() {
    LinkedListRVM<Integer> list = new LinkedListRVM<Integer>();
    list.add(1);
    list.add(2);
    list.add(3);
    list.remove((Integer) 1);
    assertEquals(-1, list.indexOf(1));
    list.add(4);
    list.add(5);
    list.remove((Integer) 4);
    assertEquals(3, list.size());
    assertEquals(2, list.indexOf(5));
  }

  @Test
  public void testRemoveInternal() {
    LinkedListRVM<Integer> list = new LinkedListRVM<Integer>();
    list.add(3);
    list.add(5);
  }
}
