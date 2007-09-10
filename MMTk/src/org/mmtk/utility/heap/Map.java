/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.utility.heap;

import org.mmtk.policy.Space;
import org.mmtk.utility.GenericFreeList;
import org.mmtk.utility.Log;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Inline;
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

  /****************************************************************************
   *
   * Class variables
   */
  private static final int[] descriptorMap;
  private static final int[] linkageMap;
  private static final Space[] spaceMap;
  private static final GenericFreeList regionMap;
  public static final GenericFreeList globalPageMap;
  private static int sharedSpaceCount = 0;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Class initializer. Create our two maps
   */
  static {
    descriptorMap = new int[Space.MAX_CHUNKS];
    linkageMap = new int[Space.MAX_CHUNKS];
    spaceMap = new Space[Space.MAX_CHUNKS];
    regionMap = new GenericFreeList(Space.MAX_CHUNKS);
    globalPageMap = new GenericFreeList(Space.AVAILABLE_PAGES, Space.AVAILABLE_PAGES, Space.MAX_SPACES);
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
      int index = hashAddress(start.plus(e));
      if (descriptorMap[index] != 0) {
        Log.write("Conflicting virtual address request for space \"");
        Log.write(space.getName()); Log.write("\" at ");
        Log.writeln(start.plus(e));
        Space.printVMMap();
        VM.assertions.fail("exiting");
      }
      descriptorMap[index] = descriptor;
      VM.barriers.setArrayNoBarrier(spaceMap, index, space);
      e = e.plus(Space.BYTES_IN_CHUNK);
    }
  }

  /**
   * Allocate some number of contiguous chunks within a discontiguous region
   *
   * @param descriptor The descriptor for the space to which these chunks will be assigned
   * @param space The space to which these chunks will be assigned
   * @param chunks The number of chunks required
   * @param previous The previous contgiuous set of chunks for this space (to create a linked list of contiguous regions for each space)
   * @return The address of the assigned memory.  This always succeeds.  If the request fails we fail right here.
   */
  public static Address allocateContiguousChunks(int descriptor, Space space, int chunks, Address previous) {
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
    Address rtn = reverseHashChunk(chunk);
    insert(rtn, Extent.fromIntZeroExtend(chunks<<Space.LOG_BYTES_IN_CHUNK), descriptor, space);
    linkageMap[chunk] = previous.isZero() ? 0 : hashAddress(previous);
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
    int chunk = hashAddress(start);
    return (chunk == 0) ? Address.zero() : (linkageMap[chunk] == 0) ? Address.zero() : reverseHashChunk(linkageMap[chunk]);
  }

  /**
   * Return the size of a contiguous region.
   *
   * @param start The start address of the region whose size is being requested
   * @return The size of the region in question
   */
  public static Extent getContiguousRegionSize(Address start) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(start.EQ(Space.chunkAlign(start, true)));
    int chunk = hashAddress(start);
    return Word.fromIntSignExtend(regionMap.size(chunk)).rshl(Space.LOG_BYTES_IN_CHUNK).toExtent();
  }

  /**
   * Free all chunks in a linked list of contiguous chunks.  This means starting
   * with lastChunk and then walking the chain of contiguous regions, freeing each.
   *
   * @param lastChunk The last chunk in the linked list of chunks to be freed
   */
  public static void freeAllChunks(Address lastChunk) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(lastChunk.EQ(Space.chunkAlign(lastChunk, true)));
    int chunk = hashAddress(lastChunk);
    while (chunk != 0) {
      int next = linkageMap[chunk];
      freeContiguousChunks(chunk);
      chunk = next;
    }
  }

  /**
   * Free some set of contiguous chunks, given the start address of those chunks
   *
   * @param start The start address of the contiguous chunks to be freed.
   */
  public static void freeContiguousChunks(Address start) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(start.EQ(Space.chunkAlign(start, true)));
    freeContiguousChunks(hashAddress(start));
  }

  /**
   * Free some set of contiguous chunks, given the chunk index
   *
   * @param chunk The chunk index of the region to be freed
   */
  private static void freeContiguousChunks(int chunk) {
    int chunks = regionMap.free(chunk);
    for (int offset = 0; offset < chunks; offset++) {
      descriptorMap[chunk + offset] = 0;
      VM.barriers.setArrayNoBarrier(spaceMap, chunk + offset, null);
      linkageMap[chunk + offset] = 0;
    }
  }

  /**
   * Finalize the space map, establishing which virtual memory
   * is nailed down, and then placing the rest into a map to
   * be used by discontiguous spaces.
   */
  public static void finalizeStaticSpaceMap() {
    int start = hashAddress(Space.AVAILABLE_START);
    int end = hashAddress(Space.AVAILABLE_END);
    regionMap.alloc(start);                  // block out entire bottom of address range
    for (int chunk = start; chunk < end; chunk++)
      regionMap.alloc(1);                    // tentitively allocate all usable chunks
    regionMap.alloc(Space.MAX_CHUNKS - end); // block out entire top of address range
    for (int chunk = start; chunk < end; chunk++)
      if (spaceMap[chunk] == null) regionMap.free(chunk);  // free all unpinned chunks
    for (int p = 0; p < Space.AVAILABLE_PAGES; p++) {
      int tmp = globalPageMap.alloc(1);      // pin down entire space
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(tmp == p);
    }
    Space.printVMMap();
  }

  /**
   * Return the ordinal number for some space wishing to share a discontiguous region.
   * @return The ordinal number for a space wishing to share a discontiguous region
   */
  public static int getSpaceMapOrdinal() {
    sharedSpaceCount++;
    return sharedSpaceCount;
  }

  /**
   * Return the space in which this address resides.
   *
   * @param address The address in question
   * @return The space in which the address resides
   */
  @Inline
  public static Space getSpaceForAddress(Address address) {
    int index = hashAddress(address);
    return (Space) VM.barriers.getArrayNoBarrier(spaceMap, index);
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
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!object.isZero());
    int index = hashAddress(object);
    return VM.barriers.getArrayNoBarrier(descriptorMap, index);
  }

  /**
   * Hash an address to a chunk (this is simply done via bit shifting)
   *
   * @param address The address to be hashed
   * @return The chunk number that this address hashes into
   */
  @Inline
  private static int hashAddress(Address address) {
    return address.toWord().rshl(Space.LOG_BYTES_IN_CHUNK).toInt();
  }
  @Inline
  private static Address reverseHashChunk(int chunk) {
    return Word.fromIntZeroExtend(chunk).lsh(Space.LOG_BYTES_IN_CHUNK).toAddress();
  }
}
