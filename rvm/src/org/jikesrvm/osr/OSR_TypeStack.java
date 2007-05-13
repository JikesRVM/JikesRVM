/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002
 */
package org.jikesrvm.osr;

import org.jikesrvm.VM;

/**
 * Utility class used by OSR_BytecodeTraverser.
 */
class OSR_TypeStack {
  private byte[] stack;
  private int top;
  private byte defv;

  public OSR_TypeStack(int depth, byte defv) {
    byte[] stk = new byte[depth];
    for (int i = 0; i < depth; i++) {
      stk[i] = defv;
    }

    this.stack = stk;
    this.top = 0;
    this.defv = defv;
  }

  public OSR_TypeStack(OSR_TypeStack other) {

    int ssize = other.stack.length;
    this.stack = new byte[ssize];
    System.arraycopy(other.stack, 0, this.stack, 0, ssize);
    this.top = other.top;
    this.defv = other.defv;
  }

  public void push(byte v) {
    if (top == stack.length) {
      VM.sysWrite("OSR_TypeStack.push(B) : overflow!\n");
    }
    stack[top++] = v;
  }

  public byte pop() {
    if (top <= 0) {
      VM.sysWrite("OSR_TypeStack.pop() : underflow!\n");
    }
    top--;
    byte v = stack[top];
    stack[top] = defv;

    return v;
  }

  public void pop(int n) {
    int newtop = top - n;

    if (newtop < 0) {
      VM.sysWrite("OSR_TypeStack.pop(I) : underflow!\n");
    }

    for (int i = top - 1; i >= newtop; i--) {
      stack[i] = defv;
    }

    top = newtop;
  }

  public byte peek() {
    return stack[top - 1];
  }

  public byte[] snapshot() {
    return stack;
  }

  public void clear() {
    top = 0;
    for (int i = 0, n = stack.length; i < n; i++) {
      stack[i] = defv;
    }
  }

  public int depth() {
    return top;
  }
}
