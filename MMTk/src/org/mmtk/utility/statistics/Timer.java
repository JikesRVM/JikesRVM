/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */

package org.mmtk.utility.statistics;

import org.mmtk.utility.Log;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;

/**
 * This class implements a simple timer.
 */
@Uninterruptible public class Timer extends LongCounter {

  /****************************************************************************
   * 
   * Initialization
   */

  /**
   * Constructor
   * 
   * @param name The name to be associated with this counter
   */
  public Timer(String name) {
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
  public Timer(String name, boolean start) {
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
  public Timer(String name, boolean start, boolean mergephases) {
    super(name, start, mergephases);
  }

  /****************************************************************************
   * 
   * Counter-specific methods
   */

  /**
   * Get the current value for this timer
   * 
   * @return The current value for this timer
   */
  @Inline
  protected final long getCurrentValue() {
    return VM.statistics.cycles();
  }

  /**
   * Print the total in microseconds
   */
  final void printTotalMicro() {
    printMicro(totalCount);
  }

  /**
   * Print the total in milliseconds
   */
  public final void printTotalMillis() {
    printMillis(totalCount);
  }

  /**
   * Print the total in seconds
   */
  public final void printTotalSecs() {
    printSecs(totalCount);
  }

  /**
   * Print a value (in milliseconds)
   * 
   * @param value The value to be printed
   */
  final void printValue(long value) {
    printMillis(value);
  }

  /**
   * Print a value in microseconds
   * 
   * @param value The value to be printed
   */
  final void printMicro(long value) {
    Log.write(1000 * VM.statistics.cyclesToMillis(value));
  }

  /**
   * Print a value in milliseconds
   * 
   * @param value The value to be printed
   */
  final void printMillis(long value) {
    Log.write(VM.statistics.cyclesToMillis(value));
  }

  /**
   * Print a value in seconds
   * 
   * @param value The value to be printed
   */
  final void printSecs(long value) {
    Log.write(VM.statistics.cyclesToSecs(value));
  }

  /**
   * Get the current value of the timer in milliseconds
   */
  final double getTotalMillis() {
    return VM.statistics.cyclesToMillis(totalCount);
  }

}

