/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.VM_Address;

/*
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
interface CycleDetector {
  public final static String Id = "$Id$"; 

  public boolean collectCycles(boolean time);
  public void possibleCycleRoot(VM_Address object);
  public void enumeratePointer(VM_Address object);
  public void printTimes(boolean totals);
}
