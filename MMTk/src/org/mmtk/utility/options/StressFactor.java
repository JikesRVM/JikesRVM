/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

import org.mmtk.utility.Conversions;

import org.mmtk.vm.Plan;

/**
 * Force frequent collections after amounts of allocation.
 *
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class StressFactor extends PagesOption {
  /**
   * Create the option, defaulting to the maximum possible value.
   */
  public StressFactor() {
    super("Stress Factor",
          "Force a collection after this much allocation",
          Conversions.bytesToPagesUp(Integer.MAX_VALUE) - 1);
  }

  /**
   * Ensure that the value is sensible.
   */
  public void validate() {
    failIf(this.value < Plan.DEFAULT_POLL_FREQUENCY, 
           "Stress Factor must be at least equal to plan's poll frequency");
  }
}
