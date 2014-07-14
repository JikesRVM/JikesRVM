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
package org.jikesrvm.compilers.baseline;

/**
 * A set of constants that define some useful Java types and stack
 * sizes that describe the state of a basic block and its stack
 * contents on entry.
 */
public final class BBConstants {

  // first two bits determine the number of Java words making up the
  // entity
  public static final byte LENGTH_MASK = 0x03;

  public static final byte VOID_TYPE = 0x00;
  public static final byte INT_TYPE = 0x01;
  public static final byte ADDRESS_TYPE = 0x02;
  public static final byte LONG_TYPE = 0x04;
  public static final byte FLOAT_TYPE = 0x08;
  public static final byte DOUBLE_TYPE = 0x10;
  public static final byte LONGHALF_TYPE = 0x20;

  public static final short DUMMYBLOCK = -1;
  public static final short STARTBLOCK = 1;
  public static final short EXITBLOCK = 2;
  public static final short EXCEPTIONHANDLER = -2;

  public static final byte TARGET_ = 0x10;
  public static final byte CONDITIONAL_ = 0x20;
  public static final byte FALLTHRU_ = 0x40;
  public static final byte TRYSTART_ = (byte) 0x80;

  public static final byte NOTBLOCK = 0x0;
  public static final byte INJSR_ = 0x1;
  public static final byte JSRENTRY = 0x2;
  public static final byte TRYHANDLERSTART = 0x4;
  public static final byte HASHANDLER_ = 0x8;
  public static final byte METHODENTRY = TARGET_;
  public static final byte CONDITIONALTARGET = TARGET_ | CONDITIONAL_;
  public static final byte UNCONDITIONALTARGET = TARGET_;
  public static final byte FALLTHRUTARGET = TARGET_ | CONDITIONAL_ | FALLTHRU_;

  private BBConstants() {
    // prevent instantiation
  }

}
