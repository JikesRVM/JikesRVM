/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt.ir;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.OPT_ClassLoaderProxy;
import com.ibm.JikesRVM.opt.OPT_MagicNotImplementedException;

/**
 * This class implements the non-machine-specific magics for the opt compiler.
 * By non-machine-specific we mean that the IR generated to implement the magic
 * is independent of the target-architecture.  
 * It does not mean that the eventual MIR that implements the magic 
 * won't differ from architecture to architecture.
 *
 * @author Dave Grove
 * @author Mauricio Serrano
 * @author Perry Cheng
 */
class OPT_GenerateMagic implements OPT_Operators, 
                                   VM_RegisterConstants, 
                                   VM_SizeConstants {

  /**
   * "Semantic inlining" of methods of the VM_Magic class.
   * Based on the methodName, generate a sequence of opt instructions
   * that implement the magic, updating the expression stack as necessary.
   *
   * @param bc2ir the bc2ir object that is generating the 
   *              ir containing this magic
   * @param gc must be bc2ir.gc
   * @param meth the VM_Method that is the magic method
   */
  static boolean generateMagic(OPT_BC2IR bc2ir, 
                               OPT_GenerationContext gc, 
                               VM_MethodReference meth) throws OPT_MagicNotImplementedException {

    if (gc.method.hasNoInlinePragma()) gc.allocFrame = true;
    
    // HACK: Don't schedule any bbs containing unsafe magics.
    // TODO: move this to individual magics that are unsafe.
    // -- igor 08/13/1999
    bc2ir.markBBUnsafeForScheduling();
    VM_Atom methodName = meth.getName();
    if (methodName == VM_MagicNames.getProcessorRegister) {
      OPT_RegisterOperand rop = gc.temps.makePROp();
      bc2ir.markGuardlessNonNull(rop);
      bc2ir.push(rop);
    } else if (methodName == VM_MagicNames.setProcessorRegister) {
      OPT_Operand val = bc2ir.popRef();
      if (val instanceof OPT_RegisterOperand) {
        bc2ir.appendInstruction(Move.create(REF_MOVE, 
                                            gc.temps.makePROp(), 
                                            val));
      } else {
        String msg = " Unexpected operand VM_Magic.setProcessorRegister";
        throw OPT_MagicNotImplementedException.UNEXPECTED(msg);
      }
    } else if (methodName == VM_MagicNames.addressArrayCreate) {
      OPT_Instruction s = bc2ir.generateAnewarray(meth.getType().getArrayElementType());
      bc2ir.appendInstruction(s);
    } else if (methodName == VM_MagicNames.addressArrayLength) {
      OPT_Operand op1 = bc2ir.pop();
      bc2ir.clearCurrentGuard();
      if (bc2ir.do_NullCheck(op1))
        return true;
      OPT_RegisterOperand t = gc.temps.makeTempInt();
      OPT_Instruction s = GuardedUnary.create(ARRAYLENGTH, t, op1, bc2ir.getCurrentGuard());
      bc2ir.push(t.copyD2U());
      bc2ir.appendInstruction(s);
    } else if (methodName == VM_MagicNames.addressArrayGet) {
      VM_TypeReference elementType = meth.getType().getArrayElementType();
      OPT_Operand index = bc2ir.popInt();
      OPT_Operand ref = bc2ir.popRef();
      OPT_RegisterOperand offset = gc.temps.makeTempInt();
      OPT_RegisterOperand result;
      if (meth.getType().isCodeArrayType()) {
        if (VM.BuildForIA32) {
          result = gc.temps.makeTemp(VM_TypeReference.Byte);
          bc2ir.appendInstruction(Load.create(BYTE_LOAD, result, ref, index,
                                              new OPT_LocationOperand(elementType),
                                              new OPT_TrueGuardOperand()));
        } else if (VM.BuildForPowerPC) {
          result = gc.temps.makeTemp(VM_TypeReference.Int);
          bc2ir.appendInstruction(Binary.create(INT_SHL, offset, index, 
                                                new OPT_IntConstantOperand(LOG_BYTES_IN_INT)));
          bc2ir.appendInstruction(Load.create(INT_LOAD, result, ref, offset.copy(),
                                              new OPT_LocationOperand(elementType),
                                              new OPT_TrueGuardOperand()));
        }
      } else { 
        result = gc.temps.makeTemp(elementType);
        bc2ir.appendInstruction(Binary.create(INT_SHL, offset, index, 
                                              new OPT_IntConstantOperand(LOG_BYTES_IN_ADDRESS)));
        OPT_Operator op = VM.BuildFor32Addr ? INT_LOAD : (VM.BuildFor64Addr ? LONG_LOAD : null);
        bc2ir.appendInstruction(Load.create(op, result, ref, offset.copy(),
                                            new OPT_LocationOperand(elementType),
                                            new OPT_TrueGuardOperand()));
      }
      bc2ir.push(result.copyD2U());
    } else if (methodName == VM_MagicNames.addressArraySet) {
      VM_TypeReference elementType = meth.getType().getArrayElementType();
      OPT_Operand val = bc2ir.pop();
      OPT_Operand index = bc2ir.popInt();
      OPT_Operand ref = bc2ir.popRef();
      OPT_RegisterOperand offset = gc.temps.makeTempInt();
      if (meth.getType().isCodeArrayType()) {
        if (VM.BuildForIA32) {
          bc2ir.appendInstruction(Store.create(BYTE_STORE, val, ref, index,
                                               new OPT_LocationOperand(elementType),
                                               new OPT_TrueGuardOperand()));
        } else if (VM.BuildForPowerPC) {
          bc2ir.appendInstruction(Binary.create(INT_SHL, offset, index, 
                                                new OPT_IntConstantOperand(LOG_BYTES_IN_INT)));
          bc2ir.appendInstruction(Store.create(INT_STORE, val, ref, offset.copy(),
                                               new OPT_LocationOperand(elementType),
                                               new OPT_TrueGuardOperand()));
        }
      } else {
        bc2ir.appendInstruction(Binary.create(INT_SHL, offset, index, 
                                              new OPT_IntConstantOperand(LOG_BYTES_IN_ADDRESS)));
        OPT_Operator op = VM.BuildFor32Addr ? INT_STORE : (VM.BuildFor64Addr ? LONG_STORE : null);
        bc2ir.appendInstruction(Store.create(op, val, ref, offset.copy(),
                                             new OPT_LocationOperand(elementType),
                                             new OPT_TrueGuardOperand()));
      }
    } else if (methodName == VM_MagicNames.getIntAtOffset) {
      OPT_Operand offset = bc2ir.popInt();
      OPT_Operand object = bc2ir.popRef();
      OPT_RegisterOperand val = gc.temps.makeTempInt();
      bc2ir.appendInstruction(Load.create(INT_LOAD, val, object, offset, 
                                          null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.setIntAtOffset) {
      OPT_Operand val = bc2ir.popInt();
      OPT_Operand offset = bc2ir.popInt();
      OPT_Operand object = bc2ir.popRef();
      bc2ir.appendInstruction(Store.create(INT_STORE, val, object, offset, 
                                           null));
    } else if (methodName == VM_MagicNames.getWordAtOffset) {
      OPT_Operand offset = bc2ir.popInt();
      OPT_Operand object = bc2ir.popRef();
      OPT_RegisterOperand val = gc.temps.makeTemp(VM_TypeReference.Word);
      bc2ir.appendInstruction(Load.create(REF_LOAD, val, object, offset, 
                                          null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.setWordAtOffset) {
      OPT_Operand val = bc2ir.popRef();
      OPT_Operand offset = bc2ir.popInt();
      OPT_Operand object = bc2ir.popRef();
      bc2ir.appendInstruction(Store.create(REF_STORE, val, object, offset, 
                                           null));
    } else if (methodName == VM_MagicNames.getLongAtOffset) {
      OPT_Operand offset = bc2ir.popInt();
      OPT_Operand object = bc2ir.popRef();
      OPT_RegisterOperand val = gc.temps.makeTempLong();
      bc2ir.appendInstruction(Load.create(LONG_LOAD, val, object, offset, 
                                          null));
      bc2ir.pushDual(val.copyD2U());
    } else if (methodName == VM_MagicNames.setLongAtOffset) {
      OPT_Operand val = bc2ir.popLong();
      OPT_Operand offset = bc2ir.popInt();
      OPT_Operand object = bc2ir.popRef();
      bc2ir.appendInstruction(Store.create(LONG_STORE, val, object, offset, 
                                           null));
    } else if (methodName == VM_MagicNames.getDoubleAtOffset) {
      OPT_Operand offset = bc2ir.popInt();
      OPT_Operand object = bc2ir.popRef();
      OPT_RegisterOperand val = gc.temps.makeTempDouble();
      bc2ir.appendInstruction(Load.create(DOUBLE_LOAD, val, object, offset, 
                                          null));
      bc2ir.pushDual(val.copyD2U());
    } else if (methodName == VM_MagicNames.setDoubleAtOffset) {
      OPT_Operand val = bc2ir.popDouble();
      OPT_Operand offset = bc2ir.popInt();
      OPT_Operand object = bc2ir.popRef();
      bc2ir.appendInstruction(Store.create(DOUBLE_STORE, val, object, offset, 
                                           null));
    } else if (methodName == VM_MagicNames.getObjectAtOffset) {
      OPT_Operand offset = bc2ir.popInt();
      OPT_Operand object = bc2ir.popRef();
      OPT_RegisterOperand val = gc.temps.makeTemp(VM_TypeReference.JavaLangObject);
      bc2ir.appendInstruction(Load.create(REF_LOAD, val, object, offset, 
                                          null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.getObjectArrayAtOffset) {
      OPT_Operand offset = bc2ir.popInt();
      OPT_Operand object = bc2ir.popRef();
      OPT_RegisterOperand val = gc.temps.makeTemp(VM_TypeReference.JavaLangObjectArray);
      bc2ir.appendInstruction(Load.create(REF_LOAD, val, object, offset, 
                                          null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.setObjectAtOffset) {
      OPT_LocationOperand loc = null;
      if (meth.getParameterTypes().length == 4) {
        loc = mapToMetadata(bc2ir.popInt());
      }
      OPT_Operand val = bc2ir.popRef();
      OPT_Operand offset = bc2ir.popInt();
      OPT_Operand object = bc2ir.popRef();
      bc2ir.appendInstruction(Store.create(REF_STORE, val, object, offset, 
                                           loc));
    } else if (methodName == VM_MagicNames.getByteAtOffset) {
      OPT_Operand offset = bc2ir.popInt();
      OPT_Operand object = bc2ir.popRef();
      OPT_RegisterOperand val = gc.temps.makeTemp(VM_TypeReference.Byte);
      bc2ir.appendInstruction(Load.create(BYTE_LOAD, val, object, offset, 
                                          null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.setByteAtOffset) {
      OPT_Operand val = bc2ir.popInt();
      OPT_Operand offset = bc2ir.popInt();
      OPT_Operand object = bc2ir.popRef();
      bc2ir.appendInstruction(Store.create(BYTE_STORE, val, object, offset, 
                                           null));
    } else if (methodName == VM_MagicNames.getCharAtOffset) {
      OPT_Operand offset = bc2ir.popInt();
      OPT_Operand object = bc2ir.popRef();
      OPT_RegisterOperand val = gc.temps.makeTemp(VM_TypeReference.Char);
      bc2ir.appendInstruction(Load.create(USHORT_LOAD, val, object, offset, 
                                          null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.setCharAtOffset) {
      OPT_Operand val = bc2ir.popInt();
      OPT_Operand offset = bc2ir.popInt();
      OPT_Operand object = bc2ir.popRef();
      bc2ir.appendInstruction(Store.create(SHORT_STORE, val, object, offset, 
                                           null));
    } else if (methodName == VM_MagicNames.getMemoryInt) {
      OPT_Operand memAddr = bc2ir.popAddress();
      OPT_RegisterOperand val = gc.temps.makeTempInt();
      bc2ir.appendInstruction(Load.create(INT_LOAD, val, 
                                          memAddr, 
                                          new OPT_IntConstantOperand(0), 
                                          null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.getMemoryWord) {
      OPT_Operand memAddr = bc2ir.popAddress();
      OPT_RegisterOperand val = gc.temps.makeTemp(VM_TypeReference.Word);
      bc2ir.appendInstruction(Load.create(REF_LOAD, val, 
                                          memAddr, 
                                          new OPT_IntConstantOperand(0),
                                          null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.getMemoryAddress) {
      OPT_Operand memAddr = bc2ir.popAddress();
      OPT_RegisterOperand val = gc.temps.makeTemp(VM_TypeReference.Address);
      bc2ir.appendInstruction(Load.create(REF_LOAD, val, 
                                          memAddr, 
                                          new OPT_IntConstantOperand(0),
                                          null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.setMemoryInt) {
      OPT_Operand val = bc2ir.popInt();
      OPT_Operand memAddr = bc2ir.popAddress();
      bc2ir.appendInstruction(Store.create(INT_STORE, val, 
                                           memAddr, 
                                           new OPT_IntConstantOperand(0), 
                                           null));
    } else if (methodName == VM_MagicNames.setMemoryWord) {
      OPT_Operand val = bc2ir.popRef();
      OPT_Operand memAddr = bc2ir.popAddress();
      bc2ir.appendInstruction(Store.create(REF_STORE, val, 
                                           memAddr, 
                                           new OPT_IntConstantOperand(0), 
                                           null));
    } else if (methodName == VM_MagicNames.setMemoryAddress) {
      OPT_LocationOperand loc = null;
      if (meth.getParameterTypes().length == 3) {
        loc = mapToMetadata(bc2ir.popInt());
      }
      OPT_Operand val = bc2ir.popRef();
      OPT_Operand memAddr = bc2ir.popAddress();
      bc2ir.appendInstruction(Store.create(REF_STORE, val, 
                                           memAddr, 
                                           new OPT_IntConstantOperand(0), 
                                           loc));
    } else if (meth.getType() == VM_TypeReference.SysCall) {
      // All methods of VM_SysCall have the following signature:
      // callNAME(VM_Address code, <var args to pass via native calling convention>)
      VM_TypeReference[] args = meth.getParameterTypes();
      int numArgs = args.length;
      VM_Field ip = VM_Entrypoints.getSysCallField(meth.getName().toString());
      OPT_MethodOperand mo = OPT_MethodOperand.STATIC(ip);
      OPT_Instruction call = Call.create(SYSCALL, null, null, mo, null,  args.length);
      for (int i = args.length-1; i >= 0; i--) {
        Call.setParam(call, i, bc2ir.pop(args[i]));
      }
      VM_TypeReference rtype = meth.getReturnType();
      if (!rtype.isVoidType()) {
        OPT_RegisterOperand op0 = gc.temps.makeTemp(rtype);
        Call.setResult(call, op0);
        bc2ir.push(op0.copyD2U(), rtype);
      }
      bc2ir.appendInstruction(call);
    } else if (methodName == VM_MagicNames.threadAsCollectorThread) {
      OPT_RegisterOperand reg = 
        gc.temps.makeTemp(VM_TypeReference.findOrCreate(VM_SystemClassLoader.getVMClassLoader(), 
                                                        VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_CollectorThread;")));
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popRef()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.objectAsType) {
      OPT_RegisterOperand reg = gc.temps.makeTemp(VM_TypeReference.VM_Type);
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popRef()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.objectAsThread) {
      OPT_RegisterOperand reg = 
        gc.temps.makeTemp(VM_TypeReference.findOrCreate(VM_SystemClassLoader.getVMClassLoader(), 
                                                        VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_Thread;")));
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popRef()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.objectAsProcessor) {
      OPT_RegisterOperand reg = gc.temps.makeTemp(VM_TypeReference.VM_Processor);
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popRef()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.objectAsAddress) {
      OPT_RegisterOperand reg = gc.temps.makeTemp(VM_TypeReference.Address);
      bc2ir.appendInstruction(Move.create(INT_MOVE, reg, bc2ir.popRef()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.addressAsObject) {
      OPT_RegisterOperand reg = gc.temps.makeTemp(VM_TypeReference.JavaLangObject);
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popAddress()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.addressAsObjectArray) {
      OPT_RegisterOperand reg = gc.temps.makeTemp(VM_TypeReference.JavaLangObjectArray);
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popAddress()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.addressAsType) {
      OPT_RegisterOperand reg = gc.temps.makeTemp(VM_TypeReference.VM_Type);
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popAddress()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.addressAsThread) {
      OPT_RegisterOperand reg = 
        gc.temps.makeTemp(VM_TypeReference.findOrCreate(VM_SystemClassLoader.getVMClassLoader(),
                                                        VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_Thread;")));
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popAddress()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.addressAsRegisters) {
      OPT_RegisterOperand reg = 
        gc.temps.makeTemp(VM_TypeReference.findOrCreate(VM_SystemClassLoader.getVMClassLoader(),
                                                        VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_Registers;")));
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popAddress()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.addressAsByteArray) {
      OPT_RegisterOperand reg = 
        gc.temps.makeTemp(VM_TypeReference.ByteArray);
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popAddress()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.addressAsIntArray) {
      OPT_RegisterOperand reg = 
        gc.temps.makeTemp(VM_TypeReference.IntArray);
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popAddress()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.objectAsByteArray) {
      OPT_RegisterOperand reg = 
        gc.temps.makeTemp(VM_TypeReference.ByteArray);
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popRef()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.objectAsShortArray) {
      OPT_RegisterOperand reg = 
        gc.temps.makeTemp(VM_TypeReference.ShortArray);
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popRef()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.objectAsIntArray) {
      OPT_RegisterOperand reg = 
        gc.temps.makeTemp(VM_TypeReference.IntArray);
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popRef()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.addressAsStack) {
      OPT_RegisterOperand reg = 
        gc.temps.makeTemp(VM_TypeReference.IntArray);
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popAddress()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.floatAsIntBits) {
      OPT_Operand val = bc2ir.popFloat();
      OPT_RegisterOperand op0 = gc.temps.makeTempInt();
      bc2ir.appendInstruction(Unary.create(FLOAT_AS_INT_BITS, op0, val));
      bc2ir.push(op0.copyD2U());
    } else if (methodName == VM_MagicNames.intBitsAsFloat) {
      OPT_Operand val = bc2ir.popInt();
      OPT_RegisterOperand op0 = gc.temps.makeTempFloat();
      bc2ir.appendInstruction(Unary.create(INT_BITS_AS_FLOAT, op0, val));
      bc2ir.push(op0.copyD2U());
    } else if (methodName == VM_MagicNames.doubleAsLongBits) {
      OPT_Operand val = bc2ir.popDouble();
      OPT_RegisterOperand op0 = gc.temps.makeTempLong();
      bc2ir.appendInstruction(Unary.create(DOUBLE_AS_LONG_BITS, op0, val));
      bc2ir.pushDual(op0.copyD2U());
    } else if (methodName == VM_MagicNames.longBitsAsDouble) {
      OPT_Operand val = bc2ir.popLong();
      OPT_RegisterOperand op0 = gc.temps.makeTempDouble();
      bc2ir.appendInstruction(Unary.create(LONG_BITS_AS_DOUBLE, op0, val));
      bc2ir.pushDual(op0.copyD2U());
    } else if (methodName == VM_MagicNames.getObjectType) {
      OPT_Operand val = bc2ir.popRef();
      OPT_Operand guard = bc2ir.getGuard(val);
      if (guard == null) {
        // it's magic, so assume that it's OK....
        guard = new OPT_TrueGuardOperand(); 
      }
      OPT_RegisterOperand tibPtr = 
        gc.temps.makeTemp(VM_TypeReference.JavaLangObjectArray);
      bc2ir.appendInstruction(GuardedUnary.create(GET_OBJ_TIB, tibPtr, 
                                                  val, guard));
      OPT_RegisterOperand op0;
      VM_TypeReference argType = val.getType();
      if (argType.isArrayType()) {
        op0 = gc.temps.makeTemp(VM_TypeReference.VM_Array);
      } else {
        if (argType == VM_TypeReference.JavaLangObject ||
            argType == VM_TypeReference.JavaLangCloneable || 
            argType == VM_TypeReference.JavaIoSerializable) {
          // could be an array or a class, so make op0 be a VM_Type
          op0 = gc.temps.makeTemp(VM_TypeReference.VM_Type);
        } else {
          op0 = gc.temps.makeTemp(VM_TypeReference.VM_Class);
        }
      }
      bc2ir.markGuardlessNonNull(op0);
      bc2ir.appendInstruction(Unary.create(GET_TYPE_FROM_TIB, op0, 
                                           tibPtr.copyD2U()));
      bc2ir.push(op0.copyD2U());
    } else if (methodName == VM_MagicNames.getArrayLength) {
      OPT_Operand val = bc2ir.popRef();
      OPT_RegisterOperand op0 = gc.temps.makeTempInt();
      bc2ir.appendInstruction(GuardedUnary.create(ARRAYLENGTH, op0, val, 
                                                  new OPT_TrueGuardOperand()));
      bc2ir.push(op0.copyD2U());
    } else if (methodName == VM_MagicNames.invokeClassInitializer) {
      OPT_Instruction s = Call.create0(CALL, null, bc2ir.popRef(), null);
      bc2ir.appendInstruction(s);
    } else if (methodName == VM_MagicNames.invokeMain) {
      OPT_Operand code = bc2ir.popRef();
      OPT_Operand args = bc2ir.popRef();
      bc2ir.appendInstruction(Call.create1(CALL, null, code, null, args));
    } else if ((methodName == VM_MagicNames.invokeMethodReturningObject)
               || (methodName == VM_MagicNames.invokeMethodReturningVoid) 
               || (methodName == VM_MagicNames.invokeMethodReturningLong) 
               || (methodName == VM_MagicNames.invokeMethodReturningDouble) 
               || (methodName == VM_MagicNames.invokeMethodReturningFloat) 
               || (methodName == VM_MagicNames.invokeMethodReturningInt)) {
      OPT_Operand spills = bc2ir.popRef();
      OPT_Operand fprs = bc2ir.popRef();
      OPT_Operand gprs = bc2ir.popRef();
      OPT_Operand code = bc2ir.popRef();
      OPT_RegisterOperand res = null;
      if (methodName == VM_MagicNames.invokeMethodReturningObject) {
        res = gc.temps.makeTemp(VM_TypeReference.JavaLangObject);
        bc2ir.push(res.copyD2U());
      } else if (methodName == VM_MagicNames.invokeMethodReturningLong) {
        res = gc.temps.makeTemp(VM_TypeReference.Long);
        bc2ir.push(res.copyD2U(), VM_TypeReference.Long);
      } else if (methodName == VM_MagicNames.invokeMethodReturningDouble) {
        res = gc.temps.makeTempDouble();
        bc2ir.push(res.copyD2U(), VM_TypeReference.Double);
      } else if (methodName == VM_MagicNames.invokeMethodReturningFloat) {
        res = gc.temps.makeTempFloat();
        bc2ir.push(res.copyD2U(), VM_TypeReference.Float);
      } else if (methodName == VM_MagicNames.invokeMethodReturningInt) {
        res = gc.temps.makeTempInt();
        bc2ir.push(res.copyD2U());
      }
      VM_Field target = VM_Entrypoints.reflectiveMethodInvokerInstructionsField;
      OPT_MethodOperand met = OPT_MethodOperand.STATIC(target);
      OPT_Instruction s = Call.create4(CALL, res, new OPT_IntConstantOperand(target.getOffset()), met, code, gprs, 
                                       fprs, spills);
      bc2ir.appendInstruction(s);
    } else if (methodName == VM_MagicNames.saveThreadState) {
      OPT_Operand p1 = bc2ir.popRef();
      VM_Field target = VM_Entrypoints.saveThreadStateInstructionsField;
      OPT_MethodOperand mo = OPT_MethodOperand.STATIC(target);
      bc2ir.appendInstruction(Call.create1(CALL, null, new OPT_IntConstantOperand(target.getOffset()), mo, p1));
    } else if (methodName == VM_MagicNames.threadSwitch) {
      OPT_Operand p2 = bc2ir.popRef();
      OPT_Operand p1 = bc2ir.popRef();
      VM_Field target = VM_Entrypoints.threadSwitchInstructionsField;
      OPT_MethodOperand mo = OPT_MethodOperand.STATIC(target);
      bc2ir.appendInstruction(Call.create2(CALL, null, new OPT_IntConstantOperand(target.getOffset()), mo, p1, p2));
    } else if (methodName == VM_MagicNames.restoreHardwareExceptionState) {
      VM_Field target = VM_Entrypoints.restoreHardwareExceptionStateInstructionsField;
      OPT_MethodOperand mo = OPT_MethodOperand.STATIC(target);
      bc2ir.appendInstruction(Call.create1(CALL, null, new OPT_IntConstantOperand(target.getOffset()), mo, bc2ir.popRef()));
    } else if (methodName == VM_MagicNames.prepareInt) {
      OPT_Operand offset = bc2ir.popInt();
      OPT_Operand base = bc2ir.popRef();
      OPT_RegisterOperand val = gc.temps.makeTempInt();
      bc2ir.appendInstruction(Prepare.create(PREPARE, val, base, offset, null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.prepareObject) {
      OPT_Operand offset = bc2ir.popInt();
      OPT_Operand base = bc2ir.popRef();
      OPT_RegisterOperand val = gc.temps.makeTemp(VM_TypeReference.JavaLangObject);
      bc2ir.appendInstruction(Prepare.create(PREPARE, val, base, offset, null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.prepareAddress) {
      OPT_Operand offset = bc2ir.popInt();
      OPT_Operand base = bc2ir.popRef();
      OPT_RegisterOperand val = gc.temps.makeTemp(VM_TypeReference.Address);
      bc2ir.appendInstruction(Prepare.create(PREPARE, val, base, offset, null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.prepareWord) {
      OPT_Operand offset = bc2ir.popInt();
      OPT_Operand base = bc2ir.popRef();
      OPT_RegisterOperand val = gc.temps.makeTemp(VM_TypeReference.Word);
      bc2ir.appendInstruction(Prepare.create(PREPARE, val, base, offset, null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.attemptInt) {
      OPT_Operand newVal = bc2ir.popInt();
      OPT_Operand oldVal = bc2ir.popInt();
      OPT_Operand offset = bc2ir.popInt();
      OPT_Operand base = bc2ir.popRef();
      OPT_RegisterOperand test = gc.temps.makeTempInt();
      bc2ir.appendInstruction(Attempt.create(ATTEMPT, test, base, offset, oldVal, 
                                             newVal, null));
      bc2ir.push(test.copyD2U());
    } else if (methodName == VM_MagicNames.attemptObject) {
      OPT_Operand newVal = bc2ir.popRef();
      OPT_Operand oldVal = bc2ir.popRef();
      OPT_Operand offset = bc2ir.popInt();
      OPT_Operand base = bc2ir.popRef();
      OPT_RegisterOperand test = gc.temps.makeTempInt();
      bc2ir.appendInstruction(Attempt.create(ATTEMPT, test, base, offset, oldVal, 
                                             newVal, null));
      bc2ir.push(test.copyD2U());
    } else if (methodName == VM_MagicNames.attemptAddress) {
      OPT_Operand newVal = bc2ir.popAddress();
      OPT_Operand oldVal = bc2ir.popAddress();
      OPT_Operand offset = bc2ir.popInt();
      OPT_Operand base = bc2ir.popRef();
      OPT_RegisterOperand test = gc.temps.makeTempInt();
      bc2ir.appendInstruction(Attempt.create(ATTEMPT, test, base, offset, oldVal, 
                                             newVal, null));
      bc2ir.push(test.copyD2U());
    } else if (methodName == VM_MagicNames.attemptWord) {
      OPT_Operand newVal = bc2ir.pop();
      OPT_Operand oldVal = bc2ir.pop();
      OPT_Operand offset = bc2ir.popInt();
      OPT_Operand base = bc2ir.popRef();
      OPT_RegisterOperand test = gc.temps.makeTempInt();
      bc2ir.appendInstruction(Attempt.create(ATTEMPT, test, base, offset, oldVal, 
                                             newVal, null));
      bc2ir.push(test.copyD2U());
    } else if (generatePolymorphicMagic(bc2ir, gc, meth, methodName)) {
      return true;
    } else if (methodName == VM_MagicNames.getTimeBase) {
      OPT_RegisterOperand op0 = gc.temps.makeTempLong();
      bc2ir.appendInstruction(Nullary.create(GET_TIME_BASE, op0));
      bc2ir.pushDual(op0.copyD2U());
    } else {
      // Wasn't machine-independent, so try the machine-dependent magics next.
      return OPT_GenerateMachineSpecificMagic.generateMagic(bc2ir, gc, meth);
    }
    return true;
  } // generateMagic


  // Generate magic where the untype operational semantics is identified by name.
  // The operands' types are determnied from the method signature.
  //
  static boolean generatePolymorphicMagic(OPT_BC2IR bc2ir, 
                                          OPT_GenerationContext gc, 
                                          VM_MethodReference meth, VM_Atom methodName) {
    VM_TypeReference [] paramTypes = meth.getParameterTypes();
    VM_TypeReference resultType = meth.getReturnType();
    if (methodName == VM_MagicNames.wordFromInt ||
        methodName == VM_MagicNames.wordFromIntZeroExtend ||
        methodName == VM_MagicNames.wordFromIntSignExtend) {
      OPT_RegisterOperand reg = gc.temps.makeTemp(resultType);
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popInt()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.wordToInt) {
      // a no-op without even a type-conversion
      OPT_RegisterOperand reg = gc.temps.makeTempInt();
      bc2ir.appendInstruction(Move.create(INT_MOVE, reg, bc2ir.popAddress()));
      bc2ir.push(reg.copyD2U());

    } else if (methodName == VM_MagicNames.wordToLong) {
      if (VM.BuildFor32Addr) {
        // This is a really crappy implementation.
        // The theory is that this magic isn't going to be used anywhere we
        // care about performance so we do something stupid here instead of
        // making a new IR operator that we have to plumb all the way through the compiler.
        OPT_RegisterOperand reg = gc.temps.makeTempInt();
        OPT_RegisterOperand lreg = gc.temps.makeTempLong();
        bc2ir.appendInstruction(Move.create(INT_MOVE, reg, bc2ir.popAddress()));
        bc2ir.appendInstruction(Unary.create(INT_2LONG, lreg, reg.copyD2U()));
        bc2ir.appendInstruction(Binary.create(LONG_AND, lreg.copyD2D(), lreg.copyD2U(),
                                              new OPT_LongConstantOperand(0x00000000ffffffffL)));
        bc2ir.pushDual(lreg.copyD2U());
      } else {
        // a no-op without even a type-conversion
        OPT_RegisterOperand reg = gc.temps.makeTempLong();
        bc2ir.appendInstruction(Move.create(LONG_MOVE, reg, bc2ir.popAddress()));
        bc2ir.pushDual(reg.copyD2U());
      }
    } else if (methodName == VM_MagicNames.wordToWord) {
      OPT_RegisterOperand reg = gc.temps.makeTemp(VM_TypeReference.Word);
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popAddress()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.wordToAddress) {
      OPT_RegisterOperand reg = gc.temps.makeTemp(VM_TypeReference.Address);
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popAddress()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.wordToOffset) {
      OPT_RegisterOperand reg = gc.temps.makeTemp(VM_TypeReference.Offset);
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popAddress()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.wordToExtent) {
      OPT_RegisterOperand reg = gc.temps.makeTemp(VM_TypeReference.Extent);
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popAddress()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.wordAdd) {
      OPT_Operand o2 = bc2ir.pop();
      OPT_Operand o1 = bc2ir.pop();
      OPT_RegisterOperand op0 = gc.temps.makeTemp(resultType);
      bc2ir.appendInstruction(Binary.create(INT_ADD, op0, o1, o2));
      bc2ir.push(op0.copyD2U());
    } else if (methodName == VM_MagicNames.wordSub) {
      OPT_Operand o2 = bc2ir.pop();
      OPT_Operand o1 = bc2ir.pop();
      OPT_RegisterOperand op0 = gc.temps.makeTemp(resultType);
      bc2ir.appendInstruction(Binary.create(INT_SUB, op0, o1, o2));
      bc2ir.push(op0.copyD2U());
    } else if (methodName == VM_MagicNames.wordDiff) {
      OPT_Operand o2 = bc2ir.pop();
      OPT_Operand o1 = bc2ir.pop();
      OPT_RegisterOperand op0 = gc.temps.makeTemp(resultType);
      bc2ir.appendInstruction(Binary.create(INT_SUB, op0, o1, o2));
      bc2ir.push(op0.copyD2U());
    } else if (methodName == VM_MagicNames.wordAnd) {
      OPT_Operand o2 = bc2ir.pop();
      OPT_Operand o1 = bc2ir.pop();
      OPT_RegisterOperand op0 = gc.temps.makeTemp(resultType);
      bc2ir.appendInstruction(Binary.create(INT_AND, op0, o1, o2));
      bc2ir.push(op0.copyD2U());
    } else if (methodName == VM_MagicNames.wordOr) {
      OPT_Operand o2 = bc2ir.pop();
      OPT_Operand o1 = bc2ir.pop();
      OPT_RegisterOperand op0 = gc.temps.makeTemp(resultType);
      bc2ir.appendInstruction(Binary.create(INT_OR, op0, o1, o2));
      bc2ir.push(op0.copyD2U());
    } else if (methodName == VM_MagicNames.wordXor) {
      OPT_Operand o2 = bc2ir.pop();
      OPT_Operand o1 = bc2ir.pop();
      OPT_RegisterOperand op0 = gc.temps.makeTemp(resultType);
      bc2ir.appendInstruction(Binary.create(INT_XOR, op0, o1, o2));
      bc2ir.push(op0.copyD2U());
    } else if (methodName == VM_MagicNames.wordNot) {
      OPT_Operand o1 = bc2ir.pop();
      OPT_RegisterOperand op0 = gc.temps.makeTemp(resultType);
      bc2ir.appendInstruction(Unary.create(INT_NOT, op0, o1));
      bc2ir.push(op0.copyD2U());
    } else if (methodName == VM_MagicNames.wordZero) {
      OPT_RegisterOperand op0 = gc.temps.makeTemp(resultType);
      bc2ir.appendInstruction(Move.create(INT_MOVE, op0, new OPT_AddressConstantOperand(VM_Address.zero())));
      bc2ir.push(op0.copyD2U());
    } else if (methodName == VM_MagicNames.wordOne) {
      OPT_RegisterOperand op0 = gc.temps.makeTemp(resultType);
      bc2ir.appendInstruction(Move.create(INT_MOVE, op0, new OPT_AddressConstantOperand(VM_Address.fromIntZeroExtend(1))));
      bc2ir.push(op0.copyD2U());
    } else if (methodName == VM_MagicNames.wordMax) {
      OPT_RegisterOperand op0 = gc.temps.makeTemp(resultType);
      bc2ir.appendInstruction(Move.create(INT_MOVE, op0, new OPT_AddressConstantOperand(VM_Address.max())));
      bc2ir.push(op0.copyD2U());
    } else if (methodName == VM_MagicNames.wordIsZero) {
      OPT_RegisterOperand op0 = gc.temps.makeTemp(resultType);
      bc2ir.appendInstruction(Move.create(INT_MOVE, op0, new OPT_AddressConstantOperand(VM_Address.zero())));
      OPT_ConditionOperand cond = OPT_ConditionOperand.EQUAL();
      cmpHelper(bc2ir,gc,cond, op0);
    } else if (methodName == VM_MagicNames.wordIsMax) {
      OPT_RegisterOperand op0 = gc.temps.makeTemp(resultType);
      bc2ir.appendInstruction(Move.create(INT_MOVE, op0, new OPT_AddressConstantOperand(VM_Address.max())));
      OPT_ConditionOperand cond = OPT_ConditionOperand.EQUAL();
      cmpHelper(bc2ir,gc,cond, op0);
    } else if (methodName == VM_MagicNames.wordEQ) {
      OPT_ConditionOperand cond = OPT_ConditionOperand.EQUAL();
      cmpHelper(bc2ir,gc,cond,null);
    } else if (methodName == VM_MagicNames.wordNE) {
      OPT_ConditionOperand cond = OPT_ConditionOperand.NOT_EQUAL();
      cmpHelper(bc2ir,gc,cond,null);
    } else if (methodName == VM_MagicNames.wordLT) {
      OPT_ConditionOperand cond = OPT_ConditionOperand.LOWER();
      cmpHelper(bc2ir,gc,cond,null);
    } else if (methodName == VM_MagicNames.wordLE) {
      OPT_ConditionOperand cond = OPT_ConditionOperand.LOWER_EQUAL();
      cmpHelper(bc2ir,gc,cond,null);
    } else if (methodName == VM_MagicNames.wordGT) {
      OPT_ConditionOperand cond = OPT_ConditionOperand.HIGHER();
      cmpHelper(bc2ir,gc,cond,null);
    } else if (methodName == VM_MagicNames.wordGE) {
      OPT_ConditionOperand cond = OPT_ConditionOperand.HIGHER_EQUAL();
      cmpHelper(bc2ir,gc,cond,null);
    } else if (methodName == VM_MagicNames.wordsLT) {
      OPT_ConditionOperand cond = OPT_ConditionOperand.LESS();
      cmpHelper(bc2ir,gc,cond,null);
    } else if (methodName == VM_MagicNames.wordsLE) {
      OPT_ConditionOperand cond = OPT_ConditionOperand.LESS_EQUAL();
      cmpHelper(bc2ir,gc,cond,null);
    } else if (methodName == VM_MagicNames.wordsGT) {
      OPT_ConditionOperand cond = OPT_ConditionOperand.GREATER();
      cmpHelper(bc2ir,gc,cond,null);
    } else if (methodName == VM_MagicNames.wordsGE) {
      OPT_ConditionOperand cond = OPT_ConditionOperand.GREATER_EQUAL();
      cmpHelper(bc2ir,gc,cond,null);
    } else if (methodName == VM_MagicNames.wordLsh) {
      OPT_Operand op2 = bc2ir.popInt();
      OPT_Operand op1 = bc2ir.popAddress();
      OPT_RegisterOperand res = gc.temps.makeTemp(resultType); // VM_TypeReference.Int);
      bc2ir.appendInstruction(Binary.create(VM.BuildFor32Addr ? INT_SHL : LONG_SHL, res, op1, op2));
      bc2ir.push(res.copyD2U());
    } else if (methodName == VM_MagicNames.wordRshl) {
      OPT_Operand op2 = bc2ir.popInt();
      OPT_Operand op1 = bc2ir.popAddress();
      OPT_RegisterOperand res = gc.temps.makeTemp(resultType);
      bc2ir.appendInstruction(Binary.create(VM.BuildFor32Addr ? INT_USHR : LONG_USHR, res, op1, op2));
      bc2ir.push(res.copyD2U());
    } else if (methodName == VM_MagicNames.wordRsha) {
      OPT_Operand op2 = bc2ir.popInt();
      OPT_Operand op1 = bc2ir.popAddress();
      OPT_RegisterOperand res = gc.temps.makeTemp(resultType);
      bc2ir.appendInstruction(Binary.create(VM.BuildFor32Addr ? INT_SHR : LONG_SHR, res, op1, op2));
      bc2ir.push(res.copyD2U());
    } else {
      return false;
    }
    return true;
  }

  private static void cmpHelper(OPT_BC2IR bc2ir, 
                                OPT_GenerationContext gc, 
                                OPT_ConditionOperand cond,
                                OPT_Operand given_o2) {
    OPT_Operand o2 = given_o2 == null ? bc2ir.pop() : given_o2;
    OPT_Operand o1 = bc2ir.pop();
    OPT_RegisterOperand res = gc.temps.makeTempInt();
    bc2ir.appendInstruction(BooleanCmp.create(BOOLEAN_CMP, res.copyRO(), o1, o2, cond, new OPT_BranchProfileOperand()));
    bc2ir.push(res.copyD2U());
  }

  private static OPT_LocationOperand mapToMetadata(OPT_Operand metadata) {
    if (metadata instanceof OPT_IntConstantOperand) {
      int index = ((OPT_IntConstantOperand)metadata).value;
      if (index == 0) return null;
      VM_MemberReference mr = VM_MemberReference.getMemberRef(index);
      return new OPT_LocationOperand(mr.asFieldReference());
    }
    return null;
  }
}
