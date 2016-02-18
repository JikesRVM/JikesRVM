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
package org.jikesrvm.compilers.opt.ir.operand;

import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.vmmagic.unboxed.Address;
import org.jikesrvm.runtime.Magic;

/**
 * Encodes the condition codes for branches.
 *
 * @see Operand
 */
public final class ConditionOperand extends Operand {

  /* signed integer arithmetic */
  /** Integer equal == */
  public static final int EQUAL               = 0;
  /** Integer not equal != */
  public static final int NOT_EQUAL           = 1;
  /** Signed integer &lt; */
  public static final int LESS                = 2;
  /** Signed integer &gt;= */
  public static final int GREATER_EQUAL       = 3;
  /** Signed integer &gt; */
  public static final int GREATER             = 4;
  /** Signed integer &lt;= */
  public static final int LESS_EQUAL          = 5;

  /* unsigned integer arithmetic */
  /** Unsigned integer &gt; - &gt;U */
  public static final int HIGHER             = 6;
  /** Unsigned integer &lt; - &lt;U */
  public static final int LOWER              = 7;
  /** Unsigned integer &gt;= - &gt;=U */
  public static final int HIGHER_EQUAL       = 8;
  /** Unsigned integer &lt;= - &lt;=U */
  public static final int LOWER_EQUAL        = 9;

  /* floating-point arithmethic */
  // branches that fall through when unordered
  /** Branch if == (equivalent to CMPG_EQUAL)  - ==F */
  public static final int CMPL_EQUAL         = 10;
  /** Branch if &gt; - &gt;F */
  public static final int CMPL_GREATER       = 11;
  /** Branch if &lt; - &lt;F */
  public static final int CMPG_LESS          = 12;
  /** Branch if &gt;= - &gt;=F */
  public static final int CMPL_GREATER_EQUAL = 13;
  /** Branch if &lt;= - &lt;=F */
  public static final int CMPG_LESS_EQUAL    = 14;

  // branches that are taken when unordered
  /** Branch if != (equivalent to CMPG_NOT_EQUAL) - !=FU */
  public static final int CMPL_NOT_EQUAL     = 15;
  /** Branch if &lt; or unordered - &lt;FU */
  public static final int CMPL_LESS          = 16;
  /** Brach if &gt;= or unordered - &gt;=FU */
  public static final int CMPG_GREATER_EQUAL = 17;
  /** Branch if &gt; or unordered - &gt;FU */
  public static final int CMPG_GREATER       = 18;
  /** Branch if &lt;= or unordered - &lt;=FU */
  public static final int CMPL_LESS_EQUAL    = 19;

  /* integer arithmetic flag operations */
  /** Would a+b produce a carry? */
  public static final int CARRY_FROM_ADD        = 20;
  /** Would a+b not produce a carry? */
  public static final int NO_CARRY_FROM_ADD     = 21;

  /** Would a+b cause an overflow? */
  public static final int OVERFLOW_FROM_ADD     = 22;
  /** Would a+b not cause an overflow? */
  public static final int NO_OVERFLOW_FROM_ADD  = 23;

  /** Would a-b produce a borrow? */
  public static final int BORROW_FROM_SUB       = 24;
  /** Would a-b not produce a borrow? */
  public static final int NO_BORROW_FROM_SUB    = 25;
  /** Would b-a produce a borrow? */
  public static final int BORROW_FROM_RSUB      = 26;
  /** Would b-a not produce a borrow? */
  public static final int NO_BORROW_FROM_RSUB   = 27;

  /** Would a-b cause an overflow? */
  public static final int OVERFLOW_FROM_SUB     = 28;
  /** Would a-b not cause an overflow? */
  public static final int NO_OVERFLOW_FROM_SUB  = 29;
  /** Would b-a cause an overflow? */
  public static final int OVERFLOW_FROM_RSUB    = 30;
  /** Would b-a not cause an overflow? */
  public static final int NO_OVERFLOW_FROM_RSUB = 31;

  /** Would is bit b set in a? */
  public static final int BIT_TEST              = 32;
  /** Would is bit b not set in a? */
  public static final int NO_BIT_TEST           = 33;
  /** Would is bit a set in b? */
  public static final int RBIT_TEST             = 34;
  /** Would is bit a not set in b? */
  public static final int NO_RBIT_TEST          = 35;

  /** Would a*b cause an overflow? */
  public static final int OVERFLOW_FROM_MUL     = 36;
  /** Would a*b not cause an overflow? */
  public static final int NO_OVERFLOW_FROM_MUL  = 37;

  /* Results from evaluations */
  /** Evaluation result is false */
  public static final int FALSE = 0;
  /** Evaluation result is true */
  public static final int TRUE = 1;
  /** Evaluation result is unknown */
  public static final int UNKNOWN = 2;

  /**
   * Value of this operand.
   */
  public int value;

  /**
   * @param code the condition code
   */
  private ConditionOperand(int code) {
    value = code;
  }

  /**
   * Create the condition code operand for EQUAL
   *
   * @return a new condition code operand
   */
  public static ConditionOperand EQUAL() {
    return new ConditionOperand(EQUAL);
  }

  /**
   * Create the condition code operand for NOT_EQUAL
   *
   * @return a newly created condition code operand
   */
  public static ConditionOperand NOT_EQUAL() {
    return new ConditionOperand(NOT_EQUAL);
  }

  /**
   * Create the condition code operand for LESS
   *
   * @return a newly created condition code operand
   */
  public static ConditionOperand LESS() {
    return new ConditionOperand(LESS);
  }

  /**
   * Create the condition code operand for GREATER_EQUAL
   *
   * @return a newly created condition code operand
   */
  public static ConditionOperand GREATER_EQUAL() {
    return new ConditionOperand(GREATER_EQUAL);
  }

  /**
   * Create the condition code operand for GREATER
   *
   * @return a newly created condition code operand
   */
  public static ConditionOperand GREATER() {
    return new ConditionOperand(GREATER);
  }

  /**
   * Create the condition code operand for LESS_EQUAL
   *
   * @return a newly created condition code operand
   */
  public static ConditionOperand LESS_EQUAL() {
    return new ConditionOperand(LESS_EQUAL);
  }

  /**
   * Create the condition code operand for HIGHER
   *
   * @return a newly created condition code operand
   */
  public static ConditionOperand HIGHER() {
    return new ConditionOperand(HIGHER);
  }

  /**
   * Create the condition code operand for LOWER
   *
   * @return a newly created condition code operand
   */
  public static ConditionOperand LOWER() {
    return new ConditionOperand(LOWER);
  }

  /**
   * Create the condition code operand for HIGHER_EQUAL
   *
   * @return a newly created condition code operand
   */
  public static ConditionOperand HIGHER_EQUAL() {
    return new ConditionOperand(HIGHER_EQUAL);
  }

  /**
   * Create the condition code operand for LOWER_EQUAL
   *
   * @return a newly created condition code operand
   */
  public static ConditionOperand LOWER_EQUAL() {
    return new ConditionOperand(LOWER_EQUAL);
  }

  /**
   * Create the condition code operand for CMPL_EQUAL
   *
   * @return a newly created condition code operand
   */
  public static ConditionOperand CMPL_EQUAL() {
    return new ConditionOperand(CMPL_EQUAL);
  }

  /**
   * Create the condition code operand for CMPL_NOT_EQUAL
   *
   * @return a newly created condition code operand
   */
  public static ConditionOperand CMPL_NOT_EQUAL() {
    return new ConditionOperand(CMPL_NOT_EQUAL);
  }

  /**
   * Create the condition code operand for CMPL_GREATER
   *
   * @return a newly created condition code operand
   */
  public static ConditionOperand CMPL_GREATER() {
    return new ConditionOperand(CMPL_GREATER);
  }

  /**
   * Create the condition code operand for CMPL_GREATER_EQUAL
   *
   * @return a newly created condition code operand
   */
  public static ConditionOperand CMPL_GREATER_EQUAL() {
    return new ConditionOperand(CMPL_GREATER_EQUAL);
  }

  /**
   * Create the condition code operand for CMPG_LESS
   *
   * @return a newly created condition code operand
   */
  public static ConditionOperand CMPG_LESS() {
    return new ConditionOperand(CMPG_LESS);
 }

  /**
   * Create the condition code operand for CARRY_FROM_ADD
   *
   * @return a newly created condition code operand
   */
  public static ConditionOperand CARRY_FROM_ADD() {
    return new ConditionOperand(CARRY_FROM_ADD);
  }

  /**
   * Create the condition code operand for OVERFLOW_FROM_ADD
   *
   * @return a newly created condition code operand
   */
  public static ConditionOperand OVERFLOW_FROM_ADD() {
    return new ConditionOperand(OVERFLOW_FROM_ADD);
  }

  /**
   * Create the condition code operand for BORROW_FROM_SUB
   *
   * @return a newly created condition code operand
   */
  public static ConditionOperand BORROW_FROM_SUB() {
    return new ConditionOperand(BORROW_FROM_SUB);
  }

  /**
   * Create the condition code operand for OVERFLOW_FROM_SUB
   *
   * @return a newly created condition code operand
   */
  public static ConditionOperand OVERFLOW_FROM_SUB() {
    return new ConditionOperand(OVERFLOW_FROM_SUB);
  }

  /**
   * Create the condition code operand for BIT_TEST
   *
   * @return a newly created condition code operand
   */
  public static ConditionOperand BIT_TEST() {
    return new ConditionOperand(BIT_TEST);
  }

  /**
   * Create the condition code operand for OVERFLOW_FROM_ADD
   *
   * @return a newly created condition code operand
   */
  public static ConditionOperand OVERFLOW_FROM_MUL() {
    return new ConditionOperand(OVERFLOW_FROM_MUL);
  }

  /**
   * Is x higher (unsigned &gt;) than y?
   * @param x first value for comparison
   * @param y second value for comparison
   * @return {@code true} if x is higher (unsigned &gt;)
   *  than y, {@code false} otherwise
   */
  private static boolean higher(int x, int y) {
    return (x & 0xFFFFFFFFL) > ((long)y & 0xFFFFFFFF);
  }

  /**
   * Is x lower (unsigned &lt;) than y?
   * @param x first value for comparison
   * @param y second value for comparison
   * @return {@code true} if x is lower (unsigned &lt;)
   *  than y, {@code false} otherwise
   */
  private static boolean lower(int x, int y) {
    return (x & 0xFFFFFFFFL) < ((long)y & 0xFFFFFFFF);
  }

  /**
   * Is x higher equal (unsigned &gt;=) than y?
   * @param x first value for comparison
   * @param y second value for comparison
   * @return {@code true} if x is higher equal (unsigned &gt;=)
   *  than y, {@code false} otherwise
   */
  private static boolean higher_equal(int x, int y) {
    return (x & 0xFFFFFFFFL) >= ((long)y & 0xFFFFFFFF);
  }

  /**
   * Is x lower equal (unsigned &lt;=) than y?
   * @param x first value for comparison
   * @param y second value for comparison
   * @return {@code true} if x is lower equal (unsigned &lt;=)
   *  than y, {@code false} otherwise
   */
  private static boolean lower_equal(int x, int y) {
    return (x & 0xFFFFFFFFL) <= ((long)y & 0xFFFFFFFF);
  }

  /**
   * Would {@code x + y} produce a carry?
   * @param x first summand
   * @param y second summand
   * @return {@code true} if the addition would produce
   *   a carry, {@code false} otherwise
   */
  private static boolean carry_from_add(int x, int y) {
    int sum = x + y;
    return lower(sum, x);
  }

  /**
   * Would {@code x - y} produce a borrow?
   * @param x minuend
   * @param y subtrahend
   * @return {@code true} if the subtraction would produce
   *   a borrow, {@code false} otherwise
   */
  private static boolean borrow_from_sub(int x, int y) {
    return lower(x, y);
  }

  /**
   * Would {@code x + y} overflow a register?
   * @param x first summand
   * @param y second summand
   * @return {@code true} if the addition would overflow,
   *   {@code false} otherwise
   */
  private static boolean overflow_from_add(int x, int y) {
    if (y >= 0)
      return x > (Integer.MAX_VALUE - y);
    else
      return x < (Integer.MIN_VALUE - y);
  }

  /**
   * Would {@code x - y} overflow a register?
   * @param x minuend
   * @param y subtrahend
   * @return {@code true} if the subtraction would overflow,
   *   {@code false} otherwise
   */
  private static boolean overflow_from_sub(int x, int y) {
    if (y >= 0)
      return x < (Integer.MIN_VALUE + y);
    else
      return x > (Integer.MAX_VALUE + y);
  }

  /**
   * Would {@code x * y} overflow a register?
   * @param x first factor
   * @param y second factor
   * @return {@code true} if the multiplication would overflow,
   *   {@code false} otherwise
   */
  private static boolean overflow_from_mul(int x, int y) {
    int z = x * y;
    long z2 = ((long)x) * ((long)y);
    return z != z2;
  }

  /**
   * Is bit y of x set?
   *
   * @param x the number to check
   * @param y the bit position (0 is least significant bit)
   * @return whether bit position y of x is set (LSB is 0)
   */
  private static boolean bit_test(int x, int y) {
    return (x & (1 << y)) != 0;
  }

  /**
   * Is the condition code EQUAL?
   *
   * @return <code>true</code> if it is or <code>false</code> if it is not
   */
  public boolean isEQUAL() {
    return value == EQUAL;
  }

  /**
   * Is the condition code NOT_EQUAL?
   *
   * @return <code>true</code> if it is or <code>false</code> if it is not
   */
  public boolean isNOT_EQUAL() {
    return value == NOT_EQUAL;
  }

  /**
   * Is the condition code LESS EQUAL?
   *
   * @return <code>true</code> if it is or <code>false</code> if it is not
   */
  public boolean isLESS_EQUAL() {
    return value == LESS_EQUAL;
  }

  /**
   * Is the condition code GREATER_EQUAL?
   *
   * @return <code>true</code> if it is or <code>false</code> if it is not
   */
  public boolean isGREATER_EQUAL() {
    return value == GREATER_EQUAL;
  }

  /**
   * Is the condition code GREATER?
   *
   * @return <code>true</code> if it is or <code>false</code> if it is not
   */
  public boolean isGREATER() {
    return value == GREATER;
  }

  /**
   * Is the condition code LESS?
   *
   * @return <code>true</code> if it is or <code>false</code> if it is not
   */
  public boolean isLESS() {
    return value == LESS;
  }

  /**
   * Is the condition code HIGHER?
   *
   * @return <code>true</code> if it is or <code>false</code> if it is not
   */
  public boolean isHIGHER() {
    return value == HIGHER;
  }

  /**
   * Is the condition code LOWER?
   *
   * @return <code>true</code> if it is or <code>false</code> if it is not
   */
  public boolean isLOWER() {
    return value == LOWER;
  }

  /**
   * Is the condition code HIGHER_EQUAL?
   *
   * @return <code>true</code> if it is or <code>false</code> if it is not
   */
  public boolean isHIGHER_EQUAL() {
    return value == HIGHER_EQUAL;
  }

  /**
   * Is the condition code LOWER_EQUAL?
   *
   * @return <code>true</code> if it is or <code>false</code> if it is not
   */
  public boolean isLOWER_EQUAL() {
    return value == LOWER_EQUAL;
  }

  /**
   * Is the condition code an unsigned comparison?
   * @return <code>true</code> if it is or <code>false</code> if it is not
   */
  public boolean isUNSIGNED() {
    switch (value) {
      case HIGHER:
      case LOWER:
      case HIGHER_EQUAL:
      case LOWER_EQUAL:
        return true;
      default:
        return false;
    }
  }

  /**
   * Is the condition code a flag operation?
   * @return <code>true</code> if it is or <code>false</code> if it is not
   */
  public boolean isFLAG_OPERATION() {
    switch (value) {
      case CARRY_FROM_ADD:
      case NO_CARRY_FROM_ADD:
      case OVERFLOW_FROM_ADD:
      case NO_OVERFLOW_FROM_ADD:
      case BORROW_FROM_SUB:
      case NO_BORROW_FROM_SUB:
      case OVERFLOW_FROM_SUB:
      case NO_OVERFLOW_FROM_SUB:
      case BORROW_FROM_RSUB:
      case NO_BORROW_FROM_RSUB:
      case OVERFLOW_FROM_RSUB:
      case NO_OVERFLOW_FROM_RSUB:
      case BIT_TEST:
      case NO_BIT_TEST:
      case RBIT_TEST:
      case NO_RBIT_TEST:
      case OVERFLOW_FROM_MUL:
      case NO_OVERFLOW_FROM_MUL:
        return true;
      default:
        return false;
    }
  }

  /**
   * Is the condition code a flag operation following an add?
   * @return <code>true</code> if it is or <code>false</code> if it is not
   */
  public boolean isFLAG_OPERATION_FROM_ADD() {
    switch (value) {
      case CARRY_FROM_ADD:
      case NO_CARRY_FROM_ADD:
      case OVERFLOW_FROM_ADD:
      case NO_OVERFLOW_FROM_ADD:
        return true;
      default:
        return false;
    }
  }

  /**
   * Is the condition code a flag operation following a subtract?
   * @return <code>true</code> if it is or <code>false</code> if it is not
   */
  public boolean isFLAG_OPERATION_FROM_SUB() {
    switch (value) {
      case BORROW_FROM_SUB:
      case NO_BORROW_FROM_SUB:
      case OVERFLOW_FROM_SUB:
      case NO_OVERFLOW_FROM_SUB:
        return true;
      default:
        return false;
    }
  }

  /**
   * Is the condition code a flag operation following a reversed subtract?
   * @return <code>true</code> if it is or <code>false</code> if it is not
   */
  public boolean isFLAG_OPERATION_FROM_RSUB() {
    switch (value) {
      case BORROW_FROM_RSUB:
      case NO_BORROW_FROM_RSUB:
      case OVERFLOW_FROM_RSUB:
      case NO_OVERFLOW_FROM_RSUB:
        return true;
      default:
        return false;
    }
  }

  /**
   * Is the condition code a flag operation following a bit test?
   * @return <code>true</code> if it is or <code>false</code> if it is not
   */
  public boolean isBIT_TEST() {
    switch (value) {
      case BIT_TEST:
      case NO_BIT_TEST:
        return true;
      default:
        return false;
    }
  }

  /**
   * Is the condition code a flag operation following a reversed bit test?
   * @return <code>true</code> if it is or <code>false</code> if it is not
   */
  public boolean isRBIT_TEST() {
    switch (value) {
      case RBIT_TEST:
      case NO_RBIT_TEST:
        return true;
      default:
        return false;
    }
  }

  /**
   * Is the condition code a floating point compare?
   * @return <code>true</code> if it is or <code>false</code> if it is not
   */
  public boolean isFLOATINGPOINT() {
    switch (value) {
      case CMPL_EQUAL:
      case CMPL_GREATER:
      case CMPG_LESS:
      case CMPL_GREATER_EQUAL:
      case CMPG_LESS_EQUAL:
      case CMPL_NOT_EQUAL:
      case CMPL_LESS:
      case CMPG_GREATER_EQUAL:
      case CMPG_GREATER:
      case CMPL_LESS_EQUAL:
        return true;
      default:
        return false;
    }
  }

  /**
   * Will this floating point compare branch if the results are
   * unordered?
   * @return <code>true</code> if it is or <code>false</code> if it is not
   */
  public boolean branchIfUnordered() {
    switch (value) {
      case CMPL_EQUAL:
      case CMPL_GREATER:
      case CMPG_LESS:
      case CMPL_GREATER_EQUAL:
      case CMPG_LESS_EQUAL:
        return false;
      case CMPL_NOT_EQUAL:
      case CMPL_LESS:
      case CMPG_GREATER_EQUAL:
      case CMPG_GREATER:
      case CMPL_LESS_EQUAL:
        return true;
      default:
        throw new OptimizingCompilerException("invalid condition " + this);
    }
  }

  /**
   * Convert this integer compare to a floating point cmpl
   * compare. Used during BC2IR.
   */
  public void translateCMPL() {
    switch (value) {
      case EQUAL:
        value = CMPL_EQUAL;
        break;
      case NOT_EQUAL:
        value = CMPL_NOT_EQUAL;
        break;
      case LESS:
        value = CMPL_LESS;
        break;
      case GREATER_EQUAL:
        value = CMPL_GREATER_EQUAL;
        break;
      case GREATER:
        value = CMPL_GREATER;
        break;
      case LESS_EQUAL:
        value = CMPL_LESS_EQUAL;
        break;
      default:
        throw new OptimizingCompilerException("invalid condition " + this);
    }
  }

  /**
   * Convert this integer compare to a floating point cmpg
   * compare. Used during BC2IR.
   */
  public void translateCMPG() {
    switch (value) {
      case EQUAL:
        value = CMPL_EQUAL;
        break;
      case NOT_EQUAL:
        value = CMPL_NOT_EQUAL;
        break;
      case LESS:
        value = CMPG_LESS;
        break;
      case GREATER_EQUAL:
        value = CMPG_GREATER_EQUAL;
        break;
      case GREATER:
        value = CMPG_GREATER;
        break;
      case LESS_EQUAL:
        value = CMPG_LESS_EQUAL;
        break;
      default:
        throw new OptimizingCompilerException("invalid condition " + this);
    }
  }

  /**
   * Convert this floating point compare to the equivalent unsigned
   * integer compare. Used during IA-32 BURS.<p>
   *
   * NB this doesn't respect ordered/unordered operation, so it
   * should only be used when it's safe to.
   *
   * @return this
   */
  public ConditionOperand translateUNSIGNED() {
    switch (value) {
      case CMPL_EQUAL:         value = EQUAL;         break;
      case CMPL_GREATER:       value = HIGHER;       break;
      case CMPG_LESS:          value = LOWER;        break;
      case CMPL_GREATER_EQUAL: value = HIGHER_EQUAL; break;
      case CMPG_LESS_EQUAL:    value = LOWER_EQUAL;  break;
      case CMPL_NOT_EQUAL:     value = NOT_EQUAL;     break;
      case CMPL_LESS:          value = LOWER;        break;
      case CMPG_GREATER_EQUAL: value = HIGHER_EQUAL; break;
      case CMPG_GREATER:       value = HIGHER;       break;
      case CMPL_LESS_EQUAL:    value = LOWER_EQUAL;  break;
      default:
        throw new OptimizingCompilerException("invalid condition " + this);
    }
    return this;
  }

  @Override
  public Operand copy() {
    return new ConditionOperand(value);
  }

  @Override
  public boolean similar(Operand op) {
    return (op instanceof ConditionOperand) && (((ConditionOperand) op).value == value);
  }

  /**
   * Given two operands, evaluate the condition on them.
   *
   * @param v1 first operand to condition
   * @param v2 second operand to condition
   * @return <code>TRUE</code> if (v1 cond v2) or
   *         <code>FALSE</code> if !(v1 cond v2) or
   *         <code>UNKNOWN</code>
   */
  public int evaluate(Operand v1, Operand v2) {
    if (v1.isAddressConstant()) {
      if (v2.isAddressConstant()) {
        return evaluate(v1.asAddressConstant().value, v2.asAddressConstant().value);
      } else if (v2.isNullConstant()) {
        return evaluate(v1.asAddressConstant().value, Address.zero());
      } else if (v2.isIntConstant()) {
        return evaluate(v1.asAddressConstant().value, Address.fromIntSignExtend(v2.asIntConstant().value));
      } else if (v2.isObjectConstant() && !v2.isMovableObjectConstant()) {
        return evaluate(v1.asAddressConstant().value,
            Magic.objectAsAddress(v2.asObjectConstant().value));
      }
    } else if (v1.isIntConstant()) {
      if (v2.isIntConstant()) {
        return evaluate(v1.asIntConstant().value, v2.asIntConstant().value);
      } else if (v2.isNullConstant()) {
        return evaluate(v1.asIntConstant().value, 0);
      } else if (v2.isAddressConstant()) {
        return evaluate(Address.fromIntSignExtend(v1.asIntConstant().value), v2.asAddressConstant().value);
      } else if (v2.isObjectConstant() && !v2.isMovableObjectConstant()) {
        return evaluate(Address.fromIntSignExtend(v1.asIntConstant().value),
            Magic.objectAsAddress(v2.asObjectConstant().value));
      }
    } else if (v1.isLongConstant()) {
      if (v2.isLongConstant()) {
        return evaluate(v1.asLongConstant().value, v2.asLongConstant().value);
      }
    } else if (v1.isFloatConstant()) {
      if (v2.isFloatConstant()) {
        return evaluate(v1.asFloatConstant().value, v2.asFloatConstant().value);
      }
    } else if (v1.isDoubleConstant()) {
      if (v2.isDoubleConstant()) {
        return evaluate(v1.asDoubleConstant().value, v2.asDoubleConstant().value);
      }
    } else if (v1.isObjectConstant()) {
      if (v2.isObjectConstant()) {
        if (!v1.isMovableObjectConstant() && !v2.isMovableObjectConstant()) {
          return evaluate(Magic.objectAsAddress(v1.asObjectConstant().value),
              Magic.objectAsAddress(v2.asObjectConstant().value));
        } else if (isEQUAL()) {
          return (v1.asObjectConstant().value == v2.asObjectConstant().value) ? TRUE : FALSE;
        } else if (isNOT_EQUAL()) {
          return (v1.asObjectConstant().value != v2.asObjectConstant().value) ? TRUE : FALSE;
        }
      }
      if (v2.isNullConstant() || (v2.isIntConstant() && v2.asIntConstant().value == 0)) {
        return evaluate(1,0);
      }
      if (!v1.isMovableObjectConstant()) {
        if (v2.isIntConstant()) {
          return evaluate(Magic.objectAsAddress(v1.asObjectConstant().value),
              Address.fromIntSignExtend(v2.asIntConstant().value));
        } else if (v2.isAddressConstant()) {
          return evaluate(Magic.objectAsAddress(v1.asObjectConstant().value),
              v2.asAddressConstant().value);
        } else if (v2.isNullConstant()) {
          return evaluate(Magic.objectAsAddress(v1.asObjectConstant().value),
              Address.zero());
        }
      }
    } else if (v1.isNullConstant()) {
      if (v2.isNullConstant()) {
        return evaluate(0, 0);
      } else if (v2.isIntConstant()) {
        return evaluate(0, v2.asIntConstant().value);
      } else if (v2.isAddressConstant()) {
        return evaluate(Address.zero(), v2.asAddressConstant().value);
      } else if (v2.isObjectConstant()) {
        if (!v2.isMovableObjectConstant()) {
          return evaluate(Address.zero(),
              Magic.objectAsAddress(v2.asObjectConstant().value));
        } else if (isEQUAL()) {
          return FALSE;
        } else if (isNOT_EQUAL()) {
          return TRUE;
        }
      }
    } else if (v1.similar(v2) && !isFLOATINGPOINT()) {
      // comparisons of identical operands can be evaluated, except
      // for floating point NaN cases
      switch (value) {
        case EQUAL:
        case GREATER_EQUAL:
        case LESS_EQUAL:
        case HIGHER_EQUAL:
        case LOWER_EQUAL:
          return TRUE;
        case NOT_EQUAL:
        case LESS:
        case GREATER:
        case HIGHER:
        case LOWER:
        case BORROW_FROM_SUB:
        case NO_BORROW_FROM_SUB:
        case BORROW_FROM_RSUB:
        case NO_BORROW_FROM_RSUB:
        case OVERFLOW_FROM_SUB:
        case NO_OVERFLOW_FROM_SUB:
        case OVERFLOW_FROM_RSUB:
        case NO_OVERFLOW_FROM_RSUB:
          return FALSE;
        case CARRY_FROM_ADD:
        case NO_CARRY_FROM_ADD:
        case OVERFLOW_FROM_ADD:
        case NO_OVERFLOW_FROM_ADD:
        case BIT_TEST:
        case NO_BIT_TEST:
        case RBIT_TEST:
        case NO_RBIT_TEST:
        case OVERFLOW_FROM_MUL:
        case NO_OVERFLOW_FROM_MUL:
          return UNKNOWN;
        default:
          throw new OptimizingCompilerException("invalid condition " + this);
      }
    }
    return UNKNOWN;
  }

  /**
   * Given two ints, evaluate the condition on them.
   *
   * @param v1 first operand to condition
   * @param v2 second operand to condition
   * @return <code>TRUE</code> if (v1 cond v2) or
   *         <code>FALSE</code> if !(v1 cond v2) or
   *         <code>UNKNOWN</code>
   */
  public int evaluate(int v1, int v2) {
    switch (value) {
      case EQUAL:          return (v1 == v2) ? TRUE : FALSE;
      case NOT_EQUAL:      return (v1 != v2) ? TRUE : FALSE;
      case GREATER:        return (v1 > v2)  ? TRUE : FALSE;
      case LESS:           return (v1 < v2) ? TRUE : FALSE;
      case GREATER_EQUAL:  return (v1 >= v2)  ? TRUE : FALSE;
      case LESS_EQUAL:     return (v1 <= v2)  ? TRUE : FALSE;
      case LOWER:          return lower(v1, v2) ? TRUE : FALSE;
      case LOWER_EQUAL:    return lower_equal(v1, v2) ? TRUE : FALSE;
      case HIGHER:         return higher(v1, v2) ? TRUE : FALSE;
      case HIGHER_EQUAL:   return higher_equal(v1, v2) ? TRUE : FALSE;
      case CARRY_FROM_ADD:        return carry_from_add(v1, v2) ? TRUE : FALSE;
      case NO_CARRY_FROM_ADD:     return carry_from_add(v1, v2) ? FALSE : TRUE;
      case OVERFLOW_FROM_ADD:     return overflow_from_add(v1, v2) ? TRUE : FALSE;
      case NO_OVERFLOW_FROM_ADD:  return overflow_from_add(v1, v2) ? FALSE : TRUE;
      case BORROW_FROM_SUB:       return borrow_from_sub(v1, v2) ? TRUE : FALSE;
      case NO_BORROW_FROM_SUB:    return borrow_from_sub(v1, v2) ? FALSE : TRUE;
      case BORROW_FROM_RSUB:      return borrow_from_sub(v2, v1) ? TRUE : FALSE;
      case NO_BORROW_FROM_RSUB:   return borrow_from_sub(v2, v1) ? FALSE : TRUE;
      case OVERFLOW_FROM_SUB:     return overflow_from_sub(v1, v2) ? TRUE : FALSE;
      case NO_OVERFLOW_FROM_SUB:  return overflow_from_sub(v1, v2) ? FALSE : TRUE;
      case OVERFLOW_FROM_RSUB:    return overflow_from_sub(v2, v1) ? TRUE : FALSE;
      case NO_OVERFLOW_FROM_RSUB: return overflow_from_sub(v2, v1) ? FALSE : TRUE;
      case BIT_TEST:              return bit_test(v1, v2) ? TRUE : FALSE;
      case NO_BIT_TEST:           return bit_test(v1, v2) ? FALSE : TRUE;
      case RBIT_TEST:             return bit_test(v2, v1) ? TRUE : FALSE;
      case NO_RBIT_TEST:          return bit_test(v2, v1) ? FALSE : TRUE;
      case OVERFLOW_FROM_MUL:     return overflow_from_mul(v1, v2) ? TRUE : FALSE;
      case NO_OVERFLOW_FROM_MUL:  return overflow_from_mul(v1, v2) ? FALSE : TRUE;
    }
    throw new OptimizingCompilerException("invalid condition " + this);
  }

  /**
   * Given two longs, evaluate the condition on them.
   *
   * @param v1 first operand to condition
   * @param v2 second operand to condition
   * @return <code>TRUE</code> if (v1 cond v2) or
   *         <code>FALSE</code> if !(v1 cond v2) or
   *         <code>UNKNOWN</code>
   */
  public int evaluate(long v1, long v2) {
    switch (value) {
      case EQUAL:         return (v1 == v2) ? TRUE : FALSE;
      case NOT_EQUAL:     return (v1 != v2) ? TRUE : FALSE;
      case GREATER:       return (v1 > v2)  ? TRUE : FALSE;
      case LESS:          return (v1 < v2)  ? TRUE : FALSE;
      case GREATER_EQUAL: return (v1 >= v2) ? TRUE : FALSE;
      case LESS_EQUAL:    return (v1 <= v2) ? TRUE : FALSE;
    }
    throw new OptimizingCompilerException("invalid condition " + this);
  }

  /**
   * Given two floats, evaluate the condition on them.
   *
   * @param v1 first operand to condition
   * @param v2 second operand to condition
   * @return <code>true</code> if (v1 cond v2) or
   *         <code>false</code> otherwise
   */
  public int evaluate(float v1, float v2) {
    switch (value) {
      // Return FALSE when UNORDERED
      case CMPL_EQUAL:               return (v1 == v2) ? TRUE : FALSE;
      case CMPL_GREATER:             return (v1 > v2)  ? TRUE : FALSE;
      case CMPG_LESS:                return (v1 < v2)  ? TRUE : FALSE;
      case CMPL_GREATER_EQUAL:        return (v1 >= v2) ? TRUE : FALSE;
      case CMPG_LESS_EQUAL:          return (v1 <= v2) ? TRUE : FALSE;
      // Return TRUE when UNORDERED
      case CMPL_NOT_EQUAL:           return (v1 == v2) ? FALSE : TRUE;
      case CMPL_LESS:                return (v1 >= v2) ? FALSE : TRUE;
      case CMPG_GREATER_EQUAL:       return (v1 < v2)  ? FALSE : TRUE;
      case CMPG_GREATER:             return (v1 <= v2) ? FALSE : TRUE;
      case CMPL_LESS_EQUAL:          return (v1 > v2)  ? FALSE : TRUE;
    }
    throw new OptimizingCompilerException("invalid condition " + this);
  }

  /**
   * Given two doubles, evaluate the condition on them.
   *
   * @param v1 first operand to condition
   * @param v2 second operand to condition
   * @return <code>true</code> if (v1 cond v2) or
   *         <code>false</code> otherwise
   */
  public int evaluate(double v1, double v2) {
    switch (value) {
      // Return FALSE when UNORDERED
      case CMPL_EQUAL:               return (v1 == v2) ? TRUE : FALSE;
      case CMPL_GREATER:             return (v1 > v2)  ? TRUE : FALSE;
      case CMPG_LESS:                return (v1 < v2)  ? TRUE : FALSE;
      case CMPL_GREATER_EQUAL:        return (v1 >= v2) ? TRUE : FALSE;
      case CMPG_LESS_EQUAL:          return (v1 <= v2) ? TRUE : FALSE;
        // Return TRUE when UNORDERED
      case CMPL_NOT_EQUAL:           return (v1 == v2) ? FALSE : TRUE;
      case CMPL_LESS:                return (v1 >= v2) ? FALSE : TRUE;
      case CMPG_GREATER_EQUAL:       return (v1 < v2)  ? FALSE : TRUE;
      case CMPG_GREATER:             return (v1 <= v2) ? FALSE : TRUE;
      case CMPL_LESS_EQUAL:          return (v1 > v2)  ? FALSE : TRUE;
    }
    throw new OptimizingCompilerException("invalid condition " + this);
  }

  /**
   * Given two Addresses, evaluate the condition on them.
   *
   * @param v1 first operand to condition
   * @param v2 second operand to condition
   * @return <code>TRUE</code> if (v1 cond v2) or
   *         <code>FALSE</code> if !(v1 cond v2) or
   *         <code>UNKNOWN</code>
   */
  public int evaluate(Address v1, Address v2) {
    switch (value) {
      case EQUAL:         return (v1.EQ(v2)) ? TRUE : FALSE;
      case NOT_EQUAL:     return (v1.NE(v2)) ? TRUE : FALSE;
      case GREATER:       return (v1.toWord().toOffset().sGT(v2.toWord().toOffset())) ? TRUE : FALSE;
      case LESS:          return (v1.toWord().toOffset().sLT(v2.toWord().toOffset())) ? TRUE : FALSE;
      case GREATER_EQUAL: return (v1.toWord().toOffset().sGE(v2.toWord().toOffset())) ? TRUE : FALSE;
      case LESS_EQUAL:    return (v1.toWord().toOffset().sLE(v2.toWord().toOffset())) ? TRUE : FALSE;
      case LOWER:         return (v1.LT(v2)) ? TRUE : FALSE;
      case LOWER_EQUAL:   return (v1.LE(v2)) ? TRUE : FALSE;
      case HIGHER:        return (v1.GT(v2)) ? TRUE : FALSE;
      case HIGHER_EQUAL:  return (v1.GE(v2)) ? TRUE : FALSE;
    }
    throw new OptimizingCompilerException("invalid condition " + this);
  }

  /**
   * Flip the direction of the condition.  Typical use is if you want to
   * change the direction of a branch. i.e. to transform:
   * <code>
   * if (condition) goto A
   * goto B
   * A:
   * </code>
   * into:
   * <code>
   * if (!condition) goto B
   * A:
   * </code>
   * Note that this is not the same as calling {@link #flipOperands}.
   *
   * @return this
   */
  public ConditionOperand flipCode() {
    switch (value) {
      case EQUAL:              value = NOT_EQUAL; break;
      case NOT_EQUAL:          value = EQUAL; break;
      case LESS:               value = GREATER_EQUAL; break;
      case LESS_EQUAL:         value = GREATER; break;
      case GREATER:            value = LESS_EQUAL; break;
      case GREATER_EQUAL:      value = LESS; break;

      case HIGHER:             value = LOWER_EQUAL; break;
      case LOWER:              value = HIGHER_EQUAL; break;
      case HIGHER_EQUAL:       value = LOWER; break;
      case LOWER_EQUAL:        value = HIGHER; break;

      case CMPL_EQUAL:         value = CMPL_NOT_EQUAL; break;
      case CMPL_GREATER:       value = CMPL_LESS_EQUAL; break;
      case CMPG_LESS:          value = CMPG_GREATER_EQUAL; break;
      case CMPL_GREATER_EQUAL: value = CMPL_LESS; break;
      case CMPG_LESS_EQUAL:    value = CMPG_GREATER; break;
      case CMPL_NOT_EQUAL:     value = CMPL_EQUAL; break;
      case CMPL_LESS:          value = CMPL_GREATER_EQUAL; break;
      case CMPG_GREATER_EQUAL: value = CMPG_LESS; break;
      case CMPG_GREATER:       value = CMPG_LESS_EQUAL; break;
      case CMPL_LESS_EQUAL:    value = CMPL_GREATER; break;

      case CARRY_FROM_ADD:        value = NO_CARRY_FROM_ADD; break;
      case NO_CARRY_FROM_ADD:     value = CARRY_FROM_ADD; break;

      case OVERFLOW_FROM_ADD:     value = NO_OVERFLOW_FROM_ADD; break;
      case NO_OVERFLOW_FROM_ADD:  value = OVERFLOW_FROM_ADD; break;

      case BORROW_FROM_SUB:       value = NO_BORROW_FROM_SUB; break;
      case NO_BORROW_FROM_SUB:    value = BORROW_FROM_SUB; break;
      case BORROW_FROM_RSUB:      value = NO_BORROW_FROM_RSUB; break;
      case NO_BORROW_FROM_RSUB:   value = BORROW_FROM_RSUB; break;

      case OVERFLOW_FROM_SUB:     value = NO_OVERFLOW_FROM_SUB; break;
      case NO_OVERFLOW_FROM_SUB:  value = OVERFLOW_FROM_SUB; break;
      case OVERFLOW_FROM_RSUB:    value = NO_OVERFLOW_FROM_RSUB; break;
      case NO_OVERFLOW_FROM_RSUB: value = OVERFLOW_FROM_RSUB; break;

      case BIT_TEST:              value = NO_BIT_TEST; break;
      case NO_BIT_TEST:           value = BIT_TEST; break;
      case RBIT_TEST:             value = NO_RBIT_TEST; break;
      case NO_RBIT_TEST:          value = RBIT_TEST; break;

      case OVERFLOW_FROM_MUL:     value = NO_OVERFLOW_FROM_MUL; break;
      case NO_OVERFLOW_FROM_MUL:  value = OVERFLOW_FROM_MUL; break;

      default:
        OptimizingCompilerException.UNREACHABLE();
    }
    return this;
  }

  /**
   * Change the condition code to allow the order of the operands to
   * be flipped. i.e. So that:
   * <code>
   * if x &lt; y then goto A
   * </code>
   * becomes:
   * <code>
   * if y &gt; x then goto A
   * </code>
   * Note that this is not the same as calling {@link #flipCode}.
   *
   * @return this
   */
  public ConditionOperand flipOperands() {
    switch (value) {
      case EQUAL:              value = EQUAL; break;
      case NOT_EQUAL:          value = NOT_EQUAL;   break;
      case LESS:               value = GREATER; break;
      case LESS_EQUAL:         value = GREATER_EQUAL; break;
      case GREATER:            value = LESS; break;
      case GREATER_EQUAL:      value = LESS_EQUAL; break;

      case HIGHER:             value = LOWER; break;
      case LOWER:              value = HIGHER; break;
      case HIGHER_EQUAL:       value = LOWER_EQUAL; break;
      case LOWER_EQUAL:        value = HIGHER_EQUAL; break;

      case CMPL_EQUAL:         value = CMPL_EQUAL; break;
      case CMPL_GREATER:       value = CMPG_LESS; break;
      case CMPG_LESS:          value = CMPL_GREATER; break;
      case CMPL_GREATER_EQUAL: value = CMPG_LESS_EQUAL; break;
      case CMPG_LESS_EQUAL:    value = CMPL_GREATER_EQUAL; break;
      case CMPL_NOT_EQUAL:     value = CMPL_NOT_EQUAL; break;
      case CMPL_LESS:          value = CMPG_GREATER; break;
      case CMPG_GREATER_EQUAL: value = CMPL_LESS_EQUAL; break;
      case CMPG_GREATER:       value = CMPL_LESS; break;
      case CMPL_LESS_EQUAL:    value = CMPG_GREATER_EQUAL; break;

      case CARRY_FROM_ADD:        break; // add is commutative
      case NO_CARRY_FROM_ADD:     break;

      case OVERFLOW_FROM_ADD:     break;
      case NO_OVERFLOW_FROM_ADD:  break;

      case BORROW_FROM_SUB:       value = BORROW_FROM_RSUB; break;
      case NO_BORROW_FROM_SUB:    value = NO_BORROW_FROM_RSUB; break;
      case BORROW_FROM_RSUB:      value = BORROW_FROM_SUB; break;
      case NO_BORROW_FROM_RSUB:   value = NO_BORROW_FROM_SUB; break;

      case OVERFLOW_FROM_SUB:     value = OVERFLOW_FROM_RSUB; break;
      case NO_OVERFLOW_FROM_SUB:  value = NO_OVERFLOW_FROM_RSUB; break;
      case OVERFLOW_FROM_RSUB:    value = OVERFLOW_FROM_SUB; break;
      case NO_OVERFLOW_FROM_RSUB: value = NO_OVERFLOW_FROM_SUB; break;

      case BIT_TEST:              value = RBIT_TEST; break;
      case NO_BIT_TEST:           value = NO_RBIT_TEST; break;
      case RBIT_TEST:             value = BIT_TEST; break;
      case NO_RBIT_TEST:          value = NO_BIT_TEST; break;

      case OVERFLOW_FROM_MUL:     break; // mul is commutative
      case NO_OVERFLOW_FROM_MUL:  break;

      default:
        OptimizingCompilerException.UNREACHABLE();
    }
    return this;
  }

  /**
   * Returns the string representation of this operand. Postfix
   * meanings:
   * <ul><li>U - unsigned comparison</li>
   *     <li>F - floating point compare that doesn't branch when
   *         operands are unordered</li>
   *     <li>FU - floating point compare that does branch when
   *         operands are unordered</li>
   * </ul>
   *
   * @return a string representation of this operand.
   */
  @Override
  public String toString() {
    switch (value) {
      case EQUAL:              return "==";
      case NOT_EQUAL:          return "!=";
      case LESS:               return "<";
      case LESS_EQUAL:         return "<=";
      case GREATER:            return ">";
      case GREATER_EQUAL:      return ">=";

      case HIGHER:             return ">U";
      case LOWER:              return "<U";
      case HIGHER_EQUAL:       return ">=U";
      case LOWER_EQUAL:        return "<=U";

      case CMPL_EQUAL:         return "==F";
      case CMPL_GREATER:       return ">F";
      case CMPG_LESS:          return "<F";
      case CMPL_GREATER_EQUAL: return ">=F";
      case CMPG_LESS_EQUAL:    return "<=F";

      case CMPL_NOT_EQUAL:     return "!=FU";
      case CMPL_LESS:          return "<FU";
      case CMPG_GREATER_EQUAL: return ">=FU";
      case CMPG_GREATER:       return ">FU";
      case CMPL_LESS_EQUAL:    return "<=FU";

      case CARRY_FROM_ADD:        return "carry(+)";
      case NO_CARRY_FROM_ADD:     return "nocarry(+)";

      case OVERFLOW_FROM_ADD:     return "overflow(+)";
      case NO_OVERFLOW_FROM_ADD:  return "nooverflow(+)";

      case BORROW_FROM_SUB:       return "borrow(-)";
      case NO_BORROW_FROM_SUB:    return "noborrow(-)";
      case BORROW_FROM_RSUB:      return "borrow(r-)";
      case NO_BORROW_FROM_RSUB:   return "noborrow(r-)";

      case OVERFLOW_FROM_SUB:     return "overflow(-)";
      case NO_OVERFLOW_FROM_SUB:  return "nooverflow(-)";
      case OVERFLOW_FROM_RSUB:    return "overflow(r-)";
      case NO_OVERFLOW_FROM_RSUB: return "nooverflow(r-)";

      case BIT_TEST:              return "bt";
      case NO_BIT_TEST:           return "!bt";
      case RBIT_TEST:             return "rbt";
      case NO_RBIT_TEST:          return "!rbt";

      case OVERFLOW_FROM_MUL:     return "overflow(*)";
      case NO_OVERFLOW_FROM_MUL:  return "nooverflow(*)";

      default:                 return "UNKNOWN";
    }
  }
}
