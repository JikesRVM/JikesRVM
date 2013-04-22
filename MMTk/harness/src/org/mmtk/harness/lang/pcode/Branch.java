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
import org.mmtk.harness.lang.compiler.Register;
import org.mmtk.harness.lang.runtime.StackFrame;

/*
 * A simple branch construct
 */
public final class Branch extends UnaryOp {

  public int target;
  public final boolean branchOn;

  /**
   * Create a branch instruction.  Jump to {@code target}
   * if {@code cond.loadBoolean() == branchOn}
   * @param source
   * @param cond Branch condition
   * @param branchOn Sense of the comparison
   * @param target Jump target
   */
  public Branch(AST source, Register cond, boolean branchOn, int target) {
    super(source, "if"+branchOn,cond);
    this.target = target;
    this.branchOn = branchOn;
  }

  /**
   * Create a branch instruction without a target.  Target will be filled in
   * later by the compiler. Jump to {@code target}
   * if {@code cond.loadBoolean() == branchOn}
   * @param source
   * @param cond Branch condition
   * @param branchOn Sense of the comparison
   */
  public Branch(AST source, Register cond, boolean branchOn) {
    super(source,"if"+branchOn,cond);
    this.branchOn = branchOn;
  }

  public void setBranchTarget(int target) {
    this.target = target;
  }

  @Override
  public int getBranchTarget() {
    return target;
  }

  @Override
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

  @Override
  public String toString() {
    return String.format("[%s] if(%st%d) goto %d", branchOn ? "" : "!", formatGcMap(),
        operand, target);
  }

}
