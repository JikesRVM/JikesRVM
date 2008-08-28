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
import org.mmtk.harness.lang.compiler.Register;
import org.mmtk.harness.lang.runtime.StackFrame;

/*
 * A simple branch construct
 */
public final class Branch extends UnaryOp {

  public int target;
  public final boolean branchOn;

  /**
   * @param cond Branch condition
   * @param falseBranch Jump target for false - fall through if true.
   */
  public Branch(Register cond, boolean branchOn, int target) {
    super("if"+branchOn,cond);
    this.target = target;
    this.branchOn = branchOn;
  }

  public Branch(Register cond, boolean branchOn) {
    super("if"+branchOn,cond);
    this.branchOn = branchOn;
  }

  public void setBranchTarget(int target) {
    this.target = target;
  }

  public int getBranchTarget() {
    return target;
  }

  public boolean affectsControlFlow() {
    return true;
  }

  @Override
  public void exec(Env env) {
  }

  @Override
  public boolean isBranch() {
    return true;
  }

  @Override
  public boolean isTaken(Env env) {
    StackFrame frame = env.top();
    return frame.get(operand).getBoolValue() == branchOn;
  }

  public String toString() {
    return String.format("if(%st%d) goto %d", branchOn ? "" : "!", operand, target);
  }

}
