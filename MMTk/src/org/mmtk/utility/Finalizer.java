/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package org.mmtk.utility;

import org.mmtk.plan.TraceLocal;
import org.mmtk.vm.Lock;
import org.mmtk.vm.VM;
import org.mmtk.vm.SynchronizedCounter;
import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class manages finalization.  When an object is created if its
 * class has a finalize() method, addElement below is called, and a
 * FinalizerListElement (see FinalizerListElement) is created for it.
 * While the object is old, the integer field of the list element
 * holds its value (this does not keep the object live during gc.  At
 * the end of gc, the list of FinalizerListElements is scanned for
 * objects which have become garbage.  Those which have are made live
 * again are moved to the live object list for finalization.
 * 
 * Elsewhere, there is a distinguished Finalizer thread which 
 * enqueues itself on the VM_Scheduler finalizerQueue.  At the end of gc, 
 * if needed and if the VM_Scheduler finalizerQueue is not empty, 
 * the finalizer thread is scheduled to be run when gc is completed.
 * 
 * @author Perry Cheng
 */
@Uninterruptible public class Finalizer {

  // ----------------//
  // Implementation //
  // ----------------//

  private static int INITIAL_SIZE = 32768;
  private static double growthFactor = 2.0;
  private static final Lock lock = VM.newLock("Finalizer");
  private static final SynchronizedCounter gcLock = VM.newSynchronizedCounter();
  
  /* Use an AddressArray rather than ObjectReference array to *avoid* this
     being traced.  We don't want this array to keep the candiates alive */
  private static AddressArray candidate = AddressArray.create(INITIAL_SIZE);
  private static int candidateEnd;                            // candidate[0] .. candidate[candidateEnd-1] contains non-zero entries
  private static ObjectReferenceArray live = ObjectReferenceArray.create(INITIAL_SIZE);
  private static int liveStart;                               // live[liveStart] .. live[liveEnd-1] are the non-null entries
  private static int liveEnd;

  // -----------//
  // interface //
  // -----------//

  // Add item.
  //
  @Interruptible
  @NoInline
  // (SJF: This method must NOT be inlined into an inlined allocation sequence, since it contains a lock!)
  //
  public static final void addCandidate(ObjectReference item) { 
 
    /* The following is tricky due to its littering of deadlock potential and
     * thread (logical and physical) race conditions, hence the unusual comment
     * verbosity.
     * 
     * If we know we need to expand newCandidate array, then do a preventive
     * full collection. This prevents two-stage deadlock arising when 
     * AddressArray.create(int) must adjust page tables, requiring acquiring
     * the immortal mutator lock which might be held by another thread which 
     * attempts to add a finalizer candidate also while simultaneously holding
     * the immortal mutator lock. 
     */
    if (candidateEnd >= candidate.length()) {
      /* must guard against multiple logical or physical processors executing
       * mutator threads which enter this method concurrently. the first thread
       * in should commence the collection; all subsequent threads must block,
       * until the GC succeeds. Note we cannot invoke System.gc() here, since
       * that method will return immediately if collection is already in-
       * progress.
       */
      if (gcLock.increment() == 0) {		// lead concurrent mutator
 	    /* Note the above lock is insufficient to guarantee that only a single
 	     * thread is in explicit collection (since another thread could have
 	     * executed System.gc() directly). Thus, use System.gc() here, rather than 
 	     * org.mmtk.vm.Collection.triggerCollection(org.mmtk.vm.Collection.EXTERNAL_GC_TRIGGER),
 	     * to prevent collection from being concurrently executed. See RVM 
 	     * bug 1511447 and RVM patch 1512948 for more detail.
 	     */
     	try {
          System.gc();
     	} finally {
          // release trailing concurrent mutators (in while-loop below)
          gcLock.reset();
     	}
      } else {		// trailing concurrent mutators
        // wait until lead thread finishes above collection
        while (gcLock.peek() != 0) {
          /* must do a yielded-spin, since the concurrent
           * thread may be on the same physically processor.
           * plus, gc may take a long time and we should
           * hog as few cycles as possible while we wait patiently.
           */    		  
          Thread.yield();
        }
      }
    }
 
    try {
      lock.acquire();
      int origLength = candidate.length();
      if (candidateEnd >= origLength) {
        /* the above explicit collection will ensure this
         * does not deadlock due to necessity for physical
         * memory management (e.g. virtual page allocation)
         * to satisfy this allocation request.
         */
        AddressArray newCandidate = AddressArray.create((int) (growthFactor * origLength));
        for (int i=0; i<origLength; i++) {
          newCandidate.set(i, candidate.get(i));
        }
        candidate = newCandidate;
      }
      candidate.set(candidateEnd++, item.toAddress());
    } finally {
      lock.release();
    }
  }

  private static final void compactCandidates() {
    int leftCursor = 0;
    int rightCursor = candidateEnd - 1;
    // Invariant: Slots left of leftCursor are non-empty and slots right of rightCursor are empty
    while (true) {
      // Advance left cursor until it hits empty slot
      while (leftCursor < rightCursor && !candidate.get(leftCursor).isZero())
        leftCursor++;
      // Back-advance right cursor until it hits non-empty slot
      while (rightCursor > leftCursor && candidate.get(rightCursor).isZero())
        rightCursor--;
      if (leftCursor >= rightCursor) // can be greater on first iteration if totally empty
        break;
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(candidate.get(leftCursor).isZero() && !candidate.get(rightCursor).isZero());
      candidate.set(leftCursor, candidate.get(rightCursor));
      candidate.set(rightCursor, Address.zero());
    }
    if (candidate.get(leftCursor).isZero())
      candidateEnd = leftCursor;
    else
      candidateEnd = leftCursor + 1;
  }

  /* Add revived object that needs to be finalized
   * 
   * The aastore is actually uninterruptible since the target is an array of Objects.
   */
  @LogicallyUninterruptible
  private static void addLive(ObjectReference obj) { 
    if (liveEnd == live.length()) {
      ObjectReferenceArray newLive = live;
      if (liveStart == 0)
        newLive = ObjectReferenceArray.create((int) (growthFactor * live.length()));
      for (int i = liveStart; i < liveEnd; i++)
        newLive.set(i - liveStart, live.get(i));
      for (int i = liveEnd - liveStart; i < live.length(); i++)
        newLive.set(i, ObjectReference.nullReference());
      liveEnd -= liveStart;
      liveStart = 0;
      live = newLive;
    }
    live.set(liveEnd++, obj);
  }

  /**
   * Called from the mutator thread: return the first object queued on
   * the finalize list, or null if none
   * 
   * The aastore is actually uninterruptible since the target is an
   * array of Objects.
   */
  @LogicallyUninterruptible
  public final static ObjectReference get() { 

    if (liveStart == liveEnd) return ObjectReference.nullReference();

    ObjectReference obj = live.get(liveStart);
    live.set(liveStart++, ObjectReference.nullReference());

    return obj;
  }

  /**
   * Move all finalizable objects to the to-be-finalized queue
   * Called on shutdown.  Caller must also scheduler the finalizer thread.
   */
  public final static void finalizeAll() {

    int cursor = 0;
    while (cursor < candidateEnd) {
      Address cand = candidate.get(cursor);
      candidate.set(cursor, Address.zero());
      addLive(cand.toObjectReference());
      cursor++;
    }

    compactCandidates();

  }


  public final static void kill() {
    candidateEnd = 0;
  }


  /**
   * Scan the array for objects which have become finalizable and move
   * them to the Finalizable class
   * 
   * @param trace The trace instance to use.
   */
  public final static int moveToFinalizable(TraceLocal trace) {
    int cursor = 0;
    int newFinalizeCount = 0;

    while (cursor < candidateEnd) {
      Address cand = candidate.get(cursor);
      boolean isFinalizable = trace.readyToFinalize(cand.toObjectReference());
      if (isFinalizable) { // object died, enqueue for finalization
        candidate.set(cursor, Address.zero());
        addLive(trace.retainForFinalize(cand.toObjectReference()));
        newFinalizeCount++;
      } else { // live beforehand but possibly moved
        candidate.set(cursor, trace.getForwardedFinalizable(cand.toObjectReference()).toAddress());
      }
      cursor++;
    }
    compactCandidates();

    return newFinalizeCount;
  } // moveToFinalizable

  /**
   * Scan the array for objects which have become finalizable and move
   * them to the Finalizable class
   * 
   * @param trace The trace object to use for forwarding.
   */
  @Inline
  public final static void forward(TraceLocal trace) { 
    int cursor = 0;

    while (cursor < candidateEnd) {
      Address cand = candidate.get(cursor);
      ObjectReference newCandidate = trace.getForwardedFinalizable(cand.toObjectReference()); 
      candidate.set(cursor, newCandidate.toAddress());
      cursor++;
    }
  }

  
  // methods for statistics and debugging

  static int countHasFinalizer() {
    return candidateEnd;
  }

  public static int countToBeFinalized() {
    return liveEnd - liveStart;
  }

}
