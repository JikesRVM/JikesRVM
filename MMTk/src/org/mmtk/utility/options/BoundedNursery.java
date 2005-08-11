/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

import org.mmtk.plan.Plan;

/**
 * Provide an upper bound on nursery size. This option is not intended to
 * be created directly, but via NurserySize.
 *
 * $Id$
 *
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class BoundedNursery extends PagesOption {
  /**
   * Create the option.
   */
  public BoundedNursery() {
    super("Bounded Nursery",
          "Bound the maximum size of the nursery to this value",
          Plan.DEFAULT_MAX_NURSERY);
  }

  /**
   * Nursery can not be empty.
   */
  protected void validate() {
    failIf(value <= 0, "Can not have an empty nursery");
  }
}
