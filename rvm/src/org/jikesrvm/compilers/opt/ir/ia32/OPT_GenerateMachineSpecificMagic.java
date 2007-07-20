/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.ir.ia32;

import org.jikesrvm.classloader.VM_Atom;
import org.jikesrvm.classloader.VM_Field;
import org.jikesrvm.classloader.VM_MethodReference;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.compilers.opt.OPT_MagicNotImplementedException;
import org.jikesrvm.compilers.opt.ir.Binary;
import org.jikesrvm.compilers.opt.ir.CacheOp;
import org.jikesrvm.compilers.opt.ir.Empty;
import org.jikesrvm.compilers.opt.ir.GetField;
import org.jikesrvm.compilers.opt.ir.Load;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.OPT_AddressConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_BC2IR;
import org.jikesrvm.compilers.opt.ir.OPT_GenerationContext;
import org.jikesrvm.compilers.opt.ir.OPT_IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_LocationOperand;
import org.jikesrvm.compilers.opt.ir.OPT_Operand;
import org.jikesrvm.compilers.opt.ir.OPT_Operators;
import org.jikesrvm.compilers.opt.ir.OPT_RegisterOperand;
import org.jikesrvm.compilers.opt.ir.OPT_TrueGuardOperand;
import org.jikesrvm.compilers.opt.ir.Store;
import org.jikesrvm.ia32.VM_ArchConstants;
import org.jikesrvm.ia32.VM_StackframeLayoutConstants;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.runtime.VM_MagicNames;
import org.jikesrvm.runtime.VM_ArchEntrypoints;

/**
 * This class implements the machine-specific magics for the opt compiler.
 *
 * @see org.jikesrvm.compilers.opt.ir.OPT_GenerateMagic for the machine-independent magics
 */
public abstract class OPT_GenerateMachineSpecificMagic implements OPT_Operators, VM_StackframeLayoutConstants {

  /**
   * "Semantic inlining" of methods of the VM_Magic class.
   * Based on the methodName, generate a sequence of opt instructions
   * that implement the magic, updating the stack as necessary
   *
   * @param bc2ir the bc2ir object generating the ir containing this magic
   * @param gc == bc2ir.gc
   * @param meth the VM_Method that is the magic method
   */
  public static boolean generateMagic(OPT_BC2IR bc2ir, OPT_GenerationContext gc, VM_MethodReference meth)
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
        bc2ir.appendInstruction(Move.create(REF_MOVE, gc.temps.makePROp(), val));
      } else {
        String msg = " Unexpected operand VM_Magic.setProcessorRegister";
        throw OPT_MagicNotImplementedException.UNEXPECTED(msg);
      }
    } else if (methodName == VM_MagicNames.getFramePointer) {
      gc.allocFrame = true;
      OPT_RegisterOperand val = gc.temps.makeTemp(VM_TypeReference.Address);
      VM_Field f = VM_ArchEntrypoints.framePointerField;
      OPT_RegisterOperand pr = new OPT_RegisterOperand(phys.getESI(), VM_TypeReference.Int);
      bc2ir.appendInstruction(GetField.create(GETFIELD,
                                              val,
                                              pr.copy(),
                                              new OPT_AddressConstantOperand(f.getOffset()),
                                              new OPT_LocationOperand(f),
                                              new OPT_TrueGuardOperand()));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.getJTOC || methodName == VM_MagicNames.getTocPointer) {
      VM_TypeReference t = (methodName == VM_MagicNames.getJTOC ? VM_TypeReference.IntArray : VM_TypeReference.Address);
      OPT_RegisterOperand val = gc.temps.makeTemp(t);
      OPT_AddressConstantOperand addr = new OPT_AddressConstantOperand(VM_Magic.getTocPointer());
      bc2ir.appendInstruction(Move.create(REF_MOVE, val, addr));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.isync) {
      // nothing required on Intel
    } else if (methodName == VM_MagicNames.sync) {
      // nothing required on Intel
    } else if (methodName == VM_MagicNames.prefetch) {
      bc2ir.appendInstruction(CacheOp.create(PREFETCH, bc2ir.popAddress()));
    } else if (methodName == VM_MagicNames.getCallerFramePointer) {
      OPT_Operand fp = bc2ir.popAddress();
      OPT_RegisterOperand val = gc.temps.makeTemp(VM_TypeReference.Address);
      bc2ir.appendInstruction(Load.create(REF_LOAD,
                                          val,
                                          fp,
                                          new OPT_IntConstantOperand(STACKFRAME_FRAME_POINTER_OFFSET),
                                          null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.setCallerFramePointer) {
      OPT_Operand val = bc2ir.popAddress();
      OPT_Operand fp = bc2ir.popAddress();
      bc2ir.appendInstruction(Store.create(REF_STORE,
                                           val,
                                           fp,
                                           new OPT_IntConstantOperand(STACKFRAME_FRAME_POINTER_OFFSET),
                                           null));
    } else if (methodName == VM_MagicNames.getCompiledMethodID) {
      OPT_Operand fp = bc2ir.popAddress();
      OPT_RegisterOperand val = gc.temps.makeTempInt();
      bc2ir.appendInstruction(Load.create(INT_LOAD,
                                          val,
                                          fp,
                                          new OPT_IntConstantOperand(STACKFRAME_METHOD_ID_OFFSET),
                                          null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == VM_MagicNames.setCompiledMethodID) {
      OPT_Operand val = bc2ir.popInt();
      OPT_Operand fp = bc2ir.popAddress();
      bc2ir.appendInstruction(Store.create(INT_STORE,
                                           val,
                                           fp,
                                           new OPT_IntConstantOperand(STACKFRAME_METHOD_ID_OFFSET),
                                           null));
    } else if (methodName == VM_MagicNames.getReturnAddressLocation) {
      OPT_Operand fp = bc2ir.popAddress();
      OPT_RegisterOperand val = gc.temps.makeTemp(VM_TypeReference.Address);
      bc2ir.appendInstruction(Binary.create(REF_ADD,
                                            val,
                                            fp,
                                            new OPT_IntConstantOperand(STACKFRAME_RETURN_ADDRESS_OFFSET)));
      bc2ir.push(val.copyD2U());
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
