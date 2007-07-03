/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.ppc;

import org.jikesrvm.ArchitectureSpecific.OPT_PhysicalRegisterSet;
import org.jikesrvm.compilers.opt.OPT_GenericRegisterRestrictions;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_Register;

/**
 * An instance of this class encapsulates restrictions on register
 * allocation.
 */
public abstract class OPT_RegisterRestrictions extends OPT_GenericRegisterRestrictions {
  /**
   * Default Constructor
   */
  public OPT_RegisterRestrictions(OPT_PhysicalRegisterSet phys) {
    super(phys);
  }

  /**
   * Is it forbidden to assign symbolic register symb to physical register r
   * in instruction s?
   */
  public boolean isForbidden(OPT_Register symb, OPT_Register r, OPT_Instruction s) {
    return false;
  }

}
