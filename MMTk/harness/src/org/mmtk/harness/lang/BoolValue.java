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
package org.mmtk.harness.lang;

/**
 * Expression consisting of a simple boolean value
 */
public class BoolValue extends Value {

  /** The value */
  private boolean value;

  /**
   * Constructor
   * @param value
   */
  public BoolValue(boolean value) {
    this.value = value;
  }

  /**
   * Object equality.
   */
  @Override
  public boolean equals(Object other) {
    return (other instanceof BoolValue && value == ((BoolValue)other).value);
  }

  /**
   * Copy the value from the given new value.
   */
  public void copyFrom(Value newValue) {
    this.value = newValue.getBoolValue();
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

  /**
   * @see java.lang.Object#clone()
   */
  @Override
  public BoolValue clone() {
    return new BoolValue(value);
  }
}
