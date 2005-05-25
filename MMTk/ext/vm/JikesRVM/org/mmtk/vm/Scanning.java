/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 *
 * (C) Copyright IBM Corp. 2001, 2003
 */
package org.mmtk.vm;

import org.mmtk.utility.deque.*;
import org.mmtk.utility.scan.*;
import org.mmtk.utility.Constants;

import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_Thread;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * $Id$ 
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Perry Cheng
 *
 * @version $Revision$
 * @date $Date$
 */
public class Scanning implements Constants, Uninterruptible {
  /****************************************************************************
   *
   * Class variables
   */

  /** Counter to track index into thread table for root tracing.  */
  private static SynchronizedCounter threadCounter = new SynchronizedCounter();
  /** An enumerator used to forward root objects */
  private static PreCopyEnumerator preCopyEnum;

  /***********************************************************************
   *
   * Initialization
   */

  /**
   * Initialization that occurs at <i>build</i> time.  The values of
   * statics at the completion of this routine will be reflected in
   * the boot image.  Any objects referenced by those statics will be
   * transitively included in the boot image.
   *
   * This is called from MM_Interface.
   */
  public static final void init() throws InterruptiblePragma {
    preCopyEnum = new PreCopyEnumerator();
  }

  /**
   * Delegated scanning of a object, processing each pointer field
   * encountered. <b>Jikes RVM never delegates, so this is never
   * executed</b>.
   *
   * @param object The object to be scanned.
   */
  public static void scanObject(ObjectReference object) 
    throws UninterruptiblePragma, InlinePragma {
    // Never reached
    if (VM.VerifyAssertions) VM._assert(false);
  }
  
  /**
   * Delegated enumeration of the pointers in an object, calling back
   * to a given plan for each pointer encountered. <b>Jikes RVM never
   * delegates, so this is never executed</b>.
   *
   * @param object The object to be scanned.
   * @param _enum the Enumerator object through which the callback
   * is made
   */
  public static void enumeratePointers(ObjectReference object, Enumerator _enum) 
    throws UninterruptiblePragma, InlinePragma {
    // Never reached
    if (VM.VerifyAssertions) VM._assert(false);
  }

  /**
   * Prepares for using the <code>computeAllRoots</code> method.  The
   * thread counter allows multiple GC threads to co-operatively
   * iterate through the thread data structure (if load balancing
   * parallel GC threads were not important, the thread counter could
   * simply be replaced by a for loop).
   */
  public static void resetThreadCounter() {
    threadCounter.reset();
  }

  /**
   * Pre-copy all potentially movable instances used in the course of
   * GC.  This includes the thread objects representing the GC threads
   * themselves.  It is crucial that these instances are forwarded
   * <i>prior</i> to the GC proper.  Since these instances <i>are
   * not</i> enqueued for scanning, it is important that when roots
   * are computed the same instances are explicitly scanned and
   * included in the set of roots.  The existence of this method
   * allows the actions of calculating roots and forwarding GC
   * instances to be decoupled. The <code>threadCounter</code> must be
   * reset so that load balancing parallel GC can share the work of
   * scanning threads.
   */
  public static void preCopyGCInstances() {
    /* pre-copy all thread objects in parallel */
    if (Collection.rendezvous(4201) == 1) /* one thread forwards the threads object */
      enumeratePointers(VM_Scheduler.threads, preCopyEnum);
    Collection.rendezvous(4202);
    while (true) {
      int threadIndex = threadCounter.increment();
      if (threadIndex >= VM_Scheduler.threads.length) break;
      VM_Thread thread = VM_Scheduler.threads[threadIndex];
      if (thread != null) {
        enumeratePointers(thread, preCopyEnum);
        enumeratePointers(thread.contextRegisters, preCopyEnum);
        enumeratePointers(thread.hardwareExceptionRegisters, preCopyEnum);
        if (thread.jniEnv != null) {
          // Right now, jniEnv are Java-visible objects (not C-visible)
          // if (VM.VerifyAssertions)
          //   VM._assert(Plan.willNotMove(VM_Magic.objectAsAddress(thread.jniEnv)));
          enumeratePointers(thread.jniEnv, preCopyEnum);
        }
      }
    }    
    Collection.rendezvous(4203);
  }
 
  /**
   * Enumerate the pointers in an object, calling back to a given plan
   * for each pointer encountered. <i>NOTE</i> that only the "real"
   * pointer fields are enumerated, not the TIB.
   *
   * @param object The object to be scanned.
   * @param _enum the Enumerate object through which the callback
   * is made
   */
  private static void enumeratePointers(Object object, Enumerator _enum) 
    throws UninterruptiblePragma, InlinePragma {
    Scan.enumeratePointers(ObjectReference.fromObject(object), _enum);
  }

 /**
   * Computes all roots.  This method establishes all roots for
   * collection and places them in the root values, root locations and
   * interior root locations queues.  This method should not have side
   * effects (such as copying or forwarding of objects).  There are a
   * number of important preconditions:
   *
   * <ul> 
   * <li> All objects used in the course of GC (such as the GC thread
   * objects) need to be "pre-copied" prior to calling this method.
   * <li> The <code>threadCounter</code> must be reset so that load
   * balancing parallel GC can share the work of scanning threads.
   * </ul>
   *
   * @param rootLocations set to store addresses containing roots
   * @param interiorRootLocations set to store addresses containing
   * return adddresses, or <code>null</code> if not required
   */
  public static void computeAllRoots(AddressDeque rootLocations,
                                     AddressPairDeque interiorRootLocations) {
    AddressPairDeque codeLocations = MM_Interface.MOVES_OBJECTS ? interiorRootLocations : null;
    
     /* scan statics */
    ScanStatics.scanStatics(rootLocations);
 
    /* scan all threads */
    while (true) {
      int threadIndex = threadCounter.increment();
      if (threadIndex >= VM_Scheduler.threads.length) break;
      
      VM_Thread thread = VM_Scheduler.threads[threadIndex];
      if (thread == null) continue;
      
      /* scan the thread (stack etc.) */
      ScanThread.scanThread(thread, rootLocations, codeLocations);

      /* identify this thread as a root */
      rootLocations.push(VM_Magic.objectAsAddress(VM_Scheduler.threads).add(threadIndex<<LOG_BYTES_IN_ADDRESS));
    }
    Collection.rendezvous(4200);
  }

}
