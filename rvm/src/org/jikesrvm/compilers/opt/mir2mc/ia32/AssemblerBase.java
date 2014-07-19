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
package org.jikesrvm.compilers.opt.mir2mc.ia32;

import java.util.ArrayList;
import java.util.Enumeration;

import static org.jikesrvm.ia32.ArchConstants.SSE2_FULL;
import org.jikesrvm.ArchitectureSpecificOpt.AssemblerOpt;
import org.jikesrvm.ArchitectureSpecific.Assembler;
import org.jikesrvm.VM;
import org.jikesrvm.Constants;
import org.jikesrvm.compilers.common.assembler.ForwardReference;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.MIR_BinaryAcc;
import org.jikesrvm.compilers.opt.ir.MIR_Branch;
import org.jikesrvm.compilers.opt.ir.MIR_Call;
import org.jikesrvm.compilers.opt.ir.MIR_Compare;
import org.jikesrvm.compilers.opt.ir.MIR_CondBranch;
import org.jikesrvm.compilers.opt.ir.MIR_Lea;
import org.jikesrvm.compilers.opt.ir.MIR_LowTableSwitch;
import org.jikesrvm.compilers.opt.ir.MIR_Move;
import org.jikesrvm.compilers.opt.ir.MIR_Test;
import org.jikesrvm.compilers.opt.ir.MIR_Unary;
import org.jikesrvm.compilers.opt.ir.MIR_UnaryNoRes;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Operator;
import org.jikesrvm.compilers.opt.ir.Operators;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.ia32.PhysicalRegisterSet;
import org.jikesrvm.compilers.opt.ir.operand.BranchOperand;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.MemoryOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.TrapCodeOperand;
import org.jikesrvm.compilers.opt.ir.operand.ia32.IA32ConditionOperand;
import org.jikesrvm.compilers.opt.regalloc.ia32.PhysicalRegisterConstants;
import org.jikesrvm.ia32.TrapConstants;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.unboxed.Offset;

/**
 *  This class provides support functionality used by the generated
 * Assembler; it handles basic impedance-matching functionality
 * such as determining which addressing mode is suitable for a given
 * IA32MemoryOperand.  This class also provides some boilerplate
 * methods that do not depend on how instructions should actually be
 * assembled, like the top-level generateCode driver.  This class is
 * not meant to be used in isolation, but rather to provide support
 * from the Assembler.
 */
abstract class AssemblerBase extends Assembler
    implements Operators, Constants, PhysicalRegisterConstants {

  private static final boolean DEBUG_ESTIMATE = false;

  /**
   * Hold EBP register object for use in estimating size of memory operands.
   */
  private final Register EBP;

  /**
   * Hold EBP register object for use in estimating size of memory operands.
   */
  private final Register ESP;

  /**
   * Operators with byte arguments
   */
  private static final Operator[] byteSizeOperators;

  /**
   * Operators with word arguments
   */
  private static final Operator[] wordSizeOperators;

  /**
   * Operators with quad arguments
   */
  private static final Operator[] quadSizeOperators;

  static {
    ArrayList<Operator> temp = new ArrayList<Operator>();
    for (Operator opr : Operator.OperatorArray) {
      if (opr != null && opr.toString().indexOf("__b") != -1) {
        temp.add(opr);
      }
    }
    byteSizeOperators = temp.toArray(new Operator[temp.size()]);
    temp.clear();
    for (Operator opr : Operator.OperatorArray) {
      if (opr != null && opr.toString().indexOf("__w") != -1) {
        temp.add(opr);
      }
    }
    wordSizeOperators = temp.toArray(new Operator[temp.size()]);
    for (Operator opr : Operator.OperatorArray) {
      if (opr != null && opr.toString().indexOf("__q") != -1) {
        temp.add(opr);
      }
    }
    quadSizeOperators = temp.toArray(new Operator[temp.size()]);
  }

  /**
   * Construct Assembler object
   * @see Assembler
   */
  AssemblerBase(int bytecodeSize, boolean shouldPrint, IR ir) {
    super(bytecodeSize, shouldPrint);
    EBP = ir.regpool.getPhysicalRegisterSet().getEBP();
    ESP = ir.regpool.getPhysicalRegisterSet().getESP();
  }

  /**
   * Should code created by this assembler instance be allocated in the
   * hot code code space? The default answer for opt compiled code is yes
   * (otherwise why are we opt compiling it?).
   */
  @Override
  protected boolean isHotCode() { return true; }

  /**
   *  Is the given operand an immediate?  In the IA32 assembly, one
   * cannot specify floating-point constants, so the possible
   * immediates we may see are IntegerConstants and
   * TrapConstants (a trap constant really is an integer), and
   * jump targets for which the exact offset is known.
   *
   * @see #getImm
   *
   * @param op the operand being queried
   * @return true if op represents an immediate
   */
  boolean isImm(Operand op) {
    return (op instanceof IntConstantOperand) ||
           (op instanceof TrapCodeOperand) ||
           (op instanceof BranchOperand && op.asBranch().target.getmcOffset() >= 0);
  }

  /**
   *  Return the IA32 ISA encoding of the immediate value
   * represented by the the given operand.  This method assumes the
   * operand is an immediate and will likely throw a
   * ClassCastException if this not the case.  It treats
   * BranchOperands somewhat differently than isImm does: in
   * case a branch target is not resolved, it simply returns a wrong
   * answer and trusts the caller to ignore it. This behavior
   * simplifies life when generating code for ImmOrLabel operands.
   *
   * @see #isImm
   *
   * @param op the operand being queried
   * @return the immediate value represented by the operand
   */
  int getImm(Operand op) {
    if (op.isIntConstant()) {
      return op.asIntConstant().value;
    } else if (op.isBranch()) {
      // used by ImmOrLabel stuff
      return op.asBranch().target.getmcOffset();
    } else {
      return ((TrapCodeOperand) op).getTrapCode() + TrapConstants.RVM_TRAP_BASE;
    }
  }

  /**
   *  Is the given operand a register operand?
   *
   * @see #getReg
   *
   * @param op the operand being queried
   * @return true if op is an RegisterOperand
   */
  boolean isReg(Operand op) {
    return op.isRegister();
  }

  boolean isGPR_Reg(Operand op) {
    return isReg(op);
  }

  boolean isFPR_Reg(Operand op) {
    return isReg(op);
  }

  boolean isMM_Reg(Operand op) {
    return false; // MM registers not currently supported in the OPT compiler
  }

  boolean isXMM_Reg(Operand op) {
    return op.isRegister() && (op.isFloat() || op.isDouble());
  }

  /**
   * Return the machine-level register number corresponding to a given integer
   * Register. The optimizing compiler has its own notion of register
   * numbers, which is not the same as the numbers used by the IA32 ISA. This
   * method takes an optimizing compiler register and translates it into the
   * appropriate machine-level encoding. This method is not applied directly to
   * operands, but rather to register objects.
   *
   * @see #getBase
   * @see #getIndex
   *
   * @param reg the register being queried
   * @return the 3 bit machine-level encoding of reg
   */
  private GPR getGPMachineRegister(Register reg) {
    if (VM.VerifyAssertions) {
      VM._assert(PhysicalRegisterSet.getPhysicalRegisterType(reg) == INT_REG);
    }
    return GPR.lookup(reg.number - FIRST_INT);
  }

  /**
   * Return the machine-level register number corresponding to a
   * given Register.  The optimizing compiler has its own notion
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
  private MachineRegister getMachineRegister(Register reg) {
    int type = PhysicalRegisterSet.getPhysicalRegisterType(reg);
    MachineRegister result;
    if (type == INT_REG) {
      result = GPR.lookup(reg.number - FIRST_INT);
    } else {
      if (VM.VerifyAssertions) VM._assert(type == DOUBLE_REG);
      if (SSE2_FULL) {
        if (reg.number < FIRST_SPECIAL) {
          result = XMM.lookup(reg.number - FIRST_DOUBLE);
        } else if (reg.number == ST0) {
          result = FP0;
        } else {
          if (VM.VerifyAssertions) VM._assert(reg.number == ST1);
          result = FP1;
        }
      } else {
        result = FPR.lookup(reg.number - FIRST_DOUBLE);
      }
    }
    return result;
  }

  /**
   * Given a register operand, return the 3 bit IA32 ISA encoding
   * of that register.  This function translates an optimizing
   * compiler register operand into the 3 bit IA32 ISA encoding that
   * can be passed to the Assembler.  This function assumes its
   * operand is a register operand, and will blow up if it is not;
   * use isReg to check operands passed to this method.
   *
   * @see #isReg
   *
   * @param op the register operand being queried
   * @return the 3 bit IA32 ISA encoding of op
   */
  MachineRegister getReg(Operand op) {
    return getMachineRegister(op.asRegister().getRegister());
  }

  GPR getGPR_Reg(Operand op) {
    return getGPMachineRegister(op.asRegister().getRegister());
  }

  FPR getFPR_Reg(Operand op) {
    return (FPR)getMachineRegister(op.asRegister().getRegister());
  }

  MM getMM_Reg(Operand op) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED, "MM registers not currently supported in the opt compiler");
    return null;
  }

  XMM getXMM_Reg(Operand op) {
    return (XMM)getMachineRegister(op.asRegister().getRegister());
  }

  /**
   * Given a memory operand, return the 3 bit IA32 ISA encoding
   * of its base regsiter.  This function translates the optimizing
   * compiler register operand representing the base of the given
   * memory operand into the 3 bit IA32 ISA encoding that
   * can be passed to the Assembler.  This function assumes its
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
  GPR getBase(Operand op) {
    return getGPMachineRegister(((MemoryOperand) op).base.getRegister());
  }

  /**
   * Given a memory operand, return the 3 bit IA32 ISA encoding
   * of its index register.  This function translates the optimizing
   * compiler register operand representing the index of the given
   * memory operand into the 3 bit IA32 ISA encoding that
   * can be passed to the Assembler.  This function assumes its
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
  GPR getIndex(Operand op) {
    return getGPMachineRegister(((MemoryOperand) op).index.getRegister());
  }

  /**
   *  Given a memory operand, return the 2 bit IA32 ISA encoding
   * of its scale, suitable for passing to the Assembler to mask
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
  short getScale(Operand op) {
    return ((MemoryOperand) op).scale;
  }

  /**
   *  Given a memory operand, return the 2 bit IA32 ISA encoding
   * of its scale, suitable for passing to the Assembler to mask
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
  Offset getDisp(Operand op) {
    return ((MemoryOperand) op).disp;
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
  boolean isRegDisp(Operand op) {
    if (op instanceof MemoryOperand) {
      MemoryOperand mop = (MemoryOperand) op;
      return (mop.base != null) && (mop.index == null) && (!mop.disp.isZero()) && (mop.scale == 0);
    } else {
      return false;
    }
  }

  /**
   * Determine if a given operand is a memory operand representing
   * absolute mode addressing.  This method takes an
   * arbitrary operand, checks whether it is a memory operand, and,
   * if it is, checks whether it should be assembled as IA32
   * absolute address mode.  That is, does it have a non-zero
   * displacement, but no scale, no scale and no index register?
   *
   * @param op the operand being queried
   * @return true if op should be assembled as absolute mode
   */
  boolean isAbs(Operand op) {
    if (op instanceof MemoryOperand) {
      MemoryOperand mop = (MemoryOperand) op;
      return (mop.base == null) && (mop.index == null) && (!mop.disp.isZero()) && (mop.scale == 0);
    } else {
      return false;
    }
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
  boolean isRegInd(Operand op) {
    if (op instanceof MemoryOperand) {
      MemoryOperand mop = (MemoryOperand) op;
      return (mop.base != null) && (mop.index == null) && (mop.disp.isZero()) && (mop.scale == 0);
    } else {
      return false;
    }
  }

  /**
   * Determine if a given operand is a memory operand representing
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
  boolean isRegOff(Operand op) {
    if (op instanceof MemoryOperand) {
      MemoryOperand mop = (MemoryOperand) op;
      return (mop.base == null) && (mop.index != null);
    } else {
      return false;
    }
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
  boolean isRegIdx(Operand op) {
    if (op instanceof MemoryOperand) {
      return !(isAbs(op) || isRegInd(op) || isRegDisp(op) || isRegOff(op));
    } else {
      return false;
    }
  }

  /**
   *  Return the condition bits of a given optimizing compiler
   * condition operand.  This method returns the IA32 ISA bits
   * representing a given condition operand, suitable for passing to
   * the Assembler to encode into the opcode of a SET, Jcc or
   * CMOV instruction.  This being IA32, there are of course
   * exceptions in the binary encoding of conditions (see FCMOV),
   * but the Assembler handles that.  This function assumes its
   * argument is an IA32ConditionOperand, and will blow up if it
   * is not.
   *
   * @param op the operand being queried
   * @return the bits that (usually) represent the given condition
   * in the IA32 ISA */
  byte getCond(Operand op) {
    return ((IA32ConditionOperand) op).value;
  }

  /**
   *  Is the given operand an IA32 condition operand?
   *
   * @param op the operand being queried
   * @return true if op is an IA32 condition operand
   */
  boolean isCond(Operand op) {
    return (op instanceof IA32ConditionOperand);
  }

  /**
   *  Return the label representing the target of the given branch
   * operand.  These labels are used to represent branch targets
   * that have not yet been assembled, and so cannot be given
   * concrete machine code offsets.  All instructions are numbered
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
  int getLabel(Operand op) {
    if (op instanceof IntConstantOperand) {
      // used by ImmOrLabel stuff
      return 0;
    } else {
      if (op.asBranch().target.getmcOffset() < 0) {
        return -op.asBranch().target.getmcOffset();
      } else {
        return -1;
      }
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
  boolean isLabel(Operand op) {
    return (op instanceof BranchOperand && op.asBranch().target.getmcOffset() < 0);
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
  @NoInline
  boolean isImmOrLabel(Operand op) {
    // TODO: Remove NoInlinePragma, work around for leave SSA bug
    return (isImm(op) || isLabel(op));
  }

  /**
   * Does the given instruction operate upon byte-sized data?  The
   * opt compiler does not represent the size of register data, so
   * this method typically looks at the memory operand, if any, and
   * checks whether that is a byte.  This does not work for the
   * size-converting moves (MOVSX and MOVZX), and those instructions
   * use the operator convention that __b on the end of the operator
   * name means operate upon byte data.
   *
   * @param inst the instruction being queried
   * @return {@code true} if inst operates upon byte data
   */
  boolean isByte(Instruction inst) {
    for(Operator opr : byteSizeOperators){
      if (opr == inst.operator) {
        return true;
      }
    }

    for (int i = 0; i < inst.getNumberOfOperands(); i++) {
      Operand op = inst.getOperand(i);
      if (op instanceof MemoryOperand) {
        return (((MemoryOperand) op).size == 1);
      }
    }

    return false;
  }

  /**
   * Does the given instruction operate upon word-sized data?  The
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
  boolean isWord(Instruction inst) {
    for(Operator opr : wordSizeOperators){
      if (opr == inst.operator) {
        return true;
      }
    }

    for (int i = 0; i < inst.getNumberOfOperands(); i++) {
      Operand op = inst.getOperand(i);
      if (op instanceof MemoryOperand) {
        return (((MemoryOperand) op).size == 2);
      }
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
   * @return {@code true} if inst operates upon quad data
   */
  boolean isQuad(Instruction inst) {
    for(Operator opr : quadSizeOperators){
      if (opr == inst.operator) {
        return true;
      }
    }

    for (int i = 0; i < inst.getNumberOfOperands(); i++) {
      Operand op = inst.getOperand(i);
      if (op instanceof MemoryOperand) {
        return (((MemoryOperand) op).size == 8);
      }
    }

    return false;
  }

  /**
   * Given a forward branch instruction and its target,
   * determine (conservatively) if the relative offset to the
   * target is less than 127 bytes
   * @param start the branch instruction
   * @param target the value of the mcOffset of the target label
   * @return {@code true} if the relative offset will be less than 127, false otherwise
   */
  protected boolean targetIsClose(Instruction start, int target) {
    Instruction inst = start.nextInstructionInCodeOrder();
    final int budget = 120; // slight fudge factor could be 127
    int offset = 0;
    while (true) {
      if (offset <= budget) return false;
      if (inst.getmcOffset() == target) {
        return true;
      }
      offset += estimateSize(inst, offset);
      inst = inst.nextInstructionInCodeOrder();
    }
  }

  protected int estimateSize(Instruction inst, int offset) {
    switch (inst.getOpcode()) {
      case LABEL_opcode:
        return (4 - offset) & 3; // return size of nop required for alignment
      case BBEND_opcode:
      case READ_CEILING_opcode:
      case WRITE_FLOOR_opcode:
      case UNINT_BEGIN_opcode:
      case UNINT_END_opcode: {
        // these generate no code
        return 0;
      }
      case IA32_METHODSTART_opcode:
        return 12;
      // Generated from the same case in Assembler
      case IA32_ADC_opcode:
      case IA32_ADD_opcode:
      case IA32_AND_opcode:
      case IA32_OR_opcode:
      case IA32_SBB_opcode:
      case IA32_XOR_opcode: {
        int size = 2; // opcode + modr/m
        size += operandCost(MIR_BinaryAcc.getResult(inst), true);
        size += operandCost(MIR_BinaryAcc.getValue(inst), true);
        return size;
      }
      case IA32_CMP_opcode: {
        int size = 2; // opcode + modr/m
        size += operandCost(MIR_Compare.getVal1(inst), true);
        size += operandCost(MIR_Compare.getVal2(inst), true);
        return size;
      }
      case IA32_TEST_opcode: {
        int size = 2; // opcode + modr/m
        size += operandCost(MIR_Test.getVal1(inst), false);
        size += operandCost(MIR_Test.getVal2(inst), false);
        return size;
      }
      case IA32_ADDSD_opcode:
      case IA32_SUBSD_opcode:
      case IA32_MULSD_opcode:
      case IA32_DIVSD_opcode:
      case IA32_XORPD_opcode:
      case IA32_SQRTSD_opcode:
      case IA32_ADDSS_opcode:
      case IA32_SUBSS_opcode:
      case IA32_MULSS_opcode:
      case IA32_DIVSS_opcode:
      case IA32_XORPS_opcode: {
        int size = 4; // opcode + modr/m
        Operand value = MIR_BinaryAcc.getValue(inst);
        size += operandCost(value, false);
        return size;
      }
      case IA32_UCOMISS_opcode: {
        int size = 3; // opcode + modr/m
        Operand val2 = MIR_Compare.getVal2(inst);
        size += operandCost(val2, false);
        return size;
      }
      case IA32_UCOMISD_opcode: {
        int size = 4; // opcode + modr/m
        Operand val2 = MIR_Compare.getVal2(inst);
        size += operandCost(val2, false);
        return size;
      }
      case IA32_CVTSI2SS_opcode:
      case IA32_CVTSI2SD_opcode:
      case IA32_CVTSS2SD_opcode:
      case IA32_CVTSD2SS_opcode:
      case IA32_CVTSD2SI_opcode:
      case IA32_CVTTSD2SI_opcode:
      case IA32_CVTSS2SI_opcode:
      case IA32_CVTTSS2SI_opcode: {
        int size = 4; // opcode + modr/m
        Operand result = MIR_Unary.getResult(inst);
        Operand value = MIR_Unary.getVal(inst);
        size += operandCost(result, false);
        size += operandCost(value, false);
        return size;
      }
      case IA32_CMPEQSD_opcode:
      case IA32_CMPLTSD_opcode:
      case IA32_CMPLESD_opcode:
      case IA32_CMPUNORDSD_opcode:
      case IA32_CMPNESD_opcode:
      case IA32_CMPNLTSD_opcode:
      case IA32_CMPNLESD_opcode:
      case IA32_CMPORDSD_opcode:
      case IA32_CMPEQSS_opcode:
      case IA32_CMPLTSS_opcode:
      case IA32_CMPLESS_opcode:
      case IA32_CMPUNORDSS_opcode:
      case IA32_CMPNESS_opcode:
      case IA32_CMPNLTSS_opcode:
      case IA32_CMPNLESS_opcode:
      case IA32_CMPORDSS_opcode: {
        int size = 5; // opcode + modr/m + type
        Operand value = MIR_BinaryAcc.getValue(inst);
        size += operandCost(value, false);
        return size;
      }
      case IA32_MOVD_opcode:
      case IA32_MOVLPD_opcode:
      case IA32_MOVQ_opcode:
      case IA32_MOVSS_opcode:
      case IA32_MOVSD_opcode: {
        int size = 4; // opcode + modr/m
        Operand result = MIR_Move.getResult(inst);
        Operand value = MIR_Move.getValue(inst);
        size += operandCost(result, false);
        size += operandCost(value, false);
        return size;
      }
      case IA32_PUSH_opcode: {
        Operand op = MIR_UnaryNoRes.getVal(inst);
        int size = 0;
        if (op instanceof RegisterOperand) {
          size += 1;
        } else if (op instanceof IntConstantOperand) {
          if (fits(((IntConstantOperand) op).value, 8)) {
            size += 2;
          } else {
            size += 5;
          }
        } else {
          size += (2 + operandCost(op, true));
        }
        return size;
      }
      case IA32_LEA_opcode: {
        int size = 2; // opcode + 1 byte modr/m
        size += operandCost(MIR_Lea.getResult(inst), false);
        size += operandCost(MIR_Lea.getValue(inst), false);
        return size;
      }
      case IA32_MOV_opcode: {
        int size = 2; // opcode + modr/m
        Operand result = MIR_Move.getResult(inst);
        Operand value = MIR_Move.getValue(inst);
        size += operandCost(result, false);
        size += operandCost(value, false);
        return size;
      }
      case MIR_LOWTABLESWITCH_opcode:
        return MIR_LowTableSwitch.getNumberOfTargets(inst)*4 + 14;
      case IA32_OFFSET_opcode:
        return 4;
      case IA32_JCC_opcode:
      case IA32_JMP_opcode:
        return 6; // assume long form
      case IA32_LOCK_opcode:
        return 1;
      case IG_PATCH_POINT_opcode:
        return 8;
      case IA32_INT_opcode:
        return 2;
      case IA32_RET_opcode:
        return 3;
      case IA32_CALL_opcode:
        Operand target = MIR_Call.getTarget(inst);
        if (isImmOrLabel(target)) {
          return 5; // opcode + 32bit immediate
        } else {
          return 2 + operandCost(target, false); // opcode + modr/m
        }
      default: {
        int size = 3; // 2 bytes opcode + 1 byte modr/m
        for (Enumeration<Operand> opEnum = inst.getRootOperands(); opEnum.hasMoreElements();) {
          Operand op = opEnum.nextElement();
          size += operandCost(op, false);
        }
        return size;
      }
    }
  }

  private int operandCost(Operand op, boolean shortFormImmediate) {
    if (op instanceof MemoryOperand) {
      MemoryOperand mop = (MemoryOperand) op;
      // If it's a 2byte mem location, we're going to need an override prefix
      int prefix = mop.size == 2 ? 1 : 0;

      // Deal with EBP wierdness
      if (mop.base != null && mop.base.getRegister() == EBP) {
        if (mop.index != null) {
          // forced into SIB + 32 bit displacement no matter what disp is
          return prefix + 5;
        }
        if (fits(mop.disp, 8)) {
          return prefix + 1;
        } else {
          return prefix + 4;
        }
      }
      if (mop.index != null && mop.index.getRegister() == EBP) {
        // forced into SIB + 32 bit displacement no matter what disp is
        return prefix + 5;
      }

      // Deal with ESP wierdness -- requires SIB byte even when index is null
      if (mop.base != null && mop.base.getRegister() == ESP) {
        if (fits(mop.disp, 8)) {
          return prefix + 2;
        } else {
          return prefix + 5;
        }
      }

      if (mop.index == null) {
        // just displacement to worry about
        if (mop.disp.isZero()) {
          return prefix + 0;
        } else if (fits(mop.disp, 8)) {
          return prefix + 1;
        } else {
          return prefix + 4;
        }
      } else {
        // have a SIB
        if (mop.base == null && mop.scale != 0) {
          // forced to 32 bit displacement even if it would fit in 8
          return prefix + 5;
        } else {
          if (mop.disp.isZero()) {
            return prefix + 1;
          } else if (fits(mop.disp, 8)) {
            return prefix + 2;
          } else {
            return prefix + 5;
          }
        }
      }
    } else if (op instanceof IntConstantOperand) {
      if (shortFormImmediate && fits(((IntConstantOperand) op).value, 8)) {
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
  protected void doJCC(Instruction inst) {
    byte cond = getCond(MIR_CondBranch.getCond(inst));
    if (isImm(MIR_CondBranch.getTarget(inst))) {
      emitJCC_Cond_Imm(cond, getImm(MIR_CondBranch.getTarget(inst)));
    } else {
      if (VM.VerifyAssertions && !isLabel(MIR_CondBranch.getTarget(inst))) {
        String msg = inst.toString();
        VM._assert(VM.NOT_REACHED, msg);
      }
      int sourceLabel = -inst.getmcOffset();
      int targetLabel = getLabel(MIR_CondBranch.getTarget(inst));
      int delta = targetLabel - sourceLabel;
      if (VM.VerifyAssertions) VM._assert(delta >= 0);
      if (delta < 10 || (delta < 90 && targetIsClose(inst, -targetLabel))) {
        int miStart = mi;
        ForwardReference r = new ForwardReference.ShortBranch(mi, targetLabel);
        forwardRefs = ForwardReference.enqueue(forwardRefs, r);
        setMachineCodes(mi++, (byte) (0x70 + cond));
        mi += 1; // leave space for displacement
        if (lister != null) lister.I(miStart, "J" + CONDITION[cond], 0);
      } else {
        emitJCC_Cond_Label(cond, targetLabel);
      }
    }
  }

  /**
   * Emit the given instruction, assuming that
   * it is a MIR_Branch instruction
   * and has a JMP operator
   *
   * @param inst the instruction to assemble
   */
  protected void doJMP(Instruction inst) {
    if (isImm(MIR_Branch.getTarget(inst))) {
      emitJMP_Imm(getImm(MIR_Branch.getTarget(inst)));
    } else if (isLabel(MIR_Branch.getTarget(inst))) {
      int sourceLabel = -inst.getmcOffset();
      int targetLabel = getLabel(MIR_Branch.getTarget(inst));
      int delta = targetLabel - sourceLabel;
      if (VM.VerifyAssertions) VM._assert(delta >= 0);
      if (delta < 10 || (delta < 90 && targetIsClose(inst, -targetLabel))) {
        int miStart = mi;
        ForwardReference r = new ForwardReference.ShortBranch(mi, targetLabel);
        forwardRefs = ForwardReference.enqueue(forwardRefs, r);
        setMachineCodes(mi++, (byte) 0xEB);
        mi += 1; // leave space for displacement
        if (lister != null) lister.I(miStart, "JMP", 0);
      } else {
        emitJMP_Label(getLabel(MIR_Branch.getTarget(inst)));
      }
    } else if (isReg(MIR_Branch.getTarget(inst))) {
      emitJMP_Reg(getGPR_Reg(MIR_Branch.getTarget(inst)));
    } else if (isAbs(MIR_Branch.getTarget(inst))) {
      emitJMP_Abs(getDisp(MIR_Branch.getTarget(inst)).toWord().toAddress());
    } else if (isRegDisp(MIR_Branch.getTarget(inst))) {
      emitJMP_RegDisp(getBase(MIR_Branch.getTarget(inst)), getDisp(MIR_Branch.getTarget(inst)));
    } else if (isRegOff(MIR_Branch.getTarget(inst))) {
      emitJMP_RegOff(getIndex(MIR_Branch.getTarget(inst)),
                     getScale(MIR_Branch.getTarget(inst)),
                     getDisp(MIR_Branch.getTarget(inst)));
    } else if (isRegIdx(MIR_Branch.getTarget(inst))) {
      emitJMP_RegIdx(getBase(MIR_Branch.getTarget(inst)),
                     getIndex(MIR_Branch.getTarget(inst)),
                     getScale(MIR_Branch.getTarget(inst)),
                     getDisp(MIR_Branch.getTarget(inst)));
    } else if (isRegInd(MIR_Branch.getTarget(inst))) {
      emitJMP_RegInd(getBase(MIR_Branch.getTarget(inst)));
    } else {
      if (VM.VerifyAssertions) {
        String msg = inst.toString();
        VM._assert(VM.NOT_REACHED, msg);
      }
    }
  }

  /**
   * Emit the given instruction, assuming that
   * it is a MIR_LowTableSwitch instruction
   * and has a MIR_LOWTABLESWITCH operator
   *
   * @param inst the instruction to assemble
   */
  protected void doLOWTABLESWITCH(Instruction inst) {
    int n = MIR_LowTableSwitch.getNumberOfTargets(inst); // n = number of normal cases (0..n-1)
    GPR ms = GPR.lookup(MIR_LowTableSwitch.getMethodStart(inst).getRegister().number);
    GPR idx = GPR.lookup(MIR_LowTableSwitch.getIndex(inst).getRegister().number);
    // idx += [ms + idx<<2 + ??] - we will patch ?? when we know the placement of the table
    int toPatchAddress = getMachineCodeIndex();
    if (VM.buildFor32Addr()) {
      emitMOV_Reg_RegIdx(idx, ms, idx, Assembler.WORD, Offset.fromIntZeroExtend(Integer.MAX_VALUE));
      emitADD_Reg_Reg(idx, ms);
    } else {
      emitMOV_Reg_RegIdx(idx, ms, idx, Assembler.WORD, Offset.fromIntZeroExtend(Integer.MAX_VALUE));
      emitADD_Reg_Reg_Quad(idx, ms);
    }
    // JMP T0
    emitJMP_Reg(idx);
    emitNOP((4-getMachineCodeIndex()) & 3); // align table
    // create table of offsets from start of method
    patchSwitchTableDisplacement(toPatchAddress);
    for (int i = 0; i < n; i++) {
      Operand target = MIR_LowTableSwitch.getTarget(inst, i);
      emitOFFSET_Imm_ImmOrLabel(i, getImm(target), getLabel(target));
    }
  }

  /**
   * Debugging support (return a printable representation of the machine code).
   *
   * @param instr  An integer to be interpreted as a PowerPC instruction
   * @param offset the mcoffset (in bytes) of the instruction
   */
  public String disasm(int instr, int offset) {
    OptimizingCompilerException.TODO("Assembler: disassembler");
    return null;
  }

  /**
   * generate machine code into ir.machinecode.
   * @param ir the IR to generate
   * @param shouldPrint should we print the machine code?
   * @return the number of machinecode instructions generated
   */
  public static int generateCode(IR ir, boolean shouldPrint) {
    int count = 0;
    AssemblerOpt asm = new AssemblerOpt(count, shouldPrint, ir);

    for (Instruction p = ir.firstInstructionInCodeOrder(); p != null; p = p.nextInstructionInCodeOrder()) {
      // Set the mc offset of all instructions to their negative position.
      // A positive value in their position means they have been created
      // by the assembler.
      count++;
      p.setmcOffset(-count);
      if (p.operator() == Operators.MIR_LOWTABLESWITCH) {
        // Table switch kludge, as these will occupy multiple slots in the
        // generated assembler
        count += MIR_LowTableSwitch.getNumberOfTargets(p);
      }
    }

    for (Instruction p = ir.firstInstructionInCodeOrder(); p != null; p = p.nextInstructionInCodeOrder()) {
      if (DEBUG_ESTIMATE) {
        int start = asm.getMachineCodeIndex();
        int estimate = asm.estimateSize(p, start);
        asm.doInst(p);
        int end = asm.getMachineCodeIndex();
        if (end - start > estimate) {
          VM.sysWriteln("Bad estimate: " + (end - start) + " " + estimate + " " + p);
          VM.sysWrite("\tMachine code: ");
          asm.writeLastInstruction(start);
          VM.sysWriteln();
        }
      } else {
        asm.doInst(p);
      }
    }

    ir.MIRInfo.machinecode = asm.getMachineCodes();

    return ir.MIRInfo.machinecode.length();
  }

}
