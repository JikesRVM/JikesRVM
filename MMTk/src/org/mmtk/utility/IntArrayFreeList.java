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

import org.mmtk.plan.Plan;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;

/**
 * This is a very simple, generic malloc-free allocator.  It works
 * abstractly, in "units", which the user may associate with some
 * other allocatable resource (e.g. heap blocks).  The user issues
 * requests for N units and the allocator returns the index of the
 * first of a contiguous set of N units or fails, returning -1.  The
 * user frees the block of N units by calling <code>free()</code> with
 * the index of the first unit as the argument.<p>
 *
 * Properties/Constraints:<ul>
 *   <li> The allocator consumes one word per allocatable unit (plus
 *   a fixed overhead of about 128 words).</li>
 *   <li> The allocator can only deal with MAX_UNITS units (see below for
 *   the value).</li>
 * </ul>
 *
 * The basic data structure used by the algorithm is a large table,
 * with one word per allocatable unit.  Each word is used in a
 * number of different ways, some combination of "undefined" (32),
 * "free/used" (1), "multi/single" (1), "prev" (15), "next" (15) &amp;
 * "size" (15) where field sizes in bits are in parenthesis.
 * <pre>
 *                       +-+-+-----------+-----------+
 *                       |f|m|    prev   | next/size |
 *                       +-+-+-----------+-----------+
 *
 *   - single free unit: "free", "single", "prev", "next"
 *   - single used unit: "used", "single"
 *    - contiguous free units
 *     . first unit: "free", "multi", "prev", "next"
 *     . second unit: "free", "multi", "size"
 *     . last unit: "free", "multi", "size"
 *    - contiguous used units
 *     . first unit: "used", "multi", "prev", "next"
 *     . second unit: "used", "multi", "size"
 *     . last unit: "used", "multi", "size"
 *    - any other unit: undefined
 *
 *                       +-+-+-----------+-----------+
 *   top sentinel        |0|0|    tail   |   head    |  [-1]
 *                       +-+-+-----------+-----------+
 *                                     ....
 *            /--------  +-+-+-----------+-----------+
 *            |          |1|1|   prev    |   next    |  [j]
 *            |          +-+-+-----------+-----------+
 *            |          |1|1|           |   size    |  [j+1]
 *         free multi    +-+-+-----------+-----------+
 *         unit block    |              ...          |  ...
 *            |          +-+-+-----------+-----------+
 *            |          |1|1|           |   size    |
 *           &gt;--------  +-+-+-----------+-----------+
 *   single free unit    |1|0|   prev    |   next    |
 *           &gt;--------  +-+-+-----------+-----------+
 *   single used unit    |0|0|                       |
 *           &gt;--------  +-+-+-----------------------+
 *            |          |0|1|                       |
 *            |          +-+-+-----------+-----------+
 *            |          |0|1|           |   size    |
 *         used multi    +-+-+-----------+-----------+
 *         unit block    |              ...          |
 *            |          +-+-+-----------+-----------+
 *            |          |0|1|           |   size    |
 *            \--------  +-+-+-----------+-----------+
 *                                     ....
 *                       +-+-+-----------------------+
 *   bottom sentinel     |0|0|                       |  [N]
 *                       +-+-+-----------------------+
 * </pre>
 * The sentinels serve as guards against out of range coalescing
 * because they both appear as "used" blocks and so will never
 * coalesce.  The top sentinel also serves as the head and tail of
 * the doubly linked list of free blocks.
 */
@Uninterruptible
public final class IntArrayFreeList extends GenericFreeList {

  /****************************************************************************
   *
   * Public instance methods
   */

  /**
   * Constructor
   *
   * @param units The number of allocatable units for this free list
   */
  public IntArrayFreeList(int units) {
    this(units, units);
  }

  /**
   * Constructor
   *
   * @param units The number of allocatable units for this free list
   * @param grain Units are allocated such that they will never cross this granularity boundary
   */
  public IntArrayFreeList(int units, int grain) {
    this(units, grain, 1);
  }

  /**
   * Constructor
   *
   * @param units The number of allocatable units for this free list
   * @param grain Units are allocated such that they will never cross this granularity boundary
   * @param heads The number of free lists which will share this instance
   */
  public IntArrayFreeList(int units, int grain, int heads) {
    this.parent = null;
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(units <= MAX_UNITS && heads <= MAX_HEADS);
    this.heads = heads;
    head = -1;

    // allocate the data structure, including space for top & bottom sentinels
    table = new int[(units + 1 + heads) << 1];
    initializeHeap(units, grain);
  }

  /**
   * Resize the free list for a parent free list.
   * This must not be called dynamically (ie not after bootstrap).
   *
   * @param units The number of allocatable units for this free list
   * @param grain Units are allocated such that they will never cross this granularity boundary
   */
  @Override
  @Interruptible
  public void resizeFreeList(int units, int grain) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(parent == null && !Plan.isInitialized());
    table = new int[(units + 1 + heads) << 1];
    initializeHeap(units, grain);
  }

  /**
   * Resize the free list for a child free list.
   * This must not be called dynamically (ie not after bootstrap).
   */
  @Override
  @Interruptible
  public void resizeFreeList() {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(parent != null && !Plan.isInitialized());
    table = parent.getTable();
  }

  /**
   * Constructor
   *
   * @param parent The parent, owning the data structures this instance will share
   * @param ordinal The ordinal number of this child
   */
  public IntArrayFreeList(IntArrayFreeList parent, int ordinal) {
    this.parent = parent;
    this.table = parent.getTable();
    this.heads = parent.getHeads();
    this.head = -(1 + ordinal);
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(-this.head <= this.heads);
  }

  /* Getter */
  int[] getTable() {
    return table;
  }
  int getHeads() {
    return heads;
  }

  @Override
  protected int getEntry(int index) {
    return table[index];
  }

  @Override
  protected void setEntry(int index, int value) {
    table[index] = value;
  }

  /**
   * Print the entire list (for debugging purposes)
   */
  @Override
  public void dbgPrintDetail() {
    Log.writeln("--vvv-- Free List --vvv--");
    for (int i = -heads; i <= table.length / 2 - heads - 1; i++) {
      dbgPrintEntry(i);
    }
    Log.writeln("--^^^-- Free List --^^^--");
  }

  @Override
  public void dbgPrintSummary() {
    // Nothing to do
  }

  private int[] table;
  private final IntArrayFreeList parent;
}
