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
package org.jikesrvm.mm.mmtk.gcspy;

import org.mmtk.utility.Log;
import static org.jikesrvm.runtime.SysCall.sysCall;
import org.jikesrvm.VM;
import org.mmtk.utility.gcspy.GCspy;
import org.mmtk.utility.gcspy.drivers.AbstractDriver;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * ServerSpace.java
 *
 * This class implements the GCspy Space abstraction.
 * Here, it largely to forward calls to the gcspy C library.
 */
@Uninterruptible public class ServerSpace extends org.mmtk.vm.gcspy.ServerSpace {

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
  public ServerSpace(
              org.mmtk.vm.gcspy.ServerInterpreter serverInterpreter,
              String serverName,
              String driverName,
              String title,
              String blockInfo,
              int tileNum,
              String unused,
              boolean mainSpace) {
    if (VM.BuildWithGCSpy) {
      spaceId = serverInterpreter.addSpace(this);
      driver = sysCall.gcspyMainServerAddDriver(serverInterpreter.getServerAddress());

      // Convert Strings to char *
      Address serverNameAddr = GCspy.util.getBytes(serverName);
      Address driverNameAddr = GCspy.util.getBytes(driverName);
      Address titleAddr      = GCspy.util.getBytes(title);
      Address blockInfoAddr  = GCspy.util.getBytes(blockInfo);
      Address unusedAddr     = GCspy.util.getBytes((unused == null) ? DEFAULT_UNUSED_STRING : unused);

      // Add the driver to the server and initialise it
      if (DEBUG) Log.writeln("--   Setting up driver");
      sysCall.gcspyDriverInit(driver, -1, serverNameAddr, driverNameAddr,
                                 titleAddr, blockInfoAddr, tileNum,
                                 unusedAddr, mainSpace ? 1 : 0);
    }
  }


  /****************************************************************************
   *
   * Interface to the GCspy C library
   */


  @Override
  public void setTilename(int i, Address start, Address end) {
    if (VM.BuildWithGCSpy)
      sysCall.gcspyDriverSetTileNameRange(driver, i, start, end);
  }

  @Override
  public void setTilename(int i, Address format, long value) {
    if (VM.BuildWithGCSpy)
      sysCall.gcspyDriverSetTileName(driver, i, format, value);
  }

  @Override
  public void setTilename(int i, String format, long value) {
    if (VM.BuildWithGCSpy) {
      Address tileName = GCspy.util.getBytes(format);
      setTilename(i, tileName, value);
      GCspy.util.free(tileName);
    }
  }

  @Override
  public void resize(int size) {
    if (VM.BuildWithGCSpy)
      sysCall.gcspyDriverResize(driver, size);
  }

  @Override
  public void startCommunication() {
    if (VM.BuildWithGCSpy)
      sysCall.gcspyDriverStartComm(driver);
  }

  @Override
  public Address addStream(int streamId) {
    if (VM.BuildWithGCSpy)
      return sysCall.gcspyDriverAddStream(driver, streamId);
    else
      return Address.zero();
  }

  @Override
  public void stream(int streamId, int len) {
    if (VM.BuildWithGCSpy)
      sysCall.gcspyDriverStream(driver, streamId, len);
  }

  @Override
  public void streamByteValue(byte value) {
    if (VM.BuildWithGCSpy)
      sysCall.gcspyDriverStreamByteValue(driver, value);
  }

  @Override
  public void streamShortValue(short value) {
    if (VM.BuildWithGCSpy)
      sysCall.gcspyDriverStreamShortValue(driver, value);
  }

  @Override
  public void streamIntValue(int value) {
    if (VM.BuildWithGCSpy)
      sysCall.gcspyDriverStreamIntValue(driver, value);
  }

  @Override
  public void streamEnd() {
    if (VM.BuildWithGCSpy)
      sysCall.gcspyDriverEndOutput(driver);
  }

  @Override
  public void summary(int streamId, int len) {
    if (VM.BuildWithGCSpy)
      sysCall.gcspyDriverSummary(driver, streamId, len);
  }

  @Override
  public void summaryValue(int val) {
    sysCall.gcspyDriverSummaryValue(driver, val);
  }

  @Override
  public void summaryEnd() {
    if (VM.BuildWithGCSpy)
      sysCall.gcspyDriverEndOutput(driver);
  }

  @Override
  public void sendControls(AbstractDriver space, int tileNum) {
    // start the stream
    if (VM.BuildWithGCSpy) {
      sysCall.gcspyDriverInitOutput(driver);
      sysCall.gcspyIntWriteControl(
                              driver/* NOTE driver->interpreter in sys.C*/,
                              spaceId,
                              tileNum);

      // send a control for each tile
      for (int i = 0; i < tileNum; ++i)
        streamByteValue(space.getControl(i));

      // end the stream
      sysCall.gcspyDriverEndOutput(driver);
    }
  }

  @Override
  public void spaceInfo(Address info) {
    if (VM.BuildWithGCSpy)
      sysCall.gcspyDriverSpaceInfo(driver, info);
  }
}
