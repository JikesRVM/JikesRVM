/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;

/**
 * Perform simple peephole optimizations for branches.
 * 
 * @author Stephen Fink
 * @author Dave Grove
 * @author Mauricio Serrano
 */
public final class OPT_BranchOptimizations
  extends OPT_BranchOptimizationDriver {

  private OPT_BranchOptimizations () { }

  /** 
   * @param level the minimum optimization level at which the branch 
   * optimizations should be performed.
   */
  OPT_BranchOptimizations (int level) {
    super(level);
  }

  /** 
   * @param level the minimum optimization level at which the branch 
   * optimizations should be performed.
   */
  OPT_BranchOptimizations (int level, boolean restrictCondBranchOpts) {
    super(level,restrictCondBranchOpts);
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
   *	1)	GOTO A	     replaced by  GOTO B
   *	     A: GOTO B
   *
   *    2) 	GOTO A	     replaced by  IF .. GOTO B
   *	     A: IF .. GOTO B 		  GOTO C
   *	     C: ...			
   *    3)   GOTO next instruction eliminated
   *    4)      GOTO A	     replaced by  GOTO B
   *	     A: LABEL	     
   *		BBEND
   *	     B: 
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
    if (!_restrictCondBranchOpts &&
	IfCmp.conforms(targetInst)) {
      // unconditional branch to a conditional branch.
      // If the Goto is the only branch instruction in its basic block
      // and the IfCmp is the only non-GOTO branch instruction
      // in its basic block then replace the goto with a copy of 
      // targetInst and append another GOTO to the not-taken
      // target of targetInst's block.
      // We impose these additional restrictions to avoid getting 
      // multiple conditional branches in a single basic block.
      // The LIR does allow this, but some MIR instruction sets don't deal
      // with it very well (without multiple condition registers, one is
      // forced to either violate the definition of a basic block or break
      // the instructions into multiple basic blocks anyways, which requires
      // care to avoid separating the actual conditional branch from the
      // computation of its input values). 
      // Also, HIR to LIR translation doesn't handle multiple conditional
      // branches in a single basic block correctly either.
      if (!g.prevInstructionInCodeOrder().isBranch() && 
	  (targetInst.nextInstructionInCodeOrder().operator == BBEND ||
	   targetInst.nextInstructionInCodeOrder().operator == GOTO)) {
	OPT_Instruction copy = targetInst.copyWithoutLinks();
	g.replace(copy);
	OPT_Instruction newGoto = 
	  getNotTakenNextBlock(targetInst.getBasicBlock()).makeGOTO();
	copy.insertAfter(newGoto);
	bb.recomputeNormalOut(ir); // fix the CFG 
	return true;
      }
    }
    return false;
  }


  /**
   * Perform optimizations for a conditional branch.  
   *
   * <pre>
   * 1)   IF .. GOTO A	        replaced by  IF .. GOTO B
   *      ...
   *   A: GOTO B
   * 2)   conditional branch to next instruction eliminated
   * 3)   IF (condition) GOTO A  replaced by  IF (!condition) GOTO B
   *	  GOTO B			   A: ...
   *   A: ...
   * 4) special case to generate Boolean compare opcode
   * 5)   IF .. GOTO A	     replaced by  IF .. GOTO B
   *   A: LABEL	     
   *   	  BBEND
   *   B: 
   * </pre>
   * 6)  fallthrough to a goto: replicate goto to enable other optimizations.
   *
   * <p> Precondition: IfCmp.conforms(cb)
   *
   * @param ir the governing IR
   * @param cb the instruction to optimize
   * @param bb the basic block holding if
   * @returns true iff made a transformation
   */
  private boolean processConditionalBranch(OPT_IR ir, 
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
    boolean endsBlock = cb.getNext().operator() == BBEND;
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
    if (Goto.conforms(targetInst)) {
      // conditional branch to unconditional branch.
      // change conditional branch target to latter's target
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
   * Perform optimizations for a two way conditional branch.  
   *
   * <pre>
   * 1)   IF .. GOTO A	        replaced by  IF .. GOTO B
   *      ...
   *   A: GOTO B
   * 2)   conditional branch to next instruction eliminated
   * 3)   IF .. GOTO A	     replaced by  IF .. GOTO B
   *   A: LABEL	     
   *   	  BBEND
   *   B: 
   * 4)  fallthrough to a goto: replicate goto to enable other optimizations.
   * </pre>
   *
   * <p> Precondition: IfCmp2.conforms(cb)
   *
   * @param ir the governing IR
   * @param cb the instruction to optimize
   * @param bb the basic block holding if
   * @returns true iff made a transformation
   */
  private boolean processTwoTargetConditionalBranch(OPT_IR ir, 
						    OPT_Instruction cb, 
						    OPT_BasicBlock bb) {
    // First condition/target
    OPT_Instruction target1Label = IfCmp2.getTarget1(cb).target; 
    OPT_Instruction target1Inst = firstRealInstructionFollowing(target1Label);
    OPT_Instruction nextLabel = firstLabelFollowing(cb);
    boolean endsBlock = cb.getNext().operator() == BBEND;
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
	if (VM.VerifyAssertions) VM.assert(cb.operator() == INT_IFCMP2);
	IfCmp.mutate(cb, INT_IFCMP,
		     IfCmp2.getGuardResult(cb), IfCmp2.getVal1(cb),
		     IfCmp2.getVal1(cb), IfCmp2.getCond1(cb), 
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
   *		   branch
   * @returns boolean result
   */
  private boolean isFlipCandidate (OPT_Instruction cb, 
      OPT_Instruction target) {
    // condition 1: is next instruction a GOTO?
    OPT_Instruction next = cb.nextInstructionInCodeOrder();
    if (!Goto.conforms(next))
      return false;
    // condition 2: is the target of the conditional branch the 
    //	next instruction after the GOTO?
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
        ((OPT_RegisterOperand)val1).type == VM_Type.BooleanType && 
	(val2 instanceof OPT_IntConstantOperand)) {
      int value = ((OPT_IntConstantOperand)val2).value;
      if (VM.VerifyAssertions && (value != 0) && (value != 1))
        throw  new OPT_OptimizingCompilerException("Invalid boolean value");
      if (cond.evaluate(value, 0)) {
        Unary.mutate(cb, BOOLEAN_NOT, res, val1);
      } else {
        Move.mutate(cb, INT_MOVE, res, val1);
      }
    } else {
      BooleanCmp.mutate(cb, BOOLEAN_CMP, res, val1, val2, cond,
			new OPT_BranchProfileOperand());
    }
  }

  /**
   * Attempt to generate a boolean compare opcode from a conditional branch
   *
   * <pre>
   * 1)   IF .. GOTO A	        replaced by  BOOLEAN_CMP x=..
   *      x = 0
   *      GOTO B
   *   A: x = 1
   *   B: ...
   * </pre>
   *
   * <p> Precondition: IfCmp.conforms(cb)
   *
   * @modified Igor Pechtchanski
   *
   * @param ir governing IR
   * @param bb basic block of cb
   * @param cb conditional branch instruction
   * @returns true if the transformation succeeds, false otherwise
   */
  private boolean generateBooleanCompare (OPT_IR ir, 
					  OPT_BasicBlock bb, 
					  OPT_Instruction cb, 
					  OPT_BasicBlock tb) {
    if (cb.operator() != INT_IFCMP)
      return false;
    // make sure this is the last branch in the block
    if (cb.getNext().operator() != BBEND)
      return false;
    OPT_Operand val1 = IfCmp.getVal1(cb);
    OPT_Operand val2 = IfCmp.getVal2(cb);
    OPT_ConditionOperand condition = IfCmp.getCond(cb);
    // "not taken" path
    OPT_BasicBlock fb = getNotTakenNextBlock(cb.getBasicBlock());
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
      OPT_RegisterOperand t = ir.regpool.makeTemp(VM_Type.BooleanType);
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
  /*
   * Case 1)     bb.-----------.              bb.-----------------------.
   *               | if(v1~v2) |                | BOOLEAN_CMP x=v1,v2,~ |
   *               | goto?     |                | return x              |
   *               `-,-------.-'                `-----------.-----------'
   *       ---.    T/         \F    ,---      ---.          |          ,---
   *           `v  v           v  v'              `v        |        v'
   *       tb.---------.   .----------.fb    tb.----------. | .----------.fb
   *        | return 1 |   | return 0 |   =>   | return 1 | | | return 0 |
   *        `------.---'   `---,------'        `------.---' | `---,------'
   *                \         /                        \    |    /
   *                 v       v                          v   v   v
   *           exit.-----------.                  exit.-----------.
   *               |           |                      |           |
   *               `-----------'                      `-----------'
   *
   * Case 2)     bb.-----------.              bb.------------------------.
   *               | if(v1~v2) |                | BOOLEAN_CMP x=v1,v2,!~ |
   *               | goto?     |                | return x               |
   *               `-,-------.-'                `-----------.------------'
   *       ---.    T/         \F    ,---      ---.          |          ,---
   *           `v  v           v  v'              `v        |        v'
   *       tb.---------.   .----------.fb    tb.----------. | .----------.fb
   *        | return 0 |   | return 1 |   =>   | return 0 | | | return 1 |
   *        `------.---'   `---,------'        `------.---' | `---,------'
   *                \         /                        \    |    /
   *                 v       v                          v   v   v
   *           exit.-----------.                  exit.-----------.
   *               |           |                      |           |
   *               `-----------'                      `-----------'
   *
   * Case 3)     bb.-----------.              bb.-----------------------.
   *               | if(v1~v2) |                | BOOLEAN_CMP x=v1,v2,~ |
   *               | goto?     |                | goto                  |
   *               `-,-------.-'                `-----------.-----------'
   *       ---.    T/         \F    ,---      ---.          |          ,---
   *           `v  v           v  v'              `v        |        v'
   *         tb.-------.   .-------.fb          tb.-------. | .-------.fb
   *           | x = 1 |   | x = 0 |      =>      | x = 1 | | | x = 0 |
   *           | goto? |   | goto? |              | goto? | | | goto? |
   *           `---.---'   `---,---'              `---.---' | `---,---'
   *                \         /                        \    |    /
   *                 v       v                          v   v   v
   *             jb.-----------.                    jb.-----------.
   *               |           |                      |           |
   *               `-----------'                      `-----------'
   *
   * Case 4)     bb.-----------.              bb.------------------------.
   *               | if(v1~v2) |                | BOOLEAN_CMP x=v1,v2,!~ |
   *               | goto?     |                | goto                   |
   *               `-,-------.-'                `-----------.------------'
   *       ---.    T/         \F    ,---      ---.          |          ,---
   *           `v  v           v  v'              `v        |        v'
   *         tb.-------.   .-------.fb          tb.-------. | .-------.fb
   *           | x = 0 |   | x = 1 |      =>      | x = 0 | | | x = 1 |
   *           | goto? |   | goto? |              | goto? | | | goto? |
   *           `---.---'   `---,---'              `---.---' | `---,---'
   *                \         /                        \    |    /
   *                 v       v                          v   v   v
   *             jb.-----------.                    jb.-----------.
   *               |           |                      |           |
   *               `-----------'                      `-----------'
   */
}
