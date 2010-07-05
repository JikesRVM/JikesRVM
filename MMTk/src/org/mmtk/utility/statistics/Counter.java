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

import org.vmmagic.pragma.*;

/**
 *
 * This abstract class describes the interface of a generic counter.
 */
@Uninterruptible public abstract class Counter {

  /****************************************************************************
   *
   * Instance variables
   */

  private final String name;
  private final boolean start;
  private final boolean mergephases;

  /**
   * Allow for counters whose values are too complex to be simply printed out.
   */
  protected boolean complex = false;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   *
   * @param name The name to be associated with this counter
   */
  Counter(String name) {
    this(name, true, false);
  }

  /**
   * Constructor
   *
   * @param name The name to be associated with this counter
   * @param start True if this counter is to be implicitly started
   * when <code>startAll()</code> is called (otherwise the counter
   * must be explicitly started).
   */
  Counter(String name, boolean start) {
    this(name, start, false);
  }

  /**
   * Constructor
   *
   * @param name The name to be associated with this counter
   * @param start  True if this counter is to be implicitly started
   * when <code>startAll()</code> is called (otherwise the counter
   * must be explicitly started).
   * @param mergephases True if this counter does not separately
   * report GC and Mutator phases.
   */
  Counter(String name, boolean start, boolean mergephases) {
    this.name = name;
    this.start = start;
    this.mergephases = mergephases;
    Stats.newCounter(this);
  }

  /****************************************************************************
   *
   * Counter control methods: start, stop, print etc
   */

  /**
   * Start this counter
   */
  abstract void start();

  /**
   * Stop this counter
   */
  abstract void stop();

  /**
   * The phase has changed (from GC to mutator or mutator to GC).
   * Take action with respect to the last phase if necessary.
   *
   * @param oldPhase The last phase
   */
  abstract void phaseChange(int oldPhase);

  /**
   * Print the value of this counter for the given phase
   *
   * @param phase The phase to be printed
   */
  abstract void printCount(int phase);

  /**
   * Print the current total for this counter
   */
  abstract void printTotal();

  /**
   * Print the current total for either the mutator or GC phase
   *
   * @param mutator True if the total for the mutator phases is to be
   * printed (otherwise the total for the GC phases will be printed).
   */
  abstract void printTotal(boolean mutator);

  /**
   * Print the current minimum value for either the mutator or GC phase
   *
   * @param mutator True if the minimum for the mutator phase is to be
   * printed (otherwise the minimum for the GC phase will be printed).
   */
  abstract void printMin(boolean mutator);

  /**
   * Print the current maximum value for either the mutator or GC phase
   *
   * @param mutator True if the maximum for the mutator phase is to be
   * printed (otherwise the maximum for the GC phase will be printed).
   */
  abstract void printMax(boolean mutator);

  /**
   * Print statistics for the most recent phase
   */
  public void printLast() {
    if (Stats.phase > 0) printCount(Stats.phase - 1);
  }


  /****************************************************************************
   *
   * Accessor methods
   */

  /**
   * Return the name of this counter
   * @return The name of this counter
   */
  String getName() { return name; }

  /**
   * Return the (option) suffix to be used when reporting this counter
   * @return The suffix
   */
  String getColumnSuffix() { return ""; }

  /**
   * Return true if this counter is implicitly started when
   * <code>startAll()</code> is called.
   * @return True if this counter is implicitly started when
   *         <code>startAll()</code> is called.
   */
  boolean getStart() { return start; }

  /**
   * Return true if this counter will merge stats for GC and mutator phases.
   * @return True if this counter will merge stats for GC and mutator phases.
   */
  boolean mergePhases() { return mergephases; }

  boolean isComplex() { return complex; }
}
