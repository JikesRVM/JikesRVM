/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt.ppc;

import org.jikesrvm.compilers.opt.OPT_GenericPhysicalRegisterTools;
import org.jikesrvm.compilers.opt.ir.OPT_RegisterOperand;
import org.jikesrvm.compilers.opt.ir.ppc.OPT_PhysicalRegisterSet;

/**
 * This abstract class provides a set of useful methods for
 * manipulating physical registers for an IR.
 */
public abstract class OPT_PhysicalRegisterTools extends
                                                OPT_GenericPhysicalRegisterTools {

  /**
   * Create a condition register operand for a given register number.
   * To be used in passthrough expressions like
   * <pre>
   *    ... Binary.create(INT_CMP, CR(2), I(1), IC(4)) ...
   * </pre>
   *
   * @param regnum the given condition register number
   * @return condition register operand
   */
  protected final OPT_RegisterOperand CR(int regnum) {
    OPT_PhysicalRegisterSet phys = getIR().regpool.getPhysicalRegisterSet();
    return CR(phys.getConditionRegister(regnum));
  }
}
