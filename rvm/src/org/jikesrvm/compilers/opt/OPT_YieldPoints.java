/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt;

import org.jikesrvm.VM;
import static org.jikesrvm.compilers.opt.OPT_Constants.INSTRUMENTATION_BCI;
import org.jikesrvm.compilers.opt.ir.Empty;
import org.jikesrvm.compilers.opt.ir.OPT_BasicBlock;
import org.jikesrvm.compilers.opt.ir.OPT_BasicBlockEnumeration;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_InlineSequence;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_Operator;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IR_PROLOGUE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.YIELDPOINT_BACKEDGE;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.YIELDPOINT_EPILOGUE;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.YIELDPOINT_PROLOGUE;

/**
 * This class inserts yield points in
 *  1) a method's prologue 
 *  2) loop headers
 *  3) (optionally) method exits (epilogue, athrow)
 */
class OPT_YieldPoints extends OPT_CompilerPhase {

  /**
   * Should this phase be performed?
   * @param options controlling compiler options
   * @return true or false
   */
  public final boolean shouldPerform(OPT_Options options) {
    return !options.NO_THREADS;
  }

  /**
   * Return the name of this phase
   * @return "Yield Point Insertion"
   */
  public final String getName() {
    return "Yield Point Insertion";
  }

  /**
   * This phase contains no per-compilation instance fields.
   */
  public final OPT_CompilerPhase newExecution(OPT_IR ir) {
    return this;
  }

  /**
   * Insert yield points in method prologues, loop heads, and method exits
   *
   * @param ir the governing IR
   */
  public final void perform(OPT_IR ir) {
    if (!ir.method.isInterruptible()) {
      return;   // don't insert yieldpoints in Uninterruptible code.
    }

    // (1) Insert prologue yieldpoint unconditionally.
    //     As part of prologue/epilogue insertion we'll remove
    //     the yieldpoints in trival methods that otherwise wouldn't need 
    //     a stackframe.
    prependYield(ir.cfg.entry(), YIELDPOINT_PROLOGUE, 0, ir.gc.inlineSequence);

    // (2) If using epilogue yieldpoints scan basic blocks, looking for returns or throws
    if (VM.UseEpilogueYieldPoints) {
      for (OPT_BasicBlockEnumeration e = ir.getBasicBlocks();
           e.hasMoreElements();) {
        OPT_BasicBlock block = e.next();
        if (block.hasReturn() || block.hasAthrowInst()) {
          prependYield(block, YIELDPOINT_EPILOGUE, INSTRUMENTATION_BCI, ir.gc.inlineSequence);
        }
      }
    }

    // (3) Insert yieldpoints in loop heads based on the LST.
    OPT_LSTGraph lst = ir.HIRInfo.LoopStructureTree;
    if (lst != null) {
      for (java.util.Enumeration<OPT_LSTNode> e = lst.getRoot().getChildren(); e.hasMoreElements();) {
        processLoopNest(e.nextElement());
      }
    }
  }

  /**
   * Process all loop heads in a loop nest by inserting a backedge yieldpoint in each of them.
   */
  private void processLoopNest(OPT_LSTNode n) {
    for (java.util.Enumeration<OPT_LSTNode> e = n.getChildren(); e.hasMoreElements();) {
      processLoopNest(e.nextElement());
    }
    OPT_Instruction dest = n.header.firstInstruction();
    if (dest.position.getMethod().isInterruptible()) {
      prependYield(n.header, YIELDPOINT_BACKEDGE, dest.bcIndex, dest.position);
    }
  }

  /**
   * Add a YIELD instruction to the appropriate place for the basic 
   * block passed.
   *
   * @param bb the basic block
   * @param yp the yieldpoint operator to insert
   * @param bcIndex the bcIndex of the yieldpoint
   * @param position the source position of the yieldpoint
   */
  private void prependYield(OPT_BasicBlock bb,
                            OPT_Operator yp,
                            int bcIndex,
                            OPT_InlineSequence position) {
    OPT_Instruction insertionPoint = null;

    if (bb.isEmpty()) {
      insertionPoint = bb.lastInstruction();
    } else {
      insertionPoint = bb.firstRealInstruction();
    }

    if (yp == YIELDPOINT_PROLOGUE) {
      if (VM.VerifyAssertions) {
        VM._assert((insertionPoint != null) &&
                   (insertionPoint.getOpcode() == IR_PROLOGUE_opcode));
      }
      // put it after the prologue
      insertionPoint = insertionPoint.nextInstructionInCodeOrder();
    } else if (VM.UseEpilogueYieldPoints &&
               yp == YIELDPOINT_EPILOGUE) {
      // epilogues go before the return or athrow (at end of block)
      insertionPoint = bb.lastRealInstruction();
    }

    OPT_Instruction s = Empty.create(yp);
    insertionPoint.insertBefore(s);
    s.position = position;
    s.bcIndex = bcIndex;
  }
}




