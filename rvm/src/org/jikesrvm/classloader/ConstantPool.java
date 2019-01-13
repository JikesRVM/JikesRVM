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
package org.jikesrvm.classloader;

import static org.jikesrvm.VM.NOT_REACHED;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_INT;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_LONG;
import static org.jikesrvm.runtime.UnboxedSizeConstants.BYTES_IN_ADDRESS;

import org.jikesrvm.VM;
import org.jikesrvm.runtime.Statics;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Offset;

/**
 * Implements low-level support for the internal constant pool format that Jikes
 * RVM uses. The internal constant pool is currently an {@code int[]} that's
 * created during class loading.
 */
public final class ConstantPool {

  // Constants for our internal encoding of constant pools.
  /** Constant pool entry for a UTF-8 encoded atom */
  public static final byte CP_UTF = 0;
  /** Constant pool entry for int literal */
  public static final byte CP_INT = 1;
  /** Constant pool entry for long literal */
  public static final byte CP_LONG = 2;
  /** Constant pool entry for float literal */
  public static final byte CP_FLOAT = 3;
  /** Constant pool entry for double literal */
  public static final byte CP_DOUBLE = 4;
  /** Constant pool entry for string literal (for annotations, may be other objects) */
  public static final byte CP_STRING = 5;
  /** Constant pool entry for member (field or method) reference */
  public static final byte CP_MEMBER = 6;
  /** Constant pool entry for type reference or class literal */
  public static final byte CP_CLASS = 7;

  private ConstantPool() {
    // prevent instantiation
  }

  /**
   * Get offset of a literal constant, in bytes.
   * Offset is with respect to virtual machine's "table of contents" (jtoc).
   *
   * @param constantPool the constant pool
   * @param constantPoolIndex the index into the constant pool
   * @return the offset in bytes from the JTOC
   */
  static Offset getLiteralOffset(int[] constantPool, int constantPoolIndex) {
    int cpValue = constantPool[constantPoolIndex];
    if (VM.VerifyAssertions) {
      int value = ConstantPool.unpackSignedCPValue(cpValue);
      byte type = ConstantPool.unpackCPType(cpValue);
      switch (type) {
        case CP_INT:
        case CP_FLOAT:
        case CP_LONG:
        case CP_DOUBLE:
        case CP_STRING:
          return Offset.fromIntSignExtend(value);
        case CP_CLASS: {
          int typeId = ConstantPool.unpackUnsignedCPValue(cpValue);
          Class<?> literalAsClass = TypeReference.getTypeRef(typeId).resolve().getClassForType();
          return Offset.fromIntSignExtend(Statics.findOrCreateObjectLiteral(literalAsClass));
        }
        default:
          VM._assert(NOT_REACHED);
          return Offset.fromIntSignExtend(0xebad0ff5);
      }
    } else {
      if (ConstantPool.packedCPTypeIsClassType(cpValue)) {
        int typeId = ConstantPool.unpackUnsignedCPValue(cpValue);
        Class<?> literalAsClass = TypeReference.getTypeRef(typeId).resolve().getClassForType();
        return Offset.fromIntSignExtend(Statics.findOrCreateObjectLiteral(literalAsClass));
      } else {
        int value = ConstantPool.unpackSignedCPValue(cpValue);
        return Offset.fromIntSignExtend(value);
      }
    }
  }

  static byte getLiteralDescription(int[] constantPool, int constantPoolIndex) {
    int cpValue = constantPool[constantPoolIndex];
    byte type = ConstantPool.unpackCPType(cpValue);
    return type;
  }

  @Uninterruptible
  static TypeReference getTypeRef(int[] constantPool, int constantPoolIndex) {
    if (constantPoolIndex != 0) {
      int cpValue = constantPool[constantPoolIndex];
      if (VM.VerifyAssertions) VM._assert(ConstantPool.unpackCPType(cpValue) == CP_CLASS);
      return TypeReference.getTypeRef(ConstantPool.unpackUnsignedCPValue(cpValue));
    } else {
      return null;
    }
  }

  @Uninterruptible
  static MethodReference getMethodRef(int[] constantPool, int constantPoolIndex) {
    int cpValue = constantPool[constantPoolIndex];
    if (VM.VerifyAssertions) VM._assert(ConstantPool.unpackCPType(cpValue) == CP_MEMBER);
    return (MethodReference) MemberReference.getMemberRef(ConstantPool.unpackUnsignedCPValue(cpValue));
  }

  @Uninterruptible
  static FieldReference getFieldRef(int[] constantPool, int constantPoolIndex) {
    int cpValue = constantPool[constantPoolIndex];
    if (VM.VerifyAssertions) VM._assert(ConstantPool.unpackCPType(cpValue) == CP_MEMBER);
    return (FieldReference) MemberReference.getMemberRef(ConstantPool.unpackUnsignedCPValue(cpValue));
  }

  @Uninterruptible
  static Atom getUtf(int[] constantPool, int constantPoolIndex) {
    int cpValue = constantPool[constantPoolIndex];
    if (VM.VerifyAssertions) VM._assert(ConstantPool.unpackCPType(cpValue) == CP_UTF);
    return Atom.getAtom(ConstantPool.unpackUnsignedCPValue(cpValue));
  }

  @Uninterruptible
  static int packCPEntry(byte type, int value) {
    return (type << 29) | (value & 0x1fffffff);
  }

  static int getLiteralSize(int[] constantPool, int constantPoolIndex) {
    int cpValue = constantPool[constantPoolIndex];
    switch (ConstantPool.unpackCPType(cpValue)) {
      case CP_INT:
      case CP_FLOAT:
        return BYTES_IN_INT;
      case CP_LONG:
      case CP_DOUBLE:
        return BYTES_IN_LONG;
      case CP_CLASS:
      case CP_STRING:
        return BYTES_IN_ADDRESS;
      default:
        if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
        return 0;
    }
  }

  @Uninterruptible
  static byte unpackCPType(int cpValue) {
    return (byte) (cpValue >>> 29);
  }

  @Uninterruptible
  static int unpackSignedCPValue(int cpValue) {
    return (cpValue << 3) >> 3;
  }

  @Uninterruptible
  static int unpackUnsignedCPValue(int cpValue) {
    return cpValue & 0x1fffffff;
  }

  @Uninterruptible
  static boolean packedCPTypeIsClassType(int cpValue) {
    return (cpValue & (7 << 29)) == (CP_CLASS << 29);
  }

  @Uninterruptible
  static int packTempCPEntry(int index1, int index2) {
    return (index1 << 16) | (index2 & 0xffff);
  }

  @Uninterruptible
  static int unpackTempCPIndex1(int cpValue) {
    return cpValue >>> 16;
  }

  @Uninterruptible
  static int unpackTempCPIndex2(int cpValue) {
    return cpValue & 0xffff;
  }

}
