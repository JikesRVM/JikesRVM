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
package org.mmtk.utility.alloc;

import org.mmtk.policy.Space;
import org.mmtk.utility.*;

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
 */
@Uninterruptible
public final class BlockAllocator implements Constants {
  /****************************************************************************
   *
   * Class variables
   */

  // block freelist
  public static final int LOG_MIN_BLOCK = 12; // 4K bytes
  public static final int LOG_MAX_BLOCK = 15; // 32K bytes
  public static final byte MAX_BLOCK_SIZE_CLASS = LOG_MAX_BLOCK - LOG_MIN_BLOCK;
  public static final int BLOCK_SIZE_CLASSES = MAX_BLOCK_SIZE_CLASS + 1;

  // metadata
  private static final Offset NEXT_OFFSET = Offset.zero();
  private static final Offset BMD_OFFSET = NEXT_OFFSET.plus(BYTES_IN_ADDRESS);
  private static final Offset CSC_OFFSET = BMD_OFFSET.plus(1);
  private static final Offset IU_OFFSET = CSC_OFFSET.plus(1);
  private static final Offset FL_META_OFFSET = IU_OFFSET.plus(BYTES_IN_SHORT);
  private static final byte BLOCK_SC_MASK = 0xf;             // lower 4 bits
  private static final int BLOCK_PAGE_OFFSET_SHIFT = 4;      // higher 4 bits
  private static final int MAX_BLOCK_PAGE_OFFSET = (1<<4)-1; // 4 bits
  private static final int LOG_BYTES_IN_BLOCK_META = LOG_BYTES_IN_ADDRESS + 2;
  private static final int LOG_BYTE_COVERAGE = LOG_MIN_BLOCK - LOG_BYTES_IN_BLOCK_META;

  public static final int META_DATA_BYTES_PER_REGION = 1 << (EmbeddedMetaData.LOG_BYTES_IN_REGION - LOG_BYTE_COVERAGE);
  public static final Extent META_DATA_EXTENT = Extent.fromIntSignExtend(META_DATA_BYTES_PER_REGION);

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
  public static Address alloc(Space space, int blockSizeClass) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert((blockSizeClass >= 0) && (blockSizeClass <= MAX_BLOCK_SIZE_CLASS));
    int pages = pagesForSizeClass(blockSizeClass);
    Address result = space.acquire(pages);
    if (!result.isZero()) {
      setBlkSizeMetaData(result, (byte) blockSizeClass);
    }
    return result;
  }

  /**
   * Free a block.  If the block is a sub-page block and the page is
   * not completely free, then the block is added to the free list.
   * Otherwise the block is returned to the virtual memory resource.
   *
   * @param block The address of the block to be freed
   */
  public static void free(Space space, Address block) {
    space.release(block);
  }

  /**
   * Return the size in bytes of a block of a given size class
   *
   * @param blockSizeClass The size class in question
   * @return The size in bytes of a block of this size class
   */
  @Inline
  public static int blockSize(int blockSizeClass) {
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
  private static int pagesForSizeClass(int blockSizeClass) {
    return 1 << (LOG_MIN_BLOCK + blockSizeClass - LOG_BYTES_IN_PAGE);
  }

  /****************************************************************************
   *
   * Block meta-data manipulation
   */

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
      VM.assertions._assert(block.EQ(Conversions.pageAlign(block)));
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
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(rtn >= 0 && rtn <= MAX_BLOCK_SIZE_CLASS);
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
  public static Address getBlkStart(Address address) {
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
  public static void setAllClientSizeClass(Address block, int blocksc, byte sc) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(block.EQ(Conversions.pageAlign(block)));
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
  public static byte getClientSizeClass(Address address) {
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
  public static void setFreeListMeta(Address address, Address value) {
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
  public static Address getFreeListMeta(Address address) {
    return getMetaAddress(address).plus(FL_META_OFFSET).loadAddress();
  }

  /**
   * Set the <i>prev</i> meta data field for a given address
   *
   * @param address The address of interest
   * @param prev The value to which this field is to be set
   */
  @Inline
  public static void setNext(Address address, Address prev) {
    getMetaAddress(address, NEXT_OFFSET).store(prev);
  }

  /**
   * Get the <i>prev</i> meta data field for a given address
   *
   * @param address The address of interest
   * @return The prev field for the block containing the given address
   */
  @Inline
  public static Address getNext(Address address) {
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
  private static Address getMetaAddress(Address address, Offset offset) {
    return EmbeddedMetaData.getMetaDataBase(address).plus(
           EmbeddedMetaData.getMetaDataOffset(address, LOG_BYTE_COVERAGE, LOG_BYTES_IN_BLOCK_META)).plus(offset);
  }

  /****************************************************************************
   *
   * Block marking
   */

  /**
   * Mark the metadata for this block.
   *
   * @param ref
   */
  @Inline
  public static void markBlockMeta(ObjectReference ref) {
    getMetaAddress(VM.objectModel.refToAddress(ref)).plus(FL_META_OFFSET).store(Word.one());
  }

  /**
   * Mark the metadata for this block.
   *
   * @param block The block address
   */
  @Inline
  public static void markBlockMeta(Address block) {
    getMetaAddress(block).plus(FL_META_OFFSET).store(Word.one());
  }

  /**
   * Return true if the metadata for this block was set.
   *
   * @param block The block address
   * @return value of the meta data.
   */
  @Inline
  public static boolean checkBlockMeta(Address block) {
    return getMetaAddress(block).plus(FL_META_OFFSET).loadWord().EQ(Word.one());
  }

  /**
   * Clear the metadata for this block
   *
   * @param block The block address
   */
  @Inline
  public static void clearBlockMeta(Address block) {
    getMetaAddress(block).plus(FL_META_OFFSET).store(Word.zero());
  }
}
