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
package org.jikesrvm.osr;

import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.driver.OptConstants;
import org.jikesrvm.compilers.opt.inlining.InlineSequence;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.InstructionEnumeration;

/**
 * OSR_AdjustBCIndex is an optimizing phase performed on HIR.
 * It adjust the byte code index of instructions from specialized
 * byte code to its original byte code.
 */

public class AdjustBCIndexes extends CompilerPhase {

  public final boolean shouldPerform(OptOptions options) {
    return true;
  }

  /**
   * Return this instance of this phase. This phase contains no
   * per-compilation instance fields.
   * @param ir not used
   * @return this
   */
  public CompilerPhase newExecution(IR ir) {
    return this;
  }

  public final String getName() { return "AdjustBytecodeIndexes"; }

  public final void perform(IR ir) {
    if (!ir.method.isForOsrSpecialization()) return;
    int offset = ir.method.getOsrPrologueLength();

    for (InstructionEnumeration ie = ir.forwardInstrEnumerator(); ie.hasMoreElements();) {
      Instruction s = ie.next();

      if ((s.position != null) && (s.position.method != ir.method)) {
        // also adjust InlineSequence of the direct callee
        InlineSequence caller = s.position.caller;
        if ((caller != null) && (caller.method == ir.method)) {
          // adjust the call site's bcIndex
          s.position.bcIndex -= offset;
        }
        continue;
      }

      if (s.bcIndex >= offset) {
        s.bcIndex -= offset;
      } else if (s.bcIndex >= 0) {
        s.bcIndex = OptConstants.OSR_PROLOGUE;
      }
    }
  }
}
