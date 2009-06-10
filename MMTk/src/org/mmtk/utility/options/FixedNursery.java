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

/**
 * Provide an lower and upper bound on nursery size.
 *
 * This option is not intended to be created directly, but via NurserySize.
 */
public final class FixedNursery extends org.vmutil.options.PagesOption {
  // values
  BoundedNursery boundedNursery;

  /**
   * Create the option
   */
  public FixedNursery(BoundedNursery boundedNursery) {
    super(Options.set, "Fixed Nursery",
        "Fix the minimum and maximum size of the nursery to this value",
        Plan.DEFAULT_MIN_NURSERY);
    this.boundedNursery = boundedNursery;
  }

  /**
   * Nursery can not be empty.
   */
  protected void validate() {
    failIf(value <= 0, "Can not have an empty nursery");
    // Update upper bound.
    boundedNursery.setBytes(this.getBytes());
  }
}
