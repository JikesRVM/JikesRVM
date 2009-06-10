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

import org.vmmagic.pragma.*;

/**
 * A composite option that provides a min/max interface to MMTk,
 * and a fixed/bounded option interface to the VM/user.
 */
public final class NurserySize {
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
