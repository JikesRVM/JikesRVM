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
package org.vmutil.options;

import org.vmmagic.pragma.Uninterruptible;

/**
 * An option that is a selection of several strings. The mapping
 * between strings and integers is determined using indexes into
 * a string array.
 * <p>
 * Enumerations are case sensitive.
 */
public class EnumOption extends Option {
  // values
  protected int defaultValue;
  protected int value;
  protected String[] values;

  /**
   * Create a new enumeration option.
   *
   * @param set The option set this option belongs to.
   * @param name The space separated name for the option.
   * @param description The purpose of the option.
   * @param values A mapping of int to string for the enum.
   * @param defaultValue The default value of the option.
   */
  protected EnumOption(OptionSet set, String name, String description, String[] values, String defaultValue) {
    super(set, ENUM_OPTION, name, description);
    this.values = values;
    this.value = this.defaultValue = findValue(defaultValue);
  }

  /**
   * Search for a string in the enumeration.
   *
   * @return The index of the passed string.
   */
  private int findValue(String string) {
    for (int i = 0; i < values.length; i++) {
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
  @Uninterruptible
  public int getValue() {
    return this.value;
  }

  /**
   * Read the string for the current value of the option.
   *
   * @return The option value.
   */
  @Uninterruptible
  public String getValueString() {
    return this.values[this.value];
  }

  /**
   * Read the default value of the option.
   *
   * @return The default value.
   */
  @Uninterruptible
  public int getDefaultValue() {
    return this.defaultValue;
  }

  /**
   * Read the string for the default value of the option.
   *
   * @return The default value.
   */
  @Uninterruptible
  public String getDefaultValueString() {
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
    this.value = value;
    validate();
    set.logChange(this);
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
   * Modify the default value of the option.
   *
   * @param value The new default value for the option.
   */
  public void setDefaultValue(String value) {
    this.value = this.defaultValue = findValue(value);
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
