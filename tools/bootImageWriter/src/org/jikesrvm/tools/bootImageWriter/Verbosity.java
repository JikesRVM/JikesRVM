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
package org.jikesrvm.tools.bootImageWriter;

/**
 * Verbosity for working. Each level includes the previous ones.
 */
public enum Verbosity {
  /** only print highest-level summary information such as space usage */
  NONE(0),
  /**
   * high-level summary information such as creation of field info and jtoc.
   * Also includes information about classes that are in the boot image but
   * not in the host VM.
   */
  SUMMARY(1),
  /** detailed information at the field level */
  DETAILED(2),
  /** addresses for reference fields */
  ADDRESSES(3),
  /** print type names */
  TYPE_NAMES(4);

  private static final Verbosity[] values = values();

  private final int level;

  Verbosity(int level) {
    if (level != ordinal()) {
      throw new Error("invalid ordinal(): verbosity level and ordinal() must be equal!");
    }
    this.level = level;
  }

  public boolean isAtLeast(Verbosity other) {
    return this.level >= other.level;
  }

  public Verbosity increaseBy(int amount) {
    return values[this.level + amount];
  }

}
