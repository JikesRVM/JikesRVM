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

  protected static final int MMAP_CHUNK_BYTES = 1 << VMLayoutConstants.LOG_MMAP_CHUNK_BYTES;   // the granularity VMResource operates at
  protected static final int MMAP_CHUNK_MASK = MMAP_CHUNK_BYTES - 1;

  /****************************************************************************
   * Generic mmap and protection functionality
   */

  /**
   * Given an address array describing the regions of virtual memory to be used
   * by MMTk, demand zero map all of them if they are not already mapped.
   *
   * @param spaceMap An address array containing a pairs of start and end
   * addresses for each of the regions to be mappe3d
   */
  public abstract void eagerlyMmapAllSpaces(AddressArray spaceMap);

  /**
   * Mark a number of pages as mapped, without making any
   * request to the operating system.  Used to mark pages
   * that the VM has already mapped.
   * @param start Address of the first page to be mapped
   * @param bytes Number of bytes to ensure mapped
   */
  public abstract void markAsMapped(Address start, int bytes);

  /**
   * Ensure that a range of pages is mmapped (or equivalent).  If the
   * pages are not yet mapped, demand-zero map them. Note that mapping
   * occurs at chunk granularity, not page granularity.<p>
   *
   * NOTE: There is a monotonicity assumption so that only updates require lock
   * acquisition.
   * TODO: Fix the above to support unmapping.
   *
   * @param start The start of the range to be mapped.
   * @param pages The size of the range to be mapped, in pages
   */
  public abstract void ensureMapped(Address start, int pages);

  /**
   * Is the page occupied by this object mapped ?
   * @param object Object in question
   * @return {@code true} if the page occupied by the start of the object
   * is mapped.
   */
  public abstract boolean objectIsMapped(ObjectReference object);

  /**
   * Is the page pointed to by this address mapped ?
   * @param addr Address in question
   * @return {@code true} if the page at the given address is mapped.
   */
  public abstract boolean addressIsMapped(Address addr);

  /**
   * Mark a number of pages as inaccessible.
   * @param start Address of the first page to be protected
   * @param pages Number of pages to be protected
   */
  public abstract void protect(Address start, int pages);

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
