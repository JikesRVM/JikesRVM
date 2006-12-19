/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2006
 */
package com.ibm.jikesrvm.opt.ir;

import static com.ibm.jikesrvm.opt.ir.OPT_Operators.*;

import com.ibm.jikesrvm.VM;
import com.ibm.jikesrvm.opt.OPT_OptimizingCompilerException;

import org.vmmagic.pragma.*;

/**
 * Wrappers around 64-bit PowerPC-specific IR
 * 
 * $Id: OPT_IA32ConditionOperand.java 10996 2006-11-16 23:37:12Z dgrove-oss $
 * 
 * @author Steve Blackburn
 */
public final class OPT_PowerPC64MachineSpecificIR extends OPT_PowerPCMachineSpecificIR {
  /* common to all ISAs */ 
  @Override
  public final boolean mayEscapeThread(OPT_Instruction instruction) {
    switch (instruction.getOpcode()) {
    case DCBST_opcode:case DCBT_opcode:case DCBTST_opcode:
    case DCBZ_opcode:case DCBZL_opcode:case ICBI_opcode:
      return false;
    case LONG_OR_opcode: case LONG_AND_opcode: case LONG_XOR_opcode:
    case LONG_SUB_opcode:case LONG_SHL_opcode: case LONG_ADD_opcode:
    case LONG_SHR_opcode:case LONG_USHR_opcode:case LONG_NEG_opcode:
    case LONG_MOVE_opcode: case LONG_2ADDR_opcode:
      return true;
    default:
      throw  new OPT_OptimizingCompilerException("OPT_SimpleEscapge: Unexpected " + instruction);
    }
  }
  @Override
  public boolean mayEscapeMethod(OPT_Instruction instruction) {
    return mayEscapeThread(instruction); // at this stage we're no more specific
  }
  /* unique to PowerPC */
}
