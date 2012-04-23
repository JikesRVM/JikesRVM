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
package org.jikesrvm.compilers.opt.ssa;

import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.util.BitVector;

/**
 * An HeapVariable represents a heap variable for heap array SSA form
 */
public class HeapVariable<T> {
  /**
   * a unique identifier for this heap variable among all heap variables
   * with this type.
   */
  private final int number;
  /**
   * a bit vector representing the basic blocks that write to this
   * variable
   */
  private final BitVector definedIn;
  /**
   * The type of this heap variable.  Must be either a
   * TypeReference, FieldReference, RVMField or a String
   */
  private final T type;

  /**
   * Create a new Heap variable of a given type, with a given number.
   *
   * @param type a FieldReference or TypeReference object, naming the type of this
   *              heap
   * @param number second part of the name of this heap variable
   * @param ir the governing IR
   */
  public HeapVariable(T type, int number, IR ir) {
    this.type = type;
    this.number = number;
    definedIn = new BitVector(ir.getMaxBasicBlockNumber() + 1);
  }

  /**
   * Return a number that uniquely identifies this heap variable, among
   * all the heap variables with the same type.
   * @return the number
   */
  public int getNumber() {
    return number;
  }

  /**
   * Return the type representing this heap object.
   * @return either a TypeReference, FieldReference, RVMField or
   * String object
   */
  public T getHeapType() {
    return type;
  }

  /**
   * Is the this the exception heap type?
   * @return true if the heap represents exceptions
   */
  public boolean isExceptionHeapType() {
    return type == SSADictionary.exceptionState;
  }

  /**
   * Return a bit vector that represents the basic blocks that define
   * this heap variable.
   * @return a bit vector that represents the basic blocks that define
   * this heap variable.
   */
  public BitVector getDefBlocks() {
    return definedIn;
  }

  /**
   * Note that this heap variable is defined in a given basic block.
   * @param b a basic block that defines this heap variable
   */
  public void registerDef(BasicBlock b) {
    definedIn.set(b.getNumber());
  }

  /**
   * Return a String representation of this variable
   * @return a String representation of this variable
   */
  @Override
  public String toString() {
    return "HEAP<" + type + ">" + number;
  }

  /**
   * Is this heap variable exposed on procedure entry?
   * <p> Equivalently: is the number = zero?
   * @return true or false
   */
  public boolean isExposedOnEntry() {
    return (number == 0);
  }
}
