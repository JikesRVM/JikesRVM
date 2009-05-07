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

public interface ClassLoaderConstants {
  // Attribute modifiers for class-, method-, and field- descriptions.
  //
  //                                      applicability
  //           name        value       class  field  method
  //    ---------------   --------     -----  -----  ------
  short ACC_PUBLIC       = 0x0001;  //   X      X      X
  short ACC_PRIVATE      = 0x0002;  //   X      X      X (applicable to inner classes)
  short ACC_PROTECTED    = 0x0004;  //   X      X      X (applicable to inner classes)
  short ACC_STATIC       = 0x0008;  //   X      X      X (applicable to inner classes)
  short ACC_FINAL        = 0x0010;  //   X      X      X
  short ACC_SYNCHRONIZED = 0x0020;  //   -      -      X  <- same value as ACC_SUPER
  short ACC_SUPER        = 0x0020;  //   X      -      -  <- same value as ACC_SYNCHRONIZED
  short ACC_VOLATILE     = 0x0040;  //   -      X      -
  short BRIDGE           = 0x0040;  //   -      -      X  <- same value as ACC_VOLATILE
  short ACC_TRANSIENT    = 0x0080;  //   -      X      -
  short VARARGS          = 0x0080;  //   -      -      X  <- same value as ACC_TRANSIENT
  short ACC_NATIVE       = 0x0100;  //   -      -      X
  short ACC_INTERFACE    = 0x0200;  //   X      -      -
  short ACC_ABSTRACT     = 0x0400;  //   X      -      X
  short ACC_STRICT       = 0x0800;  //   -      -      X
  short ACC_SYNTHETIC    = 0x1000;  //   X      X      X
  short ACC_ANNOTATION   = 0x2000;  //   X      -      -
  short ACC_ENUM         = 0x4000;  //   X      X      -

  short APPLICABLE_TO_FIELDS =
      (ACC_PUBLIC |
       ACC_PRIVATE |
       ACC_PROTECTED |
       ACC_STATIC |
       ACC_FINAL |
       ACC_VOLATILE |
       ACC_TRANSIENT |
       ACC_SYNTHETIC |
       ACC_ENUM);

  short APPLICABLE_TO_METHODS =
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

  short APPLICABLE_TO_CLASSES =
      (ACC_PUBLIC |
       ACC_PRIVATE |
       ACC_STATIC |
       ACC_FINAL |
       ACC_SUPER |
       ACC_INTERFACE |
       ACC_ABSTRACT |
       ACC_SYNTHETIC |
       ACC_ANNOTATION |
       ACC_ENUM);

  /* Possible states of a class description. */
  /** nothing present yet */
  byte CLASS_VACANT = 0;
  /** .class file contents read successfully */
  byte CLASS_LOADED = 1;
  /** fields &amp; methods laid out, tib &amp; statics allocated */
  byte CLASS_RESOLVED = 2;
  /** tib and jtoc populated */
  byte CLASS_INSTANTIATED = 3;
  /** &lt;clinit&gt; running (allocations possible) */
  byte CLASS_INITIALIZING = 4;
  /** exception occurred while running &lt;clinit&gt; class cannot be initialized successfully */
  byte CLASS_INITIALIZER_FAILED = 5;
  /** statics initialized */
  byte CLASS_INITIALIZED = 6;

  // Constant pool entry tags.
  //
  byte TAG_UTF = 1;
  byte TAG_UNUSED = 2;
  byte TAG_INT = 3;
  byte TAG_FLOAT = 4;
  byte TAG_LONG = 5;
  byte TAG_DOUBLE = 6;
  byte TAG_TYPEREF = 7;
  byte TAG_STRING = 8;
  byte TAG_FIELDREF = 9;
  byte TAG_METHODREF = 10;
  byte TAG_INTERFACE_METHODREF = 11;
  byte TAG_MEMBERNAME_AND_DESCRIPTOR = 12;

  // Type codes for class, array, and primitive types.
  //
  byte ClassTypeCode = (byte) 'L';
  byte ArrayTypeCode = (byte) '[';
  byte VoidTypeCode = (byte) 'V';
  byte BooleanTypeCode = (byte) 'Z';
  byte ByteTypeCode = (byte) 'B';
  byte ShortTypeCode = (byte) 'S';
  byte IntTypeCode = (byte) 'I';
  byte LongTypeCode = (byte) 'J';
  byte FloatTypeCode = (byte) 'F';
  byte DoubleTypeCode = (byte) 'D';
  byte CharTypeCode = (byte) 'C';

  // Constants for our internal encoding of constant pools.
  /** Constant pool entry for a UTF-8 encoded atom */
  byte CP_UTF = 0;
  /** Constant pool entry for int literal */
  byte CP_INT = 1;
  /** Constant pool entry for long literal */
  byte CP_LONG = 2;
  /** Constant pool entry for float literal */
  byte CP_FLOAT = 3;
  /** Constant pool entry for double literal */
  byte CP_DOUBLE = 4;
  /** Constant pool entry for string literal (for annotations, may be other objects) */
  byte CP_STRING = 5;
  /** Constant pool entry for member (field or method) reference */
  byte CP_MEMBER = 6;
  /** Constant pool entry for type reference or class literal */
  byte CP_CLASS = 7;
}
