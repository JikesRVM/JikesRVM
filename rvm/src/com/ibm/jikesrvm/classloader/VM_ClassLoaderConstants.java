/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.jikesrvm.classloader;

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
  int ACC_PUBLIC       = 0x00000001;  //   X      X      X
  int ACC_PRIVATE      = 0x00000002;  //   X      X      X (applicable to inner classes)
  int ACC_PROTECTED    = 0x00000004;  //   X      X      X (applicable to inner classes)
  int ACC_STATIC       = 0x00000008;  //   X      X      X (applicable to inner classes)
  int ACC_FINAL        = 0x00000010;  //   X      X      X
  int ACC_SYNCHRONIZED = 0x00000020;  //   -      -      X  <- same value as ACC_SUPER
  int ACC_SUPER        = 0x00000020;  //   X      -      -  <- same value as ACC_SYNCHRONIZED
  int ACC_VOLATILE     = 0x00000040;  //   -      X      -
  int BRIDGE           = 0x00000040;  //   -      -      X  <- same value as ACC_VOLATILE
  int ACC_TRANSIENT    = 0x00000080;  //   -      X      -
  int VARARGS          = 0x00000080;  //   -      -      X  <- same value as ACC_TRANSIENT
  int ACC_NATIVE       = 0x00000100;  //   -      -      X
  int ACC_INTERFACE    = 0x00000200;  //   X      -      -
  int ACC_ABSTRACT     = 0x00000400;  //   X      -      X
  int ACC_STRICT       = 0x00000800;  //   -      -      X
  int ACC_SYNTHETIC    = 0x00001000;  //   X      X      X
  int ACC_ANNOTATION   = 0x00002000;  //   X      -      -
  int ACC_ENUM         = 0x00004000;  //   X      X      -

  int APPLICABLE_TO_FIELDS = (ACC_PUBLIC |
                                           ACC_PRIVATE |
                                           ACC_PROTECTED |
                                           ACC_STATIC | 
                                           ACC_FINAL |
                                           ACC_VOLATILE |
                                           ACC_TRANSIENT |
                                           ACC_SYNTHETIC |                                           
                                           ACC_ENUM);

  int APPLICABLE_TO_METHODS = (ACC_PUBLIC |
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

  int APPLICABLE_TO_CLASSES = (ACC_PUBLIC |
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
  int CLASS_VACANT       = 0; // nothing present yet
  int CLASS_LOADED       = 1; // .class file contents read successfully
  int CLASS_RESOLVED     = 2; // fields & methods layed out, tib & statics allocated
  int CLASS_INSTANTIATED = 3; // tib and jtoc populated
  int CLASS_INITIALIZING = 4; // <clinit> is running
  int CLASS_INITIALIZED  = 5; // statics initialized
  
  // Constant pool entry tags.
  //
  byte TAG_UTF                        =  1;
  byte TAG_UNUSED                     =  2;
  byte TAG_INT                        =  3;
  byte TAG_FLOAT                      =  4;
  byte TAG_LONG                       =  5;
  byte TAG_DOUBLE                     =  6;
  byte TAG_TYPEREF                    =  7;
  byte TAG_STRING                     =  8;
  byte TAG_FIELDREF                   =  9;
  byte TAG_METHODREF                  = 10;
  byte TAG_INTERFACE_METHODREF        = 11;
  byte TAG_MEMBERNAME_AND_DESCRIPTOR  = 12;

  // Type codes for class, array, and primitive types.
  //
  byte ClassTypeCode   = (byte)'L';
  byte ArrayTypeCode   = (byte)'[';
  byte VoidTypeCode    = (byte)'V';
  byte BooleanTypeCode = (byte)'Z';
  byte ByteTypeCode    = (byte)'B';
  byte ShortTypeCode   = (byte)'S';
  byte IntTypeCode     = (byte)'I';
  byte LongTypeCode    = (byte)'J';
  byte FloatTypeCode   = (byte)'F';
  byte DoubleTypeCode  = (byte)'D';
  byte CharTypeCode    = (byte)'C';
}
