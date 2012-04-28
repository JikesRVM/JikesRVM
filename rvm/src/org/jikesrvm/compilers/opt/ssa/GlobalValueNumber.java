/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.ssa;

import org.jikesrvm.compilers.opt.DefUse;
import org.jikesrvm.compilers.opt.Simple;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.IR;

/**
 * This class implements global value numbering
 * ala Alpern, Wegman and Zadeck, PoPL 88.
 * See Muchnick p.348 for a nice discussion.
 */
class GlobalValueNumber extends CompilerPhase {
  /** Print verbose debugging output? */
  private static final boolean DEBUG = false;

  /**
   * Return this instance of this phase. This phase contains no
   * per-compilation instance fields.
   * @param ir not used
   * @return this
   */
  @Override
  public CompilerPhase newExecution(IR ir) {
    return this;
  }

  /**
   * Return the name of this phase.
   * @return "Global Value Number"
   */
  @Override
  public final String getName() {
    return "Global Value Number";
  }

  /**
   * Main driver for global value number-related computations
   * <p> PRECONDITION: Array SSA form
   * <p> POSTCONDITION: ir.valueNumbers holds global value number state
   */
  @Override
  public final void perform(IR ir) {
    if (ir.desiredSSAOptions.getAbort()) return;

    // make sure the SSA computation completed successfully
    // TODO if (!ir.SSAForm()) return;
    DefUse.computeDU(ir);
    DefUse.recomputeSSA(ir);

    // before doing global value numbering, get rid of
    // some troublesome dead code: <MOVE a = a> will
    // mess up GVN.  These opts also increase the power of GVN.
    // TODO: restructure these in a composite compiler phase.
    Simple.copyPropagation(ir);
    Simple.eliminateDeadInstructions(ir, true);
    //
    // compute global value numbers
    GlobalValueNumberState gvn = new GlobalValueNumberState(ir);

    if (DEBUG) {
      gvn.printValueNumbers();
    }
    ir.HIRInfo.valueNumbers = gvn;
  }
}
