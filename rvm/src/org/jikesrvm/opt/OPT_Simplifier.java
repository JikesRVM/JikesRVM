/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.opt;

import org.jikesrvm.classloader.*;
import org.jikesrvm.VM;
import org.jikesrvm.VM_TIBLayoutConstants;
import org.jikesrvm.opt.ir.*;
import org.vmmagic.unboxed.*;
import java.lang.reflect.Array;
import static org.jikesrvm.VM_SizeConstants.*;
import static org.jikesrvm.opt.ir.OPT_Operators.*;
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
 *
 * @author Dave Grove
 * @author Ian Rogers
 */
public abstract class OPT_Simplifier extends OPT_IRTools {
  // NOTE: The convention is that constant folding is controlled based
  // on the type of the result of the operator, not the type of its inputs.
  /** 
   * Constant fold integer operations?
   */
  public static final boolean CF_INT = true;
  /** 
   * Constant fold address operations?
   */
  public static final boolean CF_LONG = true;

  /** 
   * Constant fold address operations?
   */
  public static final boolean CF_ADDR = true;

  /** 
   * Constant fold float operations?  Default is true, flip to avoid
   * consuming precious JTOC slots to hold new constant values.
   */
  public static final boolean CF_FLOAT = true;
  /** 
   * Constant fold double operations?  Default is true, flip to avoid
   * consuming precious JTOC slots to hold new constant values.
   */
  public static final boolean CF_DOUBLE = true;
  /** 
   * Constant fold field operations?  Default is true, flip to avoid
   * consuming precious JTOC slots to hold new constant values.
   */
  public static final boolean CF_FIELDS = false;

  /** 
   * Constant fold TIB operations?  Default is true, flip to avoid
   * consuming precious JTOC slots to hold new constant values.
   */
  public static final boolean CF_TIB = false;

  /**
   * Effect of the simplification on Def-Use chains
   */
  public enum DefUseEffect{
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
   * @param regpool register pool in case simplification requires a temporary register
   * @param s the instruction to simplify
   * @return one of UNCHANGED, MOVE_FOLDED, MOVE_REDUCED, TRAP_REDUCED, REDUCED
   */
  public static DefUseEffect simplify(OPT_AbstractRegisterPool regpool, OPT_Instruction s) {
    DefUseEffect result;
    char opcode = s.getOpcode();
    switch (opcode) {
      ////////////////////
      // GUARD operations
      ////////////////////
    case GUARD_COMBINE_opcode:
      result = guardCombine(s);
      break;
      ////////////////////
      // TRAP operations
      ////////////////////
    case TRAP_IF_opcode:
      result = trapIf(s);
      break;
    case NULL_CHECK_opcode:
      result = nullCheck(s);
      break;
    case INT_ZERO_CHECK_opcode:
      result = intZeroCheck(s);
      break;
    case LONG_ZERO_CHECK_opcode:
      result = longZeroCheck(s);
      break;
    case CHECKCAST_opcode:
      result = checkcast(regpool, s);
      break;
    case CHECKCAST_UNRESOLVED_opcode:
      result = checkcast(regpool, s);
      break;
    case CHECKCAST_NOTNULL_opcode:
      result = checkcastNotNull(s);
      break;
    case INSTANCEOF_opcode:
      result = instanceOf(regpool, s);
      break;
    case INSTANCEOF_NOTNULL_opcode:
      result = instanceOfNotNull(s);
      break;
    case OBJARRAY_STORE_CHECK_opcode:
      result = objarrayStoreCheck(s);
      break;
    case OBJARRAY_STORE_CHECK_NOTNULL_opcode:
      result = objarrayStoreCheckNotNull(s);
      break;
    case MUST_IMPLEMENT_INTERFACE_opcode:
      result = mustImplementInterface(s);
      break;
      ////////////////////
      // Conditional moves
      ////////////////////
    case INT_COND_MOVE_opcode:
      result = intCondMove(s);
      break;
    case LONG_COND_MOVE_opcode:
      result = longCondMove(s);
      break;
    case FLOAT_COND_MOVE_opcode:
      result = floatCondMove(s);
      break;
    case DOUBLE_COND_MOVE_opcode:
      result = doubleCondMove(s);
      break;
    case REF_COND_MOVE_opcode:
      result = refCondMove(s);
      break;
    case GUARD_COND_MOVE_opcode:
      result = guardCondMove(s);
      break;
      ////////////////////
      // INT ALU operations
      ////////////////////
    case BOOLEAN_NOT_opcode:
      result = booleanNot(s);
      break;
    case BOOLEAN_CMP_INT_opcode:
      result = booleanCmpInt(s);
      break;
    case BOOLEAN_CMP_ADDR_opcode:
      result = booleanCmpAddr(s);
      break;
    case INT_ADD_opcode:
      result = intAdd(s);
      break;
    case INT_AND_opcode:
      result = intAnd(s);
      break;
    case INT_DIV_opcode:
      result = intDiv(s);
      break;
    case INT_MUL_opcode:
      result = intMul(regpool, s);
      break;
    case INT_NEG_opcode:
      result = intNeg(s);
      break;
    case INT_NOT_opcode:
      result = intNot(s);
      break;
    case INT_OR_opcode:
      result = intOr(s);
      break;
    case INT_REM_opcode:
      result = intRem(s);
      break;
    case INT_SHL_opcode:
      result = intShl(s);
      break;
    case INT_SHR_opcode:
      result = intShr(s);
      break;
    case INT_SUB_opcode:
      result = intSub(s);
      break;
    case INT_USHR_opcode:
      result = intUshr(s);
      break;
    case INT_XOR_opcode:
      result = intXor(s);
      break;
      ////////////////////
      // WORD ALU operations
      ////////////////////
    case REF_ADD_opcode:
      result = refAdd(s);
      break;
    case REF_AND_opcode:
      result = refAnd(s);
      break;
    case REF_SHL_opcode:
      result = refShl(s);
      break;
    case REF_SHR_opcode:
      result = refShr(s);
      break;
    case REF_NOT_opcode:
      result = refNot(s);
      break;
    case REF_OR_opcode:
      result = refOr(s);
      break;
    case REF_SUB_opcode:
      result = refSub(s);
      break;
    case REF_USHR_opcode:
      result = regUshr(s);
      break;
    case REF_XOR_opcode:
      result = refXor(s);
      break;
      ////////////////////
      // LONG ALU operations
      ////////////////////
    case LONG_ADD_opcode:
      result = longAdd(s);
      break;
    case LONG_AND_opcode:
      result = longAnd(s);
      break;
    case LONG_CMP_opcode:
      result = longCmp(s);
      break;
    case LONG_DIV_opcode:
      result = longDiv(s);
      break;
    case LONG_MUL_opcode:
      result = longMul(s);
      break;
    case LONG_NEG_opcode:
      result = longNeg(s);
      break;
    case LONG_NOT_opcode:
      result = longNot(s);
      break;
    case LONG_OR_opcode:
      result = longOr(s);
      break;
    case LONG_REM_opcode:
      result = longRem(s);
      break;
    case LONG_SHL_opcode:
      result = longShl(s);
      break;
    case LONG_SHR_opcode:
      result = longShr(s);
      break;
    case LONG_SUB_opcode:
      result = longSub(s);
      break;
    case LONG_USHR_opcode:
      result = longUshr(s);
      break;
    case LONG_XOR_opcode:
      result = longXor(s);
      break;
      ////////////////////
      // FLOAT ALU operations
      ////////////////////
    case FLOAT_ADD_opcode:
      result = floatAdd(s);
      break;
    case FLOAT_CMPG_opcode:
      result = floatCmpg(s);
      break;
    case FLOAT_CMPL_opcode:
      result = floatCmpl(s);
      break;
    case FLOAT_DIV_opcode:
      result = floatDiv(s);
      break;
    case FLOAT_MUL_opcode:
      result = floatMul(s);
      break;
    case FLOAT_NEG_opcode:
      result = floatNeg(s);
      break;
    case FLOAT_REM_opcode:
      result = floatRem(s);
      break;
    case FLOAT_SUB_opcode:
      result = floatSub(s);
      break;
      ////////////////////
      // DOUBLE ALU operations
      ////////////////////
    case DOUBLE_ADD_opcode:
      result = doubleAdd(s);
      break;
    case DOUBLE_CMPG_opcode:
      result = doubleCmpg(s);
      break;
    case DOUBLE_CMPL_opcode:
      result = doubleCmpl(s);
      break;
    case DOUBLE_DIV_opcode:
      result = doubleDiv(s);
      break;
    case DOUBLE_MUL_opcode:
      result = doubleMul(s);
      break;
    case DOUBLE_NEG_opcode:
      result = doubleNeg(s);
      break;
    case DOUBLE_REM_opcode:
      result = doubleRem(s);
      break;
    case DOUBLE_SUB_opcode:
      result = doubleSub(s);
      break;
      ////////////////////
      // CONVERSION operations
      ////////////////////
    case DOUBLE_2FLOAT_opcode:
      result = double2Float(s);
      break;
    case DOUBLE_2INT_opcode:
      result = double2Int(s);
      break;
    case DOUBLE_2LONG_opcode:
      result = double2Long(s);
      break;
    case DOUBLE_AS_LONG_BITS_opcode:
      result = doubleAsLongBits(s);
      break;
    case INT_2DOUBLE_opcode:
      result = int2Double(s);
      break;
    case INT_2BYTE_opcode:
      result = int2Byte(s);
      break;
    case INT_2USHORT_opcode:
      result = int2UShort(s);
      break;
    case INT_2FLOAT_opcode:
      result = int2Float(s);
      break;
    case INT_2LONG_opcode:
      result = int2Long(s);
      break;
    case INT_2ADDRSigExt_opcode:
      result = int2AddrSigExt(s);
      break;
    case INT_2ADDRZerExt_opcode:
      result = int2AddrZerExt(s);
      break;
    case LONG_2ADDR_opcode:
      result = long2Addr(s);
      break;
    case INT_2SHORT_opcode:
      result = int2Short(s);
      break;
    case INT_BITS_AS_FLOAT_opcode:
      result = intBitsAsFloat(s);
      break;
    case ADDR_2INT_opcode:
      result = addr2Int(s);
      break;
    case ADDR_2LONG_opcode:
      result = addr2Long(s);
      break;
    case FLOAT_2DOUBLE_opcode:
      result = float2Double(s);
      break;
    case FLOAT_2INT_opcode:
      result = float2Int(s);
      break;
    case FLOAT_2LONG_opcode:
      result = float2Long(s);
      break;
    case FLOAT_AS_INT_BITS_opcode:
      result = floatAsIntBits(s);
      break;
    case LONG_2FLOAT_opcode:
      result = long2Float(s);
      break;
    case LONG_2INT_opcode:
      result = long2Int(s);
      break;
    case LONG_2DOUBLE_opcode:
      result = long2Double(s);
      break;
    case LONG_BITS_AS_DOUBLE_opcode:
      result = longBitsAsDouble(s);
      break;
      ////////////////////
      // Field operations
      ////////////////////
    case ARRAYLENGTH_opcode:
      result = arrayLength(s);
      break;
    case BOUNDS_CHECK_opcode:
      result = boundsCheck(s);
      break;
    case CALL_opcode:
      result = call(s);
      break;
    case GETFIELD_opcode:
      result = getField(s);
      break;
    case GET_OBJ_TIB_opcode:
      result = getObjTib(s);
      break;
    case GET_CLASS_TIB_opcode:
      result = getClassTib(s);
      break;
    case GET_TYPE_FROM_TIB_opcode:
      result = getTypeFromTib(s);
      break;
    case GET_ARRAY_ELEMENT_TIB_FROM_TIB_opcode:
      result = getArrayElementTibFromTib(s);
      break;
    case GET_SUPERCLASS_IDS_FROM_TIB_opcode:
      result = getSuperclassIdsFromTib(s);
      break;
    case GET_DOES_IMPLEMENT_FROM_TIB_opcode:
      result = getDoesImplementFromTib(s);
      break;
    case REF_LOAD_opcode:
      result = refLoad(s);
      break;
    default:
      result = DefUseEffect.UNCHANGED;
    }
    if (VM.VerifyAssertions) {
        switch (result) {
        case MOVE_FOLDED:
            // Check move has constant RHS
            VM._assert(Move.conforms(s) &&
                       (Move.getVal(s) instanceof OPT_ConstantOperand),
                       "RHS of move " + s + " should be constant during simplification of "
                       + OPT_OperatorNames.operatorName[opcode]);
            break;
        case MOVE_REDUCED:
            // Check move has non-constant RHS
            VM._assert(Move.conforms(s) &&
                       !(Move.getVal(s) instanceof OPT_ConstantOperand),
                       "RHS of move " + s + " shouldn't be constant during simplification of "
                       + OPT_OperatorNames.operatorName[opcode]);
            break;
        default:
            // Nothing to check
        }
    }
    return result;
  }
  
  private static DefUseEffect guardCombine(OPT_Instruction s) {
    OPT_Operand op1 = Binary.getVal1(s);
    OPT_Operand op2 = Binary.getVal2(s);
    if (op1.similar(op2) || (op2 instanceof OPT_TrueGuardOperand)) {
      Move.mutate(s, GUARD_MOVE, Binary.getClearResult(s), op1);
      if (op1 instanceof OPT_TrueGuardOperand) {
        // BOTH true guards: FOLD
        return DefUseEffect.MOVE_FOLDED;
      } else {
        // ONLY OP2 IS TrueGuard: MOVE REDUCE
        return DefUseEffect.MOVE_REDUCED;
      }
    }
    else if(op1 instanceof OPT_TrueGuardOperand) {
      // ONLY OP1 IS TrueGuard: MOVE REDUCE
      Move.mutate(s, GUARD_MOVE, Binary.getClearResult(s), op2);
      return DefUseEffect.MOVE_REDUCED;
    }
    else {
      return DefUseEffect.UNCHANGED;
    }
  }
  private static DefUseEffect trapIf(OPT_Instruction s) {
   { 
      OPT_Operand op1 = TrapIf.getVal1(s);
      OPT_Operand op2 = TrapIf.getVal2(s);
      if (op1.isConstant()) {
        if (op2.isConstant()) {
          int willTrap = TrapIf.getCond(s).evaluate(op1, op2);
          if (willTrap == OPT_ConditionOperand.TRUE) {
            Trap.mutate(s, TRAP, TrapIf.getClearGuardResult(s), 
                        TrapIf.getClearTCode(s));
            return DefUseEffect.TRAP_REDUCED;
          } else if (willTrap == OPT_ConditionOperand.FALSE) {
            Move.mutate(s, GUARD_MOVE, TrapIf.getClearGuardResult(s), TG());
            return DefUseEffect.MOVE_FOLDED;
          }
        } else {
          // canonicalize
          TrapIf.mutate(s, TRAP_IF, TrapIf.getClearGuardResult(s),
                        TrapIf.getClearVal2(s),
                        TrapIf.getClearVal1(s),
                        TrapIf.getClearCond(s).flipOperands(), 
                        TrapIf.getClearTCode(s));
        }
      }
    }                   
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect nullCheck(OPT_Instruction s) {
      OPT_Operand ref = NullCheck.getRef(s);
      if (ref.isNullConstant() ||
          (ref.isAddressConstant() && ref.asAddressConstant().value.isZero())) {
          Trap.mutate(s, TRAP, NullCheck.getClearGuardResult(s),
                      OPT_TrapCodeOperand.NullPtr());
          return DefUseEffect.TRAP_REDUCED;
      } else if (ref.isConstant()) {
          // object, string, class or non-null address constant
          
          // Make the slightly suspect assumption that all non-zero address
          // constants are actually valid pointers. Not necessarily true,
          // but unclear what else we can do.
          Move.mutate(s, GUARD_MOVE, NullCheck.getClearGuardResult(s), TG());
          return DefUseEffect.MOVE_FOLDED;
      }
      else {
          return DefUseEffect.UNCHANGED;
      }
  }
  private static DefUseEffect intZeroCheck(OPT_Instruction s) {
   {
      OPT_Operand op = ZeroCheck.getValue(s);
      if (op.isIntConstant()) {
        int val = op.asIntConstant().value;
        if (val == 0) {
          Trap.mutate(s, TRAP, ZeroCheck.getClearGuardResult(s),
                      OPT_TrapCodeOperand.DivByZero());
          return DefUseEffect.TRAP_REDUCED;
        } else {
          Move.mutate(s, GUARD_MOVE, ZeroCheck.getClearGuardResult(s), TG());
          return DefUseEffect.MOVE_FOLDED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect longZeroCheck(OPT_Instruction s) {
   {
      OPT_Operand op = ZeroCheck.getValue(s);
      if (op.isLongConstant()) {
        long val = op.asLongConstant().value;
        if (val == 0L) {
          Trap.mutate(s, TRAP, ZeroCheck.getClearGuardResult(s),
                      OPT_TrapCodeOperand.DivByZero());
          return DefUseEffect.TRAP_REDUCED;
        } else {
          Move.mutate(s, GUARD_MOVE, ZeroCheck.getClearGuardResult(s), TG());
          return DefUseEffect.MOVE_FOLDED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect checkcast(OPT_AbstractRegisterPool regpool, OPT_Instruction s) {
    OPT_Operand ref = TypeCheck.getRef(s);
    if (ref.isNullConstant()) {
      Empty.mutate(s, NOP);
      return DefUseEffect.REDUCED;
    } else if (ref.isConstant()) {
      s.operator = CHECKCAST_NOTNULL;
      return checkcastNotNull(s);
    } else {
      VM_TypeReference lhsType = TypeCheck.getType(s).getTypeRef();
      VM_TypeReference rhsType = ref.getType();
      byte ans = OPT_ClassLoaderProxy.includesType(lhsType, rhsType);
      if (ans == OPT_Constants.YES) {
        Empty.mutate(s, NOP);
        return DefUseEffect.REDUCED;
      } else {
        // NOTE: OPT_Constants.NO can't help us because (T)null always succeeds
        return DefUseEffect.UNCHANGED;
      }
    }
  }
  private static DefUseEffect checkcastNotNull(OPT_Instruction s) {
    OPT_Operand ref = TypeCheck.getRef(s);
    VM_TypeReference lhsType = TypeCheck.getType(s).getTypeRef();
    VM_TypeReference rhsType = ref.getType();
    byte ans = OPT_ClassLoaderProxy.includesType(lhsType, rhsType);
    if (ans == OPT_Constants.YES) {
      Empty.mutate(s, NOP);
      return DefUseEffect.REDUCED;
    } else if (ans == OPT_Constants.NO) {
      VM_Type rType = rhsType.peekResolvedType();
      if (rType != null && rType.isClassType() && rType.asClass().isFinal()) {
        // only final (or precise) rhs types can be optimized since rhsType may be conservative
        Trap.mutate(s, TRAP, null, OPT_TrapCodeOperand.CheckCast());
        return DefUseEffect.TRAP_REDUCED;
      } else {
        return DefUseEffect.UNCHANGED;
      }
    }
    else {
      return DefUseEffect.UNCHANGED;
    }
  }
  private static DefUseEffect instanceOf(OPT_AbstractRegisterPool regpool, OPT_Instruction s) {
    OPT_Operand ref = InstanceOf.getRef(s);
    if (ref.isNullConstant()) {
      Move.mutate(s, INT_MOVE, InstanceOf.getClearResult(s), IC(0));
      return DefUseEffect.MOVE_FOLDED;
    } else if (ref.isConstant()) {
      s.operator = INSTANCEOF_NOTNULL;
      return instanceOfNotNull(s);
    } else {
      VM_TypeReference lhsType = InstanceOf.getType(s).getTypeRef();
      VM_TypeReference rhsType = ref.getType();
      byte ans = OPT_ClassLoaderProxy.includesType(lhsType, rhsType);
      // NOTE: OPT_Constants.YES doesn't help because ref may be null and null instanceof T is false
      if (ans == OPT_Constants.NO) {
        VM_Type rType = rhsType.peekResolvedType();
        if (rType != null && rType.isClassType() && rType.asClass().isFinal()) {
          // only final (or precise) rhs types can be optimized since rhsType may be conservative
          Move.mutate(s, INT_MOVE, InstanceOf.getClearResult(s), IC(0));
          return DefUseEffect.MOVE_FOLDED;
        }
        else {
          return DefUseEffect.UNCHANGED;
        }
      }
      else {
        return DefUseEffect.UNCHANGED;
      }
    }
  }
  private static DefUseEffect instanceOfNotNull(OPT_Instruction s) {
   {
      OPT_Operand ref = InstanceOf.getRef(s);
      VM_TypeReference lhsType = InstanceOf.getType(s).getTypeRef();
      VM_TypeReference rhsType = ref.getType();
      byte ans = OPT_ClassLoaderProxy.includesType(lhsType, rhsType);
      if (ans == OPT_Constants.YES) {
        Move.mutate(s, INT_MOVE, InstanceOf.getClearResult(s), IC(1));
        return DefUseEffect.MOVE_FOLDED;
      } else if (ans == OPT_Constants.NO) {
        VM_Type rType = rhsType.peekResolvedType();
        if (rType != null && rType.isClassType() && rType.asClass().isFinal()) {
          // only final (or precise) rhs types can be optimized since rhsType may be conservative
          Move.mutate(s, INT_MOVE, InstanceOf.getClearResult(s), IC(0));
          return DefUseEffect.MOVE_FOLDED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect objarrayStoreCheck(OPT_Instruction s){
    OPT_Operand val = StoreCheck.getVal(s);
    if (val.isNullConstant()) {
      // Writing null into an array is trivially safe
      Move.mutate(s, GUARD_MOVE, StoreCheck.getClearGuardResult(s), StoreCheck.getClearGuard(s));
      return DefUseEffect.MOVE_REDUCED;
    }
    else {
      OPT_Operand ref = StoreCheck.getRef(s);
      VM_TypeReference arrayTypeRef = ref.getType();
      VM_Type typeOfIMElem = arrayTypeRef.getInnermostElementType().peekResolvedType();
      if (typeOfIMElem != null) {
        VM_Type typeOfVal = val.getType().peekResolvedType();
        if ((typeOfIMElem == typeOfVal) &&
            (typeOfIMElem.isPrimitiveType() ||
             typeOfIMElem.asClass().isFinal())) {
          // Writing something of a final type to an array of that
          // final type is safe
          Move.mutate(s, GUARD_MOVE, StoreCheck.getClearGuardResult(s),
                      StoreCheck.getClearGuard(s));
          return DefUseEffect.MOVE_REDUCED;
        }
      }
      if (ref.isConstant() && (arrayTypeRef == VM_TypeReference.JavaLangObjectArray)) {
        // We know this to be an array of objects so any store must
        // be safe
        Move.mutate(s, GUARD_MOVE, StoreCheck.getClearGuardResult(s),
                    StoreCheck.getClearGuard(s));
        return DefUseEffect.MOVE_REDUCED;          
      }
      if (val.isConstant() && ref.isConstant()) {
        // writing a constant value into a constant array
        byte ans = OPT_ClassLoaderProxy.includesType(arrayTypeRef.getArrayElementType(), val.getType());
        if (ans == OPT_Constants.YES) {
          // all stores should succeed
          Move.mutate(s, GUARD_MOVE, StoreCheck.getClearGuardResult(s), StoreCheck.getClearGuard(s));
          return DefUseEffect.MOVE_REDUCED;
        }
        else if (ans == OPT_Constants.NO) {
          // all stores will fail
          Trap.mutate(s, TRAP, StoreCheck.getClearGuardResult(s), OPT_TrapCodeOperand.StoreCheck());
          return DefUseEffect.TRAP_REDUCED;
        }
      }
      return DefUseEffect.UNCHANGED;
    }
  }
  private static DefUseEffect objarrayStoreCheckNotNull(OPT_Instruction s){
    OPT_Operand val = StoreCheck.getVal(s);
    OPT_Operand ref = StoreCheck.getRef(s);
    VM_TypeReference arrayTypeRef = ref.getType();
    VM_Type typeOfIMElem = arrayTypeRef.getInnermostElementType().peekResolvedType();
    if (typeOfIMElem != null) {
      VM_Type typeOfVal = val.getType().peekResolvedType();
      if ((typeOfIMElem == typeOfVal) &&
          (typeOfIMElem.isPrimitiveType() ||
           typeOfIMElem.asClass().isFinal())) {
        // Writing something of a final type to an array of that
        // final type is safe
        Move.mutate(s, GUARD_MOVE, StoreCheck.getClearGuardResult(s), StoreCheck.getClearGuard(s));
        return DefUseEffect.MOVE_REDUCED;
      }
    }
    if (ref.isConstant() && (arrayTypeRef == VM_TypeReference.JavaLangObjectArray)) {
      // We know this to be an array of objects so any store must
      // be safe
      Move.mutate(s, GUARD_MOVE, StoreCheck.getClearGuardResult(s),
                  StoreCheck.getClearGuard(s));
      return DefUseEffect.MOVE_REDUCED;          
    }
    if (val.isConstant() && ref.isConstant()) {
      // writing a constant value into a constant array
      byte ans = OPT_ClassLoaderProxy.includesType(arrayTypeRef.getArrayElementType(), val.getType());
      if (ans == OPT_Constants.YES) {
        // all stores should succeed
        Move.mutate(s, GUARD_MOVE, StoreCheck.getClearGuardResult(s), StoreCheck.getClearGuard(s));
        return DefUseEffect.MOVE_REDUCED;
      }
      else if (ans == OPT_Constants.NO) {
        // all stores will fail
        Trap.mutate(s, TRAP, StoreCheck.getClearGuardResult(s), OPT_TrapCodeOperand.StoreCheck());
        return DefUseEffect.TRAP_REDUCED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect mustImplementInterface(OPT_Instruction s) {
    OPT_Operand ref = TypeCheck.getRef(s);
    if (ref.isNullConstant()) {
      // Possible sitatution from constant propagation. This operation
      // is really a nop as a null_check should have happened already
      Trap.mutate(s, TRAP, null,
                  OPT_TrapCodeOperand.NullPtr());
      return DefUseEffect.TRAP_REDUCED;
    } else {
      VM_TypeReference lhsType = TypeCheck.getType(s).getTypeRef(); // the interface that must be implemented
      VM_TypeReference rhsType = ref.getType();                     // our type
      byte ans = OPT_ClassLoaderProxy.includesType(lhsType, rhsType);
      if (ans == OPT_Constants.YES) {
        Empty.mutate(s, NOP);
        return DefUseEffect.REDUCED;
      } else if (ans == OPT_Constants.NO) {
        VM_Type rType = rhsType.peekResolvedType();
        if (rType != null && rType.isClassType() && rType.asClass().isFinal()) {
          // only final (or precise) rhs types can be optimized since rhsType may be conservative
          Trap.mutate(s, TRAP, null, OPT_TrapCodeOperand.MustImplement());
          return DefUseEffect.TRAP_REDUCED;
        }
      }
      return DefUseEffect.UNCHANGED;
    }
  }
  private static DefUseEffect intCondMove(OPT_Instruction s) {
   {
      OPT_Operand val1 = CondMove.getVal1(s);
      OPT_Operand val2 = CondMove.getVal2(s);
      int cond = CondMove.getCond(s).evaluate(val1, val2);
      if (cond != OPT_ConditionOperand.UNKNOWN) {
        // BOTH CONSTANTS OR SIMILAR: FOLD
        OPT_Operand val = 
          (cond == OPT_ConditionOperand.TRUE) ? CondMove.getClearTrueValue(s) 
          : CondMove.getClearFalseValue(s);
        Move.mutate(s, INT_MOVE, CondMove.getClearResult(s), val);
        return val.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
      }
      if (val1.isConstant() && !val2.isConstant()) {
        // Canonicalize by switching operands and fliping code.
        OPT_Operand tmp = CondMove.getClearVal1(s);
        CondMove.setVal1(s, CondMove.getClearVal2(s));
        CondMove.setVal2(s, tmp);
        CondMove.getCond(s).flipOperands();
      }
      OPT_Operand tv = CondMove.getTrueValue(s);
      OPT_Operand fv = CondMove.getFalseValue(s);
      if (tv.similar(fv)) {
        Move.mutate(s, INT_MOVE, CondMove.getClearResult(s), tv);
        return tv.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
      }
      if (tv.isIntConstant() && fv.isIntConstant() && !CondMove.getCond(s).isFLOATINGPOINT()) {
        int itv = tv.asIntConstant().value;
        int ifv = fv.asIntConstant().value;
        OPT_Operator op = null;
        if(val1.isLong()) {
          op = BOOLEAN_CMP_LONG;
        }
        else if(val1.isFloat()) {
          op = BOOLEAN_CMP_FLOAT;
        }
        else if(val1.isDouble()) {
          op = BOOLEAN_CMP_DOUBLE;
        }
        else {
          op = BOOLEAN_CMP_INT;
        }
        if (itv == 1 && ifv == 0) {
          BooleanCmp.mutate(s, op, CondMove.getClearResult(s),
                            CondMove.getClearVal1(s), CondMove.getClearVal2(s),
                            CondMove.getClearCond(s), new OPT_BranchProfileOperand());
          return DefUseEffect.REDUCED;
        }
        if (itv == 0 && ifv == 1) {
          BooleanCmp.mutate(s, op, CondMove.getClearResult(s),
                            CondMove.getClearVal1(s), CondMove.getClearVal2(s),
                            CondMove.getClearCond(s).flipCode(), new OPT_BranchProfileOperand());
          return DefUseEffect.REDUCED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect longCondMove(OPT_Instruction s) {
   {
      OPT_Operand val1 = CondMove.getVal1(s);
      OPT_Operand val2 = CondMove.getVal2(s);
      int cond = CondMove.getCond(s).evaluate(val1, val2);
      if (cond != OPT_ConditionOperand.UNKNOWN) {
        // BOTH CONSTANTS OR SIMILAR: FOLD
        OPT_Operand val = 
          (cond == OPT_ConditionOperand.TRUE) ? CondMove.getClearTrueValue(s) 
          : CondMove.getClearFalseValue(s);
        Move.mutate(s, LONG_MOVE, CondMove.getClearResult(s), val);
        return val.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
      }
      if (val1.isConstant() && !val2.isConstant()) {
        // Canonicalize by switching operands and fliping code.
        OPT_Operand tmp = CondMove.getClearVal1(s);
        CondMove.setVal1(s, CondMove.getClearVal2(s));
        CondMove.setVal2(s, tmp);
        CondMove.getCond(s).flipOperands();
      }
      OPT_Operand tv = CondMove.getTrueValue(s);
      OPT_Operand fv = CondMove.getFalseValue(s);
      if (tv.similar(fv)) {
        Move.mutate(s, LONG_MOVE, CondMove.getClearResult(s), tv);
        return tv.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
      }
      if (tv.isLongConstant() && fv.isLongConstant() && !CondMove.getCond(s).isFLOATINGPOINT()) {
        long itv = tv.asLongConstant().value;
        long ifv = fv.asLongConstant().value;
        OPT_Operator op = null;
        if(val1.isLong()) {
          op = BOOLEAN_CMP_LONG;
        }
        else if(val1.isFloat()) {
          op = BOOLEAN_CMP_FLOAT;
        }
        else if(val1.isDouble()) {
          op = BOOLEAN_CMP_DOUBLE;
        }
        else {
          op = BOOLEAN_CMP_INT;
        }
        if (itv == 1 && ifv == 0) {
          BooleanCmp.mutate(s, op, CondMove.getClearResult(s),
                            CondMove.getClearVal1(s), CondMove.getClearVal2(s),
                            CondMove.getClearCond(s), new OPT_BranchProfileOperand());
          return DefUseEffect.REDUCED;
        }
        if (itv == 0 && ifv == 1) {
          BooleanCmp.mutate(s, op, CondMove.getClearResult(s),
                            CondMove.getClearVal1(s), CondMove.getClearVal2(s),
                            CondMove.getClearCond(s).flipCode(), new OPT_BranchProfileOperand());
          return DefUseEffect.REDUCED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect floatCondMove(OPT_Instruction s) {
   {
      OPT_Operand val1 = CondMove.getVal1(s);
      OPT_Operand val2 = CondMove.getVal2(s);
     int cond = CondMove.getCond(s).evaluate(val1, val2);
     if (cond != OPT_ConditionOperand.UNKNOWN) {
        // BOTH CONSTANTS OR SIMILAR: FOLD
        OPT_Operand val = 
          (cond == OPT_ConditionOperand.TRUE) ? CondMove.getClearTrueValue(s) 
          : CondMove.getClearFalseValue(s);
        Move.mutate(s, FLOAT_MOVE, CondMove.getClearResult(s), val);
        return val.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
     }
      if (val1.isConstant() && !val2.isConstant()) {
        // Canonicalize by switching operands and fliping code.
        OPT_Operand tmp = CondMove.getClearVal1(s);
        CondMove.setVal1(s, CondMove.getClearVal2(s));
        CondMove.setVal2(s, tmp);
        CondMove.getCond(s).flipOperands();
      }
      OPT_Operand tv = CondMove.getTrueValue(s);
      OPT_Operand fv = CondMove.getFalseValue(s);
      if (tv.similar(fv)) {
        Move.mutate(s, FLOAT_MOVE, CondMove.getClearResult(s), tv);
        return tv.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect doubleCondMove(OPT_Instruction s) {
   {
      OPT_Operand val1 = CondMove.getVal1(s);
      OPT_Operand val2 = CondMove.getVal2(s);
     int cond = CondMove.getCond(s).evaluate(val1, val2);
     if (cond != OPT_ConditionOperand.UNKNOWN) {
        // BOTH CONSTANTS OR SIMILAR: FOLD
        OPT_Operand val = 
          (cond == OPT_ConditionOperand.TRUE) ? CondMove.getClearTrueValue(s) 
          : CondMove.getClearFalseValue(s);
        Move.mutate(s, DOUBLE_MOVE, CondMove.getClearResult(s), val);
        return val.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
     }
      if (val1.isConstant() && !val2.isConstant()) {
        // Canonicalize by switching operands and fliping code.
        OPT_Operand tmp = CondMove.getClearVal1(s);
        CondMove.setVal1(s, CondMove.getClearVal2(s));
        CondMove.setVal2(s, tmp);
        CondMove.getCond(s).flipOperands();
      }
      OPT_Operand tv = CondMove.getTrueValue(s);
      OPT_Operand fv = CondMove.getFalseValue(s);
      if (tv.similar(fv)) {
        Move.mutate(s, DOUBLE_MOVE, CondMove.getClearResult(s), tv);
        return tv.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect refCondMove(OPT_Instruction s) {
   {
      OPT_Operand val1 = CondMove.getVal1(s);
      if (val1.isConstant()) {
        OPT_Operand val2 = CondMove.getVal2(s);
        if (val2.isConstant()) {
          // BOTH CONSTANTS: FOLD
          int cond = CondMove.getCond(s).evaluate(val1, val2);
          if (cond != OPT_ConditionOperand.UNKNOWN) {
            OPT_Operand val = 
              (cond == OPT_ConditionOperand.TRUE) ? CondMove.getClearTrueValue(s) 
              : CondMove.getClearFalseValue(s);
            Move.mutate(s, REF_MOVE, CondMove.getClearResult(s), val);
            return val.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
          }
        } else {            
          // Canonicalize by switching operands and fliping code.
          OPT_Operand tmp = CondMove.getClearVal1(s);
          CondMove.setVal1(s, CondMove.getClearVal2(s));
          CondMove.setVal2(s, tmp);
          CondMove.getCond(s).flipOperands();
        }
      }
      if (CondMove.getTrueValue(s).similar(CondMove.getFalseValue(s))) {
        OPT_Operand val = CondMove.getClearTrueValue(s);
        Move.mutate(s, REF_MOVE, CondMove.getClearResult(s), val);
        return val.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect guardCondMove(OPT_Instruction s) {
   {
      OPT_Operand val1 = CondMove.getVal1(s);
      if (val1.isConstant()) {
        OPT_Operand val2 = CondMove.getVal2(s);
        if (val2.isConstant()) {
          // BOTH CONSTANTS: FOLD
          int cond = CondMove.getCond(s).evaluate(val1, val2);
          if (cond == OPT_ConditionOperand.UNKNOWN) {
            OPT_Operand val = 
              (cond == OPT_ConditionOperand.TRUE) ? CondMove.getClearTrueValue(s) 
              : CondMove.getClearFalseValue(s);
            Move.mutate(s, GUARD_MOVE, CondMove.getClearResult(s), val);
            return val.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
          }
        } else {            
          // Canonicalize by switching operands and fliping code.
          OPT_Operand tmp = CondMove.getClearVal1(s);
          CondMove.setVal1(s, CondMove.getClearVal2(s));
          CondMove.setVal2(s, tmp);
          CondMove.getCond(s).flipOperands();
        }
      }
      if (CondMove.getTrueValue(s).similar(CondMove.getFalseValue(s))) {
        OPT_Operand val = CondMove.getClearTrueValue(s);
        Move.mutate(s, GUARD_MOVE, CondMove.getClearResult(s), val);
        return val.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect booleanNot(OPT_Instruction s) {
   if (CF_INT) {
      OPT_Operand op = Unary.getVal(s);
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
  private static DefUseEffect booleanCmpInt(OPT_Instruction s) {
   if (CF_INT) {
      OPT_Operand op1 = BooleanCmp.getVal1(s);
      OPT_Operand op2 = BooleanCmp.getVal2(s);
      if (op1.isConstant()) {
        if (op2.isConstant()) {
          // BOTH CONSTANTS: FOLD
          int cond = BooleanCmp.getCond(s).evaluate(op1, op2);
          if (cond != OPT_ConditionOperand.UNKNOWN) {
            Move.mutate(s, INT_MOVE, BooleanCmp.getResult(s), 
                        (cond == OPT_ConditionOperand.TRUE) ? IC(1):IC(0));
            return DefUseEffect.MOVE_FOLDED;
          }
        } else {
          // Canonicalize by switching operands and fliping code.
          OPT_Operand tmp = BooleanCmp.getClearVal1(s);
          BooleanCmp.setVal1(s, BooleanCmp.getClearVal2(s));
          BooleanCmp.setVal2(s, tmp);
          BooleanCmp.getCond(s).flipOperands();
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect booleanCmpAddr(OPT_Instruction s) {
   if (CF_ADDR) {
      OPT_Operand op1 = BooleanCmp.getVal1(s);
      OPT_Operand op2 = BooleanCmp.getVal2(s);
      if (op1.isConstant()) {
        if (op2.isConstant()) {
          // BOTH CONSTANTS: FOLD
          int cond = BooleanCmp.getCond(s).evaluate(op1, op2);
          if (cond != OPT_ConditionOperand.UNKNOWN) {
            Move.mutate(s, REF_MOVE, BooleanCmp.getResult(s), 
                        (cond == OPT_ConditionOperand.TRUE) ? IC(1):IC(0));
            return DefUseEffect.MOVE_FOLDED;
          }
        } else {
          // Canonicalize by switching operands and fliping code.
          OPT_Operand tmp = BooleanCmp.getClearVal1(s);
          BooleanCmp.setVal1(s, BooleanCmp.getClearVal2(s));
          BooleanCmp.setVal2(s, tmp);
          BooleanCmp.getCond(s).flipOperands();
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect intAdd(OPT_Instruction s) {
   if (CF_INT) {
      canonicalizeCommutativeOperator(s);
      OPT_Operand op2 = Binary.getVal2(s);
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;
        OPT_Operand op1 = Binary.getVal1(s);
        if (op1.isIntConstant()) {
          // BOTH CONSTANTS: FOLD
          int val1 = op1.asIntConstant().value;
          Move.mutate(s, INT_MOVE, Binary.getClearResult(s), IC(val1 + val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0) {
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect intAnd(OPT_Instruction s) {
   if (CF_INT) {
      canonicalizeCommutativeOperator(s);
      OPT_Operand op1 = Binary.getVal1(s);
      OPT_Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x & x == x
        Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                    Binary.getClearVal1(s));
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
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect intDiv(OPT_Instruction s) {
   if (CF_INT) {
      OPT_Operand op1 = GuardedBinary.getVal1(s);
      OPT_Operand op2 = GuardedBinary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x / x == 1
        Move.mutate(s, INT_MOVE, GuardedBinary.getClearResult(s), 
                    IC(1));
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
          Move.mutate(s, INT_MOVE, GuardedBinary.getClearResult(s), 
                      IC(val1/val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 1) {                  // x / 1 == x;
            Move.mutate(s, INT_MOVE, GuardedBinary.getClearResult(s), 
                        GuardedBinary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
          // x / c == x >> (log c) if c is power of 2
          int power = PowerOf2(val2);
          if (power != -1) {
            Binary.mutate(s, INT_SHR, GuardedBinary.getClearResult(s), 
                          GuardedBinary.getClearVal1(s), IC(power));
            return DefUseEffect.REDUCED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect intMul(OPT_AbstractRegisterPool regpool, OPT_Instruction s) {
   if (CF_INT) {
      canonicalizeCommutativeOperator(s);
      OPT_Operand op2 = Binary.getVal2(s);
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;
        OPT_Operand op1 = Binary.getVal1(s);
        if (op1.isIntConstant()) {
          // BOTH CONSTANTS: FOLD
          int val1 = op1.asIntConstant().value;
          Move.mutate(s, INT_MOVE, Binary.getClearResult(s), IC(val1*val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == -1) {                 // x * -1 == -x
            Unary.mutate(s, INT_NEG, Binary.getClearResult(s), 
                         Binary.getClearVal1(s));
            return DefUseEffect.REDUCED;
          }
          if (val2 == 0) {                  // x * 0 == 0
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), IC(0));
            return DefUseEffect.MOVE_FOLDED;
          }
          if (val2 == 1) {                  // x * 1 == x
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }       
          // try to reduce x*c into shift and adds, but only if cost is cheap
          if (s.getPrev() != null) {
            // don't attempt to reduce if this instruction isn't
            // part of a well-formed sequence
            int cost = 0;
            for(int i=1; i < BITS_IN_INT; i++) {
              if((val2 & (1 << i)) != 0) {
                // each 1 requires a shift and add
                cost++;
              }
            }
            if (cost < 5) {
              // generate shift and adds
              OPT_RegisterOperand val1Operand = Binary.getClearVal1(s).asRegister();
              OPT_RegisterOperand resultOperand = regpool.makeTempInt();
              OPT_Instruction move;
              if ((val2 & 1) == 1) {
                // result = val1 * 1
                move = Move.create(INT_MOVE, resultOperand, val1Operand);
              } else {
                // result = 0
                move = Move.create(INT_MOVE, resultOperand, IC(0));
              }
              move.copyPosition(s);
              s.insertBefore(move);
              for(int i=1; i < BITS_IN_INT; i++) {
                if((val2 & (1 << i)) != 0) {
                  OPT_RegisterOperand tempInt = regpool.makeTempInt();
                  OPT_Instruction shift = Binary.create(INT_SHL,
                                                        tempInt,
                                                        val1Operand.copyRO(),
                                                        IC(i)
                                                        );
                  shift.copyPosition(s);
                  s.insertBefore(shift);
                  OPT_Instruction add = Binary.create(INT_ADD,
                                                      resultOperand.copyRO(),
                                                      resultOperand.copyRO(),
                                                      tempInt.copyRO()
                                                      );
                  add.copyPosition(s);
                  s.insertBefore(add);
                }
              }
              Move.mutate(s, INT_MOVE, Binary.getClearResult(s), resultOperand.copyRO());
              return DefUseEffect.REDUCED;
            }
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect intNeg(OPT_Instruction s) {
   if (CF_INT) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isIntConstant()) {
        // CONSTANT: FOLD
        int val = op.asIntConstant().value;
        Move.mutate(s, INT_MOVE, Unary.getClearResult(s), IC(-val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect intNot(OPT_Instruction s) {
   if (CF_INT) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isIntConstant()) {
        // CONSTANT: FOLD
        int val = op.asIntConstant().value;
        Move.mutate(s, INT_MOVE, Unary.getClearResult(s), IC(~val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect intOr(OPT_Instruction s) {
   if (CF_INT) {
      canonicalizeCommutativeOperator(s);
      OPT_Operand op1 = Binary.getVal1(s);
      OPT_Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x | x == x
        Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                    Binary.getClearVal1(s));
        return op1.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
      }
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;
        if (op1.isIntConstant()) {
          // BOTH CONSTANTS: FOLD
          int val1 = op1.asIntConstant().value;
          Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                      IC(val1 | val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == -1) { // x | -1 == x | 0xffffffff == 0xffffffff == -1
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), IC(-1));
            return DefUseEffect.MOVE_FOLDED;
          }
          if (val2 == 0) {                  // x | 0 == x
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect intRem(OPT_Instruction s) {
   if (CF_INT) {
      OPT_Operand op1 = GuardedBinary.getVal1(s);
      OPT_Operand op2 = GuardedBinary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x % x == 0
        Move.mutate(s, INT_MOVE, GuardedBinary.getClearResult(s), 
                    IC(0));
        return DefUseEffect.MOVE_FOLDED;
      }
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;
        if (val2 == 0) {
          // TODO: This instruction is actually unreachable.  
          // There will be an INT_ZERO_CHECK
          // guarding this instruction that will result in an 
          // ArithmeticException.  We
          // should probabbly just remove the INT_REM as dead code.
          return DefUseEffect.UNCHANGED;
        }
        if (op1.isIntConstant()) {
          // BOTH CONSTANTS: FOLD
          int val1 = op1.asIntConstant().value;
          Move.mutate(s, INT_MOVE, GuardedBinary.getClearResult(s), 
                      IC(val1%val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if ((val2 == 1)||(val2 == -1)) {             // x % 1 == 0
            Move.mutate(s, INT_MOVE, GuardedBinary.getClearResult(s), 
                        IC(0));
            return DefUseEffect.MOVE_FOLDED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect intShl(OPT_Instruction s) {
   if (CF_INT) {
      OPT_Operand op2 = Binary.getVal2(s);
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;
        OPT_Operand op1 = Binary.getVal1(s);
        if (op1.isIntConstant()) {
          // BOTH CONSTANTS: FOLD
          int val1 = op1.asIntConstant().value;
          Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                      IC(val1 << val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0) {                  // x << 0 == x
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
          if (val2 >= BITS_IN_INT) {                  // x << 32 == 0
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                        IC(0));
            return DefUseEffect.MOVE_FOLDED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect intShr(OPT_Instruction s) {
   if (CF_INT) {
      OPT_Operand op2 = Binary.getVal2(s);
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;
        OPT_Operand op1 = Binary.getVal1(s);
        if (op1.isIntConstant()) {
          // BOTH CONSTANTS: FOLD
          int val1 = op1.asIntConstant().value;
          Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                      IC(val1 >> val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0) {                  // x >> 0 == x
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect intSub(OPT_Instruction s) {
   if (CF_INT) {
      OPT_Operand op1 = Binary.getVal1(s);
      OPT_Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x - x == 0
        Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                    IC(0));
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
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
          // x - c = x + -c
          // prefer adds, since some architectures have addi but not subi
          Binary.mutate(s, INT_ADD, Binary.getClearResult(s), 
                        Binary.getClearVal1(s), IC(-val2));
          return DefUseEffect.REDUCED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect intUshr(OPT_Instruction s) {
   if (CF_INT) {
      OPT_Operand op2 = Binary.getVal2(s);
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;
        OPT_Operand op1 = Binary.getVal1(s);
        if (op1.isIntConstant()) {
          // BOTH CONSTANTS: FOLD
          int val1 = op1.asIntConstant().value;
          Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                      IC(val1 >>> val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0) {                  // x >>> 0 == x
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
          if (val2 >= BITS_IN_INT) {                  // x >>> 32 == 0
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                        IC(0));
            return DefUseEffect.MOVE_FOLDED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect intXor(OPT_Instruction s) {
   if (CF_INT) {
      canonicalizeCommutativeOperator(s);
      OPT_Operand op1 = Binary.getVal1(s);
      OPT_Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x ^ x == 0
        Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                    IC(0));
        return DefUseEffect.MOVE_FOLDED;
      }
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;

        if (op1.isIntConstant()) {
          // BOTH CONSTANTS: FOLD
          int val1 = op1.asIntConstant().value;
          Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                      IC(val1 ^ val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == -1) {                 // x ^ -1 == x ^ 0xffffffff = ~x
            Unary.mutate(s, INT_NOT, Binary.getClearResult(s), 
                         Binary.getClearVal1(s));
            return DefUseEffect.REDUCED;
          }
          if (val2 == 0) {                  // x ^ 0 == x
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect refAdd(OPT_Instruction s) {
    if (CF_ADDR) {
      canonicalizeCommutativeOperator(s);
      OPT_Operand op2 = Binary.getVal2(s);
      if (op2.isConstant() && !op2.isObjectConstant()) {
        Address val2 = getAddressValue(op2);
        OPT_Operand op1 = Binary.getVal1(s);
        if (op1.isConstant() && !op1.isObjectConstant()) {
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
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect refAnd(OPT_Instruction s) {
   if (CF_ADDR) {
      canonicalizeCommutativeOperator(s);
      OPT_Operand op1 = Binary.getVal1(s);
      OPT_Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x & x == x
        Move.mutate(s, REF_MOVE, Binary.getClearResult(s), 
                    Binary.getClearVal1(s));
        return op1.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
      }
      if (op2.isAddressConstant()) {
        Word val2 = op2.asAddressConstant().value.toWord();
        if (op1.isAddressConstant()) {
          // BOTH CONSTANTS: FOLD
          Word val1 = op1.asAddressConstant().value.toWord();
          Move.mutate(s, REF_MOVE, Binary.getClearResult(s), AC(val1.and(val2).toAddress()));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2.isZero()) {                  // x & 0 == 0
            Move.mutate(s, REF_MOVE, Binary.getClearResult(s), AC(Address.zero()));
            return DefUseEffect.MOVE_FOLDED;
          }
          if (val2.isMax()) {                 // x & -1 == x & 0xffffffff == x
            Move.mutate(s, REF_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect refShl(OPT_Instruction s) {
   if (CF_ADDR) {
      OPT_Operand op2 = Binary.getVal2(s);
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;
        OPT_Operand op1 = Binary.getVal1(s);
        if (op1.isAddressConstant()) {
          // BOTH CONSTANTS: FOLD
          Word val1 = op1.asAddressConstant().value.toWord();
          Move.mutate(s, REF_MOVE, Binary.getClearResult(s), 
                      AC(val1.lsh(val2).toAddress()));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0) {                  // x << 0 == x
            Move.mutate(s, REF_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
          if (val2 >= BITS_IN_ADDRESS) {                  // x << 32 == 0
            Move.mutate(s, REF_MOVE, Binary.getClearResult(s), 
                        IC(0));
            return DefUseEffect.MOVE_FOLDED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect refShr(OPT_Instruction s) {
   if (CF_ADDR) {
      OPT_Operand op2 = Binary.getVal2(s);
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;
        OPT_Operand op1 = Binary.getVal1(s);
        if (op1.isAddressConstant()) {
          // BOTH CONSTANTS: FOLD
          Word val1 = op1.asAddressConstant().value.toWord();
          Move.mutate(s, REF_MOVE, Binary.getClearResult(s), 
                      AC(val1.rsha(val2).toAddress()));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0) {                  // x >> 0 == x
            Move.mutate(s, REF_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect refNot(OPT_Instruction s) {
   if (CF_ADDR) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isAddressConstant()) {
        // CONSTANT: FOLD
        Word val = op.asAddressConstant().value.toWord();
        Move.mutate(s, REF_MOVE, Unary.getClearResult(s), AC(val.not().toAddress()));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect refOr(OPT_Instruction s) {
   if (CF_ADDR) {
      canonicalizeCommutativeOperator(s);
      OPT_Operand op1 = Binary.getVal1(s);
      OPT_Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x | x == x
        Move.mutate(s, REF_MOVE, Binary.getClearResult(s), 
                    Binary.getClearVal1(s));
        return op1.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
      }
      if (op2.isAddressConstant()) {
        Word val2 = op2.asAddressConstant().value.toWord();
        if (op1.isAddressConstant()) {
          // BOTH CONSTANTS: FOLD
          Word val1 = op1.asAddressConstant().value.toWord();
          Move.mutate(s, REF_MOVE, Binary.getClearResult(s), 
                      AC(val1.or(val2).toAddress()));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2.isMax()) { // x | -1 == x | 0xffffffff == 0xffffffff == -1
            Move.mutate(s, REF_MOVE, Binary.getClearResult(s), AC(Address.max()));
            return DefUseEffect.MOVE_FOLDED;
          }
          if (val2.isZero()) {                  // x | 0 == x
            Move.mutate(s, REF_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect refSub(OPT_Instruction s) {
   if (CF_ADDR) {
      OPT_Operand op1 = Binary.getVal1(s);
      OPT_Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x - x == 0
        Move.mutate(s, REF_MOVE, Binary.getClearResult(s), 
                    IC(0));
        return DefUseEffect.MOVE_FOLDED;
      }
      if (op2.isConstant() && !op2.isObjectConstant()) {
        Address val2 = getAddressValue(op2);
        if (op1.isConstant() && !op1.isObjectConstant()) {
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
          Binary.mutate(s, REF_ADD, Binary.getClearResult(s), 
                        Binary.getClearVal1(s), AC(Address.zero().minus(val2.toWord().toOffset())));
          return DefUseEffect.REDUCED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect regUshr(OPT_Instruction s) {
   if (CF_ADDR) {
      OPT_Operand op2 = Binary.getVal2(s);
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;
        OPT_Operand op1 = Binary.getVal1(s);
        if (op1.isAddressConstant()) {
          // BOTH CONSTANTS: FOLD
          Word val1 = op1.asAddressConstant().value.toWord();
          Move.mutate(s, REF_MOVE, Binary.getClearResult(s), 
                      AC(val1.rshl(val2).toAddress()));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0) {                  // x >>> 0 == x
            Move.mutate(s, REF_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
          if (val2 >= BITS_IN_ADDRESS) {                  // x >>> 32 == 0
            Move.mutate(s, REF_MOVE, Binary.getClearResult(s), 
                        IC(0));
            return DefUseEffect.MOVE_FOLDED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect refXor(OPT_Instruction s) {
   if (CF_ADDR) {
      canonicalizeCommutativeOperator(s);
      OPT_Operand op1 = Binary.getVal1(s);
      OPT_Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x ^ x == 0
        Move.mutate(s, REF_MOVE, Binary.getClearResult(s), 
                    IC(0));
        return DefUseEffect.MOVE_FOLDED;
      }
      if (op2.isAddressConstant()) {
        Word val2 = op2.asAddressConstant().value.toWord();
        if (op1.isAddressConstant()) {
          // BOTH CONSTANTS: FOLD
          Word val1 = op1.asAddressConstant().value.toWord();
          Move.mutate(s, REF_MOVE, Binary.getClearResult(s), 
                      AC(val1.xor(val2).toAddress()));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2.isMax()) {                 // x ^ -1 == x ^ 0xffffffff = ~x
            Unary.mutate(s, REF_NOT, Binary.getClearResult(s), 
                         Binary.getClearVal1(s));
            return DefUseEffect.REDUCED;
          }
          if (val2.isZero()) {                  // x ^ 0 == x
            Move.mutate(s, REF_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect longAdd(OPT_Instruction s) {
   if (CF_LONG) {
      canonicalizeCommutativeOperator(s);
      OPT_Operand op2 = Binary.getVal2(s);
      if (op2.isLongConstant()) {
        long val2 = op2.asLongConstant().value;
        OPT_Operand op1 = Binary.getVal1(s);
        if (op1.isLongConstant()) {
          // BOTH CONSTANTS: FOLD
          long val1 = op1.asLongConstant().value;
          Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), LC(val1+val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0L) {                 // x + 0 == x
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect longAnd(OPT_Instruction s) {
   if (CF_LONG) {
      canonicalizeCommutativeOperator(s);
      OPT_Operand op1 = Binary.getVal1(s);
      OPT_Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x & x == x
        Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                    Binary.getClearVal1(s));
        return op1.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
      }
      if (op2.isLongConstant()) {
        long val2 = op2.asLongConstant().value;
        if (op1.isLongConstant()) {
          // BOTH CONSTANTS: FOLD
          long val1 = op1.asLongConstant().value;
          Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                      LC(val1 & val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0L) {                 // x & 0L == 0L
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), LC(0L));
            return DefUseEffect.MOVE_FOLDED;
          }
          if (val2 == -1) {                 // x & -1L == x & 0xff...ff == x
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect longCmp(OPT_Instruction s) {
   if (CF_LONG) {
      OPT_Operand op1 = Binary.getVal1(s);
      OPT_Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: op1 == op2
        Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                    IC(0));
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
  private static DefUseEffect longDiv(OPT_Instruction s) {
   if (CF_LONG) {
      OPT_Operand op1 = GuardedBinary.getVal1(s);
      OPT_Operand op2 = GuardedBinary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x / x == 1
        Move.mutate(s, LONG_MOVE, GuardedBinary.getClearResult(s), 
                    LC(1));
        return DefUseEffect.MOVE_FOLDED;
      }
      if (op2.isLongConstant()) {
        long val2 = op2.asLongConstant().value;
        if (val2 == 0L) {
          // TODO: This instruction is actually unreachable.  
          // There will be a LONG_ZERO_CHECK
          // guarding this instruction that will result in an 
          // ArithmeticException.  We
          // should probabbly just remove the LONG_DIV as dead code.
          return DefUseEffect.UNCHANGED;
        }
        if (op1.isLongConstant()) {
          // BOTH CONSTANTS: FOLD
          long val1 = op1.asLongConstant().value;
          Move.mutate(s, LONG_MOVE, GuardedBinary.getClearResult(s), 
                      LC(val1/val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 1L) {                 // x / 1L == x
            Move.mutate(s, LONG_MOVE, GuardedBinary.getClearResult(s), 
                        GuardedBinary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect longMul(OPT_Instruction s) {
   if (CF_LONG) {
      canonicalizeCommutativeOperator(s);
      OPT_Operand op2 = Binary.getVal2(s);
      if (op2.isLongConstant()) {
        long val2 = op2.asLongConstant().value;
        OPT_Operand op1 = Binary.getVal1(s);
        if (op1.isLongConstant()) {
          // BOTH CONSTANTS: FOLD
          long val1 = op1.asLongConstant().value;
          Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                      LC(val1*val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == -1L) {                // x * -1 == -x
            Move.mutate(s, LONG_NEG, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return DefUseEffect.REDUCED;
          }
          if (val2 == 0L) {                 // x * 0L == 0L
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), LC(0L));
            return DefUseEffect.MOVE_FOLDED;
          }
          if (val2 == 1L) {                 // x * 1L == x
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect longNeg(OPT_Instruction s) {
   if (CF_LONG) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isLongConstant()) {
        // CONSTANT: FOLD
        long val = op.asLongConstant().value;
        Move.mutate(s, LONG_MOVE, Unary.getClearResult(s), LC(-val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect longNot(OPT_Instruction s) {
   if (CF_LONG) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isLongConstant()) {
        long val = op.asLongConstant().value;
        // CONSTANT: FOLD
        Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), LC(~val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect longOr(OPT_Instruction s) {
   if (CF_LONG) {
      canonicalizeCommutativeOperator(s);
      OPT_Operand op1 = Binary.getVal1(s);
      OPT_Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x | x == x
        Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                    Binary.getClearVal1(s));
        return op1.isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
      }
      if (op2.isLongConstant()) {
        long val2 = op2.asLongConstant().value;
        if (op1.isLongConstant()) {
          // BOTH CONSTANTS: FOLD
          long val1 = op1.asLongConstant().value;
          Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                      LC(val1 | val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0L) {                 // x | 0L == x
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
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
  private static DefUseEffect longRem(OPT_Instruction s) {
   if (CF_LONG) {
      OPT_Operand op1 = GuardedBinary.getVal1(s);
      OPT_Operand op2 = GuardedBinary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x % x == 0
        Move.mutate(s, LONG_MOVE, GuardedBinary.getClearResult(s), 
                    LC(0));
        return DefUseEffect.MOVE_FOLDED;
      }
      if (op2.isLongConstant()) {
        long val2 = op2.asLongConstant().value;
        if (val2 == 0L) {
          // TODO: This instruction is actually unreachable.  
          // There will be a LONG_ZERO_CHECK
          // guarding this instruction that will result in an 
          // ArithmeticException.  We
          // should probabbly just remove the LONG_REM as dead code.
          return DefUseEffect.UNCHANGED;
        }
        if (op1.isLongConstant()) {
          // BOTH CONSTANTS: FOLD
          long val1 = op1.asLongConstant().value;
          Move.mutate(s, LONG_MOVE, GuardedBinary.getClearResult(s), 
                      LC(val1%val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 1L) {                 // x % 1L == 0
            Move.mutate(s, LONG_MOVE, GuardedBinary.getClearResult(s), 
                        LC(0));
            return DefUseEffect.MOVE_FOLDED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect longShl(OPT_Instruction s) {
   if (CF_LONG) {
      OPT_Operand op2 = Binary.getVal2(s);
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;
        OPT_Operand op1 = Binary.getVal1(s);
        if (op1.isLongConstant()) {
          // BOTH CONSTANTS: FOLD
          long val1 = op1.asLongConstant().value;
          Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                      LC(val1 << val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0) {                  // x << 0 == x
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
          if (val2 >= BITS_IN_LONG) {                  // x << 64 == 0
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                        LC(0));
            return DefUseEffect.MOVE_FOLDED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect longShr(OPT_Instruction s) {
   if (CF_LONG) {
      OPT_Operand op2 = Binary.getVal2(s);
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;
        OPT_Operand op1 = Binary.getVal1(s);
        if (op1.isLongConstant()) {
          // BOTH CONSTANTS: FOLD
          long val1 = op1.asLongConstant().value;
          Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                      LC(val1 >> val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0) {                  // x >> 0L == x
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect longSub(OPT_Instruction s) {
   if (CF_LONG) {
      OPT_Operand op1 = Binary.getVal1(s);
      OPT_Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x - x == 0
        Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                    LC(0));
        return DefUseEffect.MOVE_FOLDED;
      }
      if (op2.isLongConstant()) {
        long val2 = op2.asLongConstant().value;
        if (op1.isLongConstant()) {
          // BOTH CONSTANTS: FOLD
          long val1 = op1.asLongConstant().value;
          Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), LC(val1-val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0L) {                 // x - 0 == x
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
          if (VM.BuildFor64Addr) {
            // x - c = x + -c
            // prefer adds, since some architectures have addi but not subi
            Binary.mutate(s, LONG_ADD, Binary.getClearResult(s), 
                Binary.getClearVal1(s), LC(-val2));
            return DefUseEffect.REDUCED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect longUshr(OPT_Instruction s) {
   if (CF_LONG) {
      OPT_Operand op2 = Binary.getVal2(s);
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;
        OPT_Operand op1 = Binary.getVal1(s);
        if (op1.isLongConstant()) {
          // BOTH CONSTANTS: FOLD
          long val1 = op1.asLongConstant().value;
          Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                      LC(val1 >>> val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0) {                  // x >>> 0L == x
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
          if (val2 >= BITS_IN_LONG) {                  // x >>> 64 == 0
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                        LC(0));
            return DefUseEffect.MOVE_FOLDED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect longXor(OPT_Instruction s) {
   if (CF_LONG) {
      canonicalizeCommutativeOperator(s);
      OPT_Operand op1 = Binary.getVal1(s);
      OPT_Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x ^ x == 0
        Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                    LC(0));
        return DefUseEffect.MOVE_FOLDED;
      }
      if (op2.isLongConstant()) {
        long val2 = op2.asLongConstant().value;
        if (op1.isLongConstant()) {
          // BOTH CONSTANTS: FOLD
          long val1 = op1.asLongConstant().value;
          Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                      LC(val1 ^ val2));
          return DefUseEffect.MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == -1L) {                // x ^ -1L == x ^ 0xff..ff = ~x
            Unary.mutate(s, LONG_NOT, Binary.getClearResult(s), 
                         Binary.getClearVal1(s));
            return DefUseEffect.REDUCED;
          }
          if (val2 == 0L) {                 // x ^ 0L == x
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return DefUseEffect.MOVE_REDUCED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect floatAdd(OPT_Instruction s) {
   if (CF_FLOAT) {
      canonicalizeCommutativeOperator(s);
      OPT_Operand op2 = Binary.getVal2(s);
      if (op2.isFloatConstant()) {
        float val2 = op2.asFloatConstant().value;
        OPT_Operand op1 = Binary.getVal1(s);
        if (op1.isFloatConstant()) {
          // BOTH CONSTANTS: FOLD
          float val1 = op1.asFloatConstant().value;
          Move.mutate(s, FLOAT_MOVE, Binary.getClearResult(s), 
                      FC(val1 + val2));
          return DefUseEffect.MOVE_FOLDED;
        }
        if (val2 == 0.0f) {
          // x + 0.0 is x (even is x is a Nan).
          Move.mutate(s, FLOAT_MOVE, Binary.getClearResult(s),
                      Binary.getClearVal1(s));
          return DefUseEffect.MOVE_REDUCED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect floatCmpg(OPT_Instruction s) {
   if (CF_INT) {
      OPT_Operand op2 = Binary.getVal2(s);
      if (op2.isFloatConstant()) {
        OPT_Operand op1 = Binary.getVal1(s);
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
  private static DefUseEffect floatCmpl(OPT_Instruction s) {
   if (CF_INT) {
      OPT_Operand op2 = Binary.getVal2(s);
      if (op2.isFloatConstant()) {
        OPT_Operand op1 = Binary.getVal1(s);
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
  private static DefUseEffect floatDiv(OPT_Instruction s) {
   if (CF_FLOAT) {
      OPT_Operand op2 = Binary.getVal2(s);
      if (op2.isFloatConstant()) {
        OPT_Operand op1 = Binary.getVal1(s);
        if (op1.isFloatConstant()) {
          // BOTH CONSTANTS: FOLD
          float val1 = op1.asFloatConstant().value;
          float val2 = op2.asFloatConstant().value;
          Move.mutate(s, FLOAT_MOVE, Binary.getClearResult(s), 
                      FC(val1/val2));
          return DefUseEffect.MOVE_FOLDED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect floatMul(OPT_Instruction s) {
   if (CF_FLOAT) {
      canonicalizeCommutativeOperator(s);
      OPT_Operand op2 = Binary.getVal2(s);
      if (op2.isFloatConstant()) {
        float val2 = op2.asFloatConstant().value;
        OPT_Operand op1 = Binary.getVal1(s);
        if (op1.isFloatConstant()) {
          // BOTH CONSTANTS: FOLD
          float val1 = op1.asFloatConstant().value;
          Move.mutate(s, FLOAT_MOVE, Binary.getClearResult(s), 
                      FC(val1*val2));
          return DefUseEffect.MOVE_FOLDED;
        }
        if (val2 == 1.0f) {
          // x * 1.0 is x, even if x is a NaN
          Move.mutate(s, FLOAT_MOVE, Binary.getClearResult(s),
                      Binary.getClearVal1(s));
          return DefUseEffect.MOVE_REDUCED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect floatNeg(OPT_Instruction s) {
   if (CF_FLOAT) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isFloatConstant()) {
        // CONSTANT: FOLD
        float val = op.asFloatConstant().value;
        Move.mutate(s, FLOAT_MOVE, Unary.getClearResult(s), FC(-val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect floatRem(OPT_Instruction s) {
   if (CF_FLOAT) {
      OPT_Operand op2 = Binary.getVal2(s);
      if (op2.isFloatConstant()) {
        OPT_Operand op1 = Binary.getVal1(s);
        if (op1.isFloatConstant()) {
          // BOTH CONSTANTS: FOLD
          float val1 = op1.asFloatConstant().value;
          float val2 = op2.asFloatConstant().value;
          Move.mutate(s, FLOAT_MOVE, Binary.getClearResult(s), 
                      FC(val1%val2));
          return DefUseEffect.MOVE_FOLDED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect floatSub(OPT_Instruction s) {
   if (CF_FLOAT) {
      OPT_Operand op2 = Binary.getVal2(s);
      if (op2.isFloatConstant()) {
        float val2 = op2.asFloatConstant().value;
        OPT_Operand op1 = Binary.getVal1(s);
        if (op1.isFloatConstant()) {
          // BOTH CONSTANTS: FOLD
          float val1 = op1.asFloatConstant().value;
          Move.mutate(s, FLOAT_MOVE, Binary.getClearResult(s), 
                      FC(val1 - val2));
          return DefUseEffect.MOVE_FOLDED;
        }
        if (val2 == 0.0f) {
          // x - 0.0 is x, even if x is a NaN
          Move.mutate(s, FLOAT_MOVE, Binary.getClearResult(s),
                      Binary.getClearVal1(s));
          return DefUseEffect.MOVE_REDUCED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect doubleAdd(OPT_Instruction s) {
   if (CF_DOUBLE) {
      canonicalizeCommutativeOperator(s);
      OPT_Operand op2 = Binary.getVal2(s);
      if (op2.isDoubleConstant()) {
        double val2 = op2.asDoubleConstant().value;
        OPT_Operand op1 = Binary.getVal1(s);
        if (op1.isDoubleConstant()) {
          // BOTH CONSTANTS: FOLD
          double val1 = op1.asDoubleConstant().value;
          Move.mutate(s, DOUBLE_MOVE, Binary.getClearResult(s), 
                      DC(val1 + val2));
          return DefUseEffect.MOVE_FOLDED;
        }
        if (val2 == 0.0) {
          // x + 0.0 is x, even if x is a NaN
          Move.mutate(s, DOUBLE_MOVE, Binary.getClearResult(s),
                      Binary.getClearVal1(s));
          return DefUseEffect.MOVE_REDUCED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect doubleCmpg(OPT_Instruction s) {
   if (CF_INT) {
      OPT_Operand op2 = Binary.getVal2(s);
      if (op2.isDoubleConstant()) {
        OPT_Operand op1 = Binary.getVal1(s);
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
  private static DefUseEffect doubleCmpl(OPT_Instruction s) {
   if (CF_INT) {
      OPT_Operand op2 = Binary.getVal2(s);
      if (op2.isDoubleConstant()) {
        OPT_Operand op1 = Binary.getVal1(s);
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
  private static DefUseEffect doubleDiv(OPT_Instruction s) {
   if (CF_DOUBLE) {
      OPT_Operand op2 = Binary.getVal2(s);
      if (op2.isDoubleConstant()) {
        OPT_Operand op1 = Binary.getVal1(s);
        if (op1.isDoubleConstant()) {
          // BOTH CONSTANTS: FOLD
          double val1 = op1.asDoubleConstant().value;
          double val2 = op2.asDoubleConstant().value;
          Move.mutate(s, DOUBLE_MOVE, Binary.getClearResult(s), 
                      DC(val1/val2));
          return DefUseEffect.MOVE_FOLDED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect doubleMul(OPT_Instruction s) {
   if (CF_DOUBLE) {
      canonicalizeCommutativeOperator(s);
      OPT_Operand op2 = Binary.getVal2(s);
      if (op2.isDoubleConstant()) {
        double val2 = op2.asDoubleConstant().value;
        OPT_Operand op1 = Binary.getVal1(s);
        if (op1.isDoubleConstant()) {
          // BOTH CONSTANTS: FOLD
          double val1 = op1.asDoubleConstant().value;
          Move.mutate(s, DOUBLE_MOVE, Binary.getClearResult(s), 
                      DC(val1*val2));
          return DefUseEffect.MOVE_FOLDED;
        }
        if (val2 == 1.0) {
          // x * 1.0 is x even if x is a NaN
          Move.mutate(s, DOUBLE_MOVE, Binary.getClearResult(s),
                      Binary.getClearVal1(s));
          return DefUseEffect.MOVE_REDUCED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect doubleNeg(OPT_Instruction s) {
   if (CF_DOUBLE) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isDoubleConstant()) {
        // CONSTANT: FOLD
        double val = op.asDoubleConstant().value;
        Move.mutate(s, DOUBLE_MOVE, Unary.getClearResult(s), DC(-val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect doubleRem(OPT_Instruction s) {
   if (CF_DOUBLE) {
      OPT_Operand op2 = Binary.getVal2(s);
      if (op2.isDoubleConstant()) {
        OPT_Operand op1 = Binary.getVal1(s);
        if (op1.isDoubleConstant()) {
          // BOTH CONSTANTS: FOLD
          double val1 = op1.asDoubleConstant().value;
          double val2 = op2.asDoubleConstant().value;
          Move.mutate(s, DOUBLE_MOVE, Binary.getClearResult(s), 
                      DC(val1%val2));
          return DefUseEffect.MOVE_FOLDED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect doubleSub(OPT_Instruction s) {
   if (CF_DOUBLE) {
      OPT_Operand op2 = Binary.getVal2(s);
      if (op2.isDoubleConstant()) {
        double val2 = op2.asDoubleConstant().value;
        OPT_Operand op1 = Binary.getVal1(s);
        if (op1.isDoubleConstant()) {
          // BOTH CONSTANTS: FOLD
          double val1 = op1.asDoubleConstant().value;
          Move.mutate(s, DOUBLE_MOVE, Binary.getClearResult(s), 
                      DC(val1 - val2));
          return DefUseEffect.MOVE_FOLDED;
        }
        if (val2 == 0.0) {
          // x - 0.0 is x, even if x is a NaN
          Move.mutate(s, DOUBLE_MOVE, Binary.getClearResult(s),
                      Binary.getClearVal1(s));
          return DefUseEffect.MOVE_REDUCED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect double2Float(OPT_Instruction s) {
   if (CF_FLOAT) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isDoubleConstant()) {
        // CONSTANT: FOLD
        double val = op.asDoubleConstant().value;
        Move.mutate(s, FLOAT_MOVE, Unary.getClearResult(s), FC((float)val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect double2Int(OPT_Instruction s) {
   if (CF_INT) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isDoubleConstant()) {
        // CONSTANT: FOLD
        double val = op.asDoubleConstant().value;
        Move.mutate(s, INT_MOVE, Unary.getClearResult(s), IC((int)val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect double2Long(OPT_Instruction s) {
   if (CF_LONG) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isDoubleConstant()) {
        // CONSTANT: FOLD
        double val = op.asDoubleConstant().value;
        Move.mutate(s, LONG_MOVE, Unary.getClearResult(s), LC((long)val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect doubleAsLongBits(OPT_Instruction s) {
   if (CF_LONG) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isDoubleConstant()) {
        // CONSTANT: FOLD
        double val = op.asDoubleConstant().value;
        Move.mutate(s, LONG_MOVE, Unary.getClearResult(s), 
                    LC(Double.doubleToLongBits(val)));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect int2Double(OPT_Instruction s) {
   if (CF_DOUBLE) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isIntConstant()) {
        // CONSTANT: FOLD
        int val = op.asIntConstant().value;
        Move.mutate(s, DOUBLE_MOVE, Unary.getClearResult(s), 
                    DC((double)val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect int2Byte(OPT_Instruction s) {
   if (CF_INT) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isIntConstant()) {
        // CONSTANT: FOLD
        int val = op.asIntConstant().value;
        Move.mutate(s, INT_MOVE, Unary.getClearResult(s), IC((byte)val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect int2UShort(OPT_Instruction s) {
   if (CF_INT) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isIntConstant()) {
        // CONSTANT: FOLD
        int val = op.asIntConstant().value;
        Move.mutate(s, INT_MOVE, Unary.getClearResult(s), IC((char)val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect int2Float(OPT_Instruction s) {
   if (CF_FLOAT) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isIntConstant()) {
        // CONSTANT: FOLD
        int val = op.asIntConstant().value;
        Move.mutate(s, FLOAT_MOVE, Unary.getClearResult(s), FC((float)val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect int2Long(OPT_Instruction s) {
   if (CF_LONG) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isIntConstant()) {
        // CONSTANT: FOLD
        int val = op.asIntConstant().value;
        Move.mutate(s, LONG_MOVE, Unary.getClearResult(s), LC((long)val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect int2AddrSigExt(OPT_Instruction s) {
   if (CF_ADDR) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isIntConstant()) {
        // CONSTANT: FOLD
        int val = op.asIntConstant().value;
        Move.mutate(s, REF_MOVE, Unary.getClearResult(s), AC(Address.fromIntSignExtend(val)));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect int2AddrZerExt(OPT_Instruction s) {
   if (CF_ADDR) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isIntConstant()) {
        // CONSTANT: FOLD
        int val = op.asIntConstant().value;
        Move.mutate(s, REF_MOVE, Unary.getClearResult(s), AC(Address.fromIntZeroExtend(val)));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }


  private static DefUseEffect long2Addr(OPT_Instruction s) {
    if (VM.BuildFor64Addr && CF_ADDR) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isLongConstant()) {
        // CONSTANT: FOLD
        long val = op.asLongConstant().value;
        Move.mutate(s, REF_MOVE, Unary.getClearResult(s), AC(Address.fromLong(val)));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }

  private static DefUseEffect int2Short(OPT_Instruction s) {
    if (CF_INT) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isIntConstant()) {
        // CONSTANT: FOLD
        int val = op.asIntConstant().value;
        Move.mutate(s, INT_MOVE, Unary.getClearResult(s), IC((short)val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect intBitsAsFloat(OPT_Instruction s) {
    if (CF_FLOAT) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isIntConstant()) {
        // CONSTANT: FOLD
        int val = op.asIntConstant().value;
        Move.mutate(s, FLOAT_MOVE, Unary.getClearResult(s), 
                    FC(Float.intBitsToFloat(val)));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect addr2Int(OPT_Instruction s) {
    if (CF_INT) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isAddressConstant()) {
        // CONSTANT: FOLD
        Address val = op.asAddressConstant().value;
        Move.mutate(s, INT_MOVE, Unary.getClearResult(s), IC(val.toInt()));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect addr2Long(OPT_Instruction s) {
    if (CF_LONG) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isAddressConstant()) {
        // CONSTANT: FOLD
        Address val = op.asAddressConstant().value;
        Move.mutate(s, LONG_MOVE, Unary.getClearResult(s), LC(val.toLong()));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect float2Double(OPT_Instruction s) {
    if (CF_DOUBLE) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isFloatConstant()) {
        // CONSTANT: FOLD
        float val = op.asFloatConstant().value;
        Move.mutate(s, DOUBLE_MOVE, Unary.getClearResult(s), 
                    DC((double)val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect float2Int(OPT_Instruction s) {
    if (CF_INT) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isFloatConstant()) {
        // CONSTANT: FOLD
        float val = op.asFloatConstant().value;
        Move.mutate(s, INT_MOVE, Unary.getClearResult(s), IC((int)val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect float2Long(OPT_Instruction s) {
    if (CF_LONG) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isFloatConstant()) {
        // CONSTANT: FOLD
        float val = op.asFloatConstant().value;
        Move.mutate(s, LONG_MOVE, Unary.getClearResult(s), LC((long)val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect floatAsIntBits(OPT_Instruction s) {
    if (CF_INT) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isFloatConstant()) {
        // CONSTANT: FOLD
        float val = op.asFloatConstant().value;
        Move.mutate(s, INT_MOVE, Unary.getClearResult(s), 
                    IC(Float.floatToIntBits(val)));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect long2Float(OPT_Instruction s) {
    if (CF_FLOAT) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isLongConstant()) {
        // CONSTANT: FOLD
        long val = op.asLongConstant().value;
        Move.mutate(s, FLOAT_MOVE, Unary.getClearResult(s), FC((float)val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect long2Int(OPT_Instruction s) {
    if (CF_INT) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isLongConstant()) {
        // CONSTANT: FOLD
        long val = op.asLongConstant().value;
        Move.mutate(s, INT_MOVE, Unary.getClearResult(s), IC((int)val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect long2Double(OPT_Instruction s) {
    if (CF_DOUBLE) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isLongConstant()) {
        // CONSTANT: FOLD
        long val = op.asLongConstant().value;
        Move.mutate(s, DOUBLE_MOVE, Unary.getClearResult(s), 
                    DC((double)val));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect longBitsAsDouble(OPT_Instruction s) {
    if (CF_DOUBLE) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isLongConstant()) {
        // CONSTANT: FOLD
        long val = op.asLongConstant().value;
        Move.mutate(s, DOUBLE_MOVE, Unary.getClearResult(s), 
                    DC(Double.longBitsToDouble(val)));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect arrayLength(OPT_Instruction s) {
    if (CF_FIELDS) {
      OPT_Operand op = GuardedUnary.getVal(s);
      if(op.isObjectConstant()) {
        int length = Array.getLength(op.asObjectConstant().value);
        Move.mutate(s, INT_MOVE, GuardedUnary.getClearResult(s),
                    IC(length));
        return DefUseEffect.MOVE_FOLDED;          
      } else if (op.isNullConstant()) {
        // TODO: this arraylength operation is junk so destroy
        return DefUseEffect.UNCHANGED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect boundsCheck(OPT_Instruction s) {
    if (CF_FIELDS) {
      OPT_Operand ref = BoundsCheck.getRef(s);
      OPT_Operand index = BoundsCheck.getIndex(s);
      if (ref.isNullConstant()) {
        Trap.mutate(s, TRAP, NullCheck.getClearGuardResult(s),
                    OPT_TrapCodeOperand.NullPtr());
        return DefUseEffect.TRAP_REDUCED;
      } else if(index.isIntConstant()) {
        int indexAsInt = index.asIntConstant().value;
        if (indexAsInt < 0) {
          Trap.mutate(s, TRAP, BoundsCheck.getClearGuardResult(s), OPT_TrapCodeOperand.ArrayBounds());
          return DefUseEffect.TRAP_REDUCED;
        } else if(ref.isConstant()) {
          int refLength = Array.getLength(ref.asObjectConstant().value);
          if(indexAsInt < refLength) {
            Move.mutate(s, GUARD_MOVE, BoundsCheck.getClearGuardResult(s),
                        BoundsCheck.getClearGuard(s));
            return Move.getVal(s).isConstant() ? DefUseEffect.MOVE_FOLDED : DefUseEffect.MOVE_REDUCED;
          }
          else {
            Trap.mutate(s, TRAP, BoundsCheck.getClearGuardResult(s), OPT_TrapCodeOperand.ArrayBounds());
            return DefUseEffect.TRAP_REDUCED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect call(OPT_Instruction s) {
    if (CF_FIELDS) {
      OPT_MethodOperand methOp = Call.getMethod(s);
      if ((methOp != null) && methOp.isVirtual() && !methOp.hasPreciseTarget()) {
        OPT_Operand calleeThis = Call.getParam(s, 0);
        if (calleeThis.isNullConstant()) {
          Trap.mutate(s, TRAP, NullCheck.getClearGuardResult(s),
                      OPT_TrapCodeOperand.NullPtr());
          return DefUseEffect.TRAP_REDUCED;
        }
        else if(calleeThis.isConstant() || calleeThis.asRegister().isPreciseType()) {
          VM_TypeReference calleeClass = calleeThis.getType();
          if (calleeClass.isResolved()) {
            methOp.refine(calleeClass.peekResolvedType());
            return DefUseEffect.UNCHANGED;
          }
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect getField(OPT_Instruction s) {
    if (CF_FIELDS) {
      OPT_Operand ref = GetField.getRef(s);
      if (ref.isNullConstant()) {
        Trap.mutate(s, TRAP, NullCheck.getClearGuardResult(s),
                    OPT_TrapCodeOperand.NullPtr());
        return DefUseEffect.TRAP_REDUCED;
      } else if(ref.isObjectConstant() &&
                GetField.getLocation(s).getFieldRef().resolve().isFinal()) {
        // A constant object references this field which is
        // final. NB as the fields are final and have assigned
        // values they must already have been resolved.
        VM_Field field = GetField.getLocation(s).getFieldRef().resolve();
        try {
          OPT_ConstantOperand op = OPT_StaticFieldReader.getFieldValueAsConstant(field, ref.asObjectConstant().value);
          Move.mutate(s, OPT_IRTools.getMoveOp(field.getType()),
                      GetField.getClearResult(s), op);
          return DefUseEffect.MOVE_FOLDED;
        }
        catch (NoSuchFieldException e) {
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
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect getObjTib(OPT_Instruction s) {
    if (CF_TIB) {
      OPT_Operand op = GuardedUnary.getVal(s);
      if (op.isNullConstant()) {
        Trap.mutate(s, TRAP, NullCheck.getClearGuardResult(s),
                    OPT_TrapCodeOperand.NullPtr());
        return DefUseEffect.TRAP_REDUCED;
      } else if (op.isConstant()) {
        try{
          // NB as the operand is final it must already have been
          // resolved.
          VM_Type type = op.getType().resolve();
          Move.mutate(s, REF_MOVE, GuardedUnary.getClearResult(s),
                      new OPT_TIBConstantOperand(type));
          return DefUseEffect.MOVE_FOLDED;
        } catch(NoClassDefFoundError e) {
          if (VM.runningVM) {
            // this is unexpected
            throw e;
          } else {
            // Class not found during bootstrap due to chasing a class
            // only valid in the bootstrap JVM
            System.out.println("Failed to resolve: " + op.getType() + ": " + e.getMessage());
          }
        }
      } else {
        OPT_RegisterOperand rop = op.asRegister();
        VM_TypeReference typeRef = rop.getType();
        if (typeRef.isResolved() && rop.isPreciseType()) {
          Move.mutate(s, REF_MOVE, GuardedUnary.getClearResult(s),
                      new OPT_TIBConstantOperand(typeRef.peekResolvedType())); 
          return DefUseEffect.MOVE_FOLDED;
        }
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect getClassTib(OPT_Instruction s) {
    if (CF_TIB) {
      OPT_TypeOperand typeOp = Unary.getVal(s).asType();
      if(typeOp.getTypeRef().isResolved()) {
        Move.mutate(s, REF_MOVE,
                    Unary.getClearResult(s),
                    new OPT_TIBConstantOperand(typeOp.getTypeRef().peekResolvedType()));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect getTypeFromTib(OPT_Instruction s) {
    if (CF_TIB) {
      OPT_Operand tibOp = Unary.getVal(s);
      if(tibOp.isTIBConstant()) {
        OPT_TIBConstantOperand tib = tibOp.asTIBConstant();
        Move.mutate(s, REF_MOVE,
                    Unary.getClearResult(s),
                    new OPT_ObjectConstantOperand(tib.value, Offset.zero()));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect getArrayElementTibFromTib(OPT_Instruction s) {
    if (CF_TIB) {
      OPT_Operand tibOp = Unary.getVal(s);
      if(tibOp.isTIBConstant()) {
        OPT_TIBConstantOperand tib = tibOp.asTIBConstant();
        Move.mutate(s, REF_MOVE,
                    Unary.getClearResult(s),
                    new OPT_TIBConstantOperand(tib.value.asArray().getElementType()));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect getSuperclassIdsFromTib(OPT_Instruction s) {
    if (CF_TIB) {
      OPT_Operand tibOp = Unary.getVal(s);
      if(tibOp.isTIBConstant()) {
        OPT_TIBConstantOperand tib = tibOp.asTIBConstant();
        Move.mutate(s, REF_MOVE,
                    Load.getClearResult(s),
                    new OPT_ObjectConstantOperand(tib.value.getSuperclassIds(), Offset.zero()));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect getDoesImplementFromTib(OPT_Instruction s) {
    if (CF_TIB) {
      OPT_Operand tibOp = Unary.getVal(s);
      if(tibOp.isTIBConstant()) {
        OPT_TIBConstantOperand tib = tibOp.asTIBConstant();
        Move.mutate(s, REF_MOVE,
                    Load.getClearResult(s),
                    new OPT_ObjectConstantOperand(tib.value.getDoesImplement(), Offset.zero()));
        return DefUseEffect.MOVE_FOLDED;
      }
    }
    return DefUseEffect.UNCHANGED;
  }
  private static DefUseEffect refLoad(OPT_Instruction s) {
    if (CF_TIB) {
      OPT_Operand base = Load.getAddress(s);
      if(base.isTIBConstant()) {
        OPT_TIBConstantOperand tib = base.asTIBConstant();
        OPT_Operand offset = Load.getOffset(s);
        if(tib.value.isInstantiated() && offset.isConstant()) {
          // Reading from a fixed offset from an effectively
          // constant array
          int intOffset;
          if (offset.isIntConstant()) {
            intOffset = offset.asIntConstant().value;
          }
          else {
            intOffset = offset.asAddressConstant().value.toInt();
          }
          int intSlot = intOffset >> LOG_BYTES_IN_ADDRESS;

          // Create appropriate constant operand for TIB slot
          OPT_ConstantOperand result;
          Object[] tibArray = tib.value.getTypeInformationBlock();
          if(tib.value.isTIBSlotTIB(intSlot)) {
            VM_Type typeOfTIB = (VM_Type)(((Object[])tibArray[intSlot])[VM_TIBLayoutConstants.TIB_TYPE_INDEX]);
            result = new OPT_TIBConstantOperand(typeOfTIB);
          } else if (tib.value.isTIBSlotCode(intSlot)) {
            // Only generate code constants when we want to make
            // some virtual calls go via the JTOC
            if (OPT_ConvertToLowLevelIR.CALL_VIA_JTOC) {
              VM_Method method = tib.value.getTIBMethodAtSlot(intSlot);
              result = new OPT_CodeConstantOperand(method);
            } else {
              return DefUseEffect.UNCHANGED;
            }
          } else {
            if (tibArray[intSlot] == null) {
              result = new OPT_NullConstantOperand();
            } else {
              result = new OPT_ObjectConstantOperand(tibArray[intSlot],
                                                     Offset.zero());
            }
          }
          Move.mutate(s, REF_MOVE,
                      Load.getClearResult(s),
                      result);
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
  private static void canonicalizeCommutativeOperator(OPT_Instruction instr) {
    if (Binary.getVal1(instr).isConstant()) {
      OPT_Operand tmp = Binary.getClearVal1(instr);
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
    for (; v != 0; v = v << 1, i--)
      if (v < 0) {
        if (power == -1)
          power = i; 
        else 
          return -1;
      }
    return power;
  }

  /**
   * Turn the given operand encoding an address constant into an Address
   */
  private static Address getAddressValue(OPT_Operand op) {
    if (op instanceof OPT_NullConstantOperand) 
      return Address.zero();
    if (op instanceof OPT_AddressConstantOperand)
      return op.asAddressConstant().value; 
    if (op instanceof OPT_IntConstantOperand)
      return Address.fromIntSignExtend(op.asIntConstant().value);
    throw new OPT_OptimizingCompilerException("Cannot getAddressValue from this operand " + op);
  }
}
