/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;
import com.ibm.JikesRVM.*;
/**
 * An IA32 condition operand
 *
 * @author Dave Grove
 */
public final class OPT_IA32ConditionOperand extends OPT_Operand 
  implements VM_AssemblerConstants {
  
  /**
   * Value of this operand (one of the ConditionCode constants operands 
   * defined in VM_AssemblerConstants)
   */
  byte value;

  /**
   * Returns a copy of the current operand.
   */
  public OPT_Operand copy() { 
    return new OPT_IA32ConditionOperand(value);
  }

  /**
   * Returns if this operand is the 'same' as another operand.
   *
   * @param op other operand
   */
  boolean similar(OPT_Operand op) {
    return (op instanceof OPT_IA32ConditionOperand) && 
      ((OPT_IA32ConditionOperand)op).value == value;
  }

  /**
   * flip the direction of the condition (return this, mutated to flip value)
   */
  OPT_IA32ConditionOperand flipCode() { 
    switch (value) {
    case O:   value =  NO; break;
    case NO:  value =   O; break;
    case LLT: value = LGE; break;
    case LGE: value = LLT; break;
    case EQ:  value =  NE; break;
    case NE:  value =  EQ; break;
    case LLE: value = LGT; break;
    case LGT: value = LLE; break;
    case S:   value =  NS; break;
    case NS:  value =   S; break;
    case PE:  value =  PO; break;
    case PO:  value =  PE; break;
    case LT:  value =  GE; break;
    case GE:  value =  LT; break;
    case LE:  value =  GT; break;
    case GT:  value =  LE; break;
    default:
      OPT_OptimizingCompilerException.UNREACHABLE();
    }
    return this;
  }

  /**
   * change the condition when operands are flipped 
   * (return this mutated to change value)
   */
  OPT_IA32ConditionOperand flipOperands() {
    switch (value) {
    case LLT: value = LGT; break;
    case LGE: value = LLE; break;
    case LLE: value = LGE; break;
    case LGT: value = LLT; break;
    case LT:  value =  GT; break;
    case GE:  value =  LE; break;
    case LE:  value =  GE; break;
    case GT:  value =  LT; break;
    default:
      OPT_OptimizingCompilerException.TODO();
    }
    return this;
  }      

  /**
   * Construct the IA32 Condition Operand that corresponds to the 
   * argument ConditionOperand
   */
  OPT_IA32ConditionOperand(OPT_ConditionOperand c) {
    translate(c);
  }

  static OPT_IA32ConditionOperand EQ() {
    return new OPT_IA32ConditionOperand(EQ);
  }
  static OPT_IA32ConditionOperand NE() {
    return new OPT_IA32ConditionOperand(NE);
  }
  static OPT_IA32ConditionOperand LT() {
    return new OPT_IA32ConditionOperand(LT);
  }
  static OPT_IA32ConditionOperand LE() {
    return new OPT_IA32ConditionOperand(LE);
  }
  static OPT_IA32ConditionOperand GT() {
    return new OPT_IA32ConditionOperand(GT);
  }
  static OPT_IA32ConditionOperand GE() {
    return new OPT_IA32ConditionOperand(GE);
  }
  static OPT_IA32ConditionOperand O() {
    return new OPT_IA32ConditionOperand(O);
  }
  static OPT_IA32ConditionOperand NO() {
    return new OPT_IA32ConditionOperand(NO);
  }
  static OPT_IA32ConditionOperand LGT() {
    return new OPT_IA32ConditionOperand(LGT);
  }
  static OPT_IA32ConditionOperand LLT() {
    return new OPT_IA32ConditionOperand(LLT);
  }
  static OPT_IA32ConditionOperand LGE() {
    return new OPT_IA32ConditionOperand(LGE);
  }
  static OPT_IA32ConditionOperand LLE() {
    return new OPT_IA32ConditionOperand(LLE);
  }
  static OPT_IA32ConditionOperand PE() {
    return new OPT_IA32ConditionOperand(PE);
  }
  static OPT_IA32ConditionOperand PO() {
    return new OPT_IA32ConditionOperand(PO);
  }

  private OPT_IA32ConditionOperand(byte c) {
    value = c;
  }

  // translate from OPT_ConditionOperand: used during LIR => MIR translation
  void translate(OPT_ConditionOperand c) {
     switch(c.value) {
     case OPT_ConditionOperand.EQUAL:         value =  EQ; break;
     case OPT_ConditionOperand.NOT_EQUAL:     value =  NE; break;
     case OPT_ConditionOperand.LESS:          value =  LT; break;
     case OPT_ConditionOperand.LESS_EQUAL:    value =  LE; break;
     case OPT_ConditionOperand.GREATER:       value =  GT; break;
     case OPT_ConditionOperand.GREATER_EQUAL: value =  GE; break;
     case OPT_ConditionOperand.OVERFLOW:      value =   O; break;
     case OPT_ConditionOperand.NOT_OVERFLOW:  value =  NO; break;
     case OPT_ConditionOperand.HIGHER:        value = LGT; break;
     case OPT_ConditionOperand.LOWER:         value = LLT; break;
     case OPT_ConditionOperand.HIGHER_EQUAL:  value = LGE; break;
     case OPT_ConditionOperand.LOWER_EQUAL:   value = LLE; break;
     default:
       OPT_OptimizingCompilerException.TODO();
     }
  }

  // Returns the string representation of this operand.
  public String toString() {
    return CONDITION[value];
  }

}
