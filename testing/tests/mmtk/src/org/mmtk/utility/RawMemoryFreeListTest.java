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

import org.junit.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.mmtk.harness.scheduler.MMTkThread;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.harness.SimulatedMemory;
/**
 * Junit unit-tests for ObjectReferenceDeque.
 */
public abstract class RawMemoryFreeListTest extends FreeListTests {

  private static final int ONE_MEG = 1024 * 1024;

  protected static final int PAGE_SIZE = 4096;

  private static final boolean VERBOSE = false;

  protected static Address baseAddress;

  @After
  public void afterMethod() {
    /*
     * Our largest list is 2^10 entries, 8 bytes per entry plus the list heads.
     */
    SimulatedMemory.unmap(baseAddress, ONE_MEG * 8 + PAGE_SIZE);
  }

  @Test
  public void testGenericFreeListIntSmall() throws Throwable {
    Thread t = new MMTkThread() {
      @Override
      public void run() {
        new RawMemoryFreeList(baseAddress, mapLimit(1024,1), 1024);
      }
    };
    runMMTkThread(t);
  }

  @Test
  public void testGenericFreeListIntLarge() throws Throwable {
    Thread t = new MMTkThread() {
      @Override
      public void run() {
        new RawMemoryFreeList(baseAddress, mapLimit(ONE_MEG,1), ONE_MEG);
      }
    };
    runMMTkThread(t);
  }

  @Test
  public void testRawMemoryFreeListAddrAddrIntInt() throws Throwable {
    Thread t = new MMTkThread() {
      @Override
      public void run() {
        new RawMemoryFreeList(baseAddress, mapLimit(1024,1), 1024,1);
      }
    };
    runMMTkThread(t);
  }

  @Test
  public void testRawMemoryFreeListAddrAddrIntIntLarge() throws Throwable {
    Thread t = new MMTkThread() {
      @Override
      public void run() {
        new RawMemoryFreeList(baseAddress, mapLimit(ONE_MEG,2), ONE_MEG,2);
      }
    };
    runMMTkThread(t);
  }

  @Test
  public void testRawMemoryFreeListAddrAddrIntIntEmpty() throws Throwable {
    Thread t = new MMTkThread() {
      @Override
      public void run() {
        new RawMemoryFreeList(baseAddress, baseAddress.plus(PAGE_SIZE), 0,1);
      }
    };
    runMMTkThread(t);
  }

  @Test
  public void testGenericFreeListIntIntInt() throws Throwable {
    Thread t = new MMTkThread() {
      @Override
      public void run() {
        new RawMemoryFreeList(baseAddress, mapLimit(1024,1), 1024,1,1);
      }
    };
    runMMTkThread(t);
  }

  @Test
  public void testGenericFreeListIntIntIntLarge() throws Throwable {
    Thread t = new MMTkThread() {
      @Override
      public void run() {
        new RawMemoryFreeList(baseAddress, mapLimit(ONE_MEG,32), ONE_MEG,2,32);
      }
    };
    runMMTkThread(t);
  }

  @Test
  public void testMulti() throws Throwable {
    Thread t = new MMTkThread() {
      @Override
      public void run() {
        RawMemoryFreeList fl = new RawMemoryFreeList(baseAddress, mapLimit(ONE_MEG,32), 1024,8,1);
        fl.growFreeList(0);
        fl.dbgPrintSummary();
        fl.dbgPrintDetail();
        fl.dbgPrintFree();

        fl.growFreeList(16);
        fl.dbgPrintDetail();
        fl.dbgPrintFree();

        assertEquals("initial 8-unit allocation", 0, fl.alloc(8));
        fl.dbgPrintDetail();
        fl.dbgPrintFree();

        for (int i = 8; i < 16; i++) {
          assertEquals("following 1-unit allocation", i, fl.alloc(1));
        }
        fl.dbgPrintDetail();
        fl.dbgPrintFree();
        assertEquals("list should be empty", -1, fl.alloc(1));
      }
    };
    runMMTkThread(t);
  }

  @Test
  public void testMulti2() throws Throwable {
    Thread t = new MMTkThread() {
      @Override
      public void run() {
        RawMemoryFreeList fl = new RawMemoryFreeList(baseAddress, mapLimit(ONE_MEG,1), 1024,1024,1);
        fl.resizeFreeList();

        int blockSize = 16;
        for (int block = 0; block < 4; block++) {
          fl.growFreeList(blockSize);

          int offset = block * blockSize;

          fl.setUncoalescable(offset);
          fl.setUncoalescable(offset + blockSize);
          assertEquals("initial 8-unit allocation", offset, fl.alloc(8,offset));

          for (int i = offset + 8; i < offset + blockSize; i++) {
            assertEquals("following 1-unit allocation", i, fl.alloc(1));
          }
          assertEquals("list should be empty", -1, fl.alloc(1));
        }
      }
    };
    runMMTkThread(t);
  }

  /**
   * This test attempts to reproduce the pattern used by the FreeListPageResource
   * with a discontiguous space.
   *
   * We allocate one or more chunks, then we allocate a set of units for metadata
   * at the chunk boundaries, then we free
   *
   * @throws Throwable
   */
  @Test
  public void testMulti3() throws Throwable {
    Thread t = new MMTkThread() {
      @Override
      public void run() {
        RawMemoryFreeList fl = new RawMemoryFreeList(baseAddress, mapLimit(ONE_MEG,1), 1024,16,1);
        fl.resizeFreeList();

        final int blockSize = 16;  // Size of an allocation block
        final int blocks = 4;  // Number of blocks allocated per request
        final int meta = 4; // Metadata pages per block

        boolean[] mdPages = new boolean[blockSize * blocks];

        fl.growFreeList(blockSize * blocks);
        fl.setUncoalescable(0);
        fl.setUncoalescable(blockSize * blocks + 1);

        // Simulate liberating free blocks


        // Simulate reserving metadata
        fl.dbgPrintDetail();
        for (int offset = 0; offset < blockSize * blocks; offset += blockSize) {
          if (offset != 0) fl.clearUncoalescable(offset);
          int liberated = fl.free(offset, true);

          assertEquals("initial metadata allocation at " + offset, offset, fl.alloc(meta, offset));
          for (int j = 0; j < meta; j++)
            mdPages[offset + j] = true;
        }

        // Allocate the remaining blocks
        for (int block = 0; block < blocks; block++) {
          int offset = block * blockSize;

          for (int i = offset + meta; i < offset + blockSize; i++) {
            int page = fl.alloc(1);
            assertNotEquals("following 1-unit allocation", -1, page);
            assertFalse("Metadata re-allocated at page " + page, mdPages[page]);
          }
        }
        assertEquals("list should be empty", -1, fl.alloc(1));
      }
    };
    runMMTkThread(t);
  }

  @Override
  protected GenericFreeList createFreeList(int units, int grain, int heads) {
    RawMemoryFreeList result = new RawMemoryFreeList(baseAddress, mapLimit(units,heads), units,grain,heads);
    result.growFreeList(units);
    return result;
  }

  @Test
  public void testGrowFreeList_1024_8_32() throws Throwable {
    growFreeList(1024, 8, 63, 1);
  }

  @Test
  public void testGrowFreeList_1024_4_1_1() throws Throwable {
    growFreeList(1024, 4, 1, 1);
  }

  @Test
  public void testGrowFreeList_1024_4_1_4() throws Throwable {
    growFreeList(1024, 4, 1, 4);
  }

  @Test
  public void testGrowFreeList_1M_1M_7() throws Throwable {
    growFreeList(ONE_MEG, ONE_MEG, 7, 1);
  }

  /*
   * One full page in 32-bit mode
   */
  @Test
  public void testGrowFreeList_508_16_3() throws Throwable {
    growFreeList(508, 16, 3, 1);
  }

  /*
   * One full page _+ 1 in 32-bit mode
   */
  @Test
  public void testGrowFreeList_509_16_3() throws Throwable {
    growFreeList(509, 16, 3, 1);
  }

  /*
   * One full page in 64-bit entries (which we don't (yet) use)
   */
  @Test
  public void testGrowFreeList_253_16_3() throws Throwable {
    growFreeList(496, 16, 3, 1);
  }

  /*
   * One full page + 1 in 64-bit mode
   */
  @Test
  public void testGrowFreeList_254_16_3() throws Throwable {
    growFreeList(256 - 3 - 8, 3, 8, 1);
  }

  protected void growFreeList(final int units, final int grain, final int heads, final int growBy) throws Throwable {
    Thread t = new MMTkThread() {
      int unit = 1;
      boolean[] allocated = new boolean[units];
      RawMemoryFreeList fl;
      @Override
      public void run() {
        fl = new RawMemoryFreeList(baseAddress, mapLimit(units, heads), 1, units, grain, heads);
        fl.dbgPrintSummary();

        for (int i = 0; i < units; i++) {
          allocated[i] = false;
        }

        int total = 0;
        int growUnits = growBy * grain;
        while (total + growUnits <= units) {
          fl.growFreeList(growUnits);
          total += growUnits;
          for (int i = 0; i < growUnits; i++) {
            assertNotEquals("allocation failure at unit " + unit, GenericFreeList.FAILURE, allocate());
          }
        }
        assertEquals("Free list should be exhausted at unit " + total, -1, allocate());
        fl.dbgPrintSummary();
        for (int i = 0; i < total; i++) {
          assertTrue("Unit " + (i + 1) + " is not allocated", allocated[i]);
        }
      }
      protected long allocate() {
        unit++;
        int block = fl.alloc(1);
        if (block == -1) {
          return block;
        }
        assertFalse("Allocated same block twice, " + block, allocated[block]);
        allocated[block] = true;
        return block;
      };
    };
    runMMTkThread(t);
  }

  @Test
  public void testFreeList_1M_1M_7_1024() throws Throwable {
    growFreeListMultiBlock(10 * 1024, 10 * 1024, 32, 1024);
  }

  protected void growFreeListMultiBlock(final int units, final int grain, final int heads, final int size) throws Throwable {

    Thread t = new MMTkThread() {
      int unit = 0;
      boolean[] allocated = new boolean[units];
      RawMemoryFreeList fl;
      @Override
      public void run() {
        int blockSize = RawMemoryFreeList.defaultBlockSize(units, heads);
        int mapSize = RawMemoryFreeList.sizeInPages(units, heads);
        if (VERBOSE) System.out.printf("growFreeListMultiBlock: blockSize=%d mapSize=%d%n", blockSize, mapSize);
        mapSize += mapSize % blockSize == 0 ? 0 : blockSize - mapSize % blockSize;

        fl = new RawMemoryFreeList(baseAddress,
            baseAddress.plus(Conversions.pagesToBytes(mapSize)),
            blockSize,
            units, grain, heads);
        fl.dbgPrintSummary();

        for (int i = 0; i < units; i++) {
          allocated[i] = false;
        }

        fl.dbgPrintSummary();
        fl.growFreeList(units);
        for (int i = 0; i < units / size; i++) {
          assertNotEquals("allocation failure at unit " + unit, GenericFreeList.FAILURE, allocate());
        }
        assertEquals("Free list should be exhausted at unit " + unit, -1, allocate());
        fl.dbgPrintSummary();
        for (int i = 0; i < units; i += size) {
          assertTrue("Unit " + (i + 1) + " is not allocated", allocated[i]);
        }
      }
      protected long allocate() {
        unit += size;
        long block = fl.alloc(size);
        if (block == -1) {
          return block;
        }
        assertFalse("Allocated same block twice, " + block, allocated[(int)block]);
        allocated[(int)block] = true;
        return block;
      };
    };
    runMMTkThread(t);
  }



  static Address mapLimit(final int units, final int heads) {
    //final int WORD_SIZE = ArchitecturalWord.getModel().bytesInWord();
    final int WORD_SIZE = 4;
    return baseAddress.plus(Math.floorDiv(((units + heads + 1) * WORD_SIZE * 2) + (PAGE_SIZE - 1), PAGE_SIZE) * PAGE_SIZE);
  }


}
