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
 * Expression consisting of a simple integer value
 */
public class IntValue extends Value {
  private int value;

  public IntValue(int value) {
    this.value = value;
  }

  /**
   * Object equality
   */
  @Override
  public boolean equals(Object other) {
    return (other instanceof IntValue && value == ((IntValue)other).value);
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
   * Copy the value from the given new value.
   */
  public void copyFrom(Value newValue) {
    this.value = newValue.getIntValue();
  }

  /**
   * Get this value as an integer.
   */
  public int getIntValue() {
    return value;
  }

  /**
   * Set this value as an integer.
   */
  public void setIntValue(int value) {
    this.value = value;
  }
}
