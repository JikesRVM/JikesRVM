/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;

/**
 * This class implements the machine-specific magics for the opt compiler.
 *
 * @see OPT_GenerateMagic for the machine-independent magics
 * 
 * @author Dave Grove
 */
class OPT_GenerateMachineSpecificMagic implements OPT_Operators, VM_Constants {

  /**
   * "Semantic inlining" of methods of the VM_Magic class.
   * Based on the methodName, generate a sequence of opt instructions
   * that implement the magic, updating the stack as neccessary
   *
   * @param bc2ir the bc2ir object generating the ir containing this magic
   * @param gc == bc2ir.gc
   * @param meth the VM_Method that is the magic method
   */
  static void generateMagic(OPT_BC2IR bc2ir, 
			    OPT_GenerationContext gc, 
			    VM_Method meth) 
    throws OPT_MagicNotImplementedException {

    VM_Atom methodName = meth.getName();
    OPT_PhysicalRegisterSet phys = gc.temps.getPhysicalRegisterSet();

    if (methodName == VM_MagicNames.getFramePointer) {
      gc.allocFrame = true;
      OPT_RegisterOperand val = gc.temps.makeTempInt();
      VM_Field f = (VM_Field)VM.getMember("LVM_Processor;", "framePointer", "I");
      OPT_RegisterOperand pr = 
	OPT_IRTools.R(gc.temps.getPhysicalRegisterSet().getPR());
      bc2ir.appendInstruction(GetField.create(GETFIELD, val, pr, 
					      new OPT_LocationOperand(f), 
					      new OPT_TrueGuardOperand()));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.setFramePointer) {
      gc.allocFrame = true;
      VM_Field f = (VM_Field)VM.getMember("LVM_Processor;", "framePointer", "I");
      OPT_RegisterOperand pr = 
	OPT_IRTools.R(gc.temps.getPhysicalRegisterSet().getPR());
      bc2ir.appendInstruction(PutField.create(PUTFIELD, bc2ir.popInt(), pr, 
					      new OPT_LocationOperand(f), 
					      new OPT_TrueGuardOperand()));
    } else if (methodName == VM_MagicNames.getJTOC || 
	       methodName == VM_MagicNames.getTocPointer) {
      VM_Type t = (methodName == VM_MagicNames.getJTOC ? OPT_ClassLoaderProxy.IntArrayType : VM_Type.IntType);
      OPT_RegisterOperand val = gc.temps.makeTemp(t);
      OPT_RegisterOperand pr = OPT_IRTools.R(gc.temps.getPhysicalRegisterSet().getPR());
      if (VM.BuildForIA32 && gc.options.FIXED_JTOC) {
        int jtoc = VM_Magic.objectAsAddress(VM_Magic.getJTOC());
        OPT_IntConstantOperand I = new OPT_IntConstantOperand(jtoc);
        bc2ir.appendInstruction(Move.create(REF_MOVE, val, I));
      } else {
        bc2ir.appendInstruction(Unary.create(GET_JTOC, val, pr));
      }
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.isync) {
      // nothing required on Intel
    } else if (methodName == VM_MagicNames.sync) {
      // nothing required on Intel
    } else if (methodName == VM_MagicNames.getThreadId) {
      OPT_RegisterOperand val = gc.temps.makeTempInt();
      OPT_RegisterOperand pr = new OPT_RegisterOperand(phys.getPR(),
                                                       VM_Type.IntType);
      bc2ir.appendInstruction(Load.create(INT_LOAD, val, pr,
					  new
                                          OPT_IntConstantOperand(VM_Entrypoints.threadIdOffset),
					  null));
      bc2ir.push(val.copyD2U());
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
    } else if (methodName == VM_MagicNames.getReturnAddress) {
      OPT_Operand fp = bc2ir.popInt();
      OPT_RegisterOperand val = gc.temps.makeTempInt();
      bc2ir.appendInstruction(Load.create(INT_LOAD, val, 
					  fp,
					  new OPT_IntConstantOperand(STACKFRAME_RETURN_ADDRESS_OFFSET),
					  null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.setReturnAddress) {
      OPT_Operand val = bc2ir.popInt();
      OPT_Operand fp = bc2ir.popInt();
      bc2ir.appendInstruction(Store.create(INT_STORE, val, 
					   fp, 
					   new OPT_IntConstantOperand(STACKFRAME_RETURN_ADDRESS_OFFSET),
					   null));
    } else if (methodName == VM_MagicNames.sysCall0) {
      OPT_Operand ip = bc2ir.popInt();
      OPT_RegisterOperand op0 = gc.temps.makeTempInt();
      bc2ir.appendInstruction(CallSpecial.create0(SYSCALL, op0, ip, null));
      bc2ir.push(op0.copyD2U());
    } else if (methodName == VM_MagicNames.sysCall_L_0) {
      OPT_Operand ip = bc2ir.popInt();
      OPT_RegisterOperand op0 = gc.temps.makeTempLong();
      bc2ir.appendInstruction(CallSpecial.create0(SYSCALL, op0, ip, null));
      bc2ir.pushDual(op0.copyD2U());
    } else if (methodName == VM_MagicNames.sysCall_L_I) {
      OPT_Operand p1 = bc2ir.popInt();
      OPT_Operand ip = bc2ir.popInt();
      OPT_RegisterOperand op0 = gc.temps.makeTempLong();
      bc2ir.appendInstruction(CallSpecial.create1(SYSCALL, op0, ip, null, p1));
      bc2ir.pushDual(op0.copyD2U());
    } else if (methodName == VM_MagicNames.sysCall1) {
      OPT_Operand p1 = bc2ir.popInt();
      OPT_Operand ip = bc2ir.popInt();
      OPT_RegisterOperand op0 = gc.temps.makeTempInt();
      bc2ir.appendInstruction(CallSpecial.create1(SYSCALL, op0, ip, null, p1));
      bc2ir.push(op0.copyD2U());
    } else if (methodName == VM_MagicNames.sysCall2) {
      OPT_Operand p2 = bc2ir.popInt();
      OPT_Operand p1 = bc2ir.popInt();
      OPT_Operand ip = bc2ir.popInt();
      OPT_RegisterOperand op0 = gc.temps.makeTempInt();
      bc2ir.appendInstruction(CallSpecial.create2(SYSCALL, op0, ip, null, 
                                                  p1, p2));
      bc2ir.push(op0.copyD2U());
    } else if (methodName == VM_MagicNames.sysCallAD) {
      OPT_Operand p2 = bc2ir.popDouble();
      OPT_Operand p1 = bc2ir.popInt();
      OPT_Operand ip = bc2ir.popInt();
      OPT_RegisterOperand op0 = gc.temps.makeTempInt();
      bc2ir.appendInstruction(CallSpecial.create2(SYSCALL, op0, ip, null,
                                                  p1, p2));
      bc2ir.push(op0.copyD2U());
    } else if (methodName == VM_MagicNames.sysCall3) {
      OPT_Operand p3 = bc2ir.popInt();
      OPT_Operand p2 = bc2ir.popInt();
      OPT_Operand p1 = bc2ir.popInt();
      OPT_Operand ip = bc2ir.popInt();
      OPT_RegisterOperand op0 = gc.temps.makeTempInt();
      bc2ir.appendInstruction(CallSpecial.create3(SYSCALL, op0, ip, null,
						  p1, p2, p3));
      bc2ir.push(op0.copyD2U());
    } else if (methodName == VM_MagicNames.sysCall4) {
      OPT_Operand p4 = bc2ir.popInt();
      OPT_Operand p3 = bc2ir.popInt();
      OPT_Operand p2 = bc2ir.popInt();
      OPT_Operand p1 = bc2ir.popInt();
      OPT_Operand ip = bc2ir.popInt();
      OPT_RegisterOperand op0 = gc.temps.makeTempInt();
      bc2ir.appendInstruction(CallSpecial.create4(SYSCALL, op0, ip, null,
						  p1, p2, p3, p4));
      bc2ir.push(op0.copyD2U());
    } else if (methodName == VM_MagicNames.roundToZero) {
      bc2ir.appendInstruction(Empty.create(ROUND_TO_ZERO));
    } else if (methodName == VM_MagicNames.clearFloatingPointState) {
      bc2ir.appendInstruction(Empty.create(CLEAR_FLOATING_POINT_STATE));
    } else {
      // Distinguish between magics that we know we don't implement
      // (and never plan to implement) and those (usually new ones) 
      // that we want to be warned that we don't implement.
      String msg = " Magic method not implemented: " + meth;
      if (methodName == VM_MagicNames.returnToNewStack || 
	  methodName == VM_MagicNames.pragmaNoOptCompile) {
	throw OPT_MagicNotImplementedException.EXPECTED(msg);
      } else {
	throw OPT_MagicNotImplementedException.UNEXPECTED(msg);
      }
    }
  }
}
