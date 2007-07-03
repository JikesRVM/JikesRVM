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
import org.jikesrvm.compilers.opt.ir.OPT_LIRInfo;

/**
 * Convert an IR object from HIR to LIR
 */
final class OPT_ConvertHIRtoLIR extends OPT_CompilerPhase {

  public String getName() {
    return "HIR Operator Expansion";
  }

  public OPT_CompilerPhase newExecution(OPT_IR ir) {
    return this;
  }

  public void perform(OPT_IR ir) {
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
