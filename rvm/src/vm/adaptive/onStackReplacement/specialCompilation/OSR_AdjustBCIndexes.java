/*
 * This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;

import com.ibm.JikesRVM.opt.*;
import com.ibm.JikesRVM.opt.ir.*;
/**
 * OSR_AdjustBCIndex is an optimizing phase performed on HIR.
 * It adjust the byte code index of instructions from specialized
 * byte code to its original byte code.
 *
 * @author Feng Qian
 */

public class OSR_AdjustBCIndexes extends OPT_CompilerPhase {

  public final boolean shouldPerform(OPT_Options options){
    return true;
  }

  /**
   * Return this instance of this phase. This phase contains no
   * per-compilation instance fields.
   * @param ir not used
   * @return this 
   */
  public OPT_CompilerPhase newExecution (OPT_IR ir) {
    return this;
  }

  public final String getName() { return "AdjustBytecodeIndexes"; }

  public final void perform(OPT_IR ir) {
    if (!ir.method.isForOsrSpecialization()) return;    
    int offset = ir.method.getOsrPrologueLength();

    for (OPT_InstructionEnumeration ie = ir.forwardInstrEnumerator(); 
         ie.hasMoreElements();) {
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
