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
package org.vmmagic.unboxed;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.vmmagic.unboxed.harness.MemoryConstants;

public class ObjectReferenceTest {

  final int BITS = MemoryConstants.BYTES_IN_WORD * 8;

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
  }

  @Before
  public void setUp() throws Exception {
  }

  @Test
  public void testHashCode() {
    for (int i=MemoryConstants.LOG_BITS_IN_BYTE; i < BITS; i++) {
      ObjectReference ref = Word.one().lsh(i).toAddress().toObjectReference();
      assertTrue(ref.hashCode() != 0);
    }
  }

  @Test
  public void testNullReference() {
    assertTrue(ObjectReference.nullReference().toAddress().EQ(Address.zero()));
  }

  @Test
  public void testToAddress() {
    for (int i=0; i < 32; i++) {
      ObjectReference ref = Word.one().lsh(i).toAddress().toObjectReference();
      assertTrue(ref.toAddress().toInt() == 1 << i);
    }
    for (int i=32; i < BITS; i++) {
      ObjectReference ref = Word.one().lsh(i).toAddress().toObjectReference();
      assertTrue(ref.toAddress().toLong() == 1L<<i);
    }
  }

  @Test
  public void testEqualsObject() {
    ObjectReference[] lrefs = new ObjectReference[BITS];
    ObjectReference[] rrefs = new ObjectReference[BITS];
    for (int i=0; i < BITS; i++) {
      lrefs[i] = Word.one().lsh(i).toAddress().toObjectReference();
      rrefs[i] = Address.fromLong(1L<<i).toObjectReference();
    }
    for (int i=0; i < BITS; i++) {
      for (int j=0; j < BITS; j++) {
        if (i == j) {
          assertTrue(lrefs[i].equals(rrefs[j]));
          assertTrue(lrefs[i].hashCode() == rrefs[j].hashCode());
        } else {
          assertFalse(lrefs[i].equals(rrefs[j]));
        }
      }
    }
  }

  @Test
  public void testIsNull() {
    assertTrue(Address.zero().toObjectReference().isNull());
    assertTrue(ObjectReference.nullReference().isNull());
  }

  @Test
  public void testToString() {
    final String format = BITS == 64 ? "0x%016x" : "0x%08x";
    for (int i=0; i < BITS; i++) {
      final String expected = String.format(format, 1L<<i);
      assertEquals(Word.one().lsh(i).toAddress().toObjectReference().toString(),
          expected);
    }
  }

}
