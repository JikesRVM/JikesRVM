/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.opt;

import java.util.Enumeration;
import java.util.Iterator;
import org.jikesrvm.opt.ir.*;
import static org.jikesrvm.opt.ir.OPT_Operators.*;

/**
 * Change SPLIT operations inserting for live range splitting into Moves.
 *
 * @author Stephen Fink
 */
public final class OPT_MutateSplits extends OPT_CompilerPhase {

  /**
   * Return this instance of this phase. This phase contains no
   * per-compilation instance fields.
   * @param ir not used
   * @return this 
   */
  public OPT_CompilerPhase newExecution (OPT_IR ir) {
    return this;
  }

  public boolean shouldPerform (OPT_Options options) {
    return options.LIVE_RANGE_SPLITTING;
  }

  public String getName () {
    return "Mutate Splits";
  }

  /**
   * The main entrypoint for this pass.
   */
  public void perform(OPT_IR ir) {
    for (Iterator<OPT_Instruction> e = ir.forwardInstrEnumerator(); e.hasNext();) {
      OPT_Instruction s = e.next();
      if (s.operator == SPLIT) {
        OPT_RegisterOperand lhs = Unary.getResult(s);
        OPT_Operator mv = OPT_IRTools.getMoveOp(lhs.type);
        OPT_Operand rhs = Unary.getVal(s);
        Move.mutate(s,mv,lhs,rhs);
      }
    }
  }
}
