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
import org.mmtk.harness.lang.compiler.CompiledMethod;
import org.mmtk.harness.lang.runtime.ObjectValue;
import org.mmtk.harness.lang.runtime.PcodeInterpreter;
import org.mmtk.harness.lang.runtime.StackFrame;
import org.mmtk.harness.lang.runtime.Value;
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
  private UnsyncStack<StackFrame> stack = new UnsyncStack<StackFrame>();

  /**
   * The temporary values saved during evaluation.
   */
  private Stack<ObjectValue> temporaries = new Stack<ObjectValue>();

  /**
   * The main program if we're using the compiler
   */
  private final CompiledMethod body;

  /**
   * A source of random numbers (we have one per thread so that we can write
   * deterministic scripts).
   */
  private Random rng = new Random();

  public Env(CompiledMethod body) {
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
    new PcodeInterpreter(this,body).exec();
    end();
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
  @Deprecated
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
  @Deprecated
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
   * @return The per-thread random number generator
   */
  public Random random() {
    return rng;
  }
}
