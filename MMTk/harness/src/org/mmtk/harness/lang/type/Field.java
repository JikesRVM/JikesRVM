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
package org.mmtk.harness.lang.type;

/**
 * A field of a user defined object
 */
public class Field {
  /** Name of the field */
  private final String name;
  /** Type of the field */
  private final Type type;
  /** Offset of the field within the object's reference/int fields */
  private final int offset;

  /**
   * Create a new field
   * @param name
   * @param type
   */
  Field(String name, Type type, int offset) {
    this.name = name;
    this.type = type;
    this.offset = offset;
  }

  /**
   * @return The name of this field
   */
  public String getName() {
    return name;
  }

  /**
   * @return The type of the field
   */
  public Type getType() {
    return type;
  }

  /**
   * @return The word-offset to this field
   */
  public int getOffset() {
    return offset;
  }
}
