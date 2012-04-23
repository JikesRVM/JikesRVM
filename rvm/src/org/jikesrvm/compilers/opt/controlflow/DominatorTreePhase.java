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
package org.jikesrvm.compilers.opt.controlflow;

import org.jikesrvm.compilers.opt.OperationNotImplementedException;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.driver.OptimizingCompiler;
import org.jikesrvm.compilers.opt.ir.IR;

/**
 * Driver routine for dominator tree computation
 */
public final class DominatorTreePhase extends CompilerPhase {

  /**
   * Should this phase be performed?
   * @param options controlling compiler options
   * @return true or false
   */
  @Override
  public boolean shouldPerform(OptOptions options) {
    // only perform if the dominators were successfully computed and
    // one of the following options are set.
    return options.SSA || options.PRINT_DOMINATORS;
  }

  /**
   * Returns "Dominator Tree"
   * @return "Dominator Tree"
   */
  @Override
  public String getName() {
    return "Dominator Tree";
  }

  /**
   * Should the IR be printed before and/or after this phase?
   * @param options controlling compiler options
   * @param before query control
   * @return true or false.
   */
  @Override
  public boolean printingEnabled(OptOptions options, boolean before) {
    return false;
  }

  /**
   * Main driver.
   *
   * @param ir the governing IR
   */
  @Override
  public void perform(IR ir) {
    // make sure the dominator computation completed successfully
    if (!ir.HIRInfo.dominatorsAreComputed) {
      return;
    }
    try {
      DominatorTree.perform(ir, true);
    } catch (OperationNotImplementedException e) {
      if (ir.options.PRINT_DOMINATORS || ir.options.PRINT_SSA) {
        OptimizingCompiler.report(e.getMessage());
      }
    }
  }
}
