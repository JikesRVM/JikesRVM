/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

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
public class VM_Allocator implements VM_Constants, 
				     VM_GCConstants, 
				     VM_Uninterruptible,
				     VM_Callbacks.ExitMonitor, 
				     VM_Callbacks.AppRunStartMonitor {

  static final boolean IGNORE_EXPLICIT_GC_CALLS = false;

  // to allow runtime check for which allocator/collector is running
  static final int    TYPE =11;

  // DEBUG WORD TO FIND WHO POINTS TO SOME POINTER
  static VM_Address interesting_ref = VM_Address.zero();
  static VM_Address the_array     = VM_Address.zero();

  // maps processors to their collector threads
  static VM_CollectorThread[] proc_to_collector = null;  

  // fields used for timing
  static int scanStaticsProcessor = 0;  // id of VM_Processor that scanned statics
  static int gcInitProcessor = 0;      // id of VM_Processor that did gc initialization 
  static int gcFinishProcessor = 0;    // id of VM_Processor that gc finish

  // following are referenced from elsewhere in VM
  // following are for compilation compatibility with copying collectors
  static final int    MARK_VALUE = 1;              
  static final boolean movesObjects = false;

  static final boolean writeBarrier = false;
  static final int    SMALL_SPACE_MAX = 2048;      // largest object in small heap

  static final int GC_RETRY_COUNT = 3;             // number of times to GC before giving up

  static final boolean COMPILE_FOR_TIMING_RUN = true;   // touch heap in boot

  static final boolean TIME_GC_PHASES            = VM_CollectorThread.TIME_GC_PHASES; 
  static final boolean GC_PARALLEL_FREE_BLOCKS   = true;
  static final boolean RENDEZVOUS_TIMES          = false;
  static final boolean RENDEZVOUS_WAIT_TIME     = VM_CollectorThread.MEASURE_WAIT_TIMES;
  // Flag for counting bytes allocated and objects allocated
  static final boolean COUNT_ALLOCATIONS = false;
  static final boolean GC_COUNT_FAST_ALLOC       = false;
  static final boolean GC_COUNT_LIVE_OBJECTS     = false;
  static final boolean GC_COUNT_BYTE_SIZES       = false;
  static final boolean GC_COUNT_BY_TYPES         = false;
  static final boolean GC_STATISTICS             = false;   // for timing parallel GC
  static final boolean GCDEBUG_PARALLEL          = false;   // for debugging parallel alloc
  static final boolean GC_TIMING                 = false ;  // for timing parallel GC
  static final boolean GC_MUTATOR_TIMES          = false;   // gc time as observed by mutator in gc1
  static final boolean GC_TRACEALLOCATOR         = false;   // for detailed tracing

  static double gcMinorTime;           // for timing gc times
  static double gcMaxTime  ;           // for timing gc times
  static double gcMajorTime;           // for timing gc times
  static double gcStartTime;           // for timing gc times
  static double gcEndTime;             // for timing gc times
  static double gcTotalTime = 0;       // for timing gc times

  // accumulated times & counts for sysExit callback printout SES 0317
  private static double totalStartTime = 0.0;           

  // total times for TIME_GC_PHASES output
  private static double totalInitTime;
  private static double totalScanningTime;
  private static double totalFinalizeTime;
  private static double totalFinishTime;
  
  // timestamps for TIME_GC_PHASES output
  private static double gcInitDoneTime = 0;
  private static double gcScanningDoneTime = 0;
  private static double gcFinalizeDoneTime = 0;

  static int verbose = 1;

  static final boolean  Debug = false;  
  static final boolean  Debug_torture = false;  
  static final boolean  DebugInterest = false;  
  static final boolean  DebugLink = false;
  static final boolean  GC_CLOBBERFREE = false;  

  // statistics reporting
  //
  static boolean flag2nd = false;

  static int    gcCount  = 0; // updated every entry to collect
  // major collections in generational scheme
  static int    gcMajorCount = 0; 
  static boolean gc_collect_now = false; // flag to do a collection (new logic)

  private static VM_Heap bootHeap                = new VM_Heap("Boot Image Heap");   
  private static VM_MallocHeap mallocHeap        = new VM_MallocHeap();
  private static VM_SegregatedListHeap smallHeap = new VM_SegregatedListHeap("Small Object Heap", mallocHeap);
  private static VM_ImmortalHeap immortalHeap    = new VM_ImmortalHeap();
  private static VM_LargeHeap largeHeap          = new VM_LargeHeap(immortalHeap);

  // These two fields are unused but unconditionally needed in VM_EntryPoints.
  static VM_Address areaCurrentAddress;    
  static VM_Address matureCurrentAddress;  

  static VM_BootRecord   bootrecord;
 
  static int markboot_count = 0;  // counter of objects marked in the boot image
  static int total_markboot_count = 0;  // counter of times markboot called

  static int marklarge_count = 0;  // counter of large objects marked

  static boolean gcInProgress = false;
  static int OBJECT_GC_MARK_VALUE = 0;  // changes between this and 0

  /** "getter" function for gcInProgress
  */
  static boolean  gcInProgress() {
      return gcInProgress;
  }

  /**
   * Setup done during bootimage building
   */
  static  void init () {
    VM_GCLocks.init();  

    smallHeap.init(VM_Scheduler.processors[VM_Scheduler.PRIMORDIAL_PROCESSOR_ID]);

    VM_GCWorkQueue.workQueue = new VM_GCWorkQueue();

    VM_CollectorThread.init();   // to alloc its rendezvous arrays, if necessary
  }


  static void boot (VM_BootRecord thebootrecord) {
    bootrecord = thebootrecord;  
    verbose = bootrecord.verboseGC;

    int smallHeapSize = bootrecord.smallSpaceSize;
    smallHeapSize = (smallHeapSize / GC_BLOCKALIGNMENT) * GC_BLOCKALIGNMENT;
    smallHeapSize = VM_Memory.roundUpPage(smallHeapSize);
    int immortalSize = VM_Memory.roundUpPage(4 * (bootrecord.largeSpaceSize / VM_Memory.getPagesize()) + 
					     ((int) (0.05 * smallHeapSize)) + 
					     4 * VM_Memory.getPagesize());

    VM_Heap.boot(bootHeap, thebootrecord);
    immortalHeap.attach(immortalSize);
    largeHeap.attach(bootrecord.largeSpaceSize);
    smallHeap.attach(smallHeapSize);
    mallocHeap.attach(bootrecord);

    // now touch all the pages in the heap, when 
    // preparing for a timing run to avoid cost of page fault during timing
    if (COMPILE_FOR_TIMING_RUN) {
      largeHeap.touchPages();
      smallHeap.touchPages();
    }

    VM_Processor st = VM_Scheduler.processors[VM_Scheduler.PRIMORDIAL_PROCESSOR_ID];
    smallHeap.boot(st, immortalHeap);

    VM_GCUtil.boot();

    // create the finalizer object 
    VM_Finalizer.setup();

    VM_Address min = bootHeap.start.LT(immortalHeap.start) ? bootHeap.start : immortalHeap.start;
    VM_Address max = bootHeap.end.GT(immortalHeap.start) ? bootHeap.end : immortalHeap.end;
    VM_AllocatorHeader.boot(min, max);

    VM_Callbacks.addExitMonitor(new VM_Allocator());
    VM_Callbacks.addAppRunStartMonitor(new VM_Allocator());

    if (verbose >= 1) showParameter();
  } // boot

  static void showParameter() {
      VM.sysWriteln("\nMark-Sweep Collector (verbose = ", verbose, ")");
      VM.sysWrite("         Boot Image: "); bootHeap.show(); VM.sysWriteln();
      VM.sysWrite("      Immortal Heap: "); immortalHeap.show(); VM.sysWriteln();
      VM.sysWrite("  Small Object Heap: "); smallHeap.show(); VM.sysWriteln();
      VM.sysWrite("  Large Object Heap: "); largeHeap.show(); VM.sysWriteln();
      VM.sysWriteln("  Work queue buffer size = ", VM_GCWorkQueue.WORK_BUFFER_SIZE);
      VM.sysWrite("\n");
  }


  /**
   * To be called when the application starts a run
   * @param value the exit value
   */
  public void notifyAppRunStart(int value) {
    VM.sysWrite("Clearing VM_Allocator statistics\n");
    clearSummaryStatistics();
  }

  /**
   * To be called when the VM is about to exit.
   * @param value the exit value
   */
  public void notifyExit(int value) {
      if (VM_Finalizer.finalizeOnExit) VM_Finalizer.finalizeAll();
      printSummaryStatistics();
  }

  /**
   * Allocate a "scalar" (non-array) Java object.
   * 
   *   @param size Size of the object in bytes, including the header
   *   @param tib Pointer to the Type Information Block for the object type
   *   @return Initialized object reference
   */
  public static Object allocateScalar (int size, Object[] tib)
    throws OutOfMemoryError {
    VM_Magic.pragmaInline(); 
    VM_Address objaddr = allocateRawMemory(size);
    return VM_ObjectModel.initializeScalar(objaddr, tib, size);
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
    throws OutOfMemoryError {
    VM_Magic.pragmaInline(); 
    VM_Address objaddr = allocateRawMemory(size);
    return VM_ObjectModel.initializeArray(objaddr, tib, numElements, size);
  }


  /**
   * Get space for a new object or array. 
   *
   * This code simply dispatches to one of two heaps
   * (1) If the object is large, then the large heap 
   * (2) Otherwise, the fast path of the segmented list heap 
   *     that this collector uses for small objects.
   * 
   * @param size number of bytes to allocate
   * @return the address of the first byte of the allocated zero-filled region
   */
    static VM_Address allocateRawMemory(int size) throws OutOfMemoryError {
    VM_Magic.pragmaInline();
    if (size > SMALL_SPACE_MAX) {
      return allocateLargeObject(size);
    } else {
      return VM_SegregatedListHeap.allocateFastPath(size);
    }
  }

  /**
   * Allocate a large object; if none available collect garbage and try again.
   *   @param size Size in bytes to allocate
   *   @return Address of zero-filled free storage
   */
  static VM_Address allocateLargeObject (int size) {
    VM_Magic.pragmaNoInline(); // make sure this method is not inlined

    for (int control = 0; control < GC_RETRY_COUNT; control++) {
      VM_Address objaddr = largeHeap.allocate(size);
      if (!objaddr.isZero())
	return objaddr;
      if (control > 0)
	flag2nd = true;
      gc1("Garbage collection triggered by large request ", size);
    }
    
    outOfMemory(size, "Out of memory while trying to allocate large object");
    return VM_Address.zero(); // never executed
  }

  
/**
 Garbage Collection routines begin here.
   scan jtoc for pointers to objects.
   then scan stack for pointers to objects
   processPtrValue() is the routine that processes a pointer, marks 
   the object as live, then scans the object pointed to for pointer,
   and calls processPtrValue()
*/

  public static void heapExhausted(VM_SegregatedListHeap h, int size, int count) {
    if (count > GC_RETRY_COUNT) outOfMemory(size, "Out of memory trying to allocate small object");
    flag2nd = count > 0;
    gc1("Garbage collection triggered by small request of size ", size);
  }
    

  // following is called from VM_CollectorThread.boot() - to set the number
  // of system threads into the synchronization object; this number
  // is not yet available at Allocator.boot() time
  static void gcSetup (int numSysThreads ) {
    VM_GCWorkQueue.workQueue.initialSetup(numSysThreads);
  }

  private static void  prepareNonParticipatingVPsForGC() {
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

  private static void prepareNonParticipatingVPsForAllocation() {
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

  static void collect () {
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

      gcStartTime = VM_Time.now();         // start time for GC
      totalStartTime += gcStartTime - VM_CollectorThread.gcBarrier.rendezvousStartTime; //time since GC requested

      gcCount++;

      if (GC_TRACEALLOCATOR) VM_Scheduler.trace(" Inside Mutex1", "");

      // setup common workqueue for num VPs participating
      VM_GCWorkQueue.workQueue.initialSetup(VM_CollectorThread.numCollectors());

      if (gcInProgress) {
	VM.sysWrite("VM_Allocator: Garbage Collection entered recursively \n");
	VM.sysExit(1000);
      } else {
	gcInProgress = true;
      }

      if (GC_TRACEALLOCATOR) 
	VM.sysWrite("VM_Allocator: Garbage Collection Beginning \n");

      // invert the mark_flag value, used for marking BootImage objects
      if (OBJECT_GC_MARK_VALUE == 0)
	OBJECT_GC_MARK_VALUE = VM_CommonAllocatorHeader.GC_MARK_BIT_MASK;
      else
	OBJECT_GC_MARK_VALUE = 0;

      // Now initialize the large object space mark array
      largeHeap.startCollect();

      // Initialize collection in the small heap
      smallHeap.startCollect();

      // perform per vp processing for non-participating vps
      prepareNonParticipatingVPsForGC();
    }
    //   END OF SYNCHRONIZED INITIALIZATION BLOCK

    VM_CollectorThread.gcBarrier.rendezvous(RENDEZVOUS_TIMES || RENDEZVOUS_WAIT_TIME);

    // ALL GC THREADS IN PARALLEL

    // reset collector thread local work queue buffers
    VM_GCWorkQueue.resetWorkQBuffers();

    // Each participating processor clears the mark array for the blocks it owns
    smallHeap.zeromarks(VM_Processor.getCurrentProcessor());
    
    if (TIME_GC_PHASES && (mylocal.gcOrdinal == 1))
      gcInitDoneTime = VM_Time.now();

    //   SYNCHRONIZATION CODE
    
    VM_CollectorThread.gcBarrier.rendezvous(RENDEZVOUS_TIMES || RENDEZVOUS_WAIT_TIME);

    //   END OF SYNCHRONIZING CODE

    VM_ScanStatics.scanStatics();     // all threads scan JTOC in parallel

    if (verbose >= 1) VM.sysWriteln("Scanning Stacks");
    gc_scanStacks();

    gc_emptyWorkQueue();

    // have processor 1 record timestamp for end of scan/mark/copy phase
    if (TIME_GC_PHASES && (mylocal.gcOrdinal == 1))
      gcScanningDoneTime = VM_Time.now();

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
      VM_CollectorThread.gcBarrier.rendezvous(RENDEZVOUS_TIMES || RENDEZVOUS_WAIT_TIME);

      // Now handle finalization 
      if (mylocal.gcOrdinal == 1) {
	//setup work queue -shared data
	VM_GCWorkQueue.workQueue.reset();
	VM_Finalizer.moveToFinalizable();
      }

      VM_CollectorThread.gcBarrier.rendezvous(RENDEZVOUS_TIMES || RENDEZVOUS_WAIT_TIME);
      
      // Now test if following stanza is needed - iff
      // garbage objects were moved to the finalizer queue
      if (VM_Finalizer.foundFinalizableObject) {
	gc_emptyWorkQueue();
	VM_CollectorThread.gcBarrier.rendezvous(RENDEZVOUS_TIMES || RENDEZVOUS_WAIT_TIME);
      }	
    }	// if existObjectsWithFinalizers
      
    if (TIME_GC_PHASES && (mylocal.gcOrdinal==1))  gcFinalizeDoneTime = VM_Time.now();

    // done
    if (GC_CLOBBERFREE) smallHeap.clobberfree();

    // Sweep large space
    if (mylocal.gcOrdinal == 1) {
      if (verbose >= 1) VM.sysWrite("Sweeping large space");
      largeHeap.endCollect();
    }

    // Sweep small heap
    smallHeap.sweep(mylocal);

    VM_CollectorThread.gcBarrier.rendezvous(RENDEZVOUS_TIMES || RENDEZVOUS_WAIT_TIME);

    // Each GC thread increments adds its wait times for this collection
    // into its total wait time - for printSummaryStatistics output
    //
    if (VM_CollectorThread.MEASURE_WAIT_TIMES)
      mylocal.incrementWaitTimeTotals();

    if (mylocal.gcOrdinal != 1) return;

    prepareNonParticipatingVPsForAllocation();

    gc_collect_now  = false;  // reset flag
    gcInProgress    = false;
    VM_GCLocks.reset();

    // done with collection...except for measurement counters, diagnostic output etc

    if (GC_COUNT_LIVE_OBJECTS) {
      int  small = 0;
      for (int i = 1; i < VM_Scheduler.numProcessors + 1; i++) {
        small  += VM_Scheduler.processors[i].small_live; 
        VM_Scheduler.processors[i].small_live  = 0; 
      }
      VM_Scheduler.trace("AFTER  GC", "small live =", small);
    }
    
    //TODO: complete the support for these data
    if (GC_STATISTICS) {
      //  report  high-water marks, then reset hints to the freeblock scanner 

      VM.sysWriteln("VM_Allocator:  markbootcount      = ", markboot_count); 
      VM.sysWriteln("VM_Allocator:  total_markbootcount  = ", total_markboot_count );
      //  report number of collections so far
      VM.sysWriteln(gcCount, "th  collection just completed");
    
      markboot_count  = 0;
      total_markboot_count  = 0;
    }  // end of statistics report  

    // always get time - for printSummaryStatistics
    gcEndTime = VM_Time.now();
    gcMinorTime  = gcEndTime - gcStartTime;
    gcTotalTime  += gcMinorTime;
    if (gcMinorTime > gcMaxTime) gcMaxTime = gcMinorTime;

    if (verbose >= 1 || GC_TIMING) {
        VM.sysWrite("\n<GC ", gcCount);
	VM.sysWriteln("  time ", ((int)(gcMinorTime * 1000)), " (ms)>"); 
    }

    if (TIME_GC_PHASES) accumulateGCPhaseTimes();  	

    if (verbose >= 1 && VM_CollectorThread.MEASURE_WAIT_TIMES)
      VM_CollectorThread.printThreadWaitTimes();
    else {
      if (VM_GCWorkQueue.MEASURE_WAIT_TIMES) {
	VM.sysWrite(" Wait Times for Scanning \n");
	VM_GCWorkQueue.printAllWaitTimes();
	VM_GCWorkQueue.saveAllWaitTimes();
	VM.sysWrite("\n Wait Times for Finalization \n");
	VM_GCWorkQueue.printAllWaitTimes();
	VM_GCWorkQueue.resetAllWaitTimes();
      }
    }

    if (VM_GCWorkQueue.WORKQUEUE_COUNTS) {
      VM.sysWrite(" Work Queue Counts for Scanning \n");
      VM_GCWorkQueue.printAllCounters();
      VM_GCWorkQueue.saveAllCounters();
      VM.sysWrite("\n WorkQueue Counts for Finalization \n");
      VM_GCWorkQueue.printAllCounters();
      VM_GCWorkQueue.resetAllCounters();
    }

    if (RENDEZVOUS_TIMES) VM_CollectorThread.gcBarrier.printRendezvousTimes();

    if (verbose >= 2) {
      VM.sysWrite(gcCount, "  collections ");
    }    

    if (verbose >= 1 && GC_COUNT_BY_TYPES) printCountsByType();

    smallHeap.postCollectionReport();
    if (flag2nd) {
      VM_Scheduler.trace(" End of gc:", "flag2nd on");
      smallHeap.freeSmallSpaceDetails(true);
      flag2nd = false;
    }
 
  }  // end of collect

  /**  gc_scanStacks():
   *  loop through the existing threads and scan their stacks by
   *  calling gc_scanStack, passing the stack frame where scanning
   *  is to start.  The stack under which the gc is running is treated
   *  differently: the current frame is not scanned, since there might
   *  be pointers to objects which have not been initialized.
   */
  static  void gc_scanStacks () {
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


  static void gc_scanObjectOrArray (VM_Address objRef ) {
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
  static  void gc_emptyWorkQueue()  {
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
  public  static void gc ()  {
    if (IGNORE_EXPLICIT_GC_CALLS) {
      if (Debug) VM.sysWrite("Skipped external call to collect garbage.\n");
      return;
    }
    gc1("GC triggered by external call to gc() ", 0);
  }


  public static void gc1 (String why, int size) {

    gc_collect_now  = true;

    if (verbose >= 1) VM.sysWriteln(why, size);

    //  Tell gc thread to reclaim space, then wait for it to complete its work.
    //  The gc thread will do its work by calling collect(), below.
    //
    double time = GC_MUTATOR_TIMES ? VM_Time.now() : 0.0;
    VM_CollectorThread.collect(VM_CollectorThread.collect);
    if (GC_MUTATOR_TIMES) {
      time = VM_Time.now() - time;
      VM_Scheduler.trace("GC time at mutator","(millisec)",(int)(time*1000.0));
      VM_Scheduler.trace("GC - time","     (millisec)",(int)(gcMinorTime*1000.0));
      VM_Scheduler.trace("GC TOTAL - time"," (millisec)",(int)(gcTotalTime*1000.0));
      VM_Scheduler.trace("GC TOTAL COLLECTIONS = ", "", gcCount);
    }
  }  //  gc1


  public  static long totalMemory () {
    return smallHeap.size + largeHeap.size;
  }

  public static long freeMemory () {
    return smallHeap.freeBlocks() * GC_BLOCKSIZE;
  }

  private static void accumulateGCPhaseTimes () {

    double start    = gcStartTime - VM_CollectorThread.gcBarrier.rendezvousStartTime;
    double init     = gcInitDoneTime - gcStartTime;
    double scanning = gcScanningDoneTime - gcInitDoneTime;
    double finalize = gcFinalizeDoneTime - gcScanningDoneTime;
    double finish   = gcEndTime - gcFinalizeDoneTime;

    // add current GC times into totals for summary output
    //    totalStartTime += start;   // always measured in ge initialization
    totalInitTime += init;
    totalScanningTime += scanning;
    totalFinalizeTime += finalize;
    totalFinishTime += finish;

    // if invoked with -verbose:gc print output line for this last GC
    if (verbose >= 1) {
      VM.sysWrite("<GC ", gcCount);
      VM.sysWrite(   (int)   (start*1000000.0), "(us) init ");
      VM.sysWrite(   (int)    (init*1000000.0), "(us) scanning ");
      VM.sysWrite(   (int)   (scanning*1000.0), "(ms) finalize ");
      VM.sysWrite(   (int)(finalize*1000000.0), "(us) finish ");
      VM.sysWriteln( (int)  (finish*1000000.0), "(us)>");
    }
  }



  static boolean gc_isLive (VM_Address ref) {
    if (bootHeap.refInHeap(ref) || immortalHeap.refInHeap(ref) ) {
      Object obj = VM_Magic.addressAsObject(ref);
      return VM_AllocatorHeader.testMarkBit(obj, OBJECT_GC_MARK_VALUE);
    } else if (smallHeap.refInHeap(ref)) {
      return smallHeap.isLive(ref);
    } else if (largeHeap.refInHeap(ref)) {
      return largeHeap.isLive(ref);
    } else {
      VM.sysWrite("gc_isLive: ref not in any known heap: ", ref);
      VM.assert(false);
      return false;
    }
  }


  // Normally called from constructor of VM_Processor
  // Also called a second time for the PRIMORDIAL processor during VM.boot
  //
  static  void setupProcessor (VM_Processor st) {
    // for the PRIMORDIAL processor allocation of sizes, etc occurs
    // during init(), nothing more needs to be done
    //
    if (st.id > VM_Scheduler.PRIMORDIAL_PROCESSOR_ID) 
      smallHeap.setupProcessor(st);
  }


  //  A routine to report number of live objects by type: numbers
  //  collected in processPtrFieldValue at return from gc_setMarkSmall()
  //
    private  static void printCountsByType()  {

	VM.sysWrite("  Counts of live objects by type \n");
	int  nextId = VM_TypeDictionary.getNumValues();
	int  totalObjects = 0, totalSpace = 0;
	for (int i = 1; i < nextId; i++) {
	    VM_Type type = VM_TypeDictionary.getValue(i);
	    totalObjects += type.liveCount;
	    if (type.liveCount == 0) continue;
	    VM.sysWriteField(7, type.liveCount);
	    VM.sysWrite(type.getDescriptor());
	    if (type.isClassType() && type.isResolved()) {
	      int size = type.asClass().getInstanceSize();
	      VM.sysWrite ("  space is ", size * type.liveCount);
	      totalSpace += size * type.liveCount;
	    }
	    else {
		VM.sysWrite("   space is ", type.liveSpace);
		totalSpace += type.liveSpace;
	    }
	    VM.sysWriteln();
	    type.liveCount = 0;  // reset
	    type.liveSpace = 0;  // reset
	}
	VM.sysWriteField(7, totalObjects);
	VM.sysWrite ("Live objects   space is ", totalSpace, "\n");
	VM.sysWrite("  End of counts of live objects by type \n");
    } // printCountsByType


  public  static void printclass (VM_Address ref) {
    VM_Type  type = VM_Magic.getObjectType(VM_Magic.addressAsObject(ref));
    VM.sysWrite(type.getDescriptor());
  }

/************  
  Print Summaries routine removed: if needed, consult previous version
**************/

  // Called from WriteBuffer code for generational collectors.
  // Argument is a modified old object which needs to be scanned
  //
  static void processWriteBufferEntry (VM_Address ref) {
    VM_ScanObject.scanObjectOrArray(ref);
  }

  static  boolean processFinalizerListElement(VM_FinalizerListElement  le) {
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

  static void clearSummaryStatistics () {
      VM_Processor st;
      for (int i = 1; i <= VM_Scheduler.numProcessors; i++) {
        st = VM_Scheduler.processors[i];
        st.totalBytesAllocated = 0;
        st.totalObjectsAllocated = 0;
        st.synchronizedObjectsAllocated = 0;
      }
  }


  static void  printSummaryStatistics () {
    int avgTime=0, avgStartTime=0;
    int np = VM_Scheduler.numProcessors;

	// produce summary system exit output if -verbose:gc was specified of if
	// compiled with measurement flags turned on
	//
	
	VM.sysWriteln("\nGC stats: Mark Sweep Collector (", np, " Collector Threads ):");
	VM.sysWriteln("          Heap Size ", smallHeap.size / 1024, " Kb");
	VM.sysWriteln("  Large Object Heap Size ", largeHeap.size / 1024, " Kb");
	
	if (gcCount>0) {
	    avgTime = (int)( ((gcTotalTime/(double)gcCount)*1000.0) );
	    avgStartTime = (int)( ((totalStartTime/(double)gcCount)*1000000.0) );
	}
	VM.sysWrite(gcCount, " Collections: avgTime ");
	VM.sysWrite(avgTime," (ms) maxTime ");
	VM.sysWrite((int)(gcMaxTime*1000.0)," (ms) totalTime ");
	VM.sysWrite((int)(gcTotalTime*1000.0)," (ms) avgStartTime ");
	VM.sysWrite(avgStartTime, " (us)\n\n");
	
	if (TIME_GC_PHASES) { 
	    int avgStart=0, avgInit=0, avgScan=0, avgFinalize=0, avgFinish=0;
	    if (gcCount>0) {
		avgStart = (int)((totalStartTime/(double)gcCount)*1000000.0);
		avgInit = (int)((totalInitTime/(double)gcCount)*1000000.0);
		avgScan = (int)((totalScanningTime/(double)gcCount)*1000.0);
		avgFinalize = (int)((totalFinalizeTime/(double)gcCount)*1000000.0);
		avgFinish = (int)((totalFinishTime/(double)gcCount)*1000000.0);
	    }
	    VM.sysWrite("Average Time in Phases of Collection:\n");
	    VM.sysWrite("startTime ", avgStart, "(us) init ");
	    VM.sysWrite( avgInit, "(us) scanning ");
	    VM.sysWrite( avgScan, "(ms) finalize ");
	    VM.sysWrite( avgFinalize, "(us) finish ");
	    VM.sysWrite( avgFinish, "(us)>\n\n");
	}
	
	if (VM_CollectorThread.MEASURE_WAIT_TIMES) {
	    double totalBufferWait = 0.0;
	    double totalFinishWait = 0.0;
	    double totalRendezvousWait = 0.0;  
	    int avgBufferWait=0, avgFinishWait=0, avgRendezvousWait=0;

	    VM_CollectorThread ct;
	    for (int i=1; i <= np; i++ ) {
		ct = VM_CollectorThread.collectorThreads[VM_Scheduler.processors[i].id];
		totalBufferWait += ct.totalBufferWait;
		totalFinishWait += ct.totalFinishWait;
		totalRendezvousWait += ct.totalRendezvousWait;
	    }
	    if (gcCount>0) {
		avgBufferWait = ((int)((totalBufferWait/(double)gcCount)*1000000.0))/np;
		avgFinishWait = ((int)((totalFinishWait/(double)gcCount)*1000000.0))/np;
		avgRendezvousWait = ((int)((totalRendezvousWait/(double)gcCount)*1000000.0))/np;
	    }
	    VM.sysWrite("Average Wait Times For Each Collector Thread In A Collection:\n");
	    VM.sysWrite("Buffer Wait ", avgBufferWait, " (us) Finish Wait ");
	    VM.sysWrite( avgFinishWait, " (us) Rendezvous Wait ");
	    VM.sysWrite( avgRendezvousWait, " (us)\n\n");
	    
	    VM.sysWriteln("Sum of Wait Times For All Collector Threads ( ", np, " Collector Threads ):");
	    VM.sysWrite("Buffer Wait ", (int)(totalBufferWait*1000.0), " (ms) Finish Wait ");
	    VM.sysWrite( (int)(totalFinishWait*1000.0), " (ms) Rendezvous Wait ");
	    VM.sysWrite( (int)(totalRendezvousWait*1000.0), " (ms) TOTAL ");
	    VM.sysWrite( (int)((totalRendezvousWait+totalFinishWait+totalBufferWait)*1000.0), " (ms)\n");
	}
	
	if (COUNT_ALLOCATIONS) {
	    VM.sysWrite(" Total No. of Objects Allocated in this run ");
	    long bytes = 0, objects = 0;
	    VM_Processor st;
	    for (int i = 1; i <= VM_Scheduler.numProcessors; i++) {
		st = VM_Scheduler.processors[i];
		bytes += st.totalBytesAllocated;
		objects += st.totalObjectsAllocated;
	    }
	    VM.sysWrite(Long.toString(objects));
	    VM.sysWrite("\n Total No. of bytes Allocated in this run ");
	    VM.sysWrite(Long.toString(bytes));
	    VM.sysWrite("\n");
	}
	
	VM.sysWrite(" Number of threads found stuck in native code = ");
	VM.sysWrite(VM_NativeDaemonThread.switch_count);
	VM.sysWrite("\n");
	
    }
      
    private static void outOfMemory (int size, String why) {
	VM_Scheduler.trace(" Out of memory", " could not satisfy bytes = ", size);
	VM_Scheduler.trace("              ", why);
	VM_Scheduler.trace(" Current small object heapsize ", "=", smallHeap.size);
	VM_Scheduler.trace(" Specify more space with", " -X:h=");
	VM.shutdown(-5);
    }


  /**
   * Process an object reference field during collection.
   *
   * @param location  address of a reference field
   */
  static void processPtrField ( VM_Address location ) {
      VM_Magic.setMemoryAddress(location, processPtrValue(VM_Magic.getMemoryAddress(location)));
  }

  /**
   * Process an object reference (value) during collection.
   *
   * @param ref  object reference (value)
   */
  static VM_Address processPtrValue (VM_Address ref) {
    if (ref.isZero()) return ref;  // TEST FOR NULL POINTER

    if (smallHeap.refInHeap(ref)) {
      if (!smallHeap.mark(ref)) {
	if (GC_COUNT_BY_TYPES) {
	  VM_Type type = VM_Magic.getObjectType(VM_Magic.addressAsObject(ref));
	  type.liveCount++;
	  if (type.isArrayType()) {
	    int  num_elements = VM_Magic.getArrayLength(VM_Magic.addressAsObject(ref));
	    int  full_size = type.asArray().getInstanceSize(num_elements);
	    type.liveSpace+=  full_size;
	  }
	}
	VM_GCWorkQueue.putToWorkBuffer(ref);
      }
      return ref;
    } 

    if (largeHeap.refInHeap(ref)) {
      if (!largeHeap.mark(ref))
	VM_GCWorkQueue.putToWorkBuffer(ref);
      return ref;
    }

    if (bootHeap.refInHeap(ref) || immortalHeap.refInHeap(ref) ) {
      if (VM_AllocatorHeader.testAndMark(VM_Magic.addressAsObject(ref), OBJECT_GC_MARK_VALUE))
	VM_GCWorkQueue.putToWorkBuffer(ref);
      return ref;
    }

    if (mallocHeap.refInHeap(ref))
      return ref;

    VM.sysWrite("processPtrValue: ref not in any known heap: ", ref);
    VM.assert(false);
    return VM_Address.zero();
  }  // processPtrValue
}
