/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;

/**
 * Reorder code layout of basic blocks for improved I-cache locality and
 * branch prediction.
 *
 * @see OPT_BasicBlock
 * @author Vivek Sarkar
 * @author Dave Grove
 * @modified Matthew Arnold
 */
final class OPT_ReorderingPhase extends OPT_CompilerPhase
  implements OPT_Operators {

  private static final boolean DEBUG = false;
  
  final boolean shouldPerform (OPT_Options options) {
    return options.REORDER_CODE;
  }

  String getName () {
    return "Code Reordering";
  }

  // All non-trivial methods are static, this we have no 
  // per-compilation instance fields.
  OPT_CompilerPhase newExecution (OPT_IR ir) {
    return this;
  }

  /**
   * Update basic block links so that all basic blocks with 
   * getInfrequent() = true are moved to the end.
   *
   * This method rearranges basic blocks and inserts/removes
   * unconditional GOTO's if needed.  It does not clean up branches,
   * by reversing the branch condition, however.  That is saved for
   * OPT_BranchOptimizations.
   */
  void perform (OPT_IR ir) {
    if (VM.VerifyAssertions) VM.assert (ir.IRStage != OPT_IR.MIR);
    
    OPT_BasicBlock[] newOrdering = null;
    // TODO: when the more complex algorithm seems to be reliable, change 10 to 1
    newOrdering = selectNewOrdering(ir, ir.options.getOptLevel() < 10);
    if (newOrdering != null) {
      implementNewOrdering(ir, newOrdering);
    }
  }

  /**
   * Select a new basic block ordering.  Use a simple heuristic
   * that moves all infrequent basic blocks to the end (similar to the
   * heuristic proposed by Pettis & Hansen in PLDI '90).
   *
   * @return null if new ordering is same as old ordering
   * @return newOrdering array otherwise
   */
  static OPT_BasicBlock[] selectNewOrdering(OPT_IR ir, 
					    boolean useSimpleAlgorithm) {
    int numBlocks = 0;
    for (OPT_BasicBlock bb = ir.cfg.firstInCodeOrder();
	 bb != null; 
	 bb = bb.nextBasicBlockInCodeOrder()) {
      initializeInfrequent(bb);          /// Initialize infrequent flag
      numBlocks++;
    }
    // Code reordering will be a no-op
    if (numBlocks <= 1) return null;

    OPT_BasicBlock[] newOrdering = new OPT_BasicBlock[numBlocks];
    // Make sure that first basic block is marked with infrequent = false
    // (We want first basic block to stay unchanged.)
    ir.cfg.firstInCodeOrder().setInfrequent(false);
    if (useSimpleAlgorithm) {
      // SIMPLE ALGORITHM: JUST SEPARATE FREQUENT AND INFREQUENT BLOCKS
      //                   BUT NO OTHER PERMUTATION IN BLOCK ORDERING.
      // First append frequent blocks to newOrdering
      int i = 0;
      for (OPT_BasicBlock bb = ir.cfg.firstInCodeOrder(); 
	   bb != null; 
	   bb = bb.nextBasicBlockInCodeOrder()) {
        if (!bb.getInfrequent())
          newOrdering[i++] = bb;
      }
      // Next append infrequent blocks to newOrdering
      for (OPT_BasicBlock bb = ir.cfg.firstInCodeOrder(); 
	   bb != null; 
	   bb = bb.nextBasicBlockInCodeOrder()) {
        if (bb.getInfrequent())
          newOrdering[i++] = bb;
      }
      if (VM.VerifyAssertions) VM.assert(i == numBlocks);
    } else {
      // COMPLEX ALGORITHM BASED ON CONTROL DEPENDENCE
      // Do a closure of infrequent info using dominators
      OPT_Dominators.computeApproxDominators(ir);
      // TODO: replace by efficient traversal of subtree in dominator tree
      for (OPT_BasicBlockEnumeration e = ir.getBasicBlocks(); 
                                     e.hasMoreElements();) {
        OPT_BasicBlock b = e.next();
        // mark all nodes dominated by b as infrequent
        OPT_DominatorInfo i = (OPT_DominatorInfo)b.scratchObject;
        for (OPT_BasicBlockEnumeration e2 = ir.getBasicBlocks(); 
                                        e2.hasMoreElements();) {
          OPT_BasicBlock b2 = e2.next();
          if (b2.getInfrequent() && i.isDominatedBy(b2)) {
            b.setInfrequent();
            break;
          }
        }
      }
      // Do a closure of infrequent info using postdominators
      OPT_Dominators.computeApproxPostdominators(ir);
      // TODO: replace by efficient traversal of subtree in postdominator tree
      for (OPT_BasicBlockEnumeration e = ir.getBasicBlocks(); 
                                      e.hasMoreElements();) {
        OPT_BasicBlock b = e.next();
        // mark all nodes postdominated by b as infrequent
        OPT_DominatorInfo i = (OPT_DominatorInfo)b.scratchObject;
        for (OPT_BasicBlockEnumeration e2 = ir.getBasicBlocks(); 
                                        e2.hasMoreElements();) {
          OPT_BasicBlock b2 = e2.next();
          if (b2.getInfrequent() && i.isDominatedBy(b2)) {
            b.setInfrequent();
            break;
          }
        }
      }
      for (OPT_BasicBlock bb = ir.cfg.firstInCodeOrder(); 
	   bb != null; 
	   bb = bb.nextBasicBlockInCodeOrder()) {
        bb.clearScratchFlag();
      }
      int i = 0;
      i = visitFrequentBasicBlocks(ir, ir.cfg.firstInCodeOrder(), newOrdering, i, true);
      int numFrequent = i;
      // Next append infrequent blocks to newOrdering
      for (OPT_BasicBlock bb = ir.cfg.firstInCodeOrder(); 
	   bb != null; 
	   bb = bb.nextBasicBlockInCodeOrder()) {
        if (!bb.getScratchFlag())
          i = visitFrequentBasicBlocks(ir, bb, newOrdering, i, false);
      }
      int numInfrequent = numBlocks - numFrequent;
      if (DEBUG)
        VM.sysWrite("#### numFrequent = " + numFrequent + " ; numInfrequent = "
		    + numInfrequent + " ####\n");
    }
    return newOrdering;
  }


  /**
   * put your documentation comment here
   * @param ir
   * @param bb
   * @param newOrdering
   * @param i
   * @param ignoreInfrequentBlocks
   * @return 
   */
  static int visitFrequentBasicBlocks (OPT_IR ir, OPT_BasicBlock bb, 
                                       OPT_BasicBlock[] newOrdering, 
                                       int i, boolean ignoreInfrequentBlocks) {
    if (bb.getScratchFlag() || ignoreInfrequentBlocks && bb.getInfrequent())
      // Do nothing
      return i;
    newOrdering[i++] = bb;
    bb.setScratchFlag();
    OPT_BitVector bbPdoms = ((OPT_DominatorInfo)bb.scratchObject).dominators;
    // Process bb's control flow successors
    for (OPT_SpaceEffGraphEdge e = bb.firstOutEdge(); e != null; 
          e = e.getNextOut()) {
      OPT_BasicBlock succ = (OPT_BasicBlock)e.toNode();
      // Recurse on all nodes control dependent on bb w/ this branch label
      OPT_BitVector temp = (OPT_BitVector)((OPT_DominatorInfo)
                            succ.scratchObject).dominators.clone();
      temp.and(bbPdoms);
      // Now temp = intersection of succ's postdominators and 
      // bb's postdominators
      // TODO: use postdominator tree instead (but watch out for infinite
      // loops!)
      for (OPT_BasicBlockEnumeration e2 = ir.getBasicBlocks(); 
                                            e2.hasMoreElements();) {
        OPT_BasicBlock b2 = e2.next();
        if (temp.get(b2.getNumber())) {
          i = visitFrequentBasicBlocks(ir, b2, newOrdering, i, 
                                       ignoreInfrequentBlocks);
        }
      }
    }
    return i;
  }

  /**
   * Rearrange all basic blocks according to newOrdering.
   *
   * Add/remove unconditional goto instructions as needed.
   *
   * @param newOrdering permutation of all basic blocks in CFG
   * newOrdering[0] = first basic block in new ordering
   *                  (must be same as first basic block in old ordering)
   * newOrdering[1] = second basic block in new ordering
   * ... and so on
   */
  static void implementNewOrdering (OPT_IR ir, OPT_BasicBlock[] newOrdering) {
    // Check that first basic block is unchanged in newOrdering
    // (To relax this restriction, we'll need to add a goto at the top,
    //  which seems like it would never be a win.)
    if (VM.VerifyAssertions) VM.assert(newOrdering[0] == ir.cfg.firstInCodeOrder());

    // Add/remove unconditional goto's as needed.
    for (int i = 0; i < newOrdering.length; i++) {
      OPT_Instruction lastInstr = newOrdering[i].lastRealInstruction();

      // Append a GOTO instruction if needed to maintain old fallthrough
      // semantics.
      OPT_BasicBlock fallthroughBlock = newOrdering[i].getFallThroughBlock();
      if (fallthroughBlock != null) {
        boolean needGOTO = true; // Has fallthrough according to old semantics
        if (i < newOrdering.length - 1 && 
	    fallthroughBlock == newOrdering[i + 1])
          // fallthroughBlock is also the fallthrough successor in
          // the new ordering
          needGOTO = false;
        if (needGOTO) {
          // Add unconditional goto to preserve old fallthrough semantics
          newOrdering[i].appendInstruction(fallthroughBlock.makeGOTO());
        }
      }
      // Remove last instruction if it is a redundant GOTO that
      // can be implemented by a fallthrough edge in the new ordering.
      // (Only possible if newOrdering[i] is not the last basic block.)
      if (i < newOrdering.length - 1 && lastInstr != null 
                  && lastInstr.operator() == GOTO) {
        OPT_BranchOperand op = Goto.getTarget(lastInstr);
        // GOTO instruction is a direct branch
        if (op.target.getBasicBlock() == newOrdering[i + 1]) {
          // unconditional goto is redundant in new ordering 
          // -- remove it!
          lastInstr.remove();
        }
      }
    }           // for
    // Remove all basic blocks (except the first) from old ordering
    for (int i = 1; i < newOrdering.length; i++)
      ir.cfg.removeFromCodeOrder(newOrdering[i]);
    // Re-insert all basic blocks (except the first) according to new ordering
    for (int i = 1; i < newOrdering.length; i++) {
      ir.cfg.addLastInCodeOrder(newOrdering[i]);
    }
  }

  /**
   * Check if this basic block should have INFREQUENT set = true.
   * If so, update infrequent and return.
   * A block is infrequent if:
   *  (1) it has already been marked as infrequent
   *  (2) it is an exception handler block
   *  (3) it contains a call to an unresolved 
   *  (4) it contains a call to a method that is marked pragmaNoInline()
   */
  private static void initializeInfrequent (OPT_BasicBlock bb) {
    if (bb.getInfrequent()) return; // infrequency is sticky.
    if (bb.isExceptionHandlerBasicBlock()) {
      bb.setInfrequent();
      return;
    }
    for (OPT_Instruction instr = bb.firstInstruction(); 
	 instr != bb.lastInstruction(); 
	 instr = instr.nextInstructionInCodeOrder()) {
      if (Call.conforms(instr)) {
	OPT_MethodOperand op = Call.getMethod(instr);
	if (op != null) {
	  VM_Method target = op.method;
	  if (target != null) {
	    // Current heuristic --- having a call to methods that are:
	    //   (1) unloaded methods, 
	    //   (2) marked with noInlinePragmas
	    // results in a block being considered infrequent
	    if (!target.getDeclaringClass().isLoaded() ||
		(target.getBytecodes() != null && 
		 OPT_InlineTools.hasNoInlinePragma(target, null))) {
	      bb.setInfrequent();
	      return;
	    }
	  }
	}
      } else if (instr.operator() == ATHROW) {
	bb.setInfrequent();
	return;
      }
    }
  }
}
