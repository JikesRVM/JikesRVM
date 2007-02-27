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
 * Echo when options are set?
 * 
 *
 * @author Daniel Frampton
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
