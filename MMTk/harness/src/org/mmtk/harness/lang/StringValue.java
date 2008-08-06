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
 * Expression consisting of a simple string value
 */
public class StringValue extends Value {
  private String value;

  public StringValue(String value) {
    this.value = value;
  }

  /**
   * Object equality
   */
  @Override
  public boolean equals(Object other) {
    return (other instanceof StringValue && value == ((StringValue)other).value);
  }

  /**
   * String representation
   */
  @Override
  public String toString() {
    return value;
  }

  /**
   * The type of this value
   */
  @Override
  public Type type() {
    return Type.STRING;
  }

  /**
   * Copy the value from the given new value.
   */
  public void copyFrom(Value newValue) {
    this.value = newValue.getStringValue();
  }

  /**
   * Get this value as an integer.
   */
  public String getStringValue() {
    return value;
  }

  /**
   * @see java.lang.Object#clone()
   */
  @Override
  public StringValue clone() {
    return new StringValue(value);
  }

}
