/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import  java.util.*;
import instructionFormats.*;


/**
 * Coalesce registers in phi instructions where possible.
 *
 * @see OPT_SSA
 *
 * @author Stephen Fink
 */
class OPT_CoalescePhis extends OPT_CompilerPhase implements OPT_Operators {
  
  /**
   *  verbose debugging flag 
   */ 
  static final boolean DEBUG = false;

  /**
   * Should we perform this phase?
   * @param options controlling compiler options
   * @return 
   */
  final boolean shouldPerform (OPT_Options options) {
    return  options.COALESCE_PHIS;
  }

  /**
   * Return a string name for this phase.
   * @return "Coalesce Phis"
   */
  final String getName () {
    return  "Coalesce Phis";
  }

  /**
   * Should we print the IR before or after performing this phase?
   * @param options controlling compiler options
   * @param before query before if true, after if false.
   * @return 
   */
  final boolean printingEnabled (OPT_Options options, boolean before) {
    return false;
  }

  /**
   * perform the transformation
   * @param ir the governing IR
   */
  final public void perform (OPT_IR ir) {
    // Compute liveness.
    OPT_LiveAnalysis live = new OPT_LiveAnalysis(false /* GC Maps */,
                                                 false /* don't skip local
                                                         propagation */);
    live.perform(ir);

    // Compute def-use information.
    OPT_DefUse.computeDU(ir);

    // Number the instructions
    ir.numberInstructions();
    
    // for each Phi instruction ...
    for (Enumeration e = ir.forwardInstrEnumerator(); e.hasMoreElements();) {
      OPT_Instruction s = (OPT_Instruction)e.nextElement();
      if (s.operator == PHI) {
        OPT_Register r = Phi.getResult(s).asRegister().register;
        for (int i = 0; i<Phi.getNumberOfValues(s); i++) {
          OPT_Operand val = Phi.getValue(s,i);
          if (val!=null && val.isRegister()) {
            OPT_Register r2 = val.asRegister().register;
            OPT_Coalesce.attempt(ir,live,r,r2);
          }
        }
      }
    }
  }
}
