/**
 ** ServerInterpreter
 **
 ** Generic GCspy Server Interpreter
 **
 ** (C) Copyright Richard Jones, 2002
 ** Computing Laboratory, University of Kent at Canterbury
 ** All rights reserved.
 **/

package uk.ac.kent.JikesRVM.memoryManagers.JMTk.gcspy;

import com.ibm.JikesRVM.memoryManagers.JMTk.Log;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_SysCall;


/**
 * This class implements the GCspy server. 
 * Mostly it forwards calls to the C gcspy library.
 *
 * @author <a href="www.ukc.ac.uk/people/staff/rej">Richard Jones</a>
 * @version $Revision$
 * @date $Date$
 */
public class ServerInterpreter 
  implements VM_Uninterruptible {
  public final static String Id = "$Id$";
  
  private static final int MAX_LEN = 64 * 1024;	// Buffer size
  private static VM_Address server_;		// address of the c server, gcspy_main_server_t server

  private static final boolean DEBUG_ = false;
  
  /**
   * Create a new Server
   *
   * @param name The name of the server
   * @param eventNames The names of all the events
   * @param verbose Whether the server is to run verbosely
   * @param generalInfo General info describing the server
   */
  public static void init (String name,
  		           int port,
		           String[] eventNames,
		           boolean verbose,
		           String generalInfo) {

    // Initialise the server
    if (DEBUG_) {
      Log.writeln("-- Initialising main server on port ",port);
    }
    VM_Address tmp = Util.getBytes(name);
    server_ = VM_SysCall.gcspyMainServerInit(port, MAX_LEN, tmp, verbose ? 1 : 0);
    if (DEBUG_) {
      Log.writeln("gcspy_main_server_t address = ");
      Log.write(server_);
      Log.write('\n');
    }
    Util.free(tmp);

    // Set the general info
    tmp = Util.getBytes(generalInfo);
    VM_SysCall.gcspyMainServerSetGeneralInfo(server_, tmp);
    Util.free(tmp);

    // Add each event
    for (int ev = 0; ev < eventNames.length; ev++) {
      tmp = Util.getBytes(eventNames[ev]);
      VM_SysCall.gcspyMainServerAddEvent(server_, ev, tmp);
      Util.free(tmp);
    }
  }

  /**
   * The address of the C server
   *
   * @return the address of the server
   */
  static VM_Address getServerAddress() {
    return server_;
  }


  /**
   * Start the server 
   *
   * @param wait Whether to wait for the client to connect
   */
  public static void startServer(boolean wait) {
    if (DEBUG_) {
      Log.write("Starting GCSpy server, wait=");
      Log.writeln(wait);
    }
    VM_Address serverOuterLoop 
      = VM_SysCall.gcspyMainServerOuterLoop();
    VM_SysCall.gcspyStartserver(server_, wait?1:0, serverOuterLoop);
  }

  /**
   * Should we transmit data to the visualiser at this event?
   *
   * @param event The event
   * @return true if we should transmit now
   */
  public static boolean shouldTransmit(int event) {
    int res = VM_SysCall.gcspyMainServerIsConnected(server_, event);
    return (res != 0);
  }

  /**
   * Are we connected to the visualiser?
   *
   * @param event The current event
   * @return true if we are connected
   */
  public static boolean isConnected (int event) {
    int res = VM_SysCall.gcspyMainServerIsConnected(server_, event);
    return (res != 0);
  }
  
  /**
   * Start compensation timer
   */
  public static void startCompensationTimer() {
    VM_SysCall.gcspyMainServerStartCompensationTimer(server_);
  }
  
  /**
   * Stop compensation timer
   */
  public static void stopCompensationTimer() {
    VM_SysCall.gcspyMainServerStopCompensationTimer(server_);
  }

  /**
   * Indicate that we are at a server safe point (e.g. the end of a gc)
   *
   * @param event The current event
   */
  public static void serverSafepoint (int event) {
    VM_SysCall.gcspyMainServerSafepoint(server_, event);
  }
}
