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
 * A variable declaration
 */
public class Declaration {
  /** Name of the variable */
  public String name;

  /** Initial value - actually holds the value for the lifetime of the variable */
  public Value initial;

  /** Stack frame slot */
  public int slot;

  /**
   * Constructor
   *
   * @param name
   * @param initial
   * @param slot
   */
  public Declaration(String name, Value initial, int slot) {
    this.name = name;
    this.initial = initial;
    this.slot = slot;
  }


}
