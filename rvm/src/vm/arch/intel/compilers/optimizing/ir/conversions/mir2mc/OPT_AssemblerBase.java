/*
 * (C) Copyright IBM Corp. 2001, 2004
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.opt.ir.*;
import com.ibm.JikesRVM.*;

import org.vmmagic.unboxed.Offset;

/**
 *  This class provides support functionality used by the generated
 * OPT_Assembler; it handles basic impedance-matching functionality
 * such as determining which addressing mode is suitable for a given
 * OPT_IA32MemoryOperand.  This class also provides some boilerplate
 * methods that do not depend on how instructions sould actually be
 * assembled, like the top-level generateCode driver.  This class is
 * not meant to be used in isolation, but rather to provide support
 * from the OPT_Assembler.
 *
 * @author Julian Dolby
 */
abstract class OPT_AssemblerBase extends VM_Assembler 
  implements OPT_Operators, VM_Constants, OPT_PhysicalRegisterConstants {

  /**
   *  This class requires no particular construction behavior; this
   * constructor simply calls super.
   *
   * @see VM_Assembler
   */
  OPT_AssemblerBase(int bytecodeSize, boolean shouldPrint) {
    super(bytecodeSize, shouldPrint);
  }

  /**
   * Should code created by this assembler instance be allocated in the
   * hot code code space? The default answer for opt compiled code is yes
   * (otherwise why are we opt compiling it?).
   */
  protected boolean isHotCode() { return true; }

  /**
   *  Is the given operand an immediate?  In the IA32 assembly, one
   * cannot specify floating-point constants, so the possible
   * immediates we may see are OPT_IntegerConstants and
   * OPT_TrapConstants (a trap constant really is an integer), and
   * jump targets for which the exact offset is known.
   *
   * @see #getImm
   *
   * @param op the operand being queried
   * @return true if op represents an immediate
   */
  static boolean isImm(OPT_Operand op) {
    return
      (op instanceof OPT_IntConstantOperand) ||
      (op instanceof OPT_TrapCodeOperand) ||
      (op instanceof OPT_BranchOperand && op.asBranch().target.getmcOffset() >= 0);
  }

  /**
   *  Return the IA32 ISA encoding of the immediate value
   * represented by the the given operand.  This method assumes the
   * operand is an immediate and will likely throw a
   * ClassCastException if this not the case.  It treats
   * OPT_BranchOperands somewhat differently than isImm does: in
   * case a branch target is not resolved, it simply returns a wrong
   * answer and trusts the caller to ignore it. This behavior
   * simplifies life when generating code for ImmOrLabel operands.
   *
   * @see #isImm
   *
   * @param op the operand being queried
   * @return the immediate value represented by the operand
   */
  static int getImm(OPT_Operand op) {
    if (op instanceof OPT_BranchOperand) {
      // used by ImmOrLabel stuff
      return op.asBranch().target.getmcOffset();
    } else if (op instanceof OPT_TrapCodeOperand) {
      return ((OPT_TrapCodeOperand)op).getTrapCode() + VM_TrapConstants.RVM_TRAP_BASE;
    } else {
      return op.asIntConstant().value;
    }
  }

  /**
   *  Is the given operand a register operand?
   *
   * @see #getReg
   *
   * @param op the operand being queried
   * @return true if op is an OPT_RegisterOperand
   */
  static boolean isReg(OPT_Operand op) {
    return (op instanceof OPT_RegisterOperand);
  }

  /**
   *  Return the machine-level register number corresponding to a
   * given OPT_Register.  The optimizing compiler has its own notion
   * of register numbers, which is not the same as the numbers used
   * by the IA32 ISA.  This method takes an optimizing compiler
   * register and translates it into the appropriate machine-level
   * encoding.  This method is not applied directly to operands, but
   * rather to register objects.
   *
   * @see #getReg
   * @see #getBase
   * @see #getIndex
   *
   * @param reg the register being queried
   * @return the 3 bit machine-level encoding of reg
   */
  static private byte getMachineRegister(OPT_Register reg) {
    int type = OPT_PhysicalRegisterSet.getPhysicalRegisterType(reg);
    if (type == INT_REG) 
      return (byte) (reg.number - FIRST_INT);
    else if (type == DOUBLE_REG)
      return (byte) (reg.number - FIRST_DOUBLE);
    else
      throw new OPT_OptimizingCompilerException("unexpected register type " + type);
  }
        
  /**
   *  Given a register operand, return the 3 bit IA32 ISA encoding
   * of that register.  This function translates an optimizing
   * compiler register operand into the 3 bit IA32 ISA encoding that
   * can be passed to the VM_Assembler.  This function assumes its
   * operand is a register operand, and will blow up if it is not;
   * use isReg to check operands passed to this method.
   *
   * @see #isReg 
   *
   * @param op the register operand being queried
   * @return the 3 bit IA32 ISA encoding of op
   */
  static byte getReg(OPT_Operand op) {
    return getMachineRegister( op.asRegister().register );
  }

  /**
   *  Given a memory operand, return the 3 bit IA32 ISA encoding
   * of its base regsiter.  This function translates the optimizing
   * compiler register operand representing the base of the given
   * memory operand into the 3 bit IA32 ISA encoding that
   * can be passed to the VM_Assembler.  This function assumes its
   * operand is a memory operand, and will blow up if it is not;
   * one should confirm an operand really has a base register before
   * invoking this method on it.
   *
   * @see #isRegDisp
   * @see #isRegIdx
   * @see #isRegInd
   *
   * @param op the register operand being queried
   * @return the 3 bit IA32 ISA encoding of the base register of op
   */
  static byte getBase(OPT_Operand op) {
    return getMachineRegister(((OPT_MemoryOperand)op).base.register);
  }

  /**
   *  Given a memory operand, return the 3 bit IA32 ISA encoding
   * of its index regsiter.  This function translates the optimizing
   * compiler register operand representing the index of the given
   * memory operand into the 3 bit IA32 ISA encoding that
   * can be passed to the VM_Assembler.  This function assumes its
   * operand is a memory operand, and will blow up if it is not;
   * one should confirm an operand really has an index register before
   * invoking this method on it.
   *
   * @see #isRegIdx
   * @see #isRegOff
   *
   * @param op the register operand being queried
   * @return the 3 bit IA32 ISA encoding of the index register of op
   */
  static byte getIndex(OPT_Operand op) {
    return getMachineRegister(((OPT_MemoryOperand)op).index.register);
  }

  /**
   *  Given a memory operand, return the 2 bit IA32 ISA encoding
   * of its scale, suitable for passing to the VM_Assembler to mask
   * into a SIB byte.  This function assumes its operand is a memory
   * operand, and will blow up if it is not; one should confirm an
   * operand really has a scale before invoking this method on it.
   *
   * @see #isRegIdx
   * @see #isRegOff
   *
   * @param op the register operand being queried
   * @return the IA32 ISA encoding of the scale of op
   */
  static short getScale(OPT_Operand op) {
    return ((OPT_MemoryOperand)op).scale;
  }

  /**
   *  Given a memory operand, return the 2 bit IA32 ISA encoding
   * of its scale, suitable for passing to the VM_Assembler to mask
   * into a SIB byte.  This function assumes its operand is a memory
   * operand, and will blow up if it is not; one should confirm an
   * operand really has a scale before invoking this method on it.
   *
   * @see #isRegIdx
   * @see #isRegOff
   *
   * @param op the register operand being queried
   * @return the IA32 ISA encoding of the scale of op
   */
  static Offset getDisp(OPT_Operand op) {
    return ((OPT_MemoryOperand)op).disp;
  }

  /**
   *  Determine if a given operand is a memory operand representing
   * register-displacement mode addressing.  This method takes an
   * arbitrary operand, checks whether it is a memory operand, and,
   * if it is, checks whether it should be assembled as IA32
   * register-displacement mode.  That is, does it have a non-zero
   * displacement and a base register, but no scale and no index
   * register?
   *
   * @param op the operand being queried
   * @return true if op should be assembled as register-displacement mode
   */
  static boolean isRegDisp(OPT_Operand op) {
    if (op instanceof OPT_MemoryOperand) {
      OPT_MemoryOperand mop = (OPT_MemoryOperand) op;
      return (mop.base != null) &&
        (mop.index == null) &&
        (!mop.disp.isZero()) &&
        (mop.scale == 0);
    } else
      return false;
  }

  /**
   *  Determine if a given operand is a memory operand representing
   * absolute mode addressing.  This method takes an
   * arbitrary operand, checks whether it is a memory operand, and,
   * if it is, checks whether it should be assembled as IA32
   * absolute address mode.  That is, does it have a non-zero
   * displacement, but no scale, no scale and no index register?
   *
   * @param op the operand being queried
   * @return true if op should be assembled as absolute mode
   */
  static boolean isAbs(OPT_Operand op) {
    if (op instanceof OPT_MemoryOperand) {
      OPT_MemoryOperand mop = (OPT_MemoryOperand) op;
      return (mop.base == null) &&
        (mop.index == null) &&
        (!mop.disp.isZero()) &&
        (mop.scale == 0);
    } else
      return false;
  }

  /**
   *  Determine if a given operand is a memory operand representing
   * register-indirect mode addressing.  This method takes an
   * arbitrary operand, checks whether it is a memory operand, and,
   * if it is, checks whether it should be assembled as IA32
   * register-displacement mode.  That is, does it have a base
   * register, but no displacement, no scale and no index
   * register?
   *
   * @param op the operand being queried
   * @return true if op should be assembled as register-indirect mode
   */
  static boolean isRegInd(OPT_Operand op) {
    if (op instanceof OPT_MemoryOperand) {
      OPT_MemoryOperand mop = (OPT_MemoryOperand) op;
      return (mop.base != null) &&
        (mop.index == null) &&
        (mop.disp.isZero()) &&
        (mop.scale == 0);
    } else
      return false;
  }

  /**
   *  Determine if a given operand is a memory operand representing
   * register-offset mode addressing.  This method takes an
   * arbitrary operand, checks whether it is a memory operand, and,
   * if it is, checks whether it should be assembled as IA32
   * register-offset mode.  That is, does it have a non-zero
   * displacement, a scale parameter and an index register, but no
   * base register?
   *
   * @param op the operand being queried
   * @return true if op should be assembled as register-offset mode
   */
  static boolean isRegOff(OPT_Operand op) {
    if (op instanceof OPT_MemoryOperand) {
      OPT_MemoryOperand mop = (OPT_MemoryOperand) op;
      return (mop.base == null) &&
        (mop.index != null);
    } else
      return false;
  }

  /**
   *  Determine if a given operand is a memory operand representing
   * the full glory of scaled-index-base addressing.  This method takes an
   * arbitrary operand, checks whether it is a memory operand, and,
   * if it is, checks whether it should be assembled as IA32
   * SIB mode.  That is, does it have a non-zero
   * displacement, a scale parameter, a base register and an index
   * register?
   *
   * @param op the operand being queried
   * @return true if op should be assembled as SIB mode
   */
  static boolean isRegIdx(OPT_Operand op) {
    if (op instanceof OPT_MemoryOperand) 
      return !(isAbs(op) || isRegInd(op) || isRegDisp(op) || isRegOff(op));
    else
      return false;
  }

  /**
   *  Return the condition bits of a given optimizing compiler
   * condition operand.  This method returns the IA32 ISA bits
   * representing a given condition operand, suitable for passing to
   * the VM_Assembler to encode into the opcode of a SET, Jcc or
   * CMOV instruction.  This being IA32, there are of course
   * exceptions in the binary encoding of conditions (see FCMOV),
   * but the VM_Assembler handles that.  This function assumes its
   * argument is an OPT_IA32ConditionOperand, and will blow up if it
   * is not.
   *
   * @param op the operand being queried
   * @return the bits that (usually) represent the given condition
   * in the IA32 ISA */
  static byte getCond(OPT_Operand op) {
    return ((OPT_IA32ConditionOperand)op).value;
  }

  /**
   *  Is the given operand an IA32 condition operand?
   *
   * @param op the operand being queried
   * @return true if op is an IA32 condition operand
   */
  static boolean isCond(OPT_Operand op) {
    return (op instanceof OPT_IA32ConditionOperand);
  }

  /**
   *  Return the label representing the target of the given branch
   * operand.  These labels are used to represent branch targets
   * that have not yet been assembled, and so cannot be given
   * concrete machine code offsets.  All instructions are nunbered
   * just prior to assembly, and these numbers are used as labels.
   * This method also returns 0 (not a valid label) for int
   * constants to simplify generation of branches (the branch
   * generation code will ignore this invalid label; it is used to
   * prevent type exceptions).  This method assumes its operand is a
   * branch operand (or an int) and will blow up if it is not.
   *
   * @param op the branch operand being queried
   * @return the label representing the branch target
   */
  static int getLabel(OPT_Operand op) {
    if (op instanceof OPT_IntConstantOperand) {
      // used by ImmOrLabel stuff
      return 0;
    } else {
      if (op.asBranch().target.getmcOffset() < 0)
        return - op.asBranch().target.getmcOffset();
      else
        return -1;
    }
  }

  /**
   *  Is the given operand a branch target that requires a label?
   *
   * @see #getLabel
   *
   * @param op the operand being queried
   * @return true if it represents a branch requiring a label target
   */
  static boolean isLabel(OPT_Operand op) {
    return (op instanceof OPT_BranchOperand &&
            op.asBranch().target.getmcOffset() < 0);
  }
    
  /**
   *  Is the given operand a branch target?
   *
   * @see #getLabel
   * @see #isLabel
   *
   * @param op the operand being queried
   * @return true if it represents a branch target
   */
  static boolean isImmOrLabel(OPT_Operand op) {
    return (isImm(op) || isLabel(op));
  }

  /**
   *  Does the given instruction operate upon byte-sized data?  The
   * opt compiler does not represent the size of register data, so
   * this method typically looks at the memory operand, if any, and
   * checks whether that is a byte.  This does not work for the
   * size-converting moves (MOVSX and MOVZX), and those instructions
   * use the operator convention that __b on the end of the operator
   * name means operate upon byte data.
   *
   * @param inst the instruction being queried
   * @return true if inst operates upon byte data
   */
  static boolean isByte(OPT_Instruction inst) {
    if (inst.operator.toString().indexOf("__b") != -1)
      return true;

    for(int i = 0; i < inst.getNumberOfOperands(); i++) {
      OPT_Operand op = inst.getOperand(i);
      if (op instanceof OPT_MemoryOperand)
        return (((OPT_MemoryOperand)op).size == 1);
    }

    return false;
  }

  /**
   *  Does the given instruction operate upon word-sized data?  The
   * opt compiler does not represent the size of register data, so
   * this method typically looks at the memory operand, if any, and
   * checks whether that is a word.  This does not work for the
   * size-converting moves (MOVSX and MOVZX), and those instructions
   * use the operator convention that __w on the end of the operator
   * name means operate upon word data.
   *
   * @param inst the instruction being queried
   * @return true if inst operates upon word data
   */
  static boolean isWord(OPT_Instruction inst) {
    if (inst.operator.toString().indexOf("__w") != -1)
      return true;

    for(int i = 0; i < inst.getNumberOfOperands(); i++) {
      OPT_Operand op = inst.getOperand(i);
      if (op instanceof OPT_MemoryOperand)
        return (((OPT_MemoryOperand)op).size == 2);
    }

    return false;
  }

  /**
   *  Does the given instruction operate upon quad-sized data?  The
   * opt compiler does not represent the size of register data, so
   * this method typically looks at the memory operand, if any, and
   * checks whether that is a byte.  This method also recognizes 
   * the operator convention that __q on the end of the operator
   * name means operate upon quad data; no operator currently uses
   * this convention.
   *
   * @param inst the instruction being queried
   * @return true if inst operates upon quad data
   */
  static boolean isQuad(OPT_Instruction inst) {
    if (inst.operator.toString().indexOf("__q") != -1)
      return true;

    for(int i = 0; i < inst.getNumberOfOperands(); i++) {
      OPT_Operand op = inst.getOperand(i);
      if (op instanceof OPT_MemoryOperand)
        return (((OPT_MemoryOperand)op).size == 8);
    }

    return false;
  }

  /**
   * Given a forward branch instruction and its target,
   * determine (conservatively) if the relative offset to the
   * target is less than 127 bytes
   * @param inst the branch instruction
   * @param targetMC the value of the mcOffset of the target label
   * @return true if the relative offset will be less than 127, false otherwise
   */
  protected boolean targetIsClose(OPT_Instruction start, int target) {
    OPT_Instruction inst = start.nextInstructionInCodeOrder();
    int budget = 120; // slight fudge factor could be 127
    while (true) {
      if (budget <= 0 ) return false;
      if (inst.getmcOffset() == target) {
        return true;
      }
      switch (inst.getOpcode()) {
      case LABEL_opcode:case BBEND_opcode:case UNINT_BEGIN_opcode:case UNINT_END_opcode:
        // these generate no code
        break;

        // Generated from the same case in VM_Assembler
      case IA32_ADC_opcode:case IA32_ADD_opcode:case IA32_AND_opcode:
      case IA32_OR_opcode:case IA32_SBB_opcode:
      case IA32_XOR_opcode:
        budget -= 2; // opcode + modr/m
        budget -= operandCost(MIR_BinaryAcc.getResult(inst), true);
        budget -= operandCost(MIR_BinaryAcc.getValue(inst), true);
        break;
      case IA32_CMP_opcode:
        budget -= 2; // opcode + modr/m
        budget -= operandCost(MIR_Compare.getVal1(inst), true);
        budget -= operandCost(MIR_Compare.getVal2(inst), true);
        break;
      case IA32_TEST_opcode:
        budget -= 2; // opcode + modr/m
        budget -= operandCost(MIR_Test.getVal1(inst), true);
        budget -= operandCost(MIR_Test.getVal2(inst), true);
        break;
        
      case IA32_PUSH_opcode:
        {
          OPT_Operand op = MIR_UnaryNoRes.getVal(inst);
          if (op instanceof OPT_RegisterOperand) {
            budget -= 1;
          } else if (op instanceof OPT_IntConstantOperand) {
            if (fits(((OPT_IntConstantOperand)op).value,8)) {
              budget -= 2;
            } else {
              budget -= 5;
            }
          } else {
            budget -= (2+operandCost(op, true));
          }
        }
        break;
      case IA32_MOV_opcode:
        budget -= 2; // opcode + modr/m
        budget -= operandCost(MIR_Move.getResult(inst), false);
        budget -= operandCost(MIR_Move.getValue(inst), false);
        break;
      case IA32_OFFSET_opcode:
        budget -= 4;
        break;
      case IA32_JCC_opcode: case IA32_JMP_opcode:
        budget -= 6; // assume long form
        break;
      case IA32_LOCK_opcode:
        budget -= 1;
        break;
      case IG_PATCH_POINT_opcode:
        budget -= 6;
        break;
      case IA32_INT_opcode:
        budget -= 2;
        break;
      case IA32_RET_opcode:
        budget -= 3;
        break;
      default:
        budget -= 3; // 2 bytes opcode + 1 byte modr/m
        for (OPT_OperandEnumeration opEnum = inst.getRootOperands();
             opEnum.hasMoreElements();) {
          OPT_Operand op = opEnum.next();
          budget -= operandCost(op, false);
        }
        break;
      }
      inst = inst.nextInstructionInCodeOrder();
    }
  }

  private int operandCost(OPT_Operand op, boolean shortFormImmediate) {
    if (op instanceof OPT_MemoryOperand) {
      int cost = 1; // might need SIB byte
      OPT_MemoryOperand mop = (OPT_MemoryOperand)op;
      if (!mop.disp.isZero()) {
        if (fits(mop.disp,8)) {
          cost += 1;
        } else {
          cost += 4;
        }
      }
      return cost;
    } else if (op instanceof OPT_IntConstantOperand) {
      if (shortFormImmediate && fits(((OPT_IntConstantOperand)op).value,8)) {
        return 1;
      } else {
        return 4;
      }
    } else {
      return 0;
    }
  }
  
  /**
   * Emit the given instruction, assuming that
   * it is a MIR_CondBranch instruction
   * and has a JCC operator
   *
   * @param inst the instruction to assemble
   */
  protected void doJCC(OPT_Instruction inst) {
    byte cond = getCond(MIR_CondBranch.getCond(inst));
    if (isImm(MIR_CondBranch.getTarget(inst))) {
      emitJCC_Cond_Imm(cond, getImm(MIR_CondBranch.getTarget(inst)));
    } else {
      if (VM.VerifyAssertions && !isLabel(MIR_CondBranch.getTarget(inst))) VM._assert(false, inst.toString());
      int sourceLabel = -inst.getmcOffset();
      int targetLabel = getLabel(MIR_CondBranch.getTarget(inst));
      int delta = targetLabel - sourceLabel;
      if (VM.VerifyAssertions) VM._assert(delta >= 0);
      if (delta<10 || (delta<90 && targetIsClose(inst, -targetLabel))) {
        int miStart = mi;
        VM_ForwardReference r =  new VM_ForwardReference.ShortBranch(mi, targetLabel);
        forwardRefs = VM_ForwardReference.enqueue(forwardRefs, r);
        setMachineCodes(mi++, (byte) (0x70 + cond));
        mi += 1; // leave space for displacement
        if (lister != null) lister.I(miStart, "J" + CONDITION[cond], 0);
      } else {
        emitJCC_Cond_Label(cond, targetLabel);
      }
    }
  }

  /**
   *  Emit the given instruction, assuming that
   * it is a MIR_Branch instruction
   * and has a JMP operator
   *
   * @param inst the instruction to assemble
   */
  protected void doJMP(OPT_Instruction inst) {
    if (isImm(MIR_Branch.getTarget(inst))) {
      emitJMP_Imm(getImm(MIR_Branch.getTarget(inst)));
    } else if (isLabel(MIR_Branch.getTarget(inst))) {
      int sourceLabel = -inst.getmcOffset();
      int targetLabel = getLabel(MIR_Branch.getTarget(inst));
      int delta = targetLabel - sourceLabel;
      if (VM.VerifyAssertions) VM._assert(delta >= 0);
      if (delta<10 || (delta<90 && targetIsClose(inst, -targetLabel))) {
        int miStart = mi;
        VM_ForwardReference r =  new VM_ForwardReference.ShortBranch(mi, targetLabel);
        forwardRefs = VM_ForwardReference.enqueue(forwardRefs, r);
        setMachineCodes(mi++, (byte) 0xEB);
        mi += 1; // leave space for displacement
        if (lister != null) lister.I(miStart, "JMP", 0);
      } else {
        emitJMP_Label(getLabel(MIR_Branch.getTarget(inst)));
      }
    } else if (isReg(MIR_Branch.getTarget(inst))) {
      emitJMP_Reg(getReg(MIR_Branch.getTarget(inst)));
    } else if (isAbs(MIR_Branch.getTarget(inst))) {
      emitJMP_Abs(getDisp(MIR_Branch.getTarget(inst)));
    } else if (isRegDisp(MIR_Branch.getTarget(inst))) {
      emitJMP_RegDisp(getBase(MIR_Branch.getTarget(inst)), getDisp(MIR_Branch.getTarget(inst)));
    } else if (isRegOff(MIR_Branch.getTarget(inst))) {
      emitJMP_RegOff(getIndex(MIR_Branch.getTarget(inst)), getScale(MIR_Branch.getTarget(inst)), getDisp(MIR_Branch.getTarget(inst)));
    } else if (isRegIdx(MIR_Branch.getTarget(inst))) {
      emitJMP_RegIdx(getBase(MIR_Branch.getTarget(inst)), getIndex(MIR_Branch.getTarget(inst)), getScale(MIR_Branch.getTarget(inst)), getDisp(MIR_Branch.getTarget(inst)));
    } else if (isRegInd(MIR_Branch.getTarget(inst))) {
      emitJMP_RegInd(getBase(MIR_Branch.getTarget(inst)));
    } else {
      if (VM.VerifyAssertions) VM._assert(false, inst.toString());
    }
  }

  /**
   * Debugging support (return a printable representation of the machine code).
   *
   * @param instr  An integer to be interpreted as a PowerPC instruction
   * @param offset the mcoffset (in bytes) of the instruction
   *
   */
  public static String disasm(int instr, int offset) {
    OPT_OptimizingCompilerException.TODO("OPT_Assembler: disassembler");
    return null;
  }

  /**
   * generate machine code into ir.machinecode
   * @param ir the IR to generate
   * @param shouldPrint should we print the machine code?
   * @return   the number of machinecode instructions generated
   */
  public static final int generateCode(OPT_IR ir, boolean shouldPrint) {
    int count = 0;

    for (OPT_Instruction p = ir.firstInstructionInCodeOrder(); 
         p != null; p = p.nextInstructionInCodeOrder()) 
      {
        p.setmcOffset( - ++count);
      }
      
    OPT_Assembler asm = new OPT_Assembler(count, shouldPrint);

    for (OPT_Instruction p = ir.firstInstructionInCodeOrder(); 
         p != null; p = p.nextInstructionInCodeOrder()) 
      {
        asm.doInst(p);
      }
      
    ir.MIRInfo.machinecode = asm.getMachineCodes();

    return ir.MIRInfo.machinecode.length();
  }

}
