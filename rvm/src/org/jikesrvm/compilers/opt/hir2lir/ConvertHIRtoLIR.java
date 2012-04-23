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
package org.jikesrvm.compilers.opt.hir2lir;

import org.jikesrvm.compilers.opt.depgraph.DepGraphStats;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.LIRInfo;

/**
 * Convert an IR object from HIR to LIR
 */
public final class ConvertHIRtoLIR extends CompilerPhase {

  @Override
  public String getName() {
    return "HIR Operator Expansion";
  }

  @Override
  public CompilerPhase newExecution(IR ir) {
    return this;
  }

  @Override
  public void perform(IR ir) {
    if (IR.SANITY_CHECK) {
      ir.verify("before conversion to LIR", true);
    }
    if (ir.options.PRINT_STATIC_STATS) {
      // Print summary statistics (critpath, etc.) for all basic blocks
      DepGraphStats.printBasicBlockStatistics(ir);
    }
    // Do the conversion from HIR to LIR.
    ir.IRStage = IR.LIR;
    ir.LIRInfo = new LIRInfo(ir);
    ConvertToLowLevelIR.convert(ir, ir.options);
  }
}
