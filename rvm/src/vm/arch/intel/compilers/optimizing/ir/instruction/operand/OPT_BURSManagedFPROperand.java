/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * An FPR register that BURS is managing.
 * Created by a fld, and then eventually
 * deallocated with some popping alu/store.
 *
 * @author Dave Grove
 */
public final class OPT_BURSManagedFPROperand extends OPT_Operand {
  int regNum;

  OPT_BURSManagedFPROperand(int r) {
    regNum = r;
  }

  /**
   * Returns a copy of the current operand.
   */
  OPT_Operand copy() { 
    return new OPT_BURSManagedFPROperand(regNum);
  }

  /**
   * Returns if this operand is the 'same' as another operand.
   *
   * @param op other operand
   */
  boolean similar(OPT_Operand op) {
    return (op instanceof OPT_BURSManagedFPROperand) && 
      ((OPT_BURSManagedFPROperand)op).regNum == regNum;
  }

  // Returns the string representation of this operand.
  public String toString() {
    return "ST("+regNum+")";
  }

}
