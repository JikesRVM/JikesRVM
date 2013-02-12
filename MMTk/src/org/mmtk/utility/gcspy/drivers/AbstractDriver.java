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
import org.mmtk.utility.Log;
import org.mmtk.utility.gcspy.GCspy;
import org.mmtk.utility.gcspy.Subspace;
import org.mmtk.vm.gcspy.ServerSpace;
import org.mmtk.vm.gcspy.ServerInterpreter;
import org.mmtk.vm.gcspy.Stream;
import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Abstract GCspy driver for MMTk collectors.<p>
 *
 * This class implements for the MMTk a base driver for a GCspy space.
 * All drivers for GCspy spaces should inherit from this class.
 */
@Uninterruptible
public abstract class AbstractDriver {

  /****************************************************************************
   *
   * Class variables
   */

  // Controls used for tile presentation

  /** The tile is used */
  protected static final byte CONTROL_USED            =  1;
  /** The tile is a background tile */
  protected static final byte CONTROL_BACKGROUND      =  2;
  /** The tile is unused */
  protected static final byte CONTROL_UNUSED          =  4;
  /** The tile is a separator */
  protected static final byte CONTROL_SEPARATOR       =  8;
  /** The tile is a link */
  protected static final byte CONTROL_LINK            = 16;


  private static final int MAX_STREAMS = 64;    // Max number of streams

  private static final boolean DEBUG = false;
  protected String myClass;                     // used in debugging messages


  /****************************************************************************
   *
   * Instance variables
   */

  /** The owning GCspy server */
  protected final ServerInterpreter server;
  /** The name of the GCspy space driver */
  protected final String name;
  /** The GCspy space abstraction */
  protected final ServerSpace serverSpace;
  /** The MMTK space */
  protected final Space mmtkSpace;
  /** The GCspy space's block size */
  protected int blockSize;
  /** The maximum number of tiles in this GCspy space */
  protected int maxTileNum;
  /** This space's streams  */
  protected Stream[] streams;
  /**  control values for tiles in this space */
  protected byte[] control;
  /** Has this space changed? */
  protected boolean changed = true;


  /**
   * Create a new driver for this collector.
   *
   * @param server The ServerInterpreter that owns this GCspy space.
   * @param name The name of this driver.
   * @param mmtkSpace The MMTk space represented by this driver.
   * @param blockSize The tile size.
   * @param mainSpace Is this the main space?
   */
  public AbstractDriver(ServerInterpreter server,
                        String name,
                        Space mmtkSpace,
                        int blockSize,
                        boolean mainSpace) {
    this.server = server;
    this.name = name;
    this.mmtkSpace = mmtkSpace;
    this.blockSize = blockSize;
    myClass = getClass().getName();
    maxTileNum = countTileNum(mmtkSpace.getExtent(), blockSize);
    control = (byte[])GCspy.util.createDataArray(new byte[0], maxTileNum);
    // to avoid allocation during GC we preallocate the streams array
    streams = new Stream[MAX_STREAMS];
    serverSpace = createServerSpace(server, name, maxTileNum, mainSpace);
  }

  /**
   * Create a subspace for this space.
   * Subspace provide useful facilities for contiguous spaces, even if
   * a space contains only one.
   * @param mmtkSpace The MMTk space
   */
  @Interruptible
  protected Subspace createSubspace(Space mmtkSpace) {
    Address start = mmtkSpace.getStart();
    return new Subspace(start, start, 0, blockSize, 0);
  }

  /**
   * Create a new GCspy ServerSpace and add it to the ServerInterpreter.
   * @param server the GCspy ServerInterpreter.
   * @param spaceName The name of this driver.
   * @param maxTileNum the maximum number of tiles in this space.
   * @param mainSpace Is this the main space?
   */
  @Interruptible
  protected ServerSpace createServerSpace(ServerInterpreter server,
                  String spaceName,
                  int maxTileNum,
                  boolean mainSpace) {
    // Set the block label
    String tmp = "Block Size: " + ((blockSize < 1024) ?
                     blockSize + " bytes\n":
                     (blockSize / 1024) + " Kbytes\n");

    // Create a single GCspy Space
    return VM.newGCspyServerSpace(server,           // the server
                                  spaceName,        // space name
                                  getDriverName(),  // driver (space) name
                                  "Block ",         // space title
                                  tmp,              // block info
                                  maxTileNum,       // number of tiles
                                  "UNUSED",         // the label for unused blocks
                                  mainSpace);       // main space
  }

  /**
   * Get the name of this driver type.
   * @return The name of this driver.
   */
  protected abstract String getDriverName();

  /**
   * Get the maximum number of tiles in this space.
   * @return the maximum number of tiles in the space.
   */
  public int getMaxTileNum() {
    return maxTileNum;
  }

  /**
   * The GCspy space managed by this driver.
   * @return the GCspy server space.
   */
  public ServerSpace getServerSpace() { return serverSpace; }

  /**
   * Add a stream to the driver. This also sets the stream's id
   * (unique for this space).
   * @param stream The stream
   * @exception IndexOutOfBoundsException if more than MAX_STREAMS are added
   */
  @Interruptible
  public void addStream(Stream stream) {
    int id = 0;
    while (id < MAX_STREAMS) {
      if (streams[id] == null) {
        streams[id] = stream;
        if (DEBUG) { Log.write("Adding stream with id="); Log.writeln(id); }
        Address stream_ = serverSpace.addStream(id);
        stream.setStream(id, stream_);
        return;
      }
      id++;
    }
    throw new IndexOutOfBoundsException("Too many streams added to driver "+name);
  }

  /**
   * Count number of tiles in an address range.
   * @param start The start of the range.
   * @param end The end of the range.
   * @param tileSize The size of each tile.
   * @return The number of tiles in this range.
   */
  protected int countTileNum(Address start, Address end, int tileSize) {
    if (end.LE(start)) return 0;
    int diff = end.diff(start).toInt();
    return countTileNum(diff, tileSize);
  }

  /**
   * Count number of tiles in an address range.
   * @param extent The extent of the range.
   * @param tileSize The size of each tile.
   * @return The number of tiles in this range.
   */
  protected int countTileNum(Extent extent, int tileSize) {
    int diff = extent.toInt();
    return countTileNum(diff, tileSize);
  }

  private int countTileNum(int diff, int tileSize) {
    int tiles = diff / tileSize;
    if ((diff % tileSize) != 0)
      ++tiles;
    return tiles;
  }

  /**
   * Indicate the limits of a space.
   *
   * @param start the Address of the start of the space.
   * @param end the Address of the end of the space.
   */
  public void setRange(Address start, Address end) {}

  /**
   * Indicate the limits of a space.
   *
   * @param start the Address of the start of the space.
   * @param extent the extent of the space.
   */
  public void setRange(Address start, Extent extent) {
    setRange(start, start.plus(extent));
  }

  /**
   * Setup the tile names in a subspace. Tile names are typically
   * address ranges but may be anything (e.g. a size class if the
   * space is a segregated free-list manager, or a class name if the
   * space represents the class instances loaded).
   *
   * @param subspace the Subspace
   * @param numTiles the number of tiles to name
   */
  protected void setTilenames(Subspace subspace, int numTiles) {
    Address start = subspace.getStart();
    int first = subspace.getFirstIndex();
    int bs = subspace.getBlockSize();

    for (int i = 0; i < numTiles; ++i) {
      if (subspace.indexInRange(i))
        serverSpace.setTilename(i, start.plus((i - first) * bs),
                                start.plus((i + 1 - first) * bs));
    }
  }

  /**
   * The "typical" maximum number of objects in each tile.
   * @param blockSize The size of a tile
   * @return The maximum number of objects in a tile
   */
  public int maxObjectsPerBlock(int blockSize) {
    // Maybe a misuse of ServerInterpreter but it's a convenient
    // VM-dependent class
    return blockSize / GCspy.server.computeHeaderSize();
  }

  /**
   * Is the server connected to a GCspy client?
   * @param event The current event
   */
  public boolean isConnected(int event) {
    return server.isConnected(event);
  }

  /**
   * Reset the statistics for a space.
   * In this base driver, we simply note that the data has changed.
   */
  protected void resetData() { changed = true; }

  /**
   * Scan an object found at a location.
   * Collectors typically call this method to update GCspy statistics.
   * The driver may or may not accumulate values found, depending on
   * the value of total.
   * @param obj the reference to the object found
   * @param total Whether to total the statistics
   */
  public void scan(ObjectReference obj, boolean total) {}

  /**
   * Scan an object found at a location.
   * Collectors typically call this method to update GCspy statistics
   * The driver will accumulate values found.
   * @param obj the reference to the object found
   */
  public void scan(ObjectReference obj) { scan(obj, true); }

  /**
   * Scan an object found at a location.
   * Collectors typically call this method to update GCspy statistics.
   * The driver may or may not accumulate values found, depending on
   * the value of total.
   * @param obj the reference to the object found
   * @param total Whether to total the statistics
   */
  public void scan(Address obj, boolean total) {}

  /**
   * Scan an object found at a location.
   * Collectors typically call this method to update GCspy statistics
   * The driver will accumulate values found.
   * @param obj the reference to the object found
   */
  public void scan(Address obj) {}

  /**
   * Handle a direct reference from the immortal space.<p>
   * This is an empty implementation. Subclasses may override this method
   * to increment their <code>refFromImmortal</code> Stream.
   *
   * @param addr The Address
   * @return {@code true} if the given Address is in this subspace. Always {@code false} here.
   */
  public boolean handleReferenceFromImmortalSpace(Address addr) {
    return false;
  }

  /**
   * Set space info.
   * This simply reports the size of the current space.
   * Drivers that want to send something more complex than
   *  "Current Size: size\n"
   * must override this method.
   *
   * @param size the size of the space
   */
  protected void setSpaceInfo(Offset size) {
    //    - sprintf(tmp, "Current Size: %s\n", gcspy_formatSize(size));
    Address tmp = GCspy.util.formatSize("Current Size: %s\n", 128, size.toInt());
    serverSpace.spaceInfo(tmp);
    GCspy.util.free(tmp);
  }


  /****************************************************************************
   *
   * Control values
   */

  /**
   * Is a tile used?
   * @param val the control value.
   * @return {@code true} if the tile is used
   */
  protected static boolean controlIsUsed(byte val) {
    return (val & CONTROL_USED) != 0;
  }

  /**
   * Is a tile a background pseudo-tile?
   * @param val the control value.
   * @return {@code true} if the tile is a background tile
   */
  protected static boolean controlIsBackground(byte val) {
    return (val & CONTROL_BACKGROUND) != 0;
  }

  /**
   * Is a tile unused?
   * @param val the control value.
   * @return {@code true} if the tile is unused
   */
  protected static boolean controlIsUnused(byte val) {
    return (val & CONTROL_UNUSED) != 0;
  }

  /**
   * Is this a separator?
   * @param val the control value.
   * @return {@code true} if this is a separator
   */
  protected static boolean controlIsSeparator(byte val) {
    return (val & CONTROL_SEPARATOR) != 0;
  }

  /**
   * Initialise the value of a control.
   * @param index The index of the tile.
   * @param value The new value of the control
   */
  protected void initControl(int index, byte value) {
    control[index] = value;
  }

  /**
   * Add a control to the tile
   * @param index The index of the tile.
   * @param value The control to add.
   */
  protected void addControl(int index, byte value) {
    control[index] |= value;
  }

  /** Set the control
   * @param value The value to set
   */
  protected void setControl(int index, byte value) {
    control[index] &= value;
  }

  /**
   * Get the controls for a tile.
   * @param index The index of the tile.
   * @return The value of the controls
   */
  public byte getControl(int index) {
    return control[index];
  }

  /**
   * Initialise control values in all tiles
   */
  protected void initControls() {
    for (int index = 0; index < control.length; ++index) {
      initControl(index, CONTROL_USED);
    }
  }

  /**
   * Set the control value in each tile in a region.
   * @param tag The control tag.
   * @param start The start index of the region.
   * @param len The number of tiles in the region.
   */
  protected void controlValues(byte tag, int start, int len) {
    if (DEBUG) {
      Log.write("AbstractDriver.controlValues for space ");
      Log.write(name);
      Log.write(", control length=", control.length);
      Log.write(" writing controls from ", start);
      Log.writeln(" to ", start + len);
    }
    changed = true;
    for (int i = start; i < (start+len); ++i) {
      // Cannot be both USED and UNUSED or BACKGROUND
      if (controlIsBackground(tag) || controlIsUnused(tag))
        setControl(i, (byte)~CONTROL_USED);
      else if (controlIsUsed(tag))
        setControl(i, (byte)~CONTROL_UNUSED);
      addControl(i, tag);
    }
  }

  /**
   * Transmit the streams for this space. A driver will typically
   * <ol>
   * <li> Determine whether a GCspy client is connected and interested in
   *      this event, e.g.
   *      <pre>server.isConnected(event)</pre>
   * <li> Setup the summaries for each stream, e.g.
   *      <pre>stream.setSummary(values...);</pre>
   * <li> Setup the control information for each tile. e.g.
   *      <pre>controlValues(CONTROL_USED, start, numBlocks);</pre>
   *      <pre>controlValues(CONTROL_UNUSED, end, remainingBlocks);</pre>
   * <li> Set up the space information, e.g.
   *      <pre>setSpace(info);</pre>
   * <li> Send the data for all streams, e.g.
   *      <pre>send(event, numTiles);</pre>
   *      Note that AbstractDriver.send takes care of sending the information
   *      for all streams (including control data).
   *
   * @param event The event
   */
  public abstract void transmit(int event);

  /**
   * Send all the streams for this space if it has changed.
   * Assume that the data has been gathered and that summary info
   * and control values have been set before this is called.
   *
   * @param event the event
   * @param numTiles the number of blocks in this space
   */
  protected void send(int event, int numTiles) {
    if (changed) {
      serverSpace.startCommunication();
      for (int i = 0; i < MAX_STREAMS; i++)
        if (streams[i] != null)
          streams[i].send(event, numTiles);
      serverSpace.sendControls(this, numTiles);
      serverSpace.endCommunication();
    }
  }
}
