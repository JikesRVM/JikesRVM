/**
 ** ImmortalDriver
 **
 ** GCspy driver for JMTk immortal space
 **
 ** (C) Copyright Richard Jones, 2003
 ** Computing Laboratory, University of Kent at Canterbury
 ** All rights reserved.
 **/

package org.mmtk.utility.gcspy;

import org.mmtk.vm.gcspy.AbstractDriver;
import org.mmtk.utility.heap.MonotoneVMResource;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;


//-#if RVM_WITH_GCSPY
import org.mmtk.utility.Log;
import org.mmtk.vm.gcspy.Color;
import org.mmtk.vm.gcspy.AbstractTile;
import org.mmtk.vm.gcspy.Subspace;
import org.mmtk.vm.gcspy.ServerInterpreter;
import org.mmtk.vm.gcspy.ServerSpace;
import org.mmtk.vm.gcspy.Stream;
import org.mmtk.vm.gcspy.StreamConstants;
import org.mmtk.vm.Plan;
import org.mmtk.vm.Assert;
//-#endif

/**
 * This class implements a simple driver for the JMTk treadmill space.
 *
 * @author <a href="http://www.ukc.ac.uk/people/staff/rej">Richard Jones</a>
 * @version $Revision$
 * @date $Date$
 */
public class ImmortalSpaceDriver extends AbstractDriver
  implements Uninterruptible {
  public final static String Id = "$Id$";

//-#if RVM_WITH_GCSPY
  private static final int IMMORTAL_USED_SPACE_STREAM = 0;	// stream IDs
  private static final int BUFSIZE = 128;		// scratch buffer size

  /**
   * Representation of a tile
   * We count the number of objects in each tile and the space they use
   */
  class Tile extends AbstractTile 
    implements  Uninterruptible {
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
      usedSpace = 0;
    }
  }

  // The streams
  private Stream usedSpaceStream;


  private Tile[] tiles;			// the space's tiles
  private Subspace subspace;		// there is only one subspace here

  // Overall statistics
  private int totalUsedSpace = 0;	// total space used

  private Address maxAddr;		// the largest address seen
  private MonotoneVMResource immVM;	// the VM_Resource for the immortal space

  
  /**
   * Create a new driver for this collector
   * 
   * @param name The name of this driver
   * @param immVM the VMResource for this allocator
   * @param blocksize The tile size
   * @param start The address of the start of the space
   * @param end The address of the end of the space
   * @param size The size (in blocks) of the space
   * @param mainSpace Is this the main space?
   */
  public ImmortalSpaceDriver(String name,
                             MonotoneVMResource immVM,
                             int blockSize,
                             Address start, 
                             Address end,
                             int size,
                             boolean mainSpace) {
    
    this.immVM = immVM;
    // Set up array of tiles for max possible use
    //TODO blocksize should be a multiple of treadmill granularity
    this.blockSize = blockSize;
    maxAddr = start;
    int maxTileNum = countTileNum(start, end, blockSize);
    tiles = new Tile[maxTileNum];
    //Log.writeln("ImmortalDriver: no of tiles=", maxTileNum);
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
		    "JMTk Immortal Space", 	/* driver (space) name */
		    "Block ",                   /* space title */
		    tmp,                   	/* block info */
		    size,			/* number of tiles */
		    "UNUSED",                   /* the label for unused blocks */
		    mainSpace                   /* main space */ );
    setTilenames(0);
   

    // Initialise the Space's 1 Stream1
    usedSpaceStream 
           = new Stream(
	             space, 					/* the space */
		     IMMORTAL_USED_SPACE_STREAM,		/* space ID */
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
    totalUsedSpace = 0;
  }

  private void checkspace(int index, String loc) {
    if (tiles[index].usedSpace > usedSpaceStream.getMaxValue()) {
      Log.write("Immortal.traceObject: usedSpace too high at ", index);
      Log.write(": ",tiles[index].usedSpace); 
      Log.write(", max=", usedSpaceStream.getMaxValue());
      Log.write(" in ");
      Log.writeln(loc);
      // FIXME temporary kludge
      tiles[index].usedSpace = usedSpaceStream.getMaxValue();
    }
  }
  

  /**
   * Indicate the limits of a space
   * Bump pointer allocators typically call this to report the limits
   * of a space's range, since this is all they can discover until we 
   * can sweep through this space (either by laying out arrays and
   * scalars in the same direction, or by segregating scalars and arrays).
   *
   * @param event the event
   * @param start the Address of the start of the space
   * @param end the Address of the end of the space
   */
  public void setRange(int event, Address start, Address end) {
    int index = subspace.getIndex(start);
    int length = end.diff(start).toInt();

    if (Assert.VERIFY_ASSERTIONS) Assert._assert(blockSize <= usedSpaceStream.getMaxValue());
      
    totalUsedSpace = length;
    int remainder = subspace.spaceRemaining(start);
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(remainder <= blockSize);
    if (length <= remainder) {  // fits in this tile
      tiles[index].usedSpace = length;
      checkspace(index, "setRange fits in first tile"); 
    } else {
      tiles[index].usedSpace = remainder;
      checkspace(index, "setRange remainder put in first tile");  
      length -= remainder;
      index ++;
      while (length >= blockSize) {
        tiles[index].usedSpace = blockSize;
        checkspace(index, "setRange subsequent tile");
        length -= blockSize;
        index++;
      }
      tiles[index].usedSpace += remainder;
      checkspace(index, "setRange last tile"); 
    }
    
    if (end.GT(maxAddr)) maxAddr = end;
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
      setTilenames(allTileNum);
    }
    
    // start the communication
    space.startComm();
    int numTiles = allTileNum;   
    //Log.writeln("ImmortalDriver: sending, allTileNum=", numTiles);
    
   // Used Space stream
   // send the stream data
    space.stream(IMMORTAL_USED_SPACE_STREAM, numTiles);
    for (int i = 0; i < numTiles; ++i) {
      checkspace(i, "send");
      space.streamIntValue(tiles[i].usedSpace);
    }
    space.streamEnd();
    // send the summary data 
    space.summary(IMMORTAL_USED_SPACE_STREAM, 2 /*items to send*/);
    space.summaryValue(totalUsedSpace);
    space.summaryValue(subspace.getEnd().diff(subspace.getStart()).toInt());
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
  public ImmortalSpaceDriver(String name,
		     MonotoneVMResource immVM,
		     int blockSize,
		     Address start, 
		     Address end,
		     int size,
		     boolean mainSpace) {}
//-#endif
}
