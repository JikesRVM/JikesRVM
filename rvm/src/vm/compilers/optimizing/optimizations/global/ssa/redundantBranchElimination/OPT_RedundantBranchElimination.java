/*
 * (C) Copyright IBM Corp. 2001, 2004
 */
//$Id$
package com.ibm.JikesRVM.opt;
import com.ibm.JikesRVM.*;

import com.ibm.JikesRVM.opt.ir.*;
import java.util.*;

/**
 * Redundant branch elimination based on SSA form, global value numbers,
 * and dominance relationships. 
 * The following are sufficient conditions for a conditional branch cb1
 * to be eliminated as redundant
 * <ul>
 * <li> It is equivalent (has the same value number) as another 
 *      conditional branch cb2
 * <li> Either (a) the target of the taken branch of cb2 dominates cb1 
 *      and said target block has exactly one in edge or (b)
 *      the not-taken continuation of cb2 dominates cb1 and 
 *      said continuation block has exactly one in edge.
 * </ul>
 * NOTE: the check for exactly one in edge is used to rule out
 *       situations like the following:
 * <pre>
 *      if (C) goto L2              // cb2
 *      x = x + 1;
 *  L2: x = x + 1;
 *      if (C) goto L3.            // cb1
 * </pre>
 * Here L2 (the target of cb2) dominates cb1, but it 
 * is not correct to eliminate cb1 because it is also 
 * reachable (but not dominated) from the continutation
 * block of cb2!
 *
 * @author Steve Fink
 * @author Dave Grove
 * @author Martin Trapp
 */
final class OPT_RedundantBranchElimination extends OPT_OptimizationPlanCompositeElement {

  public final boolean shouldPerform (OPT_Options options) {
    return options.REDUNDANT_BRANCH_ELIMINATION;
  }
  
  /**
   * Create this phase element as a composite of other elements
   */
  OPT_RedundantBranchElimination () {
    super("RedundantBranchElimination", new OPT_OptimizationPlanElement[] {
      // Stage 1: Require SSA form
      new OPT_OptimizationPlanAtomicElement(new OPT_CompilerPhase() {
          public String getName() { return "Ensure SSA"; }
          public boolean shouldPerform() { return true; }
          public void perform(OPT_IR ir) {
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
    public final String getName() { return "RBE Transform"; }
    public final boolean printingEnabled (OPT_Options options, boolean before) {
      return false && DEBUG;
    }

    /**
     * Transform to eliminate redundant branches passed on 
     * GVNs and dominator information.
     * 
     * @param ir   The IR on which to apply the phase
     */
    public final void perform (OPT_IR ir) {
      // (1) Remove redundant conditional branches and locally fix the PHIs
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
              OPT_BasicBlock notTaken = getNotTakenBlock(poss);
              OPT_BasicBlock taken = poss.getBranchTarget();
              if (taken == notTaken) continue; // both go to same block, so we don't know anything!
              if (notTaken.hasOneIn() && dt.dominates(notTaken, candBB)) {
                if (DEBUG) VM.sysWrite(candTest + " is dominated by not-taken branch of "+poss+"\n");
                removeCondBranch(candBB, candTest, ir);
                cc.removeVertex(gvns.valueGraph.getVertex(candTest));
                break;
              }
              if (taken.hasOneIn() && dt.dominates(taken, candBB)) {
                if (DEBUG) VM.sysWrite(candTest + " is dominated by taken branch of "+poss+"\n");
                takeCondBranch(candBB, candTest, ir);
                cc.removeVertex(gvns.valueGraph.getVertex(candTest));
                break;
              }
            }
          }
        }
      }
      // (2) We may have created some dead code; remove it
      while (haveBlock()) {
        OPT_BasicBlock curr = popBlock();
        if (curr.scratch != 0) continue; // set in push block; avoid processing block twice!
        if (DEBUG) VM.sysWrite("Checking to see if block "+curr+" is dead\n");
        if (curr.hasZeroIn() ||
            (curr.hasOneIn() && curr.pointsIn(curr))) {
          // block is dead.
          curr.scratch = 0xdeadbeef;
          if (DEBUG) VM.sysWrite("\tremoving dead block "+curr+"\n");
          for (OPT_BasicBlockEnumeration e = curr.getOut();
               e.hasMoreElements(); ) {
            OPT_BasicBlock target = e.next();
            if (target != curr && !target.isExit()) {
              OPT_SSA.purgeBlockFromPHIs(curr, target);
              pushBlock(target);
            }
          }
          ir.cfg.removeFromCFGAndCodeOrder(curr);
        }
      }
    }

    /**
     * Return the basic block that s's block will goto if s is not taken.
     */
    private final OPT_BasicBlock getNotTakenBlock(OPT_Instruction s) {
      s = s.nextInstructionInCodeOrder();
      if (Goto.conforms(s)) return s.getBranchTarget();
      if (VM.VerifyAssertions) VM._assert(s.operator() == BBEND);
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
        pushBlock(deadBB);
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
      OPT_Instruction next = cb.nextInstructionInCodeOrder();
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
        pushBlock(deadBB);
      }
    }


    /*
     * Support for maintaining a work list of possibly unreachable blocks
     */
    private static class BL {
      OPT_BasicBlock block;
      BL next;
      BL(OPT_BasicBlock b, BL n) {
        block = b;
        next = n;
      }
    }
    private BL modBlocks;
    private void pushBlock(OPT_BasicBlock bb) {
      bb.scratch = 0;
      modBlocks = new BL(bb, modBlocks);
    }
    private boolean haveBlock() {
      return modBlocks != null;
    }
    private OPT_BasicBlock popBlock() {
      OPT_BasicBlock tmp = modBlocks.block;
      modBlocks = modBlocks.next;
      return tmp;
    }

  }
}
