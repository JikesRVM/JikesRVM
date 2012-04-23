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
package org.jikesrvm.compilers.opt.regalloc;

import java.util.HashSet;

import org.jikesrvm.compilers.opt.DefUse;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.InstructionEnumeration;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.liveness.LiveAnalysis;

/**
 * Coalesce registers in move instructions where possible.
 */
public class CoalesceMoves extends CompilerPhase {

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
  @Override
  public CompilerPhase newExecution(IR ir) {
    return this;
  }

  /**
   * Should we perform this phase?
   * @param options controlling compiler options
   */
  @Override
  public final boolean shouldPerform(OptOptions options) {
    return options.SSA_COALESCE_AFTER;
  }

  /**
   * Return a string name for this phase.
   * @return "Coalesce Moves"
   */
  @Override
  public final String getName() {
    return "Coalesce Moves";
  }

  /**
   * perform the transformation
   * @param ir the governing IR
   */
  @Override
  public final void perform(IR ir) {
    // Compute liveness.
    LiveAnalysis live = new LiveAnalysis(false /* GC Maps */, false /* don't skip local
                                                         propagation */);
    live.perform(ir);

    // Compute def-use information.
    DefUse.computeDU(ir);

    // Number the instructions
    ir.numberInstructions();

    // Maintain a set of dead move instructions.
    HashSet<Instruction> dead = new HashSet<Instruction>(5);

    // for each Move instruction ...
    for (InstructionEnumeration e = ir.forwardInstrEnumerator(); e.hasMoreElements();) {
      Instruction s = e.nextElement();
      if (s.operator.isMove()) {
        Register r = Move.getResult(s).asRegister().getRegister();
        if (r.isSymbolic()) {
          Operand val = Move.getVal(s);
          if (val != null && val.isRegister()) {
            Register r2 = val.asRegister().getRegister();
            if (r2.isSymbolic()) {
              if (Coalesce.attempt(ir, live, r, r2)) {
                if (DEBUG) System.out.println("COALESCED " + r + " " + r2);
                dead.add(s);
              }
            }
          }
        }
      }
    }

    // Now remove all dead Move instructions.
    for (Instruction s : dead) {
      DefUse.removeInstructionAndUpdateDU(s);
    }
  }
}
