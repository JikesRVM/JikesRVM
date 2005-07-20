/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

/**
 * Should finalization be disabled?
 *
 * $Id$
 *
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class NoFinalizer extends BooleanOption {
  /**
   * Create the option.
   */
  public NoFinalizer() {
    super("No Finalizer",
          "Should finalization be disabled?",
          false);
  }
}
