/*
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
 * 
 * $Id$
 *
 * $Id$
 *
 * @author Perry Cheng
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 */
public class Trace implements Constants, Uninterruptible {

  // Global pools for load-balancing deques
  final SharedDeque valuePool;
  final SharedDeque remsetPool;
  final SharedDeque rootLocationPool;
  final SharedDeque interiorRootPool;

  /**
   * Constructor
   */
  public Trace(RawPageSpace metaDataSpace) {
    valuePool = new SharedDeque(metaDataSpace, 1);
    remsetPool = new SharedDeque(metaDataSpace, 1);
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
    remsetPool.reset();
    rootLocationPool.reset();
    interiorRootPool.reset();
  }
}
