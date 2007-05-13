/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.objectmodel;

import org.jikesrvm.VM;

/**
 * Layout the TIB (Type Information Block).
 */

public interface VM_TIBLayoutConstants {

   //--------------------------------------------------------------------------------------------//
   //                      Type Information Block (TIB) Layout Constants                         //
   //--------------------------------------------------------------------------------------------//
   // NOTE: only a subset of the fixed TIB slots (1..6) will actually
   //       be allocated in any configuration. 
   //       When a slots is not allocated, we slide the other slots up.
   //       The interface slots (1..k) are only allocated when using IMT
   //       directly embedded in the TIB
   //
   //                               Object[] (type info block)        VM_Type (class info)
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
   //        4:|     type cache     +-->              
   //          +--------------------+
   //        5:|     iTABLES        +-->              
   //          +--------------------+
   //        6:|  indirect IMT      +-->              
   //          +--------------------+
   //        1:|  interface slot 0  |              
   //          +--------------------+              
   //          |       ...          |
   //          +--------------------+
   //        K:| interface slot K-1 |
   //          +--------------------+
   //      K+1:|  virtual method 0  +-----+
   //          +--------------------+     |        
   //          |       ...          |     |                         INSTRUCTION[] (machine code)
   //          +--------------------+     |                        /
   //      K+N:| virtual method N-1 |     |        +--------------+
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
   int IMT_METHOD_SLOTS =
     VM.BuildForIMTInterfaceInvocation ? 29 : 0;
   
   int TIB_INTERFACE_METHOD_SLOTS =
     VM.BuildForEmbeddedIMT ? IMT_METHOD_SLOTS : 0;
   
   // First slot of tib points to VM_Type (slot 0 in above diagram).
   //
   int TIB_TYPE_INDEX = 0;

   // A vector of ids for classes that this one extends. 
   // (see vm/classLoader/VM_DynamicTypeCheck.java)
   //
   int TIB_SUPERCLASS_IDS_INDEX = TIB_TYPE_INDEX + 1;

   // "Does this class implement the ith interface?"  
   // (see vm/classLoader/VM_DynamicTypeCheck.java)
   //
   int TIB_DOES_IMPLEMENT_INDEX = TIB_SUPERCLASS_IDS_INDEX + 1;
    
   // The TIB of the elements type of an array (may be null in fringe cases
   // when element type couldn't be resolved during array resolution).
   // Will be null when not an array.
   //
   int TIB_ARRAY_ELEMENT_TIB_INDEX = TIB_DOES_IMPLEMENT_INDEX + 1;

   // If VM.ITableInterfaceInvocation then allocate 1 TIB entry to hold 
   // an array of ITABLES
   int TIB_ITABLES_TIB_INDEX =
     TIB_DOES_IMPLEMENT_INDEX + (VM.BuildForITableInterfaceInvocation ? 1 : 0);
   
   // If VM.BuildForIndirectIMT then allocate 1 TIB entry to hold a
   // pointer to the IMT
   int TIB_IMT_TIB_INDEX =
     TIB_ITABLES_TIB_INDEX + (VM.BuildForIndirectIMT ? 1 : 0);
     
   // Next group of slots point to interface method code blocks 
   // (slots 1..K in above diagram).
   int TIB_FIRST_INTERFACE_METHOD_INDEX = TIB_IMT_TIB_INDEX + 1;
 
   // Next group of slots point to virtual method code blocks 
   // (slots K+1..K+N in above diagram).
   int TIB_FIRST_VIRTUAL_METHOD_INDEX =
     TIB_FIRST_INTERFACE_METHOD_INDEX + TIB_INTERFACE_METHOD_SLOTS;

  /**
   * Special value returned by VM_ClassLoader.getFieldOffset() or
   * VM_ClassLoader.getMethodOffset() to indicate fields or methods
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
  int NEEDS_DYNAMIC_LINK = Short.MIN_VALUE+1;
}

