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
package org.jikesrvm.compilers.baseline.ia32;

import org.jikesrvm.Configuration;
import org.jikesrvm.ArchitectureSpecific.Assembler;
import org.jikesrvm.classloader.MethodReference;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.ia32.BaselineConstants;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.pragma.Inline;

/**
 * Class called from baseline compiler to generate architecture specific
 * write barriers for garbage collectors.  For baseline
 * compiled methods, the write barrier calls methods of WriteBarrier.
 */
class Barriers implements BaselineConstants {

  /**
   * Generate code to perform an array store barrier. On entry the stack holds:
   * arrayRef, index, value.
   *
   * @param asm the assembler to generate the code in
   */
  static void compileArrayStoreBarrier(Assembler asm) {
    BaselineCompilerImpl.genParameterRegisterLoad(asm, 3);
    asm.emitCALL_Abs(Magic.getTocPointer().plus(Entrypoints.aastoreMethod.getOffset()));
  }

  /**
   * Helper function for primitive array stores
   *
   * @param asm the assembler to generate the code in
   * @param compiler the compiler instance to ensure correct parameter passing
   * @param barrier the designated barrier
   */
  private static void arayStoreBarrierHelper(Assembler asm, BaselineCompilerImpl compiler, NormalMethod barrier) {
    // on entry java stack contains ...|target_array_ref|array_index|value_to_store|
    // Use the correct calling convention to pass parameters by register and the stack
    //  (size of value_to_store varies by type of array store)
    MethodReference method = barrier.getMemberRef().asMethodReference();
    compiler.genParameterRegisterLoad(method, false);
    // call the actual write barrier
    asm.emitCALL_Abs(Magic.getTocPointer().plus(barrier.getOffset()));
  }

  /**
   * Generate code to perform a bastore barrier. On entry the stack holds:
   * arrayRef, index, value.
   *
   * @param asm the assembler to generate the code in
   * @param compiler the compiler instance to ensure correct parameter passing
   */
  static void compileArrayStoreBarrierByte(Assembler asm, BaselineCompilerImpl compiler) {
    arayStoreBarrierHelper(asm, compiler, Entrypoints.byteArrayWriteBarrierMethod);
  }

  /**
   * Generate code to perform a castore barrier. On entry the stack holds:
   * arrayRef, index, value.
   *
   * @param asm the assembler to generate the code in
   * @param compiler the compiler instance to ensure correct parameter passing
   */
  static void compileArrayStoreBarrierChar(Assembler asm, BaselineCompilerImpl compiler) {
    arayStoreBarrierHelper(asm, compiler, Entrypoints.charArrayWriteBarrierMethod);
  }

  /**
   * Generate code to perform a dastore barrier. On entry the stack holds:
   * arrayRef, index, value.
   *
   * @param asm the assembler to generate the code in
   * @param compiler the compiler instance to ensure correct parameter passing
   */
  static void compileArrayStoreBarrierDouble(Assembler asm, BaselineCompilerImpl compiler) {
    arayStoreBarrierHelper(asm, compiler, Entrypoints.doubleArrayWriteBarrierMethod);
  }

  /**
   * Generate code to perform a fastore barrier. On entry the stack holds:
   * arrayRef, index, value.
   *
   * @param asm the assembler to generate the code in
   * @param compiler the compiler instance to ensure correct parameter passing
   */
  static void compileArrayStoreBarrierFloat(Assembler asm, BaselineCompilerImpl compiler) {
    arayStoreBarrierHelper(asm, compiler, Entrypoints.floatArrayWriteBarrierMethod);
  }

  /**
   * Generate code to perform a iastore barrier. On entry the stack holds:
   * arrayRef, index, value.
   *
   * @param asm the assembler to generate the code in
   * @param compiler the compiler instance to ensure correct parameter passing
   */
  static void compileArrayStoreBarrierInt(Assembler asm, BaselineCompilerImpl compiler) {
    arayStoreBarrierHelper(asm, compiler, Entrypoints.intArrayWriteBarrierMethod);
  }

  /**
   * Generate code to perform a lastore barrier. On entry the stack holds:
   * arrayRef, index, value.
   *
   * @param asm the assembler to generate the code in
   * @param compiler the compiler instance to ensure correct parameter passing
   */
  static void compileArrayStoreBarrierLong(Assembler asm, BaselineCompilerImpl compiler) {
    arayStoreBarrierHelper(asm, compiler, Entrypoints.longArrayWriteBarrierMethod);
  }

  /**
   * Generate code to perform a sastore barrier. On entry the stack holds:
   * arrayRef, index, value.
   *
   * @param asm the assembler to generate the code in
   * @param compiler the compiler instance to ensure correct parameter passing
   */
  static void compileArrayStoreBarrierShort(Assembler asm, BaselineCompilerImpl compiler) {
    arayStoreBarrierHelper(asm, compiler, Entrypoints.shortArrayWriteBarrierMethod);
  }

  /**
   * Generate code to perform a putfield barrier. On entry the stack holds:
   * object, value.
   *
   * @param asm the assembler to generate the code in
   * @param offset the register holding the offset of the field
   * @param locationMetadata meta-data about the location
   */
  @Inline
  static void compilePutfieldBarrier(Assembler asm, GPR offset, int locationMetadata) {
    asm.emitPUSH_Reg(offset);
    asm.emitPUSH_Imm(locationMetadata);
    BaselineCompilerImpl.genParameterRegisterLoad(asm, 4);
    genNullCheck(asm, T0);
    asm.emitCALL_Abs(Magic.getTocPointer().plus(Entrypoints.objectFieldWriteBarrierMethod.getOffset()));
  }

  /**
   * Generate code to perform a putfield barrier when the field is at a known
   * offset. On entry the stack holds: object, value.
   *
   * @param asm the assembler to generate the code in
   * @param fieldOffset the offset of the field
   * @param locationMetadata meta-data about the location
   */
  @Inline
  static void compilePutfieldBarrierImm(Assembler asm, Offset fieldOffset, int locationMetadata) {
    asm.emitPUSH_Imm(fieldOffset.toInt());
    asm.emitPUSH_Imm(locationMetadata);
    BaselineCompilerImpl.genParameterRegisterLoad(asm, 4);
    genNullCheck(asm, T0);
    asm.emitCALL_Abs(Magic.getTocPointer().plus(Entrypoints.objectFieldWriteBarrierMethod.getOffset()));
  }

  /**
   * Private helper method for primitive putfields
   *
   * @param asm the assembler to generate the code in
   * @param compiler the compiler instance to ensure correct parameter passing
   * @param offset the register holding the offset of the field
   * @param locationMetadata meta-data about the location
   * @param barrier the barrier method to call
   */
  @Inline
  private static void putfieldStoreBarrierHelper(Assembler asm, BaselineCompilerImpl compiler, GPR offset, int locationMetadata,
                                                 NormalMethod barrier) {
    // on entry the java stack contains... |object|value|
    asm.emitPUSH_Reg(offset);
    asm.emitPUSH_Imm(locationMetadata);
    // Use the correct calling convention to pass parameters by register and the stack
    //  (size of value varies by type of putfield)
    MethodReference method = barrier.getMemberRef().asMethodReference();
    compiler.genParameterRegisterLoad(method, false);
    genNullCheck(asm, T0);
    asm.emitCALL_Abs(Magic.getTocPointer().plus(barrier.getOffset()));
  }

  /**
   * Private helper method for primitive putfields
   *
   * @param asm the assembler to generate the code in
   * @param compiler the compiler instance to ensure correct parameter passing
   * @param fieldOffset offset of the field
   * @param locationMetadata meta-data about the location
   * @param barrier the barrier method to call
   */
  @Inline
  private static void putfieldStoreBarrierHelper(Assembler asm, BaselineCompilerImpl compiler, Offset fieldOffset, int locationMetadata,
                                                 NormalMethod barrier) {
    // on entry the java stack contains... |object|value|
    asm.emitPUSH_Imm(fieldOffset.toInt());
    asm.emitPUSH_Imm(locationMetadata);
    // Use the correct calling convention to pass parameters by register and the stack
    //  (size of value varies by type of putfield)
    MethodReference method = barrier.getMemberRef().asMethodReference();
    compiler.genParameterRegisterLoad(method, false);
    genNullCheck(asm, T0);
    asm.emitCALL_Abs(Magic.getTocPointer().plus(barrier.getOffset()));
  }

  /**
   * Generate code to perform a putfield barrier for a boolean field.
   * On entry the stack holds: object, value.
   *
   * @param asm the assembler to generate the code in
   * @param offset the register holding the offset of the field
   * @param locationMetadata meta-data about the location
   */
  @Inline
  static void compilePutfieldBarrierBoolean(Assembler asm, GPR offset, int locationMetadata, BaselineCompilerImpl compiler) {
    putfieldStoreBarrierHelper(asm, compiler, offset, locationMetadata, Entrypoints.booleanFieldWriteBarrierMethod);
  }

  /**
   * Generate code to perform a putfield barrier for a boolean field when
   * the field is at a known offset. On entry the stack holds: object, value.
   *
   * @param asm the assembler to generate the code in
   * @param fieldOffset the offset of the field
   * @param locationMetadata meta-data about the location
   */
  @Inline
  static void compilePutfieldBarrierBooleanImm(Assembler asm, Offset fieldOffset, int locationMetadata, BaselineCompilerImpl compiler) {
    putfieldStoreBarrierHelper(asm, compiler, fieldOffset, locationMetadata, Entrypoints.booleanFieldWriteBarrierMethod);
  }

  /**
   * Generate code to perform a putfield barrier for a byte field.
   * On entry the stack holds: object, value.
   *
   * @param asm the assembler to generate the code in
   * @param offset the register holding the offset of the field
   * @param locationMetadata meta-data about the location
   */
  @Inline
  static void compilePutfieldBarrierByte(Assembler asm, GPR offset, int locationMetadata, BaselineCompilerImpl compiler) {
    putfieldStoreBarrierHelper(asm, compiler, offset, locationMetadata, Entrypoints.byteFieldWriteBarrierMethod);
  }

  /**
   * Generate code to perform a putfield barrier for a byte field when
   * the field is at a known offset. On entry the stack holds: object, value.
   *
   * @param asm the assembler to generate the code in
   * @param fieldOffset the offset of the field
   * @param locationMetadata meta-data about the location
   */
  @Inline
  static void compilePutfieldBarrierByteImm(Assembler asm, Offset fieldOffset, int locationMetadata, BaselineCompilerImpl compiler) {
    putfieldStoreBarrierHelper(asm, compiler, fieldOffset, locationMetadata, Entrypoints.byteFieldWriteBarrierMethod);
  }

  /**
   * Generate code to perform a putfield barrier for a char field.
   * On entry the stack holds: object, value.
   *
   * @param asm the assembler to generate the code in
   * @param offset the register holding the offset of the field
   * @param locationMetadata meta-data about the location
   */
  @Inline
  static void compilePutfieldBarrierChar(Assembler asm, GPR offset, int locationMetadata, BaselineCompilerImpl compiler) {
    putfieldStoreBarrierHelper(asm, compiler, offset, locationMetadata, Entrypoints.charFieldWriteBarrierMethod);
  }

  /**
   * Generate code to perform a putfield barrier for a char field when
   * the field is at a known offset. On entry the stack holds: object, value.
   *
   * @param asm the assembler to generate the code in
   * @param fieldOffset the offset of the field
   * @param locationMetadata meta-data about the location
   */
  @Inline
  static void compilePutfieldBarrierCharImm(Assembler asm, Offset fieldOffset, int locationMetadata, BaselineCompilerImpl compiler) {
    putfieldStoreBarrierHelper(asm, compiler, fieldOffset, locationMetadata, Entrypoints.charFieldWriteBarrierMethod);
  }

  /**
   * Generate code to perform a putfield barrier for a double field.
   * On entry the stack holds: object, value.
   *
   * @param asm the assembler to generate the code in
   * @param offset the register holding the offset of the field
   * @param locationMetadata meta-data about the location
   */
  @Inline
  static void compilePutfieldBarrierDouble(Assembler asm, GPR offset, int locationMetadata, BaselineCompilerImpl compiler) {
    putfieldStoreBarrierHelper(asm, compiler, offset, locationMetadata, Entrypoints.doubleFieldWriteBarrierMethod);
  }

  /**
   * Generate code to perform a putfield barrier for a double field when
   * the field is at a known offset. On entry the stack holds: object, value.
   *
   * @param asm the assembler to generate the code in
   * @param fieldOffset the offset of the field
   * @param locationMetadata meta-data about the location
   */
  @Inline
  static void compilePutfieldBarrierDoubleImm(Assembler asm, Offset fieldOffset, int locationMetadata, BaselineCompilerImpl compiler) {
    putfieldStoreBarrierHelper(asm, compiler, fieldOffset, locationMetadata, Entrypoints.doubleFieldWriteBarrierMethod);
  }

  /**
   * Generate code to perform a putfield barrier for a float field.
   * On entry the stack holds: object, value.
   *
   * @param asm the assembler to generate the code in
   * @param offset the register holding the offset of the field
   * @param locationMetadata meta-data about the location
   */
  @Inline
  static void compilePutfieldBarrierFloat(Assembler asm, GPR offset, int locationMetadata, BaselineCompilerImpl compiler) {
    putfieldStoreBarrierHelper(asm, compiler, offset, locationMetadata, Entrypoints.floatFieldWriteBarrierMethod);
  }

  /**
   * Generate code to perform a putfield barrier for a float field when
   * the field is at a known offset. On entry the stack holds: object, value.
   *
   * @param asm the assembler to generate the code in
   * @param fieldOffset the offset of the field
   * @param locationMetadata meta-data about the location
   */
  @Inline
  static void compilePutfieldBarrierFloatImm(Assembler asm, Offset fieldOffset, int locationMetadata, BaselineCompilerImpl compiler) {
    putfieldStoreBarrierHelper(asm, compiler, fieldOffset, locationMetadata, Entrypoints.floatFieldWriteBarrierMethod);
  }

  /**
   * Generate code to perform a putfield barrier for a int field.
   * On entry the stack holds: object, value.
   *
   * @param asm the assembler to generate the code in
   * @param offset the register holding the offset of the field
   * @param locationMetadata meta-data about the location
   */
  @Inline
  static void compilePutfieldBarrierInt(Assembler asm, GPR offset, int locationMetadata, BaselineCompilerImpl compiler) {
    putfieldStoreBarrierHelper(asm, compiler, offset, locationMetadata, Entrypoints.intFieldWriteBarrierMethod);
  }

  /**
   * Generate code to perform a putfield barrier for a int field when
   * the field is at a known offset. On entry the stack holds: object, value.
   *
   * @param asm the assembler to generate the code in
   * @param fieldOffset the offset of the field
   * @param locationMetadata meta-data about the location
   */
  @Inline
  static void compilePutfieldBarrierIntImm(Assembler asm, Offset fieldOffset, int locationMetadata, BaselineCompilerImpl compiler) {
    putfieldStoreBarrierHelper(asm, compiler, fieldOffset, locationMetadata, Entrypoints.intFieldWriteBarrierMethod);
  }

  /**
   * Generate code to perform a putfield barrier for a long field.
   * On entry the stack holds: object, value.
   *
   * @param asm the assembler to generate the code in
   * @param offset the register holding the offset of the field
   * @param locationMetadata meta-data about the location
   */
  @Inline
  static void compilePutfieldBarrierLong(Assembler asm, GPR offset, int locationMetadata, BaselineCompilerImpl compiler) {
    putfieldStoreBarrierHelper(asm, compiler, offset, locationMetadata, Entrypoints.longFieldWriteBarrierMethod);
  }

  /**
   * Generate code to perform a putfield barrier for a long field when
   * the field is at a known offset. On entry the stack holds: object, value.
   *
   * @param asm the assembler to generate the code in
   * @param fieldOffset the offset of the field
   * @param locationMetadata meta-data about the location
   */
  @Inline
  static void compilePutfieldBarrierLongImm(Assembler asm, Offset fieldOffset, int locationMetadata, BaselineCompilerImpl compiler) {
    putfieldStoreBarrierHelper(asm, compiler, fieldOffset, locationMetadata, Entrypoints.longFieldWriteBarrierMethod);
  }

  /**
   * Generate code to perform a putfield barrier for a short field.
   * On entry the stack holds: object, value.
   *
   * @param asm the assembler to generate the code in
   * @param offset the register holding the offset of the field
   * @param locationMetadata meta-data about the location
   */
  @Inline
  static void compilePutfieldBarrierShort(Assembler asm, GPR offset, int locationMetadata, BaselineCompilerImpl compiler) {
    putfieldStoreBarrierHelper(asm, compiler, offset, locationMetadata, Entrypoints.shortFieldWriteBarrierMethod);
  }

  /**
   * Generate code to perform a putfield barrier for a short field when
   * the field is at a known offset. On entry the stack holds: object, value.
   *
   * @param asm the assembler to generate the code in
   * @param fieldOffset the offset of the field
   * @param locationMetadata meta-data about the location
   */
  @Inline
  static void compilePutfieldBarrierShortImm(Assembler asm, Offset fieldOffset, int locationMetadata, BaselineCompilerImpl compiler) {
    putfieldStoreBarrierHelper(asm, compiler, fieldOffset, locationMetadata, Entrypoints.shortFieldWriteBarrierMethod);
  }

  /**
   * Generate code to perform a putfield barrier for a unboxed Word field.
   * On entry the stack holds: object, value.
   *
   * @param asm the assembler to generate the code in
   * @param offset the register holding the offset of the field
   * @param locationMetadata meta-data about the location
   */
  @Inline
  static void compilePutfieldBarrierWord(Assembler asm, GPR offset, int locationMetadata, BaselineCompilerImpl compiler) {
    putfieldStoreBarrierHelper(asm, compiler, offset, locationMetadata, Entrypoints.wordFieldWriteBarrierMethod);
  }

  /**
   * Generate code to perform a putfield barrier for a unboxed Word field when
   * the field is at a known offset. On entry the stack holds: object, value.
   *
   * @param asm the assembler to generate the code in
   * @param fieldOffset the offset of the field
   * @param locationMetadata meta-data about the location
   */
  @Inline
  static void compilePutfieldBarrierWordImm(Assembler asm, Offset fieldOffset, int locationMetadata, BaselineCompilerImpl compiler) {
    putfieldStoreBarrierHelper(asm, compiler, fieldOffset, locationMetadata, Entrypoints.wordFieldWriteBarrierMethod);
  }

  /**
   * Generate code to perform a putfield barrier for a unboxed Address field.
   * On entry the stack holds: object, value.
   *
   * @param asm the assembler to generate the code in
   * @param offset the register holding the offset of the field
   * @param locationMetadata meta-data about the location
   */
  @Inline
  static void compilePutfieldBarrierAddress(Assembler asm, GPR offset, int locationMetadata, BaselineCompilerImpl compiler) {
    putfieldStoreBarrierHelper(asm, compiler, offset, locationMetadata, Entrypoints.addressFieldWriteBarrierMethod);
  }

  /**
   * Generate code to perform a putfield barrier for a unboxed Address field when
   * the field is at a known offset. On entry the stack holds: object, value.
   *
   * @param asm the assembler to generate the code in
   * @param fieldOffset the offset of the field
   * @param locationMetadata meta-data about the location
   */
  @Inline
  static void compilePutfieldBarrierAddressImm(Assembler asm, Offset fieldOffset, int locationMetadata, BaselineCompilerImpl compiler) {
    putfieldStoreBarrierHelper(asm, compiler, fieldOffset, locationMetadata, Entrypoints.addressFieldWriteBarrierMethod);
  }

  /**
   * Generate code to perform a putfield barrier for a unboxed Extent field.
   * On entry the stack holds: object, value.
   *
   * @param asm the assembler to generate the code in
   * @param offset the register holding the offset of the field
   * @param locationMetadata meta-data about the location
   */
  @Inline
  static void compilePutfieldBarrierExtent(Assembler asm, GPR offset, int locationMetadata, BaselineCompilerImpl compiler) {
    putfieldStoreBarrierHelper(asm, compiler, offset, locationMetadata, Entrypoints.extentFieldWriteBarrierMethod);
  }

  /**
   * Generate code to perform a putfield barrier for a unboxed Extent field when
   * the field is at a known offset. On entry the stack holds: object, value.
   *
   * @param asm the assembler to generate the code in
   * @param fieldOffset the offset of the field
   * @param locationMetadata meta-data about the location
   */
  @Inline
  static void compilePutfieldBarrierExtentImm(Assembler asm, Offset fieldOffset, int locationMetadata, BaselineCompilerImpl compiler) {
    putfieldStoreBarrierHelper(asm, compiler, fieldOffset, locationMetadata, Entrypoints.extentFieldWriteBarrierMethod);
  }

  /**
   * Generate code to perform a putfield barrier for a unboxed Offset field.
   * On entry the stack holds: object, value.
   *
   * @param asm the assembler to generate the code in
   * @param offset the register holding the offset of the field
   * @param locationMetadata meta-data about the location
   */
  @Inline
  static void compilePutfieldBarrierOffset(Assembler asm, GPR offset, int locationMetadata, BaselineCompilerImpl compiler) {
    putfieldStoreBarrierHelper(asm, compiler, offset, locationMetadata, Entrypoints.offsetFieldWriteBarrierMethod);
  }

  /**
   * Generate code to perform a putfield barrier for a unboxed Offset field when
   * the field is at a known offset. On entry the stack holds: object, value.
   *
   * @param asm the assembler to generate the code in
   * @param fieldOffset the offset of the field
   * @param locationMetadata meta-data about the location
   */
  @Inline
  static void compilePutfieldBarrierOffsetImm(Assembler asm, Offset fieldOffset, int locationMetadata, BaselineCompilerImpl compiler) {
    putfieldStoreBarrierHelper(asm, compiler, fieldOffset, locationMetadata, Entrypoints.offsetFieldWriteBarrierMethod);
  }

  static void compilePutstaticBarrier(Assembler asm, GPR reg, int locationMetadata) {
    //  on entry java stack contains ...|ref_to_store|
    //  reg holds offset of field
    asm.emitPUSH_Reg(reg); // offset
    asm.emitPUSH_Imm(locationMetadata);
    BaselineCompilerImpl.genParameterRegisterLoad(asm, 3);
    asm.emitCALL_Abs(Magic.getTocPointer().plus(Entrypoints.objectStaticWriteBarrierMethod.getOffset()));
 }

  static void compilePutstaticBarrierImm(Assembler asm, Offset fieldOffset, int locationMetadata) {
    //  on entry java stack contains ...|ref_to_store|
    asm.emitPUSH_Imm(fieldOffset.toInt());
    asm.emitPUSH_Imm(locationMetadata);
    BaselineCompilerImpl.genParameterRegisterLoad(asm, 3);
    asm.emitCALL_Abs(Magic.getTocPointer().plus(Entrypoints.objectStaticWriteBarrierMethod.getOffset()));
  }

  static void compileArrayLoadBarrier(Assembler asm, boolean pushResult) {
    // on entry java stack contains ...|target_array_ref|array_index|
    // SP -> index, SP+4 -> target_ref
    BaselineCompilerImpl.genParameterRegisterLoad(asm, 2);
    asm.emitCALL_Abs(Magic.getTocPointer().plus(Entrypoints.objectArrayReadBarrierMethod.getOffset()));
    if (pushResult) asm.emitPUSH_Reg(T0);
  }

  static void compileGetfieldBarrier(Assembler asm, GPR reg, int locationMetadata) {
    //  on entry java stack contains ...|target_ref|
    //  SP -> target_ref
    asm.emitPUSH_Reg(reg);
    asm.emitPUSH_Imm(locationMetadata);
    BaselineCompilerImpl.genParameterRegisterLoad(asm, 3);
    genNullCheck(asm, T0);
    asm.emitCALL_Abs(Magic.getTocPointer().plus(Entrypoints.objectFieldReadBarrierMethod.getOffset()));
    asm.emitPUSH_Reg(T0);
  }

  static void compileGetfieldBarrierImm(Assembler asm, Offset fieldOffset, int locationMetadata) {
    asm.emitPUSH_Imm(fieldOffset.toInt());
    asm.emitPUSH_Imm(locationMetadata);
    BaselineCompilerImpl.genParameterRegisterLoad(asm, 3);
    genNullCheck(asm, T0);
    asm.emitCALL_Abs(Magic.getTocPointer().plus(Entrypoints.objectFieldReadBarrierMethod.getOffset()));
    asm.emitPUSH_Reg(T0);
  }

  static void compileGetstaticBarrier(Assembler asm, GPR reg, int locationMetadata) {
    asm.emitPUSH_Reg(reg);
    asm.emitPUSH_Imm(locationMetadata);
    BaselineCompilerImpl.genParameterRegisterLoad(asm, 2);
    asm.emitCALL_Abs(Magic.getTocPointer().plus(Entrypoints.objectStaticReadBarrierMethod.getOffset()));
    asm.emitPUSH_Reg(T0);
  }

  static void compileGetstaticBarrierImm(Assembler asm, Offset fieldOffset, int locationMetadata) {
    asm.emitPUSH_Imm(fieldOffset.toInt());
    asm.emitPUSH_Imm(locationMetadata);
    BaselineCompilerImpl.genParameterRegisterLoad(asm, 2);
    asm.emitCALL_Abs(Magic.getTocPointer().plus(Entrypoints.objectStaticReadBarrierMethod.getOffset()));
    asm.emitPUSH_Reg(T0);
  }

  static void compileModifyCheck(Assembler asm, int offset) {
    if (!Configuration.ExtremeAssertions) return;
    // on entry java stack contains ... [SP+offset] -> target_ref
    // on exit: stack is the same
    asm.emitPUSH_RegDisp(SP, Offset.fromIntSignExtend(offset));   // dup
    BaselineCompilerImpl.genParameterRegisterLoad(asm, 1);
    asm.emitCALL_Abs(Magic.getTocPointer().plus(Entrypoints.modifyCheckMethod.getOffset()));
  }

  /**
   * Generate an implicit null check by loading the TIB of the given object.
   * Scribbles over S0.
   *
   * @param asm the assembler to generate into
   * @param objRefReg the register containing the reference
   */
  private static void genNullCheck(Assembler asm, GPR objRefReg) {
    BaselineCompilerImpl.baselineEmitLoadTIB(asm, S0, T0);
  }
}
