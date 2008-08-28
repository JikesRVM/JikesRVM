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
package org.mmtk.harness.lang.pcode;

import org.mmtk.harness.lang.Env;

public final class Goto extends NullaryOp {
  private int target;

  public Goto(int target) {
    super("goto");
    setBranchTarget(target);
  }

  public Goto() {
    super("goto");
  }

  public void setBranchTarget(int target) {
    this.target = target;
  }

  public int getBranchTarget() {
    return target;
  }

  @Override
  public void exec(Env env) {
    /* Do nothing */
  }

  @Override
  public boolean affectsControlFlow() {
    return true;
  }

  @Override
  public boolean isBranch() {
    return true;
  }

  @Override
  public boolean isTaken(Env env) {
    return true;
  }

  public String toString() {
    return String.format("%s %d", name, target);
  }

}
