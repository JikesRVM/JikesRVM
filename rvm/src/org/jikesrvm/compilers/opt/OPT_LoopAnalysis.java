/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003 - 2005
 */
package org.jikesrvm.compilers.opt;

import org.jikesrvm.compilers.opt.ir.OPT_IR;
/**
 * The driver that creates an annotated {@link OPT_AnnotatedLSTGraph}.
 *
 * @see OPT_AnnotatedLSTGraph
 *
 * @author Ian Rogers
 */
public class OPT_LoopAnalysis extends OPT_CompilerPhase {
  /**
	* Return a string name for this phase.
	* @return "Loop Analysis"
	*/
  public final String getName() {
	 return  "Loop Analysis";
  }

  /**
	* Should the optimisation be performed
	*/
  public boolean shouldPerform (OPT_Options options) {
	 return options.getOptLevel() >= 2;
  }

  /**
	* The main entry point
	* @param ir the IR to process
	*/
  public final void perform(OPT_IR ir) {
	 if (!ir.hasReachableExceptionHandlers()) {
		// Build LST tree and dominator info
		new OPT_DominatorsPhase(false).perform(ir);
		OPT_DefUse.computeDU(ir);
		// Build annotated version
		ir.HIRInfo.LoopStructureTree = new OPT_AnnotatedLSTGraph(ir, ir.HIRInfo.LoopStructureTree);
	 }
  }
}
