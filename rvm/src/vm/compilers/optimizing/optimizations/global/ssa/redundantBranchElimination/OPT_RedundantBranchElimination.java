/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;
import java.util.*;

/**
 * Redundant branch elimination based on SSA form, global value numbers,
 * and dominance relationships. 
 * A conditional branch is redundant if it is dominated by either
 * the true or false successor of an equivalent conditional branch
 * (two conditional branches are equivalent if they have the same 
 *  value number).
 *
 * @author Steve Fink
 * @author Dave Grove
 * @author Martin Trapp
 */
final class OPT_RedundantBranchElimination extends OPT_OptimizationPlanCompositeElement {

  final boolean shouldPerform (OPT_Options options) {
    return options.REDUNDANT_BRANCH_ELIMINATION;
  }
  
  /**
   * Create this phase element as a composite of other elements
   */
  OPT_RedundantBranchElimination () {
    super("RedundantBranchElimination", new OPT_OptimizationPlanElement[] {
      // Stage 1: Require SSA form
      new OPT_OptimizationPlanAtomicElement(new OPT_CompilerPhase() {
	  String getName() { return "Ensure SSA"; }
	  boolean shouldPerform() { return true; }
	  void perform(OPT_IR ir) {
	    ir.desiredSSAOptions = new OPT_SSAOptions();
	    new OPT_EnterSSA().perform(ir);
	  }
	}),
      
      // Stage2: Require GVNs
      new OPT_OptimizationPlanAtomicElement(new OPT_GlobalValueNumber()),

      // Stage3: Do the optimization
      new OPT_OptimizationPlanAtomicElement(new RBE()),
    });
  }


  private static final class RBE extends OPT_CompilerPhase implements OPT_Operators {
    private static final boolean DEBUG = false;
    final String getName() { return "Transform"; }
    final boolean shouldPerform(OPT_Options options) { return true; }
    final boolean printingEnabled (OPT_Options options, boolean before) {
      return DEBUG;
    }

    /**
     * Transform to eliminate redundant branches passed on 
     * GVNs and dominator information.
     * 
     * @param OPT_IR the IR on which to apply the phase
     */
    final void perform (OPT_IR ir) {
      OPT_GlobalValueNumberState gvns = ir.HIRInfo.valueNumbers;
      OPT_DominatorTree dt = ir.HIRInfo.dominatorTree;
      for (OPT_BasicBlockEnumeration bbs = ir.getBasicBlocks();
	   bbs.hasMoreElements();) {
	OPT_BasicBlock candBB = bbs.next();
	OPT_Instruction candTest = candBB.firstBranchInstruction();
	if (candTest == null) continue;
	if (!(IfCmp.conforms(candTest) || InlineGuard.conforms(candTest))) continue;
	OPT_GVCongruenceClass cc = gvns.congruenceClass(candTest);
	if (cc.size() > 1) {
	  for (Iterator e = cc.iterator(); e.hasNext();) {
	    OPT_Instruction poss = (OPT_Instruction)((OPT_ValueGraphVertex)e.next()).name;
	    if (poss != candTest) {
	      if (dt.dominates(getNotTakenBlock(poss), candBB)) {
		if (DEBUG) VM.sysWrite(candTest + " is dominated by not-taken branch of "+poss+"\n");
		removeCondBranch(candBB, candTest, ir);
		cc.removeVertex(gvns.valueGraph.getVertex(candTest));
		break;
	      } else if (dt.dominates(poss.getBranchTarget(), candBB)) {
		if (DEBUG) VM.sysWrite(candTest + " is dominated by taken branch of "+poss+"\n");
		takeCondBranch(candBB, candTest, ir);
		cc.removeVertex(gvns.valueGraph.getVertex(candTest));
		break;
	      }
	    }
	  }
	}
      }
    }

    /**
     * Return the basic block that s's block will goto if s is not taken.
     */
    private final OPT_BasicBlock getNotTakenBlock(OPT_Instruction s) {
      s = s.getNext();
      if (Goto.conforms(s)) return s.getBranchTarget();
      if (VM.VerifyAssertions) VM.assert(s.operator() == BBEND);
      return s.getBasicBlock().nextBasicBlockInCodeOrder();
    }

    /**
     * Remove cb from source, updating PHI nodes to maintain SSA form.
     */
    private void removeCondBranch(OPT_BasicBlock source,
				  OPT_Instruction cb,
				  OPT_IR ir) {
      if (DEBUG) VM.sysWrite("Eliminating definitely not-taken branch "+cb+"\n");
      OPT_BasicBlock deadBB = cb.getBranchTarget();
      cb.remove();
      source.recomputeNormalOut(ir);
      if (!source.pointsOut(deadBB)) {
	// there is no longer an edge from source to target;
	// update any PHIs in target to reflect this.
	OPT_SSA.purgeBlockFromPHIs(source, deadBB);
      }
    }

    /**
     * Transform cb into a GOTO, updating PHI nodes to maintain SSA form.
     */
    private void takeCondBranch(OPT_BasicBlock source,
				OPT_Instruction cb,
				OPT_IR ir) {
      if (DEBUG) VM.sysWrite("Eliminating definitely taken branch "+cb+"\n");
      OPT_BasicBlock  deadBB = source.nextBasicBlockInCodeOrder();
      OPT_Instruction next = cb.getNext();
      if (Goto.conforms(next)) {
	deadBB = next.getBranchTarget();
	next.remove();
      }
      Goto.mutate(cb, GOTO, cb.getBranchTarget().makeJumpTarget());
      source.recomputeNormalOut(ir);
      if (!source.pointsOut(deadBB)) {
	// there is no longer an edge from source to target;
	// update any PHIs in target to reflect this.
	OPT_SSA.purgeBlockFromPHIs(source, deadBB);
      }
    }
  }
}
