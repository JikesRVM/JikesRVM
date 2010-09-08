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
 * Phases of a garbage collection.
 *
 * A concurrent phase runs concurrently with mutator activity.
 */
@Uninterruptible
public final class ConcurrentPhase extends Phase
  implements Constants {

  /****************************************************************************
   * Instance fields
   */

  /**
   * The atomic scheduled phase to use when concurrent collection is not allowed
   */
  private final int atomicScheduledPhase;

  /**
   * Construct a complex phase from an array of phase IDs.
   *
   * @param name The name of the phase.
   * @param atomicScheduledPhase The atomic scheduled phase
   */
  protected ConcurrentPhase(String name, int atomicScheduledPhase) {
    super(name, null);
    this.atomicScheduledPhase = atomicScheduledPhase;
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(getSchedule(this.atomicScheduledPhase) != SCHEDULE_CONCURRENT);
  }

  /**
   * Construct a complex phase from an array of phase IDs, but using
   * the specified timer rather than creating one.
   *
   * @param name The name of the phase.
   * @param timer The timer for this phase to contribute to.
   * @param atomicScheduledPhase The atomic scheduled phase
   */
  protected ConcurrentPhase(String name, Timer timer, int atomicScheduledPhase) {
    super(name, timer);
    if (VM.VERIFY_ASSERTIONS) {
      /* Timers currently unsupported on concurrent phases */
      VM.assertions._assert(timer == null);
    }
    this.atomicScheduledPhase = atomicScheduledPhase;
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(getSchedule(this.atomicScheduledPhase) != SCHEDULE_CONCURRENT);
  }

  /**
   * Display a description of this phase, for debugging purposes.
   */
  protected void logPhase() {
    Log.write("ConcurrentPhase(");
    Log.write(name);
    Log.write(")");
  }

  /**
   * Return the atomic schedule phase, to be used in place of this phase when
   * concurrent collection is not available.
   *
   * @return The atomic scheduled phase.
   */
  protected int getAtomicScheduledPhase() {
    return this.atomicScheduledPhase;
  }
}
