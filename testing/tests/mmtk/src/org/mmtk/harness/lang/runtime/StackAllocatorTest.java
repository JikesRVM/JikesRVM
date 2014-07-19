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
package org.mmtk.harness.lang.runtime;

import static org.junit.Assert.assertEquals;
import static org.vmmagic.unboxed.harness.MemoryConstants.BYTES_IN_INT;
import static org.vmmagic.unboxed.harness.MemoryConstants.BYTES_IN_PAGE;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mmtk.harness.Harness;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;
import org.vmmagic.unboxed.harness.SimulatedMemory;

public class StackAllocatorTest {

  private static final int STACK_COUNT = 10;
  private static final int BASE_ADDRESS = 0x10000000;
  private static final int STACK_SIZE = 1024*1024;
  private static final int SIZE = STACK_COUNT*(STACK_SIZE+BYTES_IN_PAGE) + 2 * BYTES_IN_PAGE;

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    Harness.initOnce();
  }

  StackAllocator sa;

  @Before
  public void setUp() throws Exception {
    sa = new StackAllocator(Address.fromIntZeroExtend(BASE_ADDRESS),
        Extent.fromIntZeroExtend(SIZE),
        Extent.fromIntZeroExtend(STACK_SIZE));
  }

  @After
  public void tearDown() throws Exception {
    SimulatedMemory.unmap(Address.fromIntZeroExtend(BASE_ADDRESS), SIZE);
  }


  @Test
  public void testAlloc() {
    Address addr = sa.alloc();
    assertEquals(Address.fromIntZeroExtend(BASE_ADDRESS).plus(BYTES_IN_PAGE),addr);
  }

  @Test
  public void testAllocAll() {
    Address expected = Address.fromIntZeroExtend(BASE_ADDRESS).plus(BYTES_IN_PAGE);
    for (int i=0; i < STACK_COUNT; i++) {
      Address addr = sa.alloc();
      assertEquals(expected,addr);
      expected = expected.plus(STACK_SIZE + BYTES_IN_PAGE);
    }
  }

  @Test(expected=Error.class)
  public void testAllocOverflow() {
    for (int i=0; i <= STACK_COUNT; i++) {
      sa.alloc();
    }
  }

  @Test
  public void testFree() {
    Address addr = sa.alloc();
    sa.free(addr);
  }

  /**
   * Allocate all the stacks, then free them one by one.  Immediately
   * re-allocate them, and ensure that we get the same stack back.
   */
  @Test
  public void testAllocFree() {
    Address[] base = new Address[STACK_COUNT];
    for (int i=0; i < STACK_COUNT; i++) {
      base[i] = sa.alloc();
    }
    for (int i=0; i < STACK_COUNT; i++) {
      sa.free(base[i]);
      assertEquals(base[i],sa.alloc());
    }
  }

  @Test
  public void testMapped() {
    Address addr = sa.alloc();
    Address limit = addr.plus(STACK_SIZE);
    for (Address cursor = addr; cursor.LT(limit); cursor = cursor.plus(BYTES_IN_PAGE)) {
      cursor.store(0);
    }
  }


  @Test(expected=Error.class)
  public void testLowGuard() {
    sa.alloc();
    Address addr = sa.alloc();
    sa.alloc();
    addr.minus(BYTES_IN_INT).store(0);
  }

  @Test(expected=Error.class)
  public void testHighGuard() {
    sa.alloc();
    Address addr = sa.alloc();
    sa.alloc();
    addr.plus(STACK_SIZE).store(0);
  }

}
