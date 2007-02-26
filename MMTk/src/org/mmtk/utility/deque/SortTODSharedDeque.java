/**
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004.
 */
package org.mmtk.utility.deque;

import org.mmtk.policy.RawPageSpace;
import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class specializes SortSharedQueue to sort objects according to
 * their time of death (TOD).
 * 
 * $Id$
 * 
 * @author Steve Blackburn
 * @version $Revision$
 * @date $Date$
 */
@Uninterruptible
public final class SortTODSharedDeque extends SortSharedDeque {

  /**
   * Constructor
   * 
   * @param rps The space from which the instance should obtain buffers.
   * @param arity The arity of the data to be enqueued
   */
  public SortTODSharedDeque(RawPageSpace rps, int arity) {
    super(rps, arity);
  }

  /**
   * Return the sorting key for the object passed as a parameter.
   * 
   * @param obj The address of the object whose key is wanted
   * @return The value of the sorting key for this object
   */
  protected final Word getKey(Address obj) {
    return VM.traceInterface.getDeathTime(obj.toObjectReference());
  }
}
