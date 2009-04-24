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
package org.jikesrvm.objectmodel;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.SpecializedMethodManager;

/**
 * Layout the TIB (Type Information Block).
 */

public interface TIBLayoutConstants {

  //--------------------------------------------------------------------------------------------//
  //                      Type Information Block (TIB) Layout Constants                         //
  //--------------------------------------------------------------------------------------------//
  //
  //                               Object[] (type info block)        RVMType (class info)
  //                                  /                              /
  //          +--------------------+              +--------------+
  //          |    TIB pointer     |              |  TIB pointer |
  //          +--------------------+              +--------------+
  //          |      status        |              |    status    |
  //          +--------------------+              +--------------+
  //          |      length        |              |    field0    |
  //          +--------------------+              +--------------+
  //  TIB:  0:|       type         +------------> |     ...      |
  //          +--------------------+              +--------------+
  //        1:|   superclass ids   +-->           |   fieldN-1   |
  //          +--------------------+              +--------------+
  //        2:|  implements trits  +-->
  //          +--------------------+
  //        3:|  array element TIB +-->
  //          +--------------------+
  //        4:|     iTABLES/IMT    +-->
  //          +--------------------+
  //        5:|  specialized 0     +-->
  //          +--------------------+
  //          |       ...          +-->
  //          +--------------------+
  //       V0:|  virtual method 0  +-----+
  //          +--------------------+     |
  //          |       ...          |     |                         INSTRUCTION[] (machine code)
  //          +--------------------+     |                        /
  //     VN-1:| virtual method N-1 |     |        +--------------+
  //          +--------------------+     |        |  TIB pointer |
  //                                     |        +--------------+
  //                                     |        |    status    |
  //                                     |        +--------------+
  //                                     |        |    length    |
  //                                     |        +--------------+
  //                                     +------->|    code0     |
  //                                              +--------------+
  //                                              |      ...     |
  //                                              +--------------+
  //                                              |    codeN-1   |
  //                                              +--------------+
  //

  // Number of slots reserved for interface method pointers.
  //
  int IMT_METHOD_SLOTS = VM.BuildForIMTInterfaceInvocation ? 29 : 0;

  // First slot of tib points to RVMType (slot 0 in above diagram).
  //
  int TIB_TYPE_INDEX = 0;

  // A vector of ids for classes that this one extends.
  // (see vm/classLoader/DynamicTypeCheck.java)
  //
  int TIB_SUPERCLASS_IDS_INDEX = TIB_TYPE_INDEX + 1;

  // "Does this class implement the ith interface?"
  // (see vm/classLoader/DynamicTypeCheck.java)
  //
  int TIB_DOES_IMPLEMENT_INDEX = TIB_SUPERCLASS_IDS_INDEX + 1;

  // The TIB of the elements type of an array (may be null in fringe cases
  // when element type couldn't be resolved during array resolution).
  // Will be null when not an array.
  //
  int TIB_ARRAY_ELEMENT_TIB_INDEX = TIB_DOES_IMPLEMENT_INDEX + 1;

  // A pointer to either an ITable or InterfaceMethodTable (IMT)
  // depending on which dispatch implementation we are using.
  int TIB_INTERFACE_DISPATCH_TABLE_INDEX = TIB_ARRAY_ELEMENT_TIB_INDEX + 1;

  // A set of 0 or more specialized methods used in the VM such as for GC scanning
  int TIB_FIRST_SPECIALIZED_METHOD_INDEX = TIB_INTERFACE_DISPATCH_TABLE_INDEX + 1;

  // Next group of slots point to virtual method code blocks
  // (slots V1..VN in above diagram).
  int TIB_FIRST_VIRTUAL_METHOD_INDEX = TIB_FIRST_SPECIALIZED_METHOD_INDEX + SpecializedMethodManager.numSpecializedMethods();

  /**
   * Special value returned by RVMClassLoader.getFieldOffset() or
   * RVMClassLoader.getMethodOffset() to indicate fields or methods
   * that must be accessed via dynamic linking code because their
   * offset is not yet known or the class's static initializer has not
   * yet been run.
   *
   *  We choose a value that will never match a valid jtoc-,
   *  instance-, or virtual method table- offset. Short.MIN_VALUE+1 is
   *  a good value:
   *
   *  <ul>
   *  <li>the jtoc offsets are aligned and this value should be
   *  too huge to address the table</li>
   *  <li>instance field offsets are always &gte; -4</li>
   *  <li>virtual method offsets are always positive w.r.t. TIB pointer</li>
   *  <li>fits into a PowerPC 16bit immediate operand</li>
   *   </ul>
   */
  int NEEDS_DYNAMIC_LINK = Short.MIN_VALUE + 1;
}

