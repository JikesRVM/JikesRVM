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

import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.AddressArray;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Word;

@Uninterruptible
public abstract class Mmapper {

  public abstract boolean objectIsMapped(ObjectReference object);

  public abstract boolean addressIsMapped(Address addr);

  // TODO: change int to long or Extent
  public abstract void protect(Address start, int pages);

  // TODO: change int to long or Extent
  public abstract void ensureMapped(Address start, int pages);

  // TODO: change int to long or Extent
  public abstract void markAsMapped(Address start, int bytes);

  public abstract void eagerlyMmapAllSpaces(AddressArray spaceMap);

  /*
   * Size of an mmap chunk.  We map and unmap chunks in units of this size.
   */
  static final int LOG_MMAP_CHUNK_BYTES = 20;
  static final int MMAP_CHUNK_BYTES = 1 << LOG_MMAP_CHUNK_BYTES;   // the granularity VMResource operates at
  static final int MMAP_CHUNK_MASK = MMAP_CHUNK_BYTES - 1;

  /**
   * Return a given address rounded up to an mmap chunk size
   *
   * @param addr The address to be aligned
   * @return The given address rounded up to an mmap chunk size
   */
  @Inline
  public static Address chunkAlignUp(Address addr) {
    return chunkAlignDown(addr.plus(MMAP_CHUNK_MASK));
  }

  /**
   * Return a given address rounded down to an mmap chunk size
   *
   * @param addr The address to be aligned
   * @return The given address rounded down to an mmap chunk size
   */
  @Inline
  public static Address chunkAlignDown(Address addr) {
    return addr.toWord().and(Word.fromIntSignExtend(MMAP_CHUNK_MASK).not()).toAddress();
  }

}
