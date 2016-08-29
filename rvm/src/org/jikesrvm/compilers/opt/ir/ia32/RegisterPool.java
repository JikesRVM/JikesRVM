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
package org.jikesrvm.compilers.opt.ir.ia32;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.ir.GenericRegisterPool;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.LongConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.unboxed.Address;

/**
 * Pool of symbolic registers.<p>
 *
 * Intel specific implementation where JTOC is stored in the processor object
 * and accessed through the processor register.
 *
 * @see org.jikesrvm.compilers.opt.ir.Register
 */
public final class RegisterPool extends GenericRegisterPool {

  /**
   * Initializes a new register pool for the method meth.
   *
   * @param meth the RVMMethod of the outermost method
   */
  public RegisterPool(RVMMethod meth) {
    super(meth);
  }

  /**
   * Return a constant operand that is the base address of the JTOC.
   * <p>
   * TODO: This really should be returning an AddressConstantOperand,
   *       but that causes rippling changes in BURS that are larger
   *       than we want to deal with right now.
   *
   * @return an operand that holds the JTOC
   */
  @Override
  public Operand makeJTOCOp() {
    Address jtoc = Magic.getTocPointer();
    return VM.BuildFor32Addr ? new IntConstantOperand(jtoc.toInt()) :
      new LongConstantOperand(jtoc.toLong());
  }

  @Override
  public Operand makeTocOp() {
    PhysicalRegisterSet ia32Phys = (PhysicalRegisterSet) physical;
    return new RegisterOperand(ia32Phys.getJTOC(), TypeReference.Address);
  }
}
