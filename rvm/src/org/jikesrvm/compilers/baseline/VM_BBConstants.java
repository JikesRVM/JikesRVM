/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.baseline;

/**
 * A set of constants that define some useful Java types and stack
 * sizes that describe the state of a basic block and its stack
 * contents on entry.
 */
public interface VM_BBConstants {

  // first two bits determine the number of Java words making up the
  // entity 
  byte LENGTH_MASK = 0x03;

  byte VOID_TYPE = 0x00;
  byte INT_TYPE = 0x01;
  byte ADDRESS_TYPE = 0x02;
  byte LONG_TYPE = 0x04;
  byte FLOAT_TYPE = 0x08;
  byte DOUBLE_TYPE = 0x10;
  byte LONGHALF_TYPE = 0x20;

  short DUMMYBLOCK = -1;
  short STARTBLOCK = 1;
  short EXITBLOCK = 2;
  short EXCEPTIONHANDLER = -2;

  byte TARGET_ = 0x10;
  byte CONDITIONAL_ = 0x20;
  byte FALLTHRU_ = 0x40;
  byte TRYSTART_ = (byte) 0x80;

  byte NOTBLOCK = 0x0;
  byte INJSR_ = 0x1;
  byte JSRENTRY = 0x2;
  byte TRYHANDLERSTART = 0x4;
  byte HASHANDLER_ = 0x8;
  byte METHODENTRY = TARGET_;
  byte CONDITIONALTARGET = TARGET_ | CONDITIONAL_;
  byte UNCONDITIONALTARGET = TARGET_;
  byte FALLTHRUTARGET = TARGET_ | CONDITIONAL_ | FALLTHRU_;

}
