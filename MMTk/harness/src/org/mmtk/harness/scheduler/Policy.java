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
package org.mmtk.harness.scheduler;

/**
 * Interface to the per-thread scheduler policy.  The method yieldNow()
 * is called every time a thread reaches a yield point, and the method
 * returns true if a yield is required.
 */
public interface Policy {

  /**
   * @return True if the current policy requires that we yield.
   */
  boolean yieldNow();

}
