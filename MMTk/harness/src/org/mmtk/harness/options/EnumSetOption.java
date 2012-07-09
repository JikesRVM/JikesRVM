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
package org.mmtk.harness.options;

import static org.mmtk.harness.options.HarnessOptionSet.ENUM_SET_OPTION;

import java.util.Set;
import java.util.TreeSet;

import org.vmmagic.pragma.Uninterruptible;
import org.vmutil.options.Option;
import org.vmutil.options.OptionSet;

/**
 * A set-valued option, eg opt=v1,v2,v3
 * <p>
 * The values of the set come from an enumeration
 */
public class EnumSetOption extends Option {

  // values
  protected Set<Integer> defaultValues;
  protected Set<Integer> values;
  protected String[] options;

  /**
   * Create a new enumeration option.
   *
   * @param set The option set this option belongs to.
   * @param name The space separated name for the option.
   * @param description The purpose of the option.
   * @param options
   * @param defaultValues
   */
  protected EnumSetOption(OptionSet set, String name, String description, String[] options, String defaultValues) {
    super(set, ENUM_SET_OPTION, name, description);
    this.options = options;
    this.values = this.defaultValues = findValues(defaultValues);
  }

  /**
   * Search for a string in the enumeration.
   *
   * @return The index of the passed string.
   */
  private int findValue(String string) {
    for (int i = 0; i < options.length; i++) {
      if (options[i].equals(string)) {
        return i;
      }
    }
    fail("Invalid Enumeration Value, \""+string+"\"");
    return -1;
  }

  /**
   * Search for a string in the enumeration.
   *
   * @return The index of the passed string.
   */
  private Set<Integer> findValues(String string) {
    Set<Integer> results = new TreeSet<Integer>();
    for (String str : string.split(",")) {
      if (!str.equals("")) {
        results.add(findValue(str));
      }
    }
    return results;
  }

  /**
   * Read the current value of the option.
   *
   * @return The option value.
   */
  @Uninterruptible
  public Set<Integer> getValue() {
    return this.values;
  }

  /**
   * Read the default value of the option.
   *
   * @return The default value.
   */
  @Uninterruptible
  public Set<Integer> getDefaultValue() {
    return this.defaultValues;
  }

  /**
   * Update the value of the option, echoing the change if the echoOptions
   * option is set. This method also calls the validate method to allow
   * subclasses to perform any required validation.
   *
   * @param values The new value for the option.
   */
  public void setValue(Set<Integer> values) {
    this.values = values;
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
    setValue(findValues(value));
  }

  /**
   * Modify the default value of the option.
   *
   * @param values The new default value for the option.
   */
  public void setDefaultValue(String values) {
    this.values = this.defaultValues = findValues(values);
  }

  /**
   * Return the array of allowed enumeration values.
   *
   * @return The values array.
   */
  public String[] getValues() {
    return this.options;
  }
}
