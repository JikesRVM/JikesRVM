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

import static org.mmtk.harness.options.HarnessOptionSet.INT_SET_OPTION;

import org.vmmagic.pragma.Uninterruptible;
import org.vmutil.options.Option;
import org.vmutil.options.OptionSet;

/**
 * A set-valued option, eg opt=v1,v2,v3
 * <p>
 * The values of the set are integers
 */
public class IntSetOption extends Option {

  // values
  protected int[] defaultValues;
  protected int[] values;

  /**
   * Create a new enumeration option.
   *
   * @param set The option set this option belongs to.
   * @param name The space separated name for the option.
   * @param description The purpose of the option.
   * @param defaultValues
   */
  protected IntSetOption(OptionSet set, String name, String description, int[] defaultValues) {
    super(set, INT_SET_OPTION, name, description);
    this.values = this.defaultValues = defaultValues;
  }
  /**
   * Read the current value of the option.
   *
   * @return The option value.
   */
  @Uninterruptible
  public int[] getValue() {
    return this.values;
  }

  /**
   * Read the default value of the option.
   *
   * @return The default value.
   */
  @Uninterruptible
  public int[] getDefaultValue() {
    return this.defaultValues;
  }

  /**
   * Update the value of the option, echoing the change if the echoOptions
   * option is set. This method also calls the validate method to allow
   * subclasses to perform any required validation.
   *
   * @param values The new value for the option.
   */
  public void setValue(int[] values) {
    this.values = values;
    validate();
    set.logChange(this);
  }
}
