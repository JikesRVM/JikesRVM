/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt.ir;

/**
 * Represents a basic block (used in LABEL and BBEND instructions)
 *
 * @see OPT_Operand
 * @author Dave Grove 
 */
public final class OPT_BasicBlockOperand extends OPT_Operand {

  /**
   * The basic block
   */
  public OPT_BasicBlock block;

  /**
   * Construct a new basic block operand with the given block.
   * 
   * @param b the basic block
   */
  public OPT_BasicBlockOperand(OPT_BasicBlock b) {
    block = b;
  }

  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   * 
   * @return a copy of <code>this</code>
   */
  public OPT_Operand copy() {
    return new OPT_BasicBlockOperand(block);
  }


  /**
   * Are two operands semantically equivalent?
   *
   * @param op other operand
   * @return   <code>true</code> if <code>this</code> and <code>op</code>
   *           are semantically equivalent or <code>false</code> 
   *           if they are not.
   */
  public boolean similar(OPT_Operand op) {
    return (op instanceof OPT_BasicBlockOperand) &&
           (block == ((OPT_BasicBlockOperand)op).block);
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  public String toString() {
    return block.toString();
  }
}
