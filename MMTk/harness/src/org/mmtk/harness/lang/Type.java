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
 * Types in the scripting language.
 */
public enum Type {
  INT,
  OBJECT,
  STRING,
  BOOLEAN;

  /**
   * Return a friendly version of the type for error messages.
   */
  public String toString() {
    switch (this) {
      case INT:     return "int";
      case OBJECT:  return "object";
      case BOOLEAN: return "boolean";
      case STRING:  return "string";
    }
    return "UNKNOWN";
  }
}
