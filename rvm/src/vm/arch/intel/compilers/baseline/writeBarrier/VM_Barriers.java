/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Class called from baseline compiler to generate architecture specific
 * write barrier for generational garbage collectors.  For baseline 
 * compiled methods, the write barrier calls methods of VM_WriteBarrier.
 *
 * Not Yet Implemented For Intel
 *
 * @author Stephen Smith
 */
class VM_Barriers implements VM_BaselineConstants {

  static void compileArrayStoreBarrier (VM_Assembler asm,
					int spSaveAreaOffset) {
    // on entry java stack contains ...|target_array_ref|array_index|ref_to_store|
    // SP -> ref_to_store, SP+8 -> target_ref

    // need to generate a call to VM_WriteBarrier.arrayStoreWriteBarrier
    // (see powerPC version of VM_Barriers)

    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);
  }

  static void compilePutfieldBarrier (VM_Assembler asm,
				      int spSaveAreaOffset,
				      int fieldOffset) {
    //  on entry java stack contains ...|target_ref|ref_to_store|
    //  SP -> ref_to_store, SP+4 -> target_ref

    // need to generate a call to VM_WriteBarrier.resolvedPutfieldWriteBarrier
    // (see powerPC version of VM_Barriers)

    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);
    }

  static void compileUnresolvedPutfieldBarrier (VM_Assembler asm,
						int spSaveAreaOffset,
						int fieldID) {
    //  on entry java stack contains ...|target_ref|ref_to_store|
    //  SP -> ref_to_store, SP+4 -> target_ref

    // need to generate a call to VM_WriteBarrier.unresolvedPutfieldWriteBarrier
    // (see powerPC version of VM_Barriers)

    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);
    }

  // currently do not have a "write barrier for putstatic, emit nothing, for now...
  // (the collectors still scan all of statics/jtoc during each GC)
  //

  static void compilePutstaticBarrier (VM_Assembler asm, int spSaveAreaOffset,
				       int fieldOffset) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);
  }

  static void compileUnresolvedPutstaticBarrier (VM_Assembler asm,
						 int spSaveAreaOffset,
						 int fieldOffset) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);
  }

}
