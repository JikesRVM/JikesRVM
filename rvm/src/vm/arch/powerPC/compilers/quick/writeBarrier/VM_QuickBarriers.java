/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.quick;

import com.ibm.JikesRVM.*;
import org.vmmagic.unboxed.Offset;

/**
 * Class called from quick compiler to generate architecture specific
 * write barrier for generational garbage collectors.  For quick 
 * compiled methods, the write barrier calls methods of VM_QuickBarriers.
 *
 * @author Chris Hoffmann
 */
class VM_QuickBarriers implements VM_BaselineConstants {

  static void compileArrayStoreBarrier (VM_Assembler asm,
                                        int rArrayRef, int rIndex,
                                        int rValue, int rScratch) {
    asm.emitLAddrToc(rScratch, VM_Entrypoints.arrayStoreWriteBarrierMethod.getOffset());
    asm.emitMTCTR(rScratch);
    if (rArrayRef != T0)
      asm.emitMR (T0, rArrayRef); 
    if (rIndex != T1)
      asm.emitMR (T1, rIndex);    
    if (rValue != T2)
      asm.emitMR (T2, rValue);    
    asm.emitBCCTRL(); // MM_Interface.putfieldWriteBarrier(T0,T1,T2)
    }


  static void compilePutfieldBarrierImm (VM_Assembler asm, Offset fieldOffset,
                                         int rObjRef, int rValue,
                                         int locationMetadata,
                                         int rScratch) {

    asm.emitLAddrToc(rScratch, VM_Entrypoints.putfieldWriteBarrierMethod.getOffset());
    asm.emitMTCTR(rScratch);
    if (rObjRef != T0)
      asm.emitMR (T0, rObjRef);    
    asm.emitLVALAddr (T1, fieldOffset);
    if (rValue != T2)
      asm.emitMR (T2, rValue);    
    asm.emitLVAL(T3, locationMetadata);
    asm.emitBCCTRL(); // MM_Interface.putfieldWriteBarrier(T0,T1,T2,T3)
    }

  static void compilePutfieldBarrier (VM_Assembler asm, int rOffset,
                                      int rObjRef, int rValue,
                                      int locationMetadata,
                                      int rScratch) {

    asm.emitLAddrToc(rScratch, VM_Entrypoints.putfieldWriteBarrierMethod.getOffset());
    asm.emitMTCTR(rScratch);
    if (rObjRef != T0)
      asm.emitMR (T0, rObjRef);    
    if (rObjRef != T1)
      asm.emitMR (T1, rOffset);    
    if (rValue != T2)
      asm.emitMR (T2, rValue);    
    asm.emitLVAL(T3, locationMetadata);
    asm.emitBCCTRL();  // MM_Interface.putfieldWriteBarrier(T0,T1,T2,T3)

    }

  // currently do not have a "write barrier for putstatic, emit nothing, for now...
  // (still scanning all of statics/jtoc during each GC)
  //
  static void compilePutstaticBarrier (VM_Assembler asm, Offset fieldOffset,
                                       int rValue) {
    //-#if RVM_WITH_GCTk_STATIC_BARRIER
    asm.emitLtoc(T0,  VM_Entrypoints.resolvedPutStaticWriteBarrierOffset);
    asm.emitMTLR(T0);
    // Load the offset into T0 
    asm.emitLVALAddr (T0, fieldOffset);
    if (rValue != T1)
      asm.emitMR (T1, rValue);    
    asm.emitCall();   // VM_WriteBarrier.resolvedPutfieldWriteBarrie(T0,T1)
    //-#endif
  }

  static void compileUnresolvedPutstaticBarrier (VM_Assembler asm, int fieldID,
                                                 int rValue) {
    //-#if RVM_WITH_GCTk_STATIC_BARRIER
    asm.emitLtoc(T0,  VM_Entrypoints.unresolvedPutStaticWriteBarrierOffset);
    asm.emitMTLR(T0);
    asm.emitCAL (T0, fieldID, 0);     // load fieldID
    if (rValue != T1)
      asm.emitMR (T1, rValue);    
    asm.emitCall();   // VM_WriteBarrier.unresolvedPutfieldWriteBarrier(T0,T1)
    //-#endif
  }
 
}
