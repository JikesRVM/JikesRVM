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
 * Base class for boolean options.
 */
public class BooleanOption extends Option {
  // values
  protected boolean defaultValue;
  protected boolean value;

  /**
   * Create a new boolean option.
   *
   * @param set The option set this option belongs to.
   * @param name The space separated name for the option.
   * @param desc The purpose of the option
   * @param defaultValue The default value of the option.
   */
  protected BooleanOption(OptionSet set, String name, String desc, boolean defaultValue) {
    super(set, BOOLEAN_OPTION, name, desc);
    this.value = this.defaultValue = defaultValue;
  }

  /**
   * Read the current value of the option.
   *
   * @return The option value.
   */
  @Uninterruptible
  public boolean getValue() {
    return this.value;
  }

  /**
   * Read the default value of the option.
   *
   * @return The default value.
   */
  @Uninterruptible
  public boolean getDefaultValue() {
    return this.defaultValue;
  }

  /**
   * Update the value of the option, echoing the change if the echoOptions
   * option is set. This method also calls the validate method to allow
   * subclasses to perform any required validation.
   *
   * @param value The new value for the option.
   */
  public void setValue(boolean value) {
    this.value = value;
    validate();
    set.logChange(this);
  }

  /**
   * Modify the default value of the option.
   *
   * @param value The new default value for the option.
   */
  public void setDefaultValue(boolean value) {
    this.value = this.defaultValue = value;
  }
}
