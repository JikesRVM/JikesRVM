/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id:
package com.ibm.JikesRVM;

/**
 * Layout the TIB (Type Information Block).
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 * @author David Bacon
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
   static final int IMT_METHOD_SLOTS = 
     VM.BuildForIMTInterfaceInvocation ? 29 : 0;
   
   static final int TIB_INTERFACE_METHOD_SLOTS = 
     VM.BuildForEmbeddedIMT ? IMT_METHOD_SLOTS : 0;
   
   // First slot of tib points to VM_Type (slot 0 in above diagram).
   //
   static final int TIB_TYPE_INDEX = 0;

   // A vector of ids for classes that this one extends. 
   // (see vm/classLoader/VM_DynamicTypeCheck.java)
   //
   static final int TIB_SUPERCLASS_IDS_INDEX = TIB_TYPE_INDEX + 1;

   // "Does this class implement the ith interface?"  
   // (see vm/classLoader/VM_DynamicTypeCheck.java)
   //
   static final int TIB_DOES_IMPLEMENT_INDEX = TIB_SUPERCLASS_IDS_INDEX + 1;
    
   // The TIB of the elements type of an array (may be null in fringe cases
   // when element type couldn't be resolved during array resolution).
   // Will be null when not an array.
   //
   static final int TIB_ARRAY_ELEMENT_TIB_INDEX = TIB_DOES_IMPLEMENT_INDEX + 1;

   // If VM.ITableInterfaceInvocation then allocate 1 TIB entry to hold 
   // an array of ITABLES
   static final int TIB_ITABLES_TIB_INDEX = 
     TIB_DOES_IMPLEMENT_INDEX + (VM.BuildForITableInterfaceInvocation ? 1 : 0);
   
   // If VM.BuildForIndirectIMT then allocate 1 TIB entry to hold a
   // pointer to the IMT
   static final int TIB_IMT_TIB_INDEX = 
     TIB_ITABLES_TIB_INDEX + (VM.BuildForIndirectIMT ? 1 : 0);
     
   // Next group of slots point to interface method code blocks 
   // (slots 1..K in above diagram).
   static final int TIB_FIRST_INTERFACE_METHOD_INDEX = TIB_IMT_TIB_INDEX + 1;
 
   // Next group of slots point to virtual method code blocks 
   // (slots K+1..K+N in above diagram).
   static final int TIB_FIRST_VIRTUAL_METHOD_INDEX = 
     TIB_FIRST_INTERFACE_METHOD_INDEX + TIB_INTERFACE_METHOD_SLOTS;

   // Special value returned by VM_ClassLoader.getFieldOffset() or VM_ClassLoader.getMethodOffset()
   // to indicate fields or methods that must be accessed via dynamic linking code because their 
   // offset is not yet known or the class's static initializer has not yet been run.
   //
   // We choose a value that will never match a valid jtoc-, instance-, 
   // or virtual method table- offset. -1 is a good value:
   //      the jtoc uses offsets over 0
   //      instance field offsets are always more than 1 byte aligned w.r.t. object pointer
   //      virtual method offsets are always positive w.r.t. TIB pointer
   //
   public static final int NEEDS_DYNAMIC_LINK = -1;
}

