/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

/**
 * Should we shrink/grow the heap to adjust to application working set?
 *
 * $Id$
 *
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
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
