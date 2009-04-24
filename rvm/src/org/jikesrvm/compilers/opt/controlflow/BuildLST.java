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

import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.IR;

/**
 * A compiler phase to construct the loop structure tree (LST).
 * The steps are (1) construct approximate dominators (ie blocks are
 * not unfactored) and (2) build the LST.
 *
 * @see LTDominators
 * @see LSTGraph
 */
public class BuildLST extends CompilerPhase {
  public String getName() { return "Build LST"; }

  /**
   * This phase contains no per-compilation instance fields.
   */
  public final CompilerPhase newExecution(IR ir) {
    return this;
  }

  /**
   * Build the Loop Structure Tree (LST) for the given IR.
   * NOTE: CFG must be reducible.
   * TODO: Detect irreducible CFG, apply node splitting and then construct LST.
   *
   * @param ir the IR on which to apply the phase
   */
  public void perform(IR ir) {
    ir.cfg.compactNodeNumbering();
    LTDominators.approximate(ir, true);
    DominatorTree.perform(ir, true);
    LSTGraph.perform(ir);
  }
}
