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
 */
@Uninterruptible
public final class SimplePhase extends Phase
  implements Constants {
  /****************************************************************************
   * Instance fields
   */
  
  /**
   * Construct a phase given just a name and a global/local ordering
   * scheme.
   *
   * @param name The name of the phase
   */
  protected SimplePhase(String name) {
    super(name);
  }

  /**
   * Construct a phase, re-using a specified timer.
   *
   * @param name Display name of the phase
   * @param timer Timer for this phase to contribute to
   */
  protected SimplePhase(String name, Timer timer) {
    super(name, timer);
  }

  /**
   * Execute a phase during a collection.
   */
  @NoInline
  protected void execute(boolean primary, short schedule) {
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(schedule == SCHEDULE_GLOBAL || 
                            schedule == SCHEDULE_COLLECTOR || 
                            schedule == SCHEDULE_MUTATOR);
    }
    
    boolean log = Options.verbose.getValue() >= 6;
    boolean logDetails = Options.verbose.getValue() >= 7;

    if (log) {
      Log.write("Execute ");
      logPhase();
    }

    /* Start the timer */
    if (primary && timer != null) timer.start();

    Plan plan = VM.activePlan.global();
    CollectorContext collector = VM.activePlan.collector();

    /* Global phase */
    if (schedule == SCHEDULE_GLOBAL) {
      if (logDetails) Log.writeln(" as Global...");
      if (primary) plan.collectionPhase(id);
    }

    /* Collector phase */
    if (schedule == SCHEDULE_COLLECTOR) {
      if (logDetails) Log.writeln(" as Collector...");
      collector.collectionPhase(id, primary);
    }

    /* Mutator phase */
    if (schedule == SCHEDULE_MUTATOR) {
      if (logDetails) Log.writeln(" as Mutator...");
      /* Iterate through all mutator contexts */
      MutatorContext mutator = null;
      while ((mutator = VM.activePlan.getNextMutator()) != null) {
        mutator.collectionPhase(id, primary);
      }
      /* TODO: This can be skipped if the *next* phase is not mutator */
      rendezvous(9, schedule);
      if (primary) {
        VM.activePlan.resetMutatorIterator();
      }
    }
    
    rendezvous(10, schedule);
    
    /* Stop the timer */
    if (primary && timer != null) timer.stop();
  }

  /**
   * Display a phase for debugging purposes.
   */
  protected void logPhase() {
    Log.write("SimplePhase(");
    Log.write(name);
    Log.write(")");
  }
}
