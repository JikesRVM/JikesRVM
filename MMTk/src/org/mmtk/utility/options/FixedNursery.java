/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

import org.mmtk.plan.Plan;

/**
 * Provide an lower and upper bound on nursery size.
 * 
 * This option is not intended to be created directly, but via NurserySize.
 * 
 *
 */
public class FixedNursery extends PagesOption {
  // values
  BoundedNursery boundedNursery;

  /**
   * Create the option
   */
  public FixedNursery(BoundedNursery boundedNursery) {
    super("Fixed Nursery",
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
