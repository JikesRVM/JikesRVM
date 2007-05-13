/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.policy;

import org.mmtk.utility.alloc.BlockAllocator;
import org.mmtk.utility.alloc.SegregatedFreeList;
import org.mmtk.utility.Constants;
import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements unsynchronized (local) elements of an
 * explicity managed collector.  Allocation is via the segregated free list
 * (@see SegregatedFreeList).<p>
 *
 * @see SegregatedFreeList
 * @see ExplicitFreeListSpace
 */
@Uninterruptible public final class ExplicitFreeListLocal extends SegregatedFreeList
  implements Constants {

  /****************************************************************************
   * 
   * Class variables
   */
  
  public static final int META_DATA_PAGES_PER_REGION = SegregatedFreeList.META_DATA_PAGES_PER_REGION_WITH_BITMAP;


  /****************************************************************************
   * 
   * Instance variables
   */

  /****************************************************************************
   * 
   * Initialization
   */

  /**
   * Class initializer.  The primary task performed here is to
   * establish the block layout for each size class.
   */
  static {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(LAZY_SWEEP);
    
    cellSize = new int[SIZE_CLASSES];
    blockSizeClass = new byte[SIZE_CLASSES];
    cellsInBlock = new int[SIZE_CLASSES];
    blockHeaderSize = new int[SIZE_CLASSES];

    for (int sc = 0; sc < SIZE_CLASSES; sc++) {
      cellSize[sc] = getBaseCellSize(sc);
      for (byte blk = 0; blk < BlockAllocator.BLOCK_SIZE_CLASSES; blk++) {
        int usableBytes = BlockAllocator.blockSize(blk);
        int cells = usableBytes / cellSize[sc];
        blockSizeClass[sc] = blk;
        cellsInBlock[sc] = cells;
        /* cells must start at multiple of MIN_ALIGNMENT because
           cellSize is also supposed to be multiple, this should do
           the trick: */
        blockHeaderSize[sc] = BlockAllocator.blockSize(blk) - cells * cellSize[sc];
        if (((usableBytes < BYTES_IN_PAGE) && (cells*2 > MAX_CELLS)) ||
            ((usableBytes > (BYTES_IN_PAGE>>1)) && (cells > MIN_CELLS)))
          break;
      }
    }
    // dumpSizeClassData();
  }

  /**
   * Constructor
   * 
   * @param space The rc space to which this allocator
   * instances is bound.
   */
  public ExplicitFreeListLocal(ExplicitFreeListSpace space) {
    super(space);
  }


  /****************************************************************************
   * 
   * Allocation
   */

  /**
   * Prepare the next block in the free block list for use by the free
   * list allocator.  In the case of lazy sweeping this involves
   * sweeping the available cells.  <b>The sweeping operation must
   * ensure that cells are pre-zeroed</b>, as this method must return
   * pre-zeroed cells.
   *
   * @param block The block to be prepared for use
   * @param sizeClass The size class of the block
   * @return The address of the first pre-zeroed cell in the free list
   * for this block, or zero if there are no available cells.
   */
  protected Address advanceToBlock(Address block, int sizeClass) {
    return makeFreeListFromLiveBits(block, sizeClass);
  }

  protected boolean preserveFreeList() { return false; }
  protected boolean maintainSideBitmap() { return true; }
  
  /**
   * Free an object.
   * 
   * @param object The object to be freed.
   */
  @Inline
  public static void free(ObjectReference object) {
    deadObject(object);
  }

  /****************************************************************************
   * 
   * Collection
   */

  /**
   * Prepare for a collection. If paranoid, perform a sanity check.
   */
  public void prepare() {
    flushFreeLists();
  }

  /**
   * Finish up after a collection.
 */
  public void releaseCollector() {
    sweepBlocks(true);
  }
  
  /**
   * Finish up after a collection.
 */
  public void releaseMutator() {
    restoreFreeLists();
  }
}
