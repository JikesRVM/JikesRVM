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
package org.jikesrvm.compilers.opt.ia32;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.compilers.opt.OPT_BURS;
import org.jikesrvm.compilers.opt.OPT_BURS_MemOp_Helpers;
import org.jikesrvm.compilers.opt.OPT_DefUse;
import org.jikesrvm.compilers.opt.OPT_OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.Binary;
import org.jikesrvm.compilers.opt.ir.CacheOp;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.CondMove;
import org.jikesrvm.compilers.opt.ir.GuardedBinary;
import org.jikesrvm.compilers.opt.ir.IfCmp;
import org.jikesrvm.compilers.opt.ir.LowTableSwitch;
import org.jikesrvm.compilers.opt.ir.MIR_BinaryAcc;
import org.jikesrvm.compilers.opt.ir.MIR_Call;
import org.jikesrvm.compilers.opt.ir.MIR_Compare;
import org.jikesrvm.compilers.opt.ir.MIR_CompareExchange;
import org.jikesrvm.compilers.opt.ir.MIR_CompareExchange8B;
import org.jikesrvm.compilers.opt.ir.MIR_CondBranch;
import org.jikesrvm.compilers.opt.ir.MIR_CondMove;
import org.jikesrvm.compilers.opt.ir.MIR_ConvertDW2QW;
import org.jikesrvm.compilers.opt.ir.MIR_Divide;
import org.jikesrvm.compilers.opt.ir.MIR_DoubleShift;
import org.jikesrvm.compilers.opt.ir.MIR_Lea;
import org.jikesrvm.compilers.opt.ir.MIR_LowTableSwitch;
import org.jikesrvm.compilers.opt.ir.MIR_Move;
import org.jikesrvm.compilers.opt.ir.MIR_Multiply;
import org.jikesrvm.compilers.opt.ir.MIR_Nullary;
import org.jikesrvm.compilers.opt.ir.MIR_RDTSC;
import org.jikesrvm.compilers.opt.ir.MIR_Set;
import org.jikesrvm.compilers.opt.ir.MIR_Test;
import org.jikesrvm.compilers.opt.ir.MIR_TrapIf;
import org.jikesrvm.compilers.opt.ir.MIR_Unary;
import org.jikesrvm.compilers.opt.ir.MIR_UnaryAcc;
import org.jikesrvm.compilers.opt.ir.MIR_UnaryNoRes;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.Nullary;
import org.jikesrvm.compilers.opt.ir.OPT_BranchOperand;
import org.jikesrvm.compilers.opt.ir.OPT_BranchProfileOperand;
import org.jikesrvm.compilers.opt.ir.OPT_ConditionOperand;
import org.jikesrvm.compilers.opt.ir.OPT_ConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_DoubleConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_FloatConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_InlinedOsrTypeInfoOperand;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_LocationOperand;
import org.jikesrvm.compilers.opt.ir.OPT_LongConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_MemoryOperand;
import org.jikesrvm.compilers.opt.ir.OPT_MethodOperand;
import org.jikesrvm.compilers.opt.ir.OPT_Operand;
import org.jikesrvm.compilers.opt.ir.OPT_Operator;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.CALL_SAVE_VOLATILE;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DOUBLE_CMPL;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.FLOAT_CMPL;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.GUARD_MOVE;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_SHL;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_SHR;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_USHR;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_ADC;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_ADD;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_AND;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_CALL;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_CDQ;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_CMOV;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_CMP;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_FCMOV;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_FCOMI;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_FCOMIP;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_FILD;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_FIST;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_FLD;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_FLD1;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_FLDCW;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_FLDL2E;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_FLDL2T;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_FLDLG2;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_FLDLN2;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_FLDPI;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_FLDZ;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_FMOV;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_FNSTCW;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_FPREM;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_FSTP;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_IDIV;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_IMUL2;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_JCC;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_LEA;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_LOCK_CMPXCHG;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_LOCK_CMPXCHG8B;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_MOV;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_MOVSX__B;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_MOVZX__B;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_MUL;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_NEG;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_NOT;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_OR;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_RCR;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_RDTSC;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_SAR;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_SBB;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_SET__B;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_SHL;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_SHLD;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_SHR;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_SHRD;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_SUB;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_SYSCALL;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_TEST;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_TRAPIF;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_XOR;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IR_PROLOGUE;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.MIR_LOWTABLESWITCH;
import org.jikesrvm.compilers.opt.ir.OPT_Register;
import org.jikesrvm.compilers.opt.ir.OPT_RegisterOperand;
import org.jikesrvm.compilers.opt.ir.OPT_RegisterOperandEnumeration;
import org.jikesrvm.compilers.opt.ir.OPT_StackLocationOperand;
import org.jikesrvm.compilers.opt.ir.OPT_TrapCodeOperand;
import org.jikesrvm.compilers.opt.ir.OPT_TrueGuardOperand;
import org.jikesrvm.compilers.opt.ir.OsrPoint;
import org.jikesrvm.compilers.opt.ir.Prologue;
import org.jikesrvm.compilers.opt.ir.TrapIf;
import org.jikesrvm.compilers.opt.ir.Unary;
import org.jikesrvm.compilers.opt.ir.ia32.OPT_BURSManagedFPROperand;
import org.jikesrvm.compilers.opt.ir.ia32.OPT_IA32ConditionOperand;
import org.jikesrvm.runtime.VM_Entrypoints;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.runtime.VM_Runtime;
import org.vmmagic.unboxed.Offset;

/**
 * Contains IA32-specific helper functions for BURS.
 */
abstract class OPT_BURS_Helpers extends OPT_BURS_MemOp_Helpers {
  /** Constant log10(2), supported as an x87 constant */
  private static final double LG2 = Double
      .parseDouble("0.3010299956639811952256464283594894482");

  /** Constant ln(2), supported as an x87 constant */
  private static final double LN2 = Double
      .parseDouble("0.6931471805599453094286904741849753009");

  /** Constant log2(e), supported as an x87 constant */
  private static final double L2E = Double
      .parseDouble("1.4426950408889634073876517827983434472");

  /** Constant log2(10), supported as an x87 constant */
  private static final double L2T = Double
      .parseDouble("3.3219280948873623478083405569094566090");

  /**
   * When emitting certain rules this holds the condition code state to be
   * consumed by a parent rule
   */
  private OPT_ConditionOperand cc;

  /** Constructor */
  OPT_BURS_Helpers(OPT_BURS burs) {
    super(burs);
  }

  /**
   * Create the MIR instruction given by operator from the Binary LIR operands
   * @param operator the MIR operator
   * @param s the instruction being replaced
   * @param result the destination register/memory
   * @param val1 the first operand
   * @param val2 the second operand
   */
  protected void EMIT_Commutative(OPT_Operator operator, OPT_Instruction s, OPT_Operand result, OPT_Operand val1, OPT_Operand val2) {
    if(VM.VerifyAssertions) VM._assert(result.isRegister() || result.isMemory());
    // Swap operands to reduce chance of generating a move or to normalize
    // constants into val2
    if (val2.similar(result) || val1.isConstant()) {
      OPT_Operand temp = val1;
      val1 = val2;
      val2 = temp;
    }
    // Do we need to move prior to the operator - result = val1
    if (!result.similar(val1)) {
      EMIT(CPOS(s, MIR_Move.create(IA32_MOV, result.copy(), val1)));
    }   
    EMIT(MIR_BinaryAcc.mutate(s, operator, result, val2));
  }

  /**
   * Create the MIR instruction given by operator from the Binary LIR operands
   * @param operator the MIR operator
   * @param s the instruction being replaced
   * @param result the destination register/memory
   * @param val1 the first operand
   * @param val2 the second operand
   */
  protected void EMIT_NonCommutative(OPT_Operator operator, OPT_Instruction s, OPT_Operand result, OPT_Operand val1, OPT_Operand val2) {
    if(VM.VerifyAssertions) VM._assert(result.isRegister() || result.isMemory());
    if (result.similar(val1)) {
      // Straight forward case where instruction is already in accumulate form
      EMIT(MIR_BinaryAcc.mutate(s, operator, result, val2));
    }
    else if (!result.similar(val2)) {
      // Move first operand to result and perform operator on result, if
      // possible redundant moves should be remove by register allocator
      EMIT(CPOS(s, MIR_Move.create(IA32_MOV, result.copy(), val1)));
      EMIT(MIR_BinaryAcc.mutate(s, operator, result, val2));
    }
    else {
      // Potential to clobber second operand during move to result. Use a
      // temporary register to perform the operation and rely on register
      // allocator to remove redundant moves
      OPT_RegisterOperand temp = regpool.makeTemp(result);
      EMIT(CPOS(s, MIR_Move.create(IA32_MOV, temp, val1)));
      EMIT(MIR_BinaryAcc.mutate(s, operator, temp.copyRO(), val2));      
      EMIT(CPOS(s, MIR_Move.create(IA32_MOV, result, temp.copyRO())));
    }   
  }

  /**
   * Create the MIR instruction given by operator from the Binary LIR operands
   * @param operator the MIR operator
   * @param s the instruction being replaced
   * @param result the destination register/memory
   * @param value the first operand
   */
  protected void EMIT_Unary(OPT_Operator operator, OPT_Instruction s, OPT_Operand result, OPT_Operand value) {
    if(VM.VerifyAssertions) VM._assert(result.isRegister() || result.isMemory());
    // Do we need to move prior to the operator - result = val1
    if (!result.similar(value)) {
      EMIT(CPOS(s, MIR_Move.create(IA32_MOV, result.copy(), value)));
    }   
    EMIT(MIR_UnaryAcc.mutate(s, operator, result));
  }

  /**
   * Convert the given comparison with a boolean (int) value into a condition
   * suitable for the carry flag
   * @param x the value 1 (true) or 0 (false)
   * @param cond either equal or not equal
   * @return lower or higher equal
   */
  protected static OPT_ConditionOperand BIT_TEST(int x, OPT_ConditionOperand cond) {
    if (VM.VerifyAssertions) VM._assert((x==0)||(x==1));
    if (VM.VerifyAssertions) VM._assert(EQ_NE(cond));
    if ((x == 1 && cond.isEQUAL())||
        (x == 0 && cond.isNOT_EQUAL())) {
      return OPT_ConditionOperand.LOWER();
    } else {
      return OPT_ConditionOperand.HIGHER_EQUAL();
    }
  }

  /**
   * Follow a chain of Move operations filtering back to a def
   *
   * @param use the place to start from
   * @return the operand at the start of the chain
   */
  protected static OPT_Operand follow(OPT_Operand use) {
    if (!use.isRegister()) {
      return use;
    } else {
      OPT_RegisterOperand rop = use.asRegister();
      OPT_RegisterOperandEnumeration defs = OPT_DefUse.defs(rop.register);
      if (!defs.hasMoreElements()) {
        return use;
      } else {
        OPT_Operand def = defs.next();
        if (defs.hasMoreElements()) {
          return def;
        } else {
          OPT_Instruction instr = def.instruction;
          if (Move.conforms(instr)) {
            return follow(Move.getVal(instr));
          } else if (MIR_Move.conforms(instr)) {
            return follow(MIR_Move.getValue(instr));
          } else {
            return def;
          }
        }
      }
    }
  }

  /**
   * Remember a condition code in a child node
   *
   * @param c condition code to record
   */
  protected final void pushCOND(OPT_ConditionOperand c) {
    if (VM.VerifyAssertions) {
      VM._assert(cc == null);
    }
    cc = c;
  }

  /**
   * Acquire remembered condition code in parent
   *
   * @return condition code
   */
  protected final OPT_ConditionOperand consumeCOND() {
    OPT_ConditionOperand ans = cc;
    if (VM.VerifyAssertions) {
      VM._assert(cc != null);
    }
    cc = null;
    return ans;
  }

  /**
   * Can an IV be the scale in a LEA instruction?
   *
   * @param op operand to examine
   * @param trueCost the cost if this can be part of an LEA
   * @return trueCost or INFINITE
   */
  protected final int LEA_SHIFT(OPT_Operand op, int trueCost) {
    return LEA_SHIFT(op, trueCost, INFINITE);
  }

  /**
   * Can an IV be the scale in a LEA instruction?
   *
   * @param op operand to examine
   * @param trueCost the cost if this can be part of an LEA
   * @param falseCost the cost if this can't be part of an LEA
   * @return trueCost or falseCost
   */
  protected final int LEA_SHIFT(OPT_Operand op, int trueCost, int falseCost) {
    if (op.isIntConstant()) {
      int val = IV(op);
      if (val >= 0 && val <= 3) {
        return trueCost;
      }
    }
    return falseCost;
  }

  protected final byte LEA_SHIFT(OPT_Operand op) {
    switch (IV(op)) {
      case 0:
        return B_S;
      case 1:
        return W_S;
      case 2:
        return DW_S;
      case 3:
        return QW_S;
      default:
        throw new OPT_OptimizingCompilerException("bad val for LEA shift " + op);
    }
  }

  /**
   * Is the given instruction's constant operand a x87 floating point constant
   *
   * @param s the instruction to examine
   * @param trueCost the cost if this is a valid constant
   * @return trueCost or INFINITE depending on the given constant
   */
  protected final int is387_FPC(OPT_Instruction s, int trueCost) {
    OPT_Operand val = Binary.getVal2(s);
    if (val instanceof OPT_FloatConstantOperand) {
      OPT_FloatConstantOperand fc = (OPT_FloatConstantOperand) val;
      if (fc.value == 1.0f) {
        return trueCost;
      } else if (fc.value == 0.0f) {
        return trueCost;
      } else if (fc.value == (float) Math.PI) {
        return trueCost;
      } else if (fc.value == (float) LG2) {
        return trueCost;
      } else if (fc.value == (float) LN2) {
        return trueCost;
      } else if (fc.value == (float) L2E) {
        return trueCost;
      } else if (fc.value == (float) L2T) {
        return trueCost;
      }
    } else {
      OPT_DoubleConstantOperand dc = (OPT_DoubleConstantOperand) val;
      if (dc.value == 1.0) {
        return trueCost;
      } else if (dc.value == 0.0) {
        return trueCost;
      } else if (dc.value == Math.PI) {
        return trueCost;
      } else if (dc.value == LG2) {
        return trueCost;
      } else if (dc.value == LN2) {
        return trueCost;
      } else if (dc.value == L2E) {
        return trueCost;
      } else if (dc.value == L2T) {
        return trueCost;
      }
    }
    return INFINITE;
  }

  protected final OPT_Operator get387_FPC(OPT_Instruction s) {
    OPT_Operand val = Binary.getVal2(s);
    if (val instanceof OPT_FloatConstantOperand) {
      OPT_FloatConstantOperand fc = (OPT_FloatConstantOperand) val;
      if (fc.value == 1.0f) {
        return IA32_FLD1;
      } else if (fc.value == 0.0f) {
        return IA32_FLDZ;
      } else if (fc.value == (float) Math.PI) {
        return IA32_FLDPI;
      } else if (fc.value == (float) LG2) {
        return IA32_FLDLG2;
      } else if (fc.value == (float) LN2) {
        return IA32_FLDLN2;
      } else if (fc.value == (float) L2E) {
        return IA32_FLDL2E;
      } else if (fc.value == (float) L2T) {
        return IA32_FLDL2T;
      }
    } else {
      OPT_DoubleConstantOperand dc = (OPT_DoubleConstantOperand) val;
      if (dc.value == 1.0) {
        return IA32_FLD1;
      } else if (dc.value == 0.0) {
        return IA32_FLDZ;
      } else if (dc.value == Math.PI) {
        return IA32_FLDPI;
      } else if (dc.value == LG2) {
        return IA32_FLDLG2;
      } else if (dc.value == LN2) {
        return IA32_FLDLN2;
      } else if (dc.value == L2E) {
        return IA32_FLDL2E;
      } else if (dc.value == L2T) {
        return IA32_FLDL2T;
      }
    }
    throw new OPT_OptimizingCompilerException("OPT_BURS_Helpers", "unexpected 387 constant " + val);
  }

  protected final OPT_IA32ConditionOperand COND(OPT_ConditionOperand op) {
    return new OPT_IA32ConditionOperand(op);
  }

  // Get particular physical registers
  protected final OPT_Register getEAX() {
    return getIR().regpool.getPhysicalRegisterSet().getEAX();
  }

  protected final OPT_Register getECX() {
    return getIR().regpool.getPhysicalRegisterSet().getECX();
  }

  protected final OPT_Register getEDX() {
    return getIR().regpool.getPhysicalRegisterSet().getEDX();
  }

  protected final OPT_Register getEBX() {
    return getIR().regpool.getPhysicalRegisterSet().getEBX();
  }

  protected final OPT_Register getESP() {
    return getIR().regpool.getPhysicalRegisterSet().getESP();
  }

  protected final OPT_Register getEBP() {
    return getIR().regpool.getPhysicalRegisterSet().getEBP();
  }

  protected final OPT_Register getESI() {
    return getIR().regpool.getPhysicalRegisterSet().getESI();
  }

  protected final OPT_Register getEDI() {
    return getIR().regpool.getPhysicalRegisterSet().getEDI();
  }

  protected final OPT_Register getFPR(int n) {
    return getIR().regpool.getPhysicalRegisterSet().getFPR(n);
  }

  protected final OPT_Operand myFP0() {
    return new OPT_BURSManagedFPROperand(0);
  }

  protected final OPT_Operand myFP1() {
    return new OPT_BURSManagedFPROperand(1);
  }

  /**
   * Move op into a register operand if it isn't one already.
   */
  private OPT_Operand asReg(OPT_Instruction s, OPT_Operator movop, OPT_Operand op) {
    if (op.isRegister()) {
      return op;
    }
    OPT_RegisterOperand tmp = regpool.makeTemp(op);
    EMIT(CPOS(s, MIR_Move.create(movop, tmp, op)));
    return tmp.copy();
  }

  /**
   * Set the size field of the given memory operand and return it
   *
   * @param mo memory operand size to set
   * @param size the new size
   * @return mo
   */
  protected final OPT_MemoryOperand setSize(OPT_MemoryOperand mo, int size) {
    mo.size = (byte) size;
    return mo;
  }

  /**
   * Create a slot on the stack in memory for a conversion
   *
   * @param size for memory operand
   * @return memory operand of slot in stack
   */
  protected final OPT_Operand MO_CONV(byte size) {
    int offset = -burs.ir.stackManager.allocateSpaceForConversion();
    return new OPT_StackLocationOperand(true, offset, size);
  }

  /**
   * Create a 64bit slot on the stack in memory for a conversion and store the
   * given long
   */
  protected final void STORE_LONG_FOR_CONV(OPT_Operand op) {
    int offset = -burs.ir.stackManager.allocateSpaceForConversion();
    if (op instanceof OPT_RegisterOperand) {
      OPT_RegisterOperand hval = (OPT_RegisterOperand) op;
      OPT_RegisterOperand lval = new OPT_RegisterOperand(regpool
          .getSecondReg(hval.register), VM_TypeReference.Int);
      EMIT(MIR_Move.create(IA32_MOV, new OPT_StackLocationOperand(true, offset + 4, DW), hval));
      EMIT(MIR_Move.create(IA32_MOV, new OPT_StackLocationOperand(true, offset, DW), lval));
    } else {
      OPT_LongConstantOperand val = LC(op);
      EMIT(MIR_Move.create(IA32_MOV, new OPT_StackLocationOperand(true, offset + 4, DW), IC(val.upper32())));
      EMIT(MIR_Move.create(IA32_MOV, new OPT_StackLocationOperand(true, offset, DW), IC(val.lower32())));
    }
  }

  /**
   * Create memory operand to load 32 bits form a given jtoc offset
   *
   * @param offset location in JTOC
   * @return created memory operand
   */
  private OPT_MemoryOperand loadFromJTOC(Offset offset) {
    OPT_LocationOperand loc = new OPT_LocationOperand(offset);
    OPT_Operand guard = TG();
    return OPT_MemoryOperand.D(VM_Magic.getTocPointer().plus(offset), (byte) 4, loc, guard);
  }

  /*
   * IA32-specific emit rules that are complex enough that we didn't want to
   * write them in the LIR2MIR.rules file. However, all expansions in this file
   * are called during BURS and thus are constrained to generate nonbranching
   * code (ie they can't create new basic blocks and/or do branching).
   */

  /**
   * Emit code to get a caught exception object into a register
   *
   * @param s the instruction to expand
   */
  protected final void GET_EXCEPTION_OBJECT(OPT_Instruction s) {
    int offset = -burs.ir.stackManager.allocateSpaceForCaughtException();
    OPT_StackLocationOperand sl = new OPT_StackLocationOperand(true, offset, DW);
    EMIT(MIR_Move.mutate(s, IA32_MOV, Nullary.getResult(s), sl));
  }

  /**
   * Emit code to move a value in a register to the stack location where a
   * caught exception object is expected to be.
   *
   * @param s the instruction to expand
   */
  protected final void SET_EXCEPTION_OBJECT(OPT_Instruction s) {
    int offset = -burs.ir.stackManager.allocateSpaceForCaughtException();
    OPT_StackLocationOperand sl = new OPT_StackLocationOperand(true, offset, DW);
    OPT_RegisterOperand obj = (OPT_RegisterOperand) CacheOp.getRef(s);
    EMIT(MIR_Move.mutate(s, IA32_MOV, sl, obj));
  }

  /**
   * Expansion of INT_2LONG
   *
   * @param s the instruction to expand
   * @param result the result operand
   * @param value the second operand
   * @param signExtend should the value be sign or zero extended?
   */
  protected final void INT_2LONG(OPT_Instruction s, OPT_RegisterOperand result,
OPT_Operand value, boolean signExtend) {
    OPT_Register hr = result.register;
    OPT_Register lr = regpool.getSecondReg(hr);
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV, new OPT_RegisterOperand(lr, VM_TypeReference.Int), value)));
    if (signExtend) {
      EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
          new OPT_RegisterOperand(hr, VM_TypeReference.Int),
          new OPT_RegisterOperand(lr, VM_TypeReference.Int))));
      EMIT(MIR_BinaryAcc.mutate(s,IA32_SAR,
          new OPT_RegisterOperand(hr, VM_TypeReference.Int),
          IC(31)));
    } else {
      EMIT(MIR_Move.mutate(s, IA32_MOV,
          new OPT_RegisterOperand(hr, VM_TypeReference.Int),
          IC(0)));
    }
  }

  /**
   * Expansion of FLOAT_2INT and DOUBLE_2INT, using the FIST instruction. This
   * expansion does some boolean logic and conditional moves in order to avoid
   * changing the floating-point rounding mode or inserting branches. Other
   * expansions are possible, and may be better?
   *
   * @param s the instruction to expand
   * @param result the result operand
   * @param value the second operand
   */
  protected final void FPR_2INT(OPT_Instruction s, OPT_RegisterOperand result, OPT_Operand value) {
    OPT_MemoryOperand M;

    // Step 1: Get value to be converted into myFP0
    // and in 'strict' IEEE mode.
    if (value instanceof OPT_MemoryOperand) {
      // value is in memory, all we have to do is load it
      EMIT(CPOS(s, MIR_Move.create(IA32_FLD, myFP0(), value)));
    } else {
      // sigh. value is an FP register. Unfortunately,
      // SPECjbb requires some 'strict' FP semantics. Naturally, we don't
      // normally implement strict semantics, but we try to slide by in
      // order to pass the benchmark.
      // In order to pass SPECjbb, it turns out we need to enforce 'strict'
      // semantics before doing a particular f2int conversion. To do this
      // we must have a store/load sequence to cause IEEE rounding.
      if (value instanceof OPT_BURSManagedFPROperand) {
        if (VM.VerifyAssertions) {
          VM._assert(value.similar(myFP0()));
        }
        EMIT(CPOS(s, MIR_Move.create(IA32_FSTP, MO_CONV(DW), value)));
        EMIT(CPOS(s, MIR_Move.create(IA32_FLD, myFP0(), MO_CONV(DW))));
      } else {
        EMIT(CPOS(s, MIR_Move.create(IA32_FMOV, MO_CONV(DW), value)));
        EMIT(CPOS(s, MIR_Move.create(IA32_FLD, myFP0(), MO_CONV(DW))));
      }
    }

    // FP Stack: myFP0 = value
    EMIT(CPOS(s, MIR_Move.create(IA32_FIST, MO_CONV(DW), myFP0())));
    // MO_CONV now holds myFP0 converted to an integer (round-toward nearest)
    // FP Stack: myFP0 == value

    // isPositive == 1 iff 0.0 < value
    // isNegative == 1 iff 0.0 > value
    OPT_Register one = regpool.getInteger();
    OPT_Register isPositive = regpool.getInteger();
    OPT_Register isNegative = regpool.getInteger();
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV, new OPT_RegisterOperand(one, VM_TypeReference.Int), IC(1))));
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV, new OPT_RegisterOperand(isPositive, VM_TypeReference.Int), IC(0))));
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV, new OPT_RegisterOperand(isNegative, VM_TypeReference.Int), IC(0))));
    EMIT(CPOS(s, MIR_Nullary.create(IA32_FLDZ, myFP0())));
    // FP Stack: myFP0 = 0.0; myFP1 = value
    EMIT(CPOS(s, MIR_Compare.create(IA32_FCOMIP, myFP0(), myFP1())));
    // FP Stack: myFP0 = value
    EMIT(CPOS(s, MIR_CondMove.create(IA32_CMOV,
                             new OPT_RegisterOperand(isPositive, VM_TypeReference.Int),
                             new OPT_RegisterOperand(one, VM_TypeReference.Int),
                             OPT_IA32ConditionOperand.LLT())));
    EMIT(CPOS(s, MIR_CondMove.create(IA32_CMOV,
                             new OPT_RegisterOperand(isNegative, VM_TypeReference.Int),
                             new OPT_RegisterOperand(one, VM_TypeReference.Int),
                             OPT_IA32ConditionOperand.LGT())));

    EMIT(CPOS(s, MIR_Move.create(IA32_FILD, myFP0(), MO_CONV(DW))));
    // FP Stack: myFP0 = round(value), myFP1 = value

    // addee = 1 iff round(x) < x
    // subtractee = 1 iff round(x) > x
    OPT_Register addee = regpool.getInteger();
    OPT_Register subtractee = regpool.getInteger();
    EMIT(CPOS(s, MIR_Compare.create(IA32_FCOMIP, myFP0(), myFP1())));
    // FP Stack: myFP0 = value
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV, new OPT_RegisterOperand(addee, VM_TypeReference.Int), IC(0))));
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV, new OPT_RegisterOperand(subtractee, VM_TypeReference.Int), IC(0))));
    EMIT(CPOS(s, MIR_CondMove.create(IA32_CMOV,
                             new OPT_RegisterOperand(addee, VM_TypeReference.Int),
                             new OPT_RegisterOperand(one, VM_TypeReference.Int),
                             OPT_IA32ConditionOperand.LLT())));
    EMIT(CPOS(s, MIR_CondMove.create(IA32_CMOV,
                             new OPT_RegisterOperand(subtractee, VM_TypeReference.Int),
                             new OPT_RegisterOperand(one, VM_TypeReference.Int),
                             OPT_IA32ConditionOperand.LGT())));

    // Now a little tricky part.
    // We will add 1 iff isNegative and x > round(x)
    // We will subtract 1 iff isPositive and x < round(x)
    EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_AND,
                              new OPT_RegisterOperand(addee, VM_TypeReference.Int),
                              new OPT_RegisterOperand(isNegative, VM_TypeReference.Int))));
    EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_AND,
                              new OPT_RegisterOperand(subtractee, VM_TypeReference.Int),
                              new OPT_RegisterOperand(isPositive, VM_TypeReference.Int))));
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV, result.copy(), MO_CONV(DW))));
    EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_ADD, result.copy(), new OPT_RegisterOperand(addee, VM_TypeReference.Int))));
    EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SUB, result.copy(), new OPT_RegisterOperand(subtractee, VM_TypeReference.Int))));

    // Compare myFP0 with (double)Integer.MAX_VALUE
    M = OPT_MemoryOperand.D(VM_Magic.getTocPointer().plus(VM_Entrypoints.maxintField.getOffset()), QW, null, null);
    EMIT(CPOS(s, MIR_Move.create(IA32_FLD, myFP0(), M)));
    // FP Stack: myFP0 = (double)Integer.MAX_VALUE; myFP1 = value
    EMIT(CPOS(s, MIR_Compare.create(IA32_FCOMIP, myFP0(), myFP1())));
    // FP Stack: myFP0 = value
    // If MAX_VALUE < value, then result := MAX_INT
    OPT_Register maxInt = regpool.getInteger();
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV, new OPT_RegisterOperand(maxInt, VM_TypeReference.Int), IC(Integer.MAX_VALUE))));
    EMIT(CPOS(s, MIR_CondMove.create(IA32_CMOV,
                             result.copy(),
                             new OPT_RegisterOperand(maxInt, VM_TypeReference.Int),
                             OPT_IA32ConditionOperand.LLT())));

    // Compare myFP0 with (double)Integer.MIN_VALUE
    M = OPT_MemoryOperand.D(VM_Magic.getTocPointer().plus(VM_Entrypoints.minintField.getOffset()), QW, null, null);
    EMIT(CPOS(s, MIR_Move.create(IA32_FLD, myFP0(), M)));
    // FP Stack: myFP0 = (double)Integer.MIN_VALUE; myFP1 = value
    EMIT(CPOS(s, MIR_Compare.create(IA32_FCOMIP, myFP0(), myFP1())));
    // FP Stack: myFP0 = value
    // If MIN_VALUE > value, then result := MIN_INT
    OPT_Register minInt = regpool.getInteger();
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV, new OPT_RegisterOperand(minInt, VM_TypeReference.Int), IC(Integer.MIN_VALUE))));
    EMIT(CPOS(s, MIR_CondMove.create(IA32_CMOV,
                             result.copy(),
                             new OPT_RegisterOperand(minInt, VM_TypeReference.Int),
                             OPT_IA32ConditionOperand.LGT())));

    // Set condition flags: set PE iff myFP0 is a NaN
    EMIT(CPOS(s, MIR_Compare.create(IA32_FCOMIP, myFP0(), myFP0())));
    // FP Stack: back to original level (all BURS managed slots freed)
    // If FP0 was classified as a NaN, then result := 0
    OPT_Register zero = regpool.getInteger();
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV, new OPT_RegisterOperand(zero, VM_TypeReference.Int), IC(0))));
    EMIT(CPOS(s, MIR_CondMove.create(IA32_CMOV,
                             result.copy(),
                             new OPT_RegisterOperand(zero, VM_TypeReference.Int),
                             OPT_IA32ConditionOperand.PE())));
  }

  /**
   * Emit code to move 64 bits from FPRs to GPRs
   */
  protected final void FPR2GPR_64(OPT_Instruction s) {
    int offset = -burs.ir.stackManager.allocateSpaceForConversion();
    OPT_StackLocationOperand sl = new OPT_StackLocationOperand(true, offset, QW);
    OPT_StackLocationOperand sl1 = new OPT_StackLocationOperand(true, offset + 4, DW);
    OPT_StackLocationOperand sl2 = new OPT_StackLocationOperand(true, offset, DW);
    EMIT(CPOS(s, MIR_Move.create(IA32_FMOV, sl, Unary.getVal(s))));
    OPT_RegisterOperand i1 = Unary.getResult(s);
    OPT_RegisterOperand i2 = new OPT_RegisterOperand(regpool
        .getSecondReg(i1.register), VM_TypeReference.Int);
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV, i1, sl1)));
    EMIT(MIR_Move.mutate(s, IA32_MOV, i2, sl2));
  }

  /**
   * Emit code to move 64 bits from GPRs to FPRs
   */
  protected final void GPR2FPR_64(OPT_Instruction s) {
    int offset = -burs.ir.stackManager.allocateSpaceForConversion();
    OPT_StackLocationOperand sl = new OPT_StackLocationOperand(true, offset, QW);
    OPT_StackLocationOperand sl1 = new OPT_StackLocationOperand(true, offset + 4, DW);
    OPT_StackLocationOperand sl2 = new OPT_StackLocationOperand(true, offset, DW);
    OPT_Operand i1, i2;
    OPT_Operand val = Unary.getVal(s);
    if (val instanceof OPT_RegisterOperand) {
      OPT_RegisterOperand rval = (OPT_RegisterOperand) val;
      i1 = val;
      i2 = new OPT_RegisterOperand(regpool.getSecondReg(rval.register), VM_TypeReference.Int);
    } else {
      OPT_LongConstantOperand rhs = (OPT_LongConstantOperand) val;
      i1 = IC(rhs.upper32());
      i2 = IC(rhs.lower32());
    }
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV, sl1, i1)));
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV, sl2, i2)));
    EMIT(MIR_Move.mutate(s, IA32_FMOV, Unary.getResult(s), sl));
  }

  /**
   * Expansion of ROUND_TO_ZERO.
   *
   * @param s the instruction to expand
   */
  protected final void ROUND_TO_ZERO(OPT_Instruction s) {
    // load the JTOC into a register
    OPT_RegisterOperand PR = new OPT_RegisterOperand(regpool
        .getPhysicalRegisterSet().getPR(), VM_TypeReference.Int);
    OPT_Operand jtoc = OPT_MemoryOperand.BD(PR, VM_Entrypoints.jtocField
        .getOffset(), DW, null, null);
    OPT_RegisterOperand regOp = regpool.makeTempInt();
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV, regOp, jtoc)));

    // Store the FPU Control Word to a JTOC slot
    OPT_MemoryOperand M =
        OPT_MemoryOperand.BD(regOp.copyRO(), VM_Entrypoints.FPUControlWordField.getOffset(), W, null, null);
    EMIT(CPOS(s, MIR_UnaryNoRes.create(IA32_FNSTCW, M)));
    // Set the bits in the status word that control round to zero.
    // Note that we use a 32-bit and, even though we only care about the
    // low-order 16 bits
    EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_OR, M.copy(), IC(0x00000c00))));
    // Now store the result back into the FPU Control Word
    EMIT(MIR_Nullary.mutate(s, IA32_FLDCW, M.copy()));
  }

  /**
   * Expansion of INT_DIV and INT_REM
   *
   * @param s the instruction to expand
   * @param result the result operand
   * @param val1 the first operand
   * @param val2 the second operand
   * @param isDiv true for div, false for rem
   */
  protected final void INT_DIVIDES(OPT_Instruction s, OPT_RegisterOperand result, OPT_Operand val1, OPT_Operand val2,
                                   boolean isDiv) {
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV, new OPT_RegisterOperand(getEAX(), VM_TypeReference.Int), val1)));
    EMIT(CPOS(s, MIR_ConvertDW2QW.create(IA32_CDQ,
                                 new OPT_RegisterOperand(getEDX(), VM_TypeReference.Int),
                                 new OPT_RegisterOperand(getEAX(), VM_TypeReference.Int))));
    if (val2 instanceof OPT_IntConstantOperand) {
      OPT_RegisterOperand temp = regpool.makeTempInt();
      EMIT(CPOS(s, MIR_Move.create(IA32_MOV, temp, val2)));
      val2 = temp.copyRO();
    }
    EMIT(MIR_Divide.mutate(s,
                           IA32_IDIV,
                           new OPT_RegisterOperand(getEDX(), VM_TypeReference.Int),
                           new OPT_RegisterOperand(getEAX(), VM_TypeReference.Int),
                           val2,
                           GuardedBinary.getGuard(s)));
    if (isDiv) {
      EMIT(CPOS(s, MIR_Move.create(IA32_MOV, result.copyD2D(), new OPT_RegisterOperand(getEAX(), VM_TypeReference.Int))));
    } else {
      EMIT(CPOS(s, MIR_Move.create(IA32_MOV, result.copyD2D(), new OPT_RegisterOperand(getEDX(), VM_TypeReference.Int))));
    }
  }

  /**
   * Expansion of LONG_ADD
   *
   * @param s the instruction to expand
   * @param result the result operand
   * @param value1 the first operand
   * @param value2 the second operand
   */
  protected final void LONG_ADD(OPT_Instruction s, OPT_RegisterOperand result,
      OPT_Operand value1, OPT_Operand value2) {
    // The value of value1 should be identical to result, to avoid moves, and a
    // register in the case of addition with a constant
    if ((value2.similar(result)) || value1.isLongConstant()) {
      OPT_Operand temp = value1;
      value1 = value2;
      value2 = temp;
    } 
    OPT_Register lhsReg = result.register;
    OPT_Register lowlhsReg = regpool.getSecondReg(lhsReg);
    if (value1.isRegister() && value2.isRegister()) {
      OPT_Register rhsReg1 = ((OPT_RegisterOperand) value1).register;
      OPT_Register lowrhsReg1 = regpool.getSecondReg(rhsReg1);
      OPT_Register rhsReg2 = ((OPT_RegisterOperand) value2).register;
      OPT_Register lowrhsReg2 = regpool.getSecondReg(rhsReg2);
      // Do we need to move prior to the add - result = value1
      if (!value1.similar(result)) {
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
            new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
            new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));
      }
      // Perform add - result += value2
      EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_ADD,
          new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
          new OPT_RegisterOperand(lowrhsReg2, VM_TypeReference.Int))));
      EMIT(CPOS(s, MIR_BinaryAcc.mutate(s, IA32_ADC,
          new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
          new OPT_RegisterOperand(rhsReg2, VM_TypeReference.Int))));
    } else if (value1.isRegister()){
      OPT_Register rhsReg1 = ((OPT_RegisterOperand) value1).register;
      OPT_Register lowrhsReg1 = regpool.getSecondReg(rhsReg1);
      OPT_LongConstantOperand rhs2 = (OPT_LongConstantOperand) value2;
      int low = rhs2.lower32();
      int high = rhs2.upper32();
      // Do we need to move prior to the add - result = value1
      if (!value1.similar(result)) {
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
            new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
            new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));
      }
      // Perform add - result += value2
      if (low == 0) {
        EMIT(CPOS(s, MIR_BinaryAcc.mutate(s, IA32_ADD,
            new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
            IC(high))));
      } else {
        EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_ADD,
            new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
            IC(low))));
        EMIT(CPOS(s, MIR_BinaryAcc.mutate(s, IA32_ADC,
            new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
            IC(high))));
      }
    } else {
      throw new OPT_OptimizingCompilerException("OPT_BURS_Helpers",
          "unexpected parameters: " + result + "=" + value1 + "+" + value2);
    }
  }

  /**
   * Expansion of LONG_SUB
   *
   * @param s the instruction to expand
   * @param result the result operand
   * @param value1 the first operand
   * @param value2 the second operand
   */
  protected final void LONG_SUB(OPT_Instruction s, OPT_Operand result,
      OPT_Operand val1, OPT_Operand val2) {

    if (result.similar(val1)) {
      // Straight forward case where instruction is already in accumulate form
      if (result.isRegister()) {
        OPT_Register lhsReg = result.asRegister().register;
        OPT_Register lowlhsReg = regpool.getSecondReg(lhsReg);
        if (val2.isRegister()) {
          OPT_Register rhsReg2 = val2.asRegister().register;
          OPT_Register lowrhsReg2 = regpool.getSecondReg(rhsReg2);
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SUB,
              new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(lowrhsReg2, VM_TypeReference.Int))));
          EMIT(CPOS(s, MIR_BinaryAcc.mutate(s, IA32_SBB,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(rhsReg2, VM_TypeReference.Int))));
        } else if (val2.isLongConstant()) {
          OPT_LongConstantOperand rhs2 = val2.asLongConstant();
          int low = rhs2.lower32();
          int high = rhs2.upper32();
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SUB,
              new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
              IC(low))));
          EMIT(CPOS(s, MIR_BinaryAcc.mutate(s, IA32_SBB,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              IC(high))));        
        } else {
          throw new OPT_OptimizingCompilerException("OPT_BURS_Helpers",
              "unexpected parameters: " + result + "=" + val1 + "-" + val2);
        }
      } else {
        throw new OPT_OptimizingCompilerException("OPT_BURS_Helpers",
            "unexpected parameters: " + result + "=" + val1 + "-" + val2);
      }
    }      
    else if (!result.similar(val2)) {
      // Move first operand to result and perform operator on result, if
      // possible redundant moves should be remove by register allocator
      if (result.isRegister()) {
        OPT_Register lhsReg = result.asRegister().register;
        OPT_Register lowlhsReg = regpool.getSecondReg(lhsReg);
        // Move val1 into result
        if (val1.isRegister()) {
          OPT_Register rhsReg1 = val1.asRegister().register;
          OPT_Register lowrhsReg1 = regpool.getSecondReg(rhsReg1);
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));
        } else if (val1.isLongConstant()) {
          OPT_LongConstantOperand rhs1 = val1.asLongConstant();
          int low = rhs1.lower32();
          int high = rhs1.upper32();
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
              IC(low))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              IC(high))));
        } else {
          throw new OPT_OptimizingCompilerException("OPT_BURS_Helpers",
              "unexpected parameters: " + result + "=" + val1 + "-" + val2);
        }
        // Perform subtract
        if (val2.isRegister()) {
          OPT_Register rhsReg2 = val2.asRegister().register;
          OPT_Register lowrhsReg2 = regpool.getSecondReg(rhsReg2);
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SUB,
              new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(lowrhsReg2, VM_TypeReference.Int))));
          EMIT(CPOS(s, MIR_BinaryAcc.mutate(s, IA32_SBB,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(rhsReg2, VM_TypeReference.Int))));
        } else if (val2.isLongConstant()) {
          OPT_LongConstantOperand rhs2 = val2.asLongConstant();
          int low = rhs2.lower32();
          int high = rhs2.upper32();
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SUB,
              new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
              IC(low))));
          EMIT(CPOS(s, MIR_BinaryAcc.mutate(s, IA32_SBB,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              IC(high))));        
        } else {
          throw new OPT_OptimizingCompilerException("OPT_BURS_Helpers",
              "unexpected parameters: " + result + "=" + val1 + "-" + val2);
        }        
      } else {
        throw new OPT_OptimizingCompilerException("OPT_BURS_Helpers",
            "unexpected parameters: " + result + "=" + val1 + "-" + val2);
      }
    }
    else {
      // Potential to clobber second operand during move to result. Use a
      // temporary register to perform the operation and rely on register
      // allocator to remove redundant moves
      OPT_RegisterOperand temp1 = regpool.makeTempInt();
      OPT_RegisterOperand temp2 = regpool.makeTempInt();
      // Move val1 into temp
      if (val1.isRegister()) {
        OPT_Register rhsReg1 = val1.asRegister().register;
        OPT_Register lowrhsReg1 = regpool.getSecondReg(rhsReg1);
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            temp1,
            new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            temp2,
            new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));
      } else if (val1.isLongConstant()) {
        OPT_LongConstantOperand rhs1 = val1.asLongConstant();
        int low = rhs1.lower32();
        int high = rhs1.upper32();
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            temp1,
            IC(low))));
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            temp2,
            IC(high))));
      } else {
        throw new OPT_OptimizingCompilerException("OPT_BURS_Helpers",
            "unexpected parameters: " + result + "=" + val1 + "-" + val2);
      }
      // Perform subtract
      if (val2.isRegister()) {
        OPT_Register rhsReg2 = val2.asRegister().register;
        OPT_Register lowrhsReg2 = regpool.getSecondReg(rhsReg2);
        EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SUB,
            temp1.copyRO(),
            new OPT_RegisterOperand(lowrhsReg2, VM_TypeReference.Int))));
        EMIT(CPOS(s, MIR_BinaryAcc.mutate(s, IA32_SBB,
            temp2.copyRO(),
            new OPT_RegisterOperand(rhsReg2, VM_TypeReference.Int))));
      } else if (val2.isLongConstant()) {
        OPT_LongConstantOperand rhs2 = val2.asLongConstant();
        int low = rhs2.lower32();
        int high = rhs2.upper32();
        EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SUB,
            temp1.copyRO(),
            IC(low))));
        EMIT(CPOS(s, MIR_BinaryAcc.mutate(s, IA32_SBB,
            temp2.copyRO(),
            IC(high))));        
      } else {
        throw new OPT_OptimizingCompilerException("OPT_BURS_Helpers",
            "unexpected parameters: " + result + "=" + val1 + "-" + val2);
      }
      // Move result back
      if (result.isRegister()) {
        OPT_Register lhsReg = result.asRegister().register;
        OPT_Register lowlhsReg = regpool.getSecondReg(lhsReg);
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
            temp1.copyRO())));
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
            temp2.copyRO())));
      } else {
        throw new OPT_OptimizingCompilerException("OPT_BURS_Helpers",
            "unexpected parameters: " + result + "=" + val1 + "-" + val2);
      }
    }   
  }

  /**
   * Expansion of LONG_MUL
   *
   * @param s the instruction to expand
   * @param result the result operand
   * @param value1 the first operand
   * @param value2 the second operand
   */
  protected final void LONG_MUL(OPT_Instruction s, OPT_RegisterOperand result,
      OPT_Operand value1, OPT_Operand value2) {
    if (value2.isRegister()) {
      // Leave for complex LIR2MIR expansion as the most efficient form requires
      // a branch
      if (VM.VerifyAssertions) VM._assert(Binary.getResult(s).similar(result) &&
          Binary.getVal1(s).similar(value1) && Binary.getVal2(s).similar(value2));
      EMIT(s);
    }
    else {
      // The value of value1 should be identical to result, to avoid moves, and a
      // register in the case of multiplication with a constant
      if ((value2.similar(result)) || value1.isLongConstant()) {
        OPT_Operand temp = value1;
        value1 = value2;
        value2 = temp;
      }
      if (VM.VerifyAssertions) VM._assert(value1.isRegister() && value2.isLongConstant());
      
      // In general, (a,b) * (c,d) = (l(a imul d)+l(b imul c)+u(b mul d), l(b mul d))
      
      OPT_Register lhsReg = result.register;
      OPT_Register lowlhsReg = regpool.getSecondReg(lhsReg);
      
      OPT_LongConstantOperand rhs2 = (OPT_LongConstantOperand) value2;      
      OPT_Register rhsReg1 = value1.asRegister().register; // a
      OPT_Register lowrhsReg1 = regpool.getSecondReg(rhsReg1); // b
      int high2 = rhs2.upper32(); // c
      int low2 = rhs2.lower32(); // d

      // We only have to handle those cases that OPT_Simplifier wouldn't get.
      // OPT_Simplifier catches
      // high low
      // 0 0 (0L)
      // 0 1 (1L)
      // -1 -1 (-1L)
      // So, the possible cases we need to handle here:
      // -1 0
      // -1 1
      // -1 *
      // 0 -1
      // 0 *
      // 1 -1
      // 1 0
      // 1 1
      // 1 *
      // * -1
      // * 0
      // * 1
      // * *
      // (where * is something other than -1,0,1)
      if (high2 == -1) {
        if (low2 == 0) {
          // -1, 0
          // CLAIM: (a,b) * (-1,0) = (-b,0)
          if (VM.VerifyAssertions) VM._assert(lhsReg != lowrhsReg1);
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));            
          EMIT(CPOS(s, MIR_UnaryAcc.create(IA32_NEG,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
              IC(0))));
        } else if (low2 == 1) {
          // -1, 1
          // CLAIM: (a,b) * (-1,1) = (a-b,b)
          if (lowlhsReg != lowrhsReg1) {
            EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
                new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
                new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));            
          }          
          if (lhsReg != rhsReg1) {
            EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
                new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
                new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));            
          }
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SUB,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int))));
        } else {
          // -1, *
          // CLAIM: (a,b) * (-1, d) = (l(a imul d)-b+u(b mul d), l(b mul d))
          if (lhsReg != rhsReg1) {
            EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
                new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
                new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));            
          }
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_IMUL2,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              IC(low2))));
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SUB,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(getEAX(), VM_TypeReference.Int),
              IC(low2))));
          EMIT(CPOS(s, MIR_Multiply.create(IA32_MUL,
              new OPT_RegisterOperand(getEDX(), VM_TypeReference.Int),
              new OPT_RegisterOperand(getEAX(), VM_TypeReference.Int),
              new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(getEAX(), VM_TypeReference.Int))));
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_ADD,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(getEDX(), VM_TypeReference.Int))));
        }
      } else if (high2 == 0) {
        if (low2 == -1) {
          // 0, -1
          // CLAIM: (a,b) * (0,-1) = (b-(a+(b!=0?1:0)),-b)
          // avoid clobbering a and b by using tmp
          OPT_Register tmp = regpool.getInteger();
          if (lowlhsReg != lowrhsReg1) {
            EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
                new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
                new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));            
          }
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(tmp, VM_TypeReference.Int),
              new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));            
          EMIT(CPOS(s, MIR_UnaryAcc.create(IA32_NEG,
              new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int))));
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SBB,
              new OPT_RegisterOperand(tmp, VM_TypeReference.Int),
              new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(tmp, VM_TypeReference.Int))));            
        } else {
          // 0, *
          // CLAIM: (a,b) * (0,d) = (l(a imul d)+u(b mul d), l(b mul d))
          if (lhsReg != rhsReg1) {
            EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
                new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
                new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));            
          }
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_IMUL2,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              IC(low2))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(getEAX(), VM_TypeReference.Int),
              IC(low2))));
          EMIT(CPOS(s, MIR_Multiply.create(IA32_MUL,
              new OPT_RegisterOperand(getEDX(), VM_TypeReference.Int),
              new OPT_RegisterOperand(getEAX(), VM_TypeReference.Int),
              new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(getEAX(), VM_TypeReference.Int))));
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_ADD,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(getEDX(), VM_TypeReference.Int))));
        }
      } else if (high2 == 1) {
        if (low2 == -1) {
          // 1, -1          
          // CLAIM: (a,b) * (1,-1) = (2b-(a+(b!=0?1:0)),-b)
          // avoid clobbering a and b by using tmp
          OPT_Register tmp = regpool.getInteger();
          if (lowlhsReg != lowrhsReg1) {
            EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
                new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
                new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));            
          }
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(tmp, VM_TypeReference.Int),
              new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));            
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_ADD,
              new OPT_RegisterOperand(tmp, VM_TypeReference.Int),
              new OPT_RegisterOperand(tmp, VM_TypeReference.Int))));
          EMIT(CPOS(s, MIR_UnaryAcc.create(IA32_NEG,
              new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int))));
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SBB,
              new OPT_RegisterOperand(tmp, VM_TypeReference.Int),
              new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(tmp, VM_TypeReference.Int))));            
        } else if (low2 == 0) {
          // 1, 0
          // CLAIM: (x,y) * (1,0) = (y,0)
          // NB we should have simplified this LONG_MUL to a LONG_SHIFT
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
              IC(0))));
        } else if (low2 == 1) {
          // 1, 1
          // CLAIM: (x,y) * (1,1) = (x+y,y)
          // NB we should have simplified this LONG_MUL to a LONG_SHIFT and LONG_ADDs
          if (lowlhsReg != lowrhsReg1) {
            EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
                new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
                new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));            
          }
          if (lhsReg != rhsReg1) {
            EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
                new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
                new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));            
          }
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_ADD,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int))));
        } else {
          // 1, *
          // CLAIM: (a,b) * (1,d) = (l(a imul d)+b+u(b mul d), l(b mul d))
          if (lhsReg != rhsReg1) {
            EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
                new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
                new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));            
          }
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_IMUL2,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              IC(low2))));
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_ADD,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(getEAX(), VM_TypeReference.Int),
              IC(low2))));
          EMIT(CPOS(s, MIR_Multiply.create(IA32_MUL,
              new OPT_RegisterOperand(getEDX(), VM_TypeReference.Int),
              new OPT_RegisterOperand(getEAX(), VM_TypeReference.Int),
              new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(getEAX(), VM_TypeReference.Int))));
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_ADD,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(getEDX(), VM_TypeReference.Int))));
        }
      } else {
        if (low2 == -1) {
          // *, -1
          // CLAIM: (a,b) * (c, -1) = ((b+1)*c - (a + b==0?1:0), -b)
          // avoid clobbering a and b by using tmp
          OPT_Register tmp = regpool.getInteger();
          if (lowlhsReg != lowrhsReg1) {
            EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
                new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
                new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));            
          }
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(tmp, VM_TypeReference.Int),
              new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));            
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_ADD,
              new OPT_RegisterOperand(tmp, VM_TypeReference.Int),
              IC(1))));
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_IMUL2,
              new OPT_RegisterOperand(tmp, VM_TypeReference.Int),
              IC(high2))));
          EMIT(CPOS(s, MIR_UnaryAcc.create(IA32_NEG,
              new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int))));
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SBB,
              new OPT_RegisterOperand(tmp, VM_TypeReference.Int),
              new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(tmp, VM_TypeReference.Int))));            
        } else if (low2 == 0) {
          // *, 0
          // CLAIM: (a,b) * (c,0) = (l(b imul c),0)
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_IMUL2,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              IC(high2))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
              IC(0))));
        } else if (low2 == 1) {
          // *, 1
          // CLAIM: (x,y) * (z,1) = (l(y imul z)+x,y)
          if (lowlhsReg != lowrhsReg1) {
            EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
                new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
                new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));            
          }
          if (lhsReg != rhsReg1) {
            EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
                new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
                new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));            
          }
          OPT_Register tmp = regpool.getInteger();
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(tmp, VM_TypeReference.Int),
              new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_IMUL2,
              new OPT_RegisterOperand(tmp, VM_TypeReference.Int),
              IC(high2))));
          EMIT(CPOS(s, MIR_Move.create(IA32_ADD,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(tmp, VM_TypeReference.Int))));
        } else {
          // *, * can't do anything interesting and both operands have non-zero words
          // (a,b) * (c,d) = (l(a imul d)+l(b imul c)+u(b mul d), l(b mul d))
          if (lhsReg != rhsReg1) {
            EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
                new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
                new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));            
          }
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_IMUL2,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              IC(low2))));
          OPT_Register tmp = regpool.getInteger();
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(tmp, VM_TypeReference.Int),
              new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_IMUL2,
              new OPT_RegisterOperand(tmp, VM_TypeReference.Int),
              IC(high2))));
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_ADD,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(tmp, VM_TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(getEAX(), VM_TypeReference.Int),
              IC(low2))));
          EMIT(CPOS(s, MIR_Multiply.create(IA32_MUL,
              new OPT_RegisterOperand(getEDX(), VM_TypeReference.Int),
              new OPT_RegisterOperand(getEAX(), VM_TypeReference.Int),
              new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(getEAX(), VM_TypeReference.Int))));
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_ADD,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(getEDX(), VM_TypeReference.Int))));
        }
      }
    }
  }

  /**
   * Expansion of LONG_NEG
   *
   * @param s the instruction to expand
   * @param result the result operand
   * @param value the first operand
   */
  protected final void LONG_NEG(OPT_Instruction s, OPT_RegisterOperand result, OPT_Operand value) {
    OPT_Register lhsReg = result.register;
    OPT_Register lowlhsReg = regpool.getSecondReg(lhsReg);
    // Move value into result if its not already
    if (!result.similar(value)){
      if (value.isRegister()) {
        OPT_Register rhsReg = value.asRegister().register;
        OPT_Register lowrhsReg = regpool.getSecondReg(rhsReg);
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
            new OPT_RegisterOperand(lowrhsReg, VM_TypeReference.Int))));
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
            new OPT_RegisterOperand(rhsReg, VM_TypeReference.Int))));      
      } else {
        throw new OPT_OptimizingCompilerException("OPT_BURS_Helpers",
            "unexpected parameters: " + result + "= -" + value);      
      }
    }
    // Perform negation
    EMIT(CPOS(s, MIR_UnaryAcc.create(IA32_NOT,
        new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int))));
    EMIT(CPOS(s, MIR_UnaryAcc.create(IA32_NEG,
        new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int))));
    EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SBB,
        new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
        IC(-1))));
  }

  /**
   * Expansion of LONG_NOT
   * 
   * @param s the instruction to expand
   * @param result the result operand
   * @param value the first operand
   */
  protected final void LONG_NOT(OPT_Instruction s, OPT_RegisterOperand result, OPT_Operand value) {
    OPT_Register lhsReg = result.register;
    OPT_Register lowlhsReg = regpool.getSecondReg(lhsReg);
    // Move value into result if its not already
    if (!result.similar(value)){
      if (value.isRegister()) {
        OPT_Register rhsReg = value.asRegister().register;
        OPT_Register lowrhsReg = regpool.getSecondReg(rhsReg);
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
            new OPT_RegisterOperand(lowrhsReg, VM_TypeReference.Int))));
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
            new OPT_RegisterOperand(rhsReg, VM_TypeReference.Int))));      
      } else {
        throw new OPT_OptimizingCompilerException("OPT_BURS_Helpers",
            "unexpected parameters: " + result + "= ~" + value);      
      }
    }
    // Perform not
    EMIT(CPOS(s, MIR_UnaryAcc.create(IA32_NOT,
        new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int))));
    EMIT(CPOS(s, MIR_UnaryAcc.mutate(s, IA32_NOT,
        new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int))));
  }

  /**
   * Expansion of LONG_AND
   *
   * @param s the instruction to expand
   * @param result the result operand
   * @param value1 the first operand
   * @param value2 the second operand
   */
  protected final void LONG_AND(OPT_Instruction s, OPT_RegisterOperand result,
      OPT_Operand value1, OPT_Operand value2) {
    // The value of value1 should be identical to result, to avoid moves, and a
    // register in the case of addition with a constant
    if ((value2.similar(result)) || value1.isLongConstant()) {
      OPT_Operand temp = value1;
      value1 = value2;
      value2 = temp;
    } 
    OPT_Register lhsReg = result.register;
    OPT_Register lowlhsReg = regpool.getSecondReg(lhsReg);
    if (value1.isRegister() && value2.isRegister()) {
      OPT_Register rhsReg1 = ((OPT_RegisterOperand) value1).register;
      OPT_Register lowrhsReg1 = regpool.getSecondReg(rhsReg1);
      OPT_Register rhsReg2 = ((OPT_RegisterOperand) value2).register;
      OPT_Register lowrhsReg2 = regpool.getSecondReg(rhsReg2);
      // Do we need to move prior to the and - result = value1
      if (!value1.similar(result)) {
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
            new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
            new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));
      }
      // Perform and - result &= value2
      EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_AND,
          new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
          new OPT_RegisterOperand(lowrhsReg2, VM_TypeReference.Int))));
      EMIT(CPOS(s, MIR_BinaryAcc.mutate(s, IA32_AND,
          new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
          new OPT_RegisterOperand(rhsReg2, VM_TypeReference.Int))));
    } else if (value1.isRegister()){
      OPT_Register rhsReg1 = ((OPT_RegisterOperand) value1).register;
      OPT_Register lowrhsReg1 = regpool.getSecondReg(rhsReg1);
      OPT_LongConstantOperand rhs2 = (OPT_LongConstantOperand) value2;
      int low = rhs2.lower32();
      int high = rhs2.upper32();
      // Do we need to move prior to the and - result = value1
      if (!value1.similar(result)) {
        if (low != 0) {
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));
        }
        if (high != 0) {
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));
        }
      }
      // Perform and - result &= value2
      if (low == 0) {
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
            IC(0))));
      } else if (low == -1) {
        // nop
      } else {        
        EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_AND,
            new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
            IC(low))));
      }
      if (high == 0) {
        EMIT(CPOS(s, MIR_Move.mutate(s, IA32_MOV,
            new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
            IC(0))));
      } else if (high == -1) {
        // nop
      } else { 
        EMIT(CPOS(s, MIR_BinaryAcc.mutate(s, IA32_AND,
            new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
            IC(high))));
      }
    } else {
      throw new OPT_OptimizingCompilerException("OPT_BURS_Helpers",
          "unexpected parameters: " + result + "=" + value1 + "+" + value2);
    }
  }
  /**
   * Expansion of LONG_OR
   *
   * @param s the instruction to expand
   * @param result the result operand
   * @param value1 the first operand
   * @param value2 the second operand
   */
  protected final void LONG_OR(OPT_Instruction s, OPT_RegisterOperand result,
      OPT_Operand value1, OPT_Operand value2) {
    // The value of value1 should be identical to result, to avoid moves, and a
    // register in the case of addition with a constant
    if ((value2.similar(result)) || value1.isLongConstant()) {
      OPT_Operand temp = value1;
      value1 = value2;
      value2 = temp;
    } 
    OPT_Register lhsReg = result.register;
    OPT_Register lowlhsReg = regpool.getSecondReg(lhsReg);
    if (value1.isRegister() && value2.isRegister()) {
      OPT_Register rhsReg1 = ((OPT_RegisterOperand) value1).register;
      OPT_Register lowrhsReg1 = regpool.getSecondReg(rhsReg1);
      OPT_Register rhsReg2 = ((OPT_RegisterOperand) value2).register;
      OPT_Register lowrhsReg2 = regpool.getSecondReg(rhsReg2);
      // Do we need to move prior to the and - result = value1
      if (!value1.similar(result)) {
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
            new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
            new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));
      }
      // Perform or - result |= value2
      EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_OR,
          new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
          new OPT_RegisterOperand(lowrhsReg2, VM_TypeReference.Int))));
      EMIT(CPOS(s, MIR_BinaryAcc.mutate(s, IA32_OR,
          new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
          new OPT_RegisterOperand(rhsReg2, VM_TypeReference.Int))));
    } else if (value1.isRegister()){
      OPT_Register rhsReg1 = ((OPT_RegisterOperand) value1).register;
      OPT_Register lowrhsReg1 = regpool.getSecondReg(rhsReg1);
      OPT_LongConstantOperand rhs2 = (OPT_LongConstantOperand) value2;
      int low = rhs2.lower32();
      int high = rhs2.upper32();
      // Do we need to move prior to the and - result = value1
      if (!value1.similar(result)) {
        if (low != -1) {
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));
        }
        if (high != -1) {
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));
        }
      }
      // Perform or - result |= value2
      if (low == 0) {
        // nop
      } else if (low == -1) {
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
            IC(-1))));
      } else {     
        EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_OR,
            new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
            IC(low))));
      }
      if (high == 0) {
        // nop
      } else if (high == -1) {
        EMIT(CPOS(s, MIR_Move.mutate(s, IA32_MOV,
            new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
            IC(-1))));
      } else { 
        EMIT(CPOS(s, MIR_BinaryAcc.mutate(s, IA32_OR,
            new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
            IC(high))));
      }
    } else {
      throw new OPT_OptimizingCompilerException("OPT_BURS_Helpers",
          "unexpected parameters: " + result + "=" + value1 + "+" + value2);
    }
  }
  /**
   * Expansion of LONG_XOR
   *
   * @param s the instruction to expand
   * @param result the result operand
   * @param value1 the first operand
   * @param value2 the second operand
   */
  protected final void LONG_XOR(OPT_Instruction s, OPT_RegisterOperand result,
      OPT_Operand value1, OPT_Operand value2) {
    // The value of value1 should be identical to result, to avoid moves, and a
    // register in the case of addition with a constant
    if ((value2.similar(result)) || value1.isLongConstant()) {
      OPT_Operand temp = value1;
      value1 = value2;
      value2 = temp;
    } 
    OPT_Register lhsReg = result.register;
    OPT_Register lowlhsReg = regpool.getSecondReg(lhsReg);
    if (value1.isRegister() && value2.isRegister()) {
      OPT_Register rhsReg1 = ((OPT_RegisterOperand) value1).register;
      OPT_Register lowrhsReg1 = regpool.getSecondReg(rhsReg1);
      OPT_Register rhsReg2 = ((OPT_RegisterOperand) value2).register;
      OPT_Register lowrhsReg2 = regpool.getSecondReg(rhsReg2);
      // Do we need to move prior to the and - result = value1
      if (!value1.similar(result)) {
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
            new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
            new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));
      }
      // Perform or - result |= value2
      EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_XOR,
          new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
          new OPT_RegisterOperand(lowrhsReg2, VM_TypeReference.Int))));
      EMIT(CPOS(s, MIR_BinaryAcc.mutate(s, IA32_XOR,
          new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
          new OPT_RegisterOperand(rhsReg2, VM_TypeReference.Int))));
    } else if (value1.isRegister()){
      OPT_Register rhsReg1 = ((OPT_RegisterOperand) value1).register;
      OPT_Register lowrhsReg1 = regpool.getSecondReg(rhsReg1);
      OPT_LongConstantOperand rhs2 = (OPT_LongConstantOperand) value2;
      int low = rhs2.lower32();
      int high = rhs2.upper32();
      // Do we need to move prior to the and - result = value1
      if (!value1.similar(result)) {
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
            new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
            new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));
      }
      // Perform xor - result ^= value2
      if (low == 0) {
        // nop
      } else if (low == -1) {
        EMIT(CPOS(s, MIR_UnaryAcc.create(IA32_NOT,
            new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int))));
      } else {     
        EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_XOR,
            new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
            IC(low))));
      }
      if (high == 0) {
        // nop
      } else if (high == -1) {
        EMIT(CPOS(s, MIR_UnaryAcc.create(IA32_NOT,
            new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int))));
      } else { 
        EMIT(CPOS(s, MIR_BinaryAcc.mutate(s, IA32_XOR,
            new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
            IC(high))));
      }
    } else {
      throw new OPT_OptimizingCompilerException("OPT_BURS_Helpers",
          "unexpected parameters: " + result + "=" + value1 + "+" + value2);
    }
  }

  /**
   * Expansion of LONG_SHL
   * @param s the instruction to expand
   * @param result the result operand
   * @param val1 the shifted operand
   * @param val2 the shift amount operand
   * @param maskWith3f should the shift operand by masked with 0x3f? This is
   *          default behaviour on Intel but it differs from how we combine
   *          shift operands in HIR
   */
  protected final void LONG_SHL(OPT_Instruction s, OPT_Operand result,
      OPT_Operand val1, OPT_Operand val2, boolean maskWith3f) {
    if (!val2.isIntConstant()) {
      // the most efficient form of expanding a shift by a variable amount
      // requires a branch so leave for complex operators
      // NB if !maskWith3f - we assume that a mask with 0x3F was required as
      // no optimizations currently exploits shift by registers of > 63
      // returning 0
      Binary.mutate(s, LONG_SHL, result.asRegister(), val1, val2);
      EMIT(s);
    } else if (result.isRegister()) {
      int shift = val2.asIntConstant().value;
      OPT_Register lhsReg = result.asRegister().register;
      OPT_Register lowlhsReg = burs.ir.regpool.getSecondReg(lhsReg);
      OPT_Register rhsReg1 = val1.asRegister().register;
      OPT_Register lowrhsReg1 = burs.ir.regpool.getSecondReg(rhsReg1);
      
      if (shift == 0) {
        // operation is a nop.
        if (!result.similar(val1)) {
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));
        }
      } else if (shift == 1) {
        if (!result.similar(val1)) {
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));
        }
        EMIT(CPOS(s,
            MIR_BinaryAcc.create(IA32_ADD,
                new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
                new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int))));
        EMIT(MIR_BinaryAcc.mutate(s,
            IA32_ADC,
            new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
            new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int)));
      } else if (shift == 2) {
        // bits to shift in: tmp = lowrhsReg >> 30
        OPT_Register tmp = regpool.getInteger();
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new OPT_RegisterOperand(tmp, VM_TypeReference.Int),
            new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));
        EMIT(CPOS(s,
            MIR_BinaryAcc.create(IA32_SHR,
                new OPT_RegisterOperand(tmp, VM_TypeReference.Int),
                IC(30))));
        // compute top half: lhsReg = (rhsReg1 << 2) + tmp
        EMIT(CPOS(s,
            MIR_Lea.create(IA32_LEA,
                new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
                OPT_MemoryOperand.BIS(new OPT_RegisterOperand(tmp, VM_TypeReference.Int),
                    new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int),
                    (byte)2, (byte)4, null, null))));
        // compute bottom half: lowlhsReg = lowlhsReg << 2  
        EMIT(CPOS(s,
            MIR_Lea.create(IA32_LEA,
                new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
                new OPT_MemoryOperand(null, // base
                    new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int), //index
                    (byte)2, // scale
                    Offset.zero(), // displacement
                    (byte)4, // size
                    null, // location
                    null // guard
                    ))));
      } else if (shift == 3) {
        // bits to shift in: tmp = lowrhsReg >>> 29
        OPT_Register tmp = regpool.getInteger();
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new OPT_RegisterOperand(tmp, VM_TypeReference.Int),
            new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));
        EMIT(CPOS(s,
            MIR_BinaryAcc.create(IA32_SHR,
                new OPT_RegisterOperand(tmp, VM_TypeReference.Int),
                IC(29))));
        // compute top half: lhsReg = (rhsReg1 << 3) + tmp
        EMIT(CPOS(s,
            MIR_Lea.create(IA32_LEA,
                new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
                OPT_MemoryOperand.BIS(new OPT_RegisterOperand(tmp, VM_TypeReference.Int),
                    new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int),
                    (byte)3, (byte)4, null, null))));
        // compute bottom half: lowlhsReg = lowlhsReg << 3  
        EMIT(CPOS(s,
            MIR_Lea.create(IA32_LEA,
                new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
                new OPT_MemoryOperand(null, // base
                    new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int), //index
                    (byte)3, // scale
                    Offset.zero(), // displacement
                    (byte)4, // size
                    null, // location
                    null // guard
                    ))));
      } else if (shift < 32) {
        if (!result.similar(val1)) {
          EMIT(CPOS(s,
              MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));
        }
        // bits to shift in: tmp = lowrhsReg >>> (32 - shift)
        OPT_Register tmp = regpool.getInteger();
        EMIT(CPOS(s,
            MIR_Move.create(IA32_MOV,
                new OPT_RegisterOperand(tmp, VM_TypeReference.Int),
                new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));
        EMIT(CPOS(s,
            MIR_BinaryAcc.create(IA32_SHR,
                new OPT_RegisterOperand(tmp, VM_TypeReference.Int),
                IC(32 - shift))));
        // compute top half: lhsReg = (lhsReg1 << shift) | tmp
        EMIT(CPOS(s,
            MIR_BinaryAcc.create(IA32_SHL,
                new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
                IC(shift))));
        EMIT(CPOS(s,
            MIR_BinaryAcc.create(IA32_OR,
                new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
                new OPT_RegisterOperand(tmp, VM_TypeReference.Int))));
        // compute bottom half: lowlhsReg = lowlhsReg << shift  
        if (!result.similar(val1)) {
          EMIT(CPOS(s,
              MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));
        }
        EMIT(MIR_BinaryAcc.mutate(s, IA32_SHL,
                new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
                IC(shift)));
      } else if (shift == 32) {
        // lhsReg = lowrhsReg1 
        EMIT(CPOS(s,
            MIR_Move.create(IA32_MOV,
                new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
                new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));
        // lowlhsReg = 0
        EMIT(MIR_Move.mutate(s, IA32_MOV,
            new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
            IC(0)));
      } else if (shift == 33) {
        // lhsReg = lowrhsReg1 << 1
        EMIT(CPOS(s,
            MIR_Lea.create(IA32_LEA,
                new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
                new OPT_MemoryOperand(null, // base
                    new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int), //index
                    (byte)1, // scale
                    Offset.zero(), // displacement
                    (byte)4, // size
                    null, // location
                    null // guard
                    ))));
        // lowlhsReg = 0
        EMIT(MIR_Move.mutate(s, IA32_MOV,
            new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
            IC(0)));
      } else if (shift == 34) {
        // lhsReg = lowrhsReg1 << 2
        EMIT(CPOS(s,
            MIR_Lea.create(IA32_LEA,
                new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
                new OPT_MemoryOperand(null, // base
                    new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int), //index
                    (byte)2, // scale
                    Offset.zero(), // displacement
                    (byte)4, // size
                    null, // location
                    null // guard
                    ))));
        // lowlhsReg = 0
        EMIT(MIR_Move.mutate(s, IA32_MOV,
            new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
            IC(0)));
      } else if (shift == 35) {
        // lhsReg = lowrhsReg1 << 3
        EMIT(CPOS(s,
            MIR_Lea.create(IA32_LEA,
                new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
                new OPT_MemoryOperand(null, // base
                    new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int), //index
                    (byte)3, // scale
                    Offset.zero(), // displacement
                    (byte)4, // size
                    null, // location
                    null // guard
                    ))));
        // lowlhsReg = 0
        EMIT(MIR_Move.mutate(s, IA32_MOV,
            new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
            IC(0)));
      } else {
        if ((maskWith3f) || (shift < 64)){
          // lhsReg = lowrhsReg1 << ((shift - 32) & 0x1f)
          EMIT(CPOS(s,
              MIR_Move.create(IA32_MOV,
                  new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
                  new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));
          EMIT(CPOS(s,
              MIR_BinaryAcc.create(IA32_SHL,
                  new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
                  IC((shift-32) & 0x1F))));
          // lowlhsReg = 0
          EMIT(MIR_Move.mutate(s, IA32_MOV,
              new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
              IC(0)));
        } else {
          // lhsReg = 0
          EMIT(CPOS(s,
              MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              IC(0))));
          // lowlhsReg = 0
          EMIT(MIR_Move.mutate(s, IA32_MOV,
              new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
              IC(0)));          
        }
      }
    } else {
      throw new OPT_OptimizingCompilerException("OPT_BURS_Helpers",
          "unexpected parameters: " + result + "=" + val1 + "<<" + val2);
    }
  }

  /**
   * Expansion of LONG_SHR
   * @param s the instruction to expand
   * @param result the result operand
   * @param val1 the shifted operand
   * @param val2 the shift amount operand
   * @param maskWith3f should the shift operand by masked with 0x3f? This is
   *          default behaviour on Intel but it differs from how we combine
   *          shift operands in HIR
   */
  protected final void LONG_SHR(OPT_Instruction s, OPT_Operand result,
      OPT_Operand val1, OPT_Operand val2, boolean maskWith3f) {
    if (!val2.isIntConstant()) {
      // the most efficient form of expanding a shift by a variable amount
      // requires a branch so leave for complex operators
      // NB if !maskWith3f - we assume that a mask with 0x3F was required as
      // no optimizations currently exploits shift by registers of > 63
      // returning 0
      Binary.mutate(s, LONG_SHR, result.asRegister(), val1, val2);
      EMIT(s);
    } else if (result.isRegister()) {
      int shift = val2.asIntConstant().value;
      if (maskWith3f) {
        shift = shift & 0x3F;
      }
      OPT_Register lhsReg = result.asRegister().register;
      OPT_Register lowlhsReg = burs.ir.regpool.getSecondReg(lhsReg);
      OPT_Register rhsReg1 = val1.asRegister().register;
      OPT_Register lowrhsReg1 = burs.ir.regpool.getSecondReg(rhsReg1);

      if (shift == 0) {
        // operation is a nop.
        if (!result.similar(val1)) {
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));
        }
      } else if (shift == 1) {
        if (!result.similar(val1)) {
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));
        }
        // lhsReg = lhsReg >> 1
        EMIT(CPOS(s,
            MIR_BinaryAcc.create(IA32_SAR,
                new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
                IC(1))));
        // lowlhsReg = (lhsReg << 31) | (lowlhsReg >>> 1)
        EMIT(MIR_BinaryAcc.mutate(s, IA32_RCR,
            new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
            IC(1)));
      } else if (shift < 32) {
        // bits to shift in: tmp = rhsReg << (32 - shift)
        // TODO: use of LEA for SHL
        OPT_Register tmp = regpool.getInteger();
        EMIT(CPOS(s,
            MIR_Move.create(IA32_MOV,
                new OPT_RegisterOperand(tmp, VM_TypeReference.Int),
                new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));
        EMIT(CPOS(s,
            MIR_BinaryAcc.create(IA32_SHL,
                new OPT_RegisterOperand(tmp, VM_TypeReference.Int),
                IC(32 - shift))));
        // compute bottom half: lowlhsReg = (lowlhsReg1 >>> shift) | tmp
        if (!result.similar(val1)) {
          EMIT(CPOS(s,
              MIR_Move.create(IA32_MOV,
                  new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
                  new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));
        }
        EMIT(CPOS(s,
            MIR_BinaryAcc.create(IA32_SHR,
                new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
                IC(shift))));
        EMIT(CPOS(s,
            MIR_BinaryAcc.create(IA32_OR,
                new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
                new OPT_RegisterOperand(tmp, VM_TypeReference.Int))));
        // compute top half: lhsReg = lhsReg >> shift  
        if (!result.similar(val1)) {
          EMIT(CPOS(s,
              MIR_Move.create(IA32_MOV,
                  new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
                  new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));
        }
        EMIT(MIR_BinaryAcc.mutate(s, IA32_SAR,
            new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
            IC(shift)));
      } else if (shift == 32) {
        // lowlhsReg = rhsReg1
        EMIT(MIR_Move.mutate(s, IA32_MOV,
            new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
            new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int)));
        // lhsReg = rhsReg1 >> 31 
        if (!result.similar(val1)) {
          EMIT(CPOS(s,
              MIR_Move.create(IA32_MOV,
                  new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
                  new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));
        }
        EMIT(CPOS(s,
            MIR_BinaryAcc.create(IA32_SAR,
                new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
                IC(31))));
      } else {
        if ((!maskWith3f && (shift >= 0x3F))||
            (maskWith3f && ((shift & 0x3F) == 0x3F))) {
          // lhsReg = rhsReg1 >> 31
          if (!result.similar(val1)) {
            EMIT(CPOS(s,
                MIR_Move.create(IA32_MOV,
                    new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
                    new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));
          }
          EMIT(CPOS(s,
              MIR_BinaryAcc.create(IA32_SAR,
                  new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
                  IC(31))));
          // lowlhsReg = lhsReg
          EMIT(MIR_Move.mutate(s, IA32_MOV,
              new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int)));
        } else {
          // lhsReg = rhsReg1 >> 31 
          if (!result.similar(val1)) {
            EMIT(CPOS(s,
                MIR_Move.create(IA32_MOV,
                    new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
                    new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));
          }
          EMIT(CPOS(s,
              MIR_BinaryAcc.create(IA32_SAR,
                  new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
                  IC(31))));
          // lowlhsReg = rhsReg1 >> shift
          EMIT(MIR_Move.mutate(s, IA32_MOV,
              new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int)));
          EMIT(CPOS(s,
              MIR_BinaryAcc.create(IA32_SAR,
                  new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
                  IC((shift - 32) & 0x3F))));
        }
      }
    } else {
      throw new OPT_OptimizingCompilerException("OPT_BURS_Helpers",
          "unexpected parameters: " + result + "=" + val1 + ">>" + val2);
    }
  }

  /**
   * Expansion of LONG_USHR
   * @param s the instruction to expand
   * @param result the result operand
   * @param val1 the shifted operand
   * @param val2 the shift amount operand
   * @param maskWith3f should the shift operand by masked with 0x3f? This is
   *          default behaviour on Intel but it differs from how we combine
   *          shift operands in HIR
   */
  protected final void LONG_USHR(OPT_Instruction s, OPT_Operand result,
      OPT_Operand val1, OPT_Operand val2, boolean maskWith3f) {
    if (!val2.isIntConstant()) {
      // the most efficient form of expanding a shift by a variable amount
      // requires a branch so leave for complex operators
      // NB if !maskWith3f - we assume that a mask with 0x3F was required as
      // no optimizations currently exploits shift by registers of > 63
      // returning 0
      Binary.mutate(s, LONG_USHR, result.asRegister(), val1, val2);
      EMIT(s);
    } else if (result.isRegister()) {
      int shift = val2.asIntConstant().value;
      if (maskWith3f) {
        shift = shift & 0x3F;
      }
      OPT_Register lhsReg = result.asRegister().register;
      OPT_Register lowlhsReg = burs.ir.regpool.getSecondReg(lhsReg);
      OPT_Register rhsReg1 = val1.asRegister().register;
      OPT_Register lowrhsReg1 = burs.ir.regpool.getSecondReg(rhsReg1);

      if (shift == 0) {
        // operation is a nop.
        if (!result.similar(val1)) {
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));
        }
      } else if (shift == 1) {
        if (!result.similar(val1)) {
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));
        }
        // lhsReg = lhsReg >>> 1
        EMIT(CPOS(s,
            MIR_BinaryAcc.create(IA32_SHR,
                new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
                IC(1))));
        // lowlhsReg = (lhsReg << 31) | (lowlhsReg >>> 1)
        EMIT(MIR_BinaryAcc.mutate(s, IA32_RCR,
            new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
            IC(1)));
      } else if (shift < 32) {
        // bits to shift in: tmp = rhsReg << (32 - shift)
        // TODO: use LEA for SHL operator
        OPT_Register tmp = regpool.getInteger();
        EMIT(CPOS(s,
            MIR_Move.create(IA32_MOV,
                new OPT_RegisterOperand(tmp, VM_TypeReference.Int),
                new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));
        EMIT(CPOS(s,
            MIR_BinaryAcc.create(IA32_SHL,
                new OPT_RegisterOperand(tmp, VM_TypeReference.Int),
                IC(32 - shift))));
        // compute bottom half: lowlhsReg = (lowlhsReg1 >>> shift) | tmp
        if (!result.similar(val1)) {
          EMIT(CPOS(s,
              MIR_Move.create(IA32_MOV,
                  new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
                  new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));
        }
        EMIT(CPOS(s,
            MIR_BinaryAcc.create(IA32_SHR,
                new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
                IC(shift))));
        EMIT(CPOS(s,
            MIR_BinaryAcc.create(IA32_OR,
                new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
                new OPT_RegisterOperand(tmp, VM_TypeReference.Int))));
        // compute top half: lhsReg = lhsReg >>> shift  
        if (!result.similar(val1)) {
          EMIT(CPOS(s,
              MIR_Move.create(IA32_MOV,
                  new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
                  new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));
        }
        EMIT(MIR_BinaryAcc.mutate(s, IA32_SHR,
            new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
            IC(shift)));
      } else if (shift == 32) {
        // lowlhsReg = rhsReg1
        EMIT(MIR_Move.mutate(s, IA32_MOV,
            new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
            new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int)));
        // lhsReg = 0
        EMIT(CPOS(s,
            MIR_Move.create(IA32_MOV,
                new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
                IC(0))));
      } else {
        if (maskWith3f || (shift < 64)) {
          // lowlhsReg = rhsReg1 >>> (shift & 0x1F)
          EMIT(CPOS(s,
              MIR_Move.create(IA32_MOV,
              new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
              new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));
          EMIT(CPOS(s,
              MIR_BinaryAcc.create(IA32_SHR,
              new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
              IC(shift&0x1F))));
        } else {
          // lowlhsReg = 0
          EMIT(CPOS(s,
              MIR_Move.create(IA32_MOV,
                  new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
                  IC(0))));
        }
        // lhsReg = 0
        EMIT(MIR_Move.mutate(s, IA32_MOV,
                new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
                IC(0)));
      }
    } else {
      throw new OPT_OptimizingCompilerException("OPT_BURS_Helpers",
          "unexpected parameters: " + result + "=" + val1 + ">>" + val2);
    }
  }

  /**
   * Expansion of RDTSC (called GET_TIME_BASE for consistency with PPC)
   *
   * @param s the instruction to expand
   * @param result the result/first operand
   */
  protected final void GET_TIME_BASE(OPT_Instruction s,
      OPT_RegisterOperand result) {
    OPT_Register highReg = result.register;
    OPT_Register lowReg = regpool.getSecondReg(highReg);
    EMIT(CPOS(s, MIR_RDTSC.create(IA32_RDTSC,
        new OPT_RegisterOperand(getEAX(), VM_TypeReference.Int),
        new OPT_RegisterOperand(getEDX(), VM_TypeReference.Int))));
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
        new OPT_RegisterOperand(lowReg, VM_TypeReference.Int),
        new OPT_RegisterOperand(getEAX(), VM_TypeReference.Int))));
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
        new OPT_RegisterOperand(highReg, VM_TypeReference.Int),
        new OPT_RegisterOperand(getEDX(), VM_TypeReference.Int))));
  }

  /**
   * Expansion of LONG_CMP: compare to values and set result to -1, 0, 1 for <, =, >,
   * respectively
   *
   * @param s the compare instruction
   * @param res the result/first operand
   * @param val1 the first value
   * @param val2 the second value
   */
  protected final void LONG_CMP(OPT_Instruction s, OPT_RegisterOperand res, OPT_Operand val1, OPT_Operand val2) {
    OPT_RegisterOperand one = regpool.makeTempInt();
    OPT_RegisterOperand lone = regpool.makeTempInt();
    OPT_Operand two, ltwo;
    if (val1 instanceof OPT_RegisterOperand) {
      OPT_Register val1_reg = val1.asRegister().register;
      EMIT(CPOS(s, MIR_Move.create(IA32_MOV, one, new OPT_RegisterOperand(val1_reg, VM_TypeReference.Int))));
      EMIT(CPOS(s,
                MIR_Move.create(IA32_MOV,
                                lone,
                                new OPT_RegisterOperand(regpool.getSecondReg(val1_reg), VM_TypeReference.Int))));
    } else {
      OPT_LongConstantOperand tmp = (OPT_LongConstantOperand) val1;
      EMIT(CPOS(s, MIR_Move.create(IA32_MOV, one, IC(tmp.upper32()))));
      EMIT(CPOS(s, MIR_Move.create(IA32_MOV, lone, IC(tmp.lower32()))));
    }
    if (val2 instanceof OPT_RegisterOperand) {
      two = val2;
      ltwo = L(burs.ir.regpool.getSecondReg(val2.asRegister().register));
    } else {
      OPT_LongConstantOperand tmp = (OPT_LongConstantOperand) val2;
      two = IC(tmp.upper32());
      ltwo = IC(tmp.lower32());
    }
    EMIT(CPOS(s, MIR_Compare.create(IA32_CMP, lone.copyRO(), ltwo)));
    EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SBB, one.copyRO(), two)));
    EMIT(CPOS(s, MIR_Set
        .create(IA32_SET__B, res, OPT_IA32ConditionOperand.LT()))); // res =
    // (val1 < val2) ? 1 :0
    EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_OR, one.copyRO(), lone.copyRO())));
    EMIT(CPOS(s,
              MIR_Set.create(IA32_SET__B,
                             lone.copyRO(),
                             OPT_IA32ConditionOperand.NE()))); // lone = (val1 != val2) ? 1 : 0
    EMIT(CPOS(s, MIR_UnaryAcc.create(IA32_NEG, res.copyRO()))); // res = (val1 <
    // val2) ? -1 :0
    EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_OR, res.copyRO(), lone.copyRO())));
    EMIT(MIR_Unary.mutate(s, IA32_MOVSX__B, res.copyRO(), res.copyRO()));
  }

  /**
   * Expansion of FP_ADD_ACC, FP_MUL_ACC, FP_SUB_ACC, and FP_DIV_ACC. Moves
   * first value into fp0, accumulates second value into fp0 using op, moves fp0
   * into result.
   *
   * @param s the instruction to expand
   * @param op the floating point op to use
   * @param result the result operand
   * @param val1 the first operand
   * @param val2 the second operand
   */
  protected final void FP_MOV_OP_MOV(OPT_Instruction s, OPT_Operator op, OPT_Operand result, OPT_Operand val1,
                                     OPT_Operand val2) {
    EMIT(CPOS(s, MIR_Move.create(IA32_FMOV, D(getFPR(0)), val1)));
    EMIT(MIR_BinaryAcc.mutate(s, op, D(getFPR(0)), val2));
    EMIT(CPOS(s, MIR_Move.create(IA32_FMOV, result, D(getFPR(0)))));
  }

  /**
   * Expansion of FP_REM
   *
   * @param s the instruction to expand
   * @param val1 the first operand
   * @param val2 the second operand
   */
  protected final void FP_REM(OPT_Instruction s, OPT_Operand val1, OPT_Operand val2) {
    EMIT(CPOS(s, MIR_Move.create(IA32_FMOV, D(getFPR(1)), val2)));
    EMIT(CPOS(s, MIR_Move.create(IA32_FMOV, D(getFPR(0)), val1)));
    EMIT(MIR_BinaryAcc.mutate(s, IA32_FPREM, D(getFPR(0)), D(getFPR(1))));
  }

  /**
   * Expansion for [DF]CMP[GL] compare to values and set result to -1, 0, 1 for <, =, >,
   * respectively
   *
   * @param s the compare instruction
   */
  protected final void threeValueFPCmp(OPT_Instruction s) {
    // IMPORTANT: FCOMI only sets 3 of the 6 bits in EFLAGS, so
    // we can't quite just translate the condition operand as if it
    // were an integer compare.
    // FCMOI sets ZF, PF, and CF as follows:
    // Compare Results ZF PF CF
    // left > right 0 0 0
    // left < right 0 0 1
    // left == right 1 0 0
    // UNORDERED 1 1 1
    OPT_RegisterOperand one = (OPT_RegisterOperand) Binary.getClearVal1(s);
    OPT_RegisterOperand two = (OPT_RegisterOperand) Binary.getClearVal2(s);
    OPT_RegisterOperand res = Binary.getClearResult(s);
    OPT_RegisterOperand temp = burs.ir.regpool.makeTempInt();
    OPT_Register FP0 = burs.ir.regpool.getPhysicalRegisterSet().getFPR(0);
    if ((s.operator == DOUBLE_CMPL) || (s.operator == FLOAT_CMPL)) {
      EMIT(CPOS(s, MIR_Move.create(IA32_MOV, temp, IC(0))));
      // Perform compare
      EMIT(CPOS(s, MIR_Move.create(IA32_FMOV, new OPT_RegisterOperand(FP0, VM_TypeReference.Int), one)));
      EMIT(CPOS(s, MIR_Compare.create(IA32_FCOMI, new OPT_RegisterOperand(FP0, VM_TypeReference.Int), two)));
      // res = (value1 > value2) ? 1 : 0
      // temp = ((value1 < value2) || unordered) ? -1 : 0
      EMIT(CPOS(s, MIR_Set.create(IA32_SET__B, res, OPT_IA32ConditionOperand
          .LGT())));
      EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SBB, temp.copyRO(), temp.copyRO())));
    } else {
      OPT_RegisterOperand temp2 = burs.ir.regpool.makeTempInt();
      // Perform compare
      EMIT(CPOS(s, MIR_Move.create(IA32_FMOV, new OPT_RegisterOperand(FP0, VM_TypeReference.Int), one)));
      EMIT(CPOS(s, MIR_Compare.create(IA32_FCOMI, new OPT_RegisterOperand(FP0, VM_TypeReference.Int), two)));
      // res = (value1 > value2) ? 1 : 0
      // temp2 = (value1 unordered value2) ? 1 : 0
      // temp = ((value1 unordered value2) ? 1 : 0) - 0 - CF
      // (i.e. temp = (value1 < value2) ? -1 : 0)
      EMIT(CPOS(s, MIR_Set.create(IA32_SET__B, temp, OPT_IA32ConditionOperand
          .PO())));
      EMIT(CPOS(s, MIR_Set.create(IA32_SET__B, res, OPT_IA32ConditionOperand
          .LGT())));
      EMIT(CPOS(s, MIR_Move.create(IA32_MOV, temp2, temp.copyRO())));
      EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SBB, temp.copyRO(), IC(0))));
      // Put result from temp2 in res
      EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_OR, res.copyRO(), temp2.copyRO())));
    }
    EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_OR, res.copyRO(), temp.copyRO())));
    EMIT(MIR_Unary.mutate(s, IA32_MOVSX__B, res.copyRO(), res.copyRO()));
  }

  /**
   * Expansion of BOOLEAN_CMP_INT
   *
   * @param s the instruction to copy position info from
   * @param res the result operand
   * @param val1 the first value
   * @param val2 the second value
   * @param cond the condition operand
   */
  protected final void BOOLEAN_CMP_INT(OPT_Instruction s, OPT_RegisterOperand res, OPT_Operand val1, OPT_Operand val2,
                                       OPT_ConditionOperand cond) {
    EMIT(CPOS(s, MIR_Compare.create(IA32_CMP, val1, val2)));
    OPT_RegisterOperand temp = regpool.makeTemp(VM_TypeReference.Boolean);
    EMIT(CPOS(s, MIR_Set.create(IA32_SET__B, temp, COND(cond))));
    EMIT(MIR_Unary.mutate(s, IA32_MOVZX__B, res, temp.copyD2U()));
  }

  /**
   * Expansion of a special case of BOOLEAN_CMP_INT when the condition registers
   * have already been set by the previous ALU op.
   *
   * @param s the instruction to copy position info from
   * @param res the result operand
   * @param cond the condition operand
   */
  protected final void BOOLEAN_CMP_INT(OPT_Instruction s, OPT_RegisterOperand res, OPT_ConditionOperand cond) {
    OPT_RegisterOperand temp = regpool.makeTemp(VM_TypeReference.Boolean);
    EMIT(CPOS(s, MIR_Set.create(IA32_SET__B, temp, COND(cond))));
    EMIT(MIR_Unary.mutate(s, IA32_MOVZX__B, res, temp.copyD2U()));
  }

  /**
   * Expansion of BOOLEAN_CMP_DOUBLE
   *
   * @param s the instruction to copy position info from
   * @param res the result operand
   * @param val1 the first value
   * @param val2 the second value
   * @param cond the condition operand
   */
  protected final void BOOLEAN_CMP_DOUBLE(OPT_Instruction s, OPT_RegisterOperand res, OPT_ConditionOperand cond,
                                          OPT_Operand val1, OPT_Operand val2) {
    OPT_RegisterOperand temp = regpool.makeTemp(VM_TypeReference.Boolean);
    EMIT(CPOS(s, MIR_Move.create(IA32_FMOV, D(getFPR(0)), CondMove.getVal1(s))));
    EMIT(CPOS(s, MIR_Compare.create(IA32_FCOMI, D(getFPR(0)), CondMove
        .getVal2(s))));
    EMIT(CPOS(s, MIR_Set.create(IA32_SET__B, temp, COND(cond))));
    EMIT(MIR_Unary.mutate(s, IA32_MOVZX__B, res, temp.copyD2U()));
  }

  /**
   * Expansion of BOOLEAN_CMP_LONG
   *
   * @param s the instruction to copy position info from
   * @param res the result operand
   * @param val1 the first value
   * @param val2 the second value
   * @param cond the condition operand
   */
  protected final void BOOLEAN_CMP_LONG(OPT_Instruction s, OPT_RegisterOperand res, OPT_Operand val1, OPT_Operand val2,
                                        OPT_ConditionOperand cond) {
    // Can we simplify to a shift?
    if (cond.isLESS() && val2.isLongConstant() && val2.asLongConstant().value == 0 && val1.isRegister()) {
      // Put the most significant bit of val1 into res
      OPT_Register val1_reg = val1.asRegister().register;
      EMIT(MIR_Move.create(IA32_MOV, res.copyRO(), new OPT_RegisterOperand(val1_reg, VM_TypeReference.Int)));      
      EMIT(MIR_BinaryAcc.mutate(s, IA32_SHR, res, IC(31)));      
    }
    else if (cond.isGREATER_EQUAL() && val2.isLongConstant() && val2.asLongConstant().value == 0 && val1.isRegister()) {
      // Put the most significant bit of val1 into res and invert
      OPT_Register val1_reg = val1.asRegister().register;
      EMIT(MIR_Move.create(IA32_MOV, res.copyRO(), new OPT_RegisterOperand(val1_reg, VM_TypeReference.Int)));      
      EMIT(MIR_BinaryAcc.mutate(s, IA32_SHR, res, IC(31))); 
      EMIT(MIR_BinaryAcc.create(IA32_XOR, res.copyRO(), IC(1))); 
    } 
    else {
      // Long comparison is a subtraction:
      // <, >= : easy to compute as SF !=/== OF
      // >, <= : flipOperands and treat as a </>=
      // ==/!= : do subtract then OR 2 32-bit quantities test for zero/non-zero
      if (cond.isGREATER() || cond.isLESS_EQUAL()) {
        OPT_Operand swap_temp;
        cond.flipOperands();
        swap_temp = val1;
        val1 = val2;
        val2 = swap_temp;
      }
      if (VM.VerifyAssertions) {
        VM._assert(cond.isEQUAL() || cond.isNOT_EQUAL() || cond.isLESS() || cond.isGREATER_EQUAL());
      }
      OPT_RegisterOperand one = regpool.makeTempInt();
      OPT_RegisterOperand lone = regpool.makeTempInt();
      OPT_Operand two, ltwo;
      if (val1 instanceof OPT_RegisterOperand) {
        OPT_Register val1_reg = val1.asRegister().register;
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV, one, new OPT_RegisterOperand(val1_reg, VM_TypeReference.Int))));
        EMIT(CPOS(s,
            MIR_Move.create(IA32_MOV,
                lone,
                new OPT_RegisterOperand(regpool.getSecondReg(val1_reg), VM_TypeReference.Int))));
      } else {
        OPT_LongConstantOperand tmp = (OPT_LongConstantOperand) val1;
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV, one, IC(tmp.upper32()))));
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV, lone, IC(tmp.lower32()))));
      }
      if (val2 instanceof OPT_RegisterOperand) {
        two = val2;
        ltwo = L(burs.ir.regpool.getSecondReg(val2.asRegister().register));
      } else {
        OPT_LongConstantOperand tmp = (OPT_LongConstantOperand) val2;
        two = IC(tmp.upper32());
        ltwo = IC(tmp.lower32());
      }
      if (cond.isEQUAL() || cond.isNOT_EQUAL()) {
        EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SUB, lone.copyRO(), ltwo)));
        EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SBB, one.copyRO(), two)));
        EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_OR, one.copyRO(), lone.copyRO())));
      } else {
        EMIT(CPOS(s, MIR_Compare.create(IA32_CMP, lone.copyRO(), ltwo)));
        EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SBB, one.copyRO(), two)));
      }
      OPT_RegisterOperand temp = regpool.makeTemp(VM_TypeReference.Boolean);
      EMIT(CPOS(s, MIR_Set.create(IA32_SET__B, temp, COND(cond))));
      EMIT(MIR_Unary.mutate(s, IA32_MOVZX__B, res, temp.copyRO()));
    }
  }

  /**
   * Generate a long compare and cmov
   *
   * @param s the instruction to copy position info from
   * @param result the result of the conditional move
   * @param val1 the first value
   * @param val2 the second value
   * @param cond the condition operand
   * @param trueValue the value to move to result if cond is true
   * @param falseValue the value to move to result if cond is not true
   */
  protected final void LCMP_CMOV(OPT_Instruction s, OPT_RegisterOperand result, OPT_Operand val1, OPT_Operand val2,
                                 OPT_ConditionOperand cond, OPT_Operand trueValue, OPT_Operand falseValue) {
    // Long comparison is a subtraction:
    // <, >= : easy to compute as SF !=/== OF
    // >, <= : flipOperands and treat as a </>=
    // ==/!= : do subtract then OR 2 32-bit quantities test for zero/non-zero
    if (cond.isGREATER() || cond.isLESS_EQUAL()) {
      OPT_Operand swap_temp;
      cond.flipOperands();
      swap_temp = val1;
      val1 = val2;
      val2 = swap_temp;
    }
    if (VM.VerifyAssertions) {
      VM._assert(cond.isEQUAL() || cond.isNOT_EQUAL() || cond.isLESS() || cond.isGREATER_EQUAL());
    }
    OPT_RegisterOperand one = regpool.makeTempInt();
    OPT_RegisterOperand lone = regpool.makeTempInt();
    OPT_Operand two, ltwo;
    if (val1 instanceof OPT_RegisterOperand) {
      OPT_Register val1_reg = val1.asRegister().register;
      EMIT(CPOS(s, MIR_Move.create(IA32_MOV, one, new OPT_RegisterOperand(val1_reg, VM_TypeReference.Int))));
      EMIT(CPOS(s,
                MIR_Move.create(IA32_MOV,
                                lone,
                                new OPT_RegisterOperand(regpool.getSecondReg(val1_reg), VM_TypeReference.Int))));
    } else {
      OPT_LongConstantOperand tmp = (OPT_LongConstantOperand) val1;
      EMIT(CPOS(s, MIR_Move.create(IA32_MOV, one, IC(tmp.upper32()))));
      EMIT(CPOS(s, MIR_Move.create(IA32_MOV, lone, IC(tmp.lower32()))));
    }
    if (val2 instanceof OPT_RegisterOperand) {
      two = val2;
      ltwo = L(burs.ir.regpool.getSecondReg(val2.asRegister().register));
    } else {
      OPT_LongConstantOperand tmp = (OPT_LongConstantOperand) val2;
      two = IC(tmp.upper32());
      ltwo = IC(tmp.lower32());
    }
    if (cond.isEQUAL() || cond.isNOT_EQUAL()) {
      EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SUB, lone.copyRO(), ltwo)));
      EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SBB, one.copyRO(), two)));
      EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_OR, one.copyRO(), lone.copyRO())));
    } else {
      EMIT(CPOS(s, MIR_Compare.create(IA32_CMP, lone.copyRO(), ltwo)));
      EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SBB, one.copyRO(), two)));
    }
    CMOV_MOV(s, result, cond, trueValue, falseValue);
  }

  /**
   * Generate a compare and branch sequence. Used in the expansion of trees
   * where INT_IFCMP is a root
   *
   * @param s the ifcmp instruction
   * @param guardResult the guard result of the ifcmp
   * @param val1 the first value operand
   * @param val2 the second value operand
   * @param cond the condition operand
   */
  protected final void IFCMP(OPT_Instruction s, OPT_RegisterOperand guardResult, OPT_Operand val1, OPT_Operand val2,
                             OPT_ConditionOperand cond) {
    if (VM.VerifyAssertions) {
      // We only need make sure the guard information is correct when
      // validating, the null check combining phase removes all guards
      EMIT(CPOS(s, Move.create(GUARD_MOVE, guardResult, new OPT_TrueGuardOperand())));
    }
    EMIT(CPOS(s, MIR_Compare.create(IA32_CMP, val1, val2)));
    EMIT(MIR_CondBranch.mutate(s, IA32_JCC, COND(cond), IfCmp.getTarget(s), IfCmp.getBranchProfile(s)));
  }

  /**
   * Generate an integer move portion of a conditional move.
   *
   * @param s the instruction to copy position info from
   * @param result the result of the conditional move
   * @param cond the condition operand
   * @param trueValue the value to move to result if cond is true
   * @param falseValue the value to move to result if cond is not true
   */
  protected final void CMOV_MOV(OPT_Instruction s, OPT_RegisterOperand result, OPT_ConditionOperand cond,
                                OPT_Operand trueValue, OPT_Operand falseValue) {
    if (result.similar(trueValue)) {
      // in this case, only need a conditional move for the false branch.
      EMIT(MIR_CondMove.mutate(s, IA32_CMOV, result, asReg(s, IA32_MOV, falseValue), COND(cond.flipCode())));
    } else if (result.similar(falseValue)) {
      // in this case, only need a conditional move for the true branch.
      EMIT(MIR_CondMove.mutate(s, IA32_CMOV, result, asReg(s, IA32_MOV, trueValue), COND(cond)));
    } else {
      // need to handle both possible assignments. Unconditionally
      // assign one value then conditionally assign the other.
      if (falseValue.isRegister()) {
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV, result, trueValue)));
        EMIT(MIR_CondMove.mutate(s, IA32_CMOV, result.copyRO(), falseValue, COND(cond.flipCode())));
      } else {
        if (trueValue.isRegister()) {
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV, result, falseValue)));
          EMIT(MIR_CondMove.mutate(s, IA32_CMOV, result.copyRO(), trueValue, COND(cond)));
        } else {
          // Perform constant move without creating a register (costs
          // 1 or 2 more instructions but saves a register)
          int true_const = ((OPT_IntConstantOperand) trueValue).value;
          int false_const = ((OPT_IntConstantOperand) falseValue).value;
          // Generate values for consts trying to avoid zero extending the
          // set__b result
          // result = cond ? 1 : 0
          EMIT(CPOS(s, MIR_Set.create(IA32_SET__B, result.copyRO(), COND(cond))));

          if ((true_const - false_const) == 1) {
            // result = (cond ? 1 : 0) + false_const
            EMIT(CPOS(s, MIR_Unary.create(IA32_MOVZX__B, result.copyRO(), result.copyRO())));
            EMIT(MIR_BinaryAcc.mutate(s, IA32_ADD, result, IC(false_const)));
          } else if ((false_const - true_const) == 1) {
            // result = (cond ? -1 : 0) + false_const
            EMIT(CPOS(s, MIR_Unary.create(IA32_MOVZX__B, result.copyRO(), result.copyRO())));
            EMIT(CPOS(s, MIR_UnaryAcc.create(IA32_NEG, result.copyRO())));
            EMIT(MIR_BinaryAcc.mutate(s, IA32_ADD, result, IC(false_const)));
          } else if (((false_const - true_const) > 0) && ((false_const - true_const) <= 0xFF)) {
            // result = cond ? 0 : -1
            // result = (cond ? 0 : -1) & (false_const - true__const)
            // result = ((cond ? 0 : -1) & (false_const - true_const)) +
            // true_const
            EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SUB, result.copyRO(), IC(1))));
            EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_AND, result.copyRO(), IC(false_const - true_const))));
            EMIT(MIR_BinaryAcc.mutate(s, IA32_ADD, result, IC(true_const)));
          } else {
            // result = cond ? -1 : 0
            // result = (cond ? -1 : 0) & (true_const - false_const)
            // result = ((cond ? -1 : 0) & (true_const - false_const)) +
            // false_const
            if (((true_const - false_const) > 0xFF) || ((true_const - false_const) < 0)) {
              EMIT(CPOS(s, MIR_Unary.create(IA32_MOVZX__B, result.copyRO(), result.copyRO())));
            }
            EMIT(CPOS(s, MIR_UnaryAcc.create(IA32_NEG, result.copyRO())));
            EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_AND, result.copyRO(), IC(true_const - false_const))));
            EMIT(MIR_BinaryAcc.mutate(s, IA32_ADD, result, IC(false_const)));
          }
        }
      }
    }
  }

  /**
   * Generate a floating point move portion of a conditional move.
   *
   * @param s the instruction to copy position info from
   * @param result the result of the conditional move
   * @param cond the condition operand
   * @param trueValue the value to move to result if cond is true
   * @param falseValue the value to move to result if cond is not true
   */
  protected final void CMOV_FMOV(OPT_Instruction s, OPT_RegisterOperand result, OPT_ConditionOperand cond,
                                 OPT_Operand trueValue, OPT_Operand falseValue) {
    if (result.similar(trueValue)) {
      // in this case, only need a conditional move for the false branch.
      EMIT(MIR_CondMove.mutate(s, IA32_FCMOV, result, asReg(s, IA32_FMOV, falseValue), COND(cond.flipCode())));
    } else if (result.similar(falseValue)) {
      // in this case, only need a conditional move for the true branch.
      EMIT(MIR_CondMove.mutate(s, IA32_FCMOV, result, asReg(s, IA32_FMOV, trueValue), COND(cond)));
    } else {
      // need to handle both possible assignments. Unconditionally
      // assign one value then conditionally assign the other.
      if (falseValue.isRegister()) {
        EMIT(CPOS(s, MIR_Move.create(IA32_FMOV, result, trueValue)));
        EMIT(MIR_CondMove.mutate(s, IA32_FCMOV, result.copyRO(), falseValue, COND(cond.flipCode())));
      } else {
        EMIT(CPOS(s, MIR_Move.create(IA32_FMOV, result, falseValue)));
        EMIT(MIR_CondMove.mutate(s, IA32_FCMOV, result.copyRO(), asReg(s, IA32_FMOV, trueValue), COND(cond)));
      }
    }
  }

  /**
   * Expand a prologue by expanding out longs into pairs of ints
   */
  protected final void PROLOGUE(OPT_Instruction s) {
    int numFormals = Prologue.getNumberOfFormals(s);
    int numLongs = 0;
    for (int i = 0; i < numFormals; i++) {
      if (Prologue.getFormal(s, i).type.isLongType()) {
        numLongs++;
      }
    }
    if (numLongs != 0) {
      OPT_Instruction s2 = Prologue.create(IR_PROLOGUE, numFormals + numLongs);
      for (int sidx = 0, s2idx = 0; sidx < numFormals; sidx++) {
        OPT_RegisterOperand sForm = Prologue.getFormal(s, sidx);
        if (sForm.type.isLongType()) {
          sForm.type = VM_TypeReference.Int;
          Prologue.setFormal(s2, s2idx++, sForm);
          OPT_Register r2 = regpool.getSecondReg(sForm.register);
          Prologue.setFormal(s2, s2idx++, new OPT_RegisterOperand(r2, VM_TypeReference.Int));
          sForm.register.clearType();
          sForm.register.setInteger();
          r2.clearType();
          r2.setInteger();
        } else {
          Prologue.setFormal(s2, s2idx++, sForm);
        }
      }
      EMIT(s2);
    } else {
      EMIT(s);
    }
  }

  /**
   * Expansion of CALL. Expand longs registers into pairs of int registers.
   *
   * @param s the instruction to expand
   * @param address the operand containing the target address
   */
  protected final void CALL(OPT_Instruction s, OPT_Operand address) {
    // Step 1: Find out how many parameters we're going to have.
    int numParams = Call.getNumberOfParams(s);
    int longParams = 0;
    for (int pNum = 0; pNum < numParams; pNum++) {
      if (Call.getParam(s, pNum).getType().isLongType()) {
        longParams++;
      }
    }

    // Step 2: Figure out what the result and result2 values will be.
    OPT_RegisterOperand result = Call.getResult(s);
    OPT_RegisterOperand result2 = null;
    if (result != null && result.type.isLongType()) {
      result.type = VM_TypeReference.Int;
      result2 = new OPT_RegisterOperand(regpool.getSecondReg(result.register), VM_TypeReference.Int);
    }

    // Step 3: Mutate the Call to an MIR_Call.
    // Note MIR_Call and Call have a different number of fixed
    // arguments, so some amount of copying is required.
    OPT_Operand[] params = new OPT_Operand[numParams];
    for (int i = 0; i < numParams; i++) {
      params[i] = Call.getParam(s, i);
    }
    MIR_Call.mutate(s, IA32_CALL, result, result2, address, Call.getMethod(s), numParams + longParams);
    for (int paramIdx = 0, mirCallIdx = 0; paramIdx < numParams;) {
      OPT_Operand param = params[paramIdx++];
      if (param instanceof OPT_RegisterOperand) {
        MIR_Call.setParam(s, mirCallIdx++, param);
        OPT_RegisterOperand rparam = (OPT_RegisterOperand) param;
        if (rparam.type.isLongType()) {
          MIR_Call.setParam(s, mirCallIdx++, L(regpool
              .getSecondReg(rparam.register)));
        }
      } else if (param instanceof OPT_LongConstantOperand) {
        OPT_LongConstantOperand val = (OPT_LongConstantOperand) param;
        MIR_Call.setParam(s, mirCallIdx++, IC(val.upper32()));
        MIR_Call.setParam(s, mirCallIdx++, IC(val.lower32()));
      } else {
        MIR_Call.setParam(s, mirCallIdx++, param);
      }
    }

    // emit the call instruction.
    EMIT(s);
  }

  /**
   * Expansion of SYSCALL. Expand longs registers into pairs of int registers.
   *
   * @param s the instruction to expand
   * @param address the operand containing the target address
   */
  protected final void SYSCALL(OPT_Instruction s, OPT_Operand address) {
    burs.ir.setHasSysCall(true);

    // Step 1: Find out how many parameters we're going to have.
    int numParams = Call.getNumberOfParams(s);
    int longParams = 0;
    for (int pNum = 0; pNum < numParams; pNum++) {
      if (Call.getParam(s, pNum).getType().isLongType()) {
        longParams++;
      }
    }

    // Step 2: Figure out what the result and result2 values will be.
    OPT_RegisterOperand result = Call.getResult(s);
    OPT_RegisterOperand result2 = null;
    // NOTE: C callee returns longs little endian!
    if (result != null && result.type.isLongType()) {
      result.type = VM_TypeReference.Int;
      result2 = result;
      result = new OPT_RegisterOperand(regpool.getSecondReg(result.register), VM_TypeReference.Int);
    }

    // Step 3: Mutate the Call to an MIR_Call.
    // Note MIR_Call and Call have a different number of fixed
    // arguments, so some amount of copying is required.
    OPT_Operand[] params = new OPT_Operand[numParams];
    for (int i = 0; i < numParams; i++) {
      params[i] = Call.getParam(s, i);
    }
    MIR_Call.mutate(s, IA32_SYSCALL, result, result2, address, Call
        .getMethod(s), numParams + longParams);
    for (int paramIdx = 0, mirCallIdx = 0; paramIdx < numParams;) {
      OPT_Operand param = params[paramIdx++];
      if (param instanceof OPT_RegisterOperand) {
        // NOTE: longs passed little endian to C callee!
        OPT_RegisterOperand rparam = (OPT_RegisterOperand) param;
        if (rparam.type.isLongType()) {
          MIR_Call.setParam(s, mirCallIdx++, L(regpool
              .getSecondReg(rparam.register)));
        }
        MIR_Call.setParam(s, mirCallIdx++, param);
      } else if (param instanceof OPT_LongConstantOperand) {
        long value = ((OPT_LongConstantOperand) param).value;
        int valueHigh = (int) (value >> 32);
        int valueLow = (int) (value & 0xffffffff);
        // NOTE: longs passed little endian to C callee!
        MIR_Call.setParam(s, mirCallIdx++, IC(valueLow));
        MIR_Call.setParam(s, mirCallIdx++, IC(valueHigh));
      } else {
        MIR_Call.setParam(s, mirCallIdx++, param);
      }
    }

    // emit the call instruction.
    EMIT(s);
  }

  /**
   * Expansion of LOWTABLESWITCH.
   *
   * @param s the instruction to expand
   */
  protected final void LOWTABLESWITCH(OPT_Instruction s) {
    // (1) We're changing index from a U to a DU.
    // Inject a fresh copy instruction to make sure we aren't
    // going to get into trouble (if someone else was also using index).
    OPT_RegisterOperand newIndex = regpool.makeTempInt();
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV, newIndex, LowTableSwitch.getIndex(s))));
    int number = LowTableSwitch.getNumberOfTargets(s);
    OPT_Instruction s2 = CPOS(s, MIR_LowTableSwitch.create(MIR_LOWTABLESWITCH, newIndex.copyRO(), number * 2));
    for (int i = 0; i < number; i++) {
      MIR_LowTableSwitch.setTarget(s2, i, LowTableSwitch.getTarget(s, i));
      MIR_LowTableSwitch.setBranchProfile(s2, i, LowTableSwitch
          .getBranchProfile(s, i));
    }
    EMIT(s2);
  }

  /**
   * Expansion of RESOLVE. Dynamic link point. Build up MIR instructions for
   * Resolve.
   *
   * @param s the instruction to expand
   */
  protected final void RESOLVE(OPT_Instruction s) {
    OPT_Operand target = loadFromJTOC(VM_Entrypoints.optResolveMethod
        .getOffset());
    EMIT(CPOS(s,
              MIR_Call.mutate0(s,
                               CALL_SAVE_VOLATILE,
                               null,
                               null,
                               target,
                               OPT_MethodOperand.STATIC(VM_Entrypoints.optResolveMethod))));
  }

  /**
   * Expansion of TRAP_IF, with an int constant as the second value.
   *
   * @param s the instruction to expand
   * @param longConstant is the argument a long constant?
   */
  protected final void TRAP_IF_IMM(OPT_Instruction s, boolean longConstant) {
    OPT_RegisterOperand gRes = TrapIf.getGuardResult(s);
    OPT_RegisterOperand v1 = (OPT_RegisterOperand) TrapIf.getVal1(s);
    OPT_ConstantOperand v2 = (OPT_ConstantOperand) TrapIf.getVal2(s);
    OPT_ConditionOperand cond = TrapIf.getCond(s);
    OPT_TrapCodeOperand tc = TrapIf.getTCode(s);

    // A slightly ugly matter, but we need to deal with combining
    // the two pieces of a long register from a LONG_ZERO_CHECK.
    // A little awkward, but probably the easiest workaround...
    if (longConstant) {
      if (VM.VerifyAssertions) {
        VM._assert((tc.getTrapCode() == VM_Runtime.TRAP_DIVIDE_BY_ZERO) &&
                   (((OPT_LongConstantOperand) v2).value == 0L));
      }
      OPT_RegisterOperand rr = regpool.makeTempInt();
      EMIT(CPOS(s, MIR_Move.create(IA32_MOV, rr, v1.copy())));
      EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_OR,
                                rr.copy(),
                                new OPT_RegisterOperand(regpool.getSecondReg(v1.register), VM_TypeReference.Int))));
      v1 = rr.copyD2U();
      v2 = IC(0);
    }
    // emit the trap instruction
    EMIT(MIR_TrapIf.mutate(s, IA32_TRAPIF, gRes, v1, v2, COND(cond), tc));
  }

  /**
   * This routine expands an ATTEMPT instruction into an atomic
   * compare exchange. The atomic compare and exchange will place at
   * mo the value of newValue if the value of mo is oldValue. The
   * result register is set to 0/1 depending on whether the valye was
   * replaced or not.
   *
   * @param result the register operand that is set to 0/1 as a result of the
   *          attempt
   * @param mo the address at which to attempt the exchange
   * @param oldValue the old value at the address mo
   * @param newValue the new value at the address mo
   */
  protected final void ATTEMPT(OPT_RegisterOperand result, OPT_MemoryOperand mo, OPT_Operand oldValue,
                               OPT_Operand newValue) {
    OPT_RegisterOperand temp = regpool.makeTempInt();
    OPT_RegisterOperand temp2 = regpool.makeTemp(result);
    EMIT(MIR_Move.create(IA32_MOV, temp, newValue));
    EMIT(MIR_Move.create(IA32_MOV, new OPT_RegisterOperand(getEAX(), VM_TypeReference.Int), oldValue));
    EMIT(MIR_CompareExchange.create(IA32_LOCK_CMPXCHG,
                                    new OPT_RegisterOperand(getEAX(), VM_TypeReference.Int),
                                    mo,
                                    temp.copyRO()));
    EMIT(MIR_Set.create(IA32_SET__B, temp2, OPT_IA32ConditionOperand.EQ()));
    // need to zero-extend the result of the set
    EMIT(MIR_Unary.create(IA32_MOVZX__B, result, temp2.copy()));
  }

  /**
   * This routine expands an ATTEMPT instruction into an atomic
   * compare exchange. The atomic compare and exchange will place at
   * mo the value of newValue if the value of mo is oldValue. The
   * result register is set to 0/1 depending on whether the valye was
   * replaced or not.
   *
   * @param result the register operand that is set to 0/1 as a result
   * of the attempt
   * @param mo       the address at which to attempt the exchange
   * @param oldValue the old value to check for at the address mo
   * @param newValue the new value to place at the address mo
   */
  protected final void ATTEMPT_LONG(OPT_RegisterOperand result,
		  OPT_MemoryOperand mo,
		  OPT_Operand oldValue,
		  OPT_Operand newValue) {
	  // Set up EDX:EAX with the old value
	  if (oldValue.isRegister()) {
		  OPT_Register oldValue_hval = oldValue.asRegister().register;
		  OPT_Register oldValue_lval = regpool.getSecondReg(oldValue_hval);
		  EMIT(MIR_Move.create(IA32_MOV, new OPT_RegisterOperand(getEDX(), VM_TypeReference.Int),
				  new OPT_RegisterOperand(oldValue_hval, VM_TypeReference.Int)));
		  EMIT(MIR_Move.create(IA32_MOV, new OPT_RegisterOperand(getEAX(), VM_TypeReference.Int),
				  new OPT_RegisterOperand(oldValue_lval, VM_TypeReference.Int)));
	  } else {
		  if (VM.VerifyAssertions) VM._assert(oldValue.isLongConstant());
		  OPT_LongConstantOperand val = oldValue.asLongConstant();
		  EMIT(MIR_Move.create(IA32_MOV, new OPT_RegisterOperand(getEDX(), VM_TypeReference.Int),
				  IC(val.upper32())));
		  EMIT(MIR_Move.create(IA32_MOV, new OPT_RegisterOperand(getEAX(), VM_TypeReference.Int),
				  IC(val.lower32())));
	  }

	  // Set up ECX:EBX with the new value
	  if (newValue.isRegister()) {
		  OPT_Register newValue_hval = newValue.asRegister().register;
		  OPT_Register newValue_lval = regpool.getSecondReg(newValue_hval);
		  EMIT(MIR_Move.create(IA32_MOV, new OPT_RegisterOperand(getECX(), VM_TypeReference.Int),
				  new OPT_RegisterOperand(newValue_hval, VM_TypeReference.Int)));
		  EMIT(MIR_Move.create(IA32_MOV, new OPT_RegisterOperand(getEBX(), VM_TypeReference.Int),
				  new OPT_RegisterOperand(newValue_lval, VM_TypeReference.Int)));
	  } else {
		  if (VM.VerifyAssertions) VM._assert(newValue.isLongConstant());
		  OPT_LongConstantOperand val = newValue.asLongConstant();
		  EMIT(MIR_Move.create(IA32_MOV, new OPT_RegisterOperand(getECX(), VM_TypeReference.Int),
				  IC(val.upper32())));
		  EMIT(MIR_Move.create(IA32_MOV, new OPT_RegisterOperand(getEBX(), VM_TypeReference.Int),
				  IC(val.lower32())));
	  }

	  EMIT(MIR_CompareExchange8B.create(IA32_LOCK_CMPXCHG8B,
			  new OPT_RegisterOperand(getEDX(), VM_TypeReference.Int),
					  new OPT_RegisterOperand(getEAX(), VM_TypeReference.Int),
					  mo,
					  new OPT_RegisterOperand(getECX(), VM_TypeReference.Int),
					  new OPT_RegisterOperand(getEBX(), VM_TypeReference.Int)));
	  OPT_RegisterOperand temp = regpool.makeTemp(result);
	  EMIT(MIR_Set.create(IA32_SET__B, temp, OPT_IA32ConditionOperand.EQ()));
	  // need to zero-extend the result of the set
	  EMIT(MIR_Unary.create(IA32_MOVZX__B, result, temp.copy()));
  }

  /**
   * This routine expands the compound pattern IFCMP(ATTEMPT, ZERO) into an
   * atomic compare/exchange followed by a branch on success/failure of the
   * attempted atomic compare/exchange.
   *
   * @param mo the address at which to attempt the exchange
   * @param oldValue the old value at the address mo
   * @param newValue the new value at the address mo
   * @param cond the condition to branch on
   * @param target the branch target
   * @param bp the branch profile information
   */
  protected final void ATTEMPT_IFCMP(OPT_MemoryOperand mo, OPT_Operand oldValue, OPT_Operand newValue,
                                     OPT_ConditionOperand cond, OPT_BranchOperand target, OPT_BranchProfileOperand bp) {
    OPT_RegisterOperand temp = regpool.makeTempInt();
    EMIT(MIR_Move.create(IA32_MOV, temp, newValue));
    EMIT(MIR_Move.create(IA32_MOV, new OPT_RegisterOperand(getEAX(), VM_TypeReference.Int), oldValue));
    EMIT(MIR_CompareExchange.create(IA32_LOCK_CMPXCHG,
                                    new OPT_RegisterOperand(getEAX(), VM_TypeReference.Int),
                                    mo,
                                    temp.copyRO()));
    EMIT(MIR_CondBranch.create(IA32_JCC, COND(cond), target, bp));
  }

  /*
   * special case handling OSR instructions expand long type variables to two
   * intergers
   */
  void OSR(OPT_BURS burs, OPT_Instruction s) {
    if (VM.VerifyAssertions) {
      VM._assert(OsrPoint.conforms(s));
    }

    // 1. how many params
    int numparam = OsrPoint.getNumberOfElements(s);
    int numlong = 0;
    for (int i = 0; i < numparam; i++) {
      OPT_Operand param = OsrPoint.getElement(s, i);
      if (param.getType().isLongType()) {
        numlong++;
      }
    }

    // 2. collect params
    OPT_InlinedOsrTypeInfoOperand typeInfo = OsrPoint
        .getClearInlinedTypeInfo(s);

    if (VM.VerifyAssertions) {
      if (typeInfo == null) {
        VM.sysWriteln("OsrPoint " + s + " has a <null> type info:");
        VM.sysWriteln("  position :" + s.bcIndex + "@" + s.position.method);
      }
      VM._assert(typeInfo != null);
    }

    OPT_Operand[] params = new OPT_Operand[numparam];
    for (int i = 0; i < numparam; i++) {
      params[i] = OsrPoint.getClearElement(s, i);
    }

    // set the number of valid params in osr type info, used
    // in LinearScan
    typeInfo.validOps = numparam;

    // 3: only makes second half register of long being used
    // creates room for long types.
    burs.append(OsrPoint.mutate(s, s.operator(), typeInfo, numparam + numlong));

    int pidx = numparam;
    for (int i = 0; i < numparam; i++) {
      OPT_Operand param = params[i];
      OsrPoint.setElement(s, i, param);
      if (param instanceof OPT_RegisterOperand) {
        OPT_RegisterOperand rparam = (OPT_RegisterOperand) param;
        // the second half is appended at the end
        // OPT_LinearScan will update the map.
        if (rparam.type.isLongType()) {
          OsrPoint.setElement(s, pidx++, L(burs.ir.regpool
              .getSecondReg(rparam.register)));
        }
      } else if (param instanceof OPT_LongConstantOperand) {
        OPT_LongConstantOperand val = (OPT_LongConstantOperand) param;

        if (VM.TraceOnStackReplacement) {
          VM.sysWriteln("caught a long const " + val);
        }

        OsrPoint.setElement(s, i, IC(val.upper32()));
        OsrPoint.setElement(s, pidx++, IC(val.lower32()));
      } else if (param instanceof OPT_IntConstantOperand) {
      } else {
        throw new OPT_OptimizingCompilerException("OPT_BURS_Helpers", "unexpected parameter type" + param);
      }
    }

    if (pidx != (numparam + numlong)) {
      VM.sysWriteln("pidx = " + pidx);
      VM.sysWriteln("numparam = " + numparam);
      VM.sysWriteln("numlong = " + numlong);
    }

    if (VM.VerifyAssertions) {
      VM._assert(pidx == (numparam + numlong));
    }

    /*
     * if (VM.TraceOnStackReplacement) { VM.sysWriteln("BURS rewrite OsrPoint
     * "+s); VM.sysWriteln(" position "+s.bcIndex+"@"+s.position.method); }
     */
  }
}
