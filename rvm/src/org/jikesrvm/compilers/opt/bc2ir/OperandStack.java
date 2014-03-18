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
package org.jikesrvm.compilers.opt.bc2ir;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.ir.operand.Operand;

/**
 * Simulates the Java stack for abstract interpretation in {@link BC2IR}.<p>
 *
 * This class is intended to be used by a single thread. Methods from this
 * class do not provide any error handling.<p>
 *
 * The total amount of {@link Operand}s that can be accepted by an operand stack
 * is called the capacity of the operand stack. It is determined when the stack is
 * created. The capacity is distinct from the current number of operands on the stack
 * which is called the size of the operand stack.
 */
final class OperandStack {

  private final Operand[] stack;
  private int top;

  /**
   * Creates an operand stack.
   *
   * @param capacity the maximum number of operands that the stack
   *  must be able to hold, must be {@code >= 0}
   */
  OperandStack(int capacity) {
    stack = new Operand[capacity];
    top = 0;
  }

  void push(Operand val) {
//    if (VM.VerifyAssertions) VM._assert(val.instruction == null);
    stack[top++] = val;
  }

  Operand pop() {
    return stack[--top];
  }

  /**
   * Pops the two topmost operands from the stack and discards them.<p>
   *
   * This implements the pop2 bytecode.
   */
  void pop2() {
    pop();
    pop();
  }

  Operand getFromBottom(int pos) {
    return stack[pos];
  }

  Operand getFromTop(int n) {
    return stack[top - n - 1];
  }

  void replaceFromTop(int n, Operand op) {
    if (VM.VerifyAssertions) VM._assert(op.instruction == null);
    stack[top - n - 1] = op;
  }

  /**
   * Swaps the two topmost operands on the stack.<p>
   *
   * This implements the swap bytecode.
   */
  void swap() {
    Operand v1 = pop();
    Operand v2 = pop();
    push(v1);
    push(v2);
  }

  void clear() {
    top = 0;
  }

  /**
   * Returns a deep copy of the stack.<p>
   *
   * The copied stack has copies of the operands from the original stack. The
   * size and capacity of the copied stack are equal to the original stack
   * at the time of the copy.
   *
   * @return a copy of the stack
   */
  OperandStack deepCopy() {
    OperandStack newss = new OperandStack(stack.length);
    newss.top = top;
    for (int i = 0; i < top; i++) {
      // deep copy of stack
      newss.stack[i] = stack[i].copy();
    }
    return newss;
  }

  boolean isEmpty() {
    return (top == 0);
  }

  /**
   * Returns the current size of the stack.
   *
   * @return the current number of operands on the stack
   */
  int getSize() {
    return top;
  }

  /**
   * Returns a new, empty operand stack that has the same capacity
   * as this one.
   * @return a new operand stack
   */
  OperandStack createEmptyOperandStackWithSameCapacity() {
    return new OperandStack(stack.length);
  }


}
