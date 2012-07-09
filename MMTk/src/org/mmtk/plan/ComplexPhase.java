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
package org.mmtk.plan;

import org.mmtk.utility.Constants;
import org.mmtk.utility.statistics.Timer;
import org.mmtk.utility.Log;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;

/**
 * Phases of a garbage collection.<p>
 *
 * A complex phase is a sequence of phases.
 *
 */
@Uninterruptible
public final class ComplexPhase extends Phase
  implements Constants {

  /****************************************************************************
   * Instance fields
   */

  /**
   * The phases that comprise this phase.
   */
  private final int[] scheduledSubPhases;

  /**
   * Construct a complex phase from an array of phase IDs.
   *
   * @param name The name of the phase.
   * @param scheduledSubPhases The sub phases
   */
  protected ComplexPhase(String name, int[] scheduledSubPhases) {
    super(name);
    this.scheduledSubPhases = scheduledSubPhases;
    checkPhases();
  }

  /**
   * Construct a complex phase from an array of phase IDs, but using
   * the specified timer rather than creating one.
   *
   * @param name The name of the phase.
   * @param timer The timer for this phase to contribute to.
   * @param scheduledSubPhases The sub phases
   */
  protected ComplexPhase(String name, Timer timer, int[] scheduledSubPhases) {
    super(name, timer);
    this.scheduledSubPhases = scheduledSubPhases;
    checkPhases();
  }

  /**
   * Validate the scheduled sub phases.
   */
  private void checkPhases() {
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(scheduledSubPhases.length > 0);
      for(int scheduledPhase: scheduledSubPhases) {
        VM.assertions._assert(getSchedule(scheduledPhase) > 0);
        VM.assertions._assert(getPhaseId(scheduledPhase) > 0);
      }
    }
  }

  /**
   * The number of scheduled sub phases.
   */
  protected int count() {
    return scheduledSubPhases.length;
  }

  /**
   * Return an individual scheduled sub phase.
   *
   * @param index The index
   * @return The scheduled phase.
   */
  protected int get(int index) {
    return scheduledSubPhases[index];
  }

  @Override
  protected void logPhase() {
    Log.write("ComplexPhase(");
    Log.write(name);
    Log.write(", < ");
    for (int subPhase : scheduledSubPhases) {
      short ordering = getSchedule(subPhase);
      short phaseId = getPhaseId(subPhase);
      Log.write(getScheduleName(ordering));
      Log.write("(");
      Log.write(getName(phaseId));
      Log.write(") ");
    }
    Log.write(">)");
  }

  /**
   * Replace a scheduled phase. Used for example to replace a placeholder.
   *
   * @param oldScheduledPhase The scheduled phase to replace.
   * @param newScheduledPhase The new scheduled phase.
   */
  public void replacePhase(int oldScheduledPhase, int newScheduledPhase) {
    for (int i = 0; i < scheduledSubPhases.length; i++) {
      int scheduledPhase = scheduledSubPhases[i];
      if (scheduledPhase == oldScheduledPhase) {
        /* Replace */
        scheduledSubPhases[i] = newScheduledPhase;
      } else if (getSchedule(scheduledPhase) == SCHEDULE_COMPLEX) {
        /* Recurse */
        ComplexPhase p = (ComplexPhase)getPhase(getPhaseId(scheduledPhase));
        p.replacePhase(oldScheduledPhase, newScheduledPhase);
      }
    }
  }
}
