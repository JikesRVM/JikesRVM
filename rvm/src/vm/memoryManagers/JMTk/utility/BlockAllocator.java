/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.utility.alloc;

import org.mmtk.utility.*;

import org.mmtk.vm.VM_Interface;
import org.mmtk.vm.Constants;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_AddressArray;
import com.ibm.JikesRVM.VM_Extent;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_WordArray;

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
public final class BlockAllocator implements Constants, VM_Uninterruptible {
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
  private static final VM_Extent PREV_OFFSET = VM_Extent.zero();
  private static final VM_Extent NEXT_OFFSET = PREV_OFFSET.add(BYTES_IN_ADDRESS);
  private static final VM_Extent SC_OFFSET = NEXT_OFFSET.add(BYTES_IN_ADDRESS);
  private static final VM_Extent IU_OFFSET = SC_OFFSET.add(BYTES_IN_SHORT);
  private static final VM_Extent FL_META_OFFSET = IU_OFFSET.add(BYTES_IN_SHORT);
  private static final int LOG_BYTES_IN_BLOCK_META = LOG_BYTES_IN_ADDRESS + 2;
  private static final int BLOCK_META_SIZE = 1<<LOG_BYTES_IN_BLOCK_META;
  private static final int LOG_BYTE_COVERAGE = LOG_MIN_BLOCK - LOG_BYTES_IN_BLOCK_META;
  public static final int META_DATA_BYTES_PER_REGION = 1<<(EmbeddedMetaData.LOG_BYTES_IN_REGION - LOG_BYTE_COVERAGE);

  public static final VM_Extent META_DATA_EXTENT = VM_Extent.fromInt(META_DATA_BYTES_PER_REGION);

  /****************************************************************************
   *
   * Instance variables
   */
  private FreeListVMResource vmResource;
  private MemoryResource memoryResource;
  private VM_AddressArray freeList;
  private int[] freeBlocks;
  private int[] usedBlocks;

  /****************************************************************************
   *
   * Initialization
   */
  BlockAllocator(FreeListVMResource vmr, MemoryResource mr) {
    vmResource = vmr;
    memoryResource = mr;
    freeList = VM_AddressArray.create(FREE_LIST_ENTRIES);
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
  final VM_Address alloc(int blockSizeClass) {
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert((blockSizeClass >= 0) && 
			   (blockSizeClass <= MAX_BLOCK_SIZE_CLASS));
    VM_Address rtn;
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
  final void free(VM_Address block) {
    if (PARANOID) sanity(false);
    int blockSizeClass = getBlkSizeClass(block);
    if (PARANOID) usedBlocks[blockSizeClass]--;
    if (blockSizeClass <= SUB_PAGE_SIZE_CLASS) {
      if (PARANOID) freeBlocks[blockSizeClass]++;
      /* sub-page block */
      if (decInUseCount(block) == 0) {
        /* now completely free, so take blocks off free list and free page */
	block = unlinkSubPageBlocks(block, blockSizeClass);
        vmResource.release(block, memoryResource);
        if (PARANOID)
          freeBlocks[blockSizeClass] -= BYTES_IN_PAGE/blockSize(blockSizeClass);
        if (PARANOID) sanity(false);
      } else {
        /* add it to the appropriate free list */
        VM_Address next = freeList.get(blockSizeClass);
        setNext(block, next);
        if (!next.isZero())
          setPrev(next, block);
        freeList.set(blockSizeClass, block);
      }
    } else // whole page or pages, so simply return it to the resource
      vmResource.release(block, memoryResource);
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
  private final VM_Address allocFast(int blockSizeClass)
    throws VM_PragmaInline {
    VM_Address rtn;
    if (!(rtn = freeList.get(blockSizeClass)).isZero()) {
      // successfully got a block off the free list
      VM_Address next = getNextBlock(rtn);
      incInUseCount(rtn);
      freeList.set(blockSizeClass, next);
      if (!next.isZero())
	setPrev(next, VM_Address.zero());
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
  private final VM_Address allocSlow(int blockSizeClass) {
    VM_Address rtn;
    int pages = pagesForSizeClass(blockSizeClass);
    if (!(rtn = vmResource.acquire(pages, memoryResource)).isZero()) {
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
  private final void populatePage(VM_Address start, int blockSizeClass) {
    resetInUseCount(start);
    int blockSize = blockSize(blockSizeClass);
    VM_Address end = start.add(BYTES_IN_PAGE);
    VM_Address block = start.add(blockSize);
    VM_Address next = freeList.get(blockSizeClass);
    while (block.LT(end)) {
      setNext(block, next);
      if (!next.isZero())
	setPrev(next, block);
      next = block;
      block = block.add(blockSize);
    }
    freeList.set(blockSizeClass, next);
    setPrev(next, VM_Address.zero());
  }

  /**
   * Return the size in bytes of a block of a given size class
   *
   * @param blockSizeClass The size class in question
   * @return The size in bytes of a block of this size class
   */
  public final static int blockSize(int blockSizeClass) throws VM_PragmaInline {
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
    throws VM_PragmaInline {
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
  private static void resetInUseCount(VM_Address block) 
    throws VM_PragmaInline {
    setInUseCount(block, (short) 1);
  }

  /**
   * Increment the inuse count for a set of blocks
   * 
   * @param block One of the blocks in the set whose count is being
   * incremented.
   */
  private static void incInUseCount(VM_Address block) throws VM_PragmaInline {
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
  private static int decInUseCount(VM_Address block) throws VM_PragmaInline {
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
  private static final void setInUseCount(VM_Address address, short iu) 
    throws VM_PragmaInline {
    address = Conversions.pageAlign(address);
    VM_Magic.setCharAtOffset(getMetaAddress(address), IU_OFFSET.toInt(), (char) iu);
  }
  
  /**
   * Get the block's <i>in use</i> meta data field for a given page.
   * This says how many blocks are in use on a given page.
   *
   * @param address The address of interest
   * @return The inuse field for the block containing the given address
   */
  private static final short getInUseCount(VM_Address address) 
    throws VM_PragmaInline {
    address = Conversions.pageAlign(address);
    return (short) VM_Magic.getCharAtOffset(getMetaAddress(address), IU_OFFSET.toInt());
  }
  
  /**
   * Set the <i>block size class</i> meta data field for a given
   * address (all blocks on a given page are homogeneous with respect
   * to block size class).
   *
   * @param address The address of interest
   * @param sc The value to which this field is to be set
   */
  private static final void setBlkSizeClass(VM_Address address, short sc) 
    throws VM_PragmaInline {
    address = Conversions.pageAlign(address);
    VM_Magic.setCharAtOffset(getMetaAddress(address), SC_OFFSET.toInt(), (char) sc);
  }
  
  /**
   * Get the <i>block size class</i> meta data field for a given page
   * (all blocks on a given page are homogeneous with respect to block
   * size class).
   *
   * @param address The address of interest
   * @return The size class field for the block containing the given address
   */
  private static final short getBlkSizeClass(VM_Address address) 
    throws VM_PragmaInline {
    address = Conversions.pageAlign(address);
    short rtn = (short) VM_Magic.getCharAtOffset(getMetaAddress(address), SC_OFFSET.toInt());
    if (VM_Interface.VerifyAssertions) 
      VM_Interface._assert(rtn >= 0 && rtn <= (short) MAX_BLOCK_SIZE_CLASS);
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
  public static final void setFreeListMeta(VM_Address address, 
					    VM_Address value) 
    throws VM_PragmaInline {
    VM_Magic.setMemoryAddress(getMetaAddress(address).add(FL_META_OFFSET), value);
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
  public static final VM_Address getFreeListMeta(VM_Address address) 
    throws VM_PragmaInline {
    return VM_Magic.getMemoryAddress(getMetaAddress(address).add(FL_META_OFFSET));  }
  
  /**
   * Remove a block from the doubly linked list of blocks
   *
   * @param block The block to be removed from the doubly linked list
   */
  static final void unlinkBlock(VM_Address block) throws VM_PragmaInline {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(!block.isZero());
    VM_Address next = getNextBlock(block);
    VM_Address prev = getPrevBlock(block);
    //    if (VM_Interface.VerifyAssertions) {
      setNext(block, VM_Address.zero());
      setPrev(block, VM_Address.zero());
      //    }
    if (!prev.isZero()) setNext(prev, next);
    if (!next.isZero()) setPrev(next, prev);
  }

  /**
   * Add a block to the doubly linked list of blocks
   *
   * @param block The block to be added
   * @param prev The block that is to preceed the new block
   */
  static final void linkedListInsert(VM_Address block, VM_Address prev) 
    throws VM_PragmaInline {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(!block.isZero());
    VM_Address next;
    if (!prev.isZero()) {
      next = getNextBlock(prev);
      setNext(prev, block);
    } else
      next = VM_Address.zero();
    setPrev(block, prev);
    setNext(block, next);
    if (!next.isZero()) setPrev(next, block);
  }
  
  /**
   * Set the <i>prev</i> meta data field for a given address
   *
   * @param address The address of interest
   * @param prev The value to which this field is to be set
   */
  private static final void setPrev(VM_Address address, VM_Address prev) 
    throws VM_PragmaInline {
    VM_Magic.setMemoryAddress(getMetaAddress(address, PREV_OFFSET), prev);
  }
  
  /**
   * Get the <i>prev</i> meta data field for a given address
   *
   * @param address The address of interest
   * @return The prev field for the block containing the given address
   */
  static final VM_Address getPrevBlock(VM_Address address) 
    throws VM_PragmaInline {
    return VM_Magic.getMemoryAddress(getMetaAddress(address, PREV_OFFSET));
  }
  
  /**
   * Set the <i>next</i> meta data field for a given address
   *
   * @param address The address of interest
   * @param next The value to which this field is to be set
   */
  private static final void setNext(VM_Address address, VM_Address next) 
    throws VM_PragmaInline {
    VM_Magic.setMemoryAddress(getMetaAddress(address, NEXT_OFFSET), next);
  }
  
  /**
   * Get the <i>next</i> meta data field for a given address
   *
   * @param address The address of interest
   * @return The next field for the block containing the given address
   */
  public static final VM_Address getNextBlock(VM_Address address) 
    throws VM_PragmaInline {
    return VM_Magic.getMemoryAddress(getMetaAddress(address, NEXT_OFFSET));
  }
  
  /**
   * Get the address of some metadata given the address for which the
   * metadata is required and the offset into the metadata that is of
   * interest.
   *
   * @param address The address for which the metadata is required
   * @return The address of the specified meta data
   */
  private static final VM_Address getMetaAddress(VM_Address address) 
    throws VM_PragmaInline {
    return getMetaAddress(address, VM_Extent.zero());
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
  private static final VM_Address getMetaAddress(VM_Address address,
						 VM_Extent offset) 
    throws VM_PragmaInline {
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
  private VM_Address unlinkSubPageBlocks(VM_Address block, int blockSizeClass) {
    VM_Address start = Conversions.pageAlign(block); 
    VM_Address end = start.add(BYTES_IN_PAGE);
    int blockSize = blockSize(blockSizeClass);
    VM_Address head = freeList.get(blockSizeClass);
    block = start;
    while (block.LT(end)) {
      if (block.EQ(head)) {
        VM_Address next = BlockAllocator.getNextBlock(block);
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
      int blocks = sanityTraverse(freeList.get(sc), VM_Address.zero(), verbose);
      if (blocks != freeBlocks[sc]) {
        Log.write("------>"); Log.writeln(sc); Log.write(" "); Log.write(blocks); Log.write(" != "); Log.writeln(freeBlocks[sc]);
        sanityTraverse(freeList.get(sc), VM_Address.zero(), true);
        Log.writeln();
        if (VM_Interface.VerifyAssertions) VM_Interface._assert(false);
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
  final static int sanityTraverse(VM_Address block, VM_Address prev, 
				   boolean verbose) {
    if (verbose) Log.write("[");
    boolean first = true;
    int blocks = 0;
    while (!block.isZero()) {
      if (VM_Interface.VerifyAssertions) 
	VM_Interface._assert(getPrevBlock(block).EQ(prev));
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
