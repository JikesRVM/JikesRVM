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

import static org.junit.Assert.assertEquals;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.mmtk.harness.scheduler.MMTkThread;

public abstract class FreeListTests {

  protected abstract GenericFreeList createFreeList(int units, int grain,
      int heads);

  @Test
  public void testSetSentinel() throws Throwable {
    Thread t = new MMTkThread() {
      public void run() {
        GenericFreeList fl = createFreeList(1024, 2, 32);
        fl.setSentinel(1);
      }
    };
    runMMTkThread(t);
  }

  /**
   * Sequentially alloc single units until the list is free
   * @throws Throwable
   * @throws InterruptedException
   */
  @Test
  public void testAlloc() throws Throwable {
    Thread t = new MMTkThread() {
      public void run() {
        GenericFreeList fl = createFreeList(4,2,2);
        assertEquals(0, fl.alloc(1));
        assertEquals(1, fl.alloc(1));
        assertEquals(2, fl.alloc(1));
        assertEquals(3, fl.alloc(1));
        assertEquals(-1, fl.alloc(1));
      }
    };
    runMMTkThread(t);
  }

  /**
   * Sequentially alloc pairs of units until the list is free.
   */
  @Test
  public void testAlloc2() throws Throwable {
    Thread t = new MMTkThread() {
      public void run() {
        GenericFreeList fl = createFreeList(6,2,32);
        assertEquals(0, fl.alloc(2));
        assertEquals(2, fl.alloc(2));
        assertEquals(4, fl.alloc(2));
        assertEquals(-1, fl.alloc(2));
      }
    };
    runMMTkThread(t);
  }

  /**
   * Allocate and free units, ensuring that the allocator correctly reallocates them.
   */
  @Test
  public void testAlloc3() throws Throwable {
    Thread t = new MMTkThread() {
      public void run() {
        GenericFreeList fl = createFreeList(4,2,32);
        assertEquals(0, fl.alloc(1));
        assertEquals(1, fl.alloc(1));
        assertEquals(2, fl.alloc(1));
        assertEquals(3, fl.alloc(1));
        assertEquals(-1, fl.alloc(1));
        fl.free(1);
        assertEquals(1, fl.alloc(1));
        fl.free(2);
        assertEquals(2, fl.alloc(1));
        assertEquals(-1, fl.alloc(1));
        fl.free(3);
        fl.free(0);
        assertEquals(0, fl.alloc(1));
        assertEquals(3, fl.alloc(1));
        assertEquals(-1, fl.alloc(1));
      }
    };
    runMMTkThread(t);
  }

  /**
   * Allocate and free units, ensuring that the allocator correctly coalesces adjacent units
   */
  @Test
  public void testAlloc4() throws Throwable {
    Thread t = new MMTkThread() {
      public void run() {
        GenericFreeList fl = createFreeList(4,2,32);
        assertEquals(0, fl.alloc(1));
        assertEquals(1, fl.alloc(1));
        assertEquals(2, fl.alloc(1));
        assertEquals(3, fl.alloc(1));
        assertEquals(-1, fl.alloc(1));
        fl.free(1);
        fl.free(0);
        assertEquals(0, fl.alloc(2));
        assertEquals(-1, fl.alloc(1));
      }
    };
    runMMTkThread(t);
  }

  /**
   * Allocate different sizes, ensuring that allocations are correctly aligned
   */
  @Test
  public void testAlloc5() throws Throwable {
    Thread t = new MMTkThread() {
      public void run() {
        GenericFreeList fl = createFreeList(4,2,1);
        assertEquals(0, fl.alloc(1));
        assertEquals(2, fl.alloc(2));
        assertEquals(1, fl.alloc(1));
        assertEquals(-1, fl.alloc(1));
      }
    };
    runMMTkThread(t);
  }

  /**
   * Test coalescing across different unit sizes
   */
  @Test
  public void testAlloc6() throws Throwable {
    Thread t = new MMTkThread() {
      public void run() {
        GenericFreeList fl = createFreeList(4,2,32);
        assertEquals(0, fl.alloc(1));
        assertEquals(2, fl.alloc(2));
        assertEquals(1, fl.alloc(1));
        assertEquals(-1, fl.alloc(1));
        fl.free(0);
        fl.free(2);
        assertEquals(-1, fl.alloc(4));
        fl.free(1);
        assertEquals(0, fl.alloc(4));
      }
    };
    runMMTkThread(t);
  }

  protected void runMMTkThread(Thread t) throws Throwable {
    final AtomicReference<Throwable> thrown = new AtomicReference<Throwable>();

    t.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
      @Override
      public void uncaughtException(Thread t, Throwable e) {
        thrown.set(e);
      }
    });
    t.start();
    t.join();
    if (thrown.get() != null) {
      throw thrown.get();
    }
  }


}
