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
package org.mmtk.harness.lang.type;

import org.mmtk.harness.lang.runtime.Value;

/**
 * Types in the scripting language.
 */
public abstract class AbstractType implements Type {

  private final String name;

  public AbstractType(String name) {
    this.name = name;
  }

  @Override
  public String toString() {
    return getName();
  }

  public String getName() {
    return name;
  }

  @Override
  public boolean isObject() {
    return false;
  }

  public boolean isCompatibleWith(Type rhs) {
    return this == rhs;
  }

  public abstract Value initialValue();
}
