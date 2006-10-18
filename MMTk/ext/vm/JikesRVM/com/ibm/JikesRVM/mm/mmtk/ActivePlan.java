/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package com.ibm.JikesRVM.mm.mmtk;

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
 * $Id: ActivePlan.java,v 1.5 2006/06/28 00:55:44 steveb-oss Exp $
 *
 * @author Daniel Frampton 
 * @author Robin Garner 
 * @author Steve Blackburn
 * @version $Revision: 1.5 $
 * @date $Date: 2006/06/28 00:55:44 $
 */
public final class ActivePlan extends org.mmtk.vm.ActivePlan implements Uninterruptible {

  /* Collector and Mutator Context Management */
  private static final int MAX_CONTEXTS = 100;
  private static SelectedCollectorContext[] collectors = new SelectedCollectorContext[MAX_CONTEXTS];
  private static int collectorCount = 0; // Number of collector instances 
  private static SelectedMutatorContext[] mutators = new SelectedMutatorContext[MAX_CONTEXTS];
  private static int mutatorCount = 0; // Number of mutator instances 
  private static SynchronizedCounter mutatorCounter = new SynchronizedCounter();

  /** @return The active Plan instance. */
  public final Plan global() throws InlinePragma {
    return SelectedPlan.get();
  } 
  
  /** @return The active PlanConstraints instance. */
  public final PlanConstraints constraints() throws InlinePragma {
    return SelectedPlanConstraints.get();
  } 
  
  /** @return The active CollectorContext instance. */
  public final CollectorContext collector() throws InlinePragma {
    return SelectedCollectorContext.get();
  }
  
  /** @return The active MutatorContext instance. */
  public final MutatorContext mutator() throws InlinePragma {
    return SelectedMutatorContext.get();
  }

  /** Flush the mutator remembered sets (if any) for this active plan */
  public static final void flushRememberedSets() {
     SelectedMutatorContext.get().flushRememberedSets();
  }
    
  /**
   * Return the CollectorContext instance given its unique identifier.
   *
   * @param id The identifier of the CollectorContext to return
   * @return The specified CollectorContext
   */ 
  public final CollectorContext collector(int id) throws InlinePragma {
    return collectors[id];
  }
  
  /**
   * Return the MutatorContext instance given its unique identifier.
   * 
   * @param id The identifier of the MutatorContext to return
   * @return The specified MutatorContext
   */ 
  public final MutatorContext mutator(int id) throws InlinePragma {
    return mutators[id];
  }

  /**
   * Return the SelectedCollectorContext instance given its unique identifier.
   * 
   * @param id The identifier of the SelectedCollectorContext to return
   * @return The specified SelectedCollectorContext
   */ 
  public final SelectedCollectorContext selectedCollector(int id) throws InlinePragma {
    return collectors[id];
  }
  /**
   * Return the SelectedMutatorContext instance given its unique identifier.
   *
   * @param id The identifier of the SelectedMutatorContext to return
   * @return The specified SelectedMutatorContext
   */
  public final SelectedMutatorContext selectedMutator(int id) throws InlinePragma {
    return mutators[id];
  }

  
  /** @return The number of registered CollectorContext instances. */
  public final int collectorCount() throws InlinePragma {
    return collectorCount;
  }
   
  /** @return The number of registered MutatorContext instances. */
  public final int mutatorCount() {
    return mutatorCount;
  }

  /** Reset the mutator iterator */
  public void resetMutatorIterator() {
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
  public MutatorContext getNextMutator() {
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
  public final int registerCollector(CollectorContext collector) throws InterruptiblePragma {
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
  public final int registerMutator(MutatorContext mutator) throws InterruptiblePragma {
    mutators[mutatorCount] = (SelectedMutatorContext) mutator;
    return mutatorCount++;
  } 
}
