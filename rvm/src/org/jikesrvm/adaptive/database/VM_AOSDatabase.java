/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.adaptive.database;

import org.jikesrvm.adaptive.measurements.instrumentation.VM_MethodInvocationCounterData;
import org.jikesrvm.adaptive.measurements.instrumentation.VM_StringEventCounterData;
import org.jikesrvm.adaptive.measurements.instrumentation.VM_YieldpointCounterData;
import org.jikesrvm.adaptive.util.VM_AOSOptions;

/**
 * VM_AOSDatabase.java
 *
 * Used to keep track of the various data structures that make up the
 * AOS database.
 */
public final class VM_AOSDatabase {
  /**
   * Static links to data objects that are "whole-program" (as opposed
   * to per-method)
   */
  public static VM_MethodInvocationCounterData methodInvocationCounterData;
  public static VM_YieldpointCounterData yieldpointCounterData;
  public static VM_StringEventCounterData instructionCounterData;
  public static VM_StringEventCounterData debuggingCounterData;

  /**
   * Called at startup
   **/
  public static void boot(VM_AOSOptions options) {
  }
}
