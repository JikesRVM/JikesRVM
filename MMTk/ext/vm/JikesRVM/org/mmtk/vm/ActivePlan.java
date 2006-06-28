/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.vm;

import org.mmtk.plan.Plan;
import org.mmtk.plan.CollectorContext;
import org.mmtk.plan.MutatorContext;
import org.mmtk.plan.PlanConstraints;

import com.ibm.JikesRVM.memoryManagers.mmInterface.SelectedPlan;
import com.ibm.JikesRVM.memoryManagers.mmInterface.SelectedCollectorContext;
import com.ibm.JikesRVM.memoryManagers.mmInterface.SelectedMutatorContext;
import com.ibm.JikesRVM.memoryManagers.mmInterface.SelectedPlanConstraints;

import org.vmmagic.pragma.*;

/**
 * This class contains interfaces to access the current plan, plan local and
 * plan constraints instances.
 *
 * $Id$
 *
 * @author Daniel Frampton 
 * @author Robin Garner 
 * @author Steve Blackburn
 * @version $Revision$
 * @date $Date$
 */
public final class ActivePlan implements Uninterruptible {

  /* Collector and Mutator Context Management */
  private static final int MAX_CONTEXTS = 100;
  private static SelectedCollectorContext[] collectors = new SelectedCollectorContext[MAX_CONTEXTS];
  private static int collectorCount = 0; // Number of collector instances 
  private static SelectedMutatorContext[] mutators = new SelectedMutatorContext[MAX_CONTEXTS];
  private static int mutatorCount = 0; // Number of mutator instances 
  private static SynchronizedCounter mutatorCounter = new SynchronizedCounter();

  /** @return The active Plan instance. */
  public static final Plan global() throws InlinePragma {
    return SelectedPlan.get();
  } 
  
  /** @return The active PlanConstraints instance. */
  public static final PlanConstraints constraints() throws InlinePragma {
    return SelectedPlanConstraints.get();
  } 
  
  /** @return The active CollectorContext instance. */
  public static final CollectorContext collector() throws InlinePragma {
    return SelectedCollectorContext.get();
  }
  
  /** @return The active MutatorContext instance. */
  public static final MutatorContext mutator() throws InlinePragma {
    return SelectedMutatorContext.get();
  }

  /**
   * Return the CollectorContext instance given its unique identifier.
   *
   * @param id The identifier of the CollectorContext to return
   * @return The specified CollectorContext
   */ 
  public static final CollectorContext collector(int id) throws InlinePragma {
    return collectors[id];
  }
  
  /**
   * Return the MutatorContext instance given its unique identifier.
   * 
   * @param id The identifier of the MutatorContext to return
   * @return The specified MutatorContext
   */ 
  public static final MutatorContext mutator(int id) throws InlinePragma {
    return mutators[id];
  }

  /**
   * Return the SelectedCollectorContext instance given its unique identifier.
   * 
   * @param id The identifier of the SelectedCollectorContext to return
   * @return The specified SelectedCollectorContext
   */ 
  public static final SelectedCollectorContext selectedCollector(int id) throws InlinePragma {
    return collectors[id];
  }
  /**
   * Return the SelectedMutatorContext instance given its unique identifier.
   *
   * @param id The identifier of the SelectedMutatorContext to return
   * @return The specified SelectedMutatorContext
   */
  public static final SelectedMutatorContext selectedMutator(int id) throws InlinePragma {
    return mutators[id];
  }

  
  /** @return The number of registered CollectorContext instances. */
  public static final int collectorCount() throws InlinePragma {
    return collectorCount;
  }
   
  /** @return The number of registered MutatorContext instances. */
  public static final int mutatorCount() {
    return mutatorCount;
  }

  /** Reset the mutator iterator */
  public static void resetMutatorIterator() {
    mutatorCounter.reset();
  }
 
  /** 
   * Return the next <code>MutatorContext</code> in a
   * synchronized iteration of all mutators.
   *  
   * @return The next <code>MutatorContext</code> in a
   *  synchronized iteration of all mutators, or
   *  <code>null</code> when all mutators have been done.
   */
  public static MutatorContext getNextMutator() {
    int id = mutatorCounter.increment();
    return id >= mutatorCount ? null : mutators[id];
  } 

  /**
   * Register a new CollectorContext instance.
   *
   * FIXME: Possible race in allocation of ids. Should be synchronized.
   *
   * @param collector The CollectorContext to register
   * @return The CollectorContext's unique identifier
   */
  public static final int registerCollector(CollectorContext collector) throws InterruptiblePragma {
    collectors[collectorCount] = (SelectedCollectorContext) collector;
    return collectorCount++;
  }
  
  /**
   * Register a new MutatorContext instance.
   *
   * FIXME: Possible race in allocation of ids. Should be synchronized.
   *
   * @param mutator The MutatorContext to register
   * @return The MutatorContext's unique identifier
   */
  public static final int registerMutator(MutatorContext mutator) throws InterruptiblePragma {
    mutators[mutatorCount] = (SelectedMutatorContext) mutator;
    return mutatorCount++;
  } 
}
