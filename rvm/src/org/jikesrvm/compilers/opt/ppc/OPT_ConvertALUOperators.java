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
package org.jikesrvm.compilers.opt.ppc;

import org.jikesrvm.compilers.opt.OPT_CompilerPhase;
import org.jikesrvm.compilers.opt.OPT_Options;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_Operators;

/**
 * Nothing to do on PowerPC.
 */
public abstract class OPT_ConvertALUOperators extends OPT_CompilerPhase implements OPT_Operators {

  /**
   * Return this instance of this phase. This phase contains no
   * per-compilation instance fields.
   * @param ir not used
   * @return this
   */
  public OPT_CompilerPhase newExecution(OPT_IR ir) {
    return this;
  }

  public final String getName() { return "ConvertALUOps"; }

  public final boolean printingEnabled(OPT_Options options, boolean before) {
    return false;
  }

  public final void perform(OPT_IR ir) {
    // Nothing to do on PPC
  }
}
