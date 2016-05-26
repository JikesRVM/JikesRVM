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

import java.io.IOException;
import org.jikesrvm.junit.runners.RequiresBuiltJikesRVM;
import org.jikesrvm.junit.runners.VMRequirements;
import org.jikesrvm.runtime.Magic;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.vmmagic.pragma.NonMovingAllocation;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;

@Category(RequiresBuiltJikesRVM.class)
@RunWith(VMRequirements.class)
public class AddressInputStreamTest {

  private static final byte FIRST_VALUE = Byte.MAX_VALUE;
  private static final byte SECOND_VALUE = Byte.MIN_VALUE;
  private static final byte THIRD_VALUE = -1;
  private static final int NUMBER_OF_BYTE_VALUES = 256;

  private AddressInputStream streamFromByteArray;
  private AddressInputStream toBeClosedAfterTestMethodRuns;
  private byte[] byteArray;

  @NonMovingAllocation
  @Before
  public void setUp() {
    byteArray = new byte[3];
    byteArray[0] = FIRST_VALUE;
    byteArray[1] = SECOND_VALUE;
    byteArray[2] = THIRD_VALUE;
    streamFromByteArray = new AddressInputStream(Magic.objectAsAddress(byteArray), Extent.fromIntZeroExtend(byteArray.length));
  }

  @After
  public void closeRemainingStreams() throws IOException {
    streamFromByteArray.close();
    if (toBeClosedAfterTestMethodRuns != null) {
      toBeClosedAfterTestMethodRuns.close();
      toBeClosedAfterTestMethodRuns = null;
    }
  }

  @Test
  public void readReturnsMinusOneForEmptyRegion() throws IOException {
    assertEquals(-1, createEmptyRegion().read());
  }

  @Test
  public void availableReturnsZeroForEmptyRegion() throws IOException {
    assertEquals(0, createEmptyRegion().available());
  }

  private AddressInputStream createEmptyRegion() {
    return new AddressInputStream(Address.zero(), Extent.zero());
  }

  @Test
  public void testReadOfByteArray() throws IOException {
    int firstByte = streamFromByteArray.read();
    int secondByte = streamFromByteArray.read();
    int thirdByte = streamFromByteArray.read();

    assertThat(firstByte, is((int) FIRST_VALUE));
    assertThat(secondByte, is(SECOND_VALUE + NUMBER_OF_BYTE_VALUES));
    assertThat(thirdByte, is(-1 + NUMBER_OF_BYTE_VALUES));
  }

  @Test
  public void availableForNewlyCreatedRegionIsEqualToRegionLength() throws IOException {
    assertEquals(0, createEmptyRegion().available());
    int length = 13;
    AddressInputStream aRegion = new AddressInputStream(Address.zero(), Extent.fromIntZeroExtend(length));
    toBeClosedAfterTestMethodRuns = aRegion;
    assertEquals(length, aRegion.available());
  }

  @Test
  public void availableIsLengthMinusReadBytes() throws IOException {
    streamFromByteArray.read();
    assertEquals(byteArray.length - 1, streamFromByteArray.available());
  }

  @Test
  public void availableForMaximumSizeRegionsIsNonNegative() throws Exception {
    AddressInputStream maximumSizeRegion = new AddressInputStream(Address.zero(), Extent.max());
    toBeClosedAfterTestMethodRuns = maximumSizeRegion;
    assertTrue(maximumSizeRegion.available() >= 0);
  }

  @Test
  public void skipAmountIsLimitedToIntMaxValue() {
    AddressInputStream regionOfIntMaxSize = new AddressInputStream(Address.zero(), Extent.fromIntZeroExtend(Integer.MAX_VALUE));
    toBeClosedAfterTestMethodRuns = regionOfIntMaxSize;
    long skippedNumber = regionOfIntMaxSize.skip(Long.MAX_VALUE);
    assertEquals(Integer.MAX_VALUE, skippedNumber);
  }

  @Test
  public void skipAmountIsLimitedByEndOfStream() {
    long skipAmount = streamFromByteArray.skip(byteArray.length + 1);
    assertEquals(byteArray.length, skipAmount);
  }

  @Test
  public void noBytesAreSkippedWhenSkipAmountIsNegative() {
    long skippedBytes = streamFromByteArray.skip(-1);
    assertEquals(0, skippedBytes);
  }

  @Test
  public void streamSupportsMarking() throws Exception {
    assertTrue(streamFromByteArray.markSupported());
  }

  @Test
  public void markAndResetFollowsAPISpec() throws Exception {
    streamFromByteArray.read();
    int available = streamFromByteArray.available();
    int ignored = 1;
    streamFromByteArray.mark(ignored);
    int valueFromSecondEntry = streamFromByteArray.read();
    streamFromByteArray.read();
    streamFromByteArray.reset();
    assertEquals(available, streamFromByteArray.available());
    assertEquals(valueFromSecondEntry, streamFromByteArray.read());
  }

}
