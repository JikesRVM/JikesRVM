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

import java.util.Random;
import java.util.Stack;

import org.mmtk.harness.Mutator;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.plan.TraceLocal;
import org.vmmagic.unboxed.ObjectReference;

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
   * A source of random numbers (we have one per thread so that we can write
   * deterministic scripts).
   */
  private Random rng = new Random();

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
      popTemporary(e.getResult());
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
    Trace.trace(Item.ENV,"push()");
  }

  /**
   * Exit from a procedure, popping the top stack frame.
   */
  public void pop() {
    stack.pop();
    Trace.trace(Item.ENV,"pop()");
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
    int tempCount = 0, localCount = 0;
    for(ObjectValue value : temporaries) {
      Trace.trace(Item.ROOTS, "Tracing root (temporary) %s", value.toString());
      value.traceObject(trace);
      tempCount++;
    }
    for (StackFrame frame : stack) {
      localCount += frame.computeRoots(trace);
    }
    Trace.trace(Item.ROOTS, "Temporaries: %d, locals: %d", tempCount, localCount);
  }

  /**
   * Print the thread roots and add them to a stack for processing.
   */
  @Override
  public void dumpThreadRoots(int width, Stack<ObjectReference> roots) {
    System.err.print("  Temporaries [");
    for(ObjectValue value : temporaries) {
      ObjectReference ref = value.getObjectValue();
      System.err.printf(" %s", Mutator.formatObject(width, ref));
    }
    System.err.println(" ]");
    int frameId = 0;
    for (StackFrame frame : stack) {
      System.err.printf("  Frame %5d [", frameId++);
      frame.dumpRoots(width, roots);
      System.err.println(" ]");
    }
    System.err.println();
  }


  @Override
  public boolean gcSafePoint() {
    if (gcEverySafepoint) {
      gc();
      return true;
    }
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

  /*******************************************************************
   * Utility methods
   */

  /**
   * Evaluate an int expression, type-check and return an int
   * @param env
   * @param doubleAlign2
   * @param message
   * @return
   */
  int evalInt(Expression refCount2, String message) {
    Value refCountVal = refCount2.eval(this);
    check(refCountVal.type() == Type.INT, message);
    int refCountInt = refCountVal.getIntValue();
    gcSafePoint();
    return refCountInt;
  }

  /**
   * Evaluate a boolean expression, type-check and return a boolean
   * @param env
   * @param doubleAlign2
   * @param message
   * @return
   */
  boolean evalBoolVal(Expression doubleAlign2, String message) {
    Value doubleAlignVal = doubleAlign2.eval(this);

    check(doubleAlignVal.type() == Type.BOOLEAN, message);

    boolean doubleAlignBool = doubleAlignVal.getBoolValue();
    gcSafePoint();
    return doubleAlignBool;
  }

  /**
   * @return The per-thread random number generator
   */
  public Random random() {
    return rng;
  }
}
