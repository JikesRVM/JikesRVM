/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.harness.lang.runtime;

import org.mmtk.harness.lang.type.Type;

/**
 * Expression consisting of a simple integer value
 */
public class IntValue extends Value {

  public static final IntValue ZERO = new IntValue(0);
  public static final IntValue ONE = new IntValue(1);

  public static IntValue valueOf(int value) {
    switch (value) {
      case 0 : return ZERO;
      case 1: return ONE;
      default:
        return new IntValue(value);
    }
  }

  private final int value;

  public IntValue(int value) {
    this.value = value;
  }

  /**
   * String representation
   */
  @Override
  public String toString() {
    return Integer.toString(value);
  }

  /**
   * The type of this value
   */
  @Override
  public Type type() {
    return Type.INT;
  }

  /**
   * Get this value as an integer.
   */
  public int getIntValue() {
    return value;
  }

  @Override
  public Object marshall(Class<?> klass) {
    if (klass.isAssignableFrom(IntValue.class)) {
      return this;
    }
    return Integer.valueOf(value);
  }

  @Override
  public boolean equals(Object other) {
    return (other instanceof IntValue && value == ((IntValue)other).value);
  }

  @Override
  public int hashCode() {
    return value;
  }
}
