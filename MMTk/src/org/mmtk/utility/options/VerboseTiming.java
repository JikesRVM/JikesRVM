/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

/**
 * Should we display detailed breakdown of where GC time is spent?
 *
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class VerboseTiming extends BooleanOption {
  /**
   * Create the option.
   */
  public VerboseTiming() {
    super("Verbose Timing", 
          "Should we display detailed breakdown of where GC time is spent?",
          false);
  }
}
