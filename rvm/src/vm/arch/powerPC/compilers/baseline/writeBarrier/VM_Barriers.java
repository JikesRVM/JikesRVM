/*
 * (C) Copyright IBM Corp. 2001
 */
class VM_Barriers implements VM_BaselineConstants {

  static void compileArrayStoreBarrier (VM_Assembler asm, int spSaveAreaOffset) {
    //  on entry java stack contains ...|target_array_ref|array_index|ref_to_store|
    //  SP -> ref_to_store, SP+8 -> target_ref

    asm.emitLtoc(T0,  VM_Entrypoints.arrayStoreWriteBarrierOffset);
    asm.emitMTLR(T0);
    asm.emitL (T0, 8, SP);         // load arrayref
    asm.emitL (T1, 4, SP);         // load index
    asm.emitL (T2, 0, SP);         // load value
    asm.emitCall(spSaveAreaOffset);// VM_WriteBarrier.arrayStoreWriteBarrier(Object ref, int index, Object value)

    }


  static void compilePutfieldBarrier (VM_Assembler asm, int spSaveAreaOffset, int fieldOffset) {

    //  on entry java stack contains ...|target_ref|ref_to_store|
    //  SP -> ref_to_store, SP+4 -> target_ref

    asm.emitLtoc(T0, VM_Entrypoints.resolvedPutfieldWriteBarrierOffset);
    asm.emitMTLR(T0);
    asm.emitCAL (T1, fieldOffset, 0); // load offset 
    asm.emitL   (T0, 4, SP);          // load objref
    asm.emitL   (T2, 0, SP);          // load value
    asm.emitCall(spSaveAreaOffset);   // VM_WriteBarrier.resolvedPutfieldWriteBarrier(T0,T1,T2)

    }

  static void compileUnresolvedPutfieldBarrier (VM_Assembler asm, int spSaveAreaOffset, int fieldID) {

    //  on entry java stack contains ...|target_ref|ref_to_store|
    //  SP -> ref_to_store, SP+4 -> target_ref

    asm.emitLtoc(T0,  VM_Entrypoints.unresolvedPutfieldWriteBarrierOffset);
    asm.emitMTLR(T0);
    asm.emitCAL (T1, fieldID, 0); // load fieldID 
    asm.emitL   (T0, 4, SP);          // load objref
    asm.emitL   (T2, 0, SP);          // load value
    asm.emitCall(spSaveAreaOffset);   // VM_WriteBarrier.unresolvedPutfieldWriteBarrie(T0,T1,T2)

    }

  // currently do not have a "write barrier for putstatic, emit nothing, for now...
  // (still scanning all of statics/jtoc during each GC)
  //
  static void compilePutstaticBarrier (VM_Assembler asm, int spSaveAreaOffset, int fieldOffset) {
  }

  static void compileUnresolvedPutstaticBarrier (VM_Assembler asm, int spSaveAreaOffset, int fieldOffset) {
  }
 
}
