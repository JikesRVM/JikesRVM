/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.jikesrvm.opt;

import com.ibm.jikesrvm.opt.ir.*;
import com.ibm.jikesrvm.VM_ProcessorLocalState;
import com.ibm.jikesrvm.classloader.VM_TypeReference;
import static com.ibm.jikesrvm.opt.ir.OPT_Operators.*;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.Offset;

/**
 * OPT extensions to VM_ProcessorLocalState
 *
 * @see VM_Processor
 * @see VM_ProcessorLocalState
 *
 * @author Stephen Fink
 */
public final class OPT_ProcessorLocalState extends VM_ProcessorLocalState {
  

  /**
   * Insert code before instruction s to load a pointer to the current 
   * processor into a symbolic register, and return the resultant operand
   */
  public static OPT_RegisterOperand insertGetCurrentProcessor(OPT_IR ir,
                                                       OPT_Instruction s) {
    OPT_RegisterOperand result = ir.regpool.makeTemp(VM_TypeReference.VM_Processor);
    OPT_Register ESI = ir.regpool.getPhysicalRegisterSet().getESI();

    s.insertBefore(MIR_Move.create(IA32_MOV,result,new OPT_RegisterOperand(ESI, VM_TypeReference.Int)));
    return result;
  }
  /**
   * Insert code before instruction s to load a pointer to the current 
   * processor into a particular register operand.
   */
  public static OPT_RegisterOperand insertGetCurrentProcessor(OPT_IR ir,
                                                       OPT_Instruction s,
                                                       OPT_RegisterOperand rop)
  {
    OPT_Register ESI = ir.regpool.getPhysicalRegisterSet().getESI();

    OPT_RegisterOperand result = rop.copyRO();
    s.insertBefore(MIR_Move.create(IA32_MOV,result,new OPT_RegisterOperand(ESI, VM_TypeReference.Int)));
    return result;
  }
  /**
   * Insert code after instruction s to set the current 
   * processor to be the value of a particular register operand.
   */
  public static OPT_RegisterOperand appendSetCurrentProcessor(OPT_IR ir,
                                                       OPT_Instruction s,
                                                       OPT_RegisterOperand rop)
  {
    OPT_Register ESI = ir.regpool.getPhysicalRegisterSet().getESI();

    s.insertBefore(MIR_Move.create(IA32_MOV,new OPT_RegisterOperand(ESI, VM_TypeReference.Int),rop.copyRO()));
    return rop;
  }
}
