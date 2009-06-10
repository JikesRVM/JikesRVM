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
package org.jikesrvm.compilers.opt.bc2ir.ppc;

import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.MethodReference;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.MagicNotImplementedException;
import org.jikesrvm.compilers.opt.bc2ir.BC2IR;
import org.jikesrvm.compilers.opt.bc2ir.GenerationContext;
import org.jikesrvm.compilers.opt.ir.Binary;
import org.jikesrvm.compilers.opt.ir.CacheOp;
import org.jikesrvm.compilers.opt.ir.Empty;
import org.jikesrvm.compilers.opt.ir.Load;
import org.jikesrvm.compilers.opt.ir.Operators;
import org.jikesrvm.compilers.opt.ir.Store;
import org.jikesrvm.compilers.opt.ir.operand.AddressConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.ppc.StackframeLayoutConstants;
import org.jikesrvm.runtime.MagicNames;
import org.vmmagic.unboxed.Offset;

/**
 * This class implements the machine-specific magics for the opt compiler.
 *
 * @see org.jikesrvm.compilers.opt.ir.GenerateMagic for the machine-independent magics.
 */
public abstract class GenerateMachineSpecificMagic implements Operators, StackframeLayoutConstants {

  /**
   * "Semantic inlining" of methods of the Magic class
   * Based on the methodName, generate a sequence of opt instructions
   * that implement the magic, updating the stack as necessary
   *
   * @param bc2ir the bc2ir object that is generating the
   *              ir containing this magic
   * @param gc == bc2ir.gc
   * @param meth the RVMMethod that is the magic method
   */
  public static boolean generateMagic(BC2IR bc2ir, GenerationContext gc, MethodReference meth)
      throws MagicNotImplementedException {
    Atom methodName = meth.getName();
    if (methodName == MagicNames.getFramePointer) {
      bc2ir.push(gc.temps.makeFPOp());
      gc.allocFrame = true;
    } else if (methodName == MagicNames.getTocPointer) {
      bc2ir.push(gc.temps.makeJTOCOp(null, null));
    } else if (methodName == MagicNames.getJTOC) {
      bc2ir.push(gc.temps.makeTocOp());
    } else if (methodName == MagicNames.getCallerFramePointer) {
      Operand fp = bc2ir.popAddress();
      RegisterOperand val = gc.temps.makeTemp(TypeReference.Address);
      bc2ir.appendInstruction(Load.create(REF_LOAD,
                                          val,
                                          fp,
                                          new AddressConstantOperand(Offset.fromIntSignExtend(
                                              STACKFRAME_FRAME_POINTER_OFFSET)),
                                          null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == MagicNames.setCallerFramePointer) {
      Operand val = bc2ir.popAddress();
      Operand fp = bc2ir.popAddress();
      bc2ir.appendInstruction(Store.create(REF_STORE,
                                           val,
                                           fp,
                                           new AddressConstantOperand(Offset.fromIntSignExtend(
                                               STACKFRAME_FRAME_POINTER_OFFSET)),
                                           null));
    } else if (methodName == MagicNames.getCompiledMethodID) {
      Operand fp = bc2ir.popAddress();
      RegisterOperand val = gc.temps.makeTempInt();
      bc2ir.appendInstruction(Load.create(INT_LOAD,
                                          val,
                                          fp,
                                          new AddressConstantOperand(Offset.fromIntSignExtend(
                                              STACKFRAME_METHOD_ID_OFFSET)),
                                          null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == MagicNames.setCompiledMethodID) {
      Operand val = bc2ir.popInt();
      Operand fp = bc2ir.popAddress();
      bc2ir.appendInstruction(Store.create(INT_STORE,
                                           val,
                                           fp,
                                           new AddressConstantOperand(Offset.fromIntSignExtend(
                                               STACKFRAME_METHOD_ID_OFFSET)),
                                           null));
    } else if (methodName == MagicNames.getNextInstructionAddress) {
      Operand fp = bc2ir.popAddress();
      RegisterOperand val = gc.temps.makeTemp(TypeReference.Address);
      bc2ir.appendInstruction(Load.create(REF_LOAD,
                                          val,
                                          fp,
                                          new AddressConstantOperand(Offset.fromIntSignExtend(
                                              STACKFRAME_NEXT_INSTRUCTION_OFFSET)),
                                          null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == MagicNames.getReturnAddressLocation) {
      Operand fp = bc2ir.popAddress();
      RegisterOperand callerFP = gc.temps.makeTemp(TypeReference.Address);
      RegisterOperand val = gc.temps.makeTemp(TypeReference.Address);
      bc2ir.appendInstruction(Load.create(REF_LOAD,
                                          callerFP,
                                          fp,
                                          new AddressConstantOperand(Offset.fromIntSignExtend(
                                              STACKFRAME_FRAME_POINTER_OFFSET)),
                                          null));
      bc2ir.appendInstruction(Binary.create(REF_ADD,
                                            val,
                                            callerFP.copyRO(),
                                            new IntConstantOperand(STACKFRAME_NEXT_INSTRUCTION_OFFSET)));
      bc2ir.push(val.copyD2U());
    } else if (methodName == MagicNames.isync) {
      bc2ir.appendInstruction(Empty.create(READ_CEILING));
    } else if (methodName == MagicNames.sync) {
      bc2ir.appendInstruction(Empty.create(WRITE_FLOOR));
    } else if (methodName == MagicNames.pause) {
      // IA-specific
    } else if (methodName == MagicNames.dcbst) {
      bc2ir.appendInstruction(CacheOp.create(DCBST, bc2ir.popInt()));
    } else if (methodName == MagicNames.dcbt) {
      bc2ir.appendInstruction(CacheOp.create(DCBT, bc2ir.popInt()));
    } else if (methodName == MagicNames.dcbtst) {
      bc2ir.appendInstruction(CacheOp.create(DCBTST, bc2ir.popInt()));
    } else if (methodName == MagicNames.dcbz) {
      bc2ir.appendInstruction(CacheOp.create(DCBZ, bc2ir.popInt()));
    } else if (methodName == MagicNames.dcbzl) {
      bc2ir.appendInstruction(CacheOp.create(DCBZL, bc2ir.popInt()));
    } else if (methodName == MagicNames.icbi) {
      bc2ir.appendInstruction(CacheOp.create(ICBI, bc2ir.popInt()));
    } else {
      // Distinguish between magics that we know we don't implement
      // (and never plan to implement) and those (usually new ones)
      // that we want to be warned that we don't implement.
      String msg = "Magic method not implemented: " + meth;
      if (methodName == MagicNames.returnToNewStack) {
        throw MagicNotImplementedException.EXPECTED(msg);
      } else {
        return false;
        // throw MagicNotImplementedException.UNEXPECTED(msg);
      }
    }
    return true;
  }
}



