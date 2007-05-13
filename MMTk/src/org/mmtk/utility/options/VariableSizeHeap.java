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
 * Should we shrink/grow the heap to adjust to application working set?
 * 
 *
 */
public class VariableSizeHeap extends BooleanOption {
  /**
   * Create the option.
   */
  public VariableSizeHeap() {
    super("Variable Size Heap",
        "Should we shrink/grow the heap to adjust to application working set?",
        true);
  }
}
