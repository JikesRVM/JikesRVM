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
package org.jikesrvm.mm.mmtk;

import org.mmtk.plan.Plan;
import org.mmtk.plan.TraceLocal;
import org.mmtk.utility.options.Options;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

import org.jikesrvm.VM;
import org.jikesrvm.runtime.VM_Entrypoints;
import org.jikesrvm.scheduler.VM_Scheduler;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.lang.ref.PhantomReference;


/**
 * This class manages SoftReferences, WeakReferences, and
 * PhantomReferences. When a java/lang/ref/Reference object is created,
 * its address is added to a table of pending reference objects of the
 * appropriate type. An address is used so the reference will not stay
 * alive during gc if it isn't in use elsewhere the mutator. During
 * gc, the various lists are processed in the proper order to
 * determine if any Reference objects are ready to be enqueued or
 * whether referents that have died should be kept alive until the
 * Reference is explicitly cleared. MMTk drives this processing and
 * uses this class, via the VM interface, to scan the lists of pending
 * reference objects.
 *
 * As an optimization for generational collectors, each reference type
 * maintains two queues: a nursery queue and the main queue.
 */
@Uninterruptible
public class ReferenceProcessor extends org.mmtk.vm.ReferenceProcessor {

  /********************************************************************
   * Class fields
   */

  private static Lock lock = new Lock("ReferenceProcessor");

  private static final ReferenceProcessor softReferenceProcessor =
    new ReferenceProcessor(Semantics.SOFT);
  private static final ReferenceProcessor weakReferenceProcessor =
    new ReferenceProcessor(Semantics.WEAK);
  private static final ReferenceProcessor phantomReferenceProcessor =
    new ReferenceProcessor(Semantics.PHANTOM);

  // Debug flags
  private static final boolean TRACE = false;
  private static final boolean TRACE_DETAIL = false;

  /** Initial size of the reference object table */
  private static final int INITIAL_SIZE = 256;

  /**
   * Grow the reference object table by this multiplier
   * on overflow
   */
  private static final double GROWTH_FACTOR = 2.0;


  /*************************************************************************
   * Instance fields
   */

  /**
   * The table of reference objects for the current semantics
   */
  private AddressArray references = AddressArray.create(INITIAL_SIZE);

  /**
   * Index into the <code>references</code> table for the start of
   * the reference nursery.
   */
  private int nurseryIndex = 0;

  /**
   * Index of the first free slot in the reference table.
   */
  private int maxIndex = 0;

  /**
   * Flag to prevent a race between threads growing the reference object
   * table.
   */
  private boolean growingTable = false;

  /**
   * Semantics
   */
  private final Semantics semantics;

  /** Copy of semantics.toString() for use in uninterruptible code */
  private final String semanticsStr;


  /**
   * Create a reference processor for a given semantics
   *
   * @param semantics
   */
  private ReferenceProcessor(Semantics semantics) {
    this.semantics = semantics;
    this.semanticsStr = semantics.toString();
  }

  /**
   * Factory method.
   * Creates an instance of the appropriate reference type processor.
   * @return
   */
  @Interruptible
  public static ReferenceProcessor get(Semantics semantics) {
    switch(semantics) {
    case WEAK:    return weakReferenceProcessor;
    case SOFT:    return softReferenceProcessor;
    case PHANTOM: return phantomReferenceProcessor;
    default:
      VM._assert(false,"Unrecognized semantics");
      return null;
    }
  }

  /**
   * Add a reference at the end of the table
   * @param ref The reference to add
   */
  private void addReference(Reference<?> ref) {
    setReference(maxIndex++,ObjectReference.fromObject(ref));
  }

  /**
   * Update the reference table
   *
   * @param i The table index
   * @param ref The reference to insert
   */
  private void setReference(int i, ObjectReference ref) {
    if (TRACE) {
      VM.sysWriteln("  reference ",i);
      VM.sysWriteln(" <- ",ref);
    }
    references.set(i,ref.toAddress());
  }

  /**
   * Retrieve from the reference table
   *
   * @param i Table index
   * @return The reference object at index i
   */
  private ObjectReference getReference(int i) {
    return references.get(i).toObjectReference();
  }

  /**
   * Grow the reference table by GROWTH_FACTOR
   *
   * Logically Uninterruptible because it can GC when it allocates, but
   * the rest of the code can't tolerate GC
   */
  @LogicallyUninterruptible
  private AddressArray growReferenceTable() {
    int newLength = (int)(references.length() * GROWTH_FACTOR);
    if (TRACE) VM.sysWriteln("Expanding reference type table ",semantics.toString()," to ",newLength);
    AddressArray newReferences = AddressArray.create(newLength);
    for (int i=0; i < references.length(); i++)
      newReferences.set(i,references.get(i));
    return newReferences;
  }

  /**
   * Add a reference to the list of references.
   *
   * (SJF: This method must NOT be inlined into an inlined allocation
   * sequence, since it contains a lock!)
   *
   * @param ref the reference to add
   */
  @NoInline
  private void addCandidate(Reference<?> ref) {
    if (TRACE) {
      ObjectReference referenceAsAddress = ObjectReference.fromObject(ref);
      ObjectReference referent = getReferent(referenceAsAddress);
      VM.sysWriteln("Adding Reference: ", referenceAsAddress);
      VM.sysWriteln("       Referent:  ", referent);
    }

    /* TODO add comments */
    lock.acquire();
    while (maxIndex >= references.length()) {
      if (growingTable) {
        lock.release();
        VM_Scheduler.yield();
        lock.acquire();
      } else {
        growingTable = true;
        lock.release();
        AddressArray newTable = growReferenceTable();
        lock.acquire();
        references = newTable;
        growingTable = false;
      }
    }
    addReference(ref);
    lock.release();
  }

  /***********************************************************************
   *              GC time processing
   */

  /**
   * Scan through all references and forward.
   *
   * Collectors like MarkCompact determine liveness and move objects
   * using separate traces.
   *
   * Currently ignores the nursery hint.
   *
   * TODO parallelise this code
   *
   * @param trace The trace
   * @param nursery Is this a nursery collection ?
   */
  @Override
  public void forward(TraceLocal trace, boolean nursery) {
    if (TRACE) VM.sysWriteln("Starting ReferenceGlue.forward(",semanticsStr,")");

    for (int i=0; i < maxIndex; i++) {
      ObjectReference reference = getReference(i);
      setReferent(reference, trace.getForwardedReferent(getReferent(reference)));
      ObjectReference newReference = trace.getForwardedReference(reference);
      setReference(i, newReference);
    }

    if (TRACE) VM.sysWriteln("Ending ReferenceGlue.forward(",semanticsStr,")");
  }

  /**
   * Scan through the list of references. Calls ReferenceProcessor's
   * processReference method for each reference and builds a new
   * list of those references still active.
   *
   * Depending on the value of <code>nursery</code>, we will either
   * scan all references, or just those created since the last scan.
   *
   * TODO parallelise this code
   *
   * @param nursery Scan only the newly created references
   */
  @Override
  public void scan(TraceLocal trace, boolean nursery) {
    int toIndex = nursery ? nurseryIndex : 0;

    for (int fromIndex = toIndex; fromIndex < maxIndex; fromIndex++) {
      ObjectReference reference = getReference(fromIndex);

      /* Determine liveness (and forward if necessary) the reference */
      ObjectReference newReference = processReference(trace,reference);
      if (!newReference.isNull()) {
        setReference(toIndex++,newReference);
      }
    }
    if (Options.verbose.getValue() >= 3) {
      VM.sysWrite(semanticsStr);
      VM.sysWriteln(" references: ",maxIndex," -> ",toIndex);
    }
    nurseryIndex = maxIndex = toIndex;
  }

  /**
   * Put this Reference object on its ReferenceQueue (if it has one)
   * when its referent is no longer sufficiently reachable. The
   * definition of "reachable" is defined by the semantics of the
   * particular subclass of Reference. The implementation of this
   * routine is determined by the the implementation of
   * java.lang.ref.ReferenceQueue in GNU classpath. It is in this
   * class rather than the public Reference class to ensure that Jikes
   * has a safe way of enqueueing the object, one that cannot be
   * overridden by the application program.
   *
   * ************************ TODO *********************************
   * Change this so that we don't call reference.enqueue directly
   * as this can be overridden by the user.
   * ***************************************************************
   *
   * @see java.lang.ref.ReferenceQueue
   * @param addr the address of the Reference object
   * @return <code>true</code> if the reference was enqueued
   */
  public boolean enqueueReference(ObjectReference addr) {
    Reference<?> reference = (Reference<?>)addr.toObject();
    return reference.enqueue();
  }

  /**
   * Add a reference to the list of soft references.
   * @param ref the SoftReference to add
   */
  @Interruptible
  public static void addSoftCandidate(SoftReference<?> ref) {
    softReferenceProcessor.addCandidate(ref);
  }

  /**
   * Add a reference to the list of weak references.
   * @param ref the WeakReference to add
   */
  @Interruptible
  public static void addWeakCandidate(WeakReference<?> ref) {
    weakReferenceProcessor.addCandidate(ref);
  }

  /**
   * Add a reference to the list of phantom references.
   * @param ref the PhantomReference to add
   */
  @Interruptible
  public static void addPhantomCandidate(PhantomReference<?> ref) {
    phantomReferenceProcessor.addCandidate(ref);
  }

  /****************************************************************************
   *
   *               Semantics of reference types
   *
   */

  /**
   * Process a reference with the current semantics.
   * @param reference the address of the reference. This may or may not
   * be the address of a heap object, depending on the VM.
   * @param trace the thread local trace element.
   */
  public ObjectReference processReference(TraceLocal trace, ObjectReference reference) {
    if (VM.VerifyAssertions) VM._assert(!reference.isNull());

    if (TRACE) VM.sysWriteln("### old reference: ",reference);

    /*
     * If the reference is dead, we're done with it. Let it (and
     * possibly its referent) be garbage-collected.
     */
    if (!trace.isLive(reference)) {
      clearReferent(reference);                   // Too much paranoia ...
      return ObjectReference.nullReference();
    }

    /* The reference object is live */
    ObjectReference newReference = trace.getForwardedReference(reference);
    ObjectReference oldReferent = getReferent(reference);

    if (TRACE_DETAIL) {
      VM.sysWriteln("    new reference: ",newReference);
      VM.sysWriteln("    old referent: ",oldReferent);
    }

    /*
     * If the application has cleared the referent the Java spec says
     * this does not cause the Reference object to be enqueued. We
     * simply allow the Reference object to fall out of our
     * waiting list.
     */
    if (oldReferent.isNull()) {
      return ObjectReference.nullReference();
    }

    if (semantics == Semantics.SOFT) {
      /*
       * Unless we've completely run out of memory, we keep
       * softly reachable objects alive.
       */
      if (!Plan.isEmergencyCollection()) {
        if (TRACE_DETAIL) VM.sysWriteln("    resurrecting: ",oldReferent);
        trace.retainReferent(oldReferent);
      }
    } else if (semantics == Semantics.PHANTOM) {
      /*
       * The spec says we should forward the reference.  Without unsafe uses of
       * reflection, the application can't tell the difference whether we do or not,
       * so we don't forward the reference.
       */
//    trace.retainReferent(oldReferent);
    }

    if (trace.isLive(oldReferent)) {
      /*
       * Referent is still reachable in a way that is as strong as
       * or stronger than the current reference level.
       */
      ObjectReference newReferent = trace.getForwardedReferent(oldReferent);

      if (TRACE) VM.sysWriteln(" new referent: ",newReferent);

      /*
       * The reference object stays on the waiting list, and the
       * referent is untouched. The only thing we must do is
       * ensure that the former addresses are updated with the
       * new forwarding addresses in case the collector is a
       * copying collector.
       */

      /* Update the referent */
      setReferent(newReference, newReferent);
      return newReference;
    } else {
      /* Referent is unreachable. Clear the referent and enqueue the reference object. */

      if (TRACE) VM.sysWriteln(" UNREACHABLE:  ",oldReferent);

      clearReferent(newReference);
      enqueueReference(newReference);
      return ObjectReference.nullReference();
    }
  }

  /**
   * Weak and soft references always clear the referent
   * before enqueueing. We don't actually call
   * Reference.clear() as the user could have overridden the
   * implementation and we don't want any side-effects to
   * occur.
   */
  protected void clearReferent(ObjectReference newReference) {
    setReferent(newReference, ObjectReference.nullReference());
  }

  /***********************************************************************
   *
   * Reference object field accessors
   */

  /**
   * Get the referent from a reference.  For Java the reference
   * is a Reference object.
   * @param object the object reference.
   * @return the referent object reference.
   */
  protected ObjectReference getReferent(ObjectReference object) {
    return object.toAddress().loadObjectReference(VM_Entrypoints.referenceReferentField.getOffset());
  }

  /**
   * Set the referent in a reference.  For Java the reference is
   * a Reference object.
   * @param ref the ObjectReference for the reference (confusing eh?).
   * @param referent the referent object reference.
   */
  protected void setReferent(ObjectReference ref, ObjectReference referent) {
    ref.toAddress().store(referent, VM_Entrypoints.referenceReferentField.getOffset());
  }

  /***********************************************************************
   *
   * Statistics and debugging
   */

  public int countWaitingReferences() {
    return maxIndex;
  }
}
