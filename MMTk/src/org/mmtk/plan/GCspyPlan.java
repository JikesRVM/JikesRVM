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
package org.mmtk.plan;

import org.mmtk.policy.ImmortalSpace;
import org.mmtk.utility.heap.VMRequest;

/**
 * This interface provides an immortal allocation space for GCspy objects.<p>
 *
 * TODO constant interfaces are bad practice. Converting this to a class does
 * not seem to be the right thing to do because it does not fit well with the
 * current MMTk architecture and the plan concept. It's probably best to fix
 * this up if we get around to updating GCSpy.
 */
@Deprecated
public interface GCspyPlan {

  /**
   * Any GCspy objects allocated after booting are allocated
   * in a separate immortal space.
   */
  ImmortalSpace gcspySpace = new ImmortalSpace("gcspy", VMRequest.discontiguous());

  /** The descriptor for the GCspy allocation space */
  int GCSPY = gcspySpace.getDescriptor();
}
