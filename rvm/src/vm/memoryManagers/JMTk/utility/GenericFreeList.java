/*
 * (C) Copyright Architecture and Language Implementation Laboratory,
 *     Department of Computer Science,
 *     University of Massachusetts at Amherst. 2001
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 * (C) Copyright IBM Corp. 2002
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
/**
 * This is a very simple, generic malloc-free allocator.  It works
 * abstractly, in "units", which the user may associate with some
 * other allocatable resource (e.g. heap blocks).  The user issues
 * requests for N units and the allocator returns the index of the
 * first of a contigious set of N units or fails, returning -1.  The
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
 *                      +-+-+-----------+-----------+
 *                      |f|m|    prev   | next/size |
 *                      +-+-+-----------+-----------+
 *
 *   - single free unit: "free", "single", "prev", "next"
 *   - single used unit: "used", "single"
 *   - contigious free units
 *     . first unit: "free", "multi", "prev", "next"
 *     . second unit: "free", "multi", "size"
 *     . last unit: "free", "multi", "size"
 *   - contigious used units
 *     . first unit: "used", "multi", "prev", "next"
 *     . second unit: "used", "multi", "size"
 *     . last unit: "used", "multi", "size"
 *   - any other unit: undefined
 *    
 *                      +-+-+-----------+-----------+
 *  top sentinel        |0|0|    tail   |   head    |  [-1]
 *                      +-+-+-----------+-----------+ 
 *                                    ....
 *           /--------  +-+-+-----------+-----------+
 *           |          |1|1|   prev    |   next    |  [j]
 *           |          +-+-+-----------+-----------+
 *           |          |1|1|           |   size    |  [j+1]
 *        free multi    +-+-+-----------+-----------+
 *        unit block    |              ...          |  ...
 *           |          +-+-+-----------+-----------+
 *           |          |1|1|           |   size    |
 *           >--------  +-+-+-----------+-----------+
 *  single free unit    |1|0|   prev    |   next    |
 *           >--------  +-+-+-----------+-----------+
 *  single used unit    |0|0|                       |
 *           >--------  +-+-+-----------------------+
 *           |          |0|1|                       |
 *           |          +-+-+-----------+-----------+
 *           |          |0|1|           |   size    |
 *        used multi    +-+-+-----------+-----------+
 *        unit block    |              ...          |
 *           |          +-+-+-----------+-----------+
 *           |          |0|1|           |   size    |
 *           \--------  +-+-+-----------+-----------+
 *                                    ....
 *                      +-+-+-----------------------+
 *  bottom sentinel     |0|0|                       |  [N]
 *                      +-+-+-----------------------+ 
 * </pre>
 * The sentinels serve as guards against out of range coalescing
 * because they both appear as "used" blocks and so will never
 * coalesce.  The top sentinel also serves as the head and tail of
 * the doubly linked list of free blocks.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 *
 */
final class GenericFreeList implements Constants {
   public final static String Id = "$Id$";
 
  ////////////////////////////////////////////////////////////////////////////
  //
  // Public instance methods
  //

  /**
   * Constructor
   */
  GenericFreeList(int units) {
    VM._assert(units <= MAX_UNITS);

    // allocate the data structure, including space for top & bottom sentinels
    table = new int[units + 2];
    
    // Initialize the sentiels
    setEntry(-1, SENTINEL_INIT);
    setEntry(units, SENTINEL_INIT);

    // add an entry to the free list containing everything
    setSize(0, units);
    setFree(0, true);
    addToFree(0);
  }

  /**
   * Allocate <code>size</code> units.  Return the unit ID
   *
   * @param size  The number of units to be allocated
   * @return The index of the first of the <code>size</code>
   * contigious units, or -1 if the request can't be satisfied
   */
  public int alloc(int size) {
    // Note: -1 is both the default return value *and* the start sentinel index
    int rtn = HEAD; // HEAD = -1
    int s = 0;

    while (((rtn = getNext(rtn)) != HEAD) && ((s = getSize(rtn)) < size));

    if (s >= size) {
      if (s > size)
	split(rtn, size);
      removeFromFree(rtn);
      setFree(rtn, false);
    }

    dbgPrintFree();
    return rtn;
  }

  /**
   * Free a previously allocated contigious lump of units.
   *
   * @param unit The index of the first unit.
   * @return The number of units freed.
   */
  public int free(int unit) {
    int freed = getSize(unit);
    int start = getFree(getLeft(unit)) ? getLeft(unit) : unit;
    int end = getFree(getRight(unit)) ? getRight(unit) : unit;
    if (start != end)
      coalesce(start, end);
    addToFree(start);
    dbgPrintFree();

    return freed;
  }

  /**
   * Return the size of the specified lump of units
   *
   * @param unit The index of the first unit in the lump.
   * @return The size of the lump, in units.
   */
  public int size(int unit) {
    return getSize(unit);
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Private fields and methods
  //

  /**
   * Reduce a lump of units to size, freeing any excess.
   *
   * @param unit The index of the first unit
   * @param size The size of the first part
   */
  private void split(int unit, int size) {
    int basesize = getSize(unit);
    VM._assert(basesize > size);
    setSize(unit, size);
    setSize(unit + size, basesize - size);
    addToFree(unit + size);
  }

  /**
   * Coalesce two or three contigious lumps of units, removing start
   * and end lumps from the free list as necessary.
   * @param start The index of the start of the first lump
   * @param start Index of the start of the last lump
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
    int next = getNext(HEAD);
    setNext(unit, next);
    setNext(HEAD, unit);
    setPrev(unit, HEAD);
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
  }

  /**
   * Get the size of a lump of units
   *
   * @param unit The first unit in the lump of units
   * @return The size of the lump of units
   */
  private int getSize(int unit)  {
    if ((getEntry(unit) & MULTI_MASK) == MULTI_MASK) 
      return (getEntry(unit + 1) & SIZE_MASK);
    else
      return 1;
  }

  /**
   * Set the size of lump of units
   *
   * @param unit The first unit in the lump of units
   * @param size The size of the lump of units
   */
  private void setSize(int unit, int size) {
    if (size > 1) {
      setEntry(unit, getEntry(unit) | MULTI_MASK);
      setEntry(unit + 1, MULTI_MASK | size);
      setEntry(unit + size - 1, MULTI_MASK | size);
    } else 
      setEntry(unit, getEntry(unit) & ~MULTI_MASK);
  }

  /**
   * Establish whether a lump of units is free
   *
   * @param unit The first or last unit in the lump
   * @return True if the lump is free
   */
  private boolean getFree(int unit) {
    return ((getEntry(unit) & FREE_MASK) == FREE_MASK);
  }
  
  /**
   * Set the "free" flag for a lump of units (both the first and last
   * units in the lump are set.
   *
   * @param unit The first unit in the lump
   * @param isFree True if the lump is to be marked as free
   */
  private void setFree(int unit, boolean isFree) {
    int size;
    if (isFree) {
      setEntry(unit, getEntry(unit) | FREE_MASK);
      if ((size = getSize(unit)) > 1)
	setEntry(unit + size - 1, getEntry(unit + size - 1) | FREE_MASK);
    } else {
      setEntry(unit, getEntry(unit) & ~FREE_MASK);
      if ((size = getSize(unit)) > 1)
	setEntry(unit + size - 1, getEntry(unit + size - 1) & ~FREE_MASK);
    }
  }
  
  /**
   * Get the next lump in the doubly linked free list
   *
   * @param unit The index of the first unit in the current lump
   * @return The index of the first unit of the next lump of units in the list
   */
  private int getNext(int unit) {
    int next = getEntry(unit) & NEXT_MASK;
    return (next <= MAX_UNITS) ? next : HEAD;
  }

  /**
   * Set the next lump in the doubly linked free list
   *
   * @param unit The index of the first unit in the lump to be set
   * @param next The value to be set.
   */
  private void setNext(int unit, int next) {
    if (VM.VerifyAssertions) 
      VM._assert((next >= HEAD) && (next <= MAX_UNITS));
    if (next == HEAD) 
      setEntry(unit, (getEntry(unit) | NEXT_MASK));
    else
      setEntry(unit, (getEntry(unit) & ~NEXT_MASK) | next);
  }

  /**
   * Get the previous lump in the doubly linked free list
   *
   * @param unit The index of the first unit in the current lump
   * @return The index of the first unit of the previous lump of units
   * in the list
   */
  private int getPrev(int unit) {
    int prev = (getEntry(unit) & PREV_MASK) >> PREV_SHIFT;
    return (prev <= MAX_UNITS) ? prev : HEAD;
  }

  /**
   * Set the previous lump in the doubly linked free list
   *
   * @param unit The index of the first unit in the lump to be set
   * @param prev The value to be set.
   */
  private void setPrev(int unit, int prev) {
    if (VM.VerifyAssertions) 
      VM._assert((prev >= HEAD) && (prev <= MAX_UNITS));
    if (prev == HEAD)
      setEntry(unit, (getEntry(unit) | PREV_MASK));
    else
      setEntry(unit, (getEntry(unit) & ~PREV_MASK) | (prev << PREV_SHIFT));
  }

  /**
   * Get the lump to the "left" of the current lump (i.e. "above" it)
   *
   * @param unit The index of the first unit in the lump in question
   * @return The index of the first unit in the lump to the
   * "left"/"above" the lump in question.
   */
  private int getLeft(int unit) {
    if ((getEntry(unit - 1) & MULTI_MASK) == MULTI_MASK)
      return unit - (getEntry(unit - 1) & SIZE_MASK);
    else
      return unit - 1;
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
   * Get the contents of an entry
   * 
   * @param unit The index of the unit
   * @return The contents of the unit
   */
  private int getEntry(int unit) {
    return table[unit + 1];
  }

  /**
   * Set the contents of an entry
   * 
   * @param unit The index of the unit
   * @param value The contents of the unit
   */
  private void setEntry(int unit, int value) {
    table[unit + 1] = value;
  }

  /**
   * Print the free list (for debugging purposes)
   */
  private void dbgPrintFree() {
    if (DEBUG) {
      VM.sysWrite("FL[");
      int i = HEAD;
      while ((i = getNext(i)) != HEAD) {
	boolean f = getFree(i);
	int s = getSize(i);
	if (!f)
	  VM.sysWrite("->");
	VM.sysWrite(i,false);
	if (!f)
	  VM.sysWrite("<-");
	VM.sysWrite("[");
	VM.sysWrite(s,false);
	VM.sysWrite("]");
	VM.sysWrite(" ");
      }
      VM.sysWrite("]FL\n");
    }
  }

  private static final boolean DEBUG = false;
  private static final int HEAD = -1;
  private static final int LOG_UNIT_SIZE = 2;
  private static final int TOTAL_BITS = 32;
  private static final int UNIT_BITS = (TOTAL_BITS - 2)>>1;
  private static final int MAX_UNITS = ((1<<UNIT_BITS) - 1) - 1;
  private static final int NEXT_MASK = (1<<UNIT_BITS) - 1;
  private static final int PREV_SHIFT = UNIT_BITS;
  private static final int PREV_MASK = ((1<<UNIT_BITS) - 1) << PREV_SHIFT;
  private static final int FREE_MASK = 1<<(TOTAL_BITS-1);
  private static final int MULTI_MASK = 1<<(TOTAL_BITS-2);
  private static final int SIZE_MASK = (1<<UNIT_BITS) - 1;
  
  // want the sentinels to be "used" & "single", and want first
  // sentinel to initially point to itself.
  private static final int SENTINEL_INIT = NEXT_MASK | PREV_MASK;

  private int[] table;
}
