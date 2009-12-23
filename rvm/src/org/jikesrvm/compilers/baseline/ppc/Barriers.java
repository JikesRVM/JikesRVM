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

import org.jikesrvm.VM;
import org.jikesrvm.classloader.MethodReference;
import org.jikesrvm.classloader.NormalMethod;
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

  // on entry java stack contains ...|array_ref|index|value|
  static void compileArrayStoreBarrierByte(BaselineCompilerImpl comp) {
    comp.emit_resolved_invokestatic((MethodReference) Entrypoints.byteArrayWriteBarrierMethod.getMemberRef());
  }

  // on entry java stack contains ...|array_ref|index|value|
  static void compileArrayStoreBarrierChar(BaselineCompilerImpl comp) {
    comp.emit_resolved_invokestatic((MethodReference) Entrypoints.charArrayWriteBarrierMethod.getMemberRef());
  }

  // on entry java stack contains ...|array_ref|index|value|
  static void compileArrayStoreBarrierDouble(BaselineCompilerImpl comp) {
    comp.emit_resolved_invokestatic((MethodReference) Entrypoints.doubleArrayWriteBarrierMethod.getMemberRef());
  }

  // on entry java stack contains ...|array_ref|index|value|
  static void compileArrayStoreBarrierFloat(BaselineCompilerImpl comp) {
    comp.emit_resolved_invokestatic((MethodReference) Entrypoints.floatArrayWriteBarrierMethod.getMemberRef());
  }

  // on entry java stack contains ...|array_ref|index|value|
  static void compileArrayStoreBarrierInt(BaselineCompilerImpl comp) {
    comp.emit_resolved_invokestatic((MethodReference) Entrypoints.intArrayWriteBarrierMethod.getMemberRef());
  }

  // on entry java stack contains ...|array_ref|index|value|
  static void compileArrayStoreBarrierLong(BaselineCompilerImpl comp) {
    comp.emit_resolved_invokestatic((MethodReference) Entrypoints.longArrayWriteBarrierMethod.getMemberRef());
  }

  // on entry java stack contains ...|array_ref|index|value|
  static void compileArrayStoreBarrierShort(BaselineCompilerImpl comp) {
    comp.emit_resolved_invokestatic((MethodReference) Entrypoints.shortArrayWriteBarrierMethod.getMemberRef());
  }

  // on entry java stack contains ...|target_ref|ref_to_store|
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

  // on entry java stack contains ...|target_ref|ref_to_store|
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

  // on entry java stack contains ...|target_ref|value_to_store|
  // T2 already contains the offset of the field on entry
  private static void putfieldStoreBarrierHelper(BaselineCompilerImpl comp, int locationMetadata, NormalMethod barrier) {
    // value_to_store is an Int or smaller and takes up 1 stack slot
    Assembler asm = comp.asm;
    asm.emitLAddrToc(S0, barrier.getOffset());
    asm.emitMTCTR(S0);
    comp.peekAddr(T0, 1);               // store target_ref in T0
    asm.emitNullCheck(T0);
    comp.peekInt(T1, 0);                // store value_to_store in T1
    asm.emitLVAL(T3, locationMetadata); // store locationMetaData in T3
    asm.emitBCCTRL();                   // call barrier with parameters in (T0,T1,T2,T3)
    comp.discardSlots(2);               // clean up stack
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  private static void putfieldStoreBarrierHelper(BaselineCompilerImpl comp, Offset fieldOffset, int locationMetadata,
                                                 NormalMethod barrier) {
    // value_to_store is an Int or smaller and takes up 1 stack slot
    Assembler asm = comp.asm;
    asm.emitLAddrToc(S0, barrier.getOffset());
    asm.emitMTCTR(S0);
    comp.peekAddr(T0, 1);               // store target_ref in T0
    asm.emitNullCheck(T0);
    comp.peekInt(T1, 0);                // store value_to_store in T1
    asm.emitLVAL(T3, locationMetadata); // store locationMetaData in T3
    asm.emitLVALAddr(T2, fieldOffset);  // store offset in T2
    asm.emitBCCTRL();                   // call barrier with parameters in (T0,T1,T2,T3)
    comp.discardSlots(2);               // clean up stack
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  // T2 already contains the offset of the field on entry
  static void compilePutfieldBarrierBoolean(BaselineCompilerImpl comp, int locationMetadata) {
    putfieldStoreBarrierHelper(comp, locationMetadata, Entrypoints.booleanFieldWriteBarrierMethod);
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  static void compilePutfieldBarrierBooleanImm(BaselineCompilerImpl comp, Offset fieldOffset, int locationMetadata) {
    putfieldStoreBarrierHelper(comp, fieldOffset, locationMetadata, Entrypoints.booleanFieldWriteBarrierMethod);
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  // T2 already contains the offset of the field on entry
  static void compilePutfieldBarrierByte(BaselineCompilerImpl comp, int locationMetadata) {
    putfieldStoreBarrierHelper(comp, locationMetadata, Entrypoints.byteFieldWriteBarrierMethod);
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  static void compilePutfieldBarrierByteImm(BaselineCompilerImpl comp, Offset fieldOffset, int locationMetadata) {
    putfieldStoreBarrierHelper(comp, fieldOffset, locationMetadata, Entrypoints.byteFieldWriteBarrierMethod);
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  // T2 already contains the offset of the field on entry
  static void compilePutfieldBarrierChar(BaselineCompilerImpl comp, int locationMetadata) {
    putfieldStoreBarrierHelper(comp, locationMetadata, Entrypoints.charFieldWriteBarrierMethod);
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  static void compilePutfieldBarrierCharImm(BaselineCompilerImpl comp, Offset fieldOffset, int locationMetadata) {
    putfieldStoreBarrierHelper(comp, fieldOffset, locationMetadata, Entrypoints.charFieldWriteBarrierMethod);
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  // T2 already contains the offset of the field on entry
  static void compilePutfieldBarrierDouble(BaselineCompilerImpl comp, int locationMetadata) {
    Assembler asm = comp.asm;
    asm.emitLAddrToc(S0, Entrypoints.doubleFieldWriteBarrierMethod.getOffset());
    asm.emitMTCTR(S0);
    comp.peekAddr(T0, 2);               // store target_ref in T0
    asm.emitNullCheck(T0);
    comp.peekDouble(F0, 0);             // store value_to_store in F0
    asm.emitMR(T1, T2);                 // move offset in T2 to T1
    asm.emitLVAL(T2, locationMetadata); // store locationMetaData in T2
    asm.emitBCCTRL();                   // call barrier with parameters in (T0,F0,T1,T2)
    comp.discardSlots(3);               // clean up stack
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  static void compilePutfieldBarrierDoubleImm(BaselineCompilerImpl comp, Offset fieldOffset, int locationMetadata) {
    Assembler asm = comp.asm;
    asm.emitLAddrToc(S0, Entrypoints.doubleFieldWriteBarrierMethod.getOffset());
    asm.emitMTCTR(S0);
    comp.peekAddr(T0, 2);               // store target_ref in T0
    asm.emitNullCheck(T0);
    comp.peekDouble(F0, 0);             // store value_to_store in F0
    asm.emitLVALAddr(T1, fieldOffset);  // store offset in T1
    asm.emitLVAL(T2, locationMetadata); // store locationMetaData in T2
    asm.emitBCCTRL();                   // call barrier with parameters in (T0,F0,T1,T2)
    comp.discardSlots(3);               // clean up stack
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  // T2 already contains the offset of the field on entry
  static void compilePutfieldBarrierFloat(BaselineCompilerImpl comp, int locationMetadata) {
    Assembler asm = comp.asm;
    asm.emitLAddrToc(S0, Entrypoints.floatFieldWriteBarrierMethod.getOffset());
    asm.emitMTCTR(S0);
    comp.peekAddr(T0, 1);               // store target_ref in T0
    asm.emitNullCheck(T0);
    comp.peekFloat(F0, 0);              // store value_to_store in F0
    asm.emitMR(T1, T2);                 // copy offset in T2 to T1
    asm.emitLVAL(T2, locationMetadata); // store locationMetaData in T2
    asm.emitBCCTRL();                   // call barrier with parameters in (T0,F0,T1,T2)
    comp.discardSlots(2);               // clean up stack
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  static void compilePutfieldBarrierFloatImm(BaselineCompilerImpl comp, Offset fieldOffset, int locationMetadata) {
    Assembler asm = comp.asm;
    asm.emitLAddrToc(S0, Entrypoints.floatFieldWriteBarrierMethod.getOffset());
    asm.emitMTCTR(S0);
    comp.peekAddr(T0, 1);               // store target_ref in T0
    asm.emitNullCheck(T0);
    comp.peekFloat(F0, 0);              // store value_to_store in F0
    asm.emitLVALAddr(T1, fieldOffset);  // store offset in T1
    asm.emitLVAL(T2, locationMetadata); // store locationMetaData in T2
    asm.emitBCCTRL();                   // call barrier with parameters in (T0,F0,T1,T2)
    comp.discardSlots(2);               // clean up stack
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  // T2 already contains the offset of the field on entry
  static void compilePutfieldBarrierInt(BaselineCompilerImpl comp, int locationMetadata) {
    putfieldStoreBarrierHelper(comp, locationMetadata, Entrypoints.intFieldWriteBarrierMethod);
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  static void compilePutfieldBarrierIntImm(BaselineCompilerImpl comp, Offset fieldOffset, int locationMetadata) {
    putfieldStoreBarrierHelper(comp, fieldOffset, locationMetadata, Entrypoints.intFieldWriteBarrierMethod);
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  // T2 already contains the offset of the field on entry
  static void compilePutfieldBarrierLong(BaselineCompilerImpl comp, int locationMetadata) {
    Assembler asm = comp.asm;
    asm.emitLAddrToc(S0, Entrypoints.longFieldWriteBarrierMethod.getOffset());
    asm.emitMTCTR(S0);
    comp.peekAddr(T0, 2);              // store target_ref in T0
    asm.emitNullCheck(T0);
    if (VM.BuildFor64Addr) {
      comp.peekLong(T1, T1, 0);           // 64 bit store of value into T1
                                          // offset is fine in T2
      asm.emitLVAL(T3, locationMetadata); // store locationMetaData in T3
      asm.emitBCCTRL();                   // call barrier with parameters in (T0,T1,T2,T3)
    } else {
      // GPR's are only 32 bits wide
      asm.emitMR(T3, T2);                 // copy offset into T3
      comp.peekLong(T1, T2, 0);           // peek Long into T1 and T2
      asm.emitLVAL(T4, locationMetadata); // store locationMetaData in T4
      asm.emitBCCTRL();                   // call barrier with parameters in (T0,{T1,T2},T3,T4)
    }
    comp.discardSlots(3);               // clean up stack
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  static void compilePutfieldBarrierLongImm(BaselineCompilerImpl comp, Offset fieldOffset, int locationMetadata) {
    Assembler asm = comp.asm;
    asm.emitLAddrToc(S0, Entrypoints.longFieldWriteBarrierMethod.getOffset());
    asm.emitMTCTR(S0);
    comp.peekAddr(T0, 2);              // store target_ref in T0
    asm.emitNullCheck(T0);
    if (VM.BuildFor64Addr) {
      comp.peekLong(T1, T1, 0);           // 64 bit store of value into T1
      asm.emitLVALAddr(T2, fieldOffset);  // store offset in T2
      asm.emitLVAL(T3, locationMetadata); // store locationMetaData in T3
      asm.emitBCCTRL();                   // call barrier with parameters in (T0,T1,T2,T3)
    } else {
      // GPR's are only 32 bits wide
      comp.peekLong(T1, T2, 0);           // peek Long into T1 and T2
      asm.emitLVALAddr(T3, fieldOffset);  // store offset in T3
      asm.emitLVAL(T4, locationMetadata); // store locationMetaData in T4
      asm.emitBCCTRL();                   // call barrier with parameters in (T0,{T1,T2},T3,T4)
    }
    comp.discardSlots(3);               // clean up stack
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  // T2 already contains the offset of the field on entry
  static void compilePutfieldBarrierShort(BaselineCompilerImpl comp, int locationMetadata) {
    putfieldStoreBarrierHelper(comp, locationMetadata, Entrypoints.shortFieldWriteBarrierMethod);
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  static void compilePutfieldBarrierShortImm(BaselineCompilerImpl comp, Offset fieldOffset, int locationMetadata) {
    putfieldStoreBarrierHelper(comp, fieldOffset, locationMetadata, Entrypoints.shortFieldWriteBarrierMethod);
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  // T2 already contains the offset of the field on entry
  static void compilePutfieldBarrierWord(BaselineCompilerImpl comp, int locationMetadata) {
    putfieldStoreBarrierHelper(comp, locationMetadata, Entrypoints.wordFieldWriteBarrierMethod);
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  static void compilePutfieldBarrierWordImm(BaselineCompilerImpl comp, Offset fieldOffset, int locationMetadata) {
    putfieldStoreBarrierHelper(comp, fieldOffset, locationMetadata, Entrypoints.wordFieldWriteBarrierMethod);
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  // T2 already contains the offset of the field on entry
  static void compilePutfieldBarrierAddress(BaselineCompilerImpl comp, int locationMetadata) {
    putfieldStoreBarrierHelper(comp, locationMetadata, Entrypoints.addressFieldWriteBarrierMethod);
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  static void compilePutfieldBarrierAddressImm(BaselineCompilerImpl comp, Offset fieldOffset, int locationMetadata) {
    putfieldStoreBarrierHelper(comp, fieldOffset, locationMetadata, Entrypoints.addressFieldWriteBarrierMethod);
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  // T2 already contains the offset of the field on entry
  static void compilePutfieldBarrierOffset(BaselineCompilerImpl comp, int locationMetadata) {
    putfieldStoreBarrierHelper(comp, locationMetadata, Entrypoints.offsetFieldWriteBarrierMethod);
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  static void compilePutfieldBarrierOffsetImm(BaselineCompilerImpl comp, Offset fieldOffset, int locationMetadata) {
    putfieldStoreBarrierHelper(comp, fieldOffset, locationMetadata, Entrypoints.offsetFieldWriteBarrierMethod);
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  // T2 already contains the offset of the field on entry
  static void compilePutfieldBarrierExtent(BaselineCompilerImpl comp, int locationMetadata) {
    putfieldStoreBarrierHelper(comp, locationMetadata, Entrypoints.extentFieldWriteBarrierMethod);
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  static void compilePutfieldBarrierExtentImm(BaselineCompilerImpl comp, Offset fieldOffset, int locationMetadata) {
    putfieldStoreBarrierHelper(comp, fieldOffset, locationMetadata, Entrypoints.extentFieldWriteBarrierMethod);
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
