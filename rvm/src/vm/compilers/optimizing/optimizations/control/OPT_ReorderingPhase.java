/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;
import java.util.Enumeration;

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
  private int numBlocks;

  final boolean shouldPerform (OPT_Options options) {
    return options.REORDER_CODE;
  }

  final boolean printingEnabled (OPT_Options options, boolean before) {
    return DEBUG;
  }

  String getName () { 
    return "Code Reordering"; 
  }

  /**
   * Reorder basic blocks to move infrequently executed blocks 
   * to the end.
   *
   * This compiler phase rearranges basic blocks and inserts/removes
   * unconditional GOTO's if needed.  It does not clean up branches,
   * by reversing the branch condition, however.  That is saved for
   * OPT_BranchOptimizations.
   */
  void perform (OPT_IR ir) {
    if (VM.VerifyAssertions) VM.assert (ir.IRStage != OPT_IR.MIR);

    // Construct dominator and LST information 
    if (ir.options.getOptLevel() >= 1) {
      OPT_LTDominators.approximate(ir, true);
      OPT_DominatorTree.perform(ir, true);
      OPT_LSTGraph.perform(ir);
    }      
    
    // Find sources of infrequency (direct and implied)
    if (!markInfrequentBlocks(ir)) return;
    ir.cfg.entry().clearInfrequent();
    if (ir.options.getOptLevel() >= 1) {
      propagateInfrequency(ir);
    }

    // Exile infrequent blocks to end of code ordering
    implementNewOrdering(ir, selectNewOrdering(ir));
  }

  
  /** 
   * Scan the IR and determine if there are infrequent blocks.
   * Also count the number of blocks in the IR.
   * @return true if any infrequent blocks are found
   */
  private boolean markInfrequentBlocks(OPT_IR ir) {
    boolean foundSome = false;
    for (OPT_BasicBlockEnumeration e = ir.getBasicBlocks(); 
	 e.hasMoreElements();) {
      OPT_BasicBlock bb = e.next();
      bb.clearScratchFlag();
      numBlocks++;
      if (bb.getInfrequent()) foundSome = true;
    }
    return foundSome;
  }

  /**
   * Use dominator and post-dominator information to propagate
   * infrequency from the seed blocks to other blocks.
   */
  void propagateInfrequency(OPT_IR ir) {
    markChildren(ir.cfg.entry(), ir.HIRInfo.dominatorTree);

    // compute post dominators, and mark all nodes postdominated by
    // an infrequent block as infrequent
    // TODO: This craps out when IR has a block such as a checkcast trap
    //       or athrow that ends a CFG path without being linked to the exit node.
    // OPT_LTDominators.perform(ir, false, false);
    // OPT_DominatorTree.perform(ir, false);
    // markChildren(ir.cfg.exit(), ir.HIRInfo.dominatorTree);

    // restore invariant that the first block is not marked as infrequent
    ir.cfg.entry().clearInfrequent();
  }
    

  /**
   * Recursive walk of the (post)dominator tree marking all blocks 
   * (post)dominated by an infrequent node as infrequent.
   */
  private void markChildren(OPT_BasicBlock bb, OPT_DominatorTree dt) {
    boolean infrequent = bb.getInfrequent();
    for (Enumeration e = dt.getChildren(bb); e.hasMoreElements(); ) {
      OPT_BasicBlock c = ((OPT_DominatorTreeNode)e.nextElement()).getBlock();
      if (infrequent) {
	if (DEBUG && !c.getInfrequent()) {
	  VM.sysWrite("propagating infrequent to "+c+"\n");
	}
	c.setInfrequent();
      }
      markChildren(c, dt);
    }
  }


  /**
   * Select a new basic block ordering.  Use a simple heuristic
   * that moves all infrequent basic blocks to the end (similar to the
   * heuristic proposed by Pettis & Hansen in PLDI '90).
   * @param ir the OPT_IR object to reorder
   * @return the new ordering
   */
  private OPT_BasicBlock[] selectNewOrdering(OPT_IR ir) {
    OPT_BasicBlock[] newOrdering = new OPT_BasicBlock[numBlocks];
    int i = 0;
    // First append frequent blocks to newOrdering
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
    return newOrdering;
  }

  
  /**
   * Rearrange all basic blocks according to newOrdering.
   *
   * Add/remove unconditional goto instructions as needed.
   *
   * @param ir the IR to permute
   * @param newOrdering permutation of all basic blocks in CFG
   * newOrdering[0] = first basic block in new ordering
   *                  (must be same as first basic block in old ordering)
   * newOrdering[1] = second basic block in new ordering
   * ... and so on
   */
  private void implementNewOrdering (OPT_IR ir, OPT_BasicBlock[] newOrdering){
    // Check that first basic block is unchanged in newOrdering
    // (To relax this restriction, we'll need to add a goto at the top,
    //  which seems like it would never be a win.)
    if (VM.VerifyAssertions) VM.assert(newOrdering[0] == ir.cfg.firstInCodeOrder());

    // Add/remove unconditional goto's as needed.
    for (int i = 0; i<newOrdering.length; i++) {
      OPT_Instruction lastInstr = newOrdering[i].lastRealInstruction();
      // Append a GOTO if needed to maintain old fallthrough semantics.
      OPT_BasicBlock fallthroughBlock = newOrdering[i].getFallThroughBlock();
      if (fallthroughBlock != null) {
	if (i == newOrdering.length - 1 || fallthroughBlock != newOrdering[i+1]) {
	  // Add unconditional goto to preserve old fallthrough semantics
	  newOrdering[i].appendInstruction(fallthroughBlock.makeGOTO());
	}
      }
      // Remove last instruction if it is a redundant GOTO that
      // can be implemented by a fallthrough edge in the new ordering.
      // (Only possible if newOrdering[i] is not the last basic block.)
      if (i<newOrdering.length-1 && lastInstr != null && lastInstr.operator() == GOTO) {
        OPT_BranchOperand op = Goto.getTarget(lastInstr);
        if (op.target.getBasicBlock() == newOrdering[i+1]) {
          // unconditional goto is redundant in new ordering 
          lastInstr.remove();
        }
      }
    }

    // Remove all basic blocks (except the first) from old ordering
    for (int i=1; i<newOrdering.length; i++) {
      ir.cfg.removeFromCodeOrder(newOrdering[i]);
    }
    // Re-insert all basic blocks (except the first) according to new ordering
    for (int i=1; i<newOrdering.length; i++) {
      ir.cfg.addLastInCodeOrder(newOrdering[i]);
    }
  }
}
