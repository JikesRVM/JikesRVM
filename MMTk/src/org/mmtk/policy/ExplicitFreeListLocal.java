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

import org.mmtk.utility.alloc.SegregatedFreeListLocal;
import org.mmtk.utility.Constants;

import org.vmmagic.pragma.*;

/**
 * This class implements unsynchronized (local) elements of an
 * explicity managed collector.  Allocation is via the segregated free list
 * (@see org.mmtk.utility.alloc.SegregatedFreeList).<p>
 *
 * @see org.mmtk.utility.alloc.SegregatedFreeList
 * @see ExplicitFreeListSpace
 */
@Uninterruptible
public final class ExplicitFreeListLocal extends SegregatedFreeListLocal<ExplicitFreeListSpace> implements Constants {

  /****************************************************************************
   *
   * Class variables
   */

  /****************************************************************************
   *
   * Instance variables
   */

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   *
   * @param space The rc space to which this allocator
   * instances is bound.
   */
  public ExplicitFreeListLocal(ExplicitFreeListSpace space) {
    super(space);
  }

  /****************************************************************************
   *
   * Allocation
   */

  /****************************************************************************
   *
   * Collection
   */

  /**
   * Prepare for a collection. If paranoid, perform a sanity check.
   */
  public void prepare() {
    flush();
  }

  /**
   * Finish up after a collection.
   */
  public void release() {}
}
