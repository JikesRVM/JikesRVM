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

public class BitVectorTest {

  private static final int SMALL_VECTOR=31;
  private static final int LARGE_VECTOR=63;

  @Test
  public void testSetAll() {
    BitVector vector1 = new BitVector(SMALL_VECTOR);
    BitVector vector2 = new BitVector(SMALL_VECTOR);
    vector1.setAll();
    assertEquals(vector2, BitVector.not(vector1));
    BitVector vector3 = new BitVector(LARGE_VECTOR);
    BitVector vector4 = new BitVector(LARGE_VECTOR);
    vector3.setAll();
    assertEquals(vector3, BitVector.not(vector4));
  }

  @Test
  public void testSetAndGet() {
    BitVector vector1 = new BitVector(SMALL_VECTOR);
    vector1.set(3);
    assertTrue(vector1.get(3));
    BitVector vector2 = new BitVector(LARGE_VECTOR);
    vector2.set(3);
    assertTrue(vector2.get(3));
  }

  @Test
  public void testClearAll() {
    BitVector vector1 = new BitVector(SMALL_VECTOR);
    vector1.set(3);
    vector1.set(5);
    vector1.clearAll();
    assertTrue(vector1.isZero());
    BitVector vector2 = new BitVector(LARGE_VECTOR);
    vector2.set(3);
    vector2.set(5);
    vector2.clearAll();
    assertTrue(vector2.isZero());
  }

  @Test
  public void testClear() {
    BitVector vector1 = new BitVector(SMALL_VECTOR);
    vector1.set(3);
    vector1.clear(3);
    assertTrue(vector1.isZero());
    BitVector vector2 = new BitVector(LARGE_VECTOR);
    vector2.set(3);
    vector2.clear(3);
    assertTrue(vector2.isZero());
  }

  @Test
  public void testNot() {
    BitVector vector1 = new BitVector(SMALL_VECTOR);
    BitVector vector2 = new BitVector(SMALL_VECTOR);
    vector1.not();
    assertEquals(vector1, BitVector.not(vector2));
    BitVector vector3 = new BitVector(LARGE_VECTOR);
    BitVector vector4 = new BitVector(LARGE_VECTOR);
    vector3.not();
    assertEquals(vector3, BitVector.not(vector4));
  }

  @Test
  public void testAnd() {
    BitVector vector1 = new BitVector(SMALL_VECTOR);
    BitVector vector2 = new BitVector(SMALL_VECTOR);
    BitVector vector3 = new BitVector(SMALL_VECTOR);
    vector3.and(vector1);
    assertEquals(BitVector.and(vector1, vector2), vector3);
    BitVector vector4 = new BitVector(LARGE_VECTOR);
    BitVector vector5 = new BitVector(LARGE_VECTOR);
    BitVector vector6 = new BitVector(LARGE_VECTOR);
    vector6.and(vector4);
    assertEquals(BitVector.and(vector4, vector5), vector6);
  }

  @Test
  public void testOr() {
    BitVector vector1 = new BitVector(SMALL_VECTOR);
    BitVector vector2 = new BitVector(SMALL_VECTOR);
    BitVector vector3 = new BitVector(SMALL_VECTOR);
    vector3.or(vector1);
    assertEquals(BitVector.or(vector1, vector2), vector3);
    BitVector vector4 = new BitVector(LARGE_VECTOR);
    BitVector vector5 = new BitVector(LARGE_VECTOR);
    BitVector vector6 = new BitVector(LARGE_VECTOR);
    vector6.or(vector1);
    assertEquals(BitVector.or(vector4, vector5), vector6);
  }

  @Test
  public void testXor() {
    BitVector vector1 = new BitVector(SMALL_VECTOR);
    BitVector vector2 = new BitVector(SMALL_VECTOR);
    vector1.set(5);
    vector2.set(2);
    vector1.xor(vector2);
    assertTrue(vector1.get(2));
    BitVector vector3 = new BitVector(LARGE_VECTOR);
    BitVector vector4 = new BitVector(LARGE_VECTOR);
    vector3.set(5);
    vector4.set(2);
    vector3.xor(vector4);
    assertTrue(vector3.get(2));
  }

  @Test
  public void testIntersectionEmpty() {
    BitVector vector1 = new BitVector(SMALL_VECTOR);
    BitVector vector2 = new BitVector(LARGE_VECTOR);
    vector1.set(5);
    vector2.set(4);
    assertTrue(vector1.intersectionEmpty(vector2));
  }

  @Test
  public void testCopyBits() {
    BitVector vector1 = new BitVector(SMALL_VECTOR);
    BitVector vector2 = new BitVector(SMALL_VECTOR);
    vector2.set(2);
    vector2.set(3);
    vector1.copyBits(vector2);
    assertEquals(vector2, vector1);
    BitVector vector3 = new BitVector(LARGE_VECTOR);
    BitVector vector4 = new BitVector(LARGE_VECTOR);
    vector4.set(2);
    vector4.set(3);
    vector3.copyBits(vector4);
    assertEquals(vector4, vector3);
  }

  @Test
  public void testPopulationCount() {
    BitVector vector1 = new BitVector(SMALL_VECTOR);
    vector1.set(2);
    vector1.set(3);
    vector1.set(5);
    vector1.set(1);
    assertEquals(4, vector1.populationCount());
    BitVector vector2 = new BitVector(LARGE_VECTOR);
    vector2.set(2);
    vector2.set(3);
    vector2.set(5);
    vector2.set(1);
    assertEquals(4, vector2.populationCount());
  }

  @Test
  public void testLength() {
    BitVector vector1 = new BitVector(SMALL_VECTOR);
    vector1.set(2);
    vector1.set(3);
    vector1.set(5);
    vector1.set(1);
    assertEquals(32, vector1.length());
    BitVector vector2 = new BitVector(LARGE_VECTOR);
    vector2.set(2);
    vector2.set(3);
    vector2.set(5);
    vector2.set(1);
    assertEquals(64, vector2.length());
  }

  @Test
  public void testIsZero() {
    BitVector vector1 = new BitVector(SMALL_VECTOR);
    assertTrue(vector1.isZero());
    BitVector vector2 = new BitVector(LARGE_VECTOR);
    assertTrue(vector2.isZero());
  }

  @Test
  public void testDup() {
    BitVector vector1 = new BitVector(SMALL_VECTOR);
    BitVector vector2 = vector1.dup();
    assertEquals(vector1, vector2);
    BitVector vector3 = new BitVector(LARGE_VECTOR);
    BitVector vector4 = vector3.dup();
    assertEquals(vector3, vector4);
  }
}
