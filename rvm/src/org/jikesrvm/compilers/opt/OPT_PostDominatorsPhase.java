/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt;

import org.jikesrvm.compilers.opt.ir.OPT_IR;

/**
 * Driver routine for post-dominator computation.  This phase invokes
 * the Lengauer-Tarjan dominator calculation.
 */
public final class OPT_PostDominatorsPhase extends OPT_CompilerPhase {

  /**
   * Should we unfactor the CFG?
   */
  private boolean unfactor = false;

  /**
   * @param unfactor Should we unfactor the CFG before computing
   * dominators?
   */
  public OPT_PostDominatorsPhase(boolean unfactor) {
    this.unfactor = unfactor;
  }

  /**
   * Should this phase be performed?  This is a member of a composite
   * phase, so always return true.  The parent composite phase will
   * dictate.
   * @param options controlling compiler options
   */
  public boolean shouldPerform(OPT_Options options) {
    return true;
  }

  /**
   * Return a string representation of this phase
   * @return "Post-Dominators"
   */
  public String getName() {
    return "Post-Dominators";
  }

  /**
   * Should the IR be printed before and/or after this phase?
   * @param options controlling compiler options
   * @param before query control
   * @return true or false
   */
  public boolean printingEnabled(OPT_Options options, boolean before) {
    return false;
  }

  /**
   * Main driver for the post-dominator calculation.
   */
  public void perform(OPT_IR ir) {
    try {
      // reset flags in case an exception is thrown inside "perform"
      // and it doesn't return normally
      ir.HIRInfo.postDominatorsAreComputed = false;

      // compute post-dominators,
      // leaves info in scratch object of basic blocks
      OPT_LTDominators.perform(ir, false, unfactor);

      // create the dominator tree, relies on dominator info being
      // in scratch object of basic blocks
      OPT_DominatorTree.perform(ir, false);

      // computation completed, so set flag
      ir.HIRInfo.postDominatorsAreComputed = true;

    } catch (OPT_OperationNotImplementedException e) {
      OPT_Options options = ir.options;
      if (options.PRINT_POST_DOMINATORS) {
        OPT_Compiler.report(e.getMessage());
      }
    }
  }
}
