/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * A set of constants that define some useful Java types and stack
 * sizes that describe the state of a basic block and its stack
 * contents on entry.
 *
 * @author Chris Hoffmann
 * @version 1.0
 */

public interface VM_BBConstants {

  // first two bits determine the number of Java words making up the
  // entity 
  static final byte LENGTH_MASK      = 0x03;
  
  static final byte SINGLE_WORD_     = 0x01; 
  static final byte DOUBLE_WORD_     = 0x02; 
  static final byte FLOATING_POINT_  = 0x04;
  static final byte ARRAY_           = 0x08;
  static final byte RETURN_          = 0x10;
  static final byte OBJECT_          = 0x20;
  static final byte TWO_REGISTERS_   = 0x40;
  
  static final byte VOID_TYPE            = 0x00;
  static final byte WORD_TYPE            = SINGLE_WORD_;
  static final byte LONG_TYPE            = TWO_REGISTERS_|DOUBLE_WORD_;
  static final byte FLOAT_TYPE           = FLOATING_POINT_|SINGLE_WORD_;
  static final byte DOUBLE_TYPE          = FLOATING_POINT_|DOUBLE_WORD_;
  static final byte OBJECT_TYPE          = OBJECT_|SINGLE_WORD_;
  static final byte ARRAY_TYPE           = OBJECT_TYPE|ARRAY_;
  static final byte RETURN_ADDRESS_TYPE  = OBJECT_TYPE|RETURN_;

  static final short DUMMYBLOCK         = -1;
  static final short STARTBLOCK         =  1;
  static final short EXITBLOCK          =  2;
  static final short EXCEPTIONHANDLER   = -2;

  static final byte TARGET_         = 0x10;
  static final byte CONDITIONAL_    = 0x20;
  static final byte FALLTHRU_       = 0x40;
  static final byte TRYSTART_       = (byte)0x80;

  static final byte NOTBLOCK          = 0x0;
  static final byte INJSR_            = 0x1;
  static final byte JSRENTRY          = 0x2;
  static final byte TRYHANDLERSTART   = 0x4;
  static final byte HASHANDLER_       = 0x8;
  static final byte METHODENTRY       = TARGET_;
  static final byte CONDITIONALTARGET = TARGET_ | CONDITIONAL_;
  static final byte UNCONDITIONALTARGET = TARGET_;
  static final byte FALLTHRUTARGET    = TARGET_ | CONDITIONAL_| FALLTHRU_;

}
