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
package org.mmtk.harness.lang.ast;

/**
 * Types in the scripting language.
 */
public enum Type {
  INT,
  OBJECT,
  STRING,
  BOOLEAN,
  VOID;

  /**
   * Return a friendly version of the type for error messages.
   */
  public String toString() {
    return name().toLowerCase();
  }

  public boolean isCompatibleWith(Type rhs) {
    if (this == rhs) {
      return true;
    }
    if (this == BOOLEAN && rhs == OBJECT) {
      return true;
    }
    return false;
  }
}
