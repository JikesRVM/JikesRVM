package org.vmmagic.unboxed;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class ObjectReferenceTest {

  final int BITS = SimulatedMemory.BYTES_IN_WORD * 8;

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
  }

  @Before
  public void setUp() throws Exception {
  }

  @Test
  public void testHashCode() {
    for (int i=SimulatedMemory.LOG_BITS_IN_BYTE; i < BITS; i++) {
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
      assertTrue(ref.toAddress().toLong() == 1l<<i);
    }
  }

  @Test
  public void testEqualsObject() {
    ObjectReference[] lrefs = new ObjectReference[BITS];
    ObjectReference[] rrefs = new ObjectReference[BITS];
    for (int i=0; i < BITS; i++) {
      lrefs[i] = Word.one().lsh(i).toAddress().toObjectReference();
      rrefs[i] = Address.fromLong(1l<<i).toObjectReference();
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
      final String expected = String.format(format, 1l<<i);
      assertEquals(Word.one().lsh(i).toAddress().toObjectReference().toString(),
          expected);
    }
  }

}
