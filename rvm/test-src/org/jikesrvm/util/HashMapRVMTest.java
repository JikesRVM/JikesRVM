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
import static org.hamcrest.CoreMatchers.*;
import static org.jikesrvm.tests.util.TestingTools.*;

import java.util.Iterator;

import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;

public class HashMapRVMTest {

  private static final String TEST1 = "Test1";
  private static final String TEST2 = "Test2";
  private static final String TEST3 = "Test3";

  private HashMapRVM<Integer, String> map;

  @Before
  public void initializeMap() {
    map = new HashMapRVM<Integer, String>(5);
  }

  @Test
  public void testGet() {
    map.put(0, TEST1);
    map.put(1, TEST2);
    assertEquals(TEST2, map.get(1));
  }

  @Test
  public void testPutAndSize() {
    fillMapWith3Elements();
    assertEquals(3, map.size());
  }

  @Test
  public void testRemove() {
    fillMapWith3Elements();
    map.remove(2);
    assertNull(map.get(2));
  }

  @Test
  public void testValueIterator() {
    fillMapWith3Elements();
    Iterator<String> v = map.valueIterator();
    assertTrue(v.hasNext());
    assertThat(asIterable(v), Matchers.containsInAnyOrder(TEST1, TEST2, TEST3));
    Iterator<String> v2 = map.valueIterator();
    assertThat(asIterable(v2), Matchers.<String>iterableWithSize(3));
    assertFalse(v.hasNext());
  }

  @Test
  public void testKeyIterator() {
    fillMapWith3Elements();
    Iterator<Integer> v = map.keyIterator();
    assertTrue(v.hasNext());
    assertThat(asIterable(v), Matchers.containsInAnyOrder(0,1,2));
    Iterator<String> v2 = map.valueIterator();
    assertThat(asIterable(v2), Matchers.<String>iterableWithSize(3));
    assertFalse(v.hasNext());
  }

  private void fillMapWith3Elements() {
    map.put(0, TEST1);
    map.put(1, TEST2);
    map.put(2, TEST3);
  }

  @Test
  public void testEmptyMap() {
    assertNull(map.get(1));
    assertNull(map.remove(1));
    assertEquals(0,map.size());
  }

  @Test
  public void removeAllRemovesAllElements() {
    fillMapWith3Elements();
    map.removeAll();
    assertThat(map.numElems, is(0));
    assertThat(map.keyIterator().hasNext(), is(false));
    assertThat(map.valueIterator().hasNext(), is(false));
    assertThat(map.values().iterator().hasNext(), is(false));
    assertNull(map.get(0));
    assertNull(map.get(1));
    assertNull(map.get(2));
  }

  @Test
  public void addAfterRemoveAllWorks() {
    fillMapWith3Elements();
    map.removeAll();
    fillMapWith3Elements();
    assertThat(map.get(0), is(TEST1));
    assertThat(map.get(1), is(TEST2));
    assertThat(map.get(2), is(TEST3));
  }

}
