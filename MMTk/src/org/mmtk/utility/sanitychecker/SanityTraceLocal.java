/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.utility.sanitychecker;

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.Trace;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements the simply sanity closure.
 */
@Uninterruptible
public final class SanityTraceLocal extends TraceLocal {

  private final SanityChecker sanityChecker;

  /**
   * Constructor
   */
  public SanityTraceLocal(Trace trace, SanityChecker sanityChecker) {
    super(trace);
    this.sanityChecker = sanityChecker;
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
  @Override
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
  @Override
  public boolean willNotMoveInCurrentCollection(ObjectReference object) {
    // We never move objects!
    return true;
  }
}
