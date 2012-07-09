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
package org.mmtk.utility.heap;

import org.mmtk.policy.Space;
import org.mmtk.utility.GenericFreeList;
import org.mmtk.utility.Log;
import org.mmtk.vm.Lock;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;
import org.vmmagic.unboxed.Word;

/**
 * This class manages the mapping of spaces to virtual memory ranges.<p>
 *
 */
@Uninterruptible
public class Map {

  /** set the map base address so that we have an unused {@code null} chunk at the bottome of the space for 64 bit */
  private static final Address MAP_BASE_ADDRESS = Space.BITS_IN_ADDRESS == 32 ? Address.zero() : Space.HEAP_START.minus(Space.BYTES_IN_CHUNK);

  /****************************************************************************
   *
   * Class variables
   */

  /**
   *
   */
  private static final int[] descriptorMap;
  private static final int[] prevLink;
  private static final int[] nextLink;
  private static final Space[] spaceMap;
  private static final GenericFreeList regionMap;
  public static final GenericFreeList globalPageMap;
  private static int sharedDiscontigFLCount = 0;
  private static final FreeListPageResource[] sharedFLMap;
  private static int totalAvailableDiscontiguousChunks = 0;

  private static final Lock lock = VM.newLock("Map lock");

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Class initializer. Create our two maps
   */
  static {
    descriptorMap = new int[Space.MAX_CHUNKS];
    prevLink = new int[Space.MAX_CHUNKS];
    nextLink = new int[Space.MAX_CHUNKS];
    spaceMap = new Space[Space.MAX_CHUNKS];
    regionMap = new GenericFreeList(Space.MAX_CHUNKS);
    globalPageMap = new GenericFreeList(1, 1, Space.MAX_SPACES);
    sharedFLMap = new FreeListPageResource[Space.MAX_SPACES];
    if (VM.VERIFY_ASSERTIONS)
        VM.assertions._assert(Space.BITS_IN_ADDRESS == Space.LOG_ADDRESS_SPACE ||
            Space.HEAP_END.diff(MAP_BASE_ADDRESS).toWord().rshl(Space.LOG_ADDRESS_SPACE).isZero());
  }

  /****************************************************************************
   *
   * Map accesses and insertion
   */

  /**
   * Insert a space and its descriptor into the map, associating it
   * with a particular address range.
   *
   * @param start The start address of the region to be associated
   * with this space.
   * @param extent The size of the region, in bytes
   * @param descriptor The descriptor for this space
   * @param space The space to be associated with this region
   */
  public static void insert(Address start, Extent extent, int descriptor,
      Space space) {
    Extent e = Extent.zero();
    while (e.LT(extent)) {
      int index = getChunkIndex(start.plus(e));
      if (descriptorMap[index] != 0) {
        Log.write("Conflicting virtual address request for space \"");
        Log.write(space.getName()); Log.write("\" at ");
        Log.writeln(start.plus(e));
        Space.printVMMap();
        VM.assertions.fail("exiting");
      }
      descriptorMap[index] = descriptor;
      VM.barriers.objectArrayStoreNoGCBarrier(spaceMap, index, space);
      e = e.plus(Space.BYTES_IN_CHUNK);
    }
  }

  /**
   * Allocate some number of contiguous chunks within a discontiguous region
   *
   * @param descriptor The descriptor for the space to which these chunks will be assigned
   * @param space The space to which these chunks will be assigned
   * @param chunks The number of chunks required
   * @param head The previous contgiuous set of chunks for this space (to create a linked list of contiguous regions for each space)
   * @return The address of the assigned memory.  This always succeeds.  If the request fails we fail right here.
   */
  public static Address allocateContiguousChunks(int descriptor, Space space, int chunks, Address head) {
    lock.acquire();
    int chunk = regionMap.alloc(chunks);
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(chunk != 0);
    if (chunk == -1) {
      Log.write("Unable to allocate virtual address space for space \"");
      Log.write(space.getName()); Log.write("\" for ");
      Log.write(chunks); Log.write(" chunks (");
      Log.write(chunks<<Space.LOG_BYTES_IN_CHUNK); Log.writeln(" bytes)");
      Space.printVMMap();
      VM.assertions.fail("exiting");
    }
    totalAvailableDiscontiguousChunks -= chunks;
    Address rtn = addressForChunkIndex(chunk);
    insert(rtn, Extent.fromIntZeroExtend(chunks<<Space.LOG_BYTES_IN_CHUNK), descriptor, space);
    if (head.isZero()) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(nextLink[chunk] == 0);
    } else {
      nextLink[chunk] = getChunkIndex(head);
      prevLink[getChunkIndex(head)] = chunk;
    }
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(prevLink[chunk] == 0);
    lock.release();
    return rtn;
  }

  /**
   * Return the address of the next contiguous region associated with some discontiguous space by following the linked list for that space.
   *
   * @param start The current region (return the next region in the list)
   * @return Return the next contiguous region after start in the linked list of regions
   */
  public static Address getNextContiguousRegion(Address start) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(start.EQ(Space.chunkAlign(start, true)));
    int chunk = getChunkIndex(start);
    return (chunk == 0) ? Address.zero() : (nextLink[chunk] == 0) ? Address.zero() : addressForChunkIndex(nextLink[chunk]);
  }

  /**
   * Return the size of a contiguous region in chunks.
   *
   * @param start The start address of the region whose size is being requested
   * @return The size of the region in question
   */
  public static int getContiguousRegionChunks(Address start) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(start.EQ(Space.chunkAlign(start, true)));
    int chunk = getChunkIndex(start);
    return regionMap.size(chunk);
  }

  /**
   * Return the size of a contiguous region in bytes.
   *
   * @param start The start address of the region whose size is being requested
   * @return The size of the region in question
   */
  public static Extent getContiguousRegionSize(Address start) {
    return Word.fromIntSignExtend(getContiguousRegionChunks(start)).lsh(Space.LOG_BYTES_IN_CHUNK).toExtent();
  }

  /**
   * Free all chunks in a linked list of contiguous chunks.  This means starting
   * with one and then walking the chains of contiguous regions, freeing each.
   *
   * @param anyChunk Any chunk in the linked list of chunks to be freed
   */
  public static void freeAllChunks(Address anyChunk) {
    lock.acquire();
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(anyChunk.EQ(Space.chunkAlign(anyChunk, true)));
    if (!anyChunk.isZero()) {
      int chunk = getChunkIndex(anyChunk);
      while (nextLink[chunk] != 0) {
        freeContiguousChunks(nextLink[chunk]);
      }
      while (prevLink[chunk] != 0) {
        freeContiguousChunks(prevLink[chunk]);
      }
      freeContiguousChunks(chunk);
    }
    lock.release();
  }

  /**
   * Free some set of contiguous chunks, given the chunk address
   *
   * @param start The start address of the first chunk in the series
   * @return The number of chunks which were contiguously allocated
   */
  public static int freeContiguousChunks(Address start) {
    lock.acquire();
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(start.EQ(Space.chunkAlign(start, true)));
    int rtn = freeContiguousChunks(getChunkIndex(start));
    lock.release();
    return rtn;
  }

  /**
   * Free some set of contiguous chunks, given the chunk index
   *
   * @param chunk The chunk index of the region to be freed
   * @return The number of chunks freed
   */
  private static int freeContiguousChunks(int chunk) {
    int chunks = regionMap.free(chunk);
    totalAvailableDiscontiguousChunks += chunks;
    int next = nextLink[chunk];
    int prev = prevLink[chunk];
    if (next != 0) prevLink[next] = prev;
    if (prev != 0) nextLink[prev] = next;
    nextLink[chunk] = prevLink[chunk] = 0;
    for (int offset = 0; offset < chunks; offset++) {
      descriptorMap[chunk + offset] = 0;
      VM.barriers.objectArrayStoreNoGCBarrier(spaceMap, chunk + offset, null);
    }
    return chunks;
  }

  /**
   * Finalize the space map, establishing which virtual memory
   * is nailed down, and then placing the rest into a map to
   * be used by discontiguous spaces.
   */
  @Interruptible
  public static void finalizeStaticSpaceMap() {
    /* establish bounds of discontiguous space */
    Address startAddress = Space.getDiscontigStart();
    int firstChunk = getChunkIndex(startAddress);
    int lastChunk = getChunkIndex(Space.getDiscontigEnd());
    int unavailStartChunk = lastChunk + 1;
    int trailingChunks = Space.MAX_CHUNKS - unavailStartChunk;
    int pages = (1 + lastChunk - firstChunk) * Space.PAGES_IN_CHUNK;
    globalPageMap.resizeFreeList(pages, pages);
    for (int pr = 0; pr < sharedDiscontigFLCount; pr++)
      sharedFLMap[pr].resizeFreeList(startAddress);

    /* set up the region map free list */
    int allocedChunk = regionMap.alloc(firstChunk);       // block out entire bottom of address range
    for (int chunkIndex = firstChunk; chunkIndex <= lastChunk; chunkIndex++)
      allocedChunk = regionMap.alloc(1);             // Tentatively allocate all usable chunks
    allocedChunk = regionMap.alloc(trailingChunks);  // block out entire top of address range
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(allocedChunk == unavailStartChunk);

    /* set up the global page map and place chunks on free list */
    int firstPage = 0;
    for (int chunkIndex = firstChunk; chunkIndex <= lastChunk; chunkIndex++) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(spaceMap[chunkIndex] == null);
      totalAvailableDiscontiguousChunks++;
      regionMap.free(chunkIndex);  // put this chunk on the free list
      globalPageMap.setUncoalescable(firstPage);
      int allocedPages = globalPageMap.alloc(Space.PAGES_IN_CHUNK); // populate the global page map
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(allocedPages == firstPage);
      firstPage += Space.PAGES_IN_CHUNK;
    }
  }

  /**
   * Return the ordinal number for some free list space wishing to share a discontiguous region.
   * @return The ordinal number for a free list space wishing to share a discontiguous region
   */
  @Interruptible
  public static int getDiscontigFreeListPROrdinal(FreeListPageResource pr) {
    sharedFLMap[sharedDiscontigFLCount] = pr;
    sharedDiscontigFLCount++;
    return sharedDiscontigFLCount;
  }

  /**
   * Return the total number of chunks available (unassigned) within the
   * range of virtual memory apportioned to discontiguous spaces.
   *
   * @return The number of available chunks for use by discontiguous spaces.
   */
  public static int getAvailableDiscontiguousChunks() {
    return totalAvailableDiscontiguousChunks;
  }

  /**
   * Return the total number of clients contending for chunks.   This
   * is useful when establishing conservative bounds on the number
   * of remaining chunks.
   *
   * @return The total number of clients who may contend for chunks.
   */
  public static int getChunkConsumerCount() {
    return sharedDiscontigFLCount;
  }

  /**
   * Return the space in which this address resides.
   *
   * @param address The address in question
   * @return The space in which the address resides
   */
  @Inline
  public static Space getSpaceForAddress(Address address) {
    int index = getChunkIndex(address);
    return spaceMap[index];
  }

  /**
   * Return the space descriptor for the space in which this object
   * resides.
   *
   * @param object The object in question
   * @return The space descriptor for the space in which the object
   * resides
   */
  @Inline
  public static int getDescriptorForAddress(Address object) {
    int index = getChunkIndex(object);
    return descriptorMap[index];
  }

  /**
   * Hash an address to a chunk (this is simply done via bit shifting)
   *
   * @param address The address to be hashed
   * @return The chunk number that this address hashes into
   */
  @Inline
  private static int getChunkIndex(Address address) {
    if (Space.BYTES_IN_ADDRESS == 8) {
      if (address.LT(Space.HEAP_START) || address.GE(Space.HEAP_END))
        return 0;
      else
        return address.diff(MAP_BASE_ADDRESS).toWord().rshl(Space.LOG_BYTES_IN_CHUNK).toInt();
    } else
      return address.toWord().rshl(Space.LOG_BYTES_IN_CHUNK).toInt();
  }
  @Inline
  private static Address addressForChunkIndex(int chunk) {
    if (Space.BYTES_IN_ADDRESS == 8) {
      if (chunk == 0)
        return Address.zero();
      else
        return MAP_BASE_ADDRESS.plus(Word.fromIntZeroExtend(chunk).lsh(Space.LOG_BYTES_IN_CHUNK).toExtent());
    } else
      return Word.fromIntZeroExtend(chunk).lsh(Space.LOG_BYTES_IN_CHUNK).toAddress();
  }
}
