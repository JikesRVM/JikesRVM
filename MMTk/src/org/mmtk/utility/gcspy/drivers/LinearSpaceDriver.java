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
import org.mmtk.utility.gcspy.LinearScan;
import org.mmtk.utility.gcspy.StreamConstants;
import org.mmtk.utility.gcspy.Subspace;
import org.mmtk.vm.gcspy.IntStream;
import org.mmtk.vm.gcspy.ShortStream;

import org.mmtk.utility.Log;
import org.mmtk.vm.gcspy.ServerInterpreter;
import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * GCspy driver for the MMTk ContigousSpace.
 *
 * This class implements a simple driver for contiguous MMTk spaces
 * such as CopySpace and ImmortalSpace.
 */
@Uninterruptible public class LinearSpaceDriver extends AbstractDriver {

  // The GCspy streams
  protected IntStream   scalarUsedSpaceStream;
  protected IntStream   arrayUsedSpaceStream;
  protected ShortStream scalarObjectsStream;
  protected ShortStream arrayObjectsStream;
  protected ShortStream arrayPrimitiveStream;
  protected ShortStream rootsStream;
  protected ShortStream refFromImmortalStream;

  protected Subspace subspace;               // A subspace for all of this space
  protected int allTileNum;                  // total number of tiles

  // Overall statistics
  protected int totalScalarObjects   = 0;    // total number of objects allocated
  protected int totalArrayObjects    = 0;
  protected int totalPrimitives      = 0;
  protected int totalScalarUsedSpace = 0;    // total space used
  protected int totalArrayUsedSpace  = 0;
  protected int totalRoots           = 0;
  protected int totalRefFromImmortal = 0;

  private final LinearScan scanner;          // A scanner to trace objects

  // Debugging
  protected Address lastAddress = Address.zero();
  protected int lastSize = 0;
  private static final boolean DEBUG = false;


  /**
   * Create a new driver for a contiguous MMTk space.
   *
   * @param server The GCspy ServerInterpreter
   * @param spaceName The name of this GCspy space
   * @param mmtkSpace The MMTk space
   * @param blockSize The tile size
   * @param mainSpace Is this the main space?
   */
  public LinearSpaceDriver(ServerInterpreter server,
                           String spaceName,
                           Space mmtkSpace,
                           int blockSize,
                           boolean mainSpace) {

    super(server, spaceName, mmtkSpace, blockSize, mainSpace);

    if (DEBUG) {
      Log.write("LinearSpaceDriver for "); Log.write(spaceName);
      Log.write(", blocksize="); Log.write(blockSize);
      Log.write(", start="); Log.write(mmtkSpace.getStart());
      Log.write(", extent="); Log.write(mmtkSpace.getExtent());
      Log.write(", maxTileNum="); Log.writeln(maxTileNum);
    }

    // Initialise a subspace and 4 Streams
    subspace = createSubspace(mmtkSpace);
    allTileNum = 0;
    scalarUsedSpaceStream = createScalarUsedSpaceStream();
    arrayUsedSpaceStream  = createArrayUsedSpaceStream();
    scalarObjectsStream   = createScalarObjectsStream();
    arrayPrimitiveStream  = createArrayPrimitiveStream();
    arrayObjectsStream    = createArrayObjectsStream();
    rootsStream           = createRootsStream();
    refFromImmortalStream = createRefFromImmortalStream();
    serverSpace.resize(0); // the collector must call resize() before gathering data

    // Initialise the statistics
    resetData();
    scanner = new LinearScan(this);
  }

  /**
   * Get the name of this driver type.
   * @return The name of this driver.
   */
  protected String getDriverName() { return "MMTk LinearSpaceDriver"; }

  /**
   * Private creator methods to create the Streams.
   */
  @Interruptible
  private IntStream createScalarUsedSpaceStream() {
    return VM.newGCspyIntStream(
                     this,
                     "Scalar Used Space stream",            // stream name
                     0,                                     // min. data value
                     blockSize,                             // max. data value
                     0,                                     // zero value
                     0,                                     // default value
                    "Scalars and primitive arrays: ",       // value prefix
                    " bytes",                               // value suffix
                     StreamConstants.PRESENTATION_PERCENT,  // presentation style
                     StreamConstants.PAINT_STYLE_ZERO,      // paint style
                     0,                                     // index of max stream (only needed if the presentation is *_VAR)
                     Color.Red,                             // tile colour
                     true);                                 // summary enabled
  }

  @Interruptible
  private IntStream createArrayUsedSpaceStream() {
    return VM.newGCspyIntStream(
                     this,
                     "Array Used Space stream",
                     0,
                     blockSize,
                     0,
                     0,
                    "Reference arrays: ",
                    " bytes",
                     StreamConstants.PRESENTATION_PERCENT,
                     StreamConstants.PAINT_STYLE_ZERO,
                     0,
                     Color.Blue,
                     true);
  }

  @Interruptible
  private ShortStream createScalarObjectsStream() {
    return VM.newGCspyShortStream(
                     this,
                     "Scalar Objects stream",
                     (short)0,
                     // Say, max value = 50% of max possible
                     (short)(maxObjectsPerBlock(blockSize)/2),
                     (short)0,
                     (short)0,
                     "Scalars: ",
                     " objects",
                     StreamConstants.PRESENTATION_PLUS,
                     StreamConstants.PAINT_STYLE_ZERO,
                     0,
                     Color.Green,
                     true);
  }

  @Interruptible
  private ShortStream createArrayPrimitiveStream() {
    return VM.newGCspyShortStream(
                     this,
                     "Array Primitive stream",
                     (short)0,
                     // Say, typical primitive array size = 4 * typical scalar size?
                     (short)(maxObjectsPerBlock(blockSize)/8),
                     (short)0,
                     (short)0,
                     "Primitive arrays: ",
                     " objects",
                     StreamConstants.PRESENTATION_PLUS,
                     StreamConstants.PAINT_STYLE_ZERO,
                     0,
                     Color.Yellow,
                     true);
  }

  @Interruptible
  private ShortStream createArrayObjectsStream() {
    return VM.newGCspyShortStream(
                     this,
                     "Array Objects stream",
                     (short)0,
                     // Say, typical ref array size = 4 * typical scalar size?
                     (short)(maxObjectsPerBlock(blockSize)/8),
                     (short)0,
                     (short)0,
                     "Reference arrays: ",
                     " objects",
                     StreamConstants.PRESENTATION_PLUS,
                     StreamConstants.PAINT_STYLE_ZERO,
                     0,
                     Color.Cyan,
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
                     "References from immortal stream",
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
   * Reset the statistics for all the streams, including totals used for summaries
   */
  public void resetData() {
    super.resetData();

    // Reset all the streams
    scalarUsedSpaceStream.resetData();
    arrayUsedSpaceStream.resetData();
    scalarObjectsStream.resetData();
    arrayObjectsStream.resetData();
    arrayPrimitiveStream.resetData();
    refFromImmortalStream.resetData();

    // Reset the summary counts
    totalScalarObjects   = 0;
    totalArrayObjects    = 0;
    totalPrimitives      = 0;
    totalScalarUsedSpace = 0;
    totalArrayUsedSpace  = 0;
    totalRefFromImmortal = 0;
  }

  /**
   * BumpPointer.linearScan needs a LinearScan object, which we provide here.
   * @return the scanner for this driver
   */
   public LinearScan getScanner() { return scanner; }

  /**
   * Set the current address range of a contiguous space
   * @param start the start of the contiguous space
   * @param end the end of the contiguous space
   */
  public void setRange(Address start, Address end) {
    int current = subspace.getBlockNum();
    int required = countTileNum(start, end, subspace.getBlockSize());

    // Reset the subspace
    if(required != current)
      subspace.reset(start, end, 0, required);

    if (DEBUG) {
      Log.write("\nContiguousSpaceDriver.setRange for contiguous space: ");
      Log.write(subspace.getFirstIndex()); Log.write("-", subspace.getBlockNum());
      Log.write(" (", start); Log.write("-", end); Log.write(")");
    }

    // Reset the driver
    // Note release() only resets a CopySpace's  cursor (and optionally zeroes
    // or mprotects the pages); it doesn't make the pages available to other
    // spaces. If pages were to be released, change the test here to
    //     if (allTileNum != required) {
    if (allTileNum < required) {
      if (DEBUG) { Log.write(", resize from ", allTileNum); Log.write(" to ", required); }
      allTileNum = required;
      serverSpace.resize(allTileNum);
      setTilenames(subspace, allTileNum);
    }
    if (DEBUG) Log.writeln();
  }


  /**
   * Update the tile statistics
   * @param obj The current object
   */
  public void  scan(ObjectReference obj) {
    scan(obj, true);
  }

  /**
   * Update the tile statistics
   * @param obj The current object
   * @param total Whether to accumulate the values
   */
  public void scan(ObjectReference obj, boolean total) {
    boolean isArray = VM.objectModel.isArray(obj);
    int length = VM.objectModel.getCurrentSize(obj);
    Address addr = obj.toAddress();

    if (VM.VERIFY_ASSERTIONS) {
      if(addr.LT(lastAddress.plus(lastSize))) {
        Log.write("\nContiguousSpaceDriver finds addresses going backwards: ");
        Log.write("last="); Log.write(lastAddress);
        Log.write(" last size="); Log.write(lastSize);
        Log.writeln(" current=", addr);
      }
      lastAddress = addr;
      lastSize = length;
    }

    // Update the stats
    if (subspace.addressInRange(addr)) {
      int index = subspace.getIndex(addr);
      int remainder = subspace.spaceRemaining(addr);
      if (isArray) {
        arrayObjectsStream.increment(index, (short)1);
        arrayUsedSpaceStream.distribute(index, remainder, blockSize, length);
        if (total) {
          totalArrayObjects++;
          totalArrayUsedSpace += length;
        }
      } else {
        if(!this.scanCheckPrimitiveArray(obj, index, total, length)) {
          // real object
          scalarObjectsStream.increment(index, (short)1);
          if (total) {
            totalScalarObjects++;
            totalScalarUsedSpace += length;
          }
        }
        scalarUsedSpaceStream.distribute(index, remainder, blockSize, length);
      }
    }
  }

  /**
   * Check if this Object is an array of primitives.<br>
   * Part of the public scan() method.
   *
   * @param obj The Object to check
   * @param index Index of the tile
   * @param total Increment summary
   * @param length Current size of the Object, will be added to array space summary.
   * @return True if this Object is an array of primitives.
   */
  protected boolean scanCheckPrimitiveArray(ObjectReference obj, int index, boolean total, int length) {
    if(VM.objectModel.isPrimitiveArray(obj)) {
      arrayPrimitiveStream.increment(index, (short)1);
      if (total) {
        totalPrimitives++;
        totalScalarUsedSpace += length;
      }
      return true;
    } else {
      return false;
    }
  }

  /**
   * Transmit the data if this event is of interest to the client.<p>
   * Implemented using the algorithm pattern, subclasses can override parts of it.
   * @param event The event, defined in the Plan
   */
  public void transmit(int event) {
    if (!server.isConnected(event))
      return;

    if (DEBUG) {
      Log.write("CONNECTED\n");
      Log.write(myClass);
      Log.write(".send: numTiles=", allTileNum);
      //Log.write("LinearSpaceDriver.transmit: numTiles=", allTileNum);
      Log.writeln(", control.length=", control.length);
      Log.flush();
    }

    // Setup the summaries
    setupSummaries();

    // Setup the control info
    setupControlInfo();

    // Setup the space info
    Offset size = subspace.getEnd().diff(subspace.getStart());
    setSpaceInfo(size);

    // Send the all streams
    send(event, allTileNum);

    // Debugging
    if (VM.VERIFY_ASSERTIONS) {
      lastAddress = Address.zero();
      lastSize = 0;
    }
  }

  /**
   * Setup summaries part of the <code>transmit</code> method.<p>
   * Override this method to setup summaries of additional streams in subclasses.
 */
  protected void setupSummaries() {
    scalarUsedSpaceStream.setSummary(totalScalarUsedSpace,
                                     subspace.getEnd().diff(subspace.getStart()).toInt());
    arrayUsedSpaceStream.setSummary(totalArrayUsedSpace,
                                    subspace.getEnd().diff(subspace.getStart()).toInt());
    scalarObjectsStream.setSummary(totalScalarObjects);
    arrayObjectsStream.setSummary(totalArrayObjects);
    arrayPrimitiveStream.setSummary(totalPrimitives);
    rootsStream.setSummary(totalRoots);
    refFromImmortalStream.setSummary(totalRefFromImmortal);
  }

  /**
   * Setup control info part of the <code>transmit</code> method.<p>
   * Override this method to change the controls for your own driver subclass.
 */
  protected void setupControlInfo() {
    int numBlocks = subspace.getBlockNum();
    controlValues(CONTROL_USED, subspace.getFirstIndex(), numBlocks);
    if (DEBUG) {
      Log.write("LinearSpaceDriver.transmitSetupControlInfo: allTileNum=", allTileNum);
      Log.writeln(", numBlocks=", numBlocks);
    }
    if (numBlocks < allTileNum)
      controlValues(CONTROL_UNUSED,
                    subspace.getFirstIndex() + numBlocks,
                    allTileNum - numBlocks);
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
   * Reset the roots Stream
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
