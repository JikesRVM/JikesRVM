/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 *
 * (C) Copyright IBM Corp. 2001, 2003
 */
package org.mmtk.vm;

import org.mmtk.plan.TraceLocal;
import org.mmtk.utility.deque.*;
import org.mmtk.utility.scan.*;
import org.mmtk.utility.Constants;
import org.mmtk.vm.ObjectModel;

import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;
import com.ibm.JikesRVM.memoryManagers.mmInterface.VM_CollectorThread;
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
  private static final boolean TRACE_PRECOPY = false; // DEBUG

  /** Counter to track index into thread table for root tracing.  */
  private static SynchronizedCounter threadCounter = new SynchronizedCounter();

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
  }

  /**
   * Delegated scanning of a object, processing each pointer field
   * encountered. <b>Jikes RVM never delegates, so this is never
   * executed</b>.
   *
   * @param object The object to be scanned.
   */
  public static void scanObject(TraceLocal trace, ObjectReference object) 
    throws UninterruptiblePragma, InlinePragma {
    // Never reached
    if (VM.VerifyAssertions) VM._assert(false);
  }
  
  /**
   * Delegated precopying of a object's children, processing each pointer field
   * encountered. <b>Jikes RVM never delegates, so this is never
   * executed</b>.
   *
   * @param object The object to be scanned.
   */
  public static void precopyChildren(TraceLocal trace, ObjectReference object) 
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
   * @param _enum the Enumerator object through which the trace
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
   * instances to be decoupled. 
   * 
   * The thread table is scanned in parallel by each processor, by striding
   * through the table at a gap of chunkSize*numProcs.  Feel free to adjust
   * chunkSize if you want to tune a parallel collector.
   * 
   * Explicitly no-inlined to prevent over-inlining of collectionPhase.
   * 
   * TODO Experiment with specialization to remove virtual dispatch ?
   */
  public static void preCopyGCInstances(TraceLocal trace) 
  throws NoInlinePragma {
    int chunkSize = 2;
    int threadIndex, start, end, stride;
    VM_CollectorThread ct;
    
    stride = chunkSize * VM_CollectorThread.numCollectors();
    ct = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());
    start = (ct.getGCOrdinal() - 1) * chunkSize;
    
    int numThreads = VM_Scheduler.threadHighWatermark+1;
    if (TRACE_PRECOPY)
      VM.sysWriteln(ct.getGCOrdinal()," preCopying ",numThreads," threads");
    
    ObjectReference threadTable = ObjectReference.fromObject(VM_Scheduler.threads);
    while (start < numThreads) {
      end = start + chunkSize;
      if (end > numThreads)
        end = numThreads;      // End of the table - partial chunk
      if (TRACE_PRECOPY) {
        VM.sysWriteln(ct.getGCOrdinal()," Chunk start",start);
        VM.sysWriteln(ct.getGCOrdinal()," Chunk end  ",end);
      }
      for (threadIndex = start; threadIndex < end; threadIndex++) {
        VM_Thread thread = VM_Scheduler.threads[threadIndex];
        if (thread != null) {
          /* Copy the thread object - use address arithmetic to get the address 
           * of the array entry */
          if (TRACE_PRECOPY) {
            VM.sysWriteln(ct.getGCOrdinal()," Forwarding thread ",threadIndex);
            VM.sysWrite(ct.getGCOrdinal()," Old address ");
            VM.sysWriteln(ObjectReference.fromObject(thread).toAddress());
          }
          Address threadTableSlot = threadTable.toAddress().add(threadIndex<<LOG_BYTES_IN_ADDRESS);
          if (Assert.VERIFY_ASSERTIONS) 
            Assert._assert(ObjectReference.fromObject(thread).toAddress().EQ(
                threadTableSlot.loadObjectReference().toAddress()),
            "Thread table address arithmetic is wrong!");
          trace.precopyObjectLocation(threadTableSlot);
          thread = VM_Scheduler.threads[threadIndex];  // reload  it - it just moved!
          if (TRACE_PRECOPY) {
            VM.sysWrite(ct.getGCOrdinal()," New address ");
            VM.sysWriteln(ObjectReference.fromObject(thread).toAddress());
          }
          precopyChildren(trace,thread);
          precopyChildren(trace,thread.contextRegisters);
          precopyChildren(trace,thread.hardwareExceptionRegisters);
          if (thread.jniEnv != null) {
            // Right now, jniEnv are Java-visible objects (not C-visible)
            // if (VM.VerifyAssertions)
            //   VM._assert(Plan.willNotMove(VM_Magic.objectAsAddress(thread.jniEnv)));
            precopyChildren(trace,thread.jniEnv);
          }
        }
      } // end of for loop
      start = start + stride;
    }
  }
  
 
  /**
   * Enumerator the pointers in an object, calling back to a given plan
   * for each pointer encountered. <i>NOTE</i> that only the "real"
   * pointer fields are enumerated, not the TIB.
   *
   * @param object The object to be scanned.
   * @param _enum the Enumerator object through which the trace
   * is made
   */
  private static void precopyChildren(TraceLocal trace, Object object) 
    throws UninterruptiblePragma, InlinePragma {
    Scan.precopyChildren(trace,ObjectReference.fromObject(object));
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
   * TODO rewrite to avoid the per-thread synchronization, like precopy.
   *
   * @param rootLocations set to store addresses containing roots
   * @param interiorRootLocations set to store addresses containing
   * return adddresses, or <code>null</code> if not required
   */
  public static void computeAllRoots(TraceLocal trace) {
    boolean processCodeLocations = MM_Interface.MOVES_OBJECTS;
     /* scan statics */
    ScanStatics.scanStatics(trace);
 
    /* scan all threads */
    while (true) {
      int threadIndex = threadCounter.increment();
      if (threadIndex > VM_Scheduler.threadHighWatermark) break;
      
      VM_Thread thread = VM_Scheduler.threads[threadIndex];
      if (thread == null) continue;
      
      /* scan the thread (stack etc.) */
      ScanThread.scanThread(thread, trace, processCodeLocations);

      /* identify this thread as a root */
      trace.addRootLocation(VM_Magic.objectAsAddress(VM_Scheduler.threads).add(threadIndex<<LOG_BYTES_IN_ADDRESS));
    }
    Collection.rendezvous(4200);
  }

}
