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
package org.mmtk.harness.lang.pcode;

import org.mmtk.harness.lang.Env;
import org.mmtk.harness.lang.ast.AST;

public final class Goto extends NullaryOp {
  private int target;

  public Goto(AST source, int target) {
    super(source,"goto");
    setBranchTarget(target);
  }

  /**
   * A goto instruction when we don't yet know the target - the
   * compiler comes back later and fills it in.
   * @param source
   */
  public Goto(AST source) {
    super(source,"goto");
  }

  public void setBranchTarget(int target) {
    this.target = target;
  }

  @Override
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

  @Override
  public String toString() {
    return String.format("%s %d", name, target);
  }

}
