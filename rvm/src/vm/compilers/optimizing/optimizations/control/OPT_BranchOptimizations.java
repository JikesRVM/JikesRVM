/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;
import com.ibm.JikesRVM.*;

import com.ibm.JikesRVM.opt.ir.*;
import com.ibm.JikesRVM.classloader.VM_TypeReference;
import java.util.HashMap;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.HashSet;

/**
 * Perform simple peephole optimizations for branches.
 * 
 * @author Stephen Fink
 * @author Dave Grove
 * @author Mauricio Serrano
 * @author Martin Trapp
 */
public final class OPT_BranchOptimizations
  extends OPT_BranchOptimizationDriver {

  /**
   * Is branch optimizations allowed to change the code order to
   * create fallthrough edges (and thus merge basic blocks)?
   * After we run code reordering, we disallow this transformation to avoid
   * destroying the desired code order.
   */
  private boolean mayReorderCode;

  /**
   * Are we allowed to duplication conditional branches?
   * Restricted until backedge yieldpoints are inserted to 
   * avoid creating irreducible control flow by duplicating
   * a conditional branch in a loop header into a block outside the
   * loop, thus creating two loop entry blocks.
   */
  private boolean mayDuplicateCondBranches;
   

  private OPT_BranchOptimizations () { }

  /** 
   * @param level the minimum optimization level at which the branch 
   *              optimizations should be performed.
   * @param mayReorderCode are we allowed to change the code order?
   * @param mayDuplicateCondBranches are we allowed to duplicate conditional branches?
   */
  public OPT_BranchOptimizations (int level, boolean mayReorderCode, boolean mayDuplicateCondBranches) {
    super(level);
    mayReorderCode = mayReorderCode;
    mayDuplicateCondBranches = mayDuplicateCondBranches;
  }
  
  /**
   * This method actually does the work of attempting to
   * peephole optimize a branch instruction.
   * See Muchnick ~p.590
   * @param ir the containing IR
   * @param s the branch instruction to optimize
   * @param bb the containing basic block
   * @return true if an optimization was applied, false otherwise
   */
  protected final boolean optimizeBranchInstruction(OPT_IR ir,
                                                    OPT_Instruction s,
                                                    OPT_BasicBlock bb) {
    if (Goto.conforms(s))
      return processGoto(ir, s, bb);
    else if (IfCmp.conforms(s))
      return processConditionalBranch(ir, s, bb);
    else if (InlineGuard.conforms(s))
      return processInlineGuard(ir, s, bb);
    else if (IfCmp2.conforms(s)) 
      return processTwoTargetConditionalBranch(ir, s, bb);
    else 
      return false;
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
   *    5)   GOTO BBn where BBn has exactly one in ede
   *         - move BBn immediately after the GOTO in the code order,
   *           so that pattern 3) will create a fallthrough
   * <pre>
   *
   * <p> Precondition: Goto.conforms(g)
   *
   * @param ir governing IR
   * @param g the instruction to optimize
   * @param bb the basic block holding g
   * @return true if made a transformation
   */
  private boolean processGoto(OPT_IR ir, OPT_Instruction g, 
                              OPT_BasicBlock bb) {
    OPT_BasicBlock targetBlock = g.getBranchTarget();

    // don't optimize jumps to a code motion landing pad 
    if (targetBlock.getLandingPad()) return false;
    
    OPT_Instruction targetLabel = targetBlock.firstInstruction();
    // get the first real instruction at the g target
    // NOTE: this instruction is not necessarily in targetBlock,
    // iff targetBlock has no real instructions
    OPT_Instruction targetInst = firstRealInstructionFollowing(targetLabel);
    if (targetInst == null || targetInst == g) {
      return false;
    }
    OPT_Instruction nextLabel = firstLabelFollowing(g);
    if (targetLabel == nextLabel) {
      // found a GOTO to the next instruction.  just remove it.
      g.remove();
      return true;
    }
    if (Goto.conforms(targetInst)) {
      // unconditional branch to unconditional branch.
      // replace g with goto to targetInst's target
      OPT_Instruction target2 = firstRealInstructionFollowing(targetInst.getBranchTarget().firstInstruction());
      if (target2 == targetInst) {
        // Avoid an infinite recursion in the following bizarre scenario:
        // g: goto L
        // ...
        // L: goto L
        // This happens in jByteMark.EmFloatPnt.denormalize() due to a while(true) {} 
        return false;
      }
      Goto.setTarget(g, Goto.getTarget(targetInst));
      bb.recomputeNormalOut(ir); // fix the CFG 
      return true;
    }
    if (targetBlock.isEmpty()) {
      // GOTO an empty basic block.  Change target to the
      // next block.
      OPT_BasicBlock nextBlock = targetBlock.getFallThroughBlock();
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
        OPT_Instruction copy = targetInst.copyWithoutLinks();
        g.replace(copy);
        OPT_Instruction newGoto = 
          targetInst.getBasicBlock().getNotTakenNextBlock().makeGOTO();
        copy.insertAfter(newGoto);
        bb.recomputeNormalOut(ir); // fix the CFG 
        return true;
      }
    }

    // try to create a fallthrough
    if (mayReorderCode && targetBlock.getNumberOfIn() == 1) {
      OPT_BasicBlock ftBlock = targetBlock.getFallThroughBlock();
      if (ftBlock != null) {
        OPT_BranchOperand ftTarget = ftBlock.makeJumpTarget();
        targetBlock.appendInstruction(Goto.create(GOTO,ftTarget));
      }
     
      ir.cfg.removeFromCodeOrder(targetBlock);
      ir.cfg.insertAfterInCodeOrder(bb,targetBlock);
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
   * @return true iff made a transformation
   */
  private boolean processConditionalBranch(OPT_IR ir, 
                                           OPT_Instruction cb, 
                                           OPT_BasicBlock bb) {
    OPT_BasicBlock targetBlock = cb.getBranchTarget();

    // don't optimize jumps to a code motion landing pad 
    if (targetBlock.getLandingPad()) return false;
    
    OPT_Instruction targetLabel = targetBlock.firstInstruction();
    // get the first real instruction at the branch target
    // NOTE: this instruction is not necessarily in targetBlock,
    // iff targetBlock has no real instructions
    OPT_Instruction targetInst = firstRealInstructionFollowing(targetLabel);
    if (targetInst == null || targetInst == cb) {
      return false;
    }
    boolean endsBlock = cb.nextInstructionInCodeOrder().operator() == BBEND;
    if (endsBlock) {
      OPT_Instruction nextLabel = firstLabelFollowing(cb);

      if (targetLabel == nextLabel) {
        // found a conditional branch to the next instruction.  just remove it.
        cb.remove();
        return true;
      }
      OPT_Instruction nextI = firstRealInstructionFollowing(nextLabel);
      if (nextI != null && Goto.conforms(nextI)) {
        // replicate Goto
        cb.insertAfter(nextI.copyWithoutLinks());
        bb.recomputeNormalOut(ir); // fix the CFG 
        return true;
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
    OPT_BasicBlock fallThrough = bb.getFallThroughBlock();
    if (fallThrough != null) {
      OPT_Instruction
        fallThroughInstruction = fallThrough.firstRealInstruction();
      if ((  fallThroughInstruction != null)
          && Goto.conforms (fallThroughInstruction)) {
        // copy goto to bb
        bb.appendInstruction (fallThroughInstruction.copyWithoutLinks());
        bb.recomputeNormalOut(ir);
      }                                                                  
    }
    
    if (Goto.conforms(targetInst)) {
      // conditional branch to unconditional branch.
      // change conditional branch target to latter's target
      OPT_Instruction target2 = firstRealInstructionFollowing(targetInst.getBranchTarget().firstInstruction());
      if (target2 == targetInst) {
        // Avoid an infinite recursion in the following scenario:
        // g: if (...) goto L
        // ...
        // L: goto L
        // This happens in VM_GCUtil in some systems due to a while(true) {} 
        return false;
      }
      IfCmp.setTarget(cb, Goto.getTarget(targetInst));
      bb.recomputeNormalOut(ir); // fix the CFG 
      return true;
    }
    if (targetBlock.isEmpty()) {
      // branch to an empty block.  Change target to the next block.
      OPT_BasicBlock nextBlock = targetBlock.getFallThroughBlock();
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
   * @return true iff made a transformation
   */
  private boolean processInlineGuard(OPT_IR ir, 
                                     OPT_Instruction cb, 
                                     OPT_BasicBlock bb) {
    OPT_BasicBlock targetBlock = cb.getBranchTarget();
    OPT_Instruction targetLabel = targetBlock.firstInstruction();
    // get the first real instruction at the branch target
    // NOTE: this instruction is not necessarily in targetBlock,
    // iff targetBlock has no real instructions
    OPT_Instruction targetInst = firstRealInstructionFollowing(targetLabel);
    if (targetInst == null || targetInst == cb) {
      return false;
    }
    boolean endsBlock = cb.nextInstructionInCodeOrder().operator() == BBEND;
    if (endsBlock) {
      OPT_Instruction nextLabel = firstLabelFollowing(cb);
      if (targetLabel == nextLabel) {
        // found a conditional branch to the next instruction.  just remove it.
        cb.remove();
        return true;
      }
      OPT_Instruction nextI = firstRealInstructionFollowing(nextLabel);
      if (nextI != null && Goto.conforms(nextI)) {
        // replicate Goto
        cb.insertAfter(nextI.copyWithoutLinks());
        bb.recomputeNormalOut(ir); // fix the CFG 
        return true;
      }
    }
    // do we fall through to a block that has only a goto?
    OPT_BasicBlock fallThrough = bb.getFallThroughBlock();
    if (fallThrough != null) {
      OPT_Instruction
        fallThroughInstruction = fallThrough.firstRealInstruction();
      if ((  fallThroughInstruction != null)
          && Goto.conforms (fallThroughInstruction)) {
        // copy goto to bb
        bb.appendInstruction (fallThroughInstruction.copyWithoutLinks());
        bb.recomputeNormalOut(ir);
      }                                                                  
    }
    
    if (Goto.conforms(targetInst)) {
      // conditional branch to unconditional branch.
      // change conditional branch target to latter's target
      InlineGuard.setTarget(cb, Goto.getTarget(targetInst));
      bb.recomputeNormalOut(ir); // fix the CFG 
      return true;
    }
    if (targetBlock.isEmpty()) {
      // branch to an empty block.  Change target to the next block.
      OPT_BasicBlock nextBlock = targetBlock.getFallThroughBlock();
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
   * @return true iff made a transformation
   */
  private boolean processTwoTargetConditionalBranch(OPT_IR ir, 
                                                    OPT_Instruction cb, 
                                                    OPT_BasicBlock bb) {
    // First condition/target
    OPT_Instruction target1Label = IfCmp2.getTarget1(cb).target; 
    OPT_Instruction target1Inst = firstRealInstructionFollowing(target1Label);
    OPT_Instruction nextLabel = firstLabelFollowing(cb);
    boolean endsBlock = cb.nextInstructionInCodeOrder().operator() == BBEND;
    if (target1Inst != null && target1Inst != cb) {
      if (Goto.conforms(target1Inst)) {
        // conditional branch to unconditional branch.
        // change conditional branch target to latter's target
        IfCmp2.setTarget1(cb, Goto.getTarget(target1Inst));
        bb.recomputeNormalOut(ir); // fix CFG
        return true;
      }
      OPT_BasicBlock target1Block = target1Label.getBasicBlock();
      if (target1Block.isEmpty()) {
        // branch to an empty block.  Change target to the next block.
        OPT_BasicBlock nextBlock = target1Block.getFallThroughBlock();
        IfCmp2.setTarget1(cb, nextBlock.makeJumpTarget());
        bb.recomputeNormalOut(ir); // fix the CFG 
        return true;
      }
    }
      
    // Second condition/target
    OPT_Instruction target2Label = IfCmp2.getTarget2(cb).target; 
    OPT_Instruction target2Inst = firstRealInstructionFollowing(target2Label);
    if (target2Inst != null && target2Inst != cb) {
      if (Goto.conforms(target2Inst)) {
        // conditional branch to unconditional branch.
        // change conditional branch target to latter's target
        IfCmp2.setTarget2(cb, Goto.getTarget(target2Inst));
        bb.recomputeNormalOut(ir); // fix CFG
        return true;
      }
      if ((target2Label == nextLabel) && endsBlock) {
        // found a conditional branch to the next instruction. Reduce to IfCmp
        if (VM.VerifyAssertions) VM._assert(cb.operator() == INT_IFCMP2);
        IfCmp.mutate(cb, INT_IFCMP,
                     IfCmp2.getGuardResult(cb), IfCmp2.getVal1(cb),
                     IfCmp2.getVal2(cb), IfCmp2.getCond1(cb), 
                     IfCmp2.getTarget1(cb), IfCmp2.getBranchProfile1(cb));
        return true;
      }
      OPT_BasicBlock target2Block = target2Label.getBasicBlock();
      if (target2Block.isEmpty()) {
        // branch to an empty block.  Change target to the next block.
        OPT_BasicBlock nextBlock = target2Block.getFallThroughBlock();
        IfCmp2.setTarget2(cb, nextBlock.makeJumpTarget()); 
        bb.recomputeNormalOut(ir); // fix the CFG 
        return true;
      }
    }

    // if fall through to a goto; replicate the goto
    if (endsBlock) {
      OPT_Instruction nextI = firstRealInstructionFollowing(nextLabel);
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
  private boolean isFlipCandidate (OPT_Instruction cb, 
      OPT_Instruction target) {
    // condition 1: is next instruction a GOTO?
    OPT_Instruction next = cb.nextInstructionInCodeOrder();
    if (!Goto.conforms(next))
      return false;
    // condition 2: is the target of the conditional branch the 
    //  next instruction after the GOTO?
    next = firstRealInstructionFollowing(next);
    if (next != target)
      return false;
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
  private void flipConditionalBranch (OPT_Instruction cb) {
    // get the trailing GOTO instruction
    OPT_Instruction g = cb.nextInstructionInCodeOrder();
    OPT_BranchOperand gTarget = Goto.getTarget(g);
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
   * @modified Igor Pechtchanski
   *
   * @param cb conditional branch instruction
   * @param res the operand for result
   * @param val1 value being compared
   * @param val2 value being compared with
   * @param cond comparison condition
   */
  private void booleanCompareHelper(OPT_Instruction cb, 
                                    OPT_RegisterOperand res, 
                                    OPT_Operand val1, 
                                    OPT_Operand val2, 
                                    OPT_ConditionOperand cond) {
    if ((val1 instanceof OPT_RegisterOperand) && 
        ((OPT_RegisterOperand)val1).type.isBooleanType() && 
        (val2 instanceof OPT_IntConstantOperand)) {
      int value = ((OPT_IntConstantOperand)val2).value;
      if (VM.VerifyAssertions && (value != 0) && (value != 1))
        throw  new OPT_OptimizingCompilerException("Invalid boolean value");
      int c = cond.evaluate(value, 0);
      if (c == OPT_ConditionOperand.TRUE) {
        Unary.mutate(cb, BOOLEAN_NOT, res, val1);
        return;
      } else if (c == OPT_ConditionOperand.FALSE) {
        Move.mutate(cb, INT_MOVE, res, val1);
        return;
      }
    } 
    BooleanCmp.mutate(cb, (cb.operator() == REF_IFCMP )? BOOLEAN_CMP_ADDR : BOOLEAN_CMP_INT, res,
                     val1, val2, cond, new OPT_BranchProfileOperand());
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
  private boolean generateCondMove(OPT_IR ir, OPT_BasicBlock bb, 
                                   OPT_Instruction cb) {
    if (!VM.BuildForIA32) return false;
    if (!IfCmp.conforms(cb)) return false;

    // see if bb is the root of an if-then-else.
    OPT_Diamond diamond = OPT_Diamond.buildDiamond(bb);
    if (diamond == null) return false;
    OPT_BasicBlock taken = diamond.getTaken();
    OPT_BasicBlock notTaken= diamond.getNotTaken();

    // do not perform the transformation if either branch of the diamond
    // has a taboo instruction (eg., a PEI, store or divide).
    if (taken != null && hasCMTaboo(taken)) return false;
    if (notTaken != null && hasCMTaboo(notTaken)) return false;

    // if we must generate FCMOV, make sure the condition code is OK
    if (hasFloatingPointDef(taken) || hasFloatingPointDef(notTaken)) {
      if (!fpConditionOK(IfCmp.getCond(cb))) return false;
    }

    // For now, do not generate CMOVs if the condition depends on
    // floating-point compares, with troublesome NaN semantics
    if (IfCmp.getVal1(cb).isRegister()) {
      OPT_Register r = IfCmp.getVal1(cb).asRegister().register;
      if (r.isFloatingPoint() || r.isLong()) return false;
    } else if (IfCmp.getVal2(cb).isRegister()) { 
      OPT_Register r = IfCmp.getVal2(cb).asRegister().register;
      if (r.isFloatingPoint() || r.isLong()) return false;
    }
    // Don't generate CMOVs for branches that can be folded.
    if (IfCmp.getVal1(cb).isConstant() && IfCmp.getVal2(cb).isConstant()) {
      return false;
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
    int shortestCost = Math.min(takenCost,notTakenCost);
    int xformCost = 2 * (takenCost + notTakenCost);
    int k = ir.options.COND_MOVE_CUTOFF;
    if (xformCost - shortestCost > k) return false;

    // Perform the transformation!
    doCondMove(ir, diamond, cb);
    return true;
  }

  /**
   * Is a specified condition operand OK to transfer into an FCMOV
   * instruction?
   */
  private boolean fpConditionOK(OPT_ConditionOperand c) {
    // For now, we never say it's OK to generate an FCMOV. 
    // We don't have support yet in case either operand of the condition
    // is a NaN.
    return false;
  }

  /**
   * Do any of the instructions in a basic block define a floating-point
   * register?
   */
  private boolean hasFloatingPointDef(OPT_BasicBlock bb) {
    if (bb == null) return false;
    for (Enumeration e = bb.forwardRealInstrEnumerator();
         e.hasMoreElements(); ) {
      OPT_Instruction s = (OPT_Instruction)e.nextElement();
      for (Enumeration d = s.getDefs(); d.hasMoreElements(); ) {
        OPT_Operand def = (OPT_Operand)d.nextElement();
        if (def.isRegister()) {
          if (def.asRegister().register.isFloatingPoint()) return true;
        }
      }
    }
    return false;
  }
  /**
   * Do any of the instructions in a basic block define a long
   * register?
   */
  private boolean hasLongDef(OPT_BasicBlock bb) {
     if (bb == null) return false;
     for (Enumeration e = bb.forwardRealInstrEnumerator();
         e.hasMoreElements(); ) {
      OPT_Instruction s = (OPT_Instruction)e.nextElement();
      for (Enumeration d = s.getDefs(); d.hasMoreElements(); ) {
        OPT_Operand def = (OPT_Operand)d.nextElement();
        if (def.isRegister()) {
          if (def.asRegister().register.isLong()) return true;
        }
      }
    }
    return false;
  }
  /**
   * Do any of the instructions in a basic block preclude eliminating the
   * basic block with conditional moves?
   */
  private boolean hasCMTaboo(OPT_BasicBlock bb) {

    if (bb == null) return false;

    // Note: it is taboo to assign more than once to any register in the
    // block.
    HashSet defined = new HashSet();

    for (Enumeration e = bb.forwardRealInstrEnumerator();
         e.hasMoreElements(); ) {
      OPT_Instruction s = (OPT_Instruction)e.nextElement();
      if (s.isBranch()) continue;
      // for now, only the following opcodes are legal.
      switch (s.operator.opcode) {
        case INT_MOVE_opcode:
        case REF_MOVE_opcode:
        case INT_ADD_opcode:
        case REF_ADD_opcode:
        case INT_SUB_opcode:
        case REF_SUB_opcode:
        case INT_MUL_opcode:
        case INT_NEG_opcode:
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
          // these are OK.
          break;
        default:
          return true;
      }
      
      
      // make sure no register is defined more than once in this block.
      for (Enumeration defs = s.getDefs(); defs.hasMoreElements(); ) {
        OPT_Operand def = (OPT_Operand)defs.nextElement();
        if (VM.VerifyAssertions) VM._assert(def.isRegister());
        OPT_Register r = def.asRegister().register;
        if (defined.contains(r)) return true;
        defined.add(r);
      }
    }

    return false;
  }
  /**
   * Evaluate the cost of a basic block, in number of real instructions. 
   */
  private int evaluateCost(OPT_BasicBlock bb) {
    int result = 0;
    for (Enumeration e = bb.forwardRealInstrEnumerator();
         e.hasMoreElements(); ) {
      OPT_Instruction s = (OPT_Instruction)e.nextElement();
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
  private OPT_Instruction[] copyAndMapInstructions(OPT_BasicBlock bb,
                                                   HashMap map) {
    if (bb == null) return new OPT_Instruction[0];
                
    int count = 0;
    // first count the number of instructions
    for (Enumeration e = bb.forwardRealInstrEnumerator();
         e.hasMoreElements(); ) {
      OPT_Instruction s = (OPT_Instruction)e.nextElement();
      if (s.isBranch()) continue;
      count++;
    }
    // now copy.
    OPT_Instruction[] result = new OPT_Instruction[count];
    int i = 0;
    for (Enumeration e = bb.forwardRealInstrEnumerator(); 
         e.hasMoreElements(); ) {
      OPT_Instruction s = (OPT_Instruction)e.nextElement();
      if (s.isBranch()) continue;
      OPT_Instruction sprime = s.copyWithoutLinks();
      result[i++] = sprime;
      map.put(s,sprime);
    }
    return result;
  }

  /**
   * For each in a set of instructions, rewrite every def to use a new
   * temporary register.  If a rewritten def is subsequently used, then
   * use the new temporary register instead.
   */
  private void rewriteWithTemporaries(OPT_Instruction[] set, OPT_IR ir) {

    // Maintain a mapping holding the new name for each register
    HashMap map = new HashMap();
    for (int i=0; i<set.length; i++) {
      OPT_Instruction s = set[i];

      // rewrite the uses to use the new names
      for (Enumeration e = s.getUses(); e.hasMoreElements(); ) {
        OPT_Operand use = (OPT_Operand)e.nextElement();
        if (use != null && use.isRegister()) {
          OPT_Register r = use.asRegister().register;
          OPT_Register temp = (OPT_Register)map.get(r);
          if (temp != null) {
            use.asRegister().register = temp;
          }
        }
      }

      if (VM.VerifyAssertions) VM._assert(s.getNumberOfDefs() == 1);

      OPT_Operand def = (OPT_Operand)s.getDefs().nextElement();
      OPT_RegisterOperand rDef = def.asRegister();
      OPT_RegisterOperand temp = ir.regpool.makeTemp(rDef);
      map.put(rDef.register,temp.register);
      s.replaceOperand(def,temp);
    }
  }

  /**
   * Insert each instruction in a list before instruction s
   */ 
  private void insertBefore(OPT_Instruction[] list, OPT_Instruction s) {
    for (int i=0; i<list.length; i++) {
      OPT_Instruction x = list[i];
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
  private void doCondMove(OPT_IR ir, OPT_Diamond diamond, OPT_Instruction cb) {
    OPT_BasicBlock taken = diamond.getTaken();
    OPT_BasicBlock notTaken = diamond.getNotTaken();

    // for each non-branch instruction s in the diamond, 
    // copy s to a new instruction s'
    // and store a mapping from s to s'
    HashMap takenInstructions = new HashMap();
    OPT_Instruction[] takenInstructionList = copyAndMapInstructions
      (taken, takenInstructions);

    HashMap notTakenInstructions = new HashMap();
    OPT_Instruction[] notTakenInstructionList =
      copyAndMapInstructions(notTaken, notTakenInstructions);
    
    // Extract the values and condition from the conditional branch.
    OPT_Operand val1 = IfCmp.getVal1(cb);
    OPT_Operand val2 = IfCmp.getVal2(cb);
    OPT_ConditionOperand cond = IfCmp.getCond(cb);

    // Copy val1 and val2 to temporaries, just in case they're defined in
    // the diamond.  If they're not defined in the diamond, copy prop
    // should clean these moves up.
    OPT_RegisterOperand tempVal1 = ir.regpool.makeTemp(val1);
    OPT_Operator op = OPT_IRTools.getMoveOp(tempVal1.type);
    cb.insertBefore(Move.create(op,tempVal1.copyRO(),val1.copy()));
    OPT_RegisterOperand tempVal2 = ir.regpool.makeTemp(val2);
    op = OPT_IRTools.getMoveOp(tempVal2.type);
    cb.insertBefore(Move.create(op,tempVal2.copyRO(),val2.copy()));

    // For each instruction in each temporary set, rewrite it to def a new
    // temporary, and insert it before the branch.
    rewriteWithTemporaries(takenInstructionList,ir);
    rewriteWithTemporaries(notTakenInstructionList,ir);
    insertBefore(takenInstructionList,cb);
    insertBefore(notTakenInstructionList,cb);
    
    // For each register defined in the TAKEN branch, save a mapping to
    // the corresponding conditional move.
    HashMap takenMap = new HashMap();
    
    // Now insert conditional moves to replace each instruction in the diamond.
    // First handle the taken branch.
    if (taken != null) {
      for (Enumeration e = taken.forwardRealInstrEnumerator(); 
           e.hasMoreElements();) {
        OPT_Instruction s = (OPT_Instruction)e.nextElement(); 
        if (s.isBranch()) continue;
        OPT_Operand def = (OPT_Operand)s.getDefs().nextElement();
        // if the register does not span a basic block, it is a temporary
        // that will now be dead
        if (def.asRegister().register.spansBasicBlock()) {
          OPT_Instruction tempS = (OPT_Instruction)takenInstructions.get(s);
          OPT_RegisterOperand temp = (OPT_RegisterOperand)
            tempS.getDefs().nextElement();
          op = OPT_IRTools.getCondMoveOp(def.asRegister().type);
          OPT_Instruction cmov = CondMove.create(op,def.asRegister() ,
                                                 tempVal1.copy(),
                                                 tempVal2.copy(),
                                                 cond.copy().asCondition(),
                                                 temp.copy(),
                                                 def.copy());
          takenMap.put(def.asRegister().register,cmov);
          cb.insertBefore(cmov);
        }
        s.remove();
      }
    }
    // For each register defined in the NOT-TAKEN branch, save a mapping to
    // the corresponding conditional move.
    HashMap notTakenMap = new HashMap();
    // Next handle the not taken branch.
    if (notTaken != null) {
      for (Enumeration e = notTaken.forwardRealInstrEnumerator(); 
           e.hasMoreElements();) {
        OPT_Instruction s = (OPT_Instruction)e.nextElement(); 
        if (s.isBranch()) continue;
        OPT_Operand def = (OPT_Operand)s.getDefs().nextElement();
        // if the register does not span a basic block, it is a temporary
        // that will now be dead
        if (def.asRegister().register.spansBasicBlock()) {
          OPT_Instruction tempS = (OPT_Instruction)notTakenInstructions.get(s);
          OPT_RegisterOperand temp = (OPT_RegisterOperand)
            tempS.getDefs().nextElement();

          OPT_Instruction prevCmov = (OPT_Instruction)takenMap.get(def.asRegister
                                                                   ().register);
          if (prevCmov != null) {
            // if this register was also defined in the taken branch, change
            // the previous cmov with a different 'False' Value
            CondMove.setFalseValue(prevCmov, temp.copy());
            notTakenMap.put(def.asRegister().register,prevCmov);
          } else {
            // create a new cmov instruction
            op = OPT_IRTools.getCondMoveOp(def.asRegister().type);
            OPT_Instruction cmov = CondMove.create(op,def.asRegister(),
                                                   tempVal1.copy(),
                                                   tempVal2.copy(),
                                                   cond.copy().asCondition(),
                                                   def.copy(),
                                                   temp.copy());
            cb.insertBefore(cmov);
            notTakenMap.put(def.asRegister().register,cmov);
          }
        }
        s.remove();
      }
    }

    // Mutate the conditional branch into a GOTO.
    OPT_BranchOperand target = diamond.getBottom().makeJumpTarget();
    Goto.mutate(cb,GOTO,target);
    
    // Delete a potential GOTO after cb.
    OPT_Instruction next = cb.nextInstructionInCodeOrder();
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
   * @modified Igor Pechtchanski
   *
   * @param ir governing IR
   * @param bb basic block of cb
   * @param cb conditional branch instruction
   * @return true if the transformation succeeds, false otherwise
   */
  private boolean generateBooleanCompare (OPT_IR ir, 
                                          OPT_BasicBlock bb, 
                                          OPT_Instruction cb, 
                                          OPT_BasicBlock tb) 
  {
    
    if ((cb.operator() != INT_IFCMP) && (cb.operator() != REF_IFCMP))
      return false;
    // make sure this is the last branch in the block
    if (cb.nextInstructionInCodeOrder().operator() != BBEND)
      return false;
    OPT_Operand val1 = IfCmp.getVal1(cb);
    OPT_Operand val2 = IfCmp.getVal2(cb);
    OPT_ConditionOperand condition = IfCmp.getCond(cb);
    // "not taken" path
    OPT_BasicBlock fb = cb.getBasicBlock().getNotTakenNextBlock();
    // make sure it's a diamond
    if (tb.getNumberOfNormalOut() != 1)
      return false;
    if (fb.getNumberOfNormalOut() != 1)
      return false;
    OPT_BasicBlock jb = fb.getNormalOut().next();               // join block
    // make sure it's a diamond
    if (!tb.pointsOut(jb))
      return false;
    OPT_Instruction ti = tb.firstRealInstruction();
    OPT_Instruction fi = fb.firstRealInstruction();
    // make sure the instructions in target blocks are either both moves
    // or both returns
    if (ti == null || fi == null)
      return false;
    if (ti.operator() != fi.operator())
      return false;
    if (ti.operator != RETURN && ti.operator() != INT_MOVE)
      return false;
    //
    // WARNING: This code is currently NOT exercised!
    //
    if (ti.operator() == RETURN) {
      // make sure each of the target blocks contains only one instruction
      if (ti != tb.lastRealInstruction())
        return false;
      if (fi != fb.lastRealInstruction())
        return false;
      OPT_Operand tr = Return.getVal(ti);
      OPT_Operand fr = Return.getVal(fi);
      // make sure we're returning constants
      if (!(tr instanceof OPT_IntConstantOperand) || 
          !(fr instanceof OPT_IntConstantOperand))
        return false;
      int tv = ((OPT_IntConstantOperand)tr).value;
      int fv = ((OPT_IntConstantOperand)fr).value;
      if (!((tv == 1 && fv == 0) || (tv == 1 && fv == 0)))
        return false;
      OPT_RegisterOperand t = ir.regpool.makeTemp(VM_TypeReference.Boolean);
      // Cases 1) and 2)
      if (tv == 0)
        condition = condition.flipCode();
      booleanCompareHelper(cb, t, val1, val2, condition);
      cb.insertAfter(Return.create(RETURN, t.copyD2U()));
    } else {      // (ti.operator() == INT_MOVE)
      // make sure each of the target blocks only does the move
      if (ti != tb.lastRealInstruction() && 
          ti.nextInstructionInCodeOrder().operator()
          != GOTO) return false;
      if (fi != fb.lastRealInstruction() && 
          fi.nextInstructionInCodeOrder().operator() != GOTO)
        return false;
      OPT_RegisterOperand t = Move.getResult(ti);
      // make sure both moves are to the same register
      if (t.register != Move.getResult(fi).register)
        return false;
      OPT_Operand tr = Move.getVal(ti);
      OPT_Operand fr = Move.getVal(fi);
      // make sure we're assigning constants
      if (!(tr instanceof OPT_IntConstantOperand) || 
          !(fr instanceof OPT_IntConstantOperand)) return false;
      int tv = ((OPT_IntConstantOperand)tr).value;
      int fv = ((OPT_IntConstantOperand)fr).value;
      if (!((tv == 1 && fv == 0) || (tv == 0 && fv == 1)))
        return false;
      // Cases 3) and 4)
      if (tv == 0)
        condition = condition.flipCode();
      booleanCompareHelper(cb, t, val1, val2, condition);
      OPT_Instruction next = cb.nextInstructionInCodeOrder();
      if (next.operator() == GOTO)
        Goto.setTarget(next, jb.makeJumpTarget()); 
      else 
        cb.insertAfter(jb.makeGOTO());
    }
    // fixup CFG
    bb.deleteOut(tb);
    bb.deleteOut(fb);
    bb.insertOut(jb);           // Note: if we processed returns, 
                                // jb is the exit node.
    return true;
  }
}
