/**
 ** GCSpy.java
 **
 ** (C) Copyright Richard Jones,
 ** Computing Laboratory, University of Kent at Canterbury, 2003
 ** 
 ** @author Richard Jones
 **/

package org.mmtk.vm.gcspy;

import org.mmtk.plan.Plan;
import org.mmtk.utility.Log;
import org.mmtk.utility.Options;

import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;

/** 
 * This class implements collector-independent GCSpy functionality to start
 * the GCSpy server.
 *
 * author <a href="http://www.cs.ukc.ac.uk/people/staff/rej">Richard Jones</a>
 * @version $Revision$
 * @date $Date$
 */
public class GCSpy
  implements VM_Uninterruptible {
  public final static String Id = "$Id$";

//-#if RVM_WITH_GCSPY  
  ////////////////////////////////////////////////////////////////////////////
  //
  // Class variables
  //
  private static int gcspyPort_ = 0;		// port to connect on
  private static boolean gcspyWait_ = false;	// wait for connection?

  ////////////////////////////////////////////////////////////////////////////
  //
  // Initialization
  //

  /**
   * The boot method is called by the runtime immediately after
   * command-line arguments are available.  Note that allocation must
   * be supported prior to this point because the runtime
   * infrastructure may require allocation in order to parse the
   * command line arguments.  
   */
  public static void postBoot() { }

  /**
   * Get the number of the port that GCSpy communicates on
   *
   * @return the GCSpy port number
   */
  public static int getGCSpyPort() {
    //Log.writeln("GCSpy.getGCSpyPort: ", gcspyPort_);
    return Options.gcspyPort;
  }

  /**
   * Should the JVM wait for GCSpy to connect?
   *
   * @return whether the JVM should wait for the visualiser to connect
   */
  public static boolean getGCSpyWait() {
    return Options.gcspyWait;
  }

  /**
   * Start the GCSpy server
   * WARNING: allocates memory indirectly
   */
  public static void startGCSpyServer() throws VM_PragmaInterruptible {
    int port = getGCSpyPort();
    Log.write("GCSpy.startGCSpyServer, port=", port);
    Log.write(", wait=");
    Log.writeln(getGCSpyWait());
    if (port > 0) {
      Plan.startGCSpyServer(port, getGCSpyWait());
      Log.writeln("gcspy thread booted");
    }
  }
//-#else
  public static int getGCSpyPort() { return 0; }
  public static boolean getGCSpyWait() { return false; }
  public static void startGCSpyServer() {}
//-#endif
}

