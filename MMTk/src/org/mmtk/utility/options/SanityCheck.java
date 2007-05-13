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
 * Should a major GC be performed when a system GC is triggered?
 * 
 *
 */
public class SanityCheck extends BooleanOption {
  /**
   * Create the option.
   */
  public SanityCheck() {
    super("Sanity Check",
          "Perform sanity checks before and after each collection?",
          false);
  }
}
