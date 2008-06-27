/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.harness.lang;

import java.util.Stack;

import org.mmtk.harness.Mutator;
import org.mmtk.plan.TraceLocal;
import org.mmtk.vm.Collection;
import org.mmtk.vm.VM;

/**
 * An execution environment
 */
public class Env extends Mutator {

  private static boolean gcEverySafepoint = false;

  /**
   * The stack
   */
  private Stack<StackFrame> stack = new Stack<StackFrame>();

  /**
   * The temporary values saved during evaluation.
   */
  private Stack<ObjectValue> temporaries = new Stack<ObjectValue>();

  /**
   * The main program
   */
  private final Statement body;

  /**
   * Create an environment with the given main program
   * @param body
   */
  public Env(Statement body) {
    super();
    this.body = body;
  }

  public static void setGcEverySafepoint() {
    gcEverySafepoint = true;
  }

  /**
   * Thread.run()
   */
  @Override
  public void run() {
    begin();
    try {
      body.exec(this);
    } catch (ReturnException e) {
      // Ignore return values on thread exit
    }
    end();
  }

  /**
   * Return the global stack frame.
   */
  public StackFrame global() {
    return stack.get(0);
  }

  /**
   * Enter a new procedure, pushing a new stack frame.
   * @param frame
   */
  public void push(StackFrame frame) {
    stack.push(frame);
    if (Env.TRACE) System.err.println("push()");
  }

  /**
   * Exit from a procedure, popping the top stack frame.
   */
  public void pop() {
    stack.pop();
    if (Env.TRACE) System.err.println("pop()");
  }

  /**
   * The frame at the top of the stack.
   */
  public StackFrame top() {
    return stack.peek();
  }

  /**
   * Compute the thread roots for this mutator.
   */
  @Override
  public void computeThreadRoots(TraceLocal trace) {
    for(ObjectValue value : temporaries) {
      value.traceObject(trace);
    }
    for (StackFrame frame : stack) {
      frame.computeRoots(trace);
    }
  }

  @Override
  public boolean gcSafePoint() {
    if (gcEverySafepoint) VM.collection.triggerCollection(Collection.EXTERNAL_GC_TRIGGER);
    return super.gcSafePoint();
  }



  /**
   * Push a temporary value to avoid GC errors for objects held during expression evaluation.
   *
   * @param value The value to push
   */
  public void pushTemporary(Value value) {
    if (value instanceof ObjectValue) {
      temporaries.push((ObjectValue)value);
    }
  }

  /**
   * Pop the specified temporary.
   *
   * @param value The expected value, to ensure that pushes and pops match.
   */
  public void popTemporary(Value value) {
    if (value instanceof ObjectValue) {
      ObjectValue poppedValue = temporaries.pop();
      check(poppedValue == value, "Invalid temporary stack maintenance");
    }
  }
}
