/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Memory;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;

/**
 * This abstract class implements core functionality for a generic
 * free list allocator.  Each instance of this class is intended to
 * provide fast, unsynchronized access to a free list.  Therefore
 * instances must not be shared across truely concurrent threads
 * (CPUs).  Rather, one or more instances of this class should be
 * bound to each CPU.  The shared VMResource used by each instance is
 * the point of global synchronization, and synchronization only
 * occurs at the granularity of aquiring (and releasing) chunks of
 * memory from the VMResource.  Subclasses may require finer grained
 * synchronization during a marking phase, for example.<p>
 *
 * This is a first cut implementation, with plenty of room for
 * improvement...
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
abstract class BaseFreeList implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 
  
  ////////////////////////////////////////////////////////////////////////////
  //
  // Class variables
  //
  private static final int        BASE_SIZE_CLASSES = 52;
  protected static final int           SIZE_CLASSES = BASE_SIZE_CLASSES;
  protected static final int       LARGE_SIZE_CLASS = 0;
  protected static final int   MAX_SMALL_SIZE_CLASS = 39;

  public static final int NON_SMALL_OBJ_HEADER_SIZE = WORD_SIZE;

  private static final int           PREV_SP_OFFSET = 0 * WORD_SIZE;
  private static final int           NEXT_SP_OFFSET = 1 * WORD_SIZE;
  private static final int       SP_FREELIST_OFFSET = 2 * WORD_SIZE;
  private static final int       IN_USE_WORD_OFFSET = PREV_SP_OFFSET;
  private static final int   SIZE_CLASS_WORD_OFFSET = NEXT_SP_OFFSET;
  protected static final int    BASE_SP_HEADER_SIZE = 3 * WORD_SIZE;

  protected static final VM_Word          PAGE_MASK = VM_Word.fromInt(~(PAGE_SIZE - 1));
  private static final float    MID_SIZE_FIT_TARGET = (float) 0.85;// 15% wastage

  ////////////////////////////////////////////////////////////////////////////
  //
  // Instance variables
  //
  protected FreeListVMResource vmResource;
  protected MemoryResource memoryResource;
  protected int[] superPageFreeList;  // should be VM_Address [] 
  protected int[] superPageUsedList;  // should be VM_Address []

  ////////////////////////////////////////////////////////////////////////////
  //
  // Initialization
  //

  /**
   * Constructor
   *
   * @param vmr The virtual memory resource from which this free list
   * allocator will acquire virtual memory.
   * @param mr The memory resource against which memory consumption
   * for this free list allocator will be accounted.
   */
  BaseFreeList(FreeListVMResource vmr, MemoryResource mr) {
    vmResource = vmr;
    memoryResource = mr;
    superPageFreeList = new int[SIZE_CLASSES];
    superPageUsedList = new int[SIZE_CLASSES];
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Allocation
  //

  /**
   * Allocate space for a new object
   *
   * @param isScalar Is the object to be allocated a scalar (or array)?
   * @param bytes The number of bytes allocated
   * @return The address of the first byte of the allocated cell
   */
  public final VM_Address alloc(boolean isScalar, EXTENT bytes) 
    throws VM_PragmaInline {
    return alloc(isScalar, bytes, false);
  }

  /**
   * Allocate space for an object
   *
   * @param isScalar Is the object to be allocated a scalar (or array)?
   * @param bytes The number of bytes allocated
   * @param copy Is this object being copied (or is it a regular allocation?)
   * @return The address of the first byte of the allocated cell Will
   * not return zero.
   */
  private final VM_Address alloc(boolean isScalar, EXTENT bytes, boolean copy) 
    throws VM_PragmaInline {
    int sizeClass = getSizeClass(isScalar, bytes);
    if (isSmall(sizeClass)) {
      VM_Address cell = allocCell(isScalar, sizeClass);
      if (!cell.isZero()) {
	postAlloc(cell, isScalar, bytes, true, false, copy);
	Memory.zeroSmall(cell, bytes);
	return cell;
      }
    }
    return allocSlow(isScalar, bytes, copy);
  }

  /**
   * Allocate space for an object.  This method is used to get
   * uncommon cases out of line.  These are cases where the "fast
   * path" fails or is inadequate: where a small object allocation
   * fails due to a vailure of <code>expandSizeClass()</code>, or in
   * the case of any non-small allocation.
   *
   * @param isScalar Is the object to be allocated a scalar (or array)?
   * @param bytes The number of bytes allocated
   * @param copy Is this object being copied (or is it a regular allocation?)
   * @return The address of the first byte of the allocated cell Will
   * not return zero.
   */
  private final VM_Address allocSlow(boolean isScalar, EXTENT bytes,
				     boolean copy) 
    throws VM_PragmaNoInline {
    int sizeClass = getSizeClass(isScalar, bytes);
    boolean large = isLarge(sizeClass);
    boolean small = isSmall(sizeClass);
    VM_Address cell;
    for (int count = 0; ; count++) {
      cell = large ? allocLarge(isScalar, bytes) : allocCell(isScalar, sizeClass);
      if (!cell.isZero()) break;
      VM_Interface.getPlan().poll(true, memoryResource);
      if (count > 2) VM.sysFail("Out of memory in BaseFreeList.alloc");
    }
    postAlloc(cell, isScalar, bytes, small, large, copy);
    Memory.zero(cell, bytes);
    return cell;
  }

  /**
   * Allocate space for a copied object
   *
   * @param isScalar Is the object to be allocated a scalar (or array)?
   * @param bytes The number of bytes allocated
   * @return The address of the first byte of the allocated cell
   */
  public final VM_Address allocCopy(boolean isScalar, EXTENT bytes) 
    throws VM_PragmaInline {
    return alloc(isScalar, bytes, true);
  }

  abstract protected void postAlloc(VM_Address cell, boolean isScalar,
				    EXTENT bytes, boolean small,
				    boolean large, boolean copy);
    
  /**
   * Allocate a cell. Cells are maintained on free lists (as opposed
   * to large objects, which are directly allocated and freed in
   * page-grain units via the vm resource).  This routine does not
   * guarantee that the cell will be zeroed.  The caller of this
   * method must explicitly zero the cell.
   *
   * @param isScalar True if the object to occupy this cell will be a scalar
   * @param sizeClass The size class of the cell.
   * @return The address of the start of a newly allocted cell of
   * size at least sufficient to accommodate <code>sizeClass</code>.
   * May return zero if unable to acquire cell.
   */
  private final VM_Address allocCell(boolean isScalar, int sizeClass) 
    throws VM_PragmaInline {
    if (VM.VerifyAssertions) VM._assert(!isLarge(sizeClass));

    // grab a superpage from the superpage freelist
    VM_Address headSP = VM_Address.fromInt(superPageFreeList[sizeClass]);
    if (headSP.isZero())
      return slowUnlinkCell(sizeClass);
    else 
      return unlinkCell(headSP, sizeClass);
  }

  /**
   * Take the first cell off a superpage's free list
   *
   * @param sp The superpage
   * @param sizeClass The size class of the cell requested (should be
   * the size class of this superpage).
   * @return The address of the start of the first cell in the
   * superpage's free list.
   */
  private final VM_Address unlinkCell(VM_Address sp, int sizeClass) 
    throws VM_PragmaInline {
    // take off free list
    VM_Address cell = getSuperPageFreeList(sp);
    if (VM.VerifyAssertions) VM._assert(sp.EQ(getSuperPage(cell, isSmall(sizeClass))));
    VM_Address next = getNextCell(cell);
    setSuperPageFreeList(sp, next);
    incInUse(sp);
    if (next.isZero())
      unlinkFullSuperPage(sp, sizeClass);
    return cell;
  }

  /**
   * Allocate a new superpage (if possible), and take the first cell
   * off the newly allocated superpage's free list
   *
   * @param sizeClass The size class of the cell requested
   * @return The address of the start of the first cell in the newly
   * allocated superpage's free list, or zero if no new superpage
   * could be allocated
   */
  private final VM_Address slowUnlinkCell(int sizeClass)
    throws VM_PragmaNoInline {
    if (expandSizeClass(sizeClass))
      return unlinkCell(VM_Address.fromInt(superPageFreeList[sizeClass]),
			sizeClass);
    else
      return VM_Address.zero();
  }

  /**
   * Allocate a large object.  Large objects are directly allocted and
   * freed in page-grained units via the vm resource.  This routine
   * does not guarantee that the space will have been zeroed.  The
   * caller must explicitly zero the space.
   *
   * @param isScalar True if the object to occupy this space will be a scalar.
   * @param bytes The required size of this space in bytes.
   * @return The address of the start of the newly allocated region at
   * least <code>bytes</code> bytes in size.
   */
  private final VM_Address allocLarge(boolean isScalar, EXTENT bytes) {
    bytes += superPageHeaderSize(LARGE_SIZE_CLASS) + cellHeaderSize(false);
    int pages = (bytes + PAGE_SIZE - 1)>>LOG_PAGE_SIZE;
    VM_Address sp = allocSuperPage(pages, LARGE_SIZE_CLASS);
    if (sp.isZero()) return sp;
    linkToSuperPageList(sp, LARGE_SIZE_CLASS, true);
    setSizeClass(sp, LARGE_SIZE_CLASS);
    return initializeCell(sp.add(superPageHeaderSize(LARGE_SIZE_CLASS)), sp, 
			  false, true);
  }
  abstract protected VM_Address initializeCell(VM_Address cell, VM_Address sp,
					       boolean small, boolean large);

  /**
   * Set the next free list entry field for a cell
   *
   * @param cell The cell whose next field is to be set
   * @param next The value to be written into the cell's next field
   * @param sizeClass The size class for the cell
   */
  protected void setNextCell(VM_Address cell, VM_Address next, int sizeClass)
    throws VM_PragmaInline {
    if (VM.VerifyAssertions)
      VM._assert(next.isZero() || getSuperPage(cell, isSmall(sizeClass)).EQ(getSuperPage(next, isSmall(sizeClass))));
    VM_Magic.setMemoryAddress(cell, next);
  }

  /**
   * Get the next free list entry field for a cell
   *
   * @param cell The cell whose next free list entry is to be retrieved
   * @return The next free list entry field for this cell
   */
  protected VM_Address getNextCell(VM_Address cell)
    throws VM_PragmaInline {
    return VM_Magic.getMemoryAddress(cell);
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Freeing
  //

  /**
   * Free a cell.  If the cell is large (own superpage) then release
   * the superpage, if not add to the super page's free list and if
   * all cells on the superpage are free, then release the
   * superpage.
   *
   * @param cell The address of the first byte of the cell to be freed
   * @param sp The superpage containing the cell
   * @param sizeClass The sizeclass of the cell.
   */
  protected final void free(VM_Address cell, VM_Address sp, int sizeClass)
    throws VM_PragmaInline {

    if (isLarge(sizeClass))
      freeSuperPage(sp, sizeClass, true);
    else {
      addToFreeList(cell, sp, sizeClass);
      postFreeCell(cell, sp, sizeClass);
      if (decInUse(sp) == 0)
	freeSuperPage(sp, sizeClass, true);
    }
  }
  abstract protected void postFreeCell(VM_Address cell, VM_Address sp,
				       int sizeClass);

  /**
   * Add a cell to a given superpage's free list
   *
   * @param cell The cell to be added to the free list
   * @param sp The superpage to which the cell belongs, the owner of
   * the free list to which the cell must be added.
   * @param sizeClass The sizeClass of the cell
   */
  private final void addToFreeList(VM_Address cell, VM_Address sp, 
				   int sizeClass)
    throws VM_PragmaInline {
    // add it to the front of the this superpage's free list
    VM_Address next = getSuperPageFreeList(sp);
    setNextCell(cell, next, sizeClass);
    setSuperPageFreeList(sp, cell);
    if (next.isZero()) {
      // must be on used list, so move it across to free list
      if (VM.VerifyAssertions)
	VM._assert(isOnSuperPageList(sp, sizeClass, false));
      unlinkFromSuperPageList(sp, sizeClass, false);
      linkToSuperPageList(sp, sizeClass, true);
    }
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Size classes
  //

  /**
   * Get the size class for a given number of bytes.<p>
   *
   * We use size classes based on a worst case internal fragmentation
   * loss target of 1/8.  In fact, across sizes from 8 bytes to 512
   * the average worst case loss is 13.3%, giving an expected loss
   * (assuming uniform distribution) of about 7%.  We avoid using the
   * Lea class sizes because they were so numerous and therefore
   * liable to lead to excessive inter-class-size fragmentation.<p>
   * 
   * This method may segregate arrays and scalars (currently it does
   * not).
   *
   * @param isScalar True if the object to occupy the allocated space
   * will be a scalar (i.e. not a array).
   * @param bytes The number of bytes required to accommodate the
   * object to be allocated.
   * @return The size class capable of accomodating the allocation
   * request.  If the request is sufficiently large then
   * <code>LARGE_SIZE_CLASS</code> will be returned, indicating that
   * the request will not be satisfied by the freelist, but must be
   * dealt with explicitly as a large object.
   */
  protected static final int getSizeClass(boolean isScalar, EXTENT bytes)
    throws VM_PragmaInline {
    if (VM.VerifyAssertions) VM._assert(bytes >= 0);

    int sz1 = bytes - 1;
    int offset = 0;  // = isScalar ? BASE_SIZE_CLASSES : 0;
    return ((sz1 <=   63) ? offset +      (sz1 >>  2): //    4 bytes apart
	    (sz1 <=  127) ? offset + 8  + (sz1 >>  3): //    8 bytes apart
	    (sz1 <=  255) ? offset + 16 + (sz1 >>  4): //   16 bytes apart
	    (sz1 <=  511) ? offset + 24 + (sz1 >>  5): //   32 bytes apart
	    (sz1 <= 2047) ? offset + 38 + (sz1 >>  8): //  256 bytes apart
	    (sz1 <= 8192) ? offset + 44 + (sz1 >> 10): // 1024 bytes apart
	    LARGE_SIZE_CLASS);           // large object, not on free list
  }

  /**
   * Return the size of a basic cell (i.e. not including any cell
   * header) for a given size class.
   *
   * @param sc The size class in question
   * @return The size of a basic cell (i.e. not including any cell
   * header).
   */
  protected static final int getBaseCellSize(int sc) 
    throws VM_PragmaInline {
    if (VM.VerifyAssertions) 
      VM._assert(sc != LARGE_SIZE_CLASS && sc < BASE_SIZE_CLASSES);

    return ((sc < 16) ? (sc +  1) <<  2:
	    (sc < 24) ? (sc -  7) <<  3:
	    (sc < 32) ? (sc - 15) <<  4:
	    (sc < 40) ? (sc - 23) <<  5:
	    (sc < 46) ? (sc - 37) <<  8:
	                (sc - 43) << 10);
  }

  /**
   * Return true if the size class is for small objects
   *
   * @param sizeClass A size class
   * @return True if the size class is for small objects
   */
  public static boolean isSmall(int sizeClass) {
    return (sizeClass != LARGE_SIZE_CLASS) 
      && (sizeClass <= MAX_SMALL_SIZE_CLASS);
  }

  /**
   * Return true if the size class is for large objects
   *
   * @param sizeClass A size class
   * @return True if the size class is for large objects
   */
  public static boolean isLarge(int sizeClass) {
    return sizeClass == LARGE_SIZE_CLASS;
  }

  /**
   * Return the number of pages that should be used for a superpage of
   * a given class size, given some fit target,
   * <code>MID_SIZE_FIT_TARGET</code>.
   *
   * @param sizeClass The size class which will occupy the superpage
   * @param cellSize The space occupied by each cell (inclusive of
   * headers etc).
   * @param spHeaderSize The superpage header size.
   * @return The number of pages that should be used for a superpage
   * for this size class given the fit criterion
   * <code>MID_SIZE_FIT_TARGET</code>.
   */
  protected static final int optimalPagesForSuperPage(int sizeClass,
						      int cellSize,
						      int spHeaderSize) 
    throws VM_PragmaInline {
    if (VM.VerifyAssertions) 
      VM._assert(sizeClass > MAX_SMALL_SIZE_CLASS && sizeClass < BASE_SIZE_CLASSES);

    int size = spHeaderSize;
    int pages = 1;
    float fit = 0;
    int cells = 0;
    while (fit < MID_SIZE_FIT_TARGET) {
      cells++;
      size += cellSize;
      pages = (size + PAGE_SIZE - 1)>>LOG_PAGE_SIZE;
      fit = (float) size/(float) (pages<<LOG_PAGE_SIZE);
    }
    return pages;
  }

  /**
   * Repopulate the freelist with cells of size class
   * <code>sizeClass</code>.  This is done by allocating more global
   * memory from the vm resource, breaking that memory up into cells
   * and placing those cells on their super page's free list, and
   * placing the superpage at the head of the otherwise empty free
   * superpage list for that sizeclass.
   *
   * @param sizeClass The size class that needs to be repopulated.
   * @return Whether it was able to acquire underlying memory to expand.
   */
  private final boolean expandSizeClass(int sizeClass) {
    // grab superpage
    int pages = pagesForClassSize(sizeClass);
    VM_Address sp = allocSuperPage(pages, sizeClass);
    if (sp.isZero()) return false;
    int subclassHeader = superPageHeaderSize(sizeClass)-BASE_SP_HEADER_SIZE;
    if (subclassHeader != 0)
      VM_Memory.zero(sp.add(BASE_SP_HEADER_SIZE), subclassHeader);
    int spSize = pages<<LOG_PAGE_SIZE;
    
    // set up sizeclass info
    setSizeClass(sp, sizeClass);
    setInUse(sp, 0);

    // bust up and add to free list
    int cellExtent = cellSize(sizeClass);
    VM_Address sentinal = sp.add(spSize);
    VM_Address cursor = sp.add(superPageHeaderSize(sizeClass));
    VM_Address lastCell = VM_Address.zero();
    while (cursor.add(cellExtent).LE(sentinal)) {
      VM_Address cell = initializeCell(cursor, sp, isSmall(sizeClass), false);
      setNextCell(cell, lastCell, sizeClass);
      lastCell = cell;
      cursor = cursor.add(cellExtent);
    }
    setSuperPageFreeList(sp, lastCell);
				   
    // link superpage
    linkToSuperPageList(sp, sizeClass, true);

    // do other sub-class specific stuff
    postExpandSizeClass(sp, sizeClass);
    if (VM.VerifyAssertions)
      VM._assert(!lastCell.isZero());
    return true;
  }
  private final int cellsInSuperPage(VM_Address sp) {
    int spBytes = vmResource.getSize(sp)<<LOG_PAGE_SIZE;
    spBytes -= superPageHeaderSize(getSizeClass(sp));
    return spBytes/cellSize(getSizeClass(sp));
  }
  abstract protected void postExpandSizeClass(VM_Address sp, int sizeClass);
  abstract protected int pagesForClassSize(int sizeClass);
  abstract protected int superPageHeaderSize(int sizeClass);
  abstract protected int cellSize(int sizeClass);
  abstract protected int cellHeaderSize(int sizeClass);
  abstract protected int cellHeaderSize(boolean isSmall);

  ////////////////////////////////////////////////////////////////////////////
  //
  // Superpages
  //

  // The following illustrates the structure of small, medium and
  // large superpages.  Small superpages are a single page in size,
  // therefore the start of the superpage can be trivially established
  // by masking the low order bits of the cell address.  Medium and
  // large superpages use the first word of each cell to point to the
  // start of the superpage.  Large pages are a trivial case of medium
  // page (where there is only one cell).
  //
  // key:      prev    previous superpage in linked list
  //           next    next superpage in linked list
  //           IU      "in-use" counter (low order bits)
  //           SC      "size class"  (low order bits)
  //           freelst The free list for this superpage
  //           ssss    *optional* per-superblock sub-class-specific metadata
  //           cccc    *optional* per-cell sub-class-specific metadata
  //           uuuu    user-usable space
  //
  //
  //          small              medium               large
  //        +-------+           +-------+           +-------+
  //        |prev|IU|        +->|prev|IU|        +->|prev|IU|
  //        +-------+        |  +-------+        |  +-------+
  //        |next|SC|        |  |next|SC|        |  |next|SC|
  //        +-------+        |  +-------+        |  +-------+
  //        |freelst|        |  |freelst|        |  |unused |
  //        +-------+        |  +-------+        |  +-------+
  //        |sssssss|        |  |sssssss|        |  |sssssss|
  //           ...           |     ...           |     ... 
  //        |sssssss|        |  |sssssss|        |  |sssssss|
  //        +-------+        |  +-------+        |  +-------+
  //        |ccccccc|        +--+---    |        +--+---    |
  //        |uuuuuuu|        |  +-------+           +-------+
  //        |uuuuuuu|        |  |ccccccc|           |ccccccc|
  //        +-------+        |  |uuuuuuu|           |uuuuuuu|
  //        |ccccccc|        |     ...              |uuuuuuu| 
  //        |uuuuuuu|        |  |uuuuuuu|           |uuuuuuu|
  //        |uuuuuuu|        |  +-------+           |uuuuuuu|
  //        +-------+        +--+---    |              ...   
  //        |ccccccc|           +-------+
  //        |uuuuuuu|           |ccccccc|
  //           ...              |uuuuuuu|
  //                               ...

  /**
   * Allocate a super page.
   *
   * @param pages The size of the superpage in pages.
   * @return The address of the first word of the superpage.  May return zero.
   */
  private final VM_Address allocSuperPage(int pages, int sizeClass) {
    return vmResource.acquire(pages, memoryResource);
  }

  /**
   * Return a superpage to the global page pool by freeing it with the
   * vm resource.  Before this is done the super page is unlinked from
   * the linked list of super pages for this free list
   * instance.
   *
   * @param sp The superpage to be freed.
   */
  protected final void freeSuperPage(VM_Address sp, int sizeClass, 
				     boolean free) {
    if (VM.VerifyAssertions)
      VM._assert(isOnSuperPageList(sp, sizeClass, free));
    // unlink superpage
    unlinkFromSuperPageList(sp, sizeClass, free);
    // free it
    vmResource.release(sp, memoryResource);
  }

  /**
   * Return the superpage for a given cell.  If the cell is a small
   * cell then this is found by masking the cell address to find the
   * containing page.  Otherwise the first word of the cell contains
   * the address of the page.
   *
   * @param cell The address of the first word of the cell (exclusive
   * of any sub-class specific metadata).
   * @param small True if the cell is a small cell (single page superpage).
   * @return The address of the first word of the superpage containing
   * <code>cell</code>.
   */
  public static final VM_Address getSuperPage(VM_Address cell, boolean small)
    throws VM_PragmaInline {
    VM_Address rtn;
    if (small)
      rtn = cell.toWord().and(PAGE_MASK).toAddress();
    else {
      rtn = VM_Magic.getMemoryAddress(cell.sub(WORD_SIZE));
      if (VM.VerifyAssertions)
	VM._assert(rtn.EQ(rtn.toWord().and(PAGE_MASK).toAddress()));
    }
    return rtn;
  }

  private final void unlinkFullSuperPage(VM_Address sp, int sizeClass)
    throws VM_PragmaNoInline {
    // this super page is now fully used.  Move to used list
    unlinkFromSuperPageList(sp, sizeClass, true);
    linkToSuperPageList(sp, sizeClass, false);
  }

  /**
   * Link a superpage into a superpage list for a given size class
   *
   * @param sp The superpage to be linked in
   * @param sizeClass The size class for this superpage
   * @param free True if the superpage should be added to the free
   * superpage list, otherwise it will be added to the used superpage
   * list.
   */
  private final void linkToSuperPageList(VM_Address sp, int sizeClass,
					 boolean free) 
    throws VM_PragmaInline {
    if (VM.VerifyAssertions) {
      VM._assert(isLarge(sizeClass) ||
		 (free && !getSuperPageFreeList(sp).isZero()) ||
		 (!free && getSuperPageFreeList(sp).isZero()));
    }
    VM_Address next = VM_Address.fromInt(free ? superPageFreeList[sizeClass] : superPageUsedList[sizeClass]);
    setNextSuperPage(sp, next);
    setPrevSuperPage(sp, VM_Address.zero());
    if (!next.isZero()) {
      setPrevSuperPage(next, sp);
    }
    // use magic to avoid aastores & therefore preserve interruptibility
    if (free)
      VM_Magic.setIntAtOffset(superPageFreeList, sizeClass<<LOG_WORD_SIZE, VM_Magic.objectAsAddress(sp).toInt());
    else
      VM_Magic.setIntAtOffset(superPageUsedList, sizeClass<<LOG_WORD_SIZE, VM_Magic.objectAsAddress(sp).toInt());
  }

  /**
   * Unlink a superpage from a superpage list for a given size class
   *
   * @param sp The superpage to be unlinked
   * @param sizeClass The size class for this superpage
   * @param free True if this superpage should be unlinked from the
   * free superpage list, otherwise it will be unlinked from the used
   * superpage list.
   */
  private final void unlinkFromSuperPageList(VM_Address sp, int sizeClass,
					     boolean free)
    throws VM_PragmaInline {
    VM_Address next = getNextSuperPage(sp);
    VM_Address prev = getPrevSuperPage(sp);

    if (VM.VerifyAssertions) {
      VM_Address head = VM_Address.fromInt(free ? superPageFreeList[sizeClass] : superPageUsedList[sizeClass]);
      VM._assert(!head.EQ(sp) || prev.isZero());
    }
    if (!prev.isZero())
      setNextSuperPage(prev, next);
    else {
      // use magic to avoid aastores & therefore preserve interruptibility
      if (free)
	VM_Magic.setIntAtOffset(superPageFreeList, sizeClass<<LOG_WORD_SIZE, VM_Magic.objectAsAddress(next).toInt());
      else
	VM_Magic.setIntAtOffset(superPageUsedList, sizeClass<<LOG_WORD_SIZE, VM_Magic.objectAsAddress(next).toInt());
    }
    if (!next.isZero())
      setPrevSuperPage(next, prev);
  }

  /**
   * Return the head of the free list for a superpage 
   *
   * @param sp The superpage
   * @return The head of the free list for the given superpage
   */
  protected static final VM_Address getSuperPageFreeList(VM_Address sp) 
    throws VM_PragmaInline {
    VM_Address rtn = VM_Magic.getMemoryAddress(sp.add(SP_FREELIST_OFFSET));
    if (VM.VerifyAssertions) {
      VM._assert(!isLarge(getSizeClass(sp)));
      VM._assert(rtn.isZero() || sp.EQ(getSuperPage(rtn, isSmall(getSizeClass(sp)))));
    }
    return rtn;
  }
  
  /**
   * Set the head of the free list for a superpage 
   *
   * @param sp The superpage
   * @param head The head of the freelist for the given superpage
   */
  private static final void setSuperPageFreeList(VM_Address sp, 
						 VM_Address head) 
    throws VM_PragmaInline {
    if (VM.VerifyAssertions) {
      VM._assert(!isLarge(getSizeClass(sp)));
      VM._assert(head.isZero() || sp.EQ(getSuperPage(head, isSmall(getSizeClass(sp)))));
    }
    VM_Magic.setMemoryAddress(sp.add(SP_FREELIST_OFFSET), head);
  }

  /**
   * Set the prev or next link fields of a superpage, taking care not
   * to overwrite the low-order bits where the "in-use" and "size
   * class" info are stored.
   *
   * @param sp The address of the first word of the super page
   * @param link The address of the super page this link is to point to
   * @param prev True if the "prev" field is to be set, false if the
   * "next" field is to be set.
   */
  private static final void setPrevSuperPage(VM_Address sp, VM_Address prev) 
    throws VM_PragmaInline {
    setSuperPageLink(sp, prev, true);
  }
  private static final void setNextSuperPage(VM_Address sp, VM_Address next) 
    throws VM_PragmaInline {
    setSuperPageLink(sp, next, false);
  }

  private static final void setSuperPageLink(VM_Address sp, VM_Address link, 
					     boolean prev) 
    throws VM_PragmaInline {

    if (VM.VerifyAssertions) {
      VM._assert(sp.toWord().EQ(sp.toWord().and(PAGE_MASK)));
      VM._assert(link.toWord().EQ(link.toWord().and(PAGE_MASK)));
    }
    VM_Address loc = sp.add(prev ? PREV_SP_OFFSET : NEXT_SP_OFFSET);
    VM_Word wd = VM_Word.fromInt(VM_Magic.getMemoryWord(loc));
    wd = wd.and(PAGE_MASK.not()).or(link.toWord());
    VM_Magic.setMemoryWord(loc, wd.toInt());
    // VM.sysWrite(prev ? "setting prev link of " : "setting next link of ", sp);
    // VM.sysWrite(" to ", link); VM.sysWriteln(" logically ", wd);
  }
  /**
   * Get the prev or next link fields of a superpage, taking care to
   * avoid the low-order bits where the "in-use" and "size class" info
   * are stored.
   *
   * @param sp The address of the first word of the super page
   * @param prev True if the "prev" field is to be set, false if the
   * "next" field is to be set.
   * @return The prev or next link for this superpage.
   */
  private static final VM_Address getPrevSuperPage(VM_Address sp) 
    throws VM_PragmaInline {
    return getSuperPageLink(sp, true);
  }
  protected static final VM_Address getNextSuperPage(VM_Address sp) 
    throws VM_PragmaInline {
    return getSuperPageLink(sp, false);
  }
  private static final VM_Address getSuperPageLink(VM_Address sp, boolean prev)
    throws VM_PragmaInline {
    if (VM.VerifyAssertions)
      VM._assert(sp.toWord().EQ(sp.toWord().and(PAGE_MASK)));

    VM_Address loc = sp.add(prev ? PREV_SP_OFFSET : NEXT_SP_OFFSET);
    VM_Word wd = VM_Word.fromInt(VM_Magic.getMemoryWord(loc));
    return wd.and(PAGE_MASK).toAddress();
  }

  /**
   * Set the size class for a superpage
   * 
   * @param sp The superpage
   * @param sizeClass The size class for this superpage
   */
  private static final void setSizeClass(VM_Address sp, int sizeClass)
    throws VM_PragmaInline {
    if (VM.VerifyAssertions)
      VM._assert(sp.toWord().EQ(sp.toWord().and(PAGE_MASK)));
    
    sp = sp.add(SIZE_CLASS_WORD_OFFSET);
    VM_Word wd = VM_Word.fromInt(VM_Magic.getMemoryWord(sp));
    wd = wd.and(PAGE_MASK).or(VM_Word.fromInt(sizeClass));
    VM_Magic.setMemoryWord(sp, wd.toInt());
  }

  /**
   * Get the size class for a superpage
   * 
   * @param sp The superpage
   * @return The size class for this superpage
   */
  protected static final int getSizeClass(VM_Address sp)
    throws VM_PragmaInline {
    if (VM.VerifyAssertions)
      VM._assert(sp.toWord().EQ(sp.toWord().and(PAGE_MASK)));
    
    sp = sp.add(SIZE_CLASS_WORD_OFFSET);
    VM_Word wd = VM_Word.fromInt(VM_Magic.getMemoryWord(sp));
    return wd.and(PAGE_MASK.not()).toInt();
  }

  /**
   * Set the inuse field for a superpage
   *
   * @param sp The superpage
   * @param value The value to which the inuse field should be set.
   */
  private static final void setInUse(VM_Address sp, int value)
    throws VM_PragmaInline {
    if (VM.VerifyAssertions)
      VM._assert(sp.toWord().EQ(sp.toWord().and(PAGE_MASK)));

    sp = sp.add(IN_USE_WORD_OFFSET);
    VM_Word wd = VM_Word.fromInt(VM_Magic.getMemoryWord(sp)).and(PAGE_MASK);;
    VM_Magic.setMemoryWord(sp, wd.toInt());
  }

  /**
   * Get the inuse field for a superpage
   *
   * @param sp The superpage
   * @return The inuse field of this superpage
   */
  protected static final int getInUse(VM_Address sp)
    throws VM_PragmaInline {
    if (VM.VerifyAssertions)
      VM._assert(sp.toWord().EQ(sp.toWord().and(PAGE_MASK)));

    sp = sp.add(IN_USE_WORD_OFFSET);
    VM_Word wd = VM_Word.fromInt(VM_Magic.getMemoryWord(sp)).and(PAGE_MASK.not());
    return wd.toInt();
  }

  /**
   * Change the inuse field for a superpage and return the new value
   *
   * @param sp The superpage whose inuse field is to change
   * @param delta The amount by which the inuse field should change
   * @return The new, updated, inuse value for this superpage
   */
  private static final void incInUse(VM_Address sp)
    throws VM_PragmaInline {
    if (VM.VerifyAssertions)
      changeInUse(sp, 1);
    else {
      VM_Address addr = sp.add(IN_USE_WORD_OFFSET);
      int old = VM_Magic.getMemoryWord(addr);
      VM_Magic.setMemoryWord(addr, old + 1);
    }
  }
  private static final int decInUse(VM_Address sp)
    throws VM_PragmaInline {
    return changeInUse(sp, -1);
  }
  private static final int changeInUse(VM_Address sp, int delta)
    throws VM_PragmaInline {
    if (VM.VerifyAssertions)
      VM._assert(sp.toWord().EQ(sp.toWord().and(PAGE_MASK)));
    VM_Address addr = sp.add(IN_USE_WORD_OFFSET);

    VM_Word oldref;
    if (VM.VerifyAssertions)
      oldref = VM_Magic.getMemoryAddress(addr).toWord().and(PAGE_MASK);
      
    VM_Word wd = VM_Word.fromInt(VM_Magic.getMemoryWord(addr)).add(VM_Word.fromInt(delta));
    int value = wd.and(PAGE_MASK.not()).toInt();
    if (VM.VerifyAssertions)
      VM._assert(value < PAGE_SIZE);
    VM_Magic.setMemoryWord(addr, wd.toInt());
    if (VM.VerifyAssertions)
      VM._assert(oldref.EQ(VM_Magic.getMemoryAddress(addr).toWord().and(PAGE_MASK)));

    return value;
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Sanity checks and debugging
  //
  abstract protected void superPageSanity(VM_Address sp, int sizeClass);

  protected final void dump() {
    for (int sizeClass = 0; sizeClass < SIZE_CLASSES; sizeClass++) {
      dump(VM_Address.fromInt(superPageFreeList[sizeClass]));
      dump(VM_Address.fromInt(superPageUsedList[sizeClass]));
    }
  }

  private final void dump(VM_Address sp) {
    int pages = 0, cells = 0, inUse = 0, inUseBytes = 0, free = 0, freeBytes = 0;
    VM.sysWrite("=============\n");
    while (!sp.EQ(VM_Address.zero())) {
      VM.sysWrite("super page: ");VM.sysWrite(sp);VM.sysWrite("\n"); 
      VM.sysWrite("  next: ");VM.sysWrite(getNextSuperPage(sp));VM.sysWrite("\n");
      VM.sysWrite("  prev: ");VM.sysWrite(getPrevSuperPage(sp));VM.sysWrite("\n");
      pages += vmResource.getSize(sp);
      VM.sysWrite("  pages: ");VM.sysWrite(vmResource.getSize(sp));VM.sysWrite("\n");
      VM.sysWrite("  size class: ");VM.sysWrite(getSizeClass(sp));VM.sysWrite("\n");
      if (!isLarge(getSizeClass(sp))) {
	VM.sysWrite("    size class bytes: ");VM.sysWrite(getBaseCellSize(getSizeClass(sp)));VM.sysWrite("\n");
	cells += cellsInSuperPage(sp);
	VM.sysWrite("    cells: ");VM.sysWrite(cellsInSuperPage(sp));VM.sysWrite("\n");
	inUse += getInUse(sp);
	inUseBytes += (getInUse(sp)*getBaseCellSize(getSizeClass(sp)));
	VM.sysWrite("    in use: ");VM.sysWrite(getInUse(sp));VM.sysWrite("\n");
	int f = countFree(sp);
	free += f;
	freeBytes += (f*getBaseCellSize(getSizeClass(sp)));
	VM.sysWrite("    free: ");VM.sysWrite(f);VM.sysWrite("\n");
	if (VM.VerifyAssertions)
	  VM._assert((getInUse(sp)+f) == cellsInSuperPage(sp));
      }
      //      superPageSanity(sp);
      sp = getNextSuperPage(sp);
    }
    VM.sysWrite("-------------\n");
    VM.sysWrite("pages: "); VM.sysWrite(pages); VM.sysWrite("\n");
    VM.sysWrite("cells: "); VM.sysWrite(cells); VM.sysWrite("\n");
    VM.sysWrite("inUse: "); VM.sysWrite(inUse); VM.sysWrite(" ("); VM.sysWrite(inUseBytes); VM.sysWrite(")\n");
    VM.sysWrite("free: "); VM.sysWrite(free); VM.sysWrite(" ("); VM.sysWrite(freeBytes); VM.sysWrite(")\n");
    VM.sysWrite("utilization: ");VM.sysWrite((float) inUseBytes/(float) (inUseBytes+freeBytes));VM.sysWrite("\n");
    VM.sysWrite("=============\n");
  }
  private final int countFree(VM_Address sp) {
    int sc = getSizeClass(sp);
    if (VM.VerifyAssertions)
      VM._assert(!isLarge(sc));
    VM_Address start = sp;
    VM_Address end = start.add(vmResource.getSize(sp)<<LOG_PAGE_SIZE);
    int free = 0;
    VM_Address f = getSuperPageFreeList(sp);
    while (!f.isZero()) {
      free++;
      f = getNextCell(f);
    }
    return free;
  }
  protected final boolean isFree(VM_Address cell, VM_Address sp,
				 int sizeClass) {
    VM_Address f = getSuperPageFreeList(sp);
    while (f.NE(VM_Address.zero()) && f.NE(cell)) {
      f = getNextCell(f);
    }
    return f.EQ(cell);
  }
  /**
   * Sanity check.  Return true if the given superpage is on a given
   * super page list.
   *
   * @param sp The super page
   * @param sizeClass The size class for this superpage
   * @param free True if the free superpage list should be checked
   * (otherwise the used superpage list will be checked).
   * @return True if the superpage is found on the specified list
   */
  private final boolean isOnSuperPageList(VM_Address sp, int sizeClass,
					  boolean free) {
    VM_Address next = VM_Address.fromInt(free ? superPageFreeList[sizeClass] : superPageUsedList[sizeClass]);
    while (!next.isZero()) {
      if (next.EQ(sp))
	return true;
      next = getNextSuperPage(next);
    }
    return false;
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Miscellaneous
  //
  public void show() {
  }
}

