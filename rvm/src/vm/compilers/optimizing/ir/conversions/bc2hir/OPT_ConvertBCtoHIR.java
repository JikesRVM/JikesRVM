/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt.ir;

import com.ibm.JikesRVM.opt.*;

/**
 * Translate from bytecodes to HIR
 *
 * @author Dave Grove
 */
public final class OPT_ConvertBCtoHIR extends OPT_CompilerPhase {
  public OPT_ConvertBCtoHIR() {}

  public final String getName () { 
    return "Generate HIR";
  }

  /**
   * Generate HIR for ir.method into ir
   * 
   * @param ir The IR to generate HIR into
   */
  public final void perform (OPT_IR ir) {
    // Generate the cfg into gc
    OPT_GenerationContext gc = 
      new OPT_GenerationContext(ir.method, ir.compiledMethod, 
                                ir.options, ir.inlinePlan);
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
  public OPT_CompilerPhase newExecution (OPT_IR ir) {
    return this;
  }
}
