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

import org.vmmagic.pragma.Uninterruptible;

/**
 * The zeroing approach to use for new object allocations.
 * Affects each plan differently.
 */
@Uninterruptible
public final class NurseryZeroing extends org.vmutil.options.EnumOption {

  public final int TEMPORAL = 0;
  public final int NON_TEMPORAL = 1;
  public final int CONCURRENT = 2;
  public final int ADAPTIVE = 3;

  /**
   * Create the option.
   */
  public NurseryZeroing() {
    super(Options.set, "Nursery Zeroing",
          "The default approach used for zero initializing nursery objects",
          new String[] {"temporal", "nontemporal", "concurrent", "adaptive"},
          "temporal");
  }

  /**
   * @return {@code true} if a non temporal zeroing approach is to be used.
   */
  public boolean getNonTemporal() {
    return getValue() != TEMPORAL;
  }

  /**
   * @return {@code true} if a concurrent zeroing approach is to be used.
   */
  public boolean getConcurrent() {
    return getValue() == CONCURRENT || getValue() == ADAPTIVE;
  }

  /**
   * @return {@code true} if a concurrent zeroing approach is to be used.
   */
  public boolean getAdaptive() {
    return getValue() == ADAPTIVE;
  }
}
