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
package org.jikesrvm.compilers.opt.ir.operand.ppc;

import org.jikesrvm.compilers.opt.ir.operand.ConditionOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;

/**
 * Encodes the BO & BI condition fields for PowerPC
 *
 * @see Operand
 */
public final class PowerPCConditionOperand extends Operand {
  public static final int ALWAYS = (20 << 5);
  public static final int EQUAL = (12 << 5) | 2;
  public static final int NOT_EQUAL = (4 << 5) | 2;
  public static final int LESS = (12 << 5) | 0;
  public static final int GREATER_EQUAL = (4 << 5) | 0;
  public static final int GREATER = (12 << 5) | 1;
  public static final int LESS_EQUAL = (4 << 5) | 1;
  public static final int OVERFLOW = (12 << 5) | 3;
  public static final int NOT_OVERFLOW = (4 << 5) | 3;

  /* interpretation for floating-point values */
  public static final int UNORDERED = (12 << 5) | 3;
  public static final int NOT_UNORDERED = (4 << 5) | 3;

  // --CTR == 0
  public static final int CTRZ = (17 << 5) | 0;
  // --CTR != 0
  public static final int CTRNZ = (16 << 5) | 0;
  // (--CTR == 0) & condition
  public static final int CTRZ_EQUAL = (10 << 5) | 2;
  public static final int CTRZ_NOT_EQUAL = (2 << 5) | 2;
  public static final int CTRZ_LESS = (10 << 5) | 0;
  public static final int CTRZ_GREATER_EQUAL = (2 << 5) | 0;
  public static final int CTRZ_GREATER = (10 << 5) | 1;
  public static final int CTRZ_LESS_EQUAL = (2 << 5) | 1;
  public static final int CTRZ_OVERFLOW = (10 << 5) | 3;
  public static final int CTRZ_NOT_OVERFLOW = (2 << 5) | 3;
  // (--CTR != 0) & condition
  public static final int CTRNZ_EQUAL = (8 << 5) | 2;
  public static final int CTRNZ_NOT_EQUAL = (0 << 5) | 2;
  public static final int CTRNZ_LESS = (8 << 5) | 0;
  public static final int CTRNZ_GREATER_EQUAL = (0 << 5) | 0;
  public static final int CTRNZ_GREATER = (8 << 5) | 1;
  public static final int CTRNZ_LESS_EQUAL = (0 << 5) | 1;
  public static final int CTRNZ_OVERFLOW = (8 << 5) | 3;
  public static final int CTRNZ_NOT_OVERFLOW = (0 << 5) | 3;

  /**
   * Value of this operand.
   */
  public int value;

  // TODO: add the things with the CTR register also.
  public PowerPCConditionOperand(int Code) {
    value = Code;
  }

  public static PowerPCConditionOperand EQUAL() {
    return new PowerPCConditionOperand(EQUAL);
  }

  public static PowerPCConditionOperand NOT_EQUAL() {
    return new PowerPCConditionOperand(NOT_EQUAL);
  }

  public static PowerPCConditionOperand LESS() {
    return new PowerPCConditionOperand(LESS);
  }

  public static PowerPCConditionOperand LESS_EQUAL() {
    return new PowerPCConditionOperand(LESS_EQUAL);
  }

  public static PowerPCConditionOperand GREATER() {
    return new PowerPCConditionOperand(GREATER);
  }

  public static PowerPCConditionOperand GREATER_EQUAL() {
    return new PowerPCConditionOperand(GREATER_EQUAL);
  }

  public static PowerPCConditionOperand UNORDERED() {
    return new PowerPCConditionOperand(UNORDERED);
  }

  public static PowerPCConditionOperand get(ConditionOperand cond) {
    return new PowerPCConditionOperand(cond);
  }

  @Override
  public Operand copy() {
    return new PowerPCConditionOperand(value);
  }

  @Override
  public boolean similar(Operand op) {
    return (op instanceof PowerPCConditionOperand) && (((PowerPCConditionOperand) op).value == value);
  }

  /**
   * Flips the direction of the condition.
   */
  public PowerPCConditionOperand flipCode() {
    switch (value) {
      case EQUAL:
        value = NOT_EQUAL;
        break;
      case NOT_EQUAL:
        value = EQUAL;
        break;
      case LESS:
        value = GREATER_EQUAL;
        break;
      case LESS_EQUAL:
        value = GREATER;
        break;
      case GREATER:
        value = LESS_EQUAL;
        break;
      case GREATER_EQUAL:
        value = LESS;
        break;
      case OVERFLOW:
        value = NOT_OVERFLOW;
        break;
      case NOT_OVERFLOW:
        value = OVERFLOW;
        break;
      case CTRZ:
        value = CTRNZ;
        break;
      case CTRNZ:
        value = CTRZ;
        break;
      default:
        throw new org.jikesrvm.compilers.opt.OptimizingCompilerException("Unhandled case in flipCode");
    }
    return this;
  }

  /**
   * This could be used if you want to flip the order of the operands
   * You will notice that there are some differences.
   */
  public PowerPCConditionOperand flipOperands() {
    switch (value) {
      case EQUAL:
        value = NOT_EQUAL;
        break;
      case NOT_EQUAL:
        value = EQUAL;
        break;
      case LESS:
        value = GREATER;
        break;
      case LESS_EQUAL:
        value = GREATER_EQUAL;
        break;
      case GREATER:
        value = LESS;
        break;
      case GREATER_EQUAL:
        value = LESS_EQUAL;
        break;
      case OVERFLOW:
        value = NOT_OVERFLOW;
        break;
      case NOT_OVERFLOW:
        value = OVERFLOW;
        break;
        // TODO remaining
    }
    return this;
  }

  public PowerPCConditionOperand(ConditionOperand c) {
    translate(c);
  }

  /**
   * Translate from ConditionOperand: used by BURS.
   */
  public void translate(ConditionOperand c) {
    switch (c.value) {
      case ConditionOperand.EQUAL:
      case ConditionOperand.CMPL_EQUAL:
        value = EQUAL;
        break;
      case ConditionOperand.NOT_EQUAL:
      case ConditionOperand.CMPL_NOT_EQUAL: // Extra unordered test required
        value = NOT_EQUAL;
        break;
      case ConditionOperand.LESS:
      case ConditionOperand.LOWER:
      case ConditionOperand.CMPG_LESS:
      case ConditionOperand.CMPL_LESS: // Extra unordered test required
        value = LESS;
        break;
      case ConditionOperand.LESS_EQUAL:
      case ConditionOperand.LOWER_EQUAL:
      case ConditionOperand.CMPG_LESS_EQUAL:
      case ConditionOperand.CMPL_LESS_EQUAL: // Extra unordered test required
        value = LESS_EQUAL;
        break;
      case ConditionOperand.GREATER:
      case ConditionOperand.HIGHER:
      case ConditionOperand.CMPL_GREATER:
      case ConditionOperand.CMPG_GREATER: // Extra unordered test required
        value = GREATER;
        break;
      case ConditionOperand.GREATER_EQUAL:
      case ConditionOperand.HIGHER_EQUAL:
      case ConditionOperand.CMPL_GREATER_EQUAL:
      case ConditionOperand.CMPG_GREATER_EQUAL: // Extra unordered test required
        value = GREATER_EQUAL;
        break;
      default:
        org.jikesrvm.compilers.opt.OptimizingCompilerException.UNREACHABLE();
    }
  }

  /**
   * Returns the string representation of this operand.
   */
  @Override
  public String toString() {
    String result = "ppc ";
    if ((value & 0x1C0) == 0) {
      result = result + "--ctr!=0 && ";
    }
    if ((value & 0x1C0) == 0x40) {
      result = result + "--ctr==0 && ";
    }
    String temp = null;
    if ((value & 0x300) == 0x100) {             // true
      switch (value & 0x3) {
        case 0:
          temp = "<";
          break;
        case 1:
          temp = ">";
          break;
        case 2:
          temp = "==";
          break;
        case 3:
          temp = "overflow";
          break;
      }
    }
    if ((value & 0x300) == 0x000) {             // false
      switch (value & 0x3) {
        case 0:
          temp = ">=";
          break;
        case 1:
          temp = "<=";
          break;
        case 2:
          temp = "!=";
          break;
        case 3:
          temp = "not_overflow";
          break;
      }
    }
    return result + temp;
  }
}



