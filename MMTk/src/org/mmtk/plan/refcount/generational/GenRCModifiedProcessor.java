/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.plan.refcount.generational;

import org.mmtk.plan.TraceStep;
import org.mmtk.plan.refcount.RCHeader;
import org.mmtk.policy.Space;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This abstract class is the fundamental mechanism for performing a
 * transitive closure over an object graph.<p>
 * 
 * @see org.mmtk.plan.TraceLocal
 * 
 *
 */
@Uninterruptible public final class GenRCModifiedProcessor extends TraceStep { 
  private final GenRCTraceLocal trace;

  
  public GenRCModifiedProcessor(GenRCTraceLocal t) {
    trace = t; 
  }
  
  /**
   * Trace a reference during GC.
   * 
   * @param objLoc The location containing the object reference to be
   * traced.
   */
  @Inline
  public void traceObjectLocation(Address objLoc) { 
    ObjectReference object = objLoc.loadObjectReference();
    if (!object.isNull()) {
      if (Space.isInSpace(GenRC.NS, object)) {
        object = GenRC.nurserySpace.traceObject(trace, object);
        RCHeader.incRC(object);
        objLoc.store(object);
      } else if (GenRC.isRCObject(object)) {
        RCHeader.incRC(object);
      }
    }
  }
}
