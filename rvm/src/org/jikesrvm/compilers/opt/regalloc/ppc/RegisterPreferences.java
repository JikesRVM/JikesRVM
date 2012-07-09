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
package org.jikesrvm.compilers.opt.regalloc.ppc;

import org.jikesrvm.compilers.opt.regalloc.GenericRegisterPreferences;
import org.jikesrvm.compilers.opt.ir.MIR_Move;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.InstructionEnumeration;
import org.jikesrvm.compilers.opt.ir.Operators;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.operand.Operand;

/**
 * An instance of this class provides a mapping from symbolic register to
 * physical register, representing a preferred register assignment.
 */
public abstract class RegisterPreferences extends GenericRegisterPreferences implements Operators {

  /**
   * If the following is set, we use a heuristic optimization as follows:
   * weight a
   * <pre>
   *                    MOVE symbolic = symbolic
   * </pre>
   * TWICE as much as either of:
   * <pre>
   *                    MOVE symbolic = physical,
   *                    MOVE physical = symbolic.
   * </pre>
   * <p>
   * Rationale: At this point (before register allocation), the second
   * class of moves appear only due to calling conventions, parameters,
   * and return values.  We posit that the dynamic frequency of these
   * MOVES will be smaller than the frequency of an average move.
   */
  private static final boolean SYMBOLIC_SYMBOLIC_HEURISTIC = true;

  /**
   * Set up register preferences based on instructions in an IR.
   */
  @Override
  public void initialize(IR ir) {
    for (InstructionEnumeration e = ir.forwardInstrEnumerator(); e.hasMoreElements();) {
      Instruction s = e.nextElement();
      switch (s.operator.opcode) {
        case PPC_MOVE_opcode:
          // add affinities produced by MOVE instructions
          Operand result = MIR_Move.getResult(s);
          Operand value = MIR_Move.getValue(s);
          if (result.isRegister() && value.isRegister()) {
            Register r1 = result.asRegister().getRegister();
            Register r2 = value.asRegister().getRegister();
            addAffinity(1, r2, r1);

            // double the affinities if using the heuristic described
            // above.
            if (SYMBOLIC_SYMBOLIC_HEURISTIC && r1.isSymbolic() && r2.isSymbolic()) {
              addAffinity(1, r2, r1);
            }
          }
          break;
        default:
          break;
      }
    }
  }
}
