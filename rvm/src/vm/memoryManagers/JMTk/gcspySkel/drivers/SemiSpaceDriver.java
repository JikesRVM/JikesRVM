/**
 ** SemiSpaceDriver
 **
 ** GCspy skeleton driver 
 **
 ** (C) Richard Jones, 2003
 ** Computing Laboratory, University of Kent at Canterbury
 ** All rights reserved.
 **/

package com.ibm.JikesRVM.memoryManagers.JMTk;
import uk.ac.kent.JikesRVM.memoryManagers.JMTk.gcspy.AbstractDriver;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Uninterruptible;

/**
 * This class implements a simple driver for the JMTk SemiSpace copying collector.
 *
 * @author <a href="www.ukc.ac.uk/people/staff/rej">Richard Jones</a>
 * @version $Revision$
 * @date $Date$
 */
public class SemiSpaceDriver extends AbstractDriver implements VM_Uninterruptible {
  public final static String Id = "$Id$";

  public SemiSpaceDriver 
                    (String name,
		     int blockSize,
		     VM_Address start0, 
		     VM_Address end0,   
		     VM_Address start1,
		     VM_Address end1,
		     int size,
		     boolean mainSpace ) { }
  public void finish(int event, int semi) {}
}
