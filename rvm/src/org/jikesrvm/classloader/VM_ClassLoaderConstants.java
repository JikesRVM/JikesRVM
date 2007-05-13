/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2001,2002
 */
package org.jikesrvm.classloader;

interface VM_ClassLoaderConstants {
  // Attribute modifiers for class-, method-, and field- descriptions.
  //
  //                                      applicability
  //           name        value       class  field  method
  //    ---------------   --------     -----  -----  ------
  short ACC_PUBLIC = 0x0001;  //   X      X      X
  short ACC_PRIVATE = 0x0002;  //   X      X      X (applicable to inner classes)
  short ACC_PROTECTED = 0x0004;  //   X      X      X (applicable to inner classes)
  short ACC_STATIC = 0x0008;  //   X      X      X (applicable to inner classes)
  short ACC_FINAL = 0x0010;  //   X      X      X
  short ACC_SYNCHRONIZED = 0x0020;  //   -      -      X  <- same value as ACC_SUPER
  short ACC_SUPER = 0x0020;  //   X      -      -  <- same value as ACC_SYNCHRONIZED
  short ACC_VOLATILE = 0x0040;  //   -      X      -
  short BRIDGE = 0x0040;  //   -      -      X  <- same value as ACC_VOLATILE
  short ACC_TRANSIENT = 0x0080;  //   -      X      -
  short VARARGS = 0x0080;  //   -      -      X  <- same value as ACC_TRANSIENT
  short ACC_NATIVE = 0x0100;  //   -      -      X
  short ACC_INTERFACE = 0x0200;  //   X      -      -
  short ACC_ABSTRACT = 0x0400;  //   X      -      X
  short ACC_STRICT = 0x0800;  //   -      -      X
  short ACC_SYNTHETIC = 0x1000;  //   X      X      X
  short ACC_ANNOTATION = 0x2000;  //   X      -      -
  short ACC_ENUM = 0x4000;  //   X      X      -

  short APPLICABLE_TO_FIELDS = (ACC_PUBLIC |
                                ACC_PRIVATE |
                                ACC_PROTECTED |
                                ACC_STATIC |
                                ACC_FINAL |
                                ACC_VOLATILE |
                                ACC_TRANSIENT |
                                ACC_SYNTHETIC |
                                ACC_ENUM);

  short APPLICABLE_TO_METHODS = (ACC_PUBLIC |
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

  short APPLICABLE_TO_CLASSES = (ACC_PUBLIC |
                                 ACC_PRIVATE |
                                 ACC_STATIC |
                                 ACC_FINAL |
                                 ACC_SUPER |
                                 ACC_INTERFACE |
                                 ACC_ABSTRACT |
                                 ACC_SYNTHETIC |
                                 ACC_ANNOTATION |
                                 ACC_ENUM);

  // Possible states of a class description.
  //
  byte CLASS_VACANT = 0; // nothing present yet
  byte CLASS_LOADED = 1; // .class file contents read successfully
  byte CLASS_RESOLVED = 2; // fields & methods layed out, tib & statics allocated
  byte CLASS_INSTANTIATED = 3; // tib and jtoc populated
  byte CLASS_INITIALIZING = 4; // <clinit> is running
  byte CLASS_INITIALIZED = 5; // statics initialized

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
}
