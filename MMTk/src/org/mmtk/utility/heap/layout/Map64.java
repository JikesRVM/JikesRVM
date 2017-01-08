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

import static org.mmtk.utility.heap.layout.VMLayoutConstants.*;

import org.mmtk.policy.Space;
import org.mmtk.utility.Constants;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.GenericFreeList;
import org.mmtk.utility.Log;
import org.mmtk.utility.RawMemoryFreeList;
import org.mmtk.utility.heap.FreeListPageResource;
import org.mmtk.utility.heap.SpaceDescriptor;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.AddressArray;
import org.vmmagic.unboxed.Extent;
import org.vmmagic.unboxed.Word;

@Uninterruptible
public final class Map64 extends Map {

  /**
   * The fraction of a space that is left after we allocate a map that maps the entire space.
   * <p>
   * Just an estimate so that we can slightly reduce the size of the map.  OK if it's too large, but
   * too small would be bad.
   */
  private static final double NON_MAP_FRACTION = 1 - 8.0 / 4096;

  private final int[] descriptorMap;
  private final Space[] spaceMap;
  private final AddressArray highWater;
  private final AddressArray baseAddress;

  private final FreeListPageResource[] flPageResources;
  private final RawMemoryFreeList[] flMap;

  private boolean finalized = false;

  Map64() {
    this.descriptorMap = new int[HeapParameters.MAX_SPACES];
    this.spaceMap = new Space[HeapParameters.MAX_SPACES];
    this.highWater = AddressArray.create(HeapParameters.MAX_SPACES);
    this.baseAddress = AddressArray.create(HeapParameters.MAX_SPACES);
    /* Avoid producing an Address that will blow up a 32-bit compiler */
    int logSpaceSize = VM.HEAP_LAYOUT_64BIT ? HeapParameters.LOG_SPACE_SIZE_64 : 0;
    for (int i = 0; i < HeapParameters.MAX_SPACES; i++) {
      Address base = Word.fromIntZeroExtend(i).lsh(logSpaceSize).toAddress();
      highWater.set(i, base);
      baseAddress.set(i, base);
    }
    this.flPageResources = new FreeListPageResource[HeapParameters.MAX_SPACES];
    this.flMap = new RawMemoryFreeList[HeapParameters.MAX_SPACES];
  }

  public static int spaceIndex(Address addr) {
    if (addr.GT(HEAP_END)) {
      return -1;
    }
    return addr.toWord().rshl(SPACE_SHIFT_64).toInt();
  }

  private boolean isSpaceStart(Address base) {
    return base.toWord().and(SPACE_MASK_64.not()).isZero();
  }

  @Override
  @Interruptible
  public GenericFreeList createFreeList(FreeListPageResource pr) {
    /* The size in units (pages) of the free list */
    int units = (SPACE_SIZE_64.toWord().rshl(Constants.LOG_BYTES_IN_PAGE).toInt());

    return createFreeList(pr, units, units);
  }

  @Override
  @Interruptible
  public GenericFreeList createFreeList(FreeListPageResource pr, int units, int grain) {
    Space space = pr.getSpace();
    Address start = space.getStart();
    Extent extent = space.getExtent();
    int index = spaceIndex(start);

    units = (int) (units * NON_MAP_FRACTION);
    Extent listExtent = Conversions.pagesToBytes(RawMemoryFreeList.sizeInPages(units, 1));

    if (VMLayoutConstants.VERBOSE_BUILD) {
      Log.write("Allocating free list for space ");
      Log.write(space.getName());
      Log.write(", start = ", start);
      Log.write(", extent = ", extent);
      Log.write(", units = ", units);
      Log.write("  listPages = ", RawMemoryFreeList.sizeInPages(units, 1));
      Log.writeln(", listExtent = ", listExtent);
    }
    RawMemoryFreeList list = new RawMemoryFreeList(start, start.plus(listExtent), units, grain);

    flPageResources[index] = pr;
    flMap[index] = list;

    /* Adjust the base address and highwater to account for the allocated chunks for the map */
    Address base = Conversions.chunkAlign(start.plus(listExtent), false);
    highWater.set(index, base);
    baseAddress.set(index, base);

    return list;
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
  @Override
  public void insert(Address start, Extent extent, int descriptor,
      Space space) {
    if (VM.VERIFY_ASSERTIONS) {
      if (!isSpaceStart(start)) {
        Log.write("Space ");
        Log.write(space.getName());
        Log.write(", extent ", extent);
        Log.write(" starts at ", start);
        Log.writeln(" which is not the start of a space in the 64-bit model");
        VM.assertions.fail("Space start address is wrong");
      }
      if (VM.HEAP_LAYOUT_64BIT && !extent.LE(SPACE_SIZE_64)) {
        Log.write("Space ");
        Log.write(space.getName());
        Log.write(", SPACE_SIZE=", SPACE_SIZE_64);
        Log.writeln(", extent=", extent);
      }
      VM.assertions._assert(VM.HEAP_LAYOUT_32BIT || extent.LE(SPACE_SIZE_64));
    }

    final int index = spaceIndex(start);
    descriptorMap[index] = descriptor;

    VM.barriers.objectArrayStoreNoGCBarrier(spaceMap, index, space);
  }

  /**
   * Allocate some number of contiguous chunks within a discontiguous region.  In a 64-bit
   * model, this involves extending a contiguous region, using 'head' as the address
   * of the highest chunk allocated.
   *
   * @param descriptor The descriptor for the space to which these chunks will be assigned
   * @param space The space to which these chunks will be assigned
   * @param chunks The number of chunks required
   * @param head The previous contiguous set of chunks for this space (to create a linked list of contiguous regions for each space)
   * @return The address of the assigned memory.  If the request fails we return Address.zero().
   */
  @Override
  public Address allocateContiguousChunks(int descriptor, Space space,
      int chunks, Address head) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(spaceIndex(space.getStart()) == SpaceDescriptor.getIndex(descriptor));

    int index = SpaceDescriptor.getIndex(descriptor);
    Address rtn = highWater.get(index);
    Extent extent = Extent.fromIntZeroExtend(chunks << LOG_BYTES_IN_CHUNK);
    highWater.set(index, rtn.plus(extent));

    /* Grow the free list to accommodate the new chunks */
    RawMemoryFreeList freeList = flMap[spaceIndex(space.getStart())];
    if (freeList != null) {
      freeList.growFreeList(Conversions.bytesToPages(extent));
      int basePage = Conversions.bytesToPages(rtn.diff(baseAddress.get(index)));
      for (int offset = 0; offset < chunks * PAGES_IN_CHUNK; offset += PAGES_IN_CHUNK) {
        freeList.setUncoalescable(basePage + offset);
        /* The 32-bit implementation requires that pages are returned allocated to the caller */
        freeList.alloc(PAGES_IN_CHUNK, basePage + offset);
      }
    }
    return rtn;
  }

  @Override
  public Address getNextContiguousRegion(Address start) {
    VM.assertions.fail("Discontiguous spaces are not supported in 64-bit mode");
    return Address.zero();
  }

  @Override
  public int getContiguousRegionChunks(Address start) {
    VM.assertions.fail("Discontiguous spaces are not supported in 64-bit mode");
    return 0;
  }

  @Override
  public Extent getContiguousRegionSize(Address start) {
    VM.assertions.fail("Discontiguous spaces are not supported in 64-bit mode");
    return Extent.zero();
  }

  @Override
  public void freeAllChunks(Address anyChunk) {
    VM.assertions.fail("Discontiguous spaces are not supported in 64-bit mode");
  }

  @Override
  public int freeContiguousChunks(Address start) {
    VM.assertions.fail("Discontiguous spaces are not supported in 64-bit mode");
    return 0;
  }

  /**
   * Finalize the space map, which requires initializing all the
   * raw memory free lists.
   */
  @Interruptible
  @Override
  public void finalizeStaticSpaceMap() {
    for (int pr = 0; pr < HeapParameters.MAX_SPACES; pr++) {
      if (flPageResources[pr] != null) {
        flPageResources[pr].resizeFreeList(Conversions.chunkAlign(flMap[pr].getLimit(), false));
      }
    }
    this.finalized = true;
  }

  @Interruptible
  @Override
  public void boot() {
    for (int pr = 0; pr < HeapParameters.MAX_SPACES; pr++) {
      if (flMap[pr] != null) {
        flMap[pr].growFreeList(0);
      }
    }
  }

  @Override
  public boolean isFinalized() {
    return this.finalized;
  }

  /**
   * @param pr the resource that wants to share the discontiguous region
   * @return The ordinal number for a free list space wishing to share a
   *   discontiguous region
   */
  @Override
  @Interruptible
  public int getDiscontigFreeListPROrdinal(FreeListPageResource pr) {
    VM.assertions.fail("Discontiguous spaces are not supported in 64-bit mode");
    return 0;
  }

  @Override
  public int getChunkConsumerCount() {
    VM.assertions.fail("Discontiguous spaces are not supported in 64-bit mode");
    return 0;
  }

  @Override
  public int getAvailableDiscontiguousChunks() {
    VM.assertions.fail("Discontiguous spaces are not supported in 64-bit mode");
    return 0;
  }

  /**
   * Return the space in which this address resides, or null if it is
   * outside the heap.
   *
   * @param address The address in question
   * @return The space in which the address resides
   */
  @Inline
  @Override
  public Space getSpaceForAddress(Address address) {
    if (spaceIndex(address) < 0 || spaceIndex(address) >= spaceMap.length) {
      return null;
    }
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(0 <= spaceIndex(address) && spaceIndex(address) < spaceMap.length);
    return spaceMap[spaceIndex(address)];
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
  @Override
  public int getDescriptorForAddress(Address object) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(spaceIndex(object) < descriptorMap.length);
    return descriptorMap[spaceIndex(object)];
  }

}
