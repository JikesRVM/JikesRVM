/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.util.Enumeration;
import instructionFormats.*;

/**
 * Change SPLIT operations inserting for live range splitting into Moves.
 *
 * @author Stephen Fink
 */
final class OPT_MutateSplits extends OPT_CompilerPhase implements OPT_Operators{

  final boolean shouldPerform (OPT_Options options) {
    return options.LIVE_RANGE_SPLITTING;
  }

  final String getName () {
    return "Mutate Splits";
  }

  final boolean printingEnabled (OPT_Options options, boolean before) {
    return false;
  }

  /**
   * The main entrypoint for this pass.
   */
  final void perform(OPT_IR ir) {
    for (Enumeration e = ir.forwardInstrEnumerator(); e.hasMoreElements();) {
      OPT_Instruction s = (OPT_Instruction)e.nextElement();
      if (s.operator == SPLIT) {
        OPT_RegisterOperand lhs = Unary.getResult(s);
        OPT_Operator mv = OPT_IRTools.getMoveOp(lhs.type, ir.IRStage == ir.LIR);
        OPT_Operand rhs = Unary.getVal(s);
        Move.mutate(s,mv,lhs,rhs);
      }
    }
  }
}
