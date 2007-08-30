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
package org.mmtk.utility.options;

/**
 * Performance counter options.
 */
public class PerfMetric extends EnumOption {
  // enumeration values.
  public static final int RI = 0;
  public static final int L1_MISS = 1;
  public static final int L2_MISS = 2;
  public static final int DTLB_MISS = 3;
  public static final int ITLB_MISS = 4;
  public static final int ITLB_HIT = 5;
  public static final int BPU_TC = 6;
  public static final int TC_FLUSH = 7;

  /**
   * Create the option.
   */
  public PerfMetric() {
    super("Perf Metric",
          "Use this to select a performance metric to measure",
          new String[] {"RI", "L1_MISS", "L2_MISS", "DTLB_MISS", "ITLB_MISS", "ITLB_HIT", "BPU_TC", "TC_FLUSH"},
          0);
  }
}
