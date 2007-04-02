/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002
 */

package org.jikesrvm.osr;

import org.jikesrvm.compilers.opt.ir.*;
import org.jikesrvm.VM;
import org.vmmagic.unboxed.*;

/**
 * An OSR_LocalRegPair keeps the type information and localtion of 
 * a local variable/stack slot from byte code to machine code.
 *
 * @author Feng Qian
 */
public class OSR_LocalRegPair implements OSR_Constants {

  // is it a local or stack? 
  public int kind; 

  // what's the number of local of stack
  public int num;

  // what's the type code? ('I', 'J', 'D', etc)
  public byte typeCode;

  /* what's the register oprand, from which we can get the symbolic register.
   * The operand could be symbolic register, or constants, we need to take
   * it out later.
   */
  public OPT_Operand operand;
 
  /* rest part only available after updated by OPT_LinearScan.updateOSRMaps. */
  
  /* A reg value could be an integer constant (ICONST),
   *                      a physical register (PHYREG), or
   *                      a spill on the stack (SPILL).
   * The  valueType is one of them, combined with the typeCode, one shuld be
   * able to recover the value of a variable.
   */
  public int valueType;    

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
   * ( splitted in BURS ).
   * After register allocation, we should not use <code>operand</code>
   * anymore. The physical register number, spilled location, or
   * constant value is represented by (valueType, value)
   */ 
  public OSR_LocalRegPair(int kind, int num, byte type, OPT_Operand op) {
    this.kind = kind;
    this.num = num;
    this.typeCode = type;
    this.operand = op;
  }

  public OSR_LocalRegPair copy() {
    return new OSR_LocalRegPair(kind, num, typeCode, operand);
  }

  /* converts tuple to string as
   *  ( L/S num, type, valueType, value, oprand )
   */      
  public String toString() {
    StringBuilder buf = new StringBuilder("(");

    buf.append((char)kind);
    buf.append(num).append(" , ");

    char tcode = (char)typeCode;

    buf.append(tcode).append(" , ");
    buf.append(valueType).append(" , ");
    buf.append(value).append(" , ");
    buf.append(operand).append(")");

    // for long type, append another half
    if (VM.BuildFor32Addr && (tcode == LongTypeCode)) {
      buf.append("(").append(_otherHalf.valueType).append(" , ");
      buf.append(_otherHalf.value).append(")");
    }
    return buf.toString();
  }
}
  
