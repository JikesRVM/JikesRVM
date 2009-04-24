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
 * A time option that stores values at a microsecond granularity.
 */
public class MicrosecondsOption extends Option {
  // values
  protected int defaultValue;
  protected int value;

  /**
   * Create a new microsecond option.
   *
   * @param set The option set this option belongs to.
   * @param name The space separated name for the option.
   * @param desc The purpose of the option
   * @param defaultUs The default value of the option (usec).
   */
  protected MicrosecondsOption(OptionSet set, String name, String desc, int defaultUs) {
    super(set, MICROSECONDS_OPTION, name, desc);
    this.value = this.defaultValue = defaultUs;
  }

  /**
   * Read the current value of the option in microseconds.
   *
   * @return The option value.
   */
  @Uninterruptible
  public int getMicroseconds() {
    return this.value;
  }

  /**
   * Read the current value of the option in milliseconds.
   *
   * @return The option value.
   */
  @Uninterruptible
  public int getMilliseconds() {
    return this.value / 1000;
  }

  /**
   * Read the default value of the option in microseconds.
   *
   * @return The default value.
   */
  @Uninterruptible
  public int getDefaultMicroseconds() {
    return this.defaultValue;
  }

  /**
   * Read the default value of the option in milliseconds.
   *
   * @return The default value.
   */
  @Uninterruptible
  public int getDefaultMilliseconds() {
    return this.defaultValue / 1000;
  }

  /**
   * Update the value of the option, echoing the change if the echoOptions
   * option is set. An error occurs if the value is negative, and then the
   * validate method is called to allow subclasses to perform any additional
   * validation.
   *
   * @param value The new value for the option.
   */
  public void setMicroseconds(int value) {
    failIf(value < 0, "Unreasonable " + this.getName() + " value");
    this.value = value;
    validate();
    set.logChange(this);
  }

  /**
   * Modify the default value of the option.
   *
   * @param value The new default value for the option.
   */
  public void setDefaultMicrosends(int value) {
    this.value = this.defaultValue = value;
  }
}
