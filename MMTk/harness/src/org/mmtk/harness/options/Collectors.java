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
 * Number of collector threads.
 */
public final class Collectors extends org.vmutil.options.IntOption {
  /**
   * Create the option.
   */
  public Collectors() {
    super(Harness.options, "Collectors",
          "Collector threads",
          Integer.valueOf(System.getProperty("mmtk.harness.collectors", "1")));
  }

  /**
   * Only accept non-negative values.
   *
   * Except for unit tests, which can have 0.
   */
  protected void validate() {
    failIf(this.value < 0, "Need at least one collector thread");
  }
}
