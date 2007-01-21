/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.jikesrvm;

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
  
  static final byte VOID_TYPE            = 0x00;
  static final byte INT_TYPE             = 0x01;
  static final byte ADDRESS_TYPE         = 0x02;
  static final byte LONG_TYPE            = 0x04;
  static final byte FLOAT_TYPE           = 0x08;
  static final byte DOUBLE_TYPE          = 0x10;
  static final byte LONGHALF_TYPE        = 0x20;

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
