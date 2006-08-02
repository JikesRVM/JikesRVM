/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

/**
 * Should we eagerly finish sweeping at the start of a collection
 * 
 * $Id$
 * 
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class EagerCompleteSweep extends BooleanOption {
  /**
   * Create the option.
   */
  public EagerCompleteSweep() {
    super("Eager Complete Sweep",
          "Should we eagerly finish sweeping at the start of a collection",
          false);
  }
}
