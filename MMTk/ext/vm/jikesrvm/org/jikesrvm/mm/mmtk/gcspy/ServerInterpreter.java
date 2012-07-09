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
import org.mmtk.vm.VM;
import org.mmtk.utility.gcspy.GCspy;

import static org.jikesrvm.runtime.SysCall.sysCall;
import org.jikesrvm.objectmodel.JavaHeaderConstants;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Generic GCspy Server Interpreter.
 * <p>
 * This class implements the GCspy server.
 * The server runs as a separate pthread and communicates with GCspy
 * clients. It handles commands from the client and passes data to it.
 * Mostly it forwards calls to the C gcspy library.
 */
@Uninterruptible public class ServerInterpreter extends org.mmtk.vm.gcspy.ServerInterpreter
  implements JavaHeaderConstants {


  @Override
  @Interruptible
  public void init(String name, int port, boolean verbose) {
    if (org.jikesrvm.VM.BuildWithGCSpy) {
      if (VM.VERIFY_ASSERTIONS)
        VM.assertions._assert(!initialised, "Tried to re-init server interpreter");
      initialised = true;

      if (DEBUG)
        Log.writeln("-- Initialising main server on port ",port);

      Address tmp = GCspy.util.getBytes(name);
      server = sysCall.gcspyMainServerInit(port, MAX_LEN, tmp, verbose?1:0);

      if (DEBUG) {
        Log.writeln("gcspy_main_server_t address = "); Log.writeln(server);
      }

      GCspy.util.free(tmp);
      // Set up the list of ServerSpaces
      spaces = new org.jikesrvm.mm.mmtk.gcspy.ServerSpace[MAX_SPACES];
    }
  }

  @Override
  public void addEvent(int num, String name) {
    if (org.jikesrvm.VM.BuildWithGCSpy) {
      if (VM.VERIFY_ASSERTIONS)
        VM.assertions._assert(initialised,
                       "ServerInterpreter.addEvent: server not initiialised");

      Address tmp = GCspy.util.getBytes(name);
      sysCall.gcspyMainServerAddEvent(server, num, tmp);
      GCspy.util.free(tmp);
    }
  }

  @Override
  public void setGeneralInfo(String info) {
    if (org.jikesrvm.VM.BuildWithGCSpy) {
      if (VM.VERIFY_ASSERTIONS)
        VM.assertions._assert(initialised,
                       "ServerInterpreter.setGeneralInfo: server not initiialised");

      Address tmp = GCspy.util.getBytes(info);
      sysCall.gcspyMainServerSetGeneralInfo(server, tmp);
      GCspy.util.free(tmp);
    }
  }

  @Override
  public void startServer(boolean wait) {
    if (org.jikesrvm.VM.BuildWithGCSpy) {
      if (DEBUG) { Log.write("Starting GCSpy server, wait="); Log.writeln(wait); }

      Address serverOuterLoop = sysCall.gcspyMainServerOuterLoop();
      sysCall.gcspyStartserver(server, wait?1:0, serverOuterLoop);
    }
  }

  @Override
  public boolean isConnected(int event) {
    if (org.jikesrvm.VM.BuildWithGCSpy) {
      if (DEBUG)
        Log.writeln("ServerInterpreter.isConnected, server=", server);

      if (!initialised)
        return false;
      int res = sysCall.gcspyMainServerIsConnected(server, event);
      return (res != 0);
    } else {
      return false;
    }
  }

  @Override
  public void startCompensationTimer() {
    if (org.jikesrvm.VM.BuildWithGCSpy) {
      if (VM.VERIFY_ASSERTIONS)
        VM.assertions._assert(initialised,
                       "ServerInterpreter.startCompensationTimer: server not initiialised");

      sysCall.gcspyMainServerStartCompensationTimer(server);
    }
  }

  @Override
  public void stopCompensationTimer() {
    if (org.jikesrvm.VM.BuildWithGCSpy) {
      if (VM.VERIFY_ASSERTIONS)
        VM.assertions._assert(initialised,
                       "ServerInterpreter.stopCompensationTimer: server not initiialised");

      sysCall.gcspyMainServerStopCompensationTimer(server);
    }
  }

  @Override
  public void serverSafepoint(int event) {
    if (org.jikesrvm.VM.BuildWithGCSpy) {
      if (DEBUG)
        Log.writeln("ServerInterpreter.serverSafepoint, server=", server);

      if (!initialised)
        return;
      sysCall.gcspyMainServerSafepoint(server, event);
    }
  }

  @Override
  public int computeHeaderSize() {
    return JAVA_HEADER_BYTES+OTHER_HEADER_BYTES;
  }
}
