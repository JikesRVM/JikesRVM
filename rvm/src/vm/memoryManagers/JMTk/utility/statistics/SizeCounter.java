/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */

package com.ibm.JikesRVM.memoryManagers.JMTk.utility.statistics;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.VM_Uninterruptible;

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
public class SizeCounter implements VM_Uninterruptible {

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
   * @param start True if this counter is to be implicitly started at
   * boot time (otherwise the counter must be explicitly started).
   */
  public SizeCounter(String name, boolean start) {
    this(name, start, false);
  }

  /**
   * Constructor
   *
   * @param name The name to be associated with this counter
   * @param start True if this counter is to be implicitly started at
   * boot time (otherwise the counter must be explicitly started).
   * @param gconly True if this counter only pertains to (and
   * therefore functions during) GC phases.
   */
  public SizeCounter(String name, boolean start, boolean gconly) {
    units = new EventCounter(name);
    volume = new EventCounter(name+"Volume");
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
}
