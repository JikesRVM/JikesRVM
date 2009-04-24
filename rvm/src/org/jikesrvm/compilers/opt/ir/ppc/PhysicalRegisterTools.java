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
package org.jikesrvm.compilers.opt.ir.ppc;

import org.jikesrvm.compilers.opt.ir.GenericPhysicalRegisterTools;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;

/**
 * This abstract class provides a set of useful methods for
 * manipulating physical registers for an IR.
 */
public abstract class PhysicalRegisterTools extends GenericPhysicalRegisterTools {

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
  protected final RegisterOperand CR(int regnum) {
    PhysicalRegisterSet phys = getIR().regpool.getPhysicalRegisterSet();
    return CR(phys.getConditionRegister(regnum));
  }
}
