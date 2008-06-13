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
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.mmtk.harness.vm.ActivePlan;
import org.mmtk.harness.vm.ObjectModel;
import org.mmtk.plan.MutatorContext;
import org.mmtk.plan.Plan;
import org.mmtk.plan.TraceLocal;
import org.mmtk.vm.Collection;
import org.mmtk.vm.VM;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.SimulatedMemory;

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
public class Mutator extends Thread {

  /** Registered mutators */
  protected static ArrayList<Mutator> mutators = new ArrayList<Mutator>();

  /** Thread access to current collector */
  protected static ThreadLocal<Mutator> mutatorThreadLocal = new ThreadLocal<Mutator>() {
    public Mutator initialValue() {
      for(int i=0; i <mutators.size(); i++) {
        Mutator m = mutators.get(i);
        if (m == Thread.currentThread()) {
          return m;
        }
      }
      assert false: "Could not find mutator";
      return null;
    }
  };

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
    Mutator m = mutatorThreadLocal.get();
    assert m != null: "Mutator.current() called from a thread without a mutator context";
    assert m == Thread.currentThread() : "Mutator.current() does not match Thread.currentThread()";
    return m;
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
  private final MutatorContext context;

  /** The local variable -> value map (a crude representation of a mutator stack) */
  private Map<String, ObjectReference> locals;

  /**
   * Create a mutator thread, specifying an (optional) entry point and initial local variable map.
   *
   * @param entryPoint The entryPoint.
   * @param locals The local variable map.
   */
  private Mutator(Runnable entryPoint, Map<String, ObjectReference> locals) {
    super(entryPoint);
    try {
      String prefix = Harness.plan.getValue();
      this.context = (MutatorContext)Class.forName(prefix + "Mutator").newInstance();
    } catch (Exception ex) {
      throw new RuntimeException("Could not create Mutator", ex);
    }
    this.locals = locals != null ? locals : new HashMap<String, ObjectReference>();
    mutators.set(context.getId(), this);
    addActiveMutator();
  }

  /**
   * Create a new mutator thread. Intended to be used for subclasses implementing a run() method.
   */
  protected Mutator() {
    this(null, null);
  }

  /**
   * Create a new mutator thread with a specified entry point.
   * @param entryPoint
   */
  public Mutator(Runnable entryPoint) {
    this(entryPoint, null);
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
    Map<String, ObjectReference> newLocals = new HashMap<String, ObjectReference>();
    for(String key: locals.keySet()) {
      ObjectReference object = locals.get(key);
      object = trace.traceObject(object, true);
      newLocals.put(key, object);
    }
    locals = newLocals;
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
   * Object used for syncrhonizing the number of mutators waiting for a gc.
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
  private static void addActiveMutator() {
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
   * Mark a mutator as no longer active. If a GC has been triggered we must ensure
   * that it proceeds before we deactivate.
   */
  private static void removeActiveMutator() {
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
   * Set the value of a local variable
   */
  private void setVar(String var, ObjectReference value) {
    if (var != null) {
      assert Pattern.matches("[a-zA-Z_]+", var): "Invalid variable name";
      if (value == null && locals.containsKey(var)) {
        locals.remove(var);
      } else {
        locals.put(var, value);
      }
    }
  }

  /**
   * Get the value of a local variable.
   */
  private ObjectReference getVar(String var) {
    assert Pattern.matches("[a-zA-Z_]+", var): "Invalid variable name";
    ObjectReference ref = locals.get(var);
    assert ref != null: "Variable cannot be null";
    return ref;
  }

  /**
   * Load an object reference from the specified field of the named variable
   *
   * @param fromVar The variable name of the object to load from
   * @param fromIndex The reference index
   * @return The loaded ObjectReference
   */
  private ObjectReference loadInternal(String fromVar, int fromIndex) {
    ObjectReference ref = getVar(fromVar);
    int refs = ObjectModel.getRefs(ref);
    assert fromIndex >= 0 && fromIndex < refs: "Index out of bounds";
    Address slot = ObjectModel.getRefSlot(ref, fromIndex);
    if (ActivePlan.constraints.needsReadBarrier()) {
      ref = context.readBarrier(ref, slot, Offset.zero(), 0, Plan.AALOAD_READ_BARRIER);
    } else {
      ref = slot.loadObjectReference();
    }
    return ref;
  }

  /**
   * Store an object reference in a field of a named variable object.
   *
   * @param toVar The name of the variable containing the object to store into.
   * @param toIndex The index to store at.
   * @param target The ObjectReference to store.
   */
  private void storeInternal(String toVar, int toIndex, ObjectReference target) {
    ObjectReference src = getVar(toVar);
    int refs = ObjectModel.getRefs(src);
    assert toIndex >= 0 && toIndex < refs: "Index out of bounds";
    Address slot = ObjectModel.getRefSlot(src, toIndex);
    if (ActivePlan.constraints.needsWriteBarrier()) {
      context.writeBarrier(src, slot, target, Offset.zero(), 0, Plan.AASTORE_WRITE_BARRIER);
    } else {
      slot.store(target);
    }
  }

  /**
   * Allocate an object of the specified size.
   *
   * @param bytes the size of the object in bytes.
   */
  public void muAlloc(int bytes) {
    muAlloc(null, bytes);
  }

  /**
   * Allocate an object with the specified number of reference and data fields.
   *
   * @param refCount The number of reference fields.
   * @param dataCount The number of data fields.
   */
  public void muAlloc(int refCount, int dataCount) {
    muAlloc(null, refCount, dataCount);
  }

  /**
   * Allocate an object of the specified size and store a reference to it in the given variable.
   *
   * @param toVar The variable to store the object into.
   * @param bytes the size of the object in bytes.
   */
  public void muAlloc(String toVar, int bytes) {
    int words = (bytes >>> SimulatedMemory.LOG_BYTES_IN_WORD);
    assert words >= ObjectModel.HEADER_SIZE: "Allocation request smaller than minimum object size";
    muAlloc(toVar, 0, words - ObjectModel.HEADER_SIZE);
  }

  /**
   * Allocate an object with the specified number of reference and data fields and store a
   * reference to it in the given variable.
   *
   * @param toVar The variable to store the object into.
   * @param refCount The number of reference fields.
   * @param dataCount The number of data fields.
   */
  public void muAlloc(String toVar, int refCount, int dataCount) {
    ObjectReference ref = ObjectModel.allocateObject(context, refCount, dataCount);
    setVar(toVar, ref);
    gcSafePoint();
  }

  /**
   * Load a reference from an object and store the value in the given variable.
   *
   * @param toVar The variable to store the loaded value in.
   * @param fromVar The object to load the reference from.
   * @param fromIndex The index to load.
   */
  public void muLoad(String toVar, String fromVar, int fromIndex) {
    ObjectReference ref = loadInternal(fromVar, fromIndex);
    setVar(toVar, ref);
    gcSafePoint();
  }

  /**
   * Store a reference into an object at the specified index.
   *
   * @param toVar The variable holding the object to store into.
   * @param toIndex The index to store.
   * @param fromVar The variable holding the value to store.
   */
  public void muStore(String toVar, int toIndex, String fromVar) {
    ObjectReference target = getVar(fromVar);
    storeInternal(toVar, toIndex, target);
    gcSafePoint();
  }

  /**
   * Copy a value from one object field to another.
   *
   * @param toVar The variable holding the object to store into.
   * @param toIndex The index to store.
   * @param fromVar The variable holding the object to load from.
   * @param fromIndex The index to load.
   */
  public void muCopy(String toVar, int toIndex, String fromVar, int fromIndex) {
    ObjectReference ref = loadInternal(fromVar, fromIndex);
    storeInternal(toVar, toIndex, ref);
    gcSafePoint();
  }

  /**
   * Copy a value from one variable to another.
   *
   * @param toVar The variable to store into.
   * @param fromVar The variable to load from.
   */
  public void muCopy(String toVar, String fromVar) {
    ObjectReference value = getVar(fromVar);
    setVar(toVar, value);
    gcSafePoint();
  }

  /**
   * Spawn a new mutator thread at the given entry point, copying all local variables.
   *
   * @param entryPoint The entry point of the new mutator.
   */
  public void muSpawn(Runnable entryPoint) {
    muSpawn(entryPoint, (String[])locals.keySet().toArray());
  }

  /**
   * Spawn a new mutator thread at the given entry point, copying the value of
   * a specified set of local variables.
   *
   * @param entryPoint The entry point of the new mutator.
   * @param vars The variables to copy, or null to copy none.
   */
  public void muSpawn(final Runnable entryPoint, String[] vars) {
    Map<String, ObjectReference> spawnLocals = new HashMap<String, ObjectReference>();
    if (vars != null) {
      for(String var: vars) {
        spawnLocals.put(var, getVar(var));
      }
    }
    Mutator m = new Mutator(entryPoint, spawnLocals);
    m.start();
    gcSafePoint();
  }

  /**
   * Clear the value of a variable
   *
   * @param var The variable to clear
   */
  public void muClear(String var) {
    setVar(var, null);
    gcSafePoint();
  }

  /**
   * Clear the value of a all variables
   */
  public void muClearAll() {
    locals = new HashMap<String, ObjectReference>();
    gcSafePoint();
  }

  /**
   * Mark the mutator as inactive.
   */
  public void muEnd() {
    locals = new HashMap<String, ObjectReference>();
    removeActiveMutator();
  }

  /**
   * Trigger a garbage collection.
   */
  public void muGC() {
    VM.collection.triggerCollection(Collection.EXTERNAL_GC_TRIGGER);
    gcSafePoint();
  }
}
