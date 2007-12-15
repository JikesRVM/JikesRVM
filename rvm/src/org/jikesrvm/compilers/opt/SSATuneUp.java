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

import java.lang.reflect.Constructor;
import org.jikesrvm.compilers.opt.ir.IR;

/**
 * This phase puts the IR in SSA form and performs a set of simple
 * optimizations to clean up.
 */
final class SSATuneUp extends OptimizationPlanCompositeElement {

  /**
   * Build this phase as a composite of others.
   */
  SSATuneUp() {
    super("SSA Tune Up", new OptimizationPlanElement[]{
        // 1. Set up IR state to control SSA translation as needed
        new OptimizationPlanAtomicElement(new TuneUpPreparation()),
        // 2. Get the desired SSA form
        new OptimizationPlanAtomicElement(new EnterSSA()),
        // 3. Perform simple optimizations
        new OptimizationPlanAtomicElement(new Simple(true, true, false)),
        // 4. Perform expression simplification
        new OptimizationPlanAtomicElement(new FoldingDriver())});
  }

  public boolean shouldPerform(Options options) {
    return options.SSA;
  }

  /**
   * This class drives expression folding.
   */
  private static class FoldingDriver extends CompilerPhase {

    /**
     * Return this instance of this phase. This phase contains no
     * per-compilation instance fields.
     * @param ir not used
     * @return this
     */
    public CompilerPhase newExecution(IR ir) {
      return this;
    }

    public final boolean shouldPerform(Options options) {
      return options.SSA && options.EXPRESSION_FOLDING;
    }

    public final String getName() {
      return "SSA Expression Folding";
    }

    /**
     * Execute expression folding.
     */
    public final void perform(IR ir) {
      DefUse.computeDU(ir);
      ExpressionFolding.perform(ir);
    }
  }

  /**
   * This class sets up the IR state prior to entering SSA.
   */
  public static class TuneUpPreparation extends CompilerPhase {

    /**
     * Compiler phases necessary to re-build dominators and dominance
     * frontier
     */
    private final CompilerPhase dominators, frontier;

    public TuneUpPreparation() {
      dominators = new DominatorsPhase(true);
      frontier = new DominanceFrontier();
    }

    /**
     * Constructor for this compiler phase
     */
    private static final Constructor<CompilerPhase> constructor =
        getCompilerPhaseConstructor(TuneUpPreparation.class);

    /**
     * Get a constructor object for this compiler phase
     * @return compiler phase constructor
     */
    public Constructor<CompilerPhase> getClassConstructor() {
      return constructor;
    }

    public final boolean shouldPerform(Options options) {
      return options.SSA;
    }

    public final String getName() {
      return "SSA Tune UpPreparation";
    }

    /**
     * register in the IR the SSA properties we need for simple scalar
     * optimizations
     */
    public final void perform(IR ir) {
      ir.desiredSSAOptions = new SSAOptions();
      ir.desiredSSAOptions.setScalarsOnly(true);
      ir.desiredSSAOptions.setBackwards(false);
      ir.desiredSSAOptions.setInsertUsePhis(false);
      if (!ir.HIRInfo.dominatorsAreComputed) {
        dominators.perform(ir);
        frontier.perform(ir);
      }
    }
  }
}



