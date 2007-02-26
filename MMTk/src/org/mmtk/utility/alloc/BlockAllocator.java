/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.utility.alloc;

import org.mmtk.policy.Space;
import org.mmtk.utility.*;
import org.mmtk.utility.Constants;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements "block" data structures of various sizes.<p>
 * 
 * Blocks are a non-shared (thread-local) coarse-grained unit of
 * storage. Blocks are available in power-of-two sizes.
 * 
 * Virtual memory space is taken from a VM resource, and pages
 * consumed by blocks are accounted for by a memory resource.
 * 
 * @author Steve Blackburn
 * @version $Revision$
 * @date $Date$
 */
@Uninterruptible public final class BlockAllocator implements Constants {
  public static final String Id = "$Id$";


  /****************************************************************************
   * 
   * Class variables
   */
  private static final boolean PARANOID = false;

  // block freelist
  public static final int LOG_MIN_BLOCK = 12; // 4K bytes
  public static final int LOG_BLOCKS_IN_REGION = EmbeddedMetaData.LOG_BYTES_IN_REGION - LOG_MIN_BLOCK;
  private static final int SUB_PAGE_SIZE_CLASS = LOG_BYTES_IN_PAGE - LOG_MIN_BLOCK - 1;
  public static final int LOG_MAX_BLOCK = 15; // 32K bytes
  private static final int BLOCK_SIZES = 1 + LOG_MAX_BLOCK - LOG_MIN_BLOCK;
  public static final int MAX_BLOCK_SIZE = 1 << LOG_MAX_BLOCK;
  private static final int MAX_BLOCK_PAGES = 1 << (LOG_MAX_BLOCK - LOG_BYTES_IN_PAGE);
  private static final byte MAX_BLOCK_SIZE_CLASS = LOG_MAX_BLOCK - LOG_MIN_BLOCK;
  private static final byte PAGE_BLOCK_SIZE_CLASS = (byte) (LOG_BYTES_IN_PAGE - LOG_MIN_BLOCK);
  public static final int BLOCK_SIZE_CLASSES = MAX_BLOCK_SIZE_CLASS + 1;
  private static final int FREE_LIST_BITS = 4;
  private static final int FREE_LIST_ENTRIES = 1 << (FREE_LIST_BITS * 2);

  // metadata
  private static final Offset PREV_OFFSET = Offset.zero();
  private static final Offset NEXT_OFFSET = PREV_OFFSET.plus(BYTES_IN_ADDRESS);
  private static final Offset BMD_OFFSET = NEXT_OFFSET.plus(BYTES_IN_ADDRESS);
  private static final Offset CSC_OFFSET = BMD_OFFSET.plus(1);
  private static final Offset IU_OFFSET = CSC_OFFSET.plus(1);
  private static final Offset FL_META_OFFSET = IU_OFFSET.plus(BYTES_IN_SHORT);
  private static final byte BLOCK_SC_MASK = 0xf;             // lower 4 bits
  private static final int BLOCK_PAGE_OFFSET_SHIFT = 4;      // higher 4 bits
  private static final int MAX_BLOCK_PAGE_OFFSET = (1<<4)-1; // 4 bits
  private static final int LOG_BYTES_IN_BLOCK_META = LOG_BYTES_IN_ADDRESS + 2;
  private static final int BLOCK_META_SIZE = 1 << LOG_BYTES_IN_BLOCK_META;
  private static final int LOG_BYTE_COVERAGE = LOG_MIN_BLOCK - LOG_BYTES_IN_BLOCK_META;
  public static final int META_DATA_BYTES_PER_REGION = 1 << (EmbeddedMetaData.LOG_BYTES_IN_REGION - LOG_BYTE_COVERAGE);

  public static final Extent META_DATA_EXTENT = Extent.fromIntSignExtend(META_DATA_BYTES_PER_REGION);

  /****************************************************************************
   * 
   * Instance variables
   */
  private Space space;
  private AddressArray freeList;
  private int[] freeBlocks;
  private int[] usedBlocks;

  /****************************************************************************
   * 
   * Initialization
   */
  BlockAllocator(Space space) {
    this.space = space;
    freeList = AddressArray.create(FREE_LIST_ENTRIES);
    freeBlocks = new int[FREE_LIST_ENTRIES];
    usedBlocks = new int[FREE_LIST_ENTRIES];
  }

  /****************************************************************************
   * 
   * Allocation & freeing
   */

  /**
   * Allocate a block, returning the address of the first usable byte
   * in the block.
   * 
   * @param blockSizeClass The size class for the block to be allocated.
   * @return The address of the first usable byte in the block, or
   * zero on failure.
   */
  final Address alloc(int blockSizeClass) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert((blockSizeClass >= 0) && 
                           (blockSizeClass <= MAX_BLOCK_SIZE_CLASS));
    Address rtn;
    if (PARANOID) sanity(false);
    if (blockSizeClass > SUB_PAGE_SIZE_CLASS ||
        (rtn = allocFast(blockSizeClass)).isZero())
      rtn = allocSlow(blockSizeClass);
    if (PARANOID) {
      if (!rtn.isZero()) {
        usedBlocks[blockSizeClass]++;
        freeBlocks[blockSizeClass]--;
      }
      sanity(false);
    }
    return rtn;
  }

  /**
   * Free a block.  If the block is a sub-page block and the page is
   * not completely free, then the block is added to the free list.
   * Otherwise the block is returned to the virtual memory resource.
   * 
   * @param block The address of the block to be freed
   */
  final void free(Address block) {
    if (PARANOID) sanity(false);
    int blockSizeClass = getBlkSizeClass(block);
    if (PARANOID) usedBlocks[blockSizeClass]--;
    if (blockSizeClass <= SUB_PAGE_SIZE_CLASS) {
      if (PARANOID) freeBlocks[blockSizeClass]++;
      /* sub-page block */
      if (decInUseCount(block) == 0) {
        /* now completely free, so take blocks off free list and free page */
        block = unlinkSubPageBlocks(block, blockSizeClass);
        space.release(block);
        if (PARANOID)
          freeBlocks[blockSizeClass] -= BYTES_IN_PAGE/blockSize(blockSizeClass);
        if (PARANOID) sanity(false);
      } else {
        /* add it to the appropriate free list */
        Address next = freeList.get(blockSizeClass);
        setNextBlock(block, next);
        if (!next.isZero())
          setPrevBlock(next, block);
        freeList.set(blockSizeClass, block);
      }
    } else // whole page or pages, so simply return it to the resource
      space.release(block);
    if (PARANOID) sanity(false);
  }

  /**
   * Take a block off the free list (if available), updating the free
   * list and inuse counters as necessary.  Return the first usable
   * byte of the block, or zero on failure.
   * 
   * @param blockSizeClass The size class for the block to be allocated.
   * @return The address of the first usable byte in the block, or
   * zero on failure.
   */
  @Inline
  private Address allocFast(int blockSizeClass) {
    Address rtn;
    if (!(rtn = freeList.get(blockSizeClass)).isZero()) {
      // successfully got a block off the free list
      Address next = getNextBlock(rtn);
      incInUseCount(rtn);
      freeList.set(blockSizeClass, next);
      if (!next.isZero())
        setPrevBlock(next, Address.zero());
    }
    return rtn;
  }

  /**
   * Acquire new pages (if possible), and carve them up for use as
   * blocks.  Return the address of the first usable byte of the first
   * block on success, or zero on failure.  If successful, any
   * subblocks will be added to the free list.
   * 
   * @param blockSizeClass The size class for the block to be allocated.
   * @return The address of the first usable byte in the block, or
   * zero on failure.
   */
  private Address allocSlow(int blockSizeClass) {
    Address rtn;
    int pages = pagesForSizeClass(blockSizeClass);
    if (!(rtn = space.acquire(pages)).isZero()) {
      setBlkSizeMetaData(rtn, (byte) blockSizeClass);
      if (blockSizeClass <= SUB_PAGE_SIZE_CLASS)
        populatePage(rtn, blockSizeClass);
      if (PARANOID) {
        if (blockSizeClass <= SUB_PAGE_SIZE_CLASS)
          freeBlocks[blockSizeClass] += BYTES_IN_PAGE/blockSize(blockSizeClass);
        else
          freeBlocks[blockSizeClass]++;
      }

    }
    return rtn;
  }

  /**
   * Given a page of sub-page blocks, initialize
   * <code>inUseCount</code> and <code>blockSizeClass</code> for that page
   * and put all blocks except the first on the free list (the first
   * will implicitly be used immediately).
   *
   * @param start The start of the page
   * @param blockSizeClass The sizeClass of the blocks to go in this page.
   */
  private void populatePage(Address start, int blockSizeClass) {
    resetInUseCount(start);
    int blockSize = blockSize(blockSizeClass);
    Address end = start.plus(BYTES_IN_PAGE);
    Address block = start.plus(blockSize);
    Address next = freeList.get(blockSizeClass);
    while (block.LT(end)) {
      setNextBlock(block, next);
      if (!next.isZero())
        setPrevBlock(next, block);
      next = block;
      block = block.plus(blockSize);
    }
    freeList.set(blockSizeClass, next);
    setPrevBlock(next, Address.zero());
  }

  /**
   * Return the size in bytes of a block of a given size class
   * 
   * @param blockSizeClass The size class in question
   * @return The size in bytes of a block of this size class
   */
  @Inline
  public static final int blockSize(int blockSizeClass) {
    return 1 << (LOG_MIN_BLOCK + blockSizeClass);
  }

  /**
   * Return the number of pages required when allocating space for
   *         this size class.
   *
   * @param blockSizeClass The size class in question
   * @return The number of pages required when allocating a block (or
   * blocks) of this size class.
   */
  @Inline
  private static final int pagesForSizeClass(int blockSizeClass) {
    if (blockSizeClass <= SUB_PAGE_SIZE_CLASS)
      return 1;
    else
      return 1 << (LOG_MIN_BLOCK + blockSizeClass - LOG_BYTES_IN_PAGE);
  }

  /****************************************************************************
   * 
   * Block meta-data manipulation
   */

  /**
   * Reset the inuse count for a set of blocks to 1.
   * 
   * @param block One of the one or more blocks in the set whose count
   * is to be reset to 1.
   */
  @Inline
  private static void resetInUseCount(Address block) { 
    setInUseCount(block, (short) 1);
  }

  /**
   * Increment the inuse count for a set of blocks
   * 
   * @param block One of the blocks in the set whose count is being
   * incremented.
   */
  @Inline
  private static void incInUseCount(Address block) { 
    setInUseCount(block, (short) (getInUseCount(block) + 1));
  }

  /**
   * Decrement the inuse count for a set of blocks and return the
   * post-decrement value. This says how many blocks are in use on a
   * given page.
   * 
   * @param block One of one or more blocks in the set whose count is being
   *          decremented.
   * @return The post-decrement count for this set of blocks
   */
  @Inline
  private static int decInUseCount(Address block) { 
    short value = (short) (getInUseCount(block) - 1);
    setInUseCount(block, value);
    return value;
  }

  /**
   * Set the <i>in use</i> meta data field for a given page.  This
   * says how many blocks are in use on a given page.
   * 
   * @param address The address of interest
   * @param iu The value to which this field is to be set
   */
  @Inline
  private static void setInUseCount(Address address, short iu) {
    address = Conversions.pageAlign(address);
    getMetaAddress(address).store(iu, IU_OFFSET);
  }

  /**
   * Get the block's <i>in use</i> meta data field for a given page.
   * This says how many blocks are in use on a given page.
   * 
   * @param address The address of interest
   * @return The inuse field for the block containing the given address
   */
  @Inline
  private static short getInUseCount(Address address) {
    address = Conversions.pageAlign(address);
    return getMetaAddress(address).loadShort(IU_OFFSET);
  }

  /**
   * Set the <i>block size class</i> meta data field for a given
   * address (all blocks on a given page are homogeneous with respect
   * to block size class).
   * 
   * @param block The address of interest
   * @param sc The value to which this field is to be set
   */
  @Inline
  private static void setBlkSizeMetaData(Address block, byte sc) {
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(block == Conversions.pageAlign(block));
      VM.assertions._assert(pagesForSizeClass(sc) - 1  <= MAX_BLOCK_PAGE_OFFSET);
    }
    Address address = block;
    for (int i = 0; i < pagesForSizeClass(sc); i++) {
      byte value = (byte) ((i << BLOCK_PAGE_OFFSET_SHIFT) | sc);
      getMetaAddress(address).store(value, BMD_OFFSET);
      if (VM.VERIFY_ASSERTIONS) {
        VM.assertions._assert(getBlkStart(address).EQ(block));
        VM.assertions._assert(getBlkSizeClass(address) == sc);
      }
      address = address.plus(1<<VM.LOG_BYTES_IN_PAGE);
    }
  }

  /**
   * Get the <i>block size class</i> meta data field for a given page
   * (all blocks on a given page are homogeneous with respect to block
   * size class).
   * 
   * @param address The address of interest
   * @return The size class field for the block containing the given address
   */
  @Inline
  private static byte getBlkSizeClass(Address address) {
    address = Conversions.pageAlign(address);
    byte rtn = (byte) (getMetaAddress(address).loadByte(BMD_OFFSET) & BLOCK_SC_MASK);
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(rtn >= 0 && rtn <= (byte) MAX_BLOCK_SIZE_CLASS);
    return rtn;
  }

  /**
   * Get the <i>address of the start of a block size class</i> a given page
   * within the block.
   * 
   * @param address The address of interest
   * @return The address of the block containing the address
   */
  @Inline
  public static final Address getBlkStart(Address address) { 
    address = Conversions.pageAlign(address);
    byte offset = (byte) (getMetaAddress(address).loadByte(BMD_OFFSET) >>> BLOCK_PAGE_OFFSET_SHIFT);
    return address.minus(offset<<LOG_BYTES_IN_PAGE);
  }
  
  /**
   * Set the <i>client size class</i> meta data field for a given
   * address (all blocks on a given page are homogeneous with respect
   * to block size class).
   * 
   * @param block The address of interest
   * @param sc The value to which this field is to be set
   */
  @Inline
  public static final void setAllClientSizeClass(Address block, int blocksc, byte sc) { 
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(block == Conversions.pageAlign(block));
    Address address = block;
    for (int i = 0; i < pagesForSizeClass(blocksc); i++) {
      getMetaAddress(address).store(sc, CSC_OFFSET);
      address = address.plus(1<<VM.LOG_BYTES_IN_PAGE);
    }
  }

  /**
   * Get the <i>client size class</i> meta data field for a given page
   * (all blocks on a given page are homogeneous with respect to block
   * size class).
   * 
   * @param address The address of interest
   * @return The size class field for the block containing the given address
   */
  @Inline
  public static final byte getClientSizeClass(Address address) { 
    address = Conversions.pageAlign(address);
    byte rtn = getMetaAddress(address).loadByte(CSC_OFFSET);
    return rtn;
  }

  /**
   * Set the free list meta data field for a given address (this is
   * per-block meta data that is stored along with the block metadata
   * but not used by the block allocator).
   *
   * @param address The address of interest
   * @param value The value to which this field is to be set
   */
  @Inline
  public static final void setFreeListMeta(Address address, 
                                           Address value) { 
    getMetaAddress(address).plus(FL_META_OFFSET).store(value);
  }

  /**
   * Get the free list meta data field for a given address (this is
   * per-block meta data that is stored along with the block metadata
   * but not used by the block allocator).
   *
   * @param address The address of interest
   * @return The free list meta data field for the block containing
   * the given address
   */
  @Inline
  public static final Address getFreeListMeta(Address address) { 
    return getMetaAddress(address).plus(FL_META_OFFSET).loadAddress();
  }

  /**
   * Remove a block from the doubly linked list of blocks
   * 
   * @param block The block to be removed from the doubly linked list
   */
  @Inline
  static final void unlinkBlock(Address block) { 
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!block.isZero());
    Address next = getNextBlock(block);
    Address prev = getPrevBlock(block);
    // if (VM.VERIFY_ASSERTIONS) {
    setNextBlock(block, Address.zero());
    setPrevBlock(block, Address.zero());
    // }
    if (!prev.isZero()) setNextBlock(prev, next);
    if (!next.isZero()) setPrevBlock(next, prev);
  }

  /**
   * Add a block to the doubly linked list of blocks
   * 
   * @param block The block to be added
   * @param prev The block that is to preceed the new block
   */
  @Inline
  static final void linkedListInsert(Address block, Address prev) { 
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!block.isZero());
    Address next;
    if (!prev.isZero()) {
      next = getNextBlock(prev);
      setNextBlock(prev, block);
    } else
      next = Address.zero();
    setPrevBlock(block, prev);
    setNextBlock(block, next);
    if (!next.isZero()) setPrevBlock(next, block);
  }

  /**
   * Set the <i>prev</i> meta data field for a given address
   * 
   * @param address The address of interest
   * @param prev The value to which this field is to be set
   */
  @Inline
  static final void setPrevBlock(Address address, Address prev) { 
    getMetaAddress(address, PREV_OFFSET).store(prev);
  }

  /**
   * Get the <i>prev</i> meta data field for a given address
   * 
   * @param address The address of interest
   * @return The prev field for the block containing the given address
   */
  @Inline
  static final Address getPrevBlock(Address address) { 
    return getMetaAddress(address, PREV_OFFSET).loadAddress();
  }

  /**
   * Set the <i>next</i> meta data field for a given address
   * 
   * @param address The address of interest
   * @param next The value to which this field is to be set
   */
  @Inline
  static final void setNextBlock(Address address, Address next) { 
    getMetaAddress(address, NEXT_OFFSET).store(next);
  }

  /**
   * Get the <i>next</i> meta data field for a given address
   * 
   * @param address The address of interest
   * @return The next field for the block containing the given address
   */
  @Inline
  public static final Address getNextBlock(Address address) { 
    return getMetaAddress(address, NEXT_OFFSET).loadAddress();
  }

  /**
   * Get the address of some metadata given the address for which the
   * metadata is required and the offset into the metadata that is of
   * interest.
   * 
   * @param address The address for which the metadata is required
   * @return The address of the specified meta data
   */
  @Inline
  private static Address getMetaAddress(Address address) {
    return getMetaAddress(address, Offset.zero());
  }

  /**
   * Get the address of some metadata given the address for which the
   * metadata is required and the offset into the metadata that is of
   * interest.
   *
   * @param address The address for which the metadata is required
   * @param offset The offset (in bytes) into the metadata block (eg
   * for the prev pointer, or next pointer)
   * @return The address of the specified meta data
   */
  @Inline
  private static Address getMetaAddress(Address address,
                                              Offset offset) { 
    return EmbeddedMetaData.getMetaDataBase(address).plus(EmbeddedMetaData.getMetaDataOffset(address, LOG_BYTE_COVERAGE, LOG_BYTES_IN_BLOCK_META)).plus(offset);
  }

  /****************************************************************************
   * 
   * Free list manipulation
   */

  /**
   * Given some block, unlink all blocks on the page in which that
   * block resides and return the address of the containing page.
   * 
   * @param block The block whose page is to be unlinked
   * @param blockSizeClass The size class of the page
   * @return The address of the page containing <code>block</code>
   */
  private Address unlinkSubPageBlocks(Address block, int blockSizeClass) {
    Address start = Conversions.pageAlign(block);
    Address end = start.plus(BYTES_IN_PAGE);
    int blockSize = blockSize(blockSizeClass);
    Address head = freeList.get(blockSizeClass);
    block = start;
    while (block.LT(end)) {
      if (block.EQ(head)) {
        Address next = BlockAllocator.getNextBlock(block);
        freeList.set(blockSizeClass, next);
        head = next;
      }
      unlinkBlock(block);
      block = block.plus(blockSize);
    }
    return start;
  }

  /****************************************************************************
   * 
   * Sanity checks
   */

  
  /**
   * Mark the metadata for this block.
   * 
   * @param ref 
   */ 
  @Inline
  public static final void markBlockMeta(ObjectReference ref) { 
    getMetaAddress(VM.objectModel.refToAddress(ref)).plus(FL_META_OFFSET).store(Word.one());
  }
 
  /**
   * Return true if the metadata for this block was set.
   * 
   * @param block The block address
   * @return value of the meta data.
   */ 
  @Inline
  public static final boolean checkBlockMeta(Address block) { 
    return getMetaAddress(block).plus(FL_META_OFFSET).loadWord().EQ(Word.one());
  }
  
  /**
   * Clear the metadata for this block
   * 
   * @param block The block address
   */ 
  @Inline
  public static final void clearBlockMeta(Address block) { 
    getMetaAddress(block).plus(FL_META_OFFSET).store(Word.zero());
  }
  
  /**
   * Perform a basic sanity test, checking the contents of each of the
   * block free lists.
   * 
   * @param verbose If true then produce large amounts of debugging
   * output detailing the composition of the free lists.
   */
  private void sanity(boolean verbose) {
    for (int sc = 0; sc < BLOCK_SIZE_CLASSES; sc++) {
      int blocks = sanityTraverse(freeList.get(sc), Address.zero(), verbose);
      if (blocks != freeBlocks[sc]) {
        Log.write("------>"); Log.writeln(sc); Log.write(" "); Log.write(blocks); Log.write(" != "); Log.writeln(freeBlocks[sc]);
        sanityTraverse(freeList.get(sc), Address.zero(), true);
        Log.writeln();
        if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
      }
    }
    if (verbose) Log.writeln();
  }

  /**
   * Perform a sanity traversal of a linked list of blocks, checking
   * that the double links are sane, and returning the length of the
   * list.
   * 
   * @param block The first block in the list to be checked
   * @param prev The previous block in the list (possibly null)
   * @param verbose If true then produce large amounts of debugging
   * output detailing the composition of the list.
   * @return The length of the list
   */
  static final int sanityTraverse(Address block, Address prev,
                                   boolean verbose) {
    if (verbose) Log.write("[");
    boolean first = true;
    int blocks = 0;
    while (!block.isZero()) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(getPrevBlock(block).EQ(prev));
      blocks++;
      if (verbose) {
        if (!first)
          Log.write(" ");
        else
          first = false;
        Log.write(block);
      }
      block = getNextBlock(block);
      Log.flush();
    }
    if (verbose) Log.write("]");
    return blocks;
  }
}
