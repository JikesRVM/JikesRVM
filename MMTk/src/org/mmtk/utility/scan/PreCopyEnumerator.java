/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */
//$Id$
package org.mmtk.utility;

import org.mmtk.plan.Plan;

import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_Uninterruptible;

/**
 * A pointer enumeration class.  This class is used to forward all
 * fields of an instance.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $date: $
 */
public class PreCopyEnumerator extends Enumerate 
  implements VM_Uninterruptible {
  /**
   * Constructor (empty).
   */
  public PreCopyEnumerator() {}

  /**
   * Enumerate a pointer.  In this case we forward the referent object.
   *
   * @param location The address of the field being enumerated.
   */
  public void enumeratePointerLocation(VM_Address location) 
    throws VM_PragmaInline {
    Plan.forwardObjectLocation(location);
  }
}
