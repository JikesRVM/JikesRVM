/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

/**
 * Should reference type processing be disabled?
 *
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class NoReferenceTypes extends BooleanOption {
  /**
   * Create the option.
   */
  public NoReferenceTypes() {
    super("No Reference Types", 
          "Should reference type processing be disabled?",
          false);
  }
}
