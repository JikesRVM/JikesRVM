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
package org.mmtk.harness.lang.runtime;

import org.mmtk.harness.lang.type.Type;

/**
 * Expression consisting of a simple boolean value
 */
public final class BoolValue extends Value {

  public static final BoolValue FALSE = new BoolValue(false);
  public static final BoolValue TRUE = new BoolValue(true);

  public static BoolValue valueOf(boolean value) {
    return value ? TRUE : FALSE;
  }

  /** The value */
  private final boolean value;

  /**
   * Constructor
   * @param value
   */
  private BoolValue(boolean value) {
    this.value = value;
  }

  /**
   * Get this value as a boolean.
   */
  @Override
  public boolean getBoolValue() {
    return value;
  }

  /**
   * String representation
   */
  @Override
  public String toString() {
    return Boolean.toString(value);
  }

  /**
   * The type of this value
   */
  @Override
  public Type type() {
    return Type.BOOLEAN;
  }

  @Override
  public Object marshall(Class<?> klass) {
    if (klass.isAssignableFrom(BoolValue.class)) {
      return this;
    }
    return Boolean.valueOf(value);
  }

  /**
   * Object equality.
   */
  @Override
  public boolean equals(Object other) {
    return (other instanceof BoolValue && value == ((BoolValue)other).value);
  }

  @Override
  public int hashCode() {
    return value ? 0 : 1;
  }

}

