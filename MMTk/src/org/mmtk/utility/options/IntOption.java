/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

import org.mmtk.utility.Log;

import org.vmmagic.pragma.UninterruptiblePragma;

/**
 * An option with a simple integer value.
 *
 * $Id$
 *
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class IntOption extends Option {
  // values
  protected int defaultValue;
  protected int value;

  /**
   * Create a new int option.
   *
   * @param name The space separated name for the option.
   * @param desc The purpose of the option
   * @param defaultValue The default value of the option.
   */
  protected IntOption(String name, String desc, int defaultValue) {
    super(INT_OPTION, name, desc);
    this.value = this.defaultValue = defaultValue;
  }

  /**
   * Read the current value of the option.
   *
   * @return The option value.
   */
  public int getValue() throws UninterruptiblePragma {
    return this.value;
  }

  /**
   * Read the default value of the option.
   *
   * @return The default value.
   */
  public int getDefaultValue() throws UninterruptiblePragma {
    return this.defaultValue;
  }

  /**
   * Update the value of the option, echoing the change if the echoOptions
   * option is set. This method also calls the validate method to allow
   * subclasses to perform any required validation.
   *
   * @param value The new value for the option.
   */
  public void setValue(int value) {
    int oldValue = this.value;
    this.value = value;
    if (Options.echoOptions.getValue()) {
      Log.write("Option '");
      Log.write(this.getKey());
      Log.write("' set ");
      Log.write(oldValue);
      Log.write(" -> ");
      Log.writeln(value);
    }
    validate();
  }
}
