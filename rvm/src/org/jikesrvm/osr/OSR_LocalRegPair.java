/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.osr;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.ir.OPT_Operand;
import org.vmmagic.unboxed.Word;

/**
 * An OSR_LocalRegPair keeps the type information and location of
 * a local variable/stack slot from byte code to machine code.
 */
public class OSR_LocalRegPair implements OSR_Constants {

  /** is it a local or stack? */
  public final boolean kind;

  /** what's the number of local of stack */
  public int num;

  /** what's the type code? ('I', 'J', 'D', etc) */
  public final byte typeCode;

  /**
   * What's the register operand, from which we can get the symbolic register.
   * The operand could be symbolic register, or constants, we need to take
   * it out later.
   */
  public final OPT_Operand operand;

  /* rest part only available after updated by OPT_LinearScan.updateOSRMaps. */

  /* A reg value could be an integer constant (ICONST),
  *                      a physical register (PHYREG), or
  *                      a spill on the stack (SPILL).
  * The  valueType is one of them, combined with the typeCode, one should be
  * able to recover the value of a variable.
  */
  public byte valueType;

  /* The meaning of value field depends on valueType
  * for ICONST, ACONST and LCONST, it is the value of the constant,
  * for PHYREG, it is the register number,
  * for SPILL, it is the spill location.
  */
  public Word value;

  /* A LONG variable takes two symbolic registers, we need to know another
   * half part.
   */
  public OSR_LocalRegPair _otherHalf;

  /* The LiveAnalysis phase builds the linked list of tuples, and
   * the long type variables will get another half register
   * ( split in BURS ).
   * After register allocation, we should not use <code>operand</code>
   * anymore. The physical register number, spilled location, or
   * constant value is represented by (valueType, value)
   */
  public OSR_LocalRegPair(boolean kind, int num, byte type, OPT_Operand op) {
    this.kind = kind;
    this.num = num;
    this.typeCode = type;
    this.operand = op;
  }

  public OSR_LocalRegPair copy() {
    return new OSR_LocalRegPair(kind, num, typeCode, operand);
  }

  /**
   * converts tuple to string as
   * ( L/S num, type, valueType, value, operand )
   */
  public String toString() {
    StringBuilder buf = new StringBuilder("(");

    buf.append(kind == LOCAL ? 'L' : 'S');
    buf.append(num).append(" , ");

    char tcode = (char) typeCode;

    buf.append(tcode).append(" , ");
    buf.append(valueType).append(" , ");
    buf.append("0x").append(Long.toHexString(value.toLong())).append(" , ");
    buf.append(operand).append(")");

    // for long type, append another half
    if (VM.BuildFor32Addr && (tcode == LongTypeCode)) {
      buf.append("(").append(_otherHalf.valueType).append(" , ");
      buf.append("0x").append(Integer.toHexString(_otherHalf.value.toInt())).append(")");
    }
    return buf.toString();
  }
}

