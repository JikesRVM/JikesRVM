/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.opt.ir.*;

/**
 * Splits a large basic block into smaller ones with size <= MAX_NUM_INSTRUCTIONS.
 * 
 * @author Jong Choi
 */
final class OPT_SplitBasicBlock extends OPT_CompilerPhase {

  private static final int MAX_NUM_INSTRUCTIONS = 300;

  public final String getName() { return "SplitBasicBlock"; }
  public final OPT_CompilerPhase newExecution() { return this; }
  
  public final void perform(OPT_IR ir) {
    for (OPT_BasicBlockEnumeration e = ir.getBasicBlocks(); e.hasMoreElements(); ) {
      OPT_BasicBlock bb = (OPT_BasicBlock) e.nextElement();
      
      if (!bb.isEmpty()) {
        while (bb != null) 
          bb = splitEachBlock(bb, ir);
      }
    }
  }

  // Splits bb. 
  // Returns null if no splitting is done.
  // returns the second block if splitting is done.
  final OPT_BasicBlock splitEachBlock(OPT_BasicBlock bb, OPT_IR ir) {

    int instCount = MAX_NUM_INSTRUCTIONS;
    for (OPT_Instruction inst = bb.firstInstruction(); 
         inst != bb.lastInstruction(); inst = inst.nextInstructionInCodeOrder())
      {
        if ((--instCount) == 0) {
          if (inst.isBranch())
            return null; // no need to split because all the rests are just branches
          if (inst.isMove()) {  // why do we need this?? --dave
            OPT_Instruction next = inst.nextInstructionInCodeOrder();
             if (next != bb.lastInstruction() && next.isImplicitLoad()) 
                inst = next;
          }
          // Now, split!
          return bb.splitNodeWithLinksAt(inst, ir);
        }
      }

    return null; // no splitting happened
  }

}



