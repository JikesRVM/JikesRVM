/**
 ** TreadmillDriver
 **
 ** GCspy skeleton driver
 **
 ** (C) Copyright Richard Jones, 2003
 ** Computing Laboratory, University of Kent at Canterbury
 ** All rights reserved.
 **/

package com.ibm.JikesRVM.memoryManagers.JMTk;
import com.ibm.JikesRVM.VM_Address;
import uk.ac.kent.JikesRVM.memoryManagers.JMTk.gcspy.AbstractDriver;
import com.ibm.JikesRVM.VM_Uninterruptible;

/**
 * This class implements a simple driver for the JMTk treadmill space.
 *
 * @author <a href="www.ukc.ac.uk/people/staff/rej">Richard Jones</a>
 * @version $Revision$
 * @date $Date$
 */
class TreadmillDriver extends AbstractDriver implements VM_Uninterruptible {
  public final static String Id = "$Id$";

  TreadmillDriver(String name,
		     FreeListVMResource losVM,
		     int blockSize,
		     VM_Address start, 
		     VM_Address end,
		     int size,
		     int threshold,
		     boolean mainSpace) {}
}
