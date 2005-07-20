/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

/**
 * Should we print verbose fragmentation statistics for the free list allocator?
 *
 * $Id$
 *
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
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
