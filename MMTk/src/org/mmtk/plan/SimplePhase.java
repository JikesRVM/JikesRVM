/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan;

import org.mmtk.utility.Constants;
import org.mmtk.utility.statistics.Timer;
import org.mmtk.utility.options.Options;
import org.mmtk.utility.Log;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;

/**
 * Phases of a garbage collection.
 * 
 * A simple phase calls the collectionPhase method of a global
 * and/or all thread-local plan instances, and performs synchronization
 * and timing.
 * 
 * $Id$
 * 
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 */
@Uninterruptible public final class SimplePhase extends Phase
  implements Constants {
  /****************************************************************************
   * Instance fields
   */

  /* Define the ordering of global and local collection phases */
  protected final boolean globalFirst;
  protected final boolean globalLast;
  protected final boolean perCollector;
  protected final boolean perMutator;

  /* placeholder plans are no-ops */
  protected final boolean placeholder;

  /**
   * Construct a phase given just a name and a global/local ordering
   * scheme.
   * 
   * @param name The name of the phase
   * @param ordering Order of global/local phases
   */
  public SimplePhase(String name, int ordering) {
    super(name);
    this.globalFirst = (ordering & Phase.GLOBAL_FIRST_MASK) != 0;
    this.globalLast = (ordering & Phase.GLOBAL_LAST_MASK) != 0;
    this.perCollector = (ordering & Phase.COLLECTOR_MASK) != 0;
    this.perMutator = (ordering & Phase.MUTATOR_MASK) != 0;
    this.placeholder = (ordering == Phase.PLACEHOLDER);
  }

  /**
   * Construct a phase, re-using a specified timer.
   * 
   * @param name Display name of the phase
   * @param timer Timer for this phase to contribute to
   * @param ordering Order of global/local phases
   */
  public SimplePhase(String name, Timer timer, int ordering) {
    super(name, timer);
    this.globalFirst = (ordering & Phase.GLOBAL_FIRST_MASK) != 0;
    this.globalLast = (ordering & Phase.GLOBAL_LAST_MASK) != 0;
    this.perCollector = (ordering & Phase.COLLECTOR_MASK) != 0;
    this.perMutator = (ordering & Phase.MUTATOR_MASK) != 0;
    this.placeholder = (ordering == Phase.PLACEHOLDER);
  }

  /**
   * Display a phase for debugging purposes.
   */
  protected final void logPhase() {
    Log.write("simple [");
    if (globalFirst) Log.write("G");
    if (perCollector) Log.write("C");
    if (perMutator  ) Log.write("M");
    if (globalLast ) Log.write("G");
    Log.write("] phase ");
    Log.writeln(name);
  }

  /**
   * Execute a phase during a collection.
   */
  @NoInline
  protected final void delegatePhase() { 
    boolean log = Options.verbose.getValue() >= 6;
    boolean logDetails = Options.verbose.getValue() >= 7;

    if (log) {
      Log.write("SimplePhase.delegatePhase ");
      logPhase();
    }

    if (placeholder) return;

    Plan plan = VM.activePlan.global();
    CollectorContext collector = VM.activePlan.collector();

    /*
     * Synchronize at the start, and choose one CPU as the primary,
     * to perform global tasks.
     */
    int order = VM.collection.rendezvous(1000 + id);
    final boolean primary = order == 1;

    if (primary && timer != null) timer.start();
    if (globalFirst) { // Phase has a global component, executed first
      if (logDetails) Log.writeln("  global...");
      if (primary) plan.collectionPhase(id);
      VM.collection.rendezvous(2000 + id);
    }

    if (perCollector) { // Phase has a per-collector component
      if (logDetails) Log.writeln("  per-collector...");
      collector.collectionPhase(id, primary);
      VM.collection.rendezvous(3000 + id);
    }

    if (perMutator) { // Phase has a per-mutator component
      if (logDetails) Log.writeln("  per-mutator...");
      /* iterate through all mutator contexts, worker-farmer */
      MutatorContext mutator = null;
      while ((mutator = VM.activePlan.getNextMutator()) != null) {
        mutator.collectionPhase(id, primary);
      }
      VM.collection.rendezvous(4000 + id);
      if (primary) {
        VM.activePlan.resetMutatorIterator();
      }
      VM.collection.rendezvous(4500 + id);
    }

    if (globalLast) { // Phase has a global component, executed last
      if (logDetails) Log.writeln("  global...");
      if (primary) plan.collectionPhase(id);
      VM.collection.rendezvous(5000 + id);
    }

    if (primary && timer != null) timer.stop();
  }

  /**
   * Change the ordering of the phase. This can be used, for example,
   * to realise a placeholder phase at runtime.
   * 
   * @param ordering The new ordering.
   */
  public void changeOrdering(int ordering) {
  }
}
