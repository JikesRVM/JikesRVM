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
package org.mmtk.utility.statistics;

import org.mmtk.utility.Log;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;

/**
 * This class implements a simple boolean counter (counting number of
 * phases where some boolean event is true).
 */
@Uninterruptible
public class BooleanCounter extends Counter {

  /****************************************************************************
   *
   * Instance variables
   */

  /**
   *
   */
  private final boolean[] state;

  protected int total = 0;
  private boolean running = false;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   *
   * @param name The name to be associated with this counter
   */
  public BooleanCounter(String name) {
    this(name, true, false);
  }

  /**
   * Constructor
   *
   * @param name The name to be associated with this counter
   * @param start {@code true} if this counter is to be implicitly started
   * when <code>startAll()</code> is called (otherwise the counter
   * must be explicitly started).
   */
  public BooleanCounter(String name, boolean start) {
    this(name, start, false);
  }

  /**
   * Constructor
   *
   * @param name The name to be associated with this counter
   * @param start True if this counter is to be implicitly started
   * when <code>startAll()</code> is called (otherwise the counter
   * must be explicitly started).
   * @param mergephases True if this counter does not separately
   * report GC and Mutator phases.
   */
  public BooleanCounter(String name, boolean start, boolean mergephases) {
    super(name, start, mergephases);
    state = new boolean[Stats.MAX_PHASES];
  }

  /****************************************************************************
   *
   * Counter-specific methods
   */

  /**
   * Set the boolean to {@code true} for this phase, increment the total.
   */
  public void set() {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(Stats.phase == Stats.MAX_PHASES -1 || !state[Stats.phase]);
    state[Stats.phase] = true;
    total++;
  }

  /****************************************************************************
   *
   * Generic counter control methods: start, stop, print etc
   */

  /**
   * {@inheritDoc}
   */
  @Override
  protected void start() {
    if (!Stats.gatheringStats) return;
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!running);
    running = true;
  }

  @Override
  protected void stop() {
    if (!Stats.gatheringStats) return;
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(running);
    running = false;
  }

  /**
   * The phase has changed (from GC to mutator or mutator to GC).
   * Take action with respect to the last phase if necessary.
   * <b>Do nothing in this case.</b>
   *
   * @param oldPhase The last phase
   */
  @Override
  void phaseChange(int oldPhase) {}

  /**
   * {@inheritDoc}
   * Print '0' for {@code false}, '1' for {@code true}.
   *
   * @param phase The phase to be printed
   */
  @Override
  protected final void printCount(int phase) {
    if (VM.VERIFY_ASSERTIONS && mergePhases())
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert((phase | 1) == (phase + 1));
    if (mergePhases())
      printValue((state[phase] || state[phase + 1]) ? 1 : 0);
    else
      printValue((state[phase]) ? 1 : 0);
  }

  /**
   * Print the current total number of {@code true} phases for this counter
   */
  @Override
  protected final void printTotal() {
    int total = 0;
    for (int p = 0; p <= Stats.phase; p++) {
      total += (state[p]) ? 1 : 0;
    }
    printValue(total);
  }

  @Override
  protected final void printTotal(boolean mutator) {
    int total = 0;
    for (int p = (mutator) ? 0 : 1; p <= Stats.phase; p += 2) {
      total += (state[p]) ? 1 : 0;
    }
    printValue(total);
  }

  /**
   * {@inheritDoc}
   * <b>Do nothing in this case.</b>
   */
  @Override
  protected final void printMin(boolean mutator) {}

  /**
   * {@inheritDoc}
   * <b>Do nothing in this case.</b>
   */
  @Override
  protected final void printMax(boolean mutator) {}

  /**
   * Print the given value
   *
   * @param value The value to be printed
   */
  void printValue(int value) {
    Log.write(value);
  }

  @Override
  public void printLast() {
    if (Stats.phase > 0) printCount(Stats.phase - 1);
  }
}
