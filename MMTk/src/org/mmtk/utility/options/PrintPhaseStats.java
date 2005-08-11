/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

/**
 * When printing statistics, should statistics for each
 * gc-mutator phase be printed?
 *
 * $Id$
 *
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
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
