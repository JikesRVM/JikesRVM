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
package org.jikesrvm.classloader;

import org.jikesrvm.VM;
import org.jikesrvm.SizeConstants;
import org.jikesrvm.runtime.Statics;
import org.vmmagic.pragma.Inline;
import org.vmmagic.unboxed.Offset;

/**
 * Provides minimal abstraction layer to a stream of bytecodes
 * from the code attribute of a method.
 */
public class BytecodeStream implements BytecodeConstants, ClassLoaderConstants, SizeConstants {
  private final NormalMethod method;
  private final int bcLength;
  private final byte[] bcodes;
  private int bcIndex;
  private int opcode;
  private boolean wide;

  /**
   * @param m the method containing the bytecodes
   * @param bc the array of bytecodes
   */
  public BytecodeStream(NormalMethod m, byte[] bc) {
    method = m;
    bcodes = bc;
    bcLength = bc.length;
    bcIndex = 0;
  }

  /**
   * Returns the method that this bytecode stream is from
   * @return method
   */
  public final NormalMethod getMethod() {
    return method;
  }

  /**
   * Returns the declaring class that this bytecode stream is from
   * @return method
   */
  public final RVMClass getDeclaringClass() {
    return method.getDeclaringClass();
  }

  /**
   * Returns the length of the bytecode stream
   * Returns 0 if the method doesn't have any bytecodes
   *         (i.e. is abstract or native)
   * @return bytecode stream length
   */
  public final int length() {
    return bcLength;
  }

  /**
   * Returns the current bytecode index
   * @return the current bytecode index
   */
  public final int index() {
    return bcIndex;
  }

  /**
   * Resets the stream to the beginning
   * @see #reset(int)
   */
  public final void reset() {
    reset(0);
  }

  /**
   * Resets the stream to a given position
   * Use with caution
   * @param index the position to reset the stream to
   * @see #reset()
   */
  public final void reset(int index) {
    bcIndex = index;
  }

  /**
   * Does the stream have more bytecodes in it?
   * @return whether there are more bytecodes
   */
  public final boolean hasMoreBytecodes() {
    return bcIndex < bcLength;
  }

  /**
   * Returns the opcode of the next instruction in the sequence
   * without advancing to it
   * @return the opcode of the next instruction
   * @see #nextInstruction()
   */
  public final int peekNextOpcode() {
    if (VM.VerifyAssertions) VM._assert(bcIndex < bcLength);
    return getUnsignedByte(bcIndex);
  }

  /**
   * Sets up the next instruction in the sequence
   * @return the opcode of the next instruction
   * @see #peekNextOpcode()
   */
  public final int nextInstruction() {
    if (VM.VerifyAssertions) VM._assert(bcIndex < bcLength);
    opcode = readUnsignedByte();
    wide = (opcode == JBC_wide);
    return opcode;
  }

  /**
   * Returns the opcode of the current instruction in the sequence
   * Note: if skipInstruction has been called, but nextInstruction has not,
   *       this method will return the opcode of the skipped instruction!
   * @return the opcode of the current instruction
   * @see #nextInstruction()
   * @see #isWide()
   */
  public final int getOpcode() {
    return opcode;
  }

  /**
   * Are we currently processing a wide instruction?
   * @return true if current instruction is wide
   * @see #nextInstruction()
   * @see #getOpcode()
   */
  public final boolean isWide() {
    return wide;
  }

  /**
   * Skips the current instruction
   * @see #skipInstruction(int,boolean)
   */
  public final void skipInstruction() {
    if (VM.VerifyAssertions) VM._assert(bcIndex <= bcLength);
    int len = JBC_length[opcode] - 1;
    if (wide) len += len;
    if (len >= 0) {
      bcIndex += len;
    } else {
      skipSpecialInstruction(opcode);
    }
  }

  /**
   * Skips the current instruction (without using the opcode field)
   * A slightly optimized version of skipInstruction()
   * @param opcode current opcode
   * @param wide whether current instruction follows wide
   * @see #skipInstruction()
   */
  public final void skipInstruction(int opcode, boolean wide) {
    if (VM.VerifyAssertions) VM._assert(bcIndex < bcLength);
    int len = JBC_length[opcode] - 1;
    if (wide) len += len;
    if (len >= 0) {
      bcIndex += len;
    } else {
      skipSpecialInstruction(opcode);
    }
  }

  /**
   * Returns a signed byte value
   * Used for bipush
   * @return signed byte value
   */
  public final int getByteValue() {
    if (VM.VerifyAssertions) VM._assert(opcode == JBC_bipush);
    return readSignedByte();
  }

  /**
   * Returns a signed short value
   * Used for sipush
   * @return signed short value
   */
  public final int getShortValue() {
    if (VM.VerifyAssertions) VM._assert(opcode == JBC_sipush);
    return readSignedShort();
  }

  /**
   * Returns the number of the local (as an unsigned byte)
   * Used for iload, lload, fload, dload, aload,
   *          istore, lstore, fstore, dstore, astore,
   *          iinc, ret
   * @return local number
   * @see #getWideLocalNumber()
   */
  public final int getLocalNumber() {
    if (VM.VerifyAssertions) {
      VM._assert((opcode >= JBC_iload && opcode <= JBC_aload) ||
                 (opcode >= JBC_istore && opcode <= JBC_astore) ||
                 opcode == JBC_iinc ||
                 opcode == JBC_ret);
    }
    return readUnsignedByte();
  }

  /**
   * Returns the wide number of the local (as an unsigned short)
   * Used for iload, lload, fload, dload, aload,
   *          istore, lstore, fstore, dstore, astore,
   *          iinc prefixed by wide
   * @return wide local number
   * @see #getLocalNumber()
   */
  public final int getWideLocalNumber() {
    if (VM.VerifyAssertions) {
      VM._assert(wide &&
                 ((opcode >= JBC_iload && opcode <= JBC_aload) ||
                  (opcode >= JBC_istore && opcode <= JBC_astore) ||
                  opcode == JBC_iinc));
    }
    return readUnsignedShort();
  }

  /**
   * Returns an increment value (as a signed byte)
   * Used for iinc
   * @return increment
   * @see #getWideIncrement()
   */
  public final int getIncrement() {
    if (VM.VerifyAssertions) VM._assert(opcode == JBC_iinc);
    return readSignedByte();
  }

  /**
   * Returns an increment value (as a signed short)
   * Used for iinc prefixed by wide
   * @return wide increment
   * @see #getIncrement()
   */
  public final int getWideIncrement() {
    if (VM.VerifyAssertions) VM._assert(wide && opcode == JBC_iinc);
    return readSignedShort();
  }

  /**
   * Returns the offset of the branch (as a signed short)
   * Used for if<cond>, ificmp<cond>, ifacmp<cond>, goto, jsr
   * @return branch offset
   * @see #getWideBranchOffset()
   */
  public final int getBranchOffset() {
    if (VM.VerifyAssertions) {
      VM._assert((opcode >= JBC_ifeq && opcode <= JBC_ifle) ||
                 (opcode >= JBC_if_icmpeq && opcode <= JBC_if_icmple) ||
                 opcode == JBC_if_acmpeq ||
                 opcode == JBC_if_acmpne ||
                 opcode == JBC_ifnull ||
                 opcode == JBC_ifnonnull ||
                 opcode == JBC_goto ||
                 opcode == JBC_jsr);
    }
    return readSignedShort();
  }

  /**
   * Returns the wide offset of the branch (as a signed int)
   * Used for goto_w, jsr_w
   * @return wide branch offset
   * @see #getBranchOffset()
   */
  public final int getWideBranchOffset() {
    if (VM.VerifyAssertions) {
      VM._assert(opcode == JBC_goto_w || opcode == JBC_jsr_w);
    }
    return readSignedInt();
  }

  /**
   * Skips the padding of a switch instruction
   * Used for tableswitch, lookupswitch
   */
  public final void alignSwitch() {
    if (VM.VerifyAssertions) {
      VM._assert(opcode == JBC_tableswitch || opcode == JBC_lookupswitch);
    }
    int align = bcIndex & 3;
    if (align != 0) bcIndex += 4 - align; // eat padding
  }

  /**
   * Returns the default offset of the switch (as a signed int)
   * Used for tableswitch, lookupswitch
   * @return default switch offset
   */
  public final int getDefaultSwitchOffset() {
    if (VM.VerifyAssertions) {
      VM._assert(opcode == JBC_tableswitch || opcode == JBC_lookupswitch);
    }
    return readSignedInt();
  }

  /**
   * Returns the lowest value of the tableswitch (as a signed int)
   * Used for tableswitch
   * @return lowest switch value
   * @see #getHighSwitchValue()
   */
  public final int getLowSwitchValue() {
    if (VM.VerifyAssertions) VM._assert(opcode == JBC_tableswitch);
    return readSignedInt();
  }

  /**
   * Returns the highest value of the tableswitch (as a signed int)
   * Used for tableswitch
   * @return highest switch value
   * @see #getLowSwitchValue()
   */
  public final int getHighSwitchValue() {
    if (VM.VerifyAssertions) VM._assert(opcode == JBC_tableswitch);
    return readSignedInt();
  }

  /**
   * Skips the offsets of a tableswitch instruction
   * Used for tableswitch
   * @param num the number of offsets to skip
   * @see #getTableSwitchOffset(int)
   */
  public final void skipTableSwitchOffsets(int num) {
    if (VM.VerifyAssertions) VM._assert(opcode == JBC_tableswitch);
    bcIndex += (num << LOG_BYTES_IN_INT);
  }

  /**
   * Returns the numbered offset of the tableswitch (as a signed int)
   * Used for tableswitch
   * The "cursor" has to be positioned at the start of the offset table
   * NOTE: Will NOT advance cursor
   * @param num the number of the offset to retrieve
   * @return switch offset
   */
  public final int getTableSwitchOffset(int num) {
    if (VM.VerifyAssertions) VM._assert(opcode == JBC_tableswitch);
    return getSignedInt(bcIndex + (num << LOG_BYTES_IN_INT));
  }

  /**
   * Returns the offset for a given value of the tableswitch (as a signed int)
   * or 0 if the value is out of range.
   * Used for tableswitch
   * The "cursor" has to be positioned at the start of the offset table
   * NOTE: Will NOT advance cursor
   * @param value the value to retrieve offset for
   * @param low the lowest value of the tableswitch
   * @param high the highest value of the tableswitch
   * @return switch offset
   */
  public final int computeTableSwitchOffset(int value, int low, int high) {
    if (VM.VerifyAssertions) VM._assert(opcode == JBC_tableswitch);
    if (value < low || value > high) return 0;
    return getSignedInt(bcIndex + ((value - low) << LOG_BYTES_IN_INT));
  }

  /**
   * Returns the number of match-offset pairs in the lookupswitch
   *         (as a signed int)
   * Used for lookupswitch
   * @return number of switch pairs
   */
  public final int getSwitchLength() {
    if (VM.VerifyAssertions) VM._assert(opcode == JBC_lookupswitch);
    return readSignedInt();
  }

  /**
   * Skips the match-offset pairs of a lookupswitch instruction
   * Used for lookupswitch
   * @param num the number of match-offset pairs to skip
   * @see #getLookupSwitchValue(int)
   * @see #getLookupSwitchOffset(int)
   */
  public final void skipLookupSwitchPairs(int num) {
    if (VM.VerifyAssertions) VM._assert(opcode == JBC_lookupswitch);
    bcIndex += (num << (LOG_BYTES_IN_INT + 1));
  }

  /**
   * Returns the numbered offset of the lookupswitch (as a signed int)
   * Used for lookupswitch
   * The "cursor" has to be positioned at the start of the pair table
   * NOTE: Will NOT advance cursor
   * @param num the number of the offset to retrieve
   * @return switch offset
   * @see #getLookupSwitchValue(int)
   */
  public final int getLookupSwitchOffset(int num) {
    if (VM.VerifyAssertions) VM._assert(opcode == JBC_lookupswitch);
    return getSignedInt(bcIndex + (num << (LOG_BYTES_IN_INT + 1)) + BYTES_IN_INT);
  }

  /**
   * Returns the numbered value of the lookupswitch (as a signed int)
   * Used for lookupswitch
   * The "cursor" has to be positioned at the start of the pair table
   * NOTE: Will NOT advance cursor
   * @param num the number of the value to retrieve
   * @return switch value
   * @see #getLookupSwitchOffset(int)
   */
  public final int getLookupSwitchValue(int num) {
    if (VM.VerifyAssertions) VM._assert(opcode == JBC_lookupswitch);
    return getSignedInt(bcIndex + (num << (LOG_BYTES_IN_INT + 1)));
  }

  /**
   * Returns the offset for a given value of the lookupswitch
   *         (as a signed int) or 0 if the value is not in the table.
   * Used for lookupswitch
   * The "cursor" has to be positioned at the start of the offset table
   * NOTE: Will NOT advance cursor
   * WARNING: Uses LINEAR search.  Whoever has time on their hands can
   *          re-implement this as a binary search.
   * @param value the value to retrieve offset for
   * @param num the number of match-offset pairs in the lookupswitch
   * @return switch offset
   */
  public final int computeLookupSwitchOffset(int value, int num) {
    if (VM.VerifyAssertions) VM._assert(opcode == JBC_lookupswitch);
    for (int i = 0; i < num; i++) {
      if (getSignedInt(bcIndex + (i << (LOG_BYTES_IN_INT + 1))) == value) {
        return getSignedInt(bcIndex + (i << (LOG_BYTES_IN_INT + 1)) + BYTES_IN_INT);
      }
    }
    return 0;
  }

  /**
   * Returns a reference to a field
   * Used for getstatic, putstatic, getfield, putfield
   * @return field reference
   */
  public final FieldReference getFieldReference() {
    if (VM.VerifyAssertions) {
      VM._assert(opcode == JBC_getstatic ||
                 opcode == JBC_putstatic ||
                 opcode == JBC_getfield ||
                 opcode == JBC_putfield);
    }
    return getDeclaringClass().getFieldRef(readUnsignedShort());
  }

  /**
   * Returns a reference to a field, for use prior to the class being loaded.
   * Used for getstatic, putstatic, getfield, putfield
   * @return field reference
   */
  public final FieldReference getFieldReference(int[] constantPool) {
    if (VM.VerifyAssertions) {
      VM._assert(opcode == JBC_getstatic ||
                 opcode == JBC_putstatic ||
                 opcode == JBC_getfield ||
                 opcode == JBC_putfield);
    }
    return ClassFileReader.getFieldRef(constantPool, readUnsignedShort());
  }
  /**
   * Returns a reference to a field
   * Used for invokevirtual, invokespecial, invokestatic, invokeinterface
   * @return method reference
   */
  public final MethodReference getMethodReference() {
    if (VM.VerifyAssertions) {
      VM._assert(opcode == JBC_invokevirtual ||
                 opcode == JBC_invokespecial ||
                 opcode == JBC_invokestatic ||
                 opcode == JBC_invokeinterface);
    }
    return getDeclaringClass().getMethodRef(readUnsignedShort());
  }

  /**
   * Returns a reference to a field, for use prior to the class being loaded
   * Used for invokevirtual, invokespecial, invokestatic, invokeinterface
   * @return method reference
   */
  public final MethodReference getMethodReference(int[] constantPool) {
    if (VM.VerifyAssertions) {
      VM._assert(opcode == JBC_invokevirtual ||
                 opcode == JBC_invokespecial ||
                 opcode == JBC_invokestatic ||
                 opcode == JBC_invokeinterface);
    }
    return ClassFileReader.getMethodRef(constantPool, readUnsignedShort());
  }

  /**
   * Skips the extra stuff after an invokeinterface instruction
   * Used for invokeinterface
   */
  public final void alignInvokeInterface() {
    if (VM.VerifyAssertions) VM._assert(opcode == JBC_invokeinterface);
    bcIndex += 2; // eat superfluous stuff
  }

  /**
   * Returns the type reference (as a RVMType)
   * Used for new, anewarray, checkcast, instanceof, multianewarray
   * @return type reference
   */
  public final TypeReference getTypeReference() {
    if (VM.VerifyAssertions) {
      VM._assert(opcode == JBC_new ||
                 opcode == JBC_anewarray ||
                 opcode == JBC_checkcast ||
                 opcode == JBC_instanceof ||
                 opcode == JBC_multianewarray);
    }
    int index = readUnsignedShort();
    return getDeclaringClass().getTypeRef(index);
  }

  /**
   * Returns the element type (primitive) of the array (as an unsigned byte)
   * Used for newarray
   * @return array element type
   * @see #getPrimitiveArrayType()
   * @see #getPrimitiveArrayType(int)
   */
  public final int getArrayElementType() {
    if (VM.VerifyAssertions) VM._assert(opcode == JBC_newarray);
    return readUnsignedByte();
  }

  /**
   * Returns the type of the array of given primitive type (as a RVMType)
   * Used for newarray
   * @param etype element type
   * @return array type
   * @see #getArrayElementType()
   * @see #getPrimitiveArrayType()
   */
  public final RVMArray getPrimitiveArrayType(int etype) {
    if (VM.VerifyAssertions) VM._assert(opcode == JBC_newarray);
    return RVMArray.getPrimitiveArrayType(etype);
  }

  /**
   * Returns the type of the primitive array (as a RVMType)
   * Used for newarray
   * @return array type
   * @see #getArrayElementType()
   * @see #getPrimitiveArrayType(int)
   */
  public final RVMType getPrimitiveArrayType() {
    if (VM.VerifyAssertions) VM._assert(opcode == JBC_newarray);
    int etype = readUnsignedByte();
    return RVMArray.getPrimitiveArrayType(etype);
  }

  /**
   * Returns the type of the array of given object type (as a RVMType)
   * Used for anewarray
   * @param klass element type
   * @return array type
   * @see #getTypeReference()
   * @see #getObjectArrayType()
   */
  public final RVMType getObjectArrayType(RVMType klass) {
    if (VM.VerifyAssertions) VM._assert(opcode == JBC_anewarray);
    return klass.getArrayTypeForElementType();
  }

  /**
   * Returns the type of the object array (as a RVMType)
   * Used for anewarray
   * @return array type
   * @see #getObjectArrayType(RVMType)
   */
  public final TypeReference getObjectArrayType() {
    if (VM.VerifyAssertions) VM._assert(opcode == JBC_anewarray);
    TypeReference klass = getTypeReference();
    return klass.getArrayTypeForElementType();
  }

  /**
   * Returns the dimension of the array (as an unsigned byte)
   * Used for multianewarray
   * @return array dimension
   */
  public final int getArrayDimension() {
    if (VM.VerifyAssertions) VM._assert(opcode == JBC_multianewarray);
    return readUnsignedByte();
  }

  /**
   * Returns the opcode of the wide instruction
   * Used for wide
   * Can be one of iload, lload, fload, dload, aload,
   *               istore, lstore, fstore, dstore, astore, iinc
   * @return the opcode of the wide instruction
   */
  public final int getWideOpcode() {
    if (VM.VerifyAssertions) VM._assert(wide && opcode == JBC_wide);
    opcode = readUnsignedByte();
    if (VM.VerifyAssertions) {
      VM._assert((opcode >= JBC_iload && opcode <= JBC_aload) ||
                 (opcode >= JBC_istore && opcode <= JBC_astore) ||
                 opcode == JBC_iinc);
    }
    return opcode;
  }

  /**
   * Returns the constant pool index of a constant (as an unsigned byte)
   * Used for ldc
   * @return constant index
   * @see #getWideConstantIndex()
   * @see #getConstantType(int)
   * @see #getIntConstant(int)
   * @see #getLongConstant(int)
   * @see #getFloatConstant(int)
   * @see #getDoubleConstant(int)
   * @see #getStringConstant(int)
   */
  public final int getConstantIndex() {
    if (VM.VerifyAssertions) VM._assert(opcode == JBC_ldc);
    return readUnsignedByte();
  }

  /**
   * Returns the wide constant pool index of a constant (as an unsigned short)
   * Used for ldc_w, ldc2_w
   * @return wide constant index
   * @see #getConstantIndex()
   * @see #getConstantType(int)
   * @see #getIntConstant(int)
   * @see #getLongConstant(int)
   * @see #getFloatConstant(int)
   * @see #getDoubleConstant(int)
   * @see #getStringConstant(int)
   */
  public final int getWideConstantIndex() {
    if (VM.VerifyAssertions) {
      VM._assert(opcode == JBC_ldc_w || opcode == JBC_ldc2_w);
    }
    return readUnsignedShort();
  }

  /**
   * Returns the type of a constant at a given constant pool index (as a byte)
   * Used for ldc, ldc_w, ldc2_w
   * @return constant type
   * @see #getConstantIndex()
   * @see #getWideConstantIndex()
   * @see #getIntConstant(int)
   * @see #getLongConstant(int)
   * @see #getFloatConstant(int)
   * @see #getDoubleConstant(int)
   * @see #getStringConstant(int)
   */
  public final byte getConstantType(int index) {
    if (VM.VerifyAssertions) {
      VM._assert(opcode == JBC_ldc || opcode == JBC_ldc_w || opcode == JBC_ldc2_w);
    }
    byte desc = getDeclaringClass().getLiteralDescription(index);
    return desc;
  }

  /**
   * Returns the constant at a given constant pool index (as an int)
   * Used for ldc, ldc_w
   * @return int constant
   * @see #getConstantIndex()
   * @see #getWideConstantIndex()
   * @see #getConstantType(int)
   * @see #getLongConstant(int)
   * @see #getFloatConstant(int)
   * @see #getDoubleConstant(int)
   * @see #getStringConstant(int)
   */
  public final int getIntConstant(int index) {
    if (VM.VerifyAssertions) {
      VM._assert((opcode == JBC_ldc || opcode == JBC_ldc_w) &&
                 getDeclaringClass().getLiteralDescription(index) == CP_INT);
    }
    Offset offset = getDeclaringClass().getLiteralOffset(index);
    int val = Statics.getSlotContentsAsInt(offset);
    return val;
  }

  /**
   * Returns the constant at a given constant pool index (as a long)
   * Used for ldc2_w
   * @return long constant
   * @see #getConstantIndex()
   * @see #getWideConstantIndex()
   * @see #getConstantType(int)
   * @see #getIntConstant(int)
   * @see #getFloatConstant(int)
   * @see #getDoubleConstant(int)
   * @see #getStringConstant(int)
   */
  public final long getLongConstant(int index) {
    if (VM.VerifyAssertions) {
      VM._assert(opcode == JBC_ldc2_w && getDeclaringClass().getLiteralDescription(index) == CP_LONG);
    }
    Offset offset = getDeclaringClass().getLiteralOffset(index);
    long val = Statics.getSlotContentsAsLong(offset);
    return val;
  }

  /**
   * Returns the constant at a given constant pool index (as a float)
   * Used for ldc, ldc_w
   * @return float constant
   * @see #getConstantIndex()
   * @see #getWideConstantIndex()
   * @see #getConstantType(int)
   * @see #getIntConstant(int)
   * @see #getLongConstant(int)
   * @see #getDoubleConstant(int)
   * @see #getStringConstant(int)
   */
  public final float getFloatConstant(int index) {
    if (VM.VerifyAssertions) {
      VM._assert((opcode == JBC_ldc || opcode == JBC_ldc_w) &&
                 getDeclaringClass().getLiteralDescription(index) == CP_FLOAT);
    }
    Offset offset = getDeclaringClass().getLiteralOffset(index);
    int val_raw = Statics.getSlotContentsAsInt(offset);
    float val = Float.intBitsToFloat(val_raw);
    return val;
  }

  /**
   * Returns the constant at a given constant pool index (as a double)
   * Used for ldc2_w
   * @return double constant
   * @see #getConstantIndex()
   * @see #getWideConstantIndex()
   * @see #getConstantType(int)
   * @see #getIntConstant(int)
   * @see #getLongConstant(int)
   * @see #getFloatConstant(int)
   * @see #getStringConstant(int)
   */
  public final double getDoubleConstant(int index) {
    if (VM.VerifyAssertions) {
      VM._assert(opcode == JBC_ldc2_w && getDeclaringClass().getLiteralDescription(index) == CP_DOUBLE);
    }
    Offset offset = getDeclaringClass().getLiteralOffset(index);
    long val_raw = Statics.getSlotContentsAsLong(offset);
    double val = Double.longBitsToDouble(val_raw);
    return val;
  }

  /**
   * Returns the constant at a given constant pool index (as a String)
   * Used for ldc, ldc_w
   * @return String constant
   * @see #getConstantIndex()
   * @see #getWideConstantIndex()
   * @see #getConstantType(int)
   * @see #getIntConstant(int)
   * @see #getLongConstant(int)
   * @see #getFloatConstant(int)
   * @see #getDoubleConstant(int)
   */
  public final String getStringConstant(int index) {
    if (VM.VerifyAssertions) {
      VM._assert((opcode == JBC_ldc || opcode == JBC_ldc_w) &&
                 getDeclaringClass().getLiteralDescription(index) == CP_STRING);
    }
    Offset offset = getDeclaringClass().getLiteralOffset(index);
    String val = (String) Statics.getSlotContentsAsObject(offset);
    return val;
  }

  //// HELPER FUNCTIONS

  // Skip a tableswitch or a lookupswitch instruction

  private void skipSpecialInstruction(int opcode) {
    switch (opcode) {
      case JBC_tableswitch: {
        alignSwitch();
        getDefaultSwitchOffset();
        int l = getLowSwitchValue();
        int h = getHighSwitchValue();
        skipTableSwitchOffsets(h - l + 1);  // jump offsets
      }
      break;
      case JBC_lookupswitch: {
        alignSwitch();
        getDefaultSwitchOffset();
        int n = getSwitchLength();
        skipLookupSwitchPairs(n);           // match-offset pairs
      }
      break;
      case JBC_wide: {
        int oc = getWideOpcode();
        int len = JBC_length[oc] - 1;
        bcIndex += len + len;
      }
      break;
      default:
        if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    }
  }

  public final int nextPseudoInstruction() {
    if (VM.VerifyAssertions) VM._assert(opcode == JBC_impdep1);
    return readUnsignedByte();
  }

  public final int readIntConst() {
    if (VM.VerifyAssertions) VM._assert(opcode == JBC_impdep1);
    return readSignedInt();
  }

  public final long readLongConst() {
    if (VM.VerifyAssertions) VM._assert(opcode == JBC_impdep1);
    return readLong();
  }

  @Inline
  private long readLong() {
    if (VM.VerifyAssertions) VM._assert(bcIndex <= bcLength);
    int msb = bcodes[bcIndex++] << (3 * BITS_IN_BYTE);
    msb |= (bcodes[bcIndex++] & 0xFF) << (2 * BITS_IN_BYTE);
    msb |= (bcodes[bcIndex++] & 0xFF) << BITS_IN_BYTE;
    msb |= (bcodes[bcIndex++] & 0xFF);
    int lsb = bcodes[bcIndex++] << (3 * BITS_IN_BYTE);
    lsb |= (bcodes[bcIndex++] & 0xFF) << (2 * BITS_IN_BYTE);
    lsb |= (bcodes[bcIndex++] & 0xFF) << BITS_IN_BYTE;
    lsb |= (bcodes[bcIndex++] & 0xFF);
    return (((long)msb) << 32) | (lsb & 0xFFFFFFFFL);
  }

  //// READ BYTECODES
  @Inline
  private byte readSignedByte() {
    if (VM.VerifyAssertions) VM._assert(bcIndex <= bcLength);
    return bcodes[bcIndex++];
  }

  @Inline
  private int readUnsignedByte() {
    if (VM.VerifyAssertions) VM._assert(bcIndex <= bcLength);
    return bcodes[bcIndex++] & 0xFF;
  }

  @Inline
  private int getUnsignedByte(int index) {
    if (VM.VerifyAssertions) VM._assert(index <= bcLength);
    return bcodes[index] & 0xFF;
  }

  @Inline
  private int readSignedShort() {
    if (VM.VerifyAssertions) VM._assert(bcIndex <= bcLength);
    int i = bcodes[bcIndex++] << BITS_IN_BYTE;
    i |= (bcodes[bcIndex++] & 0xFF);
    return i;
  }

  @Inline
  private int readUnsignedShort() {
    if (VM.VerifyAssertions) VM._assert(bcIndex <= bcLength);
    int i = (bcodes[bcIndex++] & 0xFF) << BITS_IN_BYTE;
    i |= (bcodes[bcIndex++] & 0xFF);
    return i;
  }

  @Inline
  private int readSignedInt() {
    if (VM.VerifyAssertions) VM._assert(bcIndex <= bcLength);
    int i = bcodes[bcIndex++] << (3 * BITS_IN_BYTE);
    i |= (bcodes[bcIndex++] & 0xFF) << (2 * BITS_IN_BYTE);
    i |= (bcodes[bcIndex++] & 0xFF) << BITS_IN_BYTE;
    i |= (bcodes[bcIndex++] & 0xFF);
    return i;
  }

  @Inline
  private int getSignedInt(int index) {
    if (VM.VerifyAssertions) VM._assert(index <= bcLength);
    int i = bcodes[index++] << (3 * BITS_IN_BYTE);
    i |= (bcodes[index++] & 0xFF) << (2 * BITS_IN_BYTE);
    i |= (bcodes[index++] & 0xFF) << BITS_IN_BYTE;
    i |= (bcodes[index] & 0xFF);
    return i;
  }
}

