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

public final class ClassLoaderConstants {
  // Attribute modifiers for class-, method-, and field- descriptions.
  //
  //                                      applicability
  //           name        value       class  field  method
  //    ---------------   --------     -----  -----  ------
  public static final short ACC_PUBLIC       = 0x0001;  //   X      X      X
  public static final short ACC_PRIVATE      = 0x0002;  //   X      X      X (applicable to inner classes)
  public static final short ACC_PROTECTED    = 0x0004;  //   X      X      X (applicable to inner classes)
  public static final short ACC_STATIC       = 0x0008;  //   X      X      X (applicable to inner classes)
  public static final short ACC_FINAL        = 0x0010;  //   X      X      X
  public static final short ACC_SYNCHRONIZED = 0x0020;  //   -      -      X  <- same value as ACC_SUPER
  public static final short ACC_SUPER        = 0x0020;  //   X      -      -  <- same value as ACC_SYNCHRONIZED
  public static final short ACC_VOLATILE     = 0x0040;  //   -      X      -
  public static final short BRIDGE           = 0x0040;  //   -      -      X  <- same value as ACC_VOLATILE
  public static final short ACC_TRANSIENT    = 0x0080;  //   -      X      -
  public static final short VARARGS          = 0x0080;  //   -      -      X  <- same value as ACC_TRANSIENT
  public static final short ACC_NATIVE       = 0x0100;  //   -      -      X
  public static final short ACC_INTERFACE    = 0x0200;  //   X      -      -
  public static final short ACC_ABSTRACT     = 0x0400;  //   X      -      X
  public static final short ACC_STRICT       = 0x0800;  //   -      -      X
  public static final short ACC_SYNTHETIC    = 0x1000;  //   X      X      X
  public static final short ACC_ANNOTATION   = 0x2000;  //   X      -      -
  public static final short ACC_ENUM         = 0x4000;  //   X      X      -

  public static final short APPLICABLE_TO_FIELDS =
      (ACC_PUBLIC |
       ACC_PRIVATE |
       ACC_PROTECTED |
       ACC_STATIC |
       ACC_FINAL |
       ACC_VOLATILE |
       ACC_TRANSIENT |
       ACC_SYNTHETIC |
       ACC_ENUM);

  public static final short APPLICABLE_TO_METHODS =
      (ACC_PUBLIC |
       ACC_PRIVATE |
       ACC_PROTECTED |
       ACC_STATIC |
       ACC_FINAL |
       ACC_SYNCHRONIZED |
       BRIDGE |
       VARARGS |
       ACC_NATIVE |
       ACC_ABSTRACT |
       ACC_STRICT |
       ACC_SYNTHETIC);

  public static final short APPLICABLE_TO_CLASSES =
      (ACC_PUBLIC |
       ACC_PRIVATE |
       ACC_PROTECTED |
       ACC_STATIC |
       ACC_FINAL |
       ACC_SUPER |
       ACC_INTERFACE |
       ACC_ABSTRACT |
       ACC_SYNTHETIC |
       ACC_ANNOTATION |
       ACC_ENUM);

  /**
   * The modifiers that can appear in the return value of
   * {@link java.lang.Class#getModifiers()} according to
   * the Java API specification.
   */
  public static final short APPLICABLE_FOR_CLASS_GET_MODIFIERS =
      (ACC_PUBLIC |
       ACC_PRIVATE |
       ACC_PROTECTED |
       ACC_STATIC |
       ACC_FINAL |
       ACC_INTERFACE |
       ACC_ABSTRACT);

  /* Possible states of a class description. */
  /** nothing present yet */
  public static final byte CLASS_VACANT = 0;
  /** .class file contents read successfully */
  public static final byte CLASS_LOADED = 1;
  /** fields &amp; methods laid out, tib &amp; statics allocated */
  public static final byte CLASS_RESOLVED = 2;
  /** tib and jtoc populated */
  public static final byte CLASS_INSTANTIATED = 3;
  /** &lt;clinit&gt; running (allocations possible) */
  public static final byte CLASS_INITIALIZING = 4;
  /** exception occurred while running &lt;clinit&gt; class cannot be initialized successfully */
  public static final byte CLASS_INITIALIZER_FAILED = 5;
  /** statics initialized */
  public static final byte CLASS_INITIALIZED = 6;

  // Constant pool entry tags.
  //
  public static final byte TAG_UTF = 1;
  public static final byte TAG_UNUSED = 2;
  public static final byte TAG_INT = 3;
  public static final byte TAG_FLOAT = 4;
  public static final byte TAG_LONG = 5;
  public static final byte TAG_DOUBLE = 6;
  public static final byte TAG_TYPEREF = 7;
  public static final byte TAG_STRING = 8;
  public static final byte TAG_FIELDREF = 9;
  public static final byte TAG_METHODREF = 10;
  public static final byte TAG_INTERFACE_METHODREF = 11;
  public static final byte TAG_MEMBERNAME_AND_DESCRIPTOR = 12;

  // Type codes for class, array, and primitive types.
  //
  public static final byte ClassTypeCode = (byte) 'L';
  public static final byte ArrayTypeCode = (byte) '[';
  public static final byte VoidTypeCode = (byte) 'V';
  public static final byte BooleanTypeCode = (byte) 'Z';
  public static final byte ByteTypeCode = (byte) 'B';
  public static final byte ShortTypeCode = (byte) 'S';
  public static final byte IntTypeCode = (byte) 'I';
  public static final byte LongTypeCode = (byte) 'J';
  public static final byte FloatTypeCode = (byte) 'F';
  public static final byte DoubleTypeCode = (byte) 'D';
  public static final byte CharTypeCode = (byte) 'C';

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

  private ClassLoaderConstants() {
    // prevent instantiation
  }

}
