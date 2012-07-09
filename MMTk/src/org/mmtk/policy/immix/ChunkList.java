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
package org.mmtk.policy.immix;

import org.mmtk.plan.Plan;
import org.mmtk.policy.Space;
import org.mmtk.utility.Constants;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.AddressArray;

@Uninterruptible
public final class ChunkList implements Constants {
  private static final int LOG_PAGES_IN_CHUNK_MAP_BLOCK = 0;
  private static final int ENTRIES_IN_CHUNK_MAP_BLOCK = (BYTES_IN_PAGE<<LOG_PAGES_IN_CHUNK_MAP_BLOCK)>>LOG_BYTES_IN_ADDRESS;
  private static final int CHUNK_MAP_BLOCKS = 1<<4;
  private static final int MAX_ENTRIES_IN_CHUNK_MAP = ENTRIES_IN_CHUNK_MAP_BLOCK * CHUNK_MAP_BLOCKS;
  private AddressArray chunkMap =  AddressArray.create(CHUNK_MAP_BLOCKS);
  private int chunkMapLimit = -1;
  private int chunkMapCursor = -1;

  void reset() {
    chunkMapLimit = chunkMapCursor;
  }

  public Address getHeadChunk() {
    if (chunkMapLimit < 0)
      return Address.zero();
    else
      return getMapAddress(0).loadAddress();
  }

  public Address getTailChunk() {
    if (chunkMapLimit < 0)
      return Address.zero();
    else
      return getMapAddress(chunkMapLimit).loadAddress();
  }

  void addNewChunkToMap(Address chunk) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(Chunk.isAligned(chunk));
    if (chunkMapCursor == MAX_ENTRIES_IN_CHUNK_MAP - 1)
      consolidateMap();
    chunkMapCursor++;
    int index = getChunkIndex(chunkMapCursor);
    int map = getChunkMap(chunkMapCursor);
    if (map >= CHUNK_MAP_BLOCKS) {
      Space.printUsageMB();
      VM.assertions.fail("Overflow of chunk map!");
    }
    if (chunkMap.get(map).isZero()) {
      Address tmp = Plan.metaDataSpace.acquire(1<<LOG_PAGES_IN_CHUNK_MAP_BLOCK);
      if (tmp.isZero()) {
        Space.printUsageMB();
        VM.assertions.fail("Failed to allocate space for chunk map.  Is metadata virtual memory exhausted?");
      }
      chunkMap.set(map, tmp);
    }
    Address entry = chunkMap.get(map).plus(index<<LOG_BYTES_IN_ADDRESS);
    entry.store(chunk);
    Chunk.setMap(chunk, chunkMapCursor);
    if (VM.VERIFY_ASSERTIONS) checkMap();
  }

  void removeChunkFromMap(Address chunk) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(Chunk.isAligned(chunk));
    int entry = Chunk.getMap(chunk);
    getMapAddress(entry).store(Address.zero());  // zero it it
    Chunk.setMap(chunk, -entry);
    if (VM.VERIFY_ASSERTIONS) checkMap();
  }

  private int getChunkIndex(int entry) { return entry & (ENTRIES_IN_CHUNK_MAP_BLOCK - 1);}
  private int getChunkMap(int entry) {return entry & ~(ENTRIES_IN_CHUNK_MAP_BLOCK - 1);}

  private Address getMapAddress(int entry) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(entry >= 0);
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(entry <= chunkMapCursor);
    int index = getChunkIndex(entry);
    int map = getChunkMap(entry);
    return chunkMap.get(map).plus(index<<LOG_BYTES_IN_ADDRESS);
  }

  /**
   * A chunk iterator.  Return the next chunk in sequence, or null if the
   * next chunk is the same chunk (ie there is only one chunk in the iterator).
   *
   * @param chunk The chunk
   * @return The next chunk in the sequence, or null if next is chunk.
   */
  public Address nextChunk(Address chunk) {
    return nextChunk(chunk, chunk);
  }

  /**
   * A chunk iterator.  Return the next chunk in sequence, or {@code null} if the
   * next chunk is limit.
   *
   * @param chunk The chunk
   * @param limit The starting point (if next is equal to this, we're done)
   * @return The next chunk in the sequence, or {@code null} if next is limit.
   */
  private Address nextChunk(final Address chunk, final Address limit) {
    return nextChunk(chunk, Chunk.getMap(limit), 1);
  }

  /**
   * A chunk iterator.  Return the next chunk in sequence, strided
   * by stride steps, or {@code null} if the next chunk is start.
   *
   * @param chunk The chunk
   * @param start The point where this iterator started, which defines its end-point
   * @param stride The stride by which the iterator should be stepped
   * @return The next chunk in the sequence, or {@code null} if next is start.
   */
  public Address nextChunk(final Address chunk, final int start, final int stride) {
    if (VM.VERIFY_ASSERTIONS) checkMap();
    return nextChunk(Chunk.getMap(chunk), start, stride);
  }

  /**
   * A chunk iterator.  Return the next chunk in sequence, strided
   * by stride steps, or {@code null} if the next chunk is start.
   *
   * @param entry The entry we're currently up to
   * @param start The point where this iterator started, which defines its end-point
   * @param stride The stride by which the iterator should be stepped
   * @return The next chunk in the sequence, or {@code null} if next is start.
   */
  private Address nextChunk(int entry, final int start, final int stride) {
    if (VM.VERIFY_ASSERTIONS) checkMap();
    Address chunk;
    do {
      entry += stride;
      if (entry > chunkMapLimit) { entry = entry % stride; }
      chunk = getMapAddress(entry).loadAddress();
    } while (chunk.isZero() && entry != start);
    return entry == start ? Address.zero() : chunk;
  }

  public Address firstChunk(int ordinal, int stride) {
    if (ordinal > chunkMapCursor) return Address.zero();
    if (VM.VERIFY_ASSERTIONS) checkMap();
    Address chunk = getMapAddress(ordinal).loadAddress();
    return chunk.isZero() ? nextChunk(ordinal, ordinal, stride) : chunk;
  }

  private void checkMap() {
    VM.assertions._assert(chunkMapLimit <= chunkMapCursor);
    for (int entry = 0; entry <= chunkMapCursor; entry++) {
      Address chunk = getMapAddress(entry).loadAddress();
      if (!chunk.isZero())
        VM.assertions._assert(Chunk.getMap(chunk) == entry);
    }
  }

  public void consolidateMap() {
    int oldCursor = 0;
    int newCursor = -1;
    while (oldCursor <= chunkMapCursor) {
      Address chunk = getMapAddress(oldCursor).loadAddress();
      if (!chunk.isZero()) {
        getMapAddress(++newCursor).store(chunk);
        Chunk.setMap(chunk, newCursor);
      }
      oldCursor++;
    }
    chunkMapCursor = newCursor;
    chunkMapLimit = newCursor;
    if (VM.VERIFY_ASSERTIONS) checkMap();
  }
}
