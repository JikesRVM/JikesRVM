/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Uninterruptible;

/*
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
abstract class CycleDetector implements VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  abstract public boolean collectCycles(boolean time);
  abstract public void possibleCycleRoot(VM_Address object);
  abstract public void enumeratePointer(VM_Address object);
  abstract public void printTimes(boolean totals);
}
