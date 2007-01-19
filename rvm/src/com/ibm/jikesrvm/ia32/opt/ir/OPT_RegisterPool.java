/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.jikesrvm.ia32.opt.ir;

import com.ibm.jikesrvm.VM_Magic;
import com.ibm.jikesrvm.classloader.*;
import com.ibm.jikesrvm.opt.ir.OPT_GenericRegisterPool;
import com.ibm.jikesrvm.opt.ir.OPT_IR;
import com.ibm.jikesrvm.opt.ir.OPT_Instruction;
import com.ibm.jikesrvm.opt.ir.OPT_IntConstantOperand;
import com.ibm.jikesrvm.opt.ir.OPT_Operand;
import com.ibm.jikesrvm.opt.ir.OPT_Operators;

import org.vmmagic.unboxed.*;

/**
 * Pool of symbolic registers.
 * Intel specific implementation where JTOC is stored in the processor object
 * and accessed through the processor register.  
 * 
 * @see OPT_Register
 * 
 * @author Peter Sweeney
 * @author Stephen Fink
 */
public abstract class OPT_RegisterPool extends OPT_GenericRegisterPool implements OPT_Operators {

  /**
   * Initializes a new register pool for the method meth.
   * 
   * @param meth the VM_Method of the outermost method
   */
  protected OPT_RegisterPool(VM_Method meth) {
    super(meth);
  }

  /**
   * Return a constant operand that is the base address of the JTOC.
   * TODO: This really should be returning an OPT_AddressConstantOperand,
   *       but that causes rippling changes in BURS that are larger
   *       than I want to deal with right now. --dave 12/20/2005.
   * 
   * @param  ir  the containing IR
   * @param s    the instruction to insert the load operand before
   * @return     a register operand that holds the JTOC
   */ 
  public OPT_Operand makeJTOCOp(OPT_IR ir, OPT_Instruction s) {
    Address jtoc = VM_Magic.getTocPointer();
    return new OPT_IntConstantOperand(jtoc.toInt());
  }
}
