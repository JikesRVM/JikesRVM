/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;

/**
 * This class implements the machine-specific magics for the opt compiler. 
 *
 * @see OPT_GenerateMagic.java for the machine-independent magics.
 * 
 * @author Dave Grove
 * @author Mauricio Serrano
 */
class OPT_GenerateMachineSpecificMagic 
  implements OPT_Operators, VM_StackframeLayoutConstants {

  /**
   * "Semantic inlining" of methods of the VM_Magic class
   * Based on the methodName, generate a sequence of opt instructions
   * that implement the magic, updating the stack as neccessary
   *
   * @param bc2ir the bc2ir object that is generating the 
   *              ir containing this magic
   * @param gc == bc2ir.gc
   * @param meth the VM_Method that is the magic method
   */
  static void generateMagic (OPT_BC2IR bc2ir, 
			     OPT_GenerationContext gc, 
			     VM_Method meth) 
    throws OPT_MagicNotImplementedException {

    OPT_PhysicalRegisterSet phys = gc.temps.getPhysicalRegisterSet();

    VM_Atom methodName = meth.getName();
    if (methodName == VM_MagicNames.getFramePointer) {
      bc2ir.push(gc.temps.makeFPOp());
      gc.allocFrame = true;
    } else if (methodName == VM_MagicNames.getTocPointer) {
      bc2ir.push(gc.temps.makeJTOCOp(null,null));
    } else if (methodName == VM_MagicNames.getJTOC) {
      bc2ir.push(gc.temps.makeTocOp());
    } else if (methodName == VM_MagicNames.getThreadId) {
      OPT_RegisterOperand TIOp = 
	new OPT_RegisterOperand(phys.getTI(),VM_Type.IntType);
      bc2ir.push(TIOp);
    } else if (methodName == VM_MagicNames.setThreadSwitchBit) {
      bc2ir.appendInstruction(Empty.create(SET_THREAD_SWITCH_BIT));
    } else if (methodName == VM_MagicNames.clearThreadSwitchBit) {
      bc2ir.appendInstruction(Empty.create(CLEAR_THREAD_SWITCH_BIT));
    } else if (methodName == VM_MagicNames.getCallerFramePointer) {
      OPT_Operand fp = bc2ir.popInt();
      OPT_RegisterOperand val = gc.temps.makeTempInt();
      bc2ir.appendInstruction(Load.create(INT_LOAD, val, 
					  fp,
					  new OPT_IntConstantOperand(STACKFRAME_FRAME_POINTER_OFFSET),
					  null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.setCallerFramePointer) {
      OPT_Operand val = bc2ir.popInt();
      OPT_Operand fp = bc2ir.popInt();
      bc2ir.appendInstruction(Store.create(INT_STORE, val, 
					   fp, 
					   new OPT_IntConstantOperand(STACKFRAME_FRAME_POINTER_OFFSET),
					   null));
    } else if (methodName == VM_MagicNames.getCompiledMethodID) {
      OPT_Operand fp = bc2ir.popInt();
      OPT_RegisterOperand val = gc.temps.makeTempInt();
      bc2ir.appendInstruction(Load.create(INT_LOAD, val, 
					  fp,
					  new OPT_IntConstantOperand(STACKFRAME_METHOD_ID_OFFSET),
					  null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.setCompiledMethodID) {
      OPT_Operand val = bc2ir.popInt();
      OPT_Operand fp = bc2ir.popInt();
      bc2ir.appendInstruction(Store.create(INT_STORE, val, 
					   fp, 
					   new OPT_IntConstantOperand(STACKFRAME_METHOD_ID_OFFSET),
					   null));
    } else if (methodName == VM_MagicNames.getNextInstructionAddress) {
      OPT_Operand fp = bc2ir.popInt();
      OPT_RegisterOperand val = gc.temps.makeTempInt();
      bc2ir.appendInstruction(Load.create(INT_LOAD, val, 
					  fp,
					  new OPT_IntConstantOperand(STACKFRAME_NEXT_INSTRUCTION_OFFSET),
					  null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.setNextInstructionAddress) {
      OPT_Operand val = bc2ir.popInt();
      OPT_Operand fp = bc2ir.popInt();
      bc2ir.appendInstruction(Store.create(INT_STORE, val, 
					   fp, 
					   new OPT_IntConstantOperand(STACKFRAME_NEXT_INSTRUCTION_OFFSET),
					   null));
    } else if (methodName == VM_MagicNames.getReturnAddress) {
      OPT_Operand fp = bc2ir.popInt();
      OPT_RegisterOperand callerFP = gc.temps.makeTempInt();
      OPT_RegisterOperand val = gc.temps.makeTempInt();
      bc2ir.appendInstruction(Load.create(INT_LOAD, callerFP, 
					  fp,
					  new OPT_IntConstantOperand(STACKFRAME_FRAME_POINTER_OFFSET),
					  null));
      bc2ir.appendInstruction(Load.create(INT_LOAD, val, 
					  callerFP,
					  new OPT_IntConstantOperand(STACKFRAME_NEXT_INSTRUCTION_OFFSET),
					  null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.setReturnAddress) {
      OPT_Operand val = bc2ir.popInt();
      OPT_Operand fp = bc2ir.popInt();
      OPT_RegisterOperand callerFP = gc.temps.makeTempInt();
      bc2ir.appendInstruction(Load.create(INT_LOAD, callerFP, 
					  fp,
					  new OPT_IntConstantOperand(STACKFRAME_FRAME_POINTER_OFFSET),
					  null));
      bc2ir.appendInstruction(Store.create(INT_STORE, val, 
					   callerFP, 
					   new OPT_IntConstantOperand(STACKFRAME_NEXT_INSTRUCTION_OFFSET),
					   null));
    } else if (methodName == VM_MagicNames.sysCall0) {
      OPT_Operand toc = bc2ir.popInt();
      OPT_Operand ip = bc2ir.popInt();
      OPT_RegisterOperand op0 = gc.temps.makeTempInt();
      bc2ir.appendInstruction(CallSpecial.create0(SYSCALL, op0, ip, toc));
      bc2ir.push(op0.copyD2U());
    } else if (methodName == VM_MagicNames.sysCall_L_0) {
      OPT_Operand toc = bc2ir.popInt();
      OPT_Operand ip = bc2ir.popInt();
      OPT_RegisterOperand op0 = gc.temps.makeTempLong();
      bc2ir.appendInstruction(CallSpecial.create0(SYSCALL, op0, ip, toc));
      bc2ir.pushDual(op0.copyD2U());
    } else if (methodName == VM_MagicNames.sysCall_L_I) {
      OPT_Operand p1 = bc2ir.popInt();
      OPT_Operand toc = bc2ir.popInt();
      OPT_Operand ip = bc2ir.popInt();
      OPT_RegisterOperand op0 = gc.temps.makeTempLong();
      bc2ir.appendInstruction(CallSpecial.create1(SYSCALL, op0, ip, 
						  toc, p1));
      bc2ir.pushDual(op0.copyD2U());
    } else if (methodName == VM_MagicNames.sysCall1) {
      OPT_Operand p1 = bc2ir.popInt();
      OPT_Operand toc = bc2ir.popInt();
      OPT_Operand ip = bc2ir.popInt();
      OPT_RegisterOperand op0 = gc.temps.makeTempInt();
      bc2ir.appendInstruction(CallSpecial.create1(SYSCALL, op0, ip, 
                                                  toc, p1));
      bc2ir.push(op0.copyD2U());
    } else if (methodName == VM_MagicNames.sysCall2) {
      OPT_Operand p2 = bc2ir.popInt();
      OPT_Operand p1 = bc2ir.popInt();
      OPT_Operand toc = bc2ir.popInt();
      OPT_Operand ip = bc2ir.popInt();
      OPT_RegisterOperand op0 = gc.temps.makeTempInt();
      bc2ir.appendInstruction(CallSpecial.create2(SYSCALL, op0, ip, 
						  toc, p1, p2));
      bc2ir.push(op0.copyD2U());
    } else if (methodName == VM_MagicNames.sysCallAD) {
      OPT_Operand p2 = bc2ir.popDouble();
      OPT_Operand p1 = bc2ir.popInt();
      OPT_Operand toc = bc2ir.popInt();
      OPT_Operand ip = bc2ir.popInt();
      OPT_RegisterOperand op0 = gc.temps.makeTempInt();
      bc2ir.appendInstruction(CallSpecial.create2(SYSCALL, op0, ip, 
                                                  toc, p1, p2));
      bc2ir.push(op0.copyD2U());
    } else if (methodName == VM_MagicNames.sysCall3) {
      OPT_Operand p3 = bc2ir.popInt();
      OPT_Operand p2 = bc2ir.popInt();
      OPT_Operand p1 = bc2ir.popInt();
      OPT_Operand toc = bc2ir.popInt();
      OPT_Operand ip = bc2ir.popInt();
      OPT_RegisterOperand op0 = gc.temps.makeTempInt();
      bc2ir.appendInstruction(CallSpecial.create3(SYSCALL, op0, ip, 
						  toc, p1, p2, p3));
      bc2ir.push(op0.copyD2U());
    } else if (methodName == VM_MagicNames.sysCall4) {
      OPT_Operand p4 = bc2ir.popInt();
      OPT_Operand p3 = bc2ir.popInt();
      OPT_Operand p2 = bc2ir.popInt();
      OPT_Operand p1 = bc2ir.popInt();
      OPT_Operand toc = bc2ir.popInt();
      OPT_Operand ip = bc2ir.popInt();
      OPT_RegisterOperand op0 = gc.temps.makeTempInt();
      bc2ir.appendInstruction(CallSpecial.create4(SYSCALL, op0, ip, 
						  toc, p1, p2, p3, p4));
      bc2ir.push(op0.copyD2U());
    } else if (methodName == VM_MagicNames.getTimeBase) {
      OPT_RegisterOperand op0 = gc.temps.makeTempLong();
      bc2ir.appendInstruction(Nullary.create(GET_TIME_BASE, op0));
      bc2ir.pushDual(op0.copyD2U());
    } else if (methodName == VM_MagicNames.isync) {
      if (!gc.options.NO_CACHE_FLUSH)
        bc2ir.appendInstruction(Empty.create(READ_CEILING));
    } else if (methodName == VM_MagicNames.sync) {
      if (!gc.options.NO_CACHE_FLUSH)
        bc2ir.appendInstruction(Empty.create(WRITE_FLOOR));
    } else if (methodName == VM_MagicNames.dcbst) {
      bc2ir.appendInstruction(CacheOp.create(DCBST, bc2ir.popInt()));
    } else if (methodName == VM_MagicNames.icbi) {
      bc2ir.appendInstruction(CacheOp.create(ICBI, bc2ir.popInt()));
    } else {
      // Distinguish between magics that we know we don't implement
      // (and never plan to implement) and those (usually new ones) 
      // that we want to be warned that we don't implement.
      String msg = "Magic method not implemented: " + meth;
      if (methodName == VM_MagicNames.returnToNewStack || 
	  methodName == VM_MagicNames.pragmaNoOptCompile) {
	throw OPT_MagicNotImplementedException.EXPECTED(msg);
      } else {
	throw OPT_MagicNotImplementedException.UNEXPECTED(msg);
      }
    }
  }
}



