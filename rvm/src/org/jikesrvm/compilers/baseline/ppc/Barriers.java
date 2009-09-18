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
package org.jikesrvm.compilers.baseline.ppc;

import org.jikesrvm.classloader.MethodReference;
import org.jikesrvm.compilers.common.assembler.ppc.Assembler;
import org.jikesrvm.ppc.BaselineConstants;
import org.jikesrvm.runtime.Entrypoints;
import org.vmmagic.unboxed.Offset;

/**
 * Class called from baseline compiler to generate architecture specific
 * write barrier for generational garbage collectors.  For baseline
 * compiled methods, the write barrier calls methods of WriteBarrier.
 */
class Barriers implements BaselineConstants {

  // on entry java stack contains ...|array_ref|index|value|
  static void compileArrayStoreBarrier(BaselineCompilerImpl comp) {
    comp.emit_resolved_invokestatic((MethodReference)Entrypoints.aastoreMethod.getMemberRef());
  }

  //  on entry java stack contains ...|target_ref|ref_to_store|
  // T2 already contains the offset of the field on entry
  static void compilePutfieldBarrier(BaselineCompilerImpl comp, int locationMetadata) {
    Assembler asm = comp.asm;
    asm.emitLAddrToc(S0, Entrypoints.objectFieldWriteBarrierMethod.getOffset());
    asm.emitMTCTR(S0);
    comp.peekAddr(T0, 1);               // object base
    asm.emitNullCheck(T0);
    comp.peekAddr(T1, 0);               // value to store
    asm.emitLVAL(T3, locationMetadata);
    asm.emitBCCTRL(); // MemoryManager.putfieldWriteBarrier(T0,T1,T2,T3)
  }

  //  on entry java stack contains ...|target_ref|ref_to_store|
  static void compilePutfieldBarrierImm(BaselineCompilerImpl comp, Offset fieldOffset, int locationMetadata) {
    Assembler asm = comp.asm;
    asm.emitLAddrToc(S0, Entrypoints.objectFieldWriteBarrierMethod.getOffset());
    asm.emitMTCTR(S0);
    comp.peekAddr(T0, 1);                 // object base
    asm.emitNullCheck(T0);
    asm.emitLVALAddr(T2, fieldOffset);       // offset
    comp.peekAddr(T1, 0);                 // value to store
    asm.emitLVAL(T3, locationMetadata);
    asm.emitBCCTRL();  // MemoryManager.putfieldWriteBarrier(T0,T1,T2,T3)
  }

  //  on entry java stack contains ...|ref_to_store|
  // T1 already contains the offset of the field on entry
  static void compilePutstaticBarrier(BaselineCompilerImpl comp, int locationMetadata) {
    Assembler asm = comp.asm;
    asm.emitLAddrToc(S0, Entrypoints.objectStaticWriteBarrierMethod.getOffset());
    asm.emitMTCTR(S0);
    comp.peekAddr(T0, 0);                 // value to store
    asm.emitLVAL(T2, locationMetadata);
    asm.emitBCCTRL(); // MemoryManager.putstaticWriteBarrier(T0,T1,T2)
  }

  //  on entry java stack contains ...|ref_to_store|
  static void compilePutstaticBarrierImm(BaselineCompilerImpl comp, Offset fieldOffset, int locationMetadata) {
    Assembler asm = comp.asm;
    asm.emitLAddrToc(S0, Entrypoints.objectStaticWriteBarrierMethod.getOffset());
    asm.emitMTCTR(S0);
    asm.emitLVALAddr(T1, fieldOffset);    // offset
    comp.peekAddr(T0, 0);                 // value to store
    asm.emitLVAL(T2, locationMetadata);
    asm.emitBCCTRL();  // MemoryManager.putstaticWriteBarrier(T0,T1,T2)
  }

  // on entry T0, T1 already contain the appropriate values
  static void compileArrayLoadBarrier(BaselineCompilerImpl comp) {
    Assembler asm = comp.asm;
    asm.emitLAddrToc(S0, Entrypoints.objectArrayReadBarrierMethod.getOffset());
    asm.emitMTCTR(S0);
    asm.emitBCCTRL();  // MemoryManager.arrayLoadReadBarrier(T0,T1)
  }

  //  on entry java stack contains ...|source_ref|
  // T1 already contains the offset of the field on entry
  static void compileGetfieldBarrier(BaselineCompilerImpl comp, int locationMetadata) {
    Assembler asm = comp.asm;
    asm.emitLAddrToc(S0, Entrypoints.objectFieldReadBarrierMethod.getOffset());
    asm.emitMTCTR(S0);
    comp.peekAddr(T0, 0);               // object base
    asm.emitNullCheck(T0);
    asm.emitLVAL(T2, locationMetadata);
    asm.emitBCCTRL(); // MemoryManager.getfieldReadBarrier(T0,T1,T2)
  }

  //  on entry java stack contains ...|source_ref|
  static void compileGetfieldBarrierImm(BaselineCompilerImpl comp, Offset fieldOffset, int locationMetadata) {
    Assembler asm = comp.asm;
    asm.emitLAddrToc(S0, Entrypoints.objectFieldReadBarrierMethod.getOffset());
    asm.emitMTCTR(S0);
    comp.peekAddr(T0, 0);                 // object base
    asm.emitNullCheck(T0);
    asm.emitLVALAddr(T1, fieldOffset);       // offset
    asm.emitLVAL(T2, locationMetadata);
    asm.emitBCCTRL();  // MemoryManager.getfieldReadBarrier(T0,T1,T2)
  }

  //  on entry java stack contains ...|
  // T0 already contains the offset of the field on entry
  static void compileGetstaticBarrier(BaselineCompilerImpl comp, int locationMetadata) {
    Assembler asm = comp.asm;
    asm.emitLAddrToc(S0, Entrypoints.objectStaticReadBarrierMethod.getOffset());
    asm.emitMTCTR(S0);
    asm.emitLVAL(T1, locationMetadata);
    asm.emitBCCTRL(); // MemoryManager.getstaticReadBarrier(T0,T1)
  }

  //  on entry java stack contains ...|
  static void compileGetstaticBarrierImm(BaselineCompilerImpl comp, Offset fieldOffset, int locationMetadata) {
    Assembler asm = comp.asm;
    asm.emitLAddrToc(S0, Entrypoints.objectStaticReadBarrierMethod.getOffset());
    asm.emitMTCTR(S0);
    asm.emitLVALAddr(T0, fieldOffset);    // offset
    asm.emitLVAL(T1, locationMetadata);
    asm.emitBCCTRL();  // MemoryManager.getstaticReadBarrier(T0,T1)
  }
}
