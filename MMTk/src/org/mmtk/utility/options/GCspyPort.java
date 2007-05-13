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
 * Port number for GCSpy server to connect with visualiser.
 */
public class GCspyPort extends IntOption {
  /**
   * Create the option.
   */
  public GCspyPort() {
    super("GCSpy Port",
          "Port number for GCSpy server to connect with visualiser",
          0);
  }

  /**
   * Ensure the port is valid.
   */
  protected void validate() {
    failIf(this.value <= 0, "Unreasonable GCSpy port value");
  }
}
