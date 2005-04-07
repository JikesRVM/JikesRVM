/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt.ir;

/**
 * Encodes the BO & BI condition fields for PowerPC
 * 
 * @see OPT_Operand
 * @author by Mauricio Serrano
 */
public final class OPT_PowerPCConditionOperand extends OPT_Operand {
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
  public OPT_PowerPCConditionOperand(int Code) {
    value = Code;
  }

  public static OPT_PowerPCConditionOperand EQUAL() {
    return  new OPT_PowerPCConditionOperand(EQUAL);
  }

  public static OPT_PowerPCConditionOperand NOT_EQUAL() {
    return  new OPT_PowerPCConditionOperand(NOT_EQUAL);
  }

  public static OPT_PowerPCConditionOperand LESS() {
    return  new OPT_PowerPCConditionOperand(LESS);
  }

  public static OPT_PowerPCConditionOperand LESS_EQUAL() {
    return  new OPT_PowerPCConditionOperand(LESS_EQUAL);
  }

  public static OPT_PowerPCConditionOperand GREATER() {
    return  new OPT_PowerPCConditionOperand(GREATER);
  }

  public static OPT_PowerPCConditionOperand GREATER_EQUAL() {
    return  new OPT_PowerPCConditionOperand(GREATER_EQUAL);
  }

  public static OPT_PowerPCConditionOperand UNORDERED() {
    return  new OPT_PowerPCConditionOperand(UNORDERED);
  }

  public static OPT_PowerPCConditionOperand get(OPT_ConditionOperand cond) {
    return  new OPT_PowerPCConditionOperand(cond);
  }

  public OPT_Operand copy() {
    return  new OPT_PowerPCConditionOperand(value);
  }

  public boolean similar(OPT_Operand op) {
    return (op instanceof OPT_PowerPCConditionOperand) 
        &&(((OPT_PowerPCConditionOperand)op).value == value);
  }

  /**
   * flips the direction of the condition
   */
  public OPT_PowerPCConditionOperand flipCode() {
    switch(value) {
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
      throw new com.ibm.JikesRVM.opt.OPT_OptimizingCompilerException("Unhandled case in flipCode");
    }
    return  this;
  }

  /**
   * this could be used if you want to flip the order of the operands
   * you will notice that there are some differences
   */
  public OPT_PowerPCConditionOperand flipOperands() {
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

  public OPT_PowerPCConditionOperand(OPT_ConditionOperand c) {
    translate(c);
  }

  /**
   * translate from OPT_ConditionOperand: used by BURS
   */
  public void translate(OPT_ConditionOperand c) {
    switch (c.value) {
    case OPT_ConditionOperand.EQUAL:
    case OPT_ConditionOperand.SAME:
    case OPT_ConditionOperand.CMPL_EQUAL:
      value =  EQUAL;
      break;
    case OPT_ConditionOperand.NOT_EQUAL:
    case OPT_ConditionOperand.NOT_SAME:
    case OPT_ConditionOperand.CMPL_NOT_EQUAL: // Extra unordered test required
      value =  NOT_EQUAL;
      break;
    case OPT_ConditionOperand.LESS:
    case OPT_ConditionOperand.LOWER:
    case OPT_ConditionOperand.CMPG_LESS:
    case OPT_ConditionOperand.CMPL_LESS: // Extra unordered test required
      value =  LESS;
      break;
    case OPT_ConditionOperand.LESS_EQUAL:
    case OPT_ConditionOperand.LOWER_EQUAL:
    case OPT_ConditionOperand.CMPG_LESS_EQUAL:
    case OPT_ConditionOperand.CMPL_LESS_EQUAL: // Extra unordered test required
      value =  LESS_EQUAL;
      break;
    case OPT_ConditionOperand.GREATER:
    case OPT_ConditionOperand.HIGHER:
    case OPT_ConditionOperand.CMPL_GREATER:
    case OPT_ConditionOperand.CMPG_GREATER: // Extra unordered test required
      value =  GREATER;
      break;
    case OPT_ConditionOperand.GREATER_EQUAL:
    case OPT_ConditionOperand.HIGHER_EQUAL:
    case OPT_ConditionOperand.CMPL_GREATER_EQUAL:
    case OPT_ConditionOperand.CMPG_GREATER_EQUAL: // Extra unordered test required
      value =  GREATER_EQUAL;
      break;
    default:
      com.ibm.JikesRVM.opt.OPT_OptimizingCompilerException.UNREACHABLE();
    }
  }

  /**
   * Returns the string representation of this operand.
   */
  public String toString() {
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



