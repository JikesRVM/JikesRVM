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
    Harness.init("plan=org.mmtk.plan.nogc.NoGC");
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
    for (int i=0; i < SimulatedMemory.BYTES_IN_PAGE/SimulatedMemory.BYTES_IN_WORD; i++) {
      Offset offset = Offset.fromIntSignExtend(i*SimulatedMemory.BYTES_IN_WORD);
      Offset negOffset = Offset.fromIntSignExtend(-BYTES_IN_PAGE+i*SimulatedMemory.BYTES_IN_WORD);
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
    assertTrue(lowAddr.loadByte() == 0);
    assertTrue(highAddr.loadByte() == 0);
    lowAddr.store(BYTE_CONST1);
    highAddr.store(BYTE_CONST2);
    assertTrue(lowAddr.loadByte() == BYTE_CONST1);
    assertTrue(highAddr.loadByte() == BYTE_CONST2);
  }

  @Test
  public void testLoadByteOffset() {
    zeroTestPages();
    Address lowAddr = Address.fromIntSignExtend(LOW_TEST_PAGE);
    Address highAddr = Address.fromIntSignExtend(HIGH_TEST_PAGE);
    Address lowAddrEnd = lowAddr.plus(BYTES_IN_PAGE);
    Address highAddrEnd = highAddr.plus(BYTES_IN_PAGE);
    for (int i=0; i < SimulatedMemory.BYTES_IN_PAGE/BYTES_IN_BYTE; i++) {
      Offset offset = Offset.fromIntSignExtend(i*BYTES_IN_BYTE);
      Offset negOffset = Offset.fromIntSignExtend(-BYTES_IN_PAGE+i*BYTES_IN_BYTE);
      assertTrue(lowAddr.loadByte(offset) == 0);
      assertTrue(highAddr.loadByte(offset) == 0);
      assertTrue(lowAddrEnd.loadByte(negOffset) == 0);
      assertTrue(highAddrEnd.loadByte(negOffset) == 0);
      lowAddr.plus(offset).store(BYTE_CONST1);
      highAddr.plus(offset).store(BYTE_CONST2);
      assertTrue(lowAddr.loadByte(offset) == BYTE_CONST1);
      assertTrue(highAddr.loadByte(offset) == BYTE_CONST2);
      assertTrue(lowAddrEnd.loadByte(negOffset) == BYTE_CONST1);
      assertTrue(highAddrEnd.loadByte(negOffset) == BYTE_CONST2);
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
    for (int i=0; i < SimulatedMemory.BYTES_IN_PAGE/BYTES_IN_CHAR; i++) {
      Offset offset = Offset.fromIntSignExtend(i*BYTES_IN_CHAR);
      Offset negOffset = Offset.fromIntSignExtend(-BYTES_IN_PAGE+i*BYTES_IN_CHAR);
      assertTrue(lowAddr.loadChar(offset) == 0);
      assertTrue(highAddr.loadChar(offset) == 0);
      assertTrue(lowAddrEnd.loadChar(negOffset) == 0);
      assertTrue(highAddrEnd.loadChar(negOffset) == 0);
      lowAddr.plus(offset).store(CHAR_CONST1);
      highAddr.plus(offset).store(CHAR_CONST2);
      assertTrue(lowAddr.loadChar(offset) == CHAR_CONST1);
      assertTrue(highAddr.loadChar(offset) == CHAR_CONST2);
      assertTrue(lowAddrEnd.loadChar(negOffset) == CHAR_CONST1);
      assertTrue(highAddrEnd.loadChar(negOffset) == CHAR_CONST2);
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
    for (int i=0; i < SimulatedMemory.BYTES_IN_PAGE/BYTES_IN_SHORT; i++) {
      Offset offset = Offset.fromIntSignExtend(i*BYTES_IN_SHORT);
      Offset negOffset = Offset.fromIntSignExtend(-BYTES_IN_PAGE+i*BYTES_IN_SHORT);
      assertTrue(lowAddr.loadShort(offset) == 0);
      assertTrue(highAddr.loadShort(offset) == 0);
      assertTrue(lowAddrEnd.loadShort(negOffset) == 0);
      assertTrue(highAddrEnd.loadShort(negOffset) == 0);
      lowAddr.plus(offset).store(SHORT_CONST1);
      highAddr.plus(offset).store(SHORT_CONST2);
      assertTrue(lowAddr.loadShort(offset) == SHORT_CONST1);
      assertTrue(highAddr.loadShort(offset) == SHORT_CONST2);
      assertTrue(lowAddrEnd.loadShort(negOffset) == SHORT_CONST1);
      assertTrue(highAddrEnd.loadShort(negOffset) == SHORT_CONST2);
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
    for (int i=0; i < SimulatedMemory.BYTES_IN_PAGE/BYTES_IN_FLOAT; i++) {
      Offset offset = Offset.fromIntSignExtend(i*BYTES_IN_FLOAT);
      Offset negOffset = Offset.fromIntSignExtend(-BYTES_IN_PAGE+i*BYTES_IN_FLOAT);
      assertTrue(lowAddr.loadFloat(offset) == 0);
      assertTrue(highAddr.loadFloat(offset) == 0);
      assertTrue(lowAddrEnd.loadFloat(negOffset) == 0);
      assertTrue(highAddrEnd.loadFloat(negOffset) == 0);
      lowAddr.plus(offset).store(FLOAT_CONST1);
      highAddr.plus(offset).store(FLOAT_CONST2);
      assertTrue(lowAddr.loadFloat(offset) == FLOAT_CONST1);
      assertTrue(highAddr.loadFloat(offset) == FLOAT_CONST2);
      assertTrue(lowAddrEnd.loadFloat(negOffset) == FLOAT_CONST1);
      assertTrue(highAddrEnd.loadFloat(negOffset) == FLOAT_CONST2);
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
    for (int i=0; i < SimulatedMemory.BYTES_IN_PAGE/BYTES_IN_INT; i++) {
      Offset offset = Offset.fromIntSignExtend(i*BYTES_IN_INT);
      Offset negOffset = Offset.fromIntSignExtend(-BYTES_IN_PAGE+i*BYTES_IN_INT);
      assertTrue(lowAddr.loadInt(offset) == 0);
      assertTrue(highAddr.loadInt(offset) == 0);
      assertTrue(lowAddrEnd.loadInt(negOffset) == 0);
      assertTrue(highAddrEnd.loadInt(negOffset) == 0);
      lowAddr.plus(offset).store(INT_CONST1);
      highAddr.plus(offset).store(INT_CONST2);
      assertTrue(lowAddr.loadInt(offset) == INT_CONST1);
      assertTrue(highAddr.loadInt(offset) == INT_CONST2);
      assertTrue(lowAddrEnd.loadInt(negOffset) == INT_CONST1);
      assertTrue(highAddrEnd.loadInt(negOffset) == INT_CONST2);
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
    for (int i=0; i < SimulatedMemory.BYTES_IN_PAGE/BYTES_IN_LONG; i++) {
      Offset offset = Offset.fromIntSignExtend(i*BYTES_IN_LONG);
      Offset negOffset = Offset.fromIntSignExtend(-BYTES_IN_PAGE+i*BYTES_IN_LONG);
      assertTrue(lowAddr.loadLong(offset) == 0);
      assertTrue(highAddr.loadLong(offset) == 0);
      assertTrue(lowAddrEnd.loadLong(negOffset) == 0);
      assertTrue(highAddrEnd.loadLong(negOffset) == 0);
      lowAddr.plus(offset).store(LONG_CONST1);
      highAddr.plus(offset).store(LONG_CONST2);
      assertTrue(lowAddr.loadLong(offset) == LONG_CONST1);
      assertTrue(highAddr.loadLong(offset) == LONG_CONST2);
      assertTrue(lowAddrEnd.loadLong(negOffset) == LONG_CONST1);
      assertTrue(highAddrEnd.loadLong(negOffset) == LONG_CONST2);
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
    for (int i=0; i < SimulatedMemory.BYTES_IN_PAGE/BYTES_IN_DOUBLE; i++) {
      Offset offset = Offset.fromIntSignExtend(i*BYTES_IN_DOUBLE);
      Offset negOffset = Offset.fromIntSignExtend(-BYTES_IN_PAGE+i*BYTES_IN_DOUBLE);
      assertTrue(lowAddr.loadDouble(offset) == 0);
      assertTrue(highAddr.loadDouble(offset) == 0);
      assertTrue(lowAddrEnd.loadDouble(negOffset) == 0);
      assertTrue(highAddrEnd.loadDouble(negOffset) == 0);
      lowAddr.plus(offset).store(DOUBLE_CONST1);
      highAddr.plus(offset).store(DOUBLE_CONST2);
      assertTrue(lowAddr.loadDouble(offset) == DOUBLE_CONST1);
      assertTrue(highAddr.loadDouble(offset) == DOUBLE_CONST2);
      assertTrue(lowAddrEnd.loadDouble(negOffset) == DOUBLE_CONST1);
      assertTrue(highAddrEnd.loadDouble(negOffset) == DOUBLE_CONST2);
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
    for (int i=0; i < SimulatedMemory.BYTES_IN_PAGE/SimulatedMemory.BYTES_IN_WORD; i++) {
      Offset offset = Offset.fromIntSignExtend(i*SimulatedMemory.BYTES_IN_WORD);
      Offset negOffset = Offset.fromIntSignExtend(-BYTES_IN_PAGE+i*SimulatedMemory.BYTES_IN_WORD);
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
    for (int i=0; i < SimulatedMemory.BYTES_IN_PAGE/SimulatedMemory.BYTES_IN_WORD; i++) {
      Offset offset = Offset.fromIntSignExtend(i*SimulatedMemory.BYTES_IN_WORD);
      Offset negOffset = Offset.fromIntSignExtend(-BYTES_IN_PAGE+i*SimulatedMemory.BYTES_IN_WORD);
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
    for (int i=0; i < SimulatedMemory.BYTES_IN_PAGE/SimulatedMemory.BYTES_IN_WORD; i++) {
      Offset offset = Offset.fromIntSignExtend(i*SimulatedMemory.BYTES_IN_WORD);
      Offset negOffset = Offset.fromIntSignExtend(-BYTES_IN_PAGE+i*SimulatedMemory.BYTES_IN_WORD);
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
    for (int i=0; i < SimulatedMemory.BYTES_IN_PAGE/SimulatedMemory.BYTES_IN_WORD; i++) {
      Offset offset = Offset.fromIntSignExtend(i*SimulatedMemory.BYTES_IN_WORD);
      Offset negOffset = Offset.fromIntSignExtend(-BYTES_IN_PAGE+i*SimulatedMemory.BYTES_IN_WORD);
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
    for (int i=0; i < SimulatedMemory.BYTES_IN_PAGE/BYTES_IN_FLOAT; i++) {
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
    for (int i=0; i < SimulatedMemory.BYTES_IN_PAGE/SimulatedMemory.BYTES_IN_WORD; i++) {
      Offset offset = Offset.fromIntSignExtend(i*SimulatedMemory.BYTES_IN_WORD);
      Offset negOffset = Offset.fromIntSignExtend(-BYTES_IN_PAGE+i*SimulatedMemory.BYTES_IN_WORD);
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
    for (int i=0; i < SimulatedMemory.BYTES_IN_PAGE; i++) {
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
    for (int i=0; i < SimulatedMemory.BYTES_IN_PAGE/BYTES_IN_INT; i++) {
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
    for (int i=0; i < SimulatedMemory.BYTES_IN_PAGE/BYTES_IN_DOUBLE; i++) {
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
    for (int i=0; i < SimulatedMemory.BYTES_IN_PAGE/BYTES_IN_LONG; i++) {
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
    for (int i=0; i < SimulatedMemory.BYTES_IN_PAGE/BYTES_IN_CHAR; i++) {
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
    for (int i=0; i < SimulatedMemory.BYTES_IN_PAGE/BYTES_IN_SHORT; i++) {
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
    Address lowAddr = Address.fromIntSignExtend(LOW_TEST_PAGE+SimulatedMemory.BYTES_IN_PAGE/2);
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
    for (int i = -BYTES_IN_PAGE/2; i < BYTES_IN_PAGE/2; i += SimulatedMemory.BYTES_IN_WORD) {
      Offset offset = Offset.fromIntSignExtend(i);
      ObjectReference x = Address.fromIntSignExtend(i).toObjectReference();
      ObjectReference w = baseAddr.prepareObjectReference(offset);
      assertTrue(baseAddr.attempt(w,x,offset));
      w = baseAddr.prepareObjectReference(offset);
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
    for (int i = -BYTES_IN_PAGE/2; i < BYTES_IN_PAGE/2; i += SimulatedMemory.BYTES_IN_WORD) {
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
}
