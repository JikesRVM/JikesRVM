/*
 * (C) Copyright Richard Jones, 2004
 * Computing Laboratory, University of Kent at Canterbury
 * All rights reserved.
 */
package org.mmtk.utility.gcspy.drivers;

import org.mmtk.policy.LargeObjectSpace;
import org.mmtk.utility.gcspy.AbstractTile;
import org.mmtk.utility.gcspy.Color;
import org.mmtk.utility.gcspy.StreamConstants;
import org.mmtk.utility.gcspy.Subspace;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.Log;
import org.mmtk.vm.Assert;
import org.mmtk.vm.Plan;
import org.mmtk.vm.gcspy.ServerInterpreter;
import org.mmtk.vm.gcspy.ServerSpace;
import org.mmtk.vm.gcspy.Stream;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;


/**
 * This class implements a simple driver for the MMTk LargeObjectSpace.
 *
 * $Id$ 
 *
 * @author <a href="http://www.ukc.ac.uk/people/staff/rej">Richard Jones</a>
 * @version $Revision$
 * @date $Date$
 */
public class TreadmillDriver extends AbstractDriver 
  implements Uninterruptible {

  private static final int LOS_USED_SPACE_STREAM = 0;	// stream IDs
  private static final int LOS_OBJECTS_STREAM   = 1;
  private static final int BUFSIZE = 128;		// scratch buffer size

  /**
   * Representation of a tile
   *
   * We count the number of objects in each tile and the space they use
   */
  class Tile extends AbstractTile 
    implements  Uninterruptible {
    short objects;
    int usedSpace;
    
    /**
     * Create a new tile with statistics zeroed
     */
    public Tile() {
      zero();
    }
    
    /**
     * Zero a tile's statistics
     */
    public void zero() {
      super.zero();
      objects = 0;
      usedSpace = 0;
    }
    
    /**
     * Add some space
     */
    public void addSpace(int id, int size) {
      usedSpace += size;
    }
  
  }

  // The streams
  private Stream usedSpaceStream;
  private Stream objectsStream;


  private Tile[] tiles;			// the space's tiles
  private Subspace subspace;			

  // Overall statistics
  private int totalObjects = 0;		// total number of objects allocated
  private int totalUsedSpace = 0;	// total space used

  private Address maxAddr;		// the largest address seen
  private LargeObjectSpace lospace;	// the large object space
  private int threshold;

  
  /**
   * Create a new driver for this collector
   * 
   * @param name The name of this driver
   * @param lospace the large object space for this allocator
   * @param blockSize The tile size
   * @param threshold the size threshold of the LOS
   * @param mainSpace Is this the main space?
   */
  public TreadmillDriver(String name,
                         LargeObjectSpace lospace,
                         int blockSize,
                         int threshold,
                         boolean mainSpace) {
    
    this.lospace = lospace;
    this.threshold = threshold;
    Address start = lospace.getStart();
    Extent extent = lospace.getExtent();

    // Set up array of tiles for max possible use
    //TODO blocksize should be a multiple of treadmill granularity
    this.blockSize = blockSize;
    maxAddr = start;
    int maxTileNum = countTileNum(extent, blockSize);
    tiles = new Tile[maxTileNum];
    //Log.writeln("TreadmillDriver: no of tiles=", maxTileNum);
    for(int i = 0; i < maxTileNum; i++)
      tiles[i] = new Tile();

    subspace = new Subspace(start, start, 0, blockSize, 0); 
    allTileNum = 0;
    
    // Set the block label
    String tmp = (blockSize < 1024) ?
                   "Block Size: " + blockSize + " bytes\n":
                   "Block Size: " + (blockSize / 1024) + " bytes\n";

    
    // Create a single GCspy Space
    space = new ServerSpace(
		    Plan.getNextServerSpaceId(),/* space id */
		    name,                       /* server name */
		    "MMTk LargeObjectSpace", 	/* driver (space) name */
		    "Block ",                   /* space title */
		    tmp,                   	/* block info */
		    //size,			/* number of tiles */
		    maxTileNum,			/* number of tiles */
		    "UNUSED",                   /* the label for unused blocks */
		    mainSpace                   /* main space */ );
    setTilenames(subspace, 0);
   

    // Initialise the Space's 2 Streams
    usedSpaceStream 
           = new Stream(
	             space, 					/* the space */
		     LOS_USED_SPACE_STREAM,			/* space ID */
		     StreamConstants.INT_TYPE,			/* stream data type */
		     "Used Space stream",			/* stream name */
		     0, 					/* min. data value */
		     blockSize,					/* max. data value */
		     0, 					/* zero value */
		     0,						/* default value */
		    "Space used: ", 				/* value prefix */
		    " bytes",					/* value suffix */
		     StreamConstants.PRESENTATION_PERCENT,	/* presentation style */
		     StreamConstants.PAINT_STYLE_ZERO, 		/* paint style */
		     0,						/* max stream index */
		     Color.Red);				/* tile colour */


   objectsStream = new Stream(
	             space, 
		     LOS_OBJECTS_STREAM,
		     StreamConstants.SHORT_TYPE,
		     "Objects stream",
		     0, 
		     (int)(blockSize/threshold),
		     0, 
		     0,
		     "No. of objects = ", 
		     " objects",
		     StreamConstants.PRESENTATION_PLUS,
		     StreamConstants.PAINT_STYLE_ZERO, 
		     0,
		     Color.Green);

    space.resize(0);
    // Initialise the statistics
    zero();
  }
		      
   
  /**
   * Zero tile stats
   */
  public void zero () {
    for (int i = 0; i < tiles.length; i++) 
      tiles[i].zero();
    totalObjects = 0;
    totalUsedSpace = 0;
  }
  
  /**
   * Set the current range of LOSpace
   * @param start the start of the space
   * @param end the end of the space
   *
  public void setRange(Address start, Address end) {
    int current = subspace.getBlockNum();
    int required = countTileNum(start, end, blockSize);

    // Reset the subspace
    //if (required != current) { 
      subspace.reset(start, end, 0, required);
      //Log.write("TreadmillDriver: reset subspace, start=", start);
      //Log.write(" end=", end);
      //Log.write(" required=", required);
      //Log.writeln(" was ", current);
      allTileNum = required;
      space.resize(allTileNum);
      setTilenames(allTileNum);
    //}
  }
   */
   

  /**
   * Update the tile statistics
   * In this case, we are accounting for super-page objects, rather than
   * simply for the object they contain.
   * 
   * @param addr The address of the superpage
   */
  public void traceObject(Address addr) {
    
    int index = subspace.getIndex(addr);
    int length = lospace.getSize(addr).toInt();

    /*
    Log.write("TreadmillDriver: super=", sp);
    Log.write(", index=", index);
    int pp = lospace.getSize(sp);
    Log.write(", pages=", pp);
    int bb = Conversions.pagesToBytes(pp).toInt();
    Log.write(", bytes=", bb);
    Log.writeln(", max=", usedSpaceStream.getMaxValue());
    */

    totalObjects++;
    tiles[index].objects++;
    totalUsedSpace += length;
    distributeSpace(tiles, subspace, subspace.getBlockSize(), 
                    LOS_USED_SPACE_STREAM, addr, length);
    Address tmp = addr.add(length);
    if (tmp.GT(maxAddr)) maxAddr = tmp;
  }
  

  /**
   * Finish a transmission
   * 
   * @param event The event
   */
  public void finish (int event) {
    if (ServerInterpreter.isConnected(event)) {
      //Log.write("CONNECTED\n");
      send(event);
    }
  }

  
  /**
   * Send the data for an event
   * 
   * @param event The event
   */
  private void send (int event) {
    // at this point, we've filled the tiles with data
    // however, we don't know the size of the space

    // Calculate the highest indexed tile used so far, and update the subspace
    Address start = subspace.getStart();
    int required = countTileNum(start, maxAddr, blockSize);
    int current = subspace.getBlockNum();
    if (required > current || maxAddr != subspace.getEnd()) {
      subspace.reset(start, maxAddr, 0, required);
      allTileNum = required;
      space.resize(allTileNum);
      setTilenames(subspace, allTileNum);
    }
    
    // start the communication
    space.startComm();
    int numTiles = allTileNum;   
    //Log.writeln("TreadmillDriver: sending, allTileNum=", numTiles);
    
   // (1) Used Space stream
   // send the stream data
    space.stream(LOS_USED_SPACE_STREAM, numTiles);
    for (int i = 0; i < numTiles; ++i) {
      //checkspace(i, 0, "send");
      if (Assert.VERIFY_ASSERTIONS) 
        if (tiles[i].usedSpace > subspace.getBlockSize()) {
	  Log.write("Bad value for TreadmillDriver Used Space stream: ");
	  Log.write(tiles[i].usedSpace);
	  Log.writeln(" max=", subspace.getBlockSize());
	  // Assert._assert(false);
	}
      space.streamIntValue(tiles[i].usedSpace);
    }
    space.streamEnd();
    // send the summary data 
    space.summary(LOS_USED_SPACE_STREAM, 2 /*items to send*/);
    space.summaryValue(totalUsedSpace);
    space.summaryValue(subspace.getEnd().diff(subspace.getStart()).toInt());
    space.summaryEnd();

    // (2) Objects stream
    space.stream(LOS_OBJECTS_STREAM, numTiles);
    for (int i = 0; i < numTiles; ++i) {
      if (Assert.VERIFY_ASSERTIONS) 
        if (tiles[i].objects > subspace.getBlockSize()/threshold) {
	  Log.write("Bad value for TreadmillDriver Objects stream: ");
	  Log.write(tiles[i].usedSpace);
	  Log.writeln(" max=", (int)(subspace.getBlockSize()/threshold));
	  // Assert._assert(false);
	}
      space.streamShortValue(tiles[i].objects);
    }
    space.streamEnd();
    space.summary(LOS_OBJECTS_STREAM, 1);
    space.summaryValue(totalObjects);
    space.summaryEnd();
   
    // send the control info
    // all of space is USED
    controlValues(tiles, AbstractTile.CONTROL_USED,
		  subspace.getFirstIndex(),
		  subspace.getBlockNum());

    space.controlEnd(numTiles, tiles);     
    
    // send the space info
    Offset size = subspace.getEnd().diff(subspace.getStart());
    sendSpaceInfoAndEndComm(size);
  }
}
