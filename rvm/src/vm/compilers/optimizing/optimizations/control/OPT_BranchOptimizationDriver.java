/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;
import com.ibm.JikesRVM.*;

import com.ibm.JikesRVM.opt.ir.*;

/**
 * IR level independent driver for 
 * simple peephole optimizations of branches.
 * 
 * @author Stephen Fink
 * @author Dave Grove
 * @author Mauricio Serrano
 */
public abstract class OPT_BranchOptimizationDriver 
  extends OPT_CompilerPhase
  implements OPT_Operators {

  /**
   * Optimization level at which phase should be performed.
   */
  private int _level;

  protected OPT_BranchOptimizationDriver() {}

  /** 
   * @param level the minimum optimization level at which the branch 
   * optimizations should be performed.
   */
  OPT_BranchOptimizationDriver(int level) {
    _level = level;
  }

  /** Interface */
  public final boolean shouldPerform(OPT_Options options) {
    return  options.getOptLevel() >= _level;
  }

  public final String getName() {
    return  "Branch Optimizations";
  }

  public final boolean printingEnabled(OPT_Options options, boolean before) {
    return false;
  } 

  /**
   * This phase contains no per-compilation instance fields.
   */
  public final OPT_CompilerPhase newExecution(OPT_IR ir) {
    return  this;
  }


  /**
   * Perform peephole branch optimizations.
   * 
   * @param ir the IR to optimize
   */
  public final void perform(OPT_IR ir) {
    perform(ir, true);
  }

  public final void perform(OPT_IR ir, boolean renumber) {
    maximizeBasicBlocks(ir);
    if (VM.BuildForIA32) {
      // spans-bb information is used for CMOV insertion
      OPT_DefUse.recomputeSpansBasicBlock(ir);
    }
    boolean didSomething = false;
    boolean didSomethingThisTime = true;
    while (didSomethingThisTime) {
      didSomething |= applyPeepholeBranchOpts(ir);
      didSomethingThisTime = removeUnreachableCode(ir);
      didSomething |= didSomethingThisTime;
    }
    if (didSomething)
      maximizeBasicBlocks(ir);
    if (renumber)
      ir.cfg.compactNodeNumbering();

    if (ir.IRStage < OPT_IR.MIR) {
      ir.pruneExceptionalOut();
    }
  }

  /**
   * This pass performs peephole branch optimizations. 
   * See Muchnick ~p.590
   *
   * @param ir the IR to optimize
   */
  protected boolean applyPeepholeBranchOpts(OPT_IR ir) {
    boolean didSomething = false;
    for (OPT_BasicBlockEnumeration e = ir.getBasicBlocks(); 
         e.hasMoreElements();) {
      OPT_BasicBlock bb = e.next();
      if (!bb.isEmpty()) {
        for (OPT_InstructionEnumeration ie = bb.enumerateBranchInstructions(); 
             ie.hasMoreElements();) {
          OPT_Instruction s = ie.next();
          if (optimizeBranchInstruction(ir, s, bb)) {
            didSomething = true;
            // hack: we may have modified the instructions; start over
            ie = bb.enumerateBranchInstructions();
          }
        }
      }
    }
    return didSomething;
  }

  /**
   * This method actually does the work of attempting to
   * peephole optimize a branch instruction.
   * @param ir the containing IR
   * @param s the branch instruction to optimize
   * @param bb the containing basic block
   * @return true if an optimization was applied, false otherwise
   */
  protected abstract boolean optimizeBranchInstruction(OPT_IR ir,
                                                       OPT_Instruction s,
                                                       OPT_BasicBlock bb);

  /**
   * Remove unreachable code
   *
   * @param ir the IR to optimize
   * @return true if did something, false otherwise
   */
  protected final boolean removeUnreachableCode(OPT_IR ir) {
    boolean result = false;

    // (1) All code in a basic block after an unconditional 
    //     trap instruction is dead.
    for (OPT_Instruction s = ir.firstInstructionInCodeOrder(); 
         s != null; 
         s = s.nextInstructionInCodeOrder()) {
      if (Trap.conforms(s)) {
        OPT_Instruction p = s.nextInstructionInCodeOrder();
        if (p.operator() != BBEND) {
          OPT_BasicBlock bb = s.getBasicBlock();
          do {
            OPT_Instruction q = p;
            p = p.nextInstructionInCodeOrder();
            q.remove();
          } while (p.operator() != BBEND);
          bb.recomputeNormalOut(ir);
          result = true;
        }
      }
    }

    // (2) perform a Depth-first search of the control flow graph,
    //     and remove any nodes not reachable from entry.
    OPT_BasicBlock entry = ir.cfg.entry();
    ir.cfg.clearDFS();
    entry.sortDFS();
    for (OPT_SpaceEffGraphNode node = entry; node != null;) {
      // save it now before removeFromCFGAndCodeOrder nulls it out!!!
      OPT_SpaceEffGraphNode nextNode = node.getNext();         
      if (!node.dfsVisited()) {
        OPT_BasicBlock bb = (OPT_BasicBlock)node;
        ir.cfg.removeFromCFGAndCodeOrder(bb);
        result = true;
      }
      node = nextNode;
    }
    return  result;
  }

  /**
   * Merge adjacent basic blocks
   *
   * @param ir the IR to optimize
   */
  protected final void maximizeBasicBlocks(OPT_IR ir) {
    for (OPT_BasicBlock currBB = ir.cfg.firstInCodeOrder(); currBB != null;) {
      if (currBB.mergeFallThrough(ir)) {
        // don't advance currBB; it may have a new trivial fallthrough to swallow
      } else {
        currBB = currBB.nextBasicBlockInCodeOrder();
      }
    }
  }


  // Helper functions
  
  /**
   * Given an instruction s, return the first LABEL instruction
   * following s.
   */
  protected final OPT_Instruction firstLabelFollowing(OPT_Instruction s) {
    for (s = s.nextInstructionInCodeOrder(); s != null; 
         s = s.nextInstructionInCodeOrder()) {
      if (s.operator() == LABEL) {
        return  s;
      }
    }
    return  null;
  }

  /**
   * Given an instruction s, return the first real (non-label) instruction
   * following s
   */
  protected final OPT_Instruction firstRealInstructionFollowing(OPT_Instruction s) {
    for (s = s.nextInstructionInCodeOrder(); 
         s != null; 
         s = s.nextInstructionInCodeOrder()) {
      if (s.operator() != LABEL && s.operator() != BBEND) {
        return  s;
      }
    }
    return  s;
  }
}
