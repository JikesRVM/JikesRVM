/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

/**
 * Echo when options are set?
 *
 * $Id$
 *
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class EchoOptions extends BooleanOption {
  /**
   * Create the option.
   */
  public EchoOptions() {
    super("Echo Options",
          "Echo when options are set?",
          false);
  }
}
