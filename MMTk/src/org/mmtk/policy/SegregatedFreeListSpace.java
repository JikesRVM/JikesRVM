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
package org.mmtk.policy;

import org.mmtk.utility.alloc.BlockAllocator;
import org.mmtk.utility.alloc.EmbeddedMetaData;
import org.mmtk.utility.heap.FreeListPageResource;
import org.mmtk.utility.heap.VMRequest;
import org.mmtk.utility.Constants;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.Memory;

import org.mmtk.vm.Lock;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * Each instance of this class corresponds to one mark-sweep *space*.
 * Each of the instance methods of this class may be called by any
 * thread (i.e. synchronization must be explicit in any instance or
 * class method).  This contrasts with the MarkSweepLocal, where
 * instances correspond to *plan* instances and therefore to kernel
 * threads.  Thus unlike this class, synchronization is not necessary
 * in the instance methods of MarkSweepLocal.
 */
@Uninterruptible
public abstract class SegregatedFreeListSpace extends Space implements Constants {

  /****************************************************************************
  *
  * Class variables
  */
  protected static final boolean LAZY_SWEEP = false;
  private static final boolean COMPACT_SIZE_CLASSES = false;
  protected static final int MIN_CELLS = 6;
  protected static final int MAX_CELLS = 99; // (1<<(INUSE_BITS-1))-1;
  protected static final int MAX_CELL_SIZE = 8<<10;
  public static final int MAX_FREELIST_OBJECT_BYTES = MAX_CELL_SIZE;

  // live bits etc
  private static final int OBJECT_LIVE_SHIFT = LOG_MIN_ALIGNMENT; // 4 byte resolution
  private static final int LOG_BIT_COVERAGE = OBJECT_LIVE_SHIFT;
  private static final int LOG_LIVE_COVERAGE = LOG_BIT_COVERAGE + LOG_BITS_IN_BYTE;
  private static final int LIVE_BYTES_PER_REGION = 1 << (EmbeddedMetaData.LOG_BYTES_IN_REGION - LOG_LIVE_COVERAGE);
  private static final Word WORD_SHIFT_MASK = Word.one().lsh(LOG_BITS_IN_WORD).minus(Extent.one());
  private static final int LOG_LIVE_WORD_STRIDE = LOG_LIVE_COVERAGE + LOG_BYTES_IN_WORD;
  private static final Extent LIVE_WORD_STRIDE = Extent.fromIntSignExtend(1<<LOG_LIVE_WORD_STRIDE);
  private static final Word LIVE_WORD_STRIDE_MASK = LIVE_WORD_STRIDE.minus(1).toWord().not();
  private static final int NET_META_DATA_BYTES_PER_REGION = BlockAllocator.META_DATA_BYTES_PER_REGION + LIVE_BYTES_PER_REGION;
  protected static final int META_DATA_PAGES_PER_REGION_WITH_BITMAP = Conversions.bytesToPages(Extent.fromIntSignExtend(NET_META_DATA_BYTES_PER_REGION));
  protected static final int META_DATA_PAGES_PER_REGION_NO_BITMAP = Conversions.bytesToPages(Extent.fromIntSignExtend(BlockAllocator.META_DATA_BYTES_PER_REGION));
  private static final Extent META_DATA_OFFSET = BlockAllocator.META_DATA_EXTENT;


  // calculate worst case fragmentation very conservatively
  private static final int NEW_SIZECLASS_OVERHEAD = sizeClassCount();  // one page wasted per size class
  private static final int METADATA_OVERHEAD = META_DATA_PAGES_PER_REGION_WITH_BITMAP; // worst case scenario
  public static final float WORST_CASE_FRAGMENTATION = 1 + ((NEW_SIZECLASS_OVERHEAD + METADATA_OVERHEAD)/(float) EmbeddedMetaData.BYTES_IN_REGION);

  /****************************************************************************
   *
   * Instance variables
   */
  protected final Lock lock = VM.newLock("SegregatedFreeListGlobal");
  protected final AddressArray consumedBlockHead = AddressArray.create(sizeClassCount());
  protected final AddressArray flushedBlockHead = AddressArray.create(sizeClassCount());
  protected final AddressArray availableBlockHead = AddressArray.create(sizeClassCount());

  private final int[] cellSize = new int[sizeClassCount()];
  private final byte[] blockSizeClass = new byte[sizeClassCount()];
  private final int[] blockHeaderSize = new int[sizeClassCount()];

  /**
   * The caller specifies the region of virtual memory to be used for
   * this space.  If this region conflicts with an existing space,
   * then the constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param pageBudget The number of pages this space may consume before consulting the plan
   * @param additionalMetadata The number of meta data bytes per region for the subclass.
   * @param vmRequest An object describing the virtual memory requested.
   */
  public SegregatedFreeListSpace(String name, int pageBudget, int additionalMetadata, VMRequest vmRequest) {
    super(name, false, false, vmRequest);
    initSizeClasses();
    int totalMetadata = additionalMetadata;
    if (maintainSideBitmap()) {
      totalMetadata += META_DATA_PAGES_PER_REGION_WITH_BITMAP;
    } else {
      totalMetadata += META_DATA_PAGES_PER_REGION_NO_BITMAP;
    }
    if (vmRequest.isDiscontiguous()) {
      pr = new FreeListPageResource(pageBudget, this, totalMetadata);
    } else {
      pr = new FreeListPageResource(pageBudget, this, start, extent, totalMetadata);
    }
  }

  /**
   * Should SegregatedFreeListSpace manage a side bitmap to keep track of live objects?
   */
  protected abstract boolean maintainSideBitmap();

  /**
   * Do we need to preserve free lists as we move blocks around.
   */
  protected abstract boolean preserveFreeList();

  /****************************************************************************
   *
   * Allocation
   */

  /**
   * Return a block to the global pool.
   *
   * @param block The block to return
   * @param sizeClass The size class
   */
  public void returnConsumedBlock(Address block, int sizeClass) {
    returnBlock(block, sizeClass, Address.zero());
  }

  /**
   * Return a block to the global pool.
   *
   * @param block The block to return
   * @param sizeClass The size class
   * @param freeCell The first free cell in the block.
   */
  public void returnBlock(Address block, int sizeClass, Address freeCell) {
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(BlockAllocator.getNext(block).isZero());
    }
    if (preserveFreeList()) {
      setFreeList(block, freeCell);
    }
    lock.acquire();
    BlockAllocator.setNext(block, consumedBlockHead.get(sizeClass));
    consumedBlockHead.set(sizeClass, block);
    lock.release();
  }

  /**
   * Acquire a new block from the global pool to allocate into. This method
   * with either return a non-empty free list, or zero when allocation
   * fails.
   *
   * This method will populate the passed in free list for the given size
   * class and return the address of the block.
   *
   * @param sizeClass The size class to allocate into
   * @param freeList The free list to populate
   * @return The address of the block
   */
  public Address getAllocationBlock(int sizeClass, AddressArray freeList) {
    lock.acquire();
    Address block;
    while(!(block = availableBlockHead.get(sizeClass)).isZero()) {
      availableBlockHead.set(sizeClass, BlockAllocator.getNext(block));
      lock.release();

      /* This block is no longer on any list */
      BlockAllocator.setNext(block, Address.zero());

      /* Can we allocate into this block? */
      Address cell = advanceToBlock(block, sizeClass);
      if (!cell.isZero()) {
        freeList.set(sizeClass, cell);
        return block;
      }

      /* Block was full */
      lock.acquire();
      BlockAllocator.setNext(block, consumedBlockHead.get(sizeClass));
      consumedBlockHead.set(sizeClass, block);
    }
    lock.release();
    return expandSizeClass(sizeClass, freeList);
  }

  /**
   * Expand a particular size class, allocating a new block, breaking
   * the block into cells and placing those cells on a free list for
   * that block.  The block becomes the current head for this size
   * class and the address of the first available cell is returned.<p>
   *
   * <b>This is guaranteed to return pre-zeroed cells</b>
   *
   * @param sizeClass The size class to be expanded
   * @param freeList The free list to populate.
   * @return The block that was just allocated.
   */
  @Inline
  private Address expandSizeClass(int sizeClass, AddressArray freeList) {
    Address block = BlockAllocator.alloc(this, blockSizeClass[sizeClass]);

    if (block.isZero()) {
      return Address.zero();
    }

    BlockAllocator.setNext(block, Address.zero());
    BlockAllocator.setAllClientSizeClass(block, blockSizeClass[sizeClass], (byte) sizeClass);

    notifyNewBlock(block, sizeClass);

    int cellExtent = cellSize[sizeClass];
    int blockSize = BlockAllocator.blockSize(blockSizeClass[sizeClass]);
    int useableBlockSize = blockSize - blockHeaderSize[sizeClass];
    Address firstCell = block.plus(blockHeaderSize[sizeClass]);
    Address sentinel = block.plus(blockSize);

    /* pre-zero the block */
    VM.memory.zero(firstCell, Extent.fromIntZeroExtend(useableBlockSize));

    /* construct the free list */
    Address nextCell;
    Address cell = firstCell;
    while ((nextCell = cell.plus(cellExtent)).LT(sentinel)) {
      cell.store(nextCell);
      cell = nextCell;
    }

    /* Populate the free list */
    freeList.set(sizeClass, firstCell);
    return block;
  }

  /****************************************************************************
   *
   * Block management
   */

  /**
   * Initialize the size class data structures.
   */
  private void initSizeClasses() {
    for (int sc = 0; sc < sizeClassCount(); sc++) {
      cellSize[sc] = getBaseCellSize(sc);
      for (byte blk = 0; blk < BlockAllocator.BLOCK_SIZE_CLASSES; blk++) {
        int usableBytes = BlockAllocator.blockSize(blk);
        int cells = usableBytes / cellSize[sc];
        blockSizeClass[sc] = blk;
        /* cells must start at multiple of MIN_ALIGNMENT because
           cellSize is also supposed to be multiple, this should do
           the trick: */
        blockHeaderSize[sc] = BlockAllocator.blockSize(blk) - cells * cellSize[sc];
        if (((usableBytes < BYTES_IN_PAGE) && (cells*2 > MAX_CELLS)) ||
            ((usableBytes > (BYTES_IN_PAGE>>1)) && (cells > MIN_CELLS)))
          break;
      }
    }
  }

  /**
   * Get the size class for a given number of bytes.
   *
   * We use size classes based on a worst case internal fragmentation
   * loss target of 1/8.  In fact, across sizes from 8 bytes to 512
   * the average worst case loss is 13.3%, giving an expected loss
   * (assuming uniform distribution) of about 7%.  We avoid using the
   * Lea class sizes because they were so numerous and therefore
   * liable to lead to excessive inter-class-size fragmentation.<p>
   *
   * This method may segregate arrays and scalars (currently it does
   * not).<p>
   *
   * This method should be more intelligent and take alignment requests
   * into consideration. The issue with this is that the block header
   * which can be varied by subclasses can change the alignment of the
   * cells.<p>
   *
   * @param bytes The number of bytes required to accommodate the object
   * to be allocated.
   * @return The size class capable of accommodating the allocation request.
   */
  @Inline
  public final int getSizeClass(int bytes) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert((bytes > 0) && (bytes <= MAX_CELL_SIZE));

    int sz1 = bytes - 1;

    if (BYTES_IN_ADDRESS == 4) { // 32-bit
      if (COMPACT_SIZE_CLASSES)
        return (
            (sz1 <=  31) ?      (sz1 >>  2) : //    4 bytes apart
            (sz1 <=  63) ?  4 + (sz1 >>  3) : //    8 bytes apart
            (sz1 <=  95) ?  8 + (sz1 >>  4) : //   16 bytes apart
            (sz1 <= 223) ? 14 + (sz1 >>  6) : //   64 bytes apart
            (sz1 <= 734) ? 17 + (sz1 >>  8) : //  256 bytes apart
                           20 + (sz1 >> 10)); // 1024 bytes apart
      else
        return (
            (sz1 <=   63) ?      (sz1 >>  2) : //    4 bytes apart
            (sz1 <=  127) ? 12 + (sz1 >>  4) : //   16 bytes apart
            (sz1 <=  255) ? 16 + (sz1 >>  5) : //   32 bytes apart
            (sz1 <=  511) ? 20 + (sz1 >>  6) : //   64 bytes apart
            (sz1 <= 2047) ? 26 + (sz1 >>  8) : //  256 bytes apart
                            32 + (sz1 >> 10)); // 1024 bytes apart
    } else { // 64-bit
      if (COMPACT_SIZE_CLASSES)
        return (
            (sz1 <=   95) ?      (sz1 >>  3) : //    8 bytes apart
            (sz1 <=  127) ?  6 + (sz1 >>  4) : //   16 bytes apart
            (sz1 <=  191) ? 10 + (sz1 >>  5) : //   32 bytes apart
            (sz1 <=  383) ? 13 + (sz1 >>  6) : //   64 bytes apart
            (sz1 <=  511) ? 16 + (sz1 >>  7) : //  128 bytes apart
            (sz1 <= 1023) ? 19 + (sz1 >>  9) : //  512 bytes apart
                            20 + (sz1 >> 10)); // 1024 bytes apart
      else
        return (
            (sz1 <=  111) ?      (sz1 >>  3) : //    8 bytes apart
            (sz1 <=  223) ?  7 + (sz1 >>  4) : //   16 bytes apart
            (sz1 <=  319) ? 14 + (sz1 >>  5) : //   32 bytes apart
            (sz1 <=  575) ? 19 + (sz1 >>  6) : //   64 bytes apart
            (sz1 <= 2047) ? 26 + (sz1 >>  8) : //  256 bytes apart
                            32 + (sz1 >> 10)); // 1024 bytes apart
    }
  }

  /**
   * Return the size of a basic cell (i.e. not including any cell
   * header) for a given size class.
   *
   * @param sc The size class in question
   * @return The size of a basic cell (i.e. not including any cell
   * header).
   */
  @Inline
  public final int getBaseCellSize(int sc) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert((sc >= 0) && (sc < sizeClassCount()));

    if (BYTES_IN_ADDRESS == 4) { // 32-bit
      if (COMPACT_SIZE_CLASSES)
        return ((sc <  8) ? (sc +  1) <<  2:
                (sc < 12) ? (sc -  3) <<  3:
                (sc < 16) ? (sc -  7) <<  4:
                (sc < 18) ? (sc - 13) <<  6:
                (sc < 21) ? (sc - 16) <<  8:
                            (sc - 19) << 10);
      else
        return ((sc < 16) ? (sc +  1) <<  2:
                (sc < 20) ? (sc - 11) <<  4:
                (sc < 24) ? (sc - 15) <<  5:
                (sc < 28) ? (sc - 19) <<  6:
                (sc < 34) ? (sc - 25) <<  8:
                            (sc - 31) << 10);
    } else { // 64-bit
      if (COMPACT_SIZE_CLASSES)
        return ((sc < 12) ? (sc +  1) <<  3:
                (sc < 14) ? (sc -  5) <<  4:
                (sc < 16) ? (sc -  9) <<  5:
                (sc < 19) ? (sc - 12) <<  6:
                (sc < 20) ? (sc - 15) <<  7:
                (sc < 21) ? (sc - 18) <<  9:
                            (sc - 19) << 10);
      else
        return ((sc < 14) ? (sc +  1) <<  3:
                (sc < 21) ? (sc -  6) <<  4:
                (sc < 24) ? (sc - 13) <<  5:
                (sc < 28) ? (sc - 18) <<  6:
                (sc < 34) ? (sc - 25) <<  8:
                            (sc - 31) << 10);
    }
  }

  /**
   * The number of distinct size classes.
   */
  @Inline
  public static int sizeClassCount() {
    return (COMPACT_SIZE_CLASSES) ? 28 : 40;
  }

  /****************************************************************************
   *
   * Preserving (saving & restoring) free lists
   */

  /**
   * Prepare a block for allocation, returning a free list into the block.
   *
   * @param block The new block
   * @param sizeClass The block's sizeclass.
   */
  protected abstract Address advanceToBlock(Address block, int sizeClass);

  /**
   * Notify that a new block has been installed.
   *
   * @param block The new block
   * @param sizeClass The block's sizeclass.
   */
  protected void notifyNewBlock(Address block, int sizeClass) {}

  /**
   * Should the sweep reclaim the cell containing this object. Is this object
   * live. This is only used when maintainSideBitmap is false.
   *
   * @param object The object to query
   * @return True if the cell should be reclaimed
   */
  protected boolean reclaimCellForObject(ObjectReference object) {
    VM.assertions.fail("Must implement reclaimCellForObject if not maintaining side bitmap");
    return false;
  }

  /****************************************************************************
   *
   * Metadata manipulation
   */

  /**
   * In the case where free lists associated with each block are
   * preserved, get the free list for a given block.
   *
   * @param block The block whose free list is to be found
   * @return The free list for this block
   */
  @Inline
  protected final Address getFreeList(Address block) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(preserveFreeList());
    return BlockAllocator.getFreeListMeta(block);
  }

  /**
   * In the case where free lists associated with each block are
   * preserved, set the free list for a given block.
   *
   * @param block The block whose free list is to be found
   * @param cell The head of the free list (i.e. the first cell in the
   * free list).
   */
  @Inline
  protected final void setFreeList(Address block, Address cell) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(preserveFreeList());
    BlockAllocator.setFreeListMeta(block, cell);
  }

  /****************************************************************************
   *
   * Collection
   */

  /**
   * Clear all block marks for this space.  This method is important when
   * it is desirable to do partial collections, which man mean that block
   * marks need to be explicitly cleared when necessary.
   */
  protected final void clearAllBlockMarks() {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!maintainSideBitmap());
    for (int sizeClass = 0; sizeClass < sizeClassCount(); sizeClass++) {
      Extent blockSize = Extent.fromIntSignExtend(BlockAllocator.blockSize(blockSizeClass[sizeClass]));
      /* Flushed blocks */
      Address block = flushedBlockHead.get(sizeClass);
      while (!block.isZero()) {
        Address next = BlockAllocator.getNext(block);
        clearBlockMark(block, blockSize);
        block = next;
      }
      /* Available blocks */
      block = consumedBlockHead.get(sizeClass);
      while (!block.isZero()) {
        Address next = BlockAllocator.getNext(block);
        clearBlockMark(block, blockSize);
        block = next;
      }
    }
  }

  /**
   * Sweep all blocks for free objects.
   *
   * @param clearMarks should we clear block mark bits as we process.
   */
  protected final void sweepConsumedBlocks(boolean clearMarks) {
    for (int sizeClass = 0; sizeClass < sizeClassCount(); sizeClass++) {
      Extent blockSize = Extent.fromIntSignExtend(BlockAllocator.blockSize(blockSizeClass[sizeClass]));
      Address availableHead = Address.zero();
      /* Flushed blocks */
      Address block = flushedBlockHead.get(sizeClass);
      flushedBlockHead.set(sizeClass, Address.zero());
      while (!block.isZero()) {
        Address next = BlockAllocator.getNext(block);
        availableHead = sweepBlock(block, sizeClass, blockSize, availableHead, clearMarks);
        block = next;
      }
      /* Consumed blocks */
      block = consumedBlockHead.get(sizeClass);
      consumedBlockHead.set(sizeClass, Address.zero());
      while (!block.isZero()) {
        Address next = BlockAllocator.getNext(block);
        availableHead = sweepBlock(block, sizeClass, blockSize, availableHead, clearMarks);
        block = next;
      }
      /* Make blocks available */
      availableBlockHead.set(sizeClass, availableHead);
    }
  }

  /**
   * Sweep a block, freeing it and adding to the list given by availableHead
   * if it contains no free objects.
   *
   * @param clearMarks should we clear block mark bits as we process.
   */
  protected final Address sweepBlock(Address block, int sizeClass, Extent blockSize, Address availableHead, boolean clearMarks) {
    boolean liveBlock = containsLiveCell(block, blockSize, clearMarks);
    if (!liveBlock) {
      BlockAllocator.setNext(block, Address.zero());
      BlockAllocator.free(this, block);
    } else {
      BlockAllocator.setNext(block, availableHead);
      availableHead = block;
      if (!LAZY_SWEEP) {
        setFreeList(block, makeFreeList(block, sizeClass));
      }
    }
    return availableHead;
  }

  /**
   * Eagerly consume all remaining blocks.
   */
  protected final void consumeBlocks() {
    for (int sizeClass = 0; sizeClass < sizeClassCount(); sizeClass++) {
      while (!availableBlockHead.get(sizeClass).isZero()) {
        Address block = availableBlockHead.get(sizeClass);
        availableBlockHead.set(sizeClass, BlockAllocator.getNext(block));
        advanceToBlock(block, sizeClass);
        BlockAllocator.setNext(block, consumedBlockHead.get(sizeClass));
        consumedBlockHead.set(sizeClass, block);
      }
    }
  }

  /**
   * Flush all the allocation blocks to the consumed list.
   */
  protected final void flushAvailableBlocks() {
    for (int sizeClass = 0; sizeClass < sizeClassCount(); sizeClass++) {
      flushedBlockHead.set(sizeClass, availableBlockHead.get(sizeClass));
      availableBlockHead.set(sizeClass, Address.zero());
    }
  }

  /**
   * Does this block contain any live cells.
   *
   * @param block The block
   * @param blockSize The size of the block
   * @param clearMarks should we clear block mark bits as we process.
   * @return True if any cells in the block are live
   */
  @Inline
  protected boolean containsLiveCell(Address block, Extent blockSize, boolean clearMarks) {
    if (maintainSideBitmap()) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(alignToLiveStride(block).EQ(block));
      Address cursor = getLiveWordAddress(block);
      Address sentinel = getLiveWordAddress(block.plus(blockSize.minus(1)));
      while (cursor.LE(sentinel)) {
        Word live = cursor.loadWord();
        if (!live.isZero()) {
          return true;
        }
        cursor = cursor.plus(BYTES_IN_WORD);
      }
      return false;
    } else {
      boolean live = false;
      Address cursor = block;
      while(cursor.LT(block.plus(blockSize))) {
        live |= BlockAllocator.checkBlockMeta(cursor);
        if (clearMarks)
          BlockAllocator.clearBlockMeta(cursor);
        cursor = cursor.plus(1 << BlockAllocator.LOG_MIN_BLOCK);
      }
      return live;
    }
  }


  /**
   * Clear block marks for a block
   *
   * @param block The block
   * @param blockSize The size of the block
   */
  @Inline
  protected void clearBlockMark(Address block, Extent blockSize) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!maintainSideBitmap());
    Address cursor = block;
    while(cursor.LT(block.plus(blockSize))) {
      BlockAllocator.clearBlockMeta(cursor);
      cursor = cursor.plus(1 << BlockAllocator.LOG_MIN_BLOCK);
    }
  }

  /**
   * In the cell containing this object live?
   *
   * @param object The object
   * @return True if the cell is live
   */
  @Inline
  protected boolean isCellLive(ObjectReference object) {
    /* Must override if not using the side bitmap */
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(maintainSideBitmap());
    return liveBitSet(object);
  }

  /**
   * Use the live bits for a block to infer free cells and thus
   * construct a free list for the block.
   *
   * @param block The block to be processed
   * @param sizeClass The size class for the block
   * @return The head of the new free list
   */
  @Inline
  protected final Address makeFreeList(Address block, int sizeClass) {
    Extent blockSize = Extent.fromIntSignExtend(BlockAllocator.blockSize(blockSizeClass[sizeClass]));
    Address cursor = block.plus(blockHeaderSize[sizeClass]);
    Address lastFree = Address.zero();
    Address firstFree = Address.zero();
    Address end = block.plus(blockSize);
    Extent cellExtent = Extent.fromIntSignExtend(cellSize[sizeClass]);
    while (cursor.LT(end)) {
      ObjectReference current = VM.objectModel.getObjectFromStartAddress(cursor);
      boolean free = true;
      if (!current.isNull()) {
        free = !isCellLive(current);
      }
      if (free) {
        if (firstFree.isZero()) {
          firstFree = cursor;
        } else {
          lastFree.store(cursor);
        }
        Memory.zeroSmall(cursor, cellExtent);
        lastFree = cursor;
      }
      cursor = cursor.plus(cellExtent);
    }
    return firstFree;
  }

  /**
   * Sweep all blocks for free objects.
   */
  public void sweepCells(Sweeper sweeper) {
    for (int sizeClass = 0; sizeClass < sizeClassCount(); sizeClass++) {
      Address availableHead = Address.zero();
      /* Flushed blocks */
      Address block = flushedBlockHead.get(sizeClass);
      flushedBlockHead.set(sizeClass, Address.zero());
      while (!block.isZero()) {
        Address next = BlockAllocator.getNext(block);
        availableHead = sweepCells(sweeper, block, sizeClass, availableHead);
        block = next;
      }
      /* Consumed blocks */
      block = consumedBlockHead.get(sizeClass);
      consumedBlockHead.set(sizeClass, Address.zero());
      while (!block.isZero()) {
        Address next = BlockAllocator.getNext(block);
        availableHead = sweepCells(sweeper, block, sizeClass, availableHead);
        block = next;
      }
      /* Make blocks available */
      availableBlockHead.set(sizeClass, availableHead);
    }
  }

  /**
   * Sweep a block, freeing it and adding to the list given by availableHead
   * if it contains no free objects.
   */
  private Address sweepCells(Sweeper sweeper, Address block, int sizeClass, Address availableHead) {
    boolean liveBlock = sweepCells(sweeper, block, sizeClass);
    if (!liveBlock) {
      BlockAllocator.setNext(block, Address.zero());
      BlockAllocator.free(this, block);
    } else {
      BlockAllocator.setNext(block, availableHead);
      availableHead = block;
    }
    return availableHead;
  }

  /**
   * Sweep a block, freeing it and making it available if any live cells were found.
   * if it contains no free objects.
   *
   * This is designed to be called in parallel by multiple collector threads.
   */
  public void parallelSweepCells(Sweeper sweeper) {
    for (int sizeClass = 0; sizeClass < sizeClassCount(); sizeClass++) {
      Address block;
      while(!(block = getSweepBlock(sizeClass)).isZero()) {
        boolean liveBlock = sweepCells(sweeper, block, sizeClass);
        if (!liveBlock) {
          BlockAllocator.setNext(block, Address.zero());
          BlockAllocator.free(this, block);
        } else {
          lock.acquire();
          BlockAllocator.setNext(block, availableBlockHead.get(sizeClass));
          availableBlockHead.set(sizeClass, block);
          lock.release();
        }
      }
    }
  }

  /**
   * Get a block for a parallel sweep.
   *
   * @param sizeClass The size class of the block to sweep.
   * @return The block or zero if no blocks remain to be swept.
   */
  private Address getSweepBlock(int sizeClass) {
    lock.acquire();
    Address block;

    /* Flushed blocks */
    block = flushedBlockHead.get(sizeClass);
    if (!block.isZero()) {
      flushedBlockHead.set(sizeClass, BlockAllocator.getNext(block));
      lock.release();
      BlockAllocator.setNext(block, Address.zero());
      return block;
    }

    /* Consumed blocks */
    block = consumedBlockHead.get(sizeClass);
    if (!block.isZero()) {
      flushedBlockHead.set(sizeClass, BlockAllocator.getNext(block));
      lock.release();
      BlockAllocator.setNext(block, Address.zero());
      return block;
    }

    /* All swept! */
    lock.release();
    return Address.zero();
  }

  /**
   * Does this block contain any live cells?
   */
  @Inline
  public boolean sweepCells(Sweeper sweeper, Address block, int sizeClass) {
    Extent blockSize = Extent.fromIntSignExtend(BlockAllocator.blockSize(blockSizeClass[sizeClass]));
    Address cursor = block.plus(blockHeaderSize[sizeClass]);
    Address end = block.plus(blockSize);
    Extent cellExtent = Extent.fromIntSignExtend(cellSize[sizeClass]);
    boolean containsLive = false;
    while (cursor.LT(end)) {
      ObjectReference current = VM.objectModel.getObjectFromStartAddress(cursor);
      boolean free = true;
      if (!current.isNull()) {
        free = !liveBitSet(current);
        if (!free) {
          free = sweeper.sweepCell(current);
          if (free) unsyncClearLiveBit(current);
        }
      }
      if (!free) {
        containsLive = true;
      }
      cursor = cursor.plus(cellExtent);
    }
    return containsLive;
  }

  /**
   * A callback used to perform sweeping of a free list space.
   */
  @Uninterruptible
  public abstract static class Sweeper {
    public abstract boolean sweepCell(ObjectReference object);
  }

  /****************************************************************************
   *
   * Live bit manipulation
   */

  /**
   * Atomically set the live bit for a given object
   *
   * @param object The object whose live bit is to be set.
   * @return True if the bit was changed to true.
   */
  @Inline
  public static boolean testAndSetLiveBit(ObjectReference object) {
    return updateLiveBit(VM.objectModel.objectStartRef(object), true, true);
  }

  /**
   * Set the live bit for the block containing the given object
   *
   * @param object The object whose blocks liveness is to be set.
   */
  @Inline
  protected static void markBlock(ObjectReference object) {
    BlockAllocator.markBlockMeta(object);
  }

  /**
   * Set the live bit for the given block.
   *
   * @param block The block whose liveness is to be set.
   */
  @Inline
  protected static void markBlock(Address block) {
    BlockAllocator.markBlockMeta(block);
  }

  /**
   * Set the live bit for a given object, without using
   * synchronization primitives---must only be used when contention
   * for live bit is strictly not possible
   *
   * @param object The object whose live bit is to be set.
   */
  @Inline
  public static boolean unsyncSetLiveBit(ObjectReference object) {
    return updateLiveBit(VM.objectModel.refToAddress(object), true, false);
  }

  /**
   * Set the live bit for a given address
   *
   * @param address The address whose live bit is to be set.
   * @param set True if the bit is to be set, as opposed to cleared
   * @param atomic True if we want to perform this operation atomically
   */
  @Inline
  private static boolean updateLiveBit(Address address, boolean set, boolean atomic) {
    Word oldValue, newValue;
    Address liveWord = getLiveWordAddress(address);
    Word mask = getMask(address, true);
    if (atomic) {
      do {
        oldValue = liveWord.prepareWord();
        newValue = (set) ? oldValue.or(mask) : oldValue.and(mask.not());
      } while (!liveWord.attempt(oldValue, newValue));
    } else {
      oldValue = liveWord.loadWord();
      liveWord.store(set ? oldValue.or(mask) : oldValue.and(mask.not()));
    }
    return oldValue.and(mask).NE(mask);
  }

  /**
   * Test the live bit for a given object
   *
   * @param object The object whose live bit is to be set.
   */
  @Inline
  protected static boolean liveBitSet(ObjectReference object) {
    return liveBitSet(VM.objectModel.refToAddress(object));
  }

  /**
   * Set the live bit for a given address
   *
   * @param address The address whose live bit is to be set.
   * @return true if this operation changed the state of the live bit.
   */
  @Inline
  protected static boolean liveBitSet(Address address) {
    Address liveWord = getLiveWordAddress(address);
    Word mask = getMask(address, true);
    Word value = liveWord.loadWord();
    return value.and(mask).EQ(mask);
  }

  /**
   * Clear the live bit for a given object
   *
   * @param object The object whose live bit is to be cleared.
   */
  @Inline
  protected static void clearLiveBit(ObjectReference object) {
    clearLiveBit(VM.objectModel.refToAddress(object));
  }

  /**
   * Clear the live bit for a given address
   *
   * @param address The address whose live bit is to be cleared.
   */
  @Inline
  protected static void clearLiveBit(Address address) {
    updateLiveBit(address, false, true);
  }

  /**
   * Clear the live bit for a given object
   *
   * @param object The object whose live bit is to be cleared.
   */
  @Inline
  protected static void unsyncClearLiveBit(ObjectReference object) {
    unsyncClearLiveBit(VM.objectModel.refToAddress(object));
  }

  /**
   * Clear the live bit for a given address
   *
   * @param address The address whose live bit is to be cleared.
   */
  @Inline
  protected static void unsyncClearLiveBit(Address address) {
    updateLiveBit(address, false, false);
  }

  /**
   * Clear all live bits for a block
   */
  protected void clearLiveBits(Address block, int sizeClass) {
    int blockSize = BlockAllocator.blockSize(blockSizeClass[sizeClass]);
    Address cursor = getLiveWordAddress(block);
    Address sentinel = getLiveWordAddress(block.plus(blockSize - 1));
    while (cursor.LE(sentinel)) {
      cursor.store(Word.zero());
      cursor = cursor.plus(BYTES_IN_WORD);
    }
  }

  /**
   * Clear all live bits
   */
  protected static void zeroLiveBits(Address start, Address end) {
    Extent bytes = Extent.fromIntSignExtend(EmbeddedMetaData.BYTES_IN_REGION>>LOG_LIVE_COVERAGE);
    while (start.LT(end)) {
      Address metadata = EmbeddedMetaData.getMetaDataBase(start).plus(META_DATA_OFFSET);
      VM.memory.zero(metadata, bytes);
      start = start.plus(EmbeddedMetaData.BYTES_IN_REGION);
    }
  }

  /**
   * Align an address so that it corresponds to a live word boundary.
   * In other words, if the live bit for the given address is not the
   * zeroth bit of a live word, round the address down such that it
   * does.
   *
   * @param address The address to be aligned to a live word
   * @return The given address, aligned down so that it corresponds to
   * an address on a live word boundary.
   */
  private static Address alignToLiveStride(Address address) {
    return address.toWord().and(LIVE_WORD_STRIDE_MASK).toAddress();
  }

  /**
   * Given an address, produce a bit mask for the live table
   *
   * @param address The address whose live bit mask is to be established
   * @param set True if we want the mask for <i>setting</i> the bit,
   * false if we want the mask for <i>clearing</i> the bit.
   * @return The appropriate bit mask for object for the live table for.
   */
  @Inline
  private static Word getMask(Address address, boolean set) {
    int shift = address.toWord().rshl(OBJECT_LIVE_SHIFT).and(WORD_SHIFT_MASK).toInt();
    Word rtn = Word.one().lsh(shift);
    return (set) ? rtn : rtn.not();
  }

  /**
   * Given an address, return the address of the live word for
   * that address.
   *
   * @param address The address whose live word address is to be returned
   * @return The address of the live word for this object
   */
  @Inline
  private static Address getLiveWordAddress(Address address) {
    Address rtn = EmbeddedMetaData.getMetaDataBase(address);
    return rtn.plus(META_DATA_OFFSET).plus(EmbeddedMetaData.getMetaDataOffset(address, LOG_LIVE_COVERAGE, LOG_BYTES_IN_WORD));
  }
}
