/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.utility.sanitychecker;

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.Trace;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements the simply sanity closure.
 * 
 *
 * @author Daniel Frampton
 */
@Uninterruptible public final class SanityTraceLocal extends TraceLocal {

  private final SanityCheckerLocal sanityChecker;

  /**
   * Constructor
   */
  public SanityTraceLocal(Trace trace, SanityCheckerLocal scl) {
    super(trace);
    sanityChecker = scl;
  }

  /****************************************************************************
   * 
   * Object processing and tracing
   */

  /**
   * This method is the core method during the trace of the object graph.
   * The role of this method is to:
   * 
   * @param object The object to be traced.
   * @param root Is this object a root?
   * @return The new reference to the same object instance.
   */
  @Inline
  public ObjectReference traceObject(ObjectReference object, boolean root) { 
    sanityChecker.processObject(this, object, root);
    return object;
  }

  /**
   * Will this object move from this point on, during the current trace ?
   * 
   * @param object The object to query.
   * @return True if the object will not move.
   */
  public boolean willNotMove(ObjectReference object) {
    // We never move objects!
    return true;
  }

}
