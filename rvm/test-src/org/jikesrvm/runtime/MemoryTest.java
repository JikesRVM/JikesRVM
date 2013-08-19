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
package org.jikesrvm.runtime;

import static org.jikesrvm.SizeConstants.BYTES_IN_BYTE;
import static org.jikesrvm.SizeConstants.BYTES_IN_INT;
import static org.jikesrvm.SizeConstants.BYTES_IN_LONG;
import static org.jikesrvm.SizeConstants.BYTES_IN_SHORT;
import static org.junit.Assert.fail;

import org.jikesrvm.VM;
import org.jikesrvm.junit.runners.RequiresJikesRVM;
import org.jikesrvm.junit.runners.VMRequirements;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.vmmagic.unboxed.Address;

@RunWith(VMRequirements.class)
@Category(RequiresJikesRVM.class)
public class MemoryTest {

  private static int nativeThreshold;
  private static int arrayLength;

  @BeforeClass
  public static void setup() {
    if (!VM.runningVM) return;
    nativeThreshold = Memory.getNativeThreshold();
    arrayLength = nativeThreshold * 2;
  }

  @Test
  public void testOverlappingCopyOfByteArrayFromGreaterToLesserPosition() {
    byte[] largeByteArray = initializeByteArray();

    int copyLength = (arrayLength * 7) / 9;
    int copyStart = arrayLength - copyLength;
    int copyDest = (arrayLength) / 9;

    byte srcPosValue = largeByteArray[copyStart];

    doAligned8CopyForBytes(largeByteArray, copyStart, copyLength, copyDest);

    verifyThatCopyingToByteArrayWasCorrect(largeByteArray,
        copyLength, copyDest, srcPosValue);
  }

  private void verifyThatCopyingToByteArrayWasCorrect(byte[] largeByteArray,
      int copyLength, int copyDest, byte srcPosValue) {
    for (int i = copyDest; i < copyLength; i++) {
      if (largeByteArray[i] != srcPosValue) {
        fail("Expected " + srcPosValue + " but was " + largeByteArray[i] + " at pos " + i +
            ". This means that parts of the byte array were overwritten");
        break;
      }
      srcPosValue++;
    }
  }

  private void doAligned8CopyForBytes(byte[] largeByteArray, int copyStart,
      int copyLength, int copyDest) {
    VM.disableGC();
    Address arrayStart = Magic.objectAsAddress(largeByteArray);
    Address copyStartAddress = arrayStart.plus(copyStart * BYTES_IN_BYTE);
    Address copyDestAddress = arrayStart.plus(copyDest * BYTES_IN_BYTE);
    int copyBytes = copyLength * BYTES_IN_BYTE;
    Memory.aligned8Copy(copyDestAddress, copyStartAddress, copyBytes);
    VM.enableGC();
  }

  private byte[] initializeByteArray() {
    byte[] largeByteArray = new byte[arrayLength];

    byte startValue = Byte.MIN_VALUE;
    byte value = startValue;
    for (int i = 0; i < largeByteArray.length; i++) {
      largeByteArray[i] = value;
      value++;
    }
    return largeByteArray;
  }

  @Test
  public void testOverlappingCopyOfShortArrayFromGreaterToLesserPosition() {
    short[] largeShortArray = initializeShortArray();

    int copyLength = (arrayLength * 7) / 9;
    int copyStart = arrayLength - copyLength;
    int copyDest = (arrayLength) / 9;

    short srcPosValue = largeShortArray[copyStart];

    doAligned16BitCopyForShorts(largeShortArray, copyStart, copyLength,
        copyDest);

    srcPosValue = verifyThatCopyingToShortArrayWasCorrect(
        largeShortArray, copyLength, copyDest, srcPosValue);
  }

  private short verifyThatCopyingToShortArrayWasCorrect(
      short[] largeShortArray, int copyLength, int copyDest, short srcPosValue) {
    for (int i = copyDest; i < copyLength; i++) {
      if (largeShortArray[i] != srcPosValue) {
        fail("Expected + " + srcPosValue + " but was " + largeShortArray[i] + " at pos " + i +
            ". This means that parts of the short array were overwritten");
        break;
      }
      srcPosValue++;
    }
    return srcPosValue;
  }

  private void doAligned16BitCopyForShorts(short[] largeShortArray,
      int copyStart, int copyLength, int copyDest) {
    VM.disableGC();
    Address arrayStart = Magic.objectAsAddress(largeShortArray);
    Address copyStartAddress = arrayStart.plus(copyStart * BYTES_IN_SHORT);
    Address copyDestAddress = arrayStart.plus(copyDest * BYTES_IN_SHORT);
    int copyBytes = copyLength * BYTES_IN_SHORT;
    Memory.aligned16Copy(copyDestAddress, copyStartAddress, copyBytes);
    VM.enableGC();
  }

  private short[] initializeShortArray() {
    short[] largeShortArray = new short[arrayLength];

    short startValue = Short.MIN_VALUE;
    short value = startValue;
    for (int i = 0; i < largeShortArray.length; i++) {
      largeShortArray[i] = value;
      value++;
    }
    return largeShortArray;
  }

  @Test
  public void testOverlappingCopyOfIntArrayFromGreaterToLesserPosition() {
    int[] largeIntArray = initializeIntArray();

    int copyLength = (arrayLength * 7) / 9;
    int copyStart = arrayLength - copyLength;
    int copyDest = (arrayLength) / 9;

    int srcPosValue = largeIntArray[copyStart];

    doAligned32CopyForInts(largeIntArray, copyStart, copyLength, copyDest);

    srcPosValue = verifyThatCopyingToIntArrayWasCorrect(largeIntArray,
        copyLength, copyDest, srcPosValue);
  }

  private int verifyThatCopyingToIntArrayWasCorrect(int[] largeIntArray,
      int copyLength, int copyDest, int srcPosValue) {
    for (int i = copyDest; i < copyLength; i++) {
      if (largeIntArray[i] != srcPosValue) {
        fail("Expected + " + srcPosValue + " but was " + largeIntArray[i] + " at pos " + i +
            ". This means that parts of the int array were overwritten");
        break;
      }
      srcPosValue++;
    }
    return srcPosValue;
  }

  private void doAligned32CopyForInts(int[] largeIntArray, int copyStart,
      int copyLength, int copyDest) {
    VM.disableGC();
    Address arrayStart = Magic.objectAsAddress(largeIntArray);
    Address copyStartAddress = arrayStart.plus(copyStart * BYTES_IN_INT);
    Address copyDestAddress = arrayStart.plus(copyDest * BYTES_IN_INT);
    int copyBytes = copyLength * BYTES_IN_INT;
    Memory.aligned32Copy(copyDestAddress, copyStartAddress, copyBytes);
    VM.enableGC();
  }

  private int[] initializeIntArray() {
    int[] largeIntArray = new int[arrayLength];

    int startValue = Integer.MIN_VALUE;
    int value = startValue;
    for (int i = 0; i < largeIntArray.length; i++) {
      largeIntArray[i] = value;
      value++;
    }
    return largeIntArray;
  }

  @Test
  public void testOverlappingCopyOfLongArrayFromGreaterToLesserPosition() {
    long[] largeLongArray = initializeLongArray();

    int copyLength = (arrayLength * 7) / 9;
    int copyStart = arrayLength - copyLength;
    int copyDest = (arrayLength) / 9;

    long srcPosValue = largeLongArray[copyStart];

    doAligned64CopyForLongs(largeLongArray, copyStart, copyLength, copyDest);

    srcPosValue = verifiyThatCopyingToLongArrayWasCorrect(largeLongArray,
        copyLength, copyDest, srcPosValue);
  }

  private long verifiyThatCopyingToLongArrayWasCorrect(
      long[] largeLongArray, int copyLength, int copyDest, long srcPosValue) {
    for (int i = copyDest; i < copyLength; i++) {
      if (largeLongArray[i] != srcPosValue) {
        fail("Expected + " + srcPosValue + " but was " + largeLongArray[i] + " at pos " + i +
            ". This means that parts of the long array were overwritten");
        break;
      }
      srcPosValue++;
    }
    return srcPosValue;
  }

  private void doAligned64CopyForLongs(long[] largeLongArray, int copyStart,
      int copyLength, int copyDest) {
    VM.disableGC();
    Address arrayStart = Magic.objectAsAddress(largeLongArray);
    Address copyStartAddress = arrayStart.plus(copyStart * BYTES_IN_LONG);
    Address copyDestAddress = arrayStart.plus(copyDest * BYTES_IN_LONG);
    int copyBytes = copyLength * BYTES_IN_LONG;
    Memory.aligned64Copy(copyDestAddress, copyStartAddress, copyBytes);
    VM.enableGC();
  }

  private long[] initializeLongArray() {
    long[] largeLongArray = new long[arrayLength];

    long startValue = Long.MIN_VALUE;
    long value = startValue;
    for (int i = 0; i < largeLongArray.length; i++) {
      largeLongArray[i] = value;
      value++;
    }
    return largeLongArray;
  }

  @Test
  public void testMemcopyFromGreaterToLesserOverlapping() throws Exception {
    byte[] largeByteArray = initializeByteArray();

    int copyLength = (arrayLength * 7) / 9;
    int copyStart = arrayLength - copyLength;
    int copyDest = (arrayLength) / 9;

    byte srcPosValue = largeByteArray[copyStart];

    doMemcopy(largeByteArray, copyLength, copyStart, copyDest);

    verifyThatCopyingToByteArrayWasCorrect(largeByteArray,
        copyLength, copyDest, srcPosValue);
  }

  @Test
  public void testMemcopyFromLesserToGreaterOverlapping() throws Exception {
    byte[] largeByteArray = initializeByteArray();

    int copyLength = (arrayLength * 7) / 9;
    int copyStart = (3 * arrayLength) / 9;
    int copyDest = arrayLength - copyLength;

    byte srcPosValue = largeByteArray[copyStart];

    doMemcopy(largeByteArray, copyLength, copyStart, copyDest);

    verifyThatCopyingToByteArrayWasCorrect(largeByteArray,
        copyLength, copyDest, srcPosValue);
  }

  @Test
  public void testMemcopyWholeArray() throws Exception {
    byte[] largeByteArray = initializeByteArray();

    int copyLength = arrayLength;
    int copyStart = 0;
    int copyDest = 0;

    byte srcPosValue = largeByteArray[copyStart];

    doMemcopy(largeByteArray, copyLength, copyStart, copyDest);

    verifyThatCopyingToByteArrayWasCorrect(largeByteArray,
        copyLength, copyDest, srcPosValue);
  }

  private void doMemcopy(byte[] largeByteArray, int copyLength, int copyStart,
      int copyDest) {
    VM.disableGC();
    Address arrayStart = Magic.objectAsAddress(largeByteArray);
    Address copyStartAddress = arrayStart.plus(copyStart * BYTES_IN_BYTE);
    Address copyDestAddress = arrayStart.plus(copyDest * BYTES_IN_BYTE);
    int copyBytes = copyLength * BYTES_IN_BYTE;
    Memory.memcopy(copyDestAddress, copyStartAddress, copyBytes);
    VM.enableGC();
  }

}
