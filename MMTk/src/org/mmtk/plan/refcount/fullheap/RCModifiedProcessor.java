/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.plan.refcount.fullheap;

import org.mmtk.plan.TraceStep;
import org.mmtk.plan.refcount.RCHeader;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This abstract class is the fundamental mechanism for performing a
 * transitive closure over an object graph.<p>
 * 
 * @see org.mmtk.plan.TraceLocal
 */
@Uninterruptible public final class RCModifiedProcessor extends TraceStep {

  /**
   * Trace a reference during GC.
   * 
   * @param objLoc The location containing the object reference to be
   * traced.
   */
  public void traceObjectLocation(Address objLoc) {
    ObjectReference object = objLoc.loadObjectReference();
    if (RC.isRCObject(object)) {
      RCHeader.incRC(object);
    }
  }
}
