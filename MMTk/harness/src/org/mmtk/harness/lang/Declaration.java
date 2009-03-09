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

import org.mmtk.harness.lang.parser.Symbol;
import org.mmtk.harness.lang.runtime.Value;
import org.mmtk.harness.lang.type.Type;

/**
 * A variable declaration
 */
public class Declaration {
  /** Name of the variable */
  private final Symbol symbol;

  /** Initial value - actually holds the value for the lifetime of the variable */
  private final Value initial;

  /** Stack frame slot */
  private final int slot;

  /**
   * Constructor
   *
   * @param name
   * @param initial
   * @param slot
   */
  public Declaration(Symbol symbol, Value initial) {
    this.symbol = symbol;
    this.initial = initial;
    this.slot = symbol.getLocation();
  }

  public void accept(Visitor v) {
    v.visit(this);
  }

  public String getName() {
    return symbol.getName();
  }

  public Value getInitial() {
    return initial;
  }

  public int getSlot() {
    return slot;
  }

  public Type getType() {
    return symbol.getType();
  }
}
