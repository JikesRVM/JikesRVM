/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import java.util.Iterator;
import com.ibm.JikesRVM.opt.ir.*;
import java.util.ArrayList;
import java.util.Enumeration;

/**
 * An instance of this class encapsulates restrictions on register
 * assignment.
 * 
 * @author Stephen Fink
 */
final class OPT_RegisterRestrictions extends OPT_GenericRegisterRestrictions implements OPT_Operators, OPT_PhysicalRegisterConstants {

  /**
   * Allow scratch registers in PEIs?
   */
  final static boolean SCRATCH_IN_PEI = true;

  /**
   * Default Constructor
   */
  OPT_RegisterRestrictions(OPT_PhysicalRegisterSet phys) {
    super(phys);
  }

  /**
   * Add architecture-specific register restrictions for a basic block.
   * Override as needed.
   *
   * @param bb the basic block 
   * @param symbolics the live intervals for symbolic registers on this
   * block
   */
  void addArchRestrictions(OPT_BasicBlock bb, ArrayList symbolics) {
    // If there are any registers used in catch blocks, we want to ensure
    // that these registers are not used or evicted from scratch registers
    // at a relevant PEI, so that the assumptions of register homes in the
    // catch block remain valid.  For now, we do this by forcing any
    // register used in such a PEI as not spilled.  TODO: relax this
    // restriction for better code.
    for (OPT_InstructionEnumeration ie = bb.forwardInstrEnumerator();
         ie.hasMoreElements(); ) {
      OPT_Instruction s = ie.next();
      if (s.isPEI() && s.operator != IR_PROLOGUE) {
        if (bb.hasApplicableExceptionalOut(s) || !SCRATCH_IN_PEI) {
          for (Enumeration e = s.getOperands(); e.hasMoreElements(); ) {
            OPT_Operand op = (OPT_Operand)e.nextElement();
            if (op != null && op.isRegister()) {
              noteMustNotSpill(op.asRegister().register);
              handle8BitRestrictions(s);
            }
          }
        }
      }

      // handle special cases 
      switch (s.getOpcode()) {
        case MIR_LOWTABLESWITCH_opcode:
          {
            OPT_RegisterOperand op = MIR_LowTableSwitch.getIndex(s);
            noteMustNotSpill(op.register);
          }
          break;
        case IA32_MOVZX__B_opcode: case IA32_MOVSX__B_opcode:
          {
            OPT_RegisterOperand op = MIR_Unary.getResult(s).asRegister();
            if (MIR_Unary.getVal(s).isRegister()) {
              OPT_RegisterOperand val = MIR_Unary.getVal(s).asRegister();
              restrictTo8Bits(val.register);
            }
          }
          break;
        case IA32_SET__B_opcode:
          { 
            if (MIR_Set.getResult(s).isRegister()) {
              OPT_RegisterOperand op = MIR_Set.getResult(s).asRegister();
              restrictTo8Bits(op.register);
            }
          }
          break;

        default:
          handle8BitRestrictions(s);
          break;
      }
    }
    for (OPT_InstructionEnumeration ie = bb.forwardInstrEnumerator();
         ie.hasMoreElements(); ) {
      OPT_Instruction s = ie.next();
      if (s.operator == IA32_FNINIT) {
        // No floating point register survives across an FNINIT
        for (Iterator sym = symbolics.iterator(); sym.hasNext(); ) {
          OPT_LiveIntervalElement symb = (OPT_LiveIntervalElement) sym.next();
          if (symb.getRegister().isFloatingPoint()) {
            if (contains(symb,s.scratch)) {
              addRestrictions(symb.getRegister(),phys.getFPRs());
            }
          }
        }
      } else if (s.operator == IA32_FCLEAR) {
        // Only some FPRs survive across an FCLEAR
        for (Iterator sym = symbolics.iterator(); sym.hasNext(); ) {
          OPT_LiveIntervalElement symb = (OPT_LiveIntervalElement) sym.next();
          if (symb.getRegister().isFloatingPoint()) {
            if (contains(symb,s.scratch)) {
              int nSave = MIR_UnaryNoRes.getVal(s).asIntConstant().value;
              for (int i = nSave; i < NUM_FPRS; i++) {
                addRestriction(symb.getRegister(), phys.getFPR(i));
              }
            }
          }
        }
      }
    }
  }

  /**
   * Does instruction s contain an 8-bit memory operand?
   */
  final boolean has8BitMemoryOperand(OPT_Instruction s) {
    for (OPT_OperandEnumeration me = s.getMemoryOperands(); 
         me.hasMoreElements(); ) {
      OPT_MemoryOperand mop = (OPT_MemoryOperand)me.next();
      if (mop.size == 1) {
        return true;
      }
    }
    return false;
  }
  /**
   * Ensure that if an operand has an 8 bit memory operand that
   * all of its register operands are in 8 bit registers.
   * @param s the instruction to restrict
   */
  final void handle8BitRestrictions(OPT_Instruction s) {
    for (OPT_OperandEnumeration me = s.getMemoryOperands(); 
         me.hasMoreElements(); ) {
      OPT_MemoryOperand mop = (OPT_MemoryOperand)me.next();
      if (mop.size == 1) {
        for (OPT_OperandEnumeration e2 = s.getRootOperands(); 
             e2.hasMoreElements(); ) {
          OPT_Operand rootOp = e2.next();
          if (rootOp.isRegister()) {
            restrictTo8Bits(rootOp.asRegister().register);
          }
        }
      }
    }
  }


  /**
   * Ensure that a particular register is only assigned to AL, BL, CL, or
   * DL, since these are the only 8-bit registers we normally address.
   */
  final void restrictTo8Bits(OPT_Register r) {
    OPT_Register ESP = phys.getESP();        
    OPT_Register EBP = phys.getEBP();        
    OPT_Register ESI = phys.getESI();        
    OPT_Register EDI = phys.getEDI();        
    addRestriction(r,ESP);
    addRestriction(r,EBP);
    addRestriction(r,ESI);
    addRestriction(r,EDI);
  }

  /**
   * Given symbolic register r that appears in instruction s, does the
   * architecture demand that r be assigned to a physical register in s?
   */
  static boolean mustBeInRegister(OPT_Register r, OPT_Instruction s) {
    switch (s.getOpcode()) {
      case IA32_SHRD_opcode: case IA32_SHLD_opcode:
        {
          OPT_RegisterOperand op = MIR_DoubleShift.getSource(s);
          if (op.asRegister().register == r) return true;
        }
        break;
      case IA32_FCOMI_opcode: case IA32_FCOMIP_opcode:
        {
          OPT_Operand op = MIR_Compare.getVal2(s);
          if (!(op instanceof OPT_BURSManagedFPROperand)) {
            if (op.asRegister().register == r) return true;
          }
        }
        break;
      case IA32_IMUL2_opcode:
        { 
          OPT_RegisterOperand op = MIR_BinaryAcc.getResult(s).asRegister();
          if (op.asRegister().register == r) return true;
        }
        break;
      case MIR_LOWTABLESWITCH_opcode:
        {
          OPT_RegisterOperand op = MIR_LowTableSwitch.getIndex(s);
          if (op.asRegister().register == r) return true;
        }
        break;
      case IA32_CMOV_opcode: case IA32_FCMOV_opcode:
        {
          OPT_RegisterOperand op = MIR_CondMove.getResult(s).asRegister();
          if (op.asRegister().register == r) return true;
        }
        break;
      case IA32_MOVZX__B_opcode: case IA32_MOVSX__B_opcode:
        {
          OPT_RegisterOperand op = MIR_Unary.getResult(s).asRegister();
          if (op.asRegister().register == r) return true;
        }
        break;
      case IA32_MOVZX__W_opcode: case IA32_MOVSX__W_opcode:
        { 
          OPT_RegisterOperand op = MIR_Unary.getResult(s).asRegister();
          if (op.asRegister().register == r) return true;
        }
        break;
      case IA32_SET__B_opcode:
        { 
          if (MIR_Set.getResult(s).isRegister()) {
            OPT_RegisterOperand op = MIR_Set.getResult(s).asRegister();
            if (op.asRegister().register == r) return true;
          }
        }
        break;
      case IA32_TEST_opcode:
        {
          // at least 1 of the two operands must be in a register
          if (!MIR_Test.getVal2(s).isConstant()) {
            if (MIR_Test.getVal1(s).isRegister()) {
              if (MIR_Test.getVal1(s).asRegister().register == r) return true;
            } else if (MIR_Test.getVal2(s).isRegister()) {
              if (MIR_Test.getVal2(s).asRegister().register == r) return true;
            }
          }
        }
        break;

      default:
        break;
    }
    return false;
  }

  /**
   * Can physical register r hold an 8-bit value?
   */
  private boolean okFor8(OPT_Register r) {
    OPT_Register ESP = phys.getESP();        
    OPT_Register EBP = phys.getEBP();        
    OPT_Register ESI = phys.getESI();        
    OPT_Register EDI = phys.getEDI();        
    return (r!=ESP && r!=EBP && r!=ESI && r!=EDI);
  }

  /**
   * Is it forbidden to assign symbolic register symb to physical register r
   * in instruction s?
   */
  boolean isForbidden(OPT_Register symb, OPT_Register r,
                             OPT_Instruction s) {

    // Look at 8-bit restrictions.
    switch (s.operator.opcode) {
      case IA32_MOVZX__B_opcode: case IA32_MOVSX__B_opcode:
        {
          if (MIR_Unary.getVal(s).isRegister()) {
            OPT_RegisterOperand val = MIR_Unary.getVal(s).asRegister();
            if (val.register == symb) {
              return !okFor8(r);
            }
          }
        }
        break;
      case IA32_SET__B_opcode:
        { 
          if (MIR_Set.getResult(s).isRegister()) {
            OPT_RegisterOperand op = MIR_Set.getResult(s).asRegister();
            if (op.asRegister().register == symb) {
              return !okFor8(r);
            }
          }
        }
        break;
     }

    if (has8BitMemoryOperand(s)) {
      return !okFor8(r);
    }

    // Otherwise, it's OK.
    return false;
  }
}
