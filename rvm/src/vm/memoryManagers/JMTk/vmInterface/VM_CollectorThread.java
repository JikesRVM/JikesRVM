/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.vmInterface;

// import com.ibm.JikesRVM.memoryManagers.JMTk.WorkQueue;
import com.ibm.JikesRVM.memoryManagers.JMTk.Plan;

import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_BootRecord;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_CompiledMethods;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaNoOptCompile;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_SysCall;
import com.ibm.JikesRVM.VM_Registers;
import com.ibm.JikesRVM.VM_Processor;
import com.ibm.JikesRVM.VM_Thread;
import com.ibm.JikesRVM.VM_Memory;
import com.ibm.JikesRVM.VM_Time;
import com.ibm.JikesRVM.VM_Entrypoints;
import com.ibm.JikesRVM.VM_Reflection;
import com.ibm.JikesRVM.VM_Synchronization;

/**
 * System thread used to preform garbage collections.
 *
 * These threads are created by VM.boot() at runtime startup.  One is created
 * for each VM_Processor that will (potentially) participate in garbage collection.
 * <pre>
 * Its "run" method does the following:
 *    1. wait for a collection request
 *    2. synchronize with other collector threads (stop mutation)
 *    3. reclaim space
 *    4. synchronize with other collector threads (resume mutation)
 *    5. goto 1
 * </pre>
 * Between collections, the collector threads reside on the VM_Scheduler
 * collectorQueue.  A collection in initiated by a call to the static collect()
 * method, which calls VM_Handshake requestAndAwaitCompletion() to dequeue
 * the collector threads and schedule them for execution.  The collection 
 * commences when all scheduled collector threads arrive at the first
 * "rendezvous" in the run methods run loop.
 *
 * An instance of VM_Handshake contains state information for the "current"
 * collection.  When a collection is finished, a new VM_Handshake is allocated
 * for the next garbage collection.
 *
 * @see VM_Handshake
 *
 * @author Derek Lieber
 * @author Bowen Alpern 
 * @author Stephen Smith
 */ 
public class VM_CollectorThread extends VM_Thread {
					//  implements VM_GCConstants 

  private final static int trace = 0;

  /** When true, causes RVM collectors to display heap configuration at startup */
  static final boolean DISPLAY_OPTIONS_AT_BOOT = false;
  
  /**
   * When true, causes RVM collectors to measure time spent in each phase of
   * collection. Will also force summary statistics to be generated.
   */
  public static final boolean TIME_GC_PHASES  = false;

  /**
   * When true, collector threads measure time spent waiting for buffers while
   * processing the Work Queue, and time spent waiting in Rendezvous during
   * the collection process. Will also force summary statistics to be generated.
   */
  public final static boolean MEASURE_WAIT_TIMES = false;
  
  
  public static int[]  participantCount;    // array of size 1 to count arriving collector threads

  public static VM_CollectorThread[] collectorThreads;   // maps processor id to assoicated collector thread
  
  public static int collectionCount;             // number of collections
  
  /**
   * The VM_Handshake object that contains the state of the next or current
   * (in progress) collection.  Read by mutators when detecting a need for
   * a collection, and passed to the collect method when requesting a
   * collection.
   */
  public static VM_Handshake                       handshake;

  /** Use by collector threads to rendezvous during collection */
  public static SynchronizationBarrier gcBarrier;
  
  static {
    handshake = new VM_Handshake();
    participantCount = new int[1];  // counter for threads starting a collection
  } 
  
  /**
   * Initialize for boot image.
   */
  public static void  init() throws VM_PragmaInterruptible {
    gcBarrier = new SynchronizationBarrier();
    collectorThreads = new VM_CollectorThread[ 1 + VM_Scheduler.MAX_PROCESSORS ];
  }
  
  /**
   * Initiate a garbage collection. 
   * Called by a mutator thread when its allocator runs out of space.
   * The caller should pass the VM_Handshake that was referenced by the
   * static variable "collect" at the time space was unavailable.
   *
   * @param handshake VM_Handshake for the requested collection
   */
  public static void collect (VM_Handshake handshake) {
    handshake.requestAndAwaitCompletion();
  }
  
  /**
   * Initiate a garbage collection at next GC safe point.  Called by a
   * mutator thread at any time.  The caller should pass the
   * VM_Handshake that was referenced by the static variable "collect".
   *
   * @param handshake VM_Handshake for the requested collection
   */
  public static void asyncCollect(VM_Handshake handshake) 
    throws VM_PragmaUninterruptible  {
    handshake.requestAndContinue();
  }


  // FOLLOWING NO LONGER TRUE... we now scan the stack frame for the run method,
  // so references will get updated, and "getThis" should not be necessary.
  // 
  // The gc algorithm currently doesn't scan collector stacks, so we cannot use
  // local variables (eg. "this"). This method is a temporary workaround
  // until gc scans gc stacks too. Eventually we should be able to
  // replace "getThis()" with "this" and everything should work ok.
  //
  // Use this to access all instance variables:
  //
  private static VM_CollectorThread  getThis() throws VM_PragmaUninterruptible {
    return VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());
  }
  
  // overrides VM_Thread.toString
  public String toString() throws VM_PragmaUninterruptible {
    return "VM_CollectorThread";
  }

  // returns number of collector threads participating in a collection
  //
  public static int numCollectors() throws VM_PragmaUninterruptible {
    return( participantCount[0] );
  }

  /**
   * Return the GC ordinal for this collector thread. A integer, 1,2,...
   * assigned to each collector thread participating in the current
   * collection.  Only valid while GC is "InProgress".
   *
   * @return The GC ordinal
   */
  public final int getGCOrdinal() throws VM_PragmaUninterruptible {
    return gcOrdinal;
  }

  public final void setGCOrdinal(int ord) throws VM_PragmaUninterruptible {
    gcOrdinal = ord;
  }

  /**
   * Run method for collector thread (one per VM_Processor).
   * Enters an infinite loop, waiting for collections to be requested,
   * performing those collections, and then waiting again.
   * Calls VM_Interface.collect to perform the collection, which
   * will be different for the different allocators/collectors
   * that the RVM can be configured to use.
   */
   public void run ()
       throws VM_PragmaNoOptCompile, // refs stored in registers by opt compiler will not be relocated by GC 
	      VM_PragmaLogicallyUninterruptible,  // due to call to snipObsoleteCompiledMethods
	      VM_PragmaUninterruptible {

    for (int count = 0; ; count++) {
      
      // suspend this thread: it will resume when scheduled by VM_Handshake 
      // initiateCollection().  while suspended, collector threads reside on
      // the schedulers collectorQueue
      //
      VM_Scheduler.collectorMutex.lock();
      if (trace >= 1) {
	VM.sysWriteln("GC Message: VM_CT.run yielding");
	VM_Interface.getPlan().show();
      }
      if (count > 0)
	VM_Processor.getCurrentProcessor().enableThreadSwitching();  // resume normal scheduling
      VM_Thread.getCurrentThread().yield(VM_Scheduler.collectorQueue,
					 VM_Scheduler.collectorMutex);
      
      // block mutators from running on the current processor
      VM_Processor.getCurrentProcessor().disableThreadSwitching();
      
      if (trace >= 2) VM.sysWriteln("GC Message: VM_CT.run waking up");

      // record time it took to stop mutators on this processor and get this
      // collector thread dispatched
      //
      if (MEASURE_WAIT_TIMES) stoppingTime = 0;
      
      gcOrdinal = VM_Synchronization.fetchAndAdd(participantCount, 0, 1) + 1;
      
      if (trace > 2)
	VM.sysWriteln("GC Message: VM_CT.run entering first rendezvous - gcOrdinal =",
		      gcOrdinal);

      // the first RVM VP will wait for all the native VPs not blocked in native
      // to reach a SIGWAIT state
      //
      if( gcOrdinal == 1) {
        if (trace >= 2) VM.sysWriteln("GC Message: VM_CT.run quiescing native VPs");
        for (int i = 1; i <= VM_Processor.numberNativeProcessors; i++) {
          if ( VM_Processor.vpStatus[VM_Processor.nativeProcessors[i].vpStatusIndex] !=
	       VM_Processor.BLOCKED_IN_NATIVE) {
	    if (trace >= 2) VM.sysWriteln("GC Mesage: VM_CollectorThread.run found one not blocked in native");
					
	    while ( VM_Processor.vpStatus[VM_Processor.nativeProcessors[i].vpStatusIndex] !=
		    VM_Processor.IN_SIGWAIT) {
	      if (trace >= 2) 
		VM.sysWriteln("GC Message: VM_CT.run  WAITING FOR NATIVE PROCESSOR", i,
			      " with vpstatus = ",
			      VM_Processor.vpStatus[VM_Processor.nativeProcessors[i].vpStatusIndex]);
	      VM.sysVirtualProcessorYield();
	    }
	    // Note: threads contextRegisters (ip & fp) are set by thread before
	    // entering SIGWAIT, so nothing needs to be done here
	  }
	  else {
	    // BLOCKED_IN_NATIVE - set context regs so that scan of threads stack
	    // will start at the java to C transition frame. ip is not used for
	    // these frames, so can be set to 0.
	    //
	    VM_Thread t = VM_Processor.nativeProcessors[i].activeThread;
	    //	    t.contextRegisters.gprs[FRAME_POINTER] = t.jniEnv.JNITopJavaFP;
	    t.contextRegisters.setInnermost( VM_Address.zero() /*ip*/, t.jniEnv.topJavaFP() );
	  }
        }

	// quiesce any attached processors, blocking then IN_NATIVE or IN_SIGWAIT
	//
	quiesceAttachedProcessors();

      }  // gcOrdinal==1

      // wait for other collector threads to arrive or be made non-participants
      if (trace >= 2) VM.sysWriteln("GC Message: VM_CT.run  initializing rendezvous");
      gcBarrier.startupRendezvous();

      if (trace >= 2) VM.sysWriteln("GC Message: VM_CT.run  starting collection");
      if (getThis().isActive) 
	VM_Interface.getPlan().collect();     // gc
      if (trace >= 2) VM.sysWriteln("GC Message: VM_CT.run  finished collection");
      
      // wait for other collector threads to arrive here
      rendezvousWaitTime += gcBarrier.rendezvous(5200);
      
      // Wake up mutators waiting for this gc cycle and create new collection
      // handshake object to be used for next gc cycle.
      // Note that mutators will not run until after thread switching is enabled,
      // so no mutators can possibly arrive at old handshake object: it's safe to
      // replace it with a new one.
      //
      if ( gcOrdinal == 1 ) {
	// Snip reference to any methods that are still marked obsolete after
	// we've done stack scans. This allows reclaiming them on next GC.
	VM_CompiledMethods.snipObsoleteCompiledMethods();

	collectionCount += 1;

	// notify mutators waiting on previous handshake object - actually we
	// don't notify anymore, mutators are simply in processor ready queues
	// waiting to be dispatched.
	handshake.notifyCompletion();
	handshake.reset();

	// schedule the FinalizerThread, if there is work to do & it is idle
	VM_Interface.scheduleFinalizerThread();
      } 
      
      // wait for other collector threads to arrive here
      rendezvousWaitTime += gcBarrier.rendezvous(5210);
      if (trace > 2) VM.sysWriteln("VM_CollectorThread: past rendezvous 1 after collection");

      // final cleanup for initial collector thread
      if (gcOrdinal == 1) {
	// unblock any native processors executing in native that were blocked
	// in native at the start of GC
	//
	if (trace > 3) VM.sysWriteln("VM_CollectorThread: unblocking native procs");
	for (int i = 1; i <= VM_Processor.numberNativeProcessors; i++) {
	  VM_Processor vp = VM_Processor.nativeProcessors[i];
	  if (VM.VerifyAssertions) VM._assert(vp != null);
	  if ( VM_Processor.vpStatus[vp.vpStatusIndex] == VM_Processor.BLOCKED_IN_NATIVE ) {
	    VM_Processor.vpStatus[vp.vpStatusIndex] = VM_Processor.IN_NATIVE;
	    if (trace >= 2)
	      VM.sysWriteln("GC Message: VM_CT.run  unblocking Native Processor", vp.id);
	  }
	}

	// It is VERY unlikely, but possible that some RVM processors were
	// found in C, and were BLOCKED_IN_NATIVE, during the collection, and now
	// need to be unblocked.
	//
	if (trace >= 2)
	  VM.sysWriteln("GC Message: VM_CT.run unblocking native procs blocked during GC");
	for (int i = 1; i <= VM_Scheduler.numProcessors; i++) {
	  VM_Processor vp = VM_Scheduler.processors[i];
	  if (VM.VerifyAssertions) VM._assert(vp != null);
	  if ( VM_Processor.vpStatus[vp.vpStatusIndex] == VM_Processor.BLOCKED_IN_NATIVE ) {
	    VM_Processor.vpStatus[vp.vpStatusIndex] = VM_Processor.IN_NATIVE;
	    if (trace >= 2)
	      VM.sysWriteln("GC Message: VM_CT.run unblocking RVM Processor", vp.id);
	  }
	}

	if ( !VM.BuildForSingleVirtualProcessor ) {
	  // if NativeDaemonProcessor was BLOCKED_IN_SIGWAIT, unblock it
	  //
	  VM_Processor ndvp = VM_Scheduler.processors[VM_Scheduler.nativeDPndx];
	  if ( ndvp!=null && VM_Processor.vpStatus[ndvp.vpStatusIndex] == VM_Processor.BLOCKED_IN_SIGWAIT ) {
	    VM_Processor.vpStatus[ndvp.vpStatusIndex] = VM_Processor.IN_SIGWAIT;
	    if (trace >= 2)
	      VM.sysWriteln("GC Message: VM_CT.run unblocking Native Daemon Processor");
	  }

	  // resume any attached Processors blocked prior to Collection
	  //
	  resumeAttachedProcessors();
	}

	// clear the GC flag
	if (trace >= 2) VM.sysWriteln("GC Message: VM_CT.run clearing lock out field");
	VM_Magic.setIntAtOffset(VM_BootRecord.the_boot_record, VM_Entrypoints.lockoutProcessorField.getOffset(), 0);
      }

    }  // end of while(true) loop
    
  }  // run


  static void quiesceAttachedProcessors() throws VM_PragmaUninterruptible {

    // if there are any attached processors (for user pthreads that entered the VM
    // via an AttachJVM) we may be briefly IN_JAVA during transitions to a RVM
    // processor. Ususally they are either IN_NATIVE (having returned to the users 
    // native C code - or invoked a native method) or IN_SIGWAIT (the native code
    // invoked a JNI Function, migrated to a RVM processor, leaving the attached
    // processor running its IdleThread, which enters IN_SIGWAIT and wait for a signal.
    //
    // We wait for the processor to be in either IN_NATIVE or IN_SIGWAIT and then 
    // block it there for the duration of the Collection

    if (VM_Processor.numberAttachedProcessors == 0) return;

    if (trace >= 2) VM.sysWriteln("GC Message: VM_CT.quiesceAttachedProcessors  quiescing attached VPs");
    
    for (int i = 1; i < VM_Processor.attachedProcessors.length; i++) {
      VM_Processor vp = VM_Processor.attachedProcessors[i];
      if (vp==null) continue;   // must have detached
      if (trace >= 2) VM.sysWriteln("GC Message: VM_CT.quiesceAttachedProcessors  quiescing attached VP", i);
      
      int loopCount = 0;
      while ( true ) {
	
	while ( VM_Processor.vpStatus[vp.vpStatusIndex] == VM_Processor.IN_JAVA )
	  VM.sysVirtualProcessorYield();
	
	if ( vp.blockInWaitIfInWait() ) {
	  if (trace >= 2)
	    VM.sysWriteln("GC Message: VM_CT.quiesceAttachedProcessors  Attached Processor BLOCKED_IN_SIGWAIT", i);
	  // Note: threads contextRegisters (ip & fp) are set by thread before
	  // entering SIGWAIT, so nothing needs to be done here
	  break;
	}
	if ( vp.lockInCIfInC() ) {
	  if (trace >= 2)
	    VM.sysWriteln("GC Message: VM_CT.quiesceAttachedProcessors Attached Processor BLOCKED_IN_NATIVE", i);
	  
	  // XXX SES TON XXX
	  // TON !! what is in jniEnv.JNITopJavaFP when thread returns to user C code.
	  // AND what will happen when we scan its stack with that fp
	  
	  // set running threads context regs ip & fp to where scan of threads 
	  // stack should start.
	  VM_Thread at = vp.activeThread;
	  at.contextRegisters.setInnermost( VM_Address.zero() /*ip*/, at.jniEnv.topJavaFP() );
	  break;
	}
	
	loopCount++;
	if (trace >= 2 && (loopCount%10 == 0)) {
	  VM.sysWriteln("GC Message: VM_CollectorThread Waiting for Attached Processor", i,
			" with vpstatus ", VM_Processor.vpStatus[vp.vpStatusIndex]);
	}
	if (loopCount%1000 == 0) {
	  VM.sysWriteln("GC Message: VM_CT.quiesceAttachedProcessors STUCK Waiting for Attached Processor", i);
	  VM.sysFail("VM_CollectorThread - STUCK quiescing attached processors");
	}
      }  // while (true)
    }  // end loop over attachedProcessors[]
  }  // quiesceAttachedProcessors


  static void resumeAttachedProcessors() throws VM_PragmaUninterruptible {

    // any attached processors were quiesced in either BLOCKED_IN_SIGWAIT
    // or BLOCKED_IN_NATIVE.  Unblock them.

    if (VM_Processor.numberAttachedProcessors == 0) return;

    if (trace >= 2) 
      VM.sysWriteln("GC Message: VM_CT.resumeAttachedProcessors  resuming attached VPs");
    
    for (int i = 1; i < VM_Processor.attachedProcessors.length; i++) {
      VM_Processor vp = VM_Processor.attachedProcessors[i];
      if (vp==null) continue;   // must have detached
      if (trace >= 2)
	VM.sysWriteln("GC Message: VM_CT.resumeAttachedProcessors  resuming attached VP", i);

      if ( VM_Processor.vpStatus[vp.vpStatusIndex] == VM_Processor.BLOCKED_IN_NATIVE ) {
	VM_Processor.vpStatus[vp.vpStatusIndex] = VM_Processor.IN_NATIVE;
	if (trace >= 2)
	  VM.sysWriteln("GC Message:  VM_CollectorThread.resumeAttachedProcessors resuming Processor IN_NATIVE", i);
	continue;
      }

      if ( VM_Processor.vpStatus[vp.vpStatusIndex] == VM_Processor.BLOCKED_IN_SIGWAIT ) {
	// first unblock the processor
	vp.vpStatus[vp.vpStatusIndex] = VM_Processor.IN_SIGWAIT;
	// then send signal
	VM_SysCall.sysPthreadSignal(vp.pthread_id);
	continue;
      }

      // should not reach here: system error:
      VM.sysWriteln("GC Message: VM_CT.resumeAttachedProcessors  ERROR VP not BLOCKED", i,
		    " vpstatus ", VM_Processor.vpStatus[vp.vpStatusIndex]);
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);

    }  // end loop over attachedProcessors[]
  }  // resumeAttachedProcessors
  
  
  // Make a collector thread that will participate in gc.
  // Taken:    stack to run on
  //           processor to run on
  // Returned: collector
  // Note: "stack" must be in pinned memory: currently done by allocating it in the boot image.
  //
  public static VM_CollectorThread createActiveCollectorThread(VM_Processor processorAffinity) throws VM_PragmaInterruptible {
    int[] stack =  MM_Interface.newImmortalStack(STACK_SIZE_COLLECTOR>>2);
    return new VM_CollectorThread(stack, true, processorAffinity);
  }
  
  // Make a collector thread that will not participate in gc.
  // It will serve only to lock out mutators from the current processor.
  // Taken:    stack to run on
  //           processor to run on
  // Returned: collector
  // Note: "stack" must be in pinned memory: currently done by allocating it in the boot image.
  //
  static VM_CollectorThread createPassiveCollectorThread(int[] stack, VM_Processor processorAffinity) 
    throws VM_PragmaInterruptible {
    return new VM_CollectorThread(stack, false,  processorAffinity);
  }

  public void rendezvous(int where) throws VM_PragmaUninterruptible {
      rendezvousWaitTime += gcBarrier.rendezvous(where);
  }
  
  //-----------------//
  // Instance fields //
  //-----------------//
  
  boolean           isActive;      // are we an "active participant" in gc?
  
  /** arrival order of collectorThreads participating in a collection */
  private int               gcOrdinal;

  /** used by each CollectorThread when scanning thread stacks for references */
  VM_GCMapIteratorGroup iteratorGroup;
  
  // pointers into work queue put and get buffers, used with loadbalanced 
  // workqueues where per thread buffers when filled are given to a common shared
  // queue of buffers of work to be done, and buffers for scanning are obtained
  // from that common queue (see WorkQueue)
  //
  // Put Buffer is filled from right (end) to left (start)
  //    | next ptr | ......      <--- | 00000 | entry | entry | entry |
  //      |                             |   
  //    putStart                      putTop
  //
  // Get Buffer is emptied from left (start) to right (end)
  //    | next ptr | xxxxx | xxxxx | entry | entry | entry | entry |
  //      |                   |                                     |   
  //    getStart             getTop ---->                         getEnd
  //
  
  /** start of current work queue put buffer */
  public VM_Address putBufferStart;
  /** current position in current work queue put buffer */
  public VM_Address putBufferTop;
  /** start of current work queue get buffer */
  public VM_Address getBufferStart;
  /** current position in current work queue get buffer */
  public VM_Address getBufferTop;
  /** end of current work queue get buffer */
  public VM_Address getBufferEnd;
  /** extra Work Queue Buffer */
  public VM_Address extraBuffer;
  /** second extra Work Queue Buffer */
  public VM_Address extraBuffer2;
  
  int timeInRendezvous;       // time waiting in rendezvous (milliseconds)
  
  double stoppingTime;        // mutator stopping time - until enter Rendezvous 1
  double startingTime;        // time leaving Rendezvous 1
  double rendezvousWaitTime;  // accumulated wait time in GC Rendezvous's

  // for measuring load balancing of work queues
  //
  public int copyCount;              // for saving count of objects copied
  public int rootWorkCount;          // initial count of entries == number of roots
  public int putWorkCount;           // workqueue entries found and put into buffers
  public int getWorkCount;           // workqueue entries got from buffers & scanned
  public int swapBufferCount;        // times get & put buffers swapped
  public int putBufferCount;         // number of buffers put to common workqueue
  public int getBufferCount;         // number of buffers got from common workqueue
  public int bufferWaitCount;        // number of times had to wait for a get buffer
  public double bufferWaitTime;      // accumulated time waiting for get buffers
  public double finishWaitTime;      // time waiting for all buffers to be processed;

  public int copyCount1;              // for saving count of objects copied
  public int rootWorkCount1;          // initial count of entries == number of roots
  public int putWorkCount1;           // workqueue entries found and put into buffers
  public int getWorkCount1;           // workqueue entries got from buffers & scanned
  public int swapBufferCount1;        // times get & put buffers swapped
  public int putBufferCount1;         // number of buffers put to common workqueue
  public int getBufferCount1;         // number of buffers got from common workqueue
  public int bufferWaitCount1;        // number of times had to wait for a get buffer
  public double bufferWaitTime1;      // accumulated time waiting for get buffers
  public double finishWaitTime1;      // time waiting for all buffers to be processed;

  public double totalBufferWait;      // total time waiting for get buffers
  public double totalFinishWait;      // total time waiting for no more buffers
  public double totalRendezvousWait;  // total time waiting for no more buffers

  // constructor
  //
  VM_CollectorThread(int[] stack, boolean isActive, VM_Processor processorAffinity) throws VM_PragmaInterruptible {
    super(stack);
    makeDaemon(true); // this is redundant, but harmless
    this.isActive          = isActive;
    this.isGCThread        = true;
    this.processorAffinity = processorAffinity;
    this.iteratorGroup     = new VM_GCMapIteratorGroup();

    // associate this collector thread with its affinity processor
    collectorThreads[processorAffinity.id] = this;

  }
  
  // Record number of processors that will be participating in gc synchronization.
  //
  public static void boot(int numProcessors) throws VM_PragmaInterruptible {
    VM_Processor proc = VM_Processor.getCurrentProcessor();
    MM_Interface.setupProcessor(proc);
  }
  
  public void incrementWaitTimeTotals() throws VM_PragmaUninterruptible {
    totalBufferWait += bufferWaitTime + bufferWaitTime1;
    totalFinishWait += finishWaitTime + finishWaitTime1;
    totalRendezvousWait += rendezvousWaitTime;
  }

  public void resetWaitTimers() throws VM_PragmaUninterruptible {
    bufferWaitTime = 0.0;
    bufferWaitTime1 = 0.0;
    finishWaitTime = 0.0;
    finishWaitTime1 = 0.0;
    rendezvousWaitTime = 0.0;
  }

  public static void printThreadWaitTimes() throws VM_PragmaUninterruptible {
    VM_CollectorThread ct;

    VM.sysWrite("*** Collector Thread Wait Times (in micro-secs)\n");
    for (int i = 1; i <= VM_Scheduler.numProcessors; i++) {
      ct = VM_Magic.threadAsCollectorThread(VM_Scheduler.processors[i].activeThread );
      VM.sysWrite(i);
      VM.sysWrite(" stop ");
      VM.sysWrite(ct.stoppingTime * 1000000.0);
      VM.sysWrite(" start ");
      VM.sysWrite(ct.startingTime * 1000000.0);
      VM.sysWrite(" SBW ");
      if (ct.bufferWaitCount1 > 0)
	VM.sysWrite(ct.bufferWaitCount1-1);  // subtract finish wait
      else
	VM.sysWrite(0);
      VM.sysWrite(" SBWT ");
      VM.sysWrite(ct.bufferWaitTime1*1000000.0);
      VM.sysWrite(" SFWT ");
      VM.sysWrite(ct.finishWaitTime1*1000000.0);
      VM.sysWrite(" FBW ");
      if (ct.bufferWaitCount > 0)
	VM.sysWrite(ct.bufferWaitCount-1);  // subtract finish wait
      else
	VM.sysWrite(0);
      VM.sysWrite(" FBWT ");
      VM.sysWrite(ct.bufferWaitTime*1000000.0);
      VM.sysWrite(" FFWT ");
      VM.sysWrite(ct.finishWaitTime*1000000.0);
      VM.sysWrite(" RWT ");
      VM.sysWrite(ct.rendezvousWaitTime*1000000.0);
      VM.sysWriteln();

      ct.stoppingTime = 0.0;
      ct.startingTime = 0.0;
      ct.rendezvousWaitTime = 0.0;
      // WorkQueue.resetWaitTimes(ct);
    }
  }
}
