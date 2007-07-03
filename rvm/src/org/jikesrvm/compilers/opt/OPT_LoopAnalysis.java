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
 * The driver that creates an annotated {@link OPT_AnnotatedLSTGraph}.
 *
 * @see OPT_AnnotatedLSTGraph
 */
public class OPT_LoopAnalysis extends OPT_CompilerPhase {
  /**
   * Return a string name for this phase.
   * @return "Loop Analysis"
   */
  public final String getName() {
    return "Loop Analysis";
  }

  /**
   * Should the optimisation be performed
   */
  public boolean shouldPerform(OPT_Options options) {
    return options.getOptLevel() >= 3;
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
