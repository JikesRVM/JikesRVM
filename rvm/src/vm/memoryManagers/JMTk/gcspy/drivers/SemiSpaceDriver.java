/**
 ** SemiSpaceDriver
 **
 ** GCspy driver for the JMTk SemiSpace collector
 **
 ** (C) Copyright Richard Jones, 2003
 ** Computing Laboratory, University of Kent at Canterbury
 ** All rights reserved.
 **/

package org.mmtk.utility.gcspy;

import org.mmtk.vm.Assert;
import org.mmtk.vm.gcspy.AbstractDriver;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;


//-#if RVM_WITH_GCSPY
import com.ibm.JikesRVM.VM_Magic;
import org.mmtk.vm.Plan;
import org.mmtk.vm.gcspy.Color;
import org.mmtk.vm.gcspy.AbstractTile;
import org.mmtk.vm.gcspy.Subspace;
import org.mmtk.vm.gcspy.ServerInterpreter;
import org.mmtk.vm.gcspy.ServerSpace;
import org.mmtk.vm.gcspy.Stream;
import org.mmtk.vm.gcspy.StreamConstants;
import org.mmtk.vm.Assert;

import com.ibm.JikesRVM.classloader.VM_Type;  // FIXME => MMType !



//-#endif


/**
 * This class implements a simple driver for the JMTk SemiSpace copying collector.
 *
 * @author <a href="http://www.ukc.ac.uk/people/staff/rej">Richard Jones</a>
 * @version $Revision$
 * @date $Date$
 */
public class SemiSpaceDriver extends AbstractDriver
  implements Uninterruptible {
  public final static String Id = "$Id$";

//-#if RVM_WITH_GCSPY
  private static final int SS_SCALAR_USED_SPACE_STREAM = 0;	// stream IDs
  private static final int SS_ARRAY_USED_SPACE_STREAM = 1;
  private static final int SS_SCALAR_OBJECTS_STREAM   = 2;
  private static final int SS_ARRAY_OBJECTS_STREAM   = 3;

  /**
   * Representation of a tile
   *
   * We only count the number of objects in each tile and the space they use
   */
  class Tile extends AbstractTile 
    implements  Uninterruptible {
   
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
      super.zero();
      scalarObjects = 0;
      scalarUsedSpace = 0;
      arrayObjects = 0;
      arrayUsedSpace = 0;
    }
  }

  // The streams
  Stream scalarUsedSpaceStream;
  Stream arrayUsedSpaceStream;
  Stream scalarObjectsStream;
  Stream arrayObjectsStream;

  // The semispaces
  private Subspace[] subspace = new Subspace[2];	// 2 semispaces
  private final int numSubspaces = 2;  			// number of semispaces

  // Overall statistics for a semispace
  private int totalScalarObjects = 0;		// total number of objects allocated
  private int totalArrayObjects = 0;		
  private int totalScalarUsedSpace = 0;		// total space used
  private int totalArrayUsedSpace = 0;		
 
  // The tiles
  private Tile[] tiles;				// the space's tiles
  private static final int MIDDLE_TILES = 0;	// some tiles between the semispaces
   

  /**
   * Create a new driver for this collector
   * 
   * @param name The name of this driver
   * @param blocksize The tile size
   * @param start0 The address of the start of semispace0
   * @param end0 The address of the end of semispace0
   * @param start1 The address of the start of semispace1
   * @param end1 The address of the end of semispace1
   * @param size The size in blocks of the space
   * @param mainSpace Is this the main space?
   */
  public SemiSpaceDriver 
                    (String name,
		     int blockSize,
		     Address start0, 
		     Address end0,   
		     Address start1,
		     Address end1,
		     int size,
		     boolean mainSpace ) {
    
    // Set up drivers for each Space (in this case only 1)
  
    // Set up array of tiles for max possible use
    this.blockSize = blockSize;
    int tileNum0 = countTileNum(start0, end0, blockSize);
    int tileNum1 = countTileNum(start1, end1, blockSize);
    int tileNum = tileNum0 + tileNum1;      	 // tile num for the two spaces...
    int maxTileNum = tileNum + MIDDLE_TILES; 	// ...plus the middle tiles
    tiles = new Tile[maxTileNum];
    for(int i = 0; i < maxTileNum; i++)
      tiles[i] = new Tile();

    // Set up two semispaces. For now, these are of zero length: the collector 
    // must call resize() before gathering data
    subspace[0] = new Subspace(start0, start0, 0, blockSize, 0);
    subspace[1] = new Subspace(start1, start1, tileNum0 + MIDDLE_TILES, blockSize, 0);
    allTileNum = MIDDLE_TILES;

    // Set the block label
    String tmp = (blockSize < 1024) ?
                   "Block Size: " + blockSize + " bytes\n":
                   "Block Size: " + (blockSize / 1024) + " bytes\n";

    
    // Create a single GCspy Space
    space = new ServerSpace(
		    Plan.getNextServerSpaceId(), /* space id */
		    name,                       /* server name */
		    "JMTk Semispace GC", 	/* driver (space) name */
		    "Block ",                   /* space title */
		    tmp,                   	/* block info */
		    size,			/* number of tiles */
		    "UNUSED",                   /* the label for unused blocks */
		    mainSpace                   /* main space */ );
    setTilenames(0);
   

    // Initialise the Space's 4 Streams
    scalarUsedSpaceStream 
           = new Stream(
	             space, 					/* the space */
		     SS_SCALAR_USED_SPACE_STREAM,		/* space ID */
		     StreamConstants.INT_TYPE,			/* stream data type */
		     "Scalar Used Space stream",		/* stream name */
		     0, 					/* min. data value */
		     blockSize,					/* max. data value */
		     0, 					/* zero value */
		     0,						/* default value */
		    "Space used by scalars: ", 			/* value prefix */
		    " bytes",					/* value suffix */
		     StreamConstants.PRESENTATION_PERCENT,	/* presentation style */
		     StreamConstants.PAINT_STYLE_ZERO, 		/* paint style */
		     0,						/* max stream index */
		     Color.Red);				/* tile colour */

    arrayUsedSpaceStream 
           = new Stream(
	             space, 					/* the space */
		     SS_ARRAY_USED_SPACE_STREAM,		/* space ID */
		     StreamConstants.INT_TYPE,			/* stream data type */
		     "Array Used Space stream",			/* stream name */
		     0, 					/* min. data value */
		     blockSize,					/* max. data value */
		     0, 					/* zero value */
		     0,						/* default value */
		    "Space used by arrays: ", 			/* value prefix */
		    " bytes",					/* value suffix */
		     StreamConstants.PRESENTATION_PERCENT,	/* presentation style */
		     StreamConstants.PAINT_STYLE_ZERO, 		/* paint style */
		     0,						/* max stream index */
		     Color.Blue);				/* tile colour */

    scalarObjectsStream = new Stream(
	             space, 
		     SS_SCALAR_OBJECTS_STREAM,
		     StreamConstants.SHORT_TYPE,
		     "Scalar Objects stream",
		     0, 
		     jikesObjectsPerBlock(blockSize),
		     0, 
		     0,
		     "No. of scalar objects = ", 
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
		     jikesObjectsPerBlock(blockSize),
		     0, 
		     0,
		     "No. of array objects = ", 
		     " objects",
		     StreamConstants.PRESENTATION_PLUS,
		     StreamConstants.PAINT_STYLE_ZERO, 
		     0,
		     Color.Cyan);

    // Initialise the statistics
    zero();
  }
		      
  /**
   * Setup tile names
   *
   * @param numTiles the number of tiles to name
   */
  private void setTilenames(int numTiles) {
    int tile = 0;
    Address start0 = subspace[0].getStart();
    int first0 = subspace[0].getFirstIndex();
    int bs0 = subspace[0].getBlockSize();
    Address start1 = subspace[1].getStart();
    int first1 = subspace[1].getFirstIndex();
    int bs1 = subspace[1].getBlockSize();

    for (int i = 0; i < numTiles; ++i) {
      if (subspace[0].indexInRange(i)) 
        space.setTilename(i, start0.add((i - first0) * bs0), 
	                     start0.add((i + 1 - first0) * bs0));
      else if (subspace[1].indexInRange(i)) 
        space.setTilename(i, start1.add((i - first1) * bs1), 
	                     start1.add((i + 1 - first1) * bs1));
    }

  }
   
   
  /**
   * Zero tile stats
   */
  public void zero () {
    for (int i = 0; i < tiles.length; i++) {
      Assert._assert(tiles[i] != null);
      tiles[i].zero();
    }
    totalScalarObjects = 0;
    totalScalarUsedSpace = 0;
    totalArrayObjects = 0;
    totalArrayUsedSpace = 0;
  }
  
  /**
   * Set the current range of a semispace
   *
   * @param semi the semispace
   * @param start the start of the lower semispace
   * @param end the end of the lower semispace
   */
  public void setRange(int semi, Address start, Address end) {
    int current = subspace[semi].getBlockNum();
    int other = subspace[1-semi].getBlockNum();
    int required = countTileNum(start, end, subspace[semi].getBlockSize());

    // Reset the subspaces 
    if(required != current) {
      if (semi == 0) {
        subspace[0].reset(start, end, 0, required);
	subspace[1].reset(required + MIDDLE_TILES, other); 
      } else {
        subspace[1].reset(start, end, other + MIDDLE_TILES, required);
      }
    }
    
    // we need to reset the driver
    if (allTileNum != required + other + MIDDLE_TILES) {
      allTileNum = required + other + MIDDLE_TILES;
      space.resize(allTileNum);
      setTilenames(allTileNum);
    }

    /*
    Log.write("\nSemiSpaceDriver.setRange for semispace ", semi);
    Log.write(": low: ", subspace[0].getFirstIndex());
    Log.write("-", subspace[0].getBlockNum());
    Log.write(", high: ", subspace[1].getFirstIndex());
    Log.write("-", subspace[1].getBlockNum());
    Log.writeln(", allTileNum=", allTileNum);
    */
  }


  /**
   * Update the tile statistics
   * 
   * @param addr The address of the current object
   */
  public void traceObject(Address addr) {
    traceObject(addr, true);
  }
  
  /**
   * Update the tile statistics
   * 
   * @param addr The address of the current object
   * @param total Whether to total the statistics
   */
  public void traceObject(Address addr, boolean total) {
    // get length of object and determine if it's an array
    Object obj = (Object) VM_Magic.addressAsObject(addr);
    VM_Type type = VM_Magic.getObjectType(obj);
    boolean isArray = type.isArrayType();
    int length = getLength(obj, type, isArray);
    
    // Update the stats
    for (int a = 0; a < numSubspaces; a++) {
      if (subspace[a].addressInRange(addr)) {
        int index = subspace[a].getIndex(addr);
	if (isArray) {
	  tiles[index].arrayObjects++;
	  tiles[index].arrayUsedSpace += length;
	  Assert._assert(tiles[index].arrayUsedSpace < arrayUsedSpaceStream.getMaxValue());
	  if (total) {
	    totalArrayObjects++;
	    totalArrayUsedSpace += length;
	  }
	} else {
	  tiles[index].scalarObjects++;
	  tiles[index].scalarUsedSpace += length;
	  Assert._assert(tiles[index].arrayUsedSpace < arrayUsedSpaceStream.getMaxValue());
	  if (total) {
	    totalScalarObjects++;
	    totalScalarUsedSpace += length;
	  }
	}
	return;
      } 
    }
  }

  /**
   * Finish a transmission
   * 
   * @param event The event, either BEFORE_COLLECTION or AFTER_COLLECTION
   * @param tospace The currently used semispace
   */
  public void finish (int event, int tospace) {
    if (ServerInterpreter.isConnected(event)) {
      //Log.write("CONNECTED\n");
      send(event, tospace);
    }
  }

  /**
   * Send the data for an event
   * 
   * @param event The event, either BEFORE_COLLECTION or AFTER_COLLECTION
   * @param tospace The currently used semispace
   */
  private void send (int event, int tospace) {
    
    // start the communication
    space.startComm();
    int numTiles = allTileNum;   
    
    // (1) Scalar Used Space stream
    // send the stream data 
    space.stream(SS_SCALAR_USED_SPACE_STREAM, numTiles);
    for (int i = 0; i < numTiles; ++i) {
      space.streamIntValue(tiles[i].scalarUsedSpace);
    }
    space.streamEnd();
    // send the summary data 
    space.summary(SS_SCALAR_USED_SPACE_STREAM, 2 /*items to send*/);
    space.summaryValue(totalScalarUsedSpace);
    space.summaryValue(subspace[tospace].getEnd().diff(subspace[tospace].getStart()).toInt());
    space.summaryEnd();

    // (2) Array Used Space stream
    space.stream(SS_ARRAY_USED_SPACE_STREAM, numTiles);
    for (int i = 0; i < numTiles; ++i) {
      space.streamIntValue(tiles[i].arrayUsedSpace);
    }
    space.streamEnd();
    space.summary(SS_ARRAY_USED_SPACE_STREAM, 2);
    space.summaryValue(totalArrayUsedSpace);
    space.summaryValue(subspace[tospace].getEnd().diff(subspace[tospace].getStart()).toInt());
    space.summaryEnd();

    // (3) Scalar Objects stream
    space.stream(SS_SCALAR_OBJECTS_STREAM, numTiles);
    for (int i = 0; i < numTiles; ++i) {
      space.streamShortValue(tiles[i].scalarObjects);
    }
    space.streamEnd();
    space.summary(SS_SCALAR_OBJECTS_STREAM, 1);
    space.summaryValue(totalScalarObjects);
    space.summaryEnd();

    // (4) Array Objects stream
    space.stream(SS_ARRAY_OBJECTS_STREAM, numTiles);
    for (int i = 0; i < numTiles; ++i) {
      space.streamShortValue(tiles[i].arrayObjects);
    }
    space.streamEnd();
    space.summary(SS_ARRAY_OBJECTS_STREAM, 1);
    space.summaryValue(totalArrayObjects);
    space.summaryEnd();

    // send the control info
    // all of tospace is USED
    controlValues(tiles, AbstractTile.CONTROL_USED,
		  subspace[tospace].getFirstIndex(),
		  subspace[tospace].getBlockNum());

    // all of the other space is UNUSED
    //controlValues(tiles, AbstractTile.CONTROL_UNUSED,
    // all of the other space is USED is we want to see garbage left over in fromspace
    controlValues(tiles, AbstractTile.CONTROL_USED,
		  subspace[1-tospace].getFirstIndex(),
		  subspace[1-tospace].getBlockNum());

    // any tiles between the two spaces are BACKGROUND colour
    controlValues(tiles, AbstractTile.CONTROL_BACKGROUND,
		  subspace[0].getFirstIndex() + subspace[0].getBlockNum(),
		  MIDDLE_TILES);
    // add a separator after the end of the first semispace
    controlValues(tiles, AbstractTile.CONTROL_SEPARATOR,
		  subspace[1].getFirstIndex(), 
		  1);
    space.controlEnd(numTiles, tiles);     
    
    // send the space info and end 
    Offset size = subspace[tospace].getEnd().diff(subspace[tospace].getStart());
    sendSpaceInfoAndEndComm(size);
  }

//-#else
  public SemiSpaceDriver 
                    (String name,
		     int blockSize,
		     Address start0, 
		     Address end0,   
		     Address start1,
		     Address end1,
		     int size,
		     boolean mainSpace ) {}
  public void traceObject(Address addr, boolean total) {}
  public void finish(int event, int semi) {}
//-#endif
}
