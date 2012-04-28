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
package org.jikesrvm.compilers.opt.ir.operand.ia32;

import org.jikesrvm.compilers.opt.ir.operand.Operand;

/**
 * An FPR register that BURS is managing.
 * Created by a fld, and then eventually
 * deallocated with some popping alu/store.
 */
public final class BURSManagedFPROperand extends Operand {
  public int regNum;

  public BURSManagedFPROperand(int r) {
    regNum = r;
  }

  @Override
  public Operand copy() {
    return new BURSManagedFPROperand(regNum);
  }

  @Override
  public boolean similar(Operand op) {
    return (op instanceof BURSManagedFPROperand) && ((BURSManagedFPROperand) op).regNum == regNum;
  }

  // Returns the string representation of this operand.
  @Override
  public String toString() {
    return "ST(" + regNum + ")";
  }

}
