/**
 ** TreadmillDriver
 **
 ** GCspy driver for JMTk LOS space
 **
 ** (C) Copyright Richard Jones, 2003
 ** Computing Laboratory, University of Kent at Canterbury
 ** All rights reserved.
 **/

package org.mmtk.utility.gcspy;

import org.mmtk.vm.gcspy.AbstractDriver;
import org.mmtk.utility.heap.FreeListVMResource;
import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;


//-#if RVM_WITH_GCSPY
import org.mmtk.plan.Plan;
import org.mmtk.policy.TreadmillLocal;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.Treadmill;
import org.mmtk.utility.Log;
import org.mmtk.vm.gcspy.Color;
import org.mmtk.vm.gcspy.AbstractTile;
import org.mmtk.vm.gcspy.Subspace;
import org.mmtk.vm.gcspy.ServerInterpreter;
import org.mmtk.vm.gcspy.ServerSpace;
import org.mmtk.vm.gcspy.Stream;
import org.mmtk.vm.gcspy.StreamConstants;
import org.mmtk.vm.VM_Interface;

//-#endif

/**
 * This class implements a simple driver for the JMTk treadmill space.
 *
 * @author <a href="http://www.ukc.ac.uk/people/staff/rej">Richard Jones</a>
 * @version $Revision$
 * @date $Date$
 */
public class TreadmillDriver extends AbstractDriver
  implements Uninterruptible {
  public final static String Id = "$Id$";

//-#if RVM_WITH_GCSPY
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
  private FreeListVMResource losVM;	// The LOS' VM_Resource

  
  /**
   * Only used by sub-classes
   */
  TreadmillDriver () {}

  /**
   * Create a new driver for this collector
   * 
   * @param name The name of this driver
   * @param losVM the VMResource for this allocator
   * @param blocksize The tile size
   * @param start The address of the start of the space
   * @param end The address of the end of the space
   * @param size The size (in blocks) of the space
   * @param threshold the size threshold of the LOS
   * @param mainSpace Is this the main space?
   */
  public TreadmillDriver(String name,
                         FreeListVMResource losVM,
                         int blockSize,
                         Address start, 
                         Address end,
                         int size,
                         int threshold,
                         boolean mainSpace) {
    
    this.losVM = losVM;
    // Set up array of tiles for max possible use
    //TODO blocksize should be a multiple of treadmill granularity
    this.blockSize = blockSize;
    maxAddr = start;
    int maxTileNum = countTileNum(start, end, blockSize);
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
		    Plan.getNextServerSpaceId(), /* space id */
		    name,                       /* server name */
		    "JMTk Treadmill Space", 	/* driver (space) name */
		    "Block ",                   /* space title */
		    tmp,                   	/* block info */
		    size,			/* number of tiles */
		    "UNUSED",                   /* the label for unused blocks */
		    mainSpace                   /* main space */ );
    setTilenames(0);
   

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
   * Zero tile stats
   */
  public void zero () {
    for (int i = 0; i < tiles.length; i++) 
      tiles[i].zero();
    totalObjects = 0;
    totalUsedSpace = 0;
  }
  
  /*
   * Debugging method to check for errors in accounting space to tiles
   * @param index the index of the tile
   * @param length the number of bytes added
   * @param err an error string
   */
  private void checkspace(int index, int length, String err) {
    if (length > 0) {
      Log.write("..added ", length);
      Log.write(" to index ", index);
      Log.writeln(", now ", tiles[index].usedSpace);
    }

    int max =  usedSpaceStream.getMaxValue();
    if (tiles[index].usedSpace > max) {
      Log.write("Treadmill.traceObject: usedSpace too high at ", index);
      Log.write(": ",tiles[index].usedSpace); 
      Log.write(", max=", max);
      Log.write(" in ");
      Log.writeln(err);
      // kludge
      tiles[index].usedSpace = max;
    }
  }
  
  /**
   * Update the tile statistics
   * In this case, we are accounting for super-page objects, rather than
   * simply for the object they contain.
   * 
   * @param addr The address of the current object
   */
  public void traceObject(Address addr) {
    Address sp = TreadmillLocal.getSuperPage(addr);
    int index = subspace.getIndex(sp);
    int length = Conversions.pagesToBytes(losVM.getSize(sp)).toInt();

    /*
    Log.write("TreadmillDriver: super=", sp);
    Log.write(", index=", index);
    int pp = losVM.getSize(sp);
    Log.write(", pages=", pp);
    int bb = Conversions.pagesToBytes(pp).toInt();
    Log.write(", bytes=", bb);
    Log.writeln(", max=", usedSpaceStream.getMaxValue());
    */

    totalObjects++;
    tiles[index].objects++;

    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(blockSize <= usedSpaceStream.getMaxValue());
      
    totalUsedSpace += length;
    int remainder = subspace.spaceRemaining(addr);
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(remainder <= blockSize);
    if (length <= remainder) {  // fits in this tile
      tiles[index].usedSpace += length;
      //checkspace(index, length, "traceObject fits in first tile"); 
    } else {
      tiles[index].usedSpace += remainder;
      //checkspace(index, remainder, "traceObject remainder put in first tile");  
      length -= remainder;
      index++;
      while (length >= blockSize) {
        tiles[index].usedSpace += blockSize;
        //checkspace(index, blockSize, "traceObject subsequent tile");
        length -= blockSize;
        index++;
      }
      tiles[index].usedSpace += length;
      //checkspace(index, length, "traceObject last tile"); 
    }
    
    if (addr.GT(maxAddr)) maxAddr = addr;
  }
  
  /**
   * Get the length of the superpage
   *
   * @param addr an address in the super-page
   * @return the length of the super-page
   */
  private int getSuperPageLength(Address addr) {
    Address sp = TreadmillLocal.getSuperPage(addr);
    return Conversions.pagesToBytes(losVM.getSize(sp)).toInt();
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
      setTilenames(allTileNum);
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

//-#else
  public TreadmillDriver(String name,
		     FreeListVMResource losVM,
		     int blockSize,
		     Address start, 
		     Address end,
		     int size,
		     int threshold,
		     boolean mainSpace) {}
//-#endif
}
