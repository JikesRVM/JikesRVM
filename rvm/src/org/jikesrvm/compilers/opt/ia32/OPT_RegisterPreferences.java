/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt.ia32;

import org.jikesrvm.compilers.opt.OPT_GenericRegisterPreferences;
import org.jikesrvm.compilers.opt.ir.MIR_Move;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_InstructionEnumeration;
import org.jikesrvm.compilers.opt.ir.OPT_Operand;
import org.jikesrvm.compilers.opt.ir.OPT_Operators;
import org.jikesrvm.compilers.opt.ir.OPT_Register;

public class OPT_RegisterPreferences extends OPT_GenericRegisterPreferences implements OPT_Operators {

  /**
   * Set up register preferences based on instructions in an IR.
   */
  public void initialize(OPT_IR ir) {

    for (OPT_InstructionEnumeration e = ir.forwardInstrEnumerator(); e.hasMoreElements();) {
      OPT_Instruction s = e.nextElement();
      switch (s.operator.opcode) {
        case IA32_MOV_opcode:
          // add affinities produced by MOVE instructions
          OPT_Operand result = MIR_Move.getResult(s);
          OPT_Operand value = MIR_Move.getValue(s);
          if (result.isRegister() && value.isRegister()) {
            OPT_Register r1 = result.asRegister().register;
            OPT_Register r2 = value.asRegister().register;
            addAffinity(1, r1, r2);
          }
          break;
        default:
          break;
      }
    }
  }
}
