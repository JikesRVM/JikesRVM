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
package org.mmtk.utility.heap.layout;

import org.mmtk.policy.Space;
import org.mmtk.utility.GenericFreeList;
import org.mmtk.utility.heap.FreeListPageResource;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;

@Uninterruptible
public abstract class Map {

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
  public abstract void insert(Address start, Extent extent, int descriptor, Space space);

  /**
   * Create a free-list for a discontiguous space.  Must only be called at build time.
   * @param pr The PageResource that requires the free-list
   * @return A new free list of the appropriate type for the concrete Map implementation.
   */
  @Interruptible
  public abstract GenericFreeList createFreeList(FreeListPageResource pr);

  /**
   * Create a free-list for a contiguous space.  Must only be called at build time.
   * @param pr The PageResource that requires the free-list
   * @param units Size in units (pages) of the space
   * @param grain Maximum allocation size
   * @return A new free list of the appropriate type for the concrete Map implementation.
   */
  @Interruptible
  public abstract GenericFreeList createFreeList(FreeListPageResource pr, int units,
      int grain);

  /**
   * Allocate some number of contiguous chunks within a discontiguous region.
   *
   * @param descriptor The descriptor for the space to which these chunks will be assigned
   * @param space The space to which these chunks will be assigned
   * @param chunks The number of chunks required
   * @param head The previous contiguous set of chunks for this space (to create a linked list of contiguous regions for each space)
   * @return The address of the assigned memory.  If the request fails we return Address.zero().
   */
  public abstract Address allocateContiguousChunks(int descriptor, Space space,
      int chunks, Address head);

  /**
   * Return the address of the next contiguous region associated with some discontiguous space by following the linked list for that space.
   *
   * @param start The current region (return the next region in the list)
   * @return Return the next contiguous region after start in the linked list of regions
   */
  public abstract Address getNextContiguousRegion(Address start);

  /**
   * Return the size of a contiguous region in chunks.
   *
   * @param start The start address of the region whose size is being requested
   * @return The size of the region in question
   */
  public abstract int getContiguousRegionChunks(Address start);

  /**
   * Return the size of a contiguous region in bytes.
   *
   * @param start The start address of the region whose size is being requested
   * @return The size of the region in question
   */
  public abstract Extent getContiguousRegionSize(Address start);

  /**
   * Free all chunks in a linked list of contiguous chunks.  This means starting
   * with one and then walking the chains of contiguous regions, freeing each.
   *
   * @param anyChunk Any chunk in the linked list of chunks to be freed
   */
  public abstract void freeAllChunks(Address anyChunk);

  /**
   * Free some set of contiguous chunks, given the chunk address
   *
   * @param start The start address of the first chunk in the series
   * @return The number of chunks which were contiguously allocated
   */
  public abstract int freeContiguousChunks(Address start);

  /**
   * Finalize the space map, establishing which virtual memory
   * is nailed down, and then placing the rest into a map to
   * be used by discontiguous spaces.
   * <p>
   * This should be called at build time, after all spaces have been
   * defined.
   */
  @Interruptible
  public abstract void finalizeStaticSpaceMap();

  /**
   * @return {@code true} iff {@link #finalizeStaticSpaceMap()} has been called.
   */
  public abstract boolean isFinalized();

  /**
   * Run-time initialization.  Performs any initialization that can only
   * take place once the run-time heap is available.
   * <p>
   * This should be called at boot time, before any allocation takes place.
   */
  @Interruptible
  public abstract void boot();

  /**
   * @param pr the resource that wants to share the discontiguous region
   * @return The ordinal number for a free list space wishing to share a discontiguous region
   */
  @Interruptible
  public abstract int getDiscontigFreeListPROrdinal(FreeListPageResource pr);

  /**
   * Return the total number of chunks available (unassigned) within the
   * range of virtual memory apportioned to discontiguous spaces.
   *
   * @return The number of available chunks for use by discontiguous spaces.
   */
  public abstract int getAvailableDiscontiguousChunks();

  /**
   * Return the total number of clients contending for chunks.   This
   * is useful when establishing conservative bounds on the number
   * of remaining chunks.
   *
   * @return The total number of clients who may contend for chunks.
   */
  public abstract int getChunkConsumerCount();

  /**
   * Return the space in which this address resides.
   *
   * @param address The address in question
   * @return The space in which the address resides
   */
  public abstract Space getSpaceForAddress(Address address);

  /**
   * Return the space descriptor for the space in which this object
   * resides.
   *
   * @param object The object in question
   * @return The space descriptor for the space in which the object
   * resides
   */
  public abstract int getDescriptorForAddress(Address object);

}
