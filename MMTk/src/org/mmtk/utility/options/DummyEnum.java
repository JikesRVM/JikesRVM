/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

/**
 * A sample enumeration for testing.
 *
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class DummyEnum extends EnumOption {
  // enumeration values.
  public int FOO = 0;
  public int BAR = 1;

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
