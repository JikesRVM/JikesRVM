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
 * Types in the scripting language.
 */
public abstract class AbstractType implements Type {

  private final String name;

  /**
   * @param name Name of this type
   */
  public AbstractType(String name) {
    this.name = name;
  }

  /**
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    return getName();
  }

  /**
   * @see org.mmtk.harness.lang.type.Type#getName()
   */
  @Override
  public String getName() {
    return name;
  }

  /**
   * @see org.mmtk.harness.lang.type.Type#isObject()
   */
  @Override
  public boolean isObject() {
    return false;
  }

  /**
   * @see org.mmtk.harness.lang.type.Type#isUserType()
   */
  @Override
  public boolean isUserType() {
    return false;
  }

  /**
   * @see org.mmtk.harness.lang.type.Type#isCompatibleWith(org.mmtk.harness.lang.type.Type)
   */
  public boolean isCompatibleWith(Type rhs) {
    return this == rhs;
  }
}
