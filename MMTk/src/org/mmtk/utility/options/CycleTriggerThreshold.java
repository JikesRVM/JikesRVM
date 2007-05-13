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

/**
 * Trigger cycle detection if the space available falls below this threshold.
 * 
 *
 */
public class CycleTriggerThreshold extends PagesOption {
  /**
   * Create the option.
   */
  public CycleTriggerThreshold() {
    super("Cycle Trigger Threshold",
        "Trigger cycle detection if the space available falls below this threshold",
        512);
  }
}
