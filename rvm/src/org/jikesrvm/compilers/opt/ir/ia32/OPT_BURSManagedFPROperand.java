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
package org.jikesrvm.compilers.opt.ir.ia32;

import org.jikesrvm.compilers.opt.ir.OPT_Operand;

/**
 * An FPR register that BURS is managing.
 * Created by a fld, and then eventually
 * deallocated with some popping alu/store.
 */
public final class OPT_BURSManagedFPROperand extends OPT_Operand {
  public int regNum;

  public OPT_BURSManagedFPROperand(int r) {
    regNum = r;
  }

  /**
   * Returns a copy of the current operand.
   */
  public OPT_Operand copy() {
    return new OPT_BURSManagedFPROperand(regNum);
  }

  /**
   * Returns if this operand is the 'same' as another operand.
   *
   * @param op other operand
   */
  public boolean similar(OPT_Operand op) {
    return (op instanceof OPT_BURSManagedFPROperand) && ((OPT_BURSManagedFPROperand) op).regNum == regNum;
  }

  // Returns the string representation of this operand.
  public String toString() {
    return "ST(" + regNum + ")";
  }

}
