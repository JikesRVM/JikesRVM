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
@Uninterruptible abstract class BaseGenericFreeList implements Constants {

  /****************************************************************************
   *
   * Public instance methods
   */

  /**
   * Allocate <code>size</code> units. Return the unit ID
   *
   * @param size  The number of units to be allocated
   * @return The index of the first of the <code>size</code>
   * contiguous units, or -1 if the request can't be satisfied
   */
  public final int alloc(int size) {
    // Note: -1 is both the default return value *and* the start sentinel index
    int unit = head; // HEAD = -1
    int s = 0;
    while (((unit = getNext(unit)) != head) && ((s = getSize(unit)) < size));

    return (unit == head) ? FAILURE : alloc(size, unit, s);
  }

  /**
   * Would an allocation of <code>size</code> units succeed?
   *
   * @param size The number of units to test for
   * @return True if such a request could be satisfied.
   */
  public final boolean couldAlloc(int size) {
    // Note: -1 is both the default return value *and* the start sentinel index
    int unit = head; // HEAD = -1
    while (((unit = getNext(unit)) != head) && (getSize(unit) < size));

    return (unit != head);
  }

  /**
   * Allocate <code>size</code> units. Return the unit ID
   *
   * @param size  The number of units to be allocated
   * @return The index of the first of the <code>size</code>
   * contiguous units, or -1 if the request can't be satisfied
   */
  public final int alloc(int size, int unit) {
    int s = 0;

    if (getFree(unit) && (s = getSize(unit)) >= size)
      return alloc(size, unit, s);
    else
      return FAILURE;
  }

  /**
   * Allocate <code>size</code> units. Return the unit ID
   *
   * @param size  The number of units to be allocated
   * @return The index of the first of the <code>size</code>
   * contiguous units, or -1 if the request can't be satisfied
   */
  private int alloc(int size, int unit, int unitSize) {
    if (unitSize >= size) {
      if (unitSize > size)
        split(unit, size);
      removeFromFree(unit);
      setFree(unit, false);
    }

    if (DEBUG) dbgPrintFree();

    return unit;
  }

  /**
   * Free a previously allocated contiguous lump of units.
   *
   * @param unit The index of the first unit.
   * @return return the size of the unit which was freed.
   */
  public final int free(int unit) {
    return free(unit, false);
  }

  /**
   * Free a previously allocated contiguous lump of units.
   *
   * @param unit The index of the first unit.
   * @param returnCoalescedSize if true, return the coalesced size
   * @return The number of units freed. if returnCoalescedSize is
   *  false, return the size of the unit which was freed.  Otherwise
   *   return the size of the unit now available (the coalesced size)
   */
  public final int free(int unit, boolean returnCoalescedSize) {
    int freed = getSize(unit);
    int left = getLeft(unit);
    int start = isCoalescable(unit) && getFree(left) ? left : unit;
    int right = getRight(unit);
    int end = isCoalescable(right) && getFree(right) ? right : unit;
    if (start != end)
      coalesce(start, end);

    if (returnCoalescedSize)
      freed = getSize(start);
    addToFree(start);

    if (DEBUG) dbgPrintFree();
    return freed;
  }

  /**
   * Return the size of the specified lump of units
   *
   * @param unit The index of the first unit in the lump.
   * @return The size of the lump, in units.
   */
  public final int size(int unit) {
    return getSize(unit);
  }

  /****************************************************************************
   *
   * Private fields and methods
   */

  /**
   * Initialize a new heap.  Fabricate a free list entry containing
   * everything
   *
   * @param units The number of units in the heap
   */
  protected final void initializeHeap(int units) {
    initializeHeap(units, units);
  }

  /**
   * Initialize a new heap.  Fabricate a free list entry containing
   * everything
   *
   * @param units The number of units in the heap
   */
  protected final void initializeHeap(int units, int grain) {
    // Initialize the sentinels
    for (int i = 1; i <= heads; i++)
      setSentinel(-i);
    setSentinel(units);

    // create the free list item
    int offset = units % grain;
    int cursor = units - offset;
    if (offset > 0) {
      setSize(cursor, offset);
      addToFree(cursor);
    }
    cursor -= grain;
    while (cursor >= 0) {
      setSize(cursor, grain);
      addToFree(cursor);
      cursor -= grain;
    }
    if (DEBUG) dbgPrintFree();
  }

  /**
   * Reduce a lump of units to size, freeing any excess.
   *
   * @param unit The index of the first unit
   * @param size The size of the first part
   */
  private void split(int unit, int size) {
    int basesize = getSize(unit);
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(basesize > size);
    setSize(unit, size);
    setSize(unit + size, basesize - size);
    addToFree(unit + size);
    if (DEBUG) dbgPrintFree();
  }

  /**
   * Coalesce two or three contiguous lumps of units, removing start
   * and end lumps from the free list as necessary.
   * @param start The index of the start of the first lump
   * @param end The index of the start of the last lump
   */
  private void coalesce(int start, int end) {
    if (getFree(end))
      removeFromFree(end);
    if (getFree(start))
      removeFromFree(start);

    setSize(start, end - start + getSize(end));
  }

  /**
   * Add a lump of units to the free list
   *
   * @param unit The first unit in the lump of units to be added
   */
  private void addToFree(int unit) {
    setFree(unit, true);
    int next = getNext(head);
    setNext(unit, next);
    setNext(head, unit);
    setPrev(unit, head);
    setPrev(next, unit);
  }

  /**
   * Remove a lump of units from the free list
   *
   * @param unit The first unit in the lump of units to be removed
   */
  private void removeFromFree(int unit) {
    int next = getNext(unit);
    int prev = getPrev(unit);
    setNext(prev, next);
    setPrev(next, prev);
    if (DEBUG) dbgPrintFree();
  }

  /**
   * Get the lump to the "right" of the current lump (i.e. "below" it)
   *
   * @param unit The index of the first unit in the lump in question
   * @return The index of the first unit in the lump to the
   * "right"/"below" the lump in question.
   */
  private int getRight(int unit) {
    return unit + getSize(unit);
  }


  /**
   * Print the free list (for debugging purposes)
   */
  void dbgPrintFree() {
    if (DEBUG) {
      Log.write("FL[");
      int i = head;
      while ((i = getNext(i)) != head) {
        boolean f = getFree(i);
        int s = getSize(i);
        if (!f)
          Log.write("->");
        Log.write(i);
        if (!f)
          Log.write("<-");
        Log.write("(");
        Log.write(s);
        Log.write(")");
        Log.write(" ");
        Log.flush();
      }
      Log.writeln("]FL");
    }
  }

  abstract void setSentinel(int unit);
  abstract int getSize(int unit);
  abstract void setSize(int unit, int size);
  abstract boolean getFree(int unit);
  abstract void setFree(int unit, boolean isFree);
  abstract int getNext(int unit);
  abstract void setNext(int unit, int next);
  abstract int getPrev(int unit);
  abstract void setPrev(int unit, int prev);
  abstract int getLeft(int unit);
  abstract boolean isCoalescable(int unit);

  protected static final boolean DEBUG = false;
  public static final int FAILURE = -1;
  protected static final int MAX_HEADS = 128; // somewhat arbitrary

  protected int heads = 1;
  protected int head = -heads;
}
