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

import com.ibm.jikesrvm.classloader.*;
import com.ibm.jikesrvm.VM;
import com.ibm.jikesrvm.VM_TIBLayoutConstants;
import com.ibm.jikesrvm.opt.ir.*;
import org.vmmagic.unboxed.*;
import java.lang.reflect.Array;
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
 */
public abstract class OPT_Simplifier extends OPT_IRTools implements OPT_Operators {
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
   * Enumeration value to indicate an operation is unchanged, although the
   * order of operands may have been canonicalized.
   */
  public static final byte UNCHANGED = 0x00;    
  /**
   * Enumeration value to indicate an operation has been replaced by a
   * move instruction with a constant right hand side.
   */
  public static final byte MOVE_FOLDED = 0x01;  
  /**
   * Enumeration value to indicate an operation has been replaced by a
   * move instruction with a non-constant right hand side.
   */
  public static final byte MOVE_REDUCED = 0x02; 
  /**
   * Enumeration value to indicate an operation has been replaced by 
   * an unconditional trap instruction.
   */
  public static final byte TRAP_REDUCED = 0x03; 
  /**
   * Enumeration value to indicate an operation has been replaced by a
   * cheaper, but non-move instruction.
   */
  public static final byte REDUCED = 0x04;      

  private static Address getAddressValue(OPT_Operand op) {
    if (op instanceof OPT_NullConstantOperand) 
      return Address.zero();
    if (op instanceof OPT_AddressConstantOperand)
      return op.asAddressConstant().value; 
    if (op instanceof OPT_IntConstantOperand)
      return Address.fromIntSignExtend(op.asIntConstant().value);
    throw new OPT_OptimizingCompilerException("Cannot getAddressValue from this operand " + op);
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
  public static byte simplify(OPT_AbstractRegisterPool regpool, OPT_Instruction s) {
    switch (s.getOpcode()) {
      ////////////////////
      // GUARD operations
      ////////////////////
    case GUARD_COMBINE_opcode:
      return guardCombine(s);
      ////////////////////
      // TRAP operations
      ////////////////////
    case TRAP_IF_opcode:
      return trapIf(s);
    case NULL_CHECK_opcode:
      return nullCheck(s);
    case INT_ZERO_CHECK_opcode:
      return intZeroCheck(s);
    case LONG_ZERO_CHECK_opcode:
      return longZeroCheck(s);
    case CHECKCAST_opcode:
      return checkcast(regpool, s);
    case CHECKCAST_UNRESOLVED_opcode:
      return checkcast(regpool, s);
    case CHECKCAST_NOTNULL_opcode:
      return checkcastNotNull(s);
    case INSTANCEOF_opcode:
      return instanceOf(regpool, s);
    case INSTANCEOF_NOTNULL_opcode:
      return instanceOfNotNull(s);
    case OBJARRAY_STORE_CHECK_opcode:
      return objarrayStoreCheck(s);
    case OBJARRAY_STORE_CHECK_NOTNULL_opcode:
      return objarrayStoreCheckNotNull(s);
    case MUST_IMPLEMENT_INTERFACE_opcode:
      return mustImplementInterface(s);
      ////////////////////
      // Conditional moves
      ////////////////////
    case INT_COND_MOVE_opcode:
      return intCondMove(s);
    case LONG_COND_MOVE_opcode:
      return longCondMove(s);
    case FLOAT_COND_MOVE_opcode:
      return floatCondMove(s);
    case DOUBLE_COND_MOVE_opcode:
      return doubleCondMove(s);
    case REF_COND_MOVE_opcode:
      return refCondMove(s);
    case GUARD_COND_MOVE_opcode:
      return guardCondMove(s);
      ////////////////////
      // INT ALU operations
      ////////////////////
    case BOOLEAN_NOT_opcode:
      return booleanNot(s);
    case BOOLEAN_CMP_INT_opcode:
      return booleanCmpInt(s);
    case BOOLEAN_CMP_ADDR_opcode:
      return booleanCmpAddr(s);
    case INT_ADD_opcode:
      return intAdd(s);
    case INT_AND_opcode:
      return intAnd(s);
    case INT_DIV_opcode:
      return intDiv(s);
    case INT_MUL_opcode:
      return intMul(regpool, s);
    case INT_NEG_opcode:
      return intNeg(s);
    case INT_NOT_opcode:
      return intNot(s);
    case INT_OR_opcode:
      return intOr(s);
    case INT_REM_opcode:
      return intRem(s);
    case INT_SHL_opcode:
      return intShl(s);
    case INT_SHR_opcode:
      return intShr(s);
    case INT_SUB_opcode:
      return intSub(s);
    case INT_USHR_opcode:
      return intUshr(s);
    case INT_XOR_opcode:
      return intXor(s);
      ////////////////////
      // WORD ALU operations
      ////////////////////
    case REF_ADD_opcode:
      return refAdd(s);
    case REF_AND_opcode:
      return refAnd(s);
    case REF_SHL_opcode:
      return refShl(s);
    case REF_SHR_opcode:
      return refShr(s);
    case REF_NOT_opcode:
      return refNot(s);
    case REF_OR_opcode:
      return refOr(s);
    case REF_SUB_opcode:
      return refSub(s);
    case REF_USHR_opcode:
      return regUshr(s);
    case REF_XOR_opcode:
      return refXor(s);
      ////////////////////
      // LONG ALU operations
      ////////////////////
    case LONG_ADD_opcode:
      return longAdd(s);
    case LONG_AND_opcode:
      return longAnd(s);
    case LONG_CMP_opcode:
      return longCmp(s);
    case LONG_DIV_opcode:
      return longDiv(s);
    case LONG_MUL_opcode:
      return longMul(s);
    case LONG_NEG_opcode:
      return longNeg(s);
    case LONG_NOT_opcode:
      return longNot(s);
    case LONG_OR_opcode:
      return longOr(s);
    case LONG_REM_opcode:
      return longRem(s);
    case LONG_SHL_opcode:
      return longShl(s);
    case LONG_SHR_opcode:
      return longShr(s);
    case LONG_SUB_opcode:
      return longSub(s);
    case LONG_USHR_opcode:
      return longUshr(s);
    case LONG_XOR_opcode:
      return longXor(s);
      ////////////////////
      // FLOAT ALU operations
      ////////////////////
    case FLOAT_ADD_opcode:
      return floatAdd(s);
    case FLOAT_CMPG_opcode:
      return floatCmpg(s);
    case FLOAT_CMPL_opcode:
      return floatCmpl(s);
    case FLOAT_DIV_opcode:
      return floatDiv(s);
    case FLOAT_MUL_opcode:
      return floatMul(s);
    case FLOAT_NEG_opcode:
      return floatNeg(s);
    case FLOAT_REM_opcode:
      return floatRem(s);
    case FLOAT_SUB_opcode:
      return floatSub(s);
      ////////////////////
      // DOUBLE ALU operations
      ////////////////////
    case DOUBLE_ADD_opcode:
      return doubleAdd(s);
    case DOUBLE_CMPG_opcode:
      return doubleCmpg(s);
    case DOUBLE_CMPL_opcode:
      return doubleCmpl(s);
    case DOUBLE_DIV_opcode:
      return doubleDiv(s);
    case DOUBLE_MUL_opcode:
      return doubleMul(s);
    case DOUBLE_NEG_opcode:
      return doubleNeg(s);
    case DOUBLE_REM_opcode:
      return doubleRem(s);
    case DOUBLE_SUB_opcode:
      return doubleSub(s);
      ////////////////////
      // CONVERSION operations
      ////////////////////
    case DOUBLE_2FLOAT_opcode:
      return double2Float(s);
    case DOUBLE_2INT_opcode:
      return double2Int(s);
    case DOUBLE_2LONG_opcode:
      return double2Long(s);
    case DOUBLE_AS_LONG_BITS_opcode:
      return doubleAsLongBits(s);
    case INT_2DOUBLE_opcode:
      return int2Double(s);
    case INT_2BYTE_opcode:
      return int2Byte(s);
    case INT_2USHORT_opcode:
      return int2UShort(s);
    case INT_2FLOAT_opcode:
      return int2Float(s);
    case INT_2LONG_opcode:
      return int2Long(s);
    case INT_2ADDRSigExt_opcode:
      return int2AddrSigExt(s);
    case INT_2ADDRZerExt_opcode:
      return int2AddrZerExt(s);
      //-#if RVM_FOR_64_ADDR
    case LONG_2ADDR_opcode:
      return long2Addr(s);
      //-#endif
    case INT_2SHORT_opcode:
      return int2Short(s);
    case INT_BITS_AS_FLOAT_opcode:
      return intBitsAsFloat(s);
    case ADDR_2INT_opcode:
      return addr2Int(s);
    case ADDR_2LONG_opcode:
      return addr2Long(s);
    case FLOAT_2DOUBLE_opcode:
      return float2Double(s);
    case FLOAT_2INT_opcode:
      return float2Int(s);
    case FLOAT_2LONG_opcode:
      return float2Long(s);
    case FLOAT_AS_INT_BITS_opcode:
      return floatAsIntBits(s);
    case LONG_2FLOAT_opcode:
      return long2Float(s);
    case LONG_2INT_opcode:
      return long2Int(s);
    case LONG_2DOUBLE_opcode:
      return long2Double(s);
    case LONG_BITS_AS_DOUBLE_opcode:
      return longBitsAsDouble(s);
      ////////////////////
      // Field operations
      ////////////////////
    case ARRAYLENGTH_opcode:
      return arrayLength(s);
    case BOUNDS_CHECK_opcode:
      return boundsCheck(s);
    case CALL_opcode:
      return call(s);
    case GETFIELD_opcode:
      return getField(s);
    case GET_OBJ_TIB_opcode:
      return getObjTib(s);
    case GET_CLASS_TIB_opcode:
      return getClassTib(s);
    case GET_TYPE_FROM_TIB_opcode:
      return getTypeFromTib(s);
    case GET_ARRAY_ELEMENT_TIB_FROM_TIB_opcode:
      return getArrayElementTibFromTib(s);
    case GET_SUPERCLASS_IDS_FROM_TIB_opcode:
      return getSuperclassIdsFromTib(s);
    case GET_DOES_IMPLEMENT_FROM_TIB_opcode:
      return getDoesImplementFromTib(s);
    case REF_LOAD_opcode:
      return refLoad(s);
    default:
      return UNCHANGED;
    }
  }
  
  private static byte guardCombine(OPT_Instruction s) {
    OPT_Operand op1 = Binary.getVal1(s);
    OPT_Operand op2 = Binary.getVal2(s);
    if (op1.similar(op2) || (op2 instanceof OPT_TrueGuardOperand)) {
      Move.mutate(s, GUARD_MOVE, Binary.getClearResult(s), op1);
      if (op1 instanceof OPT_TrueGuardOperand) {
        // BOTH true guards: FOLD
        return MOVE_FOLDED;
      } else {
        // ONLY OP2 IS TrueGuard: MOVE REDUCE
        return MOVE_REDUCED;
      }
    }
    else if(op1 instanceof OPT_TrueGuardOperand) {
      // ONLY OP1 IS TrueGuard: MOVE REDUCE
      Move.mutate(s, GUARD_MOVE, Binary.getClearResult(s), op2);
      return MOVE_REDUCED;
    }
    else {
      return UNCHANGED;
    }
  }
  private static byte trapIf(OPT_Instruction s) {
   { 
      OPT_Operand op1 = TrapIf.getVal1(s);
      OPT_Operand op2 = TrapIf.getVal2(s);
      if (op1.isConstant()) {
        if (op2.isConstant()) {
          int willTrap = TrapIf.getCond(s).evaluate(op1, op2);
          if (willTrap == OPT_ConditionOperand.TRUE) {
            Trap.mutate(s, TRAP, TrapIf.getClearGuardResult(s), 
                        TrapIf.getClearTCode(s));
            return TRAP_REDUCED;
          } else if (willTrap == OPT_ConditionOperand.FALSE) {
            Move.mutate(s, GUARD_MOVE, TrapIf.getClearGuardResult(s), TG());
            return MOVE_FOLDED;
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
    return UNCHANGED;
  }
  private static byte nullCheck(OPT_Instruction s) {
      OPT_Operand ref = NullCheck.getRef(s);
      if (ref.isNullConstant() ||
          (ref.isAddressConstant() && ref.asAddressConstant().value.isZero())) {
          Trap.mutate(s, TRAP, NullCheck.getClearGuardResult(s),
                      OPT_TrapCodeOperand.NullPtr());
          return TRAP_REDUCED;
      } else if (ref.isConstant()) {
          // object, string, class or non-null address constant
          
          // Make the slightly suspect assumption that all non-zero address
          // constants are actually valid pointers. Not necessarily true,
          // but unclear what else we can do.
          Move.mutate(s, GUARD_MOVE, NullCheck.getClearGuardResult(s), TG());
          return MOVE_FOLDED;
      }
      else {
          return UNCHANGED;
      }
  }
  private static byte intZeroCheck(OPT_Instruction s) {
   {
      OPT_Operand op = ZeroCheck.getValue(s);
      if (op.isIntConstant()) {
        int val = op.asIntConstant().value;
        if (val == 0) {
          Trap.mutate(s, TRAP, ZeroCheck.getClearGuardResult(s),
                      OPT_TrapCodeOperand.DivByZero());
          return TRAP_REDUCED;
        } else {
          Move.mutate(s, GUARD_MOVE, ZeroCheck.getClearGuardResult(s), TG());
          return MOVE_FOLDED;
        }
      }
    }
    return UNCHANGED;
  }
  private static byte longZeroCheck(OPT_Instruction s) {
   {
      OPT_Operand op = ZeroCheck.getValue(s);
      if (op.isLongConstant()) {
        long val = op.asLongConstant().value;
        if (val == 0L) {
          Trap.mutate(s, TRAP, ZeroCheck.getClearGuardResult(s),
                      OPT_TrapCodeOperand.DivByZero());
          return TRAP_REDUCED;
        } else {
          Move.mutate(s, GUARD_MOVE, ZeroCheck.getClearGuardResult(s), TG());
          return MOVE_FOLDED;
        }
      }
    }
    return UNCHANGED;
  }
  private static byte checkcast(OPT_AbstractRegisterPool regpool, OPT_Instruction s) {
    OPT_Operand ref = TypeCheck.getRef(s);
    if (ref.isNullConstant()) {
      Empty.mutate(s, NOP);
      return REDUCED;
    } else if (ref.isConstant()) {
      s.operator = CHECKCAST_NOTNULL;
      return checkcastNotNull(s);
    } else {
      VM_TypeReference lhsType = TypeCheck.getType(s).getTypeRef();
      VM_TypeReference rhsType = ref.getType();
      byte ans = OPT_ClassLoaderProxy.includesType(lhsType, rhsType);
      if (ans == OPT_Constants.YES) {
        Empty.mutate(s, NOP);
        return REDUCED;
      } else {
        // NOTE: OPT_Constants.NO can't help us because (T)null always succeeds
        return UNCHANGED;
      }
    }
  }
  private static byte checkcastNotNull(OPT_Instruction s) {
    OPT_Operand ref = TypeCheck.getRef(s);
    VM_TypeReference lhsType = TypeCheck.getType(s).getTypeRef();
    VM_TypeReference rhsType = ref.getType();
    byte ans = OPT_ClassLoaderProxy.includesType(lhsType, rhsType);
    if (ans == OPT_Constants.YES) {
      Empty.mutate(s, NOP);
      return REDUCED;
    } else if (ans == OPT_Constants.NO) {
      VM_Type rType = rhsType.peekResolvedType();
      if (rType != null && rType.isClassType() && rType.asClass().isFinal()) {
        // only final (or precise) rhs types can be optimized since rhsType may be conservative
        Trap.mutate(s, TRAP, null, OPT_TrapCodeOperand.CheckCast());
        return TRAP_REDUCED;
      } else {
        return UNCHANGED;
      }
    }
    else {
      return UNCHANGED;
    }
  }
  private static byte instanceOf(OPT_AbstractRegisterPool regpool, OPT_Instruction s) {
    OPT_Operand ref = InstanceOf.getRef(s);
    if (ref.isNullConstant()) {
      Move.mutate(s, INT_MOVE, InstanceOf.getClearResult(s), IC(0));
      return MOVE_FOLDED;
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
          return MOVE_FOLDED;
        }
        else {
          return UNCHANGED;
        }
      }
      else {
        return UNCHANGED;
      }
    }
  }
  private static byte instanceOfNotNull(OPT_Instruction s) {
   {
      OPT_Operand ref = InstanceOf.getRef(s);
      VM_TypeReference lhsType = InstanceOf.getType(s).getTypeRef();
      VM_TypeReference rhsType = ref.getType();
      byte ans = OPT_ClassLoaderProxy.includesType(lhsType, rhsType);
      if (ans == OPT_Constants.YES) {
        Move.mutate(s, INT_MOVE, InstanceOf.getClearResult(s), IC(1));
        return MOVE_FOLDED;
      } else if (ans == OPT_Constants.NO) {
        VM_Type rType = rhsType.peekResolvedType();
        if (rType != null && rType.isClassType() && rType.asClass().isFinal()) {
          // only final (or precise) rhs types can be optimized since rhsType may be conservative
          Move.mutate(s, INT_MOVE, InstanceOf.getClearResult(s), IC(0));
          return MOVE_FOLDED;
        }
      }
    }
    return UNCHANGED;
  }
  private static byte objarrayStoreCheck(OPT_Instruction s){
    OPT_Operand val = StoreCheck.getVal(s);
    if (val.isNullConstant()) {
      // Writing null into an array is trivially safe
      Move.mutate(s, GUARD_MOVE, StoreCheck.getClearGuardResult(s), StoreCheck.getClearGuard(s));
      return MOVE_REDUCED;
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
          return MOVE_REDUCED;
        }
      }
      if (ref.isConstant() && (arrayTypeRef == VM_TypeReference.JavaLangObjectArray)) {
        // We know this to be an array of objects so any store must
        // be safe
        Move.mutate(s, GUARD_MOVE, StoreCheck.getClearGuardResult(s),
                    StoreCheck.getClearGuard(s));
        return MOVE_REDUCED;          
      }
      if (val.isConstant() && ref.isConstant()) {
        // writing a constant value into a constant array
        byte ans = OPT_ClassLoaderProxy.includesType(arrayTypeRef.getArrayElementType(), val.getType());
        if (ans == OPT_Constants.YES) {
          // all stores should succeed
          Move.mutate(s, GUARD_MOVE, StoreCheck.getClearGuardResult(s), StoreCheck.getClearGuard(s));
          return MOVE_REDUCED;
        }
        else if (ans == OPT_Constants.NO) {
          // all stores will fail
          Trap.mutate(s, TRAP, StoreCheck.getClearGuardResult(s), OPT_TrapCodeOperand.StoreCheck());
          return TRAP_REDUCED;
        }
      }
      return UNCHANGED;
    }
  }
  private static byte objarrayStoreCheckNotNull(OPT_Instruction s){
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
        return MOVE_REDUCED;
      }
    }
    if (ref.isConstant() && (arrayTypeRef == VM_TypeReference.JavaLangObjectArray)) {
      // We know this to be an array of objects so any store must
      // be safe
      Move.mutate(s, GUARD_MOVE, StoreCheck.getClearGuardResult(s),
                  StoreCheck.getClearGuard(s));
      return MOVE_REDUCED;          
    }
    if (val.isConstant() && ref.isConstant()) {
      // writing a constant value into a constant array
      byte ans = OPT_ClassLoaderProxy.includesType(arrayTypeRef.getArrayElementType(), val.getType());
      if (ans == OPT_Constants.YES) {
        // all stores should succeed
        Move.mutate(s, GUARD_MOVE, StoreCheck.getClearGuardResult(s), StoreCheck.getClearGuard(s));
        return MOVE_REDUCED;
      }
      else if (ans == OPT_Constants.NO) {
        // all stores will fail
        Trap.mutate(s, TRAP, StoreCheck.getClearGuardResult(s), OPT_TrapCodeOperand.StoreCheck());
        return TRAP_REDUCED;
      }
    }
    return UNCHANGED;
  }
  private static byte mustImplementInterface(OPT_Instruction s) {
    OPT_Operand ref = TypeCheck.getRef(s);
    if (VM.VerifyAssertions) VM._assert(!ref.isNullConstant());       
    VM_TypeReference lhsType = TypeCheck.getType(s).getTypeRef(); // the interface that must be implemented
    VM_TypeReference rhsType = ref.getType();                     // our type
    byte ans = OPT_ClassLoaderProxy.includesType(lhsType, rhsType);
    if (ans == OPT_Constants.YES) {
      Empty.mutate(s, NOP);
      return REDUCED;
    } else if (ans == OPT_Constants.NO) {
      VM_Type rType = rhsType.peekResolvedType();
      if (rType != null && rType.isClassType() && rType.asClass().isFinal()) {
        // only final (or precise) rhs types can be optimized since rhsType may be conservative
        Trap.mutate(s, TRAP, null, OPT_TrapCodeOperand.MustImplement());
        return TRAP_REDUCED;
      }
    }
    return UNCHANGED;
  }
  private static byte intCondMove(OPT_Instruction s) {
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
        return val.isConstant() ? MOVE_FOLDED : MOVE_REDUCED;
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
        return tv.isConstant() ? MOVE_FOLDED : MOVE_REDUCED;
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
          return REDUCED;
        }
        if (itv == 0 && ifv == 1) {
          BooleanCmp.mutate(s, op, CondMove.getClearResult(s),
                            CondMove.getClearVal1(s), CondMove.getClearVal2(s),
                            CondMove.getClearCond(s).flipCode(), new OPT_BranchProfileOperand());
          return REDUCED;
        }
      }
    }
    return UNCHANGED;
  }
  private static byte longCondMove(OPT_Instruction s) {
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
        return val.isConstant() ? MOVE_FOLDED : MOVE_REDUCED;
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
        return tv.isConstant() ? MOVE_FOLDED : MOVE_REDUCED;
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
          return REDUCED;
        }
        if (itv == 0 && ifv == 1) {
          BooleanCmp.mutate(s, op, CondMove.getClearResult(s),
                            CondMove.getClearVal1(s), CondMove.getClearVal2(s),
                            CondMove.getClearCond(s).flipCode(), new OPT_BranchProfileOperand());
          return REDUCED;
        }
      }
    }
    return UNCHANGED;
  }
  private static byte floatCondMove(OPT_Instruction s) {
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
        return val.isConstant() ? MOVE_FOLDED : MOVE_REDUCED;
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
        return tv.isConstant() ? MOVE_FOLDED : MOVE_REDUCED;
      }
    }
    return UNCHANGED;
  }
  private static byte doubleCondMove(OPT_Instruction s) {
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
        return val.isConstant() ? MOVE_FOLDED : MOVE_REDUCED;
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
        return tv.isConstant() ? MOVE_FOLDED : MOVE_REDUCED;
      }
    }
    return UNCHANGED;
  }
  private static byte refCondMove(OPT_Instruction s) {
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
            return val.isConstant() ? MOVE_FOLDED : MOVE_REDUCED;
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
        return val.isConstant() ? MOVE_FOLDED : MOVE_REDUCED;
      }
    }
    return UNCHANGED;
  }
  private static byte guardCondMove(OPT_Instruction s) {
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
            return val.isConstant() ? MOVE_FOLDED : MOVE_REDUCED;
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
        return val.isConstant() ? MOVE_FOLDED : MOVE_REDUCED;
      }
    }
    return UNCHANGED;
  }
  private static byte booleanNot(OPT_Instruction s) {
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
        return MOVE_FOLDED;
      }
    }
    return UNCHANGED;
  }
  private static byte booleanCmpInt(OPT_Instruction s) {
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
            return MOVE_FOLDED;
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
    return UNCHANGED;
  }
  private static byte booleanCmpAddr(OPT_Instruction s) {
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
            return MOVE_FOLDED;
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
    return UNCHANGED;
  }
  private static byte intAdd(OPT_Instruction s) {
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
          return MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0) {
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return MOVE_REDUCED;
          }
        }
      }
    }
    return UNCHANGED;
  }
  private static byte intAnd(OPT_Instruction s) {
   if (CF_INT) {
      canonicalizeCommutativeOperator(s);
      OPT_Operand op1 = Binary.getVal1(s);
      OPT_Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x & x == x
        Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                    Binary.getClearVal1(s));
        return op1.isConstant() ? MOVE_FOLDED : MOVE_REDUCED;
      }
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;
        if (op1.isIntConstant()) {
          // BOTH CONSTANTS: FOLD
          int val1 = op1.asIntConstant().value;
          Move.mutate(s, INT_MOVE, Binary.getClearResult(s), IC(val1 & val2));
          return MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0) {                  // x & 0 == 0
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), IC(0));
            return MOVE_FOLDED;
          }
          if (val2 == -1) {                 // x & -1 == x & 0xffffffff == x
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return MOVE_REDUCED;
          }
        }
      }
    }
    return UNCHANGED;
  }
  private static byte intDiv(OPT_Instruction s) {
   if (CF_INT) {
      OPT_Operand op1 = GuardedBinary.getVal1(s);
      OPT_Operand op2 = GuardedBinary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x / x == 1
        Move.mutate(s, INT_MOVE, GuardedBinary.getClearResult(s), 
                    IC(1));
        return MOVE_FOLDED;
      }
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;
        if (val2 == 0) {
          // TODO: This instruction is actually unreachable.  
          // There will be an INT_ZERO_CHECK
          // guarding this instruction that will result in an 
          // ArithmeticException.  We
          // should probabbly just remove the INT_DIV as dead code.
          return UNCHANGED;
        }
        if (op1.isIntConstant()) {
          // BOTH CONSTANTS: FOLD
          int val1 = op1.asIntConstant().value;
          Move.mutate(s, INT_MOVE, GuardedBinary.getClearResult(s), 
                      IC(val1/val2));
          return MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 1) {                  // x / 1 == x;
            Move.mutate(s, INT_MOVE, GuardedBinary.getClearResult(s), 
                        GuardedBinary.getClearVal1(s));
            return MOVE_REDUCED;
          }
          // x / c == x >> (log c) if c is power of 2
          int power = PowerOf2(val2);
          if (power != -1) {
            Binary.mutate(s, INT_SHR, GuardedBinary.getClearResult(s), 
                          GuardedBinary.getClearVal1(s), IC(power));
            return REDUCED;
          }
        }
      }
    }
    return UNCHANGED;
  }
  private static byte intMul(OPT_AbstractRegisterPool regpool, OPT_Instruction s) {
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
          return MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == -1) {                 // x * -1 == -x
            Unary.mutate(s, INT_NEG, Binary.getClearResult(s), 
                         Binary.getClearVal1(s));
            return REDUCED;
          }
          if (val2 == 0) {                  // x * 0 == 0
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), IC(0));
            return MOVE_FOLDED;
          }
          if (val2 == 1) {                  // x * 1 == x
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return MOVE_REDUCED;
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
              return REDUCED;
            }
          }
        }
      }
    }
    return UNCHANGED;
  }
  private static byte intNeg(OPT_Instruction s) {
   if (CF_INT) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isIntConstant()) {
        // CONSTANT: FOLD
        int val = op.asIntConstant().value;
        Move.mutate(s, INT_MOVE, Unary.getClearResult(s), IC(-val));
        return MOVE_FOLDED;
      }
    }
    return UNCHANGED;
  }
  private static byte intNot(OPT_Instruction s) {
   if (CF_INT) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isIntConstant()) {
        // CONSTANT: FOLD
        int val = op.asIntConstant().value;
        Move.mutate(s, INT_MOVE, Unary.getClearResult(s), IC(~val));
        return MOVE_FOLDED;
      }
    }
    return UNCHANGED;
  }
  private static byte intOr(OPT_Instruction s) {
   if (CF_INT) {
      canonicalizeCommutativeOperator(s);
      OPT_Operand op1 = Binary.getVal1(s);
      OPT_Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x | x == x
        Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                    Binary.getClearVal1(s));
        return op1.isConstant() ? MOVE_FOLDED : MOVE_REDUCED;
      }
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;
        if (op1.isIntConstant()) {
          // BOTH CONSTANTS: FOLD
          int val1 = op1.asIntConstant().value;
          Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                      IC(val1 | val2));
          return MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == -1) { // x | -1 == x | 0xffffffff == 0xffffffff == -1
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), IC(-1));
            return MOVE_FOLDED;
          }
          if (val2 == 0) {                  // x | 0 == x
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return MOVE_REDUCED;
          }
        }
      }
    }
    return UNCHANGED;
  }
  private static byte intRem(OPT_Instruction s) {
   if (CF_INT) {
      OPT_Operand op1 = GuardedBinary.getVal1(s);
      OPT_Operand op2 = GuardedBinary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x % x == 0
        Move.mutate(s, INT_MOVE, GuardedBinary.getClearResult(s), 
                    IC(0));
        return MOVE_FOLDED;
      }
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;
        if (val2 == 0) {
          // TODO: This instruction is actually unreachable.  
          // There will be an INT_ZERO_CHECK
          // guarding this instruction that will result in an 
          // ArithmeticException.  We
          // should probabbly just remove the INT_REM as dead code.
          return UNCHANGED;
        }
        if (op1.isIntConstant()) {
          // BOTH CONSTANTS: FOLD
          int val1 = op1.asIntConstant().value;
          Move.mutate(s, INT_MOVE, GuardedBinary.getClearResult(s), 
                      IC(val1%val2));
          return MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if ((val2 == 1)||(val2 == -1)) {             // x % 1 == 0
            Move.mutate(s, INT_MOVE, GuardedBinary.getClearResult(s), 
                        IC(0));
            return MOVE_FOLDED;
          }
        }
      }
    }
    return UNCHANGED;
  }
  private static byte intShl(OPT_Instruction s) {
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
          return MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0) {                  // x << 0 == x
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return MOVE_REDUCED;
          }
          if (val2 >= BITS_IN_INT) {                  // x << 32 == 0
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                        IC(0));
            return MOVE_FOLDED;
          }
        }
      }
    }
    return UNCHANGED;
  }
  private static byte intShr(OPT_Instruction s) {
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
          return MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0) {                  // x >> 0 == x
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return MOVE_REDUCED;
          }
        }
      }
    }
    return UNCHANGED;
  }
  private static byte intSub(OPT_Instruction s) {
   if (CF_INT) {
      OPT_Operand op1 = Binary.getVal1(s);
      OPT_Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x - x == 0
        Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                    IC(0));
        return MOVE_FOLDED;
      }
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;
        if (op1.isIntConstant()) {
          // BOTH CONSTANTS: FOLD
          int val1 = op1.asIntConstant().value;
          Move.mutate(s, INT_MOVE, Binary.getClearResult(s), IC(val1 - val2));
          return MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0) {                  // x - 0 == x
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return MOVE_REDUCED;
          }
          // x - c = x + -c
          // prefer adds, since some architectures have addi but not subi
          Binary.mutate(s, INT_ADD, Binary.getClearResult(s), 
                        Binary.getClearVal1(s), IC(-val2));
          return REDUCED;
        }
      }
    }
    return UNCHANGED;
  }
  private static byte intUshr(OPT_Instruction s) {
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
          return MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0) {                  // x >>> 0 == x
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return MOVE_REDUCED;
          }
          if (val2 >= BITS_IN_INT) {                  // x >>> 32 == 0
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                        IC(0));
            return MOVE_FOLDED;
          }
        }
      }
    }
    return UNCHANGED;
  }
  private static byte intXor(OPT_Instruction s) {
   if (CF_INT) {
      canonicalizeCommutativeOperator(s);
      OPT_Operand op1 = Binary.getVal1(s);
      OPT_Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x ^ x == 0
        Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                    IC(0));
        return MOVE_FOLDED;
      }
      if (op2.isIntConstant()) {
        int val2 = op2.asIntConstant().value;

        if (op1.isIntConstant()) {
          // BOTH CONSTANTS: FOLD
          int val1 = op1.asIntConstant().value;
          Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                      IC(val1 ^ val2));
          return MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == -1) {                 // x ^ -1 == x ^ 0xffffffff = ~x
            Unary.mutate(s, INT_NOT, Binary.getClearResult(s), 
                         Binary.getClearVal1(s));
            return REDUCED;
          }
          if (val2 == 0) {                  // x ^ 0 == x
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return MOVE_REDUCED;
          }
        }
      }
    }
    return UNCHANGED;
  }
  private static byte refAdd(OPT_Instruction s) {
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
          return MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2.isZero()) {                 // x + 0 == x
            if (op1.isIntLike()) {
              Unary.mutate(s, INT_2ADDRSigExt, Binary.getClearResult(s), Binary.getClearVal1(s));
            } else {
              Move.mutate(s, REF_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
            }
            return MOVE_REDUCED;
          }
        }
      }
    }
    return UNCHANGED;
  }
  private static byte refAnd(OPT_Instruction s) {
   if (CF_ADDR) {
      canonicalizeCommutativeOperator(s);
      OPT_Operand op1 = Binary.getVal1(s);
      OPT_Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x & x == x
        Move.mutate(s, REF_MOVE, Binary.getClearResult(s), 
                    Binary.getClearVal1(s));
        return op1.isConstant() ? MOVE_FOLDED : MOVE_REDUCED;
      }
      if (op2.isAddressConstant()) {
        Word val2 = op2.asAddressConstant().value.toWord();
        if (op1.isAddressConstant()) {
          // BOTH CONSTANTS: FOLD
          Word val1 = op1.asAddressConstant().value.toWord();
          Move.mutate(s, REF_MOVE, Binary.getClearResult(s), AC(val1.and(val2).toAddress()));
          return MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2.isZero()) {                  // x & 0 == 0
            Move.mutate(s, REF_MOVE, Binary.getClearResult(s), AC(Address.zero()));
            return MOVE_FOLDED;
          }
          if (val2.isMax()) {                 // x & -1 == x & 0xffffffff == x
            Move.mutate(s, REF_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return MOVE_REDUCED;
          }
        }
      }
    }
    return UNCHANGED;
  }
  private static byte refShl(OPT_Instruction s) {
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
          return MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0) {                  // x << 0 == x
            Move.mutate(s, REF_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return MOVE_REDUCED;
          }
          if (val2 >= BITS_IN_ADDRESS) {                  // x << 32 == 0
            Move.mutate(s, REF_MOVE, Binary.getClearResult(s), 
                        IC(0));
            return MOVE_FOLDED;
          }
        }
      }
    }
    return UNCHANGED;
  }
  private static byte refShr(OPT_Instruction s) {
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
          return MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0) {                  // x >> 0 == x
            Move.mutate(s, REF_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return MOVE_REDUCED;
          }
        }
      }
    }
    return UNCHANGED;
  }
  private static byte refNot(OPT_Instruction s) {
   if (CF_ADDR) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isAddressConstant()) {
        // CONSTANT: FOLD
        Word val = op.asAddressConstant().value.toWord();
        Move.mutate(s, REF_MOVE, Unary.getClearResult(s), AC(val.not().toAddress()));
        return MOVE_FOLDED;
      }
    }
    return UNCHANGED;
  }
  private static byte refOr(OPT_Instruction s) {
   if (CF_ADDR) {
      canonicalizeCommutativeOperator(s);
      OPT_Operand op1 = Binary.getVal1(s);
      OPT_Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x | x == x
        Move.mutate(s, REF_MOVE, Binary.getClearResult(s), 
                    Binary.getClearVal1(s));
        return op1.isConstant() ? MOVE_FOLDED : MOVE_REDUCED;
      }
      if (op2.isAddressConstant()) {
        Word val2 = op2.asAddressConstant().value.toWord();
        if (op1.isAddressConstant()) {
          // BOTH CONSTANTS: FOLD
          Word val1 = op1.asAddressConstant().value.toWord();
          Move.mutate(s, REF_MOVE, Binary.getClearResult(s), 
                      AC(val1.or(val2).toAddress()));
          return MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2.isMax()) { // x | -1 == x | 0xffffffff == 0xffffffff == -1
            Move.mutate(s, REF_MOVE, Binary.getClearResult(s), AC(Address.max()));
            return MOVE_FOLDED;
          }
          if (val2.isZero()) {                  // x | 0 == x
            Move.mutate(s, REF_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return MOVE_REDUCED;
          }
        }
      }
    }
    return UNCHANGED;
  }
  private static byte refSub(OPT_Instruction s) {
   if (CF_ADDR) {
      OPT_Operand op1 = Binary.getVal1(s);
      OPT_Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x - x == 0
        Move.mutate(s, REF_MOVE, Binary.getClearResult(s), 
                    IC(0));
        return MOVE_FOLDED;
      }
      if (op2.isConstant() && !op2.isObjectConstant()) {
        Address val2 = getAddressValue(op2);
        if (op1.isConstant() && !op1.isObjectConstant()) {
          // BOTH CONSTANTS: FOLD
          Address val1 = getAddressValue(op1);
          Move.mutate(s, REF_MOVE, Binary.getClearResult(s), AC(val1.minus(val2.toWord().toOffset())));
          return MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2.isZero()) {                 // x - 0 == x
            if (op1.isIntLike()) {
              Unary.mutate(s, INT_2ADDRSigExt, Binary.getClearResult(s), Binary.getClearVal1(s));
            } else {
              Move.mutate(s, REF_MOVE, Binary.getClearResult(s), Binary.getClearVal1(s));
            }
            return MOVE_REDUCED;
          }
          // x - c = x + -c
          // prefer adds, since some architectures have addi but not subi
          Binary.mutate(s, REF_ADD, Binary.getClearResult(s), 
                        Binary.getClearVal1(s), AC(Address.zero().minus(val2.toWord().toOffset())));
          return REDUCED;
        }
      }
    }
    return UNCHANGED;
  }
  private static byte regUshr(OPT_Instruction s) {
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
          return MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0) {                  // x >>> 0 == x
            Move.mutate(s, REF_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return MOVE_REDUCED;
          }
          if (val2 >= BITS_IN_ADDRESS) {                  // x >>> 32 == 0
            Move.mutate(s, REF_MOVE, Binary.getClearResult(s), 
                        IC(0));
            return MOVE_FOLDED;
          }
        }
      }
    }
    return UNCHANGED;
  }
  private static byte refXor(OPT_Instruction s) {
   if (CF_ADDR) {
      canonicalizeCommutativeOperator(s);
      OPT_Operand op1 = Binary.getVal1(s);
      OPT_Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x ^ x == 0
        Move.mutate(s, REF_MOVE, Binary.getClearResult(s), 
                    IC(0));
        return MOVE_FOLDED;
      }
      if (op2.isAddressConstant()) {
        Word val2 = op2.asAddressConstant().value.toWord();
        if (op1.isAddressConstant()) {
          // BOTH CONSTANTS: FOLD
          Word val1 = op1.asAddressConstant().value.toWord();
          Move.mutate(s, REF_MOVE, Binary.getClearResult(s), 
                      AC(val1.xor(val2).toAddress()));
          return MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2.isMax()) {                 // x ^ -1 == x ^ 0xffffffff = ~x
            Unary.mutate(s, REF_NOT, Binary.getClearResult(s), 
                         Binary.getClearVal1(s));
            return REDUCED;
          }
          if (val2.isZero()) {                  // x ^ 0 == x
            Move.mutate(s, REF_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return MOVE_REDUCED;
          }
        }
      }
    }
    return UNCHANGED;
  }
  private static byte longAdd(OPT_Instruction s) {
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
          return MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0L) {                 // x + 0 == x
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return MOVE_REDUCED;
          }
        }
      }
    }
    return UNCHANGED;
  }
  private static byte longAnd(OPT_Instruction s) {
   if (CF_LONG) {
      canonicalizeCommutativeOperator(s);
      OPT_Operand op1 = Binary.getVal1(s);
      OPT_Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x & x == x
        Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                    Binary.getClearVal1(s));
        return op1.isConstant() ? MOVE_FOLDED : MOVE_REDUCED;
      }
      if (op2.isLongConstant()) {
        long val2 = op2.asLongConstant().value;
        if (op1.isLongConstant()) {
          // BOTH CONSTANTS: FOLD
          long val1 = op1.asLongConstant().value;
          Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                      LC(val1 & val2));
          return MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0L) {                 // x & 0L == 0L
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), LC(0L));
            return MOVE_FOLDED;
          }
          if (val2 == -1) {                 // x & -1L == x & 0xff...ff == x
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return MOVE_REDUCED;
          }
        }
      }
    }
    return UNCHANGED;
  }
  private static byte longCmp(OPT_Instruction s) {
   if (CF_LONG) {
      OPT_Operand op1 = Binary.getVal1(s);
      OPT_Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: op1 == op2
        Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                    IC(0));
        return MOVE_FOLDED;
      }
      if (op2.isLongConstant()) {
        if (op1.isLongConstant()) {
          // BOTH CONSTANTS: FOLD
          long val1 = op1.asLongConstant().value;
          long val2 = op2.asLongConstant().value;
          int result = (val1 > val2) ? 1 : ((val1 == val2) ? 0 : -1);
          Move.mutate(s, INT_MOVE, Binary.getClearResult(s), IC(result));
          return MOVE_FOLDED;
        }
      }
    }
    return UNCHANGED;
  }
  private static byte longDiv(OPT_Instruction s) {
   if (CF_LONG) {
      OPT_Operand op1 = GuardedBinary.getVal1(s);
      OPT_Operand op2 = GuardedBinary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x / x == 1
        Move.mutate(s, LONG_MOVE, GuardedBinary.getClearResult(s), 
                    LC(1));
        return MOVE_FOLDED;
      }
      if (op2.isLongConstant()) {
        long val2 = op2.asLongConstant().value;
        if (val2 == 0L) {
          // TODO: This instruction is actually unreachable.  
          // There will be a LONG_ZERO_CHECK
          // guarding this instruction that will result in an 
          // ArithmeticException.  We
          // should probabbly just remove the LONG_DIV as dead code.
          return UNCHANGED;
        }
        if (op1.isLongConstant()) {
          // BOTH CONSTANTS: FOLD
          long val1 = op1.asLongConstant().value;
          Move.mutate(s, LONG_MOVE, GuardedBinary.getClearResult(s), 
                      LC(val1/val2));
          return MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 1L) {                 // x / 1L == x
            Move.mutate(s, LONG_MOVE, GuardedBinary.getClearResult(s), 
                        GuardedBinary.getClearVal1(s));
            return MOVE_REDUCED;
          }
        }
      }
    }
    return UNCHANGED;
  }
  private static byte longMul(OPT_Instruction s) {
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
          return MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == -1L) {                // x * -1 == -x
            Move.mutate(s, LONG_NEG, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return REDUCED;
          }
          if (val2 == 0L) {                 // x * 0L == 0L
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), LC(0L));
            return MOVE_FOLDED;
          }
          if (val2 == 1L) {                 // x * 1L == x
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return MOVE_REDUCED;
          }
        }
      }
    }
    return UNCHANGED;
  }
  private static byte longNeg(OPT_Instruction s) {
   if (CF_LONG) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isLongConstant()) {
        // CONSTANT: FOLD
        long val = op.asLongConstant().value;
        Move.mutate(s, LONG_MOVE, Unary.getClearResult(s), LC(-val));
        return MOVE_FOLDED;
      }
    }
    return UNCHANGED;
  }
  private static byte longNot(OPT_Instruction s) {
   if (CF_LONG) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isLongConstant()) {
        long val = op.asLongConstant().value;
        // CONSTANT: FOLD
        Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), LC(~val));
        return MOVE_FOLDED;
      }
    }
    return UNCHANGED;
  }
  private static byte longOr(OPT_Instruction s) {
   if (CF_LONG) {
      canonicalizeCommutativeOperator(s);
      OPT_Operand op1 = Binary.getVal1(s);
      OPT_Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x | x == x
        Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                    Binary.getClearVal1(s));
        return op1.isConstant() ? MOVE_FOLDED : MOVE_REDUCED;
      }
      if (op2.isLongConstant()) {
        long val2 = op2.asLongConstant().value;
        if (op1.isLongConstant()) {
          // BOTH CONSTANTS: FOLD
          long val1 = op1.asLongConstant().value;
          Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                      LC(val1 | val2));
          return MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0L) {                 // x | 0L == x
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return MOVE_REDUCED;
          }
          if (val2 == -1L) { // x | -1L == x | 0xff..ff == 0xff..ff == -1L
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), LC(-1L));
            return MOVE_FOLDED;
          }
        }
      }
    }
    return UNCHANGED;
  }
  private static byte longRem(OPT_Instruction s) {
   if (CF_LONG) {
      OPT_Operand op1 = GuardedBinary.getVal1(s);
      OPT_Operand op2 = GuardedBinary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x % x == 0
        Move.mutate(s, LONG_MOVE, GuardedBinary.getClearResult(s), 
                    LC(0));
        return MOVE_FOLDED;
      }
      if (op2.isLongConstant()) {
        long val2 = op2.asLongConstant().value;
        if (val2 == 0L) {
          // TODO: This instruction is actually unreachable.  
          // There will be a LONG_ZERO_CHECK
          // guarding this instruction that will result in an 
          // ArithmeticException.  We
          // should probabbly just remove the LONG_REM as dead code.
          return UNCHANGED;
        }
        if (op1.isLongConstant()) {
          // BOTH CONSTANTS: FOLD
          long val1 = op1.asLongConstant().value;
          Move.mutate(s, LONG_MOVE, GuardedBinary.getClearResult(s), 
                      LC(val1%val2));
          return MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 1L) {                 // x % 1L == 0
            Move.mutate(s, LONG_MOVE, GuardedBinary.getClearResult(s), 
                        LC(0));
            return MOVE_FOLDED;
          }
        }
      }
    }
    return UNCHANGED;
  }
  private static byte longShl(OPT_Instruction s) {
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
          return MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0) {                  // x << 0 == x
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return MOVE_REDUCED;
          }
          if (val2 >= BITS_IN_LONG) {                  // x << 64 == 0
            Move.mutate(s, INT_MOVE, Binary.getClearResult(s), 
                        LC(0));
            return MOVE_FOLDED;
          }
        }
      }
    }
    return UNCHANGED;
  }
  private static byte longShr(OPT_Instruction s) {
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
          return MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0) {                  // x >> 0L == x
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return MOVE_REDUCED;
          }
        }
      }
    }
    return UNCHANGED;
  }
  private static byte longSub(OPT_Instruction s) {
   if (CF_LONG) {
      OPT_Operand op1 = Binary.getVal1(s);
      OPT_Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x - x == 0
        Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                    LC(0));
        return MOVE_FOLDED;
      }
      if (op2.isLongConstant()) {
        long val2 = op2.asLongConstant().value;
        if (op1.isLongConstant()) {
          // BOTH CONSTANTS: FOLD
          long val1 = op1.asLongConstant().value;
          Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), LC(val1-val2));
          return MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0L) {                 // x - 0 == x
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return MOVE_REDUCED;
          }
          //-#if RVM_FOR_64_ADDR
          // x - c = x + -c
          // prefer adds, since some architectures have addi but not subi
          Binary.mutate(s, LONG_ADD, Binary.getClearResult(s), 
                        Binary.getClearVal1(s), LC(-val2));
          return REDUCED;
          //-#endif
        }
      }
    }
    return UNCHANGED;
  }
  private static byte longUshr(OPT_Instruction s) {
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
          return MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == 0) {                  // x >>> 0L == x
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return MOVE_REDUCED;
          }
          if (val2 >= BITS_IN_LONG) {                  // x >>> 64 == 0
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                        LC(0));
            return MOVE_FOLDED;
          }
        }
      }
    }
    return UNCHANGED;
  }
  private static byte longXor(OPT_Instruction s) {
   if (CF_LONG) {
      canonicalizeCommutativeOperator(s);
      OPT_Operand op1 = Binary.getVal1(s);
      OPT_Operand op2 = Binary.getVal2(s);
      if (op1.similar(op2)) {
        // THE SAME OPERAND: x ^ x == 0
        Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                    LC(0));
        return MOVE_FOLDED;
      }
      if (op2.isLongConstant()) {
        long val2 = op2.asLongConstant().value;
        if (op1.isLongConstant()) {
          // BOTH CONSTANTS: FOLD
          long val1 = op1.asLongConstant().value;
          Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                      LC(val1 ^ val2));
          return MOVE_FOLDED;
        } else {
          // ONLY OP2 IS CONSTANT: ATTEMPT TO APPLY AXIOMS
          if (val2 == -1L) {                // x ^ -1L == x ^ 0xff..ff = ~x
            Unary.mutate(s, LONG_NOT, Binary.getClearResult(s), 
                         Binary.getClearVal1(s));
            return REDUCED;
          }
          if (val2 == 0L) {                 // x ^ 0L == x
            Move.mutate(s, LONG_MOVE, Binary.getClearResult(s), 
                        Binary.getClearVal1(s));
            return MOVE_REDUCED;
          }
        }
      }
    }
    return UNCHANGED;
  }
  private static byte floatAdd(OPT_Instruction s) {
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
          return MOVE_FOLDED;
        }
        if (val2 == 0.0f) {
          // x + 0.0 is x (even is x is a Nan).
          Move.mutate(s, FLOAT_MOVE, Binary.getClearResult(s),
                      Binary.getClearVal1(s));
          return MOVE_REDUCED;
        }
      }
    }
    return UNCHANGED;
  }
  private static byte floatCmpg(OPT_Instruction s) {
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
          return MOVE_FOLDED;
        }
      }
    }
    return UNCHANGED;
  }
  private static byte floatCmpl(OPT_Instruction s) {
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
          return MOVE_FOLDED;
        }
      }
    }
    return UNCHANGED;
  }
  private static byte floatDiv(OPT_Instruction s) {
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
          return MOVE_FOLDED;
        }
      }
    }
    return UNCHANGED;
  }
  private static byte floatMul(OPT_Instruction s) {
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
          return MOVE_FOLDED;
        }
        if (val2 == 1.0f) {
          // x * 1.0 is x, even if x is a NaN
          Move.mutate(s, FLOAT_MOVE, Binary.getClearResult(s),
                      Binary.getClearVal1(s));
          return MOVE_REDUCED;
        }
      }
    }
    return UNCHANGED;
  }
  private static byte floatNeg(OPT_Instruction s) {
   if (CF_FLOAT) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isFloatConstant()) {
        // CONSTANT: FOLD
        float val = op.asFloatConstant().value;
        Move.mutate(s, FLOAT_MOVE, Unary.getClearResult(s), FC(-val));
        return MOVE_FOLDED;
      }
    }
    return UNCHANGED;
  }
  private static byte floatRem(OPT_Instruction s) {
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
          return MOVE_FOLDED;
        }
      }
    }
    return UNCHANGED;
  }
  private static byte floatSub(OPT_Instruction s) {
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
          return MOVE_FOLDED;
        }
        if (val2 == 0.0f) {
          // x - 0.0 is x, even if x is a NaN
          Move.mutate(s, FLOAT_MOVE, Binary.getClearResult(s),
                      Binary.getClearVal1(s));
          return MOVE_REDUCED;
        }
      }
    }
    return UNCHANGED;
  }
  private static byte doubleAdd(OPT_Instruction s) {
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
          return MOVE_FOLDED;
        }
        if (val2 == 0.0) {
          // x + 0.0 is x, even if x is a NaN
          Move.mutate(s, DOUBLE_MOVE, Binary.getClearResult(s),
                      Binary.getClearVal1(s));
          return MOVE_REDUCED;
        }
      }
    }
    return UNCHANGED;
  }
  private static byte doubleCmpg(OPT_Instruction s) {
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
          return MOVE_FOLDED;
        }
      }
    }
    return UNCHANGED;
  }
  private static byte doubleCmpl(OPT_Instruction s) {
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
          return MOVE_FOLDED;
        }
      }
    }
    return UNCHANGED;
  }
  private static byte doubleDiv(OPT_Instruction s) {
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
          return MOVE_FOLDED;
        }
      }
    }
    return UNCHANGED;
  }
  private static byte doubleMul(OPT_Instruction s) {
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
          return MOVE_FOLDED;
        }
        if (val2 == 1.0) {
          // x * 1.0 is x even if x is a NaN
          Move.mutate(s, DOUBLE_MOVE, Binary.getClearResult(s),
                      Binary.getClearVal1(s));
          return MOVE_REDUCED;
        }
      }
    }
    return UNCHANGED;
  }
  private static byte doubleNeg(OPT_Instruction s) {
   if (CF_DOUBLE) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isDoubleConstant()) {
        // CONSTANT: FOLD
        double val = op.asDoubleConstant().value;
        Move.mutate(s, DOUBLE_MOVE, Unary.getClearResult(s), DC(-val));
        return MOVE_FOLDED;
      }
    }
    return UNCHANGED;
  }
  private static byte doubleRem(OPT_Instruction s) {
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
          return MOVE_FOLDED;
        }
      }
    }
    return UNCHANGED;
  }
  private static byte doubleSub(OPT_Instruction s) {
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
          return MOVE_FOLDED;
        }
        if (val2 == 0.0) {
          // x - 0.0 is x, even if x is a NaN
          Move.mutate(s, DOUBLE_MOVE, Binary.getClearResult(s),
                      Binary.getClearVal1(s));
          return MOVE_REDUCED;
        }
      }
    }
    return UNCHANGED;
  }
  private static byte double2Float(OPT_Instruction s) {
   if (CF_FLOAT) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isDoubleConstant()) {
        // CONSTANT: FOLD
        double val = op.asDoubleConstant().value;
        Move.mutate(s, FLOAT_MOVE, Unary.getClearResult(s), FC((float)val));
        return MOVE_FOLDED;
      }
    }
    return UNCHANGED;
  }
  private static byte double2Int(OPT_Instruction s) {
   if (CF_INT) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isDoubleConstant()) {
        // CONSTANT: FOLD
        double val = op.asDoubleConstant().value;
        Move.mutate(s, INT_MOVE, Unary.getClearResult(s), IC((int)val));
        return MOVE_FOLDED;
      }
    }
    return UNCHANGED;
  }
  private static byte double2Long(OPT_Instruction s) {
   if (CF_LONG) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isDoubleConstant()) {
        // CONSTANT: FOLD
        double val = op.asDoubleConstant().value;
        Move.mutate(s, LONG_MOVE, Unary.getClearResult(s), LC((long)val));
        return MOVE_FOLDED;
      }
    }
    return UNCHANGED;
  }
  private static byte doubleAsLongBits(OPT_Instruction s) {
   if (CF_LONG) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isDoubleConstant()) {
        // CONSTANT: FOLD
        double val = op.asDoubleConstant().value;
        Move.mutate(s, LONG_MOVE, Unary.getClearResult(s), 
                    LC(Double.doubleToLongBits(val)));
        return MOVE_FOLDED;
      }
    }
    return UNCHANGED;
  }
  private static byte int2Double(OPT_Instruction s) {
   if (CF_DOUBLE) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isIntConstant()) {
        // CONSTANT: FOLD
        int val = op.asIntConstant().value;
        Move.mutate(s, DOUBLE_MOVE, Unary.getClearResult(s), 
                    DC((double)val));
        return MOVE_FOLDED;
      }
    }
    return UNCHANGED;
  }
  private static byte int2Byte(OPT_Instruction s) {
   if (CF_INT) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isIntConstant()) {
        // CONSTANT: FOLD
        int val = op.asIntConstant().value;
        Move.mutate(s, INT_MOVE, Unary.getClearResult(s), IC((byte)val));
        return MOVE_FOLDED;
      }
    }
    return UNCHANGED;
  }
  private static byte int2UShort(OPT_Instruction s) {
   if (CF_INT) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isIntConstant()) {
        // CONSTANT: FOLD
        int val = op.asIntConstant().value;
        Move.mutate(s, INT_MOVE, Unary.getClearResult(s), IC((char)val));
        return MOVE_FOLDED;
      }
    }
    return UNCHANGED;
  }
  private static byte int2Float(OPT_Instruction s) {
   if (CF_FLOAT) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isIntConstant()) {
        // CONSTANT: FOLD
        int val = op.asIntConstant().value;
        Move.mutate(s, FLOAT_MOVE, Unary.getClearResult(s), FC((float)val));
        return MOVE_FOLDED;
      }
    }
    return UNCHANGED;
  }
  private static byte int2Long(OPT_Instruction s) {
   if (CF_LONG) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isIntConstant()) {
        // CONSTANT: FOLD
        int val = op.asIntConstant().value;
        Move.mutate(s, LONG_MOVE, Unary.getClearResult(s), LC((long)val));
        return MOVE_FOLDED;
      }
    }
    return UNCHANGED;
  }
  private static byte int2AddrSigExt(OPT_Instruction s) {
   if (CF_ADDR) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isIntConstant()) {
        // CONSTANT: FOLD
        int val = op.asIntConstant().value;
        Move.mutate(s, REF_MOVE, Unary.getClearResult(s), AC(Address.fromIntSignExtend(val)));
        return MOVE_FOLDED;
      }
    }
    return UNCHANGED;
  }
  private static byte int2AddrZerExt(OPT_Instruction s) {
   if (CF_ADDR) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isIntConstant()) {
        // CONSTANT: FOLD
        int val = op.asIntConstant().value;
        Move.mutate(s, REF_MOVE, Unary.getClearResult(s), AC(Address.fromIntZeroExtend(val)));
        return MOVE_FOLDED;
      }
    }
    return UNCHANGED;
  }


  //-#if RVM_FOR_64_ADDR
  private static byte long2Addr(OPT_Instruction s) {
    if (CF_ADDR) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isLongConstant()) {
        // CONSTANT: FOLD
        long val = op.asLongConstant().value;
        Move.mutate(s, REF_MOVE, Unary.getClearResult(s), AC(Address.fromLong(val)));
        return MOVE_FOLDED;
      }
    }
    return UNCHANGED;
  }
  //-#endif
  private static byte int2Short(OPT_Instruction s) {
    if (CF_INT) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isIntConstant()) {
        // CONSTANT: FOLD
        int val = op.asIntConstant().value;
        Move.mutate(s, INT_MOVE, Unary.getClearResult(s), IC((short)val));
        return MOVE_FOLDED;
      }
    }
    return UNCHANGED;
  }
  private static byte intBitsAsFloat(OPT_Instruction s) {
    if (CF_FLOAT) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isIntConstant()) {
        // CONSTANT: FOLD
        int val = op.asIntConstant().value;
        Move.mutate(s, FLOAT_MOVE, Unary.getClearResult(s), 
                    FC(Float.intBitsToFloat(val)));
        return MOVE_FOLDED;
      }
    }
    return UNCHANGED;
  }
  private static byte addr2Int(OPT_Instruction s) {
    if (CF_INT) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isAddressConstant()) {
        // CONSTANT: FOLD
        Address val = op.asAddressConstant().value;
        Move.mutate(s, INT_MOVE, Unary.getClearResult(s), IC(val.toInt()));
        return MOVE_FOLDED;
      }
    }
    return UNCHANGED;
  }
  private static byte addr2Long(OPT_Instruction s) {
    if (CF_LONG) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isAddressConstant()) {
        // CONSTANT: FOLD
        Address val = op.asAddressConstant().value;
        Move.mutate(s, LONG_MOVE, Unary.getClearResult(s), LC(val.toLong()));
        return MOVE_FOLDED;
      }
    }
    return UNCHANGED;
  }
  private static byte float2Double(OPT_Instruction s) {
    if (CF_DOUBLE) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isFloatConstant()) {
        // CONSTANT: FOLD
        float val = op.asFloatConstant().value;
        Move.mutate(s, DOUBLE_MOVE, Unary.getClearResult(s), 
                    DC((double)val));
        return MOVE_FOLDED;
      }
    }
    return UNCHANGED;
  }
  private static byte float2Int(OPT_Instruction s) {
    if (CF_INT) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isFloatConstant()) {
        // CONSTANT: FOLD
        float val = op.asFloatConstant().value;
        Move.mutate(s, INT_MOVE, Unary.getClearResult(s), IC((int)val));
        return MOVE_FOLDED;
      }
    }
    return UNCHANGED;
  }
  private static byte float2Long(OPT_Instruction s) {
    if (CF_LONG) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isFloatConstant()) {
        // CONSTANT: FOLD
        float val = op.asFloatConstant().value;
        Move.mutate(s, LONG_MOVE, Unary.getClearResult(s), LC((long)val));
        return MOVE_FOLDED;
      }
    }
    return UNCHANGED;
  }
  private static byte floatAsIntBits(OPT_Instruction s) {
    if (CF_INT) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isFloatConstant()) {
        // CONSTANT: FOLD
        float val = op.asFloatConstant().value;
        Move.mutate(s, INT_MOVE, Unary.getClearResult(s), 
                    IC(Float.floatToIntBits(val)));
        return MOVE_FOLDED;
      }
    }
    return UNCHANGED;
  }
  private static byte long2Float(OPT_Instruction s) {
    if (CF_FLOAT) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isLongConstant()) {
        // CONSTANT: FOLD
        long val = op.asLongConstant().value;
        Move.mutate(s, FLOAT_MOVE, Unary.getClearResult(s), FC((float)val));
        return MOVE_FOLDED;
      }
    }
    return UNCHANGED;
  }
  private static byte long2Int(OPT_Instruction s) {
    if (CF_INT) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isLongConstant()) {
        // CONSTANT: FOLD
        long val = op.asLongConstant().value;
        Move.mutate(s, INT_MOVE, Unary.getClearResult(s), IC((int)val));
        return MOVE_FOLDED;
      }
    }
    return UNCHANGED;
  }
  private static byte long2Double(OPT_Instruction s) {
    if (CF_DOUBLE) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isLongConstant()) {
        // CONSTANT: FOLD
        long val = op.asLongConstant().value;
        Move.mutate(s, DOUBLE_MOVE, Unary.getClearResult(s), 
                    DC((double)val));
        return MOVE_FOLDED;
      }
    }
    return UNCHANGED;
  }
  private static byte longBitsAsDouble(OPT_Instruction s) {
    if (CF_DOUBLE) {
      OPT_Operand op = Unary.getVal(s);
      if (op.isLongConstant()) {
        // CONSTANT: FOLD
        long val = op.asLongConstant().value;
        Move.mutate(s, DOUBLE_MOVE, Unary.getClearResult(s), 
                    DC(Double.longBitsToDouble(val)));
        return MOVE_FOLDED;
      }
    }
    return UNCHANGED;
  }
  private static byte arrayLength(OPT_Instruction s) {
    if (CF_FIELDS) {
      OPT_Operand op = GuardedUnary.getVal(s);
      if(op.isObjectConstant()) {
        int length = Array.getLength(op.asObjectConstant().value);
        Move.mutate(s, INT_MOVE, GuardedUnary.getClearResult(s),
                    IC(length));
        return MOVE_REDUCED;          
      } else if (op.isNullConstant()) {
        // TODO: this arraylength operation is junk so destroy
        return UNCHANGED;
      }
    }
    return UNCHANGED;
  }
  private static byte boundsCheck(OPT_Instruction s) {
    if (CF_FIELDS) {
      OPT_Operand ref = BoundsCheck.getRef(s);
      OPT_Operand index = BoundsCheck.getIndex(s);
      if (ref.isNullConstant()) {
        Trap.mutate(s, TRAP, NullCheck.getClearGuardResult(s),
                    OPT_TrapCodeOperand.NullPtr());
        return TRAP_REDUCED;
      } else if(index.isIntConstant()) {
        int indexAsInt = index.asIntConstant().value;
        if (indexAsInt < 0) {
          Trap.mutate(s, TRAP, BoundsCheck.getClearGuardResult(s), OPT_TrapCodeOperand.ArrayBounds());
          return TRAP_REDUCED;
        } else if(ref.isConstant()) {
          int refLength = Array.getLength(ref.asObjectConstant().value);
          if(indexAsInt < refLength) {
            Move.mutate(s, GUARD_MOVE, BoundsCheck.getClearGuardResult(s),
                        BoundsCheck.getClearGuard(s));
            return MOVE_REDUCED;
          }
          else {
            Trap.mutate(s, TRAP, BoundsCheck.getClearGuardResult(s), OPT_TrapCodeOperand.ArrayBounds());
            return TRAP_REDUCED;
          }
        }
      }
    }
    return UNCHANGED;
  }
  private static byte call(OPT_Instruction s) {
    if (CF_FIELDS) {
      OPT_MethodOperand methOp = Call.getMethod(s);
      if ((methOp != null) && methOp.isVirtual() && !methOp.hasPreciseTarget()) {
        OPT_Operand calleeThis = Call.getParam(s, 0);
        if (calleeThis.isNullConstant()) {
          Trap.mutate(s, TRAP, NullCheck.getClearGuardResult(s),
                      OPT_TrapCodeOperand.NullPtr());
          return TRAP_REDUCED;
        }
        else if(calleeThis.isConstant() || calleeThis.asRegister().isPreciseType()) {
          VM_TypeReference calleeClass = calleeThis.getType();
          if (calleeClass.isResolved()) {
            methOp.refine(calleeClass.peekResolvedType());
            return REDUCED;
          }
        }
      }
    }
    return UNCHANGED;
  }
  private static byte getField(OPT_Instruction s) {
    if (CF_FIELDS) {
      OPT_Operand ref = GetField.getRef(s);
      if (ref.isNullConstant()) {
        Trap.mutate(s, TRAP, NullCheck.getClearGuardResult(s),
                    OPT_TrapCodeOperand.NullPtr());
        return TRAP_REDUCED;
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
          return MOVE_REDUCED;
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
    return UNCHANGED;
  }
  private static byte getObjTib(OPT_Instruction s) {
    if (CF_TIB) {
      OPT_Operand op = GuardedUnary.getVal(s);
      if (op.isNullConstant()) {
        Trap.mutate(s, TRAP, NullCheck.getClearGuardResult(s),
                    OPT_TrapCodeOperand.NullPtr());
        return TRAP_REDUCED;
      } else if (op.isConstant()) {
        try{
          // NB as the operand is final it must already have been
          // resolved.
          VM_Type type = op.getType().resolve();
          Move.mutate(s, REF_MOVE, GuardedUnary.getClearResult(s),
                      new OPT_TIBConstantOperand(type));
          return MOVE_REDUCED;
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
        }
      }
    }
    return UNCHANGED;
  }
  private static byte getClassTib(OPT_Instruction s) {
    if (CF_TIB) {
      OPT_TypeOperand typeOp = Unary.getVal(s).asType();
      if(typeOp.getTypeRef().isResolved()) {
        Move.mutate(s, REF_MOVE,
                    Unary.getClearResult(s),
                    new OPT_TIBConstantOperand(typeOp.getTypeRef().peekResolvedType()));
        return MOVE_REDUCED;
      }
    }
    return UNCHANGED;
  }
  private static byte getTypeFromTib(OPT_Instruction s) {
    if (CF_TIB) {
      OPT_Operand tibOp = Unary.getVal(s);
      if(tibOp.isTIBConstant()) {
        OPT_TIBConstantOperand tib = tibOp.asTIBConstant();
        Move.mutate(s, REF_MOVE,
                    Unary.getClearResult(s),
                    new OPT_ObjectConstantOperand(tib.value, Offset.zero()));
        return MOVE_REDUCED;
      }
    }
    return UNCHANGED;
  }
  private static byte getArrayElementTibFromTib(OPT_Instruction s) {
    if (CF_TIB) {
      OPT_Operand tibOp = Unary.getVal(s);
      if(tibOp.isTIBConstant()) {
        OPT_TIBConstantOperand tib = tibOp.asTIBConstant();
        Move.mutate(s, REF_MOVE,
                    Unary.getClearResult(s),
                    new OPT_TIBConstantOperand(tib.value.asArray().getElementType()));
        return MOVE_REDUCED;
      }
    }
    return UNCHANGED;
  }
  private static byte getSuperclassIdsFromTib(OPT_Instruction s) {
    if (CF_TIB) {
      OPT_Operand tibOp = Unary.getVal(s);
      if(tibOp.isTIBConstant()) {
        OPT_TIBConstantOperand tib = tibOp.asTIBConstant();
        Move.mutate(s, REF_MOVE,
                    Load.getClearResult(s),
                    new OPT_ObjectConstantOperand(tib.value.getSuperclassIds(), Offset.zero()));
        return MOVE_REDUCED;
      }
    }
    return UNCHANGED;
  }
  private static byte getDoesImplementFromTib(OPT_Instruction s) {
    if (CF_TIB) {
      OPT_Operand tibOp = Unary.getVal(s);
      if(tibOp.isTIBConstant()) {
        OPT_TIBConstantOperand tib = tibOp.asTIBConstant();
        Move.mutate(s, REF_MOVE,
                    Load.getClearResult(s),
                    new OPT_ObjectConstantOperand(tib.value.getDoesImplement(), Offset.zero()));
        return MOVE_REDUCED;
      }
    }
    return UNCHANGED;
  }
  private static byte refLoad(OPT_Instruction s) {
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
          Object tibArray[] = tib.value.getTypeInformationBlock();
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
              return UNCHANGED;
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
          return MOVE_REDUCED;
        }
      }
    }
    return UNCHANGED;
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
}
