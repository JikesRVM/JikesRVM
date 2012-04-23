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

import org.mmtk.policy.Space;

import org.mmtk.utility.gcspy.Color;
import org.mmtk.utility.gcspy.StreamConstants;
import org.mmtk.vm.VM;
import org.mmtk.vm.gcspy.ShortStream;

import org.mmtk.utility.Log;
import org.mmtk.vm.gcspy.ServerInterpreter;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * GCspy driver for the MMTk generational immortal space.
 * Additional Stream for remset references.
 * This class extends ImmortalSpaceDriver, a simple driver for
 * the contiguous MMTk ImmortalSpace.
 */
@Uninterruptible public class GenImmortalSpaceDriver extends ImmortalSpaceDriver {

  private static final boolean DEBUG = false;

  // The Stream for newly promoted objects
  protected ShortStream remsetStream;
  // Statistics for remset references
  protected int totalRemset = 0;


  /**
   * Create a new driver for a generational immortal space.
   *
   * @param server The GCspy ServerInterpreter
   * @param spaceName The name of this GCspy space
   * @param mmtkSpace The MMTk space
   * @param blockSize The tile size
   * @param mainSpace Is this the main space?
   */
  public GenImmortalSpaceDriver(
                     ServerInterpreter server,
                     String spaceName,
                     Space mmtkSpace,
                     int blockSize,
                     boolean mainSpace) {

    super(server, spaceName, mmtkSpace, blockSize, mainSpace);

    // create additional stream
    remsetStream = createRemsetStream();

    if (DEBUG) {
      Log.write("GenImmortalSpaceDriver for "); Log.write(spaceName);
      Log.write(", blocksize="); Log.write(blockSize);
      Log.write(", start="); Log.write(mmtkSpace.getStart());
      Log.write(", extent="); Log.write(mmtkSpace.getExtent());
      Log.write(", maxTileNum="); Log.writeln(maxTileNum);
    }

    resetData();
  }

  /**
   * Get the name of this driver type.
   * @return The name, "MMTk GenImmortalSpaceDriver" for this driver.
   */
  @Override
  protected String getDriverName() {
    return "MMTk GenImmortalSpaceDriver";
  }

  /**
   * Heelper methods to create the additional streams
 */
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
   * Overrides method in superclass to handle additional Stream.
 */
  @Override
  protected void setupSummaries() {
    super.setupSummaries();
    remsetStream.setSummary(totalRemset);
  }

  /**
   * Handle a remset address
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
      totalRemset++;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Reset the remset Stream
   * The remset Stream has to be reset seperately because we do not
   * gather data in the usual way using scan().
 */
  public void resetRemsetStream() {
    remsetStream.resetData();
    totalRemset = 0;
  }

}
