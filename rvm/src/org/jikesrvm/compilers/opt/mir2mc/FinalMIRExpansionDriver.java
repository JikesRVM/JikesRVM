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
package org.jikesrvm.compilers.opt.mir2mc;

import org.jikesrvm.ArchitectureSpecificOpt.FinalMIRExpansion;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.IR;

/**
 * A compiler phase that drives final MIR expansion.
 */
final class FinalMIRExpansionDriver extends CompilerPhase {
  public String getName() {
    return "Final MIR Expansion";
  }

  public boolean printingEnabled(OptOptions options, boolean before) {
    return !before && options.PRINT_FINAL_MIR;
  }

  // this class has no instance fields.
  public CompilerPhase newExecution(IR ir) {
    return this;
  }

  public void perform(IR ir) {
    if (IR.SANITY_CHECK) {
      ir.verify("right before Final MIR Expansion", true);
    }

    ir.MIRInfo.mcSizeEstimate = FinalMIRExpansion.expand(ir);
  }
}
