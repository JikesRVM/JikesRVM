/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

/**
 * Encodes the condition codes for branches.
 * 
 * @see OPT_Operand
 *
 * @author by Mauricio Serrano
 */
public final class OPT_ConditionOperand extends OPT_Operand {

  /* signed integer arithmetic  & floating-point */
  static final int EQUAL = 0;
  static final int NOT_EQUAL = 1;
  static final int LESS = 2;
  static final int GREATER_EQUAL = 3;
  static final int GREATER = 4;
  static final int LESS_EQUAL = 5;
  static final int OVERFLOW = 6;
  static final int NOT_OVERFLOW = 7;

  /* these should be the same as EQ or NE, provided for REFS */
  static final int NULL = 8;
  static final int NONNULL = 9;

  /* unsigned integer arithmetic */
  static final int HIGHER = 10;
  static final int LOWER = 11;
  static final int HIGHER_EQUAL = 12;
  static final int LOWER_EQUAL = 13;
  static final int CARRY = 14;
  static final int NOT_CARRY = 15;
  static final int SAME = 16;
  static final int NOT_SAME = 17;

  /* floating-point arithmethic  ?? */
  static final int UNORDERED = 18;
  static final int NOT_UNORDERED = 19;


  /**
   * Value of this operand.
   */
  int value;


  /**
   * @param code the condition code
   */
  private OPT_ConditionOperand(int code) {
    value = code;
  }

  /**
   * Create the condition code operand for EQUAL
   * 
   * @return a new condition code operand
   */
  static OPT_ConditionOperand EQUAL() {
    return new OPT_ConditionOperand(EQUAL);
  }

  /**
   * Create the condition code operand for NOT_EQUAL
   * 
   * @return a newly created condition code operand
   */
  static OPT_ConditionOperand NOT_EQUAL() {
    return new OPT_ConditionOperand(NOT_EQUAL);
  }

  /**
   * Create the condition code operand for LESS
   * 
   * @return a newly created condition code operand
   */
  static OPT_ConditionOperand LESS() {
    return new OPT_ConditionOperand(LESS);
  }

  /**
   * Create the condition code operand for GREATER_EQUAL
   * 
   * @return a newly created condition code operand
   */
  static OPT_ConditionOperand GREATER_EQUAL() {
    return new OPT_ConditionOperand(GREATER_EQUAL);
  }

  /**
   * Create the condition code operand for GREATER
   * 
   * @return a newly created condition code operand
   */
  static OPT_ConditionOperand GREATER() {
    return new OPT_ConditionOperand(GREATER);
  }

  /**
   * Create the condition code operand for LESS_EQUAL
   * 
   * @return a newly created condition code operand
   */
  static OPT_ConditionOperand LESS_EQUAL() {
    return new OPT_ConditionOperand(LESS_EQUAL);
  }

  /**
   * Create the condition code operand for HIGHER
   * 
   * @return a newly created condition code operand
   */
  static OPT_ConditionOperand HIGHER() {
    return new OPT_ConditionOperand(HIGHER);
  }

  /**
   * Create the condition code operand for LOWER
   * 
   * @return a newly created condition code operand
   */
  static OPT_ConditionOperand LOWER() {
    return new OPT_ConditionOperand(LOWER);
  }

  /**
   * Create the condition code operand for HIGHER_EQUAL
   * 
   * @return a newly created condition code operand
   */
  static OPT_ConditionOperand HIGHER_EQUAL() {
    return new OPT_ConditionOperand(HIGHER_EQUAL);
  }

  /**
   * Create the condition code operand for LOWER_EQUAL
   * 
   * @return a newly created condition code operand
   */
  static OPT_ConditionOperand LOWER_EQUAL() {
    return new OPT_ConditionOperand(LOWER_EQUAL);
  }

  /**
   * Is the condition code EQUAL?
   * 
   * @return <code>true</code> if it is or <code>false</code> if it is not
   */
  boolean isEQUAL() {
    return value == EQUAL;
  }

  /**
   * Is the condition code NOT_EQUAL?
   * 
   * @return <code>true</code> if it is or <code>false</code> if it is not
   */
  boolean isNOT_EQUAL() {
    return  value == NOT_EQUAL;
  }

  /**
   * Is the condition code LESS EQUAL?
   * 
   * @return <code>true</code> if it is or <code>false</code> if it is not
   */
  boolean isLESS_EQUAL() {
    return  value == LESS_EQUAL;
  }

  /**
   * Is the condition code GREATER_EQUAL?
   * 
   * @return <code>true</code> if it is or <code>false</code> if it is not
   */
  boolean isGREATER_EQUAL() {
    return  value == GREATER_EQUAL;
  }

  /**
   * Is the condition code GREATER?
   * 
   * @return <code>true</code> if it is or <code>false</code> if it is not
   */
  boolean isGREATER() {
    return  value == GREATER;
  }

  /**
   * Is the condition code LESS?
   * 
   * @return <code>true</code> if it is or <code>false</code> if it is not
   */
  boolean isLESS() {
    return  value == LESS;
  }

  /**
   * Is the condition code HIGHER?
   * 
   * @return <code>true</code> if it is or <code>false</code> if it is not
   */
  boolean isHIGHER() {
    return  value == HIGHER;
  }

  /**
   * Is the condition code LOWER?
   * 
   * @return <code>true</code> if it is or <code>false</code> if it is not
   */
  boolean isLOWER() {
    return  value == LOWER;
  }

  /**
   * Is the condition code HIGHER_EQUAL?
   * 
   * @return <code>true</code> if it is or <code>false</code> if it is not
   */
  boolean isHIGHER_EQUAL() {
    return  value == HIGHER_EQUAL;
  }

  /**
   * Is the condition code LOWER_EQUAL?
   * 
   * @return <code>true</code> if it is or <code>false</code> if it is not
   */
  boolean isLOWER_EQUAL() {
    return  value == LOWER_EQUAL;
  }

  /**
   * Is the condition code an unsigned comparision?
   * @return <code>true</code> if it is or <code>false</code> if it is not
   */
  boolean isUNSIGNED() {
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
   * Return a new operand that is semantically equivalent to <code>this</code>.
   * 
   * @return a copy of <code>this</code>
   */
  public OPT_Operand copy() {
    return  new OPT_ConditionOperand(value);
  }


  /**
   * Are two operands semantically equivalent?
   *
   * @param op other operand
   * @return   <code>true</code> if <code>this</code> and <code>op</code>
   *           are semantically equivalent or <code>false</code> 
   *           if they are not.
   */
  boolean similar(OPT_Operand op) {
    return  (op instanceof OPT_ConditionOperand) && (((OPT_ConditionOperand)op).value
        == value);
  }


  /**
   * Given two operands, evaluate the condition on them.
   * 
   * @param v1 first operand to condition
   * @param v2 second operand to condition
   * @return <code>true</code> if (v1 cond v2) or 
   *         <code>false</code> otherwise
   */
  boolean evaluate(OPT_Operand v1, OPT_Operand v2) {
    if (v1.isIntConstant()) {
      if (v2.isIntConstant()) {
        return evaluate(v1.asIntConstant().value, 
			v2.asIntConstant().value);
      } else if (v2.isNullConstant()) {
	return evaluate(v1.asIntConstant().value, 0); 
      }
    } else if (v1.isLongConstant()) {
      if (v2.isLongConstant()) {
        return evaluate(v1.asLongConstant().value, 
			v2.asLongConstant().value);
      } 
    } else if (v1.isFloatConstant()) {
      if (v2.isFloatConstant()) {
        return evaluate(v1.asFloatConstant().value, 
			v2.asFloatConstant().value);
      } 
    } else if (v1.isDoubleConstant()) {
      if (v2.isDoubleConstant()) {
        return evaluate(v1.asDoubleConstant().value, 
			v2.asDoubleConstant().value);
      } 
    } else if (v1.isStringConstant()) {
      if (v2.isStringConstant()) {
	if (isEQUAL()) {
          return v1.asStringConstant().value == v2.asStringConstant().value;
	} else if (isNOT_EQUAL()) {
          return v1.asStringConstant().value != v2.asStringConstant().value;
	}
      } else if (v2.isNullConstant() ||
		 (v2.isIntConstant() && v2.asIntConstant().value == 0)) {
	if (isEQUAL()) {
	  return false;
	} else if (isNOT_EQUAL()) {
	  return true;
	}
      }
    } else if (v1.isNullConstant()) {
      if (v2.isNullConstant()) {
	return evaluate(0, 0);
      } else if (v2.isIntConstant()) {
	return evaluate(0, v2.asIntConstant().value);
      } else if (v2.isStringConstant()) {
	if (isEQUAL()) {
	  return false;
	} else if (isNOT_EQUAL()) {
	  return true;
	}
      }
    }
    throw new OPT_OptimizingCompilerException("erroneous computation: " + 
					      v1 + " "+ this +" " + v2);
  }


  /**
   * Given two ints, evaluate the condition on them.
   * 
   * @param v1 first operand to condition
   * @param v2 second operand to condition
   * @return <code>true</code> if (v1 cond v2) or 
   *         <code>false</code> otherwise
   */
  boolean evaluate(int v1, int v2) {
    switch (value) {
    case EQUAL: 
      return v1 == v2;
    case NOT_EQUAL:
      return v1 != v2;
    case GREATER:
      return v1 > v2;
    case LESS:
      return v1 < v2;
    case GREATER_EQUAL:
      return v1 >= v2;
    case LESS_EQUAL:
      return v1 <= v2;
    case LOWER: 
	if ((v1 >= 0 && v2 >= 0) || (v1 < 0 && v2 < 0)) return v1 < v2;
	if (v1 < 0) return false;
	return true;
    case LOWER_EQUAL:
	if ((v1 >= 0 && v2 >= 0) || (v1 < 0 && v2 < 0)) return v1 <= v2;
	if (v1 < 0) return false;
	return true;
    case HIGHER: 
	if ((v1 >= 0 && v2 >= 0) || (v1 < 0 && v2 < 0)) return v1 > v2;
	if (v1 < 0) return true;
	return false;
    case HIGHER_EQUAL:
	if ((v1 >= 0 && v2 >= 0) || (v1 < 0 && v2 < 0)) return v1 >= v2;
	if (v1 < 0) return true;
	return false;
    }
    throw  new OPT_OptimizingCompilerException("invalid condition" + this);
  }


  /**
   * Given two longs, evaluate the condition on them.
   * 
   * @param v1 first operand to condition
   * @param v2 second operand to condition
   * @return <code>true</code> if (v1 cond v2) or 
   *         <code>false</code> otherwise
   */
  boolean evaluate(long v1, long v2) {
    switch (value) {
    case EQUAL:
      return v1 == v2;
    case NOT_EQUAL:
      return v1 != v2;
    case GREATER:
      return v1 > v2;
    case LESS:
      return v1 < v2;
    case GREATER_EQUAL:
      return v1 >= v2;
    case LESS_EQUAL:
      return v1 <= v2;
    }
    throw  new OPT_OptimizingCompilerException("invalid condition" + this);
  }


  /**
   * Given two floats, evaluate the condition on them.
   * 
   * @param v1 first operand to condition
   * @param v2 second operand to condition
   * @return <code>true</code> if (v1 cond v2) or 
   *         <code>false</code> otherwise
   */
  boolean evaluate(float v1, float v2) {
    switch (value) {
    case EQUAL:
      return v1 == v2;
    case NOT_EQUAL:
      return v1 != v2;
    case GREATER:
      return v1 > v2;
    case LESS:
      return v1 < v2;
    case GREATER_EQUAL:
      return v1 >= v2;
    case LESS_EQUAL:
      return v1 <= v2;
    }
    throw  new OPT_OptimizingCompilerException("invalid condition" + this);
  }

  /**
   * Given two doubles, evaluate the condition on them.
   * 
   * @param v1 first operand to condition
   * @param v2 second operand to condition
   * @return <code>true</code> if (v1 cond v2) or 
   *         <code>false</code> otherwise
   */
  boolean evaluate(double v1, double v2) {
    switch (value) {
    case EQUAL:
      return v1 == v2;
    case NOT_EQUAL:
      return v1 != v2;
    case GREATER:
      return v1 > v2;
    case LESS:
      return v1 < v2;
    case GREATER_EQUAL:
      return v1 >= v2;
    case LESS_EQUAL:
      return v1 <= v2;
    }
    throw  new OPT_OptimizingCompilerException("invalid condition" + this);
  }

  /**
   * Flip the direction of the condition.  Typical use is if you want to
   * change the direction of a branch.
   * Note that this is not the same as calling {@link #flipOperands}.
   */
  OPT_ConditionOperand flipCode () {
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
      case NULL:
        value = NONNULL;
        break;
      case NONNULL:
        value = NULL;
        break;
      case OVERFLOW:
        value = NOT_OVERFLOW;
        break;
      case NOT_OVERFLOW:
        value = OVERFLOW;
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
      case CARRY:
        value = NOT_CARRY;
        break;
      case NOT_CARRY:
        value = CARRY;
        break;
      case UNORDERED:
        value = NOT_UNORDERED;
        break;
      case NOT_UNORDERED:
        value = UNORDERED;
        break;
    }
    return  this;
  }

  /**
   * Change the condition code to allow the order of the operands to be flipped.
   * Note that this is not the same as calling {@link #flipCode}.
   */
  OPT_ConditionOperand flipOperands () {
    switch (value) {
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
    }
    return  this;
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  public String toString () {
    switch (value) {
      case EQUAL:
        return  "==";
      case NOT_EQUAL:
        return  "!=";
      case LESS:
        return  "<";
      case LESS_EQUAL:
        return  "<=";
      case GREATER:
        return  ">";
      case GREATER_EQUAL:
        return  ">=";
      case NULL:
        return  "null";
      case NONNULL:
        return  "nonnull";
      case OVERFLOW:
        return  "overflow";
      case NOT_OVERFLOW:
        return  "not_overflow";
      case HIGHER:
        return  ">U";
      case LOWER:
        return  "<U";
      case HIGHER_EQUAL:
        return  ">=U";
      case LOWER_EQUAL:
        return  "<=U";
      case CARRY:
        return  "carry";
      case NOT_CARRY:
        return  "not_carry";
      case UNORDERED:
        return  "unordered";
      case NOT_UNORDERED:
        return  "not_unordered";
    }
    return  "UNKNOWN";
  }
}



