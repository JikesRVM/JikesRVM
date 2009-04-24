/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.ir.operand;


/**
 *
 * @see Operand
 */
public final class BranchProfileOperand extends Operand {
  public float takenProbability;

  public static final float ALWAYS = 1f;
  public static final float LIKELY = .99f;
  public static final float UNLIKELY = 1f - LIKELY;
  public static final float NEVER = 1f - ALWAYS;

  public BranchProfileOperand(float takenProbability) {
    this.takenProbability = takenProbability;
  }

  public BranchProfileOperand() {
    this.takenProbability = 0.5f;
  }

  public static BranchProfileOperand always() {
    return new BranchProfileOperand(ALWAYS);
  }

  public static BranchProfileOperand likely() {
    return new BranchProfileOperand(LIKELY);
  }

  public static BranchProfileOperand unlikely() {
    return new BranchProfileOperand(UNLIKELY);
  }

  public static BranchProfileOperand never() {
    return new BranchProfileOperand(NEVER);
  }

  /**
   * Returns a copy of this branch operand.
   *
   * @return a copy of this operand
   */
  public Operand copy() {
    return new BranchProfileOperand(takenProbability);
  }

  /**
   * Flip the probability (p = 1 - p)
   */
  public BranchProfileOperand flip() {
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
  public boolean similar(Operand op) {
    return (op instanceof BranchProfileOperand) &&
           (takenProbability == ((BranchProfileOperand) op).takenProbability);
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




