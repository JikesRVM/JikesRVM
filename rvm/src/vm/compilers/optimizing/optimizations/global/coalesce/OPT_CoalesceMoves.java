/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import  java.util.*;
import com.ibm.JikesRVM.opt.ir.instructionFormats.*;


/**
 * Coalesce registers in move instructions where possible.
 *
 * @author Stephen Fink
 */
class OPT_CoalesceMoves extends OPT_CompilerPhase implements OPT_Operators {
  
  /**
   *  verbose debugging flag 
   */ 
  static final boolean DEBUG = false;

  /**
   * Should we perform this phase?
   * @param options controlling compiler options
   */
  final boolean shouldPerform(OPT_Options options) {
    return  options.COALESCE_AFTER_SSA;
  }

  /**
   * Return a string name for this phase.
   * @return "Coalesce Moves"
   */
  final String getName() {
    return  "Coalesce Moves";
  }

  /**
   * Should we print the IR before or after performing this phase?
   * @param options controlling compiler options
   * @param before query before if true, after if false.
   */
  final boolean printingEnabled(OPT_Options options, boolean before) {
    return false;
  }

  /**
   * perform the transformation
   * @param ir the governing IR
   */
  final public void perform(OPT_IR ir) {
    // Compute liveness.
    OPT_LiveAnalysis live = new OPT_LiveAnalysis(false /* GC Maps */,
                                                 false /* don't skip local
                                                         propagation */);
    live.perform(ir);

    // Compute def-use information.
    OPT_DefUse.computeDU(ir);

    // Number the instructions
    ir.numberInstructions();

    // Maintain a set of dead move instructions.
    HashSet dead = new HashSet(5);

    // for each Move instruction ...
    for (Enumeration e = ir.forwardInstrEnumerator(); e.hasMoreElements();) {
      OPT_Instruction s = (OPT_Instruction)e.nextElement();
      if (s.operator.isMove()) {
        OPT_Register r = Move.getResult(s).asRegister().register;
        if (r.isSymbolic()) {
          OPT_Operand val = Move.getVal(s);
          if (val!=null && val.isRegister()) {
            OPT_Register r2 = val.asRegister().register;
            if (r2.isSymbolic()) {
              if (OPT_Coalesce.attempt(ir,live,r,r2)) {
                if (DEBUG) System.out.println("COALESCED " + r + " " + r2);
                dead.add(s);
              }
            }
          }
        }
      }
    }

    // Now remove all dead Move instructions.
    for (Iterator i = dead.iterator(); i.hasNext(); ) {
      OPT_Instruction s = (OPT_Instruction)i.next();
      OPT_DefUse.removeInstructionAndUpdateDU(s);
    }
  }
}
