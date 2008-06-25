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
 * The value of a variable
 */
public class Variable implements Expression {
  /** The slot of the variable this expression refers to */
  private final int slot;

  /**
   * Constructor
   * @param slot
   */
  public Variable(int slot) {
    this.slot = slot;
  }

  public Value eval(Env env) {
    return env.top().get(slot);
  }

  /**
   * Return the slot of the variable this expression refers to.
   */
  public int getSlot() {
    return slot;
  }
}
