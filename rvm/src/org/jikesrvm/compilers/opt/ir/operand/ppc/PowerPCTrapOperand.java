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
 * Encodes the T0 field for trap operations
 *
 * @see Operand
 */
public final class PowerPCTrapOperand extends Operand {
  /**
   * Value of this operand.
   */
  public int value;
  // see PowerPC BOOK
  public static final int ALWAYS = 31;
  public static final int EQUAL = 4;
  public static final int NOT_EQUAL = 24;
  public static final int LESS = 16;
  public static final int GREATER_EQUAL = 12;
  public static final int GREATER = 8;
  public static final int LESS_EQUAL = 20;
  public static final int HIGHER = 1;
  public static final int LOWER = 2;
  public static final int HIGHER_EQUAL = 5;
  public static final int LOWER_EQUAL = 6;
  public static final int NOT_SAME = 3;
  public static final int SAME = 4;

  private PowerPCTrapOperand(int Code) {
    value = Code;
  }

  public static PowerPCTrapOperand LESS() {
    return new PowerPCTrapOperand(LESS);
  }

  public static PowerPCTrapOperand GREATER() {
    return new PowerPCTrapOperand(GREATER);
  }

  public static PowerPCTrapOperand LOWER() {
    return new PowerPCTrapOperand(LOWER);
  }

  public static PowerPCTrapOperand ALWAYS() {
    return new PowerPCTrapOperand(ALWAYS);
  }

  @Override
  public Operand copy() {
    return new PowerPCTrapOperand(value);
  }

  @Override
  public boolean similar(Operand op) {
    return (op instanceof PowerPCTrapOperand) && (((PowerPCTrapOperand) op).value == value);
  }

  /**
   * flips the direction of the condition
   */
  public PowerPCTrapOperand flipCode() {
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
      case HIGHER:
        value = LOWER_EQUAL;
        break;
      case LOWER:
        value = HIGHER_EQUAL;
        break;
      case HIGHER_EQUAL:
        value = LOWER;
        break;
      case LOWER_EQUAL:
        value = HIGHER;
        break;
      case NOT_SAME:
        value = SAME;
        break;
    }
    return this;
  }

  /**
   * This could be used if you want to flip the order of the operands.
   * You will notice that there are some differences.
   */
  PowerPCTrapOperand flipOperands() {
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
      case HIGHER:
        value = LOWER;
        break;
      case LOWER:
        value = HIGHER;
        break;
      case HIGHER_EQUAL:
        value = LOWER_EQUAL;
        break;
      case LOWER_EQUAL:
        value = HIGHER_EQUAL;
        break;
      case NOT_SAME:
        value = SAME;
        break;
    }
    return this;
  }

  public PowerPCTrapOperand(ConditionOperand c) {
    translate(c);
  }

  /**
   * Translate from ConditionOperand: used by BURS.
   */
  public void translate(ConditionOperand c) {
    switch (c.value) {
      case ConditionOperand.EQUAL:
        value = EQUAL;
        break;
      case ConditionOperand.NOT_EQUAL:
        value = NOT_EQUAL;
        break;
      case ConditionOperand.LESS:
        value = LESS;
        break;
      case ConditionOperand.LESS_EQUAL:
        value = LESS_EQUAL;
        break;
      case ConditionOperand.GREATER:
        value = GREATER;
        break;
      case ConditionOperand.GREATER_EQUAL:
        value = GREATER_EQUAL;
        break;
      case ConditionOperand.HIGHER:
        value = HIGHER;
        break;
      case ConditionOperand.LOWER:
        value = LOWER;
        break;
      case ConditionOperand.HIGHER_EQUAL:
        value = HIGHER_EQUAL;
        break;
      case ConditionOperand.LOWER_EQUAL:
        value = LOWER_EQUAL;
        break;
    }
  }

  /**
   * Returns the string representation of this operand.
   */
  @Override
  public String toString() {
    String result = "ppc trap ";
    switch (value) {
      case EQUAL:
        return result + "==";
      case NOT_EQUAL:
        return result + "!=";
      case LESS:
        return result + "<";
      case LESS_EQUAL:
        return result + "<=";
      case GREATER:
        return result + ">";
      case GREATER_EQUAL:
        return result + ">=";
      case HIGHER:
        return result + ">U";
      case LOWER:
        return result + "<U";
      case HIGHER_EQUAL:
        return result + ">=U";
      case LOWER_EQUAL:
        return result + "<=U";
      case NOT_SAME:
        return result + "U!=";
      case ALWAYS:
        return result + "always";
    }
    return "UNKNOWN";
  }
}



