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

import org.vmmagic.pragma.*;

/**
 * A composite option that provides a min/max interface to MMTk,
 * and a fixed/bounded option interface to the VM/user.
 * 
 *
 */
public class NurserySize {
  // values
  private FixedNursery fixedNursery;
  private BoundedNursery boundedNursery;

  /**
   * Create the options.
   */
  public NurserySize() {
    boundedNursery = new BoundedNursery();
    fixedNursery = new FixedNursery(boundedNursery);
  }

  /**
   * Read the upper bound of the nursery size.
   * 
   * @return maximum number of pages in the nursery.
   */
  @Uninterruptible
  public int getMaxNursery() { 
    return boundedNursery.getPages();
  }

  /**
   * Read the lower bound of the nursery size.
   * 
   * @return minimum number of pages in the nursery.
   */
  @Uninterruptible
  public int getMinNursery() { 
    return fixedNursery.getPages();
  }
}
