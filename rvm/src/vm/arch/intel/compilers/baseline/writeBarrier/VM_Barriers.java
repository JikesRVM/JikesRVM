/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Class called from baseline compiler to generate architecture specific
 * write barrier for generational garbage collectors.  For baseline 
 * compiled methods, the write barrier calls methods of VM_WriteBarrier.
 *
 * @author Steve Blackburn for Jeff Stylos (UMass)
 * @author Stephen Smith
 */
class VM_Barriers implements VM_BaselineConstants {

  static void compileArrayStoreBarrier (VM_Assembler asm) {
    // on entry java stack contains ...|target_array_ref|array_index|ref_to_store|
    // SP -> ref_to_store, SP+8 -> target_ref
    asm.emitPUSH_RegDisp(SP, 8);
    asm.emitPUSH_RegDisp(SP, 8);  // Push what was originally (SP, 4)
    asm.emitPUSH_RegDisp(SP, 8);  // Push what was originally (SP, 0)
    genParameterRegisterLoad(asm, 3);
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.arrayStoreWriteBarrierMethod.getOffset());
  }

  static void compilePutfieldBarrier (VM_Assembler asm, byte reg, int locationMetadata) {
    //  on entry java stack contains ...|target_ref|ref_to_store|
    //  SP -> ref_to_store, SP+4 -> target_ref
    asm.emitPUSH_RegDisp(SP, 4);
    asm.emitPUSH_Reg(reg);
    asm.emitPUSH_RegDisp(SP, 8);  // Push what was originally (SP, 0)
    asm.emitPUSH_Imm(locationMetadata);
    genParameterRegisterLoad(asm, 4);
    asm.emitCALL_RegDisp (JTOC, VM_Entrypoints.putfieldWriteBarrierMethod.getOffset());
  }

  static void compilePutfieldBarrierImm (VM_Assembler asm, int fieldOffset, int locationMetadata) {
    //  on entry java stack contains ...|target_ref|ref_to_store|
    //  SP -> ref_to_store, SP+4 -> target_ref
    asm.emitPUSH_RegDisp(SP, 4);
    asm.emitPUSH_Imm(fieldOffset);
    asm.emitPUSH_RegDisp(SP, 8);  // Push what was originally (SP, 0)
    asm.emitPUSH_Imm(locationMetadata);
    genParameterRegisterLoad(asm, 4);
    asm.emitCALL_RegDisp (JTOC, VM_Entrypoints.putfieldWriteBarrierMethod.getOffset());
  }

  static void compileModifyCheck (VM_Assembler asm, int offset) {
    if (!VM_Configuration.ExtremeAssertions) return;
    // on entry java stack contains ... [SP+offset] -> target_ref
    // on exit: stack is the same  
    asm.emitPUSH_RegDisp(SP, offset);   // dup
    genParameterRegisterLoad(asm, 1);
    asm.emitCALL_RegDisp (JTOC, VM_Entrypoints.modifyCheckMethod.getOffset());
  }

  // currently do not have a "write barrier for putstatic, emit nothing, for now...
  // (the collectors still scan all of statics/jtoc during each GC)
  //
  static void compilePutstaticBarrier(VM_Assembler asm, byte reg) { }
  static void compilePutstaticBarrierImm(VM_Assembler asm, int fieldOffset) { }


  /**
   * (Taken from VM_Compiler.java)
   *
   * Copy parameters from operand stack into registers.
   * Assumption: parameters are layed out on the stack in order
   * with SP pointing to the last parameter.
   * Also, this method is called before the generation of a helper method call.
   * Assumption: no floating-point parameters.
   * @param params number of parameter words (including "this" if any).
   */
  private final static void genParameterRegisterLoad (VM_Assembler asm, int params){
    if (VM.VerifyAssertions) VM._assert(0 < params);
    if (0 < NUM_PARAMETER_GPRS) {
      asm.emitMOV_Reg_RegDisp(T0, SP, (params-1) << LG_WORDSIZE);
    }
    if (1 < params && 1 < NUM_PARAMETER_GPRS) {
      asm.emitMOV_Reg_RegDisp(T1, SP, (params-2) << LG_WORDSIZE);
    }
  }
}
