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
package org.mmtk.harness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.lang.runtime.AllocationSite;
import org.mmtk.harness.lang.runtime.ObjectValue;
import org.mmtk.harness.scheduler.Scheduler;
import org.mmtk.harness.vm.ActivePlan;
import org.mmtk.harness.vm.ObjectModel;
import org.mmtk.plan.MutatorContext;
import org.mmtk.plan.Plan;
import org.mmtk.plan.TraceLocal;
import org.mmtk.vm.Collection;
import org.mmtk.vm.VM;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;

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
public abstract class Mutator {
  private static boolean gcEveryWB = false;

  /**
   * Force garbage collection on every write barrier invocation
   */
  public static void setGcEveryWB() {
    gcEveryWB = true;
  }

  /** Registered mutators */
  protected static final ArrayList<Mutator> mutators = new ArrayList<Mutator>();

  /**
   * @return The active mutators
   */
  public static java.util.Collection<Mutator> getMutators() {
    return Collections.unmodifiableCollection(mutators);
  }

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
    return Scheduler.currentMutator();
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
  public static synchronized void register(MutatorContext context) {
    int id = mutators.size();
    mutators.add(null);
    context.initMutator(id);
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
   * The type of exception that is expected at the end of execution.
   */
  private Class<?> expectedThrowable;

  /**
   * Set an expectation that the execution will exit with a throw of this exception.
   */
  public void setExpectedThrowable(Class<?> expectedThrowable) {
    this.expectedThrowable = expectedThrowable;
  }

  public Mutator() {
    try {
      String prefix = Harness.plan.getValue();
      this.context = (MutatorContext)Class.forName(prefix + "Mutator").newInstance();
      register(this.context);
    } catch (Exception ex) {
      throw new RuntimeException("Could not create Mutator", ex);
    }
  }

  public void begin() {
    mutators.set(context.getId(), this);
  }

  /**
   * Mutator-specific handling of uncaught exceptions.
   * @param t
   * @param e
   */
  public void uncaughtException(Thread t, Throwable e) {
    if (e.getClass() == expectedThrowable) {
      System.err.println("Mutator " + context.getId() + " exiting due to expected exception of class " + expectedThrowable);
      expectedThrowable = null;
      end();
    } else {
      System.err.print("Mutator " + context.getId() + " caused unexpected exception: ");
      e.printStackTrace();
      System.exit(1);
    }
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
   * Return the roots
   */
  public abstract Iterable<ObjectValue> getRoots();

  /**
   * Print the thread roots and add them to a stack for processing.
   */
  public void dumpThreadRoots(int width, Stack<ObjectReference> roots) {
    // Nothing to do for the default mutator
  }

  /**
   * Print the thread roots and add them to a stack for processing.
   */
  public static void dumpHeap() {
    int width = Integer.toHexString(ObjectModel.nextObjectId()).length()+8;
    Stack<ObjectReference> workStack = new Stack<ObjectReference>();
    Set<ObjectReference> dumped = new HashSet<ObjectReference>();
    for(Mutator m: mutators) {
      System.err.println("Mutator " + m.context.getId());
      m.dumpThreadRoots(width, workStack);
    }
    System.err.println("Heap (Depth First)");
    while(!workStack.isEmpty()) {
      ObjectReference object = workStack.pop();
      if (!dumped.contains(object)) {
        dumped.add(object);
        ObjectModel.dumpLogicalObject(width, object, workStack);
      }
    }
  }

/**
   * A gc safe point for the mutator.
   */
  public boolean gcSafePoint() {
    if (Scheduler.gcTriggered()) {
      Scheduler.waitForGC();
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
   * Mark a mutator as no longer active. If a GC has been triggered we must ensure
   * that it proceeds before we deactivate.
   */
  public void end() {
    check(expectedThrowable == null, "Expected exception of class " + expectedThrowable + " not found");
  }

  /**
   * Request a heap dump (also invokes a garbage collection)
   */
  public void heapDump() {
    Collector.requestHeapDump();
    gc();
  }

  /**
   * Request a garbage collection.
   */
  public void gc() {
    VM.collection.triggerCollection(Collection.EXTERNAL_GC_TRIGGER);
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
    check(index < limit, "Index "+index+" out of bounds "+limit);

    Address ref = ObjectModel.getDataSlot(object, index);
    ref.store(value);
    Trace.trace(Item.STORE,"%s.[%d] = %d",object.toString(),index,value);
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
    if (Trace.isEnabled(Item.STORE) || ObjectModel.isWatched(object)) {
      Trace.printf(Item.STORE,"[%s].object[%d/%d] = %s",ObjectModel.getString(object),index,limit,value.toString());
    }
    check(!object.isNull(), "Object can not be null");
    check(index >= 0, "Index must be non-negative");
    check(index < limit, "Index "+index+" out of bounds "+limit);

    Address referenceSlot = ObjectModel.getRefSlot(object, index);
    if (ActivePlan.constraints.needsWriteBarrier()) {
      context.writeBarrier(object, referenceSlot, value, null, null, Plan.AASTORE_WRITE_BARRIER);
      if (gcEveryWB) {
        gc();
      }
    } else {
      referenceSlot.store(value);
    }
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
    check(index < limit, "Index "+index+" out of bounds "+limit);

    Address dataSlot = ObjectModel.getDataSlot(object, index);
    int result = dataSlot.loadInt();
    Trace.trace(Item.LOAD,"[%s].int[%d] returned [%d]",ObjectModel.getString(object),index,result);
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
    check(index < limit, "Index "+index+" out of bounds "+limit);

    Address referenceSlot = ObjectModel.getRefSlot(object, index);
    ObjectReference result;
    if (ActivePlan.constraints.needsReadBarrier()) {
      result = context.readBarrier(object, referenceSlot, null, null, Plan.AASTORE_WRITE_BARRIER);
    } else {
      result = referenceSlot.loadObjectReference();
    }
    Trace.trace(Item.LOAD,"[%s].object[%d] returned [%s]",ObjectModel.getString(object),index,result.toString());
    return result;
  }

  /**
   * Get the hash code for the given object.
   */
  public int hash(ObjectReference object) {
    check(!object.isNull(), "Object can not be null");
    int result = ObjectModel.getHashCode(object);
    Trace.trace(Item.HASH,"hash(%s) returned [%d]",ObjectModel.getString(object),result);
    return result;
  }

  /**
   * Allocate an object and return a reference to it.
   *
   * @param refCount The number of reference fields.
   * @param dataCount The number of data fields.
   * @param doubleAlign Is this an 8 byte aligned object?
   * @param allocSite Allocation site
   * @return The object reference.
   */
  public ObjectReference alloc(int refCount, int dataCount, boolean doubleAlign, int allocSite) {
    check(refCount >= 0, "Non-negative reference field count required");
    check(refCount <= ObjectModel.MAX_REF_FIELDS, "Maximum of "+ObjectModel.MAX_REF_FIELDS+" reference fields per object");
    check(dataCount >= 0, "Non-negative data field count required");
    check(dataCount <= ObjectModel.MAX_DATA_FIELDS, "Maximum of "+ObjectModel.MAX_DATA_FIELDS+" data fields per object");
    ObjectReference result = ObjectModel.allocateObject(context, refCount, dataCount, doubleAlign, allocSite);
    Trace.trace(Item.ALLOC,"alloc(" + refCount + ", " + dataCount + ", " + doubleAlign + ") returned [" + result + "]");
    check(result != null, "Allocation returned null");
    return result;
  }

  /**
   * Return a string identifying the allocation site of an object
   * @param object
   * @return
   */
  public static String getSiteName(ObjectReference object) {
    return AllocationSite.getSite(ObjectModel.getSite(object)).toString();
  }
}
