/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import  java.util.*;

/**
 * This class implements global value numbering 
 * ala Alpern, Wegman and Zadeck, PoPL 88.
 * See Muchnick p.348 for a nice discussion.
 *
 * @author Stephen Fink
 */
class OPT_GlobalValueNumber extends OPT_CompilerPhase {
  static final boolean DEBUG = false;

  /**
   * Should this phase be performed?
   * @param options controlling compiler options
   * @return true or false
   */
  final boolean shouldPerform (OPT_Options options) {
    // always perform this as a sub-phase
    return  true;
  }

  /**
   * Return the name of this phase.
   * @return "Global Value Number"
   */
  final String getName () {
    return  "Global Value Number";
  }

  /**
   * Should the IR be printed before and/or after this phase?
   * @param options controlling compiler options
   * @param before query control
   * @return true or false
   */
  final boolean printingEnabled (OPT_Options options, boolean before) {
    return  false;
  }

  /** 
   * Main driver for global value number-related computations
   * <p> PRECONDITION: Array SSA form
   * <p> POSTCONDITION: ir.valueNumbers holds global value number state
   *
   * @param ir the governing IR
   */
  final public void perform (OPT_IR ir) {
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
    gvn.globalValueNumber();
    if (DEBUG)
      gvn.printValueNumbers();
    ir.HIRInfo.valueNumbers = gvn;
  }
}
