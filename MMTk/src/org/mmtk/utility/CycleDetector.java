/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */
package org.mmtk.utility;

import org.mmtk.vm.VM_Interface;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Uninterruptible;

/*
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
abstract class CycleDetector implements VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  abstract boolean collectCycles(int count, boolean time);
  abstract void possibleCycleRoot(VM_Address object);
}
