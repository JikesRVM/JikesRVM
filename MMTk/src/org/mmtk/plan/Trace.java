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

import org.mmtk.utility.Constants;
import org.mmtk.utility.deque.SharedDeque;
import org.mmtk.policy.RawPageSpace;

import org.vmmagic.pragma.*;

/**
 * This abstract class implements the core functionality for a transitive
 * closure over the heap.  This class holds the global state, TraceLocal
 * and its super-classes handle per-thread state.
 */
@Uninterruptible
public class Trace implements Constants {

  // Global pools for load-balancing deques
  final SharedDeque valuePool;
  final SharedDeque rootLocationPool;

  /**
   * Constructor
   */
  public Trace(RawPageSpace metaDataSpace) {
    valuePool = new SharedDeque("valuePool",metaDataSpace, 1);
    rootLocationPool = new SharedDeque("rootLocations", metaDataSpace, 1);
  }

  /**
   * Prepare for a new collection pass.
   */
  public void prepareNonBlocking() {
    valuePool.prepareNonBlocking();
    rootLocationPool.prepareNonBlocking();
  }

  /**
   * Prepare for a new collection pass.
   * All active GC threads take part.
   */
  public void prepare() {
    valuePool.prepare();
    rootLocationPool.prepareNonBlocking();
  }

  /**
   * Release resources after completing a collection pass.
   */
  public void release() {
    valuePool.reset();
    rootLocationPool.reset();
  }

  /**
   * Is there any work outstanding in this trace. That is are there any pages in the pools.
   */
  public boolean hasWork() {
    return (valuePool.enqueuedPages() + rootLocationPool.enqueuedPages()) > 0;
  }
}
