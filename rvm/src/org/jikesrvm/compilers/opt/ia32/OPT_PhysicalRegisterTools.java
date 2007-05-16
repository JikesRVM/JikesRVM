/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt.ia32;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.OPT_GenericPhysicalRegisterTools;
import org.jikesrvm.compilers.opt.OPT_OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.MIR_Move;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_FMOV;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_MOV;
import org.jikesrvm.compilers.opt.ir.OPT_RegisterOperand;

/**
 * This abstract class provides a set of useful methods for
 * manipulating physical registers for an IR.
 */
public abstract class OPT_PhysicalRegisterTools extends OPT_GenericPhysicalRegisterTools {

  /**
   * Return the governing IR.
   */
  public abstract OPT_IR getIR();

  /**
   * Create an MIR instruction to move rhs into lhs
   */
  public static OPT_Instruction makeMoveInstruction(OPT_RegisterOperand lhs, OPT_RegisterOperand rhs) {
    if (rhs.register.isInteger() || rhs.register.isLong() || rhs.register.isAddress()) {
      if (VM.VerifyAssertions) {
        VM._assert(lhs.register.isInteger() || lhs.register.isLong() || lhs.register.isAddress());
      }
      return MIR_Move.create(IA32_MOV, lhs, rhs);
    } else if (rhs.register.isFloatingPoint()) {
      if (VM.VerifyAssertions) {
        VM._assert(lhs.register.isFloatingPoint());
      }
      return MIR_Move.create(IA32_FMOV, lhs, rhs);
    } else {
      OPT_OptimizingCompilerException.TODO("OPT_PhysicalRegisterTools.makeMoveInstruction");
      return null;
    }
  }
}
