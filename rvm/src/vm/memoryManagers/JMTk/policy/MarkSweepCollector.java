/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_JavaHeader;

/**
 * Each instance of this class corresponds to one mark-sweep *space*.
 * Each of the instance methods of this class may be called by any
 * thread (i.e. synchronization must be explicit in any instance or
 * class method).  This contrasts with the MarkSweepAllocator, where
 * instances correspond to *plan* instances and therefore to kernel
 * threads.  Thus unlike this class, synchronization is not necessary
 * in the instance methods of MarkSweepAllocator.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
final class MarkSweepCollector implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  ////////////////////////////////////////////////////////////////////////////
  //
  // Class variables
  //
  private static final int LOG_BITMAP_GRAIN = 3;
  private static final int BITMAP_GRAIN = 1<<LOG_BITMAP_GRAIN;
  private static final int BITMAP_ENTRIES = PAGE_SIZE>>LOG_BITMAP_GRAIN;
  private static final int SMALL_BITMAP_PAIRS = BITMAP_ENTRIES>>LOG_WORD_BITS;
  public static final int SMALL_BITMAP_SIZE = 2*(SMALL_BITMAP_PAIRS<<LOG_WORD_SIZE);
  private static final int MID_BITMAP_PAIRS = 1;
  public static final int MID_BITMAP_SIZE = 2*(MID_BITMAP_PAIRS<<LOG_WORD_SIZE);
  public static final int MAX_MID_OBJECTS = MID_BITMAP_PAIRS<<LOG_WORD_BITS;
  private static final int    BITMAP_BASE = MarkSweepAllocator.BASE_SP_HEADER_SIZE;
  private static final int MAX_SMALL_SIZE = MarkSweepAllocator.MAX_SMALL_SIZE;
  private static final int SMALL_OBJ_BASE = BITMAP_BASE + SMALL_BITMAP_SIZE;
  private static final int   MID_OBJ_BASE = BITMAP_BASE + MID_BITMAP_SIZE;
  private static final int LOG_PAIR_GRAIN = LOG_BITMAP_GRAIN + LOG_WORD_BITS;

  private static final int     INUSE_BITMAP_OFFSET = 0;
  private static final int      MARK_BITMAP_OFFSET = WORD_SIZE;
  private static final int   TREADMILL_PREV_OFFSET = -2 * WORD_SIZE;
  private static final int   TREADMILL_NEXT_OFFSET = -3 * WORD_SIZE;
  private static final int  TREADMILL_OWNER_OFFSET = -4 * WORD_SIZE;
  public static final int    TREADMILL_HEADER_SIZE = 3 * WORD_SIZE;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Instance variables
  //
  private int markState;
  private FreeListVMResource vmResource;
  private MemoryResource memoryResource;
  private boolean inMSCollection = false;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Initialization
  //

  /**
   * Constructor
   *
   * @param vmr The virtual memory resource through which allocations
   * for this collector will go.
   * @param mr The memory resource against which allocations
   * associated with this collector will be accounted.
   */
  MarkSweepCollector(FreeListVMResource vmr, MemoryResource mr) {
    vmResource = vmr;
    memoryResource = mr;
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Allocation
  //

  /**
   *  This is called each time a cell is alloced (i.e. if a cell is
   *  reused, this will be called each time it is reused in the
   *  lifetime of the cell, by contrast to initializeCell, which is
   *  called exactly once.).
   *
   * @param cell The newly allocated cell
   * @param isScalar True if the cell will be occupied by a scalar
   * @param bytes The size of the cell in bytes
   * @param small True if the cell is for a small object
   * @param large True if the cell is for a large object
   * fresh allocation.
   * @param allocator The mark sweep allocator instance through which
   * this instance was allocated.
   */
  public final void postAlloc(VM_Address cell, boolean isScalar,
			      EXTENT bytes, boolean small, boolean large,
			      MarkSweepAllocator allocator)
    throws VM_PragmaInline {
    if (large)
      addToTreadmill(cell, allocator);
    else {
      VM_Address sp = MarkSweepAllocator.getSuperPage(cell, small);
      setInUseBit(cell, sp, small);
      if (inMSCollection)
	setMarkBit(cell, sp, small);
    }
  }

  /**
   * Return the initial value for the header of a new object instance.
   * The header for this collector includes a mark bit and a small
   * object flag.
   *
   * @param size The size of the newly allocated object
   */
  public final int getInitialHeaderValue(int size) 
    throws VM_PragmaInline {
    if (size <= MAX_SMALL_SIZE)
      return markState | MarkSweepHeader.SMALL_OBJECT_MASK;
    else
      return markState;
  }


  ////////////////////////////////////////////////////////////////////////////
  //
  // Collection
  //

  /**
   * Prepare for a new collection increment.  For the mark-sweep
   * collector we must flip the state of the mark bit between
   * collections.
   *
   * @param vm (unused)
   * @param mr (unused)
   */
  public void prepare(VMResource vm, MemoryResource mr) { 
    markState = MarkSweepHeader.MARK_BIT_MASK - markState;
    inMSCollection = true;
  }

  /**
   * A new collection increment has completed.  For the mark-sweep
   * collector this means we can perform the sweep phase.
   *
   * @param vm (unused)
   * @param mr (unused)
   */
  public void release() {
    inMSCollection = false;
  }

  /**
   * Return true if this mark-sweep space is currently being collected.
   *
   * @return True if this mark-sweep space is currently being collected.
   */
  public boolean inMSCollection() 
    throws VM_PragmaInline {
    return inMSCollection;
  }

  /**
   * Sweep a given superpage, freeing any unuse objects, and freeing
   * the entire superpage if possible.
   *
   * @param allocator The allocator instance to which this superpage
   * is associated.
   * @param sp The superpage to be freed
   * @param sizeClass The size class of this superpage
   * @param cellSize The size of each cell in this superpage, in bytes
   * @param free True if this superpage is on a superpage free list.
   * Otherwise it must be on a superpage used list.
   */
  public final void sweepSuperPage(MarkSweepAllocator allocator,
				   VM_Address sp, int sizeClass, int cellSize,
				   boolean free)
    throws VM_PragmaInline {
    VM_Address base = sp.add(BITMAP_BASE);
    boolean small = MarkSweepAllocator.isSmall(sizeClass);
    int bitmapPairs = small ? SMALL_BITMAP_PAIRS : MID_BITMAP_PAIRS;
    // first check to see if superpage is completely free and if
    // possible free the entire superpage
    if (!freePairs(allocator, sp, sizeClass, cellSize, base, bitmapPairs, small, false))
      allocator.freeSuperPage(sp, sizeClass, free);
    else
      freePairs(allocator, sp, sizeClass, cellSize, base, bitmapPairs, small, true);
  }

  /**
   * Walk through a set of mark/inuse pairs for a superpage.
   *
   * @param allocator The allocator instance through which any cells
   * should be freed
   * @param sp The superpage
   * @param sizeClass The size class for this superpage
   * @param cellSize The size of cells on this superpage
   * @param base The address of the first cell within the superpage
   * @param pairs The number of mark/inuse pairs
   * @param small True if this is a small superpage
   * @param release If true, then free up instances as they are
   * discovered.  If false do not free any instances, but return true
   * as soon as any in-use cell is discovered.
   * @return True if this superpage should be scavanged for free
   * instances, false if all instances are free, and therfore should
   * be freed enmasse.
   */
  private final boolean freePairs(MarkSweepAllocator allocator, VM_Address sp,
				  int sizeClass, int cellSize, VM_Address base,
				  int pairs, boolean small, boolean release) {
    boolean inUse = false;
    for (int pair = 0; pair < pairs; pair++) {
      if (VM.VerifyAssertions)
	VM._assert((INUSE_BITMAP_OFFSET == 0) 
		   && (MARK_BITMAP_OFFSET == WORD_SIZE));
      VM_Address inUseBitmap = base;
      base = base.add(WORD_SIZE);
      VM_Address markBitmap = base;
      base = base.add(WORD_SIZE);
      int mark = VM_Magic.getMemoryWord(markBitmap);
      if (release) {
	int inuse = VM_Magic.getMemoryWord(inUseBitmap);
	int free = mark ^ inuse;
	if (free != 0) {
	  freeFromBitmap(allocator, sp, free, sizeClass, cellSize, pair, small);
	  VM_Magic.setMemoryWord(inUseBitmap, mark); 
	}
	if (mark != 0)
	  VM_Magic.setMemoryWord(markBitmap, 0);
      } else {
	if (mark != 0)
	  return true;
      }
    }
    return false;
  }

  /**
   * Give a bitmap representing cells to be freed, free all objects on
   * a superpage which are no longer in use.
   *
   * @param allocator The allocator through which the cells are freed
   * @param sp The superpage containing these cells and bitmaps
   * @param free The bitmap of those instances to be freed
   * @param sizeClass The size class for this superpage
   * @param cellSize The size of cells on this superpage
   * @param pair The mark/inuse pair from which this free bitmap was
   * produced (inidicating the locations of the objects in the free
   * bitmap).
   * @param small True if these are small obejcts.
   */
  private final void freeFromBitmap(MarkSweepAllocator allocator, 
				    VM_Address sp, int free, int sizeClass,
				    int cellSize, int pair, boolean small)
    throws VM_PragmaInline {
    int index = (pair<<LOG_WORD_BITS);
    VM_Address base = sp.add(small ? SMALL_OBJ_BASE : MID_OBJ_BASE);
    for(int i=0; i < WORD_BITS; i++) {
      if ((free & (1<<i)) != 0) {
	int offset = (index + i)*cellSize + (small ? 0 : MarkSweepAllocator.NON_SMALL_OBJ_HEADER_SIZE);
	VM_Address cell = base.add(offset);
	allocator.free(cell, sp, sizeClass);
      }
    }
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Object processing and tracing
  //

  /**
   * Trace a reference to an object under a mark sweep collection
   * policy.  If the object header is not already marked, mark the
   * object in either the bitmap or by moving it off the treadmill,
   * and enqueue the object for subsequent processing. The object is
   * marked as (an atomic) side-effect of checking whether already
   * marked.
   *
   * @param object The object to be traced.
   * @return The object (there is no object forwarding in this
   * collector, so we always return the same object: this could be a
   * void method but for compliance to a more general interface).
   */
  public final VM_Address traceObject(VM_Address object)
    throws VM_PragmaInline {
    if (MarkSweepHeader.testAndMark(object, markState)) {
      internalMarkObject(object);
      VM_Interface.getPlan().enqueue(object);
    }
    return object;
  }

  /**
   * A new collection increment has completed.  For the mark-sweep
   * collector this means we can perform the sweep phase.
   *
   * @param obj The object in question
   * @return True if this object is known to be live (i.e. it is marked)
   */
   public boolean isLive(VM_Address obj)
    throws VM_PragmaInline {
     return MarkSweepHeader.testMarkBit(obj, markState);
   }

  /**
   * An object has been marked (identified as live).  Large objects
   * are added to the to-space treadmill, while all other objects will
   * have a mark bit set in the superpage header.
   *
   * @param object The object which has been marked.
   */
  private final void internalMarkObject(VM_Address object) 
    throws VM_PragmaInline {
    VM_Address ref = VM_JavaHeader.getPointerInMemoryRegion(object);
    if (MarkSweepHeader.isSmallObject(VM_Magic.addressAsObject(object))) {
      setMarkBit(ref, MarkSweepAllocator.getSuperPage(ref, true), true);
    } else {
      VM_Address cell = VM_JavaHeader.objectStartRef(object);
      VM_Address sp = MarkSweepAllocator.getSuperPage(cell, false);
      int sizeClass = MarkSweepAllocator.getSizeClass(sp);
      if (MarkSweepAllocator.isLarge(sizeClass)) 
	moveToTreadmill(cell, true, false);
      else
	setMarkBit(cell, sp, false);
    }
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Treadmill
  //

  /**
   * Return true if a cell is on a given treadmill
   *
   * @param cell The cell being searched for
   * @param head The head of the treadmill
   * @return True if the cell is found on the treadmill
   */
  public final boolean isOnTreadmill(VM_Address cell, VM_Address head) {
    VM_Address next = head;
    while (!next.isZero()) {
      if (next.EQ(cell)) 
	return true;
      next = getNextTreadmill(next);
    }
    return false;
  }

  public final void showTreadmill(MarkSweepAllocator a) {
    VM_Address next = a.getTreadmillFromHead();
    VM.sysWrite("FROM: ");
    VM.sysWrite(next);
    while (!next.isZero()) {
      next = getNextTreadmill(next);
      VM.sysWrite(" -> ", next);
    }
    VM.sysWriteln();
    next = a.getTreadmillToHead();
    VM.sysWrite("TO: ");
    VM.sysWrite(next);
    while (!next.isZero()) {
      next = getNextTreadmill(next);
      VM.sysWrite(" -> ", next);
    }
    VM.sysWriteln();
  }
  
  /**
   * Add a cell to the from-space treadmill
   *
   * @param cell The cell to be added to the treadmill
   * @param allocator The allocator through which this cell was
   * allocated.
   */
  public void addToTreadmill(VM_Address cell, MarkSweepAllocator allocator) 
    throws VM_PragmaInline {
    setTreadmillOwner(cell, VM_Magic.objectAsAddress((Object) allocator));
    moveToTreadmill(cell, inMSCollection, true);
  }

  /**
   * Move a cell to either the to or from space treadmills
   * 
   * @param cell The cell to be placed on the treadmill
   * @param to If true the cell should be placed on the to-space
   * treadmill.  Otherwise it should go on the from-space treadmill.
   * @param fresh If true then this is a new allocation, and therefore
   * is not already on the from-space treadmill.
   */
  private void moveToTreadmill(VM_Address cell, boolean to, boolean fresh) 
    throws VM_PragmaInline {
    MarkSweepAllocator owner = (MarkSweepAllocator) VM_Magic.addressAsObject(getTreadmillOwner(cell));
    owner.lockTreadmill();
    // If it is already on some other treadmill, remove it from there.
    if (to && !fresh) { 
      if (VM.VerifyAssertions)
	VM._assert(isOnTreadmill(cell, owner.getTreadmillFromHead()));
      // remove from "from" treadmill
      VM_Address prev = getPrevTreadmill(cell);
      VM_Address next = getNextTreadmill(cell);
      if (!prev.EQ(VM_Address.zero()))
	setNextTreadmill(prev, next);
      else
	owner.setTreadmillFromHead(next);
      if (!next.EQ(VM_Address.zero()))
	setPrevTreadmill(next, prev);
    } else {
      if (VM.VerifyAssertions)
	VM._assert(!isOnTreadmill(cell, owner.getTreadmillFromHead()));
    }

    // add to treadmill
    VM_Address head = (to ? owner.getTreadmillToHead() : owner.getTreadmillFromHead());
    setNextTreadmill(cell, head);
    setPrevTreadmill(cell, VM_Address.zero());
    if (!head.EQ(VM_Address.zero()))
      setPrevTreadmill(head, cell);
    if (to)
      owner.setTreadmillToHead(cell);
    else
      owner.setTreadmillFromHead(cell);

    if (VM.VerifyAssertions) {
      if (to)
	VM._assert(isOnTreadmill(cell, owner.getTreadmillToHead()));
      else
	VM._assert(isOnTreadmill(cell, owner.getTreadmillFromHead()));
    }

    owner.unlockTreadmill();
  }

  private static void setTreadmillOwner(VM_Address cell, VM_Address owner)
    throws VM_PragmaInline {
    setTreadmillLink(cell, owner, TREADMILL_OWNER_OFFSET);
  }
  private static void setNextTreadmill(VM_Address cell, VM_Address value)
    throws VM_PragmaInline {
    setTreadmillLink(cell, value, TREADMILL_NEXT_OFFSET);
  }
  private static void setPrevTreadmill(VM_Address cell, VM_Address value)
    throws VM_PragmaInline {
    setTreadmillLink(cell, value, TREADMILL_PREV_OFFSET);
  }
  private static void setTreadmillLink(VM_Address cell, VM_Address value,
				       int offset)
    throws VM_PragmaInline {
    VM_Magic.setMemoryAddress(cell.add(offset), value);
  }
  private static VM_Address getTreadmillOwner(VM_Address cell)
    throws VM_PragmaInline {
    return getTreadmillLink(cell, TREADMILL_OWNER_OFFSET);
  }
  public static VM_Address getNextTreadmill(VM_Address cell)
    throws VM_PragmaInline {
    return getTreadmillLink(cell, TREADMILL_NEXT_OFFSET);
  }
  private static VM_Address getPrevTreadmill(VM_Address cell)
    throws VM_PragmaInline {
    return getTreadmillLink(cell, TREADMILL_PREV_OFFSET);
  }
  private static VM_Address getTreadmillLink(VM_Address cell, int offset)
    throws VM_PragmaInline {
    return VM_Magic.getMemoryAddress(cell.add(offset));
  }
  
  ////////////////////////////////////////////////////////////////////////////
  //
  // Bitmap operations (inuse/marked)
  //
  public static void setInUseBit(VM_Address ref, VM_Address sp, boolean small)
    throws VM_PragmaInline {
    changeBit(ref, sp, small, true, true, false);
  }
  private static void unsetInUseBit(VM_Address ref, VM_Address sp,
				    boolean small)
    throws VM_PragmaInline {
    changeBit(ref, sp, small, false, true, false);
  }
  public static void setMarkBit(VM_Address ref, VM_Address sp, boolean small)
    throws VM_PragmaInline {
    changeBit(ref, sp, small, true, false, true);
  }
  public static boolean getInUseBit(VM_Address ref, VM_Address sp,
				    boolean small)
    throws VM_PragmaInline {
    return getBit(ref, sp, small, true);
  }
  private static boolean getMarkBit(VM_Address ref, VM_Address sp, 
				    boolean small)
    throws VM_PragmaInline {
    return getBit(ref, sp, small, false);
  }
  private static void changeBit(VM_Address ref, VM_Address sp, boolean small,
				boolean set, boolean inuse, boolean sync)
    throws VM_PragmaInline {
    int index = getCellIndex(ref, sp, small);
    VM_Word mask = getBitMask(index);
    VM_Address addr = getBitMapWord(index, sp, inuse, small);
    if (sync)
      syncSetBit(addr, mask, set);
    else
      unsyncSetBit(addr, mask, set);
  }
  private static boolean getBit(VM_Address ref, VM_Address sp, boolean small,
				boolean inuse)
    throws VM_PragmaInline {
    int index = getCellIndex(ref, sp, small);
    VM_Word mask = getBitMask(index);
    VM_Address addr = getBitMapWord(index, sp, inuse, small);
    VM_Word value = VM_Word.fromInt(VM_Magic.getMemoryWord(addr));
    return mask.EQ(value.and(mask));
  }
  private static int getCellIndex(VM_Address ref, VM_Address sp, boolean small)
    throws VM_PragmaInline {
    int cellSize = MarkSweepAllocator.getCellSize(sp);
    if (small) {
      sp = sp.add(SMALL_OBJ_BASE);
    } else {
      sp = sp.add(MID_OBJ_BASE);
    }
    return ref.diff(sp).toInt()/cellSize;
  }
  private static VM_Word getBitMask(int index)
    throws VM_PragmaInline {
    int bitnumber = index & (WORD_BITS - 1);
    if (VM.VerifyAssertions)
      VM._assert((bitnumber >= 0) && (bitnumber < WORD_BITS));
    return VM_Word.fromInt(1<<bitnumber);
  }
  private static VM_Address getBitMapWord(int index, VM_Address sp,
					  boolean inuse, boolean small)
    throws VM_PragmaInline {
    int offset = (index>>LOG_WORD_BITS)<<(LOG_WORD_SIZE + 1);
    if (inuse)
      offset += INUSE_BITMAP_OFFSET;
    else
      offset += MARK_BITMAP_OFFSET;
    if (VM.VerifyAssertions)
      VM._assert((small && (offset < SMALL_BITMAP_SIZE))
		 || (!small && (offset < MID_BITMAP_SIZE)));
    return sp.add(BITMAP_BASE + offset);
  }
  private static void unsyncSetBit(VM_Address bitMapWord, VM_Word mask, 
				   boolean set) 
    throws VM_PragmaInline {
    VM_Word wd = VM_Word.fromInt(VM_Magic.getMemoryWord(bitMapWord));
    if (set)
      wd = wd.or(mask);
    else
      wd = wd.and(mask.not());

    VM_Magic.setMemoryWord(bitMapWord, wd.toInt());
  }
  private static void syncSetBit(VM_Address bitMapWord, VM_Word mask, 
				 boolean set) 
    throws VM_PragmaInline {
    Object tgt = VM_Magic.addressAsObject(bitMapWord);
    VM_Word oldValue, newValue;
    do {
      oldValue = VM_Word.fromInt(VM_Magic.prepare(tgt, 0));
      newValue = (set) ? oldValue.or(mask) : oldValue.and(mask.not());
    } while(!VM_Magic.attempt(tgt, 0, oldValue.toInt(), newValue.toInt()));
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Miscellaneous
  //
  
  /**
   * Return the VMResource associated with this collector
   *
   * @return the VMResource associated with this collector
   */
  public final FreeListVMResource getVMResource() 
    throws VM_PragmaInline {
    return vmResource;
  }

  /**
   * Return the memory resource associated with this collector
   *
   * @return The memory resource associated with this collector
   */
  public final MemoryResource getMemoryResource() 
    throws VM_PragmaInline {
    return memoryResource;
  }



}
