/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

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
package com.ibm.JikesRVM.memoryManagers;

import VM;
import VM_Address;
import VM_Magic;
import VM_ObjectModel;
import VM_ClassLoader;
import VM_SystemClassLoader;
import VM_Atom;
import VM_Type;
import VM_Class;
import VM_Array;
import VM_Method;
import VM_PragmaInline;
import VM_PragmaNoInline;
import VM_PragmaUninterruptible;
import VM_PragmaLogicallyUninterruptible;
import VM_Processor;
import VM_Scheduler;
import VM_Thread;
import VM_Memory;
import VM_Time;
import VM_Entrypoints;
import VM_Reflection;
import VM_Synchronization;
import VM_Synchronizer;
import VM_EventLogger;

public class VM_Finalizer {

  //----------------//
  // Implementation //
  //----------------//

  public static boolean finalizeOnExit = false;
  static VM_FinalizerListElement live_head;
  static VM_FinalizerListElement finalize_head;
  static int live_count;
  static int finalize_count;
  private static VM_Synchronizer locker = new VM_Synchronizer();
  static boolean foundFinalizableObject;
  static int foundFinalizableCount = 0;    // set by each call to moveToFinalizable

  // Debug flags

  private static final boolean  TRACE	                = false;
  private static final boolean  TRACE_DETAIL            = false;
  private static final boolean  PRINT_FINALIZABLE_COUNT = false;

  //-----------//
  // interface //
  //-----------//

  // Add item.
  //
  public static final void addElement(Object item) throws VM_PragmaNoInline, VM_PragmaUninterruptible {
    // (SJF: This method must NOT be inlined into an inlined allocation
    // sequence, since it contains a lock!)
    synchronized (locker) {
      live_count++;
      if (TRACE_DETAIL) VM_Scheduler.trace(" VM_Finalizer: ",
					   " addElement	called, count = ", live_count);
      VM_FinalizerListElement le = new VM_FinalizerListElement(item);
      VM_FinalizerListElement old = live_head;
      live_head = le;
      le.next	= old; 
    }		// synchronized
  }


  /**
   * Called from the mutator thread: return the first object queued 
   * on the finalize list, or null if none
   */
  public final static Object get() throws VM_PragmaUninterruptible {
    VM_FinalizerListElement temp = finalize_head;
    if (temp == null) return null;
    //
    finalize_head = temp.next;
    finalize_count--;
    if (TRACE_DETAIL) {  
      VM_Scheduler.trace(" VM_Finalizer: ", "get returning ", 
			 VM_Magic.objectAsAddress(temp.pointer));
      VM_Scheduler.trace(" VM_Finalizer: ", "finalize count is ", 
			 finalize_count);
    }
    return temp.pointer;
  }

  /**
   * return true if there are objects with finalizers
   * potentially needing to be finalized: called by
   * gcs at the end of a collection to determine whether 	
   * to call moveToFinalizable()
   */
  public final static boolean existObjectsWithFinalizers() throws VM_PragmaUninterruptible {
    return (live_head != null);
  }

  /** 
   * Move all finalizable objects to the to-be-finalized queue
   * Called on shutdown
  */
  public final static void finalizeAll () throws VM_PragmaUninterruptible {
    VM_FinalizerListElement le	= live_head;
    VM_FinalizerListElement from = live_head;
    while (le != null) {
	live_count--;
	finalize_count++;

	if (TRACE) VM_Scheduler.trace("\n in finalizeall:","le.value =",
					 VM_Magic.objectAsAddress(le.pointer));

	// take this le out of the live_list
	//
	VM_FinalizerListElement current = le.next;
	if (le == live_head)
	  live_head = current;
	else
	  from.next = current;

	// put this le into the finalize_list
	//
	VM_FinalizerListElement temp = finalize_head;
	finalize_head = le;
	le.next = temp;

	le = current;
    }  // while le != null

      if (!VM_Scheduler.finalizerQueue.isEmpty()) {
	VM_Thread tt = VM_Scheduler.finalizerQueue.dequeue();
	VM_Processor.getCurrentProcessor().scheduleThread(tt);
      }
  }

  /**
   * Scan the array for objects which have become garbage
   * and move them to the Finalizable class
   */
  public final static void moveToFinalizable () throws VM_PragmaUninterruptible {
    if (TRACE) VM_Scheduler.trace(" VM_Finalizer: "," move to finalizable ");
    boolean added = false;
    boolean is_live = false;
    foundFinalizableObject = false;
    foundFinalizableCount = 0;

    int initial_finalize_count = finalize_count;
    VM_FinalizerListElement le	= live_head;
    VM_FinalizerListElement from = live_head;

    while (le != null) {
      is_live = VM_Allocator.processFinalizerListElement(le);
      if (is_live) {
	from = le;
	le = le.next;
	continue;
      } else {
	// associated object is dead
	added = true;
	live_count--;
	finalize_count++;

	if (TRACE) VM_Scheduler.trace("\n moving to finalizer:","le.value =",
				      VM_Magic.objectAsAddress(le.pointer));

	// take this le out of the live_list
	//
	VM_FinalizerListElement current = le.next;
	if (le == live_head)
	  live_head = current;
	else
	  from.next = current;

	// put this le into the finalize_list
	//
	VM_FinalizerListElement temp = finalize_head;
	finalize_head = le;
	le.next = temp;

	le = current;
      }
    }  // while le != null

    if (added) {
      if (TRACE) VM_Scheduler.trace(" VM_Finalizer: ", " added was true");
      
      // tested in garbage collectors
      foundFinalizableObject = true;
      foundFinalizableCount = finalize_count - initial_finalize_count;
      if (!VM_Scheduler.finalizerQueue.isEmpty()) {
	VM_Thread tt = VM_Scheduler.finalizerQueue.dequeue();
	VM_Processor.getCurrentProcessor().scheduleThread(tt);
      }
    }
    
    if (PRINT_FINALIZABLE_COUNT && VM_Allocator.verbose >= 1) {
      VM.sysWrite("<GC ");
      VM.sysWrite(VM_Collector.collectionCount(),false);
      VM.sysWrite(" moveToFinalizable: finalize_count: before = ");
      VM.sysWrite(initial_finalize_count,false);
      VM.sysWrite(" after = ");
      VM.sysWrite(finalize_count,false);
      VM.sysWrite(">\n");
    }
  }  // moveToFinalizable


  // Schedule the finalizerThread, if there are objects to be finalized
  // and the finalizerThread is on its queue (ie. currently idle).
  // Should be called at the end of GC after moveToFinalizable has been called,
  // and before mutators are allowed to run.
  //
  static void schedule () throws VM_PragmaUninterruptible {
    if ((finalize_head != null) && !VM_Scheduler.finalizerQueue.isEmpty()) {
      VM_Thread t = VM_Scheduler.finalizerQueue.dequeue();
      VM_Processor.getCurrentProcessor().scheduleThread(t);
    }
  }

  // methods for statistics and debugging

  static int countHasFinalizer() throws VM_PragmaUninterruptible {
    int count = 0;
    VM_FinalizerListElement le = live_head;
    while (le != null) {
      count++;
      le = le.next;
    }
    return count;
  }

  static int countToBeFinalized() throws VM_PragmaUninterruptible {
    int count = 0;
    VM_FinalizerListElement le = finalize_head;
    while (le != null) {
      count++;
      le = le.next;
    }
    return count;
  }

  /**
   * A debugging routine: print out the type of each object in live_list
   */
  public final static void dump_live() throws VM_PragmaUninterruptible {
    VM_Scheduler.trace(" VM_Finalizer.dump_live", "cnt is ", live_count);
    VM.sysWrite("\n");
    VM_FinalizerListElement le = live_head;
    while (le != null) {
      VM.sysWrite(" In live_list: object type is ");
      VM_GCUtil.printclass(le.value);
      VM.sysWrite(" at ");
      VM.sysWrite(le.value);
      VM.sysWrite("\n");
      le = le.next;
    }
  }

  /** 
   * A debugging routine: print out the type of each object to be finalized
   */
  public final static void dump_finalize() throws VM_PragmaUninterruptible {
    VM_Scheduler.trace(" VM_Finalizer.dump_finalize", "cnt is ", finalize_count);
    VM.sysWrite("\n");
    VM_FinalizerListElement le = finalize_head;
    while (le != null) {
      VM.sysWrite(" In finalize_list: object type is ");
      VM_GCUtil.printclass(le.value);
      VM.sysWrite(" at ");
      VM.sysWrite(le.value);
      VM.sysWrite("\n");
      le = le.next;
    }
  }
}
