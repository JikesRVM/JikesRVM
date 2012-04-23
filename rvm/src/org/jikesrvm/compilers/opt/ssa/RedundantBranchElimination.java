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
package org.jikesrvm.compilers.opt.ssa;

import static org.jikesrvm.compilers.opt.ir.Operators.BBEND;
import static org.jikesrvm.compilers.opt.ir.Operators.GOTO;
import static org.jikesrvm.compilers.opt.ir.Operators.GUARD_MOVE;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.controlflow.DominatorTree;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanAtomicElement;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanCompositeElement;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanElement;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.BasicBlockEnumeration;
import org.jikesrvm.compilers.opt.ir.Goto;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.IfCmp;
import org.jikesrvm.compilers.opt.ir.InlineGuard;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Move;

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
 [5~ *      if (C) goto L2              // cb2
 *      x = x + 1;
 *  L2: x = x + 1;
 *      if (C) goto L3.            // cb1
 * </pre>
 * Here L2 (the target of cb2) dominates cb1, but it
 * is not correct to eliminate cb1 because it is also
 * reachable (but not dominated) from the continutation
 * block of cb2!
 */
public final class RedundantBranchElimination extends OptimizationPlanCompositeElement {

  @Override
  public boolean shouldPerform(OptOptions options) {
    return options.SSA_REDUNDANT_BRANCH_ELIMINATION;
  }

  /**
   * Create this phase element as a composite of other elements
   */
  public RedundantBranchElimination() {
    super("RedundantBranchElimination", new OptimizationPlanElement[]{
        // Stage 1: Require SSA form
        new OptimizationPlanAtomicElement(new EnsureSSA()),

        // Stage2: Require GVNs
        new OptimizationPlanAtomicElement(new GlobalValueNumber()),

        // Stage3: Do the optimization
        new OptimizationPlanAtomicElement(new RBE()),});
  }

  private static final class EnsureSSA extends CompilerPhase {

    @Override
    public String getName() {
      return "Ensure SSA";
    }

    public boolean shouldPerform() {
      return true;
    }

    @Override
    public void perform(IR ir) {
      ir.desiredSSAOptions = new SSAOptions();
      new EnterSSA().perform(ir);
    }

    @Override
    public CompilerPhase newExecution(IR ir) {
      return this;
    }
  }

  private static final class RBE extends CompilerPhase {
    private static final boolean DEBUG = false;

    @Override
    public String getName() { return "RBE Transform"; }

    @Override
    public boolean printingEnabled(OptOptions options, boolean before) {
      return false && DEBUG;
    }

    /**
     * Return this instance of this phase. This phase contains
     * no per-compilation instance fields.
     * @param ir not used
     * @return this
     */
    @Override
    public CompilerPhase newExecution(IR ir) {
      return this;
    }

    /**
     * Transform to eliminate redundant branches passed on
     * GVNs and dominator information.
     *
     * @param ir   The IR on which to apply the phase
     */
    @Override
    public void perform(IR ir) {
      // (1) Remove redundant conditional branches and locally fix the PHIs
      GlobalValueNumberState gvns = ir.HIRInfo.valueNumbers;
      DominatorTree dt = ir.HIRInfo.dominatorTree;
      for (BasicBlockEnumeration bbs = ir.getBasicBlocks(); bbs.hasMoreElements();) {
        BasicBlock candBB = bbs.next();
        Instruction candTest = candBB.firstBranchInstruction();
        if (candTest == null) continue;
        if (!(IfCmp.conforms(candTest) || InlineGuard.conforms(candTest))) continue;
        GVCongruenceClass cc = gvns.congruenceClass(candTest);
        if (cc.size() > 1) {
          for (ValueGraphVertex vertex : cc) {
            Instruction poss = (Instruction) vertex.getName();
            if (poss != candTest) {
              BasicBlock notTaken = getNotTakenBlock(poss);
              BasicBlock taken = poss.getBranchTarget();
              if (taken == notTaken) continue; // both go to same block, so we don't know anything!
              if (notTaken.hasOneIn() && dt.dominates(notTaken, candBB)) {
                if (DEBUG) VM.sysWrite(candTest + " is dominated by not-taken branch of " + poss + "\n");
                removeCondBranch(candBB, candTest, ir, poss);
                cc.removeVertex(gvns.valueGraph.getVertex(candTest));
                break;
              }
              if (taken.hasOneIn() && dt.dominates(taken, candBB)) {
                if (DEBUG) VM.sysWrite(candTest + " is dominated by taken branch of " + poss + "\n");
                takeCondBranch(candBB, candTest, ir);
                cc.removeVertex(gvns.valueGraph.getVertex(candTest));
                break;
              }
            }
          }
        }
      }
      // (2) perform a Depth-first search of the control flow graph,
      //     and remove any nodes we have made unreachable
      removeUnreachableCode(ir);
    }

    /**
     * Remove unreachable code
     *
     * @param ir the IR to optimize
     */
    private void removeUnreachableCode(IR ir) {
      boolean removedCode = false;
      BasicBlock entry = ir.cfg.entry();
      ir.cfg.clearDFS();
      entry.sortDFS();
      for (BasicBlock node = entry; node != null;) {
        // save it now before removeFromCFGAndCodeOrder nulls it out!!!
        BasicBlock nextNode = (BasicBlock) node.getNext();
        if (!node.dfsVisited()) {
          for (BasicBlockEnumeration e = node.getOut(); e.hasMoreElements();) {
            BasicBlock target = e.next();
            if (target != node && !target.isExit() && target.dfsVisited()) {
              SSA.purgeBlockFromPHIs(node, target);
            }
          }
          ir.cfg.removeFromCFGAndCodeOrder(node);
          removedCode = true;
        }
        node = nextNode;
      }
      if (removedCode) {
        ir.cfg.compactNodeNumbering();
        ir.HIRInfo.dominatorTree = null;
        ir.HIRInfo.dominatorsAreComputed = false;
      }
    }

    /**
     * Return the basic block that s's block will goto if s is not taken.
     */
    private BasicBlock getNotTakenBlock(Instruction s) {
      s = s.nextInstructionInCodeOrder();
      if (Goto.conforms(s)) return s.getBranchTarget();
      if (VM.VerifyAssertions) VM._assert(s.operator() == BBEND);
      return s.getBasicBlock().nextBasicBlockInCodeOrder();
    }

    /**
     * Remove cb from source, updating PHI nodes to maintain SSA form.
     *
     * @param source basic block containing cb
     * @param cb conditional branch to remove
     * @param ir containing IR
     * @param di branch that dominates cb
     */
    private void removeCondBranch(BasicBlock source, Instruction cb, IR ir, Instruction di) {
      if (DEBUG) VM.sysWrite("Eliminating definitely not-taken branch " + cb + "\n");
      if (IfCmp.conforms(cb) && IfCmp.hasGuardResult(cb)) {
        cb.insertBefore(Move.create(GUARD_MOVE, IfCmp.getGuardResult(cb), IfCmp.getGuardResult(di).copy()));
      }
      BasicBlock deadBB = cb.getBranchTarget();
      cb.remove();
      source.recomputeNormalOut(ir);
      if (!source.pointsOut(deadBB)) {
        // there is no longer an edge from source to target;
        // update any PHIs in target to reflect this.
        SSA.purgeBlockFromPHIs(source, deadBB);
      }
    }

    /**
     * Transform cb into a GOTO, updating PHI nodes to maintain SSA form.
     */
    private void takeCondBranch(BasicBlock source, Instruction cb, IR ir) {
      if (DEBUG) VM.sysWrite("Eliminating definitely taken branch " + cb + "\n");
      BasicBlock deadBB = source.nextBasicBlockInCodeOrder();
      Instruction next = cb.nextInstructionInCodeOrder();
      if (Goto.conforms(next)) {
        deadBB = next.getBranchTarget();
        next.remove();
      }
      Goto.mutate(cb, GOTO, cb.getBranchTarget().makeJumpTarget());
      source.recomputeNormalOut(ir);
      if (!source.pointsOut(deadBB)) {
        // there is no longer an edge from source to target;
        // update any PHIs in target to reflect this.
        SSA.purgeBlockFromPHIs(source, deadBB);
      }
    }
  }
}
