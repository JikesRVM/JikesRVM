/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Lock;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_AddressArray;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;

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
public class Finalizer implements VM_Uninterruptible {

  //----------------//
  // Implementation //
  //----------------//

  private static int INITIAL_SIZE = 32768;
  private static double growthFactor = 2.0;
  private static Lock lock = new Lock("Finalizer");
  private static VM_AddressArray candidate = VM_AddressArray.create(INITIAL_SIZE);
  private static int candidateEnd;                            // candidate[0] .. candidate[candidateEnd-1] contains non-zero entries
  private static Object [] live = new Object[INITIAL_SIZE];
  private static int liveStart;                               // live[liveStart] .. live[liveEnd-1] are the non-null entries
  private static int liveEnd;

  // Debug flags

  private static final boolean  TRACE                   = false;
  private static final boolean  TRACE_DETAIL            = false;

  //-----------//
  // interface //
  //-----------//

  // Add item.
  //
  // (SJF: This method must NOT be inlined into an inlined allocation sequence, since it contains a lock!)
  //
  public static final void addCandidate(Object item) throws VM_PragmaNoInline, VM_PragmaInterruptible  {
    lock.acquire();

    int origLength = candidate.length();
    if (candidateEnd >= origLength) {
      VM_AddressArray newCandidate = VM_AddressArray.create((int) (growthFactor * origLength));
      for (int i=0; i<origLength; i++)
        newCandidate.set(i, candidate.get(i));
      candidate = newCandidate;
    }
    candidate.set(candidateEnd++, VM_Magic.objectAsAddress(item));
    lock.release();
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
      if (VM_Interface.VerifyAssertions) 
        VM_Interface._assert(candidate.get(leftCursor).isZero() && !candidate.get(rightCursor).isZero());
      candidate.set(leftCursor, candidate.get(rightCursor));
      candidate.set(rightCursor, VM_Address.zero());
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
  private static void addLive(Object obj) throws VM_PragmaLogicallyUninterruptible {
    if (liveEnd == live.length) {
      Object[] newLive = live;
      if (liveStart == 0) 
        newLive = new Object[(int) (growthFactor * live.length)];
      for (int i=liveStart; i<liveEnd; i++)
        newLive[i-liveStart] = live[i];
      for (int i=liveEnd - liveStart; i<live.length; i++)
        newLive[i] = null;
      liveEnd -= liveStart;
      liveStart = 0;
      live = newLive;
    }
    live[liveEnd++] = obj;
  }

  /**
   * Called from the mutator thread: return the first object queued on
   * the finalize list, or null if none
   *
   * The aastore is actually uninterruptible since the target is an
   * array of Objects.
   */
  public final static Object get() throws VM_PragmaLogicallyUninterruptible {

    if (liveStart == liveEnd) return null;

    Object obj = live[liveStart];
    live[liveStart++] = null;

    return obj;
  }

  /** 
   * Move all finalizable objects to the to-be-finalized queue
   * Called on shutdown.  Caller must also scheduler the finalizer thread.
   */
  public final static void finalizeAll () {

    int cursor = 0;
    while (cursor < candidateEnd) {
      VM_Address cand = candidate.get(cursor);
      candidate.set(cursor, VM_Address.zero());
      addLive(VM_Magic.addressAsObject(cand));
      cursor++;
    }
    
    compactCandidates();

  }


  public final static void kill () {
      candidateEnd = 0; 
  }


  /**
   * Scan the array for objects which have become finalizable and move
   * them to the Finalizable class
   */
  public final static int moveToFinalizable () {
    int cursor = 0;
    int newFinalizeCount = 0;

    while (cursor < candidateEnd) {
      VM_Address cand = candidate.get(cursor);
      boolean isFinalizable = Plan.isFinalizable(cand);
      if (isFinalizable) { // object died, enqueue for finalization
        candidate.set(cursor, VM_Address.zero());
        addLive(VM_Magic.addressAsObject(Plan.retainFinalizable(cand)));
        newFinalizeCount++;
      } else {             // live beforehand but possibly moved
        candidate.set(cursor, Plan.getForwardedReference(cand));
      }
      cursor++;
    }
    compactCandidates();

    return newFinalizeCount;
  }  // moveToFinalizable


  // methods for statistics and debugging

  static int countHasFinalizer() {
    return candidateEnd;
  }

  public static int countToBeFinalized() {
    return liveEnd - liveStart;
  }

}
