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
package org.jikesrvm.osr;

import org.jikesrvm.compilers.opt.OPT_CompilerPhase;
import org.jikesrvm.compilers.opt.OPT_Constants;
import org.jikesrvm.compilers.opt.OPT_Options;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_InlineSequence;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_InstructionEnumeration;

/**
 * OSR_AdjustBCIndex is an optimizing phase performed on HIR.
 * It adjust the byte code index of instructions from specialized
 * byte code to its original byte code.
 */

public class OSR_AdjustBCIndexes extends OPT_CompilerPhase {

  public final boolean shouldPerform(OPT_Options options) {
    return true;
  }

  /**
   * Return this instance of this phase. This phase contains no
   * per-compilation instance fields.
   * @param ir not used
   * @return this
   */
  public OPT_CompilerPhase newExecution(OPT_IR ir) {
    return this;
  }

  public final String getName() { return "AdjustBytecodeIndexes"; }

  public final void perform(OPT_IR ir) {
    if (!ir.method.isForOsrSpecialization()) return;
    int offset = ir.method.getOsrPrologueLength();

    for (OPT_InstructionEnumeration ie = ir.forwardInstrEnumerator(); ie.hasMoreElements();) {
      OPT_Instruction s = ie.next();

      if ((s.position != null) && (s.position.method != ir.method)) {
        // also adjust InlineSequence of the direct callee
        OPT_InlineSequence caller = s.position.caller;
        if ((caller != null) && (caller.method == ir.method)) {
          // adjust the call site's bcIndex
          s.position.bcIndex -= offset;
        }
        continue;
      }

      if (s.bcIndex >= offset) {
        s.bcIndex -= offset;
      } else if (s.bcIndex >= 0) {
        s.bcIndex = OPT_Constants.OSR_PROLOGUE;
      }
    }
  }
}
