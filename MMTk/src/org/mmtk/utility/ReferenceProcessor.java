/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Offset;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Lock;
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.lang.ref.PhantomReference;



/**
 * This class manages SoftReferences, WeakReferences, and
 * PhantomReferences When a java/lang/ref/Reference object is created,
 * its address is added to a list of pending reference objects of the
 * appropriate type. An address is used so the reference will not stay
 * alive during gc if it isn't in use elsewhere the mutator. During
 * gc, the various lists are processed in the proper order to
 * determine if any Reference objects are ready to be enqueued or
 * whether referents that have died should be kept alive until the
 * Reference is explicitly cleared. References that should be enqueued
 * are stored in a special list for later processing.
 *
 * Elsewhere, there is a distinguished Finalizer thread which enqueues
 * itself on the VM_Scheduler finalizerQueue.  At the end of gc, if
 * needed and if any Reference queue is not empty, the finalizer
 * thread is scheduled to be run when gc is completed. This thread
 * calls Reference.enqueue() to make the actual notifcation to the
 * user program that the object state has changed.
 *
 * Loosely based on Finalizer.java
 *
 * 
 * @author Chris Hoffmann
 */
public class ReferenceProcessor implements VM_Uninterruptible {

  //----------------//
  // Implementation //
  //----------------//

  private static final int SOFT_SEMANTICS = 0;
  private static final int WEAK_SEMANTICS = 1;
  private static final int PHANTOM_SEMANTICS = 2;

  
  private VM_Address waitingListHead = VM_Address.zero();
  private Reference  readyListHead = null;
  private int countOnWaitingList = 0;
  private int countOnReadyList = 0;

  private static boolean clearSoftReferences = false;

  private static Lock lock = new Lock("ReferenceProcessor");

  private static ReferenceProcessor softReferenceProcessor = new ReferenceProcessor();
  private static ReferenceProcessor weakReferenceProcessor = new ReferenceProcessor();
  private static ReferenceProcessor phantomReferenceProcessor = new ReferenceProcessor();

  // Debug flags

  private static final boolean  TRACE		                 = false;
  private static final boolean  TRACE_DETAIL            = false;

  //-----------//
  // interface //
  //-----------//

  private ReferenceProcessor() {
    
  }

  // Add item.
  //
  // (SJF: This method must NOT be inlined into an inlined allocation sequence, since it contains a lock!)
  //
  private void addCandidate(Reference ref) throws VM_PragmaNoInline, VM_PragmaInterruptible {
    if (TRACE) {
        VM_Address referenceAsAddress = VM_Magic.objectAsAddress(ref);
        VM_Address referent = VM_Interface.getReferent(referenceAsAddress);
        Log.write("Adding Reference: "); Log.writeln(referenceAsAddress);
        Log.write("       ReferENT:  "); Log.writeln(referent);
    }
    
    lock.acquire();
    VM_Interface.setNextReferenceAsAddress(VM_Magic.objectAsAddress(ref),
					   waitingListHead);
    waitingListHead = VM_Magic.objectAsAddress(ref);
    countOnWaitingList += 1;    
    lock.release();
  }


  /**
     Traverse through the list of references on the waiting list and
     process them. If the reference is non-reachable, or it has been
     cleared, we no longer care about it. Otherwise, depending on the
     semantics of the type of reference, we may resurrect the referent
     and/or enqueue the reference object on its ReferenceDeque.
   */
  private int traverse(int semantics) throws VM_PragmaLogicallyUninterruptible {

    if (TRACE) {
      switch (semantics) {
      case SOFT_SEMANTICS:
        Log.writeln("Starting ReferenceProcessor.traverse(SOFT)");
        break;
      case WEAK_SEMANTICS:
        Log.writeln("Starting ReferenceProcessor.traverse(WEAK)");
        break;
      case PHANTOM_SEMANTICS:
        Log.writeln("Starting ReferenceProcessor.traverse(PHANTOM)");
        break;
      }
    }
    
    int enqueued = 0;
    int waiting = 0;
    VM_Address newHead = VM_Address.zero();
    VM_Address prevReference = VM_Address.zero();
    VM_Address reference = waitingListHead;
    
      
    while (!reference.isZero()) {
      if (TRACE) {
        Log.write("+++ old reference: "); Log.writeln(reference);
      }

      // If the reference is dead, we're done with it. Let it (and
      // possibly its referent) be garbage-collected.

      // Otherwise...
      if (Plan.isLive(reference)) {
        
        
        VM_Address newReference = getForwardingAddress(reference);
        VM_Address oldReferent = VM_Interface.getReferent(reference);

        if (TRACE_DETAIL) {
          Log.write("    new reference: "); Log.writeln(newReference);
          Log.write(" old referENT: "); Log.writeln(oldReferent);
        }
        
        // If the application has cleared the referent the Java spec says
        // this does not cause the Reference object to be enqueued. We
        // simply allow the Reference object to fall out of our
        // waiting list.

        if (!oldReferent.isZero()) {
          boolean enqueue = false;

          if (semantics == PHANTOM_SEMANTICS && !Plan.isLive(oldReferent))
            {
              // Keep phantomly reachable objects from being collected
              // until they are completely unreachable.
              if (TRACE_DETAIL) {
                Log.write("    resurrecting: "); Log.writeln(oldReferent);
              }
              makeAlive(oldReferent);
              if (!VM_Interface.referenceWasEverEnqueued((Reference)VM_Magic.addressAsObject(newReference))) {
              // Ensure phantomly reachable objects are enqueued only
              // the first time they become phantomly reachable
              enqueue = true;
              }
            }
          else if (semantics == SOFT_SEMANTICS && !clearSoftReferences) {
            // Unless we've completely run out of memory, we keep
            // softly reachable objects alive.
            if (TRACE_DETAIL) {
              Log.write("    resurrecting: "); Log.writeln(oldReferent);
            }
            makeAlive(oldReferent);
          }
          
          if (Plan.isLive(oldReferent)) {
            // Referent is still reachable in a way that is as strong
            // as or stronger than the current reference level.

            VM_Address newReferent = getForwardingAddress(oldReferent);

            if (TRACE) {
              Log.write(" new referENT: "); Log.writeln(newReferent);
            }
            
            // The reference object stays on the waiting list, and the
            // referent is untouched. The only thing we must do is
            // ensure that the former addresses are updated with the
            // new forwarding addresses in case the collector is a
            // copying collector.

            // Update the referent
            VM_Interface.setReferent(newReference, newReferent);

            // Update 'next' pointer of the previous reference in the
            // linked list of waiting references.
            if (!prevReference.isZero()) {
              VM_Interface.setNextReferenceAsAddress(prevReference, 
						     newReference);
            }
            
            waiting += 1;
            prevReference = newReference;
            if (newHead.isZero())
              newHead = newReference;
          }
          else {
            // Referent is unreachable.
            
            if (TRACE) {
              Log.write(" UNREACHABLE:  "); Log.writeln(oldReferent);
            }

            // Weak and soft references always clear the referent
            // before enqueueing. We don't actually call
            // Reference.clear() as the user could have overridden the
            // implementation and we don't want any side-effects to
            // occur.
            if (semantics != PHANTOM_SEMANTICS) {
              if (TRACE_DETAIL) {
                Log.write(" clearing: "); Log.writeln(oldReferent);
              }
              VM_Interface.setReferent(newReference, VM_Address.zero());
            }
            enqueue = true;
          }

          if (enqueue) {
            // If reference can now be added to its reference queue,
            // put it in a strongly-linked list of reference objects
            // for later processing.
            
            Reference referenceAsObject =
              (Reference) VM_Magic.addressAsObject(newReference);
            
            VM_Interface.enqueueReference(referenceAsObject);
            
            readyListHead = referenceAsObject;
            
            enqueued += 1;
          }
        }
      }
      reference = VM_Interface.getNextReferenceAsAddress(reference);
    }

    countOnWaitingList = waiting;
    countOnReadyList += enqueued;

    waitingListHead = newHead;
    
    if (!prevReference.isZero())
      VM_Interface.setNextReferenceAsAddress(prevReference, VM_Address.zero());

    if (TRACE) {
      Log.writeln("Ending ReferenceProcessor.traverse()");
    }

    return enqueued;
  }


  private static void makeAlive(VM_Address addr) {
    Plan.makeAlive(addr);    
  }

  // FIXME The following is never called.  Should it be?
  private static void makeDead (VM_Address addr) {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(false);
  }
  
  private static VM_Address getForwardingAddress(VM_Address addr) {
    return Plan.getForwardedReference(addr);
  }

  /** Set flag indicating if soft references referring to non-strongly
      reachable objects should be cleared during GC. Usually this is 
  false so the referent will stay alive. But when memory becomes
  scarce the collector should reclaim all such objects before it is
  forced to throw an OutOfMemory exception. Note that this flag
  applies only to the next collection. After each collection the
  setting is restored to false.
  */
  
  public static void setClearSoftReferences(boolean set) {
    clearSoftReferences = set;
  }

  public static void addSoftCandidate(SoftReference ref) throws VM_PragmaInterruptible {
    softReferenceProcessor.addCandidate(ref);
  }

  public static void  moveSoftReferencesToReadyList() {
    softReferenceProcessor.traverse(SOFT_SEMANTICS);
    clearSoftReferences = false;
  }
  
  public static void addWeakCandidate(WeakReference ref) throws VM_PragmaInterruptible {
    weakReferenceProcessor.addCandidate(ref);
  }
  
  public static void  moveWeakReferencesToReadyList() {
    weakReferenceProcessor.traverse(WEAK_SEMANTICS);
  }
  
  public static void addPhantomCandidate(PhantomReference ref) throws VM_PragmaInterruptible {
    phantomReferenceProcessor.addCandidate(ref);
  }
  
  public static void  movePhantomReferencesToReadyList() {
    phantomReferenceProcessor.traverse(PHANTOM_SEMANTICS);
  }

  // methods for statistics and debugging

  public static int countWaitingSoftReferences() {
    return softReferenceProcessor.countOnWaitingList;
  }

  public static int countReadySoftReferences() {
    return softReferenceProcessor.countOnReadyList;
  }

  public static int countWaitingWeakReferences() {
    return weakReferenceProcessor.countOnWaitingList;
  }

  public static int countReadyWeakReferences() {
    return weakReferenceProcessor.countOnReadyList;
  }

  public static int countWaitingPhantomReferences() {
    return phantomReferenceProcessor.countOnWaitingList;
  }

  public static int countReadyPhantomReferences() {
    return phantomReferenceProcessor.countOnReadyList;
  }


}
