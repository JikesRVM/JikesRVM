/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;

/**
 *
 * @see OPT_Operand
 * @author Matthew Arnold
 */
public final class OPT_BranchProfileOperand extends OPT_Operand {
  double takenProbability;

  OPT_BranchProfileOperand(double takenProbability) {
    this.takenProbability = takenProbability;
  }

  OPT_BranchProfileOperand() {
    this.takenProbability = 0.5;
  }

  static OPT_BranchProfileOperand likely() {
    return new OPT_BranchProfileOperand(0.99);
  }
  
  static OPT_BranchProfileOperand unlikely() {
    return new OPT_BranchProfileOperand(0.01);
  }


  /**
   * Returns a copy of this branch operand.
   * 
   * @return a copy of this operand
   */
  OPT_Operand copy() {
    return new OPT_BranchProfileOperand(takenProbability);
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
    return (op instanceof OPT_BranchOperand) &&
      (takenProbability == 
       ((OPT_BranchProfileOperand)op).takenProbability);
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  public String toString() {
    return "Probability: " + takenProbability;
  }

}




