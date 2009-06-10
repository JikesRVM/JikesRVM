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
package org.mmtk.utility;

import org.mmtk.plan.Plan;
import org.mmtk.plan.semispace.gctrace.GCTrace;
import org.mmtk.plan.TraceLocal;
import org.mmtk.policy.Space;
import org.mmtk.utility.deque.*;
import org.mmtk.utility.options.Options;
import org.mmtk.utility.options.TraceRate;

import org.mmtk.vm.VM;
import org.mmtk.vm.Collection;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * Class that supports scanning Objects and Arrays for references
 * during tracing, handling those references, and computing death times
 */
@Uninterruptible public final class TraceGenerator
  implements Constants, TracingConstants {


  /***********************************************************************
   *
   * Class variables
   */

  /* Type of lifetime analysis to be used */
  public static final boolean MERLIN_ANALYSIS = true;

  /* include the notion of build-time allocation to our list of allocators */
  private static final int ALLOC_BOOT = GCTrace.ALLOCATORS;
  private static final int ALLOCATORS = ALLOC_BOOT + 1;

  /* Fields for tracing */
  private static SortTODSharedDeque tracePool; // Buffers to hold raw trace
  private static TraceBuffer trace;
  private static boolean traceBusy; // If we are building the trace
  private static Word lastGC; // Last time GC was performed
  private static ObjectReferenceArray objectLinks; // Lists of active objs

  /* Fields needed for Merlin lifetime analysis */
  private static SortTODSharedDeque workListPool; // Holds objs to process
  private static SortTODObjectReferenceStack worklist; // Objs to process
  private static Word agePropagate; // Death time propagating

  static {
    traceBusy = false;
    lastGC = Word.fromIntZeroExtend(4);
    Options.traceRate = new TraceRate();
  }


  /***********************************************************************
   *
   * Public analysis methods
   */

  /**
   * This is called at "build-time" and passes the necessary build image
   * objects to the trace processor.
   *
   * @param worklist_ The dequeue that serves as the worklist for
   * death time propagation
   * @param trace_ The dequeue used to store and then output the trace
   */
  @Interruptible
  public static void init(SortTODSharedDeque worklist_,
                                SortTODSharedDeque trace_) {
    /* Objects are only needed for merlin tracing */
    if (MERLIN_ANALYSIS) {
      workListPool = worklist_;
      worklist = new SortTODObjectReferenceStack(workListPool);
    }

    /* Trace objects */
    tracePool = trace_;
    trace = new TraceBuffer(tracePool);
    objectLinks = ObjectReferenceArray.create(Space.MAX_SPACES);
  }

  /**
   * This is called immediately before Jikes terminates.  It will perform
   * any death time processing that the analysis requires and then output
   * any remaining information in the trace buffer.
   *
   * @param value The integer value for the reason Jikes is terminating
   */
  public static void notifyExit(int value) {
    if (MERLIN_ANALYSIS)
      findDeaths();
    trace.process();
  }

  /**
   * Add a newly allocated object into the linked list of objects in a region.
   * This is typically called after each object allocation.
   *
   * @param ref The address of the object to be added to the linked list
   * @param linkSpace The region to which the object should be added
   */
  public static void addTraceObject(ObjectReference ref, int linkSpace) {
    VM.traceInterface.setLink(ref, objectLinks.get(linkSpace));
    objectLinks.set(linkSpace, ref);
  }

  /**
   * Do the work necessary following each garbage collection. This HAS to be
   * called after EACH collection.
   */
  public static void postCollection() {
    /* Find and output the object deaths */
    traceBusy = true;
    findDeaths();
    traceBusy = false;
    trace.process();
  }


  /***********************************************************************
   *
   * Trace generation code
   */

  /**
   * Add the information in the bootImage to the trace.  This should be
   * called before any allocations and pointer updates have occured.
   *
   * @param bootStart The address at which the bootimage starts
   */
  public static void boot(Address bootStart) {
    Word nextOID = VM.traceInterface.getOID();
    ObjectReference trav = VM.traceInterface.getBootImageLink().plus(bootStart.toWord().toOffset()).toObjectReference();
    objectLinks.set(ALLOC_BOOT, trav);
    /* Loop through all the objects within boot image */
    while (!trav.isNull()) {
      ObjectReference next = VM.traceInterface.getLink(trav);
      Word thisOID = VM.traceInterface.getOID(trav);
      /* Add the boot image object to the trace. */
      trace.push(TRACE_BOOT_ALLOC);
      trace.push(thisOID);
      trace.push(nextOID.minus(thisOID).lsh(LOG_BYTES_IN_ADDRESS));
      nextOID = thisOID;
      /* Move to the next object & adjust for starting address of
         the bootImage */
      if (!next.isNull()) {
        next = next.toAddress().plus(bootStart.toWord().toOffset()).toObjectReference();
        VM.traceInterface.setLink(trav, next);
      }
      trav = next;
    }
  }

  /**
   * Do any tracing work required at each a pointer store operation.  This
   * will add the pointer store to the trace buffer and, when Merlin lifetime
   * analysis is being used, performs the necessary timestamping.
   *
   * @param isScalar If this is a pointer store to a scalar object
   * @param src The address of the source object
   * @param slot The address within <code>src</code> into which
   * <code>tgt</code> will be stored
   * @param tgt The target of the pointer store
   */
  @NoInline
  public static void processPointerUpdate(boolean isScalar,
                                          ObjectReference src,
                                          Address slot, ObjectReference tgt) {
    // The trace can be busy only if this is a pointer update as a result of
    // the garbage collection needed by tracing. For the moment, we will
    // not report these updates.
    if (!traceBusy) {
      /* Process the old target potentially becoming unreachable when needed. */
      if (MERLIN_ANALYSIS) {
        ObjectReference oldTgt = slot.loadObjectReference();
        if (!oldTgt.isNull())
          VM.traceInterface.updateDeathTime(oldTgt);
      }

      traceBusy = true;
      /* Add the pointer store to the trace */
      Offset traceOffset = VM.traceInterface.adjustSlotOffset(isScalar, src, slot);
      if (isScalar)
        trace.push(TRACE_FIELD_SET);
      else
        trace.push(TRACE_ARRAY_SET);
      trace.push(VM.traceInterface.getOID(src));
      trace.push(traceOffset.toWord());
      if (tgt.isNull())
        trace.push(Word.zero());
      else
        trace.push(VM.traceInterface.getOID(tgt));
      traceBusy = false;
    }
  }

  /**
   * Do any tracing work required at each object allocation. This will add the
   * object allocation to the trace buffer, triggers the necessary collection
   * work at exact allocations, and output the data in the trace buffer.
   *
   * @param ref The address of the object just allocated.
   * @param typeRef the type reference for the instance being created
   * @param bytes The size of the object being allocated
   */
  @LogicallyUninterruptible
  @NoInline
  public static void traceAlloc(boolean isImmortal, ObjectReference ref,
      ObjectReference typeRef, int bytes) {
    boolean gcAllowed = VM.traceInterface.gcEnabled() && Plan.isInitialized() && !Plan.gcInProgress();
    /* Test if it is time/possible for an exact allocation. */
    Word oid = VM.traceInterface.getOID(ref);
    Word allocType;
    if (gcAllowed && (oid.GE(lastGC.plus(Word.fromIntZeroExtend(Options.traceRate.getValue())))))
      allocType = TRACE_EXACT_ALLOC;
    else {
      allocType = TRACE_ALLOC;
    }
    /* Add the allocation into the trace. */
    traceBusy = true;
    /* When legally permissible, add the record to the trace buffer */
    if (MERLIN_ANALYSIS) {
       Address fp = (TraceBuffer.OMIT_ALLOCS) ? Address.zero() : VM.traceInterface.skipOwnFramesAndDump(typeRef);

       if (isImmortal && allocType.EQ(TRACE_EXACT_ALLOC))
         trace.push(TRACE_EXACT_IMMORTAL_ALLOC);
       else if (isImmortal)
         trace.push(TRACE_IMMORTAL_ALLOC);
       else
         trace.push(allocType);
       trace.push(VM.traceInterface.getOID(ref));
       trace.push(Word.fromIntZeroExtend(bytes - VM.traceInterface.getHeaderSize()));
       trace.push(fp.toWord());
       trace.push(Word.zero()); /* Magic.getThreadId() */
       trace.push(TRACE_TIB_SET);
       trace.push(VM.traceInterface.getOID(ref));
       trace.push(VM.traceInterface.getOID(typeRef));
    }
    /* Perform the necessary work for death times. */
    if (allocType.EQ(TRACE_EXACT_ALLOC)) {
      if (MERLIN_ANALYSIS) {
        lastGC = VM.traceInterface.getOID(ref);
        VM.traceInterface.updateTime(lastGC);
        VM.collection.triggerCollection(Collection.INTERNAL_GC_TRIGGER);
      } else {
        VM.collection.triggerCollection(Collection.RESOURCE_GC_TRIGGER);
        lastGC = VM.traceInterface.getOID(ref);
      }
    }
    /* Add the allocation record to the buffer if we have not yet done so. */
    if (!MERLIN_ANALYSIS) {
       Address fp = (TraceBuffer.OMIT_ALLOCS) ? Address.zero() : VM.traceInterface.skipOwnFramesAndDump(typeRef);
       if (isImmortal && allocType.EQ(TRACE_EXACT_ALLOC))
         trace.push(TRACE_EXACT_IMMORTAL_ALLOC);
       else if (isImmortal)
         trace.push(TRACE_IMMORTAL_ALLOC);
       else
         trace.push(allocType);
       trace.push(VM.traceInterface.getOID(ref));
       trace.push(Word.fromIntZeroExtend(bytes - VM.traceInterface.getHeaderSize()));
       trace.push(fp.toWord());
       trace.push(Word.zero()); /* Magic.getThreadId() */
       trace.push(TRACE_TIB_SET);
       trace.push(VM.traceInterface.getOID(ref));
       trace.push(VM.traceInterface.getOID(typeRef));
    }
    trace.process();
    traceBusy = false;
  }

  /***********************************************************************
   *
   * Merlin lifetime analysis methods
   */

  /**
   * This computes and adds to the trace buffer the unreachable time for
   * all of the objects that are _provably_ unreachable.  This method
   * should be called after garbage collection (but before the space has
   * been reclaimed) and at program termination.
   */
  private static void findDeaths() {
    /* Only the merlin analysis needs to compute death times */
    if (MERLIN_ANALYSIS) {
      /* Start with an empty stack. */
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(worklist.isEmpty());
      /* Scan the linked list of objects within each region */
      for (int allocator = 0; allocator < ALLOCATORS; allocator++) {
        ObjectReference thisRef = objectLinks.get(allocator);
        /* Start at the top of each linked list */
        while (!thisRef.isNull()) {
          /* Add the unreachable objects onto the worklist. */
          if (!getTraceLocal().isReachable(thisRef))
            worklist.push(thisRef);
          thisRef = VM.traceInterface.getLink(thisRef);
        }
      }
      /* Sort the objects on the worklist by their timestamp */
      if (!worklist.isEmpty())
        worklist.sort();
      /* Now compute the death times. */
      computeTransitiveClosure();
    }
    /* Output the death times for each object */
    for (int allocator = 0; allocator < ALLOCATORS; allocator++) {
      ObjectReference thisRef = objectLinks.get(allocator);
      ObjectReference prevRef = ObjectReference.nullReference(); // the last live object seen
      while (!thisRef.isNull()) {
        ObjectReference nextRef = VM.traceInterface.getLink(thisRef);
        /* Maintain reachable objects on the linked list of allocated objects */
        if (getTraceLocal().isReachable(thisRef)) {
          thisRef = getTraceLocal().getForwardedReference(thisRef);
          VM.traceInterface.setLink(thisRef, prevRef);
          prevRef = thisRef;
        } else {
          /* For brute force lifetime analysis, objects become
             unreachable "now" */
          Word deadTime;
          if (MERLIN_ANALYSIS)
            deadTime = VM.traceInterface.getDeathTime(thisRef);
          else
            deadTime = lastGC;
          /* Add the death record to the trace for unreachable objects. */
          trace.push(TRACE_DEATH);
          trace.push(VM.traceInterface.getOID(thisRef));
          trace.push(deadTime);
        }
        thisRef = nextRef;
      }
      /* Purge the list of unreachable objects... */
      objectLinks.set(allocator, prevRef);
    }
  }

  /**
   * This method is called for each root-referenced object at every Merlin
   * root enumeration.  The method will update the death time of the parameter
   * to the current trace time.
   *
   * @param obj The root-referenced object
   */
  public static void rootEnumerate(ObjectReference obj) {
    VM.traceInterface.updateDeathTime(obj);
  }

  /**
   * This propagates the death time being computed to the object passed as an
   * address. If we find the unreachable time for the parameter, it will be
   * pushed on to the processing stack.
   *
   * @param ref The address of the object to examine
   */
  public static void propagateDeathTime(ObjectReference ref) {
    /* If this death time is more accurate, set it. */
    if (VM.traceInterface.getDeathTime(ref).LT(agePropagate)) {
      /* If we should add the object for further processing. */
      if (!getTraceLocal().isReachable(ref)) {
        VM.traceInterface.setDeathTime(ref, agePropagate);
        worklist.push(ref);
      } else {
        VM.traceInterface.setDeathTime(getTraceLocal().getForwardedReference(ref), agePropagate);
      }
    }
  }

  /**
   * This finds all object death times by computing the (limited)
   * transitive closure of the dead objects.  Death times are computed
   * as the latest reaching death time to an object.
   */
  private static void computeTransitiveClosure() {
     if (!worklist.isEmpty()) {
       /* The latest time an object can die. */
       agePropagate = Word.max();
       /* Process through the entire buffer. */
       ObjectReference ref = worklist.pop();
       while (!ref.isNull()) {
         Word currentAge = VM.traceInterface.getDeathTime(ref);
         /* This is a cheap and simple test to process objects only once. */
         if (currentAge.LE(agePropagate)) {
           /* Set the "new" dead age. */
           agePropagate = currentAge;
           /* Scan the object, pushing the survivors */
           VM.scanning.scanObject(getTraceLocal(), ref);
         }
         /* Get the next object to process */
         ref = worklist.pop();
       }
     }
  }

  private static TraceLocal getTraceLocal() {
    return VM.activePlan.collector().getCurrentTrace();
  }

}
