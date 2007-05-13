/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt;

import java.lang.reflect.Constructor;
import org.jikesrvm.compilers.opt.ir.OPT_IR;

/**
 * This phase puts the IR in SSA form and performs a set of simple
 * optimizations to clean up.  
 *
 */
final class OPT_SSATuneUp extends OPT_OptimizationPlanCompositeElement {

  /**
   * Build this phase as a composite of others.
   */
  OPT_SSATuneUp() {
    super("SSA Tune Up", new OPT_OptimizationPlanElement[] {
      // 1. Set up IR state to control SSA translation as needed
      new OPT_OptimizationPlanAtomicElement(new TuneUpPreparation()), 
      // 2. Get the desired SSA form
      new OPT_OptimizationPlanAtomicElement(new OPT_EnterSSA()), 
      // 3. Perform simple optimizations
      new OPT_OptimizationPlanAtomicElement(new OPT_Simple(true,true,false)),
      // 4. Perform expression simplification
      new OPT_OptimizationPlanAtomicElement(new FoldingDriver())
    });
  }

  public boolean shouldPerform(OPT_Options options) {
    return options.SSA;
  }

  /**
   * This class drives expression folding.
   */
  private static class FoldingDriver extends OPT_CompilerPhase {

    /**
     * Return this instance of this phase. This phase contains no
     * per-compilation instance fields.
     * @param ir not used
     * @return this 
     */
    public OPT_CompilerPhase newExecution (OPT_IR ir) {
      return this;
    }

    public final boolean shouldPerform(OPT_Options options) {
      return  options.SSA && options.EXPRESSION_FOLDING;
    }

    public final String getName() {
      return  "SSA Expression Folding";
    }

    /**
     * Execute expression folding. 
     */
    public final void perform(OPT_IR ir) {
      OPT_DefUse.computeDU(ir);
      OPT_ExpressionFolding.perform(ir);
    }
  }
  /**
   * This class sets up the IR state prior to entering SSA.
   */
  public static class TuneUpPreparation extends OPT_CompilerPhase {

	 /**
	  * Compiler phases necessary to re-build dominators and dominance
	  * frontier
	  */
	 private final OPT_CompilerPhase dominators, frontier;

	 public TuneUpPreparation () {
		dominators = new OPT_DominatorsPhase(true);
		frontier = new OPT_DominanceFrontier();
	 }

    /**
     * Constructor for this compiler phase
     */
    private static final Constructor<OPT_CompilerPhase> constructor = 
      getCompilerPhaseConstructor(TuneUpPreparation.class);

    /**
     * Get a constructor object for this compiler phase
     * @return compiler phase constructor
     */
    public Constructor<OPT_CompilerPhase> getClassConstructor() {
      return constructor;
    }

    public final boolean shouldPerform(OPT_Options options) {
      return  options.SSA;
    }

    public final String getName() {
      return  "SSA Tune UpPreparation";
    }

    /**
     * register in the IR the SSA properties we need for simple scalar
     * optimizations
     */
    public final void perform(OPT_IR ir) {
      ir.desiredSSAOptions = new OPT_SSAOptions();
      ir.desiredSSAOptions.setScalarsOnly(true);
      ir.desiredSSAOptions.setBackwards(false);
      ir.desiredSSAOptions.setInsertUsePhis(false);
		if(!ir.HIRInfo.dominatorsAreComputed) {
		  dominators.perform(ir);
		  frontier.perform(ir);
		}
    }
  }
}



