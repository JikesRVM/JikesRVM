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
package org.mmtk.utility.alloc;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mmtk.harness.Harness;
import org.mmtk.harness.tests.BaseMMTkTest;
import org.mmtk.utility.Constants;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.harness.ArchitecturalWord;
import org.vmmagic.unboxed.harness.Architecture;
import org.vmmagic.unboxed.harness.SimulatedMemory;

public class AllocatorTest extends BaseMMTkTest {

  private static final int BYTES_IN_PAGE = 4096;

  private static final int LOW_TEST_PAGE = 0x10000;

  private static final long HIGH_TEST_PAGE = 0x00F0000000010000L;


  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    Harness.initArchitecture(Arrays.asList("bits=64","timeout=600"));
    Harness.initOnce();
//  Harness.init("plan=org.mmtk.plan.nogc.NoGC","bits=64");
//  SimulatedMemory.addWatch(Address.fromIntSignExtend(LOW_TEST_PAGE), BYTES_IN_PAGE);
//  SimulatedMemory.addWatch(Address.fromIntSignExtend(HIGH_TEST_PAGE), BYTES_IN_PAGE);
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
  }

  @Before
  public void setUp() throws Exception {
  }

  @After
  public void tearDown() throws Exception {
    SimulatedMemory.unmap(Address.fromIntSignExtend(LOW_TEST_PAGE), BYTES_IN_PAGE);
    SimulatedMemory.unmap(Address.fromLong(HIGH_TEST_PAGE), BYTES_IN_PAGE);
  }

  public static boolean is64bit() {
    return ArchitecturalWord.getModel() == Architecture.BITS64;
  }

  @Test
  public void testFillAlignmentGap4() throws Throwable {
    SimulatedMemory.map(Address.fromIntSignExtend(LOW_TEST_PAGE), BYTES_IN_PAGE);
    Address base = Address.fromIntSignExtend(LOW_TEST_PAGE);

    Address low = base.plus(4);
    Address high = base.plus(8);
    Allocator.fillAlignmentGap(low, high);
    assertEquals(0, low.loadInt(Offset.fromIntSignExtend(-4)));
    assertEquals(Constants.ALIGNMENT_VALUE, low.loadInt());
    assertEquals(0, low.loadInt(Offset.fromIntSignExtend(+4)));
  }

  @Test
  public void testFillAlignmentGap8() throws Throwable {
    SimulatedMemory.map(Address.fromIntSignExtend(LOW_TEST_PAGE), BYTES_IN_PAGE);
    Address base = Address.fromIntSignExtend(LOW_TEST_PAGE);

    Address low = base.plus(4);
    Address high = base.plus(12);
    Allocator.fillAlignmentGap(low, high);
    assertEquals(0, low.loadInt(Offset.fromIntSignExtend(-4)));
    assertEquals(Constants.ALIGNMENT_VALUE, low.loadInt());
    assertEquals(0, low.loadInt(Offset.fromIntSignExtend(+4)));
  }

  // Cases where there should be no alignment required
  //
  // Just test the resulting alignment, don't write to memory.
  @Test
  public void testAlignAllocationZero() throws Throwable {
    Address base = Address.fromIntSignExtend(LOW_TEST_PAGE);

    assertEquals(base, Allocator.alignAllocation(base, 8, 16, 4, false));
    assertEquals(base, Allocator.alignAllocation(base, 8, 8, 4, false));
    assertEquals(base, Allocator.alignAllocation(base, 8, 0, 4, false));
    assertEquals(base, Allocator.alignAllocation(base, 4, 16, 4, false));
    assertEquals(base, Allocator.alignAllocation(base, 4, 12, 4, false));
    assertEquals(base, Allocator.alignAllocation(base, 4, 8, 4, false));
    assertEquals(base, Allocator.alignAllocation(base, 4, 4, 4, false));
    assertEquals(base, Allocator.alignAllocation(base, 4, 0, 4, false));
    assertEquals(base.plus(4), Allocator.alignAllocation(base.plus(4), 4, 16, 4, false));
    assertEquals(base.plus(4), Allocator.alignAllocation(base.plus(4), 4, 12, 4, false));
    assertEquals(base.plus(4), Allocator.alignAllocation(base.plus(4), 4, 8, 4, false));
    assertEquals(base.plus(4), Allocator.alignAllocation(base.plus(4), 4, 4, 4, false));
    assertEquals(base.plus(4), Allocator.alignAllocation(base.plus(4), 4, 0, 4, false));
  }

  // Cases where there should be 4 bytes of alignment required
  //
  // Just test the resulting alignment, don't write to memory.
  @Test
  public void testAlignAllocationFour() throws Throwable {
    Address base = Address.fromIntSignExtend(LOW_TEST_PAGE);

    assertEquals(base.plus(8), Allocator.alignAllocation(base.plus(4), 8, 16, 4, false));
    assertEquals(base.plus(4), Allocator.alignAllocation(base, 8, 20, 4, false));
    assertEquals(base.plus(4), Allocator.alignAllocation(base, 8, 12, 4, false));
  }

  // Cases where there should be no alignment required
  //
  // Same test cases as in testAlignAllocationZero, but also check that they've
  // Not written any alignment fill.
  @Test
  public void testAlignAllocationZeroFill() throws Throwable {
    SimulatedMemory.map(Address.fromIntSignExtend(LOW_TEST_PAGE), BYTES_IN_PAGE);
    Address base = Address.fromIntSignExtend(LOW_TEST_PAGE);

    Allocator.alignAllocation(base, 8, 16, 4, false);
    assertZero(base, 8);
    assertEquals(base, Allocator.alignAllocation(base, 8, 8, 4, false));
    assertZero(base, 8);
    assertEquals(base, Allocator.alignAllocation(base, 8, 0, 4, false));
    assertZero(base, 8);
    assertEquals(base, Allocator.alignAllocation(base, 4, 16, 4, false));
    assertZero(base, 8);
    assertEquals(base, Allocator.alignAllocation(base, 4, 12, 4, false));
    assertZero(base, 8);
    assertEquals(base, Allocator.alignAllocation(base, 4, 8, 4, false));
    assertZero(base, 8);
    assertEquals(base, Allocator.alignAllocation(base, 4, 4, 4, false));
    assertZero(base, 8);
    assertEquals(base, Allocator.alignAllocation(base, 4, 0, 4, false));
    assertZero(base, 8);
    assertEquals(base.plus(4), Allocator.alignAllocation(base.plus(4), 4, 16, 4, false));
    assertZero(base, 8);
    assertEquals(base.plus(4), Allocator.alignAllocation(base.plus(4), 4, 12, 4, false));
    assertZero(base, 8);
    assertEquals(base.plus(4), Allocator.alignAllocation(base.plus(4), 4, 8, 4, false));
    assertZero(base, 8);
    assertZero(base, 8);
    assertEquals(base.plus(4), Allocator.alignAllocation(base.plus(4), 4, 4, 4, false));
    assertZero(base, 8);
    assertEquals(base.plus(4), Allocator.alignAllocation(base.plus(4), 4, 0, 4, false));
    assertZero(base, 8);
  }

  private void assertZero(Address base, int bytes) {
    for (int offset = 0; offset < bytes; offset += Constants.LOG_BYTES_IN_INT) {
      assertEquals("Non-zero word found at offset " + offset, 0,
          base.loadInt(Offset.fromIntSignExtend(offset)));
    }
  }

  // Cases where there should be 4 bytes of alignment required
  @Test
  public void testAlignAllocationFourFill() throws Throwable {
    SimulatedMemory.map(Address.fromIntSignExtend(LOW_TEST_PAGE), BYTES_IN_PAGE);
    Address base = Address.fromIntSignExtend(LOW_TEST_PAGE);

    assertEquals(base.plus(8), Allocator.alignAllocation(base.plus(4), 8, 16, 4, true));
    assertEquals(Constants.ALIGNMENT_VALUE,
        base.loadInt(Offset.fromIntSignExtend(4)));
    assertEquals(base.plus(4), Allocator.alignAllocation(base, 8, 20, 4, true));
    assertEquals(Constants.ALIGNMENT_VALUE,  base.loadInt());
    assertEquals(base.plus(4), Allocator.alignAllocation(base, 8, 12, 4, true));
    assertEquals(Constants.ALIGNMENT_VALUE,  base.loadInt());
  }

  // Cases where there should be 4 bytes of alignment required
  // Target address in the 32-64 bit range
  @Test
  public void testAlignAllocationHighFourFill() throws Throwable {
    if (is64bit()) {
      Address base = Address.fromLong(HIGH_TEST_PAGE);
      SimulatedMemory.map(base, BYTES_IN_PAGE);

      assertEquals(base.plus(8), Allocator.alignAllocation(base.plus(4), 8, 16, 4, true));
      assertEquals(Constants.ALIGNMENT_VALUE,
          base.loadInt(Offset.fromIntSignExtend(4)));
      assertEquals(base.plus(4), Allocator.alignAllocation(base, 8, 20, 4, true));
      assertEquals(Constants.ALIGNMENT_VALUE,  base.loadInt());
      assertEquals(base.plus(4), Allocator.alignAllocation(base, 8, 12, 4, true));
      assertEquals(Constants.ALIGNMENT_VALUE,  base.loadInt());
    }
  }


}
