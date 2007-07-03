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
 * Driver routine for dominator tree computation
 */
public final class OPT_DominatorTreePhase extends OPT_CompilerPhase {

  /**
   * Should this phase be performed?
   * @param options controlling compiler options
   * @return true or false
   */
  public boolean shouldPerform(OPT_Options options) {
    // only perform if the dominators were successfully computed and
    // one of the following options are set.
    return options.SSA || options.PRINT_DOMINATORS;
  }

  /**
   * Returns "Dominator Tree"
   * @return "Dominator Tree"
   */
  public String getName() {
    return "Dominator Tree";
  }

  /**
   * Should the IR be printed before and/or after this phase?
   * @param options controlling compiler options
   * @param before query control
   * @return true or false.
   */
  public boolean printingEnabled(OPT_Options options, boolean before) {
    return false;
  }

  /**
   * Main driver.
   *
   * @param ir the governing IR
   */
  public void perform(OPT_IR ir) {
    // make sure the dominator computation completed successfully
    if (!ir.HIRInfo.dominatorsAreComputed) {
      return;
    }
    try {
      OPT_DominatorTree.perform(ir, true);
    } catch (OPT_OperationNotImplementedException e) {
      if (ir.options.PRINT_DOMINATORS || ir.options.PRINT_SSA) {
        OPT_Compiler.report(e.getMessage());
      }
    }
  }
}
