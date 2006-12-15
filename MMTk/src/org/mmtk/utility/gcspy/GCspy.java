/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Richard Jones,
 * Computing Laboratory, University of Kent at Canterbury, 2003-6
 */
package org.mmtk.utility.gcspy;

import org.mmtk.utility.Log;
import org.mmtk.utility.options.*;
import org.mmtk.vm.VM;
import org.mmtk.vm.gcspy.ServerInterpreter;
import org.mmtk.vm.gcspy.Util;

import org.vmmagic.pragma.*;

/**
 * This class implements collector-independent GCspy functionality to start
 * the GCspy server.  It handles command-line parameters for port number,
 * whether the VM should wait for a GCspy client to connect, and tile size.
 * Most importantly, it calls the Plan's startGCspyServer method which
 * creates a new ServerInterpreter, and adds events and space drivers.
 * 
 * $Id$
 * 
 * @author <a href="http://www.cs.ukc.ac.uk/people/staff/rej">Richard Jones</a>
 * @version $Revision$
 * @date $Date$
 */
@Uninterruptible public class GCspy {

  /****************************************************************************
   * 
   * Class variables
   */
  
  public static final Util util = VM.newGCspyUtil();
  public static final ServerInterpreter server = VM.newGCspyServerInterpreter();
  
  /****************************************************************************
   * 
   * Initialization
   */

  @Interruptible
  public static void createOptions() { 
    Options.gcspyPort = new GCspyPort();
    Options.gcspyWait = new GCspyWait();
    Options.gcspyTileSize = new GCspyTileSize();
  }

  /**
   * The boot method is called by the runtime immediately after
   * command-line arguments are available.  Note that allocation must
   * be supported prior to this point because the runtime
   * infrastructure may require allocation in order to parse the
   * command line arguments.
   */
  public static void postBoot() { }

  /**
   * Get the number of the port that GCspy communicates on
   * 
   * @return the GCspy port number
   */
  public static int getGCspyPort() {
    return Options.gcspyPort.getValue();
  }

  /**
   * Should the VM wait for GCspy to connect?
   * 
   * @return whether the VM should wait for the visualiser to connect
   */
  public static boolean getGCspyWait() {
    return Options.gcspyWait.getValue();
  }

  /**
   * Start the GCspy server.
   * WARNING: allocates memory indirectly
   */
  @Interruptible
  public static void startGCspyServer() { 
    int port = getGCspyPort();
    Log.write("GCspy.startGCspyServer, port="); Log.write(port);
    Log.write(", wait=");
    Log.writeln(getGCspyWait());
    if (port > 0) {
      VM.activePlan.global().startGCspyServer(port, getGCspyWait());
      //Log.writeln("gcspy thread booted");
    }
  }
}

