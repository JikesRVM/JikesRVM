/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Offset;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;

/**
 * This class extends the BaseFreeList class to implement mark-sweep
 * functionality such as mark and inuse bitmaps.  Each instance of
 * this class is intended to provide fast, unsynchronized access to a
 * free list.  Therefore instances must not be shared across truely
 * concurrent threads (CPUs).  Rather, one or more instances of this
 * class should be bound to each CPU.  The shared VMResource used by
 * each instance is the point of global synchronization, and
 * synchronization only occurs at the granularity of aquiring (and
 * releasing) chunks of memory from the VMResource.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
final class MarkSweepAllocator extends BaseFreeList 
  implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  ////////////////////////////////////////////////////////////////////////////
  //
  // Class variables
  //
  private static int cellSize[];
  private static int sizeClassPages[];

  private static final boolean PARANOID = false;
  public static final int MAX_SMALL_SIZE = 512;  // statically verified below..

  ////////////////////////////////////////////////////////////////////////////
  //
  // Instance variables
  //
  private MarkSweepCollector collector;
  private Lock treadmillLock;
  public VM_Address treadmillFromHead;
  public VM_Address treadmillToHead;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Initialization
  //

  /**
   * Constructor
   *
   * @param collector_ The mark-sweep collector to which this
   * allocator instances is bound.  The collector's VMResource and
   * MemoryResource are used to initialize the superclass.
   */
  MarkSweepAllocator(MarkSweepCollector collector_) {
    super(collector_.getVMResource(), collector_.getMemoryResource());
    collector = collector_;
    treadmillLock = new Lock("MarkSweepAllocator.treadmillLock");
  }

  /**
   * The following statically initializes the cellSize and
   * sizeClassPages arrays.
   */
  static {
    cellSize = new int[SIZE_CLASSES];
    sizeClassPages = new int[SIZE_CLASSES];
    for(int sc = 1; sc < SIZE_CLASSES; sc++) {
      int size = getBaseCellSize(sc);
      if (isSmall(sc)) {
	cellSize[sc] = size;
	sizeClassPages[sc] = 1;
      } else {
	cellSize[sc] = size + NON_SMALL_OBJ_HEADER_SIZE;
	sizeClassPages[sc] = optimalPagesForSuperPage(sc, cellSize[sc],
						      BASE_SP_HEADER_SIZE);
	int cells = (sizeClassPages[sc]/cellSize[sc]);
	if (VM.VerifyAssertions) VM._assert(cells <= MarkSweepCollector.MAX_MID_OBJECTS);
      }
      if (sc == MAX_SMALL_SIZE_CLASS)
	if (VM.VerifyAssertions) VM._assert(size == MAX_SMALL_SIZE);
    }
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Allocation
  //

  /**
   * Initialize a new cell and return the address of the first useable
   * word.  This is called only when the cell is first created, not
   * each time it is reused via a call to alloc.<p>
   *
   * In this system, small cells require no header, but all other
   * cells require a single word that points to the first word of the
   * superpage.
   *
   * @param cell The address of the first word of the allocated cell.
   * @param sp The address of the first word of the superpage
   * containing the cell.
   * @param small True if the cell is a small cell (single page
   * superpage).
   * @return The address of the first useable word.
   */
  protected final VM_Address initializeCell(VM_Address cell, VM_Address sp,
					    boolean small, boolean large)
    throws VM_PragmaInline {
    if (!small) {
      VM_Magic.setMemoryAddress(cell, sp);
      return cell.add(NON_SMALL_OBJ_HEADER_SIZE);
    } else 
      return cell;
  }

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
   * @param copy True if this allocation is for a copy rather than a
   * fresh allocation.
   */
  protected final void postAlloc(VM_Address cell, boolean isScalar,
				 EXTENT bytes, boolean small, boolean large,
				 boolean copy) 
    throws VM_PragmaInline {
    collector.postAlloc(cell, isScalar, bytes, small, large, copy, this);
  };

  ////////////////////////////////////////////////////////////////////////////
  //
  // Collection
  //

  /**
   * Prepare for a collection.  Clear the treadmill to-space head and
   * prepare the collector.  If paranoid, perform a sanity check.
   *
   * @param vm Unused
   * @param mr Unused
   */
  public final void prepare() {
    if (PARANOID)
      sanity();
    treadmillToHead = VM_Address.zero();
  }

  /**
   * Finish up after a collection.
   *
   * @param vm Unused
   * @param mr Unused
   */
  public void release() {
    // sweep the small objects
    sweepSuperPages();
    // sweep the large objects
    sweepLargePages();
  }

  /**
   * Sweep through the large pages, releasing all superpages on the
   * "from space" treadmill.
   */
  public final void sweepLargePages() {
    VM_Address cell = treadmillFromHead;
    while (!cell.isZero()) {
      VM_Address next = MarkSweepCollector.getNextTreadmill(cell);
      VM_Address sp =  getSuperPage(cell, false);
      freeSuperPage(sp, LARGE_SIZE_CLASS, true);
      cell = next;
    }
    treadmillFromHead = treadmillToHead;
    treadmillToHead = VM_Address.zero();
  }

  /**
   * Sweep all of the non-large superpages for free objects.  Performa
   * a sanity check if paranoid.
   */
  public final void sweepSuperPages() {
    for (int sizeClass = 1; sizeClass < SIZE_CLASSES; sizeClass++) {
      sweepSuperPages(VM_Address.fromInt(superPageFreeList[sizeClass]), sizeClass, true);
      sweepSuperPages(VM_Address.fromInt(superPageUsedList[sizeClass]), sizeClass, false);
    }
    if (PARANOID)
      sanity();
  }

  /**
   * Sweep a superpage list
   *
   * @param sp The head of the list
   * @param sizeClass The size class for all superpages in this list
   * @param free True if these superpages are on the free superpage
   * list, otherwise they are on the used superpage list.
   */
  private final void sweepSuperPages(VM_Address sp, int sizeClass,
				     boolean free)
    throws VM_PragmaInline {
    if (!sp.EQ(VM_Address.zero())) {
      int cellSize = cellSize(sizeClass);
      while (!sp.EQ(VM_Address.zero())) {
	VM_Address next = getNextSuperPage(sp);
	collector.sweepSuperPage(this, sp, sizeClass, cellSize, free);
	sp = next;
      }
    }
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Treadmill
  //

  /**
   * Set the head of the from-space threadmill
   *
   * @param cell The new head of the from-space treadmill
   */
  public final void setTreadmillFromHead(VM_Address cell)
    throws VM_PragmaInline {
    treadmillFromHead = cell;
  }

  /**
   * Get the head of the from-space treadmill
   *
   * @return The head of the from-space treadmill
   */
  public final VM_Address getTreadmillFromHead()
    throws VM_PragmaInline {
    return treadmillFromHead;
  }

  /**
   * Set the head of the to-space threadmill
   *
   * @param cell The new head of the to-space treadmill
   */
  public final void setTreadmillToHead(VM_Address cell)
    throws VM_PragmaInline {
    treadmillToHead = cell;
  }

  /**
   * Get the head of the to-space treadmill
   *
   * @return The head of the to-space treadmill
   */
  public final VM_Address getTreadmillToHead()
    throws VM_PragmaInline {
    return treadmillToHead;
  }

  /**
   * Lock the treadmills
   */
  public final void lockTreadmill()
    throws VM_PragmaInline {
    treadmillLock.acquire();
  }

  /**
   * Unlock the treadmills
   */
  public final void unlockTreadmill()
    throws VM_PragmaInline {
    treadmillLock.release();
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Miscellaneous size-related methods
  //

  /**
   * Return the size of a cell for a given class size, *including* any
   * per-cell header space.
   *
   * @param sizeClass The size class in question
   * @return The size of a cell for a given class size, *including*
   * any per-cell header space
   */
  public static int getCellSize(VM_Address sp) {
    return getCellSize(getSizeClass(sp));
  }
  private static int getCellSize(int sizeClass) {
    if (VM.VerifyAssertions) 
      VM._assert(!isLarge(sizeClass));

    return cellSize[sizeClass];
  }
  protected final int cellSize(int sizeClass) 
    throws VM_PragmaInline {
    return getCellSize(sizeClass);
  }

  /**
   * Return the number of pages used by a superpage of a given size
   * class.
   *
   * @param sizeClass The size class of the superpage
   * @return The number of pages used by a superpage of this sizeclass
   */
  protected final int pagesForClassSize(int sizeClass) 
    throws VM_PragmaInline {
    if (VM.VerifyAssertions) 
      VM._assert(!isLarge(sizeClass));

    return sizeClassPages[sizeClass];
  }

  /**
   * Return the size of the per-superpage header required by this
   * system.  In this case it is just the underlying superpage header
   * size.
   *
   * @param sizeClass The size class of the cells contained by this
   * superpage.
   * @return The size of the per-superpage header required by this
   * system.
   */
  protected final int superPageHeaderSize(int sizeClass)
    throws VM_PragmaInline {
    if (isLarge(sizeClass))
      return BASE_SP_HEADER_SIZE + MarkSweepCollector.TREADMILL_HEADER_SIZE;
    else if (isSmall(sizeClass))
      return BASE_SP_HEADER_SIZE + MarkSweepCollector.SMALL_BITMAP_SIZE;
    else 
      return BASE_SP_HEADER_SIZE + MarkSweepCollector.MID_BITMAP_SIZE;
  }

  /**
   * Return the size of the per-cell header for cells of a given class
   * size.
   *
   * @param sizeClass The size class in question.
   * @return The size of the per-cell header for cells of a given class
   * size.
   */
  protected final int cellHeaderSize(int sizeClass)
    throws VM_PragmaInline {
    return cellHeaderSize(isSmall(sizeClass));
  }

  /**
   * Return the size of the per-cell header for cells of a given class
   * size.
   *
   * @param isSmall True if the cell is a small cell
   * @return The size of the per-cell header for cells of a given class
   * size.
   */
  protected final int cellHeaderSize(boolean small)
    throws VM_PragmaInline {
    return small ? 0 : NON_SMALL_OBJ_HEADER_SIZE;
  }


  ////////////////////////////////////////////////////////////////////////////
  //
  // Sanity checks and debugging
  //
  
  /**
   * Do sanity check of all superpages
   */
  private final void sanity() {
    for (int sizeClass = 1; sizeClass < SIZE_CLASSES; sizeClass++) {
      sanity(VM_Address.fromInt(superPageFreeList[sizeClass]), sizeClass);
      sanity(VM_Address.fromInt(superPageUsedList[sizeClass]), sizeClass);
    }
  }

  /**
   * Peform a sanity check of all superpages in a given superpage list
   *
   * @param sp The head superpage of the list
   * @param sizeClass The sizeclass for this superpage list
   */
  private final void sanity(VM_Address sp, int sizeClass) {
    if (!sp.EQ(VM_Address.zero())) {
      int cellSize = cellSize(sizeClass);
      while (!sp.EQ(VM_Address.zero())) {
	superPageSanity(sp, sizeClass);
	sp = getNextSuperPage(sp);
      }
    }
  }

  /**
   * Check the sanity of a superpage.  Ensure that all metadata is
   * self-consistent.  Objects marked as free must be on the free
   * list, the number marked as free and alloced must sum to the
   * capacity of the superpage, and the number marked as inuse must
   * match the inuse count for the superpage.
   *
   * @param sp The superpage to be checked
   * @param sizeClass The sizeclass for this superpage
   */
  protected final void superPageSanity(VM_Address sp, int sizeClass) {
    if (isLarge(sizeClass)) {
    } else {
      boolean small = isSmall(sizeClass);
      VM_Address sentinal;
      sentinal = sp.add(pagesForClassSize(sizeClass)<<LOG_PAGE_SIZE);
      int cellSize = cellSize(sizeClass);
      VM_Address cursor = sp.add(superPageHeaderSize(sizeClass));
      int inUse = 0;
 //       VM.sysWrite(sp); VM.sysWrite(" "); VM.sysWrite(sizeClass); VM.sysWrite("\n--------------\n");
      while (cursor.add(cellSize).LE(sentinal)) {
	VM_Address cell = cursor;
	boolean free = isFree(cell.add(cellHeaderSize(small)), sp, sizeClass);
	if (MarkSweepCollector.getInUseBit(cell, sp, small)) {
	  if (VM.VerifyAssertions) VM._assert(!free);
	  inUse++;
	} else
	  if (VM.VerifyAssertions) VM._assert(free);
	cursor = cursor.add(cellSize);
      }
//       VM.sysWrite(sp); VM.sysWrite(" "); VM.sysWrite(sizeClass); VM.sysWrite("\n--------------\n\n");
      if (VM.VerifyAssertions) VM._assert(inUse == getInUse(sp));
    }
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // The following methods, declared as abstract in the superclass, do
  // nothing in this implementation, so they have empty bodies.
  //
  protected final void postFreeCell(VM_Address cell, VM_Address sp, 
				    int szClass) {};
  protected final void postExpandSizeClass(VM_Address sp, int sizeClass) {};
}
