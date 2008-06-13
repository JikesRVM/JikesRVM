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
package org.mmtk.harness.options;

import org.mmtk.harness.Harness;
import org.vmmagic.unboxed.SimulatedMemory;

/**
 * The maximum heap size.
 */
public final class MaxHeap extends org.vmutil.options.PagesOption {
  /**
   * Create the option.
   */
  public MaxHeap() {
    super(Harness.options, "Max Heap",
        "Max Heap Size",
        (1 << 20) >>> SimulatedMemory.LOG_BYTES_IN_PAGE);
  }

  protected void validate() {
    failIf(value == 0, "Must have a non-zero heap size");
  }
}
