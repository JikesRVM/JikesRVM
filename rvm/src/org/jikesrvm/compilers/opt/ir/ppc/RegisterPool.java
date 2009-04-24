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

import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.ir.GenericRegisterPool;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;

/**
 * Pool of symbolic registers.
 * powerPC specific implementation where JTOC is stored in a reserved register.
 * Each IR contains has exactly one register pool object associated with it.
 *
 * @see Register
 */
public abstract class RegisterPool extends GenericRegisterPool {

  /**
   * Initializes a new register pool for the method meth.
   *
   * @param meth the RVMMethod of the outermost method
   */
  public RegisterPool(RVMMethod meth) {
    super(meth);
  }

  /**
   * Get the JTOC register
   *
   * @return the JTOC register
   */
  public Register getJTOC() {
    return physical.getJTOC();
  }

  /**
   * Get a temporary that represents the JTOC register (as an Address)
   *
   * @param ir
   * @param s
   * @return the temp
   */
  public RegisterOperand makeJTOCOp(IR ir, Instruction s) {
    return new RegisterOperand(getJTOC(), TypeReference.Address);
  }

  /**
   * Get a temporary that represents the JTOC register (as an Object)
   *
   * @return the temp
   */
  public RegisterOperand makeTocOp() {
    return new RegisterOperand(getJTOC(), TypeReference.JavaLangObject);
  }

}
