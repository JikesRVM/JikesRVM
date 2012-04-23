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

import org.mmtk.policy.SegregatedFreeListSpace;
import org.mmtk.utility.*;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This abstract class implements a simple segregated free list.<p>
 *
 * See: Wilson, Johnstone, Neely and Boles "Dynamic Storage
 * Allocation: A Survey and Critical Review", IWMM 1995, for an
 * overview of free list allocation and the various implementation
 * strategies, including segregated free lists.<p>
 *
 * We maintain a number of size classes, each size class having a free
 * list of available objects of that size (the list may be empty).  We
 * call the storage elements "cells".  Cells reside within chunks of
 * memory called "blocks".  All cells in a given block are of the same
 * size (i.e. blocks are homogeneous with respect to size class).
 * Each block maintains its own free list (free cells within that
 * block).  For each size class a list of blocks is maintained, one of
 * which will serve the role of the current free list.  When the free
 * list on the current block is exhausted, the next block for that
 * size class becomes the current block and its free list is used.  If
 * there are no more blocks the a new block is allocated.<p>
 */
@Uninterruptible
public abstract class SegregatedFreeListLocal<S extends SegregatedFreeListSpace> extends SegregatedFreeList<S>
  implements Constants {

  /****************************************************************************
   *
   * Class variables
   */

  /****************************************************************************
   *
   * Instance variables
   */
  protected final AddressArray currentBlock;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   *
   * @param space The space with which this allocator will be associated
   */
  public SegregatedFreeListLocal(S space) {
    super(space);
    this.currentBlock = AddressArray.create(space.sizeClassCount());
  }

  /****************************************************************************
   *
   * Allocation
   */

  /**
   * Allocate <code>bytes</code> contiguous bytes of non-zeroed
   * memory.  First check if the fast path works.  This is needed
   * since this method may be called in the context when the fast
   * version was NOT just called.  If this fails, it will try finding
   * another block with a non-empty free list, or allocating a new
   * block.<p>
   *
   * This code should be relatively infrequently executed, so it is
   * forced out of line to reduce pressure on the compilation of the
   * core alloc routine.<p>
   *
   * Precondition: None
   *
   * Postconditions: A new cell has been allocated (not zeroed), and
   * the block containing the cell has been placed on the appropriate
   * free list data structures.  The free list itself is not updated
   * (the caller must do so).<p>
   *
   * @param bytes The size of the object to occupy this space, in bytes.
   * @param offset The alignment offset.
   * @param align The requested alignment.
   * @return The address of the first word or zero on failure.
   */
  @Override
  @NoInline
  public final Address allocSlowOnce(int bytes, int align, int offset) {
    // Did a collection occur and provide a free cell?
    bytes = getMaximumAlignedSize(bytes, align);
    int sizeClass = space.getSizeClass(bytes);
    Address cell = freeList.get(sizeClass);

    if (cell.isZero()) {
      Address block = currentBlock.get(sizeClass);
      if (!block.isZero()) {
        // Return the block if we currently own one
        space.returnConsumedBlock(block, sizeClass);
        currentBlock.set(sizeClass, Address.zero());
      }

      // Get a new block for allocation, if returned, it is guaranteed to have a free cell
      block = space.getAllocationBlock(sizeClass, freeList);

      if (!block.isZero()) {
        // We have a new current block and free list.
        currentBlock.set(sizeClass, block);
        cell = freeList.get(sizeClass);
        if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!cell.isZero());
      } else {
        // Allocation Failure
        return Address.zero();
      }
    }

    freeList.set(sizeClass, cell.loadAddress());
    /* Clear the free list link */
    cell.store(Address.zero());
    return alignAllocation(cell, align, offset);
  }

  /****************************************************************************
   *
   * Preserving (saving & restoring) free lists
   */

  /**
   * Zero all of the current free list pointers, and refresh the
   * <code>currentBlock</code> values, so instead of the free list
   * pointing to free cells, it points to the block containing the
   * free cells.  Then the free lists for each cell can be
   * reestablished during GC.  If the free lists are being preserved
   * on a per-block basis (eager mark-sweep and reference counting),
   * then free lists are remembered for each block.
   */
  public final void flush() {
    for (int sizeClass = 0; sizeClass < space.sizeClassCount(); sizeClass++) {
      Address block = currentBlock.get(sizeClass);
      if (!block.isZero()) {
        Address cell = freeList.get(sizeClass);
        space.returnBlock(block, sizeClass, cell);
        currentBlock.set(sizeClass, Address.zero());
        freeList.set(sizeClass, Address.zero());
      }
    }
  }
}
