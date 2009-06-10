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
package org.jikesrvm.mm.mmtk;

import org.mmtk.utility.Constants;
import org.jikesrvm.runtime.Time;
import static org.jikesrvm.runtime.SysCall.sysCall;

import org.jikesrvm.mm.mminterface.MemoryManager;

import org.vmmagic.pragma.*;

@Uninterruptible public final class Statistics extends org.mmtk.vm.Statistics implements Constants {
  /**
   * Returns the number of collections that have occurred.
   *
   * @return The number of collections that have occurred.
   */
  @Uninterruptible
  public int getCollectionCount() {
    return MemoryManager.getCollectionCount();
  }

  /**
   * Read nanoTime (high resolution, monotonically increasing clock).
   * Has same semantics as java.lang.System.nanoTime().
   */
  public long nanoTime() {
    return Time.nanoTime();
  }

  /**
   * Read a cycle counter (high resolution, non-monotonic clock).
   * This method should be used with care as the cycle counters (especially on IA32 SMP machines)
   * are not a reliably time source.
   */
  public long cycles() {
    return Time.cycles();
  }

  /**
   * Convert nanoseconds to milliseconds
   */
  public double nanosToMillis(long c) {
    return ((double)c)/1e6;
  }

  /**
   * Convert nanoseconds to seconds
   */
  public double nanosToSecs(long c) {
    return ((double)c)/1e9;
  }

  /**
   * Convert milliseconds to nanoseconds
   */
  public long millisToNanos(double t) {
    return (long)(t * 1e6);
  }

  /**
   * Convert seconds to nanoseconds
   */
  public long secsToNanos(double t) {
    return (long)(t * 1e9);
  }

  /**
   * Initialize performance counters
   *
   * @param metric An integer identifying the metric being read
   */
  public void perfCtrInit(int metric) {
    sysCall.sysPerfCtrInit(metric);
}

  /**
   * Read the current cycle count from the perfctr libraries
   *
   * @return the current cycle count from the perfctr libraries
   */
  public long perfCtrReadCycles() {
    return sysCall.sysPerfCtrReadCycles();
  }

  /**
   * Read the current event count for the metric being measured by the
   * perfctr libraries
   *
   * @return the current event count for the metric being measured by the
   * perfctr libraries
   */
  public long perfCtrReadMetric() {
    return sysCall.sysPerfCtrReadMetric();
}
}

