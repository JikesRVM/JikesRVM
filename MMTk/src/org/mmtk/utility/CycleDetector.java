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

interface CycleDetector {
  public final static String Id = "$Id$"; 

  public void collectCycles();
  public void possibleCycleRoot(VM_Address object);
  public void enumeratePointer(VM_Address object);
}
