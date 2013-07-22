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

import org.mmtk.harness.Main;
import org.mmtk.harness.Mutator;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.lang.compiler.CompiledMethod;
import org.mmtk.harness.lang.runtime.ObjectValue;
import org.mmtk.harness.lang.runtime.RuntimeStack;
import org.mmtk.harness.lang.runtime.StackAllocator;
import org.mmtk.harness.lang.runtime.StackFrame;
import org.mmtk.plan.TraceLocal;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.harness.Clock;

/**
 * An execution environment.  This is the language-specific layer
 * over a Mutator.
 */
public class Env extends Mutator {

  private static StackAllocator stackSpace;

  public static void setStackSpace(StackAllocator stackSpace) {
    Env.stackSpace = stackSpace;
  }

  public Env() {

  }

  /**
   * The stack
   */
  private final RuntimeStack stack = new RuntimeStack(stackSpace.alloc(), stackSpace.sizeInBytes());

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
   * Enter a new procedure, pushing a new stack frame.
   * @param callee Compiled method for which to push a frame
   */
  public void pushFrame(CompiledMethod callee) {
    stack.pushFrame(this, callee);
  }

  /**
   * Exit from a procedure, popping the top stack frame.
   */
  public void pop() {
    stack.pop();
  }

  /**
   * @return The frame at the top of the stack.
   */
  public StackFrame top() {
    return stack.top();
  }

  /**
   * @return The current stack
   */
  public Iterable<StackFrame> iterator() {
    return stack;
  }

  @Override
  public void computeThreadRoots(TraceLocal trace) {
    int localCount = 0;
    for (StackFrame frame : stack) {
      localCount += frame.computeRoots(trace);
    }
    Clock.stop();
    Trace.trace(Item.ROOTS, "Locals: %d", localCount);
    Clock.start();
  }

  @Override
  public void prepare() {
    for (StackFrame frame : stack) {
      frame.prepare();
    }
  }

  @Override
  public void release() {
    for (StackFrame frame : stack) {
      frame.release();
    }
  }

  @Override
  public List<ObjectValue> getRoots() {
    List<ObjectValue> roots = new ArrayList<ObjectValue>();
    for (StackFrame frame : stack) {
      roots.addAll(frame.getRoots());
    }
    return roots;
  }

  /**
   * @see org.mmtk.harness.Mutator#getRoots()
   */
  @Override
  public List<Address> getRootAddresses() {
    List<Address> roots = new ArrayList<Address>();
    for (StackFrame frame : stack) {
      roots.addAll(frame.getRootAddresses());
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
   * @see org.mmtk.harness.Mutator#end()
   */
  @Override
  public void end() {
    if (!(expectedThrowable == null)) fail(("Expected exception of class " + expectedThrowable + " not found"));
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
    Clock.stop();
    Trace.trace(Item.EXCEPTION, "Processing uncaught exception %s", e.getClass().getCanonicalName());
    if (e.getClass() == expectedThrowable) {
      System.err.println("Mutator " + context.getId() + " exiting due to expected exception of class " + expectedThrowable);
      Main.exitWithSuccess();
    } else {
      System.err.print("Mutator " + context.getId() + " caused unexpected exception: ");
      e.printStackTrace();
      Main.exitWithFailure();
    }
  }

  /**
   * @return The per-thread random number generator
   */
  public Random random() {
    return rng;
  }

  public void setStackSlot(Address slot, ObjectValue value) {
    setStackSlot(slot,value.getObjectValue());
  }

  public ObjectValue getObjectStackSlot(Address slot) {
    return new ObjectValue(getReferenceStackSlot(slot));
  }
}
