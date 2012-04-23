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
package org.jikesrvm.compilers.opt;

import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.Athrow;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.BasicBlockEnumeration;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.IfCmp;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.InstructionEnumeration;
import org.jikesrvm.compilers.opt.ir.Trap;
import org.jikesrvm.compilers.opt.ir.operand.BranchProfileOperand;
import org.jikesrvm.compilers.opt.ir.operand.MethodOperand;

/**
 * This pass adjusts branch probabilities derived from static estimates
 * to account for blocks that are statically guessed to be infrequent.
 */
public class AdjustBranchProbabilities extends CompilerPhase {

  @Override
  public final String getName() {
    return "Adjust Branch Probabilities";
  }

  @Override
  public final CompilerPhase newExecution(IR ir) {
    return this;
  }

  /**
   * Simplistic adjustment of branch probabilities.
   * The main target of this pass is to detect idioms like
   *   if (P) { infrequent block }
   *   if (P) { } else { infrequent block }
   * that are introduced by ExpandRuntimeServices.
   *
   * Key idea: If a block is infrequent then make sure that
   *           any conditional branch that targets/avoids the block
   *           does not have 0.5 as its branch probability.
   *
   * @param ir the governing IR
   */
  @Override
  public final void perform(IR ir) {
    for (BasicBlockEnumeration e = ir.getBasicBlocks(); e.hasMoreElements();) {
      BasicBlock target = e.next();
      if (findInfrequentInstruction(target)) {
        blockLoop:
        for (BasicBlockEnumeration sources = target.getIn(); sources.hasMoreElements();) {
          BasicBlock source = sources.next();
          // Found an edge to an infrequent block.
          // Look to see if there is a conditional branch that we need to adjust
          Instruction condBranch = null;
          for (InstructionEnumeration ie = source.enumerateBranchInstructions(); ie.hasMoreElements();) {
            Instruction s = ie.next();
            if (IfCmp.conforms(s) && IfCmp.getBranchProfile(s).takenProbability == 0.5f) {
              if (condBranch == null) {
                condBranch = s;
              } else {
                continue blockLoop; // branching is too complicated.
              }
            }
          }
          if (condBranch != null) {
            BasicBlock notTaken = source.getNotTakenNextBlock();
            if (notTaken == target) {
              // The not taken branch is the unlikely one, make the branch be taken always.
              IfCmp.setBranchProfile(condBranch, BranchProfileOperand.always());
            } else {
              // The taken branch is the unlikely one,
              IfCmp.setBranchProfile(condBranch, BranchProfileOperand.never());
            }
          }
        }
      }
    }
  }

  private boolean findInfrequentInstruction(BasicBlock bb) {
    for (InstructionEnumeration e2 = bb.forwardRealInstrEnumerator(); e2.hasMoreElements();) {
      Instruction s = e2.next();
      if (Call.conforms(s)) {
        MethodOperand op = Call.getMethod(s);
        if (op != null) {
          RVMMethod target = op.getTarget();
          if (target != null && target.hasNoInlinePragma()) {
            return true;
          }
        }
      } else if (Athrow.conforms(s) || Trap.conforms(s)) {
        return true;
      }
    }
    return false;
  }
}
