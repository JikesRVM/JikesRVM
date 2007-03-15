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

import org.vmmagic.unboxed.Address;

/**
 * Allow an address to be specified on the command line for use in debugging.
 * 
 *
 * @author Steve Blackburn
 */
public class DebugAddress extends AddressOption {
  /**
   * Create the option
   */
  public DebugAddress() {
    super("Debug Address",
          "Specify an address at runtime for use in debugging",
        Address.zero());
  }
}
