/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
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
