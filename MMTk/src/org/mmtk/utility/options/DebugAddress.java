/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.utility.options;

import org.vmmagic.unboxed.Address;

/**
 * Allow an address to be specified on the command line for use in debugging.
 */
public final class DebugAddress extends org.vmutil.options.AddressOption {
  /**
   * Create the option
   */
  public DebugAddress() {
    super(Options.set, "Debug Address",
          "Specify an address at runtime for use in debugging",
        Address.zero());
  }
}
