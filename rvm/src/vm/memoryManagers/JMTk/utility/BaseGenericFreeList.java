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
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
abstract class BaseGenericFreeList implements Constants, VM_Uninterruptible {
   public final static String Id = "$Id$";
 
  /****************************************************************************
   *
   * Public instance methods
   */

  /**
   * Allocate <code>size</code> units.  Return the unit ID
   *
   * @param size  The number of units to be allocated
   * @return The index of the first of the <code>size</code>
   * contigious units, or -1 if the request can't be satisfied
   */
  public final int alloc(int size) {
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
  public final int free(int unit) {
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
    // Initialize the sentiels
    setSentinel(-1);
    setSentinel(units);

    // create the free list item
    setSize(0, units);
    setFree(0, true);
    addToFree(0);
  }

  /**
   * Reduce a lump of units to size, freeing any excess.
   *
   * @param unit The index of the first unit
   * @param size The size of the first part
   */
  private final void split(int unit, int size) {
    int basesize = getSize(unit);
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(basesize > size);
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
  private final void coalesce(int start, int end) {
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
  private final void addToFree(int unit) {
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
  private final void removeFromFree(int unit) {
    int next = getNext(unit);
    int prev = getPrev(unit);
    setNext(prev, next);
    setPrev(next, prev);
  }

  /**
   * Get the lump to the "right" of the current lump (i.e. "below" it)
   *
   * @param unit The index of the first unit in the lump in question
   * @return The index of the first unit in the lump to the
   * "right"/"below" the lump in question.
   */
  private final int getRight(int unit) {
    return unit + getSize(unit);
  }


  /**
   * Print the free list (for debugging purposes)
   */
  private void dbgPrintFree() {
    if (DEBUG) {
      Log.write("FL[");
      int i = HEAD;
      while ((i = getNext(i)) != HEAD) {
        boolean f = getFree(i);
        int s = getSize(i);
        if (!f)
          Log.write("->");
        Log.write(i);
        if (!f)
          Log.write("<-");
        Log.write("[");
        Log.write(s);
        Log.write("]");
        Log.write(" ");
      }
      Log.write("]FL\n");
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

  protected static final boolean DEBUG = false;
  protected static final int HEAD = -1;
}
