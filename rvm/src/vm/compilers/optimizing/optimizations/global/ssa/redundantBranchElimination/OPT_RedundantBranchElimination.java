/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;

/**
 * Redundant branch elimination based on SSA form, global value numbers,
 * and dominance relationships. 
 * The key ideas in this pass are:
 * <ul>
 * <li> A conditional branch is redundant if it is dominated by either
 *      the true or false successor of an equivalent conditional branch.
 * <li> For some guards (eg ig_patch_point) if the value being tested is 
 *      the same, then the guard condition is irrelevant.  This is true 
 *      because we apply all code patches for the method when any 
 *      patch criteria for the method is invalidated. Therefore, an 
 *      ig_patch_point establishes a preexistence property 
 *      for that object that allows us to remove other dominated patch_point
 *      guards on the object.
 * </ul>
 * <p>
 *
 * TODO: Extend to a general conditional branch elimination pass by using
 *       GVNs instead of explicitly handling just InlineGuards.
 * 
 * @author Steve Fink
 * @author Dave Grove
 * @author Martin Trapp
 */
class OPT_RedundantBranchElimination extends OPT_CompilerPhase
  implements OPT_Operators {

  private static final boolean DEBUG = false;

  String getName () { return  "Redundant Branch Elimination"; }
  boolean shouldPerform (OPT_Options options) {
    return options.REDUNDANT_BRANCH_ELIMINATION;
  }
  boolean printingEnabled (OPT_Options options, boolean before) {
    return DEBUG;
  }

  /**
   * Do redundant branch elimination as described in class header comment.
   * 
   * @param OPT_IR the IR on which to apply the phase
   */
  void perform (OPT_IR ir) {
    // (1) Establish scalar SSA form (and GVNs -- TODO)
    ir.desiredSSAOptions = new OPT_SSAOptions();
    new OPT_EnterSSA().perform(ir); 
    // new OPT_GlobalValueNumber().perform(ir);

    if (VM.VerifyAssertions) VM.assert(ir.HIRInfo.dominatorsAreComputed);

    // (2) perform the optimization.
    OPT_DominatorTree dt = ir.HIRInfo.dominatorTree;
    for (OPT_BasicBlockEnumeration bbs = ir.getBasicBlocks();
	 bbs.hasMoreElements();) {
      OPT_BasicBlock candBB = bbs.next();
      OPT_Instruction candTest = getCandidateTest(candBB);
      if (candTest == null) continue;
      if (isRedundant(candTest, candBB, dt)) {
	if (DEBUG) VM.sysWrite("Eliminating "+candTest+" from "+candBB+"\n");
	removeCondBranch(candBB, candTest, ir);
      }
    }
  }

  /**
   * Return a candidate test, or null if the block does not contain one.
   */
  private OPT_Instruction getCandidateTest(OPT_BasicBlock bb) {
    for (OPT_InstructionEnumeration e = bb.enumerateBranchInstructions();
	 e.hasMoreElements();) {
      OPT_Instruction s = e.next();
      if (InlineGuard.conforms(s)) return s;
    }
    return null;
  }

  /**
   * Return true if the given candidate test is redundant.
   */
  private boolean isRedundant(OPT_Instruction cand, 
			      OPT_BasicBlock bb, 
			      OPT_DominatorTree dt) {
    if (dt.depth(bb) == 0) return false;
    return isRedundant(cand, bb, dt.getParent(bb), dt);
  }

  /**
   * Return true if the given inline guard is redundant.
   */
  private boolean isRedundant(OPT_Instruction cand, 
			      OPT_BasicBlock from,
			      OPT_BasicBlock curr,
			      OPT_DominatorTree dt) {
    OPT_Instruction ct = getEquivalentTest(curr, cand);
    if (ct != null && ct.getBranchTarget() != from) return true;
    if (dt.depth(curr) == 0) return false;
    return isRedundant(cand, curr, dt.getParent(curr), dt);
  }

  /**
   * Return an equivalent test, or null if the block 
   * does not contain one.
   */
  private OPT_Instruction getEquivalentTest(OPT_BasicBlock bb, 
					    OPT_Instruction goal) {
    for (OPT_InstructionEnumeration e = bb.enumerateBranchInstructions();
	 e.hasMoreElements();) {
      OPT_Instruction equiv = e.next();
      if (InlineGuard.conforms(equiv) && 
	  InlineGuard.getValue(goal).similar(InlineGuard.getValue(equiv))) {
	// Inline guards with same value; check other conditions
	if (equiv.operator() == IG_CLASS_TEST) {
	  if (goal.operator() == IG_CLASS_TEST &&
	      InlineGuard.getGoal(goal).similar(InlineGuard.getGoal(equiv))) {
	    return equiv;
	  }
	} else if (equiv.operator() == IG_METHOD_TEST) {
	  if (goal.operator() == IG_METHOD_TEST && 
	      InlineGuard.getGoal(goal).similar(InlineGuard.getGoal(equiv))) {
	    return equiv;
	  }
	} else if (equiv.operator() == IG_PATCH_POINT) {
	  if (goal.operator() == IG_PATCH_POINT) 
	    return equiv;
	}
      }
    }
    return null;
  }

  /**
   * Remove cb from source, updating PHI nodes to maintain SSA form.
   */
  private void removeCondBranch(OPT_BasicBlock source,
				OPT_Instruction cb,
				OPT_IR ir) {
    OPT_BasicBlock target = cb.getBranchTarget();
    cb.remove();
    source.recomputeNormalOut(ir);
    if (!source.pointsOut(target)) {
      // there is no longer an edge from source to target;
      // update any PHIs in target to reflect this.
      OPT_SSA.purgeBlockFromPHIs(source, target);
      return;
    }
  }
}

