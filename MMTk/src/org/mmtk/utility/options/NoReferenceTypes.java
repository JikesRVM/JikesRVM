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
 * Should reference type processing be disabled?
 * 
 *
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
