/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;

/**
 * This class inserts yield points in
 *  1) a method's prologue 
 *  2) targets of backwards branches
 *  3) (optionally) exits of a method 
 *
 * <p> Simple algorithm for targets of backwards branches: if there's
 *      an edge from basic block i -> basic block j
 *      and i >= j, add a yield point to basic block j
 *
 * @author Stephen Fink
 * @author Dave Grove
 * @author Michael Hind
 */
class OPT_YieldPoints extends OPT_CompilerPhase
  implements OPT_Operators, OPT_Constants {

  /**
   * Should this phase be performed?
   * @param options controlling compiler options
   * @return true or false
   */
  final boolean shouldPerform(OPT_Options options) {
    return !options.NO_THREADS;
  }

  /**
   * Return the name of this phase
   * @return "Yield Point Insertion"
   */
  final String getName() {
    return  "Yield Point Insertion";
  }

  /**
   * This phase contains no per-compilation instance fields.
   */
  final OPT_CompilerPhase newExecution(OPT_IR ir) {
    return  this;
  }

  /**
   * Insert yield points in method prologues, back edges, and method exits
   * 
   * @param ir the governing IR
   */
  final public void perform (OPT_IR ir) {
    if (!ir.method.isInterruptible()) {
      return;   // don't insert yieldpoints in Uninterruptible code.
    }
    
    // renumber basic blocks 
    ir.cfg.compactNodeNumbering();
    boolean[] hasYP = new boolean[ir.cfg.numberOfNodes + 1];

    // (1) Insert prologue yieldpoint unconditionally.
    //     As part of prologue/epilogue insertion we'll remove
    //     the yieldpoints in trival methods that otherwise wouldn't need 
    //     a stackframe.
    prependYield(ir.cfg.entry(), YIELDPOINT_PROLOGUE, 0, ir.gc.inlineSequence);
    
    // (2) Walk basic blocks, looking for backward branches or method exits via
    //      returns or throws
    for (OPT_BasicBlockEnumeration e = ir.getBasicBlocks(); 
	 e.hasMoreElements();) {
      OPT_BasicBlock block = e.next();

      if (VM.UseEpilogueYieldPoints && 
	  (block.hasReturn() || block.hasAthrowInst())) {
	prependYield(block, YIELDPOINT_EPILOGUE, INSTRUMENTATION_BCI, 
		     ir.gc.inlineSequence);
      } else {
	// look for a backwards branch; if we find one insert yp at target.
	for (OPT_InstructionEnumeration enum = block.enumerateBranchInstructions(); 
	     enum.hasMoreElements();) {
	  OPT_Instruction instr = enum.next();
	  for (OPT_BasicBlockEnumeration enum2 = instr.getBranchTargets();
	       enum2.hasMoreElements();) {
	    OPT_BasicBlock destBB = enum2.next();
	    int bbNum = destBB.getNumber();
	    if (bbNum <= block.getNumber() && 
		bbNum < hasYP.length && 
		!hasYP[bbNum]) {
	      OPT_Instruction dest = destBB.firstInstruction();
	      if (dest.position.getMethod().isInterruptible()) {
		prependYield(destBB, YIELDPOINT_BACKEDGE, dest.bcIndex, dest.position);
		hasYP[bbNum] = true;
	      }
	    }
	  }
	}
      }
    }
  }

  /**
   * Add a YIELD instruction to the appropriate place for the basic 
   *   block passed.
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
	VM.assert((insertionPoint != null) && 
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




