/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt.ir;

import com.ibm.JikesRVM.*;
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
 */
class OPT_GenerateMagic implements OPT_Operators, VM_RegisterConstants {

  /**
   * "Semantic inlining" of methods of the VM_Magic class.
   * Based on the methodName, generate a sequence of opt instructions
   * that implement the magic, updating the expression stack as neccessary.
   *
   * @param bc2ir the bc2ir object that is generating the 
   *              ir containing this magic
   * @param gc must be bc2ir.gc
   * @param meth the VM_Method that is the magic method
   */
  static void generateMagic(OPT_BC2IR bc2ir, 
			    OPT_GenerationContext gc, 
			    VM_Method meth) throws OPT_MagicNotImplementedException {

    if (gc.method.hasNoInlinePragma()) gc.allocFrame = true;
    
    // HACK: Don't schedule any bbs containing unsafe magics.
    // TODO: move this to individual magics that are unsafe.
    // -- igor 08/13/1999
    bc2ir.markBBUnsafeForScheduling();
    VM_Atom methodName = meth.getName();
    // VM.sysWriteln("OPT_GenerateMagic.generateMagic: method = ", methodName.toString());
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
    } else if (methodName == VM_MagicNames.getObjectAtOffset) {
      OPT_Operand offset = bc2ir.popInt();
      OPT_Operand object = bc2ir.popRef();
      OPT_RegisterOperand val = gc.temps.makeTemp(VM_Type.JavaLangObjectType);
      bc2ir.appendInstruction(Load.create(INT_LOAD, val, object, offset, 
					  null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.getObjectArrayAtOffset) {
      OPT_Operand offset = bc2ir.popInt();
      OPT_Operand object = bc2ir.popRef();
      OPT_RegisterOperand val = gc.temps.makeTemp(OPT_ClassLoaderProxy.JavaLangObjectArrayType);
      bc2ir.appendInstruction(Load.create(REF_LOAD, val, object, offset, 
					  null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.setObjectAtOffset) {
      OPT_Operand val = bc2ir.popRef();
      OPT_Operand offset = bc2ir.popInt();
      OPT_Operand object = bc2ir.popRef();
      bc2ir.appendInstruction(Store.create(INT_STORE, val, object, offset, 
					   null));
    } else if (methodName == VM_MagicNames.getByteAtOffset) {
      OPT_Operand offset = bc2ir.popInt();
      OPT_Operand object = bc2ir.popRef();
      OPT_RegisterOperand val = gc.temps.makeTemp(VM_Type.ByteType);
      bc2ir.appendInstruction(Load.create(BYTE_LOAD, val, object, offset, 
					  null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.setByteAtOffset) {
      OPT_Operand val = bc2ir.popInt();
      OPT_Operand offset = bc2ir.popInt();
      OPT_Operand object = bc2ir.popRef();
      bc2ir.appendInstruction(Store.create(BYTE_STORE, val, object, offset, 
					   null));
    } else if (methodName == VM_MagicNames.getMemoryWord) {
      OPT_Operand memAddr = bc2ir.popAddress();
      OPT_RegisterOperand val = gc.temps.makeTempInt();
      bc2ir.appendInstruction(Load.create(INT_LOAD, val, 
					  memAddr, 
					  new OPT_IntConstantOperand(0), 
					  null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.getMemoryAddress) {
      OPT_Operand memAddr = bc2ir.popAddress();
      OPT_RegisterOperand val = gc.temps.makeTemp(VM_Type.AddressType);
      bc2ir.appendInstruction(Load.create(INT_LOAD, val, 
					  memAddr, 
					  new OPT_IntConstantOperand(0), 
					  null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.setMemoryWord) {
      OPT_Operand val = bc2ir.popInt();
      OPT_Operand memAddr = bc2ir.popAddress();
      bc2ir.appendInstruction(Store.create(INT_STORE, val, 
					   memAddr, 
					   new OPT_IntConstantOperand(0), 
					   null));
    } else if (methodName == VM_MagicNames.setMemoryAddress) {
      OPT_Operand val = bc2ir.popRef();
      OPT_Operand memAddr = bc2ir.popAddress();
      bc2ir.appendInstruction(Store.create(INT_STORE, val, 
					   memAddr, 
					   new OPT_IntConstantOperand(0), 
					   null));
    } else if (methodName == VM_MagicNames.threadAsCollectorThread) {
      OPT_RegisterOperand reg = 
	gc.temps.makeTemp(OPT_ClassLoaderProxy.findOrCreateType("Lcom/ibm/JikesRVM/VM_CollectorThread;", VM_SystemClassLoader.getVMClassLoader()));
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popRef()));
      bc2ir.push(reg.copyD2U());
//-#if RVM_WITH_CONCURRENT_GC
    } else if (methodName == VM_MagicNames.threadAsRCCollectorThread) {
      OPT_RegisterOperand reg = 
	gc.temps.makeTemp(OPT_ClassLoaderProxy.findOrCreateType("Lcom/ibm/JikesRVM/VM_RCCollectorThread;"));
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popRef()));
      bc2ir.push(reg.copyD2U());
//-#endif
    } else if (methodName == VM_MagicNames.objectAsType) {
      OPT_RegisterOperand reg = 
	gc.temps.makeTemp(OPT_ClassLoaderProxy.VM_Type_type);
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popRef()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.objectAsThread) {
      OPT_RegisterOperand reg = 
	gc.temps.makeTemp(OPT_ClassLoaderProxy.findOrCreateType("Lcom/ibm/JikesRVM/VM_Thread;", VM_SystemClassLoader.getVMClassLoader()));
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popRef()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.objectAsProcessor) {
      OPT_RegisterOperand reg = 
	gc.temps.makeTemp(OPT_ClassLoaderProxy.getVMProcessorType());
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popRef()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.objectAsAddress) {
      OPT_RegisterOperand reg = gc.temps.makeTemp(VM_Type.AddressType);
      bc2ir.appendInstruction(Move.create(INT_MOVE, reg, bc2ir.popRef()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.addressAsObject) {
      OPT_RegisterOperand reg = gc.temps.makeTemp(VM_Type.JavaLangObjectType);
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popAddress()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.addressAsObjectArray) {
      OPT_RegisterOperand reg = gc.temps.makeTemp(OPT_ClassLoaderProxy.JavaLangObjectArrayType);
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popAddress()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.addressAsType) {
      OPT_RegisterOperand reg = gc.temps.makeTemp(OPT_ClassLoaderProxy.VM_Type_type);
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popAddress()));
      bc2ir.push(reg.copyD2U());
      //-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
    } else if (methodName == VM_MagicNames.addressAsBlockControl) {
      OPT_RegisterOperand reg = 
	gc.temps.makeTemp(OPT_ClassLoaderProxy.findOrCreateType("Lcom/ibm/JikesRVM/VM_BlockControl;", VM_SystemClassLoader.getVMClassLoader()));
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popAddress()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.addressAsSizeControl) {
      OPT_RegisterOperand reg = 
	gc.temps.makeTemp(OPT_ClassLoaderProxy.findOrCreateType("Lcom/ibm/JikesRVM/VM_SizeControl;", VM_SystemClassLoader.getVMClassLoader()));
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popAddress()));
      bc2ir.push(reg.copyD2U());
      //-#endif
    } else if (methodName == VM_MagicNames.addressAsThread) {
      OPT_RegisterOperand reg = 
	gc.temps.makeTemp(OPT_ClassLoaderProxy.findOrCreateType("Lcom/ibm/JikesRVM/VM_Thread;", VM_SystemClassLoader.getVMClassLoader()));
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popAddress()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.addressAsRegisters) {
      OPT_RegisterOperand reg = 
	gc.temps.makeTemp(OPT_ClassLoaderProxy.findOrCreateType("Lcom/ibm/JikesRVM/VM_Registers;", VM_SystemClassLoader.getVMClassLoader()));
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popAddress()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.addressAsByteArray) {
      OPT_RegisterOperand reg = 
	gc.temps.makeTemp(OPT_ClassLoaderProxy.ByteArrayType);
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popAddress()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.addressAsIntArray) {
      OPT_RegisterOperand reg = 
	gc.temps.makeTemp(OPT_ClassLoaderProxy.IntArrayType);
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popAddress()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.objectAsByteArray) {
      OPT_RegisterOperand reg = 
	gc.temps.makeTemp(OPT_ClassLoaderProxy.ByteArrayType);
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popRef()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.objectAsShortArray) {
      OPT_RegisterOperand reg = 
	gc.temps.makeTemp(OPT_ClassLoaderProxy.ShortArrayType);
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popRef()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.objectAsIntArray) {
      OPT_RegisterOperand reg = 
	gc.temps.makeTemp(OPT_ClassLoaderProxy.IntArrayType);
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popRef()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.addressAsStack) {
      OPT_RegisterOperand reg = 
	gc.temps.makeTemp(OPT_ClassLoaderProxy.IntArrayType);
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
        gc.temps.makeTemp(OPT_ClassLoaderProxy.JavaLangObjectArrayType);

      bc2ir.appendInstruction(GuardedUnary.create(GET_OBJ_TIB, tibPtr, 
                                                  val, guard));
      OPT_RegisterOperand op0;
      VM_Type argType = val.getType();
      if (argType.isArrayType()) {
        op0 = gc.temps.makeTemp(OPT_ClassLoaderProxy.VM_Array_type);
      } else {
	if (argType == VM_Type.JavaLangObjectType ||
	    argType == VM_Type.JavaLangCloneableType || 
	    argType == VM_Type.JavaIoSerializableType) {
	  // could be an array or a class, so make op0 be a VM_Type.
	  op0 = gc.temps.makeTemp(OPT_ClassLoaderProxy.VM_Type_type);
	} else {
	  op0 = gc.temps.makeTemp(OPT_ClassLoaderProxy.VM_Class_type);
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
      OPT_Operand Code = bc2ir.popRef();
      OPT_RegisterOperand res = null;
      if (methodName == VM_MagicNames.invokeMethodReturningObject) {
        res = gc.temps.makeTemp(VM_Type.JavaLangObjectType);
        bc2ir.push(res.copyD2U());
      } else if (methodName == VM_MagicNames.invokeMethodReturningLong) {
        res = gc.temps.makeTemp(VM_Type.LongType);
        bc2ir.push(res.copyD2U(), VM_Type.LongType);
      } else if (methodName == VM_MagicNames.invokeMethodReturningDouble) {
        res = gc.temps.makeTempDouble();
        bc2ir.push(res.copyD2U(), VM_Type.DoubleType);
      } else if (methodName == VM_MagicNames.invokeMethodReturningFloat) {
        res = gc.temps.makeTempFloat();
        bc2ir.push(res.copyD2U(), VM_Type.FloatType);
      } else if (methodName == VM_MagicNames.invokeMethodReturningInt) {
        res = gc.temps.makeTempInt();
        bc2ir.push(res.copyD2U());
      }
      OPT_MethodOperand met = 
	new OPT_MethodOperand(VM_Entrypoints.reflectiveMethodInvokerInstructionsField,
			      OPT_MethodOperand.STATIC, 
			      VM_Entrypoints.reflectiveMethodInvokerInstructionsField.getOffset());
      OPT_Instruction s = Call.create4(CALL, res, null, met, Code, gprs, 
				       fprs, spills);
      bc2ir.appendInstruction(s);
    } else if (methodName == VM_MagicNames.saveThreadState) {
      OPT_Operand p1 = bc2ir.popRef();
      OPT_MethodOperand mo = 
	new OPT_MethodOperand(VM_Entrypoints.saveThreadStateInstructionsField,
			      OPT_MethodOperand.STATIC, 
			      VM_Entrypoints.saveThreadStateInstructionsField.getOffset());
      bc2ir.appendInstruction(Call.create1(CALL, null, null, mo, p1));
    } else if (methodName == VM_MagicNames.threadSwitch) {
      OPT_Operand p2 = bc2ir.popRef();
      OPT_Operand p1 = bc2ir.popRef();
      OPT_MethodOperand mo = 
	new OPT_MethodOperand(VM_Entrypoints.threadSwitchInstructionsField,
			      OPT_MethodOperand.STATIC, 
			      VM_Entrypoints.threadSwitchInstructionsField.getOffset());
      bc2ir.appendInstruction(Call.create2(CALL, null, null, mo, p1, p2));
    } else if (methodName == VM_MagicNames.restoreHardwareExceptionState) {
      OPT_MethodOperand mo = 
	new OPT_MethodOperand(VM_Entrypoints.restoreHardwareExceptionStateInstructionsField,
			      OPT_MethodOperand.STATIC, 
			      VM_Entrypoints.restoreHardwareExceptionStateInstructionsField.getOffset());
      bc2ir.appendInstruction(Call.create1
			      (CALL, null, null, mo, bc2ir.popRef()));
    } else if (methodName == VM_MagicNames.prepare) {
      OPT_Operand offset = bc2ir.popInt();
      OPT_Operand base = bc2ir.popRef();
      OPT_RegisterOperand val = gc.temps.makeTempInt();
      bc2ir.appendInstruction(Prepare.create(PREPARE, val, base, offset, null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.attempt) {
      OPT_Operand newVal = bc2ir.popInt();
      OPT_Operand oldVal = bc2ir.popInt();
      OPT_Operand offset = bc2ir.popInt();
      OPT_Operand base = bc2ir.popRef();
      OPT_RegisterOperand test = gc.temps.makeTempInt();
      bc2ir.appendInstruction(Attempt.create(ATTEMPT, test, base, offset, oldVal, 
					     newVal, null));
      bc2ir.push(test.copyD2U());
    } else if (methodName == VM_MagicNames.addressFromInt) {
      OPT_RegisterOperand reg = gc.temps.makeTemp(VM_Type.AddressType);
      bc2ir.appendInstruction(Move.create(REF_MOVE, reg, bc2ir.popInt()));
      bc2ir.push(reg.copyD2U());
    } else if (methodName == VM_MagicNames.addressToInt) {
      // a no-op without even a type-conversion
      OPT_RegisterOperand reg = gc.temps.makeTempInt();
      bc2ir.appendInstruction(Move.create(INT_MOVE, reg, bc2ir.popAddress()));
      bc2ir.push(reg.copyD2U());
    }
    else if (methodName == VM_MagicNames.addressAdd) {
      OPT_Operand o2 = bc2ir.popInt();
      OPT_Operand o1 = bc2ir.popAddress();
      OPT_RegisterOperand op0 = gc.temps.makeTemp(VM_Type.AddressType);
      bc2ir.appendInstruction(Binary.create(INT_ADD, op0, o1, o2));
      bc2ir.push(op0.copyD2U());
    }
    else if (methodName == VM_MagicNames.addressSub) {
      OPT_Operand o2 = bc2ir.popInt();
      OPT_Operand o1 = bc2ir.popAddress();
      OPT_RegisterOperand op0 = gc.temps.makeTemp(VM_Type.AddressType);
      bc2ir.appendInstruction(Binary.create(INT_SUB, op0, o1, o2));
      bc2ir.push(op0.copyD2U());
    }
    else if (methodName == VM_MagicNames.addressDiff) {
      OPT_Operand o2 = bc2ir.popAddress();
      OPT_Operand o1 = bc2ir.popAddress();
      OPT_RegisterOperand op0 = gc.temps.makeTempInt();
      bc2ir.appendInstruction(Binary.create(INT_SUB, op0, o1, o2));
      bc2ir.push(op0.copyD2U());
    }
    else if (methodName == VM_MagicNames.addressZero) {
      OPT_RegisterOperand op0 = gc.temps.makeTemp(VM_Type.AddressType);
      bc2ir.appendInstruction(Move.create(INT_MOVE, op0, new OPT_IntConstantOperand(0)));
      bc2ir.push(op0.copyD2U());
    }
    else if (methodName == VM_MagicNames.addressMax) {
      OPT_RegisterOperand op0 = gc.temps.makeTemp(VM_Type.AddressType);
      bc2ir.appendInstruction(Move.create(INT_MOVE, op0, new OPT_IntConstantOperand(-1)));
      bc2ir.push(op0.copyD2U());
    }
    else if (methodName == VM_MagicNames.addressIsZero) {
      OPT_RegisterOperand op0 = gc.temps.makeTemp(VM_Type.AddressType);
      bc2ir.appendInstruction(Move.create(INT_MOVE, op0, new OPT_IntConstantOperand(0)));
      OPT_ConditionOperand cond = OPT_ConditionOperand.EQUAL();
      addressCmpHelper(bc2ir,gc,cond, op0);
    }
    else if (methodName == VM_MagicNames.addressIsMax) {
      OPT_RegisterOperand op0 = gc.temps.makeTemp(VM_Type.AddressType);
      bc2ir.appendInstruction(Move.create(INT_MOVE, op0, new OPT_IntConstantOperand(-1)));
      OPT_ConditionOperand cond = OPT_ConditionOperand.EQUAL();
      addressCmpHelper(bc2ir,gc,cond, op0);
    }
    else if (methodName == VM_MagicNames.addressLT) {
      OPT_ConditionOperand cond = OPT_ConditionOperand.LOWER();
      addressCmpHelper(bc2ir,gc,cond,null);
    }
    else if (methodName == VM_MagicNames.addressLE) {
      OPT_ConditionOperand cond = OPT_ConditionOperand.LOWER_EQUAL();
      addressCmpHelper(bc2ir,gc,cond,null);
    }
    else if (methodName == VM_MagicNames.addressEQ) {
      OPT_ConditionOperand cond = OPT_ConditionOperand.EQUAL();
      addressCmpHelper(bc2ir,gc,cond,null);
    }
    else if (methodName == VM_MagicNames.addressNE) {
      OPT_ConditionOperand cond = OPT_ConditionOperand.NOT_EQUAL();
      addressCmpHelper(bc2ir,gc,cond,null);
    }
    else if (methodName == VM_MagicNames.addressGT) {
      OPT_ConditionOperand cond = OPT_ConditionOperand.HIGHER();
      addressCmpHelper(bc2ir,gc,cond,null);
    }
    else if (methodName == VM_MagicNames.addressGE) {
      OPT_ConditionOperand cond = OPT_ConditionOperand.HIGHER_EQUAL();
      addressCmpHelper(bc2ir,gc,cond,null);
    } else if (methodName == VM_MagicNames.getTimeBase) {
      OPT_RegisterOperand op0 = gc.temps.makeTempLong();
      bc2ir.appendInstruction(Nullary.create(GET_TIME_BASE, op0));
      bc2ir.pushDual(op0.copyD2U());
    } else {
      // Wasn't machine-independent, so try the machine-dependent magics next.
      OPT_GenerateMachineSpecificMagic.generateMagic(bc2ir, gc, meth);
    }
  } // generateMagic

  private static void addressCmpHelper(OPT_BC2IR bc2ir, 
				       OPT_GenerationContext gc, 
				       OPT_ConditionOperand cond,
				       OPT_Operand given_o2) {
      OPT_Operand o2 = given_o2 == null ? bc2ir.popAddress() : given_o2;
      OPT_Operand o1 = bc2ir.popAddress();
      OPT_RegisterOperand res = gc.temps.makeTempInt();
      bc2ir.appendInstruction(BooleanCmp.create(BOOLEAN_CMP, res.copyRO(), o1, o2, cond, new OPT_BranchProfileOperand()));
      bc2ir.push(res.copyD2U());
  }

}
