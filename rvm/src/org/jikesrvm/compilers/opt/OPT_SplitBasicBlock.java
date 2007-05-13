/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt;

import org.jikesrvm.compilers.opt.ir.OPT_BasicBlock;
import org.jikesrvm.compilers.opt.ir.OPT_BasicBlockEnumeration;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;

/**
 * Splits a large basic block into smaller ones with size <= MAX_NUM_INSTRUCTIONS.
 * 
 */
public final class OPT_SplitBasicBlock extends OPT_CompilerPhase {

  private static final int MAX_NUM_INSTRUCTIONS = 300;

  public String getName() { return "SplitBasicBlock"; }
  public OPT_CompilerPhase newExecution(OPT_IR ir) { return this; }
  
  public void perform(OPT_IR ir) {
    for (OPT_BasicBlockEnumeration e = ir.getBasicBlocks(); e.hasMoreElements(); ) {
      OPT_BasicBlock bb = e.nextElement();
      
      if (!bb.isEmpty()) {
        while (bb != null) 
          bb = splitEachBlock(bb, ir);
      }
    }
  }

  // Splits bb. 
  // Returns null if no splitting is done.
  // returns the second block if splitting is done.
  OPT_BasicBlock splitEachBlock(OPT_BasicBlock bb, OPT_IR ir) {

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



