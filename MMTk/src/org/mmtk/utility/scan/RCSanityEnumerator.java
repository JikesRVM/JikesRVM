/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */
//$Id$
package org.mmtk.utility;

import org.mmtk.policy.RefCountLocal;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_Magic;

/**
 * A pointer enumeration class.  This class is used by the reference
 * counting collector to do recursive decrement.
 *
 * @author Ian Warrington
 * @version $Revision$
 * @date $date: $
 */
public class RCSanityEnumerator extends Enumerate 
  implements VM_Uninterruptible {
  private RefCountLocal rc;

  /**
   * Constructor.
   *
   * @param plan The plan instance with respect to which the
   * enumeration will occur.
   */
  public RCSanityEnumerator(RefCountLocal rc) {
    this.rc = rc;
  }

  /**
   * Enumerate a pointer.  In this case it is a decrement event.
   *
   * @param location The address of the field being enumerated.
   */
  public void enumeratePointerLocation(VM_Address location) 
    throws VM_PragmaInline {
    VM_Address object = VM_Magic.getMemoryAddress(location);
    if (!object.isZero()) {
      rc.sanityTraceEnqueue(object, location);
    }
  }
}
