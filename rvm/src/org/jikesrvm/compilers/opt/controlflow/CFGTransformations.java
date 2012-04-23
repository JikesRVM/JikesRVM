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

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.BasicBlockEnumeration;
import org.jikesrvm.compilers.opt.ir.Goto;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.WeightedBranchTargets;
import org.jikesrvm.compilers.opt.util.GraphNodeEnumeration;
import org.jikesrvm.util.BitVector;

/**
 *  This Phase supports
 *  <ul>
 *    <li> transforming while into until loops,
 *    <li>  elimination of critical edges,
 *  </ul>
 */
public class CFGTransformations extends CompilerPhase {

  private static final boolean DEBUG = false;

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

  /**
   * This is the method that actually does the work of the phase.
   */
  @Override
  public void perform(IR ir) {
    staticPerform(ir);
  }

  /**
   * static version of perform
   * @param ir
   */
  static void staticPerform(IR ir) {
    if (ir.hasReachableExceptionHandlers()) return;

    // Note: the following unfactors the CFG.
    DominatorsPhase dom = new DominatorsPhase(true);
    boolean moreToCome = true;
    while (moreToCome) {
      dom.perform(ir);
      moreToCome = turnWhilesIntoUntils(ir);
    }

    ensureLandingPads(ir);

    dom.perform(ir);
    ir.cfg.compactNodeNumbering();
    ir.HIRInfo.dominatorsAreComputed = false; // compacting the node numbering destroys the dominator info
  }

  /**
   * This method determines if the phase should be run, based on the
   * Options object it is passed
   */
  @Override
  public boolean shouldPerform(OptOptions options) {
    if (options.getOptLevel() < 2) {
      return false;
    }
    return options.CONTROL_TURN_WHILES_INTO_UNTILS;
  }

  /**
   * Returns the name of the phase.
   */
  @Override
  public String getName() {
    return "Loop Normalization";
  }

  /**
   * Returns true if the phase wants the IR dumped before and/or after it runs.
   */
  @Override
  public boolean printingEnabled(OptOptions options, boolean before) {
    return false;
  }

  //Implementation
  /**
   * treat all loops of the ir
   */
  private static boolean turnWhilesIntoUntils(IR ir) {
    LSTGraph lstg = ir.HIRInfo.loopStructureTree;
    if (lstg != null) {
      return turnLoopTreeIntoUntils((LSTNode) lstg.firstNode(), ir);
    }
    return false;
  }

  /**
   * deal with a sub tree of the loop structure tree
   */
  private static boolean turnLoopTreeIntoUntils(LSTNode t, IR ir) {
    GraphNodeEnumeration e = t.outNodes();
    while (e.hasMoreElements()) {
      LSTNode n = (LSTNode) e.nextElement();
      if (turnLoopTreeIntoUntils(n, ir)) {
        return true;
      }
      if (turnLoopIntoUntil(n, ir)) {
        return true;
      }
    }
    return false;
  }

  /**
   * treat all loops of the ir
   */
  private static void ensureLandingPads(IR ir) {
    LSTGraph lstg = ir.HIRInfo.loopStructureTree;
    if (lstg != null) {
      ensureLandingPads((LSTNode) lstg.firstNode(), ir);
    }
  }

  /**
   * deal with a sub tree of the loop structure tree
   */
  private static void ensureLandingPads(LSTNode t, IR ir) {
    GraphNodeEnumeration e = t.outNodes();
    while (e.hasMoreElements()) {
      LSTNode n = (LSTNode) e.nextElement();
      ensureLandingPads(n, ir);
      ensureLandingPad(n, ir);
    }
  }

  private static float edgeFrequency(BasicBlock a, BasicBlock b) {
    float prop = 0f;
    WeightedBranchTargets ws = new WeightedBranchTargets(a);
    while (ws.hasMoreElements()) {
      if (ws.curBlock() == b) prop += ws.curWeight();
      ws.advance();
    }
    return a.getExecutionFrequency() * prop;
  }

  private static void ensureLandingPad(LSTNode n, IR ir) {
    BasicBlock[] ps = loopPredecessors(n);
    if (ps.length == 1 && ps[0].getNumberOfOut() == 1) return;

    float frequency = 0f;
    for (BasicBlock bb : ps) {
      frequency += edgeFrequency(bb, n.header);
    }
    BasicBlock newPred;
    newPred = n.header.createSubBlock(n.header.firstInstruction().bcIndex, ir, 1f);
    newPred.setLandingPad();
    newPred.setExecutionFrequency(frequency);

    BasicBlock p = n.header.prevBasicBlockInCodeOrder();
    if (VM.VerifyAssertions) VM._assert(p != null);
    p.killFallThrough();
    ir.cfg.breakCodeOrder(p, n.header);
    ir.cfg.linkInCodeOrder(p, newPred);
    ir.cfg.linkInCodeOrder(newPred, n.header);

    newPred.lastInstruction().insertBefore(Goto.create(GOTO, n.header.makeJumpTarget()));
    newPred.recomputeNormalOut(ir);

    for (BasicBlock bb : ps) {
      bb.redirectOuts(n.header, newPred, ir);
    }
  }

  /**
   * Transform a given loop
   *
   * <p> Look for the set S of in-loop predecessors of the loop header h.
   * Make a copy h' of the loop header and redirect all edges going from
   * nodes in S to h. Make them point to h' instead.
   *
   * <p> As an effect of this transformation, the old header is now not anymore
   * part of the loop, but guards it.
   */
  private static boolean turnLoopIntoUntil(LSTNode n, IR ir) {
    BasicBlock header = n.header;
    BasicBlock newLoopTest = null;

    int i = 0;
    int exiters = 0;

    BasicBlockEnumeration e = ir.getBasicBlocks(n.loop);
    while (e.hasMoreElements()) {
      BasicBlock b = e.next();
      if (!exitsLoop(b, n.loop)) {
        // header doesn't exit: nothing to do
        if (b == n.header) return false;
      } else {
        exiters++;
      }
      i++;
    }
    // all blocks exit: can't improve
    if (i == exiters) return false;

    // rewriting loops where the header has more than one in-loop
    // successor will lead to irreducible control flow.
    BasicBlock[] succ = inLoopSuccessors(n);
    if (succ.length > 1) {
      if (DEBUG) VM.sysWrite("unwhiling would lead to irreducible CFG\n");
      return false;
    }

    BasicBlock[] pred = inLoopPredecessors(n);
    float frequency = 0f;

    if (pred.length > 0) {
      frequency += edgeFrequency(pred[0], header);
      // replicate the header as successor of pred[0]
      BasicBlock p = header.prevBasicBlockInCodeOrder();
      p.killFallThrough();
      newLoopTest = pred[0].replicateThisOut(ir, header, p);
    }
    for (i = 1; i < pred.length; ++i) { // check for aditional back edges
      frequency += edgeFrequency(pred[i], header);
      pred[i].redirectOuts(header, newLoopTest, ir);
    }
    newLoopTest.setExecutionFrequency(frequency);
    header.setExecutionFrequency(header.getExecutionFrequency() - frequency);
    return true;
  }

  /**
   * the predecessors of the loop header that are not part of the loop
   */
  private static BasicBlock[] loopPredecessors(LSTNode n) {
    BasicBlock header = n.header;
    BitVector loop = n.loop;

    int i = 0;
    BasicBlockEnumeration be = header.getIn();
    while (be.hasMoreElements()) if (!inLoop(be.next(), loop)) i++;

    BasicBlock[] res = new BasicBlock[i];

    i = 0;
    be = header.getIn();
    while (be.hasMoreElements()) {
      BasicBlock in = be.nextElement();
      if (!inLoop(in, loop)) res[i++] = in;
    }
    return res;
  }

  /**
   * the predecessors of the loop header that are part of the loop.
   */
  private static BasicBlock[] inLoopPredecessors(LSTNode n) {
    BasicBlock header = n.header;
    BitVector loop = n.loop;

    int i = 0;
    BasicBlockEnumeration be = header.getIn();
    while (be.hasMoreElements()) if (inLoop(be.next(), loop)) i++;

    BasicBlock[] res = new BasicBlock[i];

    i = 0;
    be = header.getIn();
    while (be.hasMoreElements()) {
      BasicBlock in = be.nextElement();
      if (inLoop(in, loop)) res[i++] = in;
    }
    return res;
  }

  /**
   * the successors of the loop header that are part of the loop.
   */
  private static BasicBlock[] inLoopSuccessors(LSTNode n) {
    BasicBlock header = n.header;
    BitVector loop = n.loop;

    int i = 0;
    BasicBlockEnumeration be = header.getOut();
    while (be.hasMoreElements()) if (inLoop(be.next(), loop)) i++;

    BasicBlock[] res = new BasicBlock[i];

    i = 0;
    be = header.getOut();
    while (be.hasMoreElements()) {
      BasicBlock in = be.nextElement();
      if (inLoop(in, loop)) res[i++] = in;
    }
    return res;
  }

  static void killFallThroughs(IR ir, BitVector nloop) {
    BasicBlockEnumeration bs = ir.getBasicBlocks(nloop);
    while (bs.hasMoreElements()) {
      BasicBlock block = bs.next();
      BasicBlockEnumeration bi = block.getIn();
      while (bi.hasMoreElements()) {
        BasicBlock in = bi.next();
        if (inLoop(in, nloop)) continue;
        in.killFallThrough();
      }
      block.killFallThrough();
    }
  }

  static boolean inLoop(BasicBlock b, BitVector nloop) {
    int idx = b.getNumber();
    if (idx >= nloop.length()) return false;
    return nloop.get(idx);
  }

  private static boolean exitsLoop(BasicBlock b, BitVector loop) {
    BasicBlockEnumeration be = b.getOut();
    while (be.hasMoreElements()) {
      if (!inLoop(be.next(), loop)) return true;
    }
    return false;
  }

  /**
   * Critical edge removal: if (a,b) is an edge in the cfg where `a' has more
   * than one out-going edge and `b' has more than one in-coming edge,
   * insert a new empty block `c' on the edge between `a' and `b'.
   *
   * <p> We do this to provide landing pads for loop-invariant code motion.
   * So we split only edges, where `a' has a lower loop nesting depth than `b'.
   */
  public static void splitCriticalEdges(IR ir) {
    BasicBlockEnumeration e = ir.getBasicBlocks();
    while (e.hasMoreElements()) {
      BasicBlock b = e.next();
      int numberOfIns = b.getNumberOfIn();
      //Exception handlers and blocks with less than two inputs
      // are no candidates for `b'.
      if (b.isExceptionHandlerBasicBlock() || numberOfIns <= 1) {
        continue;
      }
      // copy the predecessors, since we will alter the incoming edges.
      BasicBlock[] ins = new BasicBlock[numberOfIns];
      BasicBlockEnumeration ie = b.getIn();
      for (int i = 0; i < numberOfIns; ++i) {
        ins[i] = ie.next();
      }
      // skip blocks, that do not fullfil our requirements for `a'
      for (int i = 0; i < numberOfIns; ++i) {
        BasicBlock a = ins[i];
        if (a.getNumberOfOut() <= 1) {
          continue;
        }
        // insert pads only for moving code up to the start of the method
        //if (a.getExecutionFrequency() >= b.getExecutionFrequency()) continue;

        // create a new block as landing pad
        BasicBlock landingPad;
        Instruction firstInB = b.firstInstruction();
        int bcIndex = firstInB != null ? firstInB.bcIndex : -1;
        landingPad = b.createSubBlock(bcIndex, ir);
        landingPad.setLandingPad();
        landingPad.setExecutionFrequency(edgeFrequency(a, b));

        // make the landing pad jump to `b'
        Instruction g;
        g = Goto.create(GOTO, b.makeJumpTarget());
        landingPad.appendInstruction(g);
        landingPad.recomputeNormalOut(ir);
        // redirect a's outputs from b to the landing pad
        a.redirectOuts(b, landingPad, ir);

        a.killFallThrough();
        BasicBlock aNext = a.nextBasicBlockInCodeOrder();
        if (aNext != null) {
          ir.cfg.breakCodeOrder(a, aNext);
          ir.cfg.linkInCodeOrder(landingPad, aNext);
        }
        ir.cfg.linkInCodeOrder(a, landingPad);
      }
    }
  }
}
