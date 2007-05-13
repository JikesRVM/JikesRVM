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
 * When printing statistics, should statistics for each
 * gc-mutator phase be printed?
 * 
 *
 */
public class PrintPhaseStats extends BooleanOption {
  /**
   * Create the option.
   */
  public PrintPhaseStats() {
    super("Print Phase Stats",
        "When printing statistics, should statistics for each gc-mutator phase be printed?",
        false);
  }
}
