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
package org.mmtk.utility.options;

/**
 * Performance counter options.
 */
public class PerfMetric extends org.vmutil.options.EnumOption {
  /**
   * Create the option.
   */
  public PerfMetric() {
    super(Options.set, "Perf Metric",
          "Use this to select a performance metric to measure",
          new String[] {"RI", "L1D_MISS", "L2_MISS", "DTLB_MISS", "ITLB_MISS", "ITLB_HIT", "BPU_TC", "TC_FLUSH", "L1I_MISS", "BRANCHES", "BRANCH_MISS"},
          "RI");
  }
}
