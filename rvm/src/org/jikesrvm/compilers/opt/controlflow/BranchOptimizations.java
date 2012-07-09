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

import static org.jikesrvm.compilers.opt.ir.IRTools.CPOS;
import static org.jikesrvm.compilers.opt.ir.Operators.BBEND;
import static org.jikesrvm.compilers.opt.ir.Operators.BOOLEAN_CMP_ADDR;
import static org.jikesrvm.compilers.opt.ir.Operators.BOOLEAN_CMP_INT;
import static org.jikesrvm.compilers.opt.ir.Operators.BOOLEAN_NOT;
import static org.jikesrvm.compilers.opt.ir.Operators.DOUBLE_2FLOAT_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.DOUBLE_ADD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.DOUBLE_MOVE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.DOUBLE_MUL_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.DOUBLE_NEG_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.DOUBLE_SUB_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.FLOAT_2DOUBLE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.FLOAT_ADD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.FLOAT_MOVE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.FLOAT_MUL_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.FLOAT_NEG_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.FLOAT_SUB_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.GOTO;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_2BYTE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_2SHORT_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_2USHORT_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_ADD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_AND_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_IFCMP;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_IFCMP2;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_MOVE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_MUL_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_NEG_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_NOT_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_OR_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_SHL_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_SHR_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_SUB_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_USHR_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_XOR_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_ADD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_AND_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_IFCMP;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_MOVE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_NOT_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_OR_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_SHL_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_SHR_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_SUB_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_USHR_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_XOR_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.RETURN;

import java.util.HashMap;
import java.util.HashSet;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.BooleanCmp;
import org.jikesrvm.compilers.opt.ir.CondMove;
import org.jikesrvm.compilers.opt.ir.Goto;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.IRTools;
import org.jikesrvm.compilers.opt.ir.IfCmp;
import org.jikesrvm.compilers.opt.ir.IfCmp2;
import org.jikesrvm.compilers.opt.ir.InlineGuard;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.InstructionEnumeration;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.OperandEnumeration;
import org.jikesrvm.compilers.opt.ir.Operator;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.Return;
import org.jikesrvm.compilers.opt.ir.Unary;
import org.jikesrvm.compilers.opt.ir.operand.BranchOperand;
import org.jikesrvm.compilers.opt.ir.operand.BranchProfileOperand;
import org.jikesrvm.compilers.opt.ir.operand.ConditionOperand;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;

/**
 * Perform simple peephole optimizations for branches.
 */
public final class BranchOptimizations extends BranchOptimizationDriver {

  /**
   * Name of abs method used as a special case in conditional moves
   */
  private static final Atom ABS = Atom.findOrCreateAsciiAtom("abs");

  /**
   * Is branch optimizations allowed to change the code order to
   * create fallthrough edges (and thus merge basic blocks)?
   * After we run code reordering, we disallow this transformation to avoid
   * destroying the desired code order.
   */
  private final boolean mayReorderCode;

  /**
   * Are we allowed to duplication conditional branches?
   * Restricted until backedge yieldpoints are inserted to
   * avoid creating irreducible control flow by duplicating
   * a conditional branch in a loop header into a block outside the
   * loop, thus creating two loop entry blocks.
   */
  private final boolean mayDuplicateCondBranches;

  /**
   * @param level the minimum optimization level at which the branch
   *              optimizations should be performed.
   * @param mayReorderCode are we allowed to change the code order?
   * @param mayDuplicateCondBranches are we allowed to duplicate conditional branches?
   */
  public BranchOptimizations(int level, boolean mayReorderCode, boolean mayDuplicateCondBranches) {
    super(level, true);
    this.mayReorderCode = mayReorderCode;
    this.mayDuplicateCondBranches = mayDuplicateCondBranches;
  }

  /**
   * @param level the minimum optimization level at which the branch
   *              optimizations should be performed.
   * @param mayReorderCode are we allowed to change the code order?
   * @param mayDuplicateCondBranches are we allowed to duplicate conditional branches?
   * @param simplify simplify prior to optimizing?
   */
  public BranchOptimizations(int level, boolean mayReorderCode, boolean mayDuplicateCondBranches,
                                 boolean simplify) {
    super(level, simplify);
    this.mayReorderCode = mayReorderCode;
    this.mayDuplicateCondBranches = mayDuplicateCondBranches;
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
    if (Goto.conforms(s)) {
      return processGoto(ir, s, bb);
    } else if (IfCmp.conforms(s)) {
      return processConditionalBranch(ir, s, bb);
    } else if (InlineGuard.conforms(s)) {
      return processInlineGuard(ir, s, bb);
    } else if (IfCmp2.conforms(s)) {
      return processTwoTargetConditionalBranch(ir, s, bb);
    } else {
      return false;
    }
  }

  /**
   * Perform optimizations for a Goto.
   *
   * <p> Patterns:
   * <pre>
   *    1)      GOTO A       replaced by  GOTO B
   *         A: GOTO B
   *
   *    2)      GOTO A       replaced by  IF .. GOTO B
   *         A: IF .. GOTO B              GOTO C
   *         C: ...
   *    3)   GOTO next instruction eliminated
   *    4)      GOTO A       replaced by  GOTO B
   *         A: LABEL
   *            BBEND
   *         B:
   *    5)   GOTO BBn where BBn has exactly one in edge
   *         - move BBn immediately after the GOTO in the code order,
   *           so that pattern 3) will create a fallthrough
   * <pre>
   *
   * <p> Precondition: Goto.conforms(g)
   *
   * @param ir governing IR
   * @param g the instruction to optimize
   * @param bb the basic block holding g
   * @return {@code true} if made a transformation
   */
  private boolean processGoto(IR ir, Instruction g, BasicBlock bb) {
    BasicBlock targetBlock = g.getBranchTarget();

    // don't optimize jumps to a code motion landing pad
    if (targetBlock.getLandingPad()) return false;

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
    if (Goto.conforms(targetInst)) {
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
      Goto.setTarget(g, (BranchOperand) Goto.getTarget(targetInst).copy());
      bb.recomputeNormalOut(ir); // fix the CFG
      return true;
    }
    if (targetBlock.isEmpty()) {
      // GOTO an empty basic block.  Change target to the
      // next block.
      BasicBlock nextBlock = targetBlock.getFallThroughBlock();
      Goto.setTarget(g, nextBlock.makeJumpTarget());
      bb.recomputeNormalOut(ir); // fix the CFG
      return true;
    }
    if (mayDuplicateCondBranches && IfCmp.conforms(targetInst)) {
      // unconditional branch to a conditional branch.
      // If the Goto is the only branch instruction in its basic block
      // and the IfCmp is the only non-GOTO branch instruction
      // in its basic block then replace the goto with a copy of
      // targetInst and append another GOTO to the not-taken
      // target of targetInst's block.
      // We impose these additional restrictions to avoid getting
      // multiple conditional branches in a single basic block.
      if (!g.prevInstructionInCodeOrder().isBranch() &&
          (targetInst.nextInstructionInCodeOrder().operator == BBEND ||
           targetInst.nextInstructionInCodeOrder().operator == GOTO)) {
        Instruction copy = targetInst.copyWithoutLinks();
        g.replace(copy);
        Instruction newGoto = targetInst.getBasicBlock().getNotTakenNextBlock().makeGOTO();
        copy.insertAfter(newGoto);
        bb.recomputeNormalOut(ir); // fix the CFG
        return true;
      }
    }

    // try to create a fallthrough
    if (mayReorderCode && targetBlock.getNumberOfIn() == 1) {
      BasicBlock ftBlock = targetBlock.getFallThroughBlock();
      if (ftBlock != null) {
        BranchOperand ftTarget = ftBlock.makeJumpTarget();
        targetBlock.appendInstruction(CPOS(g,Goto.create(GOTO, ftTarget)));
      }

      ir.cfg.removeFromCodeOrder(targetBlock);
      ir.cfg.insertAfterInCodeOrder(bb, targetBlock);
      targetBlock.recomputeNormalOut(ir); // fix the CFG
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
   * 4) special case to generate Boolean compare opcode
   * 5) special case to generate conditional move sequence
   * 6)   IF .. GOTO A       replaced by  IF .. GOTO B
   *   A: LABEL
   *      BBEND
   *   B:
   * 7)  fallthrough to a goto: replicate goto to enable other optimizations.
   * </pre>
   *
   * <p> Precondition: IfCmp.conforms(cb)
   *
   * @param ir the governing IR
   * @param cb the instruction to optimize
   * @param bb the basic block holding if
   * @return {@code true} iff made a transformation
   */
  private boolean processConditionalBranch(IR ir, Instruction cb, BasicBlock bb) {
    BasicBlock targetBlock = cb.getBranchTarget();

    // don't optimize jumps to a code motion landing pad
    if (targetBlock.getLandingPad()) return false;

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
      if (nextI != null && Goto.conforms(nextI)) {
        // Check that the target is not the fall through (the goto itself).
        // If we add a goto to the next block, it will be removed by
        // processGoto and we will loop indefinitely.
        // This can be tripped by (strange) code such as:
        // if (condition) while (true);
        BasicBlock gotoTarget = nextI.getBranchTarget();
        Instruction gotoLabel = gotoTarget.firstInstruction();
        Instruction gotoInst = firstRealInstructionFollowing(gotoLabel);

        if (gotoInst != nextI) {
          // replicate Goto
          cb.insertAfter(nextI.copyWithoutLinks());
          bb.recomputeNormalOut(ir); // fix the CFG
          return true;
        }
      }
    }
    // attempt to generate boolean compare.
    if (generateBooleanCompare(ir, bb, cb, targetBlock)) {
      // generateBooleanCompare does all necessary CFG fixup.
      return true;
    }
    // attempt to generate a sequence using conditional moves
    if (generateCondMove(ir, bb, cb)) {
      // generateCondMove does all necessary CFG fixup.
      return true;
    }

    // do we fall through to a block that has only a goto?
    BasicBlock fallThrough = bb.getFallThroughBlock();
    if (fallThrough != null) {
      Instruction fallThroughInstruction = fallThrough.firstRealInstruction();
      if ((fallThroughInstruction != null) && Goto.conforms(fallThroughInstruction)) {
        // copy goto to bb
        bb.appendInstruction(fallThroughInstruction.copyWithoutLinks());
        bb.recomputeNormalOut(ir);
      }
    }

    if (Goto.conforms(targetInst)) {
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
      IfCmp.setTarget(cb, (BranchOperand) Goto.getTarget(targetInst).copy());
      bb.recomputeNormalOut(ir); // fix the CFG
      return true;
    }
    if (targetBlock.isEmpty()) {
      // branch to an empty block.  Change target to the next block.
      BasicBlock nextBlock = targetBlock.getFallThroughBlock();
      IfCmp.setTarget(cb, nextBlock.makeJumpTarget());
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
   * Perform optimizations for an inline guard.
   *
   * <p> Precondition: InlineGuard.conforms(cb)
   *
   * @param ir the governing IR
   * @param cb the instruction to optimize
   * @param bb the basic block holding if
   * @return {@code true} iff made a transformation
   */
  private boolean processInlineGuard(IR ir, Instruction cb, BasicBlock bb) {
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
      if (nextI != null && Goto.conforms(nextI)) {
        // replicate Goto
        cb.insertAfter(nextI.copyWithoutLinks());
        bb.recomputeNormalOut(ir); // fix the CFG
        return true;
      }
    }
    // do we fall through to a block that has only a goto?
    BasicBlock fallThrough = bb.getFallThroughBlock();
    if (fallThrough != null) {
      Instruction fallThroughInstruction = fallThrough.firstRealInstruction();
      if ((fallThroughInstruction != null) && Goto.conforms(fallThroughInstruction)) {
        // copy goto to bb
        bb.appendInstruction(fallThroughInstruction.copyWithoutLinks());
        bb.recomputeNormalOut(ir);
      }
    }

    if (Goto.conforms(targetInst)) {
      // conditional branch to unconditional branch.
      // change conditional branch target to latter's target
      InlineGuard.setTarget(cb, (BranchOperand) Goto.getTarget(targetInst).copy());
      bb.recomputeNormalOut(ir); // fix the CFG
      return true;
    }
    if (targetBlock.isEmpty()) {
      // branch to an empty block.  Change target to the next block.
      BasicBlock nextBlock = targetBlock.getFallThroughBlock();
      InlineGuard.setTarget(cb, nextBlock.makeJumpTarget());
      bb.recomputeNormalOut(ir); // fix the CFG
      return true;
    }
    return false;
  }

  /**
   * Perform optimizations for a two way conditional branch.
   *
   * <p> Precondition: IfCmp2.conforms(cb)
   *
   * @param ir the governing IR
   * @param cb the instruction to optimize
   * @param bb the basic block holding if
   * @return {@code true} iff made a transformation
   */
  private boolean processTwoTargetConditionalBranch(IR ir, Instruction cb, BasicBlock bb) {
    // First condition/target
    Instruction target1Label = IfCmp2.getTarget1(cb).target;
    Instruction target1Inst = firstRealInstructionFollowing(target1Label);
    Instruction nextLabel = firstLabelFollowing(cb);
    boolean endsBlock = cb.nextInstructionInCodeOrder().operator() == BBEND;
    if (target1Inst != null && target1Inst != cb) {
      if (Goto.conforms(target1Inst)) {
        // conditional branch to unconditional branch.
        // change conditional branch target to latter's target
        IfCmp2.setTarget1(cb, (BranchOperand) Goto.getTarget(target1Inst).copy());
        bb.recomputeNormalOut(ir); // fix CFG
        return true;
      }
      BasicBlock target1Block = target1Label.getBasicBlock();
      if (target1Block.isEmpty()) {
        // branch to an empty block.  Change target to the next block.
        BasicBlock nextBlock = target1Block.getFallThroughBlock();
        IfCmp2.setTarget1(cb, nextBlock.makeJumpTarget());
        bb.recomputeNormalOut(ir); // fix the CFG
        return true;
      }
    }

    // Second condition/target
    Instruction target2Label = IfCmp2.getTarget2(cb).target;
    Instruction target2Inst = firstRealInstructionFollowing(target2Label);
    if (target2Inst != null && target2Inst != cb) {
      if (Goto.conforms(target2Inst)) {
        // conditional branch to unconditional branch.
        // change conditional branch target to latter's target
        IfCmp2.setTarget2(cb, (BranchOperand) Goto.getTarget(target2Inst).copy());
        bb.recomputeNormalOut(ir); // fix CFG
        return true;
      }
      if ((target2Label == nextLabel) && endsBlock) {
        // found a conditional branch to the next instruction. Reduce to IfCmp
        if (VM.VerifyAssertions) VM._assert(cb.operator() == INT_IFCMP2);
        IfCmp.mutate(cb,
                     INT_IFCMP,
                     IfCmp2.getGuardResult(cb),
                     IfCmp2.getVal1(cb),
                     IfCmp2.getVal2(cb),
                     IfCmp2.getCond1(cb),
                     IfCmp2.getTarget1(cb),
                     IfCmp2.getBranchProfile1(cb));
        return true;
      }
      BasicBlock target2Block = target2Label.getBasicBlock();
      if (target2Block.isEmpty()) {
        // branch to an empty block.  Change target to the next block.
        BasicBlock nextBlock = target2Block.getFallThroughBlock();
        IfCmp2.setTarget2(cb, nextBlock.makeJumpTarget());
        bb.recomputeNormalOut(ir); // fix the CFG
        return true;
      }
    }

    // if fall through to a goto; replicate the goto
    if (endsBlock) {
      Instruction nextI = firstRealInstructionFollowing(nextLabel);
      if (nextI != null && Goto.conforms(nextI)) {
        // replicate Goto
        cb.insertAfter(nextI.copyWithoutLinks());
        bb.recomputeNormalOut(ir); // fix the CFG
        return true;
      }
    }

    return false;
  }

  /**
   * Is a conditional branch a candidate to be flipped?
   * See comment 3) of processConditionalBranch
   *
   * <p> Precondition: IfCmp.conforms(cb)
   *
   * @param cb the conditional branch instruction
   * @param target the target instruction (real instruction) of the conditional
   *               branch
   * @return boolean result
   */
  private boolean isFlipCandidate(Instruction cb, Instruction target) {
    // condition 1: is next instruction a GOTO?
    Instruction next = cb.nextInstructionInCodeOrder();
    if (!Goto.conforms(next)) {
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
   * See comment 3) of processConditionalBranch
   *
   * <p> Precondition isFlipCandidate(cb)
   * @param cb the conditional branch instruction
   */
  private void flipConditionalBranch(Instruction cb) {
    // get the trailing GOTO instruction
    Instruction g = cb.nextInstructionInCodeOrder();
    BranchOperand gTarget = (BranchOperand) (Goto.getTarget(g).copy());
    // now flip the test and set the new target
    IfCmp.setCond(cb, IfCmp.getCond(cb).flipCode());
    IfCmp.setTarget(cb, gTarget);

    // Update the branch probability.  It is now the opposite
    cb.flipBranchProbability();
    // finally, remove the trailing GOTO instruction
    g.remove();
  }

  /**
   * Generate a boolean operation opcode
   *
   * <pre>
   * 1) IF br != 0 THEN x=1 ELSE x=0       replaced by INT_MOVE x=br
   *    IF br == 0 THEN x=0 ELSE x=1
   * 2) IF br == 0 THEN x=1 ELSE x=0       replaced by BOOLEAN_NOT x=br
   *    IF br != 0 THEN x=0 ELSE x=1
   * 3) IF v1 ~ v2 THEN x=1 ELSE x=0       replaced by BOOLEAN_CMP x=v1,v2,~
   * </pre>
   *
   *
   * @param cb conditional branch instruction
   * @param res the operand for result
   * @param val1 value being compared
   * @param val2 value being compared with
   * @param cond comparison condition
   */
  private void booleanCompareHelper(Instruction cb, RegisterOperand res, Operand val1, Operand val2,
                                    ConditionOperand cond) {
    if ((val1 instanceof RegisterOperand) &&
        ((RegisterOperand) val1).getType().isBooleanType() &&
        (val2 instanceof IntConstantOperand)) {
      int value = ((IntConstantOperand) val2).value;
      if (VM.VerifyAssertions && (value != 0) && (value != 1)) {
        throw new OptimizingCompilerException("Invalid boolean value");
      }
      int c = cond.evaluate(value, 0);
      if (c == ConditionOperand.TRUE) {
        Unary.mutate(cb, BOOLEAN_NOT, res, val1);
        return;
      } else if (c == ConditionOperand.FALSE) {
        Move.mutate(cb, INT_MOVE, res, val1);
        return;
      }
    }
    BooleanCmp.mutate(cb,
                      (cb.operator() == REF_IFCMP) ? BOOLEAN_CMP_ADDR : BOOLEAN_CMP_INT,
                      res,
                      val1,
                      val2,
                      cond,
                      new BranchProfileOperand());
  }

  /**
   * Attempt to generate a straight-line sequence using conditional move
   * instructions, to replace a diamond control flow structure.
   *
   * <p>Suppose we have the following code, where e{n} is an expression:
   * <pre>
   * if (a op b) {
   *   x = e2;
   *   y = e3;
   * } else {
   *   z = e4;
   *   x = e5;
   * }
   * </pre>
   * We would transform this to:
   * <pre>
   * t1 = a;
   * t2 = b;
   * t3 = e2;
   * t4 = e3;
   * t5 = e4;
   * t6 = e5;
   * COND MOVE [if (t1 op t2) x := t3 else x := t6 ];
   * COND MOVE [if (t1 op t2) y := t4 else y := y];
   * COND MOVE [if (t1 op t2) z := z  else z := t5];
   * </pre>
   *
   * <p>Note that we rely on other optimizations (eg. copy propagation) to
   * clean up some of this unnecessary mess.
   *
   * <p>Note that in this example, we've increased the shortest path by 2
   * expression evaluations, 2 moves, and 3 cond moves, but eliminated one
   * conditional branch.
   *
   * <p>We apply a cost heuristic to guide this transformation:
   * We will eliminate a conditional branch iff it increases the shortest
   * path by no more than 'k' operations.  Currently, we count each
   * instruction (alu, move, or cond move) as 1 evaluation.
   * The parameter k is specified by OPT\_Options.COND_MOVE_CUTOFF.
   *
   * <p> In the example above, since we've increased the shortest path by
   * 6 instructions, we will only perform the transformation if k >= 7.
   *
   * <p> TODO items
   * <ul>
   * <li> consider smarter cost heuristics
   * <li> enhance downstream code generation to avoid redundant evaluation
   * of condition codes.
   * </ul>
   *
   * @param ir governing IR
   * @param bb basic block of cb
   * @param cb conditional branch instruction
   * @return true if the transformation succeeds, false otherwise
   */
  private boolean generateCondMove(IR ir, BasicBlock bb, Instruction cb) {
    final boolean VERBOSE=false;
    if (!VM.BuildForIA32) return false;
    if (!IfCmp.conforms(cb)) return false;

    if (VERBOSE) System.out.println("CondMove: Looking to optimize "+cb);
    // Don't generate CMOVs for branches that can be folded.
    if (IfCmp.getVal1(cb).isConstant() && IfCmp.getVal2(cb).isConstant()) {
      if (VERBOSE) System.out.println("CondMove: fail - could be folded");
      return false;
    }

    // see if bb is the root of an if-then-else.
    Diamond diamond = Diamond.buildDiamond(bb);
    if (diamond == null) {
      if (VERBOSE) System.out.println("CondMove: fail - no diamond");
      return false;
    }
    BasicBlock taken = diamond.getTaken();
    BasicBlock notTaken = diamond.getNotTaken();

    // do not perform the transformation if either branch of the diamond
    // has a taboo instruction (eg., a PEI, store or divide).
    if (taken != null && hasCMTaboo(taken)) {
      if (VERBOSE) System.out.println("CondMove: fail - taken branch has taboo instruction");
      return false;
    }
    if (notTaken != null && hasCMTaboo(notTaken)) {
      if (VERBOSE) System.out.println("CondMove: fail - not taken branch has taboo instruction");
      return false;
    }

    ConditionOperand cond = IfCmp.getCond(cb);

    // Do not generate when we don't know the branch probability or
    // when branch probability is high. CMOVs reduce performance of
    // the out-of-order engine (Intel Optimization Guide -
    // Assembly/Compiler Coding Rule 2).
    // Ignore in the case of an abs() method as we can create tighter
    // instructions.
    BranchProfileOperand profile = IfCmp.getBranchProfile(cb);
    if ((Math.abs(profile.takenProbability - 0.5) >= ir.options.CONTROL_WELL_PREDICTED_CUTOFF) &&
        !(cb.position != null && cb.position.method.getName() == ABS && cond.isFLOATINGPOINT())) {
      if (VERBOSE)
        System.out.println("CondMove: fail - branch could be well predicted by branch predictor: "+
            profile.takenProbability);
      return false;
    }

    // if we must generate FCMP, make sure the condition code is OK
    if (cond.isFLOATINGPOINT()) {
      if (!fpConditionOK(cond)) {
        // Condition not OK, but maybe if we flip the operands
        if (!fpConditionOK(cond.flipOperands())) {
          // still not ok so flip operands back
          cond.flipOperands();
          // give up or for SSE2 check if this is a floating point compare
          // controlling just floating point moves
          if (!VM.BuildForSSE2Full || hasFloatingPointDef(taken, true) || hasFloatingPointDef(notTaken, true)) {
            if (VERBOSE) System.out.println("CondMove: fail - fp condition not OK: "+cond);
            return false;
          }
        } else {
          // flip operands
          Operand val1 = IfCmp.getVal1(cb);
          Operand val2 = IfCmp.getVal2(cb);
          IfCmp.setVal1(cb, val2);
          IfCmp.setVal2(cb, val1);
        }
      }
    }

    if (!cond.isFLOATINGPOINT()) {
      // Can only generate moves of floating point values for floating point
      // compares or for unsigned compares in x87
      if (VM.BuildForSSE2Full || !cond.isUNSIGNED()) {
        if (hasFloatingPointDef(taken, false) || hasFloatingPointDef(notTaken, false)) {
          if (VERBOSE)
            System.out.println("CondMove: fail - not allowed integer condition controlling floating conditional move");
          return false;
        }
      }
    }

    // For now, do not generate CMOVs for longs.
    if (hasLongDef(taken) || hasLongDef(notTaken)) {
      return false;
    }

    // count the number of expression evaluations in each side of the
    // diamond
    int takenCost = 0;
    int notTakenCost = 0;
    if (taken != null) takenCost = evaluateCost(taken);
    if (notTaken != null) notTakenCost = evaluateCost(notTaken);

    // evaluate whether it's profitable.
    int shortestCost = Math.min(takenCost, notTakenCost);
    int xformCost = 2 * (takenCost + notTakenCost);
    int k = ir.options.CONTROL_COND_MOVE_CUTOFF;
    if (xformCost - shortestCost > k) {
      if (VERBOSE) System.out.println("CondMove: fail - cost too high");
      return false;
    }

    // Perform the transformation!
    doCondMove(ir, diamond, cb);
    return true;
  }

  /**
   * Is a specified condition operand 'safe' to transfer into an FCMP
   * instruction?
   */
  private boolean fpConditionOK(ConditionOperand c) {
    // FCOMI sets ZF, PF, and CF as follows:
    // Compare Results      ZF     PF      CF
    // left > right          0      0       0
    // left < right          0      0       1
    // left == right         1      0       0
    // UNORDERED             1      1       1
    switch (c.value) {
    case ConditionOperand.CMPL_EQUAL:
      return false; // (ZF == 1) but ordered
    case ConditionOperand.CMPL_NOT_EQUAL:
      return false; // (ZF == 0) but unordered
    case ConditionOperand.CMPG_LESS:
      return false; // (CF == 1) but ordered
    case ConditionOperand.CMPG_GREATER_EQUAL:
      return false; // (CF == 0) but unordered
    case ConditionOperand.CMPG_LESS_EQUAL:
      return false; // (CF == 1 || ZF == 1) but ordered
    case ConditionOperand.CMPG_GREATER:
      return false; // (CF == 0 && ZF == 0) but unordered

    case ConditionOperand.CMPL_GREATER:
      return true; // (CF == 0 && ZF == 0) and ordered
    case ConditionOperand.CMPL_LESS_EQUAL:
      return true; // (CF == 1 || ZF == 1) and unordered
    case ConditionOperand.CMPL_GREATER_EQUAL:
      return true; // (CF == 0) and ordered
    case ConditionOperand.CMPL_LESS:
      return true; // (CF == 1) and unordered
    default:
      OptimizingCompilerException.UNREACHABLE();
    return false; // keep jikes happy
    }
  }

  /**
   * Do any of the instructions in a basic block define a floating-point
   * register?
   *
   * @param bb basic block to search
   * @param invert invert the sense of the search
   */
  private static boolean hasFloatingPointDef(BasicBlock bb, boolean invert) {
    if (bb == null) return false;
    for (InstructionEnumeration e = bb.forwardRealInstrEnumerator(); e.hasMoreElements();) {
      Instruction s = e.nextElement();
      for (OperandEnumeration d = s.getDefs(); d.hasMoreElements();) {
        Operand def = d.nextElement();
        if (def.isRegister()) {
          if (def.asRegister().getRegister().isFloatingPoint() != invert) return true;
        }
      }
    }
    return false;
  }

  /**
   * Do any of the instructions in a basic block define a long
   * register?
   */
  private boolean hasLongDef(BasicBlock bb) {
    if (bb == null) return false;
    for (InstructionEnumeration e = bb.forwardRealInstrEnumerator(); e.hasMoreElements();) {
      Instruction s = e.nextElement();
      for (OperandEnumeration d = s.getDefs(); d.hasMoreElements();) {
        Operand def = d.nextElement();
        if (def.isRegister()) {
          if (def.asRegister().getRegister().isLong()) return true;
        }
      }
    }
    return false;
  }

  /**
   * Do any of the instructions in a basic block preclude eliminating the
   * basic block with conditional moves?
   */
  private boolean hasCMTaboo(BasicBlock bb) {

    if (bb == null) return false;

    // Note: it is taboo to assign more than once to any register in the
    // block.
    HashSet<Register> defined = new HashSet<Register>();

    for (InstructionEnumeration e = bb.forwardRealInstrEnumerator(); e.hasMoreElements();) {
      Instruction s = e.nextElement();
      if (s.isBranch()) continue;
      // for now, only the following opcodes are legal.
      switch (s.operator.opcode) {
        case INT_MOVE_opcode:
        case REF_MOVE_opcode:
        case DOUBLE_MOVE_opcode:
        case FLOAT_MOVE_opcode:
        case INT_ADD_opcode:
        case REF_ADD_opcode:
        case FLOAT_ADD_opcode:
        case DOUBLE_ADD_opcode:
        case INT_SUB_opcode:
        case REF_SUB_opcode:
        case FLOAT_SUB_opcode:
        case DOUBLE_SUB_opcode:
        case INT_MUL_opcode:
        case FLOAT_MUL_opcode:
        case DOUBLE_MUL_opcode:
        case INT_NEG_opcode:
        case FLOAT_NEG_opcode:
        case DOUBLE_NEG_opcode:
        case REF_SHL_opcode:
        case INT_SHL_opcode:
        case REF_SHR_opcode:
        case INT_SHR_opcode:
        case REF_USHR_opcode:
        case INT_USHR_opcode:
        case REF_AND_opcode:
        case INT_AND_opcode:
        case REF_OR_opcode:
        case INT_OR_opcode:
        case REF_XOR_opcode:
        case INT_XOR_opcode:
        case REF_NOT_opcode:
        case INT_NOT_opcode:
        case INT_2BYTE_opcode:
        case INT_2USHORT_opcode:
        case INT_2SHORT_opcode:
        case FLOAT_2DOUBLE_opcode:
        case DOUBLE_2FLOAT_opcode:
          // these are OK.
          break;
        default:
          return true;
      }

      // make sure no register is defined more than once in this block.
      for (OperandEnumeration defs = s.getDefs(); defs.hasMoreElements();) {
        Operand def = defs.nextElement();
        if (VM.VerifyAssertions) VM._assert(def.isRegister());
        Register r = def.asRegister().getRegister();
        if (defined.contains(r)) return true;
        defined.add(r);
      }
    }

    return false;
  }

  /**
   * Evaluate the cost of a basic block, in number of real instructions.
   */
  private int evaluateCost(BasicBlock bb) {
    int result = 0;
    for (InstructionEnumeration e = bb.forwardRealInstrEnumerator(); e.hasMoreElements();) {
      Instruction s = e.nextElement();
      if (!s.isBranch()) result++;
    }
    return result;
  }

  /**
   * For each real non-branch instruction s in bb,
   * <ul>
   * <li> Copy s to s', and store s' in the returned array
   * <li> Insert the function s->s' in the map
   * </ul>
   */
  private Instruction[] copyAndMapInstructions(BasicBlock bb, HashMap<Instruction, Instruction> map) {
    if (bb == null) return new Instruction[0];

    int count = 0;
    // first count the number of instructions
    for (InstructionEnumeration e = bb.forwardRealInstrEnumerator(); e.hasMoreElements();) {
      Instruction s = e.nextElement();
      if (s.isBranch()) continue;
      count++;
    }
    // now copy.
    Instruction[] result = new Instruction[count];
    int i = 0;
    for (InstructionEnumeration e = bb.forwardRealInstrEnumerator(); e.hasMoreElements();) {
      Instruction s = e.nextElement();
      if (s.isBranch()) continue;
      Instruction sprime = s.copyWithoutLinks();
      result[i++] = sprime;
      map.put(s, sprime);
    }
    return result;
  }

  /**
   * For each in a set of instructions, rewrite every def to use a new
   * temporary register.  If a rewritten def is subsequently used, then
   * use the new temporary register instead.
   */
  private void rewriteWithTemporaries(Instruction[] set, IR ir) {

    // Maintain a mapping holding the new name for each register
    HashMap<Register, Register> map = new HashMap<Register, Register>();
    for (Instruction s : set) {
      // rewrite the uses to use the new names
      for (OperandEnumeration e = s.getUses(); e.hasMoreElements();) {
        Operand use = e.nextElement();
        if (use != null && use.isRegister()) {
          Register r = use.asRegister().getRegister();
          Register temp = map.get(r);
          if (temp != null) {
            use.asRegister().setRegister(temp);
          }
        }
      }

      if (VM.VerifyAssertions) VM._assert(s.getNumberOfDefs() == 1);

      Operand def = s.getDefs().nextElement();
      RegisterOperand rDef = def.asRegister();
      RegisterOperand temp = ir.regpool.makeTemp(rDef);
      map.put(rDef.getRegister(), temp.getRegister());
      s.replaceOperand(def, temp);
    }
  }

  /**
   * Insert each instruction in a list before instruction s
   */
  private void insertBefore(Instruction[] list, Instruction s) {
    for (Instruction x : list) {
      s.insertBefore(x);
    }
  }

  /**
   * Perform the transformation to replace conditional branch with a
   * sequence using conditional moves.
   *
   * @param ir governing IR
   * @param diamond the IR diamond structure to replace
   * @param cb conditional branch instruction at the head of the diamond
   */
  private void doCondMove(IR ir, Diamond diamond, Instruction cb) {
    BasicBlock taken = diamond.getTaken();
    BasicBlock notTaken = diamond.getNotTaken();

    // for each non-branch instruction s in the diamond,
    // copy s to a new instruction s'
    // and store a mapping from s to s'
    HashMap<Instruction, Instruction> takenInstructions = new HashMap<Instruction, Instruction>();
    Instruction[] takenInstructionList = copyAndMapInstructions(taken, takenInstructions);

    HashMap<Instruction, Instruction> notTakenInstructions = new HashMap<Instruction, Instruction>();
    Instruction[] notTakenInstructionList = copyAndMapInstructions(notTaken, notTakenInstructions);

    // Extract the values and condition from the conditional branch.
    Operand val1 = IfCmp.getVal1(cb);
    Operand val2 = IfCmp.getVal2(cb);
    ConditionOperand cond = IfCmp.getCond(cb);

    // Copy val1 and val2 to temporaries, just in case they're defined in
    // the diamond.  If they're not defined in the diamond, copy prop
    // should clean these moves up.
    RegisterOperand tempVal1 = ir.regpool.makeTemp(val1);
    Operator op = IRTools.getMoveOp(tempVal1.getType());
    cb.insertBefore(Move.create(op, tempVal1.copyRO(), val1.copy()));
    RegisterOperand tempVal2 = ir.regpool.makeTemp(val2);
    op = IRTools.getMoveOp(tempVal2.getType());
    cb.insertBefore(Move.create(op, tempVal2.copyRO(), val2.copy()));

    // For each instruction in each temporary set, rewrite it to def a new
    // temporary, and insert it before the branch.
    rewriteWithTemporaries(takenInstructionList, ir);
    rewriteWithTemporaries(notTakenInstructionList, ir);
    insertBefore(takenInstructionList, cb);
    insertBefore(notTakenInstructionList, cb);

    // For each register defined in the TAKEN branch, save a mapping to
    // the corresponding conditional move.
    HashMap<Register, Instruction> takenMap = new HashMap<Register, Instruction>();

    // Now insert conditional moves to replace each instruction in the diamond.
    // First handle the taken branch.
    if (taken != null) {
      for (InstructionEnumeration e = taken.forwardRealInstrEnumerator(); e.hasMoreElements();) {
        Instruction s = e.nextElement();
        if (s.isBranch()) continue;
        Operand def = s.getDefs().nextElement();
        // if the register does not span a basic block, it is a temporary
        // that will now be dead
        if (def.asRegister().getRegister().spansBasicBlock()) {
          Instruction tempS = takenInstructions.get(s);
          RegisterOperand temp = (RegisterOperand) tempS.getDefs().nextElement();
          op = IRTools.getCondMoveOp(def.asRegister().getType());
          Instruction cmov =
              CondMove.create(op,
                              def.asRegister(),
                              tempVal1.copy(),
                              tempVal2.copy(),
                              cond.copy().asCondition(),
                              temp.copy(),
                              def.copy());
          takenMap.put(def.asRegister().getRegister(), cmov);
          cb.insertBefore(cmov);
        }
        s.remove();
      }
    }
    // For each register defined in the NOT-TAKEN branch, save a mapping to
    // the corresponding conditional move.
    HashMap<Register, Instruction> notTakenMap = new HashMap<Register, Instruction>();
    // Next handle the not taken branch.
    if (notTaken != null) {
      for (InstructionEnumeration e = notTaken.forwardRealInstrEnumerator(); e.hasMoreElements();) {
        Instruction s = e.nextElement();
        if (s.isBranch()) continue;
        Operand def = s.getDefs().nextElement();
        // if the register does not span a basic block, it is a temporary
        // that will now be dead
        if (def.asRegister().getRegister().spansBasicBlock()) {
          Instruction tempS = notTakenInstructions.get(s);
          RegisterOperand temp = (RegisterOperand) tempS.getDefs().nextElement();

          Instruction prevCmov = takenMap.get(def.asRegister().getRegister());
          if (prevCmov != null) {
            // if this register was also defined in the taken branch, change
            // the previous cmov with a different 'False' Value
            CondMove.setFalseValue(prevCmov, temp.copy());
            notTakenMap.put(def.asRegister().getRegister(), prevCmov);
          } else {
            // create a new cmov instruction
            op = IRTools.getCondMoveOp(def.asRegister().getType());
            Instruction cmov =
                CondMove.create(op,
                                def.asRegister(),
                                tempVal1.copy(),
                                tempVal2.copy(),
                                cond.copy().asCondition(),
                                def.copy(),
                                temp.copy());
            cb.insertBefore(cmov);
            notTakenMap.put(def.asRegister().getRegister(), cmov);
          }
        }
        s.remove();
      }
    }

    // Mutate the conditional branch into a GOTO.
    BranchOperand target = diamond.getBottom().makeJumpTarget();
    Goto.mutate(cb, GOTO, target);

    // Delete a potential GOTO after cb.
    Instruction next = cb.nextInstructionInCodeOrder();
    if (next.operator != BBEND) {
      next.remove();
    }

    // Recompute the CFG.
    diamond.getTop().recomputeNormalOut(ir); // fix the CFG
  }

  /**
   * Attempt to generate a boolean compare opcode from a conditional branch.
   *
   * <pre>
   * 1)   IF .. GOTO A          replaced by  BOOLEAN_CMP x=..
   *      x = 0
   *      GOTO B
   *   A: x = 1
   *   B: ...
   * </pre>
   *
   * <p> Precondition: <code>IfCmp.conforms(<i>cb</i>)</code>
   *
   *
   * @param ir governing IR
   * @param bb basic block of cb
   * @param cb conditional branch instruction
   * @return true if the transformation succeeds, false otherwise
   */
  private boolean generateBooleanCompare(IR ir, BasicBlock bb, Instruction cb, BasicBlock tb) {

    if ((cb.operator() != INT_IFCMP) && (cb.operator() != REF_IFCMP)) {
      return false;
    }
    // make sure this is the last branch in the block
    if (cb.nextInstructionInCodeOrder().operator() != BBEND) {
      return false;
    }
    Operand val1 = IfCmp.getVal1(cb);
    Operand val2 = IfCmp.getVal2(cb);
    ConditionOperand condition = IfCmp.getCond(cb);
    // "not taken" path
    BasicBlock fb = cb.getBasicBlock().getNotTakenNextBlock();
    // make sure it's a diamond
    if (tb.getNumberOfNormalOut() != 1) {
      return false;
    }
    if (fb.getNumberOfNormalOut() != 1) {
      return false;
    }
    BasicBlock jb = fb.getNormalOut().next();               // join block
    // make sure it's a diamond
    if (!tb.pointsOut(jb)) {
      return false;
    }
    Instruction ti = tb.firstRealInstruction();
    Instruction fi = fb.firstRealInstruction();
    // make sure the instructions in target blocks are either both moves
    // or both returns
    if (ti == null || fi == null) {
      return false;
    }
    if (ti.operator() != fi.operator()) {
      return false;
    }
    if (ti.operator != RETURN && ti.operator() != INT_MOVE) {
      return false;
    }
    //
    // WARNING: This code is currently NOT exercised!
    //
    if (ti.operator() == RETURN) {
      // make sure each of the target blocks contains only one instruction
      if (ti != tb.lastRealInstruction()) {
        return false;
      }
      if (fi != fb.lastRealInstruction()) {
        return false;
      }
      Operand tr = Return.getVal(ti);
      Operand fr = Return.getVal(fi);
      // make sure we're returning constants
      if (!(tr instanceof IntConstantOperand) || !(fr instanceof IntConstantOperand)) {
        return false;
      }
      int tv = ((IntConstantOperand) tr).value;
      int fv = ((IntConstantOperand) fr).value;
      if (!((tv == 1 && fv == 0) || (tv == 1 && fv == 0))) {
        return false;
      }
      RegisterOperand t = ir.regpool.makeTemp(TypeReference.Boolean);
      // Cases 1) and 2)
      if (tv == 0) {
        condition = condition.flipCode();
      }
      booleanCompareHelper(cb, t, val1.copy(), val2.copy(), condition);
      cb.insertAfter(Return.create(RETURN, t.copyD2U()));
    } else {      // (ti.operator() == INT_MOVE)
      // make sure each of the target blocks only does the move
      if (ti != tb.lastRealInstruction() && ti.nextInstructionInCodeOrder().operator() != GOTO) {
        return false;
      }
      if (fi != fb.lastRealInstruction() && fi.nextInstructionInCodeOrder().operator() != GOTO) {
        return false;
      }
      RegisterOperand t = Move.getResult(ti);
      // make sure both moves are to the same register
      if (t.getRegister() != Move.getResult(fi).getRegister()) {
        return false;
      }
      Operand tr = Move.getVal(ti);
      Operand fr = Move.getVal(fi);
      // make sure we're assigning constants
      if (!(tr instanceof IntConstantOperand) || !(fr instanceof IntConstantOperand)) {
        return false;
      }
      int tv = ((IntConstantOperand) tr).value;
      int fv = ((IntConstantOperand) fr).value;
      if (!((tv == 1 && fv == 0) || (tv == 0 && fv == 1))) {
        return false;
      }
      // Cases 3) and 4)
      if (tv == 0) {
        condition = condition.flipCode();
      }
      booleanCompareHelper(cb, t.copyRO(), val1.copy(), val2.copy(), condition);
      Instruction next = cb.nextInstructionInCodeOrder();
      if (next.operator() == GOTO) {
        Goto.setTarget(next, jb.makeJumpTarget());
      } else {
        cb.insertAfter(jb.makeGOTO());
      }
    }
    // fixup CFG
    bb.deleteOut(tb);
    bb.deleteOut(fb);
    bb.insertOut(jb);           // Note: if we processed returns,
    // jb is the exit node.
    return true;
  }
}
