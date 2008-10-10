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
package org.mmtk.harness.vm;

import org.mmtk.harness.Collector;
import org.mmtk.harness.Mutator;
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

  /** Initialise static state */
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

  /** Used for iterating over mutators */
  private static int mutatorIndex;

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

  /**
   * Return the <code>CollectorContext</code> instance given its unique identifier.
   *
   * @param id The identifier of the <code>CollectorContext</code>  to return
   * @return The specified <code>CollectorContext</code>
   */
  @Override
  public CollectorContext collector(int id) { return Collector.get(id).getContext(); }

  /**
   * Return the <code>MutatorContext</code> instance given its unique identifier.
   *
   * @param id The identifier of the <code>MutatorContext</code>  to return
   * @return The specified <code>MutatorContext</code>
   */
  @Override
  public MutatorContext mutator(int id) { return Mutator.get(id).getContext(); }

  /** @return The number of registered <code>CollectorContext</code> instances. */
  @Override
  public int collectorCount() { return Collector.count(); }

  /** @return The number of registered <code>MutatorContext</code> instances. */
  @Override
  public int mutatorCount() { return Mutator.count(); }

  /** Reset the mutator iterator */
  @Override
  public void resetMutatorIterator() { mutatorIndex = 0; }

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
      if (mutatorIndex >= Mutator.count()) return null;
      return Mutator.get(mutatorIndex++).getContext();
    }
  }

  /**
   * Register a new <code>CollectorContext</code> instance.
   *
   * @param collector The <code>CollectorContext</code> to register.
   * @return The <code>CollectorContext</code>'s unique identifier
   */
  @Interruptible
  @Override
  public int registerCollector(CollectorContext collector) {
    return Collector.register(collector);
  }

  /**
   * Register a new <code>MutatorContext</code> instance.
   *
   * @param mutator The <code>MutatorContext</code> to register.
   * @return The <code>MutatorContext</code>'s unique identifier
   */
  @Interruptible
  @Override
  public int registerMutator(MutatorContext mutator) {
    return Mutator.register(mutator);
  }
}
