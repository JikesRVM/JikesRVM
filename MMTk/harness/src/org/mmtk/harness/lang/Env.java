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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.Stack;

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
  private UnsyncStack<StackFrame> stack = new UnsyncStack<StackFrame>();

  /**
   * A source of random numbers (we have one per thread so that we can write
   * deterministic scripts).
   */
  private Random rng = new Random();

  public static void setGcEverySafepoint() {
    gcEverySafepoint = true;
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
  public void dumpThreadRoots(int width, Stack<ObjectReference> roots) {
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
    }
    return super.gcSafePoint();
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
