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
 * Arithmetic and logical operators
 *
 * Operations themselves implemented in the appropriate Expression types
 */
public enum Operator {
  /* Equality */
  EQ,
  NE,
  /* Integer comparison */
  GT,
  LT,
  LE,
  GE,
  /* Logical */
  AND,
  OR,
  /* Unary */
  NOT,
  /* Mathematical */
  PLUS,
  MINUS,
  MULT,
  DIV,
  REM,
  LS,
  RS,
  RSL;

  /**
   * @return true if this is a binary operation
   */
  public boolean isBinary() {
    return !isUnary();
  }

  /**
   * @return true if this is a unary operation
   */
  public boolean isUnary() {
    return this == NOT;
  }
}
