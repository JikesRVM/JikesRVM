/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;

/**
 * Represents a branch target.
 *
 * @see OPT_Operand
 * @author John Whaley
 */
public final class OPT_BranchOperand extends OPT_Operand {

  /**
   * Target of this branch.
   */
  OPT_Instruction target;

  /**
   * Construct a new branch operand with the given target.
   * <STRONG> Precondition: </STRONG> targ must be a Label instruction.
   * 
   * @param targ target of branch
   */
  OPT_BranchOperand(OPT_Instruction targ) {
    if (VM.VerifyAssertions) VM.assert(Label.conforms(targ));
    target = targ;
  }

  /**
   * Returns a copy of this branch operand.
   * 
   * @return a copy of this operand
   */
  OPT_Operand copy() {
    return new OPT_BranchOperand(target);
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
           (target == ((OPT_BranchOperand)op).target);
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  public String toString() {
    return "LABEL"+Label.getBlock(target).block.getNumber(); 
  }

}




