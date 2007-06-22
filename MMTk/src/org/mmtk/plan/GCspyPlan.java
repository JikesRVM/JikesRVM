/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.plan;

import org.mmtk.policy.ImmortalSpace;

/**
 * This interface provides an immortal allocation space for GCspy objects.
 */
public interface GCspyPlan {

  int GCSPY_MB = 4; // 1 chunk

  /**
   * Any GCspy objects allocated after booting are allocated
   * in a separate immortal space.
   */
  ImmortalSpace gcspySpace =
      new ImmortalSpace("gcspy", Plan.DEFAULT_POLL_FREQUENCY, GCSPY_MB);

  /** The descriptor for the GCspy allocation space */
  int GCSPY = gcspySpace.getDescriptor();
}
