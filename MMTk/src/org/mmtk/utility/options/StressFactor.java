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

import org.mmtk.plan.Plan;
import org.mmtk.utility.Constants;

/**
 * Force frequent collections after amounts of allocation.
 */
public final class StressFactor extends org.vmutil.options.PagesOption {
  /**
   * Create the option, defaulting to the maximum possible value.
   */
  public StressFactor() {
    super(Options.set, "Stress Factor",
          "Force a collection after this much allocation",
        Integer.MAX_VALUE >>> Constants.LOG_BYTES_IN_PAGE);
  }

  /**
   * Ensure that the value is sensible.
   */
  public void validate() {
    failIf(this.value < Plan.DEFAULT_POLL_FREQUENCY,
        "Stress Factor must be at least equal to plan's poll frequency");
  }
}
