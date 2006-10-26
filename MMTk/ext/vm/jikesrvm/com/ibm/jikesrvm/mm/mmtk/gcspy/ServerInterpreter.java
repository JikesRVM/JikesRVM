/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Richard Jones, 2002-6
 * Computing Laboratory, University of Kent at Canterbury
 */
package com.ibm.jikesrvm.mm.mmtk.gcspy;

import org.mmtk.utility.Log;
import org.mmtk.vm.VM;
import org.mmtk.utility.gcspy.GCspy;

import com.ibm.jikesrvm.VM_SysCall;
import com.ibm.jikesrvm.VM_JavaHeaderConstants;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Generic GCspy Server Interpreter
 *
 * This class implements the GCspy server. 
 * The server runs as a separate pthread and communicates with GCspy
 * clients. It handles commands from the client and passes data to it.
 * Mostly it forwards calls to the C gcspy library.
 *
 * $Id: ServerInterpreter.java 10806 2006-09-22 12:17:46Z dgrove-oss $
 * @author <a href="http://www.ukc.ac.uk/people/staff/rej">Richard Jones</a>
 * @version $Revision: 10806 $
 * @date $Date: 2006-09-22 13:17:46 +0100 (Fri, 22 Sep 2006) $
 */
public class ServerInterpreter extends org.mmtk.vm.gcspy.ServerInterpreter
  implements Uninterruptible, VM_JavaHeaderConstants {
  
  
  /**
   * Create a new ServerInterpreter singleton.
   * @param name The name of the server
   * @param port The number of the port on which to communicate
   * @param verbose Whether the server is to run verbosely
   */
  public void init (String name, int port, boolean verbose) 
  	 throws InterruptiblePragma {
    if (VM_SysCall.WITH_GCSPY) {
      if (VM.VERIFY_ASSERTIONS)
        VM.assertions._assert(!initialised, "Tried to re-init server interpreter");
      initialised = true;
      
      if (DEBUG) 
        Log.writeln("-- Initialising main server on port ",port);
      
      Address tmp = GCspy.util.getBytes(name);
      server = VM_SysCall.gcspyMainServerInit(port, MAX_LEN, tmp, verbose?1:0);
      
      if (DEBUG) {
        Log.writeln("gcspy_main_server_t address = "); Log.writeln(server); 
      }
      
      GCspy.util.free(tmp);
      // Set up the list of ServerSpaces
      spaces = new com.ibm.jikesrvm.mm.mmtk.gcspy.ServerSpace[MAX_SPACES];
    }
  }
 
  /** 
   * Add an event to the ServerInterpreter.
   * @param num the event number
   * @param name the event name
   */
  public void addEvent (int num, String name) {
    if (VM_SysCall.WITH_GCSPY) {
      if (VM.VERIFY_ASSERTIONS)
        VM.assertions._assert(initialised, 
                       "ServerInterpreter.addEvent: server not initiialised");
      
      Address tmp = GCspy.util.getBytes(name);
      VM_SysCall.gcspyMainServerAddEvent(server, num, tmp);
      GCspy.util.free(tmp);
    }
  }

  /**
   * Set the general info for the ServerInterpreter.
   * @param info the information
   */
  public void setGeneralInfo(String info) {
    if (VM_SysCall.WITH_GCSPY) {
      if (VM.VERIFY_ASSERTIONS)
        VM.assertions._assert(initialised, 
                       "ServerInterpreter.setGeneralInfo: server not initiialised");
      
      Address tmp = GCspy.util.getBytes(info);
      VM_SysCall.gcspyMainServerSetGeneralInfo(server, tmp);
      GCspy.util.free(tmp);
    }
  } 
  
  /**
   * Start the server, running its main loop in a pthread.
   * @param wait Whether to wait for the client to connect
   */
  public void startServer(boolean wait) {
    if (VM_SysCall.WITH_GCSPY) {
      if (DEBUG) { Log.write("Starting GCSpy server, wait="); Log.writeln(wait); }
      
      Address serverOuterLoop = VM_SysCall.gcspyMainServerOuterLoop();
      VM_SysCall.gcspyStartserver(server, wait?1:0, serverOuterLoop);
    }
  }

  /**
   * Are we connected to a GCspy client?
   * @param event The current event
   * @return true if we are connected
   */
  public boolean isConnected (int event) {
    if (VM_SysCall.WITH_GCSPY) {
      if (DEBUG)
        Log.writeln("ServerInterpreter.isConnected, server=", server);
      
      if (!initialised) 
        return false;
      int res = VM_SysCall.gcspyMainServerIsConnected(server, event);
      return (res != 0);
      }
    else 
      return false;
  }
  
  /**
   * Start compensation timer so that time spent gathering data is
   * not confused with the time spent in the application and the VM.
   */
  public void startCompensationTimer() {
    if (VM_SysCall.WITH_GCSPY) {
      if (VM.VERIFY_ASSERTIONS)
        VM.assertions._assert(initialised, 
                       "ServerInterpreter.startCompensationTimer: server not initiialised");
      
      VM_SysCall.gcspyMainServerStartCompensationTimer(server);
    }
  }
  
  /**
   * Stop compensation timer so that time spent gathering data is
   * not confused with the time spent in the application and the VM.r
   */
  public void stopCompensationTimer() {
    if (VM_SysCall.WITH_GCSPY) {
      if (VM.VERIFY_ASSERTIONS)
        VM.assertions._assert(initialised, 
                       "ServerInterpreter.stopCompensationTimer: server not initiialised");
      
      VM_SysCall.gcspyMainServerStopCompensationTimer(server);
    }
  }

  /**
   * Indicate that we are at a server safe point (e.g. the end of a GC).
   * This is a point at which the server can pause, play one, etc.
   * @param event The current event
   */
  public void serverSafepoint (int event) {
    if (VM_SysCall.WITH_GCSPY) {
      if (DEBUG)
        Log.writeln("ServerInterpreter.serverSafepoint, server=", server);
      
      if (!initialised) 
        return;
      VM_SysCall.gcspyMainServerSafepoint(server, event);
    }
  }

  /**
   * Discover the smallest header size for objects.
   * @return the size in bytes
   */
  public int computeHeaderSize() {
    return JAVA_HEADER_BYTES+OTHER_HEADER_BYTES;
  }
}
