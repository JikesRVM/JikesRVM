/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.utility.options;

/**
 * A sample enumeration for testing.
 */
public final class DummyEnum extends org.vmutil.options.EnumOption {

  // enumeration values.
  public final int FOO = 0;
  public final int BAR = 1;

  /**
   * Create the option.
   */
  public DummyEnum() {
    super(Options.set, "Dummy Enum",
          "This is a sample enumeration to test the options system",
          new String[] {"foo", "bar"},
          "foo");
  }
}
