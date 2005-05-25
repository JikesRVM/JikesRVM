/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */
//$Id$
package org.mmtk.utility.scan;

import org.mmtk.vm.Plan;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * A pointer enumeration class.  This class is used to forward all
 * fields of an instance.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $date: $
 */
public class PreCopyEnumerator extends Enumerator 
  implements Uninterruptible {
  /**
   * Constructor (empty).
   */
  public PreCopyEnumerator() {}

  /**
   * Enumerate a pointer.  In this case we forward the referent object.
   *
   * @param location The address of the field being enumerated.
   */
  public void enumeratePointerLocation(Address location) 
    throws InlinePragma {
    Plan.forwardObjectLocation(location);
  }
}
