/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package com.ibm.jikesrvm.mm.mmtk;

import org.mmtk.plan.Plan;
import org.mmtk.plan.CollectorContext;
import org.mmtk.plan.MutatorContext;
import org.mmtk.plan.PlanConstraints;

import com.ibm.jikesrvm.memorymanagers.mminterface.Selected;

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
@Uninterruptible public final class ActivePlan extends org.mmtk.vm.ActivePlan {

  /* Collector and Mutator Context Management */
  private static final int MAX_CONTEXTS = 100;
  private static Selected.Collector[] collectors = new Selected.Collector[MAX_CONTEXTS];
  private static int collectorCount = 0; // Number of collector instances 
  private static Selected.Mutator[] mutators = new Selected.Mutator[MAX_CONTEXTS];
  private static int mutatorCount = 0; // Number of mutator instances 
  private static SynchronizedCounter mutatorCounter = new SynchronizedCounter();

  /** @return The active Plan instance. */
  public final Plan global() throws InlinePragma {
    return Selected.Plan.get();
  } 
  
  /** @return The active PlanConstraints instance. */
  public final PlanConstraints constraints() throws InlinePragma {
    return Selected.Constraints.get();
  } 
  
  /** @return The active CollectorContext instance. */
  public final CollectorContext collector() throws InlinePragma {
    return Selected.Collector.get();
  }
  
  /** @return The active MutatorContext instance. */
  public final MutatorContext mutator() throws InlinePragma {
    return Selected.Mutator.get();
  }

  /** Flush the mutator remembered sets (if any) for this active plan */
  public static final void flushRememberedSets() {
     Selected.Mutator.get().flushRememberedSets();
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
   * Return the Selected.Collector instance given its unique identifier.
   * 
   * @param id The identifier of the Selected.Collector to return
   * @return The specified Selected.Collector
   */ 
  public final Selected.Collector selectedCollector(int id) throws InlinePragma {
    return collectors[id];
  }
  /**
   * Return the Selected.Mutator instance given its unique identifier.
   *
   * @param id The identifier of the Selected.Mutator to return
   * @return The specified Selected.Mutator
   */
  public final Selected.Mutator selectedMutator(int id) throws InlinePragma {
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
    collectors[collectorCount] = (Selected.Collector) collector;
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
    mutators[mutatorCount] = (Selected.Mutator) mutator;
    return mutatorCount++;
  } 
}
