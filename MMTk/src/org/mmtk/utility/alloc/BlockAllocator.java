/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.utility.alloc;

import org.mmtk.policy.Space;
import org.mmtk.utility.*;
import org.mmtk.utility.heap.*;
import org.mmtk.vm.Assert;
import org.mmtk.vm.Constants;

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
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public final class BlockAllocator implements Constants, Uninterruptible {
  public final static String Id = "$Id$"; 


  /****************************************************************************
   *
   * Class variables
   */
  private static final boolean PARANOID = false;

  // block freelist
  public static final int LOG_MIN_BLOCK = 12;  // 4K bytes
  public static final int LOG_BLOCKS_IN_REGION = EmbeddedMetaData.LOG_BYTES_IN_REGION - LOG_MIN_BLOCK;
  private static final int SUB_PAGE_SIZE_CLASS = LOG_BYTES_IN_PAGE - LOG_MIN_BLOCK - 1;
  public static final int LOG_MAX_BLOCK = 15; // 32K bytes
  public static final int MAX_BLOCK_SIZE = 1<<LOG_MAX_BLOCK;
  private static final int MAX_BLOCK_PAGES = 1<<(LOG_MAX_BLOCK - LOG_BYTES_IN_PAGE);
  private static final byte MAX_BLOCK_SIZE_CLASS = LOG_MAX_BLOCK - LOG_MIN_BLOCK;
  private static final byte PAGE_BLOCK_SIZE_CLASS = LOG_BYTES_IN_PAGE - LOG_MIN_BLOCK;
  public static final int BLOCK_SIZE_CLASSES = MAX_BLOCK_SIZE_CLASS + 1;
  private static final int FREE_LIST_BITS = 4;
  private static final int FREE_LIST_ENTRIES = 1<<(FREE_LIST_BITS*2);

  // metadata
  private static final Offset PREV_OFFSET = Offset.zero();
  private static final Offset NEXT_OFFSET = PREV_OFFSET.add(BYTES_IN_ADDRESS);
  private static final Offset SC_OFFSET = NEXT_OFFSET.add(BYTES_IN_ADDRESS);
  private static final Offset IU_OFFSET = SC_OFFSET.add(BYTES_IN_SHORT);
  private static final Offset FL_META_OFFSET = IU_OFFSET.add(BYTES_IN_SHORT);
  private static final int LOG_BYTES_IN_BLOCK_META = LOG_BYTES_IN_ADDRESS + 2;
  private static final int BLOCK_META_SIZE = 1<<LOG_BYTES_IN_BLOCK_META;
  private static final int LOG_BYTE_COVERAGE = LOG_MIN_BLOCK - LOG_BYTES_IN_BLOCK_META;
  public static final int META_DATA_BYTES_PER_REGION = 1<<(EmbeddedMetaData.LOG_BYTES_IN_REGION - LOG_BYTE_COVERAGE);

  public static final Extent META_DATA_EXTENT = Extent.fromInt(META_DATA_BYTES_PER_REGION);

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
    if (Assert.VERIFY_ASSERTIONS) Assert._assert((blockSizeClass >= 0) && 
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
  private final Address allocFast(int blockSizeClass)
    throws InlinePragma {
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
  private final Address allocSlow(int blockSizeClass) {
    Address rtn;
    int pages = pagesForSizeClass(blockSizeClass);
    if (!(rtn = space.acquire(pages)).isZero()) {
      setBlkSizeClass(rtn, (short) blockSizeClass);
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
  private final void populatePage(Address start, int blockSizeClass) {
    resetInUseCount(start);
    int blockSize = blockSize(blockSizeClass);
    Address end = start.add(BYTES_IN_PAGE);
    Address block = start.add(blockSize);
    Address next = freeList.get(blockSizeClass);
    while (block.LT(end)) {
      setNextBlock(block, next);
      if (!next.isZero())
	setPrevBlock(next, block);
      next = block;
      block = block.add(blockSize);
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
  public final static int blockSize(int blockSizeClass) throws InlinePragma {
    return 1<<(LOG_MIN_BLOCK + blockSizeClass);
  }

  /**
   * Return the number of pages required when allocating space for
   * this size class.
   *
   * @param blockSizeClass The size class in question
   * @return The number of pages required when allocating a block (or
   * blocks) of this size class.
   */
  private final static int pagesForSizeClass(int blockSizeClass) 
    throws InlinePragma {
    if (blockSizeClass <= SUB_PAGE_SIZE_CLASS)
      return 1;
    else
      return 1<<(LOG_MIN_BLOCK + blockSizeClass - LOG_BYTES_IN_PAGE);
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
  private static void resetInUseCount(Address block) 
    throws InlinePragma {
    setInUseCount(block, (short) 1);
  }

  /**
   * Increment the inuse count for a set of blocks
   * 
   * @param block One of the blocks in the set whose count is being
   * incremented.
   */
  private static void incInUseCount(Address block) throws InlinePragma {
    setInUseCount(block, (short) (getInUseCount(block) + 1));
  }
  
  /**
   * Decrement the inuse count for a set of blocks and return the
   * post-decrement value. This says how many blocks are in use on a
   * given page.
   * 
   * @param block One of one or more blocks in the set whose count is being
   * decremented.
   * @return The post-decrement count for this set of blocks
   */
  private static int decInUseCount(Address block) throws InlinePragma {
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
  private static final void setInUseCount(Address address, short iu) 
    throws InlinePragma {
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
  private static final short getInUseCount(Address address) 
    throws InlinePragma {
    address = Conversions.pageAlign(address);
    return getMetaAddress(address).loadShort(IU_OFFSET);
  }
  
  /**
   * Set the <i>block size class</i> meta data field for a given
   * address (all blocks on a given page are homogeneous with respect
   * to block size class).
   *
   * @param address The address of interest
   * @param sc The value to which this field is to be set
   */
  private static final void setBlkSizeClass(Address address, short sc) 
    throws InlinePragma {
    address = Conversions.pageAlign(address);
    getMetaAddress(address).store(sc, SC_OFFSET);
  }
  
  /**
   * Get the <i>block size class</i> meta data field for a given page
   * (all blocks on a given page are homogeneous with respect to block
   * size class).
   *
   * @param address The address of interest
   * @return The size class field for the block containing the given address
   */
  private static final short getBlkSizeClass(Address address) 
    throws InlinePragma {
    address = Conversions.pageAlign(address);
    short rtn = getMetaAddress(address).loadShort(SC_OFFSET);
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(rtn >= 0 && rtn <= (short) MAX_BLOCK_SIZE_CLASS);
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
  public static final void setFreeListMeta(Address address, 
					   Address value) 
    throws InlinePragma {
    getMetaAddress(address).add(FL_META_OFFSET).store(value);
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
  public static final Address getFreeListMeta(Address address) 
    throws InlinePragma {
    return getMetaAddress(address).add(FL_META_OFFSET).loadAddress();
  }
  
  /**
   * Remove a block from the doubly linked list of blocks
   *
   * @param block The block to be removed from the doubly linked list
   */
  static final void unlinkBlock(Address block) throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(!block.isZero());
    Address next = getNextBlock(block);
    Address prev = getPrevBlock(block);
    //    if (Assert.VERIFY_ASSERTIONS) {
      setNextBlock(block, Address.zero());
      setPrevBlock(block, Address.zero());
      //    }
    if (!prev.isZero()) setNextBlock(prev, next);
    if (!next.isZero()) setPrevBlock(next, prev);
  }

  /**
   * Add a block to the doubly linked list of blocks
   *
   * @param block The block to be added
   * @param prev The block that is to preceed the new block
   */
  static final void linkedListInsert(Address block, Address prev) 
    throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(!block.isZero());
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
  static final void setPrevBlock(Address address, Address prev) 
    throws InlinePragma {
    getMetaAddress(address, PREV_OFFSET).store(prev);
  }
  
  /**
   * Get the <i>prev</i> meta data field for a given address
   *
   * @param address The address of interest
   * @return The prev field for the block containing the given address
   */
  static final Address getPrevBlock(Address address) 
    throws InlinePragma {
    return getMetaAddress(address, PREV_OFFSET).loadAddress();
  }
  
  /**
   * Set the <i>next</i> meta data field for a given address
   *
   * @param address The address of interest
   * @param next The value to which this field is to be set
   */
  static final void setNextBlock(Address address, Address next) 
    throws InlinePragma {
    getMetaAddress(address, NEXT_OFFSET).store(next);
  }
  
  /**
   * Get the <i>next</i> meta data field for a given address
   *
   * @param address The address of interest
   * @return The next field for the block containing the given address
   */
  public static final Address getNextBlock(Address address) 
    throws InlinePragma {
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
  private static final Address getMetaAddress(Address address) 
    throws InlinePragma {
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
  private static final Address getMetaAddress(Address address,
					      Offset offset) 
    throws InlinePragma {
    return EmbeddedMetaData.getMetaDataBase(address).add(EmbeddedMetaData.getMetaDataOffset(address, LOG_BYTE_COVERAGE, LOG_BYTES_IN_BLOCK_META)).add(offset);
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
    Address end = start.add(BYTES_IN_PAGE);
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
      block = block.add(blockSize);
    }
    return start;
  }

  /****************************************************************************
   *
   * Sanity checks
   */

  
  /**
   * Perform a basic sanity test, checking the contents of each of the
   * block free lists.
   *
   * @param verbose If true then produce large amounts of debugging
   * output detailing the composition of the free lists.
   */
  private final void sanity(boolean verbose) {
    for (int sc = 0; sc < BLOCK_SIZE_CLASSES; sc++) {
      int blocks = sanityTraverse(freeList.get(sc), Address.zero(), verbose);
      if (blocks != freeBlocks[sc]) {
        Log.write("------>"); Log.writeln(sc); Log.write(" "); Log.write(blocks); Log.write(" != "); Log.writeln(freeBlocks[sc]);
        sanityTraverse(freeList.get(sc), Address.zero(), true);
        Log.writeln();
        if (Assert.VERIFY_ASSERTIONS) Assert._assert(false);
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
  final static int sanityTraverse(Address block, Address prev, 
				   boolean verbose) {
    if (verbose) Log.write("[");
    boolean first = true;
    int blocks = 0;
    while (!block.isZero()) {
      if (Assert.VERIFY_ASSERTIONS) Assert._assert(getPrevBlock(block).EQ(prev));
      blocks++;
      if (verbose) {
	if (!first)
	  Log.write(" ");
	else
	  first = false;
	Log.write(block);
      }
      prev = block;
      block = getNextBlock(block);
      Log.flush();
    }
    if (verbose) Log.write("]");
    return blocks;
  }
}
