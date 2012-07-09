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
 * "free/used" (1), "multi/single" (1), "prev" (15), "next" (15) &
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
 *           >--------  +-+-+-----------+-----------+
 *   single free unit    |1|0|   prev    |   next    |
 *           >--------  +-+-+-----------+-----------+
 *   single used unit    |0|0|                       |
 *           >--------  +-+-+-----------------------+
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
public final class GenericFreeList extends BaseGenericFreeList implements Constants {

  /****************************************************************************
   *
   * Public instance methods
   */

  /**
   * Constructor
   *
   * @param units The number of allocatable units for this free list
   */
  public GenericFreeList(int units) {
    this(units, units);
  }

  /**
   * Constructor
   *
   * @param units The number of allocatable units for this free list
   * @param grain Units are allocated such that they will never cross this granularity boundary
   */
  public GenericFreeList(int units, int grain) {
    this(units, grain, 1);
  }

  /**
   * Constructor
   *
   * @param units The number of allocatable units for this free list
   * @param grain Units are allocated such that they will never cross this granularity boundary
   * @param heads The number of free lists which will share this instance
   */
  public GenericFreeList(int units, int grain, int heads) {
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
  public GenericFreeList(GenericFreeList parent, int ordinal) {
    this.parent = parent;
    this.table = parent.getTable();
    this.heads = parent.getHeads();
    this.head = -(1 + ordinal);
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(-this.head <= this.heads);
  }

  /* Getter */
  int[] getTable() { return table; }
  int getHeads() { return heads; }

  /**
   * Initialize a unit as a sentinel
   *
   * @param unit The unit to be initialized
   */
  @Override
  protected void setSentinel(int unit) {
    setLoEntry(unit, NEXT_MASK & unit);
    setHiEntry(unit, PREV_MASK & unit);
  }

  /**
   * Get the size of a lump of units
   *
   * @param unit The first unit in the lump of units
   * @return The size of the lump of units
   */
  @Override
  protected int getSize(int unit) {
    if ((getHiEntry(unit) & MULTI_MASK) == MULTI_MASK)
      return (getHiEntry(unit + 1) & SIZE_MASK);
    else
      return 1;
  }

  /**
   * Set the size of lump of units
   *
   * @param unit The first unit in the lump of units
   * @param size The size of the lump of units
   */
  @Override
  protected void setSize(int unit, int size) {
    if (size > 1) {
      setHiEntry(unit, getHiEntry(unit) | MULTI_MASK);
      setHiEntry(unit + 1, MULTI_MASK | size);
      setHiEntry(unit + size - 1, MULTI_MASK | size);
    } else
      setHiEntry(unit, getHiEntry(unit) & ~MULTI_MASK);
  }

  /**
   * Establish whether a lump of units is free
   *
   * @param unit The first or last unit in the lump
   * @return {@code true} if the lump is free
   */
  @Override
  protected boolean getFree(int unit) {
    return ((getLoEntry(unit) & FREE_MASK) == FREE_MASK);
  }

  /**
   * Set the "free" flag for a lump of units (both the first and last
   * units in the lump are set.
   *
   * @param unit The first unit in the lump
   * @param isFree {@code true} if the lump is to be marked as free
   */
  @Override
  protected void setFree(int unit, boolean isFree) {
    int size;
    if (isFree) {
      setLoEntry(unit, getLoEntry(unit) | FREE_MASK);
      if ((size = getSize(unit)) > 1)
        setLoEntry(unit + size - 1, getLoEntry(unit + size - 1) | FREE_MASK);
    } else {
      setLoEntry(unit, getLoEntry(unit) & ~FREE_MASK);
      if ((size = getSize(unit)) > 1)
        setLoEntry(unit + size - 1, getLoEntry(unit + size - 1) & ~FREE_MASK);
    }
  }

  /**
   * Get the next lump in the doubly linked free list
   *
   * @param unit The index of the first unit in the current lump
   * @return The index of the first unit of the next lump of units in the list
   */
  @Override
  protected int getNext(int unit) {
    int next = getHiEntry(unit) & NEXT_MASK;
    return (next <= MAX_UNITS) ? next : head;
  }

  /**
   * Set the next lump in the doubly linked free list
   *
   * @param unit The index of the first unit in the lump to be set
   * @param next The value to be set.
   */
  @Override
  protected void setNext(int unit, int next) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert((next >= -heads) && (next <= MAX_UNITS));
    int oldValue = getHiEntry(unit);
    int newValue = (oldValue & ~NEXT_MASK) | (next & NEXT_MASK);
    setHiEntry(unit, newValue);
  }

  /**
   * Get the previous lump in the doubly linked free list
   *
   * @param unit The index of the first unit in the current lump
   * @return The index of the first unit of the previous lump of units
   * in the list
   */
  @Override
  protected int getPrev(int unit) {
    int prev = getLoEntry(unit) & PREV_MASK;
    return (prev <= MAX_UNITS) ? prev : head;
  }

  /**
   * Set the previous lump in the doubly linked free list
   *
   * @param unit The index of the first unit in the lump to be set
   * @param prev The value to be set.
   */
  @Override
  protected void setPrev(int unit, int prev) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert((prev >= -heads) && (prev <= MAX_UNITS));
    int oldValue = getLoEntry(unit);
    int newValue = (oldValue & ~PREV_MASK) | (prev & PREV_MASK);
    setLoEntry(unit, newValue);
  }

  /**
   * Set the uncoalescable bit associated with this unit.
   * This ensures this unit cannot be coalesced with units below
   * it.
   *
   * @param unit The unit whose uncoalescable bit is to be set
   */
  public void setUncoalescable(int unit) {
    setLoEntry(unit, getLoEntry(unit) | COALESC_MASK);
  }

  /**
   * Clear the uncoalescable bit associated with this unit.
   * This allows this unit to be coalesced with units below
   * it.
   *
   * @param unit The unit whose uncoalescable bit is to be cleared
   */
  public void clearUncoalescable(int unit) {
    setLoEntry(unit, getLoEntry(unit) & ~COALESC_MASK);
  }

  /**
   * Return true if this unit may be coalesced with the unit below it.
   *
   * @param unit The unit in question
   * @return {@code true} if this unit may be coalesced with the unit below it.
   */
  @Override
  public boolean isCoalescable(int unit) {
    return (getLoEntry(unit) & COALESC_MASK) == 0;
  }

  /**
   * Get the lump to the "left" of the current lump (i.e. "above" it)
   *
   * @param unit The index of the first unit in the lump in question
   * @return The index of the first unit in the lump to the
   * "left"/"above" the lump in question.
   */
  @Override
  protected int getLeft(int unit) {
    if ((getHiEntry(unit - 1) & MULTI_MASK) == MULTI_MASK)
      return unit - (getHiEntry(unit - 1) & SIZE_MASK);
    else
      return unit - 1;
  }


  /**
   * Get the contents of an entry
   *
   * @param unit The index of the unit
   * @return The contents of the unit
   */
  private int getLoEntry(int unit) {
    return table[(unit + heads) << 1];
  }
  private int getHiEntry(int unit) {
    return table[((unit + heads) << 1) + 1];
  }

  /**
   * Set the contents of an entry
   *
   * @param unit The index of the unit
   * @param value The contents of the unit
   */
  private void setLoEntry(int unit, int value) {
    table[(unit + heads) << 1] = value;
  }
  private void setHiEntry(int unit, int value) {
    table[((unit + heads) << 1) + 1] = value;
  }

  private static final int TOTAL_BITS = 32;
  private static final int UNIT_BITS = (TOTAL_BITS - 2);
  public static final int MAX_UNITS = (int) (((((long) 1) << UNIT_BITS) - 1) - MAX_HEADS - 1);
  private static final int NEXT_MASK = (int) ((((long) 1) << UNIT_BITS) - 1);
  private static final int PREV_MASK = (int) ((((long) 1) << UNIT_BITS) - 1);
  private static final int FREE_MASK = 1 << (TOTAL_BITS - 1);
  private static final int MULTI_MASK = 1 << (TOTAL_BITS - 1);
  private static final int COALESC_MASK = 1 << (TOTAL_BITS - 2);
  private static final int SIZE_MASK = (int) ((((long) 1) << UNIT_BITS) - 1);

  private int[] table;
  private final GenericFreeList parent;
}
