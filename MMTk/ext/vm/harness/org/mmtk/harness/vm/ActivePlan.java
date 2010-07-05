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
package org.mmtk.harness.vm;

import java.util.concurrent.BlockingQueue;

import org.mmtk.harness.Collector;
import org.mmtk.harness.Mutator;
import org.mmtk.harness.Mutators;
import org.mmtk.harness.scheduler.Scheduler;
import org.mmtk.plan.Plan;
import org.mmtk.plan.CollectorContext;
import org.mmtk.plan.MutatorContext;
import org.mmtk.plan.PlanConstraints;
import org.mmtk.utility.Log;

import org.vmmagic.pragma.*;

/**
 * Stub to give access to plan local, constraint and global instances
 */
@Uninterruptible
public final class ActivePlan extends org.mmtk.vm.ActivePlan {

  /**
   * Initialise static state
   * @param prefix The name of the plan class (prefix for the associated classes)
   */
  public static void init(String prefix) {
    try {
      constraints = (PlanConstraints)Class.forName(prefix + "Constraints").newInstance();
    } catch (Exception ex) {
      throw new RuntimeException("Could not create PlanConstraints", ex);
    }
    try {
      plan = (Plan)Class.forName(prefix).newInstance();
    } catch (Exception ex) {
      throw new RuntimeException("Could not create Plan", ex);
    }
  }

  /** The global plan */
  public static Plan plan;

  /** The global constraints */
  public static PlanConstraints constraints;

  /** @return The active Plan instance. */
  @Override
  public Plan global() { return plan; };

  /** @return The active PlanConstraints instance. */
  @Override
  public PlanConstraints constraints() { return constraints; };

  /** @return The active <code>CollectorContext</code> instance. */
  @Override
  public CollectorContext collector() { return Collector.current().getContext(); };

  /** @return The active <code>MutatorContext</code> instance. */
  @Override
  public MutatorContext mutator() { return Mutator.current().getContext(); }

  /** @return The active <code>MutatorContext</code> instance. */
  @Override
  public Log log() { return Scheduler.currentLog(); }

  /** @return The number of registered <code>CollectorContext</code> instances. */
  @Override
  public int collectorCount() { return Collector.count(); }

  private BlockingQueue<Mutator> mutators = null;

  /** Reset the mutator iterator */
  @Override
  public void resetMutatorIterator() { mutators = null; }

  /**
   * Return the next <code>MutatorContext</code> in a
   * synchronized iteration of all mutators.
   *
   * @return The next <code>MutatorContext</code> in a
   *  synchronized iteration of all mutators, or
   *  <code>null</code> when all mutators have been done.
   */
  @Override
  public MutatorContext getNextMutator() {
    synchronized(ActivePlan.class) {
      if (mutators == null) {
        mutators = Mutators.getAll();
      }
    }
    Mutator m = mutators.poll();
    return m == null ? null : m.getContext();
  }
}
