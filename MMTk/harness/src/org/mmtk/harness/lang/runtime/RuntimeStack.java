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
package org.mmtk.harness.lang.runtime;

import java.util.Iterator;

import org.mmtk.harness.lang.Env;
import org.mmtk.harness.lang.UnsyncStack;
import org.mmtk.harness.lang.compiler.CompiledMethod;
import org.vmmagic.unboxed.Address;

/**
 * A concrete stack for the execution of a scripting language frame.
 * <p>
 * Consists of a stack (in the abstract sense) of StackFrames.
 */
public class RuntimeStack implements Iterable<StackFrame> {

  /** The minimum address of this stack. */
  private final Address stackBase;

  /** The maximum address of this stack. */
  private final Address stackLimit;

  /**
   * Current top-of-stack.  The most recent stack frame occupies
   * the bytes below this address.
   */
  private Address top;

  /**
   * @param stackBase The base address of the stack
   * @param sizeInBytes The size in bytes
   */
  public RuntimeStack(Address stackBase, int sizeInBytes) {
    this.stackBase = stackBase;
    this.stackLimit = stackBase.plus(sizeInBytes);
    this.top = stackBase;
  }

  /**
   * The stack
   */
  private final UnsyncStack<StackFrame> stack = new UnsyncStack<StackFrame>();

  /**
   * Enter a new procedure, pushing a new stack frame.
   * @param frame Stack frame to push
   */
  public void push(StackFrame frame) {
    top = top.plus(frame.sizeInBytes());
    assert top.LT(stackLimit);
    stack.push(frame);
  }

  /**
   * Exit from a procedure, popping the top stack frame.
   */
  public void pop() {
    StackFrame frame = stack.pop();
    top = top.minus(frame.sizeInBytes());
    assert top.GE(stackBase);
  }

  /**
   * @return The frame at the top of the stack.
   */
  public StackFrame top() {
    return stack.peek();
  }

  @Override
  public Iterator<StackFrame> iterator() {
    return stack.iterator();
  }

  /**
   * Create a stack frame for the given CompiledMethod
   * @param env The thread-specific runtime environment
   * @param callee The CompiledMethod that executes in this frame
   */
  public void pushFrame(Env env, CompiledMethod callee) {
    StackFrame frame = new StackFrame(env, callee, top);
    push(frame);
  }


}
