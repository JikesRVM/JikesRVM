/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.opt.ir.*;
import java.util.Enumeration;

/**
 * This abstract class provides a set of useful methods for
 * manipulating physical registers for an IR.
 *
 * @author Jong-Deok Choi
 * @author Dave Grove
 * @author Mauricio Serrano
 * @author John Whaley
 * @author Stephen Fink
 */
abstract class OPT_PhysicalRegisterTools extends
OPT_GenericPhysicalRegisterTools {

  /**
   * Create a condition register operand for a given register number.
   * To be used in passthrough expressions like
   * <pre>
   *    ... Binary.create(INT_CMP, CR(2), R(1), IC(4)) ...
   * </pre>
   *
   * @param regnum the given condition register number
   * @return condition register operand
   */
  final OPT_RegisterOperand CR(int regnum) {
    OPT_PhysicalRegisterSet phys = getIR().regpool.getPhysicalRegisterSet();
    return CR(phys.getConditionRegister(regnum));
  }
}
