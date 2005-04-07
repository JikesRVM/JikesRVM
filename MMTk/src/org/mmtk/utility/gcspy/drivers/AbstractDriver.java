/*
 * (C) Copyright Richard Jones, 2004
 * Computing Laboratory, University of Kent at Canterbury
 * All rights reserved.
 */
package org.mmtk.utility.gcspy.drivers;

import org.mmtk.policy.Space;
import org.mmtk.utility.Log;
import org.mmtk.utility.gcspy.AbstractTile;
import org.mmtk.utility.gcspy.Subspace;
import org.mmtk.vm.gcspy.ServerSpace;
import org.mmtk.vm.gcspy.ServerInterpreter;
import org.mmtk.vm.gcspy.Util;
import org.mmtk.vm.Assert;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Abstract GCspy driver for MMTk collectors
 *
 * This class implements a base driver for the MMTk.
 *
 * $Id$
 *
 * @author <a href="http://www.ukc.ac.uk/people/staff/rej">Richard Jones</a>
 * @version $Revision$
 * @date $Date$
 */
abstract public class AbstractDriver implements Uninterruptible {
  // The tiles
  protected int blockSize;                      // tile size
  protected int allTileNum;                     // total number of tiles
  
  protected ServerSpace space;                  // The GCspy space abstraction
  
  /**
   * Count number of tiles in an address range
   * 
   * @param start The start of the range
   * @param end The end of the range
   * @param tileSize The size of each tile
   * @return The number of tiles in this range
   */
 public int countTileNum (Address start, Address end, int tileSize) {
    if (end.LE(start)) return 0;
    int diff = end.diff(start).toInt();
    return countTileNum(diff, tileSize);
  }

  /**
   * Count number of tiles in an address range
   * 
   * @param extent The extent of the range
   * @param tileSize The size of each tile
   * @return The number of tiles in this range
   */
  public int countTileNum (Extent extent, int tileSize) {
    int diff = extent.toInt();
    return countTileNum(diff, tileSize);
  }
 
 private int countTileNum (int diff, int tileSize) {
    int tiles = diff / tileSize;
    if ( (diff % tileSize) != 0 )
      ++tiles;
    return tiles;
  }
 
  /**
   * Setup tile names
   *
   * @param subspace the Subspace
   * @param numTiles the number of tiles to name
   */
  public void setTilenames(Subspace subspace, int numTiles) {
    int tile = 0;
    Address start = subspace.getStart();
    int first = subspace.getFirstIndex();
    int bs = subspace.getBlockSize();

    for (int i = 0; i < numTiles; ++i) {
      if (subspace.indexInRange(i)) 
        space.setTilename(i, start.add((i - first) * bs), 
                             start.add((i + 1 - first) * bs));
    }
  }
 
  /**
   * The "typical" number of objects in each tile
   *
   * @param blockSize The size of a tile
   * @return The maximum number of objects in a tile
   */
  public int maxObjectsPerBlock (int blockSize) {
    // Maybe a misuse of ServerInterpreter but its a convenient
    // VM-dependent class
    return blockSize / ServerInterpreter.computeHeaderSize();
  }
  
  /**
   * Should we transmit data to the visualiser?
   * 
   * @param event The current event
   * @return true if we should transmit
   */
  public boolean shouldTransmit(int event) {
    return ServerInterpreter.shouldTransmit(event);
  }
    
  /**
   * Set the control value in each tile in a region
   * 
   * @param tiles an array of tiles
   * @param tag The control tag
   * @param start The start index of the region
   * @param len The number of tiles in the region
   */
  public void controlValues (AbstractTile[] tiles, byte tag, int start, 
                             int len) {
    for (int i = start; i < (start+len); ++i) {
      if (AbstractTile.controlIsBackground(tag) ||
          AbstractTile.controlIsUnused(tag)) {
        if (AbstractTile.controlIsUsed(tiles[i].getControl()))
          tiles[i].setControl((byte)~AbstractTile.CONTROL_USED);
      }
      tiles[i].addControl(tag);
    }
  }
  
  /**
   * Collectors typically call this method to update GCspy stats
   * @param obj the reference to the object found
   * @param total Whether to total the statistics
   */
  public void traceObject(ObjectReference obj, boolean total) {}

  /**
   * Collectors typically call this method to update GCspy stats
   * @param obj the reference to the object found
   */
  public void traceObject(ObjectReference obj) { traceObject(obj, true); }

  /**
   * Collectors typically call this method to update GCspy stats
   * @param obj the reference to the object found
   * @param total Whether to total the statistics
   */
  public void traceObject(Address obj, boolean total) {}

  /**
   * Collectors typically call this method to update GCspy stats
   * @param obj the reference to the object found
   */
  public void traceObject(Address obj) { traceObject(obj, true); }
                    
  /**
   * Collectors typically call this method to update GCspy stats
   * @param obj the reference to the object found
   */
  public void scan(ObjectReference obj) {}

  /**
   * Indicate the limits of a space
   *
   * @param start the Address of the start of the space
   * @param end the Address of the end of the space
   */
  public void setRange(Address start, Address end) {}

  /**
   * Indicate the limits of a space
   *
   * @param start the Address of the start of the space
   * @param extent the extent of the space
   */
  public void setRange(Address start, Extent extent) {
    setRange(start, start.add(extent));
  }

  /**
   * Zero the statistics for a space
   */
  public void zero() {}

  /*
   * Debugging method to check for errors in accounting space to tiles
   * @param index the index of the tile
   * @param length the number of bytes added
   * @param err an error string
  private void checkspace(int index, int length, String err) {
    if (length > 0) {
      Log.write("..added "); Log.write(length);
      Log.write(" to index "); Log.write(index);
      Log.writeln(", now "); Log.write(tiles[index].usedSpace);
    }

    int max =  usedSpaceStream.getMaxValue();
    if (tiles[index].usedSpace > max) {
      Log.write("Treadmill.traceObject: usedSpace too high at ");
      Log.write(index);
      Log.write(": "); Log.write(tiles[index].usedSpace); 
      Log.write(", max="); Log.write(max);
      Log.write(" in ");
      Log.writeln(err);
      // kludge
      tiles[index].usedSpace = max;
    }
  }
   */

  /**
   * Distribute an object's space across a sequence of tiles
   * @param tiles the tiles to distribute over
   * @param subspace the subspace
   * @param blockSize the size of each tile
   * @param streamID a stream ID
   * @param start the starting address
   * @param length the lenght to distribute
   */
   public static void distributeSpace(AbstractTile[] tiles, 
                               Subspace subspace,
                               int blockSize, 
                               int streamID,
                               Address start,
                               int length) {

     int index = subspace.getIndex(start);
     int remainder = subspace.spaceRemaining(start);
     if (Assert.VERIFY_ASSERTIONS) Assert._assert(remainder <= blockSize);
     if (length <= remainder) {  // fits in this tile
       tiles[index].addSpace(streamID, length);
       //checkspace(index, length, "traceObject fits in first tile"); 
     } else {
       tiles[index].addSpace(streamID, remainder);
       //checkspace(index, remainder, "traceObject remainder put in first tile");  
       length -= remainder;
       index++;
       while (length >= blockSize) {
         tiles[index].addSpace(streamID, blockSize);
         //checkspace(index, blockSize, "traceObject subsequent tile");
         length -= blockSize;
         index++;
       }
       tiles[index].addSpace(streamID, length);
       //checkspace(index, length, "traceObject last tile"); 
     }
   }

  /**
   * Send space info and end communication
   * This simply sends the size of the current space.
   * Drivers that want to send something more complex than 
   *  "Current Size: <size>\n"
   * must override this method.
   *
   * @param size the size of the space
   */
  protected void sendSpaceInfoAndEndComm(Offset size) {
    //    - sprintf(tmp, "Current Size: %s\n", gcspy_formatSize(size));
    Address tmp = Util.formatSize("Current Size: %s\n", 128, size.toInt());
    space.spaceInfo(tmp); 
    space.endComm();
    Util.free(tmp);
  }
}
