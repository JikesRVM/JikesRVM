/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */

package org.mmtk.utility.statistics;

import org.mmtk.vm.VM_Interface;

import org.vmmagic.pragma.*;

/**
 * This class implements a simple counter of events of different sizes
 * (eg object allocations, where total number of objects and total
 * volume of objects would be counted).
 *
 * The counter is trivially composed from two event counters (one for
 * counting the number of events, the other for counting the volume).
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 * $Id$
 */
public class SizeCounter implements Uninterruptible {

  /****************************************************************************
   *
   * Instance variables
   */
  private EventCounter units;
  private EventCounter volume;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   *
   * @param name The name to be associated with this counter
   */
  public SizeCounter(String name) {
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
  public SizeCounter(String name, boolean start) {
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
  public SizeCounter(String name, boolean start, boolean mergephases) {
    units = new EventCounter(name, start, mergephases);
    volume = new EventCounter(name+"Volume", start, mergephases);
  }

  /****************************************************************************
   *
   * Counter-specific methods
   */

  /** 
   * Increment the event counter by <code>value</code>
   *
   * @param value The amount by which the counter should be incremented.
   */
  public void inc(int value) {
    units.inc();
    volume.inc(value);
  }

  /****************************************************************************
   *
   * Generic counter control methods: start, stop, print etc
   */
  
  /**
   * Start this counter
   */
  public void start() {
    units.start();
    volume.start();
  }

  /**
   * Stop this counter
   */
  public void stop() {
    units.stop();
    volume.stop();
  }
 
  /**
   * Print units
   */
  public void printUnits(){
    units.printTotal();
  }
  
  /**
   * Print volume
   */
  public void printVolume(){
    volume.printTotal();
  }
}
