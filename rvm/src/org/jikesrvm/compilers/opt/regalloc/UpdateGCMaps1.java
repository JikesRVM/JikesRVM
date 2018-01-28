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

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.GCIRMapElement;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.RegSpillListElement;
import org.jikesrvm.compilers.opt.ir.Register;

/**
 * Update GC maps after register allocation but before inserting spill
 * code.
 */
final class UpdateGCMaps1 extends CompilerPhase {

  @Override
  public boolean shouldPerform(OptOptions options) {
    return true;
  }

  /**
   * Return this instance of this phase. This phase contains no
   * per-compilation instance fields.
   * @param ir not used
   * @return this
   */
  @Override
  public CompilerPhase newExecution(IR ir) {
    return this;
  }

  @Override
  public String getName() {
    return "Update GCMaps 1";
  }

  @Override
  public boolean printingEnabled(OptOptions options, boolean before) {
    return false;
  }

  /**
   *  Iterate over the IR-based GC map collection and for each entry
   *  replace the symbolic reg with the real reg or spill it was allocated
   *  @param ir the IR
   */
  @Override
  public void perform(IR ir) {
    RegisterAllocatorState regAllocState = ir.MIRInfo.regAllocState;

    for (GCIRMapElement GCelement : ir.MIRInfo.gcIRMap) {
      if (LinearScan.GC_DEBUG) {
        VM.sysWrite("GCelement " + GCelement);
      }

      for (RegSpillListElement elem : GCelement.regSpillList()) {
        Register symbolic = elem.getSymbolicReg();

        if (LinearScan.GC_DEBUG) {
          VM.sysWriteln("get location for " + symbolic);
        }

        if (symbolic.isAllocated()) {
          Register ra = regAllocState.getMapping(symbolic);
          elem.setRealReg(ra);
          if (LinearScan.GC_DEBUG) {
            VM.sysWriteln(ra.toString());
          }

        } else if (symbolic.isSpilled()) {
          int spill = ir.MIRInfo.regAllocState.getSpill(symbolic);
          elem.setSpill(spill);
          if (LinearScan.GC_DEBUG) {
            VM.sysWriteln(Integer.toString(spill));
          }
        } else {
          OptimizingCompilerException.UNREACHABLE("LinearScan", "register not alive:", symbolic.toString());
        }
      }
    }
  }
}
