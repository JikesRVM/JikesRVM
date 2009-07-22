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
package org.mmtk.harness.lang;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import org.mmtk.harness.Mutator;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.lang.runtime.ObjectValue;
import org.mmtk.harness.lang.runtime.StackFrame;
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
  private final UnsyncStack<StackFrame> stack = new UnsyncStack<StackFrame>();

  /**
   * A source of random numbers (we have one per thread so that we can write
   * deterministic scripts).
   */
  private final Random rng = new Random();

  /**
   * The type of exception that is expected at the end of execution.
   */
  private Class<?> expectedThrowable;

  /**
   * Enable a CG on every safepoint
   */
  public static void setGcEverySafepoint() {
    gcEverySafepoint = true;
  }

  /**
   * Enter a new procedure, pushing a new stack frame.
   * @param frame Stack frame to push
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
   * @return The frame at the top of the stack.
   */
  public StackFrame top() {
    return stack.peek();
  }

  /**
   * @return The current stack
   */
  public Iterable<StackFrame> stack() {
    return stack;
  }

  /**
   * Compute the thread roots for this mutator.
   */
  @Override
  public void computeThreadRoots(TraceLocal trace) {
    int localCount = 0;
    for (StackFrame frame : stack) {
      localCount += frame.computeRoots(trace);
    }
    Trace.trace(Item.ROOTS, "Locals: %d", localCount);
  }

  /**
   * @see org.mmtk.harness.Mutator#getRoots()
   */
  @Override
  public Collection<ObjectValue> getRoots() {
    List<ObjectValue> roots = new ArrayList<ObjectValue>();
    for (StackFrame frame : stack) {
      roots.addAll(frame.getRoots());
    }
    return roots;
  }

  /**
   * Print the thread roots and add them to a stack for processing.
   */
  @Override
  public Collection<ObjectReference> dumpThreadRoots(int width) {
    int frameId = 0;
    List<ObjectReference> roots = new ArrayList<ObjectReference>();
    for (StackFrame frame : stack) {
      System.err.printf("  Frame %5d [", frameId++);
      roots.addAll(frame.dumpRoots(width));
      System.err.println(" ]");
    }
    System.err.println();
    return roots;
  }


  /**
   * @see org.mmtk.harness.Mutator#gcSafePoint()
   */
  @Override
  public boolean gcSafePoint() {
    if (gcEverySafepoint) {
      gc();
    }
    return super.gcSafePoint();
  }


  /**
   * @see org.mmtk.harness.Mutator#end()
   */
  @Override
  public void end() {
    check(expectedThrowable == null, "Expected exception of class " + expectedThrowable + " not found");
    super.end();
  }

  /**
   * Set an expectation that the execution will exit with a throw of this exception.
   * @param expectedThrowable The expected exception class
   */
  public void setExpectedThrowable(Class<?> expectedThrowable) {
    Trace.trace(Item.EXCEPTION, "Setting expected exception %s", expectedThrowable.getCanonicalName());
    this.expectedThrowable = expectedThrowable;
  }

  /**
   * Mutator-specific handling of uncaught exceptions.  The scheduler calls this when
   * it catches an unhandled exception.
   * @param t Thread object
   * @param e Exception
   */
  public void uncaughtException(Thread t, Throwable e) {
    Trace.trace(Item.EXCEPTION, "Processing uncaught exception %s", e.getClass().getCanonicalName());
    if (e.getClass() == expectedThrowable) {
      System.err.println("Mutator " + context.getId() + " exiting due to expected exception of class " + expectedThrowable);
      System.exit(0);
    } else {
      System.err.print("Mutator " + context.getId() + " caused unexpected exception: ");
      e.printStackTrace();
      System.exit(1);
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
