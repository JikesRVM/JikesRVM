/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

/**
 * GC verbosity level.
 *
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class Verbose extends IntOption {
  /**
   * Create the option.
   */
  public Verbose() {
    super("Verbose",
          "GC verbosity level",
          0);
  }

  /**
   * Only accept non-negative values.
   */
  protected void validate() {
    failIf(this.value < 0, "Unreasonable verbosity level");
  }
}
