/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */

package org.mmtk.utility.statistics;

import org.mmtk.utility.Log;
import org.mmtk.vm.Statistics;

import org.vmmagic.pragma.*;

/**
 * This class implements a simple timer.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 * $Id$
 */
public class Timer extends LongCounter
  implements Uninterruptible {

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
  final protected long getCurrentValue() throws InlinePragma {
    return Statistics.cycles();
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
    Log.write(1000*Statistics.cyclesToMillis(value));
  }

  /**
   * Print a value in milliseconds
   *
   * @param value The value to be printed
   */
  final void printMillis(long value) {
    Log.write(Statistics.cyclesToMillis(value));
  }

  /**
   * Print a value in seconds
   *
   * @param value The value to be printed
   */
  final void printSecs(long value) {
    Log.write(Statistics.cyclesToSecs(value));
  }
}

