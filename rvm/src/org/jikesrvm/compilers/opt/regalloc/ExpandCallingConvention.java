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
package org.jikesrvm.compilers.opt.regalloc;

import org.jikesrvm.ArchitectureSpecificOpt.CallingConvention;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.IR;

/**
 *  Phase for expanding the calling convention
 */
public final class ExpandCallingConvention extends CompilerPhase {

  /**
   * Return this instance of this phase. This phase contains no
   * per-compilation instance fields.
   * @param ir not used
   * @return this
   */
  public CompilerPhase newExecution(IR ir) {
    return this;
  }

  public boolean printingEnabled(OptOptions options, boolean before) {
    return options.PRINT_CALLING_CONVENTIONS && !before;
  }

  public String getName() {
    return "Expand Calling Convention";
  }

  public void perform(org.jikesrvm.compilers.opt.ir.IR ir) {
    CallingConvention.expandCallingConventions(ir);
  }
}
