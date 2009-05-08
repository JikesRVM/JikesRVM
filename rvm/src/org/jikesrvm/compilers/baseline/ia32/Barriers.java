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
package org.jikesrvm.compilers.baseline.ia32;

import org.jikesrvm.Configuration;
import org.jikesrvm.ArchitectureSpecific.Assembler;
import org.jikesrvm.ia32.BaselineConstants;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.pragma.Inline;

/**
 * Class called from baseline compiler to generate architecture specific
 * write barrier for generational garbage collectors.  For baseline
 * compiled methods, the write barrier calls methods of WriteBarrier.
 */
class Barriers implements BaselineConstants {

  /**
   * Generate code to perform an array store barrier. On entry the stack holds:
   * arrayRef, index, value.
   *
   * @param asm the assembler to generate the code in
   */
  static void compileArrayStoreBarrier(Assembler asm) {
    BaselineCompilerImpl.genParameterRegisterLoad(asm, 3);
    asm.emitCALL_Abs(Magic.getTocPointer().plus(Entrypoints.aastoreMethod.getOffset()));
  }

  /**
   * Generate code to perform a putfield barrier. On entry the stack holds:
   * object, value.
   *
   * @param asm the assembler to generate the code in
   * @param offset the register holding the offset of the field
   * @param locationMetadata meta-data about the location
   */
  @Inline
  static void compilePutfieldBarrier(Assembler asm, GPR offset, int locationMetadata) {
    asm.emitPUSH_Reg(offset);
    asm.emitPUSH_Imm(locationMetadata);
    BaselineCompilerImpl.genParameterRegisterLoad(asm, 4);
    genNullCheck(asm, T0);
    asm.emitCALL_Abs(Magic.getTocPointer().plus(Entrypoints.putfieldWriteBarrierMethod.getOffset()));
  }

  /**
   * Generate code to perform a putfield barrier when the field is at a known
   * offset. On entry the stack holds: object, value.
   *
   * @param asm the assembler to generate the code in
   * @param fieldOffset the offset of the field
   * @param locationMetadata meta-data about the location
   */
  @Inline
  static void compilePutfieldBarrierImm(Assembler asm, Offset fieldOffset, int locationMetadata) {
    asm.emitPUSH_Imm(fieldOffset.toInt());
    asm.emitPUSH_Imm(locationMetadata);
    BaselineCompilerImpl.genParameterRegisterLoad(asm, 4);
    genNullCheck(asm, T0);
    asm.emitCALL_Abs(Magic.getTocPointer().plus(Entrypoints.putfieldWriteBarrierMethod.getOffset()));
  }

  static void compilePutstaticBarrier(Assembler asm, GPR reg, int locationMetadata) {
    //  on entry java stack contains ...|ref_to_store|
    //  reg holds offset of field
    asm.emitPUSH_Reg(reg); // offset
    asm.emitPUSH_Imm(locationMetadata);
    BaselineCompilerImpl.genParameterRegisterLoad(asm, 3);
    asm.emitCALL_Abs(Magic.getTocPointer().plus(Entrypoints.putstaticWriteBarrierMethod.getOffset()));
 }

  static void compilePutstaticBarrierImm(Assembler asm, Offset fieldOffset, int locationMetadata) {
    //  on entry java stack contains ...|ref_to_store|
    asm.emitPUSH_Imm(fieldOffset.toInt());
    asm.emitPUSH_Imm(locationMetadata);
    BaselineCompilerImpl.genParameterRegisterLoad(asm, 3);
    asm.emitCALL_Abs(Magic.getTocPointer().plus(Entrypoints.putstaticWriteBarrierMethod.getOffset()));
  }

  static void compileArrayLoadBarrier(Assembler asm, boolean pushResult) {
    // on entry java stack contains ...|target_array_ref|array_index|
    // SP -> index, SP+4 -> target_ref
    BaselineCompilerImpl.genParameterRegisterLoad(asm, 2);
    asm.emitCALL_Abs(Magic.getTocPointer().plus(Entrypoints.arrayLoadReadBarrierMethod.getOffset()));
    if (pushResult) asm.emitPUSH_Reg(T0);
  }

  static void compileGetfieldBarrier(Assembler asm, GPR reg, int locationMetadata) {
    //  on entry java stack contains ...|target_ref|
    //  SP -> target_ref
    asm.emitPUSH_Reg(reg);
    asm.emitPUSH_Imm(locationMetadata);
    BaselineCompilerImpl.genParameterRegisterLoad(asm, 3);
    genNullCheck(asm, T0);
    asm.emitCALL_Abs(Magic.getTocPointer().plus(Entrypoints.getfieldReadBarrierMethod.getOffset()));
    asm.emitPUSH_Reg(T0);
  }

  static void compileGetfieldBarrierImm(Assembler asm, Offset fieldOffset, int locationMetadata) {
    asm.emitPUSH_Imm(fieldOffset.toInt());
    asm.emitPUSH_Imm(locationMetadata);
    BaselineCompilerImpl.genParameterRegisterLoad(asm, 3);
    genNullCheck(asm, T0);
    asm.emitCALL_Abs(Magic.getTocPointer().plus(Entrypoints.getfieldReadBarrierMethod.getOffset()));
    asm.emitPUSH_Reg(T0);
  }

  static void compileGetstaticBarrier(Assembler asm, GPR reg, int locationMetadata) {
    asm.emitPUSH_Reg(reg);
    asm.emitPUSH_Imm(locationMetadata);
    BaselineCompilerImpl.genParameterRegisterLoad(asm, 2);
    asm.emitCALL_Abs(Magic.getTocPointer().plus(Entrypoints.getstaticReadBarrierMethod.getOffset()));
    asm.emitPUSH_Reg(T0);
  }

  static void compileGetstaticBarrierImm(Assembler asm, Offset fieldOffset, int locationMetadata) {
    asm.emitPUSH_Imm(fieldOffset.toInt());
    asm.emitPUSH_Imm(locationMetadata);
    BaselineCompilerImpl.genParameterRegisterLoad(asm, 2);
    asm.emitCALL_Abs(Magic.getTocPointer().plus(Entrypoints.getstaticReadBarrierMethod.getOffset()));
    asm.emitPUSH_Reg(T0);
  }

  static void compileModifyCheck(Assembler asm, int offset) {
    if (!Configuration.ExtremeAssertions) return;
    // on entry java stack contains ... [SP+offset] -> target_ref
    // on exit: stack is the same
    asm.emitPUSH_RegDisp(SP, Offset.fromIntSignExtend(offset));   // dup
    BaselineCompilerImpl.genParameterRegisterLoad(asm, 1);
    asm.emitCALL_Abs(Magic.getTocPointer().plus(Entrypoints.modifyCheckMethod.getOffset()));
  }

  /**
   * Generate an implicit null check by loading the TIB of the given object.
   * Scribbles over S0.
   *
   * @param asm the assembler to generate into
   * @param objRefReg the register containing the reference
   */
  private static void genNullCheck(Assembler asm, GPR objRefReg) {
    BaselineCompilerImpl.baselineEmitLoadTIB(asm, S0, T0);
  }
}
