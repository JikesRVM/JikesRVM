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
import static org.jikesrvm.tests.util.TestingTools.*;

import java.util.Iterator;

import org.hamcrest.Matchers;
import org.junit.Test;

public class HashMapRVMTest {

  @Test
  public void testGet() {
    HashMapRVM<Integer, String> map = new HashMapRVM<Integer, String>(5);
    map.put(0, "Test1");
    map.put(1, "Test2");
    assertEquals("Test2", map.get(1));
  }

  @Test
  public void testPutAndSize() {
    HashMapRVM<Integer, String> map = new HashMapRVM<Integer, String>(5);
    map.put(0, "Test1");
    map.put(1, "Test2");
    map.put(2, "Test3");
    assertEquals(3, map.size());
  }

  @Test
  public void testRemove() {
    HashMapRVM<Integer, String> map = new HashMapRVM<Integer, String>(5);
    map.put(0, "Test1");
    map.put(1, "Test2");
    map.put(2, "Test3");
    map.remove(2);
    assertNull(map.get(2));
  }

  @Test
  public void testValueIterator() {
    HashMapRVM<Integer, String> map = new HashMapRVM<Integer, String>(5);
    map.put(0, "Test1");
    map.put(1, "Test2");
    map.put(2, "Test3");
    Iterator<String> v = map.valueIterator();
    assertTrue(v.hasNext());
    assertThat(asIterable(v), Matchers.containsInAnyOrder("Test1","Test2","Test3"));
    Iterator<String> v2 = map.valueIterator();
    assertThat(asIterable(v2), Matchers.<String>iterableWithSize(3));
    assertFalse(v.hasNext());
  }

  @Test
  public void testKeyIterator() {
    HashMapRVM<Integer, String> map = new HashMapRVM<Integer, String>(5);
    map.put(0, "Test1");
    map.put(1, "Test2");
    map.put(2, "Test3");
    Iterator<Integer> v = map.keyIterator();
    assertTrue(v.hasNext());
    assertThat(asIterable(v), Matchers.containsInAnyOrder(0,1,2));
    Iterator<String> v2 = map.valueIterator();
    assertThat(asIterable(v2), Matchers.<String>iterableWithSize(3));
    assertFalse(v.hasNext());
  }

  @Test
  public void testEmptyMap() {
    HashMapRVM<Integer, String> map = new HashMapRVM<Integer, String>(5);
    assertNull(map.get(1));
    assertNull(map.remove(1));
    assertEquals(0,map.size());
  }
}
