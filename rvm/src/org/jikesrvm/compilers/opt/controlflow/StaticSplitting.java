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

import static org.jikesrvm.compilers.opt.ir.Operators.GOTO;
import static org.jikesrvm.compilers.opt.ir.Operators.YIELDPOINT_OSR;

import java.util.Enumeration;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.Goto;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.InlineGuard;
import org.jikesrvm.compilers.opt.ir.Instruction;

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
 */
public class StaticSplitting extends CompilerPhase {

  private static final boolean DEBUG = false;
  private final BranchOptimizations branchOpts;

  public StaticSplitting() {
    branchOpts = new BranchOptimizations(-1, false, false);
  }

  /**
   * Return this instance of this phase. This phase contains no
   * per-compilation instance fields.
   * @param ir not used
   * @return this
   */
  @Override
  public CompilerPhase newExecution(IR ir) {
    return this;
  }

  @Override
  public String getName() { return "Static Splitting"; }

  @Override
  public boolean shouldPerform(OptOptions options) {
    return options.CONTROL_STATIC_SPLITTING;
  }

  @Override
  public boolean printingEnabled(OptOptions options, boolean before) {
    return DEBUG;
  }

  /**
   * Do simplistic static splitting to create hot traces
   * with that do not have incoming edges from
   * blocks that are statically predicted to be cold.
   *
   * @param ir   The IR on which to apply the phase
   */
  @Override
  public void perform(IR ir) {
    // (1) Find candidates to split
    simpleCandidateSearch(ir);

    // (2) Split them
    boolean needCleanup = haveCandidates();
    while (haveCandidates()) {
      splitCandidate(nextCandidate(), ir);
    }

    // (3) If something was split optimize the CFG
    if (needCleanup) {
      branchOpts.perform(ir);
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
  private void simpleCandidateSearch(IR ir) {
    for (Enumeration<BasicBlock> e = ir.getBasicBlocks(); e.hasMoreElements();) {
      BasicBlock cand = e.nextElement();
      if (cand.isExceptionHandlerBasicBlock()) continue;
      Instruction candTest = getCandidateTest(cand);
      if (candTest == null) continue;
      BasicBlock coldPrev = findColdPrev(cand);
      if (coldPrev == null) continue;
      if (tooBig(cand, ir.options.CONTROL_STATIC_SPLITTING_MAX_COST)) continue;
      BasicBlock coldSucc = findColdSucc(cand, candTest);
      if (containsOSRPoint(coldSucc)) continue;
      if (DEBUG) {
        VM.sysWrite("Found candidate \n");
        VM.sysWrite("\tTest is " + candTest + "\n");
        VM.sysWrite("\tcoldPrev is " + coldPrev + "\n");
        VM.sysWrite("\tcoldSucc is " + coldSucc + "\n");
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
  private void splitCandidate(CandInfo ci, IR ir) {
    BasicBlock cand = ci.candBB;
    BasicBlock prev = ci.prevBB;
    BasicBlock succ = ci.succBB;
    BasicBlock clone = cand.copyWithoutLinks(ir);

    // Redirect clone to always stay on cold path.
    Instruction s = clone.lastRealInstruction();
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
  private Instruction getCandidateTest(BasicBlock bb) {
    Instruction test = null;
    for (Enumeration<Instruction> e = bb.enumerateBranchInstructions(); e.hasMoreElements();) {
      Instruction branch = e.nextElement();
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
   * If there is not exactly 1, return {@code null}.
   */
  private BasicBlock findColdPrev(BasicBlock bb) {
    BasicBlock cold = null;
    for (java.util.Enumeration<BasicBlock> e = bb.getInNodes(); e.hasMoreElements();) {
      BasicBlock p = e.nextElement();
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
  private BasicBlock findColdSucc(BasicBlock bb, Instruction test) {
    return test.getBranchTarget();
  }

  /**
   * Simplistic cost estimate; since we
   * are doing the splitting based on
   * static hints, we are only willing to
   * copy a very small amount of code.
   */
  private boolean tooBig(BasicBlock bb, int maxCost) {
    int cost = 0;
    for (Enumeration<Instruction> e = bb.forwardRealInstrEnumerator(); e.hasMoreElements();) {
      Instruction s = e.nextElement();
      if (s.isCall()) {
        cost += 3;
      } else if (s.isAllocation()) {
        cost += 6;
      } else {
        cost++;
      }
      if (cost > maxCost) return true;
    }
    return false;
  }

  private boolean containsOSRPoint(BasicBlock bb) {
    for (Enumeration<Instruction> e = bb.forwardRealInstrEnumerator(); e.hasMoreElements();) {
      Instruction s = e.nextElement();
      if (s.operator() == YIELDPOINT_OSR) {
        return true;
      }
    }
    return false;
  }

  /*
   * Support for remembering candidates
   */
  private CandInfo cands;

  private static final class CandInfo {
    final BasicBlock candBB;
    final BasicBlock prevBB;
    final BasicBlock succBB;
    final Instruction test;
    final CandInfo next;

    CandInfo(BasicBlock c, BasicBlock p, BasicBlock s, Instruction t, CandInfo n) {
      candBB = c;
      prevBB = p;
      succBB = s;
      test = t;
      next = n;
    }
  }

  private void pushCandidate(BasicBlock cand, BasicBlock prev, BasicBlock succ, Instruction test) {
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
