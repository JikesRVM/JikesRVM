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
package org.mmtk.policy;

import org.mmtk.utility.alloc.LargeObjectAllocator;
import org.mmtk.utility.Treadmill;
import org.mmtk.utility.gcspy.drivers.TreadmillDriver;
import org.mmtk.utility.Constants;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * Each instance of this class is intended to provide fast,
 * unsynchronized access to a treadmill.  Therefore instances must not
 * be shared across truely concurrent threads (CPUs).  Rather, one or
 * more instances of this class should be bound to each CPU.  The
 * shared VMResource used by each instance is the point of global
 * synchronization, and synchronization only occurs at the granularity
 * of aquiring (and releasing) chunks of memory from the VMResource.
 *
 * If there are C CPUs and T TreadmillSpaces, there must be C X T
 * instances of this class, one for each CPU, TreadmillSpace pair.
 */
@Uninterruptible public final class LargeObjectLocal extends LargeObjectAllocator
  implements Constants {

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
   * @param space The treadmill space to which this thread instance is
   * bound.
   */
  public LargeObjectLocal(LargeObjectSpace space) {
    super(space);
    this.space = space;
  }

  /****************************************************************************
   *
   * Allocation
   */

  /**
   *  This is called each time a cell is alloced (i.e. if a cell is
   *  reused, this will be called each time it is reused in the
   *  lifetime of the cell, by contrast to initializeCell, which is
   *  called exactly once.).
   *
   * @param cell The newly allocated cell
   */
  @Inline
  protected void postAlloc(Address cell) {
    space.getTreadmill().addToTreadmill(Treadmill.payloadToNode(cell));
  }

  /****************************************************************************
   *
   * Collection
   */

  /**
   * Prepare for a collection.  Clear the treadmill to-space head and
   * prepare the collector.  If paranoid, perform a sanity check.
   */
  public void prepare(boolean fullHeap) {
  }

  /**
   * Finish up after a collection.
   */
  public void release(boolean fullHeap) {
  }

  /****************************************************************************
   *
   * Miscellaneous size-related methods
   */

  /**
   * Return the size of the per-superpage header required by this
   * system.  In this case it is just the underlying superpage header
   * size.
   *
   * @return The size of the per-superpage header required by this
   * system.
   */
  @Inline
  protected int superPageHeaderSize() {
    return Treadmill.headerSize();
  }

  /**
   * Return the size of the per-cell header for cells of a given class
   * size.
   *
   * @return The size of the per-cell header for cells of a given class
   * size.
   */
  @Inline
  protected int cellHeaderSize() {
    return 0;
  }

  /**
   * Gather data for GCSpy from the nursery
   * @param event the gc event
   * @param losDriver the GCSpy space driver
   */
  public void gcspyGatherData(int event, TreadmillDriver losDriver) {
    // TODO: assumes single threaded
    space.getTreadmill().gcspyGatherData(event, losDriver);
  }

  /**
   * Gather data for GCSpy for an older space
   * @param event the gc event
   * @param losDriver the GCSpy space driver
   * @param tospace gather from tospace?
   */
  public void gcspyGatherData(int event, TreadmillDriver losDriver, boolean tospace) {
    // TODO: assumes single threaded
    space.getTreadmill().gcspyGatherData(event, losDriver, tospace);
  }
}
