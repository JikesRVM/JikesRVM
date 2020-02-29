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

import static org.mmtk.utility.Constants.MAX_INT;
import static org.mmtk.utility.Constants.LOG_BITS_IN_INT;
import static org.mmtk.utility.Constants.LOG_BITS_IN_BYTE;

import org.mmtk.vm.VM;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * Implementation of GenericFreeList backed by raw memory, allocated
 * on demand direct from the OS (via mmap).
 * <p>
 * The method growFreeList(int) must be called to grow the list.  Where
 * the list has a grain smaller than the maximum size of the list, it
 * can only grow in increments of whole grains.
 * <p>
 * Unlike its sibling IntArrayFreeList, it is not possible for a RawMemoryFreeList
 * to have multiple sub-lists threaded through the same list.
 */
@Uninterruptible
public final class RawMemoryFreeList extends GenericFreeList {

  /*
   * Size of a unit.
   *
   * Assuming that each unit represents a 4096-byte page, the maximum size
   * addressable by this map is ~ 2^40 bits.  Exceeding this requires increasing
   * the page size or changing the interface to the map from into to Word or long.
   */

  /** log2 of the number of bits used by a free list entry (two entries per unit) */
  private static final int LOG_ENTRY_BITS = LOG_BITS_IN_INT;

  /** log2 of the number of bytes used by a free list entry (two entries per unit) */
  private static final int LOG_BYTES_IN_ENTRY = LOG_ENTRY_BITS - LOG_BITS_IN_BYTE;

  /** log2 of the number of bytes used by a free list unit */
  private static final int LOG_BYTES_IN_UNIT = LOG_BYTES_IN_ENTRY + 1;

  /** Lowest address occupied by this list */
  private final Address base;

  /**
   * Highest address this list is allowed to occupy. Attempts to grow the
   * list beyond this will fail.
   */
  private final Address limit;

  /**
   * Memory addresses between base (inclusive) and highWater (exclusive) are already
   * allocated to the map.
   */
  private Address highWater;

  /** Maximum configured size of list in units */
  private final int maxUnits;

  /** Maximum configured size of list in units */
  private final int grain;

  /**
   * Current formatted size of list in units
   */
  private int currentUnits;

  /** The free list allocates VM in blocks of this many pages */
  private final int pagesPerBlock;

  /**
    Local version of Math.min(int, int)
   * @param a an integer
   * @param b another integer
   * @return the minimum value of the arguments
   */
  private static int min(int a, int b) {
    return a > b ? b : a;
  }

  /** @return the number of units held in a block (excluding list heads and sentinels) */
  protected int unitsPerBlock() {
    return Conversions.pagesToBytes(pagesPerBlock).toInt() >> LOG_BYTES_IN_UNIT;
  }

  /** @return the number of units held in the first block (including list heads and sentinels) */
  private int unitsInFirstBlock() {
    return unitsPerBlock() - heads - 1;
  }

  public static int defaultBlockSize(int units, int heads) {
    return min(sizeInPages(units, heads), 16);
  }

  /****************************************************************************
  *
  * Public static methods
  */

  /**
   * Calculate the size (in pages) of a map for a given number of units
   * and heads
   * @param units Number of units in the map
   * @param heads Number of independent heads
   * @return The size in pages of the map
   */
  public static int sizeInPages(int units, int heads) {
    Extent mapSize = Word.fromIntZeroExtend(units + heads + 1).lsh(LOG_BYTES_IN_UNIT).toExtent();
    return Conversions.bytesToPagesUp(mapSize);
  }


  /**
   * Constructor
   *
   * @param base lowest address occuppied by the list
   * @param limit highest address that the list could occupy
   * @param units The number of allocatable units for this free list
   *
   * Keeping grain as a unit restricts the maximum space size of a page-per-unit
   * allocator to 8TB.  Not terribly restrictive in 2016, but  ...
   */
  public RawMemoryFreeList(Address base, Address limit, int units) {
    this(base, limit, units, min(units,MAX_INT));
  }

  /**
   * Constructor
   *
   * @param base lowest address occuppied by the list
   * @param limit highest address that the list could occupy
   * @param units The number of allocatable units for this free list
   * @param grain Units are allocated such that they will never cross this granularity boundary
   */
  public RawMemoryFreeList(Address base, Address limit, int units, int grain) {
    this(base, limit, units, grain, 1);
  }

  RawMemoryFreeList(Address base, Address limit, int units, int grain, int heads) {
    this(base, limit, defaultBlockSize(units,heads), units, grain, heads);
  }

  /**
   * Constructor
   *
   * @param base lowest address occuppied by the list
   * @param limit highest address that the list could occupy
   * @param units The number of allocatable units for this free list
   * @param pagesPerBlock number of pages in a block
   * @param grain Units are allocated such that they will never cross this granularity boundary
   * @param heads The number of free lists which will share this instance
   */
  RawMemoryFreeList(Address base, Address limit, int pagesPerBlock, int units, int grain, int heads) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(units <= MAX_UNITS && heads <= MAX_HEADS);
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(base.plus(Conversions.pagesToBytes(sizeInPages(units,heads))).LE(limit));
    this.maxUnits = units;
    this.grain = grain;
    this.heads = heads;
    head = -1;

    this.pagesPerBlock = pagesPerBlock;

    this.base = base;
    this.limit = limit;
    this.highWater = base;
    this.currentUnits = 0;
    if (VERBOSE) {
      dbgPrintSummary();
    }
  }


  /****************************************************************************
   *
   * Public instance methods
   */

  /** {@inheritDoc} */
  @Override
  @Interruptible
  public void resizeFreeList() {
    // Nothing to do.
  }

  @Override
  public void resizeFreeList(int units, int heads) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions.fail("Unimplemented method");
  }

  @Override
  public int alloc(int size) {
    if (currentUnits == 0) {
      return FAILURE;
    }
    return super.alloc(size);
  }

  @Override
  protected int getEntry(int index) {
    Offset offset = Offset.fromIntZeroExtend(index << LOG_BYTES_IN_ENTRY);
    if (VM.VERIFY_ASSERTIONS) {
      if (base.plus(offset).LT(base) || base.plus(offset).GE(highWater)) {
        Log.write("getEntry(");
        Log.write(index);
        Log.write(") : addr=");
        Log.write(base.plus(offset));
        Log.write(", hwm=");
        Log.writeln(highWater);
        VM.assertions.fail("RawMemoryFreeList attempted to read an address outside the list");
      }
    }
    return base.loadInt(offset);
  }

  @Override
  protected void setEntry(int index, int value) {
    Offset offset = Offset.fromIntZeroExtend(index << LOG_BYTES_IN_ENTRY);
    if (VM.VERIFY_ASSERTIONS) {
      if (base.plus(offset).LT(base) || base.plus(offset).GE(highWater)) {
        Log.write("setEntry(");
        Log.write(index);
        Log.write(",");
        Log.write(value);
        Log.write(") : addr=");
        Log.write(base.plus(offset));
        Log.write(", hwm=");
        Log.writeln(highWater);
        VM.assertions.fail("RawMemoryFreeList attempted to write an address outside the list");
      }
    }
    base.store(value, offset);
  }

  /**
   * @return The current capacity (units) of the allocated blocks, allowing
   * one unit for the bottom sentinel.
   */
  private int currentCapacity() {
    int listBlocks = Conversions.bytesToPages(highWater.diff(base)) / pagesPerBlock;
    return unitsInFirstBlock() + (listBlocks - 1) * unitsPerBlock();
  }

  /**
   * Grows the free list by a specified number of units.
   *
   * @param units the number of units to add
   *
   * @return {@code true} if the list was grown, {@code false} if it was not
   * (e.g. because the maximum would have been surpasssed)
   */
  public boolean growFreeList(int units) {
    int requiredUnits = units + currentUnits;
    if (requiredUnits > maxUnits) {
      return false;
    }
    int blocks = 0;
    if (requiredUnits > currentCapacity()) {
      int unitsReqd = requiredUnits - currentCapacity();
      blocks = (unitsReqd + unitsPerBlock() - 1) / unitsPerBlock();
    }
    growListByBlocks(blocks, requiredUnits);
    return true;
  }

  /**
   * Grow the list and initialize the new portion.
   *
   * @param blocks Number of map blocks to add to the map (may be zero)
   * @param newMax The new capacity of the list in units
   */
  private void growListByBlocks(int blocks, int newMax) {
    if (VM.VERIFY_ASSERTIONS) {
      if ((newMax > grain) && ((newMax / grain) * grain != newMax)) {
        Log.write("growListByBlocks: newMax=", newMax);
        Log.write(", grain=", grain);
        Log.write(", (newMax / grain) * grain=");
        Log.writeln((newMax / grain) * grain);
      }
      VM.assertions._assert((newMax <= grain) || (((newMax / grain) * grain) == newMax));
    }

    if (VERBOSE) {
      dbgPrintSummary();
      Log.write("Growing free list by ", blocks);
      Log.write(" blocks to ", newMax);
      Log.writeln(" units.");
    }

    if (blocks > 0) {
      // Allocate more VM from the OS
      raiseHighWater(blocks);
    }

    int oldMax = currentUnits;
    if (newMax > currentCapacity()) {
      Log.write("newMax = ", newMax);
      Log.writeln(", currentCapacity() = ", currentCapacity());
      VM.assertions.fail("blocks and new max are inconsistent: need more blocks for the requested capacity");
    }
    if (newMax > maxUnits) {
      VM.assertions.fail("Requested list to grow larger than the configured maximum");
    }
    currentUnits = newMax;

    if (oldMax == 0) {
      // First allocation of capacity: initialize the sentinels.
      for (int i = 1; i <= heads; i++) {
        setSentinel(-i);
      }
    } else {
      // Turn the old top-of-heap sentinel into a single used block
      setSize(oldMax, 1);
    }

    if (newMax == 0) {
      return;
    }

    // Set a sentinel at the top of the new range
    setSentinel(newMax);

    int cursor = newMax;

    /* A series of grain size regions in the middle */
    int grain = min(this.grain, newMax - oldMax);
    cursor -= grain;
    while (cursor >= oldMax) {
      if (VERBOSE) {
        Log.write("Adding free block ");
        Log.write(cursor);
        Log.write("(", grain);
        Log.writeln(")");
      }
      setSize(cursor, grain);
      addToFree(cursor);
      cursor -= grain;
    }
    if (VERBOSE) dbgPrintSummary();
    if (DEBUG) dbgPrintFree();
  }

  /**
   * Raise the high water mark by requesting more pages from the OS
   *
   * @param blocks block count to raise by
   */
  private void raiseHighWater(int blocks) {
    Extent growExtent = Conversions.pagesToBytes(pagesPerBlock * blocks);
    if (highWater.EQ(limit)) {
      Log.write("limit=", limit);
      Log.write(", highWater=", highWater);
      Log.writeln(", growExtent=", growExtent);
      VM.assertions.fail("Attempt to grow FreeList beyond limit");
    }
    if (highWater.plus(growExtent).GT(limit)) {
      growExtent = highWater.diff(limit).toWord().toExtent();
    }
    mmap(highWater, growExtent);
    highWater = highWater.plus(growExtent);
  }

  private void mmap(Address start, Extent bytes) {
    int errno = VM.memory.dzmmap(start, bytes.toInt());
    if (errno != 0) {
      Log.write("mmap failed with errno ", errno);
      Log.writeln(" on address ", start);
      VM.assertions.fail("Can't get more space with mmap()");
    }
  }

  @Override
  public void dbgPrintSummary() {
    Log.write("RFL[", base);
    Log.write(":", limit);
    Log.write(", blksz=", pagesPerBlock);
    Log.write(", grain=", grain);
    Log.write(", hwm=", highWater);
    Log.write(", max=", maxUnits);
    Log.write(", cur=", currentUnits);
    Log.writeln("]");
  }

  @Override
  public void dbgPrintFree() {
    if (currentUnits > 0) {
      super.dbgPrintFree();
    } else {
      Log.writeln("FL[<empty>]FL");
    }
  }

  /**
   * Print the entire list (for debugging purposes)
   */
  @Override
  public void dbgPrintDetail() {
    Log.writeln("--vvv-- Free List --vvv--");
    if (currentUnits > 0) {
      for (int i = -heads; i <= currentUnits; i++) {
        dbgPrintEntry(i);
      }
    }
    Log.writeln("--^^^-- Free List --^^^--");
  }

  /**
   * @return The highest virtual address that can be occupied by this
   * list.
   */
  public Address getLimit() {
    return limit;
  }
}
