/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.opt.ir.*;
/**
 * Convert an IR object from HIR to LIR
 *
 * @author Dave Grove
 */
final class OPT_ConvertHIRtoLIR extends OPT_CompilerPhase {

  public final String getName () {
    return "HIR Operator Expansion";
  }

  public OPT_CompilerPhase newExecution (OPT_IR ir) {
    return this;
  }

  public final void perform (OPT_IR ir) {
    if (OPT_IR.SANITY_CHECK) {
      ir.verify("before conversion to LIR", true);
    }
    if (ir.options.STATIC_STATS) {
      // Print summary statistics (critpath, etc.) for all basic blocks
      OPT_DepGraphStats.printBasicBlockStatistics(ir);
    }
    // Do the conversion from HIR to LIR.
    ir.IRStage = OPT_IR.LIR;
    ir.LIRInfo = new OPT_LIRInfo(ir);
    OPT_ConvertToLowLevelIR.convert(ir, ir.options);
  }
}
