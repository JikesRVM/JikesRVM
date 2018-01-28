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
package org.mmtk.utility;

import java.util.Arrays;

import org.junit.BeforeClass;
import org.junit.Test;
import org.mmtk.harness.Harness;
import org.mmtk.harness.scheduler.MMTkThread;
import org.vmmagic.unboxed.Address;

public class RawMemoryFreeListTest32 extends RawMemoryFreeListTest {

  private static final long TEST_BASE = 0xF0000000L;

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    Harness.initArchitecture(Arrays.asList("plan=NoGC", "bits=32"));
    Harness.initOnce();
    baseAddress = Address.fromLong(TEST_BASE);
  }

  @Override
  @Test
  public void testAlloc5() throws Throwable {
    super.testAlloc5();
  }

  @Test
  public void compareLists() throws Throwable {
    Thread t = new MMTkThread() {
      @Override
      public void run() {
        RawMemoryFreeList raw = new RawMemoryFreeList(baseAddress, mapLimit(1024,1), 16, 4);
        raw.growFreeList(16);
        GenericFreeList cooked = new IntArrayFreeList(16, 4);

        raw.dbgPrintDetail();
        cooked.dbgPrintDetail();

        raw.setUncoalescable(0);
        raw.setUncoalescable(4);
        cooked.setUncoalescable(0);
        cooked.setUncoalescable(4);

        raw.alloc(4,0);
        cooked.alloc(4,0);
        raw.free(0);
        cooked.free(0);

//        raw.dbgPrintFree();
        raw.dbgPrintDetail();
//        cooked.dbgPrintFree();
        cooked.dbgPrintDetail();

      }
    };
    runMMTkThread(t);
  }


}
