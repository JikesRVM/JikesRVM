/*
 * (C) Copyright Richard Jones,
 * Computing Laboratory, University of Kent at Canterbury, 2003
 */
package org.mmtk.utility.gcspy;

import org.mmtk.vm.Plan;
import org.mmtk.utility.Log;
import org.mmtk.utility.options.*;
import org.vmmagic.pragma.*;

/** 
 * This class implements collector-independent GCspy functionality to start
 * the GCspy server.
 *
 * $Id$
 *
 * @author <a href="http://www.cs.ukc.ac.uk/people/staff/rej">Richard Jones</a>
 * @version $Revision$
 * @date $Date$
 */
public class GCspy implements Uninterruptible {

  /****************************************************************************
   *
   * Class variables
   */
  private static int gcspyPort_ = 0;            // port to connect on
  private static boolean gcspyWait_ = false;    // wait for connection?

  /****************************************************************************
   *
   * Initialization
   */

  public static GCspyPort gcspyPort;
  public static GCspyWait gcspyWait; 
  public static GCspyTileSize gcspyTilesize;

  public static void createOptions() throws InterruptiblePragma {
    gcspyPort = new GCspyPort();
    gcspyWait = new GCspyWait(); 
    gcspyTilesize = new GCspyTileSize();
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
    return gcspyPort.getValue();
  }

  /**
   * Should the JVM wait for GCspy to connect?
   *
   * @return whether the JVM should wait for the visualiser to connect
   */
  public static boolean getGCspyWait() {
    return gcspyWait.getValue();
  }

  /**
   * Start the GCspy server
   * WARNING: allocates memory indirectly
   */
  public static void startGCspyServer() throws InterruptiblePragma {
    int port = getGCspyPort();
    Log.write("GCspy.startGCspyServer, port="); Log.write(port);
    Log.write(", wait=");
    Log.writeln(getGCspyWait());
    if (port > 0) {
      Plan.startGCspyServer(port, getGCspyWait());
      Log.writeln("gcspy thread booted");
    }
  }
}

