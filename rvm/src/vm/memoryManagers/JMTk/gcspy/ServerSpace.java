/**
 ** ServerSpace.java
 **
 ** (C) Copyright Richard Jones, 2003
 ** Computing Laboratory, University of Kent at Canterbury
 ** All rights reserved.
 **/

package org.mmtk.vm.gcspy;

import org.mmtk.utility.Log;

import com.ibm.JikesRVM.VM_SysCall;


import org.vmmagic.unboxed.*;

/**
 * ServerSpace.java
 *
 * This class implements the GCspy Space abstraction.
 * Here, it largely to forward calls to the gcspy C library.
 *
 * @author <a href="http://www.ukc.ac.uk/people/staff/rej">Richard Jones</a>
 * @version $Revision$
 * @date $Date$
 */
public class ServerSpace 
  implements  Uninterruptible {
  public final static String Id = "$Id$";
  
  private static final String DEFAULT_UNUSED_STRING = "NOT USED";	// The "unused" string

  private final Address driver_;               	// the c driver, gcspy_gc_driver_t *driver;
  private final int id_;				// the space's ID
  private static final boolean DEBUG_ = false;
  

  /**
   * Create a new GCspy Space
   *
   * @param id	The space's ID
   * @param serverName The server's name
   * @param driverName The space driver's name
   * @param title Title for the space
   * @param blockInfo A label for each block
   * @param tileNum Number of tiles
   * @param unused A label for unused blocks
   * @param mainSpace Whether this space is the main space
   */
  public ServerSpace(int id, 
	      String serverName, 
	      String driverName,
	      String title,
	      String blockInfo,
	      int tileNum,
	      String unused, 
	      boolean mainSpace    ) {
    
    driver_ = VM_SysCall.gcspyMainServerAddDriver(ServerInterpreter.getServerAddress());
    this.id_ = id;
    
    // Convert Strings to char *
    Address serverName_ = Util.getBytes(serverName);
    Address driverName_ = Util.getBytes(driverName);
    Address title_      = Util.getBytes(title);
    Address blockInfo_  = Util.getBytes(title);
    Address unused_ =     Util.getBytes((unused == null) ? DEFAULT_UNUSED_STRING
                                                      : unused);
    
    // Add the driver to the server and initialise it
    if (DEBUG_)
      Log.writeln("--   Setting up driver");
    VM_SysCall.gcspyDriverInit(driver_, -1, serverName_, driverName_,
	   	               title_, blockInfo_, tileNum, 
		               unused_, mainSpace ? 1 : 0 );
  }

  /**
   * Pass in the tile names
   *
   * @param i the number of the tile
   * @param start the starting address of the tile
   * @param end the end address
   */
  public void setTilename(int i, Address start, Address end) {
    VM_SysCall.gcspyDriverSetTileName(driver_, i, start, end);
  }

  /**
   * Add a stream to the driver
   *
   * @param id The stream's ID
   * @return The address of the stream, gcspy_gc_stream_t *
   */
  Address addStream(int id) {
    return VM_SysCall.gcspyDriverAddStream(driver_, id);
  }

  /**
   * Driver address
   * 
   * @return The address of the stream, gcspy_gc_stream_t *
   */
  Address getDriverAddress() {
    return driver_;
  }
  
  /**
   * Resize the driver
   *
   * @param size the new driver size
   */
  public void resize(int size) {
    VM_SysCall.gcspyDriverResize(driver_, size);
  }

  // Interface to the GCspy C library -----------------------------------------------
  
  /** 
   * Start a transmission
   */
  public void startComm() {
    VM_SysCall.gcspyDriverStartComm(driver_);
  }

  /**
   * Start transmitting a stream
   * 
   * @param id The stream's id
   * @param len The number of items in the stream
   */
  public void stream(int id, int len) {
    VM_SysCall.gcspyDriverStream(driver_, id, len);
  }

  /**
   * Send a byte
   * 
   * @param value The byte
   */
  public void streamByteValue(byte value) {
    VM_SysCall.gcspyDriverStreamByteValue(driver_, value);
  }

  /**
   * Send a short
   * 
   * @param value The short
   */
  public void streamShortValue(short value) {
    VM_SysCall.gcspyDriverStreamShortValue(driver_, value);
  }
  
  /**
   * Send an int
   * 
   * @param value The int
   */
  public void streamIntValue(int value) {
     VM_SysCall.gcspyDriverStreamIntValue(driver_, value);
  }

  /**
   * End of this stream
   */
  public void streamEnd () {
    VM_SysCall.gcspyDriverEndOutput(driver_);
  }

  /**
   * Start to send a summary
   *
   * @param id The stream's id
   * @param len The number of items to be sent
   */
  public void summary (int id, int len) {
    VM_SysCall.gcspyDriverSummary(driver_, id, len);
  }

  /**
   * Send a summary value
   *
   * @param val The value
   */
  public void summaryValue (int val) {
    VM_SysCall.gcspyDriverSummaryValue(driver_, val);
  }

  /**
   * End the summary
   */
  public void summaryEnd () {
    VM_SysCall.gcspyDriverEndOutput(driver_);
  }

  /**
   * Send all the control info for the space
   * 
   * @param tileNum The number of tiles
   * @param tiles The array of tiles
   */
  public void controlEnd (int tileNum, AbstractTile[] tiles) {
    // start the stream
    VM_SysCall.gcspyDriverInitOutput(driver_);
    VM_SysCall.gcspyIntWriteControl(
	                    driver_/* NOTE driver->interpreter in sys.C*/, 
	 	            id_, 
		            tileNum); 
    
    // send a control for each tile
    for (int i = 0; i < tileNum; ++i) {
      streamByteValue(tiles[i].getControl());
    }

    // end the stream
    VM_SysCall.gcspyDriverEndOutput(driver_);    
  }

  /**
   * Send space info for this space
   * 
   * @param info The info
   */
  public void spaceInfo (Address info) {
    VM_SysCall.gcspyDriverSpaceInfo(driver_, info);
  }

  /**
   * End the transmission for this event
   */
  public void endComm() {
    /* Just now a NOP */
    if (DEBUG_)
      Log.write("endComm\n");
  }
    
}
