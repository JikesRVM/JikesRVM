/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;
import com.ibm.JikesRVM.*;

import  java.util.*;
import com.ibm.JikesRVM.opt.ir.*;

/**
 *  This Phase supports
 *  <ul>
 *    <li> transforming while into until loops,
 *    <li>  elimination of critical edges,
 *  </ul>
 *
 * @author Martin Trapp
 */
class OPT_CFGTransformations extends OPT_CompilerPhase
  implements OPT_Operators {

  private static boolean DEBUG = false;

  /**
   * This is the method that actually does the work of the phase.
   */
  public void perform(OPT_IR ir) {
    staticPerform(ir);
  }

  /**
   * static version of perform
   * @param ir
   */
  static void staticPerform(OPT_IR ir) {
    if (ir.hasReachableExceptionHandlers()) return;

    // Note: the following unfactors the CFG.
    OPT_DominatorsPhase dom = new OPT_DominatorsPhase(true);
    boolean moreToCome = true;
    while (moreToCome) {
      dom.perform(ir);
      moreToCome = turnWhilesIntoUntils(ir);
    }

    ensureLandingPads (ir);
    
    dom.perform(ir);
    ir.cfg.compactNodeNumbering();
  }

  /**
   * This method determines if the phase should be run, based on the
   * OPT_Options object it is passed
   */
  public boolean shouldPerform(OPT_Options options) {
    if (options.getOptLevel() < 2)
      return  false;
    return  options.TURN_WHILES_INTO_UNTILS;
  }

  /**O
   * Returns the name of the phase.
   */
  public String getName() {
    return "Loop Normalization";
  }

  /**
   * Returns true if the phase wants the IR dumped before and/or after it runs.
   */
  public boolean printingEnabled(OPT_Options options, boolean before) {
    return  false;
  }

  //Implementation
  /**
   * treat all loops of the ir
   */
  private static boolean turnWhilesIntoUntils(OPT_IR ir) {
    OPT_LSTGraph lstg = ir.HIRInfo.LoopStructureTree;
    if (lstg != null)
      return  turnLoopTreeIntoUntils((OPT_LSTNode)lstg.firstNode(), ir);
    return  false;
  }

  /**
   * deal with a sub tree of the loop structure tree
   */
  private static boolean turnLoopTreeIntoUntils(OPT_LSTNode t, OPT_IR ir) {
    Enumeration e = t.outNodes();
    while (e.hasMoreElements()) {
      OPT_LSTNode n = (OPT_LSTNode)e.nextElement();
      if (turnLoopTreeIntoUntils(n, ir))
        return  true;
      if (turnLoopIntoUntil(n, ir))
        return  true;
    }
    return  false;
  }

  /**
   * treat all loops of the ir
   */
  private static void ensureLandingPads (OPT_IR ir) {
    OPT_LSTGraph lstg = ir.HIRInfo.LoopStructureTree;
    if (lstg != null)
      ensureLandingPads ((OPT_LSTNode)lstg.firstNode(), ir);
  }

  /**
   * deal with a sub tree of the loop structure tree
   */
  private static void ensureLandingPads (OPT_LSTNode t, OPT_IR ir) {
    Enumeration e = t.outNodes();
    while (e.hasMoreElements()) {
      OPT_LSTNode n = (OPT_LSTNode)e.nextElement();
      ensureLandingPads(n, ir);
      ensureLandingPad (n, ir);
    }
  }


  private static float edgeFrequency (OPT_BasicBlock a, OPT_BasicBlock b)
  {
    float prop = 0f;
    OPT_WeightedBranchTargets ws = new OPT_WeightedBranchTargets (a);
    while (ws.hasMoreElements()) {
      if (ws.curBlock() == b) prop += ws.curWeight();
      ws.advance();
    }
    return a.getExecutionFrequency() * prop;
  }

  
  /**
   *
   */
  private static void ensureLandingPad (OPT_LSTNode n, OPT_IR ir)
  {
    OPT_BasicBlock[] ps = loopPredecessors (n);
    if (ps.length == 1 && ps[0].getNumberOfOut() == 1) return;

    float frequency = 0f;
    for (int i = 0;  i < ps.length;  ++i) {
      OPT_BasicBlock p = ps[i];
      frequency += edgeFrequency (p, n.header);
    }
    OPT_BasicBlock newPred;
    newPred = n.header.createSubBlock (n.header.firstInstruction().bcIndex,
                                       ir, 1f);
    newPred.setLandingPad();
    newPred.setExecutionFrequency (frequency);

    OPT_BasicBlock p = n.header.prevBasicBlockInCodeOrder();
    if (VM.VerifyAssertions) VM._assert (p != null);
    p.killFallThrough();
    ir.cfg.breakCodeOrder (p, n.header);
    ir.cfg.linkInCodeOrder (p, newPred);
    ir.cfg.linkInCodeOrder (newPred, n.header);
    
    newPred.lastInstruction().insertBefore
      (Goto.create(GOTO, n.header.makeJumpTarget()));
    newPred.recomputeNormalOut(ir);

    for (int i = 0;  i < ps.length;  ++i) {
      ps[i].redirectOuts (n.header, newPred, ir);
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
  private static boolean turnLoopIntoUntil(OPT_LSTNode n, OPT_IR ir) {
    OPT_BitVector nloop = n.loop;
    OPT_BasicBlock header = n.header;
    OPT_BasicBlock newLoopTest = null;

    int i = 0;
    int exiters = 0;
    
    OPT_BasicBlockEnumeration e = ir.getBasicBlocks (n.loop);
    while (e.hasMoreElements()) {
      OPT_BasicBlock b = e.next();
      if (!exitsLoop (b, n.loop)) {
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
    OPT_BasicBlock succ[] = inLoopSuccessors (n);
    if (succ.length > 1) {
      if (DEBUG) VM.sysWrite ("unwhiling would lead to irreducible CFG\n");
      return false;
    }
    
    OPT_BasicBlock pred[] = inLoopPredecessors (n);
    float frequency = 0f;

    if (pred.length > 0) {
      frequency += edgeFrequency (pred[0], header);
      // replicate the header as successor of pred[0]
      OPT_BasicBlock p = header.prevBasicBlockInCodeOrder();
      p.killFallThrough();
      newLoopTest = pred[0].replicateThisOut (ir, header, p);
    }
    for (i = 1;  i < pred.length;  ++i) { // check for aditional back edges
      frequency += edgeFrequency (pred[i], header);
      pred[i].redirectOuts(header, newLoopTest, ir);
    }
    newLoopTest.setExecutionFrequency (frequency);
    header.setExecutionFrequency (header.getExecutionFrequency() - frequency);
    return  true;
  }

  /**
   * the predecessors of the loop header that are not part of the loop
   */
  private static OPT_BasicBlock[] loopPredecessors(OPT_LSTNode n) {
    OPT_BasicBlock header = n.header;
    OPT_BitVector loop = n.loop;
    
    int i = 0;
    OPT_BasicBlockEnumeration be = header.getIn();
    while (be.hasMoreElements()) if (!inLoop (be.next(), loop)) i++;

    OPT_BasicBlock res[] = new OPT_BasicBlock[i];

    i = 0;
    be = header.getIn();
    while (be.hasMoreElements()) {
      OPT_BasicBlock in = (OPT_BasicBlock)be.nextElement();
      if (!inLoop(in, loop)) res[i++] = in;
    }
    return  res;
  }

  /**
   * the predecessors of the loop header that are part of the loop.
   */
  private static OPT_BasicBlock[] inLoopPredecessors(OPT_LSTNode n) {
    OPT_BasicBlock header = n.header;
    OPT_BitVector loop = n.loop;

    int i = 0;
    OPT_BasicBlockEnumeration be = header.getIn();
    while (be.hasMoreElements()) if (inLoop (be.next(), loop)) i++;

    OPT_BasicBlock res[] = new OPT_BasicBlock[i];
    
    i = 0;
    be = header.getIn();
    while (be.hasMoreElements()) {
      OPT_BasicBlock in = (OPT_BasicBlock)be.nextElement();
      if (inLoop(in, loop)) res[i++] = in;
    }
    return  res;
  }
  /**
   * the successors of the loop header that are part of the loop.
   */
  private static OPT_BasicBlock[] inLoopSuccessors (OPT_LSTNode n) {
    OPT_BasicBlock header = n.header;
    OPT_BitVector loop = n.loop;

    int i = 0;
    OPT_BasicBlockEnumeration be = header.getOut();
    while (be.hasMoreElements()) if (inLoop (be.next(), loop)) i++;

    OPT_BasicBlock res[] = new OPT_BasicBlock[i];
    
    i = 0;
    be = header.getOut();
    while (be.hasMoreElements()) {
      OPT_BasicBlock in = (OPT_BasicBlock)be.nextElement();
      if (inLoop(in, loop)) res[i++] = in;
    }
    return  res;
  }

  static void killFallThroughs(OPT_IR ir, OPT_BitVector nloop) {
    OPT_BasicBlockEnumeration bs = ir.getBasicBlocks (nloop);
    while (bs.hasMoreElements()) {
      OPT_BasicBlock block = bs.next();
      OPT_BasicBlockEnumeration bi = block.getIn();
      while (bi.hasMoreElements()) {
        OPT_BasicBlock in = bi.next();
        if (inLoop (in, nloop)) continue;
        in.killFallThrough();
      }
      block.killFallThrough();
    }
  }

  static boolean inLoop(OPT_BasicBlock b, OPT_BitVector nloop) {
    int idx = b.getNumber();
    if (idx >= nloop.length()) return false;
    return nloop.get(idx);
  }
  
  static private boolean exitsLoop (OPT_BasicBlock b, OPT_BitVector loop)
  {
    OPT_BasicBlockEnumeration be = b.getOut();
    while (be.hasMoreElements()) {
      if (!inLoop (be.next(), loop)) return true;
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
  public static void splitCriticalEdges(OPT_IR ir) {
    OPT_BasicBlockEnumeration e = ir.getBasicBlocks();
    while (e.hasMoreElements()) {
      OPT_BasicBlock b = e.next();
      int numberOfIns = b.getNumberOfIn();
      //Exception handlers and blocks with less than two inputs
      // are no candidates for `b'.
      if (b.isExceptionHandlerBasicBlock() || numberOfIns <= 1)
        continue;
      // copy the predecessors, since we will alter the incoming edges.
      OPT_BasicBlock ins[] = new OPT_BasicBlock[numberOfIns];
      OPT_BasicBlockEnumeration ie = b.getIn();
      for (int i = 0; i < numberOfIns; ++i)
        ins[i] = ie.next();
      // skip blocks, that do not fullfil our requirements for `a'
      for (int i = 0; i < numberOfIns; ++i) {
        OPT_BasicBlock a = ins[i];
        if (a.getNumberOfOut() <= 1)
          continue;
        // insert pads only for moving code up to the start of the method
        //if (a.getExecutionFrequency() >= b.getExecutionFrequency()) continue;

        // create a new block as landing pad
        OPT_BasicBlock landingPad;
        OPT_Instruction firstInB = b.firstInstruction();
        int bcIndex = firstInB != null ? firstInB.bcIndex : -1;
        landingPad = b.createSubBlock(bcIndex, ir);
        landingPad.setLandingPad();
        landingPad.setExecutionFrequency (edgeFrequency (a, b));
        
        // make the landing pad jump to `b'
        OPT_Instruction g;
        g = Goto.create(GOTO, b.makeJumpTarget());
        landingPad.appendInstruction(g);
        landingPad.recomputeNormalOut(ir);
        // redirect a's outputs from b to the landing pad
        a.redirectOuts(b, landingPad,ir);

        a.killFallThrough ();
        OPT_BasicBlock aNext = a.nextBasicBlockInCodeOrder();
        if (aNext != null) {
          ir.cfg.breakCodeOrder(a, aNext);
          ir.cfg.linkInCodeOrder(landingPad, aNext);
        }
        ir.cfg.linkInCodeOrder(a, landingPad);
      }
    }
  }
}
