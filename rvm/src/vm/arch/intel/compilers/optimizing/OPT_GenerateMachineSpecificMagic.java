/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM.opt.ir;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.OPT_ClassLoaderProxy;
import com.ibm.JikesRVM.opt.OPT_MagicNotImplementedException;

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
   * that implement the magic, updating the stack as necessary
   *
   * @param bc2ir the bc2ir object generating the ir containing this magic
   * @param gc == bc2ir.gc
   * @param meth the VM_Method that is the magic method
   */
  static boolean generateMagic(OPT_BC2IR bc2ir, 
                               OPT_GenerationContext gc, 
                               VM_MethodReference meth) 
    throws OPT_MagicNotImplementedException {

    VM_Atom methodName = meth.getName();
    OPT_PhysicalRegisterSet phys = gc.temps.getPhysicalRegisterSet();

    if (methodName == VM_MagicNames.getESIAsProcessor) {
      OPT_RegisterOperand rop = gc.temps.makePROp();
      bc2ir.markGuardlessNonNull(rop);
      bc2ir.push(rop);
    } else if (methodName == VM_MagicNames.setESIAsProcessor) {
      OPT_Operand val = bc2ir.popRef();
      if (val instanceof OPT_RegisterOperand) {
        bc2ir.appendInstruction(Move.create(REF_MOVE, 
                                            gc.temps.makePROp(), 
                                            val));
      } else {
        String msg = " Unexpected operand VM_Magic.setProcessorRegister";
        throw OPT_MagicNotImplementedException.UNEXPECTED(msg);
      }
    } else if (methodName == VM_MagicNames.getFramePointer) {
      gc.allocFrame = true;
      OPT_RegisterOperand val = gc.temps.makeTemp(VM_TypeReference.Address);
      VM_Field f = VM_Entrypoints.framePointerField;
      OPT_RegisterOperand pr = null;
      if (VM.dedicatedESI) {
        pr = OPT_IRTools.R(phys.getESI());
      } else {
        pr = gc.temps.makeTemp(VM_TypeReference.VM_Processor);
        bc2ir.appendInstruction(Nullary.create(GET_CURRENT_PROCESSOR,pr)); 
      }
      bc2ir.appendInstruction(GetField.create(GETFIELD, val, pr.copy(),
                                              new OPT_AddressConstantOperand(f.getOffset()),
                                              new OPT_LocationOperand(f), 
                                              new OPT_TrueGuardOperand()));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.getJTOC || 
               methodName == VM_MagicNames.getTocPointer) {
      VM_TypeReference t = (methodName == VM_MagicNames.getJTOC ? VM_TypeReference.IntArray : VM_TypeReference.Address);
      OPT_RegisterOperand val = gc.temps.makeTemp(t);
      if (gc.options.FIXED_JTOC) {
        OPT_AddressConstantOperand addr = new OPT_AddressConstantOperand(VM_Magic.getTocPointer());
        bc2ir.appendInstruction(Move.create(REF_MOVE, val, addr));
      } else {
        OPT_RegisterOperand pr = null;
        if (VM.dedicatedESI) {
          pr = OPT_IRTools.R(phys.getESI());
        } else {
          pr = gc.temps.makeTemp(VM_TypeReference.VM_Processor);
          bc2ir.appendInstruction(Nullary.create(GET_CURRENT_PROCESSOR,pr)); 
        }
        bc2ir.appendInstruction(Unary.create(GET_JTOC, val, pr.copy()));
      }
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.isync) {
      // nothing required on Intel
    } else if (methodName == VM_MagicNames.sync) {
      // nothing required on Intel
    } else if (methodName == VM_MagicNames.getCallerFramePointer) {
      OPT_Operand fp = bc2ir.popAddress();
      OPT_RegisterOperand val = gc.temps.makeTemp(VM_TypeReference.Address);
      bc2ir.appendInstruction(Load.create(REF_LOAD, val, 
                                          fp,
                                          new OPT_IntConstantOperand(STACKFRAME_FRAME_POINTER_OFFSET),
                                          null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.setCallerFramePointer) {
      OPT_Operand val = bc2ir.popAddress();
      OPT_Operand fp = bc2ir.popAddress();
      bc2ir.appendInstruction(Store.create(REF_STORE, val, 
                                           fp, 
                                           new OPT_IntConstantOperand(STACKFRAME_FRAME_POINTER_OFFSET),
                                           null));
    } else if (methodName == VM_MagicNames.getCompiledMethodID) {
      OPT_Operand fp = bc2ir.popAddress();
      OPT_RegisterOperand val = gc.temps.makeTempInt();
      bc2ir.appendInstruction(Load.create(INT_LOAD, val, 
                                          fp,
                                          new OPT_IntConstantOperand(STACKFRAME_METHOD_ID_OFFSET),
                                          null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.setCompiledMethodID) {
      OPT_Operand val = bc2ir.popInt();
      OPT_Operand fp = bc2ir.popAddress();
      bc2ir.appendInstruction(Store.create(INT_STORE, val, 
                                           fp, 
                                           new OPT_IntConstantOperand(STACKFRAME_METHOD_ID_OFFSET),
                                           null));
    } else if (methodName == VM_MagicNames.getReturnAddressLocation) {
      OPT_Operand fp = bc2ir.popAddress();
      OPT_RegisterOperand val = gc.temps.makeTemp(VM_TypeReference.Address);
      bc2ir.appendInstruction(Binary.create(REF_ADD, val, 
                                            fp,
                                            new OPT_IntConstantOperand(STACKFRAME_RETURN_ADDRESS_OFFSET)));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.roundToZero) {
      bc2ir.appendInstruction(Empty.create(ROUND_TO_ZERO));
    } else if (methodName == VM_MagicNames.clearFloatingPointState) {
      bc2ir.appendInstruction(Empty.create(CLEAR_FLOATING_POINT_STATE));
    } else {
      // Distinguish between magics that we know we don't implement
      // (and never plan to implement) and those (usually new ones) 
      // that we want to be warned that we don't implement.
      String msg = " Magic method not implemented: " + meth;
      if (methodName == VM_MagicNames.returnToNewStack) {
        throw OPT_MagicNotImplementedException.EXPECTED(msg);
      } else {
        return false;
        // throw OPT_MagicNotImplementedException.UNEXPECTED(msg);
      }
    }
    return true;
  }
}
