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

import org.mmtk.utility.alloc.LargeObjectAllocator;
import org.mmtk.utility.gcspy.drivers.TreadmillDriver;
import org.mmtk.utility.Constants;

import org.vmmagic.pragma.*;

/**
 * Each instance of this class is intended to provide fast,
 * unsynchronized access to a treadmill.  Therefore instances must not
 * be shared across truly concurrent threads (CPUs).  Rather, one or
 * more instances of this class should be bound to each CPU.  The
 * shared VMResource used by each instance is the point of global
 * synchronization, and synchronization only occurs at the granularity
 * of acquiring (and releasing) chunks of memory from the VMResource.<p>
 *
 * If there are C CPUs and T TreadmillSpaces, there must be C X T
 * instances of this class, one for each CPU, TreadmillSpace pair.
 */
@Uninterruptible
public final class LargeObjectLocal extends LargeObjectAllocator implements Constants {

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
  public LargeObjectLocal(BaseLargeObjectSpace space) {
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
   * Gather data for GCSpy from the nursery
   * @param event the gc event
   * @param losDriver the GCSpy space driver
   */
  public void gcspyGatherData(int event, TreadmillDriver losDriver) {
    // TODO: assumes single threaded
    // TODO: assumes non-explit LOS
    ((LargeObjectSpace)space).getTreadmill().gcspyGatherData(event, losDriver);
  }

  /**
   * Gather data for GCSpy for an older space
   * @param event the gc event
   * @param losDriver the GCSpy space driver
   * @param tospace gather from tospace?
   */
  public void gcspyGatherData(int event, TreadmillDriver losDriver, boolean tospace) {
    // TODO: assumes single threaded
    ((LargeObjectSpace)space).getTreadmill().gcspyGatherData(event, losDriver, tospace);
  }
}
