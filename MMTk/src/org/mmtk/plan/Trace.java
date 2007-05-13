/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan;

import org.mmtk.utility.Constants;
import org.mmtk.utility.deque.SharedDeque;
import org.mmtk.policy.RawPageSpace;

import org.vmmagic.pragma.*;

/**
 * This abstract class implements the core functionality for a transitive
 * closure over the heap.  This class holds the global state, TraceLocal
 * and its super-classes handle per-thread state.
 */
@Uninterruptible public class Trace implements Constants {

  // Global pools for load-balancing deques
  final SharedDeque valuePool;
  final SharedDeque rootLocationPool;
  final SharedDeque interiorRootPool;

  /**
   * Constructor
   */
  public Trace(RawPageSpace metaDataSpace) {
    valuePool = new SharedDeque(metaDataSpace, 1);
    rootLocationPool = new SharedDeque(metaDataSpace, 1);
    interiorRootPool = new SharedDeque(metaDataSpace, 2);
  }

  /**
   * Prepare for a new collection pass.
   */
  public void prepare() {
    // Nothing to do.
  }

  /**
   * Release resources after completing a collection pass.
   */
  public void release() {
    valuePool.reset();
    rootLocationPool.reset();
    interiorRootPool.reset();
  }
}
