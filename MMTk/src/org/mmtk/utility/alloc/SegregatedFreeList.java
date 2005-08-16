/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */
package org.mmtk.utility.alloc;

import org.mmtk.policy.Space;
import org.mmtk.utility.*;
import org.mmtk.vm.Assert;
import org.mmtk.utility.Constants;
import org.mmtk.vm.ObjectModel;
import org.mmtk.utility.options.Options;
import org.mmtk.utility.options.FragmentationStats;
import org.mmtk.utility.options.VerboseFragmentationStats;
import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 *
 * This abstract class implements a simple segregated free list.<p>
 *
 * See: Wilson, Johnstone, Neely and Boles "Dynamic Storage
 * Allocation: A Survey and Critical Review", IWMM 1995, for an
 * overview of free list allocation and the various implementation
 * strategies, including segregated free lists.<p>
 *
 * We maintain a number of size classes, each size class having a free
 * list of available objects of that size (the list may be empty).  We
 * call the storage elements "cells".  Cells reside within chunks of
 * memory called "blocks".  All cells in a given block are of the same
 * size (i.e. blocks are homogeneous with respect to size class).
 * Each block maintains its own free list (free cells within that
 * block).  For each size class a list of blocks is maintained, one of
 * which will serve the role of the current free list.  When the free
 * list on the current block is exhausted, the next block for that
 * size class becomes the current block and its free list is used.  If
 * there are no more blocks the a new block is allocated.<p>
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public abstract class SegregatedFreeList extends Allocator 
  implements Constants, Uninterruptible {
  public final static String Id = "$Id$"; 

  /****************************************************************************
   *
   * Class variables
   */
  protected static final boolean LAZY_SWEEP = true;
  private static final boolean COMPACT_SIZE_CLASSES = false;
  private static final boolean SORT_FREE_BLOCKS = false;
  private static final int BLOCK_BUCKETS = 3;
  protected static final Address DEBUG_BLOCK = Address.max();  // 0x5b098008
  protected static final int SIZE_CLASSES = (COMPACT_SIZE_CLASSES) ? 28 : 40;
  protected static final int FREE_LIST_HEADER_BYTES = BYTES_IN_ADDRESS;
  private static final int FREE_LIST_OFFSET = 0;
  private static final int FREE_LIST_BITS = BlockAllocator.LOG_MAX_BLOCK;
  private static final int SIZE_CLASS_BITS = 6;
  private static final int INUSE_BITS = 10;
  private static final int SIZE_CLASS_SHIFT = FREE_LIST_BITS;
  private static final int INUSE_SHIFT = FREE_LIST_BITS + SIZE_CLASS_BITS;
  protected static final int MIN_CELLS = 6;
  protected static final int MAX_CELLS = 99; //(1<<(INUSE_BITS-1))-1;

  // live bits etc
  private static final int OBJECT_LIVE_SHIFT = LOG_MIN_ALIGNMENT; // 4 byte resolution
  private static final int LOG_BIT_COVERAGE = OBJECT_LIVE_SHIFT;
  protected static final int BYTES_PER_LIVE_BIT = 1<<LOG_BIT_COVERAGE;
  private static final int BYTES_PER_LIVE_WORD = 1<<(LOG_BIT_COVERAGE+LOG_BITS_IN_WORD);
  private static final int LOG_LIVE_COVERAGE = LOG_BIT_COVERAGE + LOG_BITS_IN_BYTE;
  private static final int LIVE_BYTES_PER_REGION = 1<<(EmbeddedMetaData.LOG_BYTES_IN_REGION - LOG_LIVE_COVERAGE);
  private static final Word WORD_SHIFT_MASK = Word.one().lsh(LOG_BITS_IN_WORD).sub(Extent.one());
  private static final int LOG_LIVE_WORD_STRIDE = LOG_LIVE_COVERAGE + LOG_BYTES_IN_WORD;
  private static final Extent LIVE_WORD_STRIDE = Extent.fromIntSignExtend(1<<LOG_LIVE_WORD_STRIDE);
  private static final Word LIVE_WORD_STRIDE_MASK = LIVE_WORD_STRIDE.sub(1).toWord().not();
  private static final int NET_META_DATA_BYTES_PER_REGION = BlockAllocator.META_DATA_BYTES_PER_REGION + LIVE_BYTES_PER_REGION;
  public static final int META_DATA_PAGES_PER_REGION = Conversions.bytesToPages(Extent.fromIntSignExtend(NET_META_DATA_BYTES_PER_REGION));
  
  private static final Extent META_DATA_OFFSET = BlockAllocator.META_DATA_EXTENT;

  protected static int[] cellSize;
  protected static byte[] blockSizeClass;
  protected static int[] blockHeaderSize;
  protected static int[] cellsInBlock;

  public static final boolean FRAGMENTATION_CHECK = false;
  protected static final boolean FRAG_VERBOSE = false;
  protected static int bytesAlloc;
  private long[] fragInuseCellBytes;
  private int[] fragUsedPages;

  /****************************************************************************
   *
   * Instance variables
   */
  protected BlockAllocator blockAllocator;
  protected AddressArray freeList; 
  protected AddressArray firstBlock;
  protected AddressArray lastBlock;
  protected AddressArray currentBlock;
  private AddressArray blockBucketHead; 
  private AddressArray blockBucketTail; 
  protected int [] cellsInUse;

  /****************************************************************************
   *
   * Initialization
   */

  static {
    Options.fragmentationStats = new FragmentationStats();
    Options.verboseFragmentationStats = new VerboseFragmentationStats();  
  }

  /**
   * Constructor
   *
   * @param space The space with which this allocator will be associated
   */
  public SegregatedFreeList(Space space) {
    blockAllocator = new BlockAllocator(space);
    freeList = AddressArray.create(SIZE_CLASSES);
    firstBlock = AddressArray.create(SIZE_CLASSES);
    lastBlock = AddressArray.create(SIZE_CLASSES);
    currentBlock = AddressArray.create(SIZE_CLASSES);
    blockBucketHead = AddressArray.create(BLOCK_BUCKETS);
    blockBucketTail = AddressArray.create(BLOCK_BUCKETS);
  }

  /****************************************************************************
   *
   * Allocation
   */

  /**
   * Allocate <code>bytes</code> contigious bytes of zeroed memory.<p>
   *
   * This code first tries the fast version and, if needed, the slow path.
   *
   * @param bytes The size of the object to occupy this space, in bytes.
   * @param align The requested alignment.
   * @param offset The alignment offset.
   * @param inGC If true, this allocation is occuring with respect to
   * a space that is currently being collected.
   * @return The address of the first word of <code>bytes</code>
   * contigious bytes of zeroed memory.
   */
  public final Address alloc(int bytes, int align, int offset, boolean inGC) 
    throws InlinePragma {
    if (FRAGMENTATION_CHECK)
      bytesAlloc += bytes;
    Address cell = allocFast(bytes, align, offset, inGC);
    if (cell.isZero()) 
      return allocSlow(bytes, align, offset, inGC);
    else
      return cell;
  }

  /**
   * Allocate <code>bytes</code> contigious bytes of zeroed memory.<p>
   *
   * This code must be efficient and must compile easily.  Here we
   * minimize the number of calls to inlined functions, and force the
   * "slow path" (uncommon case) out of line to reduce pressure on the
   * compiler.  We have a call to an abstract method that allows
   * subclasses to customize post-allocation.
   *
   * @param bytes The size of the object to occupy this space, in bytes.
   * @param align The requested alignment.
   * @param offset The alignment offset.
   * @param inGC If true, this allocation is occuring with respect to
   * a space that is currently being collected.
   * @return The address of the first word of <code>bytes</code>
   * contigious bytes of zeroed memory.
   */
  public final Address allocFast(int bytes, int align, int offset,
                                 boolean inGC) throws InlinePragma {
    int alignedBytes = getMaximumAlignedSize(bytes, align);
    int sizeClass = getSizeClass(alignedBytes);
    Address cell = freeList.get(sizeClass);
    if (!cell.isZero()) {
      freeList.set(sizeClass, getNextCell(cell));
      setNextCell(cell, Address.zero()); // clear out the free list link
      if (alignedBytes != bytes) {
        // Ensure aligned as requested.
        return alignAllocation(cell, align, offset);
      } 
    } 

    // Alignment request guaranteed or cell.isZero().
    return cell;
  }

  /**
   * Allocate <code>bytes</code> contigious bytes of non-zeroed
   * memory.  First check if the fast path works.  This is needed
   * since this method may be called in the context when the fast
   * version was NOT just called.  If this fails, it will try finding
   * another block with a non-empty free list, or allocating a new
   * block.<p>
   *
   * This code should be relatively infrequently executed, so it is
   * forced out of line to reduce pressure on the compilation of the
   * core alloc routine.<p>
   *
   * Precondition: None 
   *
   * Postconditions: A new cell has been allocated (not zeroed), and
   * the block containing the cell has been placed on the appropriate
   * free list data structures.  The free list itself is not updated
   * (the caller must do so).<p>
   * 
   * @param bytes The size of the object to occupy this space, in bytes.
   * @param align The requested alignment.
   * @param offset The alignment offset.
   * @param inGC If true, this allocation is occuring with respect to
   * a space that is currently being collected.
   * @return The address of the first word of the <code>bytes</code>
   * contigious bytes of zerod memory.
   */
  public final Address allocSlowOnce(int bytes, int align, int offset,
                                     boolean inGC) throws NoInlinePragma {
    Address cell = allocFast(bytes, align, offset, inGC);
    if (!cell.isZero()) 
      return cell;

    // Bytes within which we can guarantee an aligned allocation.
    bytes = getMaximumAlignedSize(bytes, align); 
    int sizeClass = getSizeClass(bytes);
    Address current = currentBlock.get(sizeClass);
    if (!current.isZero()) {
      // zero the current (empty) free list if necessary
      if (preserveFreeList())
        setFreeList(current, Address.zero());

      // find a free list which is not empty
      current = BlockAllocator.getNextBlock(current);
      while (!current.isZero()) {
        cell = advanceToBlock(current, sizeClass);
        if (!cell.isZero()) {
          // this block has at least one free cell, so use it
          currentBlock.set(sizeClass, current);
          freeList.set(sizeClass, getNextCell(cell));
          setNextCell(cell, Address.zero()); // clear out the free list link
          return alignAllocation(cell, align, offset);
        }
        current = BlockAllocator.getNextBlock(current);
      }
    }

    cell = expandSizeClass(sizeClass);
    if (cell.isZero())
      return Address.zero();

    freeList.set(sizeClass, getNextCell(cell));
    Memory.zeroSmall(cell, Extent.fromIntZeroExtend(bytes));
    return alignAllocation(cell, align, offset);
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
   * @return The address of the first available cell in the newly
   * allocated block of pre-zeroed cells, or return zero if there were
   * insufficient resources to allocate a new block.
   */
  private final Address expandSizeClass(int sizeClass) 
    throws InlinePragma {
    Address block = blockAllocator.alloc(blockSizeClass[sizeClass]);
    if (block.isZero())
      return Address.zero();

    installNewBlock(block, sizeClass);

    int cellExtent = cellSize[sizeClass];
    Address cursor = block.add(blockHeaderSize[sizeClass]);
    int blockSize = BlockAllocator.blockSize(blockSizeClass[sizeClass]);
    int useableBlockSize = blockSize - blockHeaderSize[sizeClass];
    Address sentinel = block.add(blockSize);
    Address lastCell = Address.zero();
    int cellCount = 0;

    // pre-zero the block
    Memory.zero(cursor, Extent.fromIntZeroExtend(useableBlockSize));

    // construct the free list
    while (cursor.add(cellExtent).LE(sentinel)) {
      setNextCell(cursor, lastCell); 
      lastCell = cursor;
      cursor = cursor.add(cellExtent);
      cellCount++;
    }

    if (Assert.VERIFY_ASSERTIONS) Assert._assert(!lastCell.isZero());
    return lastCell;
  }

  /**
   * Return the next cell in a free list chain.
   *
   * @param cell The address of teh current cell.
   * @return The next cell in a free list chain (null if this is the
   * last).
   */
  protected final Address getNextCell(Address cell)
    throws InlinePragma {
    return cell.loadAddress();
  }

  /**
   * Set the next cell in a free list chain
   *
   * @param cell The cell whose link is to be set
   * @param next The next cell in the chain.
   */
  private final void setNextCell(Address cell, Address next)
    throws InlinePragma {
    cell.store(next);
  }

  /****************************************************************************
   *
   * Freeing
   */

  /**
   * Free a cell.  The cell is added to the free list for the given
   * block, and the inuse count for the block is decremented, the
   * block freed if empty.  <b>The cell is zeroed as it is freed.</b>
   *
   * @param cell The cell to be freed
   * @param block The block on which the cell resides
   * @param sizeClass The size class of the cell and block
   * @param nextFree The next cell in the free list
   */
  public final void free(Address cell, Address block, int sizeClass, 
                         Address nextFree)
    throws InlinePragma {
    Memory.zeroSmall(cell, Extent.fromIntZeroExtend(cellSize[sizeClass]));
    setNextCell(cell, nextFree);
  }

  /****************************************************************************
   *
   * Block management
   */

  /**
   * Install a new block. The block needs to be added to the size
   * class's linked list of blocks and made the current block.
   *
   * @param block The block to be added
   * @param sizeClass The size class to which the block is being added
   */
  private final void installNewBlock(Address block, int sizeClass) 
    throws InlinePragma {
    BlockAllocator.linkedListInsert(block, lastBlock.get(sizeClass));
    currentBlock.set(sizeClass, block);
    lastBlock.set(sizeClass, block);
    if (firstBlock.get(sizeClass).isZero())
      firstBlock.set(sizeClass, block);
  }

  /**
   * Free a block.  The block needs to be removed from its size
   * class's linked list before being freed.
   *
   * @param block The block to be freed
   * @param sizeClass The size class with which the block was associated.
   */
  protected final void freeBlock(Address block, int sizeClass) 
    throws InlinePragma {
    Address next = BlockAllocator.getNextBlock(block);
    Address prev = BlockAllocator.getPrevBlock(block);
    BlockAllocator.unlinkBlock(block);
    if (firstBlock.get(sizeClass).EQ(block))
      firstBlock.set(sizeClass, next);
    if (currentBlock.get(sizeClass).EQ(block))
      currentBlock.set(sizeClass, next);
    if (lastBlock.get(sizeClass).EQ(block))
      lastBlock.set(sizeClass, prev);
    blockAllocator.free(block);
  }

  /****************************************************************************
   *
   * Size classes
   */

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
   * This method should be more intelligent and take alignment requests
   * into consideration. The issue with this is that the block header 
   * which can be varied by subclasses can change the alignment of the 
   * cells. 
   *
   * @param bytes The number of bytes required to accommodate the
   * object to be allocated.
   * @return The size class capable of accomodating the allocation
   * request.  If the request is sufficiently large then
   * <code>LARGE_SIZE_CLASS</code> will be returned, indicating that
   * the request will not be satisfied by the freelist, but must be
   * dealt with explicitly as a large object.
   */
  protected static final int getSizeClass(int bytes)
    throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert((bytes > 0) && (bytes <= 8192));

    int sz1 = bytes - 1;

    if (BYTES_IN_ADDRESS == 32) { //32-bit
      if (COMPACT_SIZE_CLASSES) 
        return ((sz1 <= 31) ?      (sz1 >>  2): //    4 bytes apart
              (sz1 <=   63) ?  4 + (sz1 >>  3): //    8 bytes apart
              (sz1 <=   95) ?  8 + (sz1 >>  4): //   16 bytes apart
              (sz1 <=  223) ? 14 + (sz1 >>  6): //   64 bytes apart
              (sz1 <=  734) ? 17 + (sz1 >>  8): //  256 bytes apart
                              20 + (sz1 >> 10));// 1024 bytes apart
      else 
        return ((sz1 <=   63) ?    (sz1 >>  2): //    4 bytes apart
              (sz1 <=  127) ? 12 + (sz1 >>  4): //   16 bytes apart
              (sz1 <=  255) ? 16 + (sz1 >>  5): //   32 bytes apart
              (sz1 <=  511) ? 20 + (sz1 >>  6): //   64 bytes apart
              (sz1 <= 2047) ? 26 + (sz1 >>  8): //  256 bytes apart
                              32 + (sz1 >> 10));// 1024 bytes apart
    } else { //64-bit 
      if (COMPACT_SIZE_CLASSES) 
        return ((sz1 <= 95) ?      (sz1 >>  3): //    8 bytes apart
              (sz1 <=  127) ?  6 + (sz1 >>  4): //   16 bytes apart
              (sz1 <=  191) ? 10 + (sz1 >>  5): //   32 bytes apart
              (sz1 <=  383) ? 13 + (sz1 >>  6): //   64 bytes apart
              (sz1 <=  511) ? 16 + (sz1 >>  7): //  128 bytes apart
              (sz1 <= 1023) ? 19 + (sz1 >>  9): //  512 bytes apart
                              20 + (sz1 >> 10));// 1024 bytes apart
      else 
        return ((sz1 <= 111) ?     (sz1 >>  3): //    8 bytes apart
              (sz1 <=  223) ?  7 + (sz1 >>  4): //   16 bytes apart
              (sz1 <=  319) ? 14 + (sz1 >>  5): //   32 bytes apart
              (sz1 <=  575) ? 19 + (sz1 >>  6): //   64 bytes apart
              (sz1 <= 2047) ? 26 + (sz1 >>  8): //  256 bytes apart
                              32 + (sz1 >> 10));// 1024 bytes apart
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
  protected static final int getBaseCellSize(int sc) 
    throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert((sc >= 0) && (sc < SIZE_CLASSES));

    if (BYTES_IN_ADDRESS == 32) { //32-bit
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
    } else { //64-bit
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

  /****************************************************************************
   *
   * Preserving (saving & restoring) free lists
   *
   */

  abstract protected boolean preserveFreeList();
  abstract protected Address advanceToBlock(Address block, int sizeClass);

  /**
   * Zero all of the current free list pointers, and refresh the
   * <code>currentBlock</code> values, so instead of the free list
   * pointing to free cells, it points to the block containing the
   * free cells.  Then the free lists for each cell can be
   * reestablished during GC.  If the free lists are being preserved
   * on a per-block basis (eager mark-sweep and reference counting),
   * then free lists are remembered for each block.
   */
  public final void flushFreeLists() {
    for (int sizeClass = 0; sizeClass < SIZE_CLASSES; sizeClass++)
      if (!currentBlock.get(sizeClass).isZero()) {
        Address block = currentBlock.get(sizeClass);
        Address cell = freeList.get(sizeClass);
        if (preserveFreeList()) setFreeList(block, cell);
        currentBlock.set(sizeClass, Address.zero());
        freeList.set(sizeClass, Address.zero());
      }
  }

  /**
   * Retrieve free list pointers from the first blocks in the free
   * list. 
   */
  public final void restoreFreeLists() {
    for (int sizeClass = 0; sizeClass < SIZE_CLASSES; sizeClass++) {
      Address block = firstBlock.get(sizeClass);
      currentBlock.set(sizeClass, block);
      if (block.isZero()) {
        freeList.set(sizeClass, Address.zero());
      } else if (preserveFreeList()) {
        freeList.set(sizeClass, getFreeList(block));
      } else
        freeList.set(sizeClass, advanceToBlock(block, sizeClass));
    }
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
  protected final Address getFreeList(Address block) 
    throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(preserveFreeList());
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
  protected final void setFreeList(Address block, Address cell)
    throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(preserveFreeList());
    BlockAllocator.setFreeListMeta(block, cell);
  }


  /****************************************************************************
   *
   * Collection
   */

  /**
   * Sweep all blocks for free objects. 
   */
  protected final void sweepBlocks() {
    for (int sizeClass = 0; sizeClass < SIZE_CLASSES; sizeClass++) {
      Address block = firstBlock.get(sizeClass);
      clearBucketList();
      Extent blockSize = Extent.fromIntSignExtend(BlockAllocator.blockSize(blockSizeClass[sizeClass]));
      while (!block.isZero()) {
        /* first check to see if block is completely free and if possible
         * free the entire block */
        Address next = BlockAllocator.getNextBlock(block);
        int liveness = getLiveness(block, blockSize, SORT_FREE_BLOCKS);
        if (liveness == 0)
          freeBlock(block, sizeClass);
        else if (!LAZY_SWEEP)
          setFreeList(block, makeFreeListFromLiveBits(block, sizeClass));
        else if (SORT_FREE_BLOCKS)
          addToBlockBucket(block, liveness);
        block = next;
      }
      if (SORT_FREE_BLOCKS) reestablishBlockFreeList(sizeClass);
    }
  }

  /**
   * Add a block to a liveness bucket according to the specified
   * liveness.  This allows a cheap approximation to sorting the
   * blocks by liveness.
   *
   * @param block the block to be added to a bucket
   * @param liveness the liveness of the block that is to be added
   */
  private final void addToBlockBucket(Address block, int liveness) {
    int bucket = (liveness >= BLOCK_BUCKETS) ? BLOCK_BUCKETS - 1 : liveness;
    if (blockBucketHead.get(bucket).isZero())
      blockBucketHead.set(bucket, block);
    Address tail = blockBucketTail.get(bucket);
    BlockAllocator.setPrevBlock(block, tail);
    if (!tail.isZero()) BlockAllocator.setNextBlock(tail, block);
    blockBucketTail.set(bucket, block);
  }

  /**
   * Clear the list of block buckets prior to re-using it
   */
  private final void clearBucketList() {
    for (int bucket = 0; bucket < BLOCK_BUCKETS; bucket++) {
      blockBucketHead.set(bucket, Address.zero());
      blockBucketTail.set(bucket, Address.zero());
    }
  }
  
  /**
   * Reestablish a free block list based on the ordering of buckets,
   * taking the lists of blocks from the buckets and composing them
   * into a new free list of blocks for a given sizeclass.  The new
   * free list is built up in LIFO (stack) orderr, starting with the
   * blocks that will be used last, and finishing with the blocks that
   * should be used first by the allocator.
   *
   * @param sizeClass The sizeclass whose free block list is being
   * composed
   */
  private final void reestablishBlockFreeList(int sizeClass) {
    Address head = Address.zero();
    for (int bucket = 0; bucket < BLOCK_BUCKETS; bucket++)
      head = addToFreeBlockList(sizeClass, head, bucket);
    
    if (!head.isZero()) BlockAllocator.setPrevBlock(head, Address.zero());
    firstBlock.set(sizeClass, head);
  }
  
  /**
   * Add a bucket full of blocks to the front of the free block list
   * for a given class.  This allows the LIFO (stack order)
   * construction of new free lists using buckets.
   *
   * @param sizeClass The size class whose frelist is being built
   * @param head The current head of the free block list for this sizeclass
   * @param bucket The index of the bucket to be added to the front of
   * this free block list.
   */
  private final Address addToFreeBlockList(int sizeClass, Address head, 
                                           int bucket) throws InlinePragma {
    Address tail = blockBucketTail.get(bucket);
    if (!tail.isZero()) {
      if (head.isZero()) 
        lastBlock.set(sizeClass, tail);
      else
        BlockAllocator.setPrevBlock(head, tail);
      BlockAllocator.setNextBlock(tail, head);
      head = blockBucketHead.get(bucket);
    }
    return head;
  }

  /****************************************************************************
   *
   * Live bit manipulation
   */

  /**
   * Set the live bit for a given object
   *
   * @param object The object whose live bit is to be set.
   */
  public static final void liveObject(ObjectReference object)
    throws InlinePragma {
    liveAddress(ObjectModel.refToAddress(object), true);
  }

  /**
   * Set the live bit for a given object, without using
   * synchronization primitives---must only be used when contention
   * for live bit is strictly not possible
   *
   * @param object The object whose live bit is to be set.
   */
  public static final void unsyncLiveObject(ObjectReference object)
    throws InlinePragma {
    liveAddress(ObjectModel.refToAddress(object), false);
  }

  /**
   * Set the live bit for a given address
   *
   * @param address The address whose live bit is to be set.
   * @param atomic True if we want to perform this operation atomically
   */
  protected static final void liveAddress(Address address, boolean atomic)
    throws InlinePragma {
    Word oldValue, newValue;
    Address liveWord = getLiveWordAddress(address);
    Word mask = getMask(address, true);
    if (atomic) {
      do {
        oldValue = liveWord.prepareWord();
        newValue = oldValue.or(mask);
      } while(!liveWord.attempt(oldValue, newValue));
    } else 
      liveWord.store(liveWord.loadWord().or(mask));
  }

  /**
   * Clear the live bit for a given object
   *
   * @param object The object whose live bit is to be cleared.
   */
  protected static final void deadObject(ObjectReference object)
    throws InlinePragma {
    deadAddress(ObjectModel.refToAddress(object));
  }

  /**
   * Clear the live bit for a given address
   *
   * @param address The address whose live bit is to be cleared.
   */
  protected static final void deadAddress(Address address)
    throws InlinePragma {
    Word oldValue, newValue;
    Address liveWord = getLiveWordAddress(address);
    Word mask = getMask(address, false);
    liveWord.store(liveWord.loadWord().and(mask));
  }

  /**
   * Clear all live bits
   */
  public static final void zeroLiveBits(Address start, Address end) {
    Extent bytes = Extent.fromIntSignExtend(EmbeddedMetaData.BYTES_IN_REGION>>LOG_LIVE_COVERAGE);
    while (start.LT(end)) {
      Address metadata = EmbeddedMetaData.getMetaDataBase(start).add(SegregatedFreeList.META_DATA_OFFSET);
      Memory.zero(metadata, bytes);
      start = start.add(EmbeddedMetaData.BYTES_IN_REGION);
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
  protected static final Address alignToLiveStride(Address address) {
    return address.toWord().and(LIVE_WORD_STRIDE_MASK).toAddress();
  }

  /**
   * Walk through a set of live bitmaps for a block, checking which
   * are alive.  If <code>SORT_FREE_BLOCKS</code>and if all cells are
   * unused, return true.
   *
   * @param block The block
   * @param blockSize The size of the block
   * @param count If true return a count of all non zero words,
   * otherwise just return 1 if any live word exists
   * @return If <code>count</code> is true, return a count of all
   * non-zero words, otherwise return 1 if any live word exists, zero
   * otherwise.
   */
  private static final int getLiveness(Address block,  Extent blockSize,
                                       boolean count) throws InlinePragma {
    int liveWords = 0;
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(alignToLiveStride(block).EQ(block));
    Address cursor = getLiveWordAddress(block);
    Address sentinel = getLiveWordAddress(block.add(blockSize.sub(1)));
    while (cursor.LE(sentinel)) {
      Word live = cursor.loadWord();
      if (!live.isZero()) {
        if (count)
          liveWords++;
        else
          return 1;
      }
      cursor = cursor.add(BYTES_IN_WORD);
    }
    return liveWords;
  }

  /**
   * Use the live bits for a block to infer free cells and thus
   * construct a free list for the block.
   *
   * @param block The block to be processed
   * @param sizeClass The size class for the block
   * @return The head of the new free list
   */
  protected final Address makeFreeListFromLiveBits(Address block, 
                                                      int sizeClass)
    throws InlinePragma {
    Extent cellBytes = Extent.fromIntSignExtend(cellSize[sizeClass]);
    Address cellCursor = block.add(blockHeaderSize[sizeClass]);
    Extent blockSize = Extent.fromIntSignExtend(BlockAllocator.blockSize(blockSizeClass[sizeClass]));
    Address end = block.add(blockSize);
    Address nextFree = Address.zero();
    Address nextCellCursor = cellCursor.add(cellBytes);
    Address liveCursor = alignToLiveStride(cellCursor);
    Address liveWordCursor = getLiveWordAddress(liveCursor);
    boolean isLive = false;
    while (liveCursor.LT(end)) {
      Word live = liveWordCursor.loadWord();
      if (!live.isZero()) {
        for (int i=0; i < BITS_IN_WORD; i++) {
          if (!(live.and(Word.one().lsh(i)).isZero()))
            isLive = true;
          liveCursor = liveCursor.add(BYTES_PER_LIVE_BIT);
          if (liveCursor.GE(nextCellCursor)) {
            if (!isLive) {
              free(cellCursor, block, sizeClass, nextFree);
              nextFree = cellCursor;
            }
            cellCursor = nextCellCursor;
            nextCellCursor = nextCellCursor.add(cellBytes);
            isLive = false;
          }
        }
      } else {
        liveCursor = liveCursor.add(BYTES_PER_LIVE_WORD);
        while (liveCursor.GE(nextCellCursor)) {
          //      while (nextCellCursor.LT(liveCursor)) {
          if (!isLive) {
            free(cellCursor, block, sizeClass, nextFree);
            nextFree = cellCursor;
          }
          cellCursor = nextCellCursor;
          nextCellCursor = nextCellCursor.add(cellBytes);
          isLive = false;
        }
      }
      liveWordCursor = liveWordCursor.add(BYTES_IN_WORD);
    }
    return nextFree;
  }


  /**
   * Return the live word for a region including a given address
   *
   * @param address The address for which the live word is required
   * @return A word containing live bits for the given address.
   */
  protected static final Word getLiveBits(Address address) {
    return getLiveWordAddress(address).loadWord();
  }

  /**
   * Given an address, produce a bit mask for the live table
   *
   * @param address The address whose live bit mask is to be established
   * @param set True if we want the mask for <i>setting</i> the bit,
   * false if we want the mask for <i>clearing</i> the bit.
   * @return The appropriate bit mask for object for the live table for.
   */
  protected static final Word getMask(Address address, boolean set) 
    throws InlinePragma {
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
  protected static final Address getLiveWordAddress(Address address)
    throws InlinePragma {
    Address rtn = EmbeddedMetaData.getMetaDataBase(address);
    return rtn.add(META_DATA_OFFSET).add(EmbeddedMetaData.getMetaDataOffset(address, LOG_LIVE_COVERAGE, LOG_BYTES_IN_WORD));
  }

  /****************************************************************************
   *
   * Miscellaneous
   */
  public void show() {
  }
  private void freeListSanity() {
    for (int i = 0; i < freeList.length(); i++)
      freeListSanity(i);
  }
  private void freeListSanity(int sizeClass) {
    freeListSanity(freeList.get(sizeClass));
  }
  private void freeListSanity(Address cell) {
    while (!cell.isZero()) {
      Address next = getNextCell(cell);
      Log.write("("); Log.write(cell); Log.write("->"); Log.write(next); Log.write(")"); Log.flush();
      cell = next;
    }
    Log.writeln();
  }
}
