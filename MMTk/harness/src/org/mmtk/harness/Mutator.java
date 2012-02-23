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

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.lang.runtime.AllocationSite;
import org.mmtk.harness.lang.runtime.ObjectValue;
import org.mmtk.harness.sanity.Sanity;
import org.mmtk.harness.scheduler.Scheduler;
import org.mmtk.harness.vm.ActivePlan;
import org.mmtk.harness.vm.ObjectModel;
import org.mmtk.harness.vm.Scanning;
import org.mmtk.plan.MutatorContext;
import org.mmtk.plan.Plan;
import org.mmtk.plan.TraceLocal;
import org.mmtk.vm.Collection;
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

  /**
   * Get the currently executing mutator.
   * @return the currently executing mutator.
   */
  public static Mutator current() {
    return Scheduler.currentMutator();
  }

  /**
   * Register a mutator, returning the allocated id.
   * @param context The mutator context to register
   */
  private static synchronized void register(MutatorContext context) {
    context.initMutator(Mutators.registerMutator());
  }

  /** Is this thread out of memory if the gc cannot free memory */
  private boolean outOfMemory;

  /** Get the out of memory status
   * @return True if we're subject to an out-of-memory condition
   */
  public boolean isOutOfMemory() {
    return outOfMemory;
  }

  /**
   * Set the out of memory status
   * @param value The status
   */
  public void setOutOfMemory(boolean value) {
    outOfMemory = value;
  }

  /** The number of collection attempts this thread has had since allocation succeeded */
  private int collectionAttempts;

  /**
   * @return the number of collection attempts
   */
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

  /**
   * Was the last failure a physical allocation failure
   * @return Was the last failure a physical allocation failure
   */
  public boolean isPhysicalAllocationFailure() {
    return physicalAllocationFailure;
  }

  /** Set if last failure a physical allocation failure
   * @param value The new status */
  public void setPhysicalAllocationFailure(boolean value) {
    physicalAllocationFailure = value;
  }

  /** The MMTk MutatorContext for this mutator */
  protected final MutatorContext context;

  /**
   * Constructor
   */
  public Mutator() {
    this.context = Harness.createMutatorContext();
    register(this.context);
  }

  /**
   * Initial processing of a mutator.  Enters the mutator in the mutator table.
   */
  public void begin() {
    Mutators.set(this);
    Scanning.initThreadIteratorTable(this);
  }

  /**
   * @return the MMTk MutatorContext for this mutator.
   */
  public MutatorContext getContext() {
    return context;
  }

  /**
   * Compute the thread roots for this mutator.
   * @param trace The MMTk TraceLocal to receive the roots
   */
  public void computeThreadRoots(TraceLocal trace) {
    // Nothing to do for the default mutator
  }

  /**
   * Return the roots
   * @return The roots for this mutator
   */
  public abstract Iterable<ObjectValue> getRoots();

  /**
   * Print the thread roots and return them for processing.
   * @param width Output width
   * @return The thread roots
   */
  public java.util.Collection<ObjectReference> dumpThreadRoots(int width) {
    return Collections.emptyList();
  }

  /**
   * Print the thread roots and add them to a stack for processing.
   */
  public static void dumpHeap() {
    int width = 80;
    Deque<ObjectReference> workStack = new ArrayDeque<ObjectReference>();
    Set<ObjectReference> dumped = new HashSet<ObjectReference>();
    for(Mutator m: Mutators.getAll()) {
      System.err.println("Mutator " + m.context.getId());
      workStack.addAll(m.dumpThreadRoots(width));
    }
    System.err.println("Heap (Depth First)");
    while(!workStack.isEmpty()) {
      ObjectReference object = workStack.pop();
      if (!dumped.contains(object)) {
        dumped.add(object);
        workStack.addAll(ObjectModel.dumpLogicalObject(width, object));
      }
    }
  }

  /**
   * A gc safe point for the mutator.
   * @return Whether a GC has occurred
   */
  public boolean gcSafePoint() {
    if (Scheduler.gcTriggered()) {
      Scheduler.waitForGC();
      return true;
    }
    return false;
  }

  /**
   * Mark a mutator as no longer active. If a GC has been triggered we must ensure
   * that it proceeds before we deactivate.
   */
  public void end() {
  }

  /**
   * Request a heap dump (also invokes a garbage collection)
   */
  public void heapDump() {
    // Collector.requestHeapDump();
    // TODO
    gc();
  }

  /**
   * Request a garbage collection.
   */
  public void gc() {
//    VM.collection.triggerCollection(Collection.EXTERNAL_GC_TRIGGER);
  }

  /**
   * Fail during execution.
   * @param failMessage Message to write
   */
  public void fail(String failMessage) {
    throw new RuntimeException(failMessage);
  }

  /**
   * Print a message that this code path should be impossible and exit.
   */
  public void notReached() {
    fail("Unreachable code reached!");
  }

  /**
   * Store a value into the data field of an object.
   *
   * @param object The object to store the field of.
   * @param index The field index.
   * @param value The value to store.
   */
  public void storeDataField(ObjectReference object, int index, int value) {
    if (object.isNull()) fail("Object can not be null in object "+ObjectModel.getString(object));
    Sanity.assertValid(object);
    int limit = ObjectModel.getDataCount(object);
    if (index < 0) fail("Index must be non-negative in object "+ObjectModel.getString(object));
    if (index >= limit) fail("Index "+index+" out of bounds "+limit+" in object "+ObjectModel.getString(object));

    Address ref = ObjectModel.getDataSlot(object, index);
    if (ActivePlan.constraints.needsIntWriteBarrier()) {
      context.intWrite(object, ref, value, ref.toWord(), null, Plan.INSTANCE_FIELD);
    } else {
      ref.store(value);
    }
    if (Trace.isEnabled(Item.STORE)) {
      Trace.trace(Item.STORE,"%s.[%d] = %d", object.toString(), index, value);
    }
  }

  /**
   * Store a value into a reference field of an object.
   *
   * @param object The object to store the field of.
   * @param index The field index.
   * @param value The value to store.
   */
  public void storeReferenceField(ObjectReference object, int index, ObjectReference value) {
    if (object.isNull()) fail(("Object can not be null in object "+ObjectModel.getString(object)));
    Sanity.assertValid(object);
    Sanity.assertValid(value);
    int limit = ObjectModel.getRefs(object);
    if (Trace.isEnabled(Item.STORE) || ObjectModel.isWatched(object)) {
      Trace.printf(Item.STORE,"[%s].object[%d/%d] = %s%n",ObjectModel.getString(object),index,limit,value.toString());
    }
    if (!(index >= 0)) fail(("Index must be non-negative in object "+ObjectModel.getString(object)));
    if (!(index < limit)) fail(("Index "+index+" out of bounds "+limit+" in object "+ObjectModel.getString(object)));

    Address referenceSlot = ObjectModel.getRefSlot(object, index);
    if (ActivePlan.constraints.needsObjectReferenceWriteBarrier()) {
      context.objectReferenceWrite(object, referenceSlot, value, referenceSlot.toWord(), null, Plan.INSTANCE_FIELD);
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
   * @return The contents of the data field
   */
  public int loadDataField(ObjectReference object, int index) {
    if (object.isNull()) fail(("Object can not be null in object "+ObjectModel.getString(object)));
    Sanity.assertValid(object);
    int limit = ObjectModel.getDataCount(object);
    if (!(index >= 0)) fail(("Index must be non-negative in object "+ObjectModel.getString(object)));
    if (!(index < limit)) fail(("Index "+index+" out of bounds "+limit+" in object "+ObjectModel.getString(object)));

    Address dataSlot = ObjectModel.getDataSlot(object, index);
    int result;
    if (ActivePlan.constraints.needsIntReadBarrier()) {
      result = context.intRead(object, dataSlot, dataSlot.toWord(), null, Plan.INSTANCE_FIELD);
    } else {
      result = dataSlot.loadInt();
    }
    if (Trace.isEnabled(Item.LOAD) || ObjectModel.isWatched(object)) {
      Trace.printf(Item.LOAD,"[%s].int[%d] returned [%d]%n",ObjectModel.getString(object),index,result);
    }
    return result;
  }

  /**
   * Load and return the value of a reference field of an object.
   *
   * @param object The object to load the field of.
   * @param index The field index.
   * @return The object reference
   */
  public ObjectReference loadReferenceField(ObjectReference object, int index) {
    if (object.isNull()) fail(("Object can not be null in object "+ObjectModel.getString(object)));
    Sanity.assertValid(object);
    int limit = ObjectModel.getRefs(object);
    if (!(index >= 0)) fail(("Index must be non-negative in object "+ObjectModel.getString(object)));
    if (!(index < limit)) fail(("Index "+index+" out of bounds "+limit+" in object "+ObjectModel.getString(object)));

    Address referenceSlot = ObjectModel.getRefSlot(object, index);
    ObjectReference result;
    if (ActivePlan.constraints.needsObjectReferenceReadBarrier()) {
      result = context.objectReferenceRead(object, referenceSlot, referenceSlot.toWord(), null, Plan.INSTANCE_FIELD);
    } else {
      result = referenceSlot.loadObjectReference();
    }
    Sanity.assertValid(object);
    if (Trace.isEnabled(Item.LOAD) || ObjectModel.isWatched(object)) {
      Trace.printf(Item.LOAD,"[%s].object[%d] returned [%s]%n",ObjectModel.getString(object),index,result.toString());
    }
    return result;
  }

  /**
   * Get the hash code for the given object.
   * @param object The object
   * @return The hash code
   */
  public int hash(ObjectReference object) {
    if (object.isNull()) fail("Object can not be null");
    int result = ObjectModel.getHashCode(object);
    if (Trace.isEnabled(Item.HASH) || ObjectModel.isWatched(object)) {
      Trace.printf(Item.HASH,"hash(%s) returned [%d]%n",ObjectModel.getString(object),result);
    }
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
    if (!(refCount >= 0)) fail("Non-negative reference field count required");
    if (!(refCount <= ObjectModel.MAX_REF_FIELDS)) fail(("Maximum of "+ObjectModel.MAX_REF_FIELDS+" reference fields per object"));
    if (!(dataCount >= 0)) fail("Non-negative data field count required");
    if (!(dataCount <= ObjectModel.MAX_DATA_FIELDS)) fail(("Maximum of "+ObjectModel.MAX_DATA_FIELDS+" data fields per object"));
    ObjectReference result = ObjectModel.allocateObject(context, refCount, dataCount, doubleAlign, allocSite);
    if (Trace.isEnabled(Item.ALLOC) || ObjectModel.isWatched(result)) {
      Trace.printf(Item.ALLOC,"alloc(%d,%d,%b) -> [%s]%n",refCount,dataCount,doubleAlign,ObjectModel.getString(result));
    }
    if (!(result != null)) fail("Allocation returned null");
    return result;
  }

  /**
   * Return a string identifying the allocation site of an object
   * @param object The object
   * @return Where in the script it was allocated
   */
  public static String getSiteName(ObjectReference object) {
    int site = ObjectModel.getSite(object);
    if (!AllocationSite.isValid(site)) {
      return "<invalid>";
    }
    return AllocationSite.getSite(site).toString();
  }

  /**
   * @return The thread iterator table
   */
  public ObjectReference allocThreadIteratorTable() {
    return alloc(Scanning.THREAD_ITERATOR_TABLE_ENTRIES,0,false,AllocationSite.INTERNAL_SITE_ID);
  }
}
