/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

import org.mmtk.utility.Log;

import org.vmmagic.pragma.UninterruptiblePragma;

/**
 * An option that is a selection of several strings. The mapping
 * between strings and integers is determined using indexes into
 * a string array.
 *
 * Enumerations are case sensitive.
 *
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class EnumOption extends Option {
  // values
  protected int defaultValue;
  protected int value;
  protected String[] values;

  /**
   * Create a new enumeration option.
   *
   * @param name The space separated name for the option.
   * @param description The purpose of the option.
   * @param values A mapping of int to string for the enum.
   * @param defaultValue The default value of the option.
   */ 
  protected EnumOption(String name, String description, 
                       String[] values, int defaultValue) { 
    super(ENUM_OPTION, name, description);
    this.values = values;
    this.value = this.defaultValue = defaultValue;
  }
  
  /**
   * Search for a string in the enumeration.
   *
   * @return The index of the passed string.
   */ 
  private int findValue(String string) { 
    for(int i=0;i<values.length;i++) {
      if (values[i].equals(string)) {
        return i;
      }
    }
    fail("Invalid Enumeration Value");
    return -1;
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
   * Read the string for the current value of the option.
   *
   * @return The option value.
   */
  public String getValueString() throws UninterruptiblePragma {
    return this.values[this.value];
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
   * Read the string for the default value of the option.
   *
   * @return The default value.
   */
  public String getDefaultValueString() throws UninterruptiblePragma {
    return this.values[this.defaultValue];
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
    if (echoOptions.getValue()) {
      Log.write("Option '");
      Log.write(this.getKey());
      Log.write("' set ");
      Log.write(this.values[oldValue]);
      Log.write(" -> ");
      Log.writeln(this.values[value]);
    }
    validate();
  }

  /**
   * Look up the value for a string and update the value of the option
   * accordingly, echoing the change if the echoOptions option is set.
   * This method also calls the validate method to allow subclasses to 
   * perform any required validation.
   *
   * @param value The new value for the option.
   */
  public void setValue(String value) {
    setValue(findValue(value));
  }

  /**
   * Return the array of allowed enumeration values.
   *
   * @return The values array.
   */
  public String[] getValues() {
    return this.values;
  }
}
