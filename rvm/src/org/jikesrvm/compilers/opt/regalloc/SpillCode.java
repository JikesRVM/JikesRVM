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

import java.util.Enumeration;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Operators;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;

/**
 * Insert Spill Code after register assignment.
 */
final class SpillCode extends CompilerPhase {
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
  public boolean shouldPerform(OptOptions options) {
    return true;
  }

  @Override
  public String getName() {
    return "Spill Code";
  }

  @Override
  public boolean printingEnabled(OptOptions options, boolean before) {
    return false;
  }

  /**
   *  @param ir the IR
   */
  @Override
  public void perform(IR ir) {
    replaceSymbolicRegisters(ir);

    // Generate spill code if necessary
    if (ir.hasSysCall() || ir.MIRInfo.linearScanState.spilledSomething) {
      GenericStackManager stackMan = ir.stackManager;
      stackMan.insertSpillCode(ir.MIRInfo.linearScanState.active);
    }

    if (VM.BuildForIA32 && !VM.BuildForSSE2Full) {
      Operators.helper.rewriteFPStack(ir);
    }
  }

  /**
   *  Iterates over the IR and replace each symbolic register with its
   *  allocated physical register.
   *
   *  @param ir the IR to process
   */
  private static void replaceSymbolicRegisters(IR ir) {
    for (Enumeration<Instruction> inst = ir.forwardInstrEnumerator(); inst.hasMoreElements();) {
      Instruction s = inst.nextElement();
      for (Enumeration<Operand> ops = s.getOperands(); ops.hasMoreElements();) {
        Operand op = ops.nextElement();
        if (op.isRegister()) {
          RegisterOperand rop = op.asRegister();
          Register r = rop.getRegister();
          if (r.isSymbolic() && !r.isSpilled()) {
            Register p = RegisterAllocatorState.getMapping(r);
            if (VM.VerifyAssertions) VM._assert(p != null);
            rop.setRegister(p);
          }
        }
      }
    }
  }

}
