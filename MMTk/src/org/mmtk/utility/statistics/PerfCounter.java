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

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;

/**
 * This class implements a simple performance counter, used to gather
 * data from hardware performance counters.
 */
@Uninterruptible
public class PerfCounter extends LongCounter {

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   *
   * @param name The name to be associated with this counter
   */
  public PerfCounter(String name) {
    this(name, true, false);
  }

  /**
   * Constructor
   *
   * @param name The name to be associated with this counter
   * @param start True if this counter is to be implicitly started at
   * boot time (otherwise the counter must be explicitly started).
   */
  public PerfCounter(String name, boolean start) {
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
  public PerfCounter(String name, boolean start, boolean gconly) {
    super(name, start, gconly);
  }

  /****************************************************************************
   *
   * Counter-specific methods
   */

  /**
   * Get the current value for this counter
   *
   * @return The current value for this counter
   */
  @Inline
  protected final long getCurrentValue() {
    return VM.statistics.perfCtrReadMetric();
  }
}
