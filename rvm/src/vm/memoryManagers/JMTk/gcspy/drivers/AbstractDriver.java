/**
 ** AbstractDriver
 **
 ** Abstract GCspy driver for JMTk collectors
 **
 ** (C) Copyright Richard Jones, 2003
 ** Computing Laboratory, University of Kent at Canterbury
 ** All rights reserved.
 **/

package org.mmtk.vm.gcspy;

import com.ibm.JikesRVM.classloader.VM_Type;

import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Offset;
import com.ibm.JikesRVM.VM_Magic;


/**
 * This class implements a base driver for the JMTk.
 *
 * @author <a href="www.ukc.ac.uk/people/staff/rej">Richard Jones</a>
 * @version $Revision$
 * @date $Date$
 */
abstract public class AbstractDriver
  implements VM_Uninterruptible {
  public final static String Id = "$Id$";

  // The tiles
  protected int blockSize;			// tile size
  protected int allTileNum;			// total number of tiles
  
  protected ServerSpace space;			// The GCspy space abstraction
  
  /**
   * Count number of tiles in an address range
   * 
   * @param start The start of the range
   * @param end The end of the range
   * @param tileSize The size of each tile
   * @return The number of tiles in this range
   */
 public int countTileNum (VM_Address start, VM_Address end, int tileSize) {
    if (end.LE(start)) return 0;
    int diff = end.diff(start).toInt();
    int tiles = diff / tileSize;
    if ( (diff % tileSize) != 0 )
      ++tiles;
    return tiles;
  }
 
  /**
   * The "typical" number of objects in each tile in Jikes RVM
   *
   * @param blocksize The size of a tile
   * @return The maximum number of objects in a tile
   */
  public int jikesObjectsPerBlock (int blockSize) {
    return blockSize / 32;
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
   * @param tag The control tag
   * @param start The start index of the region
   * @param len The number of tiles in the region
   */
  public void controlValues (AbstractTile[] tiles, byte tag, int start, int len) {
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
   * Get the size of an object
   *
   * @param obj the object
   * @param type the object's type
   * @param isArray is the object an array
   * @return the length of the object in bytes
   */
  public int getLength(Object obj, VM_Type type, boolean isArray) {
    if (isArray) {
      int numElements = VM_Magic.getArrayLength(obj);
      return type.asArray().getInstanceSize(numElements);
    } else {
       return type.asClass().getInstanceSize();
    }
  }

  /**
   * Collectors typically call this method to update GCSpy stats
   *
   * @param addr the address of the object found
   */
  public void traceObject(VM_Address addr) {} 
                    
  /**
   * Indicate the limits of a space
   * Bump pointer allocators typically call this to report the limits
   * of a space's range, since this is all they can discover until we 
   * can sweep through this space (either by laying out arrays and
   * scalars in the same direction, or by segregating scalars and arrays).
   *
   * @param event the event
   * @param start the VM_Address of the start of the space
   * @param end the VM_Address of the end of the space
   */
  public void setRange(int event, VM_Address start, VM_Address end) {}

  /**
   * Send space info and end communication
   * This simply sends the size of the current space.
   * Drivers that want to send something more complex than 
   *  "Current Size: <size>\n"
   * must override this method.
   *
   * @param size the size of the space
   */
  protected void sendSpaceInfoAndEndComm(VM_Offset size) {
    //	  - sprintf(tmp, "Current Size: %s\n", gcspy_formatSize(size));
    VM_Address tmp = Util.malloc(BUFSIZE);
    VM_Address formattedSize = Util.malloc(BUFSIZE);
    VM_Address currentSize = Util.getBytes("Current Size: %s\n"); 
    Util.formatSize(formattedSize, size.toInt());
    Util.sprintf(tmp, currentSize, formattedSize);
    
    space.spaceInfo(tmp); 
    space.endComm();

    Util.free(currentSize);
    Util.free(formattedSize);
    Util.free(tmp);
  }

  private static final int BUFSIZE = 128;
}
