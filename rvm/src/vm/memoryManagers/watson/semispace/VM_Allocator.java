/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Simple Semi-Space Copying Allocator/Collector
 * <p>
 * Heap Layout:
 * <pre>
 *  There are 6 heaps and their relative ordering is not assumed.
 *
 *     bootHeap, fromHeap, toHeap, largeHeap, immortalHeap
 *
 * </pre>
 * Allocation (with PROCESSOR_LOCAL_ALLOCATE = true - the default):
 * <p>
 * Allocation comes from 1 of the 2 semi-spaces, labeled "FromSpace".
 * When PROCESSOR_LOCAL_ALLOCATE is true, each processor maintains chunks
 *   of memory obtained using an FAA.  Within each chunk, space is allocated
 *   sequentially. Otherwise if PROCESSOR_LOCAL_ALLOATE is false, each allocation 
 *   requires a FAA.
 * <pre>
 *             FromSpace                         ToSpace            
 *  +--------------------------------+   +---------------------+
 *  |chunk | chunk | ...|->          |   |  reserved           |
 *  +--------------------------------+   +---------------------+
 *      areaCurAddr^    ^areaEndAddr
 *
 * Collection is initiated when a processor cannot acquire a new chunk.
 * The other semi-space is labeled "ToSpace". During collection, live
 * objects from FromSpace are copied to ToSpace. Collector threads
 * participating in the collection acquire space in ToSpace in chunks, and
 * allocated sequentially within their local chunks.
 * <pre>
 *           FromSpace                      ToSpace              
 *  +-----------------------------------+  +-----------------------------+
 *  |obj|obj| ...|obj|obj|obj|.....|obj||  | chunk | chunk | ...|->      |
 *  +-----------------------------------+  +-----------------------------+
 *	               |                              ^        
 *	               |                              |         ^matureCurrentAddr
 *	               +---->----> copied obj ---->---+        
 *
 *  processPtrField(*loc) {
 *     if (*loc is in FromSpace) { 
 *        attempt to mark *loc
 *        if (not previously marked) 
 *           copy object, set forwarding pointer, add to WorkQueue
 *        change *loc to the ToSpace copy of object
 *     }
 *     else (*loc is in bootImage or largeSpace or immortalHeap) {
 *        attempt to mark *loc
 *        if (not previously marked) add to WorkQueue
 *     }
 *  }
 *
 *  Approximate Collection Process:
 *    Find Root References by scanning thread stacks & static variables (JTOC)
 *    For each root reference do processPtrField()
 *    Process WorkQueue references until empty, for each entry:
 *      1. scan the pointed to object for pointer fields
 *      2. execute processPtrField for each pointer field
 * </pre>
 * Collection is performed in parallel by multiple VM_CollectorThreads.
 * Typically one VM_CollectorThread executes on each of the VM_Processors
 * configured in the RVM.  A load balancing work queue is
 * used to evenly distribute the workload over the multiple collector
 * threads.
 * <p>
 * After each collection FromSpace and ToSpace are inter-changed,
 * and VM_Processors start allocating from the new FromSpace.
 *
 * @see VM_GCWorkQueue
 * @see VM_CollectorThread
 * @see VM_Finalizer
 * @see VM_GCMapIterator
 * 
 * @author Stephen Smith
 *
 * @modified by Perry Cheng  Heavily re-written to factor out common code and adding VM_Address
 */


public class VM_Allocator  extends VM_GCStatistics
                           implements VM_Constants, 
				     VM_GCConstants, 
				     VM_Uninterruptible,
				     VM_Callbacks.ExitMonitor, 
				     VM_Callbacks.AppRunStartMonitor {

  static int verbose = 0; // control chattering during progress of GC

  /**
   * When true (the default), touch heap pages during startup to 
   * avoid page fault overhead during timing runs.
   */
  static final boolean COMPILE_FOR_TIMING_RUN = true;     
  
  /**
   * When true, causes each gc thread to measure accumulated wait times
   * during collection. Forces summary statistics to be generated.
   * See VM_CollectorThread.MEASURE_WAIT_TIMES.
   */
  static final boolean RENDEZVOUS_WAIT_TIME = VM_CollectorThread.MEASURE_WAIT_TIMES;

  /**
   * When true, measure rendezvous times and show them
   */
  static final boolean RENDEZVOUS_TIMES = false;

  /**
   * Initialize for boot image.
   */
  static void init () {
    VM_GCLocks.init();           // to alloc lock fields used during GC (in bootImage)
    VM_GCWorkQueue.init();       // to alloc shared work queue
    VM_CollectorThread.init();   // to alloc its rendezvous arrays, if necessary
  }

  /**
   * Initialize for execution.
   */
  static void boot (VM_BootRecord thebootrecord) {

    bootrecord = thebootrecord;	
    verbose = bootrecord.verboseGC;

    // smallHeapSize might not originally have been an even number of pages
    smallHeapSize = bootrecord.smallSpaceSize;
    int oneHeapSize = VM_Memory.roundUpPage(smallHeapSize / 2);
    int largeSize = bootrecord.largeSpaceSize;
    int ps = VM_Memory.getPagesize();
    int immortalSize = VM_Memory.roundUpPage(1024 * 1024 + (4 * largeSize / ps) + 4 * ps);

    if (verbose >= 2) VM.sysWriteln("Attaching heaps");
    VM_Heap.boot(bootHeap, bootrecord);
    fromHeap.attach(oneHeapSize);
    toHeap.attach(oneHeapSize);
    immortalHeap.attach(immortalSize);
    largeHeap.attach(largeSize);

    if (COMPILE_FOR_TIMING_RUN) {
      if (verbose >= 2) VM.sysWriteln("Touching toHeap, largeSpace, and fromHeap");
      toHeap.touchPages();
      // largeHeap.touchPages();
      fromHeap.touchPages();
    }

    fromHeap.reset();

    VM_GCUtil.boot();
    VM_Finalizer.setup();
 
    if (verbose >= 1) showParameter();

  }  // boot()

  static void showParameter() {
      int np = VM_Scheduler.numProcessors;
      VM.sysWriteln("Semi-Space Copying Collector (", np, " Processors)");
      bootHeap.show();
      immortalHeap.show();
      largeHeap.show();
      fromHeap.show();
      toHeap.show();
      if (ZERO_NURSERY_IN_PARALLEL)  VM.sysWrite("  Compiled with ZERO_NURSERY_IN_PARALLEL\n");
      if (ZERO_CHUNKS_ON_ALLOCATION) VM.sysWrite("  Compiled with ZERO_CHUNKS_ON_ALLOCATION\n");
      if (PROCESSOR_LOCAL_ALLOCATE)  VM.sysWrite("  Compiled with PROCESSOR_LOCAL_ALLOCATE\n");
      if (PROCESSOR_LOCAL_MATURE_ALLOCATE) VM.sysWrite("  Compiled with PROCESSOR_LOCAL_MATURE_ALLOCATE\n");
      if (writeBarrier) VM.sysWrite("WARNING - semi-space collector compiled with write barriers\n");
      VM.sysWriteln();
  }

  /**
   * To be called when the VM is about to exit.
   * @param value the exit value
   */
  public void notifyExit(int value) {
    printSummaryStatistics();
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
   * Force a garbage collection. Supports System.gc() called from
   * application programs.
   */
  public static void gc () {
    gcExternalCount++;
    gc1("GC triggered by external call to gc() ", 0);
  }

  /**
   * VM internal method to initiate a collection
   */
  private static void gc1 (String why, int size) {
    if (verbose >= 2) VM.sysWriteln("\n", why, size);

    if ( VM_Thread.getCurrentThread().isGCThread ) 
      VM.sysFail("VM_Allocator: Garbage Collection Failure: GC Thread attempting to allocate during GC");
  
    // notify GC threads to initiate collection, wait until done
    VM_CollectorThread.collect(VM_CollectorThread.collect);
  }  // gc1

  public static boolean gcInProgress() {
    return gcInProgress;
  }

  /**
   * Get total amount of memory.  Includes both full size of the
   * small object heap and the size of the large object heap.
   *
   * @return the number of bytes
   */
  public static long totalMemory () {
      return (smallHeapSize + largeHeap.size);    
  }
  
  /**
   * Get the number of bytes currently available for object allocation.
   * Includes shared pool of small object heap.
   *
   * @return number of bytes available
   */
  public static long freeMemory () {
    return fromHeap.freeMemory();
  }

  /*
   *  Includes freeMemory and per-processor local storage
   */
  public static long allSmallFreeMemory () {
    return freeMemory() + VM_Chunk.freeMemoryChunk1();
  }

  public static long allSmallUsableMemory () {
      return fromHeap.getSize();
  }

  
  /**
   * Allocate a scalar object. Fills in the header for the object,
   * and set all data fields to zero.
   *
   * @param size         size of object (including header), in bytes
   * @param tib          type information block for object
   *
   * @return the reference for the allocated object
   */
  public static Object allocateScalar (int size, Object[] tib)
    throws OutOfMemoryError {
    VM_Magic.pragmaInline();
    
    profileAlloc(size, tib); // profile/debug; usually inlined away to nothing

    VM_Address region = allocateRawMemory(size);
    return VM_ObjectModel.initializeScalar(region, tib, size);
  }   // end of allocateScalar() 
  

  /**
   * Allocate an array object. Fills in the header for the object,
   * sets the array length to the specified length, and sets
   * all data fields to zero.
   *
   * @param numElements  number of array elements
   * @param size         size of array object (including header), in bytes
   * @param tib          type information block for array object
   *
   * @return the reference for the allocated array object 
   */
  public static Object allocateArray (int numElements, int size, Object[] tib)
    throws OutOfMemoryError {
    VM_Magic.pragmaInline();

    profileAlloc(size, tib); // profile/debug: usually inlined away to nothing

    // note: array size might not be a word multiple,
    //       must preserve alignment of future allocations
    size = VM_Memory.align(size, WORDSIZE);

    VM_Address region = allocateRawMemory(size);  
    return VM_ObjectModel.initializeArray(region, tib, numElements, size);
  }  // allocateArray


  /**
   * Get space for a new object or array. 
   *
   * This code simply dispatches to one of three routines that actually
   * does the allocation.
   * (1) If the object is large, then the large heap
   * (2) Otherwise, either call VM_Chunk to do processor local
   *     allocation or
   * (3) call the allocation routine on fromHeap.
   * 
   * @param size number of bytes to allocate
   * @return the address of the first byte of the allocated zero-filled region
   */
  static VM_Address allocateRawMemory(int size) throws OutOfMemoryError {
    VM_Magic.pragmaInline();
    if (size >= SMALL_SPACE_MAX) {
      return allocateLargeObject(size);
    } else if (PROCESSOR_LOCAL_ALLOCATE) {
      return VM_Chunk.allocateChunk1(size);
    } else {
      VM_Address addr = allocateSmallObject(size);
      if (ZERO_CHUNKS_ON_ALLOCATION) VM_Memory.zeroTemp(addr, size);
      return addr;
    }
  }


  /**
   * Logic to handle allocations from the large heap,
   * triggering GC's as necessary and calling out of memory
   * when forced to.
   * 
   * @param size the number of bytes to allocate
   */
  private static VM_Address allocateLargeObject(int size) throws OutOfMemoryError {
    VM_Magic.pragmaNoInline();
    VM_Address addr = largeHeap.allocate(size);
    if (addr.isZero()) {
      for (int i=0; i<3; i++) {
	// There's a possible race condition where other Java threads
	// chew up all the large heap (and some of it becomes garbage)
	// before this thread gets to run again. 
	// So, we try a couple times before giving up.
	// This isn't a 100% solution, but it may handle it in practice.
	gc1("GC triggered by large object request of ", size);
	addr = largeHeap.allocate(size);
	if (!addr.isZero()) return addr;
      }
      largeHeap.outOfMemory(size);
    }
    return addr;
  }
  
  /**
   * Handle small space allocations when !PROCESSOR_LOCAL_ALLOCATE
   * @param size the number of bytes to allocate
   */
  private static VM_Address allocateSmallObject(int size) throws OutOfMemoryError {
    VM_Address addr = fromHeap.allocate(size);
    if (addr.isZero()) {
      for (int i=0; i<3; i++) {
	// There's a possible race condition where other Java threads
	// chew up all the small heap (and some of it becomes garbage)
	// before this thread gets to run again. 
	// So, we try a couple times before giving up.
	// This isn't a 100% solution, but it may handle it in practice.
	VM_Allocator.gc1("GC triggered by large object request of ", size);
	addr = fromHeap.allocate(size);
	if (!addr.isZero()) return addr;
      }
      outOfMemory(size);
    }
    return addr;
  }

  /**
   * Handle heap exhaustion.
   * 
   * @param size number of bytes requested in the failing allocation
   */
  public static void heapExhausted(VM_Heap heap, int size, int count) {
    if (count>3) outOfMemory(size);
    if (heap == fromHeap) {
      gc1("GC triggered by object request of ", size);
    } else if (heap == toHeap) {
      outOfMemory(-1);
    } else {
      VM.sysFail("unexpected heap");
    }
  }

  // *************************************
  // implementation
  // *************************************
  static final int      TYPE = 7;

  /** May this collector move objects during collction? */
  static final boolean movesObjects = true;

  /** Does this collector require that compilers generate the write barrier? */
  static final boolean writeBarrier = false;

  /**
   * The boundary between "small" objects and "large" objects. For the copying
   * allocators/collectors like this one, this boundary is somewhat arbitrary,
   * as long as it is less than 4K, the unit of allocation in the large object heap.
   */
  private static final int     SMALL_SPACE_MAX = 2048 + 1024 + 12;

  private static VM_ProcessorLock lock = new VM_ProcessorLock(); // for reporting out of memory
  private static boolean outOfMemoryReported = false;
  private static volatile boolean initGCDone = false;
  private static volatile boolean gcDone = false;
  
  static VM_BootRecord	 bootrecord;
  
  private static int smallHeapSize;  // total size of small object heaps = 2 * fromHeap.size
  private static VM_Heap bootHeap             = new VM_Heap("Boot Image Heap");   
  private static VM_ContiguousHeap fromHeap   = new VM_ContiguousHeap("Small Object Heap 1");
  private static VM_ContiguousHeap toHeap     = new VM_ContiguousHeap("Small Object Heap 2");
  private static VM_ImmortalHeap immortalHeap = new VM_ImmortalHeap();
  private static VM_LargeHeap largeHeap       = new VM_LargeHeap(immortalHeap);

  // TODO: these are no longer used; delete as soon as all allocators
  //       have been switched to the new VM_Chunk code.
  static VM_Address areaCurrentAddress;
  static VM_Address matureCurrentAddress;  

  static boolean gcInProgress;      // true if collection in progress, initially false

  // FromSpace object are "marked" if mark bit in statusword == MARK_VALUE
  // if "marked" == 0, then storing an aligned forwarding ptr also "marks" the
  // original FromSpace copy of the object (ie. GC's go faster
  // if "marked" == 1, then allocation of new objects do not require a store
  // to set the markbit on (unmarked), but returning a forwarding ptr requires
  // masking out the mark bit ( ie allocation faster, GC slower )
  //
  static final int MARK_VALUE = 1;        // to mark new/young objects already forwarded
  
  private static int BOOT_MARK_VALUE = 0;   // to mark bootimage objects during major GCs
  
  // ------- End of Statics --------

  static void gcSetup ( int numSysThreads ) {
    VM_GCWorkQueue.workQueue.initialSetup(numSysThreads);
  } // gcSetup

  /**
   * Print OutOfMemoryError message and exit.
   * TODO: make it possible to throw an exception, but this will have
   * to be done without doing further allocations (or by using temp space)
   */
  public static void outOfMemory (int size) {

    // First thread to be out of memory will write out the message,
    // and issue the shutdown. Others just spinwait until the end.

    if (size > SMALL_SPACE_MAX) {
	largeHeap.outOfMemory(size);
	return;
    }
    lock.lock();
    if (!outOfMemoryReported) {
      outOfMemoryReported = true;
      VM_Processor.getCurrentProcessor().disableThreadSwitching();
      VM.sysWriteln("\nOutOfMemoryError");
      VM.sysWriteln("Insufficient heap size for semi-space collector");
      VM.sysWriteln("Current heap size = ", smallHeapSize);
      VM.sysWriteln("Specify a larger heap using -X:h=nnn command line argument");
      // call shutdown while holding the processor lock
      VM.shutdown(-5);
    }
    else {
      lock.release();
      while( outOfMemoryReported == true );  // spin until VM shuts down
    }
  }


  private static void  prepareNonParticipatingVPsForGC() {
    // include NativeDaemonProcessor in following loop over processors
    for (int i = 1; i <= VM_Scheduler.numProcessors+1; i++) {
      VM_Processor vp = VM_Scheduler.processors[i];
      if (vp == null) continue;   // the last VP (nativeDeamonProcessor) may be null
      int vpStatus = VM_Processor.vpStatus[vp.vpStatusIndex];
      if ((vpStatus == VM_Processor.BLOCKED_IN_NATIVE) || (vpStatus == VM_Processor.BLOCKED_IN_SIGWAIT)) {
	if (vpStatus == VM_Processor.BLOCKED_IN_NATIVE) { 
	  // processor & its running thread are block in C for this GC.  Its stack
	  // needs to be scanned, starting from the "top" java frame, which has
	  // been saved in the running threads JNIEnv.  Put the saved frame pointer
	  // into the threads saved context regs, which is where the stack scan starts.
	  //
	  VM_Thread t = vp.activeThread;
	  t.contextRegisters.setInnermost( VM_Address.zero(), t.jniEnv.JNITopJavaFP);
	}

	// force exception if it comes back and tries to participate
	if (PROCESSOR_LOCAL_MATURE_ALLOCATE) 
	  VM_Chunk.resetChunk2(vp, null, false);

	// If writebarrier is being generated, presumably for measurement purposes, since
	// it is not used in this non-generational collector, then reset the write buffers
	// to empty so they don't overflow
	//
	if (writeBarrier)
	  vp.modifiedOldObjectsTop = VM_Magic.objectAsAddress(vp.modifiedOldObjects).sub(4);
      }
    }
  }

  private static void prepareNonParticipatingVPsForAllocation() {
    // include NativeDaemonProcessor in following loop over processors
    for (int i = 1; i <= VM_Scheduler.numProcessors+1; i++) {
      VM_Processor vp = VM_Scheduler.processors[i];

      if (vp == null) continue;   // the last VP (nativeDeamonProcessor) may be null

      int vpStatus = VM_Processor.vpStatus[vp.vpStatusIndex];
      if ((vpStatus == VM_Processor.BLOCKED_IN_NATIVE) || (vpStatus == VM_Processor.BLOCKED_IN_SIGWAIT)) {
        // Did not participate in GC. 
	// Reset chunk space to the new fromSpace, but don't acquire a chunk
	// since we might never use it.
	if (PROCESSOR_LOCAL_ALLOCATE) 
	  VM_Chunk.resetChunk1(vp, fromHeap, false);
      }
    }
  }


  /**
   * Perform a garbage collection.  Called from VM_CollectorThread run
   * method by each collector thread participating in a collection.
   *
   * ASSUMPTIONS:
   *    initGCDone flag is false before first GC thread enter collect
   *    initLock is reset before first GC thread enter collect
   */
  static void collect () {

    if (VM.BuildForEventLogging && VM.EventLoggingEnabled)
      VM_EventLogger.logGarbageCollectionEvent();
 
    // set running threads context regs so that a scan of its stack
    // will start at the caller of collect (ie. VM_CollectorThread.run)
    //
    VM_Address fp = VM_Magic.getFramePointer();
    VM_Address caller_ip = VM_Magic.getReturnAddress(fp);
    VM_Address caller_fp = VM_Magic.getCallerFramePointer(fp);
    VM_Thread.getCurrentThread().contextRegisters.setInnermost( caller_ip, caller_fp );
 
    // BEGIN SINGLE GC THREAD SECTION - GC INITIALIZATION

    double tempStart = 0.0, tempEnd = 0.0;
 
    if ( VM_GCLocks.testAndSetInitLock() ) {

      // Start timers to measure time since GC requested
      //
      startTime.start(VM_CollectorThread.gcBarrier.rendezvousStartTime); 
      initTime.start(startTime);
      GCTime.start(initTime.lastStart);

      // Set up flags 
      //
      if (VM.VerifyAssertions) VM.assert( initGCDone == false );  
      gcInProgress = true;
      gcDone = false;
      gcCount++;
      if (verbose >= 2) VM.sysWriteln("Starting GC ", gcCount);

      // Set up common workqueue for num VPs participating which varies from GC to GC.
      VM_GCWorkQueue.workQueue.initialSetup(VM_CollectorThread.numCollectors());

      gcInProgress = true;
      gcDone = false;

      // All space in toHeap is now available for allocation
      toHeap.reset();
 
      // invert the mark_flag value, used for marking BootImage objects
      BOOT_MARK_VALUE = BOOT_MARK_VALUE ^ VM_CommonAllocatorHeader.GC_MARK_BIT_MASK;
 
      // Now prepare large space for collection - clears out mark array
      largeHeap.startCollect();

      if (VM.ParanoidGCCheck) toHeap.unprotect();
      
      // this gc thread copies its own VM_Processor, resets processor register & processor
      // local allocation pointers (before copying first object to ToSpace)
      gc_initProcessor();
 
      // Some RVM VM_Processors may
      // be blocked in native C and not participating in a collection.
      prepareNonParticipatingVPsForGC();

      // precopy new VM_Thread objects, updating schedulers threads array
      // here done by one thread. could divide among multiple collector threads
      gc_copyThreads();
      
      VM_GCLocks.resetFinishLock();  // for singlethread'ing end of collections

      // must sync memory changes so GC threads on other processors see above changes
      // sync before setting initGCDone flag to allow other GC threads to proceed
      VM_Magic.sync();
    
      rootTime.start(initTime);

      // set Done flag to allow other GC threads to begin processing
      initGCDone = true;

    } // END SINGLE GC THREAD SECTION - GC INITIALIZATION

    else {
      // Each GC thread must wait here until initialization is complete
      // this should be short, if necessary at all, so we spin instead of sysYield
      //
      // It is NOT required that all GC threads reach here before any can proceed
      //
      tempStart = RENDEZVOUS_WAIT_TIME ? VM_Time.now() : 0.0;
      while( initGCDone == false ); // spin until initialization finished
      VM_Magic.isync();             // prevent following inst. from moving infront of waitloop
      tempEnd = RENDEZVOUS_WAIT_TIME ? VM_Time.now() : 0.0;

      // each gc thread copies own VM_Processor, resets processor register & processor
      // local allocation pointers, copies activeThread (itself) and resets workqueue buffers
      gc_initProcessor();
    }
    
    // ALL GC THREADS IN PARALLEL

    // each GC threads acquires ptr to its thread object, for accessing thread local counters
    // and workqueue pointers.  If the thread object needs to be moved, it has been, in copyThreads
    // above, and its ref in the threads array (in copyThreads) and the activeThread field of the
    // current processors VM_Processor (in initProcessor) have been updated  This means using either
    // of those fields to get "currentThread" get the copied thread object.
    //
    VM_CollectorThread mylocal = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());
    if (RENDEZVOUS_WAIT_TIME) 
	mylocal.rendezvousWaitTime += VM_CollectorThread.gcBarrier.rendezvousRecord(tempStart, tempEnd);

    // following rendezvous seems to be necessary, we are not sure why. Without it,
    // some processors proceed into finding roots, before all gc threads have
    // executed the above gc_initProcessor, and this seems related to the failure.
    // 
    mylocal.rendezvousWaitTime += VM_CollectorThread.gcBarrier.rendezvous(RENDEZVOUS_TIMES || RENDEZVOUS_WAIT_TIME);
         
    // Begin finding roots for this collection.
    // Root refs are updated and the copied object is enqueued for later scanning.
    gc_scanProcessor();           // each gc threads scans its own processor object
    VM_ScanStatics.scanStatics(); // all threads scan JTOC in parallel
    gc_scanThreads();             // all GC threads process thread objects & scan their stacks

    // This synchronization is necessary to ensure all stacks have been scanned
    // and all internal save ip values have been updated before we scan copied
    // objects.  Because if we scan a VM_Method, and then update its code pointer
    // we can no longer compute old ip offsets for updating saved ip values
    //
    //    REQUIRED SYNCHRONIZATION - WAIT FOR ALL GC THREADS TO REACH HERE
    mylocal.rendezvousWaitTime += VM_CollectorThread.gcBarrier.rendezvous(RENDEZVOUS_TIMES || RENDEZVOUS_WAIT_TIME);
    if (mylocal.gcOrdinal == 1)	scanTime.start(rootTime);

    // each GC thread processes its own work queue until empty
    if (verbose >= 2) VM.sysWriteln("  Emptying work queue");
    gc_emptyWorkQueue();
    if (mylocal.gcOrdinal == 1)	finalizeTime.start(scanTime);
    
    // If counting or timing in VM_GCWorkQueue, save current counter values
    //
    if (VM_GCWorkQueue.WORKQUEUE_COUNTS)   VM_GCWorkQueue.saveCounters(mylocal);
    if (VM_GCWorkQueue.MEASURE_WAIT_TIMES || VM_CollectorThread.MEASURE_WAIT_TIMES)
      VM_GCWorkQueue.saveWaitTimes(mylocal);

    // If there are not any objects with finalizers skip finalization phases.
    // Otherwise, one thread scans finalizer list for dead objects and resuscitated.
    //   Then, all threads process objects in parallel.
    if (VM_Finalizer.existObjectsWithFinalizers()) {

      /*** The following reset() will wait for previous use of workqueue to finish
	   ie. all threads to leave.  So no rendezvous is necessary (we hope)
         Without the reset, a rendezvous is necessary because some "slow" gc threads may still be
         in emptyWorkQueue (in VM_GCWorkQueue.getBufferAndWait) and have not seen
         the completionFlag==true.  The following call to reset will reset that
         flag to false, possibly leaving the slow GC threads stuck.  This rendezvous
         ensures that all threads have left the previous emptyWorkQueue, before
         doing the reset. (We could make reset smarter, and have it wait until
         the threadsWaiting count returns to 0, before doing the reset - TODO)

	 One thread scans the hasFinalizer list for dead objects.  They are made live
	 again, and put into that threads work queue buffers.
      ***/

      if (mylocal.gcOrdinal == 1) {
	VM_GCWorkQueue.workQueue.reset();   // reset work queue shared control variables
	VM_Finalizer.moveToFinalizable();
      }
      // ALL threads have to wait to see if any finalizable objects are found
      mylocal.rendezvousWaitTime += VM_CollectorThread.gcBarrier.rendezvous(RENDEZVOUS_TIMES || RENDEZVOUS_WAIT_TIME);
      if (VM_Finalizer.foundFinalizableObject) 
	gc_emptyWorkQueue();
    }  //  end of Finalization Processing
    if (verbose >= 2) VM.sysWriteln("  Finished finalizer processing");
    if (mylocal.gcOrdinal == 1) finishTime.start(finalizeTime);
      
    // gcDone flag has been set to false earlier
    //
    if ( VM_GCLocks.testAndSetFinishLock() ) {

      // BEGIN SINGLE GC THREAD SECTION FOR END OF GC

      // make any access to discarded fromspace impossible
      if (VM.ParanoidGCCheck) {
	  VM.sysWriteln("  Protecting fromspace");
	  fromHeap.protect();
	  toHeap.paranoidScan(fromHeap, false);
      }

      // Swap sense of toHeap and fromHeap
      VM_ContiguousHeap temp = fromHeap;
      fromHeap = toHeap;
      toHeap = temp;

      // round up current address in fromHeap to allow using efficient zeroPages()
      fromHeap.roundUpPage();

      // notify large space that all live objects marked at this point
      largeHeap.endCollect();

      // The remainder of the current semi-space must be zero'ed before allowing
      // any allocations.  This collector can be compiled to do this in either 
      // of three ways:
      //     - 1 GC threads zeros it all (the executing thread) (BAD !!)
      //     - All threads zero chunks in parallel (the executing thread
      //       determines the per thread regions to be zero'ed
      //     - Zeroing is deferred until processors allocate processor
      //       local chunks, while mutators are running (BEST ??)
      //
      if (!ZERO_NURSERY_IN_PARALLEL && !ZERO_CHUNKS_ON_ALLOCATION) {
	// have one processor (the executing one) do all the zeroing
	fromHeap.zeroFreeSpace();
      }

      // reset lock for next GC before starting mutators
      VM_GCLocks.reset();

      prepareNonParticipatingVPsForAllocation();

      gcInProgress = false;
     
      // reset the flag used to make GC threads wait until GC initialization
      // completed....for the next GC 
      initGCDone = false;

      VM_Magic.sync();

      gcDone = true;  // lets spinning GC threads continue
       
    }  // END OF SINGLE THREAD SECTION
    else {
      // other GC threads spin wait until finishing thread completes
      double start = RENDEZVOUS_WAIT_TIME ? VM_Time.now() : 0.0;
      while( gcDone == false );
      VM_Magic.isync();           // prevent from moving infront of waitloop
      if (RENDEZVOUS_WAIT_TIME) 
	  mylocal.rendezvousWaitTime += VM_CollectorThread.gcBarrier.rendezvousRecord(start, VM_Time.now());
    }

    // ALL GC THREADS IN PARALLEL - AFTER COLLECTION

    // have each processor begin allocations in next mutator cycle in what
    // remains of the current chunk acquired for copying objects during GC.
    // Note. it must be zero'ed
    //
    if (PROCESSOR_LOCAL_ALLOCATE) {
      if (PROCESSOR_LOCAL_MATURE_ALLOCATE) 
	VM_Chunk.swapChunks(VM_Processor.getCurrentProcessor());
      else 
	VM_Chunk.resetChunk1(VM_Processor.getCurrentProcessor(), fromHeap, false);
    }

    if (ZERO_NURSERY_IN_PARALLEL) 
      fromHeap.zeroFreeSpaceParallel();

    // Each GC thread increments adds its wait times for this collection
    // into its total wait time - for printSummaryStatistics output
    //
    if (VM_CollectorThread.MEASURE_WAIT_TIMES)
      mylocal.incrementWaitTimeTotals();
    if (mylocal.gcOrdinal == 1) {
	finishTime.stop();
	GCTime.stop(finishTime.lastStop);
    }

    // generate -verbosegc output, done here after zeroing nursery. 
    // only approx. since other gc threads may be zeroing in parallel
    // this is done by the 1 gc thread that finished the preceeding GC
    //
    if (mylocal.gcOrdinal == 1) {
	updateGCStats(DEFAULT, fromHeap.current().diff(fromHeap.start));
	printGCStats(DEFAULT);
    }

    return;
  }  // collect




  /**
   * Internal method called by collector threads during collection to
   * get space in ToSpace for a live object that needs to be copied.
   * Space is obtained from the processor local "chunk" if available,
   * otherwise space is obtained directly from ToSpace using 
   * atomic compare and swap instructions.
   */
  private static VM_Address gc_getToSpace(int size) {
    if (PROCESSOR_LOCAL_MATURE_ALLOCATE) {
      return VM_Chunk.allocateChunk2(size);
    } else {
      VM_Address addr = toHeap.allocate(size);
      if (addr.isZero()) outOfMemory(-1);
      return addr;
    }
  }

  /**
   * Processes live objects in FromSpace that need to be marked, copied and
   * forwarded during collection.  Returns the new address of the object
   * in ToSpace.  If the object was not previously marked, then the
   * invoking collector thread will do the copying and optionally enqueue the
   * on the work queue of objects to be scanned.
   *
   * @param fromObj Object in FromSpace to be processed
   * @param scan should the object be scanned?
   * @return the address of the Object in ToSpace (as a reference)
   */
  private static VM_Address copyAndScanObject (VM_Address fromRef, boolean scan) {

    if (VM.VerifyAssertions) VM.assert(fromHeap.refInHeap(fromRef));

    Object fromObj = VM_Magic.addressAsObject(fromRef);
    int forwardingPtr = VM_AllocatorHeader.attemptToForward(fromObj);
    VM_Magic.isync();   // prevent instructions moving infront of attemptToForward

    if (VM_AllocatorHeader.stateIsForwardedOrBeingForwarded(forwardingPtr)) {
      // if isBeingForwarded, object is being copied by another GC thread; 
      // wait (should be very short) for valid ptr to be set
      if (COUNT_COLLISIONS && VM_AllocatorHeader.stateIsBeingForwarded(forwardingPtr)) collisionCount++;
      while (VM_AllocatorHeader.stateIsBeingForwarded(forwardingPtr)) {
	forwardingPtr = VM_AllocatorHeader.getForwardingWord(fromObj);
      }
      VM_Magic.isync();  // prevent following instructions from being moved in front of waitloop
      VM_Address toRef = VM_Address.fromInt(forwardingPtr & ~VM_AllocatorHeader.GC_FORWARDING_MASK);
      if (VM.VerifyAssertions && !(VM_AllocatorHeader.stateIsForwarded(forwardingPtr) && VM_GCUtil.validRef(toRef))) {
	VM_Scheduler.traceHex("copyAndScanObject", "invalid forwarding ptr =",forwardingPtr);
	VM.assert(false);  
      }
      return toRef;
    }

    // We are the GC thread that must copy the object, so do it.
    Object[] tib = VM_ObjectModel.getTIB(fromObj);
    VM_Type type = VM_Magic.objectAsType(tib[TIB_TYPE_INDEX]);
    Object toObj;
    VM_Address toRef;
    if (VM.VerifyAssertions) VM.assert(VM_GCUtil.validObject(type));
    if (type.isClassType()) {
      VM_Class classType = type.asClass();
      int numBytes = VM_ObjectModel.bytesRequiredWhenCopied(fromObj, classType);
      VM_Address region = gc_getToSpace(numBytes);
      toObj = VM_ObjectModel.moveObject(region, fromObj, numBytes, classType, forwardingPtr);
      toRef = VM_Magic.objectAsAddress(toObj);
    } else {
      VM_Array arrayType = type.asArray();
      int numElements = VM_Magic.getArrayLength(fromObj);
      int numBytes = VM_ObjectModel.bytesRequiredWhenCopied(fromObj, arrayType, numElements);
      VM_Address region = gc_getToSpace(numBytes);
      toObj = VM_ObjectModel.moveObject(region, fromObj, numBytes, arrayType, forwardingPtr);
      toRef = VM_Magic.objectAsAddress(toObj);
      if (arrayType == VM_Type.CodeType) {  // conservatively includes all int/byte arrays
	// must sync moved code instead of sync'ing chunks when full
	int dataSize = numBytes - VM_ObjectModel.computeHeaderSize(VM_Magic.getObjectType(toObj));
	VM_Memory.sync(toRef, dataSize);
      }
    }

    VM_Magic.sync(); // make changes viewable to other processors 
    VM_AllocatorHeader.setForwardingPointer(fromObj, toObj);
    if (scan) VM_GCWorkQueue.putToWorkBuffer(toRef);
    return toRef;
  }

  // called by ONE gc/collector thread to copy and "new" thread objects
  // copies but does NOT enqueue for scanning
  //
  private static void gc_copyThreads () {
    
    for (int i=0; i<VM_Scheduler.threads.length; i++ ) {

      VM_Thread t = VM_Scheduler.threads[i];
      if (t == null) continue;
      
      VM_Address tAddr = VM_Magic.objectAsAddress(t);
      if (fromHeap.refInHeap(tAddr)) {
	tAddr = copyAndScanObject(tAddr, false);
	t = VM_Magic.objectAsThread(VM_Magic.addressAsObject(tAddr));
	// change entry in threads array to point to new copy of thread
	VM_Magic.setObjectAtOffset( VM_Scheduler.threads, i*4, t);
      }
    }  // end of loop over threads[]

  } // gc_copyThreads
     

  // Scans all threads in the VM_Scheduler threads array.  A threads stack
  // will be copied if necessary and any interior addresses relocated.
  // Each threads stack is scanned for object references, which will
  // becomes Roots for a collection.
  //
  // All collector threads execute here in parallel, and compete for
  // individual threads to process.  Each collector thread processes
  // its own thread object and stack.
  //
  static void gc_scanThreads ()  {

    // get ID of running GC thread
    int myThreadId = VM_Thread.getCurrentThread().getIndex();
    int[] oldstack;
    
    for (int i=0; i<VM_Scheduler.threads.length; i++ ) {

      VM_Thread t = VM_Scheduler.threads[i];
      VM_Address ta = VM_Magic.objectAsAddress(t);
      
      if ( t == null )
	continue;

      // let each GC thread scan its own thread object to force updating
      // of the header TIB pointer, and possible copying of register arrays
      // stacks are supposed to be in the bootimage (for now)

      if ( i == myThreadId ) {  // at thread object for running gc thread

	// GC threads are assumed not to have native processors.  if this proves
	// false, then we will have to deal with its write buffers
	//
	if (VM.VerifyAssertions) VM.assert(t.nativeAffinity == null);
	
	// all threads should have been copied out of fromspace earlier
	if (VM.VerifyAssertions) VM.assert( !fromHeap.refInHeap(ta));
	
	if (VM.VerifyAssertions) oldstack = t.stack;    // for verifying  gc stacks not moved
	VM_ScanObject.scanObjectOrArray(t);
	if (VM.VerifyAssertions) VM.assert(oldstack == t.stack);
	
	if (t.jniEnv != null) VM_ScanObject.scanObjectOrArray(t.jniEnv);

	VM_ScanObject.scanObjectOrArray(t.contextRegisters);

	VM_ScanObject.scanObjectOrArray(t.hardwareExceptionRegisters);

	VM_ScanStack.scanStack( t, VM_Address.zero(), true );
	
	continue;
      }

      // skip other collector threads participating (have ordinal number) in this GC
      if ( t.isGCThread && (VM_Magic.threadAsCollectorThread(t).gcOrdinal > 0) )
	continue;

      // have thread to be processed, compete for it with other GC threads
      if ( VM_GCLocks.testAndSetThreadLock(i) ) {

	if (verbose >= 3) VM.sysWriteln("    Processing mutator thread ",i);
	
	// all threads should have been copied out of fromspace earlier
	if (VM.VerifyAssertions) VM.assert( !(fromHeap.refInHeap(ta)));
	
	// scan thread object to force "interior" objects to be copied, marked, and
	// queued for later scanning.
	oldstack = t.stack;    // remember old stack address before scanThread
	VM_ScanObject.scanObjectOrArray(t);
	
	// if stack moved, adjust interior stack pointers
	if ( oldstack != t.stack ) {
	  if (verbose >= 3) VM.sysWriteln("    Adjusting mutator stack ",i);
	  t.fixupMovedStack(VM_Magic.objectAsAddress(t.stack).diff(VM_Magic.objectAsAddress(oldstack)));
	}
	
	// the above scanThread(t) will have marked and copied the threads JNIEnvironment object,
	// but not have scanned it (likely queued for later scanning).  We force a scan of it now,
	// to force copying of the JNI Refs array, which the following scanStack call will update,
	// and we want to ensure that the updates go into the "new" copy of the array.
	//
	if (t.jniEnv != null) VM_ScanObject.scanObjectOrArray(t.jniEnv);
	
	// Likewise we force scanning of the threads contextRegisters, to copy 
	// contextRegisters.gprs where the threads registers were saved when it yielded.
	// Any saved object references in the gprs will be updated during the scan
	// of its stack.
	//
	VM_ScanObject.scanObjectOrArray(t.contextRegisters);

	VM_ScanObject.scanObjectOrArray(t.hardwareExceptionRegisters);
	
	// all threads in "unusual" states, such as running threads in
	// SIGWAIT (nativeIdleThreads, nativeDaemonThreads, passiveCollectorThreads),
	// set their ContextRegisters before calling SIGWAIT so that scans of
	// their stacks will start at the caller of SIGWAIT
	//
	// fp = -1 case, which we need to add support for again
	// this is for "attached" threads that have returned to C, but
	// have been given references which now reside in the JNIEnv sidestack

	if (verbose >= 3) VM.sysWriteln("    Scanning stack for thread ",i);
	VM_ScanStack.scanStack( t, VM_Address.zero(), true );
	
      }  // (if true) we seized got the thread to process
      
      else continue;  // some other gc thread has seized this thread
      
    }  // end of loop over threads[]
    
  }  // gc_scanThreads
  
  
  // initProcessor is called by each GC thread to copy the processor object of the
  // processor it is running on, and reset it processor register, and update its
  // entry in the scheduler processors array and reset its local allocation pointers
  //
  static void gc_initProcessor ()  {

    VM_Processor st = VM_Processor.getCurrentProcessor();  // get current Processor Register

    // if compiled for processor local chunking of "mature space" reset processor local 
    // pointers, to cause first request to get a block (only reset on major collection
    // for minor collection, continue filling last/current mature buffer
    //
    if (PROCESSOR_LOCAL_MATURE_ALLOCATE) {
      VM_Chunk.resetChunk2(st, toHeap, true);
    }
  
    // if Processor is in fromHeap, copy and update array entry
    VM_Address stAddr = VM_Magic.objectAsAddress(st);
    if (fromHeap.refInHeap(stAddr)) {
      stAddr = copyAndScanObject(stAddr, false);   // copies object, does not queue for scanning
      st = VM_Magic.objectAsProcessor(VM_Magic.addressAsObject(stAddr));
      // change entry in system threads array to point to copied sys thread
      VM_Magic.setObjectAtOffset(VM_Scheduler.processors, st.id*4, st);
    }
  
    // each gc thread updates its PROCESSOR_REGISTER after copying its 
    // VM_Processor object
    VM_ProcessorLocalState.setCurrentProcessor(st);

    // reset local heap pointers - to prevent allocation during GC.
    //
    if (PROCESSOR_LOCAL_ALLOCATE) {
      // force exception upon attempt to allocate during GC.
      VM_Chunk.resetChunk1(st, null, false);
    }
  
    // if Processors activethread (should be current, gc, thread) field -> in FromSpace,
    // ie. still points to old copy of VM_Thread, make it point to the copied ToSpace
    // copy. All threads copied, and the VM_Scheduler.threads array updated in GC initialization.
    // We want BOTH ways of computing getCurrentThread to return the new copy.
    //
    VM_Thread activeThread = st.activeThread;
    VM_Address activeThreadAddr = VM_Magic.objectAsAddress(activeThread);
    if (fromHeap.refInHeap(activeThreadAddr)) {
      // following gc_copyObject call will return new address of the thread object
      activeThreadAddr = copyAndScanObject(activeThreadAddr, false);
      st.activeThread = VM_Magic.objectAsThread(VM_Magic.addressAsThread(activeThreadAddr));
    }
  
    // setup the work queue buffers for this gc thread
    VM_GCWorkQueue.resetWorkQBuffers();
    
  }  //gc_initProcessor


  // scan a VM_Processor object to force "interior" objects to be copied, marked,
  // and queued for later scanning. adjusts write barrier pointers, if
  // write buffer is moved.
  //
  static void gc_scanProcessor () {

    VM_Processor   st = VM_Processor.getCurrentProcessor();
    // verify that processor copied out of FromSpace earlier
    if (VM.VerifyAssertions) VM.assert( ! fromHeap.refInHeap(VM_Magic.objectAsAddress(st)));

    VM_Address oldbuffer = VM_Magic.objectAsAddress(st.modifiedOldObjects);
    VM_ScanObject.scanObjectOrArray(st);
    // if writebuffer moved, adjust interior pointers
    VM_Address newbuffer = VM_Magic.objectAsAddress(st.modifiedOldObjects);
    if (oldbuffer.NE(newbuffer)) {
      if (verbose >= 2) VM.sysWriteln("  Write buffer copied ",st.id);
      st.modifiedOldObjectsMax = newbuffer.add(st.modifiedOldObjectsMax.diff(oldbuffer));
      st.modifiedOldObjectsTop = newbuffer.add(st.modifiedOldObjectsTop.diff(oldbuffer));
    }
  }  // gc_scanProcessor
 

  // Process references in work queue buffers until empty.
  //
  static void gc_emptyWorkQueue () {

    VM_Address ref = VM_GCWorkQueue.getFromWorkBuffer();

    if (VM_GCWorkQueue.WORKQUEUE_COUNTS) {
      VM_CollectorThread myThread = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());
      myThread.rootWorkCount = myThread.putWorkCount;
    }
    
    while (!ref.isZero()) {
      VM_ScanObject.scanObjectOrArray( ref );	   
      ref = VM_GCWorkQueue.getFromWorkBuffer();
    }
  }  // gc_emptyWorkQueue 




  static boolean validForwardingPtr (VM_Address ref ) {
      return toHeap.refInHeap(ref);
  }

  // Somebody tried to allocate an object within a block
  // of code guarded by VM.disableGC() / VM.enableGC().
  //
  private static void fail () {
    VM.sysWrite("vm error: allocator/collector called within critical section\n");
    VM.assert(false);
  }

  /*
   * Initialize a VM_Processor for allocation and collection.
   */
  static void setupProcessor (VM_Processor p) {
    if (PROCESSOR_LOCAL_ALLOCATE) 
      VM_Chunk.resetChunk1(p, fromHeap, false);
    if (writeBarrier)
      VM_WriteBuffer.setupProcessor(p);
  }

  // Check if the "integer" pointer points to a dead or live object.
  // If live, and in the FromSpace (ie has been marked and forwarded),
  // then update the integer pointer to the objects new location.
  // If dead, then force it live, copying it if in the FromSpace, marking it
  // and putting it on the workqueue for scanning.
  //
  // in this collector (copyingGC) allocated objects with finalizers can
  // only be in the semi-space heap, or large space...and so far, only
  // arrays exist in large space, and they do not have finalizers...but we
  // allow for large space objects anyway
  //
  // Called by ONE GC collector thread at the end of collection, after
  // all reachable object are marked and forwarded
  //
  static boolean processFinalizerListElement (VM_FinalizerListElement le) {

    VM_Address ref = le.value;
    if (fromHeap.refInHeap(ref)) {
      Object obj = VM_Magic.addressAsObject(ref);
      if (VM_AllocatorHeader.isForwarded(obj)) {
	// live, set le.value to forwarding address
	le.move(VM_Magic.objectAsAddress(VM_AllocatorHeader.getForwardingPointer(obj)));
	return true;
      }
      else {
	// dead, mark, copy, and enque for scanning, and set le.pointer
	le.finalize(copyAndScanObject(ref, true));
	return false;
      }
    }
    else {
      if (VM.VerifyAssertions) VM.assert(largeHeap.refInHeap(ref));
      if (largeHeap.isLive(ref))
	return true;  
      else {
	largeHeap.mark(ref);
	VM_GCWorkQueue.putToWorkBuffer(ref);
	le.finalize(ref);
	return false;
      }
    }
  }  // processFinalizerListElement
  
  // Called from WriteBuffer code for generational collectors.
  // Argument is a modified old object which needs to be scanned
  //
  static void processWriteBufferEntry (VM_Address ref) {
    VM_ScanObject.scanObjectOrArray(ref);
  }
  
  /**
   * Process an object reference field during collection.
   * Called from GC utility classes like VM_ScanStack.
   *
   * @param location  address of a reference field
   */
  static void processPtrField (VM_Address location) {
    VM_Magic.setMemoryAddress(location, processPtrValue(VM_Magic.getMemoryAddress(location)));
  }

  /**
   * Process an object reference (value) during collection.
   * Called from GC utility classes like VM_ScanStack.
   *
   * @param location  address of a reference field
   */
  static VM_Address processPtrValue (VM_Address ref ) {
  
    if (ref.isZero()) return ref;
  
    // in FromSpace, if not marked, mark, copy & queue for scanning
    if (fromHeap.refInHeap(ref)) 
      return copyAndScanObject(ref, true);

    if (bootHeap.refInHeap(ref) || 
	immortalHeap.refInHeap(ref)) {
	if (  !VM_AllocatorHeader.testAndMark(VM_Magic.addressAsObject(ref), BOOT_MARK_VALUE) )
	    return ref;   // object already marked with current mark value
	// we marked a previously unmarked object - add object ref to GC work queue
	VM_GCWorkQueue.putToWorkBuffer(ref);
	return ref;
    }

    if (largeHeap.refInHeap(ref)) {
	if (!largeHeap.mark(ref))                   // if not previously marked, 
	    VM_GCWorkQueue.putToWorkBuffer(ref);     //    need to scan later
	return ref;
    }

    if (toHeap.refInHeap(ref)) 
	return ref;

    if (VM.VerifyAssertions) {
	VM.sysWriteln("procesPtrValue: ref value not in any known heap: ", ref);
	showParameter();
	VM.sysFail("procesPtrValue: ref value not in any known heap");
    }

    return VM_Address.zero();

  } // processPtrValue

    
  //-#if RVM_WITH_ONE_WORD_MASK_OBJECT_MODEL
  static Object[] newTIB (int n) {
      if (! VM.runningVM)
	  return new Object[n];

      VM_Array objectArray    = VM_Type.JavaLangObjectArrayType;
      Object[] objectArrayTIB = objectArray.getTypeInformationBlock();
      int      size           = VM_Type.JavaLangObjectArrayType.getInstanceSize(n);
      int      storage        = immortalHeap.allocateRawMemory(size, VM_JavaHeader.TIB_ALIGNMENT, 
							       VM_JavaHeader.computeArrayHeaderSize(objectArray));
      Object[] newtib         = (Object[]) VM_ObjectModel.initializeArray(storage, objectArrayTIB, n, size);

      VM_AllocatorHeader.writeMarkBit(newtib, BOOT_MARK_VALUE);
      if (VM.VerifyAssertions) VM.assert((VM_Magic.objectAsAddress(newtib) & (VM_JavaHeader.TIB_ALIGNMENT-1)) == 0);
      return newtib;
  }
  //-#endif

}   // VM_Allocator
