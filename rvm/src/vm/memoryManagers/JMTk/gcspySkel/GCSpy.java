/* GCSpy.java skeleton
 *
 * (C) Copyright Richard Jones,
 * Computing Laboratory, University of Kent at Canterbury, 2003
 *
 * @author Richard Jones
 */

package uk.ac.kent.JikesRVM.memoryManagers.JMTk.gcspy;
import com.ibm.JikesRVM.VM_Uninterruptible;

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

  public static int getGCSpyPort() { return 0; }
  public static boolean getGCSpyWait() { return false; }
  public static void startGCSpyServer() {}
}



  
