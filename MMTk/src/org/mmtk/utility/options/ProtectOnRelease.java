/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

/**
 * Should memory be protected on release?
 *
 * $Id$
 *
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class ProtectOnRelease extends BooleanOption {
  /**
   * Create the option.
   */
  public ProtectOnRelease() {
    super("Protect On Release",
          "Should memory be protected on release?",
          false);
  }
}
