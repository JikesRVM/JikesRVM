/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */
//$Id$
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_Uninterruptible;

/**
 * Callbacks from ScanObject to Plan.enumeratePointerLocation are
 * dispatched through an object of this class, so that we have the
 * opportunity to change the behaviour through sub-classing. <p>
 *
 * @author Robin Garner
 * @version $Revision$
 * @date    $Date$
 */

public class Enumerate implements VM_Uninterruptible {
  protected Plan plan;

  /* Constructor 
   */

  Enumerate(Plan plan) { 
    this.plan = plan; 
  }

  public void enumeratePointerLocation(VM_Address location) 
      throws VM_PragmaInline {
    plan.enumeratePointerLocation(location);
  }
}
