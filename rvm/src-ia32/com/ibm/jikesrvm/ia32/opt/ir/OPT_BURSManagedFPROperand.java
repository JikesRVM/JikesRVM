/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.jikesrvm.ia32.opt.ir;

import com.ibm.jikesrvm.opt.ir.OPT_Operand;

/**
 * An FPR register that BURS is managing.
 * Created by a fld, and then eventually
 * deallocated with some popping alu/store.
 *
 * @author Dave Grove
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
    return (op instanceof OPT_BURSManagedFPROperand) && 
      ((OPT_BURSManagedFPROperand)op).regNum == regNum;
  }

  // Returns the string representation of this operand.
  public String toString() {
    return "ST("+regNum+")";
  }

}
