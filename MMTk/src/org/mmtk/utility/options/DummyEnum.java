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
 * A sample enumeration for testing.
 */
public final class DummyEnum extends EnumOption {

  // enumeration values.
  public final int FOO = 0;
  public final int BAR = 1;

  /**
   * Create the option.
   */
  public DummyEnum() {
    super("Dummy Enum",
        "This is a sample enumeration to test the options system",
          new String[] {"foo", "bar"},
          0);
  }
}
