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
import org.vmmagic.unboxed.harness.MemoryConstants;
import org.vmmagic.unboxed.harness.SimulatedMemory;

public class AddressTest {

  private static final char CHAR_CONST2 = (char)0xcafe;
  private static final char CHAR_CONST1 = (char)25;
  private static final byte BYTE_CONST2 = (byte)0xca;
  private static final byte BYTE_CONST1 = (byte)25;
  private static final double DOUBLE_CONST2 = 7.36573648929734E65;
  private static final double DOUBLE_CONST1 = 25.0;
  private static final long LONG_CONST2 = 0xcafebabedeadbeefL;
  private static final long LONG_CONST1 = 25L;
  private static final int INT_CONST2 = 0xcafebabe;
  private static final int INT_CONST1 = 25;
  private static final float FLOAT_CONST2 = 7.2E9f;
  private static final float FLOAT_CONST1 = 1.759376f;
  private static final short SHORT_CONST2 = (short)0xcafe;
  private static final short SHORT_CONST1 = 25;
  private static final int HIGH_TEST_PAGE = 0xF0000000;
  private static final int LOW_TEST_PAGE = 0x10000;

  // Eliminate mysterious numeric constants ...
  //
  // Some of these can be imported from other modules, but I
  // don't want to have to negotiate around initialization races.
  private static final int BYTES_IN_BYTE = 1;
  private static final int BYTES_IN_CHAR = 2;
  private static final int BYTES_IN_SHORT = 2;
  private static final int BYTES_IN_INT = 4;
  private static final int BYTES_IN_FLOAT = 4;
  private static final int BYTES_IN_LONG = 8;
  private static final int BYTES_IN_DOUBLE = 8;

  private static final int BYTES_IN_PAGE = 4096;

  private static boolean is64bit() {
    return ArchitecturalWord.getModel() == Architecture.BITS64;
  }

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    Harness.init("plan=org.mmtk.plan.nogc.NoGC","bits=32");
//    Harness.init("plan=org.mmtk.plan.nogc.NoGC","bits=64");
//    SimulatedMemory.addWatch(Address.fromIntSignExtend(LOW_TEST_PAGE), BYTES_IN_PAGE);
//    SimulatedMemory.addWatch(Address.fromIntSignExtend(HIGH_TEST_PAGE), BYTES_IN_PAGE);
    SimulatedMemory.map(Address.fromIntSignExtend(LOW_TEST_PAGE), BYTES_IN_PAGE);
    SimulatedMemory.map(Address.fromIntSignExtend(HIGH_TEST_PAGE), BYTES_IN_PAGE);
  }

  private static void zeroTestPages() {
    SimulatedMemory.zero(Address.fromIntSignExtend(LOW_TEST_PAGE), BYTES_IN_PAGE);
    SimulatedMemory.zero(Address.fromIntSignExtend(HIGH_TEST_PAGE), BYTES_IN_PAGE);
  }

  final Address zero = Address.zero();
  final Address one = Address.fromIntSignExtend(1);
  final Address large = Address.fromIntSignExtend(Integer.MAX_VALUE);
  final Address veryLarge = Address.fromIntSignExtend(Integer.MIN_VALUE);
  final Address largest = Address.max();

  @Test
  public void testLoadObjectReference() {
    zeroTestPages();
    Address lowAddr = Address.fromIntSignExtend(LOW_TEST_PAGE);
    Address highAddr = Address.fromIntSignExtend(HIGH_TEST_PAGE);
    assertTrue(lowAddr.loadObjectReference().isNull());
    assertTrue(highAddr.loadObjectReference().isNull());
    lowAddr.store(Address.fromIntSignExtend(INT_CONST1).toObjectReference());
    highAddr.store(Address.fromIntZeroExtend(INT_CONST2).toObjectReference());
    assertTrue(highAddr.loadObjectReference().toAddress().toLong() == (INT_CONST2&0xFFFFFFFFL));
    if (is64bit()) {
      highAddr.store(Address.fromIntSignExtend(INT_CONST2).toObjectReference());
      assertTrue(highAddr.loadObjectReference().toAddress().toLong() == INT_CONST2);
    }
  }

  @Test
  public void testLoadObjectReferenceOffset() {
    zeroTestPages();
    Address lowAddr = Address.fromIntSignExtend(LOW_TEST_PAGE);
    Address highAddr = Address.fromIntSignExtend(HIGH_TEST_PAGE);
    Address lowAddrEnd = lowAddr.plus(BYTES_IN_PAGE);
    Address highAddrEnd = highAddr.plus(BYTES_IN_PAGE);
    for (int i=0; i < MemoryConstants.BYTES_IN_PAGE/MemoryConstants.BYTES_IN_WORD; i++) {
      Offset offset = Offset.fromIntSignExtend(i*MemoryConstants.BYTES_IN_WORD);
      Offset negOffset = Offset.fromIntSignExtend(-BYTES_IN_PAGE+i*MemoryConstants.BYTES_IN_WORD);
      assertTrue(lowAddr.loadObjectReference(offset).isNull());
      assertTrue(highAddr.loadObjectReference(offset).isNull());
      assertTrue(lowAddrEnd.loadObjectReference(negOffset).isNull());
      assertTrue(highAddrEnd.loadObjectReference(negOffset).isNull());
      lowAddr.plus(offset).store(Address.fromIntSignExtend(INT_CONST1).toObjectReference());
      highAddr.plus(offset).store(Address.fromIntZeroExtend(INT_CONST2).toObjectReference());
      assertTrue(lowAddr.loadObjectReference(offset).toAddress().toInt() == INT_CONST1);
      assertTrue(highAddr.loadObjectReference(offset).toAddress().toInt() == INT_CONST2);
      assertTrue(lowAddrEnd.loadObjectReference(negOffset).toAddress().toLong() == (INT_CONST1&0xFFFFFFFFL));
      assertTrue(highAddr.loadObjectReference().toAddress().toLong() == (INT_CONST2&0xFFFFFFFFL));
    }
  }

  @Test
  public void testLoadByte() {
    zeroTestPages();
    Address lowAddr = Address.fromIntSignExtend(LOW_TEST_PAGE);
    Address highAddr = Address.fromIntSignExtend(HIGH_TEST_PAGE);
    assertEquals((byte)0,lowAddr.loadByte());
    assertEquals((byte)0,highAddr.loadByte());
    lowAddr.store(BYTE_CONST1);
    highAddr.store(BYTE_CONST2);
    assertEquals(BYTE_CONST1,lowAddr.loadByte());
    assertEquals(BYTE_CONST2,highAddr.loadByte());
  }

  @Test
  public void testLoadByteOffset() {
    zeroTestPages();
    Address lowAddr = Address.fromIntSignExtend(LOW_TEST_PAGE);
    Address highAddr = Address.fromIntSignExtend(HIGH_TEST_PAGE);
    Address lowAddrEnd = lowAddr.plus(BYTES_IN_PAGE);
    Address highAddrEnd = highAddr.plus(BYTES_IN_PAGE);
    for (int i=0; i < MemoryConstants.BYTES_IN_PAGE/BYTES_IN_BYTE; i++) {
      Offset offset = Offset.fromIntSignExtend(i*BYTES_IN_BYTE);
      Offset negOffset = Offset.fromIntSignExtend(-BYTES_IN_PAGE+i*BYTES_IN_BYTE);
      assertEquals((byte)0,lowAddr.loadByte(offset));
      assertEquals((byte)0,highAddr.loadByte(offset));
      assertEquals((byte)0,lowAddrEnd.loadByte(negOffset));
      assertEquals((byte)0,highAddrEnd.loadByte(negOffset));
      lowAddr.plus(offset).store(BYTE_CONST1);
      highAddr.plus(offset).store(BYTE_CONST2);
      assertEquals(BYTE_CONST1,lowAddr.loadByte(offset));
      assertEquals(BYTE_CONST2,highAddr.loadByte(offset));
      assertEquals(BYTE_CONST1,lowAddrEnd.loadByte(negOffset));
      assertEquals(BYTE_CONST2,highAddrEnd.loadByte(negOffset));
    }
  }

//  @Test
//  public void testLoadChar() {
//    fail("Not yet implemented");
//  }

  @Test
  public void testLoadCharOffset() {
    zeroTestPages();
    Address lowAddr = Address.fromIntSignExtend(LOW_TEST_PAGE);
    Address highAddr = Address.fromIntSignExtend(HIGH_TEST_PAGE);
    Address lowAddrEnd = lowAddr.plus(BYTES_IN_PAGE);
    Address highAddrEnd = highAddr.plus(BYTES_IN_PAGE);
    for (int i=0; i < MemoryConstants.BYTES_IN_PAGE/BYTES_IN_CHAR; i++) {
      Offset offset = Offset.fromIntSignExtend(i*BYTES_IN_CHAR);
      Offset negOffset = Offset.fromIntSignExtend(-BYTES_IN_PAGE+i*BYTES_IN_CHAR);
      assertEquals((char)0,lowAddr.loadChar(offset));
      assertEquals((char)0,highAddr.loadChar(offset));
      assertEquals((char)0,lowAddrEnd.loadChar(negOffset));
      assertEquals((char)0,highAddrEnd.loadChar(negOffset));
      lowAddr.plus(offset).store(CHAR_CONST1);
      highAddr.plus(offset).store(CHAR_CONST2);
      assertEquals(CHAR_CONST1,lowAddr.loadChar(offset));
      assertEquals(CHAR_CONST2,highAddr.loadChar(offset));
      assertEquals(CHAR_CONST1,lowAddrEnd.loadChar(negOffset));
      assertEquals(CHAR_CONST2,highAddrEnd.loadChar(negOffset));
    }
  }

//  @Test
//  public void testLoadShort() {
//    fail("Not yet implemented");
//  }

  @Test
  public void testLoadShortOffset() {
    zeroTestPages();
    Address lowAddr = Address.fromIntSignExtend(LOW_TEST_PAGE);
    Address highAddr = Address.fromIntSignExtend(HIGH_TEST_PAGE);
    Address lowAddrEnd = lowAddr.plus(BYTES_IN_PAGE);
    Address highAddrEnd = highAddr.plus(BYTES_IN_PAGE);
    for (int i=0; i < MemoryConstants.BYTES_IN_PAGE/BYTES_IN_SHORT; i++) {
      Offset offset = Offset.fromIntSignExtend(i*BYTES_IN_SHORT);
      Offset negOffset = Offset.fromIntSignExtend(-BYTES_IN_PAGE+i*BYTES_IN_SHORT);
      assertEquals((short)0,lowAddr.loadShort(offset));
      assertEquals((short)0,highAddr.loadShort(offset));
      assertEquals((short)0,lowAddrEnd.loadShort(negOffset));
      assertEquals((short)0,highAddrEnd.loadShort(negOffset));
      lowAddr.plus(offset).store(SHORT_CONST1);
      highAddr.plus(offset).store(SHORT_CONST2);
      assertEquals(SHORT_CONST1,lowAddr.loadShort(offset));
      assertEquals(SHORT_CONST2,highAddr.loadShort(offset));
      assertEquals(SHORT_CONST1,lowAddrEnd.loadShort(negOffset));
      assertEquals(SHORT_CONST2,highAddrEnd.loadShort(negOffset));
    }
  }

//  @Test
//  public void testLoadFloat() {
//    fail("Not yet implemented");
//  }

  @Test
  public void testLoadFloatOffset() {
    zeroTestPages();
    Address lowAddr = Address.fromIntSignExtend(LOW_TEST_PAGE);
    Address highAddr = Address.fromIntSignExtend(HIGH_TEST_PAGE);
    Address lowAddrEnd = lowAddr.plus(BYTES_IN_PAGE);
    Address highAddrEnd = highAddr.plus(BYTES_IN_PAGE);
    for (int i=0; i < MemoryConstants.BYTES_IN_PAGE/BYTES_IN_FLOAT; i++) {
      Offset offset = Offset.fromIntSignExtend(i*BYTES_IN_FLOAT);
      Offset negOffset = Offset.fromIntSignExtend(-BYTES_IN_PAGE+i*BYTES_IN_FLOAT);
      assertEquals(0.0f,lowAddr.loadFloat(offset),0.0f);
      assertEquals(0.0f,highAddr.loadFloat(offset),0.0f);
      assertEquals(0.0f,lowAddrEnd.loadFloat(negOffset),0.0f);
      assertEquals(0.0f,highAddrEnd.loadFloat(negOffset),0.0f);
      lowAddr.plus(offset).store(FLOAT_CONST1);
      highAddr.plus(offset).store(FLOAT_CONST2);
      assertEquals(FLOAT_CONST1,lowAddr.loadFloat(offset),0.0f);
      assertEquals(FLOAT_CONST2,highAddr.loadFloat(offset),0.0f);
      assertEquals(FLOAT_CONST1,lowAddrEnd.loadFloat(negOffset),0.0f);
      assertEquals(FLOAT_CONST2,highAddrEnd.loadFloat(negOffset),0.0f);
    }
  }

//  @Test
//  public void testLoadInt() {
//    fail("Not yet implemented");
//  }

  @Test
  public void testLoadIntOffset() {
    zeroTestPages();
    Address lowAddr = Address.fromIntSignExtend(LOW_TEST_PAGE);
    Address highAddr = Address.fromIntSignExtend(HIGH_TEST_PAGE);
    Address lowAddrEnd = lowAddr.plus(BYTES_IN_PAGE);
    Address highAddrEnd = highAddr.plus(BYTES_IN_PAGE);
    for (int i=0; i < MemoryConstants.BYTES_IN_PAGE/BYTES_IN_INT; i++) {
      Offset offset = Offset.fromIntSignExtend(i*BYTES_IN_INT);
      Offset negOffset = Offset.fromIntSignExtend(-BYTES_IN_PAGE+i*BYTES_IN_INT);
      assertEquals(0,lowAddr.loadInt(offset));
      assertEquals(0,highAddr.loadInt(offset));
      assertEquals(0,lowAddrEnd.loadInt(negOffset));
      assertEquals(0,highAddrEnd.loadInt(negOffset));
      lowAddr.plus(offset).store(INT_CONST1);
      highAddr.plus(offset).store(INT_CONST2);
      assertEquals(INT_CONST1,lowAddr.loadInt(offset));
      assertEquals(INT_CONST2,highAddr.loadInt(offset));
      assertEquals(INT_CONST1,lowAddrEnd.loadInt(negOffset));
      assertEquals(INT_CONST2,highAddrEnd.loadInt(negOffset));
    }
  }

//  @Test
//  public void testLoadLong() {
//    fail("Not yet implemented");
//  }

  @Test
  public void testLoadLongOffset() {
    zeroTestPages();
    Address lowAddr = Address.fromIntSignExtend(LOW_TEST_PAGE);
    Address highAddr = Address.fromIntSignExtend(HIGH_TEST_PAGE);
    Address lowAddrEnd = lowAddr.plus(BYTES_IN_PAGE);
    Address highAddrEnd = highAddr.plus(BYTES_IN_PAGE);
    for (int i=0; i < MemoryConstants.BYTES_IN_PAGE/BYTES_IN_LONG; i++) {
      Offset offset = Offset.fromIntSignExtend(i*BYTES_IN_LONG);
      Offset negOffset = Offset.fromIntSignExtend(-BYTES_IN_PAGE+i*BYTES_IN_LONG);
      assertEquals(0L,lowAddr.loadLong(offset));
      assertEquals(0L,highAddr.loadLong(offset));
      assertEquals(0L,lowAddrEnd.loadLong(negOffset));
      assertEquals(0L,highAddrEnd.loadLong(negOffset));
      lowAddr.plus(offset).store(LONG_CONST1);
      highAddr.plus(offset).store(LONG_CONST2);
      assertEquals(LONG_CONST1,lowAddr.loadLong(offset));
      assertEquals(LONG_CONST2,highAddr.loadLong(offset));
      assertEquals(LONG_CONST1,lowAddrEnd.loadLong(negOffset));
      assertEquals(LONG_CONST2,highAddrEnd.loadLong(negOffset));
    }
  }

//  @Test
//  public void testLoadDouble() {
//    fail("Not yet implemented");
//  }
//
  @Test
  public void testLoadDoubleOffset() {
    zeroTestPages();
    Address lowAddr = Address.fromIntSignExtend(LOW_TEST_PAGE);
    Address highAddr = Address.fromIntSignExtend(HIGH_TEST_PAGE);
    Address lowAddrEnd = lowAddr.plus(BYTES_IN_PAGE);
    Address highAddrEnd = highAddr.plus(BYTES_IN_PAGE);
    for (int i=0; i < MemoryConstants.BYTES_IN_PAGE/BYTES_IN_DOUBLE; i++) {
      Offset offset = Offset.fromIntSignExtend(i*BYTES_IN_DOUBLE);
      Offset negOffset = Offset.fromIntSignExtend(-BYTES_IN_PAGE+i*BYTES_IN_DOUBLE);
      assertEquals(0.0,lowAddr.loadDouble(offset),0.0);
      assertEquals(0.0,highAddr.loadDouble(offset),0.0);
      assertEquals(0.0,lowAddrEnd.loadDouble(negOffset),0.0);
      assertEquals(0.0,highAddrEnd.loadDouble(negOffset),0.0);
      lowAddr.plus(offset).store(DOUBLE_CONST1);
      highAddr.plus(offset).store(DOUBLE_CONST2);
      assertEquals(DOUBLE_CONST1,lowAddr.loadDouble(offset),0.0);
      assertEquals(DOUBLE_CONST2,highAddr.loadDouble(offset),0.0);
      assertEquals(DOUBLE_CONST1,lowAddrEnd.loadDouble(negOffset),0.0);
      assertEquals(DOUBLE_CONST2,highAddrEnd.loadDouble(negOffset),0.0);
    }
  }

//  @Test
//  public void testLoadAddress() {
//    fail("Not yet implemented");
//  }

  @Test
  public void testLoadAddressOffset() {
    zeroTestPages();
    Address lowAddr = Address.fromIntSignExtend(LOW_TEST_PAGE);
    Address highAddr = Address.fromIntSignExtend(HIGH_TEST_PAGE);
    Address lowAddrEnd = lowAddr.plus(BYTES_IN_PAGE);
    Address highAddrEnd = highAddr.plus(BYTES_IN_PAGE);
    for (int i=0; i < MemoryConstants.BYTES_IN_PAGE/MemoryConstants.BYTES_IN_WORD; i++) {
      Offset offset = Offset.fromIntSignExtend(i*MemoryConstants.BYTES_IN_WORD);
      Offset negOffset = Offset.fromIntSignExtend(-BYTES_IN_PAGE+i*MemoryConstants.BYTES_IN_WORD);
      assertTrue(lowAddr.loadAddress(offset).isZero());
      assertTrue(highAddr.loadAddress(offset).isZero());
      assertTrue(lowAddrEnd.loadAddress(negOffset).isZero());
      assertTrue(highAddrEnd.loadAddress(negOffset).isZero());
      lowAddr.plus(offset).store(Address.fromIntSignExtend(INT_CONST1));
      highAddr.plus(offset).store(Address.fromIntZeroExtend(INT_CONST2));
      assertTrue(lowAddr.loadAddress(offset).toLong() == INT_CONST1);
      assertTrue(highAddr.loadAddress(offset).toInt() == INT_CONST2);
      assertTrue(lowAddrEnd.loadAddress(negOffset).toLong() == (INT_CONST1&0xFFFFFFFFL));
      assertTrue(highAddrEnd.loadAddress(negOffset).toLong() == (INT_CONST2&0xFFFFFFFFL));
    }
  }

//  @Test
//  public void testLoadWord() {
//    fail("Not yet implemented");
//  }

  @Test
  public void testLoadWordOffset() {
    zeroTestPages();
    Address lowAddr = Address.fromIntSignExtend(LOW_TEST_PAGE);
    Address highAddr = Address.fromIntSignExtend(HIGH_TEST_PAGE);
    Address lowAddrEnd = lowAddr.plus(BYTES_IN_PAGE);
    Address highAddrEnd = highAddr.plus(BYTES_IN_PAGE);
    for (int i=0; i < MemoryConstants.BYTES_IN_PAGE/MemoryConstants.BYTES_IN_WORD; i++) {
      Offset offset = Offset.fromIntSignExtend(i*MemoryConstants.BYTES_IN_WORD);
      Offset negOffset = Offset.fromIntSignExtend(-BYTES_IN_PAGE+i*MemoryConstants.BYTES_IN_WORD);
      assertTrue(lowAddr.loadWord(offset).isZero());
      assertTrue(highAddr.loadWord(offset).isZero());
      assertTrue(lowAddrEnd.loadWord(negOffset).isZero());
      assertTrue(highAddrEnd.loadWord(negOffset).isZero());
      lowAddr.plus(offset).store(Word.fromIntSignExtend(INT_CONST1));
      highAddr.plus(offset).store(Word.fromIntZeroExtend(INT_CONST2));
      assertTrue(lowAddr.loadWord(offset).toLong() == INT_CONST1);
      assertTrue(highAddr.loadWord(offset).toInt() == INT_CONST2);
      assertTrue(lowAddrEnd.loadWord(negOffset).toLong() == (INT_CONST1&0xFFFFFFFFL));
      assertTrue(highAddrEnd.loadWord(negOffset).toLong() == (INT_CONST2&0xFFFFFFFFL));
    }
  }

  // Write with addr.store(x,offset), read with offset.plus(offset)
  @Test
  public void testStoreObjectReferenceOffset() {
    zeroTestPages();
    Address lowAddr = Address.fromIntSignExtend(LOW_TEST_PAGE);
    Address highAddr = Address.fromIntSignExtend(HIGH_TEST_PAGE);
    Address highAddrEnd = highAddr.plus(BYTES_IN_PAGE);
    for (int i=0; i < MemoryConstants.BYTES_IN_PAGE/MemoryConstants.BYTES_IN_WORD; i++) {
      Offset offset = Offset.fromIntSignExtend(i*MemoryConstants.BYTES_IN_WORD);
      Offset negOffset = Offset.fromIntSignExtend(-BYTES_IN_PAGE+i*MemoryConstants.BYTES_IN_WORD);
      assertTrue(lowAddr.plus(offset).loadObjectReference().isNull());
      assertTrue(highAddr.plus(offset).loadObjectReference().isNull());
      lowAddr.store(Address.fromIntSignExtend(INT_CONST1).toObjectReference(),offset);
      highAddrEnd.store(Address.fromIntZeroExtend(INT_CONST2).toObjectReference(),negOffset);
      assertTrue(lowAddr.plus(offset).loadObjectReference().toAddress().toInt() == INT_CONST1);
      assertTrue(highAddr.plus(offset).loadObjectReference().toAddress().toInt() == INT_CONST2);
    }
  }

  @Test
  public void testStoreAddressOffset() {
    zeroTestPages();
    Address lowAddr = Address.fromIntSignExtend(LOW_TEST_PAGE);
    Address highAddr = Address.fromIntSignExtend(HIGH_TEST_PAGE);
    Address highAddrEnd = highAddr.plus(BYTES_IN_PAGE);
    for (int i=0; i < MemoryConstants.BYTES_IN_PAGE/MemoryConstants.BYTES_IN_WORD; i++) {
      Offset offset = Offset.fromIntSignExtend(i*MemoryConstants.BYTES_IN_WORD);
      Offset negOffset = Offset.fromIntSignExtend(-BYTES_IN_PAGE+i*MemoryConstants.BYTES_IN_WORD);
      assertTrue(lowAddr.plus(offset).loadAddress().isZero());
      assertTrue(highAddr.plus(offset).loadAddress().isZero());
      lowAddr.store(Address.fromIntSignExtend(INT_CONST1),offset);
      highAddrEnd.store(Address.fromIntZeroExtend(INT_CONST2),negOffset);
      assertTrue(lowAddr.plus(offset).loadAddress().toInt() == INT_CONST1);
      assertTrue(highAddr.plus(offset).loadAddress().toInt() == INT_CONST2);
    }
  }

  @Test
  public void testStoreFloatOffset() {
    zeroTestPages();
    Address lowAddr = Address.fromIntSignExtend(LOW_TEST_PAGE);
    Address highAddr = Address.fromIntSignExtend(HIGH_TEST_PAGE);
    Address highAddrEnd = highAddr.plus(BYTES_IN_PAGE);
    for (int i=0; i < MemoryConstants.BYTES_IN_PAGE/BYTES_IN_FLOAT; i++) {
      Offset offset = Offset.fromIntSignExtend(i*BYTES_IN_FLOAT);
      Offset negOffset = Offset.fromIntSignExtend(-BYTES_IN_PAGE+i*BYTES_IN_FLOAT);
      assertTrue(lowAddr.plus(offset).loadFloat() == 0f);
      assertTrue(highAddr.plus(offset).loadFloat() == 0f);
      lowAddr.store(FLOAT_CONST1,offset);
      highAddrEnd.store(FLOAT_CONST2,negOffset);
      assertTrue(lowAddr.plus(offset).loadFloat() == FLOAT_CONST1);
      assertTrue(highAddr.plus(offset).loadFloat() == FLOAT_CONST2);
    }
  }

  @Test
  public void testStoreWordOffset() {
    zeroTestPages();
    Address lowAddr = Address.fromIntSignExtend(LOW_TEST_PAGE);
    Address highAddr = Address.fromIntSignExtend(HIGH_TEST_PAGE);
    Address highAddrEnd = highAddr.plus(BYTES_IN_PAGE);
    for (int i=0; i < MemoryConstants.BYTES_IN_PAGE/MemoryConstants.BYTES_IN_WORD; i++) {
      Offset offset = Offset.fromIntSignExtend(i*MemoryConstants.BYTES_IN_WORD);
      Offset negOffset = Offset.fromIntSignExtend(-BYTES_IN_PAGE+i*MemoryConstants.BYTES_IN_WORD);
      assertTrue(lowAddr.plus(offset).loadWord().isZero());
      assertTrue(highAddr.plus(offset).loadWord().isZero());
      lowAddr.store(Word.fromIntSignExtend(INT_CONST1),offset);
      highAddrEnd.store(Word.fromIntZeroExtend(INT_CONST2),negOffset);
      assertTrue(lowAddr.plus(offset).loadWord().toInt() == INT_CONST1);
      assertTrue(highAddr.plus(offset).loadWord().toInt() == INT_CONST2);
    }
  }

  @Test
  public void testStoreByteOffset() {
    zeroTestPages();
    Address lowAddr = Address.fromIntSignExtend(LOW_TEST_PAGE);
    Address highAddr = Address.fromIntSignExtend(HIGH_TEST_PAGE);
    Address highAddrEnd = highAddr.plus(BYTES_IN_PAGE);
    for (int i=0; i < MemoryConstants.BYTES_IN_PAGE; i++) {
      Offset offset = Offset.fromIntSignExtend(i*1);
      Offset negOffset = Offset.fromIntSignExtend(-BYTES_IN_PAGE+i*1);
      assertTrue(lowAddr.plus(offset).loadByte() == 0);
      assertTrue(highAddr.plus(offset).loadByte() == 0);
      lowAddr.store(BYTE_CONST1,offset);
      highAddrEnd.store(BYTE_CONST2,negOffset);
      assertTrue(lowAddr.plus(offset).loadByte() == BYTE_CONST1);
      assertTrue(highAddr.plus(offset).loadByte() == BYTE_CONST2);
    }
  }

  @Test
  public void testStoreIntOffset() {
    zeroTestPages();
    Address lowAddr = Address.fromIntSignExtend(LOW_TEST_PAGE);
    Address highAddr = Address.fromIntSignExtend(HIGH_TEST_PAGE);
    Address highAddrEnd = highAddr.plus(BYTES_IN_PAGE);
    for (int i=0; i < MemoryConstants.BYTES_IN_PAGE/BYTES_IN_INT; i++) {
      Offset offset = Offset.fromIntSignExtend(i*BYTES_IN_INT);
      Offset negOffset = Offset.fromIntSignExtend(-BYTES_IN_PAGE+i*BYTES_IN_INT);
      assertTrue(lowAddr.plus(offset).loadInt() == 0);
      assertTrue(highAddr.plus(offset).loadInt() == 0);
      lowAddr.store(INT_CONST1,offset);
      highAddrEnd.store(INT_CONST2,negOffset);
      assertTrue(lowAddr.plus(offset).loadInt() == INT_CONST1);
      assertTrue(highAddr.plus(offset).loadInt() == INT_CONST2);
    }
  }

  @Test
  public void testStoreDoubleOffset() {
    zeroTestPages();
    Address lowAddr = Address.fromIntSignExtend(LOW_TEST_PAGE);
    Address highAddr = Address.fromIntSignExtend(HIGH_TEST_PAGE);
    Address highAddrEnd = highAddr.plus(BYTES_IN_PAGE);
    for (int i=0; i < MemoryConstants.BYTES_IN_PAGE/BYTES_IN_DOUBLE; i++) {
      Offset offset = Offset.fromIntSignExtend(i*BYTES_IN_DOUBLE);
      Offset negOffset = Offset.fromIntSignExtend(-BYTES_IN_PAGE+i*BYTES_IN_DOUBLE);
      assertTrue(lowAddr.plus(offset).loadDouble() == 0);
      assertTrue(highAddr.plus(offset).loadDouble() == 0);
      lowAddr.store(DOUBLE_CONST1,offset);
      highAddrEnd.store(DOUBLE_CONST2,negOffset);
      assertTrue(lowAddr.plus(offset).loadDouble() == DOUBLE_CONST1);
      assertTrue(highAddr.plus(offset).loadDouble() == DOUBLE_CONST2);
    }
  }

  @Test
  public void testStoreLongOffset() {
    zeroTestPages();
    Address lowAddr = Address.fromIntSignExtend(LOW_TEST_PAGE);
    Address highAddr = Address.fromIntSignExtend(HIGH_TEST_PAGE);
    Address highAddrEnd = highAddr.plus(BYTES_IN_PAGE);
    for (int i=0; i < MemoryConstants.BYTES_IN_PAGE/BYTES_IN_LONG; i++) {
      Offset offset = Offset.fromIntSignExtend(i*BYTES_IN_LONG);
      Offset negOffset = Offset.fromIntSignExtend(-BYTES_IN_PAGE+i*BYTES_IN_LONG);
      assertTrue(lowAddr.plus(offset).loadLong() == 0);
      assertTrue(highAddr.plus(offset).loadLong() == 0);
      lowAddr.store(LONG_CONST1,offset);
      highAddrEnd.store(LONG_CONST2,negOffset);
      assertTrue(lowAddr.plus(offset).loadLong() == LONG_CONST1);
      assertTrue(highAddr.plus(offset).loadLong() == LONG_CONST2);
    }
  }

  @Test
  public void testStoreCharOffset() {
    zeroTestPages();
    Address lowAddr = Address.fromIntSignExtend(LOW_TEST_PAGE);
    Address highAddr = Address.fromIntSignExtend(HIGH_TEST_PAGE);
    Address highAddrEnd = highAddr.plus(BYTES_IN_PAGE);
    for (int i=0; i < MemoryConstants.BYTES_IN_PAGE/BYTES_IN_CHAR; i++) {
      Offset offset = Offset.fromIntSignExtend(i*BYTES_IN_CHAR);
      Offset negOffset = Offset.fromIntSignExtend(-BYTES_IN_PAGE+i*BYTES_IN_CHAR);
      assertTrue(lowAddr.plus(offset).loadChar() == 0);
      assertTrue(highAddr.plus(offset).loadChar() == 0);
      lowAddr.store(CHAR_CONST1,offset);
      highAddrEnd.store(CHAR_CONST2,negOffset);
      assertTrue(lowAddr.plus(offset).loadChar() == CHAR_CONST1);
      assertTrue(highAddr.plus(offset).loadChar() == CHAR_CONST2);
    }
  }

  @Test
  public void testStoreShortOffset() {
    zeroTestPages();
    Address lowAddr = Address.fromIntSignExtend(LOW_TEST_PAGE);
    Address highAddr = Address.fromIntSignExtend(HIGH_TEST_PAGE);
    Address highAddrEnd = highAddr.plus(BYTES_IN_PAGE);
    for (int i=0; i < MemoryConstants.BYTES_IN_PAGE/BYTES_IN_SHORT; i++) {
      Offset offset = Offset.fromIntSignExtend(i*BYTES_IN_SHORT);
      Offset negOffset = Offset.fromIntSignExtend(-BYTES_IN_PAGE+i*BYTES_IN_SHORT);
      assertTrue(lowAddr.plus(offset).loadShort() == 0);
      assertTrue(highAddr.plus(offset).loadShort() == 0);
      lowAddr.store(SHORT_CONST1,offset);
      highAddrEnd.store(SHORT_CONST2,negOffset);
      assertTrue(lowAddr.plus(offset).loadShort() == SHORT_CONST1);
      assertTrue(highAddr.plus(offset).loadShort() == SHORT_CONST2);
    }
  }

  @Test
  public void testExchangeWord() {
    Address lowAddr = Address.fromIntSignExtend(LOW_TEST_PAGE);
    Word w = lowAddr.prepareWord();
    assertTrue(lowAddr.attempt(w,Word.one()));
    w = lowAddr.prepareWord();
    assertTrue(w.EQ(Word.one()));
    lowAddr.store(Word.zero());
    assertFalse(lowAddr.attempt(w,Word.zero()));
  }

  @Test
  public void testExchangeWordOffset() {
    Address lowAddr = Address.fromIntSignExtend(LOW_TEST_PAGE+MemoryConstants.BYTES_IN_PAGE/2);
    Offset posOffset = Offset.fromIntSignExtend(20);
    Offset negOffset = Offset.fromIntSignExtend(-20);
    Word w = lowAddr.prepareWord(posOffset);
    assertTrue(lowAddr.attempt(w,Word.one(),posOffset));
    w = lowAddr.prepareWord(posOffset);
    assertTrue(w.EQ(Word.one()));
    lowAddr.store(Word.zero(),posOffset);
    assertFalse(lowAddr.attempt(w,Word.zero(),posOffset));

    w = lowAddr.prepareWord(negOffset);
    assertTrue(lowAddr.attempt(w,Word.one(),negOffset));
    w = lowAddr.prepareWord(negOffset);
    assertTrue(w.EQ(Word.one()));
    lowAddr.store(Word.zero(),negOffset);
    assertFalse(lowAddr.attempt(w,Word.zero(),negOffset));

  }

  @Test
  public void testExchangeObjectReference() {
    Address loc = Address.fromIntSignExtend(LOW_TEST_PAGE+8);
    final ObjectReference one = Word.one().toAddress().toObjectReference();

    ObjectReference w = loc.prepareObjectReference();
    assertTrue(loc.attempt(w,one));
    assertTrue(loc.loadObjectReference().toAddress().EQ(one.toAddress()));

    w = loc.prepareObjectReference();
    assertTrue(w.toAddress().EQ(one.toAddress()));
    loc.store(ObjectReference.nullReference());
    assertFalse(loc.attempt(w,ObjectReference.nullReference()));
  }

  @Test
  public void testExchangeObjectReferenceOffset() {
    Address baseAddr = Address.fromIntSignExtend(LOW_TEST_PAGE+BYTES_IN_PAGE/2);
    /* An address not on this page */
    ObjectReference outAddr = Address.fromIntSignExtend(LOW_TEST_PAGE+BYTES_IN_PAGE*2+8).toObjectReference();
    for (int i = -BYTES_IN_PAGE/2; i < BYTES_IN_PAGE/2; i += MemoryConstants.BYTES_IN_WORD) {
      Offset offset = Offset.fromIntSignExtend(i);
      ObjectReference x = Address.fromIntSignExtend(i).toObjectReference();
      ObjectReference w = baseAddr.prepareObjectReference(offset);
      assertTrue(baseAddr.attempt(w,x,offset));
      w = baseAddr.prepareObjectReference(offset);
      if (!w.toAddress().EQ(x.toAddress())) {
        System.err.printf("Exchanging ObjectReference at %s+%s, expected %s, actual %s%n",baseAddr,offset,x.toAddress(),w.toAddress());
      }
      assertTrue(w.toAddress().EQ(x.toAddress()));
      baseAddr.store(outAddr,offset);
      assertFalse(baseAddr.attempt(w,ObjectReference.nullReference(),offset));
    }
  }

  @Test
  public void testExchangeAddress() {
    Address lowAddr = Address.fromIntSignExtend(LOW_TEST_PAGE);
    Address w = lowAddr.prepareAddress();
    assertTrue(lowAddr.attempt(w,Word.one().toAddress()));
    w = lowAddr.prepareAddress();
    assertTrue(w.EQ(Word.one().toAddress()));
    lowAddr.store(Word.zero().toAddress());
    assertFalse(lowAddr.attempt(w,Word.zero().toAddress()));
  }

  @Test
  public void testExchangeAddressOffset() {
    Address baseAddr = Address.fromIntSignExtend(LOW_TEST_PAGE+BYTES_IN_PAGE/2);
    /* An address not on this page */
    Address outAddr = Address.fromIntSignExtend(LOW_TEST_PAGE+BYTES_IN_PAGE*2+8);
    for (int i = -BYTES_IN_PAGE/2; i < BYTES_IN_PAGE/2; i += MemoryConstants.BYTES_IN_WORD) {
      Offset offset = Offset.fromIntSignExtend(i);
      Address x = Address.fromIntSignExtend(i);
      Address w = baseAddr.prepareAddress(offset);
      assertTrue(baseAddr.attempt(w,x,offset));
      w = baseAddr.prepareAddress(offset);
      assertTrue(w.EQ(x));
      baseAddr.store(outAddr,offset);
      assertFalse(baseAddr.attempt(w,Address.zero(),offset));
    }
  }

  @Test
  public void testExchangeInt() {
    Address lowAddr = Address.fromIntSignExtend(LOW_TEST_PAGE);
    int w = lowAddr.prepareInt();
    assertTrue(lowAddr.attempt(w,INT_CONST1));
    w = lowAddr.prepareInt();
    assertTrue(w == INT_CONST1);
    lowAddr.store(0);
    assertFalse(lowAddr.attempt(w,0));

    w = lowAddr.prepareInt();
    assertTrue(lowAddr.attempt(w,INT_CONST2));
    w = lowAddr.prepareInt();
    assertTrue(w == INT_CONST2);
    lowAddr.store(0);
    assertFalse(lowAddr.attempt(w,0));
  }

  @Test
  public void testExchangeIntOffset() {
    Address baseAddr = Address.fromIntSignExtend(LOW_TEST_PAGE+BYTES_IN_PAGE/2);
    for (int i = -BYTES_IN_PAGE/2; i < BYTES_IN_PAGE/2; i += BYTES_IN_INT) {
      Offset offset = Offset.fromIntSignExtend(i);
      int w = baseAddr.prepareInt(offset);
      assertTrue(baseAddr.attempt(w,INT_CONST1,offset));
      w = baseAddr.prepareInt(offset);
      assertTrue(w == INT_CONST1);
      baseAddr.store(INT_CONST2,offset);
      assertFalse(baseAddr.attempt(w,0,offset));
    }
  }

  @Test
  public void testExchangeLong() {
    Address lowAddr = Address.fromIntSignExtend(LOW_TEST_PAGE);
    long w = lowAddr.prepareLong();
    assertTrue(lowAddr.attempt(w,LONG_CONST1));
    w = lowAddr.prepareLong();
    assertTrue(w == LONG_CONST1);
    lowAddr.store(0L);
    assertFalse(lowAddr.attempt(w,0L));

    w = lowAddr.prepareLong();
    assertTrue(lowAddr.attempt(w,LONG_CONST2));
    w = lowAddr.prepareLong();
    assertTrue(w == LONG_CONST2);
    lowAddr.store(0L);
    assertFalse(lowAddr.attempt(w,0L));
  }

  @Test
  public void testExchangeLongOffset() {
    Address baseAddr = Address.fromIntSignExtend(LOW_TEST_PAGE+BYTES_IN_PAGE/2);
    for (int i = -BYTES_IN_PAGE/2; i < BYTES_IN_PAGE/2; i += BYTES_IN_LONG) {
      Offset offset = Offset.fromIntSignExtend(i);
      long w = baseAddr.prepareLong(offset);
      assertTrue(baseAddr.attempt(w,LONG_CONST1,offset));
      w = baseAddr.prepareLong(offset);
      assertTrue(w == LONG_CONST1);
      baseAddr.store(LONG_CONST2,offset);
      assertFalse(baseAddr.attempt(w,0,offset));
    }
  }

  /**
   * Test whether bytes are read and written in the right
   * order within words.
   */
  @Test
  public void testByteEndianness() {
    Address baseAddr = Address.fromIntSignExtend(LOW_TEST_PAGE);
    baseAddr.store(0);
    assertEquals(0,baseAddr.loadInt());
    baseAddr.store((byte)1);
    assertEquals(0x00000001,baseAddr.loadInt());
    baseAddr.store((byte)2,Offset.fromIntSignExtend(1));
    assertEquals(0x00000201,baseAddr.loadInt());
    baseAddr.store((byte)3,Offset.fromIntSignExtend(2));
    assertEquals(0x00030201,baseAddr.loadInt());
    baseAddr.store((byte)4,Offset.fromIntSignExtend(3));
    assertEquals(0x04030201,baseAddr.loadInt());
    baseAddr.store((byte)0);
    assertEquals(0x04030200,baseAddr.loadInt());
    baseAddr.store((byte)0,Offset.fromIntSignExtend(1));
    assertEquals(0x04030000,baseAddr.loadInt());
    baseAddr.store((byte)0,Offset.fromIntSignExtend(2));
    assertEquals(0x04000000,baseAddr.loadInt());
    baseAddr.store((byte)0,Offset.fromIntSignExtend(3));
    assertEquals(0,baseAddr.loadInt());
  }

  /**
   * Test whether shorts are read and written in the right
   * order within words.
   */
  @Test
  public void testShortEndianness() {
    Address baseAddr = Address.fromIntSignExtend(LOW_TEST_PAGE);
    baseAddr.store(0);
    assertEquals(0,baseAddr.loadInt());
    baseAddr.store((short)1);
    assertEquals(0x00000001,baseAddr.loadInt());
    baseAddr.store((short)2,Offset.fromIntSignExtend(2));
    assertEquals(0x00020001,baseAddr.loadInt());
    baseAddr.store((short)0);
    assertEquals(0x00020000,baseAddr.loadInt());
    baseAddr.store((short)0,Offset.fromIntSignExtend(2));
    assertEquals(0x00000000,baseAddr.loadInt());
  }

  /**
   * Test whether ints are read and written in the right
   * order within longs.
   */
  @Test
  public void testIntEndianness() {
    Address baseAddr = Address.fromIntSignExtend(LOW_TEST_PAGE);
    baseAddr.store(0L);
    assertEquals(0L,baseAddr.loadLong());
    baseAddr.store(1);
    assertEquals(0x0000000000000001L,baseAddr.loadLong());
    baseAddr.store(2,Offset.fromIntSignExtend(4));
    assertEquals(0x0000000200000001L,baseAddr.loadLong());
    baseAddr.store(0);
    assertEquals(0x0000000200000000L,baseAddr.loadLong());
    baseAddr.store(0,Offset.fromIntSignExtend(4));
    assertEquals(0x00000000L,baseAddr.loadLong());
  }
}
