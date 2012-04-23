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

import java.lang.reflect.Constructor;

import org.jikesrvm.compilers.opt.OperationNotImplementedException;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.driver.OptimizingCompiler;
import org.jikesrvm.compilers.opt.ir.IR;

/**
 * Driver routine for dominator computation.  This phase invokes
 * the Lengauer-Tarjan dominator calculation.
 */
public final class DominatorsPhase extends CompilerPhase {

  /**
   * Should we unfactor the CFG?
   */
  private final boolean unfactor;

  /**
   * @param unfactor Should we unfactor the CFG before computing
   * dominators?
   */
  public DominatorsPhase(boolean unfactor) {
    super(new Object[]{unfactor});
    this.unfactor = unfactor;
  }

  /**
   * Constructor for this compiler phase
   */
  private static final Constructor<CompilerPhase> constructor =
      getCompilerPhaseConstructor(DominatorsPhase.class, new Class[]{Boolean.TYPE});

  /**
   * Get a constructor object for this compiler phase
   * @return compiler phase constructor
   */
  @Override
  public Constructor<CompilerPhase> getClassConstructor() {
    return constructor;
  }

  /**
   * Should this phase be performed?  This is a member of a composite
   * phase, so always return true.  The parent composite phase will
   * dictate.
   * @param options controlling compiler options
   */
  @Override
  public boolean shouldPerform(OptOptions options) {
    return true;
  }

  /**
   * Return a string representation of this phase
   * @return "Dominators + LpStrTree"
   */
  @Override
  public String getName() {
    return "Dominators + LpStrTree";
  }

  /**
   * Should the IR be printed before and/or after this phase?
   * @param options controlling compiler options
   * @param before query control
   * @return true or false
   */
  @Override
  public boolean printingEnabled(OptOptions options, boolean before) {
    return false;
  }

  /**
   * Main driver for the dominator calculation.
   */
  @Override
  public void perform(IR ir) {
    try {
      // reset flags in case an exception is thrown inside "perform"
      // and it doesn't return normally
      ir.HIRInfo.dominatorsAreComputed = false;

      // compute (forward) dominators,
      // leaves info in scratch object of basic blocks
      LTDominators.perform(ir, true, unfactor);

      // create the dominator tree, relies on dominator info being
      // in scratch object of basic blocks
      DominatorTree.perform(ir, true);

      // create the loop structured tree (LST)
      LSTGraph.perform(ir);

      // computation completed, so set flag
      ir.HIRInfo.dominatorsAreComputed = true;
    } catch (OperationNotImplementedException e) {
      OptimizingCompiler.report(e.getMessage());
    }
  }
}
