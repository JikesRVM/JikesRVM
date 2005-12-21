/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt.ir;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.OPT_ClassLoaderProxy;

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
public class OPT_RegisterPool extends OPT_GenericRegisterPool implements OPT_Operators {

  /**
   * Initializes a new register pool for the method meth.
   * 
   * @param meth the VM_Method of the outermost method
   */
  OPT_RegisterPool(VM_Method meth) {
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
