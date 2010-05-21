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

import org.mmtk.utility.alloc.BumpPointer;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Extent;

/**
 * This class implements unsynchronized (local) elements of a
 * sliding mark-compact collector. Allocation is via the bump pointer
 * (@see BumpPointer).
 *
 * @see BumpPointer
 * @see MarkCompactSpace
 */
@Uninterruptible
public final class MarkCompactLocal extends BumpPointer {

  /**
   * Constructor
   *
   * @param space The space to bump point into.
   */
  public MarkCompactLocal(MarkCompactSpace space) {
    super(space, true);
  }

  private MarkCompactSpace mcSpace() {
    return (MarkCompactSpace)space;
  }

  /**
   * Prepare for collection: update the metadata for the current region, and flush
   * this bump-pointer's allocations to the global page list.
   */
  public void prepare() {
    if (!initialRegion.isZero()) {
      setDataEnd(region,cursor);
      mcSpace().append(initialRegion);
    }
    reset();
  }

  /**
   * Flush this thread-local component in preparation for the mutator thread
   * to die.
   */
  public void flush() {
    prepare();
  }

  /**
   * Maximum size of a single region. Important for children that implement
   * load balancing or increments based on region size.
   * @return the maximum region size
   */
  @Override
  protected Extent maximumRegionSize() { return Extent.fromIntZeroExtend(4 << LOG_BLOCK_SIZE) ; }

}
