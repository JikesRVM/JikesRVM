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
  float takenProbability;

  public static final float ALWAYS = 1f;
  public static final float LIKELY = .99f;
  public static final float UNLIKELY = 1f - LIKELY;
  public static final float NEVER = 1f - ALWAYS;

  OPT_BranchProfileOperand(float takenProbability) {
    this.takenProbability = takenProbability;
  }

  OPT_BranchProfileOperand() {
    this.takenProbability = 0.5f;
  }

  static OPT_BranchProfileOperand always() {
    return new OPT_BranchProfileOperand(ALWAYS);
  }

  static OPT_BranchProfileOperand likely() {
    return new OPT_BranchProfileOperand(LIKELY);
  }
  
  static OPT_BranchProfileOperand unlikely() {
    return new OPT_BranchProfileOperand(UNLIKELY);
  }

  static OPT_BranchProfileOperand never() {
    return new OPT_BranchProfileOperand(NEVER);
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
   * Flip the probability (p = 1 - p)
   */
  public OPT_BranchProfileOperand flip() {
    takenProbability = 1f - takenProbability;
    return this;
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




