/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.vmInterface;

import com.ibm.JikesRVM.memoryManagers.JMTk.Lock;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_Processor;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_Thread;
import com.ibm.JikesRVM.VM_Memory;
import com.ibm.JikesRVM.VM_Time;
import com.ibm.JikesRVM.VM_Synchronizer;

/**
 * This class manages finalization.  When an object is created
 * if its class has a finalize() method, addElement below is 
 * called, and a VM_FinalizerListElement (see VM_FinalizerListElement)
 * is created for it.  While the object is old, the integer field of 
 * the list element holds its value (this does not keep the object 
 * live during gc.  At the end of gc, the list of VM_FinalizerListElements
 * is scanned for objects which have become garbage.  Those which have
 * are made live again, by setting the ref field of the VM_FLE to the
 * object's address, and the VM_FLE is moved from the live objects list
 * to the to-be-finalized objects list.  A distinguished thread, the
 * Finalizer thread (see FinalizerThread.java) enqueues itself on 
 * the VM_Scheduler finalizerQueue.  At the end of gc, if VM_FLEs have 
 * been added to the to-be-finalized list, and if the VM_Scheduler
 * finalizerQueue is not empty, the finalizer thread is scheduled to be
 * run when gc is completed.
 *
 * @author Dick Attanasio
 * @author Stephen Smith
 */
public class VM_Finalizer implements VM_Uninterruptible {

  //----------------//
  // Implementation //
  //----------------//

  private static int INITIAL_SIZE = 32768;
  private static Lock lock = new Lock("Finalizer");
  private static int [] candidate = new int[INITIAL_SIZE];    // should be VM_Address [] but compiler does not support that type properly
  private static int candidateEnd;                            // candidate[0] .. candidate[candidateEnd-1] contains non-zero entries
  private static Object [] live = new Object[INITIAL_SIZE];
  private static int liveStart;                               // live[liveStart] .. live[liveEnd-1] are the non-null entries
  private static int liveEnd;

  // Debug flags

  private static final boolean  TRACE	                = false;
  private static final boolean  TRACE_DETAIL            = false;

  //-----------//
  // interface //
  //-----------//

  // Add item.
  //
  // (SJF: This method must NOT be inlined into an inlined allocation sequence, since it contains a lock!)
  //
  public static final void addCandidate(Object item) throws VM_PragmaNoInline {
    lock.acquire();
    if (TRACE_DETAIL) VM_Scheduler.trace(" VM_Finalizer: ",
					 " addElement	called, count = ", candidateEnd);
    if (candidateEnd >= candidate.length) {
      VM.sysWrite("finalizer queue exceeded - increase size (", candidate.length);
      VM.sysWriteln(") or implement dynamic adjustment a la write buffer");
      VM._assert(false);
    }
    candidate[candidateEnd++] = VM_Magic.objectAsAddress(item).toInt();
    lock.release();
  }

  private static final void compactCandidates() {
    int leftCursor = 0;
    int rightCursor = candidateEnd - 1;
    // Invariant: Slots left of leftCursor are non-empty and slots right of rightCursor are empty
    while (true) {
      // Advance left cursor until it hits empty slot
      while (leftCursor < rightCursor && candidate[leftCursor] != 0)
	leftCursor++;
      // Back-advance right cursor until it hits non-empty slot
      while (rightCursor > leftCursor && candidate[rightCursor] == 0)
	rightCursor--;
      if (leftCursor >= rightCursor) // can be greater on first iteration if totally empty
	break;
      if (VM.VerifyAssertions) VM._assert(candidate[leftCursor] == 0);
      if (VM.VerifyAssertions) VM._assert(candidate[rightCursor] != 0);
      candidate[leftCursor] = candidate[rightCursor];
      candidate[rightCursor] = 0;
    }
    if (candidate[leftCursor] == 0)
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
      if (liveStart == 0) {
	VM.sysWriteln("finalizer's live queue exceeded - increase size or implement dynamic adjustment a la write buffer");
	VM._assert(false);
      }
      for (int i=liveStart; i<liveEnd; i++)
	live[i-liveStart] = live[i];
      for (int i=liveEnd - liveStart; i<live.length; i++)
	live[i] = null;
    }
    live[liveEnd++] = obj;
  }

  /**
   * Called from the mutator thread: return the first object queued 
   * on the finalize list, or null if none
   *
   * The aastore is actually uninterruptible since the target is an array of Objects.
   */
  public final static Object get() throws VM_PragmaLogicallyUninterruptible {

    if (liveStart == liveEnd) return null;

    Object obj = live[liveStart];
    live[liveStart++] = null;

    if (TRACE_DETAIL) 
      VM_Scheduler.trace(" VM_Finalizer: ", "get returning ", VM_Magic.objectAsAddress(obj));

    return obj;
  }

  /** 
   * Move all finalizable objects to the to-be-finalized queue
   * Called on shutdown
  */
  public final static void finalizeAll () {

    int cursor = 0;
    while (cursor < candidateEnd) {
      VM_Address cand = VM_Address.fromInt(candidate[cursor]);
      candidate[cursor] = 0;
      addLive(VM_Magic.addressAsObject(cand));
      cursor++;
    }
    
    compactCandidates();

    if (!VM_Scheduler.finalizerQueue.isEmpty()) {
      VM_Thread tt = VM_Scheduler.finalizerQueue.dequeue();
      VM_Processor.getCurrentProcessor().scheduleThread(tt);
    }
  }


  /**
   * Scan the array for objects which have become garbage
   * and move them to the Finalizable class
   */
  public final static int moveToFinalizable () {

    if (TRACE) VM_Scheduler.trace(" VM_Finalizer: "," move to finalizable ");

    int cursor = 0;
    int newFinalizeCount = 0;

    while (cursor < candidateEnd) {
      VM_Address cand = VM_Address.fromInt(candidate[cursor]);
      boolean isLive = VM_Interface.isLive(cand);
      VM_Address newObj = VM_Interface.processPtrValue(cand);
      if (isLive) {
	// live beforehand but possibly moved
	candidate[cursor] = newObj.toInt();
      }
      else {
	// died and revived, needs finalization now
	candidate[cursor] = 0;
	addLive(VM_Magic.addressAsObject(newObj));
	newFinalizeCount++;
      }
      cursor++;
    }
    
    compactCandidates();

    return newFinalizeCount;
  }  // moveToFinalizable


  // Schedule the finalizerThread, if there are objects to be finalized
  // and the finalizerThread is on its queue (ie. currently idle).
  // Should be called at the end of GC after moveToFinalizable has been called,
  // and before mutators are allowed to run.
  //
  static void schedule () throws VM_PragmaUninterruptible {
    if ((countToBeFinalized() > 0) && !VM_Scheduler.finalizerQueue.isEmpty()) {
      VM_Thread t = VM_Scheduler.finalizerQueue.dequeue();
      VM_Processor.getCurrentProcessor().scheduleThread(t);
    }
  }

  // methods for statistics and debugging

  static int countHasFinalizer() {
    return candidateEnd;
  }

  static int countToBeFinalized() {
    return liveEnd - liveStart;
  }

}
