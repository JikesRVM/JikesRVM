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

import org.junit.BeforeClass;
import org.junit.Test;
import org.mmtk.harness.Harness;
import org.vmmagic.unboxed.harness.ArchitecturalWord;
import org.vmmagic.unboxed.harness.Architecture;

/**
 * Basic tests of the address type, specifically those that don't require the
 * harness to be initialized.
 */
public class AddressTestBasic {

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
//    Harness.bits.setValue(64);
    ArchitecturalWord.init(Harness.bits.getValue());
  }

  private static boolean is64bit() {
    return ArchitecturalWord.getModel() == Architecture.BITS64;
  }

  private static boolean is32bit() {
    return !is64bit();
  }

  final Address zero = Address.zero();
  final Address one = Address.fromIntSignExtend(1);
  final Address large = Address.fromIntSignExtend(Integer.MAX_VALUE);
  final Address veryLarge = Address.fromIntSignExtend(Integer.MIN_VALUE/2);
  final Address largest = Address.max();

  final Address zero() { return Address.fromIntSignExtend(0); }
  final Address one() { return Address.fromIntSignExtend(1); }
  final Address large() { return  Address.fromIntSignExtend(Integer.MAX_VALUE); }
  final Address veryLarge() { return Address.fromIntSignExtend(Integer.MIN_VALUE/2); }
  final Address largest() { return Address.max(); }

  @Test
  public void testZero() {
    assertTrue(Address.zero().toInt() == 0);
  }

  @Test
  public void testIsZero() {
    assertTrue(Address.fromIntSignExtend(0).isZero());
    assertTrue(Address.zero().isZero());
  }

  @Test
  public void testIsMax() {
    assertTrue(Address.max().isMax());
  }

  @Test
  public void testFromIntSignExtend() {
    assertTrue(Address.fromIntSignExtend(1).toInt() == 1);
    assertTrue(Address.fromIntSignExtend(-1).toInt() == -1);
    assertTrue(Address.fromIntSignExtend(1).toLong() == 1);
    if (is32bit()) {
      assertFalse(Address.fromIntSignExtend(-1).toLong() == -1);
    } else {
      assertTrue(Address.fromIntSignExtend(-1).toLong() == -1);
    }
  }

  @Test
  public void testFromIntZeroExtend() {
    assertTrue(Address.fromIntZeroExtend(1).toInt() == 1);
    assertTrue(Address.fromIntZeroExtend(-1).toInt() == -1);
    assertTrue(Address.fromIntZeroExtend(1).toLong() == 1);
    assertTrue(Address.fromIntZeroExtend(-1).toLong() == 0xFFFFFFFFL);
  }

  @Test
  public void testFromLong() {
    assertTrue(Address.fromLong(0).isZero());
    assertTrue(Address.fromLong(1).toInt() == 1);
    assertTrue(Address.fromLong(0xA0000000L).EQ(Address.fromIntZeroExtend(0xA0000000)));
    if (is64bit()) {
      assertTrue(Address.fromLong(0x00000000A0000000L).toString().equals("0x00000000A0000000"));
      assertTrue(Address.fromLong(0x000000A000000000L).toString().equals("0x000000A000000000"));
      assertTrue(Address.fromLong(0x0000A00000000000L).toString().equals("0x0000A00000000000"));
      assertTrue(Address.fromLong(0x00A0000000000000L).toString().equals("0x00A0000000000000"));
    }
  }

  @Test
  public void testToObjectReference() {
  }

  @Test
  public void testToInt() {
  }

  @Test
  public void testToLong() {
  }

  @Test
  public void testToWord() {
  }

  @Test
  public void testPlusInt() {
    assertTrue(Address.zero().plus(1).toInt() == 1);
    assertTrue(Address.zero().plus(-1).toInt() == -1);
  }

  @Test
  public void testPlusOffset() {
    Offset one = Offset.fromIntSignExtend(1);
    Offset minusOne = Offset.fromIntSignExtend(-1);
    assertTrue(Address.zero().plus(one).toInt() == 1);
    assertTrue(Address.zero().plus(minusOne).toInt() == -1);
  }

  @Test
  public void testPlusExtent() {
    Extent one = Extent.fromIntSignExtend(1);
    assertTrue(Address.zero().plus(one).toInt() == 1);
  }

  @Test
  public void testMinusInt() {
    assertTrue(Address.zero().minus(1).toInt() == -1);
    assertTrue(Address.zero().minus(-1).toInt() == 1);
  }

  @Test
  public void testMinusOffset() {
    Offset one = Offset.fromIntSignExtend(1);
    Offset minusOne = Offset.fromIntSignExtend(-1);
    assertTrue(Address.zero().minus(one).toInt() == -1);
    assertTrue(Address.zero().minus(minusOne).toInt() == 1);
  }

  @Test
  public void testMinusExtent() {
    Extent one = Extent.fromIntSignExtend(1);
    assertTrue(Address.zero().minus(one).toInt() == -1);
  }

  @Test
  public void testDiff() {
    Address addr1 = Address.fromIntSignExtend(200);
    Address addr2 = Address.fromIntSignExtend(100);
    assertTrue(addr1.diff(addr2).toInt() == 100);
    assertTrue(addr2.diff(addr1).toInt() == -100);
  }

  @Test
  public void testLT() {
    assertTrue(Address.fromIntZeroExtend(0x20000000).LT(Address.fromIntZeroExtend(0xa0000000)));
    assertTrue(zero.LT(one));
    assertTrue(one.LT(large));
    assertTrue(large.LT(veryLarge));
    assertTrue(veryLarge.LT(largest));
    assertFalse(zero.LT(zero));
    assertFalse(one.LT(one));
    assertFalse(large.LT(large));
    assertFalse(veryLarge.LT(veryLarge));
    assertFalse(largest.LT(largest));
    assertFalse(one.LT(zero));
    assertFalse(large.LT(one));
    assertFalse(veryLarge.LT(large));
    assertFalse(largest.LT(veryLarge));
  }

  @Test
  public void testLE() {
    assertTrue(zero.LE(one));
    assertTrue(one.LE(large));
    assertTrue(large.LE(veryLarge));
    assertTrue(veryLarge.LE(largest));
    assertTrue(zero.LE(zero));
    assertTrue(one.LE(one));
    assertTrue(large.LE(large));
    assertTrue(veryLarge.LE(veryLarge));
    assertTrue(largest.LE(largest));
    assertFalse(one.LE(zero));
    assertFalse(large.LE(one));
    assertFalse(veryLarge.LE(large));
    assertFalse(largest.LE(veryLarge));
  }

  @Test
  public void testGT() {
    assertFalse(Address.fromIntZeroExtend(0x20000000).GT(Address.fromIntZeroExtend(0xa0000000)));
    assertFalse(zero.GT(one));
    assertFalse(one.GT(large));
    assertFalse(large.GT(veryLarge));
    assertFalse(veryLarge.GT(largest));
    assertFalse(zero.GT(zero));
    assertFalse(one.GT(one));
    assertFalse(large.GT(large));
    assertFalse(veryLarge.GT(veryLarge));
    assertFalse(largest.GT(largest));
    assertTrue(one.GT(zero));
    assertTrue(large.GT(one));
    assertTrue(veryLarge.GT(large));
    assertTrue(largest.GT(veryLarge));
  }

  @Test
  public void testGE() {
    assertFalse(zero.GE(one));
    assertFalse(one.GE(large));
    assertFalse(large.GE(veryLarge));
    assertFalse(veryLarge.GE(largest));
    assertTrue(zero.GE(zero));
    assertTrue(one.GE(one));
    assertTrue(large.GE(large));
    assertTrue(veryLarge.GE(veryLarge));
    assertTrue(largest.GE(largest));
    assertTrue(one.GE(zero));
    assertTrue(large.GE(one));
    assertTrue(veryLarge.GE(large));
    assertTrue(largest.GE(veryLarge));
  }

  @Test
  public void testEQ() {
    Address[] lefties = new Address[] {
        zero, one, large, veryLarge, largest
    };
    Address[] righties = new Address[] {
        zero(), one(), large(), veryLarge(), largest()
    };
    for (int i=0; i < lefties.length; i++) {
      for (int j=0; j < righties.length; j++) {
        if (i == j)
          assertTrue(lefties[i].EQ(righties[j]));
        else
          assertFalse(lefties[i].EQ(righties[j]));
      }
    }
  }

  @Test
  public void testNE() {
    Address[] lefties = new Address[] {
        zero, one, large, veryLarge, largest
    };
    Address[] righties = new Address[] {
        zero(), one(), large(), veryLarge(), largest()
    };
    for (int i=0; i < lefties.length; i++) {
      for (int j=0; j < righties.length; j++) {
        if (i == j)
          assertFalse(lefties[i].NE(righties[j]));
        else
          assertTrue(lefties[i].NE(righties[j]));
      }
    }
  }

  @Test
  public void testToString() {
    if (!is64bit()) {
      assertEquals(Address.zero().toString(),"0x00000000");
      assertEquals(Address.max().toString(),"0xFFFFFFFF");
    } else {
      assertEquals(Address.zero().toString(),"0x0000000000000000");
      assertEquals(Address.max().toString(),"0xFFFFFFFFFFFFFFFF");
    }
  }

  @Test
  public void misc1() {
    Address AVAILABLE_END = Address.fromIntZeroExtend(0xA0000000);
    Address AVAILABLE_START = Address.fromIntZeroExtend(0x20000000);
    Extent AVAILABLE_BYTES = AVAILABLE_END.toWord().minus(AVAILABLE_START.toWord()).toExtent();
    assertEquals(AVAILABLE_BYTES.toString(),Address.fromIntZeroExtend(0x80000000).toString());
    float frac = 0.6f;
    final int LOG_BYTES_IN_MBYTE = 20;
    long bytes = (long) (frac * AVAILABLE_BYTES.toLong());
    Word mb = Word.fromIntSignExtend((int) (bytes >> LOG_BYTES_IN_MBYTE));
    Extent rtn = mb.lsh(LOG_BYTES_IN_MBYTE).toExtent();
  }
}
