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
 * GC verbosity level.
 * 
 * $Id$
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
