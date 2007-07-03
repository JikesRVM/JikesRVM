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

import org.jikesrvm.ArchitectureSpecific.OPT_CallingConvention;
import org.jikesrvm.compilers.opt.ir.OPT_IR;

/**
 *  Phase for expanding the calling convention
 */
public final class OPT_ExpandCallingConvention extends OPT_CompilerPhase {

  /**
   * Return this instance of this phase. This phase contains no
   * per-compilation instance fields.
   * @param ir not used
   * @return this
   */
  public OPT_CompilerPhase newExecution(OPT_IR ir) {
    return this;
  }

  public boolean printingEnabled(OPT_Options options, boolean before) {
    return options.PRINT_CALLING_CONVENTIONS && !before;
  }

  public String getName() {
    return "Expand Calling Convention";
  }

  public void perform(org.jikesrvm.compilers.opt.ir.OPT_IR ir) {
    OPT_CallingConvention.expandCallingConventions(ir);
  }
}
