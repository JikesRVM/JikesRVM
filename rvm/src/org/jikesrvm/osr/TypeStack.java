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
package org.jikesrvm.osr;

import org.jikesrvm.VM;

/**
 * Utility class used by BytecodeTraverser.
 */
class TypeStack {
  private final byte[] stack;
  private int top;
  private final byte defv;

  TypeStack(int depth, byte defv) {
    byte[] stk = new byte[depth];
    for (int i = 0; i < depth; i++) {
      stk[i] = defv;
    }

    this.stack = stk;
    this.top = 0;
    this.defv = defv;
  }

  TypeStack(TypeStack other) {
    int ssize = other.stack.length;
    this.stack = new byte[ssize];
    System.arraycopy(other.stack, 0, this.stack, 0, ssize);
    this.top = other.top;
    this.defv = other.defv;
  }

  void push(byte v) {
    if (top == stack.length) {
      VM.sysWrite("TypeStack.push(B) : overflow!\n");
    }
    stack[top++] = v;
  }

  byte pop() {
    if (top <= 0) {
      VM.sysWrite("TypeStack.pop() : underflow!\n");
    }
    top--;
    byte v = stack[top];
    stack[top] = defv;

    return v;
  }

  void pop(int n) {
    int newtop = top - n;

    if (newtop < 0) {
      VM.sysWrite("TypeStack.pop(I) : underflow!\n");
    }

    for (int i = top - 1; i >= newtop; i--) {
      stack[i] = defv;
    }

    top = newtop;
  }

  byte peek() {
    return stack[top - 1];
  }

  byte[] snapshot() {
    return stack;
  }

  void clear() {
    top = 0;
    for (int i = 0, n = stack.length; i < n; i++) {
      stack[i] = defv;
    }
  }

  int depth() {
    return top;
  }
}
