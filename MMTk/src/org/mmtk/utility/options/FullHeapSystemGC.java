/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

/**
 * Should a major GC be performed when a system GC is triggered?
 *
 * $Id$
 *
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class FullHeapSystemGC extends BooleanOption {
  /**
   * Create the option.
   */
  public FullHeapSystemGC() {
    super("Full Heap System GC",
          "Should a major GC be performed when a system GC is triggered?",
          false);
  }
}
