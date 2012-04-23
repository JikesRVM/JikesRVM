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
  public static final int EQUAL = 0;
  public static final int NOT_EQUAL = 1;
  public static final int LESS = 2;
  public static final int GREATER_EQUAL = 3;
  public static final int GREATER = 4;
  public static final int LESS_EQUAL = 5;

  /* unsigned integer arithmetic */
  public static final int HIGHER = 6;
  public static final int LOWER = 7;
  public static final int HIGHER_EQUAL = 8;
  public static final int LOWER_EQUAL = 9;

  /* floating-point arithmethic */
  // branches that fall through when unordered
  /** Branch if == (equivalent to CMPG_EQUAL) */
  public static final int CMPL_EQUAL = 10;
  /** Branch if > */
  public static final int CMPL_GREATER = 11;
  /** Branch if < */
  public static final int CMPG_LESS = 12;
  /** Branch if >= */
  public static final int CMPL_GREATER_EQUAL = 13;
  /** Branch if <= */
  public static final int CMPG_LESS_EQUAL = 14;
  // branches that are taken when unordered
  /** Branch if != (equivalent to CMPG_NOT_EQUAL) */
  public static final int CMPL_NOT_EQUAL = 17;
  /** Branch if < or unordered */
  public static final int CMPL_LESS = 18;
  /** Branch if >= or unordered */
  public static final int CMPG_GREATER_EQUAL = 19;
  /** Branch if > or unordered */
  public static final int CMPG_GREATER = 20;
  /** Branch if <= or unordered */
  public static final int CMPL_LESS_EQUAL = 21;

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
   * Is the condition code an unsigned comparision?
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
   * compare. Used during bc2ir.
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
   * compare. Used during bc2ir.
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
   * integer compare. Used during IA-32 BURS - NB this doesn't respect
   * ordered/unordered operation, so it should only be used when it's
   * safe to.
   */
  public ConditionOperand translateUNSIGNED() {
    switch (value) {
      case CMPL_EQUAL:
        value = EQUAL;
        break;
      case CMPL_GREATER:
        value = HIGHER;
        break;
      case CMPG_LESS:
        value = LOWER;
        break;
      case CMPL_GREATER_EQUAL:
        value = HIGHER_EQUAL;
        break;
      case CMPG_LESS_EQUAL:
        value = LOWER_EQUAL;
        break;
      case CMPL_NOT_EQUAL:
        value = NOT_EQUAL;
        break;
      case CMPL_LESS:
        value = LOWER;
        break;
      case CMPG_GREATER_EQUAL:
        value = HIGHER_EQUAL;
        break;
      case CMPG_GREATER:
        value = HIGHER;
        break;
      case CMPL_LESS_EQUAL:
        value = LOWER_EQUAL;
        break;
      default:
        throw new OptimizingCompilerException("invalid condition " + this);
    }
    return this;
  }

  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   *
   * @return a copy of <code>this</code>
   */
  @Override
  public Operand copy() {
    return new ConditionOperand(value);
  }

  /**
   * Are two operands semantically equivalent?
   *
   * @param op other operand
   * @return   <code>true</code> if <code>this</code> and <code>op</code>
   *           are semantically equivalent or <code>false</code>
   *           if they are not.
   */
  @Override
  public boolean similar(Operand op) {
    return (op instanceof ConditionOperand) && (((ConditionOperand) op).value == value);
  }

  public static final int FALSE = 0;
  public static final int TRUE = 1;
  public static final int UNKNOWN = 2;

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
          return FALSE;
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
      case EQUAL:
        return (v1 == v2) ? TRUE : FALSE;
      case NOT_EQUAL:
        return (v1 != v2) ? TRUE : FALSE;
      case GREATER:
        return (v1 > v2) ? TRUE : FALSE;
      case LESS:
        return (v1 < v2) ? TRUE : FALSE;
      case GREATER_EQUAL:
        return (v1 >= v2) ? TRUE : FALSE;
      case LESS_EQUAL:
        return (v1 <= v2) ? TRUE : FALSE;
      case LOWER:
        if ((v1 >= 0 && v2 >= 0) || (v1 < 0 && v2 < 0)) {
          return (v1 < v2) ? TRUE : FALSE;
        } else if (v1 < 0) {
          return FALSE;
        } else {
          return TRUE;
        }
      case LOWER_EQUAL:
        if ((v1 >= 0 && v2 >= 0) || (v1 < 0 && v2 < 0)) {
          return (v1 <= v2) ? TRUE : FALSE;
        } else if (v1 < 0) {
          return FALSE;
        } else {
          return TRUE;
        }
      case HIGHER:
        if ((v1 >= 0 && v2 >= 0) || (v1 < 0 && v2 < 0)) {
          return (v1 > v2) ? TRUE : FALSE;
        } else if (v1 < 0) {
          return TRUE;
        } else {
          return FALSE;
        }
      case HIGHER_EQUAL:
        if ((v1 >= 0 && v2 >= 0) || (v1 < 0 && v2 < 0)) {
          return (v1 >= v2) ? TRUE : FALSE;
        } else if (v1 < 0) {
          return TRUE;
        } else {
          return FALSE;
        }
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
      case EQUAL:
        return (v1 == v2) ? TRUE : FALSE;
      case NOT_EQUAL:
        return (v1 != v2) ? TRUE : FALSE;
      case GREATER:
        return (v1 > v2) ? TRUE : FALSE;
      case LESS:
        return (v1 < v2) ? TRUE : FALSE;
      case GREATER_EQUAL:
        return (v1 >= v2) ? TRUE : FALSE;
      case LESS_EQUAL:
        return (v1 <= v2) ? TRUE : FALSE;
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
      case CMPL_EQUAL:
        return (v1 == v2) ? TRUE : FALSE;
      case CMPL_GREATER:
        return (v1 > v2) ? TRUE : FALSE;
      case CMPG_LESS:
        return (v1 < v2) ? TRUE : FALSE;
      case CMPL_GREATER_EQUAL:
        return (v1 >= v2) ? TRUE : FALSE;
      case CMPG_LESS_EQUAL:
        return (v1 <= v2) ? TRUE : FALSE;
        // Return TRUE when UNORDERED
      case CMPL_NOT_EQUAL:
        return (v1 == v2) ? FALSE : TRUE;
      case CMPL_LESS:
        return (v1 >= v2) ? FALSE : TRUE;
      case CMPG_GREATER_EQUAL:
        return (v1 < v2) ? FALSE : TRUE;
      case CMPG_GREATER:
        return (v1 <= v2) ? FALSE : TRUE;
      case CMPL_LESS_EQUAL:
        return (v1 > v2) ? FALSE : TRUE;
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
      case CMPL_EQUAL:
        return (v1 == v2) ? TRUE : FALSE;
      case CMPL_GREATER:
        return (v1 > v2) ? TRUE : FALSE;
      case CMPG_LESS:
        return (v1 < v2) ? TRUE : FALSE;
      case CMPL_GREATER_EQUAL:
        return (v1 >= v2) ? TRUE : FALSE;
      case CMPG_LESS_EQUAL:
        return (v1 <= v2) ? TRUE : FALSE;
        // Return TRUE when UNORDERED
      case CMPL_NOT_EQUAL:
        return (v1 == v2) ? FALSE : TRUE;
      case CMPL_LESS:
        return (v1 >= v2) ? FALSE : TRUE;
      case CMPG_GREATER_EQUAL:
        return (v1 < v2) ? FALSE : TRUE;
      case CMPG_GREATER:
        return (v1 <= v2) ? FALSE : TRUE;
      case CMPL_LESS_EQUAL:
        return (v1 > v2) ? FALSE : TRUE;
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
      case EQUAL:
        return (v1.EQ(v2)) ? TRUE : FALSE;
      case NOT_EQUAL:
        return (v1.NE(v2)) ? TRUE : FALSE;
      case GREATER:
        return (v1.toWord().toOffset().sGT(v2.toWord().toOffset())) ? TRUE : FALSE;
      case LESS:
        return (v1.toWord().toOffset().sLT(v2.toWord().toOffset())) ? TRUE : FALSE;
      case GREATER_EQUAL:
        return (v1.toWord().toOffset().sGE(v2.toWord().toOffset())) ? TRUE : FALSE;
      case LESS_EQUAL:
        return (v1.toWord().toOffset().sLE(v2.toWord().toOffset())) ? TRUE : FALSE;
      case LOWER:
        return (v1.LT(v2)) ? TRUE : FALSE;
      case LOWER_EQUAL:
        return (v1.LE(v2)) ? TRUE : FALSE;
      case HIGHER:
        return (v1.GT(v2)) ? TRUE : FALSE;
      case HIGHER_EQUAL:
        return (v1.GE(v2)) ? TRUE : FALSE;
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
   */
  public ConditionOperand flipCode() {
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
      case CMPL_EQUAL:
        value = CMPL_NOT_EQUAL;
        break;
      case CMPL_GREATER:
        value = CMPL_LESS_EQUAL;
        break;
      case CMPG_LESS:
        value = CMPG_GREATER_EQUAL;
        break;
      case CMPL_GREATER_EQUAL:
        value = CMPL_LESS;
        break;
      case CMPG_LESS_EQUAL:
        value = CMPG_GREATER;
        break;
      case CMPL_NOT_EQUAL:
        value = CMPL_EQUAL;
        break;
      case CMPL_LESS:
        value = CMPL_GREATER_EQUAL;
        break;
      case CMPG_GREATER_EQUAL:
        value = CMPG_LESS;
        break;
      case CMPG_GREATER:
        value = CMPG_LESS_EQUAL;
        break;
      case CMPL_LESS_EQUAL:
        value = CMPL_GREATER;
        break;
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
   * if y &gte; x then goto A
   * </code>
   * Note that this is not the same as calling {@link #flipCode}.
   */
  public ConditionOperand flipOperands() {
    switch (value) {
      case EQUAL:
        value = EQUAL;
        break;
      case NOT_EQUAL:
        value = NOT_EQUAL;
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
      case CMPL_EQUAL:
        value = CMPL_EQUAL;
        break;
      case CMPL_GREATER:
        value = CMPG_LESS;
        break;
      case CMPG_LESS:
        value = CMPL_GREATER;
        break;
      case CMPL_GREATER_EQUAL:
        value = CMPG_LESS_EQUAL;
        break;
      case CMPG_LESS_EQUAL:
        value = CMPL_GREATER_EQUAL;
        break;
      case CMPL_NOT_EQUAL:
        value = CMPL_NOT_EQUAL;
        break;
      case CMPL_LESS:
        value = CMPG_GREATER;
        break;
      case CMPG_GREATER_EQUAL:
        value = CMPL_LESS_EQUAL;
        break;
      case CMPG_GREATER:
        value = CMPL_LESS;
        break;
      case CMPL_LESS_EQUAL:
        value = CMPG_GREATER_EQUAL;
        break;
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
      case EQUAL:
        return "==";
      case NOT_EQUAL:
        return "!=";
      case LESS:
        return "<";
      case LESS_EQUAL:
        return "<=";
      case GREATER:
        return ">";
      case GREATER_EQUAL:
        return ">=";
      case HIGHER:
        return ">U";
      case LOWER:
        return "<U";
      case HIGHER_EQUAL:
        return ">=U";
      case LOWER_EQUAL:
        return "<=U";
      case CMPL_EQUAL:
        return "==F";
      case CMPL_GREATER:
        return ">F";
      case CMPG_LESS:
        return "<F";
      case CMPL_GREATER_EQUAL:
        return ">=F";
      case CMPG_LESS_EQUAL:
        return "<=F";
      case CMPL_NOT_EQUAL:
        return "!=FU";
      case CMPL_LESS:
        return "<FU";
      case CMPG_GREATER_EQUAL:
        return ">=FU";
      case CMPG_GREATER:
        return ">FU";
      case CMPL_LESS_EQUAL:
        return "<=FU";
      default:
        return "UNKNOWN";
    }
  }
}
