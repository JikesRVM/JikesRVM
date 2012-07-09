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

import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ssa.HeapVariable;

/**
 * Represents a heap variable for instructions in Heap Array SSA form.
 *
 * @see Operand
 * @see HeapVariable
 */
public final class HeapOperand<T> extends Operand {

  /**
   * The heap variable corresponding to this operand.
   */
  public final HeapVariable<T> value;

  /**
   * Return  the heap variable corresponding to this operand.
   * @return the heap variable corresponding to this operand.
   */
  public HeapVariable<T> getHeapVariable() {
    return value;
  }

  /**
   * Return the number of the heap variable corresponding to this
   * operand.
   * @return the number of the heap variable corresponding to this
   * operand.
   */
  public int getNumber() {
    return value.getNumber();
  }

  /**
   * Return the type corresponding to the heap variable associated with
   * this operand.
   * @return the type corresponding to the heap variable associated with
   * this operand.
   */
  public T getHeapType() {
    return value.getHeapType();
  }

  /**
   * Construct an operand corresponding to a heap variable.
   * @param   heap the heap variable corresponding to this operand.
   */
  public HeapOperand(HeapVariable<T> heap) {
    value = heap;
  }

  /**
   * Construct a new heap operand associated with the same heap variable as
   * this operand
   *
   * @return a new heap operand associated with the same heap variable as
   * this operand
   */
  @Override
  public HeapOperand<T> copy() {
    return new HeapOperand<T>(value);
  }

  /**
   * Does this operand correspond to the same heap variable as another
   * heap operand?
   *
   * @param op the second operand to compare with
   * @return {@code true} or {@code false}
   */
  @Override
  public boolean similar(Operand op) {
    if (!(op instanceof HeapOperand<?>)) {
      return false;
    }
    HeapOperand<?> h = (HeapOperand<?>) op;
    return (h.value == value);
  }

  /**
   * Return a string representation of this operand.
   * @return a string representation of this operand.
   */
  @Override
  public String toString() {
    return value.toString();
  }

  /**
   * Associate this operand with a given instruction.
   * @param s the associated instruction
   */
  public void setInstruction(Instruction s) {
    this.instruction = s;
  }

  /**
   * Return the instruction associated with this operand.
   * @return the instruction associated with this operand.
   */
  public Instruction getInstruction() {
    return instruction;
  }
}
