/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import java.util.Enumeration;
import com.ibm.JikesRVM.opt.ir.*;

/**
 * An instance of this class provides a mapping from symbolic register to
 * physical register, representing a preferred register assignment.
 * 
 * @author Stephen Fink
 */
final class OPT_RegisterPreferences extends OPT_GenericRegisterPreferences
implements OPT_Operators {

  /**
   * If the following is set, we use a heuristic optimization as follows:
   * weight a  
   *                    MOVE symbolic = symbolic 
   * TWICE as much as either of:
   *                    MOVE symbolic = physical, 
   *                    MOVE physical = symbolic.
   *
   * Rationale: At this point (before register allocation), the second
   * class of moves appear only due to calling conventions, parameters,
   * and return values.  We posit that the dynamic frequency of these
   * MOVES will be smaller than the frequency of an average move.
   */
  private final static boolean SYMBOLIC_SYMBOLIC_HEURISTIC = true;

  /**
   * Set up register preferences based on instructions in an IR.
   */
  void initialize(OPT_IR ir) {
    for (Enumeration e = ir.forwardInstrEnumerator(); 
         e.hasMoreElements();) {
      OPT_Instruction s = (OPT_Instruction)e.nextElement();
      switch (s.operator.opcode) {
        case PPC_MOVE_opcode:
          // add affinities produced by MOVE instructions
          OPT_Operand result = MIR_Move.getResult(s);
          OPT_Operand value = MIR_Move.getValue(s);
          if (result.isRegister() && value.isRegister()) {
            OPT_Register r1 = result.asRegister().register;
            OPT_Register r2 = value.asRegister().register;
            addAffinity(1,r2,r1);

            // double the affinities if using the heuristic described
            // above.
            if (SYMBOLIC_SYMBOLIC_HEURISTIC && r1.isSymbolic() && 
                r2.isSymbolic()) {
              addAffinity(1,r2,r1);
            }
          }
          break;
        default:
          break;
      }
    }
  }
}
