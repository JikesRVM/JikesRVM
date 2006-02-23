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
