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
package org.jikesrvm.compilers.opt.bc2ir;

import static org.jikesrvm.compilers.opt.ir.IRTools.AC;
import static org.jikesrvm.compilers.opt.ir.Operators.ADDR_2INT;
import static org.jikesrvm.compilers.opt.ir.Operators.ADDR_2LONG;
import static org.jikesrvm.compilers.opt.ir.Operators.ARRAYLENGTH;
import static org.jikesrvm.compilers.opt.ir.Operators.ATTEMPT_ADDR;
import static org.jikesrvm.compilers.opt.ir.Operators.ATTEMPT_INT;
import static org.jikesrvm.compilers.opt.ir.Operators.ATTEMPT_LONG;
import static org.jikesrvm.compilers.opt.ir.Operators.BOOLEAN_CMP_ADDR;
import static org.jikesrvm.compilers.opt.ir.Operators.BYTE_LOAD;
import static org.jikesrvm.compilers.opt.ir.Operators.BYTE_STORE;
import static org.jikesrvm.compilers.opt.ir.Operators.CALL;
import static org.jikesrvm.compilers.opt.ir.Operators.DOUBLE_AS_LONG_BITS;
import static org.jikesrvm.compilers.opt.ir.Operators.DOUBLE_LOAD;
import static org.jikesrvm.compilers.opt.ir.Operators.DOUBLE_SQRT;
import static org.jikesrvm.compilers.opt.ir.Operators.DOUBLE_STORE;
import static org.jikesrvm.compilers.opt.ir.Operators.FENCE;
import static org.jikesrvm.compilers.opt.ir.Operators.FLOAT_AS_INT_BITS;
import static org.jikesrvm.compilers.opt.ir.Operators.FLOAT_LOAD;
import static org.jikesrvm.compilers.opt.ir.Operators.FLOAT_SQRT;
import static org.jikesrvm.compilers.opt.ir.Operators.FLOAT_STORE;
import static org.jikesrvm.compilers.opt.ir.Operators.FRAMESIZE;
import static org.jikesrvm.compilers.opt.ir.Operators.GET_OBJ_TIB;
import static org.jikesrvm.compilers.opt.ir.Operators.GET_TIME_BASE;
import static org.jikesrvm.compilers.opt.ir.Operators.GET_TYPE_FROM_TIB;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_2ADDRSigExt;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_2ADDRZerExt;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_ADD;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_BITS_AS_FLOAT;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_LOAD;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_SHL;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_STORE;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_2ADDR;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_BITS_AS_DOUBLE;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_LOAD;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_STORE;
import static org.jikesrvm.compilers.opt.ir.Operators.PREPARE_ADDR;
import static org.jikesrvm.compilers.opt.ir.Operators.PREPARE_INT;
import static org.jikesrvm.compilers.opt.ir.Operators.PREPARE_LONG;
import static org.jikesrvm.compilers.opt.ir.Operators.READ_CEILING;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_ADD;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_AND;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_LOAD;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_NOT;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_OR;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_SHL;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_SHR;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_STORE;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_SUB;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_USHR;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_XOR;
import static org.jikesrvm.compilers.opt.ir.Operators.SHORT_LOAD;
import static org.jikesrvm.compilers.opt.ir.Operators.SHORT_STORE;
import static org.jikesrvm.compilers.opt.ir.Operators.SYSCALL;
import static org.jikesrvm.compilers.opt.ir.Operators.UBYTE_LOAD;
import static org.jikesrvm.compilers.opt.ir.Operators.USHORT_LOAD;
import static org.jikesrvm.compilers.opt.ir.Operators.WRITE_FLOOR;
import static org.jikesrvm.objectmodel.TIBLayoutConstants.TIB_FIRST_SPECIALIZED_METHOD_INDEX;
import static org.jikesrvm.runtime.JavaSizeConstants.LOG_BYTES_IN_INT;
import static org.jikesrvm.runtime.UnboxedSizeConstants.LOG_BYTES_IN_ADDRESS;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.MemberReference;
import org.jikesrvm.classloader.MethodReference;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.MagicNotImplementedException;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.Simplifier;
import org.jikesrvm.compilers.opt.ir.Attempt;
import org.jikesrvm.compilers.opt.ir.Binary;
import org.jikesrvm.compilers.opt.ir.BooleanCmp;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.Empty;
import org.jikesrvm.compilers.opt.ir.GuardedUnary;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Load;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.Nullary;
import org.jikesrvm.compilers.opt.ir.Operator;
import org.jikesrvm.compilers.opt.ir.Prepare;
import org.jikesrvm.compilers.opt.ir.Store;
import org.jikesrvm.compilers.opt.ir.Unary;
import org.jikesrvm.compilers.opt.ir.operand.BranchProfileOperand;
import org.jikesrvm.compilers.opt.ir.operand.ConditionOperand;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.LocationOperand;
import org.jikesrvm.compilers.opt.ir.operand.MethodOperand;
import org.jikesrvm.compilers.opt.ir.operand.ObjectConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.TrueGuardOperand;
import org.jikesrvm.runtime.ArchEntrypoints;
import org.jikesrvm.runtime.MagicNames;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * This class implements the non-machine-specific magics for the opt compiler.
 * By non-machine-specific we mean that the IR generated to implement the magic
 * is independent of the target-architecture.
 * It does not mean that the eventual MIR that implements the magic
 * won't differ from architecture to architecture.
 */
public class GenerateMagic {

  /**
   * "Semantic inlining" of methods of the Magic class.
   * Based on the methodName, generate a sequence of opt instructions
   * that implement the magic, updating the expression stack as necessary.
   *
   * @param bc2ir the bc2ir object that is generating the
   *              ir containing this magic
   * @param gc must be bc2ir.gc
   * @param meth the RVMMethod that is the magic method
   * @return {@code true} if and only if magic was generated
   */
  static boolean generateMagic(BC2IR bc2ir, GenerationContext gc, MethodReference meth)
      throws MagicNotImplementedException {

    if (gc.getMethod().hasNoInlinePragma()) gc.forceFrameAllocation();

    Atom methodName = meth.getName();

    boolean address = (meth.getType() == TypeReference.Address);

    // Address magic
    TypeReference[] types = meth.getParameterTypes();
    TypeReference returnType = meth.getReturnType();

    if (address && isLoad(methodName)) {
      // LOAD
      Operand offset = (types.length == 0) ? AC(Address.zero()) : bc2ir.popAddress();
      Operand base = bc2ir.popAddress();
      RegisterOperand result = gc.getTemps().makeTemp(returnType);
      bc2ir.appendInstruction(Load.create(getOperator(returnType, LOAD_OP), result, base, offset, null));
      bc2ir.push(result.copyD2U(), returnType);

    } else if (address && isPrepare(methodName)) {
      // PREPARE
      Operand offset = (types.length == 0) ? AC(Address.zero()) : bc2ir.popAddress();
      Operand base = bc2ir.popAddress();
      RegisterOperand result = gc.getTemps().makeTemp(returnType);
      bc2ir.appendInstruction(Prepare.create(getOperator(returnType, PREPARE_OP), result, base, offset, null));
      bc2ir.push(result.copyD2U(), returnType);

    } else if (address && methodName == MagicNames.attempt) {
      // ATTEMPT
      TypeReference attemptType = types[0];

      Operand offset = (types.length == 2) ? AC(Address.zero()) : bc2ir.popAddress();

      Operand newVal = bc2ir.pop();
      Operand oldVal = bc2ir.pop();
      Operand base = bc2ir.popAddress();
      RegisterOperand test = gc.getTemps().makeTempInt();
      bc2ir.appendInstruction(Attempt.create(getOperator(attemptType, ATTEMPT_OP),
                                             test,
                                             base,
                                             offset,
                                             oldVal,
                                             newVal,
                                             null));
      bc2ir.push(test.copyD2U(), returnType);

    } else if (address && methodName == MagicNames.store) {
      // STORE
      TypeReference storeType = types[0];

      Operand offset = (types.length == 1) ? AC(Address.zero()) : bc2ir.popAddress();

      Operand val = bc2ir.pop(storeType);
      Operand base = bc2ir.popAddress();
      bc2ir.appendInstruction(Store.create(getOperator(storeType, STORE_OP), val, base, offset, null));

    } else if (methodName == MagicNames.getThreadRegister) {
      RegisterOperand rop = gc.getTemps().makeTROp();
      bc2ir.markGuardlessNonNull(rop);
      bc2ir.push(rop);
    } else if (methodName == MagicNames.setThreadRegister) {
      Operand val = bc2ir.popRef();
      if (val instanceof RegisterOperand) {
        bc2ir.appendInstruction(Move.create(REF_MOVE, gc.getTemps().makeTROp(), val));
      } else {
        String msg = " Unexpected operand Magic.setThreadRegister";
        throw MagicNotImplementedException.UNEXPECTED(msg);
      }
    } else if (methodName == MagicNames.addressArrayCreate) {
      Instruction s = bc2ir.generateAnewarray(null, meth.getType().getArrayElementType());
      bc2ir.appendInstruction(s);
    } else if (methodName == MagicNames.addressArrayLength) {
      Operand op1 = bc2ir.pop();
      bc2ir.clearCurrentGuard();
      if (bc2ir.do_NullCheck(op1)) {
        return true;
      }
      RegisterOperand t = gc.getTemps().makeTempInt();
      Instruction s = GuardedUnary.create(ARRAYLENGTH, t, op1, bc2ir.getCurrentGuard());
      bc2ir.push(t.copyD2U());
      bc2ir.appendInstruction(s);
    } else if (methodName == MagicNames.addressArrayGet) {
      TypeReference elementType = meth.getReturnType();
      Operand index = bc2ir.popInt();
      Operand ref = bc2ir.popRef();
      RegisterOperand offsetI = gc.getTemps().makeTempInt();
      RegisterOperand offset = gc.getTemps().makeTempOffset();
      RegisterOperand result;
      if (meth.getType().isCodeArrayType()) {
        if (VM.BuildForIA32) {
          result = gc.getTemps().makeTemp(TypeReference.Byte);
          bc2ir.appendInstruction(Load.create(BYTE_LOAD,
                                              result,
                                              ref,
                                              index,
                                              new LocationOperand(elementType),
                                              new TrueGuardOperand()));
        } else if (VM.BuildForPowerPC) {
          result = gc.getTemps().makeTemp(TypeReference.Int);
          bc2ir.appendInstruction(Binary.create(INT_SHL, offsetI, index, new IntConstantOperand(LOG_BYTES_IN_INT)));
          bc2ir.appendInstruction(Unary.create(INT_2ADDRZerExt, offset, offsetI.copy()));
          bc2ir.appendInstruction(Load.create(INT_LOAD,
                                              result,
                                              ref,
                                              offset.copy(),
                                              new LocationOperand(elementType),
                                              new TrueGuardOperand()));
        }
      } else {
        result = gc.getTemps().makeTemp(elementType);
        bc2ir.appendInstruction(Binary.create(INT_SHL,
                                              offsetI,
                                              index,
                                              new IntConstantOperand(LOG_BYTES_IN_ADDRESS)));
        bc2ir.appendInstruction(Unary.create(INT_2ADDRZerExt, offset, offsetI.copy()));
        bc2ir.appendInstruction(Load.create(REF_LOAD,
                                            result,
                                            ref,
                                            offset.copy(),
                                            new LocationOperand(elementType),
                                            new TrueGuardOperand()));
      }
      bc2ir.push(result.copyD2U());
    } else if (methodName == MagicNames.addressArraySet) {
      TypeReference elementType = meth.getParameterTypes()[1];
      Operand val = bc2ir.pop();
      Operand index = bc2ir.popInt();
      Operand ref = bc2ir.popRef();
      RegisterOperand offsetI = gc.getTemps().makeTempInt();
      RegisterOperand offset = gc.getTemps().makeTempOffset();
      if (meth.getType().isCodeArrayType()) {
        if (VM.BuildForIA32) {
          bc2ir.appendInstruction(Store.create(BYTE_STORE,
                                               val,
                                               ref,
                                               index,
                                               new LocationOperand(elementType),
                                               new TrueGuardOperand()));
        } else if (VM.BuildForPowerPC) {
          bc2ir.appendInstruction(Binary.create(INT_SHL, offsetI, index, new IntConstantOperand(LOG_BYTES_IN_INT)));
          bc2ir.appendInstruction(Unary.create(INT_2ADDRZerExt, offset, offsetI.copy()));
          bc2ir.appendInstruction(Store.create(INT_STORE,
                                               val,
                                               ref,
                                               offset.copy(),
                                               new LocationOperand(elementType),
                                               new TrueGuardOperand()));
        }
      } else {
        bc2ir.appendInstruction(Binary.create(INT_SHL,
                                              offsetI,
                                              index,
                                              new IntConstantOperand(LOG_BYTES_IN_ADDRESS)));
        bc2ir.appendInstruction(Unary.create(INT_2ADDRZerExt, offset, offsetI.copy()));
        bc2ir.appendInstruction(Store.create(REF_STORE,
                                             val,
                                             ref,
                                             offset.copy(),
                                             new LocationOperand(elementType),
                                             new TrueGuardOperand()));
      }
    } else if (methodName == MagicNames.getIntAtOffset) {
      Operand offset = bc2ir.popAddress();
      Operand object = bc2ir.popRef();
      RegisterOperand val = gc.getTemps().makeTempInt();
      bc2ir.appendInstruction(Load.create(INT_LOAD, val, object, offset, null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == MagicNames.setIntAtOffset) {
      LocationOperand loc = null;
      if (meth.getParameterTypes().length == 4) {
        loc = mapToMetadata(bc2ir.popInt());
      }
      Operand val = bc2ir.popInt();
      Operand offset = bc2ir.popAddress();
      Operand object = bc2ir.popRef();
      bc2ir.appendInstruction(Store.create(INT_STORE, val, object, offset, loc));
    } else if (methodName == MagicNames.getFloatAtOffset) {
      Operand offset = bc2ir.popAddress();
      Operand object = bc2ir.popRef();
      RegisterOperand val = gc.getTemps().makeTempFloat();
      bc2ir.appendInstruction(Load.create(FLOAT_LOAD, val, object, offset, null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == MagicNames.setFloatAtOffset) {
      LocationOperand loc = null;
      if (meth.getParameterTypes().length == 4) {
        loc = mapToMetadata(bc2ir.popInt());
      }
      Operand val = bc2ir.popFloat();
      Operand offset = bc2ir.popAddress();
      Operand object = bc2ir.popRef();
      bc2ir.appendInstruction(Store.create(FLOAT_STORE, val, object, offset, loc));
    } else if (methodName == MagicNames.getWordAtOffset) {
      LocationOperand loc = null;
      if (meth.getParameterTypes().length == 3) {
        loc = mapToMetadata(bc2ir.popInt());
      }
      Operand offset = bc2ir.popAddress();
      Operand object = bc2ir.popRef();
      RegisterOperand val = gc.getTemps().makeTemp(TypeReference.Word);
      bc2ir.appendInstruction(Load.create(REF_LOAD, val, object, offset, loc));
      bc2ir.push(val.copyD2U());
    } else if (methodName == MagicNames.getAddressAtOffset) {
      LocationOperand loc = null;
      if (meth.getParameterTypes().length == 3) {
        loc = mapToMetadata(bc2ir.popInt());
      }
      Operand offset = bc2ir.popAddress();
      Operand object = bc2ir.popRef();
      RegisterOperand val = gc.getTemps().makeTemp(TypeReference.Address);
      bc2ir.appendInstruction(Load.create(REF_LOAD, val, object, offset, loc));
      bc2ir.push(val.copyD2U());
    } else if (methodName == MagicNames.getExtentAtOffset) {
      LocationOperand loc = null;
      if (meth.getParameterTypes().length == 3) {
        loc = mapToMetadata(bc2ir.popInt());
      }
      Operand offset = bc2ir.popAddress();
      Operand object = bc2ir.popRef();
      RegisterOperand val = gc.getTemps().makeTemp(TypeReference.Extent);
      bc2ir.appendInstruction(Load.create(REF_LOAD, val, object, offset, loc));
      bc2ir.push(val.copyD2U());
    } else if (methodName == MagicNames.getOffsetAtOffset) {
      LocationOperand loc = null;
      if (meth.getParameterTypes().length == 3) {
        loc = mapToMetadata(bc2ir.popInt());
      }
      Operand offset = bc2ir.popAddress();
      Operand object = bc2ir.popRef();
      RegisterOperand val = gc.getTemps().makeTemp(TypeReference.Offset);
      bc2ir.appendInstruction(Load.create(REF_LOAD, val, object, offset, loc));
      bc2ir.push(val.copyD2U());
    } else if (methodName == MagicNames.setWordAtOffset ||
        methodName == MagicNames.setAddressAtOffset ||
        methodName == MagicNames.setOffsetAtOffset ||
        methodName == MagicNames.setExtentAtOffset) {
      LocationOperand loc = null;
      if (meth.getParameterTypes().length == 4) {
        loc = mapToMetadata(bc2ir.popInt());
      }
      Operand val = bc2ir.popRef();
      Operand offset = bc2ir.popAddress();
      Operand object = bc2ir.popRef();
      bc2ir.appendInstruction(Store.create(REF_STORE, val, object, offset, loc));
    } else if (methodName == MagicNames.getLongAtOffset) {
      Operand offset = bc2ir.popAddress();
      Operand object = bc2ir.popRef();
      RegisterOperand val = gc.getTemps().makeTempLong();
      bc2ir.appendInstruction(Load.create(LONG_LOAD, val, object, offset, null));
      bc2ir.pushDual(val.copyD2U());
    } else if (methodName == MagicNames.setLongAtOffset) {
      LocationOperand loc = null;
      if (meth.getParameterTypes().length == 4) {
        loc = mapToMetadata(bc2ir.popInt());
      }
      Operand val = bc2ir.popLong();
      Operand offset = bc2ir.popAddress();
      Operand object = bc2ir.popRef();
      bc2ir.appendInstruction(Store.create(LONG_STORE, val, object, offset, loc));
    } else if (methodName == MagicNames.getDoubleAtOffset) {
      Operand offset = bc2ir.popAddress();
      Operand object = bc2ir.popRef();
      RegisterOperand val = gc.getTemps().makeTempDouble();
      bc2ir.appendInstruction(Load.create(DOUBLE_LOAD, val, object, offset, null));
      bc2ir.pushDual(val.copyD2U());
    } else if (methodName == MagicNames.setDoubleAtOffset) {
      LocationOperand loc = null;
      if (meth.getParameterTypes().length == 4) {
        loc = mapToMetadata(bc2ir.popInt());
      }
      Operand val = bc2ir.popDouble();
      Operand offset = bc2ir.popAddress();
      Operand object = bc2ir.popRef();
      bc2ir.appendInstruction(Store.create(DOUBLE_STORE, val, object, offset, loc));
    } else if (methodName == MagicNames.getObjectAtOffset) {
      LocationOperand loc = null;
      if (meth.getParameterTypes().length == 3) {
        loc = mapToMetadata(bc2ir.popInt());
      }
      Operand offset = bc2ir.popAddress();
      Operand object = bc2ir.popRef();
      RegisterOperand val = gc.getTemps().makeTemp(TypeReference.JavaLangObject);
      bc2ir.appendInstruction(Load.create(REF_LOAD, val, object, offset, loc));
      bc2ir.push(val.copyD2U());
    } else if (methodName == MagicNames.getTIBAtOffset) {
      Operand offset = bc2ir.popAddress();
      Operand object = bc2ir.popRef();
      RegisterOperand val = gc.getTemps().makeTemp(TypeReference.TIB);
      bc2ir.appendInstruction(Load.create(REF_LOAD, val, object, offset, null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == MagicNames.setObjectAtOffset) {
      LocationOperand loc = null;
      if (meth.getParameterTypes().length == 4) {
        loc = mapToMetadata(bc2ir.popInt());
      }
      Operand val = bc2ir.popRef();
      Operand offset = bc2ir.popAddress();
      Operand object = bc2ir.popRef();
      bc2ir.appendInstruction(Store.create(REF_STORE, val, object, offset, loc));
    } else if (methodName == MagicNames.getByteAtOffset) {
      Operand offset = bc2ir.popAddress();
      Operand object = bc2ir.popRef();
      RegisterOperand val = gc.getTemps().makeTemp(TypeReference.Byte);
      bc2ir.appendInstruction(Load.create(BYTE_LOAD, val, object, offset, null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == MagicNames.getUnsignedByteAtOffset) {
      Operand offset = bc2ir.popAddress();
      Operand object = bc2ir.popRef();
      RegisterOperand val = gc.getTemps().makeTemp(TypeReference.Byte);
      bc2ir.appendInstruction(Load.create(UBYTE_LOAD, val, object, offset, null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == MagicNames.setByteAtOffset || methodName == MagicNames.setBooleanAtOffset) {
      LocationOperand loc = null;
      if (meth.getParameterTypes().length == 4) {
        loc = mapToMetadata(bc2ir.popInt());
      }
      Operand val = bc2ir.popInt();
      Operand offset = bc2ir.popAddress();
      Operand object = bc2ir.popRef();
      bc2ir.appendInstruction(Store.create(BYTE_STORE, val, object, offset, loc));
    } else if (methodName == MagicNames.getShortAtOffset) {
      Operand offset = bc2ir.popAddress();
      Operand object = bc2ir.popRef();
      RegisterOperand val = gc.getTemps().makeTemp(TypeReference.Char);
      bc2ir.appendInstruction(Load.create(SHORT_LOAD, val, object, offset, null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == MagicNames.getCharAtOffset) {
      Operand offset = bc2ir.popAddress();
      Operand object = bc2ir.popRef();
      RegisterOperand val = gc.getTemps().makeTemp(TypeReference.Char);
      bc2ir.appendInstruction(Load.create(USHORT_LOAD, val, object, offset, null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == MagicNames.setCharAtOffset || methodName == MagicNames.setShortAtOffset) {
      LocationOperand loc = null;
      if (meth.getParameterTypes().length == 4) {
        loc = mapToMetadata(bc2ir.popInt());
      }
      Operand val = bc2ir.popInt();
      Operand offset = bc2ir.popAddress();
      Operand object = bc2ir.popRef();
      bc2ir.appendInstruction(Store.create(SHORT_STORE, val, object, offset, loc));
    } else if (methodName == MagicNames.getMemoryInt) {
      Operand memAddr = bc2ir.popAddress();
      RegisterOperand val = gc.getTemps().makeTempInt();
      bc2ir.appendInstruction(Load.create(INT_LOAD, val, memAddr, AC(Offset.zero()), null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == MagicNames.getMemoryWord) {
      Operand memAddr = bc2ir.popAddress();
      RegisterOperand val = gc.getTemps().makeTemp(TypeReference.Word);
      bc2ir.appendInstruction(Load.create(REF_LOAD, val, memAddr, AC(Offset.zero()), null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == MagicNames.getMemoryAddress) {
      Operand memAddr = bc2ir.popAddress();
      RegisterOperand val = gc.getTemps().makeTemp(TypeReference.Address);
      bc2ir.appendInstruction(Load.create(REF_LOAD, val, memAddr, AC(Offset.zero()), null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == MagicNames.setMemoryInt) {
      Operand val = bc2ir.popInt();
      Operand memAddr = bc2ir.popAddress();
      bc2ir.appendInstruction(Store.create(INT_STORE,
                                           val,
                                           memAddr,
                                           AC(Offset.zero()),
                                           null));
    } else if (methodName == MagicNames.setMemoryWord) {
      Operand val = bc2ir.popRef();
      Operand memAddr = bc2ir.popAddress();
      bc2ir.appendInstruction(Store.create(REF_STORE,
                                           val,
                                           memAddr,
                                           AC(Offset.zero()),
                                           null));
    } else if (meth.isSysCall()) {
      // All methods of SysCall have the following signature:
      // callNAME(Address functionAddress, <var args to pass via native calling convention>)
      // With 64 bit PowerPC ELF ABI, functionAddress points to the function descriptor
      TypeReference[] args = meth.getParameterTypes();
      Instruction call = Call.create(SYSCALL, null, null, null, null, args.length - 1);
      for (int i = args.length - 1; i >= 1; i--) {
        Call.setParam(call, i - 1, bc2ir.pop(args[i]));
      }
      Operand functionAddress = bc2ir.pop(args[0]);
      Call.setAddress(call, functionAddress);
      if (!returnType.isVoidType()) {
        RegisterOperand op0 = gc.getTemps().makeTemp(returnType);
        Call.setResult(call, op0);
        bc2ir.push(op0.copyD2U(), returnType);
      }
      Call.setMethod(call, MethodOperand.STATIC(meth, meth.peekResolvedMethod()));
      bc2ir.appendInstruction(call);
    } else if (meth.isSpecializedInvoke()) {
      // The callsite looks like              RETURN = INVOKE (ID, OBJECT, P0, P1 .. PN)
      // And the actual method will look like RETURN = INVOKE     (OBJECT, P0, P1 .. PN)

      // Create the call instruction
      Instruction call = Call.create(CALL, null, null, null, null, types.length - 1);

      // Plumb all of the normal parameters into the call
      for (int i = types.length - 1; i >= 2; i--) {
        Call.setParam(call, i - 1, bc2ir.pop(types[i]));
      }
      // The object being specialized
      Operand objectOperand = bc2ir.pop(types[1]);
      Call.setParam(call, 0, objectOperand);
      Operand guard = BC2IR.copyGuardFromOperand(objectOperand);
      if (guard == null) {
        // it's magic, so assume that it's OK....
        guard = new TrueGuardOperand();
      }
      Call.setGuard(call, guard);

      // Load the tib of this object
      RegisterOperand tibObject = gc.getTemps().makeTemp(TypeReference.TIB);
      bc2ir.appendInstruction(GuardedUnary.create(GET_OBJ_TIB, tibObject, objectOperand.copy(), guard.copy()));

      // The index of the specialized method
      Operand methodId = bc2ir.popInt();

      // Add the base offset for specialized methods and convert from index to address
      RegisterOperand tibOffset = gc.getTemps().makeTemp(TypeReference.Int);
      bc2ir.appendInstruction(Binary.create(INT_ADD, tibOffset, methodId, new IntConstantOperand(TIB_FIRST_SPECIALIZED_METHOD_INDEX)));
      bc2ir.appendInstruction(Binary.create(INT_SHL, tibOffset.copyRO(), tibOffset.copyD2U(), new IntConstantOperand(LOG_BYTES_IN_ADDRESS)));

      // Load the code address from the TIB
      RegisterOperand codeAddress = gc.getTemps().makeTemp(TypeReference.Address);
      bc2ir.appendInstruction(Load.create(REF_LOAD, codeAddress, tibObject.copyD2U(), tibOffset.copyD2U(), null));

      Call.setAddress(call, codeAddress.copyD2U());
      if (!returnType.isVoidType()) {
        RegisterOperand op0 = gc.getTemps().makeTemp(returnType);
        Call.setResult(call, op0);
        bc2ir.push(op0.copyD2U(), returnType);
      }
      bc2ir.appendInstruction(call);
    } else if (methodName == MagicNames.objectAsType) {
      RegisterOperand reg = gc.getTemps().makeTemp(TypeReference.Type);
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popRef()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == MagicNames.objectAsThread) {
      RegisterOperand reg = gc.getTemps().makeTemp(TypeReference.Thread);
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popRef()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == MagicNames.objectAsAddress) {
      RegisterOperand reg = gc.getTemps().makeTemp(TypeReference.Address);
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popRef()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == MagicNames.addressAsObject) {
      RegisterOperand reg = gc.getTemps().makeTemp(TypeReference.JavaLangObject);
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popAddress()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == MagicNames.addressAsTIB) {
      RegisterOperand reg = gc.getTemps().makeTemp(TypeReference.TIB);
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popAddress()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == MagicNames.addressAsByteArray) {
      RegisterOperand reg = gc.getTemps().makeTemp(TypeReference.ByteArray);
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popAddress()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == MagicNames.objectAsShortArray) {
      RegisterOperand reg = gc.getTemps().makeTemp(TypeReference.ShortArray);
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popRef()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == MagicNames.objectAsIntArray) {
      RegisterOperand reg = gc.getTemps().makeTemp(TypeReference.IntArray);
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popRef()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == MagicNames.floatAsIntBits) {
      Operand val = bc2ir.popFloat();
      RegisterOperand op0 = gc.getTemps().makeTempInt();
      bc2ir.appendInstruction(Unary.create(FLOAT_AS_INT_BITS, op0, val));
      bc2ir.push(op0.copyD2U());
    } else if (methodName == MagicNames.intBitsAsFloat) {
      Operand val = bc2ir.popInt();
      RegisterOperand op0 = gc.getTemps().makeTempFloat();
      bc2ir.appendInstruction(Unary.create(INT_BITS_AS_FLOAT, op0, val));
      bc2ir.push(op0.copyD2U());
    } else if (methodName == MagicNames.doubleAsLongBits) {
      Operand val = bc2ir.popDouble();
      RegisterOperand op0 = gc.getTemps().makeTempLong();
      bc2ir.appendInstruction(Unary.create(DOUBLE_AS_LONG_BITS, op0, val));
      bc2ir.pushDual(op0.copyD2U());
    } else if (methodName == MagicNames.longBitsAsDouble) {
      Operand val = bc2ir.popLong();
      RegisterOperand op0 = gc.getTemps().makeTempDouble();
      bc2ir.appendInstruction(Unary.create(LONG_BITS_AS_DOUBLE, op0, val));
      bc2ir.pushDual(op0.copyD2U());
    } else if (methodName == MagicNames.sqrt) {
      TypeReference[] args = meth.getParameterTypes();
      if (args[0] == TypeReference.Float) {
        Operand val = bc2ir.popFloat();
        RegisterOperand op0 = gc.getTemps().makeTempFloat();
        bc2ir.appendInstruction(Unary.create(FLOAT_SQRT, op0, val));
        bc2ir.push(op0.copyD2U());
      } else if (args[0] == TypeReference.Double) {
        Operand val = bc2ir.popDouble();
        RegisterOperand op0 = gc.getTemps().makeTempDouble();
        bc2ir.appendInstruction(Unary.create(DOUBLE_SQRT, op0, val));
        bc2ir.pushDual(op0.copyD2U());
      } else {
        if (VM.VerifyAssertions)
          VM._assert(VM.NOT_REACHED,"SQRT only handles Double or Float operands");
      }
    } else if (methodName == MagicNames.getObjectType) {
      Operand val = bc2ir.popRef();
      if (val.isObjectConstant()) {
        bc2ir.push(new ObjectConstantOperand(val.getType().peekType(), Offset.zero()));
      } else {
        Operand guard = BC2IR.copyGuardFromOperand(val);
        if (guard == null) {
          // it's magic, so assume that it's OK....
          guard = new TrueGuardOperand();
        }
        RegisterOperand tibPtr = gc.getTemps().makeTemp(TypeReference.TIB);
        bc2ir.appendInstruction(GuardedUnary.create(GET_OBJ_TIB, tibPtr, val, guard));
        RegisterOperand op0;
        TypeReference argType = val.getType();
        if (argType.isArrayType()) {
          op0 = gc.getTemps().makeTemp(TypeReference.RVMArray);
        } else {
          if (argType == TypeReference.JavaLangObject ||
              argType == TypeReference.JavaLangCloneable ||
              argType == TypeReference.JavaIoSerializable) {
            // could be an array or a class, so make op0 be a RVMType
            op0 = gc.getTemps().makeTemp(TypeReference.Type);
          } else {
            op0 = gc.getTemps().makeTemp(TypeReference.Class);
          }
        }
        bc2ir.markGuardlessNonNull(op0);
        bc2ir.appendInstruction(Unary.create(GET_TYPE_FROM_TIB, op0, tibPtr.copyD2U()));
        bc2ir.push(op0.copyD2U());
      }
    } else if (methodName == MagicNames.getArrayLength) {
      Operand val = bc2ir.popRef();
      RegisterOperand op0 = gc.getTemps().makeTempInt();
      bc2ir.appendInstruction(GuardedUnary.create(ARRAYLENGTH, op0, val, new TrueGuardOperand()));
      bc2ir.push(op0.copyD2U());
    } else if (methodName == MagicNames.invokeClassInitializer) {
      Instruction s = Call.create0(CALL, null, bc2ir.popRef(), null);
      bc2ir.appendInstruction(s);
    } else if ((methodName == MagicNames.invokeMethodReturningObject) ||
               (methodName == MagicNames.invokeMethodReturningVoid) ||
               (methodName == MagicNames.invokeMethodReturningLong) ||
               (methodName == MagicNames.invokeMethodReturningDouble) ||
               (methodName == MagicNames.invokeMethodReturningFloat) ||
               (methodName == MagicNames.invokeMethodReturningInt)) {
      Operand spills = bc2ir.popRef();
      Operand fprmeta = bc2ir.popRef();
      Operand fprs = bc2ir.popRef();
      Operand gprs = bc2ir.popRef();
      Operand code = bc2ir.popRef();
      RegisterOperand res = null;
      if (methodName == MagicNames.invokeMethodReturningObject) {
        res = gc.getTemps().makeTemp(TypeReference.JavaLangObject);
        bc2ir.push(res.copyD2U());
      } else if (methodName == MagicNames.invokeMethodReturningLong) {
        res = gc.getTemps().makeTemp(TypeReference.Long);
        bc2ir.push(res.copyD2U(), TypeReference.Long);
      } else if (methodName == MagicNames.invokeMethodReturningDouble) {
        res = gc.getTemps().makeTempDouble();
        bc2ir.push(res.copyD2U(), TypeReference.Double);
      } else if (methodName == MagicNames.invokeMethodReturningFloat) {
        res = gc.getTemps().makeTempFloat();
        bc2ir.push(res.copyD2U(), TypeReference.Float);
      } else if (methodName == MagicNames.invokeMethodReturningInt) {
        res = gc.getTemps().makeTempInt();
        bc2ir.push(res.copyD2U());
      }
      RVMField target = ArchEntrypoints.reflectiveMethodInvokerInstructionsField;
      MethodOperand met = MethodOperand.STATIC(target);
      Instruction s =
          Call.create5(CALL, res, AC(target.getOffset()), met, code, gprs, fprs, fprmeta, spills);
      bc2ir.appendInstruction(s);
    } else if (methodName == MagicNames.saveThreadState) {
      Operand p1 = bc2ir.popRef();
      RVMField target = ArchEntrypoints.saveThreadStateInstructionsField;
      MethodOperand mo = MethodOperand.STATIC(target);
      bc2ir.appendInstruction(Call.create1(CALL, null, AC(target.getOffset()), mo, p1));
    } else if (methodName == MagicNames.threadSwitch) {
      Operand p2 = bc2ir.popRef();
      Operand p1 = bc2ir.popRef();
      RVMField target = ArchEntrypoints.threadSwitchInstructionsField;
      MethodOperand mo = MethodOperand.STATIC(target);
      bc2ir.appendInstruction(Call.create2(CALL, null, AC(target.getOffset()), mo, p1, p2));
    } else if (methodName == MagicNames.restoreHardwareExceptionState) {
      RVMField target = ArchEntrypoints.restoreHardwareExceptionStateInstructionsField;
      MethodOperand mo = MethodOperand.STATIC(target);
      bc2ir.appendInstruction(Call.create1(CALL,
                                           null,
                                           AC(target.getOffset()),
                                           mo,
                                           bc2ir.popRef()));
    } else if (methodName == MagicNames.prepareInt) {
      Operand offset = bc2ir.popAddress();
      Operand base = bc2ir.popRef();
      RegisterOperand val = gc.getTemps().makeTempInt();
      bc2ir.appendInstruction(Prepare.create(PREPARE_INT, val, base, offset, null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == MagicNames.prepareLong) {
      Operand offset = bc2ir.popAddress();
      Operand base = bc2ir.popRef();
      RegisterOperand val = gc.getTemps().makeTempLong();
      bc2ir.appendInstruction(Prepare.create(PREPARE_LONG, val, base, offset, null));
      bc2ir.pushDual(val.copyD2U());
    } else if (methodName == MagicNames.prepareObject) {
      Operand offset = bc2ir.popAddress();
      Operand base = bc2ir.popRef();
      RegisterOperand val = gc.getTemps().makeTemp(TypeReference.JavaLangObject);
      bc2ir.appendInstruction(Prepare.create(PREPARE_ADDR, val, base, offset, null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == MagicNames.prepareAddress) {
      Operand offset = bc2ir.popAddress();
      Operand base = bc2ir.popRef();
      RegisterOperand val = gc.getTemps().makeTemp(TypeReference.Address);
      bc2ir.appendInstruction(Prepare.create(PREPARE_ADDR, val, base, offset, null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == MagicNames.prepareWord) {
      Operand offset = bc2ir.popAddress();
      Operand base = bc2ir.popRef();
      RegisterOperand val = gc.getTemps().makeTemp(TypeReference.Word);
      bc2ir.appendInstruction(Prepare.create(PREPARE_ADDR, val, base, offset, null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == MagicNames.attemptInt) {
      Operand newVal = bc2ir.popInt();
      Operand oldVal = bc2ir.popInt();
      Operand offset = bc2ir.popAddress();
      Operand base = bc2ir.popRef();
      RegisterOperand test = gc.getTemps().makeTempBoolean();
      bc2ir.appendInstruction(Attempt.create(ATTEMPT_INT, test, base, offset, oldVal, newVal, null));
      bc2ir.push(test.copyD2U());
    } else if (methodName == MagicNames.attemptLong) {
      Operand newVal = bc2ir.popLong();
      Operand oldVal = bc2ir.popLong();
      Operand offset = bc2ir.popAddress();
      Operand base = bc2ir.popRef();
      RegisterOperand test = gc.getTemps().makeTempBoolean();
      bc2ir.appendInstruction(Attempt.create(ATTEMPT_LONG, test, base, offset, oldVal, newVal, null));
      bc2ir.push(test.copyD2U());
    } else if (methodName == MagicNames.attemptObject) {
      Operand newVal = bc2ir.popRef();
      Operand oldVal = bc2ir.popRef();
      Operand offset = bc2ir.popAddress();
      Operand base = bc2ir.popRef();
      RegisterOperand test = gc.getTemps().makeTempBoolean();
      bc2ir.appendInstruction(Attempt.create(ATTEMPT_ADDR, test, base, offset, oldVal, newVal, null));
      bc2ir.push(test.copyD2U());
    } else if (methodName == MagicNames.attemptAddress) {
      Operand newVal = bc2ir.popAddress();
      Operand oldVal = bc2ir.popAddress();
      Operand offset = bc2ir.popAddress();
      Operand base = bc2ir.popRef();
      RegisterOperand test = gc.getTemps().makeTempBoolean();
      bc2ir.appendInstruction(Attempt.create(ATTEMPT_ADDR, test, base, offset, oldVal, newVal, null));
      bc2ir.push(test.copyD2U());
    } else if (methodName == MagicNames.attemptWord) {
      Operand newVal = bc2ir.pop();
      Operand oldVal = bc2ir.pop();
      Operand offset = bc2ir.popAddress();
      Operand base = bc2ir.popRef();
      RegisterOperand test = gc.getTemps().makeTempBoolean();
      bc2ir.appendInstruction(Attempt.create(ATTEMPT_ADDR, test, base, offset, oldVal, newVal, null));
      bc2ir.push(test.copyD2U());
    } else if (methodName == MagicNames.fence) {
      bc2ir.appendInstruction(Empty.create(FENCE));
    } else if (methodName == MagicNames.combinedLoadBarrier) {
      bc2ir.appendInstruction(Empty.create(READ_CEILING));
    } else if (methodName == MagicNames.storeStoreBarrier) {
      bc2ir.appendInstruction(Empty.create(WRITE_FLOOR));
    } else if (generatePolymorphicMagic(bc2ir, gc, meth, methodName)) {
      return true;
    } else if (methodName == MagicNames.getTimeBase) {
      RegisterOperand op0 = gc.getTemps().makeTempLong();
      bc2ir.appendInstruction(Nullary.create(GET_TIME_BASE, op0));
      bc2ir.pushDual(op0.copyD2U());
    } else if (methodName == MagicNames.getInlineDepth) {
      bc2ir.push(new IntConstantOperand(gc.getInlineSequence().getInlineDepth()));
    } else if (methodName == MagicNames.getCompilerLevel) {
      bc2ir.push(new IntConstantOperand(gc.getOptions().getOptLevel()));
    } else if (methodName == MagicNames.isConstantParameter) {
      Operand requestedOperand = bc2ir.pop();
      if (!(requestedOperand instanceof IntConstantOperand)) {
        throw new OptimizingCompilerException("Must supply constant to Magic.isConstantParameter");
      }
      int requested = ((IntConstantOperand)(requestedOperand)).value;
      boolean isConstant = gc.getArguments()[requested].isConstant();
      bc2ir.push(new IntConstantOperand(isConstant ? 1 : 0));
    } else if (methodName == MagicNames.getFrameSize) {
      RegisterOperand res = gc.getTemps().makeTempInt();
      gc.forceFrameAllocation();
      bc2ir.appendInstruction(Nullary.create(FRAMESIZE, res));
      bc2ir.push(res.copyD2U());
    } else {
      // Wasn't machine-independent, so try the machine-dependent magics next.
      if (VM.BuildForIA32) {
        return org.jikesrvm.compilers.opt.bc2ir.ia32.GenerateMachineSpecificMagic.generateMagic(bc2ir, gc, meth);
      } else {
        if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
        return org.jikesrvm.compilers.opt.bc2ir.ppc.GenerateMachineSpecificMagic.generateMagic(bc2ir, gc, meth);
      }
    }
    return true;
  } // generateMagic

  // Generate magic where the untype operational semantics is identified by name.
  // The operands' types are determined from the method signature.
  //
  static boolean generatePolymorphicMagic(BC2IR bc2ir, GenerationContext gc, MethodReference meth,
                                          Atom methodName) {
    TypeReference resultType = meth.getReturnType();
    Instruction s = null;
    if (methodName == MagicNames.wordFromInt || methodName == MagicNames.wordFromIntSignExtend) {
      s = bc2ir._unaryHelper(INT_2ADDRSigExt, bc2ir.popInt(), resultType);
    } else if (methodName == MagicNames.wordFromIntZeroExtend) {
      s = bc2ir._unaryHelper(INT_2ADDRZerExt, bc2ir.popInt(), resultType);
    } else if (methodName == MagicNames.wordFromLong) {
      s = bc2ir._unaryHelper(LONG_2ADDR, bc2ir.popLong(), resultType);
    } else if (methodName == MagicNames.wordToInt) {
      s = bc2ir._unaryHelper(ADDR_2INT, bc2ir.popAddress(), resultType);
    } else if (methodName == MagicNames.wordToLong) {
      s = bc2ir._unaryDualHelper(ADDR_2LONG, bc2ir.popAddress(), resultType);
    } else if (methodName == MagicNames.wordToWord) {
      s = bc2ir._moveHelper(REF_MOVE, bc2ir.popAddress(), resultType);
    } else if (methodName == MagicNames.wordToAddress) {
      s = bc2ir._moveHelper(REF_MOVE, bc2ir.popRef(), resultType);
    } else if (methodName == MagicNames.wordToObject) {
      s = bc2ir._moveHelper(REF_MOVE, bc2ir.popRef(), resultType);
    } else if (methodName == MagicNames.wordToObjectReference || methodName == MagicNames.wordFromObject) {
      s = bc2ir._moveHelper(REF_MOVE, bc2ir.popRef(), resultType);
    } else if (methodName == MagicNames.wordToOffset) {
      s = bc2ir._moveHelper(REF_MOVE, bc2ir.popAddress(), resultType);
    } else if (methodName == MagicNames.wordToExtent) {
      s = bc2ir._moveHelper(REF_MOVE, bc2ir.popAddress(), resultType);
    } else if (methodName == MagicNames.codeArrayAsObject) {
      s = bc2ir._moveHelper(REF_MOVE, bc2ir.pop(TypeReference.CodeArray), resultType);
    } else if (methodName == MagicNames.tibAsObject) {
      s = bc2ir._moveHelper(REF_MOVE, bc2ir.pop(TypeReference.TIB), resultType);
    } else if (methodName == MagicNames.wordPlus) {
      Operand o2 = bc2ir.pop();
      if (VM.BuildFor64Addr && o2.isInt()) {
        s = bc2ir._unaryHelper(INT_2ADDRSigExt, o2, resultType);
        if (s != null) bc2ir.appendInstruction(s);
        o2 = bc2ir.pop();
      }
      Operand o1 = bc2ir.pop();
      s = bc2ir._binaryHelper(REF_ADD, o1, o2, resultType);
    } else if (methodName == MagicNames.wordMinus) {
      Operand o2 = bc2ir.pop();
      if (VM.BuildFor64Addr && o2.isInt()) {
        s = bc2ir._unaryHelper(INT_2ADDRSigExt, o2, resultType);
        if (s != null) bc2ir.appendInstruction(s);
        o2 = bc2ir.pop();
      }
      Operand o1 = bc2ir.pop();
      s = bc2ir._binaryHelper(REF_SUB, o1, o2, resultType);
    } else if (methodName == MagicNames.wordDiff) {
      Operand o2 = bc2ir.pop();
      Operand o1 = bc2ir.pop();
      s = bc2ir._binaryHelper(REF_SUB, o1, o2, resultType);
    } else if (methodName == MagicNames.wordAnd) {
      Operand o2 = bc2ir.pop();
      Operand o1 = bc2ir.pop();
      s = bc2ir._binaryHelper(REF_AND, o1, o2, resultType);
    } else if (methodName == MagicNames.wordOr) {
      Operand o2 = bc2ir.pop();
      Operand o1 = bc2ir.pop();
      s = bc2ir._binaryHelper(REF_OR, o1, o2, resultType);
    } else if (methodName == MagicNames.wordXor) {
      Operand o2 = bc2ir.pop();
      Operand o1 = bc2ir.pop();
      s = bc2ir._binaryHelper(REF_XOR, o1, o2, resultType);
    } else if (methodName == MagicNames.wordNot) {
      Operand o1 = bc2ir.pop();
      s = bc2ir._unaryHelper(REF_NOT, o1, resultType);
    } else if (methodName == MagicNames.wordZero || methodName == MagicNames.wordNull) {
      s = bc2ir._moveHelper(REF_MOVE, AC(Address.zero()), resultType);
    } else if (methodName == MagicNames.wordOne) {
      s = bc2ir._moveHelper(REF_MOVE, AC(Address.fromIntZeroExtend(1)), resultType);
    } else if (methodName == MagicNames.wordMax) {
      s = bc2ir._moveHelper(REF_MOVE, AC(Address.max()), resultType);
    } else if (methodName == MagicNames.wordIsNull || methodName == MagicNames.wordIsZero) {
      s = _cmpHelper(bc2ir, gc, ConditionOperand.EQUAL(), AC(Address.zero()));
    } else if (methodName == MagicNames.wordIsMax) {
      s = _cmpHelper(bc2ir, gc, ConditionOperand.EQUAL(), AC(Address.max()));
    } else if (methodName == MagicNames.wordEQ) {
      s = _cmpHelper(bc2ir, gc, ConditionOperand.EQUAL(), null);
    } else if (methodName == MagicNames.wordNE) {
      s = _cmpHelper(bc2ir, gc, ConditionOperand.NOT_EQUAL(), null);
    } else if (methodName == MagicNames.wordLT) {
      s = _cmpHelper(bc2ir, gc, ConditionOperand.LOWER(), null);
    } else if (methodName == MagicNames.wordLE) {
      s = _cmpHelper(bc2ir, gc, ConditionOperand.LOWER_EQUAL(), null);
    } else if (methodName == MagicNames.wordGT) {
      s = _cmpHelper(bc2ir, gc, ConditionOperand.HIGHER(), null);
    } else if (methodName == MagicNames.wordGE) {
      s = _cmpHelper(bc2ir, gc, ConditionOperand.HIGHER_EQUAL(), null);
    } else if (methodName == MagicNames.wordsLT) {
      s = _cmpHelper(bc2ir, gc, ConditionOperand.LESS(), null);
    } else if (methodName == MagicNames.wordsLE) {
      s = _cmpHelper(bc2ir, gc, ConditionOperand.LESS_EQUAL(), null);
    } else if (methodName == MagicNames.wordsGT) {
      s = _cmpHelper(bc2ir, gc, ConditionOperand.GREATER(), null);
    } else if (methodName == MagicNames.wordsGE) {
      s = _cmpHelper(bc2ir, gc, ConditionOperand.GREATER_EQUAL(), null);
    } else if (methodName == MagicNames.wordLsh) {
      Operand op2 = bc2ir.popInt();
      Operand op1 = bc2ir.popAddress();
      s = bc2ir._binaryHelper(REF_SHL, op1, op2, resultType);
    } else if (methodName == MagicNames.wordRshl) {
      Operand op2 = bc2ir.popInt();
      Operand op1 = bc2ir.popAddress();
      s = bc2ir._binaryHelper(REF_USHR, op1, op2, resultType);
    } else if (methodName == MagicNames.wordRsha) {
      Operand op2 = bc2ir.popInt();
      Operand op1 = bc2ir.popAddress();
      s = bc2ir._binaryHelper(REF_SHR, op1, op2, resultType);
    } else {
      return false;
    }
    if (s != null) {
      bc2ir.appendInstruction(s);
    }
    return true;
  }

  private static Instruction _cmpHelper(BC2IR bc2ir, GenerationContext gc, ConditionOperand cond,
                                Operand given_o2) {
    Operand o2 = given_o2 == null ? bc2ir.pop() : given_o2;
    Operand o1 = bc2ir.pop();
    RegisterOperand res = gc.getTemps().makeTempInt();
    Instruction s = BooleanCmp.create(BOOLEAN_CMP_ADDR, res, o1, o2, cond, new BranchProfileOperand());
    Simplifier.DefUseEffect simp = Simplifier.simplify(true, gc.getTemps(), gc.getOptions(), s);
    if ((simp == Simplifier.DefUseEffect.MOVE_FOLDED) || (simp == Simplifier.DefUseEffect.MOVE_REDUCED)) {
      gc.getTemps().release(res);
      bc2ir.push(Move.getClearVal(s));
      return null;
    } else {
      bc2ir.push(res.copyD2U());
      return s;
    }
  }

  private static LocationOperand mapToMetadata(Operand metadata) {
    if (metadata instanceof IntConstantOperand) {
      int index = ((IntConstantOperand) metadata).value;
      if (index == 0) return null;
      MemberReference mr = MemberReference.getMemberRef(index);
      return new LocationOperand(mr.asFieldReference());
    }
    return null;
  }

  private static final int LOAD_OP = 1;
  private static final int PREPARE_OP = 2;
  private static final int STORE_OP = 3;
  private static final int ATTEMPT_OP = 4;

  private static Operator getOperator(TypeReference type, int operatorClass)
      throws MagicNotImplementedException {
    if (operatorClass == LOAD_OP) {
      if (type == TypeReference.Address) return REF_LOAD;
      if (type == TypeReference.ObjectReference) return REF_LOAD;
      if (type == TypeReference.Word) return REF_LOAD;
      if (type == TypeReference.Offset) return REF_LOAD;
      if (type == TypeReference.Extent) return REF_LOAD;
      if (type == TypeReference.Int) return INT_LOAD;
      if (type == TypeReference.Byte) return BYTE_LOAD;
      if (type == TypeReference.Short) return SHORT_LOAD;
      if (type == TypeReference.Char) return USHORT_LOAD;
      if (type == TypeReference.Float) return FLOAT_LOAD;
      if (type == TypeReference.Double) return DOUBLE_LOAD;
      if (type == TypeReference.Long) return LONG_LOAD;
    } else if (operatorClass == PREPARE_OP) {
      if (type == TypeReference.Address) return PREPARE_ADDR;
      if (type == TypeReference.ObjectReference) return PREPARE_ADDR;
      if (type == TypeReference.Word) return PREPARE_ADDR;
      if (type == TypeReference.Int) return PREPARE_INT;
      if (type == TypeReference.Long) return PREPARE_LONG;
    } else if (operatorClass == ATTEMPT_OP) {
      if (type == TypeReference.Address) return ATTEMPT_ADDR;
      if (type == TypeReference.ObjectReference) return ATTEMPT_ADDR;
      if (type == TypeReference.Word) return ATTEMPT_ADDR;
      if (type == TypeReference.Int) return ATTEMPT_INT;
      if (type == TypeReference.Long) return ATTEMPT_LONG;
    } else if (operatorClass == STORE_OP) {
      if (type == TypeReference.Address) return REF_STORE;
      if (type == TypeReference.ObjectReference) return REF_STORE;
      if (type == TypeReference.Word) return REF_STORE;
      if (type == TypeReference.Offset) return REF_STORE;
      if (type == TypeReference.Extent) return REF_STORE;
      if (type == TypeReference.Int) return INT_STORE;
      if (type == TypeReference.Byte || type == TypeReference.Boolean) return BYTE_STORE;
      if (type == TypeReference.Short) return SHORT_STORE;
      if (type == TypeReference.Char) return SHORT_STORE;
      if (type == TypeReference.Float) return FLOAT_STORE;
      if (type == TypeReference.Double) return DOUBLE_STORE;
      if (type == TypeReference.Long) return LONG_STORE;
    }
    String msg = " Unexpected call to getOperator";
    throw MagicNotImplementedException.UNEXPECTED(msg);
  }

  private static boolean isLoad(Atom methodName) {
    return isPrefix(MagicNames.loadPrefix, methodName.toByteArray());
  }

  private static boolean isPrepare(Atom methodName) {
    return isPrefix(MagicNames.preparePrefix, methodName.toByteArray());
  }

  /**
   * Is string <code>a</code> a prefix of string
   * <code>b</code>. String <code>b</code> is encoded as an ASCII byte
   * array.
   *
   * @param prefix  Prefix atom
   * @param b       String which may contain prefix, encoded as an ASCII
   * byte array.
   * @return <code>true</code> if <code>a</code> is a prefix of
   * <code>b</code>
   */
  @Interruptible
  private static boolean isPrefix(Atom prefix, byte[] b) {
    byte[] a = prefix.toByteArray();
    int aLen = a.length;
    if (aLen > b.length) {
      return false;
    }
    for (int i = 0; i < aLen; i++) {
      if (a[i] != b[i]) {
        return false;
      }
    }
    return true;
  }

}
