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

/**
 * Should we print verbose fragmentation statistics for the free list allocator?
 */
public final class VerboseFragmentationStats extends org.vmutil.options.BooleanOption {
  /**
   * Create the option.
   */
  public VerboseFragmentationStats() {
    super(Options.set, "Verbose Fragmentation Stats",
        "Should we print verbose fragmentation statistics for the free list allocator?",
        false);
  }
}
