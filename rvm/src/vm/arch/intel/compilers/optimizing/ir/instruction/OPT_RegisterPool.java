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
   * Inject an instruction to load the JTOC from
   * the processor register and return an OPT_RegisterOperand
   * that contains the result of said load.
   * 
   * @param  ir  the containing IR
   * @param s    the instruction to insert the load operand before
   * @return     a register operand that holds the JTOC
   */ 
  public OPT_Operand makeJTOCOp(OPT_IR ir, OPT_Instruction s) {
    if (ir.options.FIXED_JTOC) {
      Address jtoc = VM_Magic.getTocPointer();
      return new OPT_IntConstantOperand(jtoc.toInt());
    } else {
      OPT_RegisterOperand res = ir.regpool.makeTemp(VM_TypeReference.IntArray);
      s.insertBefore(Unary.create(GET_JTOC, res, 
                                  OPT_IRTools.
                                  R(ir.regpool.getPhysicalRegisterSet().
                                    getPR())));
      return res.copyD2U();
    }
  }
}
