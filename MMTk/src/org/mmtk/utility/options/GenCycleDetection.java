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
 * Should we use a generational approach to cycle detection?
 * 
 *
 */
public class GenCycleDetection extends BooleanOption {
  /**
   * Create the option.
   */
  public GenCycleDetection() {
    super("Gen Cycle Detection",
          "Should we use a generational approach to cycle detection?",
          false);
  }
}
