/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import  java.util.*;
import instructionFormats.*;

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

  private static boolean changed = false;
  private static boolean DEBUG = true;

  // gack
  private static OPT_BranchOptimizations branchOpts = new OPT_BranchOptimizations(-1);

  /**
   * This is the method that actually does the work of the phase.
   */
  void perform (OPT_IR ir) {
    staticPerform(ir);
  }

  /**
   * static version of perform
   * @param ir
   */
  static void staticPerform (OPT_IR ir) {

    if (ir.hasReachableExceptionHandlers()) return;

    // Note: the following unfactors the CFG.
    OPT_DominatorsPhase dom = new OPT_DominatorsPhase(true);

    for (;;) {
      dom.perform(ir);
      if (!turnWhilesIntoUntils(ir))
        break;
    }
    
    branchOpts.perform(ir, false);
    dom.perform(ir);
    splitCriticalEdges(ir);
    ir.cfg.compactNodeNumbering();
  }

  /**
   * This method determines if the phase should be run, based on the
   * OPT_Options object it is passed
   */
  boolean shouldPerform (OPT_Options options) {
    if (options.getOptLevel() < 2)
      return  false;
    return  options.TURN_WHILES_INTO_UNTILS;
  }

  /**
   * Returns the name of the phase.
   */
  String getName () {
    return  "CFGTransformations";
  }

  /**
   * Returns true if the phase wants the IR dumped before and/or after it runs.
   */
  boolean printingEnabled (OPT_Options options, boolean before) {
    return  false;
  }

  //Implementation
  /**
   * treat all loops of the ir
   */
  private static boolean turnWhilesIntoUntils (OPT_IR ir) {
    OPT_LSTGraph lstg = ir.HIRInfo.LoopStructureTree;
    if (lstg != null)
      return  turnLoopTreeIntoUntils((OPT_LSTNode)lstg.firstNode(), ir);
    return  false;
  }

  /**
   * deal with a sub tree of the loop structure tree
   */
  private static boolean turnLoopTreeIntoUntils (OPT_LSTNode t, OPT_IR ir) {
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
   * Transform a given loop
   *
   * <p> Look for the set S of in-loop predecessors of the loop header h.
   * Make a copy h' of the loop header and redirect all edges going from
   * nodes in S to h. Make them point to h' instead.
   *
   * <p> As an effect of this transformation, the old header is now not anymore
   * part of the loop, but guards it.
   */
  private static boolean turnLoopIntoUntil (OPT_LSTNode n, OPT_IR ir) {
    OPT_BitVector nloop = n.loop;
    OPT_BasicBlock header = n.header;
    OPT_BasicBlock newLoopTest = null, pred[] = inLoopPredecessors(n);
    // nothing to do or to complex?
    if (pred == null)
      return  false;
    int i = -1;
    while (++i < pred.length) {
      if (pred[i] == null)
        continue;
      changed = true;
      // replicate the header as successor of pred[i]
      newLoopTest = pred[i].replicateThisOut(ir, header);
      // can not really fix loop structure tree, try the best
      n.loop.clear(header.getNumber());
      n.header = null;          // this might not be unique anymore
      removeYieldPoint(header);
      addToLoops(newLoopTest, n);
      break;                    // only handle the first back edge
    }
    while (++i < pred.length) { // check for aditional back edges
      if (pred[i] == null)
        continue;
      pred[i].redirectOuts(header, newLoopTest,ir);
    }
    return  true;
  }

  /**
   * the predecessors of `block' that are part of the loop
   */
  private static OPT_BasicBlock[] inLoopPredecessors (OPT_LSTNode n) {
    boolean headerExits = false;
    boolean singleNodeLoop = true;
    OPT_BasicBlock header = n.header;
    OPT_BitVector loop = n.loop;
    // check for the following condition:
    // loop header `h' has a successor that is not part of the loop
    // all in loop predecessors of `h' have `h' as single successor
    OPT_BasicBlockEnumeration be = header.getOut();
    while (be.hasMoreElements()) {
      if (!loop.get(be.next().getNumber())) {
        headerExits = true;
        break;
      }
    }
    if (!headerExits)
      return  null;
    OPT_BasicBlock res[] = new OPT_BasicBlock[header.getNumberOfIn()];
    be = header.getIn();
    int i = 0;
    while (be.hasMoreElements()) {
      OPT_BasicBlock in = (OPT_BasicBlock)be.nextElement();
      if (loop.get(in.getNumber())) {
        if (in.hasOneOut()) {
          res[i] = in;
          i++;
        } else {
          return  null;
        }
      }
    }
    return  res;
  }

  /**
   * Add `b' to loop `n' and all enclosing loops.
   */
  private static void addToLoops (OPT_BasicBlock b, OPT_LSTNode n) {
    while (n.loop != null) {
      if (VM.VerifyAssertions)
        VM.assert(n.hasOneIn());
      n.loop.set(b.getNumber());
      n = (OPT_LSTNode)n.firstInNode();
    }
  }

  /**
   * If the given block has a yield point, remove it.
   */
  static void removeYieldPoint (OPT_BasicBlock b) {
    OPT_InstructionEnumeration e = b.forwardInstrEnumerator();
    while (e.hasMoreElements()) {
      OPT_Instruction inst = e.next();
      if (inst.operator.opcode == OPT_Operators.YIELDPOINT_PROLOGUE_opcode ||
	  inst.operator.opcode == OPT_Operators.YIELDPOINT_EPILOGUE_opcode ||
	  inst.operator.opcode == OPT_Operators.YIELDPOINT_BACKEDGE_opcode) {
	//VM.sysWrite ("yieldpoint removed\n");
	inst.remove();
      }
    }
  }

  static void killFallThroughs (OPT_IR ir, OPT_BitVector nloop) {
    OPT_BasicBlockEnumeration bs = ir.getBasicBlocks (nloop);
    while (bs.hasMoreElements()) {
      OPT_BasicBlock block = bs.next();
      OPT_BasicBlockEnumeration bi = block.getIn();
      while (bi.hasMoreElements()) {
	OPT_BasicBlock in = bi.next();
	if (nloop.get(in.getNumber())) continue;
	killFallThrough(in);
      }
      killFallThrough(block);
    }
  }

  static void killFallThrough (OPT_BasicBlock b) {
    OPT_BasicBlock fallThrough = b.getFallThroughBlock();
    if (fallThrough != null) {
      b.lastInstruction().insertBefore
	(Goto.create(GOTO,fallThrough.makeJumpTarget()));
    }
  }

  /**
   * Critical edge removal: if (a,b) is an edge in the cfg where `a' has more
   * than one out-going edge and `b' has more than one in-coming edge,
   * insert a new empty block `c' on the edge between `a' and `b'.
   *
   * <p> We do this to provide landing pads for loop-invariant code motion.
   * So we split only edges, where `a' has a lower loop nesting depth than `b'.
   */
  static void splitCriticalEdges (OPT_IR ir) {
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
        if (frequency(a, ir) >= frequency(b, ir))
          continue;
        //VM.sysWrite ("Critical edge: " + a + " -> " + b + "\n");
        changed = true;
        // create a new block as landing pad
        OPT_BasicBlock landingPad;
        OPT_Instruction firstInB = b.firstInstruction();
        int bcIndex = firstInB != null ? firstInB.bcIndex : -1;
        landingPad = b.createSubBlock(bcIndex, ir);
        //landingPad.setInfrequent();
        // make the landing pad jump to `b'
        OPT_Instruction g;
        g = Goto.create(GOTO, b.makeJumpTarget());
        landingPad.appendInstruction(g);
        landingPad.recomputeNormalOut(ir);
        // redirect a's outputs from b to the landing pad
        a.redirectOuts(b, landingPad,ir);
        // link landing pad into the code order.
        OPT_BasicBlock aFallThrough = a.getFallThroughBlock();
        if (aFallThrough != null) {
          g = Goto.create(GOTO, aFallThrough.makeJumpTarget());
          a.appendInstruction(g);
        }
        OPT_BasicBlock aNext = a.nextBasicBlockInCodeOrder();
        if (aNext != null) {
          ir.cfg.breakCodeOrder(a, aNext);
          ir.cfg.linkInCodeOrder(landingPad, aNext);
        }
        ir.cfg.linkInCodeOrder(a, landingPad);
      }
    }
  }

  /**
   * How often will this block be executed?
   * @param b
   * @param ir
   * @return
   */
  static final int frequency (OPT_BasicBlock b, OPT_IR ir) {
    //-#if BLOCK_COUNTER_WORKS
    OPT_Instruction inst = b.firstInstruction();
    return  basicBlockCounter.getCount(inst.bcIndex, inst.position);
    //-#else
    return  ir.HIRInfo.LoopStructureTree.getLoopNestDepth(b);
    //-#endif
  }
}



