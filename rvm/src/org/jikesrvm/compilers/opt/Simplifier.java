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
package org.jikesrvm.compilers.opt;

import static org.jikesrvm.SizeConstants.BITS_IN_ADDRESS;
import static org.jikesrvm.SizeConstants.BITS_IN_INT;
import static org.jikesrvm.SizeConstants.BITS_IN_LONG;
import static org.jikesrvm.SizeConstants.LOG_BYTES_IN_ADDRESS;
import static org.jikesrvm.compilers.opt.ir.Operators.*;

import java.lang.reflect.Array;
import java.lang.reflect.Method;

import org.jikesrvm.VM;
import org.jikesrvm.ArchitectureSpecific.CodeArray;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.driver.OptConstants;
import org.jikesrvm.compilers.opt.inlining.InlineSequence;
import org.jikesrvm.compilers.opt.ir.AbstractRegisterPool;
import org.jikesrvm.compilers.opt.ir.Binary;
import org.jikesrvm.compilers.opt.ir.BooleanCmp;
import org.jikesrvm.compilers.opt.ir.BoundsCheck;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.CondMove;
import org.jikesrvm.compilers.opt.ir.Empty;
import org.jikesrvm.compilers.opt.ir.GetField;
import org.jikesrvm.compilers.opt.ir.GuardedBinary;
import org.jikesrvm.compilers.opt.ir.GuardedUnary;
import org.jikesrvm.compilers.opt.ir.IRTools;
import org.jikesrvm.compilers.opt.ir.InstanceOf;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Load;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.NullCheck;
import org.jikesrvm.compilers.opt.ir.Operator;
import org.jikesrvm.compilers.opt.ir.OperatorNames;
import org.jikesrvm.compilers.opt.ir.StoreCheck;
import org.jikesrvm.compilers.opt.ir.Trap;
import org.jikesrvm.compilers.opt.ir.TrapIf;
import org.jikesrvm.compilers.opt.ir.TypeCheck;
import org.jikesrvm.compilers.opt.ir.Unary;
import org.jikesrvm.compilers.opt.ir.ZeroCheck;
import org.jikesrvm.compilers.opt.ir.operand.AddressConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.BranchProfileOperand;
import org.jikesrvm.compilers.opt.ir.operand.CodeConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.ConditionOperand;
import org.jikesrvm.compilers.opt.ir.operand.ConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.LongConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.MethodOperand;
import org.jikesrvm.compilers.opt.ir.operand.NullConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.ObjectConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.TIBConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.TrapCodeOperand;
import org.jikesrvm.compilers.opt.ir.operand.TrueGuardOperand;
import org.jikesrvm.compilers.opt.ir.operand.TypeOperand;
import org.jikesrvm.compilers.opt.ir.operand.UnreachableOperand;
import org.jikesrvm.objectmodel.TIB;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Reflection;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * A constant folder, strength reducer and axiomatic simplifier.
 *
 * <p> This module performs no analysis, it simply attempts to
 * simplify the instruction as is. The intent is that
 * analysis modules can call this transformation engine, allowing us to
 * share the tedious simplification code among multiple analysis modules.
 *
 * <p> NOTE: For maintainability purposes, I've intentionally avoided being
 * clever about combining 'similar' operators together into a combined case
 * of the main switch switch statement. Also, operators are in sorted ordered
 * within each major grouping.  Please maintain this coding style.
 * I'd rather have this module be 2000 lines of obviously correct code than
 * 500 lines of clever code.
 */
public abstract class Simplifier extends IRTools {
  /**
   * Effect of the simplification on Def-Use chains
   */
  public enum DefUseEffect {
    /**
     * Enumeration value to indicate an operation is unchanged,
     * although the order of operands may have been canonicalized and
     * type information strengthened.
     */
    UNCHANGED,
    /**
     * Enumeration value to indicate an operation has been replaced by
     * a move instruction with a constant right hand side.
     */
    MOVE_FOLDED,
    /**
     * Enumeration value to indicate an operation has been replaced by
     * a move instruction with a non-constant right hand side.
     */
    MOVE_REDUCED,
    /**
     * Enumeration value to indicate an operation has been replaced by
     * an unconditional trap instruction.
     */
    TRAP_REDUCED,
    /**
     * Enumeration value to indicate an operation has been replaced by
     * a cheaper, but non-move instruction.
     */
    REDUCED
  }

  /**
   * Given an instruction, attempt to simplify it.
   * The instruction will be mutated in place.
   *
   * <p> We don't deal with branching operations here --
   * doing peephole optimizations of branches
   * is the job of a separate module.
   *
   * @param hir is this the HIR phase?
   * @param regpool register pool in case simplification requires a temporary register
   * @param opts options for this compilation
   * @param s the instruction to simplify
   * @return one of UNCHANGED, MOVE_FOLDED, MOVE_REDUCED, TRAP_REDUCED, REDUCED
   */
  public static DefUseEffect simplify(boolean hir, AbstractRegisterPool regpool, OptOptions opts, Instruction s) {
    DefUseEffect result;
    char opcode = s.getOpcode();
    switch (opcode) {
      ////////////////////
      // GUARD operations
      ////////////////////
      case GUARD_COMBINE_opcode:
        result = guardCombine(s, opts);
        break;
        ////////////////////
        // TRAP operations
        ////////////////////
      case TRAP_IF_opcode:
        result = trapIf(s, opts);
        break;
      case NULL_CHECK_opcode:
        result = nullCheck(s, opts);
        break;
      case INT_ZERO_CHECK_opcode:
        result = intZeroCheck(s, opts);
        break;
      case LONG_ZERO_CHECK_opcode:
        result = longZeroCheck(s, opts);
        break;
      case CHECKCAST_opcode:
        result = checkcast(s, opts);
        break;
      case CHECKCAST_UNRESOLVED_opcode:
        result = checkcast(s, opts);
        break;
      case CHECKCAST_NOTNULL_opcode:
        result = checkcastNotNull(s, opts);
        break;
      case INSTANCEOF_opcode:
        result = instanceOf(s, opts);
        break;
      case INSTANCEOF_NOTNULL_opcode:
        result = instanceOfNotNull(s, opts);
        break;
      case OBJARRAY_STORE_CHECK_opcode:
        result = objarrayStoreCheck(s, opts);
        break;
      case OBJARRAY_STORE_CHECK_NOTNULL_opcode:
        result = objarrayStoreCheckNotNull(s, opts);
        break;
      case MUST_IMPLEMENT_INTERFACE_opcode:
        result = mustImplementInterface(s, opts);
        break;
        ////////////////////
        // Conditional moves
        ////////////////////
      case INT_COND_MOVE_opcode:
        result = intCondMove(s, opts);
        break;
      case LONG_COND_MOVE_opcode:
        result = longCondMove(s, opts);
        break;
      case FLOAT_COND_MOVE_opcode:
        result = floatCondMove(s, opts);
        break;
      case DOUBLE_COND_MOVE_opcode:
        result = doubleCondMove(s, opts);
        break;
      case REF_COND_MOVE_opcode:
        result = refCondMove(s, opts);
        break;
      case GUARD_COND_MOVE_opcode:
        result = guardCondMove(s, opts);
        break;
        ////////////////////
        // INT ALU operations
        ////////////////////
      case BOOLEAN_NOT_opcode:
        result = booleanNot(s, opts);
        break;
      case BOOLEAN_CMP_INT_opcode:
        result = booleanCmpInt(s, opts);
        break;
      case BOOLEAN_CMP_ADDR_opcode:
        result = booleanCmpAddr(s, opts);
        break;
      case INT_ADD_opcode:
        result = intAdd(s, opts);
        break;
      case INT_AND_opcode:
        result = intAnd(s, opts);
        break;
      case INT_DIV_opcode:
        result = intDiv(regpool, s, opts);
        break;
      case INT_MUL_opcode:
        result = intMul(regpool, s, opts);
        break;
      case INT_NEG_opcode:
        result = intNeg(s, opts);
        break;
      case INT_NOT_opcode:
        result = intNot(s, opts);
        break;
      case INT_OR_opcode:
        result = intOr(s, opts);
        break;
      case INT_REM_opcode:
        result = intRem(s, opts);
        break;
      case INT_SHL_opcode:
        result = intShl(s, opts);
        break;
      case INT_SHR_opcode:
        result = intShr(s, opts);
        break;
      case INT_SUB_opcode:
        result = intSub(s, opts);
        break;
      case INT_USHR_opcode:
        result = intUshr(s, opts);
        break;
      case INT_XOR_opcode:
        result = intXor(s, opts);
        break;
        ////////////////////
        // WORD ALU operations
        ////////////////////
      case REF_ADD_opcode:
        result = refAdd(s, opts);
        break;
      case REF_AND_opcode:
        result = refAnd(s, opts);
        break;
      case REF_SHL_opcode:
        result = refShl(s, opts);
        break;
      case REF_SHR_opcode:
        result = refShr(s, opts);
        break;
      case REF_NEG_opcode:
        result = refNeg(s, opts);
        break;
      case REF_NOT_opcode:
        result = refNot(s, opts);
        break;
      case REF_OR_opcode:
        result = refOr(s, opts);
        break;
      case REF_SUB_opcode:
        result = refSub(s, opts);
        break;
      case REF_USHR_opcode:
        result = refUshr(s, opts);
        break;
      case REF_XOR_opcode:
        result = refXor(s, opts);
        break;
        ////////////////////
        // LONG ALU operations
        ////////////////////
      case LONG_ADD_opcode:
        result = longAdd(s, opts);
        break;
      case LONG_AND_opcode:
        result = longAnd(s, opts);
        break;
      case LONG_CMP_opcode:
        result = longCmp(s, opts);
        break;
      case LONG_DIV_opcode:
        result = longDiv(s, opts);
        break;
      case LONG_MUL_opcode:
        result = longMul(regpool, s, opts);
        break;
      case LONG_NEG_opcode:
        result = longNeg(s, opts);
        break;
      case LONG_NOT_opcode:
        result = longNot(s, opts);
        break;
      case LONG_OR_opcode:
        result = longOr(s, opts);
        break;
      case LONG_REM_opcode:
        result = longRem(s, opts);
        break;
      case LONG_SHL_opcode:
        result = longShl(s, opts);
        break;
      case LONG_SHR_opcode:
        result = longShr(s, opts);
        break;
      case LONG_SUB_opcode:
        result = longSub(s, opts);
        break;
      case LONG_USHR_opcode:
        result = longUshr(s, opts);
        break;
      case LONG_XOR_opcode:
        result = longXor(s, opts);
        break;
        ////////////////////
        // FLOAT ALU operations
        ////////////////////
      case FLOAT_ADD_opcode:
        result = floatAdd(s, opts);
        break;
      case FLOAT_CMPG_opcode:
        result = floatCmpg(s, opts);
        break;
      case FLOAT_CMPL_opcode:
        result = floatCmpl(s, opts);
        break;
      case FLOAT_DIV_opcode:
        result = floatDiv(s, opts);
        break;
      case FLOAT_MUL_opcode:
        result = floatMul(s, opts);
        break;
      case FLOAT_NEG_opcode:
        result = floatNeg(s, opts);
        break;
      case FLOAT_REM_opcode:
        result = floatRem(s, opts);
        break;
      case FLOAT_SUB_opcode:
        result = floatSub(s, opts);
        break;
      case FLOAT_SQRT_opcode:
        result = floatSqrt(s, opts);
        break;
        ////////////////////
        // DOUBLE ALU operations
        ////////////////////
      case DOUBLE_ADD_opcode:
        result = doubleAdd(s, opts);
        break;
      case DOUBLE_CMPG_opcode:
        result = doubleCmpg(s, opts);
        break;
      case DOUBLE_CMPL_opcode:
        result = doubleCmpl(s, opts);
        break;
      case DOUBLE_DIV_opcode:
        result = doubleDiv(s, opts);
        break;
      case DOUBLE_MUL_opcode:
        result = doubleMul(s, opts);
        break;
      case DOUBLE_NEG_opcode:
        result = doubleNeg(s, opts);
        break;
      case DOUBLE_REM_opcode:
        result = doubleRem(s, opts);
        break;
      case DOUBLE_SUB_opcode:
        result = doubleSub(s, opts);
        break;
      case DOUBLE_SQRT_opcode:
        result = doubleSqrt(s, opts);
        break;
        ////////////////////
        // CONVERSION operations
        ////////////////////
      case DOUBLE_2FLOAT_opcode:
        result = double2Float(s, opts);
        break;
      case DOUBLE_2INT_opcode:
        result = double2Int(s, opts);
        break;
      case DOUBLE_2LONG_opcode:
        result = double2Long(s, opts);
        break;
      case DOUBLE_AS_LONG_BITS_opcode:
        result = doubleAsLongBits(s, opts);
        break;
      case INT_2DOUBLE_opcode:
        result = int2Double(s, opts);
        break;
      case INT_2BYTE_opcode:
        result = int2Byte(s, opts);
        break;
      case INT_2USHORT_opcode:
        result = int2UShort(s, opts);
        break;
      case INT_2FLOAT_opcode:
        result = int2Float(s, opts);
        break;
      case INT_2LONG_opcode:
        result = int2Long(s, opts);
        break;
      case INT_2ADDRSigExt_opcode:
        result = int2AddrSigExt(s, opts);
        break;
      case INT_2ADDRZerExt_opcode:
        result = int2AddrZerExt(s, opts);
        break;
      case LONG_2ADDR_opcode:
        result = long2Addr(s, opts);
        break;
      case INT_2SHORT_opcode:
        result = int2Short(s, opts);
        break;
      case INT_BITS_AS_FLOAT_opcode:
        result = intBitsAsFloat(s, opts);
        break;
      case ADDR_2INT_opcode:
        result = addr2Int(s, opts);
        break;
      case ADDR_2LONG_opcode:
        result = addr2Long(s, opts);
        break;
      case FLOAT_2DOUBLE_opcode:
        result = float2Double(s, opts);
        break;
      case FLOAT_2INT_opcode:
        result = float2Int(s, opts);
        break;
      case FLOAT_2LONG_opcode:
        result = float2Long(s, opts);
        break;
      case FLOAT_AS_INT_BITS_opcode:
        result = floatAsIntBits(s, opts);
        break;
      case LONG_2FLOAT_opcode:
        result = long2Float(s, opts);
        break;
      case LONG_2INT_opcode:
        result = long2Int(s, opts);
        break;
      case LONG_2DOUBLE_opcode:
        result = long2Double(s, opts);
        break;
      case LONG_BITS_AS_DOUBLE_opcode:
        result = longBitsAsDouble(s, opts);
        break;
        ////////////////////
        // Field operations
        ////////////////////
      case ARRAYLENGTH_opcode:
        result = arrayLength(s, opts);
        break;
      case BOUNDS_CHECK_opcode:
        result = boundsCheck(s, opts);
        break;
      case CALL_opcode:
        result = call(hir, regpool, s, opts);
        break;
      case GETFIELD_opcode:
        result = getField(s, opts);
        break;
      case GET_OBJ_TIB_opcode:
        result = getObjTib(s, opts);
        break;
      case GET_CLASS_TIB_opcode:
        result = getClassTib(s, opts);
        break;
      case GET_TYPE_FROM_TIB_opcode:
        result = getTypeFromTib(s, opts);
        break;
      case GET_ARRAY_ELEMENT_TIB_FROM_TIB_opcode:
        result = getArrayElementTibFromTib(s, opts);
        break;
      case GET_SUPERCLASS_IDS_FROM_TIB_opcode:
        result = getSuperclassIdsFromTib(s, opts);
        break;
      case GET_DOES_IMPLEMENT_FROM_TIB_opcode:
        result = getDoesImplementFromTib(s, opts);
        break;
      case REF_LOAD_opcode:
        result = refLoad(s, opts);
        break;
      default:
        result = DefUseEffect.UNCHANGED;
    }
    if (VM.VerifyAssertions) {
      switch (result) {
        case MOVE_FOLDED:
          // Check move has constant RHS
          VM._assert(Move.conforms(s) && (Move.getVal(s) instanceof ConstantOperand),
                     "RHS of move " +
                     s +
                     " should be constant during simplification of " +
                     OperatorNames.operatorName[opcode]);
          break;
        case MOVE_REDUCED:
          // Check move has non-constant RHS
          VM._assert(Move.conforms(s) && !(Move.getVal(s) instanceof ConstantOperand),
                     "RHS of move " +
                     s +
                     " shouldn't be constant during simplification of " +
                     OperatorNames.operatorName[opcode]);
          break;
        default:
          // Nothing to check
      }
    }
    return result;
  }

  private static DefUseEffect guardCombine(Instruction s, OptOptions opts) {
    Operand op1 = Binary.getVal1(s);
    Operand op2 = Binary.getVal2(s);
    if (op1.similar(op2) || (op2 instanceof TrueGuardOperand)) {
      Move.mutate(s, GUARD_MOVE, Binary.getClearResult(s), op1);
      if (op1 instanceof TrueGuardOperand) {
        // BOTH true guards: FOLD
        return DefUseEffect.MOVE_FOLDED;
      } else {
        // ONLY OP2 IS TrueGuard: MOVE REDUCE
        return DefUseEffect.MOVE_REDUCED;
      }
    } else if (op1 instanceof TrueGuardOperand) {
      // ONLY OP1 IS TrueGuard: MOVE REDUCE
      Move.mutate(s, GUARD_MOVE, Binary.getClearResult(s), op2);
      return DefUseEffect.MOVE_REDUCED;
    } else {
      return DefUseEffect.UNCHANGED;
    }
  }

  private static DefUseEffect trapIf(Instruction s, OptOptions opts) {
    {
      Operand op1 = TrapIf.getVal1(s);
      Operand op2 = TrapIf.getVal2(s);
      if (op1.isConstant()) {
        if (op2.isConstant()) {
          int willTrap = TrapIf.getCond(s).evaluate(op1, op2);
          if (willTrap == ConditionOperand.TRUE) {
            Trap.mutate(s, TRAP, TrapIf.getClearGuardResult(s), TrapIf.getClearTCode(s));
            return DefUseEffect.TRAP_REDUCED;
          } else if (willTrap == ConditionOperand.FALSE) {
            Move.mutate(s, GUARD_MOVE, TrapIf.getClearGuardResult(s), TG());
            return DefUseEffect.MOVE_FOLDED;
          }
        } else {
          // canonicalize
          TrapIf.mutate(s,
                        TRAP_IF,
                        TrapIf.getClearGuardResult(s),
                        TrapIf.getClearVal2(s),
                        TrapIf.getClearVal1(s),
                        TrapIf.getClearCond(s).flipOperands(),
                        TrapIf.getClearTCode(s));
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect nullCheck(Instruction s, OptOptions opts) {
    Operand ref = NullCheck.getRef(s);
    if (ref.isNullConstant() || (ref.isAddressConstant() && ref.asAddressConstant().value.isZero())) {
      Trap.mutate(s, TRAP, NullCheck.getClearGuardResult(s), TrapCodeOperand.NullPtr());
      return DefUseEffect.TRAP_REDUCED;
    } else if (ref.isConstant()) {
      // object, string, class or non-null address constant

      // Make the slightly suspect assumption that all non-zero address
      // constants are actually valid pointers. Not necessarily true,
      // but unclear what else we can do.
      Move.mutate(s, GUARD_MOVE, NullCheck.getClearGuardResult(s), TG());
      return DefUseEffect.MOVE_FOLDED;
    } else {
      return DefUseEffect.UNCHANGED;
    }
  }

  private static DefUseEffect intZeroCheck(Instruction s, OptOptions opts) {
    {
      Operand op = ZeroCheck.getValue(s);
      if (op.isIntConstant()) {
        int val = op.asIntConstant().value;
        if (val == 0) {
          Trap.mutate(s, TRAP, ZeroCheck.getClearGuardResult(s), TrapCodeOperand.DivByZero());
          return DefUseEffect.TRAP_REDUCED;
        } else {
          Move.mutate(s, GUARD_MOVE, ZeroCheck.getClearGuardResult(s), TG());
          return DefUseEffect.MOVE_FOLDED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect longZeroCheck(Instruction s, OptOptions opts) {
    {
      Operand op = ZeroCheck.getValue(s);
      if (op.isLongConstant()) {
        long val = op.asLongConstant().value;
        if (val == 0L) {
          Trap.mutate(s, TRAP, ZeroCheck.getClearGuardResult(s), TrapCodeOperand.DivByZero());
          return DefUseEffect.TRAP_REDUCED;
        } else {
          Move.mutate(s, GUARD_MOVE, ZeroCheck.getClearGuardResult(s), TG());
          return DefUseEffect.MOVE_FOLDED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect checkcast(Instruction s, OptOptions opts) {
    Operand ref = TypeCheck.getRef(s);
    if (ref.isNullConstant()) {
      Move.mutate(s, REF_MOVE, TypeCheck.getResult(s), ref);
      if (ref.isConstant())
        return DefUseEffect.MOVE_FOLDED;
      else
        return DefUseEffect.MOVE_REDUCED;
    } else if (ref.isConstant()) {
      s.operator = CHECKCAST_NOTNULL;
      return checkcastNotNull(s, opts);
    } else {
      TypeReference lhsType = TypeCheck.getType(s).getTypeRef();
      TypeReference rhsType = ref.getType();
      byte ans = ClassLoaderProxy.includesType(lhsType, rhsType);
      if (ans == OptConstants.YES) {
        Move.mutate(s, REF_MOVE, TypeCheck.getResult(s), ref);
        if (ref.isConstant())
          return DefUseEffect.MOVE_FOLDED;
        else
          return DefUseEffect.MOVE_REDUCED;
      } else {
        // NOTE: Constants.NO can't help us because (T)null always succeeds
        return DefUseEffect.UNCHANGED;
      }
    }
  }

  private static DefUseEffect checkcastNotNull(Instruction s, OptOptions opts) {
    Operand ref = TypeCheck.getRef(s);
    TypeReference lhsType = TypeCheck.getType(s).getTypeRef();
    TypeReference rhsType = ref.getType();
    byte ans = ClassLoaderProxy.includesType(lhsType, rhsType);
    if (ans == OptConstants.YES) {
      Move.mutate(s, REF_MOVE, TypeCheck.getResult(s), ref);
      if (ref.isConstant())
        return DefUseEffect.MOVE_FOLDED;
      else
        return DefUseEffect.MOVE_REDUCED;
    } else if (ans == OptConstants.NO) {
      RVMType rType = rhsType.peekType();
      if (rType != null && rType.isClassType() && rType.asClass().isFinal()) {
        // only final (or precise) rhs types can be optimized since rhsType may be conservative
        Trap.mutate(s, TRAP, null, TrapCodeOperand.CheckCast());
        return DefUseEffect.TRAP_REDUCED;
      } else {
        return DefUseEffect.UNCHANGED;
      }
    } else {
      return DefUseEffect.UNCHANGED;
    }
  }
  private static DefUseEffect instanceOf(Instruction s, OptOptions opts) {
    Operand ref = InstanceOf.getRef(s);
    if (ref.isNullConstant()) {
      Move.mutate(s, INT_MOVE, InstanceOf.getClearResult(s), IC(0));
      return DefUseEffect.MOVE_FOLDED;
    } else if (ref.isConstant()) {
      s.operator = INSTANCEOF_NOTNULL;
      return instanceOfNotNull(s, opts);
    } else {
      TypeReference lhsType = InstanceOf.getType(s).getTypeRef();
      TypeReference rhsType = ref.getType();
      byte ans = ClassLoaderProxy.includesType(lhsType, rhsType);
      // NOTE: Constants.YES doesn't help because ref may be null and null instanceof T is false
      if (ans == OptConstants.NO) {
        RVMType rType = rhsType.peekType();
        if (rType != null && rType.isClassType() && rType.asClass().isFinal()) {
          // only final (or precise) rhs types can be optimized since rhsType may be conservative
          Move.mutate(s, INT_MOVE, InstanceOf.getClearResult(s), IC(0));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          return DefUseEffect.UNCHANGED;
        }
      } else {
        return DefUseEffect.UNCHANGED;
      }
    }
  }

  private static DefUseEffect instanceOfNotNull(Instruction s, OptOptions opts) {
    {
      Operand ref = InstanceOf.getRef(s);
      TypeReference lhsType = InstanceOf.getType(s).getTypeRef();
      TypeReference rhsType = ref.getType();
      byte ans = ClassLoaderProxy.includesType(lhsType, rhsType);
      if (ans == OptConstants.YES) {
        Move.mutate(s, INT_MOVE, InstanceOf.getClearResult(s), IC(1));
        return DefUseEffect.MOVE_FOLDED;
      } else if (ans == OptConstants.NO) {
        RVMType rType = rhsType.peekType();
        if (rType != null && rType.isClassType() && rType.asClass().isFinal()) {
          // only final (or precise) rhs types can be optimized since rhsType may be conservative
          Move.mutate(s, INT_MOVE, InstanceOf.getClearResult(s), IC(0));
          return DefUseEffect.MOVE_FOLDED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect objarrayStoreCheck(Instruction s, OptOptions opts) {
    Operand val = StoreCheck.getVal(s);
    if (val.isNullConstant()) {
      // Writing null into an array is trivially safe
      Move.mutate(s, GUARD_MOVE, StoreCheck.getClearGuardResult(s), StoreCheck.getClearGuard(s));
      return DefUseEffect.MOVE_REDUCED;
    } else {
      Operand ref = StoreCheck.getRef(s);
      TypeReference arrayTypeRef = ref.getType();
      if (!arrayTypeRef.isArrayType()) {
        // Caused by inlining new and type propogation
        return DefUseEffect.UNCHANGED;
      }
      RVMType typeOfIMElem = arrayTypeRef.getInnermostElementType().peekType();
      if (typeOfIMElem != null) {
        RVMType typeOfVal = val.getType().peekType();
        if ((typeOfIMElem == typeOfVal) &&
            (typeOfIMElem.isPrimitiveType() || typeOfIMElem.isUnboxedType() || typeOfIMElem.asClass().isFinal())) {
          // Writing something of a final type to an array of that
          // final type is safe
          Move.mutate(s, GUARD_MOVE, StoreCheck.getClearGuardResult(s), StoreCheck.getClearGuard(s));
          return DefUseEffect.MOVE_REDUCED;
        }
      }
      final boolean refIsPrecise = ref.isConstant() || (ref.isRegister() && ref.asRegister().isPreciseType());
      if ((arrayTypeRef == TypeReference.JavaLangObjectArray) && refIsPrecise) {
        // We know this to be an array of objects so any store must
        // be safe
        Move.mutate(s, GUARD_MOVE, StoreCheck.getClearGuardResult(s), StoreCheck.getClearGuard(s));
        return DefUseEffect.MOVE_REDUCED;
      }
      final boolean valIsPrecise = val.isConstant() || (val.isRegister() && val.asRegister().isPreciseType());
      if (refIsPrecise && valIsPrecise) {
        // writing a known type of value into a known type of array
        byte ans = ClassLoaderProxy.includesType(arrayTypeRef.getArrayElementType(), val.getType());
        if (ans == OptConstants.YES) {
          // all stores should succeed
          Move.mutate(s, GUARD_MOVE, StoreCheck.getClearGuardResult(s), StoreCheck.getClearGuard(s));
          return DefUseEffect.MOVE_REDUCED;
        } else if (ans == OptConstants.NO) {
          // all stores will fail
          Trap.mutate(s, TRAP, StoreCheck.getClearGuardResult(s), TrapCodeOperand.StoreCheck());
          return DefUseEffect.TRAP_REDUCED;
        }
      }
      return DefUseEffect.UNCHANGED;
    }
  }

  private static DefUseEffect objarrayStoreCheckNotNull(Instruction s, OptOptions opts) {
    Operand val = StoreCheck.getVal(s);
    Operand ref = StoreCheck.getRef(s);
    TypeReference arrayTypeRef = ref.getType();
    if (!arrayTypeRef.isArrayType()) {
      // Caused by inlining new and type propogation
      return DefUseEffect.UNCHANGED;
    }
    RVMType typeOfIMElem = arrayTypeRef.getInnermostElementType().peekType();
    if (typeOfIMElem != null) {
      RVMType typeOfVal = val.getType().peekType();
      if ((typeOfIMElem == typeOfVal) &&
          (typeOfIMElem.isPrimitiveType() || typeOfIMElem.isUnboxedType() || typeOfIMElem.asClass().isFinal())) {
        // Writing something of a final type to an array of that
        // final type is safe
        Move.mutate(s, GUARD_MOVE, StoreCheck.getClearGuardResult(s), StoreCheck.getClearGuard(s));
        return DefUseEffect.MOVE_REDUCED;
      }
    }
    final boolean refIsPrecise = ref.isConstant() || (ref.isRegister() && ref.asRegister().isPreciseType());
    if ((arrayTypeRef == TypeReference.JavaLangObjectArray) && refIsPrecise) {
      // We know this to be an array of objects so any store must
      // be safe
      Move.mutate(s, GUARD_MOVE, StoreCheck.getClearGuardResult(s), StoreCheck.getClearGuard(s));
      return DefUseEffect.MOVE_REDUCED;
    }
    final boolean valIsPrecise = val.isConstant() || (val.isRegister() && val.asRegister().isPreciseType());
    if (refIsPrecise && valIsPrecise) {
      // writing a known type of value into a known type of array
      byte ans = ClassLoaderProxy.includesType(arrayTypeRef.getArrayElementType(), val.getType());
      if (ans == OptConstants.YES) {
        // all stores should succeed
        Move.mutate(s, GUARD_MOVE, StoreCheck.getClearGuardResult(s), StoreCheck.getClearGuard(s));
        return DefUseEffect.MOVE_REDUCED;
      } else if (ans == OptConstants.NO) {
        // all stores will fail
        Trap.mutate(s, TRAP, StoreCheck.getClearGuardResult(s), TrapCodeOperand.StoreCheck());
        return DefUseEffect.TRAP_REDUCED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect mustImplementInterface(Instruction s, OptOptions opts) {
    Operand ref = TypeCheck.getRef(s);
    if (ref.isNullConstant()) {
      // Possible situation from constant propagation. This operation
      // is really a nop as a null_check should have happened already
      Trap.mutate(s, TRAP, null, TrapCodeOperand.NullPtr());
      return DefUseEffect.TRAP_REDUCED;
    } else {
      TypeReference lhsType = TypeCheck.getType(s).getTypeRef(); // the interface that must be implemented
      TypeReference rhsType = ref.getType();                     // our type
      byte ans = ClassLoaderProxy.includesType(lhsType, rhsType);
      if (ans == OptConstants.YES) {
        RVMType rType = rhsType.peekType();
        if (rType != null) {
          if (rType.isClassType() && rType.asClass().isInterface()) {
            /* This is exactly the kind of typing that could require us to raise an IncompatibleClassChangeError */
            return DefUseEffect.UNCHANGED;
          }
          Move.mutate(s, REF_MOVE, TypeCheck.getResult(s), ref);
          if (ref.isConstant())
            return DefUseEffect.MOVE_FOLDED;
          else
            return DefUseEffect.MOVE_REDUCED;
        } else {
          return DefUseEffect.UNCHANGED;
        }
      } else if (ans == OptConstants.NO) {
        RVMType rType = rhsType.peekType();
        if (rType != null && rType.isClassType() && rType.asClass().isFinal()) {
          // only final (or precise) rhs types can be optimized since rhsType may be conservative
          Trap.mutate(s, TRAP, null, TrapCodeOperand.MustImplement());
          return DefUseEffect.TRAP_REDUCED;
        }
      }
      return DefUseEffect.UNCHANGED;
    }
  }

  private static DefUseEffect intCondMove(Instruction s, OptOptions opts) {
    {
      Operand val1 = CondMove.getVal1(s);
      Operand val2 = CondMove.getVal2(s);
      int cond = CondMove.getCond(s).evaluate(val1, val2);
      if (cond != ConditionOperand.UNKNOWN) {
        // BOTH CONSTANTS OR SIMILAR: FOLD
        Operand val =
            (cond == ConditionOperand.TRUE) ? CondMove.getClearTrueValue(s) : CondMove.getClearFalseValue(s);
        Move.mutate(s, INT_MOVE, CondMove.getClearResult(s), val);
        return val.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
      }
      if (val1.isConstant() && !val2.isConstant()) {
        // Canonicalize by switching operands and fliping code.
        Operand tmp = CondMove.getClearVal1(s);
        CondMove.setVal1(s, CondMove.getClearVal2(s));
        CondMove.setVal2(s, tmp);
        CondMove.getCond(s).flipOperands();
      }
      Operand tv = CondMove.getTrueValue(s);
      Operand fv = CondMove.getFalseValue(s);
      if (tv.similar(fv)) {
        Move.mutate(s, INT_MOVE, CondMove.getClearResult(s), tv);
        return tv.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
      }
      if (tv.isIntConstant() && fv.isIntConstant() && !CondMove.getCond(s).isFLOATINGPOINT()) {
        int itv = tv.asIntConstant().value;
        int ifv = fv.asIntConstant().value;
        Operator op = null;
        if (val1.isLong()) {
          op = BOOLEAN_CMP_LONG;
        } else if (val1.isFloat()) {
          op = BOOLEAN_CMP_FLOAT;
        } else if (val1.isDouble()) {
          op = BOOLEAN_CMP_DOUBLE;
        } else if (val1.isRef()) {
          op = BOOLEAN_CMP_ADDR;
        } else {
          op = BOOLEAN_CMP_INT;
        }
        if (itv == 1 && ifv == 0) {
          BooleanCmp.mutate(s,
                            op,
                            CondMove.getClearResult(s),
                            CondMove.getClearVal1(s),
                            CondMove.getClearVal2(s),
                            CondMove.getClearCond(s),
                            new BranchProfileOperand());
          return DefUseEffect.REDUCED;
        }
        if (itv == 0 && ifv == 1) {
          BooleanCmp.mutate(s,
                            op,
                            CondMove.getClearResult(s),
                            CondMove.getClearVal1(s),
                            CondMove.getClearVal2(s),
                            CondMove.getClearCond(s).flipCode(),
                            new BranchProfileOperand());
          return DefUseEffect.REDUCED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect longCondMove(Instruction s, OptOptions opts) {
    {
      Operand val1 = CondMove.getVal1(s);
      Operand val2 = CondMove.getVal2(s);
      int cond = CondMove.getCond(s).evaluate(val1, val2);
      if (cond != ConditionOperand.UNKNOWN) {
        // BOTH CONSTANTS OR SIMILAR: FOLD
        Operand val =
            (cond == ConditionOperand.TRUE) ? CondMove.getClearTrueValue(s) : CondMove.getClearFalseValue(s);
        Move.mutate(s, LONG_MOVE, CondMove.getClearResult(s), val);
        return val.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
      }
      if (val1.isConstant() && !val2.isConstant()) {
        // Canonicalize by switching operands and fliping code.
        Operand tmp = CondMove.getClearVal1(s);
        CondMove.setVal1(s, CondMove.getClearVal2(s));
        CondMove.setVal2(s, tmp);
        CondMove.getCond(s).flipOperands();
      }
      Operand tv = CondMove.getTrueValue(s);
      Operand fv = CondMove.getFalseValue(s);
      if (tv.similar(fv)) {
        Move.mutate(s, LONG_MOVE, CondMove.getClearResult(s), tv);
        return tv.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
      }
      if (tv.isLongConstant() && fv.isLongConstant() && !CondMove.getCond(s).isFLOATINGPOINT()) {
        long itv = tv.asLongConstant().value;
        long ifv = fv.asLongConstant().value;
        Operator op = null;
        if (val1.isLong()) {
          op = BOOLEAN_CMP_LONG;
        } else if (val1.isFloat()) {
          op = BOOLEAN_CMP_FLOAT;
        } else if (val1.isDouble()) {
          op = BOOLEAN_CMP_DOUBLE;
        } else {
          op = BOOLEAN_CMP_INT;
        }
        if (itv == 1 && ifv == 0) {
          BooleanCmp.mutate(s,
                            op,
                            CondMove.getClearResult(s),
                            CondMove.getClearVal1(s),
                            CondMove.getClearVal2(s),
                            CondMove.getClearCond(s),
                            new BranchProfileOperand());
          return DefUseEffect.REDUCED;
        }
        if (itv == 0 && ifv == 1) {
          BooleanCmp.mutate(s,
                            op,
                            CondMove.getClearResult(s),
                            CondMove.getClearVal1(s),
                            CondMove.getClearVal2(s),
                            CondMove.getClearCond(s).flipCode(),
                            new BranchProfileOperand());
          return DefUseEffect.REDUCED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect floatCondMove(Instruction s, OptOptions opts) {
    {
      Operand val1 = CondMove.getVal1(s);
      Operand val2 = CondMove.getVal2(s);
      int cond = CondMove.getCond(s).evaluate(val1, val2);
      if (cond != ConditionOperand.UNKNOWN) {
        // BOTH CONSTANTS OR SIMILAR: FOLD
        Operand val =
            (cond == ConditionOperand.TRUE) ? CondMove.getClearTrueValue(s) : CondMove.getClearFalseValue(s);
        Move.mutate(s, FLOAT_MOVE, CondMove.getClearResult(s), val);
        return val.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
      }
      if (val1.isConstant() && !val2.isConstant()) {
        // Canonicalize by switching operands and fliping code.
        Operand tmp = CondMove.getClearVal1(s);
        CondMove.setVal1(s, CondMove.getClearVal2(s));
        CondMove.setVal2(s, tmp);
        CondMove.getCond(s).flipOperands();
      }
      Operand tv = CondMove.getTrueValue(s);
      Operand fv = CondMove.getFalseValue(s);
      if (tv.similar(fv)) {
        Move.mutate(s, FLOAT_MOVE, CondMove.getClearResult(s), tv);
        return tv.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect doubleCondMove(Instruction s, OptOptions opts) {
    {
      Operand val1 = CondMove.getVal1(s);
      Operand val2 = CondMove.getVal2(s);
      int cond = CondMove.getCond(s).evaluate(val1, val2);
      if (cond != ConditionOperand.UNKNOWN) {
        // BOTH CONSTANTS OR SIMILAR: FOLD
        Operand val =
            (cond == ConditionOperand.TRUE) ? CondMove.getClearTrueValue(s) : CondMove.getClearFalseValue(s);
        Move.mutate(s, DOUBLE_MOVE, CondMove.getClearResult(s), val);
        return val.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
      }
      if (val1.isConstant() && !val2.isConstant()) {
        // Canonicalize by switching operands and fliping code.
        Operand tmp = CondMove.getClearVal1(s);
        CondMove.setVal1(s, CondMove.getClearVal2(s));
        CondMove.setVal2(s, tmp);
        CondMove.getCond(s).flipOperands();
      }
      Operand tv = CondMove.getTrueValue(s);
      Operand fv = CondMove.getFalseValue(s);
      if (tv.similar(fv)) {
        Move.mutate(s, DOUBLE_MOVE, CondMove.getClearResult(s), tv);
        return tv.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect refCondMove(Instruction s, OptOptions opts) {
    {
      Operand val1 = CondMove.getVal1(s);
      if (val1.isConstant()) {
        Operand val2 = CondMove.getVal2(s);
        if (val2.isConstant()) {
          // BOTH CONSTANTS: FOLD
          int cond = CondMove.getCond(s).evaluate(val1, val2);
          if (cond != ConditionOperand.UNKNOWN) {
            Operand val =
                (cond == ConditionOperand.TRUE) ? CondMove.getClearTrueValue(s) : CondMove.getClearFalseValue(s);
            Move.mutate(s, REF_MOVE, CondMove.getClearResult(s), val);
            return val.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
          }
        } else {
          // Canonicalize by switching operands and fliping code.
          Operand tmp = CondMove.getClearVal1(s);
          CondMove.setVal1(s, CondMove.getClearVal2(s));
          CondMove.setVal2(s, tmp);
          CondMove.getCond(s).flipOperands();
        }
      }
      if (CondMove.getTrueValue(s).similar(CondMove.getFalseValue(s))) {
        Operand val = CondMove.getClearTrueValue(s);
        Move.mutate(s, REF_MOVE, CondMove.getClearResult(s), val);
        return val.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect guardCondMove(Instruction s, OptOptions opts) {
    {
      Operand val1 = CondMove.getVal1(s);
      if (val1.isConstant()) {
        Operand val2 = CondMove.getVal2(s);
        if (val2.isConstant()) {
          // BOTH CONSTANTS: FOLD
          int cond = CondMove.getCond(s).evaluate(val1, val2);
          if (cond == ConditionOperand.UNKNOWN) {
            Operand val =
                (cond == ConditionOperand.TRUE) ? CondMove.getClearTrueValue(s) : CondMove.getClearFalseValue(s);
            Move.mutate(s, GUARD_MOVE, CondMove.getClearResult(s), val);
            return val.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
          }
        } else {
          // Canonicalize by switching operands and fliping code.
          Operand tmp = CondMove.getClearVal1(s);
          CondMove.setVal1(s, CondMove.getClearVal2(s));
          CondMove.setVal2(s, tmp);
          CondMove.getCond(s).flipOperands();
        }
      }
      if (CondMove.getTrueValue(s).similar(CondMove.getFalseValue(s))) {
        Operand val = CondMove.getClearTrueValue(s);
        Move.mutate(s, GUARD_MOVE, CondMove.getClearResult(s), val);
        return val.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect booleanNot(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_INTEGER_OPS) {
      Operand op = Unary.getVal(s);
      if (op.isIntConstant()) {
        // CONSTANT: FOLD
        int val = op.asIntConstant().value;
        if (val == 0) {
          Move.mutate(s, INT_MOVE, Unary.getClearResult(s), IC(1));
        } else {
          Move.mutate(s, INT_MOVE, Unary.getClearResult(s), IC(0));
        }
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect booleanCmpInt(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_INTEGER_OPS) {
      Operand op1 = BooleanCmp.getVal1(s);
      Operand op2 = BooleanCmp.getVal2(s);
      if (op1.isConstant()) {
        if (op2.isConstant()) {
          // BOTH CONSTANTS: FOLD
          int cond = BooleanCmp.getCond(s).evaluate(op1, op2);
          if (cond != ConditionOperand.UNKNOWN) {
            Move.mutate(s, INT_MOVE, BooleanCmp.getResult(s), (cond == ConditionOperand.TRUE) ? IC(1) : IC(0));
            return DefUseEffect.MOVE_FOLDED;
          }
        } else {
          // Canonicalize by switching operands and fliping code.
          BooleanCmp.setVal1(s, op2);
          BooleanCmp.setVal2(s, op1);
          BooleanCmp.getCond(s).flipOperands();
          op2 = op1;
          op1 = BooleanCmp.getVal1(s);
        }
      }
      // try to fold boolean compares involving one boolean constant
      // e.g.: x = (y == true)  ? true : false ==> x = y
      // or:   x = (y == false) ? true : false ==> x = !y
      if (op1.getType().isBooleanType() && op2.isConstant()) {
        ConditionOperand cond = BooleanCmp.getCond(s);
        int op2value = op2.asIntConstant().value;
        if ((cond.isNOT_EQUAL() && (op2value == 0)) || (cond.isEQUAL() && (op2value == 1))) {
          Move.mutate(s, INT_MOVE, BooleanCmp.getResult(s), op1);
          return DefUseEffect.MOVE_REDUCED;
        } else if ((cond.isEQUAL() && (op2value == 0)) || (cond.isNOT_EQUAL() && (op2value == 1))) {
          Unary.mutate(s, BOOLEAN_NOT, BooleanCmp.getResult(s), op1);
          return DefUseEffect.REDUCED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect booleanCmpAddr(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_REF_OPS) {
      Operand op1 = BooleanCmp.getVal1(s);
      Operand op2 = BooleanCmp.getVal2(s);
      if (op1.isConstant()) {
        if (op2.isConstant()) {
          // BOTH CONSTANTS: FOLD
          int cond = BooleanCmp.getCond(s).evaluate(op1, op2);
          if (cond != ConditionOperand.UNKNOWN) {
            Move.mutate(s, REF_MOVE, BooleanCmp.getResult(s), (cond == ConditionOperand.TRUE) ? IC(1) : IC(0));
            return DefUseEffect.MOVE_FOLDED;
          }
        } else {
          // Canonicalize by switching operands and fliping code.
          Operand tmp = BooleanCmp.getClearVal1(s);
          BooleanCmp.setVal1(s, BooleanCmp.getClearVal2(s));
          BooleanCmp.setVal2(s, tmp);
          BooleanCmp.getCond(s).flipOperands();
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect intAdd(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_INTEGER_OPS) {
      canonicalizeCommutativeOperator(s);
      Operand op2 = Binary.getVal2(s);
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;
        Operand op1 = Binary.getVal1(s);
        if (op1.isIntConstant()) {
          // BOTH CONSTANTS: FOLD
          int val1 = op1.asIntConstant().value;
          Move.mutate(s, INT_MOVE, Binary.getClearResult(s), IC(val1 + val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0) {
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
        }
      } else {
        Operand op1 = Binary.getVal1(s);
        if (op1.similar(op2)) {
          // Adding something to itself is the same as a multiply by 2 so
          // canonicalize as a shift left
          Binary.mutate(s, INT_SHL, Binary.getClearResult(s), op1, IC(1));
          return DefUseEffect.UNCHANGED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect intAnd(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_INTEGER_OPS) {
      canonicalizeCommutativeOperator(s);
      Operand op1 = Binary.getVal1(s);
      Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x & x == x
        Move.mutate(s, INT_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
        return op1.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
      }
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;
        if (op1.isIntConstant()) {
          // BOTH CONSTANTS: FOLD
          int val1 = op1.asIntConstant().value;
          Move.mutate(s, INT_MOVE, Binary.getClearResult(s), IC(val1 & val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0) {                  // x & 0 == 0
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), IC(0));
            return DefUseEffect.MOVE_FOLDED;
          }
          if (val2 == -1) {                 // x & -1 == x & 0xffffffff == x
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect intDiv(AbstractRegisterPool regpool, Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_INTEGER_OPS) {
      Operand op1 = GuardedBinary.getVal1(s);
      Operand op2 = GuardedBinary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x / x == 1
        Move.mutate(s, INT_MOVE, GuardedBinary.getClearResult(s), IC(1));
        return DefUseEffect.MOVE_FOLDED;
      }
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;
        if (val2 == 0) {
          // TODO: This instruction is actually unreachable.
          // There will be an INT_ZERO_CHECK
          // guarding this instruction that will result in an
          // ArithmeticException.  We
          // should probabbly just remove the INT_DIV as dead code.
          return DefUseEffect.UNCHANGED;
        }
        if (op1.isIntConstant()) {
          // BOTH CONSTANTS: FOLD
          int val1 = op1.asIntConstant().value;
          Move.mutate(s, INT_MOVE, GuardedBinary.getClearResult(s), IC(val1 / val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 1) {                  // x / 1 == x;
            Move.mutate(s, INT_MOVE, GuardedBinary.getClearResult(s), GuardedBinary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
          // x / c == (x + (((1 << c) - 1) & (x >> 31))) >> c .. if c is power of 2
          if (s.hasPrev()) {
            int power = PowerOf2(val2);
            if (power != -1) {
              RegisterOperand tempInt1 = regpool.makeTempInt();
              RegisterOperand tempInt2 = regpool.makeTempInt();
              RegisterOperand tempInt3 = regpool.makeTempInt();
              Instruction sign = Binary.create(INT_SHR, tempInt1, GuardedBinary.getVal1(s).copy(), IC(31));
              sign.copyPosition(s);
              s.insertBefore(sign);
              Instruction masked = Binary.create(INT_AND, tempInt2, tempInt1.copyRO(), IC((1 << power)-1));
              masked.copyPosition(s);
              s.insertBefore(masked);
              Instruction adjusted = Binary.create(INT_ADD, tempInt3, tempInt2.copyRO(), GuardedBinary.getClearVal1(s));
              adjusted.copyPosition(s);
              s.insertBefore(adjusted);
              Binary.mutate(s, INT_SHR, GuardedBinary.getClearResult(s), tempInt3.copyRO(), IC(power));
              return DefUseEffect.REDUCED;
            }
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect intMul(AbstractRegisterPool regpool, Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_INTEGER_OPS) {
      canonicalizeCommutativeOperator(s);
      Operand op2 = Binary.getVal2(s);
      if (op2.isIntConstant()) {
        Operand op1 = Binary.getVal1(s);
        if (op1.isIntConstant()) {
          // BOTH CONSTANTS: FOLD
          int val1 = op1.asIntConstant().value;
          int val2 = op2.asIntConstant().value;
          Move.mutate(s, INT_MOVE, Binary.getClearResult(s), IC(val1 * val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT
          return multiplyByConstant(regpool, s, op1, op2, opts);
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect multiplyByConstant(AbstractRegisterPool regpool, Instruction s, Operand op1, Operand op2, OptOptions opts) {
    Operator addOperator, moveOperator, negateOperator, shiftLeftOperator;
    ConstantOperand zero;
    long val2;
    int numBits;
    if (op2.isIntConstant()) {
      val2 = op2.asIntConstant().value;
      addOperator = INT_ADD;
      moveOperator = INT_MOVE;
      negateOperator = INT_NEG;
      shiftLeftOperator = INT_SHL;
      zero = IntConstantOperand.zero;
      numBits = 32;
    } else {
      val2 = op2.asLongConstant().value;
      addOperator = LONG_ADD;
      moveOperator = LONG_MOVE;
      negateOperator = LONG_NEG;
      shiftLeftOperator = LONG_SHL;
      zero = LongConstantOperand.zero;
      numBits = 64;
    }
    // ATTEMPT TO APPLY AXIOMS
    if (val2 == 0) {                  // x * 0 == 0
      Move.mutate(s, moveOperator, Binary.getClearResult(s), zero.copy());
      return DefUseEffect.MOVE_FOLDED;
    } else if (numBits == 32 && ((int)val2 == ((int)-val2))) { // x * MIN_INT == x << 31
      Binary.mutate(s, INT_SHL, Binary.getClearResult(s), op1, IC(31));
      return DefUseEffect.REDUCED;
    } else if (numBits == 64 && val2 == -val2) { // x * MIN_LONG == x << 63
      Binary.mutate(s, LONG_SHL, Binary.getClearResult(s), op1, IC(63));
      return DefUseEffect.REDUCED;
    }
    // Try to reduce x*c into shift and adds, but only if cost is cheap
    if (s.hasPrev()) {
      // don't attempt to reduce if this instruction isn't
      // part of a well-formed sequence

      // Cost of shift and add replacement
      int cost = 0;
      boolean negative = val2 < 0;
      if (negative) {
        val2 = -val2;
        cost++;
      }
      if (VM.BuildForIA32 && numBits <= BITS_IN_ADDRESS) {
        int lastShift = 0;
        boolean lastShiftWasShort = false;
        for (int i = 1; i < numBits; i++) {
          if ((val2 & (1L << i)) != 0) {
            // LEA shift and add uses 1 instruction, try to determine if we can
            // use in favour to separate shift and adds
            // NB LEA shift needs to be of size 1, 2 or 3 and if the last
            // shift used an LEA then the add can't be merged
            // (so we can allow better ILP by just using a regular shift of
            // the original operand)
            if (i < 4) {
              // can use LEA of operand
              cost++;
            } else if ((i - lastShift) < 4 && !lastShiftWasShort) {
              // can use LEA of last shift
              cost++;
              lastShiftWasShort = true;
            } else {
              // need separate shift and add
              cost+=2;
              lastShiftWasShort = false;
            }
            lastShift = i;
          }
        }
      } else if (numBits > BITS_IN_ADDRESS) {
        for (int i = 1; i < BITS_IN_ADDRESS; i++) {
          if ((val2 & (1L << i)) != 0) {
            // each 1 requires a shift and add
            cost+=2;
          }
        }
        for (int i = BITS_IN_ADDRESS; i < numBits; i++) {
          if ((val2 & (1L << i)) != 0) {
            // when the shift is > than the bits in the address we can just 0
            // the bottom word, make the cost cheaper
            cost++;
          }
        }
      } else {
        for (int i = 1; i < numBits; i++) {
          if ((val2 & (1L << i)) != 0) {
            // each 1 requires a shift and add
            cost+=2;
          }
        }
      }
      int targetCost;
      if (VM.BuildForIA32) {
        targetCost = numBits == 64 ? 6 : 4;
      } else {
        targetCost = 2;
      }
      if (cost <= targetCost) {
        // generate shift and adds
        RegisterOperand val1Operand = op1.asRegister();
        RegisterOperand resultOperand = numBits == 32 ? regpool.makeTempInt() : regpool.makeTempLong();
        Instruction move;
        if ((val2 & 1) == 1) {
          // result = val1 * 1
          move = Move.create(moveOperator, resultOperand, val1Operand);
        } else {
          // result = 0
          move = Move.create(moveOperator, resultOperand, zero.copy());
        }
        move.copyPosition(s);
        s.insertBefore(move);
        int lastShift = 0;
        RegisterOperand lastShiftResult = null;
        boolean lastShiftWasShort = false;
        for (int i = 1; i < numBits; i++) {
          if ((val2 & (1L << i)) != 0) {
            Instruction shift;
            RegisterOperand shiftResult = numBits == 32 ? regpool.makeTempInt() : regpool.makeTempLong();
            if (VM.BuildForIA32 && numBits <= BITS_IN_ADDRESS &&
                lastShiftResult != null && ((i-lastShift) <= 3) && (i > 3) && !lastShiftWasShort) {
              // We can produce a short shift (1, 2 or 3) using the result of the last shift
              shift = Binary.create(shiftLeftOperator, shiftResult, lastShiftResult.copyRO(), IC(i-lastShift));
              lastShiftWasShort = true;
            } else {
              shift = Binary.create(shiftLeftOperator, shiftResult, val1Operand.copyRO(), IC(i));
              lastShiftWasShort = false;
            }
            shift.copyPosition(s);
            s.insertBefore(shift);
            lastShiftResult = shiftResult;
            lastShift = i;
            RegisterOperand addResult = numBits == 32 ? regpool.makeTempInt() : regpool.makeTempLong();
            Instruction add = Binary.create(addOperator, addResult, resultOperand.copyRO(), shiftResult.copyRO());
            add.copyPosition(s);
            s.insertBefore(add);
            resultOperand = addResult;
          }
        }
        if (negative) {
          Unary.mutate(s, negateOperator, Binary.getClearResult(s), resultOperand.copyRO());
        } else {
          Move.mutate(s, moveOperator, Binary.getClearResult(s), resultOperand.copyRO());
        }
        return DefUseEffect.REDUCED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect intNeg(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_INTEGER_OPS) {
      Operand op = Unary.getVal(s);
      if (op.isIntConstant()) {
        // CONSTANT: FOLD
        int val = op.asIntConstant().value;
        Move.mutate(s, INT_MOVE, Unary.getClearResult(s), IC(-val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect intNot(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_INTEGER_OPS) {
      Operand op = Unary.getVal(s);
      if (op.isIntConstant()) {
        // CONSTANT: FOLD
        int val = op.asIntConstant().value;
        Move.mutate(s, INT_MOVE, Unary.getClearResult(s), IC(~val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect intOr(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_INTEGER_OPS) {
      canonicalizeCommutativeOperator(s);
      Operand op1 = Binary.getVal1(s);
      Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x | x == x
        Move.mutate(s, INT_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
        return op1.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
      }
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;
        if (op1.isIntConstant()) {
          // BOTH CONSTANTS: FOLD
          int val1 = op1.asIntConstant().value;
          Move.mutate(s, INT_MOVE, Binary.getClearResult(s), IC(val1 | val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == -1) { // x | -1 == x | 0xffffffff == 0xffffffff == -1
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), IC(-1));
            return DefUseEffect.MOVE_FOLDED;
          }
          if (val2 == 0) {                  // x | 0 == x
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect intRem(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_INTEGER_OPS) {
      Operand op1 = GuardedBinary.getVal1(s);
      Operand op2 = GuardedBinary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x % x == 0
        Move.mutate(s, INT_MOVE, GuardedBinary.getClearResult(s), IC(0));
        return DefUseEffect.MOVE_FOLDED;
      }
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;
        if (val2 == 0) {
          // TODO: This instruction is actually unreachable.
          // There will be an INT_ZERO_CHECK
          // guarding this instruction that will result in an
          // ArithmeticException.  We
          // should probably just remove the INT_REM as dead code.
          return DefUseEffect.UNCHANGED;
        }
        if (op1.isIntConstant()) {
          // BOTH CONSTANTS: FOLD
          int val1 = op1.asIntConstant().value;
          Move.mutate(s, INT_MOVE, GuardedBinary.getClearResult(s), IC(val1 % val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if ((val2 == 1) || (val2 == -1)) {             // x % 1 == 0
            Move.mutate(s, INT_MOVE, GuardedBinary.getClearResult(s), IC(0));
            return DefUseEffect.MOVE_FOLDED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect intShl(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_INTEGER_OPS) {
      Operand op2 = Binary.getVal2(s);
      Operand op1 = Binary.getVal1(s);
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;
        if (op1.isIntConstant()) {
          // BOTH CONSTANTS: FOLD
          int val1 = op1.asIntConstant().value;
          Move.mutate(s, INT_MOVE, Binary.getClearResult(s), IC(val1 << val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0) {                  // x << 0 == x
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
          if ((val2 >= BITS_IN_INT) || (val2 < 0)) {  // x << 32 == 0
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), IC(0));
            return DefUseEffect.MOVE_FOLDED;
          }
        }
      } else if (op1.isIntConstant()) {
        int val1 = op1.asIntConstant().value;
        // ONLY OP1 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
        if (val1 == 0) {                  // 0 << x == 0
          Move.mutate(s, INT_MOVE, Binary.getClearResult(s), IC(0));
          return DefUseEffect.MOVE_FOLDED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect intShr(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_INTEGER_OPS) {
      Operand op1 = Binary.getVal1(s);
      Operand op2 = Binary.getVal2(s);
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;
        if (op1.isIntConstant()) {
          // BOTH CONSTANTS: FOLD
          int val1 = op1.asIntConstant().value;
          Move.mutate(s, INT_MOVE, Binary.getClearResult(s), IC(val1 >> val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0) {                  // x >> 0 == x
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
          if ((val2 >= BITS_IN_INT) || (val2 < 0)) { // x >> 32 == x >> 31
            Binary.setVal2(s, IC(31));
            return DefUseEffect.UNCHANGED;
          }
        }
      } else if (op1.isIntConstant()) {
        int val1 = op1.asIntConstant().value;
        // ONLY OP1 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
        if ((val1 == -1) || (val1 == 0)) { // -1 >> x == -1,0 >> x == 0
          Move.mutate(s, INT_MOVE, Binary.getClearResult(s), op1);
          return DefUseEffect.MOVE_FOLDED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect intSub(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_INTEGER_OPS) {
      Operand op1 = Binary.getVal1(s);
      Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x - x == 0
        Move.mutate(s, INT_MOVE, Binary.getClearResult(s), IC(0));
        return DefUseEffect.MOVE_FOLDED;
      }
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;
        if (op1.isIntConstant()) {
          // BOTH CONSTANTS: FOLD
          int val1 = op1.asIntConstant().value;
          Move.mutate(s, INT_MOVE, Binary.getClearResult(s), IC(val1 - val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0) {                  // x - 0 == x
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
          // x - c = x + -c
          // prefer adds, since some architectures have addi but not subi, also
          // add is commutative and gives greater flexibility to LIR/MIR phases
          // without possibly introducing temporary variables
          Binary.mutate(s, INT_ADD, Binary.getClearResult(s), Binary.getClearVal1(s), IC(-val2));
          return DefUseEffect.REDUCED;
        }
      } else if (op1.isIntConstant() && (op1.asIntConstant().value == 0)) {
        Unary.mutate(s, INT_NEG, Binary.getClearResult(s), Binary.getClearVal2(s));
        return DefUseEffect.REDUCED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect intUshr(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_INTEGER_OPS) {
      Operand op2 = Binary.getVal2(s);
      Operand op1 = Binary.getVal1(s);
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;
        if (op1.isIntConstant()) {
          // BOTH CONSTANTS: FOLD
          int val1 = op1.asIntConstant().value;
          Move.mutate(s, INT_MOVE, Binary.getClearResult(s), IC(val1 >>> val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0) {                  // x >>> 0 == x
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
          if ((val2 >= BITS_IN_INT) || (val2 < 0)) { // x >>> 32 == 0
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), IC(0));
            return DefUseEffect.MOVE_FOLDED;
          }
        }
      } else if (op1.isIntConstant()) {
        int val1 = op1.asIntConstant().value;
        // ONLY OP1 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
        if (val1 == 0) {                  // 0 >>> x == 0
          Move.mutate(s, INT_MOVE, Binary.getClearResult(s), IC(0));
          return DefUseEffect.MOVE_FOLDED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect intXor(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_INTEGER_OPS) {
      canonicalizeCommutativeOperator(s);
      Operand op1 = Binary.getVal1(s);
      Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x ^ x == 0
        Move.mutate(s, INT_MOVE, Binary.getClearResult(s), IC(0));
        return DefUseEffect.MOVE_FOLDED;
      }
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;

        if (op1.isIntConstant()) {
          // BOTH CONSTANTS: FOLD
          int val1 = op1.asIntConstant().value;
          Move.mutate(s, INT_MOVE, Binary.getClearResult(s), IC(val1 ^ val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == -1) {                 // x ^ -1 == x ^ 0xffffffff = ~x
            Unary.mutate(s, INT_NOT, Binary.getClearResult(s), Binary.getClearVal1(s));
            return DefUseEffect.REDUCED;
          }
          if (val2 == 0) {                  // x ^ 0 == x
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect refAdd(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_REF_OPS) {
      canonicalizeCommutativeOperator(s);
      Operand op2 = Binary.getVal2(s);
      if (op2.isConstant() && !op2.isMovableObjectConstant()) {
        Address val2 = getAddressValue(op2);
        Operand op1 = Binary.getVal1(s);
        if (op1.isConstant() && !op1.isMovableObjectConstant()) {
          // BOTH CONSTANTS: FOLD
          Address val1 = getAddressValue(op1);
          Move.mutate(s, REF_MOVE, Binary.getClearResult(s), AC(val1.plus(val2.toWord().toOffset())));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2.isZero()) {                 // x + 0 == x
            if (op1.isIntLike()) {
              Unary.mutate(s, INT_2ADDRSigExt, Binary.getClearResult(s), Binary.getClearVal1(s));
              return DefUseEffect.REDUCED;
            } else {
              Move.mutate(s, REF_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
              return op1.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
            }
          }
        }
      } else {
        Operand op1 = Binary.getVal1(s);
        if (op1.similar(op2)) {
          // Adding something to itself is the same as a multiply by 2 so
          // canonicalize as a shift left
          Binary.mutate(s, REF_SHL, Binary.getClearResult(s), op1, IC(1));
          return DefUseEffect.UNCHANGED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect refAnd(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_REF_OPS) {
      canonicalizeCommutativeOperator(s);
      Operand op1 = Binary.getVal1(s);
      Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x & x == x
        Move.mutate(s, REF_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
        return op1.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
      }
      if (op2.isConstant() && !op2.isMovableObjectConstant()) {
        Word val2 = getAddressValue(op2).toWord();
        if (op1.isConstant() && !op1.isMovableObjectConstant()) {
          // BOTH CONSTANTS: FOLD
          Word val1 = getAddressValue(op1).toWord();
          Move.mutate(s, REF_MOVE, Binary.getClearResult(s), AC(val1.and(val2).toAddress()));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2.isZero()) {                  // x & 0 == 0
            Move.mutate(s, REF_MOVE, Binary.getClearResult(s), AC(Address.zero()));
            return DefUseEffect.MOVE_FOLDED;
          }
          if (val2.isMax()) {                 // x & -1 == x & 0xffffffff == x
            Move.mutate(s, REF_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect refShl(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_REF_OPS) {
      Operand op2 = Binary.getVal2(s);
      Operand op1 = Binary.getVal1(s);
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;
        if (op1.isConstant() && !op1.isMovableObjectConstant()) {
          // BOTH CONSTANTS: FOLD
          Word val1 = getAddressValue(op1).toWord();
          Move.mutate(s, REF_MOVE, Binary.getClearResult(s), AC(val1.lsh(val2).toAddress()));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0) {                  // x << 0 == x
            Move.mutate(s, REF_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
          if ((val2 >= BITS_IN_ADDRESS) || (val2 < 0)) { // x << 32 == 0
            Move.mutate(s, REF_MOVE, Binary.getClearResult(s), IC(0));
            return DefUseEffect.MOVE_FOLDED;
          }
        }
      } else if (op1.isConstant() && !op1.isMovableObjectConstant()) {
        Word val1 = getAddressValue(op1).toWord();
        // ONLY OP1 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
        if (val1.isZero()) {                  // 0 << x == 0
          Move.mutate(s, REF_MOVE, Binary.getClearResult(s), AC(Address.zero()));
          return DefUseEffect.MOVE_FOLDED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect refShr(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_REF_OPS) {
      Operand op1 = Binary.getVal1(s);
      Operand op2 = Binary.getVal2(s);
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;
        if (op1.isConstant() && !op1.isMovableObjectConstant()) {
          // BOTH CONSTANTS: FOLD
          Word val1 = getAddressValue(op1).toWord();
          Move.mutate(s, REF_MOVE, Binary.getClearResult(s), AC(val1.rsha(val2).toAddress()));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0) {                  // x >> 0 == x
            Move.mutate(s, REF_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
          if ((val2 >= BITS_IN_ADDRESS) || (val2 < 0)) { // x >> 32 == x >> 31
            Binary.setVal2(s, IC(BITS_IN_ADDRESS - 1));
            return DefUseEffect.UNCHANGED;
          }
        }
      } else if (op1.isConstant() && !op1.isMovableObjectConstant()) {
        Word val1 = getAddressValue(op1).toWord();
        // ONLY OP1 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
        // -1 >> x == -1, 0 >> x == 0
        if (val1.EQ(Word.zero()) || val1.EQ(Word.zero().minus(Word.one()))) {
          Move.mutate(s, REF_MOVE, Binary.getClearResult(s), op1);
          return DefUseEffect.MOVE_FOLDED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect refNeg(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_REF_OPS) {
      Operand op = Unary.getVal(s);
      if (op.isConstant() && !op.isMovableObjectConstant()) {
        // CONSTANT: FOLD
        Word val = getAddressValue(op).toWord();
        Word negVal = Word.zero().minus(val);
        Move.mutate(s, REF_MOVE, Unary.getClearResult(s), AC(negVal.toAddress()));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect refNot(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_REF_OPS) {
      Operand op = Unary.getVal(s);
      if (op.isConstant() && !op.isMovableObjectConstant()) {
        // CONSTANT: FOLD
        Word val = getAddressValue(op).toWord();
        Move.mutate(s, REF_MOVE, Unary.getClearResult(s), AC(val.not().toAddress()));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect refOr(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_REF_OPS) {
      canonicalizeCommutativeOperator(s);
      Operand op1 = Binary.getVal1(s);
      Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x | x == x
        Move.mutate(s, REF_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
        return op1.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
      }
      if (op2.isConstant() && !op2.isMovableObjectConstant()) {
        Word val2 = getAddressValue(op2).toWord();
        if (op1.isAddressConstant()) {
          // BOTH CONSTANTS: FOLD
          Word val1 = getAddressValue(op1).toWord();
          Move.mutate(s, REF_MOVE, Binary.getClearResult(s), AC(val1.or(val2).toAddress()));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2.isMax()) { // x | -1 == x | 0xffffffff == 0xffffffff == -1
            Move.mutate(s, REF_MOVE, Binary.getClearResult(s), AC(Address.max()));
            return DefUseEffect.MOVE_FOLDED;
          }
          if (val2.isZero()) {                  // x | 0 == x
            Move.mutate(s, REF_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect refSub(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_REF_OPS) {
      Operand op1 = Binary.getVal1(s);
      Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x - x == 0
        Move.mutate(s, REF_MOVE, Binary.getClearResult(s), IC(0));
        return DefUseEffect.MOVE_FOLDED;
      }
      if (op2.isConstant() && !op2.isMovableObjectConstant()) {
        Address val2 = getAddressValue(op2);
        if (op1.isConstant() && !op1.isMovableObjectConstant()) {
          // BOTH CONSTANTS: FOLD
          Address val1 = getAddressValue(op1);
          Move.mutate(s, REF_MOVE, Binary.getClearResult(s), AC(val1.minus(val2.toWord().toOffset())));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2.isZero()) {                 // x - 0 == x
            if (op1.isIntLike()) {
              Unary.mutate(s, INT_2ADDRSigExt, Binary.getClearResult(s), Binary.getClearVal1(s));
              return DefUseEffect.REDUCED;
            } else {
              Move.mutate(s, REF_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
              return op1.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
            }
          }
          // x - c = x + -c
          // prefer adds, since some architectures have addi but not subi
          Binary.mutate(s,
                        REF_ADD,
                        Binary.getClearResult(s),
                        Binary.getClearVal1(s),
                        AC(Address.zero().minus(val2.toWord().toOffset())));
          return DefUseEffect.REDUCED;
        }
      } else if (op1.isConstant() && !op1.isMovableObjectConstant()) {
        Address val1 = getAddressValue(op1);
        if (val1.EQ(Address.zero())) {
          Unary.mutate(s, REF_NEG, Binary.getClearResult(s), Binary.getClearVal2(s));
          return DefUseEffect.REDUCED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect refUshr(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_REF_OPS) {
      Operand op2 = Binary.getVal2(s);
      Operand op1 = Binary.getVal1(s);
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;
        if (op1.isConstant() && !op1.isMovableObjectConstant()) {
          // BOTH CONSTANTS: FOLD
          Word val1 = getAddressValue(op1).toWord();
          Move.mutate(s, REF_MOVE, Binary.getClearResult(s), AC(val1.rshl(val2).toAddress()));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0) {                  // x >>> 0 == x
            Move.mutate(s, REF_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
          if ((val2 >= BITS_IN_ADDRESS) || (val2 < 0)) { // x >>> 32 == 0
            Move.mutate(s, REF_MOVE, Binary.getClearResult(s), IC(0));
            return DefUseEffect.MOVE_FOLDED;
          }
        }
      } else if (op1.isConstant() && !op1.isMovableObjectConstant()) {
        Word val1 = getAddressValue(op1).toWord();
        // ONLY OP1 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
        if (val1.EQ(Word.zero())) {                  // 0 >>> x == 0
          Move.mutate(s, REF_MOVE, Binary.getClearResult(s), AC(Address.zero()));
          return DefUseEffect.MOVE_FOLDED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect refXor(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_REF_OPS) {
      canonicalizeCommutativeOperator(s);
      Operand op1 = Binary.getVal1(s);
      Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x ^ x == 0
        Move.mutate(s, REF_MOVE, Binary.getClearResult(s), IC(0));
        return DefUseEffect.MOVE_FOLDED;
      }
      if (op2.isConstant() && !op2.isMovableObjectConstant()) {
        Word val2 = getAddressValue(op2).toWord();
        if (op1.isConstant() && !op1.isMovableObjectConstant()) {
          // BOTH CONSTANTS: FOLD
          Word val1 = getAddressValue(op1).toWord();
          Move.mutate(s, REF_MOVE, Binary.getClearResult(s), AC(val1.xor(val2).toAddress()));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2.isMax()) {                 // x ^ -1 == x ^ 0xffffffff = ~x
            Unary.mutate(s, REF_NOT, Binary.getClearResult(s), Binary.getClearVal1(s));
            return DefUseEffect.REDUCED;
          }
          if (val2.isZero()) {                  // x ^ 0 == x
            Move.mutate(s, REF_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect longAdd(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_LONG_OPS) {
      canonicalizeCommutativeOperator(s);
      Operand op2 = Binary.getVal2(s);
      if (op2.isLongConstant()) {
        long val2 = op2.asLongConstant().value;
        Operand op1 = Binary.getVal1(s);
        if (op1.isLongConstant()) {
          // BOTH CONSTANTS: FOLD
          long val1 = op1.asLongConstant().value;
          Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), LC(val1 + val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0L) {                 // x + 0 == x
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
        }
      } else {
        Operand op1 = Binary.getVal1(s);
        if (op1.similar(op2)) {
          // Adding something to itself is the same as a multiply by 2 so
          // canonicalize as a shift left
          Binary.mutate(s, LONG_SHL, Binary.getClearResult(s), op1, IC(1));
          return DefUseEffect.UNCHANGED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect longAnd(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_LONG_OPS) {
      canonicalizeCommutativeOperator(s);
      Operand op1 = Binary.getVal1(s);
      Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x & x == x
        Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
        return op1.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
      }
      if (op2.isLongConstant()) {
        long val2 = op2.asLongConstant().value;
        if (op1.isLongConstant()) {
          // BOTH CONSTANTS: FOLD
          long val1 = op1.asLongConstant().value;
          Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), LC(val1 & val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0L) {                 // x & 0L == 0L
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), LC(0L));
            return DefUseEffect.MOVE_FOLDED;
          }
          if (val2 == -1) {                 // x & -1L == x & 0xff...ff == x
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect longCmp(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_LONG_OPS) {
      Operand op1 = Binary.getVal1(s);
      Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: op1 == op2
        Move.mutate(s, INT_MOVE, Binary.getClearResult(s), IC(0));
        return DefUseEffect.MOVE_FOLDED;
      }
      if (op2.isLongConstant()) {
        if (op1.isLongConstant()) {
          // BOTH CONSTANTS: FOLD
          long val1 = op1.asLongConstant().value;
          long val2 = op2.asLongConstant().value;
          int result = (val1 > val2) ? 1 : ((val1 == val2) ? 0 : -1);
          Move.mutate(s, INT_MOVE, Binary.getClearResult(s), IC(result));
          return DefUseEffect.MOVE_FOLDED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect longDiv(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_LONG_OPS) {
      Operand op1 = GuardedBinary.getVal1(s);
      Operand op2 = GuardedBinary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x / x == 1
        Move.mutate(s, LONG_MOVE, GuardedBinary.getClearResult(s), LC(1));
        return DefUseEffect.MOVE_FOLDED;
      }
      if (op2.isLongConstant()) {
        long val2 = op2.asLongConstant().value;
        if (val2 == 0L) {
          // TODO: This instruction is actually unreachable.
          // There will be a LONG_ZERO_CHECK
          // guarding this instruction that will result in an
          // ArithmeticException.  We
          // should probably just remove the LONG_DIV as dead code.
          return DefUseEffect.UNCHANGED;
        }
        if (op1.isLongConstant()) {
          // BOTH CONSTANTS: FOLD
          long val1 = op1.asLongConstant().value;
          Move.mutate(s, LONG_MOVE, GuardedBinary.getClearResult(s), LC(val1 / val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 1L) {                 // x / 1L == x
            Move.mutate(s, LONG_MOVE, GuardedBinary.getClearResult(s), GuardedBinary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect longMul(AbstractRegisterPool regpool, Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_LONG_OPS) {
      canonicalizeCommutativeOperator(s);
      Operand op2 = Binary.getVal2(s);
      if (op2.isLongConstant()) {
        Operand op1 = Binary.getVal1(s);
        if (op1.isLongConstant()) {
          // BOTH CONSTANTS: FOLD
          long val1 = op1.asLongConstant().value;
          long val2 = op2.asLongConstant().value;
          Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), LC(val1 * val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT
          return multiplyByConstant(regpool, s, op1, op2, opts);
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect longNeg(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_LONG_OPS) {
      Operand op = Unary.getVal(s);
      if (op.isLongConstant()) {
        // CONSTANT: FOLD
        long val = op.asLongConstant().value;
        Move.mutate(s, LONG_MOVE, Unary.getClearResult(s), LC(-val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect longNot(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_LONG_OPS) {
      Operand op = Unary.getVal(s);
      if (op.isLongConstant()) {
        long val = op.asLongConstant().value;
        // CONSTANT: FOLD
        Move.mutate(s, LONG_MOVE, Unary.getClearResult(s), LC(~val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect longOr(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_LONG_OPS) {
      canonicalizeCommutativeOperator(s);
      Operand op1 = Binary.getVal1(s);
      Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x | x == x
        Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
        return op1.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
      }
      if (op2.isLongConstant()) {
        long val2 = op2.asLongConstant().value;
        if (op1.isLongConstant()) {
          // BOTH CONSTANTS: FOLD
          long val1 = op1.asLongConstant().value;
          Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), LC(val1 | val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0L) {                 // x | 0L == x
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
          if (val2 == -1L) { // x | -1L == x | 0xff..ff == 0xff..ff == -1L
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), LC(-1L));
            return DefUseEffect.MOVE_FOLDED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect longRem(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_LONG_OPS) {
      Operand op1 = GuardedBinary.getVal1(s);
      Operand op2 = GuardedBinary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x % x == 0
        Move.mutate(s, LONG_MOVE, GuardedBinary.getClearResult(s), LC(0));
        return DefUseEffect.MOVE_FOLDED;
      }
      if (op2.isLongConstant()) {
        long val2 = op2.asLongConstant().value;
        if (val2 == 0L) {
          // TODO: This instruction is actually unreachable.
          // There will be a LONG_ZERO_CHECK
          // guarding this instruction that will result in an
          // ArithmeticException.  We
          // should probably just remove the LONG_REM as dead code.
          return DefUseEffect.UNCHANGED;
        }
        if (op1.isLongConstant()) {
          // BOTH CONSTANTS: FOLD
          long val1 = op1.asLongConstant().value;
          Move.mutate(s, LONG_MOVE, GuardedBinary.getClearResult(s), LC(val1 % val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if ((val2 == 1L)||(val2 == -1L)) {                 // x % 1L == 0
            Move.mutate(s, LONG_MOVE, GuardedBinary.getClearResult(s), LC(0));
            return DefUseEffect.MOVE_FOLDED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect longShl(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_LONG_OPS) {
      Operand op2 = Binary.getVal2(s);
      Operand op1 = Binary.getVal1(s);
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;
        if (op1.isLongConstant()) {
          // BOTH CONSTANTS: FOLD
          long val1 = op1.asLongConstant().value;
          Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), LC(val1 << val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0) {                  // x << 0 == x
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
          if ((val2 >= BITS_IN_LONG) || (val2 < 0)) { // x << 64 == 0
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), LC(0));
            return DefUseEffect.MOVE_FOLDED;
          }
        }
      } else if (op1.isLongConstant()) {
        long val1 = op1.asLongConstant().value;
        // ONLY OP1 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
        if (val1 == 0L) {                  // 0 << x == 0
          Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), op1);
          return DefUseEffect.MOVE_FOLDED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect longShr(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_LONG_OPS) {
      Operand op1 = Binary.getVal1(s);
      Operand op2 = Binary.getVal2(s);
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;
        if (op1.isLongConstant()) {
          // BOTH CONSTANTS: FOLD
          long val1 = op1.asLongConstant().value;
          Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), LC(val1 >> val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0) {                  // x >> 0L == x
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
          if ((val2 >= BITS_IN_LONG) || (val2 < 0)) { // x >> 64 == x >> 63
            Binary.setVal2(s, LC(63));
            return DefUseEffect.UNCHANGED;
          }
        }
      } else if (op1.isLongConstant()) {
        long val1 = op1.asLongConstant().value;
        // ONLY OP1 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
        if ((val1 == -1L) || (val1 == 0L)) {  // -1 >> x == -1, 0 >> x == 0
          Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), op1);
          return DefUseEffect.MOVE_FOLDED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect longSub(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_LONG_OPS) {
      Operand op1 = Binary.getVal1(s);
      Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x - x == 0
        Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), LC(0));
        return DefUseEffect.MOVE_FOLDED;
      }
      if (op2.isLongConstant()) {
        long val2 = op2.asLongConstant().value;
        if (op1.isLongConstant()) {
          // BOTH CONSTANTS: FOLD
          long val1 = op1.asLongConstant().value;
          Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), LC(val1 - val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0L) {                 // x - 0 == x
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
          // x - c = x + -c
          // prefer adds, since some architectures have addi but not subi, also
          // add is commutative and gives greater flexibility to LIR/MIR phases
          // without possibly introducing temporary variables
          Binary.mutate(s, LONG_ADD, Binary.getClearResult(s),
              Binary.getClearVal1(s), LC(-val2));
          return DefUseEffect.REDUCED;
        }
      } else if (op1.isLongConstant() && (op1.asLongConstant().value == 0)) {
        Unary.mutate(s, LONG_NEG, Binary.getClearResult(s), Binary.getClearVal2(s));
        return DefUseEffect.REDUCED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect longUshr(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_LONG_OPS) {
      Operand op2 = Binary.getVal2(s);
      Operand op1 = Binary.getVal1(s);
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;
        if (op1.isLongConstant()) {
          // BOTH CONSTANTS: FOLD
          long val1 = op1.asLongConstant().value;
          Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), LC(val1 >>> val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0) {                  // x >>> 0L == x
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
          if ((val2 >= BITS_IN_LONG) || (val2 < 0)) {  // x >>> 64 == 0
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), LC(0));
            return DefUseEffect.MOVE_FOLDED;
          }
        }
      } else if (op1.isLongConstant()) {
        long val1 = op1.asLongConstant().value;
        // ONLY OP1 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
        if (val1 == 0L) {                  // 0 >>> x == 0
          Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), op1);
          return DefUseEffect.MOVE_FOLDED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect longXor(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_LONG_OPS) {
      canonicalizeCommutativeOperator(s);
      Operand op1 = Binary.getVal1(s);
      Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x ^ x == 0
        Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), LC(0));
        return DefUseEffect.MOVE_FOLDED;
      }
      if (op2.isLongConstant()) {
        long val2 = op2.asLongConstant().value;
        if (op1.isLongConstant()) {
          // BOTH CONSTANTS: FOLD
          long val1 = op1.asLongConstant().value;
          Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), LC(val1 ^ val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == -1L) {                // x ^ -1L == x ^ 0xff..ff = ~x
            Unary.mutate(s, LONG_NOT, Binary.getClearResult(s), Binary.getClearVal1(s));
            return DefUseEffect.REDUCED;
          }
          if (val2 == 0L) {                 // x ^ 0L == x
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect floatAdd(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_FLOAT_OPS) {
      canonicalizeCommutativeOperator(s);
      Operand op2 = Binary.getVal2(s);
      if (op2.isFloatConstant()) {
        float val2 = op2.asFloatConstant().value;
        Operand op1 = Binary.getVal1(s);
        if (op1.isFloatConstant()) {
          // BOTH CONSTANTS: FOLD
          float val1 = op1.asFloatConstant().value;
          Move.mutate(s, FLOAT_MOVE, Binary.getClearResult(s), FC(val1 + val2));
          return DefUseEffect.MOVE_FOLDED;
        }
        if (val2 == 0.0f) {
          // x + 0.0 is x (even is x is a Nan).
          Move.mutate(s, FLOAT_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
          return DefUseEffect.MOVE_REDUCED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect floatCmpg(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_INTEGER_OPS) {
      Operand op2 = Binary.getVal2(s);
      if (op2.isFloatConstant()) {
        Operand op1 = Binary.getVal1(s);
        if (op1.isFloatConstant()) {
          // BOTH CONSTANTS: FOLD
          float val1 = op1.asFloatConstant().value;
          float val2 = op2.asFloatConstant().value;
          int result = (val1 < val2) ? -1 : ((val1 == val2) ? 0 : 1);
          Move.mutate(s, INT_MOVE, Binary.getClearResult(s), IC(result));
          return DefUseEffect.MOVE_FOLDED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect floatCmpl(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_INTEGER_OPS) {
      Operand op2 = Binary.getVal2(s);
      if (op2.isFloatConstant()) {
        Operand op1 = Binary.getVal1(s);
        if (op1.isFloatConstant()) {
          // BOTH CONSTANTS: FOLD
          float val1 = op1.asFloatConstant().value;
          float val2 = op2.asFloatConstant().value;
          int result = (val1 > val2) ? 1 : ((val1 == val2) ? 0 : -1);
          Move.mutate(s, INT_MOVE, Binary.getClearResult(s), IC(result));
          return DefUseEffect.MOVE_FOLDED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect floatDiv(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_FLOAT_OPS) {
      Operand op2 = Binary.getVal2(s);
      if (op2.isFloatConstant()) {
        Operand op1 = Binary.getVal1(s);
        if (op1.isFloatConstant()) {
          // BOTH CONSTANTS: FOLD
          float val1 = op1.asFloatConstant().value;
          float val2 = op2.asFloatConstant().value;
          Move.mutate(s, FLOAT_MOVE, Binary.getClearResult(s), FC(val1 / val2));
          return DefUseEffect.MOVE_FOLDED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect floatMul(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_FLOAT_OPS) {
      canonicalizeCommutativeOperator(s);
      Operand op2 = Binary.getVal2(s);
      if (op2.isFloatConstant()) {
        float val2 = op2.asFloatConstant().value;
        Operand op1 = Binary.getVal1(s);
        if (op1.isFloatConstant()) {
          // BOTH CONSTANTS: FOLD
          float val1 = op1.asFloatConstant().value;
          Move.mutate(s, FLOAT_MOVE, Binary.getClearResult(s), FC(val1 * val2));
          return DefUseEffect.MOVE_FOLDED;
        }
        if (val2 == 1.0f) {
          // x * 1.0 is x, even if x is a NaN
          Move.mutate(s, FLOAT_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
          return DefUseEffect.MOVE_REDUCED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect floatNeg(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_FLOAT_OPS) {
      Operand op = Unary.getVal(s);
      if (op.isFloatConstant()) {
        // CONSTANT: FOLD
        float val = op.asFloatConstant().value;
        Move.mutate(s, FLOAT_MOVE, Unary.getClearResult(s), FC(-val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect floatRem(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_FLOAT_OPS) {
      Operand op2 = Binary.getVal2(s);
      if (op2.isFloatConstant()) {
        Operand op1 = Binary.getVal1(s);
        if (op1.isFloatConstant()) {
          // BOTH CONSTANTS: FOLD
          float val1 = op1.asFloatConstant().value;
          float val2 = op2.asFloatConstant().value;
          Move.mutate(s, FLOAT_MOVE, Binary.getClearResult(s), FC(val1 % val2));
          return DefUseEffect.MOVE_FOLDED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect floatSub(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_FLOAT_OPS) {
      Operand op1 = Binary.getVal1(s);
      Operand op2 = Binary.getVal2(s);
      if (op2.isFloatConstant()) {
        float val2 = op2.asFloatConstant().value;
        if (op1.isFloatConstant()) {
          // BOTH CONSTANTS: FOLD
          float val1 = op1.asFloatConstant().value;
          Move.mutate(s, FLOAT_MOVE, Binary.getClearResult(s), FC(val1 - val2));
          return DefUseEffect.MOVE_FOLDED;
        }
        if (val2 == 0.0f) {
          // x - 0.0 is x, even if x is a NaN
          Move.mutate(s, FLOAT_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
          return DefUseEffect.MOVE_REDUCED;
        }
      } else if (op1.isFloatConstant() && (op1.asFloatConstant().value == 0.0f)) {
        Unary.mutate(s, FLOAT_NEG, Binary.getClearResult(s), Binary.getClearVal2(s));
        return DefUseEffect.REDUCED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect floatSqrt(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_FLOAT_OPS) {
      Operand op = Unary.getVal(s);
      if (op.isFloatConstant()) {
        // CONSTANT: FOLD
        float val = op.asFloatConstant().value;
        Move.mutate(s, FLOAT_MOVE, Unary.getClearResult(s), FC((float)Math.sqrt(val)));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect doubleAdd(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_DOUBLE_OPS) {
      canonicalizeCommutativeOperator(s);
      Operand op2 = Binary.getVal2(s);
      if (op2.isDoubleConstant()) {
        double val2 = op2.asDoubleConstant().value;
        Operand op1 = Binary.getVal1(s);
        if (op1.isDoubleConstant()) {
          // BOTH CONSTANTS: FOLD
          double val1 = op1.asDoubleConstant().value;
          Move.mutate(s, DOUBLE_MOVE, Binary.getClearResult(s), DC(val1 + val2));
          return DefUseEffect.MOVE_FOLDED;
        }
        if (val2 == 0.0) {
          // x + 0.0 is x, even if x is a NaN
          Move.mutate(s, DOUBLE_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
          return DefUseEffect.MOVE_REDUCED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect doubleCmpg(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_INTEGER_OPS) {
      Operand op2 = Binary.getVal2(s);
      if (op2.isDoubleConstant()) {
        Operand op1 = Binary.getVal1(s);
        if (op1.isDoubleConstant()) {
          // BOTH CONSTANTS: FOLD
          double val1 = op1.asDoubleConstant().value;
          double val2 = op2.asDoubleConstant().value;
          int result = (val1 < val2) ? -1 : ((val1 == val2) ? 0 : 1);
          Move.mutate(s, INT_MOVE, Binary.getClearResult(s), IC(result));
          return DefUseEffect.MOVE_FOLDED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect doubleCmpl(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_INTEGER_OPS) {
      Operand op2 = Binary.getVal2(s);
      if (op2.isDoubleConstant()) {
        Operand op1 = Binary.getVal1(s);
        if (op1.isDoubleConstant()) {
          // BOTH CONSTANTS: FOLD
          double val1 = op1.asDoubleConstant().value;
          double val2 = op2.asDoubleConstant().value;
          int result = (val1 > val2) ? 1 : ((val1 == val2) ? 0 : -1);
          Move.mutate(s, DOUBLE_MOVE, Binary.getClearResult(s), IC(result));
          return DefUseEffect.MOVE_FOLDED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect doubleDiv(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_DOUBLE_OPS) {
      Operand op2 = Binary.getVal2(s);
      if (op2.isDoubleConstant()) {
        Operand op1 = Binary.getVal1(s);
        if (op1.isDoubleConstant()) {
          // BOTH CONSTANTS: FOLD
          double val1 = op1.asDoubleConstant().value;
          double val2 = op2.asDoubleConstant().value;
          Move.mutate(s, DOUBLE_MOVE, Binary.getClearResult(s), DC(val1 / val2));
          return DefUseEffect.MOVE_FOLDED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect doubleMul(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_DOUBLE_OPS) {
      canonicalizeCommutativeOperator(s);
      Operand op2 = Binary.getVal2(s);
      if (op2.isDoubleConstant()) {
        double val2 = op2.asDoubleConstant().value;
        Operand op1 = Binary.getVal1(s);
        if (op1.isDoubleConstant()) {
          // BOTH CONSTANTS: FOLD
          double val1 = op1.asDoubleConstant().value;
          Move.mutate(s, DOUBLE_MOVE, Binary.getClearResult(s), DC(val1 * val2));
          return DefUseEffect.MOVE_FOLDED;
        }
        if (val2 == 1.0) {
          // x * 1.0 is x even if x is a NaN
          Move.mutate(s, DOUBLE_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
          return DefUseEffect.MOVE_REDUCED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect doubleNeg(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_DOUBLE_OPS) {
      Operand op = Unary.getVal(s);
      if (op.isDoubleConstant()) {
        // CONSTANT: FOLD
        double val = op.asDoubleConstant().value;
        Move.mutate(s, DOUBLE_MOVE, Unary.getClearResult(s), DC(-val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect doubleRem(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_DOUBLE_OPS) {
      Operand op2 = Binary.getVal2(s);
      if (op2.isDoubleConstant()) {
        Operand op1 = Binary.getVal1(s);
        if (op1.isDoubleConstant()) {
          // BOTH CONSTANTS: FOLD
          double val1 = op1.asDoubleConstant().value;
          double val2 = op2.asDoubleConstant().value;
          Move.mutate(s, DOUBLE_MOVE, Binary.getClearResult(s), DC(val1 % val2));
          return DefUseEffect.MOVE_FOLDED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect doubleSub(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_DOUBLE_OPS) {
      Operand op1 = Binary.getVal1(s);
      Operand op2 = Binary.getVal2(s);
      if (op2.isDoubleConstant()) {
        double val2 = op2.asDoubleConstant().value;
        if (op1.isDoubleConstant()) {
          // BOTH CONSTANTS: FOLD
          double val1 = op1.asDoubleConstant().value;
          Move.mutate(s, DOUBLE_MOVE, Binary.getClearResult(s), DC(val1 - val2));
          return DefUseEffect.MOVE_FOLDED;
        }
        if (val2 == 0.0) {
          // x - 0.0 is x, even if x is a NaN
          Move.mutate(s, DOUBLE_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
          return DefUseEffect.MOVE_REDUCED;
        }
      } else if (op1.isDoubleConstant() && (op1.asDoubleConstant().value == 0.0)) {
        Unary.mutate(s, DOUBLE_NEG, Binary.getClearResult(s), Binary.getClearVal2(s));
        return DefUseEffect.REDUCED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect doubleSqrt(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_DOUBLE_OPS) {
      Operand op = Unary.getVal(s);
      if (op.isDoubleConstant()) {
        // CONSTANT: FOLD
        double val = op.asDoubleConstant().value;
        Move.mutate(s, DOUBLE_MOVE, Unary.getClearResult(s), DC(Math.sqrt(val)));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect double2Float(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_FLOAT_OPS) {
      Operand op = Unary.getVal(s);
      if (op.isDoubleConstant()) {
        // CONSTANT: FOLD
        double val = op.asDoubleConstant().value;
        Move.mutate(s, FLOAT_MOVE, Unary.getClearResult(s), FC((float) val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect double2Int(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_INTEGER_OPS) {
      Operand op = Unary.getVal(s);
      if (op.isDoubleConstant()) {
        // CONSTANT: FOLD
        double val = op.asDoubleConstant().value;
        Move.mutate(s, INT_MOVE, Unary.getClearResult(s), IC((int) val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect double2Long(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_LONG_OPS) {
      Operand op = Unary.getVal(s);
      if (op.isDoubleConstant()) {
        // CONSTANT: FOLD
        double val = op.asDoubleConstant().value;
        Move.mutate(s, LONG_MOVE, Unary.getClearResult(s), LC((long) val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect doubleAsLongBits(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_LONG_OPS) {
      Operand op = Unary.getVal(s);
      if (op.isDoubleConstant()) {
        // CONSTANT: FOLD
        double val = op.asDoubleConstant().value;
        Move.mutate(s, LONG_MOVE, Unary.getClearResult(s), LC(Double.doubleToLongBits(val)));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect int2Double(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_DOUBLE_OPS) {
      Operand op = Unary.getVal(s);
      if (op.isIntConstant()) {
        // CONSTANT: FOLD
        int val = op.asIntConstant().value;
        Move.mutate(s, DOUBLE_MOVE, Unary.getClearResult(s), DC(val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect int2Byte(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_INTEGER_OPS) {
      Operand op = Unary.getVal(s);
      if (op.isIntConstant()) {
        // CONSTANT: FOLD
        int val = op.asIntConstant().value;
        Move.mutate(s, INT_MOVE, Unary.getClearResult(s), IC((byte) val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect int2UShort(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_INTEGER_OPS) {
      Operand op = Unary.getVal(s);
      if (op.isIntConstant()) {
        // CONSTANT: FOLD
        int val = op.asIntConstant().value;
        Move.mutate(s, INT_MOVE, Unary.getClearResult(s), IC((char) val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect int2Float(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_FLOAT_OPS) {
      Operand op = Unary.getVal(s);
      if (op.isIntConstant()) {
        // CONSTANT: FOLD
        int val = op.asIntConstant().value;
        Move.mutate(s, FLOAT_MOVE, Unary.getClearResult(s), FC(val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect int2Long(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_LONG_OPS) {
      Operand op = Unary.getVal(s);
      if (op.isIntConstant()) {
        // CONSTANT: FOLD
        int val = op.asIntConstant().value;
        Move.mutate(s, LONG_MOVE, Unary.getClearResult(s), LC(val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect int2AddrSigExt(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_REF_OPS) {
      Operand op = Unary.getVal(s);
      if (op.isIntConstant()) {
        // CONSTANT: FOLD
        int val = op.asIntConstant().value;
        Move.mutate(s, REF_MOVE, Unary.getClearResult(s), AC(Address.fromIntSignExtend(val)));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect int2AddrZerExt(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_REF_OPS) {
      Operand op = Unary.getVal(s);
      if (op.isIntConstant()) {
        // CONSTANT: FOLD
        int val = op.asIntConstant().value;
        Move.mutate(s, REF_MOVE, Unary.getClearResult(s), AC(Address.fromIntZeroExtend(val)));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect long2Addr(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_REF_OPS) {
      Operand op = Unary.getVal(s);
      if (op.isLongConstant()) {
        if (VM.BuildFor64Addr) {
          // CONSTANT: FOLD
          long val = op.asLongConstant().value;
          Move.mutate(s, REF_MOVE, Unary.getClearResult(s), AC(Address.fromLong(val)));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // CONSTANT: FOLD
          int val = (int) op.asLongConstant().value;
          Move.mutate(s, REF_MOVE, Unary.getClearResult(s), AC(Address.fromIntZeroExtend(val)));
          return DefUseEffect.MOVE_FOLDED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect int2Short(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_INTEGER_OPS) {
      Operand op = Unary.getVal(s);
      if (op.isIntConstant()) {
        // CONSTANT: FOLD
        int val = op.asIntConstant().value;
        Move.mutate(s, INT_MOVE, Unary.getClearResult(s), IC((short) val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect intBitsAsFloat(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_FLOAT_OPS) {
      Operand op = Unary.getVal(s);
      if (op.isIntConstant()) {
        // CONSTANT: FOLD
        int val = op.asIntConstant().value;
        Move.mutate(s, FLOAT_MOVE, Unary.getClearResult(s), FC(Float.intBitsToFloat(val)));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect addr2Int(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_INTEGER_OPS) {
      Operand op = Unary.getVal(s);
      if (op.isConstant() && !op.isMovableObjectConstant()) {
        // CONSTANT: FOLD
        Address val = getAddressValue(op);
        Move.mutate(s, INT_MOVE, Unary.getClearResult(s), IC(val.toInt()));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect addr2Long(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_LONG_OPS) {
      Operand op = Unary.getVal(s);
      if (op.isConstant() && !op.isMovableObjectConstant()) {
        // CONSTANT: FOLD
        Address val = getAddressValue(op);
        Move.mutate(s, LONG_MOVE, Unary.getClearResult(s), LC(val.toLong()));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect float2Double(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_DOUBLE_OPS) {
      Operand op = Unary.getVal(s);
      if (op.isFloatConstant()) {
        // CONSTANT: FOLD
        float val = op.asFloatConstant().value;
        Move.mutate(s, DOUBLE_MOVE, Unary.getClearResult(s), DC(val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect float2Int(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_INTEGER_OPS) {
      Operand op = Unary.getVal(s);
      if (op.isFloatConstant()) {
        // CONSTANT: FOLD
        float val = op.asFloatConstant().value;
        Move.mutate(s, INT_MOVE, Unary.getClearResult(s), IC((int) val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect float2Long(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_LONG_OPS) {
      Operand op = Unary.getVal(s);
      if (op.isFloatConstant()) {
        // CONSTANT: FOLD
        float val = op.asFloatConstant().value;
        Move.mutate(s, LONG_MOVE, Unary.getClearResult(s), LC((long) val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect floatAsIntBits(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_INTEGER_OPS) {
      Operand op = Unary.getVal(s);
      if (op.isFloatConstant()) {
        // CONSTANT: FOLD
        float val = op.asFloatConstant().value;
        Move.mutate(s, INT_MOVE, Unary.getClearResult(s), IC(Float.floatToIntBits(val)));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect long2Float(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_FLOAT_OPS) {
      Operand op = Unary.getVal(s);
      if (op.isLongConstant()) {
        // CONSTANT: FOLD
        long val = op.asLongConstant().value;
        Move.mutate(s, FLOAT_MOVE, Unary.getClearResult(s), FC(val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect long2Int(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_INTEGER_OPS) {
      Operand op = Unary.getVal(s);
      if (op.isLongConstant()) {
        // CONSTANT: FOLD
        long val = op.asLongConstant().value;
        Move.mutate(s, INT_MOVE, Unary.getClearResult(s), IC((int) val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect long2Double(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_DOUBLE_OPS) {
      Operand op = Unary.getVal(s);
      if (op.isLongConstant()) {
        // CONSTANT: FOLD
        long val = op.asLongConstant().value;
        Move.mutate(s, DOUBLE_MOVE, Unary.getClearResult(s), DC(val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect longBitsAsDouble(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_DOUBLE_OPS) {
      Operand op = Unary.getVal(s);
      if (op.isLongConstant()) {
        // CONSTANT: FOLD
        long val = op.asLongConstant().value;
        Move.mutate(s, DOUBLE_MOVE, Unary.getClearResult(s), DC(Double.longBitsToDouble(val)));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect arrayLength(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_FIELD_OPS) {
      Operand op = GuardedUnary.getVal(s);
      if (op.isObjectConstant()) {
        int length = 0;
        if (op.getType().getArrayElementType().isCodeType()) {
          length = ((CodeArray)(op.asObjectConstant().value)).length();
        } else {
          length = Array.getLength(op.asObjectConstant().value);
        }
        Move.mutate(s, INT_MOVE, GuardedUnary.getClearResult(s), IC(length));
        return DefUseEffect.MOVE_FOLDED;
      } else if (op.isNullConstant()) {
        // TODO: this arraylength operation is junk so destroy
        return DefUseEffect.UNCHANGED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect boundsCheck(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_FIELD_OPS) {
      Operand ref = BoundsCheck.getRef(s);
      Operand index = BoundsCheck.getIndex(s);
      if (ref.isNullConstant()) {
        // Should already be caught by nullcheck simplification
        return DefUseEffect.UNCHANGED;
      } else if (index.isIntConstant()) {
        int indexAsInt = index.asIntConstant().value;
        if (indexAsInt < 0) {
          Trap.mutate(s, TRAP, BoundsCheck.getClearGuardResult(s), TrapCodeOperand.ArrayBounds());
          return DefUseEffect.TRAP_REDUCED;
        } else if (ref.isConstant()) {
          if (ref.isObjectConstant()) {
            int refLength = Array.getLength(ref.asObjectConstant().value);
            if (indexAsInt < refLength) {
              Move.mutate(s, GUARD_MOVE, BoundsCheck.getClearGuardResult(s), BoundsCheck.getClearGuard(s));
              return Move.getVal(s).isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
            } else {
              Trap.mutate(s, TRAP, BoundsCheck.getClearGuardResult(s), TrapCodeOperand.ArrayBounds());
              return DefUseEffect.TRAP_REDUCED;
            }
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect call(boolean HIR, AbstractRegisterPool regpool, Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_FIELD_OPS) {
      MethodOperand methOp = Call.getMethod(s);
      if (methOp == null) {
        return DefUseEffect.UNCHANGED;
      }
      if (methOp.isVirtual() && !methOp.hasPreciseTarget()) {
        Operand calleeThis = Call.getParam(s, 0);
        if (calleeThis.isNullConstant()) {
          // Should already be caught by nullcheck simplification
          return DefUseEffect.UNCHANGED;
        } else if (calleeThis.isConstant() || calleeThis.asRegister().isPreciseType()) {
          TypeReference calleeClass = calleeThis.getType();
          if (calleeClass.isResolved()) {
            methOp.refine(calleeClass.peekType());
            return DefUseEffect.UNCHANGED;
          }
        }
      } else if (methOp.isStatic() && methOp.hasPreciseTarget() && HIR) {
        RVMMethod method = methOp.getTarget();
        // Can we remove the need for RVMClass.getClass...FromStackFrame to walk the stack?
        if (method == Entrypoints.getClassLoaderFromStackFrame ||
            method == Entrypoints.getClassFromStackFrame) {
          Operand frameOp = Call.getParam(s, 0);
          if (frameOp.isIntConstant()) {
            int frame = frameOp.asIntConstant().value;
            InlineSequence currentFrame = s.position;
            while (frame > 0 && currentFrame != null) {
              currentFrame = currentFrame.caller;
              frame--;
            }
            if (currentFrame != null) {
              // we found the caller
              ObjectConstantOperand cop;
              if (method == Entrypoints.getClassLoaderFromStackFrame) {
                cop = new ObjectConstantOperand(currentFrame.method.getDeclaringClass().getTypeRef().getClassLoader(), Offset.zero());
              } else {
                cop = new ObjectConstantOperand(currentFrame.method.getDeclaringClass(), Offset.zero());
              }
              Move.mutate(s, REF_MOVE, Call.getClearResult(s), cop);
              return DefUseEffect.MOVE_FOLDED;
            }
          }
        }
      }
      if (!VM.runningVM && methOp.hasPreciseTarget() && methOp.getTarget().isRuntimePure()) {
        RVMMethod method = methOp.getTarget();
        switch(method.getAnnotation(org.vmmagic.pragma.RuntimePure.class).value()) {
        case Unavailable: // not available at boot image write time
          return DefUseEffect.UNCHANGED;
        default:
          throw new Error("Unhandled RuntimePure value: " +
            method.getAnnotation(org.vmmagic.pragma.RuntimePure.class).value());
        }
      } else if (methOp.hasPreciseTarget() && methOp.getTarget().isPure()) {
        // Look for a precise method call to a pure method with all constant arguments
        RVMMethod method = methOp.getTarget();
        int n = Call.getNumberOfParams(s);
        for(int i=0; i < n; i++) {
          Operand param = Call.getParam(s,i);
          if (!param.isConstant() || param.isNullConstant()) {
            return DefUseEffect.UNCHANGED;
          }
        }
        // Pure method with all constant arguments. Perform reflective method call
        Object thisArg = null;
        TypeReference[] paramTypes = method.getParameterTypes();
        Object[] otherArgs;
        Object result = null;
        if (!methOp.isStatic()) {
          thisArg = boxConstantOperand((ConstantOperand)Call.getParam(s,0), method.getDeclaringClass().getTypeRef());
          n--;
          otherArgs = new Object[n];
          for(int i=0; i < n; i++) {
            otherArgs[i] = boxConstantOperand((ConstantOperand)Call.getParam(s,i+1),paramTypes[i]);
          }
        } else {
          otherArgs = new Object[n];
          for(int i=0; i < n; i++) {
            otherArgs[i] = boxConstantOperand((ConstantOperand)Call.getParam(s,i),paramTypes[i]);
          }
        }
        Throwable t = null;
        Method m = null;
        try {
          if (VM.runningVM) {
            result = Reflection.invoke(method, null, thisArg, otherArgs, false);
          } else {
            Class<?>[] argTypes = new Class<?>[n];
            for(int i=0; i < n; i++) {
              argTypes[i] = Call.getParam(s,i).getType().resolve().getClassForType();
            }
            m = method.getDeclaringClass().getClassForType().getDeclaredMethod(method.getName().toString(), argTypes);
            result = m.invoke(thisArg, otherArgs);
          }
        } catch (Throwable e) { t = e;}
        if (t != null) {
          // Call threw exception so leave in to generate at execution time
          return DefUseEffect.UNCHANGED;
        }
        if (result == null) throw new OptimizingCompilerException("Method " + m + "/" + method + " returned null");
        if(method.getReturnType().isVoidType()) {
          Empty.mutate(s, NOP);
          return DefUseEffect.REDUCED;
        } else {
          Operator moveOp = IRTools.getMoveOp(method.getReturnType());
          Move.mutate(s,moveOp, Call.getClearResult(s),
                      boxConstantObjectAsOperand(result, method.getReturnType()));
          return DefUseEffect.MOVE_FOLDED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  /**
   * Package up a constant operand as an object
   * @param op the constant operand to package
   * @param t the type of the object (needed to differentiate primitive from numeric types..)
   * @return the object
   */
  private static Object boxConstantOperand(ConstantOperand op, TypeReference t){
    if (op.isObjectConstant()) {
      return op.asObjectConstant().value;
    } else if (op.isLongConstant()) {
      return op.asLongConstant().value;
    } else if (op.isFloatConstant()) {
      return op.asFloatConstant().value;
    } else if (op.isDoubleConstant()) {
      return op.asDoubleConstant().value;
    } else if (t.isIntType()) {
      return op.asIntConstant().value;
    } else if (t.isBooleanType()) {
      return op.asIntConstant().value == 1;
    } else if (t.isByteType()) {
      return (byte)op.asIntConstant().value;
    } else if (t.isCharType()) {
      return (char)op.asIntConstant().value;
    } else if (t.isShortType()) {
      return (short)op.asIntConstant().value;
    } else {
      throw new OptimizingCompilerException("Trying to box an VM magic unboxed type ("+op+
                                            ")for a pure method call is not possible:\n"+op.instruction+
                                            "\n at "+op.instruction.position);
    }
  }
  /**
   * Package up an object as a constant operand
   * @param x the object to package
   * @param t the type of the object (needed to differentiate primitive from numeric types..)
   * @return the constant operand
   */
  private static ConstantOperand boxConstantObjectAsOperand(Object x, TypeReference t){
    if (VM.VerifyAssertions) VM._assert(!t.isUnboxedType());
    if (x == null) {
      throw new Error("Field of type: " + t + " is null");
    }
    if (t.isIntType()) {
      return IC((Integer)x);
    } else if (t.isBooleanType()) {
      return IC((Boolean)x ? 1 : 0);
    } else if (t.isByteType()) {
      return IC((Byte)x);
    } else if (t.isCharType()) {
      return IC((Character)x);
    } else if (t.isShortType()) {
      return IC((Short)x);
    } else if (t.isLongType()) {
      return LC((Long)x);
    } else if (t.isFloatType()) {
      return FC((Float)x);
    } else if (t.isDoubleType()) {
      return DC((Double)x);
    } else if (x instanceof String) {
      // Handle as object constant but make sure to use interned String
      x = ((String)x).intern();
      return new ObjectConstantOperand(x, Offset.zero());
    } else if (x instanceof Class) {
      // Handle as object constant
      return new ObjectConstantOperand(x, Offset.zero());
    } else {
      return new ObjectConstantOperand(x, Offset.zero());
    }
  }

  private static DefUseEffect getField(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_FIELD_OPS) {
      Operand ref = GetField.getRef(s);
      if (VM.VerifyAssertions && ref.isNullConstant()) {
        // Simplify to an unreachable operand, this instruction is dead code
        // guarded by a nullcheck that should already have been simplified
        RegisterOperand result = GetField.getClearResult(s);
        Move.mutate(s, IRTools.getMoveOp(result.getType()), result, new UnreachableOperand());
        return DefUseEffect.MOVE_FOLDED;
      } else if (opts.SIMPLIFY_CHASE_FINAL_FIELDS && ref.isObjectConstant()) {
        // A constant object references this field which is
        // final. As the reference is final the constructor
        // of the referred object MUST have already completed.
        // This also implies that the type MUST have been resolved.
        RVMField field = GetField.getLocation(s).getFieldRef().resolve();
        if (field.isFinal() && field.getDeclaringClass().isInitialized()) {
          try {
            ConstantOperand op = StaticFieldReader.getFieldValueAsConstant(field, ref.asObjectConstant().value);
            Move.mutate(s, IRTools.getMoveOp(field.getType()), GetField.getClearResult(s), op);
            return DefUseEffect.MOVE_FOLDED;
          } catch (NoSuchFieldException e) {
            if (VM.runningVM) {
              // this is unexpected
              throw new Error("Unexpected exception", e);
            } else {
              // Field not found during bootstrap due to chasing a field
              // only valid in the bootstrap JVM
            }
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect getObjTib(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_TIB_OPS) {
      Operand op = GuardedUnary.getVal(s);
      if (op.isNullConstant()) {
        // Simplify to an unreachable operand, this instruction is dead code
        // guarded by a nullcheck that should already have been simplified
        RegisterOperand result = GetField.getClearResult(s);
        Move.mutate(s, IRTools.getMoveOp(result.getType()), result, new UnreachableOperand());
        return DefUseEffect.MOVE_FOLDED;
      } else if (op.isConstant()) {
        final TypeReference typeRef = op.getType();
        if (typeRef.isResolved()) {
          Move.mutate(s, REF_MOVE, GuardedUnary.getClearResult(s), new TIBConstantOperand(op.getType().peekType()));
          return DefUseEffect.MOVE_FOLDED;
        }
      } else {
        RegisterOperand rop = op.asRegister();
        TypeReference typeRef = rop.getType();
        // Is the type of this register only one possible type?
        if (typeRef.isResolved() && rop.isPreciseType() && typeRef.resolve().isInstantiated()) {
          // before simplifying ensure that the type is instantiated, this stops
          // constant propagation potentially moving the TIB constant before the
          // runtime call that instantiates it
          Move.mutate(s,
                      REF_MOVE,
                      GuardedUnary.getClearResult(s),
                      new TIBConstantOperand(typeRef.peekType()));
          return DefUseEffect.MOVE_FOLDED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect getClassTib(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_TIB_OPS) {
      TypeOperand typeOp = Unary.getVal(s).asType();
      if (typeOp.getTypeRef().isResolved()) {
        Move.mutate(s,
                    REF_MOVE,
                    Unary.getClearResult(s),
                    new TIBConstantOperand(typeOp.getTypeRef().peekType()));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect getTypeFromTib(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_TIB_OPS) {
      Operand tibOp = Unary.getVal(s);
      if (tibOp.isTIBConstant()) {
        TIBConstantOperand tib = tibOp.asTIBConstant();
        Move.mutate(s, REF_MOVE, Unary.getClearResult(s), new ObjectConstantOperand(tib.value, Offset.zero()));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect getArrayElementTibFromTib(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_TIB_OPS) {
      Operand tibOp = Unary.getVal(s);
      if (tibOp.isTIBConstant()) {
        TIBConstantOperand tib = tibOp.asTIBConstant();
        Move.mutate(s,
                    REF_MOVE,
                    Unary.getClearResult(s),
                    new TIBConstantOperand(tib.value.asArray().getElementType()));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect getSuperclassIdsFromTib(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_TIB_OPS) {
      Operand tibOp = Unary.getVal(s);
      if (tibOp.isTIBConstant()) {
        TIBConstantOperand tib = tibOp.asTIBConstant();
        Move.mutate(s,
                    REF_MOVE,
                    Unary.getClearResult(s),
                    new ObjectConstantOperand(tib.value.getSuperclassIds(), Offset.zero()));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect getDoesImplementFromTib(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_TIB_OPS) {
      Operand tibOp = Unary.getVal(s);
      if (tibOp.isTIBConstant()) {
        TIBConstantOperand tib = tibOp.asTIBConstant();
        Move.mutate(s,
                    REF_MOVE,
                    Unary.getClearResult(s),
                    new ObjectConstantOperand(tib.value.getDoesImplement(), Offset.zero()));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect refLoad(Instruction s, OptOptions opts) {
    if (opts.SIMPLIFY_TIB_OPS) {
      Operand base = Load.getAddress(s);
      if (base.isTIBConstant()) {
        TIBConstantOperand tib = base.asTIBConstant();
        Operand offset = Load.getOffset(s);
        if (tib.value.isInstantiated() && offset.isConstant()) {
          // Reading from a fixed offset from an effectively
          // constant array
          int intOffset;
          if (offset.isIntConstant()) {
            intOffset = offset.asIntConstant().value;
          } else {
            intOffset = offset.asAddressConstant().value.toInt();
          }
          int intSlot = intOffset >> LOG_BYTES_IN_ADDRESS;

          // Create appropriate constant operand for TIB slot
          ConstantOperand result;
          TIB tibArray = tib.value.getTypeInformationBlock();
          if (tibArray.slotContainsTib(intSlot)) {
            RVMType typeOfTIB = ((TIB)tibArray.get(intSlot)).getType();
            result = new TIBConstantOperand(typeOfTIB);
          } else if (tibArray.slotContainsCode(intSlot)) {
            // Only generate code constants when we want to make
            // some virtual calls go via the JTOC
            if (opts.H2L_CALL_VIA_JTOC) {
              RVMMethod method = tib.value.getTIBMethodAtSlot(intSlot);
              result = new CodeConstantOperand(method);
            } else {
              return DefUseEffect.UNCHANGED;
            }
          } else {
            if (tibArray.get(intSlot) == null) {
              result = new NullConstantOperand();
            } else {
              result = new ObjectConstantOperand(tibArray.get(intSlot), Offset.zero());
            }
          }
          Move.mutate(s, REF_MOVE, Load.getClearResult(s), result);
          return DefUseEffect.MOVE_FOLDED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  /**
   * To reduce the number of conditions to consider, we
   * transform all commutative
   * operators to a canoncial form.  The following forms are considered
   * canonical:
   * <ul>
   * <li> <code> Reg = Reg <op> Reg </code>
   * <li> <code> Reg = Reg <op> Constant </code>
   * <li> <code> Reg = Constant <op> Constant </code>
   * </ul>
   */
  private static void canonicalizeCommutativeOperator(Instruction instr) {
    if (Binary.getVal1(instr).isConstant()) {
      Operand tmp = Binary.getClearVal1(instr);
      Binary.setVal1(instr, Binary.getClearVal2(instr));
      Binary.setVal2(instr, tmp);
    }
  }

  /**
   * Compute 2 raised to the power v, 0 <= v <= 31
   */
  private static int PowerOf2(int v) {
    int i = 31;
    int power = -1;
    for (; v != 0; v = v << 1, i--) {
      if (v < 0) {
        if (power == -1) {
          power = i;
        } else {
          return -1;
        }
      }
    }
    return power;
  }

  /**
   * Turn the given operand encoding an address constant into an Address
   */
  private static Address getAddressValue(Operand op) {
    if (op instanceof NullConstantOperand) {
      return Address.zero();
    }
    if (op instanceof AddressConstantOperand) {
      return op.asAddressConstant().value;
    }
    if (op instanceof IntConstantOperand) {
      return Address.fromIntSignExtend(op.asIntConstant().value);
    }
    if (op instanceof LongConstantOperand) {
      if (VM.BuildFor64Addr) {
          return Address.fromLong(op.asLongConstant().value);
      } else {
          return Address.fromIntZeroExtend((int)op.asLongConstant().value);
      }
    }
    if (op instanceof ObjectConstantOperand) {
      if (VM.VerifyAssertions) VM._assert(!op.isMovableObjectConstant());
      return Magic.objectAsAddress(op.asObjectConstant().value);
    }
    throw new OptimizingCompilerException("Cannot getAddressValue from this operand " + op);
  }
}
