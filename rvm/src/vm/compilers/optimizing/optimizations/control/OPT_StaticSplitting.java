/*
 * (C) Copyright IBM Corp. 2001, 2004
 */
//$Id$
package com.ibm.JikesRVM.opt;
import com.ibm.JikesRVM.*;

import com.ibm.JikesRVM.opt.ir.*;

/**
 * Static splitting based on very simple hints left by
 * guarded inlining (off blocks marked as infrequent)
 * and semantic knowledge of tests.
 * The goal of this pass is to create 'common-case' traces.
 * This is done by eliminating merge points where 
 * uncommon-case code merges back into common case code
 * by code duplication. We rely on a later pass to
 * eliminate redundant tests on the common-case trace. 
 * <p>
 * We use semantic knowledge of the tests to reduce the
 * code replicated.  The key idea is that for a guarded 
 * inlining, it is correct to take the 'off' branch even
 * if test would select the on-branch.  Therefore we can
 * avoid replicating the on-branch code downstream of the
 * replicated test, at the possible cost of trapping an
 * execution in the uncommon-case trace that might have 
 * been able to use a subset of to common-case trace.
 * <p>
 * 
 * @author Steve Fink
 * @author Dave Grove
 */
class OPT_StaticSplitting extends OPT_CompilerPhase
  implements OPT_Operators {

  private static final boolean DEBUG = false;
  private static final int MAX_COST = 10; // upper bound on instructions duplicated

  public String getName () { return  "Static Splitting"; }
  public boolean shouldPerform (OPT_Options options) {
    return options.STATIC_SPLITTING;
  }
  public boolean printingEnabled (OPT_Options options, boolean before) {
    return DEBUG;
  }

  /**
   * Do simplistic static splitting to create hot traces
   * with that do not have incoming edges from 
   * blocks that are statically predicted to be cold.
   * 
   * @param ir   The IR on which to apply the phase
   */
  public void perform (OPT_IR ir) {
    // (1) Find candidates to split
    simpleCandidateSearch(ir);

    // (2) Split them
    boolean needCleanup = haveCandidates();
    while (haveCandidates()) {
      splitCandidate(nextCandidate(), ir);
    }
  }

  /**
   * Identify candidate blocks by using a very 
   * simplistic algorithm.
   * <ul>
   * <li> Find all blocks that end in a test that 
   *      is statically known to be likely to 
   *      create a common case trace. For example,
   *      blocks that end in IG_METHOD_TEST, IG_CLASS_TEST
   *      and IG_PATCH_POINT. Note that these tests also
   *      have the property that it is correct
   *      (but less desirable) to execute the off branch
   *      when the test would have selected the on branch.
   * <li> If such a block has a control flow predecessor
   *      that is marked as infrequent, and if the block
   *      is relatively small, then it is almost certainly
   *      profitable to duplicate the block and transfer 
   *      the infrequent predecessor to go to
   *      the cloned block.  This has the effect of freeing 
   *      the common-case path from the pollution of the 
   *      infrequently executed block. Therefore we identify 
   *      the block as a splitting candidate.
   * </ul>
   */
  private void simpleCandidateSearch(OPT_IR ir) {
    for (OPT_BasicBlockEnumeration e = ir.getBasicBlocks();
         e.hasMoreElements();) {
      OPT_BasicBlock cand = e.next();
      if (cand.isExceptionHandlerBasicBlock()) continue;
      OPT_Instruction candTest = getCandidateTest(cand);
      if (candTest == null) continue;
      OPT_BasicBlock coldPrev = findColdPrev(cand);
      if (coldPrev == null) continue;
      if (tooBig(cand)) continue;
      OPT_BasicBlock coldSucc = findColdSucc(cand, candTest);
      if (DEBUG) {
        VM.sysWrite("Found candidate \n");
        VM.sysWrite("\tTest is "+candTest+"\n");
        VM.sysWrite("\tcoldPrev is "+coldPrev+"\n");
        VM.sysWrite("\tcoldSucc is "+coldSucc+"\n");
        cand.printExtended();
      }
      pushCandidate(cand, coldPrev, coldSucc, candTest);
    }
  }

  /**
   * Split a node where we can safely not
   * replicate the on-branch in the cloned node.
   * @param ci description of the split candidate.
   */
  private void splitCandidate(CandInfo ci, OPT_IR ir) {
    OPT_BasicBlock cand = ci.candBB;
    OPT_BasicBlock prev = ci.prevBB;
    OPT_BasicBlock succ = ci.succBB;
    OPT_Instruction test = ci.test;
    OPT_BasicBlock clone = cand.copyWithoutLinks(ir);

    // Redirect clone to always stay on cold path.
    OPT_Instruction s = clone.lastRealInstruction();
    while (s.isBranch()) {
      s = s.remove();
    }
    clone.appendInstruction(Goto.create(GOTO, succ.makeJumpTarget()));

    // inject clone in code order; 
    // force prev to go to clone instead of cand.
    prev.redirectOuts(cand, clone, ir);
    clone.recomputeNormalOut(ir);
    ir.cfg.addLastInCodeOrder(clone);
    clone.setInfrequent();
  }


  /**
   * Return the candidate test in b, or <code>null</code> if 
   * b does not have one.
   */
  private OPT_Instruction getCandidateTest(OPT_BasicBlock bb) {
    OPT_Instruction test = null;
    for (OPT_InstructionEnumeration e = bb.enumerateBranchInstructions();
         e.hasMoreElements();) {
      OPT_Instruction branch = e.next();
      if (InlineGuard.conforms(branch)) {
        if (test != null) return null; // found multiple tests!
        test = branch;
      } else if (branch.operator() != GOTO) {
        return null;
      }
    }
    return test;
  }
  

  /**
   * Return the cold predecessor to the argument block.
   * If there is not exactly 1, return null.
   */
  private OPT_BasicBlock findColdPrev(OPT_BasicBlock bb) {
    OPT_BasicBlock cold = null;
    for (java.util.Enumeration e = bb.getInNodes(); 
         e.hasMoreElements();) {
      OPT_BasicBlock p = (OPT_BasicBlock)e.nextElement();
      if (p.getInfrequent()) {
        if (cold != null) return null;
        cold = p;
      }
    }
    return cold;
  }

  /**
   * Return the off-trace successor of b
   * (on and off relative to the argument test)
   */
  private OPT_BasicBlock findColdSucc(OPT_BasicBlock bb,
                                      OPT_Instruction test) {
    return test.getBranchTarget();
  }

  /**
   * Simplistic cost estimate; since we 
   * are doing the splitting based on 
   * static hints, we are only willing to
   * copy a very small amount of code.
   */
  private boolean tooBig(OPT_BasicBlock bb) { 
    int cost = 0;
    for (OPT_InstructionEnumeration e = bb.forwardRealInstrEnumerator();
         e.hasMoreElements();) {
      OPT_Instruction s = e.next();
      if (s.isCall()) {
        cost += 3;
      } else if (s.isAllocation()) {
        cost += 6;
      } else {
        cost++;
      }
      if (cost > MAX_COST) return true;
    }
    return false;
  }

  /*
   * Support for remembering candidates
   */
  private CandInfo cands;
  private static class CandInfo {
    OPT_BasicBlock candBB;
    OPT_BasicBlock prevBB;
    OPT_BasicBlock succBB;
    OPT_Instruction test;
    CandInfo next;

    CandInfo(OPT_BasicBlock c, OPT_BasicBlock p, 
             OPT_BasicBlock s, OPT_Instruction t, 
             CandInfo n) {
      candBB = c;
      prevBB = p;
      succBB = s;
      test = t;
      next = n;
    }
  }
  private void pushCandidate(OPT_BasicBlock cand,
                             OPT_BasicBlock prev,
                             OPT_BasicBlock succ,
                             OPT_Instruction test) {
    cands = new CandInfo(cand, prev, succ, test, cands);
  }
  private boolean haveCandidates() {
    return cands != null;
  }
  private CandInfo nextCandidate() {
    CandInfo res = cands; 
    cands = cands.next;
    return res;
  }
}
