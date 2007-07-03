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
package org.jikesrvm.compilers.opt;

import java.util.Enumeration;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_IRTools;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_Operand;
import org.jikesrvm.compilers.opt.ir.OPT_Operator;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.SPLIT;
import org.jikesrvm.compilers.opt.ir.OPT_RegisterOperand;
import org.jikesrvm.compilers.opt.ir.Unary;

/**
 * Change SPLIT operations inserting for live range splitting into Moves.
 */
public final class OPT_MutateSplits extends OPT_CompilerPhase {

  /**
   * Return this instance of this phase. This phase contains no
   * per-compilation instance fields.
   * @param ir not used
   * @return this
   */
  public OPT_CompilerPhase newExecution(OPT_IR ir) {
    return this;
  }

  public boolean shouldPerform(OPT_Options options) {
    return options.LIVE_RANGE_SPLITTING;
  }

  public String getName() {
    return "Mutate Splits";
  }

  /**
   * The main entrypoint for this pass.
   */
  public void perform(OPT_IR ir) {
    for (Enumeration<OPT_Instruction> e = ir.forwardInstrEnumerator(); e.hasMoreElements();) {
      OPT_Instruction s = e.nextElement();
      if (s.operator == SPLIT) {
        OPT_RegisterOperand lhs = Unary.getResult(s);
        OPT_Operator mv = OPT_IRTools.getMoveOp(lhs.type);
        OPT_Operand rhs = Unary.getVal(s);
        Move.mutate(s, mv, lhs, rhs);
      }
    }
  }
}
