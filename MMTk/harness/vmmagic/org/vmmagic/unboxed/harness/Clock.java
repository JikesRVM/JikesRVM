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
package org.vmmagic.unboxed.harness;

import java.util.concurrent.atomic.AtomicLong;

import org.mmtk.harness.scheduler.Scheduler;

/**
 * A 'clock' that counts memory accesses
 */
public class Clock {

  /**
   * The state of the clock, which depends on whether we are running
   * mutator or MMTk code, or whether we are running in harness-specific
   * code.  Since some code can be called both from the harness and
   * non-harness code, we track a level of nesting.  We are not in the
   * harness (and hence the clock is ticking) if nesting level is
   * zero.
   */
  private static final class ClockState {
    int nesting = 1;
    boolean inHarness() {
      return nesting > 0;
    }

    void enter() {
      nesting++;
      if (nesting < 1) {
        System.out.println("Nesting = "+nesting);
      }
      assert inHarness();
    }

    void exit() {
      --nesting;
      assert nesting >= 0;
    }
  }

  /**
   * Flag to tell the clock whether we are in the Harness (and should
   * not tick) or in MMTk.  Threads are created in the harness.
   */
  private static final ThreadLocal<ClockState> CLOCK_STATE =
      new ThreadLocal<ClockState>() {
        @Override protected ClockState initialValue() {
          return new ClockState();
        }
  };

  /** The clock */
  private static final AtomicLong clock = new AtomicLong(0);

  /**
   * If the clock is ticking (ie we are running in MMTk or the
   * script and not the harness itself), increment the clock and
   * (potentially) take a yield-point.
   */
  public static void tick() {
    if (isTicking()) {
      clock.incrementAndGet();
      Scheduler.yield();
    }
  }

  /**
   * @return The current value of the clock
   */
  public static long read() {
    return clock.get();
  }

  public static boolean isTicking() {
    return !CLOCK_STATE.get().inHarness();
  }

  /**
   * Stop the clock, generally while doing harness-internal processing.
   *
   * Especially significant in the deterministic scheduler, because the
   * work of the harness varies as we turn trace flags on and off.
   */
  public static void stop() {
    CLOCK_STATE.get().enter();
  }

  /**
   * Start the clock, entering non-harness code (mutator or MMTk).
   */
  public static void start() {
    assertStopped();
    CLOCK_STATE.get().exit();
  }

  /**
   * Throw an assertion error when the clock is not stopped.
   */
  public static void assertStopped() {
    if (!CLOCK_STATE.get().inHarness()) {
      new AssertionError().printStackTrace();
    }
    assert CLOCK_STATE.get().inHarness();
  }

  /**
   * Throw an assertion error when the clock is not running.
   */
  public static void assertStarted() {
    assert !CLOCK_STATE.get().inHarness();
  }
}
