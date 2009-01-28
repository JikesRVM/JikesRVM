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
package org.jikesrvm.compilers.baseline.ia32;

import org.jikesrvm.Configuration;
import org.jikesrvm.VM;
import org.jikesrvm.ArchitectureSpecific.Assembler;
import org.jikesrvm.ia32.BaselineConstants;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.unboxed.Offset;

/**
 * Class called from baseline compiler to generate architecture specific
 * write barrier for generational garbage collectors.  For baseline
 * compiled methods, the write barrier calls methods of WriteBarrier.
 */
class Barriers implements BaselineConstants {

  static void compileArrayStoreBarrier(Assembler asm) {
    // on entry java stack contains ...|target_array_ref|array_index|ref_to_store|
    BaselineCompilerImpl.genParameterRegisterLoad(asm, 3);
    asm.emitCALL_Abs(Magic.getTocPointer().plus(Entrypoints.arrayStoreWriteBarrierMethod.getOffset()));
  }

  static void compilePutfieldBarrier(Assembler asm, GPR reg, int locationMetadata) {
    //  on entry java stack contains ...|target_ref|ref_to_store|
    //  reg holds offset of field
    if(VM.VerifyAssertions) VM._assert(reg == T0);
    asm.emitPOP_Reg(S0);   // S0 = ref_to_store
    asm.emitPOP_Reg(T1);   // T1 = target_ref
    genNullCheck(asm, T1);
    asm.emitPUSH_Reg(T1);  // target_ref
    asm.emitPUSH_Reg(reg); // offset
    asm.emitPUSH_Reg(S0);  // ref_to_store
    asm.emitPUSH_Imm(locationMetadata);
    BaselineCompilerImpl.genParameterRegisterLoad(asm, 4);
    asm.emitCALL_Abs(Magic.getTocPointer().plus(Entrypoints.putfieldWriteBarrierMethod.getOffset()));
  }

  static void compilePutfieldBarrierImm(Assembler asm, Offset fieldOffset, int locationMetadata) {
    //  on entry java stack contains ...|target_ref|ref_to_store|
    asm.emitPOP_Reg(S0);   // S0 = ref_to_store
    asm.emitPOP_Reg(T1);   // T1 = target_ref
    genNullCheck(asm, T1);
    asm.emitPUSH_Reg(T1);  // target_ref
    asm.emitPUSH_Imm(fieldOffset.toInt());
    asm.emitPUSH_Reg(S0);  // ref_to_store
    asm.emitPUSH_Imm(locationMetadata);
    BaselineCompilerImpl.genParameterRegisterLoad(asm, 4);
    asm.emitCALL_Abs(Magic.getTocPointer().plus(Entrypoints.putfieldWriteBarrierMethod.getOffset()));
  }

  static void compilePutstaticBarrier(Assembler asm, GPR reg, int locationMetadata) {
    //  on entry java stack contains ...|ref_to_store|
    //  reg holds offset of field
    if(VM.VerifyAssertions) VM._assert(reg != S0);
    asm.emitPOP_Reg(S0);   // S0 = ref_to_store
    asm.emitPUSH_Reg(reg); // offset
    asm.emitPUSH_Reg(S0);  // ref_to_store
    asm.emitPUSH_Imm(locationMetadata);
    BaselineCompilerImpl.genParameterRegisterLoad(asm, 3);
    asm.emitCALL_Abs(Magic.getTocPointer().plus(Entrypoints.putstaticWriteBarrierMethod.getOffset()));
  }

  static void compilePutstaticBarrierImm(Assembler asm, Offset fieldOffset, int locationMetadata) {
    //  on entry java stack contains ...|ref_to_store|
    asm.emitPOP_Reg(S0);   // S0 = ref_to_store
    asm.emitPUSH_Imm(fieldOffset.toInt());
    asm.emitPUSH_Reg(S0);  // ref_to_store
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
    genNullCheck(asm, 0);
    asm.emitPUSH_Reg(reg);
    asm.emitPUSH_Imm(locationMetadata);
    BaselineCompilerImpl.genParameterRegisterLoad(asm, 3);
    asm.emitCALL_Abs(Magic.getTocPointer().plus(Entrypoints.getfieldReadBarrierMethod.getOffset()));
    asm.emitPUSH_Reg(T0);
  }

  static void compileGetfieldBarrierImm(Assembler asm, Offset fieldOffset, int locationMetadata) {
    genNullCheck(asm, 0);
    asm.emitPUSH_Imm(fieldOffset.toInt());
    asm.emitPUSH_Imm(locationMetadata);
    BaselineCompilerImpl.genParameterRegisterLoad(asm, 3);
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

  /**
   * Generate a cheap nullcheck by attempting to load the TIB of the object
   * at the given offset to SP.
   */
  private static void genNullCheck(Assembler asm, int offset) {
    if (VM.BuildFor32Addr) {
      if (offset == 0) {
        asm.emitMOV_Reg_RegInd(T1, SP);
      } else {
        asm.emitMOV_Reg_RegDisp(T1, SP, Offset.fromIntZeroExtend(offset));
      }
    } else {
      if (offset == 0) {
        asm.emitMOV_Reg_RegInd_Quad(T1, SP);
      } else {
        asm.emitMOV_Reg_RegDisp_Quad(T1, SP, Offset.fromIntZeroExtend(offset));
      }
    }
    BaselineCompilerImpl.baselineEmitLoadTIB(asm, T1, T1);
  }

  /**
   * Generate a cheap nullcheck by attempting at offset 0 from the object
   * in reg
   */
  private static void genNullCheck(Assembler asm, GPR reg) {
    // do load from reg, without clobbering any registers and using a short encoding
    asm.emitTEST_RegInd_Reg(reg, reg);
  }

  static void compileModifyCheck(Assembler asm, int offset) {
    if (!Configuration.ExtremeAssertions) return;
    // on entry java stack contains ... [SP+offset] -> target_ref
    // on exit: stack is the same
    asm.emitPUSH_RegDisp(SP, Offset.fromIntSignExtend(offset));   // dup
    BaselineCompilerImpl.genParameterRegisterLoad(asm, 1);
    asm.emitCALL_Abs(Magic.getTocPointer().plus(Entrypoints.modifyCheckMethod.getOffset()));
  }
}
