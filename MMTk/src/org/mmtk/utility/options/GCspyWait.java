/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

/**
 * Should the VM wait for the visualiser to connect?
 *
 * $Id$
 *
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class GCspyWait extends BooleanOption {
  /**
   * Create the option
   */
  public GCspyWait() {
    super("GCSpy Wait",
          "Should the VM wait for the visualiser to connect?",
          false);
  }
}
