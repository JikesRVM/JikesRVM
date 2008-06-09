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
package org.jikesrvm.compilers.baseline.ppc;

import org.jikesrvm.compilers.common.assembler.ppc.VM_Assembler;
import org.jikesrvm.ppc.VM_BaselineConstants;
import org.jikesrvm.runtime.VM_Entrypoints;
import org.vmmagic.unboxed.Offset;

/**
 * Class called from baseline compiler to generate architecture specific
 * write barrier for generational garbage collectors.  For baseline
 * compiled methods, the write barrier calls methods of VM_WriteBarrier.
 */
class VM_Barriers implements VM_BaselineConstants {

  // on entry T0, T1, and T2 already contain the appropriate values
  static void compileArrayStoreBarrier(VM_BaselineCompilerImpl comp) {
    VM_Assembler asm = comp.asm;
    asm.emitLAddrToc(S0, VM_Entrypoints.arrayStoreWriteBarrierMethod.getOffset());
    asm.emitMTCTR(S0);
    asm.emitBCCTRL();  // MM_Interface.arrayStoreWriteBarrier(Object ref, int index, Object value)
  }

  //  on entry java stack contains ...|target_ref|ref_to_store|
  // T1 already contains the offset of the field on entry
  static void compilePutfieldBarrier(VM_BaselineCompilerImpl comp, int locationMetadata) {
    VM_Assembler asm = comp.asm;
    asm.emitLAddrToc(S0, VM_Entrypoints.putfieldWriteBarrierMethod.getOffset());
    asm.emitMTCTR(S0);
    comp.peekAddr(T0, 1);               // object base
    asm.emitNullCheck(T0);
    comp.peekAddr(T2, 0);               // value to store
    asm.emitLVAL(T3, locationMetadata);
    asm.emitBCCTRL(); // MM_Interface.putfieldWriteBarrier(T0,T1,T2,T3)
  }

  //  on entry java stack contains ...|target_ref|ref_to_store|
  static void compilePutfieldBarrierImm(VM_BaselineCompilerImpl comp, Offset fieldOffset, int locationMetadata) {
    VM_Assembler asm = comp.asm;
    asm.emitLAddrToc(S0, VM_Entrypoints.putfieldWriteBarrierMethod.getOffset());
    asm.emitMTCTR(S0);
    comp.peekAddr(T0, 1);                 // object base
    asm.emitNullCheck(T0);
    asm.emitLVALAddr(T1, fieldOffset);       // offset
    comp.peekAddr(T2, 0);                 // value to store
    asm.emitLVAL(T3, locationMetadata);
    asm.emitBCCTRL();  // MM_Interface.putfieldWriteBarrier(T0,T1,T2,T3)
  }

  //  on entry java stack contains ...|ref_to_store|
  // T0 already contains the offset of the field on entry
  static void compilePutstaticBarrier(VM_BaselineCompilerImpl comp, int locationMetadata) {
    VM_Assembler asm = comp.asm;
    asm.emitLAddrToc(S0, VM_Entrypoints.putstaticWriteBarrierMethod.getOffset());
    asm.emitMTCTR(S0);
    comp.peekAddr(T1, 0);                 // value to store
    asm.emitLVAL(T2, locationMetadata);
    asm.emitBCCTRL(); // MM_Interface.putstaticWriteBarrier(T0,T1)
  }

  //  on entry java stack contains ...|ref_to_store|
  static void compilePutstaticBarrierImm(VM_BaselineCompilerImpl comp, Offset fieldOffset, int locationMetadata) {
    VM_Assembler asm = comp.asm;
    asm.emitLAddrToc(S0, VM_Entrypoints.putstaticWriteBarrierMethod.getOffset());
    asm.emitMTCTR(S0);
    asm.emitLVALAddr(T0, fieldOffset);    // offset
    comp.peekAddr(T1, 0);                 // value to store
    asm.emitLVAL(T2, locationMetadata);
    asm.emitBCCTRL();  // MM_Interface.putstaticWriteBarrier(T0,T1)
  }

  // on entry T0, T1 already contain the appropriate values
  static void compileArrayLoadBarrier(VM_BaselineCompilerImpl comp) {
    VM_Assembler asm = comp.asm;
    asm.emitLAddrToc(S0, VM_Entrypoints.arrayLoadReadBarrierMethod.getOffset());
    asm.emitMTCTR(S0);
    asm.emitBCCTRL();  // MM_Interface.arrayLoadReadBarrier(T0,T1)
  }

  //  on entry java stack contains ...|source_ref|
  // T1 already contains the offset of the field on entry
  static void compileGetfieldBarrier(VM_BaselineCompilerImpl comp, int locationMetadata) {
    VM_Assembler asm = comp.asm;
    asm.emitLAddrToc(S0, VM_Entrypoints.getfieldReadBarrierMethod.getOffset());
    asm.emitMTCTR(S0);
    comp.peekAddr(T0, 0);               // object base
    asm.emitNullCheck(T0);
    asm.emitLVAL(T2, locationMetadata);
    asm.emitBCCTRL(); // MM_Interface.getfieldReadBarrier(T0,T1,T2)
  }

  //  on entry java stack contains ...|source_ref|
  static void compileGetfieldBarrierImm(VM_BaselineCompilerImpl comp, Offset fieldOffset, int locationMetadata) {
    VM_Assembler asm = comp.asm;
    asm.emitLAddrToc(S0, VM_Entrypoints.getfieldReadBarrierMethod.getOffset());
    asm.emitMTCTR(S0);
    comp.peekAddr(T0, 0);                 // object base
    asm.emitNullCheck(T0);
    asm.emitLVALAddr(T1, fieldOffset);       // offset
    asm.emitLVAL(T2, locationMetadata);
    asm.emitBCCTRL();  // MM_Interface.getfieldReadBarrier(T0,T1,T2)
  }

  //  on entry java stack contains ...|
  // T0 already contains the offset of the field on entry
  static void compileGetstaticBarrier(VM_BaselineCompilerImpl comp, int locationMetadata) {
    VM_Assembler asm = comp.asm;
    asm.emitLAddrToc(S0, VM_Entrypoints.getstaticReadBarrierMethod.getOffset());
    asm.emitMTCTR(S0);
    asm.emitLVAL(T1, locationMetadata);
    asm.emitBCCTRL(); // MM_Interface.getstaticReadBarrier(T0,T1)
  }

  //  on entry java stack contains ...|
  static void compileGetstaticBarrierImm(VM_BaselineCompilerImpl comp, Offset fieldOffset, int locationMetadata) {
    VM_Assembler asm = comp.asm;
    asm.emitLAddrToc(S0, VM_Entrypoints.getstaticReadBarrierMethod.getOffset());
    asm.emitMTCTR(S0);
    asm.emitLVALAddr(T0, fieldOffset);    // offset
    asm.emitLVAL(T1, locationMetadata);
    asm.emitBCCTRL();  // MM_Interface.getstaticReadBarrier(T0,T1)
  }
}
