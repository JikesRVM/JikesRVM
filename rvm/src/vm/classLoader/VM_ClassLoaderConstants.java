/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM.classloader;

/**
 * @author Bowen Alpern
 * @author Derek Lieber
 */
interface VM_ClassLoaderConstants {
  // Attribute modifiers for class-, method-, and field- descriptions.
  //
  //                                                    applicability
  //                      name         value         class  field  method
  //               ---------------   ----------      -----  -----  ------
  static final int ACC_PUBLIC       = 0x00000001;  //   X      X      X
  static final int ACC_PRIVATE      = 0x00000002;  //   X      X      X (applicable to inner classes)
  static final int ACC_PROTECTED    = 0x00000004;  //   X      X      X (applicable to inner classes)
  static final int ACC_STATIC       = 0x00000008;  //   X      X      X (applicable to inner classes)
  static final int ACC_FINAL        = 0x00000010;  //   X      X      X
  static final int ACC_SYNCHRONIZED = 0x00000020;  //   -      -      X  <- same value as ACC_SPECIAL
  static final int ACC_SUPER        = 0x00000020;  //   X      -      -  <- same value as ACC_SYNCHRONIZED
  static final int ACC_VOLATILE     = 0x00000040;  //   -      X      -
  static final int ACC_TRANSIENT    = 0x00000080;  //   -      X      -
  static final int ACC_NATIVE       = 0x00000100;  //   -      -      X
  static final int ACC_INTERFACE    = 0x00000200;  //   X      -      -
  static final int ACC_ABSTRACT     = 0x00000400;  //   X      -      X
  static final int ACC_STRICT       = 0x00000800;  //   -      -      X

  static final int APPLICABLE_TO_FIELDS = (ACC_PUBLIC | 
                                           ACC_PRIVATE |
                                           ACC_PROTECTED |
                                           ACC_STATIC | 
                                           ACC_FINAL |
                                           ACC_VOLATILE |
                                           ACC_TRANSIENT);

  static final int APPLICABLE_TO_METHODS = (ACC_PUBLIC | 
                                            ACC_PRIVATE | 
                                            ACC_PROTECTED | 
                                            ACC_STATIC | 
                                            ACC_FINAL | 
                                            ACC_SYNCHRONIZED | 
                                            ACC_NATIVE | 
                                            ACC_ABSTRACT | 
                                            ACC_STRICT);

  static final int APPLICABLE_TO_CLASSES = (ACC_PUBLIC | 
                                            ACC_PRIVATE | 
                                            ACC_FINAL | 
                                            ACC_SUPER | 
                                            ACC_INTERFACE | 
                                            ACC_ABSTRACT);

  // Possible states of a class description.
  //
  static final int CLASS_VACANT       = 0; // nothing present yet
  static final int CLASS_LOADED       = 1; // .class file contents read successfully
  static final int CLASS_RESOLVED     = 2; // fields & methods layed out, tib & statics allocated
  static final int CLASS_INSTANTIATED = 3; // tib and jtoc populated
  static final int CLASS_INITIALIZING = 4; // <clinit> is running
  static final int CLASS_INITIALIZED  = 5; // statics initialized
  
  // Constant pool entry tags.
  //
  static final byte TAG_UTF                        =  1;
  static final byte TAG_UNUSED                     =  2;
  static final byte TAG_INT                        =  3;
  static final byte TAG_FLOAT                      =  4;
  static final byte TAG_LONG                       =  5;
  static final byte TAG_DOUBLE                     =  6;
  static final byte TAG_TYPEREF                    =  7;
  static final byte TAG_STRING                     =  8;
  static final byte TAG_FIELDREF                   =  9;
  static final byte TAG_METHODREF                  = 10;
  static final byte TAG_INTERFACE_METHODREF        = 11;
  static final byte TAG_MEMBERNAME_AND_DESCRIPTOR  = 12;

  // Type codes for class, array, and primitive types.
  //
  static final byte ClassTypeCode   = (byte)'L';
  static final byte ArrayTypeCode   = (byte)'[';
  static final byte VoidTypeCode    = (byte)'V';
  static final byte BooleanTypeCode = (byte)'Z';
  static final byte ByteTypeCode    = (byte)'B';
  static final byte ShortTypeCode   = (byte)'S';
  static final byte IntTypeCode     = (byte)'I';
  static final byte LongTypeCode    = (byte)'J';
  static final byte FloatTypeCode   = (byte)'F';
  static final byte DoubleTypeCode  = (byte)'D';
  static final byte CharTypeCode    = (byte)'C';
}
