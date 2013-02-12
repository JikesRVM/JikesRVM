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
package org.mmtk.harness.options;

import org.mmtk.harness.Harness;

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
        Integer.valueOf(System.getProperty("mmtk.harness.heap.max", "384")));
  }

  @Override
  protected void validate() {
    failIf(value == 0, "Must have a non-zero heap size");
  }
}
