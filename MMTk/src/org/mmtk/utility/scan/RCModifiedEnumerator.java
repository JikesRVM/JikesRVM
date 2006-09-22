/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */
//$Id$
package org.mmtk.utility.scan;

import org.mmtk.plan.refcount.RCBase;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * A pointer enumeration class.  This class is used by the
 * reference counting collector to do modified buffer
 * enumeration.
 * 
 * @author Steve Blackburn
 * @author Ian Warrington
 * @version $Revision$
 * @date $date: $
 */
public class RCModifiedEnumerator extends Enumerator 
  implements Uninterruptible {

  /**
   * Enumerate a pointer. In this case it is an increment event.
   * 
   * @param location The address of the field being enumerated.
   */
  public void enumeratePointerLocation(Address location) throws InlinePragma {
    RCBase.collector().enumerateModifiedPointerLocation(location);
  }
}
