/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

/**
 * Should we use a generational approach to cycle detection?
 *
 * $Id$
 *
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
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
