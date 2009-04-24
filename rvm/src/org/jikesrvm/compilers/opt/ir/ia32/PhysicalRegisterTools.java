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
package org.jikesrvm.compilers.opt.ir.ia32;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.GenericPhysicalRegisterTools;
import org.jikesrvm.compilers.opt.ir.MIR_Move;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FMOV;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_MOV;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_MOVSD;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_MOVSS;

import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.ia32.ArchConstants;

/**
 * This abstract class provides a set of useful methods for
 * manipulating physical registers for an IR.
 */
public abstract class PhysicalRegisterTools extends GenericPhysicalRegisterTools {

  /**
   * Return the governing IR.
   */
  public abstract IR getIR();

  /**
   * Create an MIR instruction to move rhs into lhs
   */
  public static Instruction makeMoveInstruction(RegisterOperand lhs, RegisterOperand rhs) {
    if (rhs.getRegister().isInteger() || rhs.getRegister().isLong() || rhs.getRegister().isAddress()) {
      if (VM.VerifyAssertions) {
        VM._assert(lhs.getRegister().isInteger() || lhs.getRegister().isLong() || lhs.getRegister().isAddress());
      }
      return MIR_Move.create(IA32_MOV, lhs, rhs);
    } else if (rhs.getRegister().isFloatingPoint()) {
      if (VM.VerifyAssertions) {
        VM._assert(lhs.getRegister().isFloatingPoint());
      }
      if (ArchConstants.SSE2_FULL) {
        if (rhs.getRegister().isFloat()) {
          return MIR_Move.create(IA32_MOVSS, lhs, rhs);
        } else {
          return MIR_Move.create(IA32_MOVSD, lhs, rhs);
        }
      } else {
        return MIR_Move.create(IA32_FMOV, lhs, rhs);
      }
    } else {
      OptimizingCompilerException.TODO("PhysicalRegisterTools.makeMoveInstruction");
      return null;
    }
  }
}
