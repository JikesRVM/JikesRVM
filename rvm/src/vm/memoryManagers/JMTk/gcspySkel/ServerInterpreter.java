/**
 ** ServerInterpreter
 **
 ** Generic GCspy Server Interpreter skeleton
 **
 ** (C) Copyright Richard Jones, 2002
 ** Computing Laboratory, University of Kent at Canterbury
 ** All rights reserved.
 **/

package uk.ac.kent.JikesRVM.memoryManagers.JMTk.gcspy;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Uninterruptible;


/**
 * This class implements the GCspy server. 
 * Mostly it forwards calls to the C gcspy library.
 *
 * @author <a href="www.ukc.ac.uk/people/staff/rej">Richard Jones</a>
 * @version $Revision$
 * @date $Date$
 */
public class ServerInterpreter implements VM_Uninterruptible {
  public final static String Id = "$Id$";
  
  public static void init (String name,
  		           int port,
		           String[] eventNames,
		           boolean verbose,
		           String generalInfo) {}

  public static void startServer(boolean wait) { }
  public static boolean shouldTransmit(int event) { return false; }
  public static void startCompensationTimer() { }
  public static void stopCompensationTimer() {}
  public static void serverSafepoint (int event) { }
}
