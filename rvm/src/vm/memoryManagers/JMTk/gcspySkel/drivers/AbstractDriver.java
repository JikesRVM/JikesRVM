/**
 ** AbstractDriver
 **
 ** Abstract GCspy skeleton 
 **
 ** (C) Copyright Richard Jones, 2003
 ** Computing Laboratory, University of Kent at Canterbury
 ** All rights reserved.
 **/

package uk.ac.kent.JikesRVM.memoryManagers.JMTk.gcspy;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Uninterruptible;

/**
 * This class implements a base driver for the JMTk.
 *
 * @author <a href="www.ukc.ac.uk/people/staff/rej">Richard Jones</a>
 * @version $Revision$
 * @date $Date$
 */
abstract public class AbstractDriver implements VM_Uninterruptible {
  public final static String Id = "$Id$";
  public int countTileNum (VM_Address start, VM_Address end, int tileSize) { return 0; }
  public int jikesObjectsPerBlock (int blockSize) { return 0; }
  public boolean shouldTransmit(int event) { return false; }
  public void traceObject(VM_Address addr) {} 
  public void setRange(int event, VM_Address start, VM_Address end) {}
  public void zero() {}
  public void finish(int event) {}
}
