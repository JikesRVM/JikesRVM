/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Encodes the T0 field for trap operations 
 * 
 * @see OPT_Operand
 * @author by Mauricio Serrano
 */
public final class OPT_PowerPCTrapOperand extends OPT_Operand {
  /**
   * Value of this operand.
   */
  int value;
  // see PowerPC BOOK
  static final int ALWAYS = 31;
  static final int EQUAL = 4;
  static final int NOT_EQUAL = 24;
  static final int LESS = 16;
  static final int GREATER_EQUAL = 12;
  static final int GREATER = 8;
  static final int LESS_EQUAL = 20;
  static final int HIGHER = 1;
  static final int LOWER = 2;
  static final int HIGHER_EQUAL = 5;
  static final int LOWER_EQUAL = 6;
  static final int NOT_SAME = 3;
  static final int SAME = 4;

  private OPT_PowerPCTrapOperand (int Code) {
    value = Code;
  }

  static OPT_PowerPCTrapOperand LESS () {
    return  new OPT_PowerPCTrapOperand(LESS);
  }

  static OPT_PowerPCTrapOperand LOWER () {
    return  new OPT_PowerPCTrapOperand(LOWER);
  }

  static OPT_PowerPCTrapOperand ALWAYS () {
    return  new OPT_PowerPCTrapOperand(ALWAYS);
  }

  OPT_Operand copy () {
    return  new OPT_PowerPCTrapOperand(value);
  }

  boolean similar (OPT_Operand op) {
    return  (op instanceof OPT_PowerPCTrapOperand) && 
        (((OPT_ConditionOperand)op).value == value);
  }

  /**
   * flips the direction of the condition
   */
  OPT_PowerPCTrapOperand flipCode () {
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
    return  this;
  }

  /**
   * this could be used if you want to flip the order of the operands
   * you will notice that there are some differences
   */
  OPT_PowerPCTrapOperand flipOperands () {
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
    return  this;
  }

  OPT_PowerPCTrapOperand (OPT_ConditionOperand c) {
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
      case OPT_ConditionOperand.HIGHER:
        value = HIGHER;
        break;
      case OPT_ConditionOperand.LOWER:
        value = LOWER;
        break;
      case OPT_ConditionOperand.HIGHER_EQUAL:
        value = HIGHER_EQUAL;
        break;
      case OPT_ConditionOperand.LOWER_EQUAL:
        value = LOWER_EQUAL;
        break;
      case OPT_ConditionOperand.SAME:
        value = SAME;
        break;
      case OPT_ConditionOperand.NOT_SAME:
        value = NOT_SAME;
        break;
    }
  }

  /**
   * Returns the string representation of this operand.
   */
  public String toString () {
    String result = "ppc trap ";
    switch (value) {
      case EQUAL:
        return  result + "==";
      case NOT_EQUAL:
        return  result + "!=";
      case LESS:
        return  result + "<";
      case LESS_EQUAL:
        return  result + "<=";
      case GREATER:
        return  result + ">";
      case GREATER_EQUAL:
        return  result + ">=";
      case HIGHER:
        return  result + ">U";
      case LOWER:
        return  result + "<U";
      case HIGHER_EQUAL:
        return  result + ">=U";
      case LOWER_EQUAL:
        return  result + "<=U";
      case NOT_SAME:
        return  result + "U!=";
      case ALWAYS:
        return  result + "always";
    }
    return  "UNKNOWN";
  }
}



