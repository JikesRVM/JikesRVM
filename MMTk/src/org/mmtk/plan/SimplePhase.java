/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan;

import org.mmtk.utility.Constants;
import org.mmtk.utility.statistics.Timer;
import org.mmtk.utility.options.Options;
import org.mmtk.utility.Log;

import org.mmtk.vm.ActivePlan;
import org.mmtk.vm.Assert;
import org.mmtk.vm.Collection;

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
public final class SimplePhase extends Phase
  implements Uninterruptible, Constants {
  /*
   * Instance fields
   */

  /* Define the ordering of global and local collection phases */
  protected final boolean globalFirst;
  protected final boolean globalLast;
  protected final boolean local;

  /* placeholder plans are no-ops */
  protected final boolean placeholder;

  /**
   * Do we perform processing on behalf of threads that are blocked
   * in native code ?
   */
  protected final boolean includeNP;

  /**
   * Construct a phase given just a name and a global/local ordering
   * scheme.
   *
   * @param name The name of the phase
   * @param ordering Order of global/local phases
   */
  public SimplePhase(String name, int ordering) {
    this(name, ordering, false);
  }

  /**
   * Construct a phase.
   *
   * @param name Display name of the phase
   * @param ordering Order of global/local phases
   * @param includeNonParticipating Do we include non-participants ?
   */
  public SimplePhase(String name, int ordering,
                     boolean includeNonParticipating) {
    super(name);
    this.globalFirst = (ordering & Phase.GLOBAL_FIRST_MASK) != 0;
    this.globalLast  = (ordering & Phase.GLOBAL_LAST_MASK) != 0;
    this.local       = (ordering & Phase.LOCAL_MASK) != 0;
    this.placeholder = (ordering == Phase.PLACEHOLDER);
    this.includeNP   = includeNonParticipating;
  }

  /**
   * Construct a phase, re-using a specified timer.
   *
   * @param name Display name of the phase
   * @param timer Timer for this phase to contribute to
   * @param ordering Order of global/local phases
   */
  public SimplePhase(String name, Timer timer, int ordering) {
    this(name, timer, ordering, false);
  }

  /**
   * Construct a phase, re-using a specified timer, and optionally
   * including non-participating threads.
   *
   * @param name Display name of the phase
   * @param timer Timer for this phase to contribute to
   * @param ordering Order of global/local phases
   */
  public SimplePhase(String name, Timer timer,
                     int ordering, boolean includeNonParticipating) {
    super(name, timer);
    this.globalFirst = (ordering & Phase.GLOBAL_FIRST_MASK) != 0;
    this.globalLast  = (ordering & Phase.GLOBAL_LAST_MASK) != 0;
    this.local       = (ordering & Phase.LOCAL_MASK) != 0;
    this.placeholder = (ordering == Phase.PLACEHOLDER);
    this.includeNP   = includeNonParticipating;
    if (Assert.VERIFY_ASSERTIONS) {
      Assert._assert(!includeNonParticipating || local);
    }
  }

  /**
   * Display a phase for debugging purposes.
   */
  protected final void logPhase() {
    Log.write("simple [");
    if (globalFirst) Log.write("G");
    if (local      ) Log.write("L");
    if (globalLast ) Log.write("G");
    if (includeNP  ) Log.write("+np");
    Log.write("] phase ");
    Log.writeln(name);
  }

  /**
   * Execute a phase during a collection.
   */
  protected final void delegatePhase() throws NoInlinePragma {
    boolean log = Options.verbose.getValue() >= 6;
    boolean logDetails = Options.verbose.getValue() >= 7;

    if (log) {
      Log.write("SimplePhase.delegatePhase ");
      logPhase();
    }

    if (placeholder) return;

    Plan plan = ActivePlan.global();
    PlanLocal planLocal = ActivePlan.local();

    /*
     * Synchronize at the start, and choose one CPU as the primary,
     * to perform global tasks.
     */
    int order = Collection.rendezvous(1000 + id);
    final boolean primary = order == 1;

    if (primary && timer != null) timer.start();

    if (globalFirst) { // Phase has a global component, executed first
      if (logDetails) Log.writeln("  global...");
      if (primary) plan.collectionPhase(id);
      Collection.rendezvous(2000 + id);
    }

    if (local) {      // Phase has a local component
      if (includeNP && primary) {
        /* Primary processor executes non-participants phases */
        for (int i=0; i < ActivePlan.localCount(); i++) {
          PlanLocal p = ActivePlan.local(i);
          if (Collection.isNonParticipating(p)) {
            if (logDetails)
              Log.writeln("Processing non-participating thread...");
            p.collectionPhase(id, false, primary);
          }
        }
      }
      if (logDetails) Log.writeln("  local...");
      planLocal.collectionPhase(id, true, primary);
      Collection.rendezvous(3000 + id);
    } // if(local)

    if (globalLast) {  // Phase has a global component, executed last
      if (logDetails) Log.writeln("  global...");
      if (primary) plan.collectionPhase(id);
      Collection.rendezvous(4000 + id);
    }

    if (primary && timer != null) timer.stop();
  }
}
