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
package org.mmtk.utility.gcspy.drivers;

import org.mmtk.utility.gcspy.Color;
import org.mmtk.utility.gcspy.StreamConstants;
import org.mmtk.vm.VM;
import org.mmtk.vm.gcspy.ShortStream;
import org.mmtk.vm.gcspy.ServerInterpreter;

import org.mmtk.policy.LargeObjectSpace;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;


/**
 * This class extends a simple driver for the MMTk LargeObjectSpace
 * for Generational Collectors.
 */
@Uninterruptible public class GenLOSDriver extends TreadmillDriver {

  private static final boolean DEBUG = false;

  /** The additional remset stream */
  protected ShortStream remsetStream;
  /** total of remset Addresses */
  protected int totalRemset = 0;


  /**
   * Create a new driver for this collector
   *
   * @param server The name of the GCspy server that owns this space
   * @param spaceName The name of this driver
   * @param lospace the large object space for this allocator
   * @param blockSize The tile size
   * @param threshold the size threshold of the LOS
   * @param mainSpace Is this the main space?
   */
  public GenLOSDriver(ServerInterpreter server,
                      String spaceName,
                      LargeObjectSpace lospace,
                      int blockSize,
                      int threshold,
                      boolean mainSpace) {
    //TODO blocksize should be a multiple of treadmill granularity
    super(server, spaceName, lospace, blockSize, threshold, mainSpace);
    // create remset stream
    remsetStream    = createRemsetStream();
    // Initialise the statistics
    resetData();
  }

  /**
   * Get the name of this driver type.
   * @return The name, "MMTk GenLOSDriver" for this driver.
   */
  @Override
  protected String getDriverName() {
    return "MMTk GenLOSDriver";
  }

  // private creator methods for the streams
  @Interruptible
  private ShortStream createRemsetStream() {
    return VM.newGCspyShortStream(
                     this,
                     "Remembered set stream",
                     (short)0,
                     // Say, typical size = 4 * typical scalar size?
                     (short)(maxObjectsPerBlock(blockSize)/8),
                     (short)0,
                     (short)0,
                     "Remset references: ",
                     " references",
                     StreamConstants.PRESENTATION_PLUS,
                     StreamConstants.PAINT_STYLE_ZERO,
                     0,
                     Color.Cyan,
                     true);
  }

  /**
   * Setup summaries part of the <code>transmit</code> method.<p>
   * Overrides <code>transmitSetupSummaries </code> of superclass to
   * handle additional streams.
 */
  @Override
  protected void setupSummaries() {
    super.setupSummaries();
    remsetStream.setSummary(totalRemset);
  }

  /**
   * Handle a remset address.
   *
   * @param addr Remset Address
   * @return true if the given Address is in this subspace.
   */
  public boolean handleRemsetAddress(Address addr) {
    if(subspace.addressInRange(addr)) {
      // increment tile
      int index = subspace.getIndex(addr);
      remsetStream.increment(index, (short)1);
      // increment summary
      this.totalRemset++;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Reset the remset Stream. <p>
   * The remset Stream has to be reset seperately because we do not
   * gather data in the usual way using <code>scan()</code>.
 */
  public void resetRemsetStream() {
    remsetStream.resetData();
    totalRemset = 0;
  }

}
