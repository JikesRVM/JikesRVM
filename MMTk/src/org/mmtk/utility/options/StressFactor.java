/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

import org.mmtk.plan.Plan;
import org.mmtk.utility.Constants;

/**
 * Force frequent collections after amounts of allocation.
 *
 * $Id$
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
