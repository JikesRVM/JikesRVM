/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.opt.ir.OPT_IR;
/**
 * A compiler phase to construct the loop structure tree (LST).
 * The steps are (1) construct approximate dominators (ie blocks are 
 * not unfactored) and (2) build the LST.
 * 
 * @see OPT_LTDominators
 * @see OPT_LSTGraph
 *
 * @author Dave Grove
 */
public class OPT_BuildLST extends OPT_CompilerPhase {
  public String getName () { return  "Build LST"; }

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
  public void perform (OPT_IR ir) {
    ir.cfg.compactNodeNumbering();
    OPT_LTDominators.approximate(ir, true);
    OPT_DominatorTree.perform(ir, true);
    OPT_LSTGraph.perform(ir);
  }
}
