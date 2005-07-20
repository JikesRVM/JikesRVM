/*
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
 * $Id$
 *
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
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
