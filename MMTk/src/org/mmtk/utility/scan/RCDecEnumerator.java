/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */
//$Id$
package org.mmtk.utility.scan;

import org.mmtk.plan.Plan;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * A pointer enumeration class.  This class is used by the reference
 * counting collector to do recursive decrement.
 *
 * @author Ian Warrington
 * @version $Revision$
 * @date $date: $
 */
public class RCDecEnumerator extends Enumerate implements Uninterruptible {
  private Plan plan;

  /**
   * Constructor.
   *
   * @param plan The plan instance with respect to which the
   * enumeration will occur.
   */
  public RCDecEnumerator(Plan plan) {
    this.plan = plan;
  }

  /**
   * Enumerate a pointer.  In this case it is a decrement event.
   *
   * @param location The address of the field being enumerated.
   */
  public void enumeratePointerLocation(Address location) 
      throws InlinePragma {
    plan.enumerateDecrementPointerLocation(location);
  }
}
