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
package org.mmtk.policy;

import org.mmtk.utility.heap.FreeListPageResource;
import org.mmtk.utility.heap.VMRequest;
import org.mmtk.utility.Constants;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * Each instance of this class corresponds to one treadmill *space*.
 *
 * Each of the instance methods of this class may be called by any
 * thread (i.e. synchronization must be explicit in any instance or
 * class method).
 *
 * This stands in contrast to TreadmillLocal, which is instantiated
 * and called on a per-thread basis, where each instance of
 * TreadmillLocal corresponds to one thread operating over one space.
 */
@Uninterruptible
public abstract class BaseLargeObjectSpace extends Space implements Constants {

  /****************************************************************************
   *
   * Class variables
   */
  protected static final Word PAGE_MASK = Word.fromIntSignExtend(~(BYTES_IN_PAGE - 1));

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * The caller specifies the region of virtual memory to be used for
   * this space.  If this region conflicts with an existing space,
   * then the constructor will fail.
   *
   * @param name The name of this space (used when printing error messages etc)
   * @param vmRequest An object describing the virtual memory requested.
   */
  public BaseLargeObjectSpace(String name, VMRequest vmRequest) {
    super(name, false, false, vmRequest);
    if (vmRequest.isDiscontiguous()) {
      pr = new FreeListPageResource(this, 0);
    } else {
      pr = new FreeListPageResource(this, start, extent);
    }
  }

  /**
   * Calculate the header size required for the large object.
   *
   * Must be multiple of MIN_ALIGNMENT.
   */
  public final int getHeaderSize() {
    return superPageHeaderSize() + cellHeaderSize();
  }

  /****************************************************************************
   *
   * Freeing
   */

  /**
   * Free a cell.  If the cell is large (own superpage) then release
   * the superpage, if not add to the super page's free list and if
   * all cells on the superpage are free, then release the
   * superpage.
   *
   * @param cell The address of the first byte of the cell to be freed
   */
  @Inline
  public final void free(Address cell) {
    release(getSuperPage(cell));
  }

  /****************************************************************************
   *
   * Superpages
   */

  protected abstract int superPageHeaderSize();
  protected abstract int cellHeaderSize();

  /**
   * Return the superpage for a given cell.  If the cell is a small
   * cell then this is found by masking the cell address to find the
   * containing page.  Otherwise the first word of the cell contains
   * the address of the page.
   *
   * @param cell The address of the first word of the cell (exclusive
   * of any sub-class specific metadata).
   * @return The address of the first word of the superpage containing
   *         <code>cell</code>.
   */
  @Inline
  public static Address getSuperPage(Address cell) {
    return cell.toWord().and(PAGE_MASK).toAddress();
  }

  /**
   * Return the size of the super page
   *
   * @param first the Address of the first word in the superpage
   * @return the size in bytes
   */
  public Extent getSize(Address first) {
    return ((FreeListPageResource) pr).getSize(first);
  }
}
