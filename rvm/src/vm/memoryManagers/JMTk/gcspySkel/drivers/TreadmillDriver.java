/**
 * TreadmillDriver
 *
 * GCspy skeleton driver
 *
 * (C) Copyright Richard Jones, 2003
 * Computing Laboratory, University of Kent at Canterbury
 * All rights reserved.
 */

package org.mmtk.utility.gcspy;

import org.mmtk.utility.FreeListVMResource;
import org.mmtk.vm.gcspy.AbstractDriver;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Uninterruptible;

/**
 * This class implements a simple driver for the JMTk treadmill space.
 *
 * @author <a href="www.ukc.ac.uk/people/staff/rej">Richard Jones</a>
 * @version $Revision$
 * @date $Date$
 */
public class TreadmillDriver extends AbstractDriver 
  implements VM_Uninterruptible {
  public final static String Id = "$Id$";

  public TreadmillDriver(String name,
			 FreeListVMResource losVM,
			 int blockSize,
			 VM_Address start, 
			 VM_Address end,
			 int size,
			 int threshold,
			 boolean mainSpace) {}
}
