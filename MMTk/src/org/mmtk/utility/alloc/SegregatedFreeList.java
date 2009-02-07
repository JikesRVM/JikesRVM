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
package org.mmtk.utility.alloc;

import org.mmtk.policy.SegregatedFreeListSpace;
import org.mmtk.utility.*;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This abstract class implements the fast past for a segregated free list.
 */
@Uninterruptible
public abstract class SegregatedFreeList<S extends SegregatedFreeListSpace> extends Allocator implements Constants {

  /****************************************************************************
   *
   * Instance variables
   */

  /** The space */
  protected final S space;

  /** The current free lists for the size classes */
  protected final AddressArray freeList;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   *
   * @param space The space with which this allocator will be associated
   */
  public SegregatedFreeList(S space) {
    this.space = space;
    this.freeList = AddressArray.create(sizeClassCount());
  }

  /**
   * Return the space this allocator is currently bound to.
   *
   * @return The Space.
   */
  protected final S getSpace() {
    return this.space;
  }

  /****************************************************************************
   *
   * Allocation
   */

  /**
   * Allocate <code>bytes</code> contiguous bytes of zeroed memory.<p>
   *
   * This code implements the fast path, and on failure delegates to the slow path.
   *
   * @param bytes The size of the object to occupy this space, in bytes.
   * @param align The requested alignment.
   * @param offset The alignment offset.
   * @return The address of the first word or zero on failure
   */
  @Inline
  public final Address alloc(int bytes, int align, int offset) {
    int alignedBytes = getMaximumAlignedSize(bytes, align);
    int sizeClass = getSizeClass(alignedBytes);
    Address cell = freeList.get(sizeClass);
    if (!cell.isZero()) {
      freeList.set(sizeClass, cell.loadAddress());
      /* Clear the free list link */
      cell.store(Address.zero());
      if (alignedBytes != bytes) {
        /* Ensure aligned as requested. */
        cell = alignAllocation(cell, align, offset);
      }
      return cell;
    }
    return allocSlow(bytes, align, offset);
  }

  /**
   * The number of distinct size classes.
   *
   * NOTE: For optimal performance this call must be implemented in a way
   * it can be inlined and optimized within the allocation sequence.
   */
  @Inline
  private int sizeClassCount() {
    return SegregatedFreeListSpace.sizeClassCount();
  }

  /**
   * Get the size class for a given number of bytes.
   *
   * NOTE: For optimal performance this call must be implemented in a way
   * it can be inlined and optimized within the allocation sequence.
   *
   * @param bytes The number of bytes required to accommodate the object
   * @return The size class capable of accommodating the allocation request.
   */
  @Inline
  private int getSizeClass(int bytes) {
    return space.getSizeClass(bytes);
  }
}
