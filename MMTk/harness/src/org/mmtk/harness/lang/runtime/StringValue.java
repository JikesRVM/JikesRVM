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
 * Expression consisting of a simple string value
 */
public class StringValue extends Value {
  private final String value;

  public StringValue(String value) {
    this.value = value;
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
   * Get this value as a String
   */
  @Override
  public String getStringValue() {
    return value;
  }

  @Override
  public Object marshall(Class<?> klass) {
    if (klass.isAssignableFrom(StringValue.class)) {
      return this;
    }
    return value;
  }

  @Override
  public boolean equals(Object other) {
    return (other instanceof StringValue && value.equals(((StringValue)other).value));
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }
}

