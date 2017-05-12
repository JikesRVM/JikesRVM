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
package org.jikesrvm.compilers.baseline.arm;

import static org.jikesrvm.compilers.common.assembler.arm.AssemblerConstants.COND.ALWAYS;
import static org.jikesrvm.arm.BaselineConstants.T0;
import static org.jikesrvm.arm.BaselineConstants.T1;
import static org.jikesrvm.arm.BaselineConstants.T2;
import static org.jikesrvm.arm.BaselineConstants.T3;
import static org.jikesrvm.arm.BaselineConstants.F0;
import static org.jikesrvm.arm.BaselineConstants.F0and1;
import static org.jikesrvm.arm.RegisterConstants.JTOC;
import static org.jikesrvm.arm.RegisterConstants.LR;

import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.common.assembler.arm.Assembler;
import org.jikesrvm.runtime.Entrypoints;

/**
 * Class called from baseline compiler to generate architecture specific
 * write barrier for generational garbage collectors.  For baseline
 * compiled methods, the write barrier calls methods of WriteBarrier.
 */
class Barriers {

  /*
   * Array read/write barriers
   */

  // Assumes T0 = array ref, T1 = array index; On return T0 = value
  static void compileArrayLoadBarrier(BaselineCompilerImpl comp) {
    Assembler asm = comp.asm;
    asm.generateOffsetLoad(ALWAYS, LR, JTOC, Entrypoints.objectArrayReadBarrierMethod.getOffset());
    asm.emitBLX(ALWAYS, LR);      // Call barrier with parameters in (T0,T1)
  }

  // Assumes T0 = array index, T1 = array ref, T2 = value to store
  static void compileArrayStoreBarrierByte(BaselineCompilerImpl comp) {
    arrayStoreBarrierHelper(comp, Entrypoints.byteArrayWriteBarrierMethod);
  }

  // Assumes T0 = array index, T1 = array ref, T2 = value to store
  static void compileArrayStoreBarrierChar(BaselineCompilerImpl comp) {
    arrayStoreBarrierHelper(comp, Entrypoints.charArrayWriteBarrierMethod);
  }

  // Assumes T0 = array index, T1 = array ref, F0and1 = value to store
  static void compileArrayStoreBarrierDouble(BaselineCompilerImpl comp) {
    arrayStoreBarrierHelper(comp, Entrypoints.doubleArrayWriteBarrierMethod);
  }

  // Assumes T0 = array index, T1 = array ref, F0 = value to store
  static void compileArrayStoreBarrierFloat(BaselineCompilerImpl comp) {
    arrayStoreBarrierHelper(comp, Entrypoints.floatArrayWriteBarrierMethod);
  }

  // Assumes T0 = array index, T1 = array ref, T2 = value to store
  static void compileArrayStoreBarrierInt(BaselineCompilerImpl comp) {
    arrayStoreBarrierHelper(comp, Entrypoints.intArrayWriteBarrierMethod);
  }

  // Assumes T0 = array index, T1 = array ref, F0and1 = value to store
  static void compileArrayStoreBarrierLong(BaselineCompilerImpl comp) {
    arrayStoreBarrierHelper(comp, Entrypoints.longArrayWriteBarrierMethod);
  }

  // Assumes T0 = array index, T1 = array ref, T2 = value to store
  static void compileArrayStoreBarrierShort(BaselineCompilerImpl comp) {
    arrayStoreBarrierHelper(comp, Entrypoints.shortArrayWriteBarrierMethod);
  }

  // Assumes T0 = array index, T1 = array ref, T2/F0/F0and1 = value to store (depending on type)
  private static void arrayStoreBarrierHelper(BaselineCompilerImpl comp, NormalMethod barrier) {
    Assembler asm = comp.asm;
    asm.generateOffsetLoad(ALWAYS, LR, JTOC, barrier.getOffset());
    asm.emitBLX(ALWAYS, LR);      // Call barrier with parameters in (T0,T1,T2/F0/F0and1)
  }

  /*
   * Putfield barriers
   */

  // on entry java stack contains ...|target_ref|ref_to_store|
  // T2 already contains the offset of the field on entry
  static void compilePutfieldBarrier(BaselineCompilerImpl comp, int locationMetadata) {
    putfieldStoreBarrierHelper(comp, locationMetadata, Entrypoints.objectFieldWriteBarrierMethod);
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  // T2 already contains the offset of the field on entry
  static void compilePutfieldBarrierBoolean(BaselineCompilerImpl comp, int locationMetadata) {
    putfieldStoreBarrierHelper(comp, locationMetadata, Entrypoints.booleanFieldWriteBarrierMethod);
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  // T2 already contains the offset of the field on entry
  static void compilePutfieldBarrierByte(BaselineCompilerImpl comp, int locationMetadata) {
    putfieldStoreBarrierHelper(comp, locationMetadata, Entrypoints.byteFieldWriteBarrierMethod);
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  // T2 already contains the offset of the field on entry
  static void compilePutfieldBarrierChar(BaselineCompilerImpl comp, int locationMetadata) {
    putfieldStoreBarrierHelper(comp, locationMetadata, Entrypoints.charFieldWriteBarrierMethod);
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  // T2 already contains the offset of the field on entry
  static void compilePutfieldBarrierShort(BaselineCompilerImpl comp, int locationMetadata) {
    putfieldStoreBarrierHelper(comp, locationMetadata, Entrypoints.shortFieldWriteBarrierMethod);
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  // T2 already contains the offset of the field on entry
  static void compilePutfieldBarrierInt(BaselineCompilerImpl comp, int locationMetadata) {
    putfieldStoreBarrierHelper(comp, locationMetadata, Entrypoints.intFieldWriteBarrierMethod);
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  // T2 already contains the offset of the field on entry
  static void compilePutfieldBarrierWord(BaselineCompilerImpl comp, int locationMetadata) {
    putfieldStoreBarrierHelper(comp, locationMetadata, Entrypoints.wordFieldWriteBarrierMethod);
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  // T2 already contains the offset of the field on entry
  static void compilePutfieldBarrierAddress(BaselineCompilerImpl comp, int locationMetadata) {
    putfieldStoreBarrierHelper(comp, locationMetadata, Entrypoints.addressFieldWriteBarrierMethod);
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  // T2 already contains the offset of the field on entry
  static void compilePutfieldBarrierOffset(BaselineCompilerImpl comp, int locationMetadata) {
    putfieldStoreBarrierHelper(comp, locationMetadata, Entrypoints.offsetFieldWriteBarrierMethod);
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  // T2 already contains the offset of the field on entry
  static void compilePutfieldBarrierExtent(BaselineCompilerImpl comp, int locationMetadata) {
    putfieldStoreBarrierHelper(comp, locationMetadata, Entrypoints.extentFieldWriteBarrierMethod);
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  // T2 already contains the offset of the field on entry
  private static void putfieldStoreBarrierHelper(BaselineCompilerImpl comp, int locationMetadata, NormalMethod barrier) {
    // value_to_store is a 32-bit (or smaller) General-Purpose Register type
    Assembler asm = comp.asm;
    asm.generateOffsetLoad(ALWAYS, LR, JTOC, barrier.getOffset());
    asm.emitPOP(ALWAYS, T1);            // value to store
    asm.emitPOP(ALWAYS, T0);            // object base
    asm.generateNullCheck(T0);
    asm.generateImmediateLoad(ALWAYS, T3, locationMetadata);
    asm.emitBLX(ALWAYS, LR);      // Call barrier with parameters in (T0,T1,T2,T3)
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  // T1 already contains the offset of the field on entry
  static void compilePutfieldBarrierLong(BaselineCompilerImpl comp, int locationMetadata) {
    Assembler asm = comp.asm;
    asm.generateOffsetLoad(ALWAYS, LR, JTOC, Entrypoints.longFieldWriteBarrierMethod.getOffset());
    asm.emitVPOP64(ALWAYS, F0and1);     // value to store
    asm.emitPOP(ALWAYS, T0);            // object base
    asm.generateNullCheck(T0);
    asm.generateImmediateLoad(ALWAYS, T2, locationMetadata);
    asm.emitBLX(ALWAYS, LR);      // Call barrier with parameters in (T0,F0and1,T1,T2)
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  // T1 already contains the offset of the field on entry
  static void compilePutfieldBarrierDouble(BaselineCompilerImpl comp, int locationMetadata) {
    Assembler asm = comp.asm;
    asm.generateOffsetLoad(ALWAYS, LR, JTOC, Entrypoints.doubleFieldWriteBarrierMethod.getOffset());
    asm.emitVPOP64(ALWAYS, F0and1);     // value to store
    asm.emitPOP(ALWAYS, T0);            // object base
    asm.generateNullCheck(T0);
    asm.generateImmediateLoad(ALWAYS, T2, locationMetadata);
    asm.emitBLX(ALWAYS, LR);      // Call barrier with parameters in (T0,F0and1,T1,T2)
  }

  // on entry java stack contains ...|target_ref|value_to_store|
  // T1 already contains the offset of the field on entry
  static void compilePutfieldBarrierFloat(BaselineCompilerImpl comp, int locationMetadata) {
    Assembler asm = comp.asm;
    asm.generateOffsetLoad(ALWAYS, LR, JTOC, Entrypoints.floatFieldWriteBarrierMethod.getOffset());
    asm.emitVPOP32(ALWAYS, F0);         // value to store
    asm.emitPOP(ALWAYS, T0);            // object base
    asm.generateNullCheck(T0);
    asm.generateImmediateLoad(ALWAYS, T2, locationMetadata);
    asm.emitBLX(ALWAYS, LR);      // Call barrier with parameters in (T0,F0,T1,T2)
  }

  /*
   * Other barriers
   */

  // on entry java stack contains ...|ref_to_store|
  // T1 already contains the offset of the field on entry
  static void compilePutstaticBarrier(BaselineCompilerImpl comp, int locationMetadata) {
    Assembler asm = comp.asm;
    asm.generateOffsetLoad(ALWAYS, LR, JTOC, Entrypoints.objectStaticWriteBarrierMethod.getOffset());
    asm.emitPOP(ALWAYS, T0);             // value to store
    asm.generateImmediateLoad(ALWAYS, T2, locationMetadata);
    asm.emitBLX(ALWAYS, LR);      // MemoryManager.putstaticWriteBarrier(T0,T1,T2)
  }

  // on entry java stack contains ...|source_ref|
  // T1 already contains the offset of the field on entry
  static void compileGetfieldBarrier(BaselineCompilerImpl comp, int locationMetadata) {
    Assembler asm = comp.asm;
    asm.generateOffsetLoad(ALWAYS, LR, JTOC, Entrypoints.objectFieldReadBarrierMethod.getOffset());
    asm.emitPOP(ALWAYS, T0);               // object base
    asm.generateNullCheck(T0);
    asm.generateImmediateLoad(ALWAYS, T2, locationMetadata);
    asm.emitBLX(ALWAYS, LR);      // MemoryManager.getfieldReadBarrier(T0,T1,T2)
  }

  // on entry java stack contains ...|
  // T0 already contains the offset of the field on entry
  static void compileGetstaticBarrier(BaselineCompilerImpl comp, int locationMetadata) {
    Assembler asm = comp.asm;
    asm.generateOffsetLoad(ALWAYS, LR, JTOC, Entrypoints.objectStaticReadBarrierMethod.getOffset());
    asm.generateImmediateLoad(ALWAYS, T1, locationMetadata);
    asm.emitBLX(ALWAYS, LR);      // MemoryManager.getstaticReadBarrier(T0,T1)
  }
}
