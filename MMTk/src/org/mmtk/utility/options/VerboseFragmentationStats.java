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
 * Should we print verbose fragmentation statistics for the free list allocator?
 */
public class VerboseFragmentationStats extends BooleanOption {
  /**
   * Create the option.
   */
  public VerboseFragmentationStats() {
    super("Verbose Fragmentation Stats",
        "Should we print verbose fragmentation statistics for the free list allocator?",
        false);
  }
}
