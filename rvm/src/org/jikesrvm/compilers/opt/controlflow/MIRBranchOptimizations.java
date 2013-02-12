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

import org.jikesrvm.compilers.opt.ir.MIR_Branch;
import org.jikesrvm.compilers.opt.ir.MIR_CondBranch;
import org.jikesrvm.compilers.opt.ir.MIR_CondBranch2;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Operators;
import org.jikesrvm.compilers.opt.ir.operand.BranchOperand;

import static org.jikesrvm.compilers.opt.ir.Operators.BBEND;

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
    if (MIR_Branch.conforms(s)) {
      return processGoto(ir, s, bb);
    } else if (MIR_CondBranch.conforms(s)) {
      return processCondBranch(ir, s, bb);
    } else if (MIR_CondBranch2.conforms(s)) {
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
   * <pre>
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
    if (MIR_Branch.conforms(targetInst)) {
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
      BranchOperand top = (BranchOperand) MIR_Branch.getTarget(targetInst).copy();
      MIR_Branch.setTarget(g, top);
      bb.recomputeNormalOut(ir); // fix the CFG
      return true;
    }
    if (targetBlock.isEmpty()) {
      // GOTO an empty block.  Change target to the next block.
      BasicBlock nextBlock = targetBlock.getFallThroughBlock();
      MIR_Branch.setTarget(g, nextBlock.makeJumpTarget());
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
      if (nextI != null && MIR_Branch.conforms(nextI)) {
        // replicate Goto
        cb.insertAfter(nextI.copyWithoutLinks());
        bb.recomputeNormalOut(ir); // fix the CFG
        return true;
      }
    }
    if (MIR_Branch.conforms(targetInst)) {
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
      MIR_CondBranch.setTarget(cb, (BranchOperand) (MIR_Branch.getTarget(targetInst).copy()));
      bb.recomputeNormalOut(ir); // fix the CFG
      return true;
    }
    if (targetBlock.isEmpty()) {
      // branch to an empty block.  Change target to the next block.
      BasicBlock nextBlock = targetBlock.getFallThroughBlock();
      BranchOperand newTarget = nextBlock.makeJumpTarget();
      MIR_CondBranch.setTarget(cb, newTarget);
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
    Instruction target1Label = MIR_CondBranch2.getTarget1(cb).target;
    Instruction target1Inst = firstRealInstructionFollowing(target1Label);
    Instruction nextLabel = firstLabelFollowing(cb);
    boolean endsBlock = cb.nextInstructionInCodeOrder().operator() == BBEND;
    if (target1Inst != null && target1Inst != cb) {
      if (MIR_Branch.conforms(target1Inst)) {
        // conditional branch to unconditional branch.
        // change conditional branch target to latter's target
        MIR_CondBranch2.setTarget1(cb, MIR_Branch.getTarget(target1Inst));
        bb.recomputeNormalOut(ir); // fix CFG
        return true;
      }
      BasicBlock target1Block = target1Label.getBasicBlock();
      if (target1Block.isEmpty()) {
        // branch to an empty block.  Change target to the next block.
        BasicBlock nextBlock = target1Block.getFallThroughBlock();
        MIR_CondBranch2.setTarget1(cb, nextBlock.makeJumpTarget());
        bb.recomputeNormalOut(ir); // fix the CFG
        return true;
      }
    }

    // Second condition/target
    Instruction target2Label = MIR_CondBranch2.getTarget2(cb).target;
    Instruction target2Inst = firstRealInstructionFollowing(target2Label);
    if (target2Inst != null && target2Inst != cb) {
      if (MIR_Branch.conforms(target2Inst)) {
        // conditional branch to unconditional branch.
        // change conditional branch target to latter's target
        MIR_CondBranch2.setTarget2(cb, MIR_Branch.getTarget(target2Inst));
        bb.recomputeNormalOut(ir); // fix CFG
        return true;
      }
      if ((target2Label == nextLabel) && endsBlock) {
        // found a conditional branch to the next instruction.
        // Reduce to MIR_BranchCond
        Operators.helper.mutateMIRCondBranch(cb);
        return true;
      }
      BasicBlock target2Block = target2Label.getBasicBlock();
      if (target2Block.isEmpty()) {
        // branch to an empty block.  Change target to the next block.
        BasicBlock nextBlock = target2Block.getFallThroughBlock();
        MIR_CondBranch2.setTarget2(cb, nextBlock.makeJumpTarget());
        bb.recomputeNormalOut(ir); // fix the CFG
        return true;
      }
    }

    // if fall through to a goto; replicate the goto
    if (endsBlock) {
      Instruction nextI = firstRealInstructionFollowing(nextLabel);
      if (nextI != null && MIR_Branch.conforms(nextI)) {
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
    if (!MIR_Branch.conforms(next)) {
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
    BranchOperand gTarget = MIR_Branch.getTarget(g);
    // now flip the test and set the new target
    MIR_CondBranch.setCond(cb, MIR_CondBranch.getCond(cb).flipCode());
    MIR_CondBranch.setTarget(cb, gTarget);
    // Remove the trailing GOTO instruction
    g.remove();
  }
}
