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

import org.mmtk.policy.LargeObjectSpace;
import org.mmtk.utility.gcspy.Color;
import org.mmtk.utility.gcspy.StreamConstants;
import org.mmtk.utility.gcspy.Subspace;
import org.mmtk.vm.gcspy.IntStream;
import org.mmtk.vm.gcspy.ShortStream;
import org.mmtk.vm.gcspy.ServerInterpreter;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.Log;
import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;


/**
 * This class implements a simple driver for the MMTk LargeObjectSpace.
 */
@Uninterruptible public class TreadmillDriver extends AbstractDriver {

  private static final boolean DEBUG = false;

  // The streams
  protected IntStream   usedSpaceStream;
  protected ShortStream objectsStream;
  protected ShortStream rootsStream;
  protected ShortStream refFromImmortalStream;

  protected Subspace subspace;            // A single subspace for this space
  protected int allTileNum;               // total number of tiles

  // Overall statistics
  protected int totalObjects         = 0; // total number of objects allocated
  protected int totalUsedSpace       = 0; // total space used
  protected int totalRoots           = 0; // total of roots
  protected int totalRefFromImmortal = 0; // total direct references from the immortal space
  protected Address maxAddr;              // the largest address seen
  protected int threshold;


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
  public TreadmillDriver(
                         ServerInterpreter server,
                         String spaceName,
                         LargeObjectSpace lospace,
                         int blockSize,
                         int threshold,
                         boolean mainSpace) {
    //TODO blocksize should be a multiple of treadmill granularity
    super(server, spaceName, lospace, blockSize, mainSpace);

    if (DEBUG) {
      Log.write("TreadmillDriver for "); Log.write(spaceName);
      Log.write(", blocksize="); Log.write(blockSize);
      Log.write(", start="); Log.write(lospace.getStart());
      Log.write(", extent="); Log.write(lospace.getExtent());
      Log.write(", maxTileNum="); Log.writeln(maxTileNum);
    }

    this.threshold = threshold;

    // Initialise a subspace and 2 Streams
    subspace = createSubspace(lospace);
    allTileNum = 0;
    maxAddr = lospace.getStart();
    usedSpaceStream       = createUsedSpaceStream();
    objectsStream         = createObjectsStream();
    rootsStream           = createRootsStream();
    refFromImmortalStream = createRefFromImmortalStream();
    serverSpace.resize(0); // the collector must call resize() before gathering data

    // Initialise the statistics
    resetData();
  }

  /**
   * Get the name of this driver type.
   * @return The name, "MMTk TreadmillDriver" for this driver.
   */
  @Override
  protected String getDriverName() {
    return "MMTk TreadmillDriver";
  }

  // private creator methods for the streams
  @Interruptible
  private IntStream createUsedSpaceStream() {
    return VM.newGCspyIntStream(
                     this,
                     "Used Space stream",                    // stream name
                     0,                                      // min. data value
                     blockSize,                              // max. data value
                     0,                                      // zero value
                     0,                                      // default value
                    "Space used: ",                          // value prefix
                    " bytes",                                // value suffix
                     StreamConstants.PRESENTATION_PERCENT,   // presentation style
                     StreamConstants.PAINT_STYLE_ZERO,       // paint style
                     0,                                      // index of the max stream (only needed if presentation is *_VAR)
                     Color.Red,                              // tile colour
                     true);                                  // summary enabled
  }

  @Interruptible
  private ShortStream createObjectsStream() {
    return VM.newGCspyShortStream(
                     this,
                     "Objects stream",
                     (short)0,
                     (short)(blockSize/threshold),
                     (short)0,
                     (short)0,
                     "No. of objects = ",
                     " objects",
                     StreamConstants.PRESENTATION_PLUS,
                     StreamConstants.PAINT_STYLE_ZERO,
                     0,
                     Color.Green,
                     true);
  }

  @Interruptible
  private ShortStream createRootsStream() {
    return VM.newGCspyShortStream(
                     this,
                     "Roots stream",
                     (short)0,
                     // Say, typical size = 4 * typical scalar size?
                     (short)(maxObjectsPerBlock(blockSize)/8),
                     (short)0,
                     (short)0,
                     "Roots: ",
                     " objects",
                     StreamConstants.PRESENTATION_PLUS,
                     StreamConstants.PAINT_STYLE_ZERO,
                     0,
                     Color.Blue,
                     true);
  }

  @Interruptible
  private ShortStream createRefFromImmortalStream() {
    return VM.newGCspyShortStream(
                     this,
                     "References from Immortal stream",
                     (short)0,
                     // Say, typical size = 4 * typical scalar size?
                     (short)(maxObjectsPerBlock(blockSize)/8),
                     (short)0,
                     (short)0,
                     "References from immortal space: ",
                     " references",
                     StreamConstants.PRESENTATION_PLUS,
                     StreamConstants.PAINT_STYLE_ZERO,
                     0,
                     Color.Blue,
                     true);
  }

  /**
   * Reset the tile stats for all streams, including values used for summaries
   */
  @Override
  public void resetData() {
    super.resetData();

    // Reset all the streams
    usedSpaceStream.resetData();
    objectsStream.resetData();
    refFromImmortalStream.resetData();

    // Reset the summary counts
    totalUsedSpace = 0;
    totalObjects = 0;
    totalRefFromImmortal = 0;
  }

  /**
   * Update the tile statistics
   * In this case, we are accounting for super-page objects, rather than
   * simply for the objects they contain.
   *
   * @param addr The address of the superpage
   */
  @Override
  public void scan(Address addr) {

    int index = subspace.getIndex(addr);
    int length = ((LargeObjectSpace)mmtkSpace).getSize(addr).toInt();

    if (DEBUG) {
      Log.write("TreadmillDriver: super=", addr);
      Log.write(", index=", index);
      Log.write(", pages=", length);
      Log.write(", bytes=", Conversions.pagesToBytes(length).toInt());
      Log.writeln(", max=", usedSpaceStream.getMaxValue());
    }

    totalObjects++;
    totalUsedSpace += length;
    objectsStream.increment(index, (short)1);
    int remainder = subspace.spaceRemaining(addr);
    usedSpaceStream.distribute(index, remainder, blockSize, length);

    Address tmp = addr.plus(length);
    if (tmp.GT(maxAddr)) maxAddr = tmp;
  }

  /**
   * Transmit the data if this event is of interest to the client
   * @param event The event, either BEFORE_COLLECTION, SEMISPACE_COPIED
   * or AFTER_COLLECTION
   */
  @Override
  public void transmit(int event) {
    if (!isConnected(event))
      return;

    // At this point, we've filled the tiles with data,
    // however, we don't know the size of the space
    // Calculate the highest indexed tile used so far, and update the subspace
    Address start = subspace.getStart();
    int required = countTileNum(start, maxAddr, blockSize);
    int current = subspace.getBlockNum();
    if (required > current || maxAddr != subspace.getEnd()) {
      subspace.reset(start, maxAddr, 0, required);
      allTileNum = required;
      serverSpace.resize(allTileNum);
      setTilenames(subspace, allTileNum);
    }

    // Set the summaries
    setupSummaries();

    // set the control info: all of space is USED
    controlValues(CONTROL_USED,
                  subspace.getFirstIndex(), subspace.getBlockNum());

    // send the space info
    Offset size = subspace.getEnd().diff(subspace.getStart());
    setSpaceInfo(size);

    // Send the streams
    send(event, allTileNum);
  }

  /**
   * Setup summaries part of the <code>transmit</code> method.<p>
   * Override this method to setup summaries of additional streams in subclasses.
   */
  protected void setupSummaries() {
    usedSpaceStream.setSummary(totalUsedSpace,
                               subspace.getEnd().diff(subspace.getStart()).toInt());
    objectsStream.setSummary(totalObjects);
    rootsStream.setSummary(totalRoots);
    refFromImmortalStream.setSummary(totalRefFromImmortal);
  }


  /**
   * Handle a root address
   *
   * @param addr Root Address
   * @return true if the given Address is in this subspace.
   */
  public boolean handleRoot(Address addr) {
    if(subspace.addressInRange(addr)) {
      // increment tile
      int index = subspace.getIndex(addr);
      rootsStream.increment(index, (short)1);
      // increment summary
      this.totalRoots++;
      return true;
    } else {
      return false;
    }
  }

  /**
   * Reset the roots Stream. <br>
   * The roots Stream has to be reset seperately because we do not
   * gather data in the usual way using <code>scan()</code>.
   */
  public void resetRootsStream() {
    rootsStream.resetData();
    totalRoots = 0;
  }

  /**
   * Handle a direct reference from the immortal space.
   *
   * @param addr The Address
   * @return true if the given Address is in this subspace.
   */
  @Override
  public boolean handleReferenceFromImmortalSpace(Address addr) {
    if(subspace.addressInRange(addr)) {
      // increment tile
      int index = subspace.getIndex(addr);
      refFromImmortalStream.increment(index, (short)1);
      // increment summary
      this.totalRefFromImmortal++;
      return true;
    } else {
      return false;
    }
  }
}
