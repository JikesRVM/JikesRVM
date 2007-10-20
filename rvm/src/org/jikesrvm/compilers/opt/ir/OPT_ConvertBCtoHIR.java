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
package org.jikesrvm.compilers.opt.ir;

import org.jikesrvm.compilers.opt.OPT_CompilerPhase;

/**
 * Translate from bytecodes to HIR
 */
public final class OPT_ConvertBCtoHIR extends OPT_CompilerPhase {

  public String getName() {
    return "Generate HIR";
  }

  /**
   * Generate HIR for ir.method into ir
   *
   * @param ir The IR to generate HIR into
   */
  public void perform(OPT_IR ir) {
    // Generate the cfg into gc
    OPT_GenerationContext gc = new OPT_GenerationContext(ir.method, ir.params, ir.compiledMethod, ir.options, ir.inlinePlan);
    OPT_BC2IR.generateHIR(gc);
    // Transfer HIR and misc state from gc to the ir object
    ir.gc = gc;
    ir.cfg = gc.cfg;
    ir.regpool = gc.temps;
    if (gc.allocFrame) {
      ir.stackManager.forceFrameAllocation();
    }
    // ir now contains well formed HIR.
    ir.IRStage = OPT_IR.HIR;
    ir.HIRInfo = new OPT_HIRInfo(ir);
    if (OPT_IR.SANITY_CHECK) {
      ir.verify("Initial HIR", true);
    }
  }

  // This phase contains no instance fields.
  public OPT_CompilerPhase newExecution(OPT_IR ir) {
    return this;
  }
}
