/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.jikesrvm.opt;

import com.ibm.jikesrvm.opt.ir.*;

/**
 * This class implements global value numbering 
 * ala Alpern, Wegman and Zadeck, PoPL 88.
 * See Muchnick p.348 for a nice discussion.
 *
 * @author Stephen Fink
 */
class OPT_GlobalValueNumber extends OPT_CompilerPhase {
  /** Print verbose debugging output? */
  private static final boolean DEBUG = false;

  /**
   * Return this instance of this phase. This phase contains no
   * per-compilation instance fields.
   * @param ir not used
   * @return this
   */
  public OPT_CompilerPhase newExecution(OPT_IR ir) {
    return this;
  }

  /**
   * Return the name of this phase.
   * @return "Global Value Number"
   */
  public final String getName () {
    return  "Global Value Number";
  }

  /** 
   * Main driver for global value number-related computations
   * <p> PRECONDITION: Array SSA form
   * <p> POSTCONDITION: ir.valueNumbers holds global value number state
   *
   * @param ir the governing IR
   */
  public final void perform (OPT_IR ir) {
    if (ir.desiredSSAOptions.getAbort()) return;
    
    // make sure the SSA computation completed successfully
    // TODO if (!ir.SSAForm()) return;
    OPT_DefUse.computeDU(ir);
    OPT_DefUse.recomputeSSA(ir);

    // before doing global value numbering, get rid of
    // some troublesome dead code: <MOVE a = a> will
    // mess up GVN.  These opts also increase the power of GVN.
    // TODO: restructure these in a composite compiler phase.
    OPT_Simple.copyPropagation(ir);
    OPT_Simple.eliminateDeadInstructions(ir, true);
    //
    // compute global value numbers
    OPT_GlobalValueNumberState gvn = new OPT_GlobalValueNumberState(ir);

    if (DEBUG)
      gvn.printValueNumbers();
    ir.HIRInfo.valueNumbers = gvn;
  }
}
