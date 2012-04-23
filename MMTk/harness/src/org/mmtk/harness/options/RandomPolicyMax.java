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
public final class RandomPolicyMax extends org.vmutil.options.IntOption {
  /**
   * Create the option.
   */
  public RandomPolicyMax() {
    super(Harness.options, "Random Policy Max",
        "Maximum yield interval for the random scheduler policy",
        Integer.valueOf(System.getProperty("mmtk.harness.yieldpolicy.random.max", "20")));
  }

  @Override
  protected void validate() {
  }
}
