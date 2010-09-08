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
package org.mmtk.utility.options;

/**
 * The number of GC threads to use for parallel collection.
 *
 * This is slightly unclean as the default value is not known at build time.
 */
public final class Threads extends org.vmutil.options.IntOption {

  /** Has a value been set? */
  private boolean valueSet;

  /**
   * Create the option.
   */
  public Threads() {
    super(Options.set, "Threads",
          "Number of GC threads to use",
          1);
    valueSet = false;
  }

  /**
   * Update the default value, only overriding value if no explicit value was set.
   *
   * @param defaultValue The actual default value.
   */
  public void updateDefaultValue(int defaultValue) {
    this.defaultValue = defaultValue;
    if (!valueSet) {
      this.value = defaultValue;
    }
  }

  /**
   * Return the number of threads to use, or delegate to the runtime if this has not been set.
   */
  public void setValue(int value) {
    super.setValue(value);
    valueSet = true;
  }

  /**
   * Only accept values of 1 or higher.
   */
  protected void validate() {
    failIf(this.value < 1, "Must have at least one gc thread");
  }
}
