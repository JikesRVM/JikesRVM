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
package org.jikesrvm.compilers.opt.lir2mir.ia32;

import static org.jikesrvm.compilers.opt.ir.Operators.CALL_SAVE_VOLATILE;
import static org.jikesrvm.compilers.opt.ir.Operators.DOUBLE_CMPL;
import static org.jikesrvm.compilers.opt.ir.Operators.FLOAT_CMPL;
import static org.jikesrvm.compilers.opt.ir.Operators.GUARD_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_ADC;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_ADD;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_AND;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_ANDNPD;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_ANDNPS;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_ANDPD;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_ANDPS;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CALL;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CDQ;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CMOV;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CMP;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CMPEQSD;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CMPEQSS;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CMPLESD;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CMPLESS;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CMPLTSD;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CMPLTSS;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CVTSS2SD;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FCMOV;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FCOMI;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FCOMIP;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FFREE;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FILD;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FIST;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FLD;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FLD1;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FLDL2E;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FLDL2T;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FLDLG2;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FLDLN2;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FLDPI;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FLDZ;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FMOV;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FPREM;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FSTP;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_IDIV;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_IMUL2;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_JCC;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_LEA;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_LOCK_CMPXCHG;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_LOCK_CMPXCHG8B;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_METHODSTART;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_MOV;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_MOVD;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_MOVLPD;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_MOVSD;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_MOVSS;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_MOVSX__B;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_MOVZX__B;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_MUL;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_NEG;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_NOT;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_OR;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_ORPD;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_ORPS;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_RCR;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_RDTSC;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_SAR;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_SBB;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_SET__B;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_SHL;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_SHR;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_SUB;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_SYSCALL;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_TRAPIF;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_XOR;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_XORPD;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_XORPS;
import static org.jikesrvm.compilers.opt.ir.Operators.IR_PROLOGUE;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_SHL;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_SHR;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_USHR;
import static org.jikesrvm.compilers.opt.ir.Operators.MIR_LOWTABLESWITCH;

import java.util.Enumeration;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.DefUse;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.Binary;
import org.jikesrvm.compilers.opt.ir.CacheOp;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.CondMove;
import org.jikesrvm.compilers.opt.ir.GuardedBinary;
import org.jikesrvm.compilers.opt.ir.IfCmp;
import org.jikesrvm.compilers.opt.ir.Instruction;
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
import org.jikesrvm.compilers.opt.ir.MIR_Lea;
import org.jikesrvm.compilers.opt.ir.MIR_LowTableSwitch;
import org.jikesrvm.compilers.opt.ir.MIR_Move;
import org.jikesrvm.compilers.opt.ir.MIR_Multiply;
import org.jikesrvm.compilers.opt.ir.MIR_Nullary;
import org.jikesrvm.compilers.opt.ir.MIR_RDTSC;
import org.jikesrvm.compilers.opt.ir.MIR_Set;
import org.jikesrvm.compilers.opt.ir.MIR_TrapIf;
import org.jikesrvm.compilers.opt.ir.MIR_Unary;
import org.jikesrvm.compilers.opt.ir.MIR_UnaryAcc;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.Nullary;
import org.jikesrvm.compilers.opt.ir.Operator;
import org.jikesrvm.compilers.opt.ir.OsrPoint;
import org.jikesrvm.compilers.opt.ir.Prologue;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.TrapIf;
import org.jikesrvm.compilers.opt.ir.Unary;
import org.jikesrvm.compilers.opt.ir.operand.BranchOperand;
import org.jikesrvm.compilers.opt.ir.operand.BranchProfileOperand;
import org.jikesrvm.compilers.opt.ir.operand.ConditionOperand;
import org.jikesrvm.compilers.opt.ir.operand.ConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.DoubleConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.FloatConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.InlinedOsrTypeInfoOperand;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.LocationOperand;
import org.jikesrvm.compilers.opt.ir.operand.LongConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.MemoryOperand;
import org.jikesrvm.compilers.opt.ir.operand.MethodOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.StackLocationOperand;
import org.jikesrvm.compilers.opt.ir.operand.TrapCodeOperand;
import org.jikesrvm.compilers.opt.ir.operand.TrueGuardOperand;
import org.jikesrvm.compilers.opt.ir.operand.ia32.BURSManagedFPROperand;
import org.jikesrvm.compilers.opt.ir.operand.ia32.IA32ConditionOperand;
import org.jikesrvm.compilers.opt.lir2mir.BURS;
import org.jikesrvm.compilers.opt.lir2mir.BURS_MemOp_Helpers;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.jikesrvm.runtime.Statics;
import org.vmmagic.unboxed.Offset;

/**
 * Contains IA32-specific helper functions for BURS.
 */
abstract class BURS_Helpers extends BURS_MemOp_Helpers {
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

  /** Mask to flip sign bits in XMM registers */
  private static final Offset floatSignMask =
    VM.BuildForSSE2Full ?
      Offset.fromIntSignExtend(Statics.findOrCreate16ByteSizeLiteral(0x8000000080000000L, 0x8000000080000000L)) :
      Offset.zero();

  /** Mask to flip sign bits in XMM registers */
  private static final Offset doubleSignMask =
    VM.BuildForSSE2Full ?
      Offset.fromIntSignExtend(Statics.findOrCreate16ByteSizeLiteral(0x8000000000000000L, 0x8000000000000000L)) :
      Offset.zero();

  /** Mask to abs an XMM registers */
  private static final Offset floatAbsMask =
    VM.BuildForSSE2Full ?
      Offset.fromIntSignExtend(Statics.findOrCreate16ByteSizeLiteral(0x7FFFFFFF7FFFFFFFL, 0x7FFFFFFF7FFFFFFFL)) :
      Offset.zero();

  /** Mask to abs an XMM registers */
  private static final Offset doubleAbsMask =
    VM.BuildForSSE2Full ?
      Offset.fromIntSignExtend(Statics.findOrCreate16ByteSizeLiteral(0x7FFFFFFFFFFFFFFFL, 0x7FFFFFFFFFFFFFFFL)) :
      Offset.zero();

  /**
   * When emitting certain rules this holds the condition code state to be
   * consumed by a parent rule
   */
  private ConditionOperand cc;

  /** Constructor */
  BURS_Helpers(BURS burs) {
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
  protected void EMIT_Commutative(Operator operator, Instruction s, Operand result, Operand val1, Operand val2) {
    if(VM.VerifyAssertions) VM._assert(result.isRegister() || result.isMemory());
    // Swap operands to reduce chance of generating a move or to normalize
    // constants into val2
    if (val2.similar(result) || val1.isConstant()) {
      Operand temp = val1;
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
  protected void EMIT_NonCommutative(Operator operator, Instruction s, Operand result, Operand val1, Operand val2) {
    if(VM.VerifyAssertions) VM._assert(result.isRegister() || result.isMemory());
    if (result.similar(val1)) {
      // Straight forward case where instruction is already in accumulate form
      EMIT(MIR_BinaryAcc.mutate(s, operator, result, val2));
    } else if (!result.similar(val2)) {
      // Move first operand to result and perform operator on result, if
      // possible redundant moves should be remove by register allocator
      EMIT(CPOS(s, MIR_Move.create(IA32_MOV, result.copy(), val1)));
      EMIT(MIR_BinaryAcc.mutate(s, operator, result, val2));
    } else {
      // Potential to clobber second operand during move to result. Use a
      // temporary register to perform the operation and rely on register
      // allocator to remove redundant moves
      RegisterOperand temp = regpool.makeTemp(result);
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
  protected void EMIT_Unary(Operator operator, Instruction s, Operand result, Operand value) {
    if(VM.VerifyAssertions) VM._assert(result.isRegister() || result.isMemory());
    // Do we need to move prior to the operator - result = val1
    if (!result.similar(value)) {
      EMIT(CPOS(s, MIR_Move.create(IA32_MOV, result.copy(), value)));
    }
    EMIT(MIR_UnaryAcc.mutate(s, operator, result));
  }

  /**
   * Create the MIR LEA instruction performing a few simplifications if possible
   * @param s the instruction being replaced
   * @param result the destination register
   * @param mo the memory operand
   */
  protected void EMIT_Lea(Instruction s, RegisterOperand result, MemoryOperand mo) {
    // A memory operand is: base + scaled index + displacement
    if ((mo.index == null) && mo.disp.isZero()) {
      if (VM.VerifyAssertions) VM._assert(mo.scale == 0 && mo.base != null);
      // If there is no index or displacement emit a move
      EMIT(MIR_Move.mutate(s, IA32_MOV, result, mo.base));
    } else if ((mo.index == null) && result.similar(mo.base)) {
      if (VM.VerifyAssertions) VM._assert(mo.scale == 0);
      // If there is no index and we're redefining the same register, emit an add
      EMIT(MIR_BinaryAcc.mutate(s, IA32_ADD, result, IC(mo.disp.toInt())));
    } else {
      // Lea is simplest form
      EMIT(MIR_Lea.mutate(s, IA32_LEA, result, mo));
    }
  }

  /**
   * Convert the given comparison with a boolean (int) value into a condition
   * suitable for the carry flag
   * @param x the value 1 (true) or 0 (false)
   * @param cond either equal or not equal
   * @return lower or higher equal
   */
  protected static ConditionOperand BIT_TEST(int x, ConditionOperand cond) {
    if (VM.VerifyAssertions) VM._assert((x==0)||(x==1));
    if (VM.VerifyAssertions) VM._assert(EQ_NE(cond));
    if ((x == 1 && cond.isEQUAL())||
        (x == 0 && cond.isNOT_EQUAL())) {
      return ConditionOperand.LOWER();
    } else {
      return ConditionOperand.HIGHER_EQUAL();
    }
  }

  /**
   * Follow a chain of Move operations filtering back to a def
   *
   * @param use the place to start from
   * @return the operand at the start of the chain
   */
  protected static Operand follow(Operand use) {
    if (!use.isRegister()) {
      return use;
    } else {
      RegisterOperand rop = use.asRegister();
      Enumeration<RegisterOperand> defs = DefUse.defs(rop.getRegister());
      if (!defs.hasMoreElements()) {
        return use;
      } else {
        Operand def = defs.nextElement();
        if (defs.hasMoreElements()) {
          return def;
        } else {
          Instruction instr = def.instruction;
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
  protected final void pushCOND(ConditionOperand c) {
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
  protected final ConditionOperand consumeCOND() {
    ConditionOperand ans = cc;
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
  protected final int LEA_SHIFT(Operand op, int trueCost) {
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
  protected final int LEA_SHIFT(Operand op, int trueCost, int falseCost) {
    if (op.isIntConstant()) {
      int val = IV(op);
      if (val >= 0 && val <= 3) {
        return trueCost;
      }
    }
    return falseCost;
  }

  protected final byte LEA_SHIFT(Operand op) {
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
        throw new OptimizingCompilerException("bad val for LEA shift " + op);
    }
  }

  /**
   * Is the given instruction's constant operand a x87 floating point constant
   *
   * @param s the instruction to examine
   * @param trueCost the cost if this is a valid constant
   * @return trueCost or INFINITE depending on the given constant
   */
  protected final int is387_FPC(Instruction s, int trueCost) {
    Operand val = Binary.getVal2(s);
    if (val instanceof FloatConstantOperand) {
      FloatConstantOperand fc = (FloatConstantOperand) val;
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
      DoubleConstantOperand dc = (DoubleConstantOperand) val;
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

  protected final Operator get387_FPC(Instruction s) {
    Operand val = Binary.getVal2(s);
    if (val instanceof FloatConstantOperand) {
      FloatConstantOperand fc = (FloatConstantOperand) val;
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
      DoubleConstantOperand dc = (DoubleConstantOperand) val;
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
    throw new OptimizingCompilerException("BURS_Helpers", "unexpected 387 constant " + val);
  }

  /** Can the given condition for a compare be converted to a test? */
  protected final boolean CMP_TO_TEST(ConditionOperand op) {
    switch(op.value) {
    case ConditionOperand.EQUAL:
    case ConditionOperand.NOT_EQUAL:
    case ConditionOperand.LESS:
    case ConditionOperand.GREATER_EQUAL:
    case ConditionOperand.GREATER:
    case ConditionOperand.LESS_EQUAL:
      return true;
    default:
      return false;
    }
  }

  protected final IA32ConditionOperand COND(ConditionOperand op) {
    return new IA32ConditionOperand(op);
  }

  // Get particular physical registers
  protected final Register getEAX() {
    return getIR().regpool.getPhysicalRegisterSet().getEAX();
  }

  protected final Register getECX() {
    return getIR().regpool.getPhysicalRegisterSet().getECX();
  }

  protected final Register getEDX() {
    return getIR().regpool.getPhysicalRegisterSet().getEDX();
  }

  protected final Register getEBX() {
    return getIR().regpool.getPhysicalRegisterSet().getEBX();
  }

  protected final Register getESP() {
    return getIR().regpool.getPhysicalRegisterSet().getESP();
  }

  protected final Register getEBP() {
    return getIR().regpool.getPhysicalRegisterSet().getEBP();
  }

  protected final Register getESI() {
    return getIR().regpool.getPhysicalRegisterSet().getESI();
  }

  protected final Register getEDI() {
    return getIR().regpool.getPhysicalRegisterSet().getEDI();
  }

  protected final Register getFPR(int n) {
    return getIR().regpool.getPhysicalRegisterSet().getFPR(n);
  }

  protected final Operand myFP0() {
    return new BURSManagedFPROperand(0);
  }

  protected final Operand myFP1() {
    return new BURSManagedFPROperand(1);
  }

  protected final Register getST0() {
    return getIR().regpool.getPhysicalRegisterSet().getST0();
  }

  /**
   * Move op into a register operand if it isn't one already.
   */
  private Operand asReg(Instruction s, Operator movop, Operand op) {
    if (op.isRegister()) {
      return op;
    }
    RegisterOperand tmp = regpool.makeTemp(op);
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
  protected final MemoryOperand setSize(MemoryOperand mo, int size) {
    mo.size = (byte) size;
    return mo;
  }

  /**
   * Create a slot on the stack in memory for a conversion
   *
   * @param size for memory operand
   * @return memory operand of slot in stack
   */
  protected final Operand MO_CONV(byte size) {
    int offset = -burs.ir.stackManager.allocateSpaceForConversion();
    return new StackLocationOperand(true, offset, size);
  }

  /**
   * Create a 64bit slot on the stack in memory for a conversion and store the
   * given long
   */
  protected final void STORE_LONG_FOR_CONV(Operand op) {
    int offset = -burs.ir.stackManager.allocateSpaceForConversion();
    if (op instanceof RegisterOperand) {
      RegisterOperand hval = (RegisterOperand) op;
      RegisterOperand lval = new RegisterOperand(regpool.getSecondReg(hval.getRegister()),
          TypeReference.Int);
      EMIT(MIR_Move.create(IA32_MOV, new StackLocationOperand(true, offset + 4, DW), hval));
      EMIT(MIR_Move.create(IA32_MOV, new StackLocationOperand(true, offset, DW), lval));
    } else {
      LongConstantOperand val = LC(op);
      EMIT(MIR_Move.create(IA32_MOV, new StackLocationOperand(true, offset + 4, DW), IC(val.upper32())));
      EMIT(MIR_Move.create(IA32_MOV, new StackLocationOperand(true, offset, DW), IC(val.lower32())));
    }
  }

  /**
   * Create memory operand to load from a given jtoc offset
   *
   * @param offset location in JTOC
   * @param size of value in JTOC
   * @return created memory operand
   */
  static MemoryOperand loadFromJTOC(Offset offset, byte size) {
    LocationOperand loc = new LocationOperand(offset);
    Operand guard = TG();
    return MemoryOperand.D(Magic.getTocPointer().plus(offset), size, loc, guard);
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
  protected final void GET_EXCEPTION_OBJECT(Instruction s) {
    int offset = -burs.ir.stackManager.allocateSpaceForCaughtException();
    StackLocationOperand sl = new StackLocationOperand(true, offset, DW);
    EMIT(MIR_Move.mutate(s, IA32_MOV, Nullary.getResult(s), sl));
  }

  /**
   * Emit code to move a value in a register to the stack location where a
   * caught exception object is expected to be.
   *
   * @param s the instruction to expand
   */
  protected final void SET_EXCEPTION_OBJECT(Instruction s) {
    int offset = -burs.ir.stackManager.allocateSpaceForCaughtException();
    StackLocationOperand sl = new StackLocationOperand(true, offset, DW);
    Operand val = (Operand) CacheOp.getRef(s);
    if (val.isRegister()) {
        EMIT(MIR_Move.mutate(s, IA32_MOV, sl, val));
    } else if (val.isIntConstant()) {
        RegisterOperand temp = regpool.makeTempInt();
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV, temp, val)));
        val = temp.copyRO(); // for opt compiler var usage info?
        EMIT(MIR_Move.mutate(s, IA32_MOV, sl, temp));
    } else {
        throw new OptimizingCompilerException("BURS_Helpers",
                "unexpected operand type "+val+" in SET_EXCEPTION_OBJECT");
    }
  }

  /**
   * Expansion of INT_2LONG
   *
   * @param s the instruction to expand
   * @param result the result operand
   * @param value the second operand
   * @param signExtend should the value be sign or zero extended?
   */
  protected final void INT_2LONG(Instruction s, RegisterOperand result,
Operand value, boolean signExtend) {
    Register hr = result.getRegister();
    Register lr = regpool.getSecondReg(hr);
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV, new RegisterOperand(lr, TypeReference.Int), value)));
    if (signExtend) {
      EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
          new RegisterOperand(hr, TypeReference.Int),
          new RegisterOperand(lr, TypeReference.Int))));
      EMIT(MIR_BinaryAcc.mutate(s,IA32_SAR,
          new RegisterOperand(hr, TypeReference.Int),
          IC(31)));
    } else {
      EMIT(MIR_Move.mutate(s, IA32_MOV,
          new RegisterOperand(hr, TypeReference.Int),
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
  protected final void FPR_2INT(Instruction s, RegisterOperand result, Operand value) {
    MemoryOperand M;

    // Step 1: Get value to be converted into myFP0
    // and in 'strict' IEEE mode.
    if (value instanceof MemoryOperand) {
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
      if (value instanceof BURSManagedFPROperand) {
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
    Register one = regpool.getInteger();
    Register isPositive = regpool.getInteger();
    Register isNegative = regpool.getInteger();
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV, new RegisterOperand(one, TypeReference.Int), IC(1))));
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV, new RegisterOperand(isPositive, TypeReference.Int), IC(0))));
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV, new RegisterOperand(isNegative, TypeReference.Int), IC(0))));
    EMIT(CPOS(s, MIR_Nullary.create(IA32_FLDZ, myFP0())));
    // FP Stack: myFP0 = 0.0; myFP1 = value
    EMIT(CPOS(s, MIR_Compare.create(IA32_FCOMIP, myFP0(), myFP1())));
    // FP Stack: myFP0 = value
    EMIT(CPOS(s, MIR_CondMove.create(IA32_CMOV,
                             new RegisterOperand(isPositive, TypeReference.Int),
                             new RegisterOperand(one, TypeReference.Int),
                             IA32ConditionOperand.LLT())));
    EMIT(CPOS(s, MIR_CondMove.create(IA32_CMOV,
                             new RegisterOperand(isNegative, TypeReference.Int),
                             new RegisterOperand(one, TypeReference.Int),
                             IA32ConditionOperand.LGT())));

    EMIT(CPOS(s, MIR_Move.create(IA32_FILD, myFP0(), MO_CONV(DW))));
    // FP Stack: myFP0 = round(value), myFP1 = value

    // addee = 1 iff round(x) < x
    // subtractee = 1 iff round(x) > x
    Register addee = regpool.getInteger();
    Register subtractee = regpool.getInteger();
    EMIT(CPOS(s, MIR_Compare.create(IA32_FCOMIP, myFP0(), myFP1())));
    // FP Stack: myFP0 = value
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV, new RegisterOperand(addee, TypeReference.Int), IC(0))));
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV, new RegisterOperand(subtractee, TypeReference.Int), IC(0))));
    EMIT(CPOS(s, MIR_CondMove.create(IA32_CMOV,
                             new RegisterOperand(addee, TypeReference.Int),
                             new RegisterOperand(one, TypeReference.Int),
                             IA32ConditionOperand.LLT())));
    EMIT(CPOS(s, MIR_CondMove.create(IA32_CMOV,
                             new RegisterOperand(subtractee, TypeReference.Int),
                             new RegisterOperand(one, TypeReference.Int),
                             IA32ConditionOperand.LGT())));

    // Now a little tricky part.
    // We will add 1 iff isNegative and x > round(x)
    // We will subtract 1 iff isPositive and x < round(x)
    EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_AND,
                              new RegisterOperand(addee, TypeReference.Int),
                              new RegisterOperand(isNegative, TypeReference.Int))));
    EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_AND,
                              new RegisterOperand(subtractee, TypeReference.Int),
                              new RegisterOperand(isPositive, TypeReference.Int))));
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV, result.copy(), MO_CONV(DW))));
    EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_ADD, result.copy(), new RegisterOperand(addee, TypeReference.Int))));
    EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SUB, result.copy(), new RegisterOperand(subtractee, TypeReference.Int))));

    // Compare myFP0 with (double)Integer.MAX_VALUE
    M = MemoryOperand.D(Magic.getTocPointer().plus(Entrypoints.maxintField.getOffset()), QW, null, null);
    EMIT(CPOS(s, MIR_Move.create(IA32_FLD, myFP0(), M)));
    // FP Stack: myFP0 = (double)Integer.MAX_VALUE; myFP1 = value
    EMIT(CPOS(s, MIR_Compare.create(IA32_FCOMIP, myFP0(), myFP1())));
    // FP Stack: myFP0 = value
    // If MAX_VALUE < value, then result := MAX_INT
    Register maxInt = regpool.getInteger();
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV, new RegisterOperand(maxInt, TypeReference.Int), IC(Integer.MAX_VALUE))));
    EMIT(CPOS(s, MIR_CondMove.create(IA32_CMOV,
                             result.copy(),
                             new RegisterOperand(maxInt, TypeReference.Int),
                             IA32ConditionOperand.LLT())));

    // Compare myFP0 with (double)Integer.MIN_VALUE
    M = MemoryOperand.D(Magic.getTocPointer().plus(Entrypoints.minintField.getOffset()), QW, null, null);
    EMIT(CPOS(s, MIR_Move.create(IA32_FLD, myFP0(), M)));
    // FP Stack: myFP0 = (double)Integer.MIN_VALUE; myFP1 = value
    EMIT(CPOS(s, MIR_Compare.create(IA32_FCOMIP, myFP0(), myFP1())));
    // FP Stack: myFP0 = value
    // If MIN_VALUE > value, then result := MIN_INT
    Register minInt = regpool.getInteger();
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV, new RegisterOperand(minInt, TypeReference.Int), IC(Integer.MIN_VALUE))));
    EMIT(CPOS(s, MIR_CondMove.create(IA32_CMOV,
                             result.copy(),
                             new RegisterOperand(minInt, TypeReference.Int),
                             IA32ConditionOperand.LGT())));

    // Set condition flags: set PE iff myFP0 is a NaN
    EMIT(CPOS(s, MIR_Compare.create(IA32_FCOMIP, myFP0(), myFP0())));
    // FP Stack: back to original level (all BURS managed slots freed)
    // If FP0 was classified as a NaN, then result := 0
    Register zero = regpool.getInteger();
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV, new RegisterOperand(zero, TypeReference.Int), IC(0))));
    EMIT(CPOS(s, MIR_CondMove.create(IA32_CMOV,
                             result.copy(),
                             new RegisterOperand(zero, TypeReference.Int),
                             IA32ConditionOperand.PE())));
  }

  /**
   * Emit code to move 64 bits from FPRs to GPRs
   */
  protected final void FPR2GPR_64(Instruction s) {
    int offset = -burs.ir.stackManager.allocateSpaceForConversion();
    StackLocationOperand sl = new StackLocationOperand(true, offset, QW);
    StackLocationOperand sl1 = new StackLocationOperand(true, offset + 4, DW);
    StackLocationOperand sl2 = new StackLocationOperand(true, offset, DW);
    EMIT(CPOS(s, MIR_Move.create(IA32_FMOV, sl, Unary.getVal(s))));
    RegisterOperand i1 = Unary.getResult(s);
    RegisterOperand i2 = new RegisterOperand(regpool
        .getSecondReg(i1.getRegister()), TypeReference.Int);
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV, i1, sl1)));
    EMIT(MIR_Move.mutate(s, IA32_MOV, i2, sl2));
  }

  /**
   * Emit code to move 64 bits from GPRs to FPRs
   */
  protected final void GPR2FPR_64(Instruction s) {
    int offset = -burs.ir.stackManager.allocateSpaceForConversion();
    StackLocationOperand sl = new StackLocationOperand(true, offset, QW);
    StackLocationOperand sl1 = new StackLocationOperand(true, offset + 4, DW);
    StackLocationOperand sl2 = new StackLocationOperand(true, offset, DW);
    Operand i1, i2;
    Operand val = Unary.getVal(s);
    if (val instanceof RegisterOperand) {
      RegisterOperand rval = (RegisterOperand) val;
      i1 = val;
      i2 = new RegisterOperand(regpool.getSecondReg(rval.getRegister()), TypeReference.Int);
    } else {
      LongConstantOperand rhs = (LongConstantOperand) val;
      i1 = IC(rhs.upper32());
      i2 = IC(rhs.lower32());
    }
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV, sl1, i1)));
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV, sl2, i2)));
    EMIT(MIR_Move.mutate(s, IA32_FMOV, Unary.getResult(s), sl));
  }

  /**
   * Returns the appropriate move operator based on the type of operand.
   */
  protected final Operator SSE2_MOVE(Operand o) {
    return o.isFloat() ? IA32_MOVSS : IA32_MOVSD;
  }

  /**
   * Returns the size based on the type of operand.
   */
  protected final byte SSE2_SIZE(Operand o) {
    return o.isFloat() ? DW : QW;
  }

  /**
   * Performs a long -> double/float conversion using x87 and marshalls back to XMMs.
   */
  protected final void SSE2_X87_FROMLONG(Instruction s) {
    Operand result = Unary.getResult(s);
    STORE_LONG_FOR_CONV(Unary.getVal(s));
    // conversion space allocated, contains the long to load.
    int offset = -burs.ir.stackManager.allocateSpaceForConversion();
    StackLocationOperand sl = new StackLocationOperand(true, offset, SSE2_SIZE(result));
    RegisterOperand st0 = new RegisterOperand(getST0(), result.getType());
    EMIT(CPOS(s, MIR_Move.create(IA32_FILD, st0, sl)));
    EMIT(CPOS(s, MIR_Move.create(IA32_FSTP, sl.copy(), st0.copyD2U())));
    EMIT(CPOS(s, MIR_Move.mutate(s, SSE2_MOVE(result), result, sl.copy())));
  }

  /**
   * Performs a long -> double/float conversion using x87 and marshalls between to XMMs.
   */
  protected final void SSE2_X87_REM(Instruction s) {
    Operand result = Binary.getClearResult(s);
    RegisterOperand st0 = new RegisterOperand(getST0(), result.getType());
    int offset = -burs.ir.stackManager.allocateSpaceForConversion();
    StackLocationOperand sl = new StackLocationOperand(true, offset, SSE2_SIZE(result));
    EMIT(CPOS(s, MIR_Move.create(SSE2_MOVE(result), sl, Binary.getVal2(s))));
    EMIT(CPOS(s, MIR_Move.create(IA32_FLD, st0, sl.copy())));
    EMIT(CPOS(s, MIR_Move.create(SSE2_MOVE(result), sl.copy(), Binary.getVal1(s))));
    EMIT(CPOS(s, MIR_Move.create(IA32_FLD, st0.copy(), sl.copy())));
    // The parameters to FPREM actually get ignored (implied ST0/ST1)
    EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_FPREM, st0.copy(), st0.copy())));
    EMIT(CPOS(s, MIR_Move.create(IA32_FSTP, sl.copy(), st0.copy())));
    EMIT(CPOS(s, MIR_Nullary.create(IA32_FFREE, st0.copy())));
    EMIT(MIR_Move.mutate(s, SSE2_MOVE(result), result, sl.copy()));
  }

  /**
   * Emit code to move 64 bits from SSE2 FPRs to GPRs
   */
  protected final void SSE2_FPR2GPR_64(Instruction s) {
    int offset = -burs.ir.stackManager.allocateSpaceForConversion();
    StackLocationOperand sl = new StackLocationOperand(true, offset, QW);
    StackLocationOperand sl1 = new StackLocationOperand(true, offset + 4, DW);
    StackLocationOperand sl2 = new StackLocationOperand(true, offset, DW);
    EMIT(CPOS(s, MIR_Move.create(IA32_MOVLPD, sl, Unary.getVal(s))));
    RegisterOperand i1 = Unary.getResult(s);
    RegisterOperand i2 = new RegisterOperand(regpool
        .getSecondReg(i1.getRegister()), TypeReference.Int);
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV, i1, sl1)));
    EMIT(MIR_Move.mutate(s, IA32_MOV, i2, sl2));
  }

  /**
   * Emit code to move 64 bits from GPRs to SSE2 FPRs
   */
  protected final void SSE2_GPR2FPR_64(Instruction s) {
    int offset = -burs.ir.stackManager.allocateSpaceForConversion();
    StackLocationOperand sl = new StackLocationOperand(true, offset, QW);
    StackLocationOperand sl1 = new StackLocationOperand(true, offset + 4, DW);
    StackLocationOperand sl2 = new StackLocationOperand(true, offset, DW);
    Operand i1, i2;
    Operand val = Unary.getVal(s);
    if (val instanceof RegisterOperand) {
      RegisterOperand rval = (RegisterOperand) val;
      i1 = val;
      i2 = new RegisterOperand(regpool.getSecondReg(rval.getRegister()), TypeReference.Int);
    } else {
      LongConstantOperand rhs = (LongConstantOperand) val;
      i1 = IC(rhs.upper32());
      i2 = IC(rhs.lower32());
    }
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV, sl1, i1)));
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV, sl2, i2)));
    EMIT(MIR_Move.mutate(s, IA32_MOVLPD, Unary.getResult(s), sl));
  }

  /**
   * Emit code to move 32 bits from FPRs to GPRs
   */
  protected final void SSE2_FPR2GPR_32(Instruction s) {
    EMIT(MIR_Move.mutate(s, IA32_MOVD, Unary.getResult(s), Unary.getVal(s)));
//    int offset = -burs.ir.stackManager.allocateSpaceForConversion();
//    StackLocationOperand sl = new StackLocationOperand(true, offset, DW);
//    EMIT(CPOS(s, MIR_Move.create(IA32_MOVSS, sl, Unary.getVal(s))));
//    EMIT(MIR_Move.mutate(s, IA32_MOV, Unary.getResult(s), sl.copy()));
  }

  /**
   * Emit code to move 32 bits from GPRs to FPRs
   */
  protected final void SSE2_GPR2FPR_32(Instruction s) {
    EMIT(MIR_Move.mutate(s, IA32_MOVD, Unary.getResult(s), Unary.getVal(s)));
//    int offset = -burs.ir.stackManager.allocateSpaceForConversion();
//    StackLocationOperand sl = new StackLocationOperand(true, offset, DW);
//    EMIT(CPOS(s, MIR_Move.create(IA32_MOV, sl, Unary.getVal(s))));
//    EMIT(MIR_Move.mutate(s, IA32_MOVSS, Unary.getResult(s), sl.copy()));
  }

  /**
   * BURS expansion of a commutative SSE2 operation.
   */
  protected void SSE2_COP(Operator operator, Instruction s, Operand result, Operand val1, Operand val2) {
    if(VM.VerifyAssertions) VM._assert(result.isRegister());
    // Swap operands to reduce chance of generating a move or to normalize
    // constants into val2
    if (val2.similar(result)) {
      Operand temp = val1;
      val1 = val2;
      val2 = temp;
    }
    // Do we need to move prior to the operator - result = val1
    if (!result.similar(val1)) {
      EMIT(CPOS(s, MIR_Move.create(SSE2_MOVE(result), result.copy(), val1)));
    }
    EMIT(MIR_BinaryAcc.mutate(s, operator, result, val2));
  }

  /**
   * BURS expansion of a non commutative SSE2 operation.
   */
  protected void SSE2_NCOP(Operator operator, Instruction s, Operand result, Operand val1, Operand val2) {
    if(VM.VerifyAssertions) VM._assert(result.isRegister());
    if (result.similar(val1)) {
      // Straight forward case where instruction is already in accumulate form
      EMIT(MIR_BinaryAcc.mutate(s, operator, result, val2));
    } else if (!result.similar(val2)) {
      // Move first operand to result and perform operator on result, if
      // possible redundant moves should be remove by register allocator
      EMIT(CPOS(s, MIR_Move.create(SSE2_MOVE(result), result.copy(), val1)));
      EMIT(MIR_BinaryAcc.mutate(s, operator, result, val2));
    } else {
      // Potential to clobber second operand during move to result. Use a
      // temporary register to perform the operation and rely on register
      // allocator to remove redundant moves
      RegisterOperand temp = regpool.makeTemp(result);
      EMIT(CPOS(s, MIR_Move.create(SSE2_MOVE(result), temp, val1)));
      EMIT(MIR_BinaryAcc.mutate(s, operator, temp.copyRO(), val2));
      EMIT(CPOS(s, MIR_Move.create(SSE2_MOVE(result), result, temp.copyRO())));
    }
  }

  /**
   * Expansion of SSE2 negation ops
   */
  protected final void SSE2_NEG(boolean single, Instruction s, Operand result, Operand value) {
    if(VM.VerifyAssertions) VM._assert(result.isRegister());
    if (!result.similar(value)) {
      EMIT(CPOS(s, MIR_Move.create(single ? IA32_MOVSS : IA32_MOVSD, result.copy(), value)));
    }
    Offset signMaskOffset = single ? floatSignMask : doubleSignMask;
    EMIT(MIR_BinaryAcc.mutate(s, single ? IA32_XORPS : IA32_XORPD, result,
        MemoryOperand.D(Magic.getTocPointer().plus(signMaskOffset), PARAGRAPH,
            new LocationOperand(signMaskOffset), TG())));
  }

  /**
   * Expansion of SSE2 conversions double <-> float
   */
  protected final void SSE2_CONV(Operator op, Instruction s, Operand result, Operand value) {
    if(VM.VerifyAssertions) VM._assert(result.isRegister());
    EMIT(MIR_Unary.mutate(s, op, result, value));
  }

  /**
   * Expansion of SSE2 comparison operations
   */
  protected final void SSE2_IFCMP(Operator op, Instruction s, Operand val1, Operand val2) {
    EMIT(CPOS(s, MIR_Compare.create(op, val1, val2)));
    EMIT(s); // ComplexLIR2MIRExpansion will handle rest of the work.
  }

  protected static Operator SSE2_CMP_OP(ConditionOperand cond, boolean single) {
    switch(cond.value) {
    case ConditionOperand.CMPL_EQUAL:
      return single ? IA32_CMPEQSS : IA32_CMPEQSD;
    case ConditionOperand.CMPG_LESS:
      return single ? IA32_CMPLTSS : IA32_CMPLTSD;
    case ConditionOperand.CMPG_LESS_EQUAL:
      return single ? IA32_CMPLESS : IA32_CMPLESD;
    default:
      return null;
    }
  }

  protected final void SSE2_FCMP_FCMOV(Instruction s, RegisterOperand result, Operand lhsCmp, Operand rhsCmp,
      ConditionOperand cond, Operand trueValue, Operand falseValue) {
    final boolean singleResult = result.isFloat();
    final boolean singleCmp = lhsCmp.isFloat();

    // TODO: support for the MAXSS/MAXSD instructions taking care of NaN cases
    // find cmpOperator flipping code or operands as necessary
    Operator cmpOperator=SSE2_CMP_OP(cond, singleCmp);
    boolean needFlipOperands = false;
    boolean needFlipCode = false;
    if (cmpOperator == null) {
      needFlipOperands = !needFlipOperands;
      cmpOperator = SSE2_CMP_OP(cond.flipOperands(), singleCmp);
      if (cmpOperator == null) {
        needFlipCode = !needFlipCode;
        cmpOperator = SSE2_CMP_OP(cond.flipCode(), singleCmp);
        if (cmpOperator == null) {
          needFlipOperands = !needFlipOperands;
          cmpOperator = SSE2_CMP_OP(cond.flipOperands(), singleCmp);
          if (VM.VerifyAssertions) VM._assert(cmpOperator != null);
        }
      }
    }
    if (needFlipOperands) {
      Operand temp = lhsCmp;
      lhsCmp = rhsCmp;
      rhsCmp = temp;
    }
    if (needFlipCode) {
      Operand temp = falseValue;
      falseValue = trueValue;
      trueValue = temp;
    }
    // place true value in a temporary register to be used for generation of result
    RegisterOperand temp = regpool.makeTemp(result);
    EMIT(CPOS(s, MIR_Move.create(singleResult ? IA32_MOVSS : IA32_MOVSD, temp, trueValue)));
    // do compare ensuring size is >= size of result
    if (!singleResult && singleCmp) {
      RegisterOperand temp2 = regpool.makeTemp(result);
      EMIT(CPOS(s, MIR_Unary.create(IA32_CVTSS2SD, temp2, rhsCmp)));
      EMIT(CPOS(s, MIR_Unary.create(IA32_CVTSS2SD, result.copyRO(), lhsCmp)));
      rhsCmp = temp2;
      cmpOperator = SSE2_CMP_OP(cond, false);
    } else {
      if (!result.similar(lhsCmp)) {
        EMIT(CPOS(s, MIR_Move.create(singleResult ? IA32_MOVSS : IA32_MOVSD, result.copyRO(), lhsCmp)));
      }
    }
    EMIT(MIR_BinaryAcc.mutate(s, cmpOperator, result, rhsCmp));
    // result contains all 1s or 0s, use masks and OR to perform conditional move
    EMIT(CPOS(s, MIR_BinaryAcc.create(singleResult ? IA32_ANDPS : IA32_ANDPD, temp.copyRO(), result.copyRO())));
    EMIT(CPOS(s, MIR_BinaryAcc.create(singleResult ? IA32_ANDNPS : IA32_ANDNPD, result.copyRO(), falseValue)));
    EMIT(CPOS(s, MIR_BinaryAcc.create(singleResult ? IA32_ORPS : IA32_ORPD, result.copyRO(), temp.copyRO())));
  }

  protected final boolean IS_MATERIALIZE_ZERO(Instruction s) {
    Operand val = Binary.getVal2(s); // float or double value
    return (val.isFloatConstant() && Float.floatToRawIntBits(val.asFloatConstant().value) == 0) ||
           (val.isDoubleConstant() && Double.doubleToRawLongBits(val.asDoubleConstant().value) == 0L);
  }

  protected final boolean SIMILAR_REGISTERS(Operand... ops) {
    Operand last = null;
    for (Operand op : ops) {
      if (!op.isRegister() || (last != null && !op.similar(last))) {
        return false;
      }
      last = op;
    }
    return true;
  }

  protected final boolean SSE2_IS_GT_OR_GE(ConditionOperand cond) {
    switch(cond.value) {
    case ConditionOperand.CMPG_GREATER:
    case ConditionOperand.CMPG_GREATER_EQUAL:
    case ConditionOperand.CMPL_GREATER:
    case ConditionOperand.CMPL_GREATER_EQUAL:
      return true;
    }
    return false;
  }

  protected final boolean SSE2_IS_LT_OR_LE(ConditionOperand cond) {
    switch(cond.value) {
    case ConditionOperand.CMPG_LESS:
    case ConditionOperand.CMPG_LESS_EQUAL:
    case ConditionOperand.CMPL_LESS:
    case ConditionOperand.CMPL_LESS_EQUAL:
      return true;
    }
    return false;
  }

  protected final void SSE2_ABS(boolean single, Instruction s, Operand result, Operand value) {
    if(VM.VerifyAssertions) VM._assert(result.isRegister());
    if (!result.similar(value)) {
      EMIT(CPOS(s, MIR_Move.create(single ? IA32_MOVSS : IA32_MOVSD, result.copy(), value)));
    }
    Offset absMaskOffset = single ? floatAbsMask : doubleAbsMask;
    EMIT(MIR_BinaryAcc.mutate(s, single ? IA32_ANDPS : IA32_ANDPD, result,
        MemoryOperand.D(Magic.getTocPointer().plus(absMaskOffset), PARAGRAPH,
            new LocationOperand(absMaskOffset), TG())));
  }

  /**
   * Expansion of SSE2 floating point constant loads
   */
  protected final void SSE2_FPCONSTANT(Instruction s) {
    RegisterOperand res = Binary.getResult(s);
    Operand val = Binary.getVal2(s); // float or double value
    if (val.isFloatConstant() && Float.floatToRawIntBits(val.asFloatConstant().value) == 0) {
      EMIT(MIR_BinaryAcc.mutate(s, IA32_XORPS, res, res.copyRO()));
    } else if (val.isDoubleConstant() && Double.doubleToRawLongBits(val.asDoubleConstant().value) == 0L) {
      EMIT(MIR_BinaryAcc.mutate(s, IA32_XORPD, res, res.copyRO()));
    }else {
      EMIT(MIR_Move.mutate(s, SSE2_MOVE(res), res, MO_MC(s)));
    }
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
  protected final void INT_DIVIDES(Instruction s, RegisterOperand result, Operand val1, Operand val2,
                                   boolean isDiv) {
    if (val1.isIntConstant()) {
      int value = val1.asIntConstant().value;
      if (value < 0) {
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV, new RegisterOperand(getEDX(), TypeReference.Int), IC(-1))));
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV, new RegisterOperand(getEAX(), TypeReference.Int), val1)));
      } else {
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV, new RegisterOperand(getEDX(), TypeReference.Int), IC(0))));
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV, new RegisterOperand(getEAX(), TypeReference.Int), val1)));
      }
    } else {
      EMIT(CPOS(s, MIR_Move.create(IA32_MOV, new RegisterOperand(getEAX(), TypeReference.Int), val1)));
      EMIT(CPOS(s, MIR_ConvertDW2QW.create(IA32_CDQ,
                                   new RegisterOperand(getEDX(), TypeReference.Int),
                                   new RegisterOperand(getEAX(), TypeReference.Int))));
    }
    if (val2.isIntConstant()) {
      RegisterOperand temp = regpool.makeTempInt();
      EMIT(CPOS(s, MIR_Move.create(IA32_MOV, temp, val2)));
      val2 = temp.copyRO();
    }
    EMIT(MIR_Divide.mutate(s,
                           IA32_IDIV,
                           new RegisterOperand(getEDX(), TypeReference.Int),
                           new RegisterOperand(getEAX(), TypeReference.Int),
                           val2,
                           GuardedBinary.getGuard(s)));
    if (isDiv) {
      EMIT(CPOS(s, MIR_Move.create(IA32_MOV, result.copyD2D(), new RegisterOperand(getEAX(), TypeReference.Int))));
    } else {
      EMIT(CPOS(s, MIR_Move.create(IA32_MOV, result.copyD2D(), new RegisterOperand(getEDX(), TypeReference.Int))));
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
  protected final void LONG_ADD(Instruction s, RegisterOperand result,
      Operand value1, Operand value2) {
    // The value of value1 should be identical to result, to avoid moves, and a
    // register in the case of addition with a constant
    if ((value2.similar(result)) || value1.isLongConstant()) {
      Operand temp = value1;
      value1 = value2;
      value2 = temp;
    }
    Register lhsReg = result.getRegister();
    Register lowlhsReg = regpool.getSecondReg(lhsReg);
    if (value1.isRegister() && value2.isRegister()) {
      Register rhsReg1 = ((RegisterOperand) value1).getRegister();
      Register lowrhsReg1 = regpool.getSecondReg(rhsReg1);
      Register rhsReg2 = ((RegisterOperand) value2).getRegister();
      Register lowrhsReg2 = regpool.getSecondReg(rhsReg2);
      // Do we need to move prior to the add - result = value1
      if (!value1.similar(result)) {
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new RegisterOperand(lowlhsReg, TypeReference.Int),
            new RegisterOperand(lowrhsReg1, TypeReference.Int))));
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new RegisterOperand(lhsReg, TypeReference.Int),
            new RegisterOperand(rhsReg1, TypeReference.Int))));
      }
      // Perform add - result += value2
      EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_ADD,
          new RegisterOperand(lowlhsReg, TypeReference.Int),
          new RegisterOperand(lowrhsReg2, TypeReference.Int))));
      EMIT(CPOS(s, MIR_BinaryAcc.mutate(s, IA32_ADC,
          new RegisterOperand(lhsReg, TypeReference.Int),
          new RegisterOperand(rhsReg2, TypeReference.Int))));
    } else if (value1.isRegister()){
      Register rhsReg1 = ((RegisterOperand) value1).getRegister();
      Register lowrhsReg1 = regpool.getSecondReg(rhsReg1);
      LongConstantOperand rhs2 = (LongConstantOperand) value2;
      int low = rhs2.lower32();
      int high = rhs2.upper32();
      // Do we need to move prior to the add - result = value1
      if (!value1.similar(result)) {
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new RegisterOperand(lowlhsReg, TypeReference.Int),
            new RegisterOperand(lowrhsReg1, TypeReference.Int))));
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new RegisterOperand(lhsReg, TypeReference.Int),
            new RegisterOperand(rhsReg1, TypeReference.Int))));
      }
      // Perform add - result += value2
      if (low == 0) {
        EMIT(CPOS(s, MIR_BinaryAcc.mutate(s, IA32_ADD,
            new RegisterOperand(lhsReg, TypeReference.Int),
            IC(high))));
      } else {
        EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_ADD,
            new RegisterOperand(lowlhsReg, TypeReference.Int),
            IC(low))));
        EMIT(CPOS(s, MIR_BinaryAcc.mutate(s, IA32_ADC,
            new RegisterOperand(lhsReg, TypeReference.Int),
            IC(high))));
      }
    } else {
      throw new OptimizingCompilerException("BURS_Helpers",
          "unexpected parameters: " + result + "=" + value1 + "+" + value2);
    }
  }

  /**
   * Expansion of LONG_SUB
   *
   * @param s the instruction to expand
   * @param result the result operand
   * @param val1 the first operand
   * @param val2 the second operand
   */
  protected final void LONG_SUB(Instruction s, Operand result, Operand val1, Operand val2) {

    if (result.similar(val1)) {
      // Straight forward case where instruction is already in accumulate form
      if (result.isRegister()) {
        Register lhsReg = result.asRegister().getRegister();
        Register lowlhsReg = regpool.getSecondReg(lhsReg);
        if (val2.isRegister()) {
          Register rhsReg2 = val2.asRegister().getRegister();
          Register lowrhsReg2 = regpool.getSecondReg(rhsReg2);
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SUB,
              new RegisterOperand(lowlhsReg, TypeReference.Int),
              new RegisterOperand(lowrhsReg2, TypeReference.Int))));
          EMIT(CPOS(s, MIR_BinaryAcc.mutate(s, IA32_SBB,
              new RegisterOperand(lhsReg, TypeReference.Int),
              new RegisterOperand(rhsReg2, TypeReference.Int))));
        } else if (val2.isLongConstant()) {
          LongConstantOperand rhs2 = val2.asLongConstant();
          int low = rhs2.lower32();
          int high = rhs2.upper32();
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SUB,
              new RegisterOperand(lowlhsReg, TypeReference.Int),
              IC(low))));
          EMIT(CPOS(s, MIR_BinaryAcc.mutate(s, IA32_SBB,
              new RegisterOperand(lhsReg, TypeReference.Int),
              IC(high))));
        } else {
          throw new OptimizingCompilerException("BURS_Helpers",
              "unexpected parameters: " + result + "=" + val1 + "-" + val2);
        }
      } else {
        throw new OptimizingCompilerException("BURS_Helpers",
            "unexpected parameters: " + result + "=" + val1 + "-" + val2);
      }
    } else if (!result.similar(val2)) {
      // Move first operand to result and perform operator on result, if
      // possible redundant moves should be remove by register allocator
      if (result.isRegister()) {
        Register lhsReg = result.asRegister().getRegister();
        Register lowlhsReg = regpool.getSecondReg(lhsReg);
        // Move val1 into result
        if (val1.isRegister()) {
          Register rhsReg1 = val1.asRegister().getRegister();
          Register lowrhsReg1 = regpool.getSecondReg(rhsReg1);
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(lowlhsReg, TypeReference.Int),
              new RegisterOperand(lowrhsReg1, TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(lhsReg, TypeReference.Int),
              new RegisterOperand(rhsReg1, TypeReference.Int))));
        } else if (val1.isLongConstant()) {
          LongConstantOperand rhs1 = val1.asLongConstant();
          int low = rhs1.lower32();
          int high = rhs1.upper32();
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(lowlhsReg, TypeReference.Int),
              IC(low))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(lhsReg, TypeReference.Int),
              IC(high))));
        } else {
          throw new OptimizingCompilerException("BURS_Helpers",
              "unexpected parameters: " + result + "=" + val1 + "-" + val2);
        }
        // Perform subtract
        if (val2.isRegister()) {
          Register rhsReg2 = val2.asRegister().getRegister();
          Register lowrhsReg2 = regpool.getSecondReg(rhsReg2);
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SUB,
              new RegisterOperand(lowlhsReg, TypeReference.Int),
              new RegisterOperand(lowrhsReg2, TypeReference.Int))));
          EMIT(CPOS(s, MIR_BinaryAcc.mutate(s, IA32_SBB,
              new RegisterOperand(lhsReg, TypeReference.Int),
              new RegisterOperand(rhsReg2, TypeReference.Int))));
        } else if (val2.isLongConstant()) {
          LongConstantOperand rhs2 = val2.asLongConstant();
          int low = rhs2.lower32();
          int high = rhs2.upper32();
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SUB,
              new RegisterOperand(lowlhsReg, TypeReference.Int),
              IC(low))));
          EMIT(CPOS(s, MIR_BinaryAcc.mutate(s, IA32_SBB,
              new RegisterOperand(lhsReg, TypeReference.Int),
              IC(high))));
        } else {
          throw new OptimizingCompilerException("BURS_Helpers",
              "unexpected parameters: " + result + "=" + val1 + "-" + val2);
        }
      } else {
        throw new OptimizingCompilerException("BURS_Helpers",
            "unexpected parameters: " + result + "=" + val1 + "-" + val2);
      }
    } else {
      // Potential to clobber second operand during move to result. Use a
      // temporary register to perform the operation and rely on register
      // allocator to remove redundant moves
      RegisterOperand temp1 = regpool.makeTempInt();
      RegisterOperand temp2 = regpool.makeTempInt();
      // Move val1 into temp
      if (val1.isRegister()) {
        Register rhsReg1 = val1.asRegister().getRegister();
        Register lowrhsReg1 = regpool.getSecondReg(rhsReg1);
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            temp1,
            new RegisterOperand(lowrhsReg1, TypeReference.Int))));
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            temp2,
            new RegisterOperand(rhsReg1, TypeReference.Int))));
      } else if (val1.isLongConstant()) {
        LongConstantOperand rhs1 = val1.asLongConstant();
        int low = rhs1.lower32();
        int high = rhs1.upper32();
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            temp1,
            IC(low))));
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            temp2,
            IC(high))));
      } else {
        throw new OptimizingCompilerException("BURS_Helpers",
            "unexpected parameters: " + result + "=" + val1 + "-" + val2);
      }
      // Perform subtract
      if (val2.isRegister()) {
        Register rhsReg2 = val2.asRegister().getRegister();
        Register lowrhsReg2 = regpool.getSecondReg(rhsReg2);
        EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SUB,
            temp1.copyRO(),
            new RegisterOperand(lowrhsReg2, TypeReference.Int))));
        EMIT(CPOS(s, MIR_BinaryAcc.mutate(s, IA32_SBB,
            temp2.copyRO(),
            new RegisterOperand(rhsReg2, TypeReference.Int))));
      } else if (val2.isLongConstant()) {
        LongConstantOperand rhs2 = val2.asLongConstant();
        int low = rhs2.lower32();
        int high = rhs2.upper32();
        EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SUB,
            temp1.copyRO(),
            IC(low))));
        EMIT(CPOS(s, MIR_BinaryAcc.mutate(s, IA32_SBB,
            temp2.copyRO(),
            IC(high))));
      } else {
        throw new OptimizingCompilerException("BURS_Helpers",
            "unexpected parameters: " + result + "=" + val1 + "-" + val2);
      }
      // Move result back
      if (result.isRegister()) {
        Register lhsReg = result.asRegister().getRegister();
        Register lowlhsReg = regpool.getSecondReg(lhsReg);
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new RegisterOperand(lowlhsReg, TypeReference.Int),
            temp1.copyRO())));
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new RegisterOperand(lhsReg, TypeReference.Int),
            temp2.copyRO())));
      } else {
        throw new OptimizingCompilerException("BURS_Helpers",
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
  protected final void LONG_MUL(Instruction s, RegisterOperand result,
      Operand value1, Operand value2) {
    if (value2.isRegister()) {
      // Leave for complex LIR2MIR expansion as the most efficient form requires
      // a branch
      if (VM.VerifyAssertions) VM._assert(Binary.getResult(s).similar(result) &&
          Binary.getVal1(s).similar(value1) && Binary.getVal2(s).similar(value2));
      EMIT(s);
    } else {
      // The value of value1 should be identical to result, to avoid moves, and a
      // register in the case of multiplication with a constant
      if ((value2.similar(result)) || value1.isLongConstant()) {
        Operand temp = value1;
        value1 = value2;
        value2 = temp;
      }
      if (VM.VerifyAssertions) VM._assert(value1.isRegister() && value2.isLongConstant());

      // In general, (a,b) * (c,d) = (l(a imul d)+l(b imul c)+u(b mul d), l(b mul d))

      Register lhsReg = result.getRegister();
      Register lowlhsReg = regpool.getSecondReg(lhsReg);

      LongConstantOperand rhs2 = (LongConstantOperand) value2;
      Register rhsReg1 = value1.asRegister().getRegister(); // a
      Register lowrhsReg1 = regpool.getSecondReg(rhsReg1); // b
      int high2 = rhs2.upper32(); // c
      int low2 = rhs2.lower32(); // d

      // We only have to handle those cases that Simplifier wouldn't get.
      // Simplifier catches
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
              new RegisterOperand(lhsReg, TypeReference.Int),
              new RegisterOperand(lowrhsReg1, TypeReference.Int))));
          EMIT(CPOS(s, MIR_UnaryAcc.create(IA32_NEG,
              new RegisterOperand(lhsReg, TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(lowlhsReg, TypeReference.Int),
              IC(0))));
        } else if (low2 == 1) {
          // -1, 1
          // CLAIM: (a,b) * (-1,1) = (a-b,b)
          if (lowlhsReg != lowrhsReg1) {
            EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
                new RegisterOperand(lowlhsReg, TypeReference.Int),
                new RegisterOperand(lowrhsReg1, TypeReference.Int))));
          }
          if (lhsReg != rhsReg1) {
            EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
                new RegisterOperand(lhsReg, TypeReference.Int),
                new RegisterOperand(rhsReg1, TypeReference.Int))));
          }
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SUB,
              new RegisterOperand(lhsReg, TypeReference.Int),
              new RegisterOperand(lowlhsReg, TypeReference.Int))));
        } else {
          // -1, *
          // CLAIM: (a,b) * (-1, d) = (l(a imul d)-b+u(b mul d), l(b mul d))
          if (lhsReg != rhsReg1) {
            EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
                new RegisterOperand(lhsReg, TypeReference.Int),
                new RegisterOperand(rhsReg1, TypeReference.Int))));
          }
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_IMUL2,
              new RegisterOperand(lhsReg, TypeReference.Int),
              IC(low2))));
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SUB,
              new RegisterOperand(lhsReg, TypeReference.Int),
              new RegisterOperand(lowrhsReg1, TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(getEAX(), TypeReference.Int),
              IC(low2))));
          EMIT(CPOS(s, MIR_Multiply.create(IA32_MUL,
              new RegisterOperand(getEDX(), TypeReference.Int),
              new RegisterOperand(getEAX(), TypeReference.Int),
              new RegisterOperand(lowlhsReg, TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(lowlhsReg, TypeReference.Int),
              new RegisterOperand(getEAX(), TypeReference.Int))));
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_ADD,
              new RegisterOperand(lhsReg, TypeReference.Int),
              new RegisterOperand(getEDX(), TypeReference.Int))));
        }
      } else if (high2 == 0) {
        if (low2 == -1) {
          // 0, -1
          // CLAIM: (a,b) * (0,-1) = (b-(a+(b!=0?1:0)),-b)
          // avoid clobbering a and b by using tmp
          Register tmp = regpool.getInteger();
          if (lowlhsReg != lowrhsReg1) {
            EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
                new RegisterOperand(lowlhsReg, TypeReference.Int),
                new RegisterOperand(lowrhsReg1, TypeReference.Int))));
          }
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(tmp, TypeReference.Int),
              new RegisterOperand(lowrhsReg1, TypeReference.Int))));
          EMIT(CPOS(s, MIR_UnaryAcc.create(IA32_NEG,
              new RegisterOperand(lowlhsReg, TypeReference.Int))));
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SBB,
              new RegisterOperand(tmp, TypeReference.Int),
              new RegisterOperand(rhsReg1, TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(lhsReg, TypeReference.Int),
              new RegisterOperand(tmp, TypeReference.Int))));
        } else {
          // 0, *
          // CLAIM: (a,b) * (0,d) = (l(a imul d)+u(b mul d), l(b mul d))
          if (lhsReg != rhsReg1) {
            EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
                new RegisterOperand(lhsReg, TypeReference.Int),
                new RegisterOperand(rhsReg1, TypeReference.Int))));
          }
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_IMUL2,
              new RegisterOperand(lhsReg, TypeReference.Int),
              IC(low2))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(getEAX(), TypeReference.Int),
              IC(low2))));
          EMIT(CPOS(s, MIR_Multiply.create(IA32_MUL,
              new RegisterOperand(getEDX(), TypeReference.Int),
              new RegisterOperand(getEAX(), TypeReference.Int),
              new RegisterOperand(lowrhsReg1, TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(lowlhsReg, TypeReference.Int),
              new RegisterOperand(getEAX(), TypeReference.Int))));
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_ADD,
              new RegisterOperand(lhsReg, TypeReference.Int),
              new RegisterOperand(getEDX(), TypeReference.Int))));
        }
      } else if (high2 == 1) {
        if (low2 == -1) {
          // 1, -1
          // CLAIM: (a,b) * (1,-1) = (2b-(a+(b!=0?1:0)),-b)
          // avoid clobbering a and b by using tmp
          Register tmp = regpool.getInteger();
          if (lowlhsReg != lowrhsReg1) {
            EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
                new RegisterOperand(lowlhsReg, TypeReference.Int),
                new RegisterOperand(lowrhsReg1, TypeReference.Int))));
          }
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(tmp, TypeReference.Int),
              new RegisterOperand(lowrhsReg1, TypeReference.Int))));
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_ADD,
              new RegisterOperand(tmp, TypeReference.Int),
              new RegisterOperand(tmp, TypeReference.Int))));
          EMIT(CPOS(s, MIR_UnaryAcc.create(IA32_NEG,
              new RegisterOperand(lowlhsReg, TypeReference.Int))));
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SBB,
              new RegisterOperand(tmp, TypeReference.Int),
              new RegisterOperand(rhsReg1, TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(lhsReg, TypeReference.Int),
              new RegisterOperand(tmp, TypeReference.Int))));
        } else if (low2 == 0) {
          // 1, 0
          // CLAIM: (x,y) * (1,0) = (y,0)
          // NB we should have simplified this LONG_MUL to a LONG_SHIFT
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(lhsReg, TypeReference.Int),
              new RegisterOperand(lowrhsReg1, TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(lowlhsReg, TypeReference.Int),
              IC(0))));
        } else if (low2 == 1) {
          // 1, 1
          // CLAIM: (x,y) * (1,1) = (x+y,y)
          // NB we should have simplified this LONG_MUL to a LONG_SHIFT and LONG_ADDs
          if (lowlhsReg != lowrhsReg1) {
            EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
                new RegisterOperand(lowlhsReg, TypeReference.Int),
                new RegisterOperand(lowrhsReg1, TypeReference.Int))));
          }
          if (lhsReg != rhsReg1) {
            EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
                new RegisterOperand(lhsReg, TypeReference.Int),
                new RegisterOperand(rhsReg1, TypeReference.Int))));
          }
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_ADD,
              new RegisterOperand(lhsReg, TypeReference.Int),
              new RegisterOperand(lowlhsReg, TypeReference.Int))));
        } else {
          // 1, *
          // CLAIM: (a,b) * (1,d) = (l(a imul d)+b+u(b mul d), l(b mul d))
          if (lhsReg != rhsReg1) {
            EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
                new RegisterOperand(lhsReg, TypeReference.Int),
                new RegisterOperand(rhsReg1, TypeReference.Int))));
          }
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_IMUL2,
              new RegisterOperand(lhsReg, TypeReference.Int),
              IC(low2))));
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_ADD,
              new RegisterOperand(lhsReg, TypeReference.Int),
              new RegisterOperand(lowrhsReg1, TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(getEAX(), TypeReference.Int),
              IC(low2))));
          EMIT(CPOS(s, MIR_Multiply.create(IA32_MUL,
              new RegisterOperand(getEDX(), TypeReference.Int),
              new RegisterOperand(getEAX(), TypeReference.Int),
              new RegisterOperand(lowrhsReg1, TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(lowlhsReg, TypeReference.Int),
              new RegisterOperand(getEAX(), TypeReference.Int))));
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_ADD,
              new RegisterOperand(lhsReg, TypeReference.Int),
              new RegisterOperand(getEDX(), TypeReference.Int))));
        }
      } else {
        if (low2 == -1) {
          // *, -1
          // CLAIM: (a,b) * (c, -1) = ((b+1)*c - (a + b==0?1:0), -b)
          // avoid clobbering a and b by using tmp
          Register tmp = regpool.getInteger();
          if (lowlhsReg != lowrhsReg1) {
            EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
                new RegisterOperand(lowlhsReg, TypeReference.Int),
                new RegisterOperand(lowrhsReg1, TypeReference.Int))));
          }
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(tmp, TypeReference.Int),
              new RegisterOperand(lowrhsReg1, TypeReference.Int))));
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_ADD,
              new RegisterOperand(tmp, TypeReference.Int),
              IC(1))));
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_IMUL2,
              new RegisterOperand(tmp, TypeReference.Int),
              IC(high2))));
          EMIT(CPOS(s, MIR_UnaryAcc.create(IA32_NEG,
              new RegisterOperand(lowlhsReg, TypeReference.Int))));
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SBB,
              new RegisterOperand(tmp, TypeReference.Int),
              new RegisterOperand(rhsReg1, TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(lhsReg, TypeReference.Int),
              new RegisterOperand(tmp, TypeReference.Int))));
        } else if (low2 == 0) {
          // *, 0
          // CLAIM: (a,b) * (c,0) = (l(b imul c),0)
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(lhsReg, TypeReference.Int),
              new RegisterOperand(lowrhsReg1, TypeReference.Int))));
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_IMUL2,
              new RegisterOperand(lhsReg, TypeReference.Int),
              IC(high2))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(lowlhsReg, TypeReference.Int),
              IC(0))));
        } else if (low2 == 1) {
          // *, 1
          // CLAIM: (x,y) * (z,1) = (l(y imul z)+x,y)
          if (lowlhsReg != lowrhsReg1) {
            EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
                new RegisterOperand(lowlhsReg, TypeReference.Int),
                new RegisterOperand(lowrhsReg1, TypeReference.Int))));
          }
          if (lhsReg != rhsReg1) {
            EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
                new RegisterOperand(lhsReg, TypeReference.Int),
                new RegisterOperand(rhsReg1, TypeReference.Int))));
          }
          Register tmp = regpool.getInteger();
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(tmp, TypeReference.Int),
              new RegisterOperand(lowrhsReg1, TypeReference.Int))));
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_IMUL2,
              new RegisterOperand(tmp, TypeReference.Int),
              IC(high2))));
          EMIT(CPOS(s, MIR_Move.create(IA32_ADD,
              new RegisterOperand(lhsReg, TypeReference.Int),
              new RegisterOperand(tmp, TypeReference.Int))));
        } else {
          // *, * can't do anything interesting and both operands have non-zero words
          // (a,b) * (c,d) = (l(a imul d)+l(b imul c)+u(b mul d), l(b mul d))
          if (lhsReg != rhsReg1) {
            EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
                new RegisterOperand(lhsReg, TypeReference.Int),
                new RegisterOperand(rhsReg1, TypeReference.Int))));
          }
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_IMUL2,
              new RegisterOperand(lhsReg, TypeReference.Int),
              IC(low2))));
          Register tmp = regpool.getInteger();
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(tmp, TypeReference.Int),
              new RegisterOperand(lowrhsReg1, TypeReference.Int))));
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_IMUL2,
              new RegisterOperand(tmp, TypeReference.Int),
              IC(high2))));
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_ADD,
              new RegisterOperand(lhsReg, TypeReference.Int),
              new RegisterOperand(tmp, TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(getEAX(), TypeReference.Int),
              IC(low2))));
          EMIT(CPOS(s, MIR_Multiply.create(IA32_MUL,
              new RegisterOperand(getEDX(), TypeReference.Int),
              new RegisterOperand(getEAX(), TypeReference.Int),
              new RegisterOperand(lowrhsReg1, TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(lowlhsReg, TypeReference.Int),
              new RegisterOperand(getEAX(), TypeReference.Int))));
          EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_ADD,
              new RegisterOperand(lhsReg, TypeReference.Int),
              new RegisterOperand(getEDX(), TypeReference.Int))));
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
  protected final void LONG_NEG(Instruction s, RegisterOperand result, Operand value) {
    Register lhsReg = result.getRegister();
    Register lowlhsReg = regpool.getSecondReg(lhsReg);
    // Move value into result if its not already
    if (!result.similar(value)){
      if (value.isRegister()) {
        Register rhsReg = value.asRegister().getRegister();
        Register lowrhsReg = regpool.getSecondReg(rhsReg);
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new RegisterOperand(lowlhsReg, TypeReference.Int),
            new RegisterOperand(lowrhsReg, TypeReference.Int))));
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new RegisterOperand(lhsReg, TypeReference.Int),
            new RegisterOperand(rhsReg, TypeReference.Int))));
      } else {
        throw new OptimizingCompilerException("BURS_Helpers",
            "unexpected parameters: " + result + "= -" + value);
      }
    }
    // Perform negation
    EMIT(CPOS(s, MIR_UnaryAcc.create(IA32_NOT,
        new RegisterOperand(lhsReg, TypeReference.Int))));
    EMIT(CPOS(s, MIR_UnaryAcc.create(IA32_NEG,
        new RegisterOperand(lowlhsReg, TypeReference.Int))));
    EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SBB,
        new RegisterOperand(lhsReg, TypeReference.Int),
        IC(-1))));
  }

  /**
   * Expansion of LONG_NOT
   *
   * @param s the instruction to expand
   * @param result the result operand
   * @param value the first operand
   */
  protected final void LONG_NOT(Instruction s, RegisterOperand result, Operand value) {
    Register lhsReg = result.getRegister();
    Register lowlhsReg = regpool.getSecondReg(lhsReg);
    // Move value into result if its not already
    if (!result.similar(value)){
      if (value.isRegister()) {
        Register rhsReg = value.asRegister().getRegister();
        Register lowrhsReg = regpool.getSecondReg(rhsReg);
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new RegisterOperand(lowlhsReg, TypeReference.Int),
            new RegisterOperand(lowrhsReg, TypeReference.Int))));
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new RegisterOperand(lhsReg, TypeReference.Int),
            new RegisterOperand(rhsReg, TypeReference.Int))));
      } else {
        throw new OptimizingCompilerException("BURS_Helpers",
            "unexpected parameters: " + result + "= ~" + value);
      }
    }
    // Perform not
    EMIT(CPOS(s, MIR_UnaryAcc.create(IA32_NOT,
        new RegisterOperand(lhsReg, TypeReference.Int))));
    EMIT(CPOS(s, MIR_UnaryAcc.mutate(s, IA32_NOT,
        new RegisterOperand(lowlhsReg, TypeReference.Int))));
  }

  /**
   * Expansion of LONG_AND
   *
   * @param s the instruction to expand
   * @param result the result operand
   * @param value1 the first operand
   * @param value2 the second operand
   */
  protected final void LONG_AND(Instruction s, RegisterOperand result,
      Operand value1, Operand value2) {
    // The value of value1 should be identical to result, to avoid moves, and a
    // register in the case of addition with a constant
    if ((value2.similar(result)) || value1.isLongConstant()) {
      Operand temp = value1;
      value1 = value2;
      value2 = temp;
    }
    Register lhsReg = result.getRegister();
    Register lowlhsReg = regpool.getSecondReg(lhsReg);
    if (value1.isRegister() && value2.isRegister()) {
      Register rhsReg1 = ((RegisterOperand) value1).getRegister();
      Register lowrhsReg1 = regpool.getSecondReg(rhsReg1);
      Register rhsReg2 = ((RegisterOperand) value2).getRegister();
      Register lowrhsReg2 = regpool.getSecondReg(rhsReg2);
      // Do we need to move prior to the and - result = value1
      if (!value1.similar(result)) {
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new RegisterOperand(lowlhsReg, TypeReference.Int),
            new RegisterOperand(lowrhsReg1, TypeReference.Int))));
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new RegisterOperand(lhsReg, TypeReference.Int),
            new RegisterOperand(rhsReg1, TypeReference.Int))));
      }
      // Perform and - result &= value2
      EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_AND,
          new RegisterOperand(lowlhsReg, TypeReference.Int),
          new RegisterOperand(lowrhsReg2, TypeReference.Int))));
      EMIT(CPOS(s, MIR_BinaryAcc.mutate(s, IA32_AND,
          new RegisterOperand(lhsReg, TypeReference.Int),
          new RegisterOperand(rhsReg2, TypeReference.Int))));
    } else if (value1.isRegister()){
      Register rhsReg1 = ((RegisterOperand) value1).getRegister();
      Register lowrhsReg1 = regpool.getSecondReg(rhsReg1);
      LongConstantOperand rhs2 = (LongConstantOperand) value2;
      int low = rhs2.lower32();
      int high = rhs2.upper32();
      // Do we need to move prior to the and - result = value1
      if (!value1.similar(result)) {
        if (low != 0) {
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(lowlhsReg, TypeReference.Int),
              new RegisterOperand(lowrhsReg1, TypeReference.Int))));
        }
        if (high != 0) {
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(lhsReg, TypeReference.Int),
              new RegisterOperand(rhsReg1, TypeReference.Int))));
        }
      }
      // Perform and - result &= value2
      if (low == 0) {
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new RegisterOperand(lowlhsReg, TypeReference.Int),
            IC(0))));
      } else if (low == -1) {
        // nop
      } else {
        EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_AND,
            new RegisterOperand(lowlhsReg, TypeReference.Int),
            IC(low))));
      }
      if (high == 0) {
        EMIT(CPOS(s, MIR_Move.mutate(s, IA32_MOV,
            new RegisterOperand(lhsReg, TypeReference.Int),
            IC(0))));
      } else if (high == -1) {
        // nop
      } else {
        EMIT(CPOS(s, MIR_BinaryAcc.mutate(s, IA32_AND,
            new RegisterOperand(lhsReg, TypeReference.Int),
            IC(high))));
      }
    } else {
      throw new OptimizingCompilerException("BURS_Helpers",
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
  protected final void LONG_OR(Instruction s, RegisterOperand result,
      Operand value1, Operand value2) {
    // The value of value1 should be identical to result, to avoid moves, and a
    // register in the case of addition with a constant
    if ((value2.similar(result)) || value1.isLongConstant()) {
      Operand temp = value1;
      value1 = value2;
      value2 = temp;
    }
    Register lhsReg = result.getRegister();
    Register lowlhsReg = regpool.getSecondReg(lhsReg);
    if (value1.isRegister() && value2.isRegister()) {
      Register rhsReg1 = ((RegisterOperand) value1).getRegister();
      Register lowrhsReg1 = regpool.getSecondReg(rhsReg1);
      Register rhsReg2 = ((RegisterOperand) value2).getRegister();
      Register lowrhsReg2 = regpool.getSecondReg(rhsReg2);
      // Do we need to move prior to the and - result = value1
      if (!value1.similar(result)) {
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new RegisterOperand(lowlhsReg, TypeReference.Int),
            new RegisterOperand(lowrhsReg1, TypeReference.Int))));
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new RegisterOperand(lhsReg, TypeReference.Int),
            new RegisterOperand(rhsReg1, TypeReference.Int))));
      }
      // Perform or - result |= value2
      EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_OR,
          new RegisterOperand(lowlhsReg, TypeReference.Int),
          new RegisterOperand(lowrhsReg2, TypeReference.Int))));
      EMIT(CPOS(s, MIR_BinaryAcc.mutate(s, IA32_OR,
          new RegisterOperand(lhsReg, TypeReference.Int),
          new RegisterOperand(rhsReg2, TypeReference.Int))));
    } else if (value1.isRegister()){
      Register rhsReg1 = ((RegisterOperand) value1).getRegister();
      Register lowrhsReg1 = regpool.getSecondReg(rhsReg1);
      LongConstantOperand rhs2 = (LongConstantOperand) value2;
      int low = rhs2.lower32();
      int high = rhs2.upper32();
      // Do we need to move prior to the and - result = value1
      if (!value1.similar(result)) {
        if (low != -1) {
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(lowlhsReg, TypeReference.Int),
              new RegisterOperand(lowrhsReg1, TypeReference.Int))));
        }
        if (high != -1) {
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(lhsReg, TypeReference.Int),
              new RegisterOperand(rhsReg1, TypeReference.Int))));
        }
      }
      // Perform or - result |= value2
      if (low == 0) {
        // nop
      } else if (low == -1) {
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new RegisterOperand(lowlhsReg, TypeReference.Int),
            IC(-1))));
      } else {
        EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_OR,
            new RegisterOperand(lowlhsReg, TypeReference.Int),
            IC(low))));
      }
      if (high == 0) {
        // nop
      } else if (high == -1) {
        EMIT(CPOS(s, MIR_Move.mutate(s, IA32_MOV,
            new RegisterOperand(lhsReg, TypeReference.Int),
            IC(-1))));
      } else {
        EMIT(CPOS(s, MIR_BinaryAcc.mutate(s, IA32_OR,
            new RegisterOperand(lhsReg, TypeReference.Int),
            IC(high))));
      }
    } else {
      throw new OptimizingCompilerException("BURS_Helpers",
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
  protected final void LONG_XOR(Instruction s, RegisterOperand result,
      Operand value1, Operand value2) {
    // The value of value1 should be identical to result, to avoid moves, and a
    // register in the case of addition with a constant
    if ((value2.similar(result)) || value1.isLongConstant()) {
      Operand temp = value1;
      value1 = value2;
      value2 = temp;
    }
    Register lhsReg = result.getRegister();
    Register lowlhsReg = regpool.getSecondReg(lhsReg);
    if (value1.isRegister() && value2.isRegister()) {
      Register rhsReg1 = ((RegisterOperand) value1).getRegister();
      Register lowrhsReg1 = regpool.getSecondReg(rhsReg1);
      Register rhsReg2 = ((RegisterOperand) value2).getRegister();
      Register lowrhsReg2 = regpool.getSecondReg(rhsReg2);
      // Do we need to move prior to the and - result = value1
      if (!value1.similar(result)) {
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new RegisterOperand(lowlhsReg, TypeReference.Int),
            new RegisterOperand(lowrhsReg1, TypeReference.Int))));
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new RegisterOperand(lhsReg, TypeReference.Int),
            new RegisterOperand(rhsReg1, TypeReference.Int))));
      }
      // Perform or - result |= value2
      EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_XOR,
          new RegisterOperand(lowlhsReg, TypeReference.Int),
          new RegisterOperand(lowrhsReg2, TypeReference.Int))));
      EMIT(CPOS(s, MIR_BinaryAcc.mutate(s, IA32_XOR,
          new RegisterOperand(lhsReg, TypeReference.Int),
          new RegisterOperand(rhsReg2, TypeReference.Int))));
    } else if (value1.isRegister()){
      Register rhsReg1 = ((RegisterOperand) value1).getRegister();
      Register lowrhsReg1 = regpool.getSecondReg(rhsReg1);
      LongConstantOperand rhs2 = (LongConstantOperand) value2;
      int low = rhs2.lower32();
      int high = rhs2.upper32();
      // Do we need to move prior to the and - result = value1
      if (!value1.similar(result)) {
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new RegisterOperand(lowlhsReg, TypeReference.Int),
            new RegisterOperand(lowrhsReg1, TypeReference.Int))));
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new RegisterOperand(lhsReg, TypeReference.Int),
            new RegisterOperand(rhsReg1, TypeReference.Int))));
      }
      // Perform xor - result ^= value2
      if (low == 0) {
        // nop
      } else if (low == -1) {
        EMIT(CPOS(s, MIR_UnaryAcc.create(IA32_NOT,
            new RegisterOperand(lowlhsReg, TypeReference.Int))));
      } else {
        EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_XOR,
            new RegisterOperand(lowlhsReg, TypeReference.Int),
            IC(low))));
      }
      if (high == 0) {
        // nop
      } else if (high == -1) {
        EMIT(CPOS(s, MIR_UnaryAcc.create(IA32_NOT,
            new RegisterOperand(lhsReg, TypeReference.Int))));
      } else {
        EMIT(CPOS(s, MIR_BinaryAcc.mutate(s, IA32_XOR,
            new RegisterOperand(lhsReg, TypeReference.Int),
            IC(high))));
      }
    } else {
      throw new OptimizingCompilerException("BURS_Helpers",
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
  protected final void LONG_SHL(Instruction s, Operand result,
      Operand val1, Operand val2, boolean maskWith3f) {
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
      Register lhsReg = result.asRegister().getRegister();
      Register lowlhsReg = burs.ir.regpool.getSecondReg(lhsReg);
      Register rhsReg1 = val1.asRegister().getRegister();
      Register lowrhsReg1 = burs.ir.regpool.getSecondReg(rhsReg1);

      if (shift == 0) {
        // operation is a nop.
        if (!result.similar(val1)) {
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(lowlhsReg, TypeReference.Int),
              new RegisterOperand(lowrhsReg1, TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(lhsReg, TypeReference.Int),
              new RegisterOperand(rhsReg1, TypeReference.Int))));
        }
      } else if (shift == 1) {
        if (!result.similar(val1)) {
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(lowlhsReg, TypeReference.Int),
              new RegisterOperand(lowrhsReg1, TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(lhsReg, TypeReference.Int),
              new RegisterOperand(rhsReg1, TypeReference.Int))));
        }
        EMIT(CPOS(s,
            MIR_BinaryAcc.create(IA32_ADD,
                new RegisterOperand(lowlhsReg, TypeReference.Int),
                new RegisterOperand(lowlhsReg, TypeReference.Int))));
        EMIT(MIR_BinaryAcc.mutate(s,
            IA32_ADC,
            new RegisterOperand(lhsReg, TypeReference.Int),
            new RegisterOperand(lhsReg, TypeReference.Int)));
      } else if (shift == 2) {
        // bits to shift in: tmp = lowrhsReg >> 30
        Register tmp = regpool.getInteger();
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new RegisterOperand(tmp, TypeReference.Int),
            new RegisterOperand(lowrhsReg1, TypeReference.Int))));
        EMIT(CPOS(s,
            MIR_BinaryAcc.create(IA32_SHR,
                new RegisterOperand(tmp, TypeReference.Int),
                IC(30))));
        // compute top half: lhsReg = (rhsReg1 << 2) + tmp
        EMIT(CPOS(s,
            MIR_Lea.create(IA32_LEA,
                new RegisterOperand(lhsReg, TypeReference.Int),
                MemoryOperand.BIS(new RegisterOperand(tmp, TypeReference.Int),
                    new RegisterOperand(rhsReg1, TypeReference.Int),
                    (byte)2, (byte)4, null, null))));
        // compute bottom half: lowlhsReg = lowlhsReg << 2
        EMIT(CPOS(s,
            MIR_Lea.create(IA32_LEA,
                new RegisterOperand(lowlhsReg, TypeReference.Int),
                new MemoryOperand(null, // base
                    new RegisterOperand(lowrhsReg1, TypeReference.Int), //index
                    (byte)2, // scale
                    Offset.zero(), // displacement
                    (byte)4, // size
                    null, // location
                    null // guard
                    ))));
      } else if (shift == 3) {
        // bits to shift in: tmp = lowrhsReg >>> 29
        Register tmp = regpool.getInteger();
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
            new RegisterOperand(tmp, TypeReference.Int),
            new RegisterOperand(lowrhsReg1, TypeReference.Int))));
        EMIT(CPOS(s,
            MIR_BinaryAcc.create(IA32_SHR,
                new RegisterOperand(tmp, TypeReference.Int),
                IC(29))));
        // compute top half: lhsReg = (rhsReg1 << 3) + tmp
        EMIT(CPOS(s,
            MIR_Lea.create(IA32_LEA,
                new RegisterOperand(lhsReg, TypeReference.Int),
                MemoryOperand.BIS(new RegisterOperand(tmp, TypeReference.Int),
                    new RegisterOperand(rhsReg1, TypeReference.Int),
                    (byte)3, (byte)4, null, null))));
        // compute bottom half: lowlhsReg = lowlhsReg << 3
        EMIT(CPOS(s,
            MIR_Lea.create(IA32_LEA,
                new RegisterOperand(lowlhsReg, TypeReference.Int),
                new MemoryOperand(null, // base
                    new RegisterOperand(lowrhsReg1, TypeReference.Int), //index
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
              new RegisterOperand(lhsReg, TypeReference.Int),
              new RegisterOperand(rhsReg1, TypeReference.Int))));
        }
        // bits to shift in: tmp = lowrhsReg >>> (32 - shift)
        Register tmp = regpool.getInteger();
        EMIT(CPOS(s,
            MIR_Move.create(IA32_MOV,
                new RegisterOperand(tmp, TypeReference.Int),
                new RegisterOperand(lowrhsReg1, TypeReference.Int))));
        EMIT(CPOS(s,
            MIR_BinaryAcc.create(IA32_SHR,
                new RegisterOperand(tmp, TypeReference.Int),
                IC(32 - shift))));
        // compute top half: lhsReg = (lhsReg1 << shift) | tmp
        EMIT(CPOS(s,
            MIR_BinaryAcc.create(IA32_SHL,
                new RegisterOperand(lhsReg, TypeReference.Int),
                IC(shift))));
        EMIT(CPOS(s,
            MIR_BinaryAcc.create(IA32_OR,
                new RegisterOperand(lhsReg, TypeReference.Int),
                new RegisterOperand(tmp, TypeReference.Int))));
        // compute bottom half: lowlhsReg = lowlhsReg << shift
        if (!result.similar(val1)) {
          EMIT(CPOS(s,
              MIR_Move.create(IA32_MOV,
              new RegisterOperand(lowlhsReg, TypeReference.Int),
              new RegisterOperand(lowrhsReg1, TypeReference.Int))));
        }
        EMIT(MIR_BinaryAcc.mutate(s, IA32_SHL,
                new RegisterOperand(lowlhsReg, TypeReference.Int),
                IC(shift)));
      } else if (shift == 32) {
        // lhsReg = lowrhsReg1
        EMIT(CPOS(s,
            MIR_Move.create(IA32_MOV,
                new RegisterOperand(lhsReg, TypeReference.Int),
                new RegisterOperand(lowrhsReg1, TypeReference.Int))));
        // lowlhsReg = 0
        EMIT(MIR_Move.mutate(s, IA32_MOV,
            new RegisterOperand(lowlhsReg, TypeReference.Int),
            IC(0)));
      } else if (shift == 33) {
        // lhsReg = lowrhsReg1 << 1
        EMIT(CPOS(s,
            MIR_Lea.create(IA32_LEA,
                new RegisterOperand(lhsReg, TypeReference.Int),
                new MemoryOperand(null, // base
                    new RegisterOperand(lowrhsReg1, TypeReference.Int), //index
                    (byte)1, // scale
                    Offset.zero(), // displacement
                    (byte)4, // size
                    null, // location
                    null // guard
                    ))));
        // lowlhsReg = 0
        EMIT(MIR_Move.mutate(s, IA32_MOV,
            new RegisterOperand(lowlhsReg, TypeReference.Int),
            IC(0)));
      } else if (shift == 34) {
        // lhsReg = lowrhsReg1 << 2
        EMIT(CPOS(s,
            MIR_Lea.create(IA32_LEA,
                new RegisterOperand(lhsReg, TypeReference.Int),
                new MemoryOperand(null, // base
                    new RegisterOperand(lowrhsReg1, TypeReference.Int), //index
                    (byte)2, // scale
                    Offset.zero(), // displacement
                    (byte)4, // size
                    null, // location
                    null // guard
                    ))));
        // lowlhsReg = 0
        EMIT(MIR_Move.mutate(s, IA32_MOV,
            new RegisterOperand(lowlhsReg, TypeReference.Int),
            IC(0)));
      } else if (shift == 35) {
        // lhsReg = lowrhsReg1 << 3
        EMIT(CPOS(s,
            MIR_Lea.create(IA32_LEA,
                new RegisterOperand(lhsReg, TypeReference.Int),
                new MemoryOperand(null, // base
                    new RegisterOperand(lowrhsReg1, TypeReference.Int), //index
                    (byte)3, // scale
                    Offset.zero(), // displacement
                    (byte)4, // size
                    null, // location
                    null // guard
                    ))));
        // lowlhsReg = 0
        EMIT(MIR_Move.mutate(s, IA32_MOV,
            new RegisterOperand(lowlhsReg, TypeReference.Int),
            IC(0)));
      } else {
        if ((maskWith3f) || (shift < 64)){
          // lhsReg = lowrhsReg1 << ((shift - 32) & 0x1f)
          EMIT(CPOS(s,
              MIR_Move.create(IA32_MOV,
                  new RegisterOperand(lhsReg, TypeReference.Int),
                  new RegisterOperand(lowrhsReg1, TypeReference.Int))));
          EMIT(CPOS(s,
              MIR_BinaryAcc.create(IA32_SHL,
                  new RegisterOperand(lhsReg, TypeReference.Int),
                  IC((shift-32) & 0x1F))));
          // lowlhsReg = 0
          EMIT(MIR_Move.mutate(s, IA32_MOV,
              new RegisterOperand(lowlhsReg, TypeReference.Int),
              IC(0)));
        } else {
          // lhsReg = 0
          EMIT(CPOS(s,
              MIR_Move.create(IA32_MOV,
              new RegisterOperand(lhsReg, TypeReference.Int),
              IC(0))));
          // lowlhsReg = 0
          EMIT(MIR_Move.mutate(s, IA32_MOV,
              new RegisterOperand(lowlhsReg, TypeReference.Int),
              IC(0)));
        }
      }
    } else {
      throw new OptimizingCompilerException("BURS_Helpers",
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
  protected final void LONG_SHR(Instruction s, Operand result,
      Operand val1, Operand val2, boolean maskWith3f) {
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
      Register lhsReg = result.asRegister().getRegister();
      Register lowlhsReg = burs.ir.regpool.getSecondReg(lhsReg);
      Register rhsReg1 = val1.asRegister().getRegister();
      Register lowrhsReg1 = burs.ir.regpool.getSecondReg(rhsReg1);

      if (shift == 0) {
        // operation is a nop.
        if (!result.similar(val1)) {
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(lowlhsReg, TypeReference.Int),
              new RegisterOperand(lowrhsReg1, TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(lhsReg, TypeReference.Int),
              new RegisterOperand(rhsReg1, TypeReference.Int))));
        }
      } else if (shift == 1) {
        if (!result.similar(val1)) {
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(lowlhsReg, TypeReference.Int),
              new RegisterOperand(lowrhsReg1, TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(lhsReg, TypeReference.Int),
              new RegisterOperand(rhsReg1, TypeReference.Int))));
        }
        // lhsReg = lhsReg >> 1
        EMIT(CPOS(s,
            MIR_BinaryAcc.create(IA32_SAR,
                new RegisterOperand(lhsReg, TypeReference.Int),
                IC(1))));
        // lowlhsReg = (lhsReg << 31) | (lowlhsReg >>> 1)
        EMIT(MIR_BinaryAcc.mutate(s, IA32_RCR,
            new RegisterOperand(lowlhsReg, TypeReference.Int),
            IC(1)));
      } else if (shift < 32) {
        // bits to shift in: tmp = rhsReg << (32 - shift)
        // TODO: use of LEA for SHL
        Register tmp = regpool.getInteger();
        EMIT(CPOS(s,
            MIR_Move.create(IA32_MOV,
                new RegisterOperand(tmp, TypeReference.Int),
                new RegisterOperand(rhsReg1, TypeReference.Int))));
        EMIT(CPOS(s,
            MIR_BinaryAcc.create(IA32_SHL,
                new RegisterOperand(tmp, TypeReference.Int),
                IC(32 - shift))));
        // compute bottom half: lowlhsReg = (lowlhsReg1 >>> shift) | tmp
        if (!result.similar(val1)) {
          EMIT(CPOS(s,
              MIR_Move.create(IA32_MOV,
                  new RegisterOperand(lowlhsReg, TypeReference.Int),
                  new RegisterOperand(lowrhsReg1, TypeReference.Int))));
        }
        EMIT(CPOS(s,
            MIR_BinaryAcc.create(IA32_SHR,
                new RegisterOperand(lowlhsReg, TypeReference.Int),
                IC(shift))));
        EMIT(CPOS(s,
            MIR_BinaryAcc.create(IA32_OR,
                new RegisterOperand(lowlhsReg, TypeReference.Int),
                new RegisterOperand(tmp, TypeReference.Int))));
        // compute top half: lhsReg = lhsReg >> shift
        if (!result.similar(val1)) {
          EMIT(CPOS(s,
              MIR_Move.create(IA32_MOV,
                  new RegisterOperand(lhsReg, TypeReference.Int),
                  new RegisterOperand(rhsReg1, TypeReference.Int))));
        }
        EMIT(MIR_BinaryAcc.mutate(s, IA32_SAR,
            new RegisterOperand(lhsReg, TypeReference.Int),
            IC(shift)));
      } else if (shift == 32) {
        // lowlhsReg = rhsReg1
        EMIT(MIR_Move.mutate(s, IA32_MOV,
            new RegisterOperand(lowlhsReg, TypeReference.Int),
            new RegisterOperand(rhsReg1, TypeReference.Int)));
        // lhsReg = rhsReg1 >> 31
        if (!result.similar(val1)) {
          EMIT(CPOS(s,
              MIR_Move.create(IA32_MOV,
                  new RegisterOperand(lhsReg, TypeReference.Int),
                  new RegisterOperand(rhsReg1, TypeReference.Int))));
        }
        EMIT(CPOS(s,
            MIR_BinaryAcc.create(IA32_SAR,
                new RegisterOperand(lhsReg, TypeReference.Int),
                IC(31))));
      } else {
        if ((!maskWith3f && (shift >= 0x3F))||
            (maskWith3f && ((shift & 0x3F) == 0x3F))) {
          // lhsReg = rhsReg1 >> 31
          if (!result.similar(val1)) {
            EMIT(CPOS(s,
                MIR_Move.create(IA32_MOV,
                    new RegisterOperand(lhsReg, TypeReference.Int),
                    new RegisterOperand(rhsReg1, TypeReference.Int))));
          }
          EMIT(CPOS(s,
              MIR_BinaryAcc.create(IA32_SAR,
                  new RegisterOperand(lhsReg, TypeReference.Int),
                  IC(31))));
          // lowlhsReg = lhsReg
          EMIT(MIR_Move.mutate(s, IA32_MOV,
              new RegisterOperand(lowlhsReg, TypeReference.Int),
              new RegisterOperand(lhsReg, TypeReference.Int)));
        } else {
          // lhsReg = rhsReg1 >> 31
          if (!result.similar(val1)) {
            EMIT(CPOS(s,
                MIR_Move.create(IA32_MOV,
                    new RegisterOperand(lhsReg, TypeReference.Int),
                    new RegisterOperand(rhsReg1, TypeReference.Int))));
          }
          EMIT(CPOS(s,
              MIR_BinaryAcc.create(IA32_SAR,
                  new RegisterOperand(lhsReg, TypeReference.Int),
                  IC(31))));
          // lowlhsReg = rhsReg1 >> shift
          EMIT(MIR_Move.mutate(s, IA32_MOV,
              new RegisterOperand(lowlhsReg, TypeReference.Int),
              new RegisterOperand(rhsReg1, TypeReference.Int)));
          EMIT(CPOS(s,
              MIR_BinaryAcc.create(IA32_SAR,
                  new RegisterOperand(lowlhsReg, TypeReference.Int),
                  IC((shift - 32) & 0x3F))));
        }
      }
    } else {
      throw new OptimizingCompilerException("BURS_Helpers",
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
  protected final void LONG_USHR(Instruction s, Operand result,
      Operand val1, Operand val2, boolean maskWith3f) {
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
      Register lhsReg = result.asRegister().getRegister();
      Register lowlhsReg = burs.ir.regpool.getSecondReg(lhsReg);
      Register rhsReg1 = val1.asRegister().getRegister();
      Register lowrhsReg1 = burs.ir.regpool.getSecondReg(rhsReg1);

      if (shift == 0) {
        // operation is a nop.
        if (!result.similar(val1)) {
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(lowlhsReg, TypeReference.Int),
              new RegisterOperand(lowrhsReg1, TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(lhsReg, TypeReference.Int),
              new RegisterOperand(rhsReg1, TypeReference.Int))));
        }
      } else if (shift == 1) {
        if (!result.similar(val1)) {
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(lowlhsReg, TypeReference.Int),
              new RegisterOperand(lowrhsReg1, TypeReference.Int))));
          EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
              new RegisterOperand(lhsReg, TypeReference.Int),
              new RegisterOperand(rhsReg1, TypeReference.Int))));
        }
        // lhsReg = lhsReg >>> 1
        EMIT(CPOS(s,
            MIR_BinaryAcc.create(IA32_SHR,
                new RegisterOperand(lhsReg, TypeReference.Int),
                IC(1))));
        // lowlhsReg = (lhsReg << 31) | (lowlhsReg >>> 1)
        EMIT(MIR_BinaryAcc.mutate(s, IA32_RCR,
            new RegisterOperand(lowlhsReg, TypeReference.Int),
            IC(1)));
      } else if (shift < 32) {
        // bits to shift in: tmp = rhsReg << (32 - shift)
        // TODO: use LEA for SHL operator
        Register tmp = regpool.getInteger();
        EMIT(CPOS(s,
            MIR_Move.create(IA32_MOV,
                new RegisterOperand(tmp, TypeReference.Int),
                new RegisterOperand(rhsReg1, TypeReference.Int))));
        EMIT(CPOS(s,
            MIR_BinaryAcc.create(IA32_SHL,
                new RegisterOperand(tmp, TypeReference.Int),
                IC(32 - shift))));
        // compute bottom half: lowlhsReg = (lowlhsReg1 >>> shift) | tmp
        if (!result.similar(val1)) {
          EMIT(CPOS(s,
              MIR_Move.create(IA32_MOV,
                  new RegisterOperand(lowlhsReg, TypeReference.Int),
                  new RegisterOperand(lowrhsReg1, TypeReference.Int))));
        }
        EMIT(CPOS(s,
            MIR_BinaryAcc.create(IA32_SHR,
                new RegisterOperand(lowlhsReg, TypeReference.Int),
                IC(shift))));
        EMIT(CPOS(s,
            MIR_BinaryAcc.create(IA32_OR,
                new RegisterOperand(lowlhsReg, TypeReference.Int),
                new RegisterOperand(tmp, TypeReference.Int))));
        // compute top half: lhsReg = lhsReg >>> shift
        if (!result.similar(val1)) {
          EMIT(CPOS(s,
              MIR_Move.create(IA32_MOV,
                  new RegisterOperand(lhsReg, TypeReference.Int),
                  new RegisterOperand(rhsReg1, TypeReference.Int))));
        }
        EMIT(MIR_BinaryAcc.mutate(s, IA32_SHR,
            new RegisterOperand(lhsReg, TypeReference.Int),
            IC(shift)));
      } else if (shift == 32) {
        // lowlhsReg = rhsReg1
        EMIT(MIR_Move.mutate(s, IA32_MOV,
            new RegisterOperand(lowlhsReg, TypeReference.Int),
            new RegisterOperand(rhsReg1, TypeReference.Int)));
        // lhsReg = 0
        EMIT(CPOS(s,
            MIR_Move.create(IA32_MOV,
                new RegisterOperand(lhsReg, TypeReference.Int),
                IC(0))));
      } else {
        if (maskWith3f || (shift < 64)) {
          // lowlhsReg = rhsReg1 >>> (shift & 0x1F)
          EMIT(CPOS(s,
              MIR_Move.create(IA32_MOV,
              new RegisterOperand(lowlhsReg, TypeReference.Int),
              new RegisterOperand(rhsReg1, TypeReference.Int))));
          EMIT(CPOS(s,
              MIR_BinaryAcc.create(IA32_SHR,
              new RegisterOperand(lowlhsReg, TypeReference.Int),
              IC(shift&0x1F))));
        } else {
          // lowlhsReg = 0
          EMIT(CPOS(s,
              MIR_Move.create(IA32_MOV,
                  new RegisterOperand(lowlhsReg, TypeReference.Int),
                  IC(0))));
        }
        // lhsReg = 0
        EMIT(MIR_Move.mutate(s, IA32_MOV,
                new RegisterOperand(lhsReg, TypeReference.Int),
                IC(0)));
      }
    } else {
      throw new OptimizingCompilerException("BURS_Helpers",
          "unexpected parameters: " + result + "=" + val1 + ">>" + val2);
    }
  }

  /**
   * Expansion of RDTSC (called GET_TIME_BASE for consistency with PPC)
   *
   * @param s the instruction to expand
   * @param result the result/first operand
   */
  protected final void GET_TIME_BASE(Instruction s,
      RegisterOperand result) {
    Register highReg = result.getRegister();
    Register lowReg = regpool.getSecondReg(highReg);
    EMIT(CPOS(s, MIR_RDTSC.create(IA32_RDTSC,
        new RegisterOperand(getEAX(), TypeReference.Int),
        new RegisterOperand(getEDX(), TypeReference.Int))));
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
        new RegisterOperand(lowReg, TypeReference.Int),
        new RegisterOperand(getEAX(), TypeReference.Int))));
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV,
        new RegisterOperand(highReg, TypeReference.Int),
        new RegisterOperand(getEDX(), TypeReference.Int))));
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
  protected final void LONG_CMP(Instruction s, RegisterOperand res, Operand val1, Operand val2) {
    RegisterOperand one = regpool.makeTempInt();
    RegisterOperand lone = regpool.makeTempInt();
    Operand two, ltwo;
    if (val1 instanceof RegisterOperand) {
      Register val1_reg = val1.asRegister().getRegister();
      EMIT(CPOS(s, MIR_Move.create(IA32_MOV, one, new RegisterOperand(val1_reg, TypeReference.Int))));
      EMIT(CPOS(s,
                MIR_Move.create(IA32_MOV,
                                lone,
                                new RegisterOperand(regpool.getSecondReg(val1_reg), TypeReference.Int))));
    } else {
      LongConstantOperand tmp = (LongConstantOperand) val1;
      EMIT(CPOS(s, MIR_Move.create(IA32_MOV, one, IC(tmp.upper32()))));
      EMIT(CPOS(s, MIR_Move.create(IA32_MOV, lone, IC(tmp.lower32()))));
    }
    if (val2 instanceof RegisterOperand) {
      two = val2;
      ltwo = L(burs.ir.regpool.getSecondReg(val2.asRegister().getRegister()));
    } else {
      LongConstantOperand tmp = (LongConstantOperand) val2;
      two = IC(tmp.upper32());
      ltwo = IC(tmp.lower32());
    }
    EMIT(CPOS(s, MIR_Compare.create(IA32_CMP, lone.copyRO(), ltwo)));
    EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SBB, one.copyRO(), two)));
    EMIT(CPOS(s, MIR_Set
        .create(IA32_SET__B, res, IA32ConditionOperand.LT()))); // res =
    // (val1 < val2) ? 1 :0
    EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_OR, one.copyRO(), lone.copyRO())));
    EMIT(CPOS(s,
              MIR_Set.create(IA32_SET__B,
                             lone.copyRO(),
                             IA32ConditionOperand.NE()))); // lone = (val1 != val2) ? 1 : 0
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
  protected final void FP_MOV_OP_MOV(Instruction s, Operator op, Operand result, Operand val1,
                                     Operand val2) {
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
  protected final void FP_REM(Instruction s, Operand val1, Operand val2) {
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
  protected final void threeValueFPCmp(Instruction s) {
    // IMPORTANT: FCOMI only sets 3 of the 6 bits in EFLAGS, so
    // we can't quite just translate the condition operand as if it
    // were an integer compare.
    // FCMOI sets ZF, PF, and CF as follows:
    // Compare Results ZF PF CF
    // left > right 0 0 0
    // left < right 0 0 1
    // left == right 1 0 0
    // UNORDERED 1 1 1
    RegisterOperand one = (RegisterOperand) Binary.getClearVal1(s);
    RegisterOperand two = (RegisterOperand) Binary.getClearVal2(s);
    RegisterOperand res = Binary.getClearResult(s);
    RegisterOperand temp = burs.ir.regpool.makeTempInt();
    Register FP0 = burs.ir.regpool.getPhysicalRegisterSet().getFPR(0);
    if ((s.operator == DOUBLE_CMPL) || (s.operator == FLOAT_CMPL)) {
      EMIT(CPOS(s, MIR_Move.create(IA32_MOV, temp, IC(0))));
      // Perform compare
      EMIT(CPOS(s, MIR_Move.create(IA32_FMOV, new RegisterOperand(FP0, TypeReference.Int), one)));
      EMIT(CPOS(s, MIR_Compare.create(IA32_FCOMI, new RegisterOperand(FP0, TypeReference.Int), two)));
      // res = (value1 > value2) ? 1 : 0
      // temp = ((value1 < value2) || unordered) ? -1 : 0
      EMIT(CPOS(s, MIR_Set.create(IA32_SET__B, res, IA32ConditionOperand
          .LGT())));
      EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_SBB, temp.copyRO(), temp.copyRO())));
    } else {
      RegisterOperand temp2 = burs.ir.regpool.makeTempInt();
      // Perform compare
      EMIT(CPOS(s, MIR_Move.create(IA32_FMOV, new RegisterOperand(FP0, TypeReference.Int), one)));
      EMIT(CPOS(s, MIR_Compare.create(IA32_FCOMI, new RegisterOperand(FP0, TypeReference.Int), two)));
      // res = (value1 > value2) ? 1 : 0
      // temp2 = (value1 unordered value2) ? 1 : 0
      // temp = ((value1 unordered value2) ? 1 : 0) - 0 - CF
      // (i.e. temp = (value1 < value2) ? -1 : 0)
      EMIT(CPOS(s, MIR_Set.create(IA32_SET__B, temp, IA32ConditionOperand
          .PO())));
      EMIT(CPOS(s, MIR_Set.create(IA32_SET__B, res, IA32ConditionOperand
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
  protected final void BOOLEAN_CMP_INT(Instruction s, RegisterOperand res, Operand val1, Operand val2,
                                       ConditionOperand cond) {
    EMIT(CPOS(s, MIR_Compare.create(IA32_CMP, val1, val2)));
    RegisterOperand temp = regpool.makeTemp(TypeReference.Boolean);
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
  protected final void BOOLEAN_CMP_INT(Instruction s, RegisterOperand res, ConditionOperand cond) {
    RegisterOperand temp = regpool.makeTemp(TypeReference.Boolean);
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
  protected final void BOOLEAN_CMP_DOUBLE(Instruction s, RegisterOperand res, ConditionOperand cond,
                                          Operand val1, Operand val2) {
    RegisterOperand temp = regpool.makeTemp(TypeReference.Boolean);
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
  protected final void BOOLEAN_CMP_LONG(Instruction s, RegisterOperand res, Operand val1, Operand val2,
                                        ConditionOperand cond) {
    // Can we simplify to a shift?
    if (cond.isLESS() && val2.isLongConstant() && val2.asLongConstant().value == 0 && val1.isRegister()) {
      // Put the most significant bit of val1 into res
      Register val1_reg = val1.asRegister().getRegister();
      EMIT(MIR_Move.create(IA32_MOV, res.copyRO(), new RegisterOperand(val1_reg, TypeReference.Int)));
      EMIT(MIR_BinaryAcc.mutate(s, IA32_SHR, res, IC(31)));
    } else if (cond.isGREATER_EQUAL() && val2.isLongConstant() && val2.asLongConstant().value == 0 && val1.isRegister()) {
      // Put the most significant bit of val1 into res and invert
      Register val1_reg = val1.asRegister().getRegister();
      EMIT(MIR_Move.create(IA32_MOV, res.copyRO(), new RegisterOperand(val1_reg, TypeReference.Int)));
      EMIT(MIR_BinaryAcc.mutate(s, IA32_SHR, res, IC(31)));
      EMIT(MIR_BinaryAcc.create(IA32_XOR, res.copyRO(), IC(1)));
    } else {
      // Long comparison is a subtraction:
      // <, >= : easy to compute as SF !=/== OF
      // >, <= : flipOperands and treat as a </>=
      // ==/!= : do subtract then OR 2 32-bit quantities test for zero/non-zero
      if (cond.isGREATER() || cond.isLESS_EQUAL()) {
        Operand swap_temp;
        cond.flipOperands();
        swap_temp = val1;
        val1 = val2;
        val2 = swap_temp;
      }
      if (VM.VerifyAssertions) {
        VM._assert(cond.isEQUAL() || cond.isNOT_EQUAL() || cond.isLESS() || cond.isGREATER_EQUAL());
      }
      RegisterOperand one = regpool.makeTempInt();
      RegisterOperand lone = regpool.makeTempInt();
      Operand two, ltwo;
      if (val1 instanceof RegisterOperand) {
        Register val1_reg = val1.asRegister().getRegister();
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV, one, new RegisterOperand(val1_reg, TypeReference.Int))));
        EMIT(CPOS(s,
            MIR_Move.create(IA32_MOV,
                lone,
                new RegisterOperand(regpool.getSecondReg(val1_reg), TypeReference.Int))));
      } else {
        LongConstantOperand tmp = (LongConstantOperand) val1;
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV, one, IC(tmp.upper32()))));
        EMIT(CPOS(s, MIR_Move.create(IA32_MOV, lone, IC(tmp.lower32()))));
      }
      if (val2 instanceof RegisterOperand) {
        two = val2;
        ((RegisterOperand)two).setType(TypeReference.Int);
        ltwo = new RegisterOperand(burs.ir.regpool.getSecondReg(val2.asRegister().getRegister()), TypeReference.Int);
      } else {
        LongConstantOperand tmp = (LongConstantOperand) val2;
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
      RegisterOperand temp = regpool.makeTemp(TypeReference.Boolean);
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
  protected final void LCMP_CMOV(Instruction s, RegisterOperand result, Operand val1, Operand val2,
                                 ConditionOperand cond, Operand trueValue, Operand falseValue) {
    // Long comparison is a subtraction:
    // <, >= : easy to compute as SF !=/== OF
    // >, <= : flipOperands and treat as a </>=
    // ==/!= : do subtract then OR 2 32-bit quantities test for zero/non-zero
    if (cond.isGREATER() || cond.isLESS_EQUAL()) {
      Operand swap_temp;
      cond.flipOperands();
      swap_temp = val1;
      val1 = val2;
      val2 = swap_temp;
    }
    if (VM.VerifyAssertions) {
      VM._assert(cond.isEQUAL() || cond.isNOT_EQUAL() || cond.isLESS() || cond.isGREATER_EQUAL());
    }
    RegisterOperand one = regpool.makeTempInt();
    RegisterOperand lone = regpool.makeTempInt();
    Operand two, ltwo;
    if (val1 instanceof RegisterOperand) {
      Register val1_reg = val1.asRegister().getRegister();
      EMIT(CPOS(s, MIR_Move.create(IA32_MOV, one, new RegisterOperand(val1_reg, TypeReference.Int))));
      EMIT(CPOS(s,
                MIR_Move.create(IA32_MOV,
                                lone,
                                new RegisterOperand(regpool.getSecondReg(val1_reg), TypeReference.Int))));
    } else {
      LongConstantOperand tmp = (LongConstantOperand) val1;
      EMIT(CPOS(s, MIR_Move.create(IA32_MOV, one, IC(tmp.upper32()))));
      EMIT(CPOS(s, MIR_Move.create(IA32_MOV, lone, IC(tmp.lower32()))));
    }
    if (val2 instanceof RegisterOperand) {
      two = val2;
      ((RegisterOperand)two).setType(TypeReference.Int);
      ltwo = new RegisterOperand(burs.ir.regpool.getSecondReg(val2.asRegister().getRegister()), TypeReference.Int);
    } else {
      LongConstantOperand tmp = (LongConstantOperand) val2;
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
  protected final void IFCMP(Instruction s, RegisterOperand guardResult, Operand val1, Operand val2,
                             ConditionOperand cond) {
    if (VM.VerifyAssertions) {
      // We only need make sure the guard information is correct when
      // validating, the null check combining phase removes all guards
      EMIT(CPOS(s, Move.create(GUARD_MOVE, guardResult, new TrueGuardOperand())));
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
  protected final void CMOV_MOV(Instruction s, RegisterOperand result, ConditionOperand cond,
                                Operand trueValue, Operand falseValue) {
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
          int true_const = ((IntConstantOperand) trueValue).value;
          int false_const = ((IntConstantOperand) falseValue).value;
          if (cond.isLOWER()) {
            // Comparison sets carry flag so use to avoid setb, movzx
            // result = cond ? -1 : 0
            EMIT(CPOS(s, MIR_BinaryAcc.mutate(s, IA32_SBB, result, result.copyRO())));
            if (true_const - false_const != -1) {
              if (true_const - false_const == 1) {
                EMIT(CPOS(s, MIR_UnaryAcc.create(IA32_NEG, result.copyRO())));
              } else {
                EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_AND, result.copyRO(), IC(true_const - false_const))));
              }
            }
            if (false_const != 0) {
              EMIT(MIR_BinaryAcc.create(IA32_ADD, result.copyRO(), IC(false_const)));
            }
          } else if(cond.isHIGHER_EQUAL()) {
            // Comparison sets carry flag so use to avoid setb, movzx
            // result = cond ? 0 : -1
            EMIT(CPOS(s, MIR_BinaryAcc.mutate(s, IA32_SBB, result, result.copyRO())));
            if (false_const - true_const != -1) {
              if (false_const - true_const == 1) {
                EMIT(CPOS(s, MIR_UnaryAcc.create(IA32_NEG, result.copyRO())));
              } else {
                EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_AND, result.copyRO(), IC(false_const - true_const))));
              }
            }
            if (true_const != 0) {
              EMIT(MIR_BinaryAcc.create(IA32_ADD, result, IC(true_const)));
            }
          } else {
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
  protected final void CMOV_FMOV(Instruction s, RegisterOperand result, ConditionOperand cond,
                                 Operand trueValue, Operand falseValue) {
    RegisterOperand FP0 = new RegisterOperand(burs.ir.regpool.getPhysicalRegisterSet().getFPR(0), result.getType());
    // need to handle both possible assignments. Unconditionally
    // assign one value then conditionally assign the other.
    if (falseValue.isRegister()) {
      EMIT(CPOS(s, MIR_Move.create(IA32_FMOV, FP0, trueValue)));
      EMIT(MIR_CondMove.mutate(s, IA32_FCMOV, FP0.copyRO(), falseValue, COND(cond.flipCode())));
    } else {
      EMIT(CPOS(s, MIR_Move.create(IA32_FMOV, FP0, falseValue)));
      EMIT(MIR_CondMove.mutate(s, IA32_FCMOV, FP0.copyRO(), asReg(s, IA32_FMOV, trueValue), COND(cond)));
    }
    EMIT(CPOS(s, MIR_Move.create(IA32_FMOV, result.copyRO(), FP0.copyRO())));
  }

  /**
   * Expand a prologue by expanding out longs into pairs of ints
   */
  protected final void PROLOGUE(Instruction s) {
    int numFormals = Prologue.getNumberOfFormals(s);
    int numLongs = 0;
    for (int i = 0; i < numFormals; i++) {
      if (Prologue.getFormal(s, i).getType().isLongType()) {
        numLongs++;
      }
    }
    if (numLongs != 0) {
      Instruction s2 = Prologue.create(IR_PROLOGUE, numFormals + numLongs);
      for (int sidx = 0, s2idx = 0; sidx < numFormals; sidx++) {
        RegisterOperand sForm = Prologue.getFormal(s, sidx);
        if (sForm.getType().isLongType()) {
          sForm.setType(TypeReference.Int);
          Prologue.setFormal(s2, s2idx++, sForm);
          Register r2 = regpool.getSecondReg(sForm.getRegister());
          Prologue.setFormal(s2, s2idx++, new RegisterOperand(r2, TypeReference.Int));
          sForm.getRegister().clearType();
          sForm.getRegister().setInteger();
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
  protected final void CALL(Instruction s, Operand address) {
    // Step 1: Find out how many parameters we're going to have.
    int numParams = Call.getNumberOfParams(s);
    int longParams = 0;
    for (int pNum = 0; pNum < numParams; pNum++) {
      if (Call.getParam(s, pNum).getType().isLongType()) {
        longParams++;
      }
    }

    // Step 2: Figure out what the result and result2 values will be.
    RegisterOperand result = Call.getResult(s);
    RegisterOperand result2 = null;
    if (result != null && result.getType().isLongType()) {
      result.setType(TypeReference.Int);
      result2 = new RegisterOperand(regpool.getSecondReg(result.getRegister()), TypeReference.Int);
    }

    // Step 3: Mutate the Call to an MIR_Call.
    // Note MIR_Call and Call have a different number of fixed
    // arguments, so some amount of copying is required.
    Operand[] params = new Operand[numParams];
    for (int i = 0; i < numParams; i++) {
      params[i] = Call.getParam(s, i);
    }
    MIR_Call.mutate(s, IA32_CALL, result, result2, address, Call.getMethod(s), numParams + longParams);
    for (int paramIdx = 0, mirCallIdx = 0; paramIdx < numParams;) {
      Operand param = params[paramIdx++];
      if (param instanceof RegisterOperand) {
        RegisterOperand rparam = (RegisterOperand) param;
        MIR_Call.setParam(s, mirCallIdx++, rparam);
        if (rparam.getType().isLongType()) {
          rparam.setType(TypeReference.Int);
          MIR_Call.setParam(s, mirCallIdx-1, rparam);
          MIR_Call.setParam(s, mirCallIdx++,
            new RegisterOperand(regpool.getSecondReg(rparam.getRegister()), TypeReference.Int));
        }
      } else if (param instanceof LongConstantOperand) {
        LongConstantOperand val = (LongConstantOperand) param;
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
  protected final void SYSCALL(Instruction s, Operand address) {
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
    RegisterOperand result = Call.getResult(s);
    RegisterOperand result2 = null;
    // NOTE: C callee returns longs little endian!
    if (result != null && result.getType().isLongType()) {
      result.setType(TypeReference.Int);
      result2 = result;
      result = new RegisterOperand(regpool.getSecondReg(result.getRegister()), TypeReference.Int);
    }

    // Step 3: Mutate the Call to an MIR_Call.
    // Note MIR_Call and Call have a different number of fixed
    // arguments, so some amount of copying is required.
    Operand[] params = new Operand[numParams];
    for (int i = 0; i < numParams; i++) {
      params[i] = Call.getParam(s, i);
    }
    MIR_Call.mutate(s, IA32_SYSCALL, result, result2, address, Call
        .getMethod(s), numParams + longParams);
    for (int paramIdx = 0, mirCallIdx = 0; paramIdx < numParams;) {
      Operand param = params[paramIdx++];
      if (param instanceof RegisterOperand) {
        // NOTE: longs passed little endian to C callee!
        RegisterOperand rparam = (RegisterOperand) param;
        if (rparam.getType().isLongType()) {
          rparam.setType(TypeReference.Int);
          MIR_Call.setParam(s, mirCallIdx++,
            new RegisterOperand(regpool.getSecondReg(rparam.getRegister()), TypeReference.Int));
        }
        MIR_Call.setParam(s, mirCallIdx++, param);
      } else if (param instanceof LongConstantOperand) {
        long value = ((LongConstantOperand) param).value;
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
  protected final void LOWTABLESWITCH(Instruction s) {
    // (1) We're changing index from a U to a DU.
    // Inject a fresh copy instruction to make sure we aren't
    // going to get into trouble (if someone else was also using index).
    RegisterOperand newIndex = regpool.makeTempInt();
    EMIT(CPOS(s, MIR_Move.create(IA32_MOV, newIndex, LowTableSwitch.getIndex(s))));
    RegisterOperand methodStart = regpool.makeTemp(TypeReference.Address);
    EMIT(CPOS(s, MIR_Nullary.create(IA32_METHODSTART, methodStart)));
    int number = LowTableSwitch.getNumberOfTargets(s);
    Instruction s2 = CPOS(s, MIR_LowTableSwitch.create(MIR_LOWTABLESWITCH, newIndex.copyRO(), methodStart.copyD2U(), number * 2));
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
  protected final void RESOLVE(Instruction s) {
    Operand target = loadFromJTOC(Entrypoints.optResolveMethod.getOffset(), DW);
    EMIT(CPOS(s,
              MIR_Call.mutate0(s,
                               CALL_SAVE_VOLATILE,
                               null,
                               null,
                               target,
                               MethodOperand.STATIC(Entrypoints.optResolveMethod))));
  }

  /**
   * Expansion of TRAP_IF, with an int constant as the second value.
   *
   * @param s the instruction to expand
   * @param longConstant is the argument a long constant?
   */
  protected final void TRAP_IF_IMM(Instruction s, boolean longConstant) {
    RegisterOperand gRes = TrapIf.getGuardResult(s);
    RegisterOperand v1 = (RegisterOperand) TrapIf.getVal1(s);
    ConstantOperand v2 = (ConstantOperand) TrapIf.getVal2(s);
    ConditionOperand cond = TrapIf.getCond(s);
    TrapCodeOperand tc = TrapIf.getTCode(s);

    // A slightly ugly matter, but we need to deal with combining
    // the two pieces of a long register from a LONG_ZERO_CHECK.
    // A little awkward, but probably the easiest workaround...
    if (longConstant) {
      if (VM.VerifyAssertions) {
        VM._assert((tc.getTrapCode() == RuntimeEntrypoints.TRAP_DIVIDE_BY_ZERO) &&
                   (((LongConstantOperand) v2).value == 0L));
      }
      RegisterOperand vr = v1.copyRO();
      vr.setType(TypeReference.Int);
      RegisterOperand rr = regpool.makeTempInt();
      EMIT(CPOS(s, MIR_Move.create(IA32_MOV, rr, vr)));
      EMIT(CPOS(s, MIR_BinaryAcc.create(IA32_OR,
                                rr.copy(),
                                new RegisterOperand(regpool.getSecondReg(v1.getRegister()), TypeReference.Int))));
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
  protected final void ATTEMPT(RegisterOperand result, MemoryOperand mo, Operand oldValue,
                               Operand newValue) {
    RegisterOperand temp = regpool.makeTempInt();
    RegisterOperand temp2 = regpool.makeTemp(result);
    EMIT(MIR_Move.create(IA32_MOV, temp, newValue));
    EMIT(MIR_Move.create(IA32_MOV, new RegisterOperand(getEAX(), TypeReference.Int), oldValue));
    EMIT(MIR_CompareExchange.create(IA32_LOCK_CMPXCHG,
                                    new RegisterOperand(getEAX(), TypeReference.Int),
                                    mo,
                                    temp.copyRO()));
    EMIT(MIR_Set.create(IA32_SET__B, temp2, IA32ConditionOperand.EQ()));
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
  protected final void ATTEMPT_LONG(RegisterOperand result,
                                    MemoryOperand mo,
                                    Operand oldValue,
                                    Operand newValue) {
    // Set up EDX:EAX with the old value
    if (oldValue.isRegister()) {
      Register oldValue_hval = oldValue.asRegister().getRegister();
      Register oldValue_lval = regpool.getSecondReg(oldValue_hval);
      EMIT(MIR_Move.create(IA32_MOV, new RegisterOperand(getEDX(), TypeReference.Int),
          new RegisterOperand(oldValue_hval, TypeReference.Int)));
      EMIT(MIR_Move.create(IA32_MOV, new RegisterOperand(getEAX(), TypeReference.Int),
          new RegisterOperand(oldValue_lval, TypeReference.Int)));
    } else {
      if (VM.VerifyAssertions) VM._assert(oldValue.isLongConstant());
      LongConstantOperand val = oldValue.asLongConstant();
      EMIT(MIR_Move.create(IA32_MOV, new RegisterOperand(getEDX(), TypeReference.Int),
          IC(val.upper32())));
      EMIT(MIR_Move.create(IA32_MOV, new RegisterOperand(getEAX(), TypeReference.Int),
          IC(val.lower32())));
    }

    // Set up ECX:EBX with the new value
    if (newValue.isRegister()) {
      Register newValue_hval = newValue.asRegister().getRegister();
      Register newValue_lval = regpool.getSecondReg(newValue_hval);
      EMIT(MIR_Move.create(IA32_MOV, new RegisterOperand(getECX(), TypeReference.Int),
          new RegisterOperand(newValue_hval, TypeReference.Int)));
      EMIT(MIR_Move.create(IA32_MOV, new RegisterOperand(getEBX(), TypeReference.Int),
          new RegisterOperand(newValue_lval, TypeReference.Int)));
    } else {
      if (VM.VerifyAssertions) VM._assert(newValue.isLongConstant());
      LongConstantOperand val = newValue.asLongConstant();
      EMIT(MIR_Move.create(IA32_MOV, new RegisterOperand(getECX(), TypeReference.Int),
          IC(val.upper32())));
      EMIT(MIR_Move.create(IA32_MOV, new RegisterOperand(getEBX(), TypeReference.Int),
          IC(val.lower32())));
    }

    EMIT(MIR_CompareExchange8B.create(IA32_LOCK_CMPXCHG8B,
         new RegisterOperand(getEDX(), TypeReference.Int),
         new RegisterOperand(getEAX(), TypeReference.Int),
         mo,
         new RegisterOperand(getECX(), TypeReference.Int),
         new RegisterOperand(getEBX(), TypeReference.Int)));

    RegisterOperand temp = regpool.makeTemp(result);
    EMIT(MIR_Set.create(IA32_SET__B, temp, IA32ConditionOperand.EQ()));
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
  protected final void ATTEMPT_IFCMP(MemoryOperand mo, Operand oldValue, Operand newValue,
                                     ConditionOperand cond, BranchOperand target, BranchProfileOperand bp) {
    RegisterOperand temp = regpool.makeTempInt();
    EMIT(MIR_Move.create(IA32_MOV, temp, newValue));
    EMIT(MIR_Move.create(IA32_MOV, new RegisterOperand(getEAX(), TypeReference.Int), oldValue));
    EMIT(MIR_CompareExchange.create(IA32_LOCK_CMPXCHG,
                                    new RegisterOperand(getEAX(), TypeReference.Int),
                                    mo,
                                    temp.copyRO()));
    EMIT(MIR_CondBranch.create(IA32_JCC, COND(cond), target, bp));
  }

  /*
   * special case handling OSR instructions expand long type variables to two
   * intergers
   */
  void OSR(BURS burs, Instruction s) {
    if (VM.VerifyAssertions) {
      VM._assert(OsrPoint.conforms(s));
    }

    // 1. how many params
    int numparam = OsrPoint.getNumberOfElements(s);
    int numlong = 0;
    for (int i = 0; i < numparam; i++) {
      Operand param = OsrPoint.getElement(s, i);
      if (param.getType().isLongType()) {
        numlong++;
      }
    }

    // 2. collect params
    InlinedOsrTypeInfoOperand typeInfo = OsrPoint
        .getClearInlinedTypeInfo(s);

    if (VM.VerifyAssertions) {
      if (typeInfo == null) {
        VM.sysWriteln("OsrPoint " + s + " has a <null> type info:");
        VM.sysWriteln("  position :" + s.bcIndex + "@" + s.position.method);
      }
      VM._assert(typeInfo != null);
    }

    Operand[] params = new Operand[numparam];
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
      Operand param = params[i];
      OsrPoint.setElement(s, i, param);
      if (param instanceof RegisterOperand) {
        RegisterOperand rparam = (RegisterOperand) param;
        // the second half is appended at the end
        // LinearScan will update the map.
        if (rparam.getType().isLongType()) {
          OsrPoint.setElement(s, pidx++, L(burs.ir.regpool
              .getSecondReg(rparam.getRegister())));
        }
      } else if (param instanceof LongConstantOperand) {
        LongConstantOperand val = (LongConstantOperand) param;

        if (VM.TraceOnStackReplacement) {
          VM.sysWriteln("caught a long const " + val);
        }

        OsrPoint.setElement(s, i, IC(val.upper32()));
        OsrPoint.setElement(s, pidx++, IC(val.lower32()));
      } else if (param instanceof IntConstantOperand) {
      } else {
        throw new OptimizingCompilerException("BURS_Helpers", "unexpected parameter type" + param);
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
