/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

import org.mmtk.utility.Log;

import org.vmmagic.pragma.UninterruptiblePragma;

/**
 * An option that has a simple single precision floating point value.
 *
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class FloatOption extends Option {
  // values
  protected float defaultValue;
  protected float value;

  /**
   * Create a new float option.
   *
   * @param name The space separated name for the option.
   * @param desc The purpose of the option
   * @param defaultValue The default value of the option.
   */ 
  protected FloatOption(String name, String desc, float defaultValue) { 
    super(FLOAT_OPTION, name, desc);
    this.value = this.defaultValue = defaultValue;
  }

  /**
   * Read the current value of the option.
   *
   * @return The option value.
   */
  public float getValue() throws UninterruptiblePragma {
    return this.value;
  }

  /**
   * Read the default value of the option
   *
   * @return The default value.
   */
  public float getDefaultValue() throws UninterruptiblePragma {
    return this.defaultValue;
  }

  /**
   * Update the value of the option, echoing the change if the echoOptions 
   * option is set. This method also calls the validate method to allow 
   * subclasses to perform any required validation.
   *
   * @param value The new value for the option.
   */
  public void setValue(float value) {
    float oldValue = this.value;
    this.value = value;
    if (echoOptions.getValue()) {
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
