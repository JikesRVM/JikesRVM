/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.ir.*;

/**
 * This pass adjusts branch probabilities derived from static estimates
 * to account for blocks that are statically guessed to be infrequent.
 *
 * @author Dave Grove
 */
class OPT_AdjustBranchProbabilities extends OPT_CompilerPhase
  implements OPT_Operators {

  public final String getName() {
    return "Adjust Branch Probabilities";
  }

  public final OPT_CompilerPhase newExecution(OPT_IR ir) {
    return this;
  }

  /**
   * Simplistic adjustment of branch probabilities.
   * The main target of this pass is to detect idioms like
   *   if (P) { infrequent block } 
   *   if (P) { } else { infrequent block }
   * that are introduced by OPT_ExpandRuntimeServices.
   * 
   * Key idea: If a block is infrequent then make sure that
   *           any conditional branch that targets/avoids the block
   *           does not have 0.5 as its branch probability.
   * 
   * @param ir the governing IR
   */
  public final void perform (OPT_IR ir) {
    for (OPT_BasicBlockEnumeration e = ir.getBasicBlocks();
         e.hasMoreElements();) {
      OPT_BasicBlock target = e.next();
      if (findInfrequentInstruction(target)) {
      blockLoop:
        for (OPT_BasicBlockEnumeration sources = target.getIn();
             sources.hasMoreElements();) {
          OPT_BasicBlock source = sources.next();
          // Found an edge to an infrequent block.
          // Look to see if there is a conditional branch that we need to adjust
          OPT_Instruction condBranch = null;
          for (OPT_InstructionEnumeration ie = source.enumerateBranchInstructions(); 
               ie.hasMoreElements();) {
            OPT_Instruction s = ie.next();
            if (IfCmp.conforms(s) && 
                IfCmp.getBranchProfile(s).takenProbability == 0.5f) {
              if (condBranch == null) {
                condBranch = s;
              } else {
                continue blockLoop; // branching is too complicated.
              }
            }
          }
          if (condBranch != null) {
            OPT_BasicBlock notTaken = source.getNotTakenNextBlock();
            if (notTaken == target) {
              // The not taken branch is the unlikely one, make the branch be taken always.
              IfCmp.setBranchProfile(condBranch, OPT_BranchProfileOperand.always());
            } else {
              // The taken branch is the unlikely one,
              IfCmp.setBranchProfile(condBranch, OPT_BranchProfileOperand.never());
            }
          }
        }
      }
    }
  }

  private boolean findInfrequentInstruction(OPT_BasicBlock bb) {
    for (OPT_InstructionEnumeration e2 = bb.forwardRealInstrEnumerator();
         e2.hasMoreElements();) {
      OPT_Instruction s = e2.next();
      if (Call.conforms(s)) {
        OPT_MethodOperand op = Call.getMethod(s);
        if (op != null) {
          VM_Method target = op.getTarget();
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




