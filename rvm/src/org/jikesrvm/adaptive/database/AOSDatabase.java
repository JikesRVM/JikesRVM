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
package org.jikesrvm.adaptive.database;

import org.jikesrvm.adaptive.measurements.instrumentation.MethodInvocationCounterData;
import org.jikesrvm.adaptive.measurements.instrumentation.StringEventCounterData;
import org.jikesrvm.adaptive.measurements.instrumentation.YieldpointCounterData;
import org.jikesrvm.adaptive.util.AOSOptions;

/**
 * Used to keep track of the various data structures that make up the
 * AOS database.
 */
public final class AOSDatabase {
  /*
   * Static links to data objects that are "whole-program" (as opposed
   * to per-method)
   */
  public static MethodInvocationCounterData methodInvocationCounterData;
  public static YieldpointCounterData yieldpointCounterData;
  public static StringEventCounterData instructionCounterData;
  public static StringEventCounterData debuggingCounterData;

  /**
   * Called at startup
   **/
  public static void boot(AOSOptions options) {
  }
}
