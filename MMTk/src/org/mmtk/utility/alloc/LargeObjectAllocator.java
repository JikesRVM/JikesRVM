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

import org.mmtk.policy.BaseLargeObjectSpace;
import org.mmtk.utility.Constants;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This abstract class implements core functionality for a generic
 * large object allocator. The shared VMResource used by each instance
 * is the point of global synchronization, and synchronization only
 * occurs at the granularity of aquiring (and releasing) chunks of
 * memory from the VMResource.  Subclasses may require finer grained
 * synchronization during a marking phase, for example.<p>
 *
 * This is a first cut implementation, with plenty of room for
 * improvement...
 */
@Uninterruptible
public abstract class LargeObjectAllocator extends Allocator implements Constants {

  /****************************************************************************
   *
   * Instance variables
   */
  protected final BaseLargeObjectSpace space;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   *
   * @param space The space with which this large object allocator
   * will be associated.
   */
  public LargeObjectAllocator(BaseLargeObjectSpace space) {
    this.space = space;
  }

  /**
   * Return the space this allocator is currently bound to.
   *
   * @return The Space.
   */
  protected final BaseLargeObjectSpace getSpace() {
    return this.space;
  }

  /****************************************************************************
   *
   * Allocation
   */

  /**
   * Allocate space for an object
   *
   * @param bytes The number of bytes allocated
   * @param align The requested alignment.
   * @param offset The alignment offset.
   * @return The address of the first byte of the allocated cell Will
   * not return zero.
   */
  @NoInline
  public final Address alloc(int bytes, int align, int offset) {
    Address cell = allocSlow(bytes, align, offset);
    return alignAllocation(cell, align, offset);
  }

  /**
   * Allocate a large object.  Large objects are directly allocted and
   * freed in page-grained units via the vm resource.  This routine
   * returned zeroed memory.
   *
   * @param bytes The required size of this space in bytes.
   * @param offset The alignment offset.
   * @param align The requested alignment.
   * @return The address of the start of the newly allocated region at
   * least <code>bytes</code> bytes in size.
   */
  protected final Address allocSlowOnce(int bytes, int align, int offset) {
    int header = space.getHeaderSize();
    int maxbytes = getMaximumAlignedSize(bytes + header, align);
    int pages = (maxbytes + BYTES_IN_PAGE - 1) >> LOG_BYTES_IN_PAGE;
    Address sp = space.acquire(pages);
    if (sp.isZero()) return sp;
    Address cell = sp.plus(header);
    return cell;
  }

  /****************************************************************************
   *
   * Miscellaneous
   */
  public void show() {
  }
}

