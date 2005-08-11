/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

import org.mmtk.utility.Log;

import org.vmmagic.pragma.UninterruptiblePragma;

/**
 * An option with a simple double precision floating point value.
 *
 * $Id$
 *
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class DoubleOption extends Option {
  // values
  protected double defaultValue;
  protected double value;

  /**
   * Create a new double option.
   *
   * @param name The space separated name for the option.
   * @param desc The purpose of the option
   * @param defaultValue The default value of the option.
   */
  protected DoubleOption(String name, String desc, double defaultValue) {
    super(DOUBLE_OPTION, name, desc);
    this.value = this.defaultValue = defaultValue;
  }

  /**
   * Read the current value of the option.
   *
   * @return The option value.
   */
  public double getValue() throws UninterruptiblePragma {
    return this.value;
  }

  /**
   * Read the default value of the option
   *
   * @return The default value.
   */
  public double getDefaultValue() throws UninterruptiblePragma {
    return this.defaultValue;
  }

  /**
   * Update the value of the option, echoing the change if the echoOptions
   * option is set. This method also calls the validate method to allow
   * subclasses to perform any required validation.
   *
   * @param value The new value for the option.
   */
  public void setValue(double value) {
    double oldValue = this.value;
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
