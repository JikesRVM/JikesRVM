package org.mmtk.vm;

import org.mmtk.plan.Plan;
import org.mmtk.plan.CollectorContext;
import org.mmtk.plan.MutatorContext;
import org.mmtk.plan.PlanConstraints;

/**
 * Stub to give access to plan local, constraint and global instances
 *
 * $Id$
 *
 * @author Daniel Frampton
 * @author Robin Garner
 */
public class ActivePlan {
  
  /** @return The active Plan instance. */
  public static final Plan global() {
    return null;
  }
	
  /** @return The active PlanConstraints instance. */
  public static final PlanConstraints constraints() {
    return null;
  }
	
  /** @return The active <code>CollectorContext</code> instance. */
  public static final CollectorContext collector() {
    return null;
  }

  /** @return The active <code>MutatorContext</code> instance. */
  public static final MutatorContext mutator() {
    return null;
  }
  
  /**
   * Return the <code>CollectorContext</code> instance given its unique identifier.
   * 
   * @param id The identifier of the <code>CollectorContext</code> to return
   * @return The specified <code>CollectorContext</code>
   */
  public static final CollectorContext collector(int id) {
    return null;
  }
  
  /**
   * Return the <code>MutatorContext</code> instance given it's unique identifier.
   * 
   * @param id The identifier of the <code>MutatorContext</code>  to return
   * @return The specified <code>MutatorContext</code> 
   */
  public static final MutatorContext mutator(int id) {
    return null;
  }
  
  /** @return The number of registered <code>CollectorContext</code>  instances. */
  public static final int collectorCount() {
    return 0;
  }
  
  /** @return The number of registered <code>MutatorContext</code> instances. */
  public static final int mutatorCount() {
    return 0;
  }

  /** Reset the mutator iterator */
  public static void resetMutatorIterator() {}
  
  /**
   * Return the next <code>MutatorContext</code> in a
   * synchronized iteration of all mutators.
   *  
   * @return The next <code>MutatorContext</code> in a
   *  synchronized iteration of all mutators, or
   *  <code>null</code> when all mutators have been done.
   */
  public static MutatorContext getNextMutator() {
    return null;
  }
  
  /**
   * Register a new <code>CollectorContext</code> instance.
   * 
   * @param collector The <code>CollectorContext</code> to register.
   * @return The <code>CollectorContext</code>'s unique identifier
   */
  public static final int registerCollector(CollectorContext collector) {
    return 0;
  }
  
  /**
   * Register a new <code>MutatorContext</code> instance.
   * 
   * @param mutator The <code>MutatorContext</code> to register.
   * @return The <code>MutatorContext</code>'s unique identifier
   */
  public static final int registerMutator(MutatorContext mutator) {
    return 0;
  }

}
