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
package org.mmtk.harness;

import java.util.ArrayList;

import org.mmtk.harness.lang.Env;
import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.vm.ActivePlan;
import org.mmtk.harness.vm.ObjectModel;
import org.mmtk.plan.MutatorContext;
import org.mmtk.plan.Plan;
import org.mmtk.plan.TraceLocal;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Offset;

/**
 * This class represents a mutator thread that has memory managed by MMTk.
 *
 * From within the context of this thread it is possible to call the muXXX methods
 * to test MMTk.
 *
 * To get the current Mutator (from a context where this is valid) it is possible to
 * call Mutator.current().
 *
 * Note that as soon as the mutator is created it is considered active. This means
 * that a GC can not occur unless you execute commands on the mutator (or muEnd it).
 */
public class Mutator extends MMTkThread {
  /** Debugging */
  public static final boolean TRACE = false;

  /** Registered mutators */
  protected static ArrayList<Mutator> mutators = new ArrayList<Mutator>();

  /**
   * Get a mutator by id.
   */
  public static Mutator get(int id) {
    return mutators.get(id);
  }

  /**
   * Get the currently executing mutator.
   */
  public static Mutator current() {
    assert Thread.currentThread() instanceof Mutator  : "Current thread does is not a Mutator";
    return (Mutator)Thread.currentThread();
  }

  /**
   * Return the number of mutators that have been created.
   */
  public static int count() {
    return mutators.size();
  }

  /**
   * Register a mutator, returning the allocated id.
   */
  public static synchronized int register(MutatorContext context) {
    int id = mutators.size();
    mutators.add(null);
    return id;
  }

  /** Is this thread out of memory if the gc cannot free memory */
  private boolean outOfMemory;

  /** Get the out of memory status */
  public boolean isOutOfMemory() {
    return outOfMemory;
  }

  /** Set the out of memory status */
  public void setOutOfMemory(boolean value) {
    outOfMemory = value;
  }

  /** The number of collection attempts this thread has had since allocation succeeded */
  private int collectionAttempts;

  /** Get the number of collection attempts */
  public int getCollectionAttempts() {
    return collectionAttempts;
  }

  /** Report a collection attempt */
  public void reportCollectionAttempt() {
    collectionAttempts++;
  }

  /** Clear the collection attempts */
  public void clearCollectionAttempts() {
    collectionAttempts = 0;
  }

  /** Was the last failure a physical allocation failure (rather than a budget failure) */
  private boolean physicalAllocationFailure;

  /** Was the last failure a physical allocation failure */
  public boolean isPhysicalAllocationFailure() {
    return physicalAllocationFailure;
  }

  /** Set if last failure a physical allocation failure */
  public void setPhysicalAllocationFailure(boolean value) {
    physicalAllocationFailure = value;
  }

  /** The MMTk MutatorContext for this mutator */
  protected final MutatorContext context;

  /**
   * Create a mutator thread, specifying an (optional) entry point and initial local variable map.
   *
   * @param entryPoint The entryPoint.
   * @param locals The local variable map.
   */
  public Mutator(Runnable entryPoint) {
    super(entryPoint);
    try {
      String prefix = Harness.plan.getValue();
      this.context = (MutatorContext)Class.forName(prefix + "Mutator").newInstance();
    } catch (Exception ex) {
      throw new RuntimeException("Could not create Mutator", ex);
    }
    mutators.set(context.getId(), this);
    setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
      public void uncaughtException(Thread t, Throwable e) {
        if (e instanceof Mutator.OutOfMemory) {
          System.err.println("Mutator " + context.getId() + " exiting due to OutOfMemory");
          end();
        } else {
          System.err.print("Mutator " + context.getId() + " caused unexpected exception: ");
          e.printStackTrace();
          System.exit(1);
        }
      }
    });
  }

  /**
   * Create a new mutator thread. Intended to be used for subclasses implementing a run() method.
   */
  protected Mutator() {
    this(null);
  }

  /**
   * Return the MMTk MutatorContext for this mutator.
   */
  public MutatorContext getContext() {
    return context;
  }

  /**
   * Compute the thread roots for this mutator.
   */
  public void computeThreadRoots(TraceLocal trace) {
    // Nothing to do for the default mutator
  }

  /**
   * A gc safe point for the mutator.
   */
  public boolean gcSafePoint() {
    if (Collector.gcTriggered()) {
      waitForGC();
      return true;
    }
    return false;
  }

  /**
   * An out of memory error originating from within MMTk.
   *
   * Tests that try to exercise out of memory conditions can catch this exception.
   */
  public static class OutOfMemory extends RuntimeException {
    public static final long serialVersionUID = 1;
  }

  /**
   * Object used for synchronizing the number of mutators waiting for a gc.
   */
  private static Object count = new Object();

  /**
   * The number of mutators waiting for a collection to proceed.
   */
  private static int mutatorsWaitingForGC;

  /**
   * The number of mutators currently executing in the system.
   */
  private static int activeMutators;

  /**
   * Mark a mutator as currently active. If a GC is currently in process we must
   * wait for it to finish.
   */
  protected static void begin() {
    synchronized (count) {
      if (!allWaitingForGC()) {
        activeMutators++;
        return;
      }
      mutatorsWaitingForGC++;
    }
    Collector.waitForGC(false);
    synchronized (count) {
      mutatorsWaitingForGC--;
      activeMutators++;
    }
  }

  /**
   * A mutator is creating a new mutator and calling begin on its behalf.
   * This simplfies the logic and is guaranteed not to block for GC.
   */
  public void beginChild() {
    synchronized (count) {
      if (!allWaitingForGC()) {
        activeMutators++;
        return;
      }
    }
    notReached();
  }

  /**
   * Mark a mutator as no longer active. If a GC has been triggered we must ensure
   * that it proceeds before we deactivate.
   */
  protected void end() {
    boolean lastToGC;
    synchronized (count) {
      lastToGC = (mutatorsWaitingForGC == (activeMutators - 1));
      if (!lastToGC) {
        activeMutators--;
        return;
      }
      mutatorsWaitingForGC++;
    }
    Collector.waitForGC(lastToGC);
    synchronized (count) {
        mutatorsWaitingForGC--;
        activeMutators--;
    }
  }

  /**
   * Are all active mutators waiting for GC?
   */
  public static boolean allWaitingForGC() {
    return mutatorsWaitingForGC == activeMutators;
  }

  /**
   * Cause the current thread to wait for a triggered GC to proceed.
   */
  public static void waitForGC() {
    boolean allWaiting;
    synchronized (count) {
      mutatorsWaitingForGC++;
      allWaiting = allWaitingForGC();
    }
    Collector.waitForGC(allWaiting);
    synchronized (count) {
        mutatorsWaitingForGC--;
    }
  }

  /**
   * Fail during execution.
   * @param failMessage
   */
  public void fail(String failMessage) {
    check(false, failMessage);
  }

  /**
   * Print a message that this code path should be impossible and exit.
   */
  public void notReached() {
    check(false, "Unreachable code reached!");
  }

  /**
   * Assert that the given condition is true or print the failure message.
   */
  public void check(boolean condition, String failMessage) {
    if (!condition) throw new RuntimeException(failMessage);
  }

  /**
   * Store a value into the data field of an object.
   *
   * @param object The object to store the field of.
   * @param index The field index.
   * @param value The value to store.
   */
  public void storeDataField(ObjectReference object, int index, int value) {
    int limit = ObjectModel.getDataCount(object);
    check(!object.isNull(), "Object can not be null");
    check(index >= 0, "Index must be non-negative");
    check(index < limit, "Index out of bounds");

    Address ref = ObjectModel.getDataSlot(object, index);
    ref.store(value);
    if (Env.TRACE) System.err.println(object + ".data[" + index + "] = " + value);
  }

  /**
   * Store a value into a reference field of an object.
   *
   * @param object The object to store the field of.
   * @param index The field index.
   * @param value The value to store.
   */
  public void storeReferenceField(ObjectReference object, int index, ObjectReference value) {
    int limit = ObjectModel.getRefs(object);
    check(!object.isNull(), "Object can not be null");
    check(index >= 0, "Index must be non-negative");
    check(index < limit, "Index out of bounds");

    Address referenceSlot = ObjectModel.getRefSlot(object, index);
    if (ActivePlan.constraints.needsWriteBarrier()) {
      context.writeBarrier(object, referenceSlot, value, Offset.zero(), 0, Plan.AASTORE_WRITE_BARRIER);
    } else {
      referenceSlot.store(value);
    }
    if (Env.TRACE) System.err.println("[" + object + "].object[" + index + "] = " + value);
  }

  /**
   * Load and return the value of a data field of an object.
   *
   * @param object The object to load the field of.
   * @param index The field index.
   */
  public int loadDataField(ObjectReference object, int index) {
    int limit = ObjectModel.getDataCount(object);
    check(!object.isNull(), "Object can not be null");
    check(index >= 0, "Index must be non-negative");
    check(index < limit, "Index out of bounds");

    Address dataSlot = ObjectModel.getDataSlot(object, index);
    int result = dataSlot.loadInt();
    if (Env.TRACE) System.err.println("[" + object + "].int[" + index + "] returned [" + result + "]");
    return result;
  }

  /**
   * Load and return the value of a reference field of an object.
   *
   * @param object The object to load the field of.
   * @param index The field index.
   */
  public ObjectReference loadReferenceField(ObjectReference object, int index) {
    int limit = ObjectModel.getRefs(object);
    check(!object.isNull(), "Object can not be null");
    check(index >= 0, "Index must be non-negative");
    check(index < limit, "Index out of bounds");

    Address referenceSlot = ObjectModel.getRefSlot(object, index);
    ObjectReference result;
    if (ActivePlan.constraints.needsReadBarrier()) {
      result = context.readBarrier(object, referenceSlot, Offset.zero(), 0, Plan.AASTORE_WRITE_BARRIER);
    } else {
      result = referenceSlot.loadObjectReference();
    }
    if (Env.TRACE) System.err.println(object + ".ref[" + index + "] returned [" + result + "]");
    return result;
  }

  /**
   * Get the hash code for the given object.
   */
  public int hash(ObjectReference object) {
    check(!object.isNull(), "Object can not be null");
    int result = ObjectModel.getHashCode(object);
    if (Env.TRACE) System.err.println("hash(" + object + ") returned [" + result + "]");
    return result;
  }

  /**
   * Allocate an object and return a reference to it.
   *
   * @param refCount The number of reference fields.
   * @param dataCount The number of data fields.
   * @param doubleAlign Is this an 8 byte aligned object?
   * @return The object reference.
   */
  public ObjectReference alloc(int refCount, int dataCount, boolean doubleAlign) {
    check(refCount >= 0, "Non-negative reference field count required");
    check(dataCount >= 0, "Non-negative data field count required");
    ObjectReference result = ObjectModel.allocateObject(context, refCount, dataCount, doubleAlign);
    Trace.trace(Item.ALLOC,"alloc(" + refCount + ", " + dataCount + ", " + doubleAlign + ") returned [" + result + "]");
    return result;
  }
}
