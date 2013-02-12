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

import org.jikesrvm.ArchitectureSpecificOpt.PhysicalRegisterSet;
import org.jikesrvm.compilers.opt.regalloc.GenericRegisterRestrictions;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Register;

/**
 * An instance of this class encapsulates restrictions on register
 * allocation.
 */
public abstract class RegisterRestrictions extends GenericRegisterRestrictions {
  /**
   * Default Constructor
   */
  public RegisterRestrictions(PhysicalRegisterSet phys) {
    super(phys);
  }

  @Override
  public boolean isForbidden(Register symb, Register r, Instruction s) {
    return false;
  }

}
