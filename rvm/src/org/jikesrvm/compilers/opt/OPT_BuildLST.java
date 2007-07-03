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
 * A compiler phase to construct the loop structure tree (LST).
 * The steps are (1) construct approximate dominators (ie blocks are
 * not unfactored) and (2) build the LST.
 *
 * @see OPT_LTDominators
 * @see OPT_LSTGraph
 */
public class OPT_BuildLST extends OPT_CompilerPhase {
  public String getName() { return "Build LST"; }

  /**
   * This phase contains no per-compilation instance fields.
   */
  public final OPT_CompilerPhase newExecution(OPT_IR ir) {
    return this;
  }

  /**
   * Build the Loop Structure Tree (LST) for the given IR.
   * NOTE: CFG must be reducible.
   * TODO: Detect irreducible CFG, apply node splitting and then construct LST.
   *
   * @param ir the IR on which to apply the phase
   */
  public void perform(OPT_IR ir) {
    ir.cfg.compactNodeNumbering();
    OPT_LTDominators.approximate(ir, true);
    OPT_DominatorTree.perform(ir, true);
    OPT_LSTGraph.perform(ir);
  }
}
