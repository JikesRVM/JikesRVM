/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM;

import instructionFormats.*;
import java.util.Enumeration;

/**
 * Reorder code layout of basic blocks for improved I-cache locality and
 * branch prediction. This code assumes that basic block frequencies have
 * been computed and blocks have been marked infrequent. 
 * This pass actually implements two code placement algorithms:
 * (1) A simple 'fluff' removal pass that moves all infrequent basic blocks
 *     to the end of the code order.
 * (2) Pettis and Hansen Algo2.
 *
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
   * Reorder basic blocks either by trivially moving infrequent blocks 
   * to the end of the code order or by applying Pettis and Hansen Algo2.
   *
   * We will rearrange basic blocks and insert/remove
   * unconditional GOTO's if needed.  It does not clean up branches,
   * by reversing the branch condition, however.  That is saved for
   * OPT_BranchOptimizations.
   */
  void perform (OPT_IR ir) {
    OPT_BasicBlock[] newOrder;
    ir.cfg.entry().clearInfrequent();
    if (false && ir.options.REORDER_CODE_PH) {
      // Do Pettis and Hansen Algo2
      newOrder = null;
    } else {
      // Simple algorithm: just move infrequent code to the end
      if (!findInfrequentBlocks(ir)) return;
      newOrder = exileInfrequentBlocks(ir);
    }

    implementNewOrdering(ir, newOrder);
  }

  
  /** 
   * Scan the IR and determine if there are infrequent blocks.
   * Also count the number of blocks in the IR.
   * @return true if any infrequent blocks are found
   */
  private boolean findInfrequentBlocks(OPT_IR ir) {
    boolean foundSome = false;
    for (OPT_BasicBlockEnumeration e = ir.getBasicBlocks(); 
	 e.hasMoreElements();) {
      OPT_BasicBlock bb = e.next();
      numBlocks++;
      foundSome |= bb.getInfrequent();
    }
    return foundSome;
  }

  /**
   * Select a new basic block ordering via a simple heuristic
   * that moves all infrequent basic blocks to the end .
   * @param ir the OPT_IR object to reorder
   * @return the new ordering
   */
  private OPT_BasicBlock[] exileInfrequentBlocks(OPT_IR ir) {
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
