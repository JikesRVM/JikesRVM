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
package org.mmtk.vm;

import org.mmtk.plan.Plan;
import org.mmtk.plan.CollectorContext;
import org.mmtk.plan.MutatorContext;
import org.mmtk.plan.PlanConstraints;

import org.mmtk.utility.Log;

import org.vmmagic.pragma.*;

/**
 * Stub to give access to plan local, constraint and global instances
 */
@Uninterruptible public abstract class ActivePlan {

  /** @return The active Plan instance. */
  public abstract Plan global();

  /** @return The active PlanConstraints instance. */
  public abstract PlanConstraints constraints();

  /** @return The active <code>CollectorContext</code> instance. */
  public abstract CollectorContext collector();

  /** @return Is the active thread a mutator thread. */
  public abstract boolean isMutator();

  /** @return The active <code>MutatorContext</code> instance. */
  public abstract MutatorContext mutator();

  /** @return The log for the active thread */
  public abstract Log log();

  /** @return The maximum number of collector threads that may participate in parallel GC. */
  public abstract int collectorCount();

  /** Reset the mutator iterator */
  public abstract void resetMutatorIterator();

  /**
   * Return the next <code>MutatorContext</code> in a
   * synchronized iteration of all mutators.
   *
   * @return The next <code>MutatorContext</code> in a
   *  synchronized iteration of all mutators, or
   *  <code>null</code> when all mutators have been done.
   */
  public abstract MutatorContext getNextMutator();
}
