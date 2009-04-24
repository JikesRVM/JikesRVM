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
package org.jikesrvm.compilers.opt.ir;

import org.jikesrvm.ArchitectureSpecificOpt.PhysicalRegisterSet;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;

/**
 * Pool of symbolic registers.
 * Each IR contains has exactly one register pool object associated with it.
 *
 * @see Register
 */
public class GenericRegisterPool extends AbstractRegisterPool {

  protected final PhysicalRegisterSet physical = new PhysicalRegisterSet();

  public PhysicalRegisterSet getPhysicalRegisterSet() {
    return physical;
  }

  /**
   * Initializes a new register pool for the method meth.
   *
   * @param meth the RVMMethod of the outermost method
   */
  protected GenericRegisterPool(RVMMethod meth) {
    // currentNum is assigned an initial value to avoid overlap of
    // physical and symbolic registers.
    currentNum = PhysicalRegisterSet.getSize();
  }

  /**
   * Return the number of symbolic registers (doesn't count physical ones)
   * @return the number of symbolic registers allocated by the pool
   */
  public int getNumberOfSymbolicRegisters() {
    int start = PhysicalRegisterSet.getSize();
    return currentNum - start;
  }

  /**
   * Get the Framepointer (FP)
   *
   * @return the FP register
   */
  public Register getFP() {
    return physical.getFP();
  }

  /**
   * Get a temporary that represents the FP register
   *
   * @return the temp
   */
  public RegisterOperand makeFPOp() {
    return new RegisterOperand(getFP(), TypeReference.Address);
  }

  /**
   * Get a temporary that represents the PR register
   *
   * @return the temp
   */
  public RegisterOperand makeTROp() {
    RegisterOperand trOp = new RegisterOperand(physical.getTR(), TypeReference.Thread);
    trOp.setPreciseType();
    return trOp;
  }

}
