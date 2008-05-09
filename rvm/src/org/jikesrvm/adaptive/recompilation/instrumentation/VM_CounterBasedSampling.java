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
package org.jikesrvm.adaptive.recompilation.instrumentation;

import org.jikesrvm.VM_Constants;
import org.jikesrvm.adaptive.util.VM_AOSOptions;
import org.vmmagic.pragma.Uninterruptible;

/**
 *  VM_CounterBasedSampling.java
 *
 *  Contains necessary infrastructure to perform counter-based
 *  sampling used with the instrumentation sampling code (PLDI'01)
 *  (see InstrumentationSamplingFramework)
 *
 * */
@Uninterruptible
public final class VM_CounterBasedSampling implements VM_Constants {
  static final boolean DEBUG = false;

  /**
   * Holds the value that is used to reset the global counter after
   * a sample is taken.
   */
  static int resetValue = 100;

  /**
   *  The global counter.
   */
  static int globalCounter = resetValue;

  /**
   * Perform at system boot.
   */
  public static void boot(VM_AOSOptions options) {
    // Initialize the counter values
    resetValue = options.COUNTER_BASED_SAMPLE_INTERVAL - 1;
    globalCounter = resetValue;

  }
}
