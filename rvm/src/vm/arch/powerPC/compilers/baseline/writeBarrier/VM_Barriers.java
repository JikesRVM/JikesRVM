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
 */
class VM_Barriers implements VM_BaselineConstants {

  static void compileArrayStoreBarrier (VM_Assembler asm, int spSaveAreaOffset) {
    //  on entry java stack contains ...|target_array_ref|array_index|ref_to_store|
    //  SP -> ref_to_store, SP+8 -> target_ref
    asm.emitLWZtoc(T0,  VM_Entrypoints.arrayStoreWriteBarrierMethod.getOffset());
    asm.emitMTCTR(T0);
    asm.emitLWZ(T0, 8, SP);         // load arrayref
    asm.emitLWZ(T1, 4, SP);         // load index
    asm.emitLWZ(T2, 0, SP);         // load value
    asm.emitCall(spSaveAreaOffset);// VM_WriteBarrier.arrayStoreWriteBarrier(Object ref, int index, Object value)
  }

  static void compilePutfieldBarrier (VM_Assembler asm, int spSaveAreaOffset) {
    //  on entry java stack contains ...|target_ref|ref_to_store|
    //  SP -> ref_to_store, SP+4 -> target_ref
    // T1 contains the offset of the field
    asm.emitLWZtoc(T0, VM_Entrypoints.putfieldWriteBarrierMethod.getOffset());
    asm.emitMTCTR(T0);
    asm.emitLWZ  (T0, 4, SP);          // load objref
    asm.emitLWZ  (T2, 0, SP);          // load value
    asm.emitCall(spSaveAreaOffset);   // VM_WriteBarrier.putfieldWriteBarrier(T0,T1,T2)
  }

  static void compilePutfieldBarrierImm (VM_Assembler asm, int spSaveAreaOffset, int fieldOffset) {
    //  on entry java stack contains ...|target_ref|ref_to_store|
    //  SP -> ref_to_store, SP+4 -> target_ref
    asm.emitLWZtoc(T0, VM_Entrypoints.putfieldWriteBarrierMethod.getOffset());
    asm.emitMTCTR(T0);
    asm.emitADDI (T1, fieldOffset, 0); // load offset 
    asm.emitLWZ  (T0, 4, SP);          // load objref
    asm.emitLWZ  (T2, 0, SP);          // load value
    asm.emitCall(spSaveAreaOffset);   // VM_WriteBarrier.putfieldWriteBarrier(T0,T1,T2)
  }

  // currently do not have a "write barrier for putstatic, emit nothing, for now...
  // (still scanning all of statics/jtoc during each GC)
  //
  static void compilePutstaticBarrier (VM_Assembler asm, int spSaveAreaOffset) { }
  static void compilePutstaticBarrierImm (VM_Assembler asm, int spSaveAreaOffset, int fieldOffset) { }
}
