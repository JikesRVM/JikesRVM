/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * Class called from baseline compiler to generate architecture specific
 * write barrier for generational garbage collectors.
 *
 * Not Yet Implemented For Intel
 */
class VM_Barriers implements VM_BaselineConstants {

  static void compileArrayStoreBarrier (VM_Assembler asm, int spSaveAreaOffset) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);
  }

  static void compilePutfieldBarrier (VM_Assembler asm, int spSaveAreaOffset, int fieldID) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);
  }

  // the generational collectors do not have a "write barrier for putstatic,
  // emit nothing, for now...
  // (still scanning all of statics/jtoc during each GC)
  //
  static void compilePutstaticBarrier (VM_Assembler asm, int spSaveAreaOffset, int fieldID) {
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);
  }
 
}
