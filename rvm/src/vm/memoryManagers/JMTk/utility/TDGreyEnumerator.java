/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */
//$Id$
package org.mmtk.utility;

import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_Uninterruptible;

/**
 * A pointer enumeration class.  This class is used by the trial
 * deletion cycle detector to perform transitive closure of its "mark
 * grey" phase.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $date: $
 */
class TDGreyEnumerator extends Enumerate implements VM_Uninterruptible {
  private TrialDeletion td;

  /**
   * Constructor.
   *
   * @param plan The plan instance with respect to which the
   * enumeration will occur.
   */
  TDGreyEnumerator(TrialDeletion td) {
    this.td = td;
  }

  /**
   * Enumerate a pointer.  In this case it is a mark-grey event.
   *
   * @param location The address of the field being enumerated.
   */
  public void enumeratePointerLocation(VM_Address objLoc) 
    throws VM_PragmaInline {
    td.enumerateGrey(VM_Magic.getMemoryAddress(objLoc));
  }
}
