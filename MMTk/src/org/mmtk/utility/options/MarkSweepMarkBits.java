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

import org.mmtk.policy.MarkSweepSpace;

/**
 * Number of bits to use for the header cycle of mark sweep spaces.
 * 
 * $Id$
 * 
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class MarkSweepMarkBits extends IntOption {
  /**
   * Create the option.
   */
  public MarkSweepMarkBits() {
    super("Mark Sweep Mark Bits",
          "Number of bits to use for the header cycle of mark sweep spaces",
          MarkSweepSpace.DEFAULT_MARKCOUNT_BITS);
  }

  /**
   * Ensure the value is valid.
   */
  protected void validate() {
    failIf(this.value <= 0, "Must provide at least one bit");
    failIf(this.value > MarkSweepSpace.MAX_MARKCOUNT_BITS , "Only "+MarkSweepSpace.MAX_MARKCOUNT_BITS+" bits are reserved in MarkSweepSpace");
  }
}
