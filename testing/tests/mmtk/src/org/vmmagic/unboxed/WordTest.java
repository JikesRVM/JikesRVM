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
import org.junit.Test;
import org.mmtk.harness.Harness;
import org.vmmagic.unboxed.harness.ArchitecturalWord;
import org.vmmagic.unboxed.harness.MemoryConstants;

public class WordTest {

  private static boolean is32bit() {
    return MemoryConstants.BYTES_IN_WORD == 4;
  }

  private static boolean is64bit() {
    return MemoryConstants.BYTES_IN_WORD == 8;
  }

  @Before
  public void setUp() throws Exception {
    ArchitecturalWord.init(Harness.bits.getValue());
  }

  @Test
  public void testWord() {
    assertTrue(Word.zero().toInt() == new Word(0).toInt());
    assertTrue(Word.one().toInt() == new Word(1).toInt());
  }

  @Test
  public void testFromIntSignExtend() {
    assertTrue(Word.fromIntSignExtend(0x80000000).EQ(Word.fromLong(0x80000000)));
    if (is32bit()) {
      assertTrue(Word.fromIntSignExtend(0x80000000).EQ(Word.fromLong(0x80000000L)));
    } else {
      assertFalse(Word.fromIntSignExtend(0x80000000).EQ(Word.fromLong(0x80000000L)));
    }
  }

  @Test
  public void testFromIntZeroExtend() {
    assertTrue(Word.fromIntZeroExtend(0x80000000).EQ(Word.fromLong(0x80000000L)));
    if (is32bit()) {
      assertTrue(Word.fromIntZeroExtend(0x80000000).EQ(Word.fromLong(0x80000000)));
    } else {
      assertFalse(Word.fromIntZeroExtend(0x80000000).EQ(Word.fromLong(0x80000000)));
    }
  }

  @Test
  public void testFromLong() {
    if (is32bit()) {
      assertTrue(Word.fromLong(0xF00000000L).EQ(Word.zero()));
    } else {
      assertTrue(Word.fromLong(0xF00000000L).NE(Word.zero()));
    }
  }

  @Test
  public void testZero() {
    assertTrue(Word.zero().toInt() == 0);
  }

  @Test
  public void testOne() {
    assertTrue(Word.one().toInt() == 1);
  }

  @Test
  public void testMax() {
    // Check semantics!!
    assertTrue(Word.max().toInt() == -1);
  }

  @Test
  public void testToInt() {
    for (int i=0; i < 32; i++) {
      assertTrue(Word.fromIntZeroExtend(1<<i).toInt() == (1<<i));
    }
  }

  @Test
  public void testToLong() {
    for (int i=0; i < 8*MemoryConstants.BYTES_IN_WORD; i++) {
      assertTrue(Word.fromLong(1L<<i).toLong() == (1L<<i));
    }
  }

  @Test
  public void testToAddress() {
    for (int i=0; i < 8*MemoryConstants.BYTES_IN_WORD; i++) {
      assertTrue(Word.fromLong(1L<<i).toAddress().EQ(Address.fromLong(1L<<i)));
    }
  }

  @Test
  public void testToOffset() {
    for (int i=0; i < 8*MemoryConstants.BYTES_IN_WORD; i++) {
      assertTrue(Word.fromLong(1L<<i).toOffset().EQ(
          Address.fromLong(1L<<i).diff(Address.zero())));
    }
  }

  @Test
  public void testToExtent() {
    for (int i=0; i < 32; i++) {
      assertTrue(Word.fromLong(1<<i).toExtent().EQ(Extent.fromIntSignExtend(1<<i)));
    }
  }

  @Test
  public void testPlusWord() {
    assertTrue(Word.zero().plus(Word.zero()).EQ(Word.zero()));
    assertTrue(Word.zero().plus(Word.one()).EQ(Word.one()));
    assertTrue(Word.one().plus(Word.zero()).EQ(Word.one()));
    assertTrue(Word.max().plus(Word.one()).EQ(Word.zero()));
    assertTrue(Word.one().plus(Word.max()).EQ(Word.zero()));
    if (is64bit()) {
      assertTrue(Word.fromIntZeroExtend(0xFFFFFFFF).plus(Word.one()).EQ(Word.fromLong(0x100000000L)));
      assertTrue(Word.one().plus(Word.fromIntZeroExtend(0xFFFFFFFF)).EQ(Word.fromLong(0x100000000L)));
    }
  }

  @Test
  public void testPlusOffset() {
    Offset one = Offset.fromIntSignExtend(1);
    Offset minusOne = Offset.fromIntSignExtend(-1);
    assertTrue(Word.zero().plus(Offset.zero()).EQ(Word.zero()));
    assertTrue(Word.zero().plus(one).EQ(Word.one()));
    assertTrue(Word.one().plus(minusOne).EQ(Word.zero()));
    assertTrue(Word.max().plus(one).EQ(Word.zero()));
    if (is64bit()) {
      assertTrue(Word.fromIntZeroExtend(0xFFFFFFFF).plus(one).EQ(Word.fromLong(0x100000000L)));
    }
  }

  @Test
  public void testPlusExtent() {
    assertTrue(Word.zero().plus(Extent.zero()).EQ(Word.zero()));
    assertTrue(Word.zero().plus(Extent.one()).EQ(Word.one()));
    assertTrue(Word.max().plus(Extent.one()).EQ(Word.zero()));
    assertTrue(Word.one().plus(Extent.max()).EQ(Word.zero()));
    if (is64bit()) {
      assertTrue(Word.fromIntZeroExtend(0xFFFFFFFF).plus(Extent.one()).EQ(Word.fromLong(0x100000000L)));
      assertTrue(Word.one().plus(Extent.fromIntZeroExtend(0xFFFFFFFF)).EQ(Word.fromLong(0x100000000L)));
    }
  }

  @Test
  public void testMinusWord() {
    assertTrue(Word.zero().minus(Word.zero()).EQ(Word.zero()));
    assertTrue(Word.zero().minus(Word.one()).EQ(Word.max()));
    assertTrue(Word.one().minus(Word.zero()).EQ(Word.one()));
    assertTrue(Word.zero().minus(Word.max()).EQ(Word.one()));
    if (is64bit()) {
      assertTrue(Word.fromLong(0x100000000L).minus(Word.one()).EQ(Word.fromIntZeroExtend(0xFFFFFFFF)));
    }
  }

  @Test
  public void testMinusOffset() {
    Offset one = Offset.fromIntSignExtend(1);
    Offset minusOne = Offset.fromIntSignExtend(-1);
    assertTrue(Word.zero().minus(Offset.zero()).EQ(Word.zero()));
    assertTrue(Word.zero().minus(one).EQ(Word.max()));
    assertTrue(Word.one().minus(one).EQ(Word.zero()));
    assertTrue(Word.max().minus(minusOne).EQ(Word.zero()));
    if (is64bit()) {
      assertTrue(Word.fromIntZeroExtend(0xFFFFFFFF).minus(minusOne).EQ(Word.fromLong(0x100000000L)));
      assertTrue(Word.fromLong(0x100000000L).minus(one).EQ(Word.fromIntZeroExtend(0xFFFFFFFF)));
    }
  }

  @Test
  public void testMinusExtent() {
    assertTrue(Word.zero().minus(Extent.zero()).EQ(Word.zero()));
    assertTrue(Word.zero().minus(Extent.one()).EQ(Word.max()));
    assertTrue(Word.zero().minus(Extent.max()).EQ(Word.one()));
    if (is64bit()) {
      assertTrue(Word.fromLong(0x100000000L).minus(Extent.one()).EQ(Word.fromIntZeroExtend(0xFFFFFFFF)));
    }
  }

  @Test
  public void testIsZero() {
    assertTrue(Word.zero().isZero());
    assertTrue(Word.fromIntSignExtend(0).isZero());
    assertTrue(Word.fromIntZeroExtend(0).isZero());
    assertFalse(Word.one().isZero());
  }

  @Test
  public void testIsMax() {
    assertTrue(Word.max().isMax());
    assertTrue(Word.fromIntSignExtend(-1).isMax());
    if (is64bit()) {
      assertTrue(Word.fromLong(-1L).isMax());
    }
  }

  @Test
  public void testLT() {
    Word word1 = Word.fromIntSignExtend(567787565);
    Word word2 = Word.fromIntSignExtend(-768686677);
    assertTrue(Word.zero().LT(Word.one()));
    assertTrue(Word.one().LT(Word.max()));
    assertTrue(Word.one().LT(word1));
    assertTrue(word1.LT(word2));
    assertTrue(word2.LT(Word.max()));
    assertFalse(Word.max().LT(word1));
    assertFalse(word2.LT(Word.zero()));
  }

  @Test
  public void testLE() {
    Word word1 = Word.fromIntSignExtend(567787565);
    Word word2 = Word.fromIntSignExtend(-768686677);
    assertTrue(Word.zero().LE(Word.one()));
    assertTrue(Word.one().LE(Word.max()));
    assertTrue(Word.one().LE(word1));
    assertTrue(word1.LE(word2));
    assertTrue(word2.LE(Word.max()));
    assertFalse(Word.max().LE(word1));
    assertFalse(word2.LE(Word.zero()));

    assertTrue(Word.zero().LE(Word.zero()));
    assertTrue(Word.one().LE(Word.one()));
    assertTrue(Word.max().LE(Word.max()));
    assertTrue(word1.LE(word1));
    assertTrue(word2.LE(word2));
  }

  @Test
  public void testGT() {
    Word word1 = Word.fromIntSignExtend(567787565);
    Word word2 = Word.fromIntSignExtend(-768686677);
    assertFalse(Word.zero().GT(Word.one()));
    assertFalse(Word.one().GT(Word.max()));
    assertFalse(Word.one().GT(word1));
    assertFalse(word1.GT(word2));
    assertFalse(word2.GT(Word.max()));
    assertTrue(Word.max().GT(word1));
    assertTrue(word2.GT(Word.zero()));
  }

  @Test
  public void testGE() {
    Word word1 = Word.fromIntSignExtend(567787565);
    Word word2 = Word.fromIntSignExtend(-768686677);
    assertFalse(Word.zero().GE(Word.one()));
    assertFalse(Word.one().GE(Word.max()));
    assertFalse(Word.one().GE(word1));
    assertFalse(word1.GE(word2));
    assertFalse(word2.GE(Word.max()));
    assertTrue(Word.max().GE(word1));
    assertTrue(word2.GE(Word.zero()));

    assertTrue(Word.zero().GE(Word.zero()));
    assertTrue(Word.one().GE(Word.one()));
    assertTrue(Word.max().GE(Word.max()));
    assertTrue(word1.GE(word1));
    assertTrue(word2.GE(word2));
}

  @Test
  public void testEQ() {
    assertTrue(Word.zero().EQ(Word.zero()));
    assertTrue(Word.zero().EQ(Word.fromIntZeroExtend(0)));
    assertTrue(Word.zero().EQ(Word.fromIntSignExtend(0)));
    assertTrue(Word.max().EQ(Word.fromIntSignExtend(-1)));
    if (is64bit()) {
      assertTrue(Word.fromLong(-1L).EQ(Word.max()));
    }
  }

  @Test
  public void testNE() {
    assertFalse(Word.zero().NE(Word.zero()));
    assertFalse(Word.zero().NE(Word.fromIntZeroExtend(0)));
    assertFalse(Word.zero().NE(Word.fromIntSignExtend(0)));
    assertFalse(Word.max().NE(Word.fromIntSignExtend(-1)));
    if (is64bit()) {
      assertFalse(Word.fromLong(-1L).NE(Word.max()));
    }
  }

  @Test
  public void testAnd() {
    assertTrue(Word.zero().and(Word.zero()).EQ(Word.zero()));
    assertTrue(Word.zero().and(Word.one()).EQ(Word.zero()));
    assertTrue(Word.one().and(Word.zero()).EQ(Word.zero()));
    assertTrue(Word.one().and(Word.one()).EQ(Word.one()));
    assertTrue(Word.max().and(Word.zero()).EQ(Word.zero()));
    assertTrue(Word.max().and(Word.one()).EQ(Word.one()));
    assertTrue(Word.one().and(Word.max()).EQ(Word.one()));
    assertTrue(Word.max().and(Word.max()).EQ(Word.max()));
  }

  @Test
  public void testOr() {
    assertTrue(Word.zero().or(Word.zero()).EQ(Word.zero()));
    assertTrue(Word.zero().or(Word.one()).EQ(Word.one()));
    assertTrue(Word.one().or(Word.zero()).EQ(Word.one()));
    assertTrue(Word.one().or(Word.one()).EQ(Word.one()));
    assertTrue(Word.max().or(Word.zero()).EQ(Word.max()));
    assertTrue(Word.max().or(Word.one()).EQ(Word.max()));
    assertTrue(Word.one().or(Word.max()).EQ(Word.max()));
    assertTrue(Word.max().or(Word.max()).EQ(Word.max()));
  }

  @Test
  public void testNot() {
    assertTrue(Word.zero().not().EQ(Word.max()));
    assertTrue(Word.max().not().EQ(Word.zero()));
    assertTrue(Word.one().not().EQ(Word.fromIntSignExtend(0xFFFFFFFE)));
  }

  @Test
  public void testXor() {
    assertTrue(Word.zero().xor(Word.zero()).EQ(Word.zero()));
    assertTrue(Word.zero().xor(Word.one()).EQ(Word.one()));
    assertTrue(Word.one().xor(Word.zero()).EQ(Word.one()));
    assertTrue(Word.one().xor(Word.one()).EQ(Word.zero()));
    assertTrue(Word.max().xor(Word.zero()).EQ(Word.max()));
    assertTrue(Word.max().xor(Word.one()).EQ(Word.fromIntSignExtend(0xFFFFFFFE)));
    assertTrue(Word.one().xor(Word.max()).EQ(Word.fromIntSignExtend(0xFFFFFFFE)));
    assertTrue(Word.max().xor(Word.max()).EQ(Word.zero()));
  }

  @Test
  public void testLsh() {
    for (int i=0; i < 32; i++) {
      assertTrue(Word.one().lsh(i).toInt() == (1<<i));
      assertTrue(Word.one().lsh(i).toLong() == (1L<<i));
    }
    if (is64bit()) {
      for (int i=0; i < 64; i++) {
        assertTrue(Word.one().lsh(i).toLong() == (1L<<i));
      }
    }
  }

  @Test
  public void testRshl() {
    for (int i=0; i < 32; i++) {
      assertTrue(Word.one().lsh(i).rshl(i).EQ(Word.one()));
    }
    if (is64bit()) {
      for (int i=0; i < 64; i++) {
        assertTrue(Word.one().lsh(i).rshl(i).EQ(Word.one()));
      }
    }
  }

  @Test
  public void testRsha() {
    for (int i=0; i < 31; i++) {
      assertTrue(Word.one().lsh(i).rsha(i).EQ(Word.one()));
    }
    if (is32bit()) {
      assertTrue(Word.one().lsh(31).rsha(31).EQ(Word.max()));
    } else {
      for (int i=0; i < 63; i++) {
        assertTrue(Word.one().lsh(i).rsha(i).EQ(Word.one()));
      }
      assertTrue(Word.one().lsh(63).rsha(63).EQ(Word.max()));
    }
  }

  @Test
  public void testToString() {
    if (is32bit()) {
      assertEquals(Word.zero().toString(),"0x00000000");
      assertEquals(Word.one().toString(),"0x00000001");
      assertEquals(Word.one().lsh(1).toString(),"0x00000002");
      assertEquals(Word.one().lsh(2).toString(),"0x00000004");
      assertEquals(Word.one().lsh(3).toString(),"0x00000008");
      assertEquals(Word.one().lsh(4).toString(),"0x00000010");
      assertEquals(Word.one().lsh(5).toString(),"0x00000020");
      assertEquals(Word.max().toString(),"0xFFFFFFFF");
      assertEquals(Word.one().lsh(3).or(Word.one().lsh(2)).toString(),"0x0000000C");
    } else {
      assertEquals(Word.zero().toString(),"0x0000000000000000");
      assertEquals(Word.one().toString(),"0x0000000000000001");
      assertEquals(Word.one().lsh(1).toString(),"0x0000000000000002");
      assertEquals(Word.one().lsh(2).toString(),"0x0000000000000004");
      assertEquals(Word.one().lsh(3).toString(),"0x0000000000000008");
      assertEquals(Word.one().lsh(4).toString(),"0x0000000000000010");
      assertEquals(Word.one().lsh(5).toString(),"0x0000000000000020");
      assertEquals(Word.max().toString(),"0xFFFFFFFFFFFFFFFF");
      assertEquals(Word.one().lsh(3).or(Word.one().lsh(2)).toString(),"0x000000000000000C");
      assertEquals(Word.one().lsh(45).toString(),"0x0000200000000000");
    }
  }

}
