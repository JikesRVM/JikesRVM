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

import java.util.HashSet;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_InstructionEnumeration;
import org.jikesrvm.compilers.opt.ir.OPT_Operand;
import org.jikesrvm.compilers.opt.ir.OPT_Register;

/**
 * Coalesce registers in move instructions where possible.
 */
class OPT_CoalesceMoves extends OPT_CompilerPhase {

  /**
   *  verbose debugging flag
   */
  static final boolean DEBUG = false;

  /**
   * Return this instance of this phase. This phase contains no
   * per-compilation instance fields.
   * @param ir not used
   * @return this
   */
  public OPT_CompilerPhase newExecution(OPT_IR ir) {
    return this;
  }

  /**
   * Should we perform this phase?
   * @param options controlling compiler options
   */
  public final boolean shouldPerform(OPT_Options options) {
    return options.COALESCE_AFTER_SSA;
  }

  /**
   * Return a string name for this phase.
   * @return "Coalesce Moves"
   */
  public final String getName() {
    return "Coalesce Moves";
  }

  /**
   * perform the transformation
   * @param ir the governing IR
   */
  public final void perform(OPT_IR ir) {
    // Compute liveness.
    OPT_LiveAnalysis live = new OPT_LiveAnalysis(false /* GC Maps */, false /* don't skip local
                                                         propagation */);
    live.perform(ir);

    // Compute def-use information.
    OPT_DefUse.computeDU(ir);

    // Number the instructions
    ir.numberInstructions();

    // Maintain a set of dead move instructions.
    HashSet<OPT_Instruction> dead = new HashSet<OPT_Instruction>(5);

    // for each Move instruction ...
    for (OPT_InstructionEnumeration e = ir.forwardInstrEnumerator(); e.hasMoreElements();) {
      OPT_Instruction s = e.nextElement();
      if (s.operator.isMove()) {
        OPT_Register r = Move.getResult(s).asRegister().register;
        if (r.isSymbolic()) {
          OPT_Operand val = Move.getVal(s);
          if (val != null && val.isRegister()) {
            OPT_Register r2 = val.asRegister().register;
            if (r2.isSymbolic()) {
              if (OPT_Coalesce.attempt(ir, live, r, r2)) {
                if (DEBUG) System.out.println("COALESCED " + r + " " + r2);
                dead.add(s);
              }
            }
          }
        }
      }
    }

    // Now remove all dead Move instructions.
    for (OPT_Instruction s : dead) {
      OPT_DefUse.removeInstructionAndUpdateDU(s);
    }
  }
}
