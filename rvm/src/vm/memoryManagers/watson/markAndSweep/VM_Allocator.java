/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$


package com.ibm.JikesRVM.memoryManagers;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_BootRecord;
import com.ibm.JikesRVM.VM_Constants;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_JavaHeader;
import com.ibm.JikesRVM.VM_ClassLoader;
import com.ibm.JikesRVM.VM_SystemClassLoader;
import com.ibm.JikesRVM.VM_Atom;
import com.ibm.JikesRVM.VM_Type;
import com.ibm.JikesRVM.VM_Class;
import com.ibm.JikesRVM.VM_Array;
import com.ibm.JikesRVM.VM_Method;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_Processor;
import com.ibm.JikesRVM.VM_ProcessorLocalState;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_Registers;
import com.ibm.JikesRVM.VM_Thread;
import com.ibm.JikesRVM.VM_Memory;
import com.ibm.JikesRVM.VM_Time;
import com.ibm.JikesRVM.VM_Entrypoints;
import com.ibm.JikesRVM.VM_Reflection;
import com.ibm.JikesRVM.VM_Synchronization;
import com.ibm.JikesRVM.VM_Synchronizer;
import com.ibm.JikesRVM.VM_EventLogger;
import com.ibm.JikesRVM.VM_Callbacks;

/**
 *    Noncopying nongenerational memory manager.  The heap
 *    is divided into chunks of size GC_BLOCKSIZE bytes (see
 *    VM_GCConstants.java) for objects <= 2048 bytes.  Each
 *    chunk is divided into slots of one of a set of fixed 
 *    sizes (see GC_SIZEVALUES in VM_GCConstants), and an
 *    allocation is satisfied from a chunk whose size is the smallest
 *    that accommodates the request.  See also VM_BlockControl.java
 *    and VM_SizeControl.java.  Each virtual processor allocates from
 *    a private set of chunks to avoid locking on allocations, which
 *    occur only when a new chunk is required.
 *    Collection is stop-the-world mark/sweep; after all live objects
 *    have been marked, allocation begins in the first chunk of each 
 *    size for each virtual processor: the free slots in each chunk
 *    are singly-listed, being identified by the result of the mark phase.
 * <p> 
 *
 * @author Dick Attanasio
 * @modified by Stephen Smith
 * @modified by David F. Bacon
 * 
 * @modified by Perry Cheng  Heavily re-written to factor out common code and adding VM_Address
 * @modified by Dave Grove Created VM_SegregatedListHeap to factor out common code
 */
public class VM_Allocator extends VM_GCStatistics 
  implements VM_Constants, 
	     VM_GCConstants {

  /**
   * Control chattering during progress of GC
   */
  static int verbose = 0;

  // DEBUG WORD TO FIND WHO POINTS TO SOME POINTER
  static VM_Address interesting_ref = VM_Address.zero();

  // following are referenced from elsewhere in VM
  static final boolean movesObjects = false;
  static final boolean writeBarrier = false;
  static final int    SMALL_SPACE_MAX = 2048;      // largest object in small heap

  static final int GC_RETRY_COUNT = 3;             // number of times to GC before giving up

  static final boolean  Debug = false;  
  static final boolean  Debug_torture = false;  
  static final boolean  DebugInterest = false;  

  // statistics reporting
  //
  static boolean flag2nd = false;

  static boolean gc_collect_now = false; // flag to do a collection (new logic)

  private static VM_BootHeap bootHeap            = new VM_BootHeap();   
  private static VM_MallocHeap mallocHeap        = new VM_MallocHeap();
  private static VM_SegregatedListHeap smallHeap = new VM_SegregatedListHeap("Small Object Heap", mallocHeap);
  public  static VM_ImmortalHeap immortalHeap    = new VM_ImmortalHeap();
  private static VM_LargeHeap largeHeap          = new VM_LargeHeap(immortalHeap);

  static boolean gcInProgress = false;

  /** "getter" function for gcInProgress
  */
  static boolean  gcInProgress() throws VM_PragmaUninterruptible {
      return gcInProgress;
  }

  /**
   * Setup done during bootimage building
   */
  static void init () throws VM_PragmaInterruptible {
    smallHeap.init(VM_Scheduler.processors[VM_Scheduler.PRIMORDIAL_PROCESSOR_ID]);

    VM_CollectorThread.init();   // to alloc its rendezvous arrays, if necessary
  }


  static void boot (VM_BootRecord bootrecord) throws VM_PragmaInterruptible {
    verbose = bootrecord.verboseGC;

    int smallHeapSize = bootrecord.smallSpaceSize;
    smallHeapSize = (smallHeapSize / GC_BLOCKALIGNMENT) * GC_BLOCKALIGNMENT;
    smallHeapSize = VM_Memory.roundUpPage(smallHeapSize);
    int immortalSize = VM_Memory.roundUpPage(4 * (bootrecord.largeSpaceSize / VM_Memory.getPagesize()) + 
					     ((int) (0.05 * smallHeapSize)) + 
					     4 * VM_Memory.getPagesize());

    VM_Heap.boot(bootHeap, mallocHeap, bootrecord);
    immortalHeap.attach(immortalSize);
    largeHeap.attach(bootrecord.largeSpaceSize);
    smallHeap.attach(smallHeapSize);

    VM_Processor st = VM_Scheduler.processors[VM_Scheduler.PRIMORDIAL_PROCESSOR_ID];
    smallHeap.boot(st, immortalHeap);

    VM_GCUtil.boot();

    if (verbose >= 1) showParameter();
  }

  static void showParameter() throws VM_PragmaUninterruptible {
      VM.sysWriteln("\nMark-Sweep Collector (verbose = ", verbose, ")");
      bootHeap.show();
      immortalHeap.show();
      smallHeap.show();
      largeHeap.show();
      VM.sysWriteln("  Work queue buffer size = ", VM_GCWorkQueue.WORK_BUFFER_SIZE);
  }

  /**
   * Allocate a "scalar" (non-array) Java object.
   * 
   *   @param size Size of the object in bytes, including the header
   *   @param tib Pointer to the Type Information Block for the object type
   *   @return Initialized object reference
   */
  public static Object allocateScalar (int size, Object[] tib)
    throws OutOfMemoryError, VM_PragmaInline, VM_PragmaUninterruptible {
    if (size > SMALL_SPACE_MAX) {
      return largeHeap.allocateScalar(size, tib);
    } else {
      VM_Address objaddr = VM_SegregatedListHeap.allocateFastPath(size);
      profileAlloc(objaddr, size, tib);
      return VM_ObjectModel.initializeScalar(objaddr, tib, size);
    }
  }


  /**
   * Allocate an array object.
   * 
   *   @param numElements Number of elements in the array
   *   @param size Size of the object in bytes, including the header
   *   @param tib Pointer to the Type Information Block for the object type
   *   @return Initialized array reference
   */
  public static Object allocateArray (int numElements, int size, Object[] tib)
    throws OutOfMemoryError, VM_PragmaInline, VM_PragmaUninterruptible {
    if (size > SMALL_SPACE_MAX) {
      return largeHeap.allocateArray(numElements, size, tib);
    } else {
      VM_Address objaddr = VM_SegregatedListHeap.allocateFastPath(size);
      profileAlloc(objaddr, size, tib);
      return VM_ObjectModel.initializeArray(objaddr, tib, numElements, size);
    }
  }


  /**
   * Handle heap exhaustion.
   * 
   * @param heap the exhausted heap
   * @param size number of bytes requested in the failing allocation
   * @param count the retry count for the failing allocation.
   */
  public static void heapExhausted(VM_Heap heap, int size, int count) throws VM_PragmaUninterruptible {
    flag2nd = count > 0;
    if (heap == smallHeap) {
      if (count>GC_RETRY_COUNT) VM_GCUtil.outOfMemory("small object space", heap.getSize(), "-X:h=nnn");
      gc1("GC triggered by object request of ", size);
    } else if (heap == largeHeap) {
      if (count>GC_RETRY_COUNT) VM_GCUtil.outOfMemory("large object space", heap.getSize(), "-X:lh=nnn");
      gc1("GC triggered by large object request of ", size);
    } else {
      VM.sysFail("unexpected heap");
    }
  }
    

  // following is called from VM_CollectorThread.boot() - to set the number
  // of system threads into the synchronization object; this number
  // is not yet available at Allocator.boot() time
  static void gcSetup (int numSysThreads ) throws VM_PragmaUninterruptible {
    VM_GCWorkQueue.workQueue.initialSetup(numSysThreads);
  }

  private static void  prepareNonParticipatingVPsForGC() throws VM_PragmaUninterruptible {
    // include NativeDaemonProcessor in following loop over processors
    for (int i = 1; i <= VM_Scheduler.numProcessors+1; i++) {
      VM_Processor vp = VM_Scheduler.processors[i];
      
      if (vp == null) continue; 	// protect next stmt

      int vpStatus = VM_Processor.vpStatus[vp.vpStatusIndex];
      if ((vpStatus == VM_Processor.BLOCKED_IN_NATIVE) || (vpStatus == VM_Processor.BLOCKED_IN_SIGWAIT)) {
	if (vpStatus == VM_Processor.BLOCKED_IN_NATIVE) {
	  // processor & its running thread are block in C for this GC.  Its stack
	  // needs to be scanned, starting from the "top" java frame, which has
	  // been saved in the running threads JNIEnv.  Put the saved frame pointer
	  // into the threads saved context regs, which is where the stack scan starts.
	  //
	  VM_Thread thr = vp.activeThread;
	  thr.contextRegisters.setInnermost( VM_Address.zero(), thr.jniEnv.JNITopJavaFP );
	}
	smallHeap.zeromarks(vp);		// reset mark bits for nonparticipating vps
      }
    }
  }

  private static void prepareNonParticipatingVPsForAllocation() throws VM_PragmaUninterruptible {
    // include NativeDaemonProcessor in following loop over processors
    for (int i = 1; i <= VM_Scheduler.numProcessors+1; i++) {
      VM_Processor vp = VM_Scheduler.processors[i];
      if (vp != null) {
	int vpStatus = VM_Processor.vpStatus[vp.vpStatusIndex];
	if ((vpStatus == VM_Processor.BLOCKED_IN_NATIVE) || 
	    (vpStatus == VM_Processor.BLOCKED_IN_SIGWAIT)) {
	  // at this point we do _not_ attempt to reclaim free blocks - an
	  // expensive operation - for non participating vps, since this would
	  // not be done in parallel; we assume that, in general, in the next
	  // collection non-participating vps do participate
	  smallHeap.setupallocation(vp);
	}
      }
    }
  }

  static void collect () throws VM_PragmaUninterruptible {
    if (!gc_collect_now) {
      VM_Scheduler.trace(" gc entered with collect_now off", "");
      return;  // to avoid cascading gc
    }

    VM_CollectorThread mylocal = 
      VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());

    //   SYNCHRONIZATION CODE for parallel gc
    if (VM_GCLocks.testAndSetInitLock()) {
      if (flag2nd) {
	VM_Scheduler.trace(" collectstart:", "flag2nd on");
	smallHeap.freeSmallSpaceDetails(true);
      }

      // Start timers to measure time since GC requested
      //
      startTime.start(VM_CollectorThread.gcBarrier.rendezvousStartTime); 
      initTime.start(startTime);
      GCTime.start(initTime.lastStart);

      if (gcInProgress) {
	VM.sysWrite("VM_Allocator: Garbage Collection entered recursively \n");
	VM.sysExit(1000);
      } else {
	gcInProgress = true;
      }
      
      gcCount++;
      
      if (verbose >= 2) VM.sysWriteln("Starting GC ", gcCount);

      // setup common workqueue for num VPs participating
      VM_GCWorkQueue.workQueue.initialSetup(VM_CollectorThread.numCollectors());

      bootHeap.startCollect();
      immortalHeap.startCollect();

      // Now initialize the large object space mark array
      largeHeap.startCollect();

      // Initialize collection in the small heap
      smallHeap.startCollect();

      // perform per vp processing for non-participating vps
      prepareNonParticipatingVPsForGC();

      rootTime.start(initTime);
    }
    //   END OF SYNCHRONIZED INITIALIZATION BLOCK

    mylocal.rendezvous();

    // ALL GC THREADS IN PARALLEL

    // reset collector thread local work queue buffers
    VM_GCWorkQueue.resetWorkQBuffers();

    // Each participating processor clears the mark array for the blocks it owns
    smallHeap.zeromarks(VM_Processor.getCurrentProcessor());
    
    mylocal.rendezvous();

    VM_ScanStatics.scanStatics();     // all threads scan JTOC in parallel

    if (verbose >= 1) VM.sysWriteln("Scanning Stacks");
    gc_scanStacks();

    if (mylocal.gcOrdinal == 1)	scanTime.start(rootTime);

    gc_emptyWorkQueue();

    if (mylocal.gcOrdinal == 1)	finalizeTime.start(scanTime);

    // If counting or timing in VM_GCWorkQueue, save current counter values
    //
    if (VM_GCWorkQueue.WORKQUEUE_COUNTS)   VM_GCWorkQueue.saveCounters(mylocal);
    if (VM_GCWorkQueue.MEASURE_WAIT_TIMES || VM_CollectorThread.MEASURE_WAIT_TIMES)
      VM_GCWorkQueue.saveWaitTimes(mylocal);

    // If no objects with finalizers are potentially garbage (i.e., there were
    // no live objects with finalizers before this collection,
    // skip finalization phases
    //
    if (VM_Finalizer.existObjectsWithFinalizers()) {
      mylocal.rendezvous();

      // Now handle finalization 
      if (mylocal.gcOrdinal == 1) {
	//setup work queue -shared data
	VM_GCWorkQueue.workQueue.reset();
	VM_Finalizer.moveToFinalizable();
      }

      mylocal.rendezvous();
      
      // Now test if following stanza is needed - iff
      // garbage objects were moved to the finalizer queue
      if (VM_Finalizer.foundFinalizableObject) {
	gc_emptyWorkQueue();
	mylocal.rendezvous();
      }	
    }	// if existObjectsWithFinalizers
      
    if (mylocal.gcOrdinal == 1)	finishTime.start(finalizeTime);

    // done
    if (VM.ParanoidGCCheck) smallHeap.clobberfree();

    // Sweep large space
    if (mylocal.gcOrdinal == 1) {
      if (verbose >= 1) VM.sysWrite("Sweeping large space");
      largeHeap.endCollect();
    }

    // Sweep small heap
    smallHeap.sweep(mylocal);

    mylocal.rendezvous();

    // Each GC thread increments adds its wait times for this collection
    // into its total wait time - for printSummaryStatistics output
    //
    if (VM_CollectorThread.MEASURE_WAIT_TIMES)
      mylocal.incrementWaitTimeTotals();

    if (mylocal.gcOrdinal == 1) {
      prepareNonParticipatingVPsForAllocation();

      gc_collect_now  = false;  // reset flag
      gcInProgress    = false;
      VM_GCLocks.reset();

      // done with collection...except for measurement counters, diagnostic output etc

      finishTime.stop();
      GCTime.stop(finishTime.lastStop);

      updateGCStats(DEFAULT, 0);
      printGCStats(DEFAULT);

      mylocal.printRendezvousTime();
      
      if (verbose >= 2) VM.sysWrite(gcCount, "  collections ");
    
      smallHeap.postCollectionReport();
      if (flag2nd) {
	VM_Scheduler.trace(" End of gc:", "flag2nd on");
	smallHeap.freeSmallSpaceDetails(true);
	flag2nd = false;
      }
    }
  }  // end of collect

  /**  gc_scanStacks():
   *  loop through the existing threads and scan their stacks by
   *  calling gc_scanStack, passing the stack frame where scanning
   *  is to start.  The stack under which the gc is running is treated
   *  differently: the current frame is not scanned, since there might
   *  be pointers to objects which have not been initialized.
   */
  static  void gc_scanStacks () throws VM_PragmaUninterruptible {
    //  get thread ptr of running (the GC) thread
    VM_Thread mythread  = VM_Thread.getCurrentThread();  

    for (int i = 0; i < VM_Scheduler.threads.length; i++) {
      VM_Thread t  = VM_Scheduler.threads[i];
      //  exclude GCThreads and "Empty" threads
      if ((t != null) && !t.isGCThread) {
	// get control of this thread
	//  SYNCHRONIZING STATEMENT
	if (VM_GCLocks.testAndSetThreadLock(i)) {
          VM_ScanStack.scanStack( t, VM_Address.zero(), false /*relocate_code*/ );
        }
      }  // not a null pointer, not a gc thread
    }    // scan all threads in the system
  }      // scanStacks()


  static void gc_scanObjectOrArray (VM_Address objRef ) throws VM_PragmaUninterruptible {
    // NOTE: no need to process TIB; 
    //       all TIBS found through JTOC and are never moved.

    VM_Type type  = VM_Magic.getObjectType(VM_Magic.addressAsObject(objRef));
    if (type.isClassType()) { 
      int[]  referenceOffsets = type.asClass().getReferenceOffsets();
      for (int i = 0, n = referenceOffsets.length; i < n; ++i) {
        if (DebugInterest) {
	    if (VM_Magic.getMemoryAddress(objRef.add(referenceOffsets[i])).EQ(interesting_ref)) {
		VM_Scheduler.trace("  In a ref", " found int. ref in ", objRef);
		printclass(objRef);
		VM_Scheduler.trace("  is the type of the pointing ref ", " ");
	    }
        }
        processPtrValue( VM_Magic.getMemoryAddress(objRef.add(referenceOffsets[i]))  );
      }
    } else if (type.isArrayType()) {
      if (type.asArray().getElementType().isReferenceType()) {
        int  num_elements = VM_Magic.getArrayLength(VM_Magic.addressAsObject(objRef));
        VM_Address  location = objRef;   // for arrays = address of [0] entry
        VM_Address  end      = objRef.add(num_elements * 4);
        while ( location.LT(end) ) {
          if (DebugInterest) {
	    if (VM_Magic.getMemoryAddress(location).EQ(interesting_ref)) {
	      VM_Scheduler.trace("  In array of refs", " found int. ref in ", objRef);
	      VM.sysWrite(type.getDescriptor());
            }
          }
          processPtrValue(VM_Magic.getMemoryAddress(location));
          //  USING  "4" where should be using "size_of_pointer" (for 64-bits)
          location  = location.add(4);
        }
      }
    } else  {
      VM.sysWrite("VM_Allocator.gc_scanObjectOrArray: type not Array or Class");
      VM.sysExit(1000);
    }
  }  //  gc_scanObjectOrArray

    
  //  process objects in the work queue buffers until no more buffers to process
  //
  static  void gc_emptyWorkQueue()  throws VM_PragmaUninterruptible {
    VM_Address ref = VM_GCWorkQueue.getFromWorkBuffer();
      
    if (VM_GCWorkQueue.WORKQUEUE_COUNTS) {
      VM_CollectorThread myThread = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());
      myThread.rootWorkCount = myThread.putWorkCount;
    }
      
    while (!ref.isZero()) {
      gc_scanObjectOrArray( ref );
      ref = VM_GCWorkQueue.getFromWorkBuffer();
    }
  }    // gc_emptyWorkQueue


  //  To be able to be called from java/lang/runtime, or internally
  //
  public  static void gc ()  throws VM_PragmaUninterruptible {
    gc1("GC triggered by external call to gc() ", 0);
  }


  public static void gc1 (String why, int size) throws VM_PragmaUninterruptible {
    gc_collect_now  = true;

    if (verbose >= 1) VM.sysWriteln(why, size);

    //  Tell gc thread to reclaim space, then wait for it to complete its work.
    //  The gc thread will do its work by calling collect(), below.
    //
    VM_CollectorThread.collect(VM_CollectorThread.collect);
  }


  public  static long totalMemory () throws VM_PragmaUninterruptible {
    return smallHeap.size + largeHeap.size;
  }

  public static long freeMemory () throws VM_PragmaUninterruptible {
    return smallHeap.freeBlocks() * GC_BLOCKSIZE;
  }

  /*
   * Includes freeMemory and per-processor local storage
   * and partial blocks in small heap.
   */
  public static long allSmallFreeMemory () throws VM_PragmaUninterruptible {
    return freeMemory() + smallHeap.partialBlockFreeMemory();
  }

  public static long allSmallUsableMemory () throws VM_PragmaUninterruptible {
    return smallHeap.getSize();
  }

  static boolean gc_isLive (VM_Address ref) throws VM_PragmaUninterruptible {
    if (bootHeap.refInHeap(ref)) {
      return bootHeap.isLive(ref);
    } else if (immortalHeap.refInHeap(ref)) {
      return immortalHeap.isLive(ref);
    } else if (smallHeap.refInHeap(ref)) {
      return smallHeap.isLive(ref);
    } else if (largeHeap.refInHeap(ref)) {
      return largeHeap.isLive(ref);
    } else {
      VM.sysWrite("gc_isLive: ref not in any known heap: ", ref);
      VM._assert(false);
      return false;
    }
  }


  // Normally called from constructor of VM_Processor
  // Also called a second time for the PRIMORDIAL processor during VM.boot
  //
  static void setupProcessor(VM_Processor st) throws VM_PragmaInterruptible {
    // for the PRIMORDIAL processor allocation of sizes, etc occurs
    // during init(), nothing more needs to be done
    //
    if (st.id > VM_Scheduler.PRIMORDIAL_PROCESSOR_ID) 
      smallHeap.setupProcessor(st);
  }


  public  static void printclass (VM_Address ref) throws VM_PragmaUninterruptible {
    VM_Type  type = VM_Magic.getObjectType(VM_Magic.addressAsObject(ref));
    VM.sysWrite(type.getDescriptor());
  }

  // Called from WriteBuffer code for generational collectors.
  // Argument is a modified old object which needs to be scanned
  //
  static void processWriteBufferEntry (VM_Address ref) throws VM_PragmaUninterruptible {
    VM_ScanObject.scanObjectOrArray(ref);
  }

  static  boolean processFinalizerListElement(VM_FinalizerListElement  le) throws VM_PragmaUninterruptible {
    boolean is_live = gc_isLive(le.value);
    if ( !is_live ) {
      // now set pointer field of list element, which will keep 
      // the object alive in subsequent collections, until the 
      // FinalizerThread executes the objects finalizer
      //
      le.pointer = VM_Magic.addressAsObject(le.value);
      // process the ref, to mark it live & enqueue for scanning
      VM_Allocator.processPtrValue(le.value);	
    }
    return is_live;
  }

  /**
   * Process an object reference field during collection.
   *
   * @param location  address of a reference field
   */
  public static void processPtrField ( VM_Address location ) throws VM_PragmaUninterruptible {
      VM_Magic.setMemoryAddress(location, processPtrValue(VM_Magic.getMemoryAddress(location)));
  }

  /**
   * Process an object reference (value) during collection.
   *
   * @param ref  object reference (value)
   */
  static VM_Address processPtrValue (VM_Address ref) throws VM_PragmaUninterruptible {
    if (ref.isZero()) return ref;  // TEST FOR NULL POINTER

    if (smallHeap.refInHeap(ref)) {
      if (smallHeap.mark(ref)) VM_GCWorkQueue.putToWorkBuffer(ref);
      return ref;
    } 

    if (largeHeap.refInHeap(ref)) {
      if (largeHeap.mark(ref)) VM_GCWorkQueue.putToWorkBuffer(ref);
      return ref;
    }

    if (bootHeap.refInHeap(ref)) {
      if (bootHeap.mark(ref)) VM_GCWorkQueue.putToWorkBuffer(ref);
      return ref;
    }

    if (immortalHeap.refInHeap(ref)) {
      if (immortalHeap.mark(ref)) VM_GCWorkQueue.putToWorkBuffer(ref);
      return ref;
    }

    if (mallocHeap.refInHeap(ref))
      return ref;

    VM.sysWrite("processPtrValue: ref not in any known heap: ", ref);
    VM._assert(false);
    return VM_Address.zero();
  }  // processPtrValue
}
