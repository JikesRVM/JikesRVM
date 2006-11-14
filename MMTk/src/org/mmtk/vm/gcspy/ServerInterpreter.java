/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Richard Jones, 2002-6
 * Computing Laboratory, University of Kent at Canterbury
 * All rights reserved.
 */
package org.mmtk.vm.gcspy;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.Address;

/**
 * Abstract class for the GCspy server interpreter
 * 
 * Implementing classes will mostly forward calls to the C gcspy library.
 * 
 * $Id$
 * 
 * @author <a href="http://www.ukc.ac.uk/people/staff/rej">Richard Jones</a>
 * @version $Revision$
 * @date $Date$
 */
public abstract class ServerInterpreter implements Uninterruptible {

  protected static final int MAX_LEN = 64 * 1024; // Buffer size
  protected static final int MAX_SPACES = 32;     // Maximum number of spaces
  protected static boolean initialised = false;
  
  protected ServerSpace[] spaces;                 // The server's spaces
  protected Address server;                       // a pointer to the c server, gcspy_main_server_t server

  protected static final boolean DEBUG = false;


  /*
   * Create a new ServerInterpreter.
   */
  public ServerInterpreter() {}
  
  /**
   * Create a new ServerInterpreter singleton.
   * @param name The name of the server
   * @param port The number of the port on which to communicate
   * @param verbose Whether the server is to run verbosely
   */
  public abstract void init (String name, int port, boolean verbose)
      throws InterruptiblePragma;
  
  /** 
   * Add an event to the ServerInterpreter.
   * @param num the event number
   * @param name the event name
   */
  public abstract void addEvent (int num, String name);
  
  /**
   * Set the general info for the ServerInterpreter.
   * @param info the information
   */
  public abstract void setGeneralInfo(String info);
  
  /**
   * Get a pointer to the C server, gcspy_main_server_t.
   * This address is used in alll calls to the server in the C library.
   * @return the address of the server
   */
  public Address getServerAddress() { return server; }
  
  /**
   * Add a GCspy ServerSpace to the ServerInterpreter.
   * This method returns a unique space ID for the ServerSpace
   * (again used in calls to the C library).
   *
   * @param space the ServerSpace to add
   * @return a unique id for this space
   * @exception IndexOutOfBoundsException on attempt to add more than 
   * MAX_SPACES spaces
   */
  public int addSpace(ServerSpace space) throws InterruptiblePragma { 
    int id = 0;
    while (id < MAX_SPACES) {
      if (spaces[id] == null) {
        spaces[id] = space;
        return id;
      }
      id++;
    }
    throw new IndexOutOfBoundsException(
        "Too many spaces to add to interpreter.\nSet MAX_SPACES to higher value in ServerInterpreter.");
  }

  /**
   * Start the server, running its main loop in a pthread.
   * @param wait Whether to wait for the client to connect
   */
  public abstract void startServer(boolean wait);
  
  /**
   * Are we connected to a GCspy client?
   * @param event The current event
   * @return true if we are connected
   */
  public abstract boolean isConnected (int event);

  /**
   * Start compensation timer so that time spent gathering data is
   * not confused with the time spent in the application and the VM.
   */
  public abstract void startCompensationTimer();
  
  /**
   * Stop compensation timer so that time spent gathering data is
   * not confused with the time spent in the application and the VM.r
   */
  public abstract void stopCompensationTimer();
  
  /**
   * Indicate that we are at a server safe point (e.g. the end of a GC).
   * This is a point at which the server can pause, play one, etc.
   * @param event The current event
   */
  public abstract void serverSafepoint (int event);
  
  /**
   * Discover the smallest header size for objects.
   * @return the size in bytes
   */
  public abstract int computeHeaderSize();
}
