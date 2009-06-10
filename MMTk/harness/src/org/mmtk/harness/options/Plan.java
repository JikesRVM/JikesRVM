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
 * The plan to use for MMTk.
 */
public final class Plan extends org.vmutil.options.StringOption {
  /**
   * Create the option.
   */
  public Plan() {
    super(Harness.options, "Plan",
          "Plan to use",
          System.getProperty("mmtk.harness.plan","org.mmtk.plan.marksweep.MS"));
  }
}
