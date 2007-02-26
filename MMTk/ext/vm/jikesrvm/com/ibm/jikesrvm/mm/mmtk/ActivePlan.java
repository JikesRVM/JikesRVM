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
  @Inline
  public final Plan global() { 
    return Selected.Plan.get();
  } 
  
  /** @return The active PlanConstraints instance. */
  @Inline
  public final PlanConstraints constraints() { 
    return Selected.Constraints.get();
  } 
  
  /** @return The active CollectorContext instance. */
  @Inline
  public final CollectorContext collector() { 
    return Selected.Collector.get();
  }
  
  /** @return The active MutatorContext instance. */
  @Inline
  public final MutatorContext mutator() { 
    return Selected.Mutator.get();
  }

  /** Flush the mutator remembered sets (if any) for this active plan */
  public static void flushRememberedSets() {
     Selected.Mutator.get().flushRememberedSets();
  }
    
  /**
   * Return the CollectorContext instance given its unique identifier.
   *
   * @param id The identifier of the CollectorContext to return
   * @return The specified CollectorContext
   */ 
  @Inline
  public final CollectorContext collector(int id) { 
    return collectors[id];
  }
  
  /**
   * Return the MutatorContext instance given its unique identifier.
   * 
   * @param id The identifier of the MutatorContext to return
   * @return The specified MutatorContext
   */ 
  @Inline
  public final MutatorContext mutator(int id) { 
    return mutators[id];
  }

  /**
   * Return the Selected.Collector instance given its unique identifier.
   * 
   * @param id The identifier of the Selected.Collector to return
   * @return The specified Selected.Collector
   */ 
  @Inline
  public final Selected.Collector selectedCollector(int id) { 
    return collectors[id];
  }
  /**
   * Return the Selected.Mutator instance given its unique identifier.
   *
   * @param id The identifier of the Selected.Mutator to return
   * @return The specified Selected.Mutator
   */
  @Inline
  public final Selected.Mutator selectedMutator(int id) { 
    return mutators[id];
  }

  
  /** @return The number of registered CollectorContext instances. */
  @Inline
  public final int collectorCount() { 
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
  @Interruptible
  public final int registerCollector(CollectorContext collector) { 
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
  @Interruptible
  public final int registerMutator(MutatorContext mutator) { 
    mutators[mutatorCount] = (Selected.Mutator) mutator;
    return mutatorCount++;
  } 
}
