/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import java.util.Enumeration;
import com.ibm.JikesRVM.opt.ir.*;

/**
 * This abstract class provides a set of useful architecture-independent 
 * methods for
 * manipulating physical registers for an IR.
 *
 * @author Jong-Deok Choi
 * @author Dave Grove
 * @author Mauricio Serrano
 * @author John Whaley
 * @author Stephen Fink
 */
abstract class OPT_GenericPhysicalRegisterTools extends OPT_IRTools {

  /**
   * Return the governing IR.
   */
  abstract OPT_IR getIR();


  /**
   * Create an address register operand for a given physical GPR.
   * To be used in passthrough expressions like
   * <pre>
   *    ... Load.create(INT_LOAD, R(2), A(1), IC(4)) ...
   * </pre>
   *
   * @param regnum the given GPR register number
   * @return integer register operand
   */
  final OPT_RegisterOperand A(int regnum) {
    OPT_PhysicalRegisterSet phys = getIR().regpool.getPhysicalRegisterSet();
    return A(phys.getGPR(regnum));
  }

  /**
   * Create an integer register operand for a given physical GPR.
   * To be used in passthrough expressions like
   * <pre>
   *    ... Load.create(INT_LOAD, R(2), A(1), IC(4)) ...
   * </pre>
   *
   * @param regnum the given GPR register number
   * @return integer register operand
   */
  final OPT_RegisterOperand R(int regnum) {
    OPT_PhysicalRegisterSet phys = getIR().regpool.getPhysicalRegisterSet();
    return R(phys.getGPR(regnum));
  }

  /**
   * Create a float register operand for a given physical FPR.
   * To be used in passthrough expressions like
   * <pre>
   *    ... Load.create(FLOAT_LOAD, F(2), A(1), IC(4)) ...
   * </pre>
   *
   * @param regnum the given DOUBLE register number
   * @return float register operand
   */
  final OPT_RegisterOperand F(int regnum) {
    OPT_PhysicalRegisterSet phys = getIR().regpool.getPhysicalRegisterSet();
    return F(phys.getFPR(regnum));
  }

  /**
   * Create a double register operand for a given physical FPR.
   * To be used in passthrough expressions like
   * <pre>
   *    ... Load.create(DOUBLE_LOAD, D(2), A(1), IC(4)) ...
   * </pre>
   *
   * @param regnum the given double register number
   * @return double register operand
   */
  final OPT_RegisterOperand D(int regnum) {
    OPT_PhysicalRegisterSet phys = getIR().regpool.getPhysicalRegisterSet();
    return D(phys.getFPR(regnum));
  }

  /**
   * Create a long register operand for a given GPR number.
   * To be used in passthrough expressions like
   * <pre>
   *    ... Load.create(LONG_LOAD, L(2), A(1), IC(4)) ...
   * </pre>
   *
   * @param regnum the given GPR register number
   * @return long register operand
   */
  final OPT_RegisterOperand L(int regnum) {
    OPT_PhysicalRegisterSet phys = getIR().regpool.getPhysicalRegisterSet();
    return L(phys.getGPR(regnum));
  }

  /**
   * Does instruction s have an operand that contains a physical register?
   */
  static boolean hasPhysicalOperand(OPT_Instruction s) {
    for (Enumeration e = s.getOperands(); e.hasMoreElements(); ) {
      OPT_Operand op = (OPT_Operand)e.nextElement();
      if (op == null) continue;
      if (op.isRegister()) {
        if (op.asRegister().register.isPhysical()) {
          return true;
        }
      }
    }
    return false;
  }
}
