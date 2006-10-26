/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Richard Jones, 2003-6
 * Computing Laboratory, University of Kent at Canterbury
 */
package com.ibm.jikesrvm.mm.mmtk.gcspy;

import org.mmtk.utility.Log;
import com.ibm.jikesrvm.VM_SysCall;
import org.mmtk.utility.gcspy.GCspy;
import org.mmtk.utility.gcspy.drivers.AbstractDriver;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * ServerSpace.java
 *
 * This class implements the GCspy Space abstraction.
 * Here, it largely to forward calls to the gcspy C library.
 *
 * $Id: ServerSpace.java 10806 2006-09-22 12:17:46Z dgrove-oss $
 *
 * @author <a href="http://www.ukc.ac.uk/people/staff/rej">Richard Jones</a>
 * @version $Revision: 10806 $
 * @date $Date: 2006-09-22 13:17:46 +0100 (Fri, 22 Sep 2006) $
 */
public class ServerSpace extends org.mmtk.vm.gcspy.ServerSpace implements  Uninterruptible { 

  /**
   * Create a new GCspy Space
   *
   * @param serverInterpreter The server that owns this space
   * @param serverName The server's name
   * @param driverName The space driver's name
   * @param title Title for the space
   * @param blockInfo A label for each block
   * @param tileNum Max number of tiles in this space
   * @param unused A label for unused blocks
   * @param mainSpace Whether this space is the main space
   */
  public ServerSpace (
              org.mmtk.vm.gcspy.ServerInterpreter serverInterpreter,
              String serverName, 
              String driverName,
              String title,
              String blockInfo,
              int tileNum,
              String unused, 
              boolean mainSpace) throws InterruptiblePragma {
    if (VM_SysCall.WITH_GCSPY) {
      spaceId = serverInterpreter.addSpace(this);
      driver = VM_SysCall.gcspyMainServerAddDriver(serverInterpreter.getServerAddress());
      
      // Convert Strings to char *
      Address serverNameAddr = GCspy.util.getBytes(serverName);
      Address driverNameAddr = GCspy.util.getBytes(driverName);
      Address titleAddr      = GCspy.util.getBytes(title);
      Address blockInfoAddr  = GCspy.util.getBytes(blockInfo);
      Address unusedAddr     = GCspy.util.getBytes((unused == null) 
                                                ? DEFAULT_UNUSED_STRING
                                                : unused);
      
      // Add the driver to the server and initialise it
      if (DEBUG) Log.writeln("--   Setting up driver");
      VM_SysCall.gcspyDriverInit(driver, -1, serverNameAddr, driverNameAddr,
                                 titleAddr, blockInfoAddr, tileNum, 
                                 unusedAddr, mainSpace ? 1 : 0 );
    }
  }

  
  /****************************************************************************
   *
   * Interface to the GCspy C library
   */


  /**
   * Tell the native driver the tile name.
   * @param i the number of the tile
   * @param start the starting address of the tile
   * @param end the end address
   */
  public void setTilename(int i, Address start, Address end) {
    if (VM_SysCall.WITH_GCSPY) 
      VM_SysCall.gcspyDriverSetTileNameRange(driver, i, start, end);
  }

  /**
   * Tell the native driver the tile name.
   * @param i the number of the tile
   * @param format the name of the tile, a format string
   * @param value The value for the format string
   */
  public void setTilename(int i, Address format, long value) {
    if (VM_SysCall.WITH_GCSPY) 
      VM_SysCall.gcspyDriverSetTileName(driver, i, format, value);
  }

  /**
   * Tell the native driver the tile names.
   * @param i the number of the tile
   * @param format The name, including format tags
   * @param value The value for the format string
   */
  public void setTilename(int i, String format, long value) {
    if (VM_SysCall.WITH_GCSPY) {
      Address tileName = GCspy.util.getBytes(format);
      setTilename(i, tileName, value);
      GCspy.util.free(tileName);
    }
  }
  
  /**
   * Tell the C driver to resize
   * @param size the new driver size
   */
  public void resize(int size) {
    if (VM_SysCall.WITH_GCSPY) 
      VM_SysCall.gcspyDriverResize(driver, size);
  }

  /** 
   * Start a transmission
   */
  public void startCommunication() {
    if (VM_SysCall.WITH_GCSPY) 
      VM_SysCall.gcspyDriverStartComm(driver);
  }

  /**
   * Add a stream to the native driver
   * @param streamId the stream's ID
   * @return the address of the C gcspy_gc_stream_t
   */
  public Address addStream(int streamId) {
    if (VM_SysCall.WITH_GCSPY) 
      return VM_SysCall.gcspyDriverAddStream(driver, streamId);
    else 
      return Address.zero();
  }

  /**
   * Start transmitting a stream.
   * @param streamId The stream's ID
   * @param len The number of items in the stream
   */
  public void stream(int streamId, int len) {
    if (VM_SysCall.WITH_GCSPY) 
      VM_SysCall.gcspyDriverStream(driver, streamId, len);
  }

  /**
   * Send a byte
   * @param value The byte
   */
  public void streamByteValue(byte value) {
    if (VM_SysCall.WITH_GCSPY) 
      VM_SysCall.gcspyDriverStreamByteValue(driver, value);
  }

  /**
   * Send a short
   * @param value The short
   */
  public void streamShortValue(short value) {
    if (VM_SysCall.WITH_GCSPY) 
      VM_SysCall.gcspyDriverStreamShortValue(driver, value);
  }
  
  /**
   * Send an int
   * @param value The int
   */
  public void streamIntValue(int value) {
    if (VM_SysCall.WITH_GCSPY) 
      VM_SysCall.gcspyDriverStreamIntValue(driver, value);
  }

  /**
   * End of this stream
   */
  public void streamEnd () {
    if (VM_SysCall.WITH_GCSPY) 
      VM_SysCall.gcspyDriverEndOutput(driver);
  }

  /**
   * Start to send a summary
   * @param streamId The stream's ID
   * @param len The number of items to be sent
   */
  public void summary (int streamId, int len) {
    if (VM_SysCall.WITH_GCSPY) 
      VM_SysCall.gcspyDriverSummary(driver, streamId, len);
  }

  /**
   * Send a summary value
   * @param val The value
   */
  public void summaryValue (int val) {
    VM_SysCall.gcspyDriverSummaryValue(driver, val);
  }

  /**
   * End the summary
   */
  public void summaryEnd () {
    if (VM_SysCall.WITH_GCSPY) 
      VM_SysCall.gcspyDriverEndOutput(driver);
  }

  /**
   * Send all the control info for the space
   * @param space The GCspy driver for this space
   * @param tileNum The number of tiles
   */
  public void sendControls (AbstractDriver space, int tileNum) {
    // start the stream
    if (VM_SysCall.WITH_GCSPY) {
      VM_SysCall.gcspyDriverInitOutput(driver);
      VM_SysCall.gcspyIntWriteControl(
                              driver/* NOTE driver->interpreter in sys.C*/, 
                              spaceId, 
                              tileNum); 
      
      // send a control for each tile
      for (int i = 0; i < tileNum; ++i) 
        streamByteValue(space.getControl(i));
  
      // end the stream
      VM_SysCall.gcspyDriverEndOutput(driver);  
    }
  }

  /**
   * Send info for this space
   * @param info A pointer to the information (held as C string)
   */
  public void spaceInfo (Address info) {
    if (VM_SysCall.WITH_GCSPY) 
      VM_SysCall.gcspyDriverSpaceInfo(driver, info);
  }

}
