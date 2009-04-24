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
import org.mmtk.harness.scheduler.Scheduler.SchedPolicy;

/**
 * Number of collector threads.
 */
public final class SchedulerPolicy extends org.vmutil.options.EnumOption {
  /**
   * Create the option.
   */
  public SchedulerPolicy() {
    super(Harness.options, "Scheduler Policy",
          "MMTk Harness scheduler policy",
          SchedPolicy.valueNames(),
          System.getProperty("mmtk.harness.yieldpolicy", "RANDOM"));
  }

  /**
   * Only accept non-negative values.
   */
  protected void validate() {
  }

  public SchedPolicy policy() {
    return SchedPolicy.valueOf(SchedPolicy.class,values[getValue()]);
  }
}
