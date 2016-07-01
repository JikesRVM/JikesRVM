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
package org.jikesrvm.compilers.opt.controlflow;

import static org.jikesrvm.compilers.opt.ir.Operators.BBEND;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.operand.BranchOperand;

/**
 * Perform simple peephole optimizations for MIR branches.
 */
public final class MIRBranchOptimizations extends BranchOptimizationDriver {

  /**
   * @param level the minimum optimization level at which the branch
   * optimizations should be performed.
   */
  public MIRBranchOptimizations(int level) {
    super(level);
  }

  private static boolean isMIR_Branch(Instruction x) {
    if (VM.BuildForIA32) {
      return org.jikesrvm.compilers.opt.ir.ia32.MIR_Branch.conforms(x);
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      return org.jikesrvm.compilers.opt.ir.ppc.MIR_Branch.conforms(x);
    }
  }

  private static boolean isMIR_CondBranch(Instruction x) {
    if (VM.BuildForIA32) {
      return org.jikesrvm.compilers.opt.ir.ia32.MIR_CondBranch.conforms(x);
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      return org.jikesrvm.compilers.opt.ir.ppc.MIR_CondBranch.conforms(x);
    }
  }

  private static boolean isMIR_CondBranch2(Instruction x) {
    if (VM.BuildForIA32) {
      return org.jikesrvm.compilers.opt.ir.ia32.MIR_CondBranch2.conforms(x);
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      return org.jikesrvm.compilers.opt.ir.ppc.MIR_CondBranch2.conforms(x);
    }
  }

  private static BranchOperand MIR_Branch_getTarget(Instruction x) {
    if (VM.BuildForIA32) {
      return org.jikesrvm.compilers.opt.ir.ia32.MIR_Branch.getTarget(x);
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      return org.jikesrvm.compilers.opt.ir.ppc.MIR_Branch.getTarget(x);
    }
  }

  private static BranchOperand MIR_Branch_getClearTarget(Instruction x) {
    if (VM.BuildForIA32) {
      return org.jikesrvm.compilers.opt.ir.ia32.MIR_Branch.getClearTarget(x);
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      return org.jikesrvm.compilers.opt.ir.ppc.MIR_Branch.getClearTarget(x);
    }
  }

  private static void MIR_Branch_setTarget(Instruction x, BranchOperand y) {
    if (VM.BuildForIA32) {
      org.jikesrvm.compilers.opt.ir.ia32.MIR_Branch.setTarget(x, y);
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      org.jikesrvm.compilers.opt.ir.ppc.MIR_Branch.setTarget(x, y);
    }
  }

  private static void MIR_CondBranch_setTarget(Instruction x, BranchOperand y) {
    if (VM.BuildForIA32) {
      org.jikesrvm.compilers.opt.ir.ia32.MIR_CondBranch.setTarget(x, y);
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      org.jikesrvm.compilers.opt.ir.ppc.MIR_CondBranch.setTarget(x, y);
    }
  }

  private static BranchOperand MIR_CondBranch2_getTarget1(Instruction x) {
    if (VM.BuildForIA32) {
      return org.jikesrvm.compilers.opt.ir.ia32.MIR_CondBranch2.getTarget1(x);
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      return org.jikesrvm.compilers.opt.ir.ppc.MIR_CondBranch2.getTarget1(x);
    }
  }

  private static BranchOperand MIR_CondBranch2_getTarget2(Instruction x) {
    if (VM.BuildForIA32) {
      return org.jikesrvm.compilers.opt.ir.ia32.MIR_CondBranch2.getTarget2(x);
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      return org.jikesrvm.compilers.opt.ir.ppc.MIR_CondBranch2.getTarget2(x);
    }
  }

  private static void MIR_CondBranch2_setTarget1(Instruction x, BranchOperand y) {
    if (VM.BuildForIA32) {
      org.jikesrvm.compilers.opt.ir.ia32.MIR_CondBranch2.setTarget1(x, y);
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      org.jikesrvm.compilers.opt.ir.ppc.MIR_CondBranch2.setTarget1(x, y);
    }
  }

  private static void MIR_CondBranch2_setTarget2(Instruction x, BranchOperand y) {
    if (VM.BuildForIA32) {
      org.jikesrvm.compilers.opt.ir.ia32.MIR_CondBranch2.setTarget2(x, y);
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      org.jikesrvm.compilers.opt.ir.ppc.MIR_CondBranch2.setTarget2(x, y);
    }
  }

  /**
   * This method actually does the work of attempting to
   * peephole optimize a branch instruction.
   * See Muchnick ~p.590
   * @param ir the containing IR
   * @param s the branch instruction to optimize
   * @param bb the containing basic block
   * @return {@code true} if an optimization was applied, {@code false} otherwise
   */
  @Override
  protected boolean optimizeBranchInstruction(IR ir, Instruction s, BasicBlock bb) {
    if (isMIR_Branch(s)) {
      return processGoto(ir, s, bb);
    } else if (isMIR_CondBranch(s)) {
      return processCondBranch(ir, s, bb);
    } else if (isMIR_CondBranch2(s)) {
      return processTwoTargetConditionalBranch(ir, s, bb);
    } else {
      return false;
    }
  }

  /**
   * Perform optimizations for an unconditonal branch.
   *
   * <p> Patterns:
   * <pre>
   *    1)      GOTO A       replaced by  GOTO B
   *         A: GOTO B
   *
   *    2)   GOTO next instruction eliminated
   *    3)      GOTO A       replaced by  GOTO B
   *         A: LABEL
   *            BBEND
   *         B:
   * </pre>
   *
   * <p> Precondition: MIR_Branch.conforms(g)
   *
   * @param ir governing IR
   * @param g the instruction to optimize
   * @param bb the basic block holding g
   * @return {@code true} if made a transformation
   */
  private boolean processGoto(IR ir, Instruction g, BasicBlock bb) {
    BasicBlock targetBlock = g.getBranchTarget();
    Instruction targetLabel = targetBlock.firstInstruction();
    // get the first real instruction at the g target
    // NOTE: this instruction is not necessarily in targetBlock,
    // iff targetBlock has no real instructions
    Instruction targetInst = firstRealInstructionFollowing(targetLabel);
    if (targetInst == null || targetInst == g) {
      return false;
    }
    Instruction nextLabel = firstLabelFollowing(g);
    if (targetLabel == nextLabel) {
      // found a GOTO to the next instruction.  just remove it.
      g.remove();
      return true;
    }
    if (isMIR_Branch(targetInst)) {
      // unconditional branch to unconditional branch.
      // replace g with goto to targetInst's target
      Instruction target2 = firstRealInstructionFollowing(targetInst.getBranchTarget().firstInstruction());
      if (target2 == targetInst) {
        // Avoid an infinite recursion in the following bizarre scenario:
        // g: goto L
        // ...
        // L: goto L
        // This happens in jByteMark.EmFloatPnt.denormalize() due to a while(true) {}
        return false;
      }
      BranchOperand top = (BranchOperand)(MIR_Branch_getTarget(targetInst).copy());
      MIR_Branch_setTarget(g, top);
      bb.recomputeNormalOut(ir); // fix the CFG
      return true;
    }
    if (targetBlock.isEmpty()) {
      // GOTO an empty block.  Change target to the next block.
      BasicBlock nextBlock = targetBlock.getFallThroughBlock();
      MIR_Branch_setTarget(g, nextBlock.makeJumpTarget());
      bb.recomputeNormalOut(ir); // fix the CFG
      return true;
    }
    return false;
  }

  /**
   * Perform optimizations for a conditional branch.
   *
   * <pre>
   * 1)   IF .. GOTO A          replaced by  IF .. GOTO B
   *      ...
   *   A: GOTO B
   * 2)   conditional branch to next instruction eliminated
   * 3)   IF (condition) GOTO A  replaced by  IF (!condition) GOTO B
   *      GOTO B                           A: ...
   *   A: ...
   * 4)   IF .. GOTO A       replaced by  IF .. GOTO B
   *   A: LABEL
   *      BBEND
   *   B:
   * 5)  fallthrough to a goto: replicate goto to enable other optimizations.
   * </pre>
   *
   * <p> Precondition: MIR_CondBranch.conforms(cb)
   *
   * @param ir the governing IR
   * @param cb the instruction to optimize
   * @param bb the basic block holding if
   * @return {@code true} iff made a transformation
   */
  private boolean processCondBranch(IR ir, Instruction cb, BasicBlock bb) {
    BasicBlock targetBlock = cb.getBranchTarget();
    Instruction targetLabel = targetBlock.firstInstruction();
    // get the first real instruction at the branch target
    // NOTE: this instruction is not necessarily in targetBlock,
    // iff targetBlock has no real instructions
    Instruction targetInst = firstRealInstructionFollowing(targetLabel);
    if (targetInst == null || targetInst == cb) {
      return false;
    }
    boolean endsBlock = cb.nextInstructionInCodeOrder().operator() == BBEND;
    if (endsBlock) {
      Instruction nextLabel = firstLabelFollowing(cb);
      if (targetLabel == nextLabel) {
        // found a conditional branch to the next instruction.  just remove it.
        cb.remove();
        return true;
      }
      Instruction nextI = firstRealInstructionFollowing(nextLabel);
      if (nextI != null && isMIR_Branch(nextI)) {
        // replicate Goto
        cb.insertAfter(nextI.copyWithoutLinks());
        bb.recomputeNormalOut(ir); // fix the CFG
        return true;
      }
    }
    if (isMIR_Branch(targetInst)) {
      // conditional branch to unconditional branch.
      // change conditional branch target to latter's target
      Instruction target2 = firstRealInstructionFollowing(targetInst.getBranchTarget().firstInstruction());
      if (target2 == targetInst) {
        // Avoid an infinite recursion in the following scenario:
        // g: if (...) goto L
        // ...
        // L: goto L
        // This happens in GCUtil in some systems due to a while(true) {}
        return false;
      }
      MIR_CondBranch_setTarget(cb, (BranchOperand)MIR_Branch_getTarget(targetInst).copy());
      bb.recomputeNormalOut(ir); // fix the CFG
      return true;
    }
    if (targetBlock.isEmpty()) {
      // branch to an empty block.  Change target to the next block.
      BasicBlock nextBlock = targetBlock.getFallThroughBlock();
      BranchOperand newTarget = nextBlock.makeJumpTarget();
      MIR_CondBranch_setTarget(cb, newTarget);
      bb.recomputeNormalOut(ir); // fix the CFG
      return true;
    }
    if (isFlipCandidate(cb, targetInst)) {
      flipConditionalBranch(cb);
      bb.recomputeNormalOut(ir); // fix the CFG
      return true;
    }
    return false;
  }

  /**
   * Perform optimizations for a two way conditional branch.
   *
   * <pre>
   * 1)   IF .. GOTO A          replaced by  IF .. GOTO B
   *      ...
   *   A: GOTO B
   * 2)   conditional branch to next instruction eliminated
   * 3)   IF .. GOTO A       replaced by  IF .. GOTO B
   *   A: LABEL
   *      BBEND
   *   B:
   * 4)  fallthrough to a goto: replicate goto to enable other optimizations.
   * </pre>
   *
   * <p> Precondition: MIR_CondBranch2.conforms(cb)
   *
   * @param ir the governing IR
   * @param cb the instruction to optimize
   * @param bb the basic block holding if
   * @return {@code true} iff made a transformation
   */
  private boolean processTwoTargetConditionalBranch(IR ir, Instruction cb, BasicBlock bb) {
    // First condition/target
    Instruction target1Label = MIR_CondBranch2_getTarget1(cb).target;
    Instruction target1Inst = firstRealInstructionFollowing(target1Label);
    Instruction nextLabel = firstLabelFollowing(cb);
    boolean endsBlock = cb.nextInstructionInCodeOrder().operator() == BBEND;
    if (target1Inst != null && target1Inst != cb) {
      if (isMIR_Branch(target1Inst)) {
        // conditional branch to unconditional branch.
        // change conditional branch target to latter's target
        MIR_CondBranch2_setTarget1(cb, MIR_Branch_getTarget(target1Inst).copy().asBranch());
        bb.recomputeNormalOut(ir); // fix CFG
        return true;
      }
      BasicBlock target1Block = target1Label.getBasicBlock();
      if (target1Block.isEmpty()) {
        // branch to an empty block.  Change target to the next block.
        BasicBlock nextBlock = target1Block.getFallThroughBlock();
        MIR_CondBranch2_setTarget1(cb, nextBlock.makeJumpTarget());
        bb.recomputeNormalOut(ir); // fix the CFG
        return true;
      }
    }

    // Second condition/target
    Instruction target2Label = MIR_CondBranch2_getTarget2(cb).target;
    Instruction target2Inst = firstRealInstructionFollowing(target2Label);
    if (target2Inst != null && target2Inst != cb) {
      if (isMIR_Branch(target2Inst)) {
        // conditional branch to unconditional branch.
        // change conditional branch target to latter's target
        MIR_CondBranch2_setTarget2(cb, MIR_Branch_getTarget(target2Inst).copy().asBranch());
        bb.recomputeNormalOut(ir); // fix CFG
        return true;
      }
      if ((target2Label == nextLabel) && endsBlock) {
        // found a conditional branch to the next instruction.
        // Reduce to MIR_BranchCond
        if (VM.BuildForIA32) {
          org.jikesrvm.compilers.opt.ir.ia32.MIR_CondBranch.mutate(cb,
              org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.IA32_JCC,
              org.jikesrvm.compilers.opt.ir.ia32.MIR_CondBranch2.getCond1(cb),
              org.jikesrvm.compilers.opt.ir.ia32.MIR_CondBranch2.getTarget1(cb),
              org.jikesrvm.compilers.opt.ir.ia32.MIR_CondBranch2.getBranchProfile1(cb));
        } else {
          if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
          org.jikesrvm.compilers.opt.ir.ppc.MIR_CondBranch.mutate(cb,
              org.jikesrvm.compilers.opt.ir.ppc.ArchOperators.PPC_BCOND,
              org.jikesrvm.compilers.opt.ir.ppc.MIR_CondBranch2.getValue(cb),
              org.jikesrvm.compilers.opt.ir.ppc.MIR_CondBranch2.getCond1(cb),
              org.jikesrvm.compilers.opt.ir.ppc.MIR_CondBranch2.getTarget1(cb),
              org.jikesrvm.compilers.opt.ir.ppc.MIR_CondBranch2.getBranchProfile1(cb));
        }
        return true;
      }
      BasicBlock target2Block = target2Label.getBasicBlock();
      if (target2Block.isEmpty()) {
        // branch to an empty block.  Change target to the next block.
        BasicBlock nextBlock = target2Block.getFallThroughBlock();
        MIR_CondBranch2_setTarget2(cb, nextBlock.makeJumpTarget());
        bb.recomputeNormalOut(ir); // fix the CFG
        return true;
      }
    }

    // if fall through to a goto; replicate the goto
    if (endsBlock) {
      Instruction nextI = firstRealInstructionFollowing(nextLabel);
      if (nextI != null && isMIR_Branch(nextI)) {
        // replicate unconditional branch
        cb.insertAfter(nextI.copyWithoutLinks());
        bb.recomputeNormalOut(ir); // fix the CFG
        return true;
      }
    }

    return false;
  }

  /**
   * Is a conditional branch a candidate to be flipped?
   * See comment 3) of processCondBranch
   *
   * <p> Precondition: MIR_CondBranch.conforms(cb)
   *
   * @param cb the conditional branch instruction
   * @param target the target instruction (real instruction) of the conditional
   *               branch
   * @return boolean result
   */
  private boolean isFlipCandidate(Instruction cb, Instruction target) {
    // condition 1: is next instruction a GOTO?
    Instruction next = cb.nextInstructionInCodeOrder();
    if (!isMIR_Branch(next)) {
      return false;
    }
    // condition 2: is the target of the conditional branch the
    //  next instruction after the GOTO?
    next = firstRealInstructionFollowing(next);
    if (next != target) {
      return false;
    }
    // got this far.  It's a candidate.
    return true;
  }

  /**
   * Flip a conditional branch and remove the trailing goto.
   * See comment 3) of processCondBranch
   *
   * <p> Precondition isFlipCandidate(cb)
   * @param cb the conditional branch instruction
   */
  private void flipConditionalBranch(Instruction cb) {
    // get the trailing GOTO instruction
    Instruction g = cb.nextInstructionInCodeOrder();
    BranchOperand gTarget = MIR_Branch_getClearTarget(g);
    // now flip the test and set the new target
    if (VM.BuildForIA32) {
      org.jikesrvm.compilers.opt.ir.ia32.MIR_CondBranch.setCond(cb,
          org.jikesrvm.compilers.opt.ir.ia32.MIR_CondBranch.getCond(cb).flipCode());
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      org.jikesrvm.compilers.opt.ir.ppc.MIR_CondBranch.setCond(cb,
          org.jikesrvm.compilers.opt.ir.ppc.MIR_CondBranch.getCond(cb).flipCode());
    }
    MIR_CondBranch_setTarget(cb, gTarget);
    // Remove the trailing GOTO instruction
    g.remove();
  }
}
