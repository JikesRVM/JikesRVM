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
package org.jikesrvm.adaptive.measurements.instrumentation;

import org.jikesrvm.adaptive.database.AOSDatabase;
import org.jikesrvm.adaptive.measurements.RuntimeMeasurements;
import org.jikesrvm.adaptive.util.AOSOptions;
import org.jikesrvm.compilers.opt.InstrumentedEventCounterManager;

/**
 * Instrumentation.java
 *
 * This class is used to provide general functionality useful to
 * instrumenting methods.
 *
 *
 */

public final class Instrumentation {

  /**
   * A pointer to a InstrumentedEventCounterManager, (See
   * InstrumentedEventCounterManager.java for the idea behind a
   * counter manager) There can be multiple managers in use at the
   * same time (for example, one per method)., but for now we just use
   * one for everything.
   **/
  public static InstrumentedEventCounterManager eventCounterManager;

  /**
   * Called at boot time
   **/
  public static void boot(AOSOptions options) {

    // If the system may perform any instrumentation that uses managed
    // event counters, initialize a counter manager here.
    if (options
        .INSERT_INSTRUCTION_COUNTERS ||
                                     options
                                         .INSERT_METHOD_COUNTERS_OPT ||
                                                                     options
                                                                         .INSERT_YIELDPOINT_COUNTERS ||
                                                                                                     options
                                                                                                         .INSERT_DEBUGGING_COUNTERS) {
      eventCounterManager = new CounterArrayManager();
    }

    // If inserting method counters, initialize the counter space for
    // the invocation counters, using the eventCounterManager from above.
    if (options.INSERT_METHOD_COUNTERS_OPT) {
      AOSDatabase.methodInvocationCounterData = new MethodInvocationCounterData(eventCounterManager);

      // Method Counters have only one array of counters for the whole
      // program, so initialize it here. Make it automitacally double
      // in size when needed.
      AOSDatabase.methodInvocationCounterData.
          automaticallyGrowCounters(true);

      // Report at end
      RuntimeMeasurements.
          registerReportableObject(AOSDatabase.methodInvocationCounterData);
    }

    /**
     * If collecting yieldpoint counts, initialize the
     * data here.
     **/
    if (options.INSERT_YIELDPOINT_COUNTERS) {
      // Create it here, because we need only one array of numbers,
      // not one per method.
      AOSDatabase.yieldpointCounterData = new YieldpointCounterData(eventCounterManager);

      // We want to report everything at the end.
      RuntimeMeasurements.
          registerReportableObject(AOSDatabase.yieldpointCounterData);

    }

    /**
     * If collecting instruction counts, initialize the
     * data here.
     **/
    if (options.INSERT_INSTRUCTION_COUNTERS) {
      AOSDatabase.instructionCounterData = new StringEventCounterData(eventCounterManager, "Instruction Counter");
      AOSDatabase.instructionCounterData.automaticallyGrowCounters(true);

      // We want to report everything at the end.
      RuntimeMeasurements.
          registerReportableObject(AOSDatabase.instructionCounterData);
    }

    /**
     * If collecting instruction counts, initialize the
     * data here.
     **/
    if (options.INSERT_DEBUGGING_COUNTERS) {
      AOSDatabase.debuggingCounterData = new StringEventCounterData(eventCounterManager, "Debugging Counters");
      AOSDatabase.debuggingCounterData.automaticallyGrowCounters(true);

      // We want to report everything at the end.
      RuntimeMeasurements.
          registerReportableObject(AOSDatabase.debuggingCounterData);
    }

  }

  /**
   * Calling this routine causes all future compilations not to insert
   * instrumentation, regardless of what the options say.  Used during
   * system shutdown.  Note, this method will not stop instrumentation
   * in currently compiled methods from executing.
   */
  static void disableInstrumentation() {
    instrumentationEnabled = false;
  }

  /**
   * Enable instrumentations, so that future compilations will not
   * perform any instrumentation.
   */
  static void enableInstrumentation() {
    instrumentationEnabled = true;
  }

  /**
   * Is it currently O.K. to compile a method and insert instrumentation?
   */
  public static boolean instrumentationEnabled() {
    return instrumentationEnabled;
  }

  private static boolean instrumentationEnabled = true;
}
