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

/**
 * The plan to use for MMTk.
 */
public final class Plan extends org.vmutil.options.StringOption {
  /**
   * Create the option.
   */
  public Plan() {
    super(Harness.options, "Plan",
          "Plan to use",
          defaultPlan());
  }

  private static String defaultPlan() {
    String result = System.getProperties().getProperty("mmtk.harness.plan");
    if (result != null)
      return result;
    return "org.mmtk.plan.marksweep.MS";
  }
}
