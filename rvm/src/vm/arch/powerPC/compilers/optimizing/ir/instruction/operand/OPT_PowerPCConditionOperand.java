/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Encodes the BO & BI condition fields for PowerPC
 * 
 * @see OPT_Operand
 * @author by Mauricio Serrano
 */
public final class OPT_PowerPCConditionOperand extends OPT_Operand {
  /**
   * Value of this operand.
   */
  int value;
  static final int ALWAYS = (20 << 5);
  static final int EQUAL = (12 << 5) | 2;
  static final int NOT_EQUAL = (4 << 5) | 2;
  static final int LESS = (12 << 5) | 0;
  static final int GREATER_EQUAL = (4 << 5) | 0;
  static final int GREATER = (12 << 5) | 1;
  static final int LESS_EQUAL = (4 << 5) | 1;
  static final int OVERFLOW = (12 << 5) | 3;
  static final int NOT_OVERFLOW = (4 << 5) | 3;

  /* interpretation for floating-point values */
  static final int UNORDERED = (12 << 5) | 3;
  static final int NOT_UNORDERED = (4 << 5) | 3;

  /* special RVM value */
  static final int NO_THREAD_SWITCH = (4 << 5) | 0;             // same as !geq
  static final int THREAD_SWITCH = (12 << 5) | 0;               // same as less
  // --CTR == 0
  static final int CTRZ = (17 << 5) | 0;
  // --CTR != 0
  static final int CTRNZ = (16 << 5) | 0;
  // (--CTR == 0) & condition
  static final int CTRZ_EQUAL = (10 << 5) | 2;
  static final int CTRZ_NOT_EQUAL = (2 << 5) | 2;
  static final int CTRZ_LESS = (10 << 5) | 0;
  static final int CTRZ_GREATER_EQUAL = (2 << 5) | 0;
  static final int CTRZ_GREATER = (10 << 5) | 1;
  static final int CTRZ_LESS_EQUAL = (2 << 5) | 1;
  static final int CTRZ_OVERFLOW = (10 << 5) | 3;
  static final int CTRZ_NOT_OVERFLOW = (2 << 5) | 3;
  // (--CTR != 0) & condition
  static final int CTRNZ_EQUAL = (8 << 5) | 2;
  static final int CTRNZ_NOT_EQUAL = (0 << 5) | 2;
  static final int CTRNZ_LESS = (8 << 5) | 0;
  static final int CTRNZ_GREATER_EQUAL = (0 << 5) | 0;
  static final int CTRNZ_GREATER = (8 << 5) | 1;
  static final int CTRNZ_LESS_EQUAL = (0 << 5) | 1;
  static final int CTRNZ_OVERFLOW = (8 << 5) | 3;
  static final int CTRNZ_NOT_OVERFLOW = (0 << 5) | 3;

  // TODO: add the things with the CTR register also.
  OPT_PowerPCConditionOperand (int Code) {
    value = Code;
  }

  static OPT_PowerPCConditionOperand EQUAL () {
    return  new OPT_PowerPCConditionOperand(EQUAL);
  }

  static OPT_PowerPCConditionOperand NOT_EQUAL () {
    return  new OPT_PowerPCConditionOperand(NOT_EQUAL);
  }

  static OPT_PowerPCConditionOperand LESS () {
    return  new OPT_PowerPCConditionOperand(LESS);
  }

  static OPT_PowerPCConditionOperand LESS_EQUAL () {
    return  new OPT_PowerPCConditionOperand(LESS_EQUAL);
  }

  static OPT_PowerPCConditionOperand GREATER () {
    return  new OPT_PowerPCConditionOperand(GREATER);
  }

  static OPT_PowerPCConditionOperand GREATER_EQUAL () {
    return  new OPT_PowerPCConditionOperand(GREATER_EQUAL);
  }

  static OPT_PowerPCConditionOperand UNORDERED () {
    return  new OPT_PowerPCConditionOperand(UNORDERED);
  }

  static OPT_PowerPCConditionOperand NO_THREAD_SWITCH () {
    return  new OPT_PowerPCConditionOperand(NO_THREAD_SWITCH);
  }

  static OPT_PowerPCConditionOperand THREAD_SWITCH () {
    return  new OPT_PowerPCConditionOperand(THREAD_SWITCH);
  }

  static OPT_PowerPCConditionOperand get (OPT_ConditionOperand cond) {
    return  new OPT_PowerPCConditionOperand(cond);
  }

  OPT_Operand copy () {
    return  new OPT_PowerPCConditionOperand(value);
  }

  boolean similar (OPT_Operand op) {
    return  (op instanceof OPT_PowerPCConditionOperand) 
        && (((OPT_PowerPCConditionOperand)op).value == value);
  }

  /**
   * flips the direction of the condition
   */
  OPT_PowerPCConditionOperand flipCode () {
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
      throw new OPT_OptimizingCompilerException("Unhandled case in flipCode");
    }
    return  this;
  }

  /**
   * this could be used if you want to flip the order of the operands
   * you will notice that there are some differences
   */
  OPT_PowerPCConditionOperand flipOperands () {
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
    return  this;
  }

  OPT_PowerPCConditionOperand (OPT_ConditionOperand c) {
    translate(c);
  }

  /**
   * translate from OPT_ConditionOperand: used by BURS
   */
  void translate (OPT_ConditionOperand c) {
    switch (c.value) {
      case OPT_ConditionOperand.EQUAL:
        value = EQUAL;
        break;
      case OPT_ConditionOperand.NOT_EQUAL:
        value = NOT_EQUAL;
        break;
      case OPT_ConditionOperand.LESS:
        value = LESS;
        break;
      case OPT_ConditionOperand.LESS_EQUAL:
        value = LESS_EQUAL;
        break;
      case OPT_ConditionOperand.GREATER:
        value = GREATER;
        break;
      case OPT_ConditionOperand.GREATER_EQUAL:
        value = GREATER_EQUAL;
        break;
      case OPT_ConditionOperand.NULL:
        value = EQUAL;
        break;
      case OPT_ConditionOperand.NONNULL:
        value = NOT_EQUAL;
        break;
      case OPT_ConditionOperand.OVERFLOW:
        value = OVERFLOW;
        break;
      case OPT_ConditionOperand.NOT_OVERFLOW:
        value = NOT_OVERFLOW;
        break;
      case OPT_ConditionOperand.HIGHER:
        value = GREATER;
        break;
      case OPT_ConditionOperand.LOWER:
        value = LESS;
        break;
      case OPT_ConditionOperand.HIGHER_EQUAL:
        value = GREATER_EQUAL;
        break;
      case OPT_ConditionOperand.LOWER_EQUAL:
        value = LESS_EQUAL;
        break;
      case OPT_ConditionOperand.CARRY:
        value = OVERFLOW;
        break;
      case OPT_ConditionOperand.NOT_CARRY:
        value = NOT_OVERFLOW;
        break;
      case OPT_ConditionOperand.UNORDERED:
        value = OVERFLOW;
        break;
      case OPT_ConditionOperand.NOT_UNORDERED:
        value = NOT_OVERFLOW;
        break;
    }
  }

  /**
   * Returns the string representation of this operand.
   */
  public String toString () {
    String result = "ppc ";
    if ((value & 0x1C0) == 0)
      result = result + "--ctr!=0 && ";
    if ((value & 0x1C0) == 0x40)
      result = result + "--ctr==0 && ";
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
    return  result + temp;
  }
}



