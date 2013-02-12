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

import org.jikesrvm.compilers.opt.DefUse;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.IR;

/**
 * The driver that creates an annotated {@link AnnotatedLSTGraph}.
 *
 * @see AnnotatedLSTGraph
 */
public class LoopAnalysis extends CompilerPhase {
  /**
   * Return a string name for this phase.
   * @return "Loop Analysis"
   */
  @Override
  public final String getName() {
    return "Loop Analysis";
  }

  /**
   * This phase is disabled by default.
   * <p>
   * It will run only on O3 but O2 is the default maximum optimization level.
   */
  @Override
  public boolean shouldPerform(OptOptions options) {
    return options.getOptLevel() >= 3;
  }

  @Override
  public final void perform(IR ir) {
    if (!ir.hasReachableExceptionHandlers()) {
      // Build LST tree and dominator info
      new DominatorsPhase(false).perform(ir);
      DefUse.computeDU(ir);
      // Build annotated version
      ir.HIRInfo.loopStructureTree = new AnnotatedLSTGraph(ir, ir.HIRInfo.loopStructureTree);
    }
  }
}
