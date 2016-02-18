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
package org.jikesrvm.mm.mmtk;

import org.mmtk.plan.TraceLocal;
import org.mmtk.utility.options.Options;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

import org.jikesrvm.VM;
import org.jikesrvm.mm.mminterface.DebugUtil;
import org.jikesrvm.mm.mminterface.Selected;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.scheduler.RVMThread;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.lang.ref.PhantomReference;


/**
 * This class manages SoftReferences, WeakReferences, and
 * PhantomReferences. When a java/lang/ref/Reference object is created,
 * its address is added to a table of pending reference objects of the
 * appropriate type. An address is used so the reference will not stay
 * alive during GC if it isn't in use elsewhere the mutator. During
 * GC, the various lists are processed in the proper order to
 * determine if any Reference objects are ready to be enqueued or
 * whether referents that have died should be kept alive until the
 * Reference is explicitly cleared. MMTk drives this processing and
 * uses this class, via the VM interface, to scan the lists of pending
 * reference objects.
 * <p>
 * As an optimization for generational collectors, each reference type
 * maintains two queues: a nursery queue and the main queue.
 */
@Uninterruptible
public final class ReferenceProcessor extends org.mmtk.vm.ReferenceProcessor {

  /********************************************************************
   * Class fields
   */

  private static final Lock lock = new Lock("ReferenceProcessor");

  private static final ReferenceProcessor softReferenceProcessor =
    new ReferenceProcessor(Semantics.SOFT);
  private static final ReferenceProcessor weakReferenceProcessor =
    new ReferenceProcessor(Semantics.WEAK);
  private static final ReferenceProcessor phantomReferenceProcessor =
    new ReferenceProcessor(Semantics.PHANTOM);

  // Debug flags
  private static final boolean TRACE = false;
  private static final boolean TRACE_UNREACHABLE = false;
  private static final boolean TRACE_DETAIL = false;
  private static final boolean STRESS = false || VM.ForceFrequentGC;

  /** Initial size of the reference object table */
  private static final int INITIAL_SIZE = STRESS ? 1 : 256;

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
  private volatile AddressArray references = AddressArray.create(INITIAL_SIZE);

  /**
   * In a MarkCompact (or similar) collector, we need to update the {@code references}
   * field, and then update its contents.  We implement this by saving the pointer in
   * this untraced field for use during the {@code forward} pass.
   */
  @Untraced
  private volatile AddressArray unforwardedReferences = null;

  /**
   * Index into the <code>references</code> table for the start of
   * the reference nursery.
   */
  private int nurseryIndex = 0;

  /**
   * Index of the first free slot in the reference table.
   */
  private volatile int maxIndex = 0;

  /**
   * Flag to prevent a race between threads growing the reference object
   * table.
   */
  private volatile boolean growingTable = false;

  /**
   * Semantics
   */
  private final Semantics semantics;

  /** Copy of semantics.toString() for use in uninterruptible code */
  private final String semanticsStr;


  /**
   * Create a reference processor for a given semantics
   *
   * @param semantics the semantics this processor should use
   *  (i.e. the types of references that it will process)
   */
  private ReferenceProcessor(Semantics semantics) {
    this.semantics = semantics;
    this.semanticsStr = semantics.toString();
  }

  /**
   * Creates an instance of the appropriate reference type processor.
   *
   * @param semantics the semantics that the reference processor should
   *  use (i.e. the type of references that it will process)
   * @return the reference processor
   */
  @Interruptible
  public static ReferenceProcessor get(Semantics semantics) {
    switch(semantics) {
    case WEAK:    return weakReferenceProcessor;
    case SOFT:    return softReferenceProcessor;
    case PHANTOM: return phantomReferenceProcessor;
    default:
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED,"Unrecognized semantics");
      return null;
    }
  }

  /**
   * Add a reference at the end of the table
   * @param ref The reference to add
   * @param referent the object pointed to by the reference
   */
  private void addReference(Reference<?> ref, ObjectReference referent) {
    ObjectReference reference = ObjectReference.fromObject(ref);
    setReferent(reference, referent);
    setReference(maxIndex++,reference);
  }

  /**
   * Update the reference table
   *
   * @param i The table index
   * @param ref The reference to insert
   */
  private void setReference(int i, ObjectReference ref) {
    if (TRACE_DETAIL) {
      VM.sysWrite("slot ",i);
      VM.sysWriteln(" => ",ref);
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
   * Grow the reference table by GROWTH_FACTOR.
   *
   * <p>Marked as UninterruptibleNoWarn because it can GC when it allocates, but
   * the rest of the code can't tolerate GC.
   *
   * <p>This method is called without the reference processor lock held,
   * but with the flag <code>growingTable</code> set.
   *
   * @return the start address of the new reference table
   */
  @UninterruptibleNoWarn
  private AddressArray growReferenceTable() {
    int newLength = STRESS ? references.length() + 1 : (int)(references.length() * GROWTH_FACTOR);
    if (TRACE) VM.sysWriteln("Expanding reference type table ",semanticsStr," to ",newLength);
    AddressArray newReferences = AddressArray.create(newLength);
    for (int i = 0; i < references.length(); i++)
      newReferences.set(i,references.get(i));
    return newReferences;
  }

  /**
   * Add a reference to the list of references.  This method is responsible
   * for installing the  address of the referent into the Reference object
   * so that the referent is traced at all yield points before the Reference
   * is correctly installed in the reference table.
   *
   * (SJF: This method must NOT be inlined into an inlined allocation
   * sequence, since it contains a lock!)
   *
   * @param referent The referent of the reference
   * @param ref The reference to add
   */
  @NoInline
  @Unpreemptible("Non-preemptible but yield when table needs to be grown")
  private void addCandidate(Reference<?> ref, ObjectReference referent) {
    if (TRACE) {
      ObjectReference referenceAsAddress = ObjectReference.fromObject(ref);
      VM.sysWrite("Adding Reference: ", referenceAsAddress);
      VM.sysWriteln(" ~> ", referent);
    }

    /*
     * Ensure that only one thread at a time can grow the
     * table of references.  The volatile flag <code>growingTable</code> is
     * used to allow growing the table to trigger GC, but to prevent
     * any other thread from accessing the table while it is being grown.
     *
     * If the table has space, threads will add the reference, incrementing maxIndex
     * and exit.
     *
     * If the table is full, the first thread to notice will grow the table.
     * Subsequent threads will release the lock and yield at (1) while the
     * first thread
     */
    lock.acquire();
    while (growingTable || maxIndex >= references.length()) {
      if (growingTable) {
        // FIXME: We should probably speculatively allocate a new table instead.
        // note, we can copy without the lock after installing the new table (unint during copy).
        lock.release();
        RVMThread.yieldWithHandshake(); // (1) Allow another thread to grow the table
        lock.acquire();
      } else {
        growingTable = true;  // Prevent other threads from growing table while lock is released
        lock.release();       // Can't hold the lock while allocating
        AddressArray newTable = growReferenceTable();
        lock.acquire();
        references = newTable;
        growingTable = false; // Allow other threads to grow the table rather than waiting for us
      }
    }
    addReference(ref,referent);
    lock.release();
  }

  /***********************************************************************
   *              GC time processing
   */

  /**
   * {@inheritDoc}
   * <p>
   * Collectors like MarkCompact determine liveness and move objects
   * using separate traces.
   * <p>
   * Currently ignores the nursery hint.
   * <p>
   * TODO parallelise this code
   *
   */
  @Override
  public void forward(TraceLocal trace, boolean nursery) {
    if (VM.VerifyAssertions) VM._assert(unforwardedReferences != null);
    if (TRACE) VM.sysWriteln("Starting ReferenceGlue.forward(",semanticsStr,")");
    if (TRACE_DETAIL) {
      VM.sysWrite(semanticsStr," Reference table is ",
          Magic.objectAsAddress(references));
      VM.sysWriteln("unforwardedReferences is ",
          Magic.objectAsAddress(unforwardedReferences));
    }
    for (int i = 0; i < maxIndex; i++) {
      if (TRACE_DETAIL) VM.sysWrite("slot ",i,": ");
      ObjectReference reference = unforwardedReferences.get(i).toObjectReference();
      if (TRACE_DETAIL) VM.sysWriteln("forwarding ",reference);
      setReferent(reference, trace.getForwardedReferent(getReferent(reference)));
      ObjectReference newReference = trace.getForwardedReference(reference);
      unforwardedReferences.set(i, newReference.toAddress());
    }
    if (TRACE) VM.sysWriteln("Ending ReferenceGlue.forward(",semanticsStr,")");
    unforwardedReferences = null;
  }

  @Override
  public void clear() {
    maxIndex = 0;
  }

  /**
   * {@inheritDoc} Calls ReferenceProcessor's
   * processReference method for each reference and builds a new
   * list of those references still active.
   * <p>
   * Depending on the value of <code>nursery</code>, we will either
   * scan all references, or just those created since the last scan.
   * <p>
   * TODO parallelise this code
   *
   * @param nursery Scan only the newly created references
   */
  @Override
  public void scan(TraceLocal trace, boolean nursery, boolean retain) {
    unforwardedReferences = references;

    if (TRACE) VM.sysWriteln("Starting ReferenceGlue.scan(",semanticsStr,")");
    int toIndex = nursery ? nurseryIndex : 0;

    if (TRACE_DETAIL) VM.sysWriteln(semanticsStr," Reference table is ",Magic.objectAsAddress(references));
    if (retain) {
      for (int fromIndex = toIndex; fromIndex < maxIndex; fromIndex++) {
          ObjectReference reference = getReference(fromIndex);
          retainReferent(trace, reference);
        }
    } else {
      for (int fromIndex = toIndex; fromIndex < maxIndex; fromIndex++) {
        ObjectReference reference = getReference(fromIndex);

        /* Determine liveness (and forward if necessary) the reference */
        ObjectReference newReference = processReference(trace,reference);
        if (!newReference.isNull()) {
          setReference(toIndex++,newReference);
          if (TRACE_DETAIL) {
            int index = toIndex - 1;
            VM.sysWrite("SCANNED ",index);
            VM.sysWrite(" ",references.get(index));
            VM.sysWrite(" -> ");
            VM.sysWriteln(getReferent(references.get(index).toObjectReference()));
          }
               }
             }
      if (Options.verbose.getValue() >= 3) {
        VM.sysWrite(semanticsStr);
        VM.sysWriteln(" references: ",maxIndex," -> ",toIndex);
      }
      nurseryIndex = maxIndex = toIndex;
    }

    /* flush out any remset entries generated during the above activities */
    Selected.Mutator.get().flushRememberedSets();
    if (TRACE) VM.sysWriteln("Ending ReferenceGlue.scan(",semanticsStr,")");
  }

  /**
   * This method deals only with soft references. It retains the referent
   * if the reference is definitely reachable.
   * @param reference the address of the reference. This may or may not
   * be the address of a heap object, depending on the VM.
   * @param trace the thread local trace element.
   */
  protected void retainReferent(TraceLocal trace, ObjectReference reference) {
    if (VM.VerifyAssertions) VM._assert(!reference.isNull());
    if (VM.VerifyAssertions) VM._assert(semantics == Semantics.SOFT);

    if (TRACE_DETAIL) {
      VM.sysWrite("Processing reference: ",reference);
    }

    if (!trace.isLive(reference)) {
      /*
       * Reference is currently unreachable but may get reachable by the
       * following trace. We postpone the decision.
       */
      return;
    }

    /*
     * Reference is definitely reachable.  Retain the referent.
     */
    ObjectReference referent = getReferent(reference);
    if (!referent.isNull())
      trace.retainReferent(referent);
    if (TRACE_DETAIL) {
      VM.sysWriteln(" ~> ", referent.toAddress(), " (retained)");
    }
  }

  /**
   * Put this Reference object on its ReferenceQueue (if it has one)
   * when its referent is no longer sufficiently reachable. The
   * definition of "reachable" is defined by the semantics of the
   * particular subclass of Reference.
   * <p>
   * The implementation of this routine is determined by the the
   * implementation of java.lang.ref.ReferenceQueue in the class library.
   * It is in this class rather than the public Reference class to
   * ensure that Jikes has a safe way of enqueueing the object,
   * one that cannot be overridden by the application program.
   *
   * @see java.lang.ref.ReferenceQueue
   * @param addr the address of the Reference object
   * @return <code>true</code> if the reference was enqueued
   */
  public boolean enqueueReference(ObjectReference addr) {
    Reference<?> reference = (Reference<?>)addr.toObject();
    return reference.enqueueInternal();
  }

  /**
   * Add a reference to the list of soft references.
   * @param ref the SoftReference to add
   * @param referent the object that the reference points to
   */
  @Interruptible
  public static void addSoftCandidate(SoftReference<?> ref, ObjectReference referent) {
    softReferenceProcessor.addCandidate(ref, referent);
  }

  /**
   * Add a reference to the list of weak references.
   * @param ref the WeakReference to add
   * @param referent the object that the reference points to
   */
  @Interruptible
  public static void addWeakCandidate(WeakReference<?> ref, ObjectReference referent) {
    weakReferenceProcessor.addCandidate(ref, referent);
  }

  /**
   * Add a reference to the list of phantom references.
   * @param ref the PhantomReference to add
   * @param referent the object that the reference points to
   */
  @Interruptible
  public static void addPhantomCandidate(PhantomReference<?> ref, ObjectReference referent) {
    phantomReferenceProcessor.addCandidate(ref, referent);
  }

  /****************************************************************************
   *
   *               Semantics of reference types
   *
   */

  /**
   * Processes a reference with the current semantics.
   * <p>
   * This method deals with  a soft reference as if it were a weak reference, i.e.
   * it does not retain the referent. To retain the referent, use
   * {@link #retainReferent(TraceLocal, ObjectReference)} followed by a transitive
   * closure phase.
   *
   * @param reference the address of the reference. This may or may not
   * be the address of a heap object, depending on the VM.
   * @param trace the thread local trace element.
   * @return an updated reference (e.g. with a new address) if the reference
   *  is still live, {@code ObjectReference.nullReference()} otherwise
   */
  public ObjectReference processReference(TraceLocal trace, ObjectReference reference) {
    if (VM.VerifyAssertions) VM._assert(!reference.isNull());

    if (TRACE_DETAIL) {
      VM.sysWrite("Processing reference: ",reference);
    }
    /*
     * If the reference is dead, we're done with it. Let it (and
     * possibly its referent) be garbage-collected.
     */
    if (!trace.isLive(reference)) {
      clearReferent(reference);                   // Too much paranoia ...
      if (TRACE_UNREACHABLE) {
        VM.sysWriteln(" UNREACHABLE reference:  ",reference);
      }
      if (TRACE_DETAIL) {
        VM.sysWriteln(" (unreachable)");
      }
      return ObjectReference.nullReference();
    }

    /* The reference object is live */
    ObjectReference newReference = trace.getForwardedReference(reference);
    ObjectReference oldReferent = getReferent(reference);

    if (TRACE_DETAIL) {
      VM.sysWrite(" ~> ",oldReferent);
    }

    /*
     * If the application has cleared the referent the Java spec says
     * this does not cause the Reference object to be enqueued. We
     * simply allow the Reference object to fall out of our
     * waiting list.
     */
    if (oldReferent.isNull()) {
      if (TRACE_DETAIL) VM.sysWriteln(" (null referent)");
      return ObjectReference.nullReference();
    }

    if (TRACE_DETAIL)  VM.sysWrite(" => ",newReference);

    if (trace.isLive(oldReferent)) {
      if (VM.VerifyAssertions) {
        if (!DebugUtil.validRef(oldReferent)) {
          VM.sysWriteln("Error in old referent.");
          DebugUtil.dumpRef(oldReferent);
          VM.sysFail("Invalid reference");
        }
      }
      /*
       * Referent is still reachable in a way that is as strong as
       * or stronger than the current reference level.
       */
      ObjectReference newReferent = trace.getForwardedReferent(oldReferent);

      if (TRACE_DETAIL) VM.sysWriteln(" ~> ",newReferent);

      if (VM.VerifyAssertions) {
        if (!DebugUtil.validRef(newReferent)) {
          VM.sysWriteln("Error forwarding reference object.");
          DebugUtil.dumpRef(oldReferent);
          VM.sysFail("Invalid reference");
        }
        VM._assert(trace.isLive(newReferent));
      }

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

      if (TRACE_DETAIL) VM.sysWriteln(" UNREACHABLE");
      else if (TRACE_UNREACHABLE) VM.sysWriteln(" UNREACHABLE referent:  ",oldReferent);

      clearReferent(newReference);
      enqueueReference(newReference);
      return ObjectReference.nullReference();
    }
  }

  /**
   * Weak and soft references always clear the referent
   * before enqueueing. We don't actually call
   * {@code Reference.clear()} as the user could have overridden the
   * implementation and we don't want any side-effects to
   * occur.
   *
   * @param newReference the reference whose referent is to
   *  be cleared
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
    if (VM.VerifyAssertions) VM._assert(!object.isNull());
    return object.toAddress().loadObjectReference(Entrypoints.referenceReferentField.getOffset());
  }

  /**
   * Set the referent in a reference.  For Java the reference is
   * a Reference object.
   * @param ref the ObjectReference for the reference (confusing eh?).
   * @param referent the referent object reference.
   */
  protected void setReferent(ObjectReference ref, ObjectReference referent) {
    ref.toAddress().store(referent, Entrypoints.referenceReferentField.getOffset());
  }

  /***********************************************************************
   *
   * Statistics and debugging
   */

  @Override
  public int countWaitingReferences() {
    return maxIndex;
  }
}
