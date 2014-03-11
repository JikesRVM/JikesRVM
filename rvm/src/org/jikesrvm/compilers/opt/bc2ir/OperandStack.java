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
 * class do not provide any error handling.
 */
final class OperandStack {

  private final Operand[] stack;
  private int top;

  OperandStack(int size) {
    stack = new Operand[size];
    top = 0;
  }

  void push(Operand val) {
//    if (VM.VerifyAssertions) VM._assert(val.instruction == null);
    stack[top++] = val;
  }

  Operand pop() {
    return stack[--top];
  }

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

  void swap() {
    Operand v1 = pop();
    Operand v2 = pop();
    push(v1);
    push(v2);
  }

  void clear() {
    top = 0;
  }

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

  int getSize() {
    return top;
  }

  int getCapacity() {
    return stack.length;
  }


}
