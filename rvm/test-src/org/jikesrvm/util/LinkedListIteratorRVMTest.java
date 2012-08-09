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

import java.util.ArrayList;
import java.util.ListIterator;

import org.junit.Test;

public class LinkedListIteratorRVMTest {

  @Test
  public void testHasNext() {
    LinkedListRVM<Integer> list = new LinkedListRVM<Integer>();
    list.add(1);
    list.add(2);
    list.add(3);
    ListIterator<Integer> it = list.listIterator();
    assertTrue(it.hasNext());
    it.next();
    it.next();
    it.next();
    assertFalse(it.hasNext());
  }

  @Test
  public void testHasPreviousArrayList() {
    ArrayList<Integer> list = new ArrayList<Integer>();
    list.add(1);
    list.add(2);
    list.add(3);
    ListIterator<Integer> it = list.listIterator();
    assertFalse(it.hasPrevious());
    it.next();
    it.next();
    assertTrue(it.hasPrevious());
    it.next();
    assertTrue(it.hasPrevious());
  }

  @Test
  public void testHasPrevious() {
    LinkedListRVM<Integer> list = new LinkedListRVM<Integer>();
    list.add(1);
    list.add(2);
    list.add(3);
    ListIterator<Integer> it = list.listIterator();
    assertFalse(it.hasPrevious());
    it.next();
    it.next();
    assertTrue(it.hasPrevious());
    it.next();
    assertTrue(it.hasPrevious());
  }

  @Test
  public void testNext() {
    LinkedListRVM<Integer> list = new LinkedListRVM<Integer>();
    list.add(1);
    list.add(2);
    list.add(3);
    ListIterator<Integer> it = list.listIterator();
    assertEquals(1, it.next(), 0);
    assertEquals(2, it.next(), 0);
    assertTrue(it.hasNext());
    assertEquals(3, it.next(), 0);
    assertTrue(it.hasPrevious());
  }

  @Test
  public void testEmptyList(){
    LinkedListRVM<Integer> list = new LinkedListRVM<Integer>();
    ListIterator<Integer> it = list.listIterator();
    assertFalse(it.hasNext());
    assertFalse(it.hasPrevious());
  }
}
