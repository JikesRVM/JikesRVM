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
 * @author Stephen Smith
 * @modified Daniel Frampton
 */
class VM_Barriers implements VM_BaselineConstants {

  // on entry T0, T1, and T2 already contain the appropriate values
  static void compileArrayStoreBarrier (VM_Compiler comp) {
    VM_Assembler asm = comp.asm;
    asm.emitLAddrToc(S0,  VM_Entrypoints.arrayStoreWriteBarrierMethod.getOffset());
    asm.emitMTCTR(S0);
    asm.emitBCCTRL();  // MM_Interface.arrayStoreWriteBarrier(Object ref, int index, Object value)
  }

  //  on entry java stack contains ...|target_ref|ref_to_store|
  // T1 already contains the offset of the field on entry
  static void compilePutfieldBarrier (VM_Compiler comp, int locationMetadata) {
    VM_Assembler asm = comp.asm;
    asm.emitLAddrToc(S0, VM_Entrypoints.putfieldWriteBarrierMethod.getOffset());
    asm.emitMTCTR(S0);
    comp.peekAddr(T0, 1);               // object base
    if (VM.ExplicitlyGuardLowMemory) asm.emitNullCheck(T0);
    comp.peekAddr(T2, 0);               // value to store
    asm.emitLVAL(T3, locationMetadata);
    asm.emitBCCTRL(); // MM_Interface.putfieldWriteBarrier(T0,T1,T2,T3)
  }

  //  on entry java stack contains ...|target_ref|ref_to_store|
  static void compilePutfieldBarrierImm (VM_Compiler comp, int fieldOffset, int locationMetadata) {
    VM_Assembler asm = comp.asm;
    asm.emitLAddrToc(S0, VM_Entrypoints.putfieldWriteBarrierMethod.getOffset());
    asm.emitMTCTR(S0);
    comp.peekAddr(T0, 1);                 // object base
    if (VM.ExplicitlyGuardLowMemory) asm.emitNullCheck(T0);
    asm.emitLVAL (T1, fieldOffset);       // offset 
    comp.peekAddr(T2, 0);                 // value to store
    asm.emitLVAL(T3, locationMetadata);
    asm.emitBCCTRL();  // MM_Interface.putfieldWriteBarrier(T0,T1,T2,T3)
  }

  // currently do not have a "write barrier for putstatic, emit
  // nothing, for now...  (still scanning all of statics/jtoc during
  // each GC)
  //
  static void compilePutstaticBarrier (VM_Compiler comp) { }
  static void compilePutstaticBarrierImm (VM_Compiler comp, int fieldOffset) { }

}
