/*
 * (C) Copyright Richard Jones, 2004
 * Computing Laboratory, University of Kent at Canterbury
 * All rights reserved.
 */
package org.mmtk.utility.gcspy.drivers;

import org.mmtk.plan.SemiSpaceGCspy;
import org.mmtk.policy.Space;
import org.mmtk.utility.scan.MMType;
import org.mmtk.utility.gcspy.AbstractTile;
import org.mmtk.utility.gcspy.Color;
import org.mmtk.utility.gcspy.LinearScan;
import org.mmtk.utility.gcspy.StreamConstants;
import org.mmtk.utility.gcspy.Subspace;
import org.mmtk.utility.Log;
import org.mmtk.vm.Plan;
import org.mmtk.vm.gcspy.ServerInterpreter;
import org.mmtk.vm.gcspy.ServerSpace;
import org.mmtk.vm.gcspy.Stream;
import org.mmtk.vm.ObjectModel;
import org.mmtk.vm.Assert;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * GCspy driver for the MMTk ContigousSpace
 *
 * This class implements a simple driver for the MMTk SemiSpace
 * copying collector.
 *
 * $Id$
 *
 * @author <a href="http://www.ukc.ac.uk/people/staff/rej">Richard Jones</a>
 * @version $Revision$
 * @date $Date$
 */
public class ContiguousSpaceDriver extends AbstractDriver
  implements Uninterruptible {

  private static final int SS_SCALAR_USED_SPACE_STREAM = 0;     // stream IDs
  private static final int SS_ARRAY_USED_SPACE_STREAM = 1;
  private static final int SS_SCALAR_OBJECTS_STREAM   = 2;
  private static final int SS_ARRAY_OBJECTS_STREAM   = 3;

  /**
   * Representation of a tile
   *
   * We only count the number of objects in each tile and the space they use
   */
  class Tile extends AbstractTile implements  Uninterruptible {
   
    short scalarObjects;
    int scalarUsedSpace;
    short arrayObjects;
    int arrayUsedSpace;     

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
      scalarObjects = 0;
      scalarUsedSpace = 0;
      arrayObjects = 0;
      arrayUsedSpace = 0;
    }
    
    /**
     * add some used space
     */
    public void addSpace(int id, int size) {
      if (Assert.VERIFY_ASSERTIONS) 
        Assert._assert(id == SS_SCALAR_USED_SPACE_STREAM ||
                       id == SS_ARRAY_USED_SPACE_STREAM,
                       "Bad tag given to addSpace");
      if (id == SS_SCALAR_USED_SPACE_STREAM) 
        scalarUsedSpace += size;
      else if (id == SS_ARRAY_USED_SPACE_STREAM) 
        arrayUsedSpace += size;
    }
  }

  // The streams
  Stream scalarUsedSpaceStream;
  Stream arrayUsedSpaceStream;
  Stream scalarObjectsStream;
  Stream arrayObjectsStream;


  // Debugging
  Address lastAddress = Address.zero();
  int lastSize = 0;

  // The semispaces
  private Subspace subspace;

  // Overall statistics for a semispace
  private int totalScalarObjects = 0;           // total number of objects allocated
  private int totalArrayObjects = 0;            
  private int totalScalarUsedSpace = 0;         // total space used
  private int totalArrayUsedSpace = 0;          
 
  // The tiles
  private Tile[] tiles;                         // the space's tiles

  // A LinearScan 
  private final LinearScan scanner;
   

  /**
   * Create a new driver for this collector
   * 
   * @param name The name of this driver
   * @param sp The space
   * @param blocksize The tile size
   * @param mainSpace Is this the main space?
   */
  public ContiguousSpaceDriver 
                    (String name,
                     Space sp,
                     int blockSize,
                     boolean mainSpace ) {
    
    // Set up array of tiles for max possible use
    this.blockSize = blockSize;
    Address start = sp.getStart();
    Extent extent = sp.getExtent();
    int maxTileNum = countTileNum(extent, blockSize);

    /*
    Log.write("ContiguousSpaceDriver for ");
    Log.write(name);
    Log.write(", blocksize=", blockSize);
    Log.write(", start=", start); Log.write(", extent=", extent);
    Log.writeln(", maxTileNum=", maxTileNum);
    */

    tiles = new Tile[maxTileNum];
    for(int i = 0; i < maxTileNum; i++)
      tiles[i] = new Tile();

    // Set up semispace. For now,  of zero length: the collector 
    // must call resize() before gathering data
    //TODO do we need subspaces?
    subspace = new Subspace(start, start, 0, blockSize, 0);
    allTileNum = 0;

    // Set the block label
    String tmp = (blockSize < 1024) ?
                   "Block Size: " + blockSize + " bytes\n":
                   "Block Size: " + (blockSize / 1024) + " bytes\n";

    
    // Create a single GCspy Space
    space = new ServerSpace(
                    SemiSpaceGCspy.getNextServerSpaceId(), /* space id */
                    name,                       /* server name */
                    "MMTk ContiguousSpace GC",  /* driver (space) name */
                    "Block ",                   /* space title */
                    tmp,                        /* block info */
                    maxTileNum,                 /* number of tiles */
                    "UNUSED",                   /* the label for unused blocks */
                    mainSpace                   /* main space */ );
    setTilenames(subspace, 0);
   

    // Initialise the Space's 4 Streams
    scalarUsedSpaceStream 
           = new Stream(
                     space,                                     /* the space */
                     SS_SCALAR_USED_SPACE_STREAM,               /* space ID */
                     StreamConstants.INT_TYPE,                  /* stream data type */
                     "Scalar Used Space stream",                /* stream name */
                     0,                                         /* min. data value */
                     blockSize,                                 /* max. data value */
                     0,                                         /* zero value */
                     0,                                         /* default value */
                    "Space used by scalars and primitive arrays: ",     /* value prefix */
                    " bytes",                                   /* value suffix */
                     StreamConstants.PRESENTATION_PERCENT,      /* presentation style */
                     StreamConstants.PAINT_STYLE_ZERO,          /* paint style */
                     0,                                         /* max stream index */
                     Color.Red);                                /* tile colour */

    arrayUsedSpaceStream 
           = new Stream(
                     space,                                     /* the space */
                     SS_ARRAY_USED_SPACE_STREAM,                /* space ID */
                     StreamConstants.INT_TYPE,                  /* stream data type */
                     "Array Used Space stream",                 /* stream name */
                     0,                                         /* min. data value */
                     blockSize,                                 /* max. data value */
                     0,                                         /* zero value */
                     0,                                         /* default value */
                    "Space used by reference arrays: ",         /* value prefix */
                    " bytes",                                   /* value suffix */
                     StreamConstants.PRESENTATION_PERCENT,      /* presentation style */
                     StreamConstants.PAINT_STYLE_ZERO,          /* paint style */
                     0,                                         /* max stream index */
                     Color.Blue);                               /* tile colour */

    scalarObjectsStream = new Stream(
                     space, 
                     SS_SCALAR_OBJECTS_STREAM,
                     StreamConstants.SHORT_TYPE,
                     "Scalar Objects stream",
                     0, 
                     // Say, max value = 50% of max possible
                     maxObjectsPerBlock(blockSize)/2,
                     0, 
                     0,
                     "No. of scalar and primitive array objects = ", 
                     " objects",
                     StreamConstants.PRESENTATION_PLUS,
                     StreamConstants.PAINT_STYLE_ZERO, 
                     0,
                     Color.Green);

   arrayObjectsStream = new Stream(
                     space, 
                     SS_ARRAY_OBJECTS_STREAM,
                     StreamConstants.SHORT_TYPE,
                     "Array Objects stream",
                     0, 
                     // Say, typical ref array size = 4 * typical scalar size?
                     maxObjectsPerBlock(blockSize)/8,
                     0, 
                     0,
                     "No. of reference array objects = ", 
                     " objects",
                     StreamConstants.PRESENTATION_PLUS,
                     StreamConstants.PAINT_STYLE_ZERO, 
                     0,
                     Color.Cyan);

    space.resize(0);
    // Initialise the statistics
    zero();

    scanner = new LinearScan(this);
  }
                      
  /**
   * BumpPointer.linearScan needs a LinearScan object, which we provide here.
   * @return the scanner for this driver
   */
   public LinearScan getScanner() { return scanner; }
   

  /**
   * Zero tile stats
   */
  public void zero () {
    for (int i = 0; i < tiles.length; i++) {
      tiles[i].zero();
    }
    totalScalarObjects = 0;
    totalScalarUsedSpace = 0;
    totalArrayObjects = 0;
    totalArrayUsedSpace = 0;

    if (Assert.VERIFY_ASSERTIONS) {
      lastAddress = Address.zero();
      lastSize = 0;
    } 
  }
  
  /**
   * Set the current range of a semispace
   *
   * @param start the start of the lower semispace
   * @param end the end of the lower semispace
   */
  public void setRange(Address start, Address end) {
    int current = subspace.getBlockNum();
    int required = countTileNum(start, end, subspace.getBlockSize());

    // Reset the subspaces 
    if(required != current) 
      subspace.reset(start, end, 0, required);
    
    /*
    Log.write("\nContiguousSpaceDriver.setRange for semispace: ");
    Log.write(subspace.getFirstIndex()); Log.write("-", subspace.getBlockNum());
    Log.write(" (", start); Log.write("-", end); Log.write(")");
    */
    
    // Reset the driver
    // FIXME As far as I can see, release() only resets a CopySpace's
    // cursor (and optionally zeroes or mprotects the pages); it doesn't
    // make the pages available to other spaces. See SemiSpaceGCspy.gcspyGatherData
    // If pages really are released, change the test here to 
    // if (allTileNum != required) {
    if (allTileNum < required) {
      //Log.write(", resize from ", allTileNum); Log.write(" to ", required);
      allTileNum = required;
      space.resize(allTileNum);
      setTilenames(subspace, allTileNum);
    }
  }


  /**
   * Update the tile statistics
   * 
   * @param addr The address of the current object
   */
  public void scan(ObjectReference obj) {
    traceObject(obj, true);
  }

  /**
   * Update the tile statistics
   * 
   * @param obj The current object
   */
  public void traceObject(ObjectReference obj) {
    traceObject(obj, true);
  }
  
  /**
   * Update the tile statistics
   * 
   * @param obj The current object
   * @param total Whether to total the statistics
   */
  public void traceObject(ObjectReference obj, boolean total) {
    // get length of object and determine if it's an array
    MMType type = ObjectModel.getObjectType(obj);
    // VM_Type would say whether array; MMType won't, so we'll just show
    // reference arrays
    boolean isArray = type.isReferenceArray();
    int length = ObjectModel.getCurrentSize(obj);
    
    // Update the stats
    Address addr = obj.toAddress();
    
    if (Assert.VERIFY_ASSERTIONS) {
      if(addr.LT(lastAddress.add(lastSize))) {
        Log.write("ContiguousSpaceDriver finds addresses going backwards: ");
        Log.write("last="); Log.write(lastAddress);
        Log.write("last size="); Log.write(lastSize);
        Log.writeln("current=", addr);
      }
      lastAddress = addr;
      lastSize = length;
    } 

    if (subspace.addressInRange(addr)) {
      int index = subspace.getIndex(addr);
      if (isArray) {
        tiles[index].arrayObjects++;
        distributeSpace(tiles, subspace, subspace.getBlockSize(), SS_ARRAY_USED_SPACE_STREAM, addr, length);
        if (total) {
          totalArrayObjects++;
          totalArrayUsedSpace += length;
        }
      } else {
        tiles[index].scalarObjects++;
        distributeSpace(tiles, subspace, subspace.getBlockSize(), SS_SCALAR_USED_SPACE_STREAM, addr, length);
        if (total) {
          totalScalarObjects++;
          totalScalarUsedSpace += length;
        }
      }
      return;
    } 
  }

  /**
   * Finish a transmission
   * 
   * @param event The event, either BEFORE_COLLECTION or AFTER_COLLECTION
   */
  public void finish (int event) {
    //Log.writeln("\nContinuousSpaceDriver.finish ", event);
    if (ServerInterpreter.isConnected(event)) {
      //Log.write("CONNECTED\n");
      send(event);
    }
  }

  /**
   * Send the data for an event
   * 
   * @param event The event, either BEFORE_COLLECTION or AFTER_COLLECTION
   */
  private void send (int event) {
    /*
    Log.write("ContiguousSpaceDriver.send: numTiles=", allTileNum);
    Log.writeln(", tiles.length=", tiles.length);
    Log.flush();
    */
    // start the communication
    space.startComm();
    int numTiles = allTileNum;   
    
    // (1) Scalar Used Space stream
    // send the stream data 
    space.stream(SS_SCALAR_USED_SPACE_STREAM, numTiles);
    for (int i = 0; i < numTiles; ++i) {
      // Presentation style is not PRESENTATION_PLUS so we can check
      if (Assert.VERIFY_ASSERTIONS) 
        if (tiles[i].scalarUsedSpace > scalarUsedSpaceStream.getMaxValue()) {
	  Log.write("Bad value for ContiguousSpaceDriver Scalar Used Space stream: ");
	  Log.write(tiles[i].scalarUsedSpace);
	  Log.writeln(" max=", scalarUsedSpaceStream.getMaxValue());
	  // Assert._assert(false);
	}
      space.streamIntValue(tiles[i].scalarUsedSpace);
    }
    space.streamEnd();
    // send the summary data 
    space.summary(SS_SCALAR_USED_SPACE_STREAM, 2 /*items to send*/);
    space.summaryValue(totalScalarUsedSpace);
    space.summaryValue(subspace.getEnd().diff(subspace.getStart()).toInt());
    space.summaryEnd();

    // (2) Array Used Space stream
    space.stream(SS_ARRAY_USED_SPACE_STREAM, numTiles);
    for (int i = 0; i < numTiles; ++i) {
      // Presentation style is not PRESENTATION_PLUS so we can check
      if (Assert.VERIFY_ASSERTIONS) 
        if (tiles[i].arrayUsedSpace > arrayUsedSpaceStream.getMaxValue()) {
	  Log.write("Bad value for ContiguousSpaceDriver Array Used Space stream: ");
	  Log.write(tiles[i].arrayUsedSpace);
	  Log.writeln(" max=",  arrayUsedSpaceStream.getMaxValue());
	  // Assert._assert(false);
	}
      space.streamIntValue(tiles[i].arrayUsedSpace);
    }
    space.streamEnd();
    space.summary(SS_ARRAY_USED_SPACE_STREAM, 2);
    space.summaryValue(totalArrayUsedSpace);
    space.summaryValue(subspace.getEnd().diff(subspace.getStart()).toInt());
    space.summaryEnd();

    // (3) Scalar Objects stream
    space.stream(SS_SCALAR_OBJECTS_STREAM, numTiles);
    for (int i = 0; i < numTiles; ++i) {
      // Presentation style is PRESENTATION_PLUS so we cannot check max value
      space.streamShortValue(tiles[i].scalarObjects);
    }
    space.streamEnd();
    space.summary(SS_SCALAR_OBJECTS_STREAM, 1);
    space.summaryValue(totalScalarObjects);
    space.summaryEnd();

    // (4) Array Objects stream
    space.stream(SS_ARRAY_OBJECTS_STREAM, numTiles);
    for (int i = 0; i < numTiles; ++i) {
      // Presentation style is PRESENTATION_PLUS so we cannot check max value
      space.streamShortValue(tiles[i].arrayObjects);
    }
    space.streamEnd();
    space.summary(SS_ARRAY_OBJECTS_STREAM, 1);
    space.summaryValue(totalArrayObjects);
    space.summaryEnd();

    // send the control info
    int numBlocks = subspace.getBlockNum();
    controlValues(tiles, AbstractTile.CONTROL_USED,
		  subspace.getFirstIndex(),
		  numBlocks);
    if (numBlocks < numTiles) 
      controlValues(tiles, AbstractTile.CONTROL_UNUSED,
		  subspace.getFirstIndex() + numBlocks,
		  numTiles - numBlocks);

    space.controlEnd(numTiles, tiles);     
    
    // send the space info and end 
    Offset size = subspace.getEnd().diff(subspace.getStart());
    sendSpaceInfoAndEndComm(size);
  }
}
