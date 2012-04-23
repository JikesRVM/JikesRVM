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
import org.mmtk.utility.statistics.PerfEvent;
import org.jikesrvm.runtime.Time;
import static org.jikesrvm.runtime.SysCall.sysCall;

import org.vmmagic.pragma.*;

@Uninterruptible
public final class Statistics extends org.mmtk.vm.Statistics implements Constants {
  /**
   * Read nanoTime (high resolution, monotonically increasing clock).
   * Has same semantics as java.lang.System.nanoTime().
   */
  @Override
  public long nanoTime() {
    return Time.nanoTime();
  }

  /**
   * Read a cycle counter (high resolution, non-monotonic clock).
   * This method should be used with care as the cycle counters (especially on IA32 SMP machines)
   * are not a reliably time source.
   */
  @Override
  public long cycles() {
    return Time.cycles();
  }

  /**
   * Convert nanoseconds to milliseconds
   */
  @Override
  public double nanosToMillis(long c) {
    return ((double)c)/1e6;
  }

  /**
   * Convert nanoseconds to seconds
   */
  @Override
  public double nanosToSecs(long c) {
    return ((double)c)/1e9;
  }

  /**
   * Convert milliseconds to nanoseconds
   */
  @Override
  public long millisToNanos(double t) {
    return (long)(t * 1e6);
  }

  /**
   * Convert seconds to nanoseconds
   */
  @Override
  public long secsToNanos(double t) {
    return (long)(t * 1e9);
  }

  private PerfEvent[] perfEvents;

  /**
   * Initialize performance events
   */
  @Override
  @Interruptible
  public void perfEventInit(String events) {
    if (events.length() == 0) {
      // no initialization needed
      return;
    }
    // initialize perf event
    String[] perfEventNames = events.split(",");
    int n = perfEventNames.length;
    sysCall.sysPerfEventInit(n);
    perfEvents = new PerfEvent[n];
    for (int i = 0; i < n; i++) {
      sysCall.sysPerfEventCreate(i, perfEventNames[i].concat("\0").getBytes());
      perfEvents[i] = new PerfEvent(i, perfEventNames[i]);
    }
    sysCall.sysPerfEventEnable();
  }

  /**
   * Read a performance event
   */
  @Override
  public void perfEventRead(int id, long[] values) {
    sysCall.sysPerfEventRead(id, values);
  }
}

