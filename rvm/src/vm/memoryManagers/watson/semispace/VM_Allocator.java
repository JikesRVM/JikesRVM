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

public class VM_Allocator

 implements VM_Constants, VM_GCConstants, VM_Uninterruptible,
	    VM_Callbacks.ExitMonitor, VM_Callbacks.AppRunStartMonitor {

  private static final boolean debugNative = false;   // temporary flag for debugging new threads pkg

  static int verbose = 0; // control chattering during progress of GC

  /**
   * When true (the default), VM_Processors acquire chunks of space from
   * the shared Small Object Heap, and then allocate from within their
   * local chunks.
   */
  static final boolean PROCESSOR_LOCAL_ALLOCATE = true;

  /**
   * When true (the default), Collector Threads acquire chunks of space
   * from FromSpace during collection, and allocate space for copying
   * live objects from their local chunks.
   */
  static final boolean PROCESSOR_LOCAL_MATURE_ALLOCATE = true;

  /**
   * When true (the default), touch heap pages during startup to 
   * avoid page fault overhead during timing runs.
   */
  static final boolean COMPILE_FOR_TIMING_RUN = true;     
  
  // set at most one of the following 2 zeroing options on, if neither is
  // on then one processor zeros at end of GC (a bad idea, keep for comparison)

  /**
   * When true, all collector threads zero the space for new allocations
   * in parallel, at the end of a collection, before mutators execute.
   */
  static final boolean ZERO_NURSERY_IN_PARALLEL = false;

  /**
   * When true (the default), no zeroing is done at the end of a collection.
   * Instead, each VM_Processor zeros the chunks of heap it acquires in order
   * to satisfy allocation requests.
   */
  static final boolean ZERO_BLOCKS_ON_ALLOCATION = true;

  /**
   * When true, causes time spent in each phase of collection to be measured.
   * Forces summary statistics to be generated. See VM_CollectorThread.TIME_GC_PHASES.
   */
  static final boolean TIME_GC_PHASES = VM_CollectorThread.TIME_GC_PHASES;

  /**
   * When true, causes each gc thread to measure accumulated wait times
   * during collection. Forces summary statistics to be generated.
   * See VM_CollectorThread.MEASURE_WAIT_TIMES.
   */
  static final boolean RENDEZVOUS_WAIT_TIME = VM_CollectorThread.MEASURE_WAIT_TIMES;

  /**
   * Flag for counting bytes allocated and objects allocated
   */
  static final boolean COUNT_ALLOCATIONS = false;

  /** count times parallel GC threads attempt to mark the same object */
  private static final boolean COUNT_COLLISIONS = false;

  /**
   * When true, measure rendezvous times and show them
   */
  static final boolean RENDEZVOUS_TIMES = false;

  /**
   * Initialize for boot image.
   */
  static void init () {

    VM_GCLocks.init();    // to alloc lock fields used during GC (in bootImage)
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

    // Allocation of small objects are from from-space
    areaCurrentAddress = fromHeap.start;
    areaEndAddress     = fromHeap.end;

    // Initialize these fields use for FAAs
    if (verbose >= 2) VM.sysWriteln("Setting up addresses used for FAA");
    addrAreaCurrentAddress = VM_Magic.getTocPointer().add(VM_Entrypoints.areaCurrentAddressField.getOffset());
    addrMatureCurrentAddress = VM_Magic.getTocPointer().add(VM_Entrypoints.matureCurrentAddressField.getOffset());
    

    if (COMPILE_FOR_TIMING_RUN) {
      if (verbose >= 2) VM.sysWriteln("Touching toHeap, largeSpace, and fromHeap");
      toHeap.touchPages();
      // largeHeap.touchPages();
      fromHeap.touchPages();
    }

    VM_GCUtil.boot();
    VM_Finalizer.setup();
    // VM_Statistics.boot();
 
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
      if (ZERO_BLOCKS_ON_ALLOCATION) VM.sysWrite("  Compiled with ZERO_BLOCKS_ON_ALLOCATION\n");
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

    // if here and in a GC thread doing GC then it is a system error,
    //  GC thread must have attempted to allocate.
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
    return areaEndAddress.diff(areaCurrentAddress);
  }

  /*
   *  Includes freeMemory and per-processor local storage
   */
  public static long allSmallFreeMemory () {
      int sum = areaEndAddress.diff(areaCurrentAddress);
      for (int i=0; i<VM_Scheduler.numProcessors; i++) {
	  VM_Processor st = VM_Scheduler.processors[i+1];
	  sum += st.localEndAddress.diff(st.localCurrentAddress);
      }
      return sum;
  }
  
  /**
   * Get space for a new object or array. 
   *
   * Fast: If compiled to allocate from processor-local chunks, then it will attempt
   * to request from the current chunk if the requested space is small (< SMALL_SPACE_MAX).
   * Otherwise, the "slow" version is called.  The "fast" version is basically the slow
   * version assuming path 2 using the local chunk is successful.
   * 
   * Slow: Self-contained.  Will work even if not called from "Fast" version.
   * (1) If the size is greater than SMALL_SPACE_MAX, then space is allocated from 
   *     from the shared large object heap.
   * (2) If processor-local allocation is used, then we check if there is enough
   *     the current chunk for space.  If not enough remains, then get a new chunk
   *     from the shared heap with an FAA and allocate from the new chunk.
   * (3) IF no processor-local allocation, directly allocate from shared heap.
   * (4) A collection is initiated if space is still not acquired.
   *     After the collection, it will retry acquiring the space, and if it fails
   *     again, the system will exit with an "OUT OF MEMORY" error message.
   *     (A TODO is to throw a proper java Exception)
   * 
   * @param size number of bytes to allocate
   * @return the address of the first byte of the allocated zero-filled region
   */
  static VM_Address getHeapSpaceFast ( int size ) {
    VM_Magic.pragmaInline();

    if (PROCESSOR_LOCAL_ALLOCATE) { 
      if (size < SMALL_SPACE_MAX) {
	// NOTE: This code sequence is carefully written to generate
	//       optimal code when inlined by the optimzing compiler.  
	//       If you change it you must verify that the efficient 
	//       inlined allocation sequence isn't hurt! --dave
	VM_Address oldCurrent = VM_Processor.getCurrentProcessor().localCurrentAddress;
	VM_Address newCurrent = oldCurrent.add(size);
	if (newCurrent.LE(VM_Processor.getCurrentProcessor().localEndAddress)) {
	  VM_Processor.getCurrentProcessor().localCurrentAddress = newCurrent;
	  // if we didn't zero the block when we gave it to the processor,
	  // then we must zero it now.
	  if (!ZERO_BLOCKS_ON_ALLOCATION) VM_Memory.zeroTemp(oldCurrent, size);
	  return oldCurrent;
	}
      }
    } 
    return getHeapSpaceSlow(size);
  }  // getHeapSpaceFast


  static VM_Address getHeapSpaceSlow ( int size ) {
    VM_Magic.pragmaNoInline();	     

    // Sanity check
    if (VM.VerifyAssertions) {
      VM_Thread t = VM_Thread.getCurrentThread();
      VM.assert( gcInProgress == false );
      VM.assert( (t.disallowAllocationsByThisThread == false) && ((size & 3) == 0) );
    }

    VM_Address addr = getHeapSpaceSlowOnce(size);
    if (!addr.isZero())
	return addr;
    // GC and try again
    gc1("GC triggered by object request of ", size);
    addr = getHeapSpaceSlowOnce(size);
    if (addr.isZero())
	outOfMemory(size);
    return addr;
  }


  static VM_Address getHeapSpaceSlowOnce ( int size ) {

    VM_Magic.pragmaNoInline();	     
    
    // if large object, allocate from large object space
    if (size > SMALL_SPACE_MAX) 
	return largeHeap.allocate(size);
    
    // otherwise, handle normal allocation of small objects in heap using chunks
    if (PROCESSOR_LOCAL_ALLOCATE) {
	VM_Processor st = VM_Processor.getCurrentProcessor();
      if (st.localCurrentAddress.add(size).LE(st.localEndAddress)) {
	  VM_Address addr = st.localCurrentAddress;
	  st.localCurrentAddress = st.localCurrentAddress.add(size);
	  return addr;
      }
      else { // not enough space in local chunk, get the next chunk for allocation
	 VM_Address addr = VM_Synchronization.fetchAndAddAddressWithBound(addrAreaCurrentAddress,
							       CHUNK_SIZE, areaEndAddress);
  	 if (addr.isMax()) 
	     return VM_Address.zero();
	 st.localEndAddress = addr.add(CHUNK_SIZE);
	 st.localCurrentAddress = addr.add(size);
	 if (ZERO_BLOCKS_ON_ALLOCATION)
	     VM_Memory.zeroPages(addr, CHUNK_SIZE);
	 return addr;
      }
    }

    // if no chunk allocation, use fetchAndAdd directly
    VM_Address addr =  VM_Synchronization.fetchAndAddAddressWithBound(addrAreaCurrentAddress,
								      size, areaEndAddress);
    if (addr.isMax())
	return VM_Address.zero();
    VM_Memory.zeroTemp(addr, size);
    return addr;
    
  }  // getHeapSpaceSlowOnce

  
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
    
    debugAlloc(size, tib); // debug; usually inlined away to nothing

    VM_Address region = getHeapSpaceFast(size);
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

    debugAlloc(size, tib); // debug: usually inlined away to nothing

    // note: array size might not be a word multiple,
    //       must preserve alignment of future allocations
    size = VM_Memory.align(size, WORDSIZE);

    VM_Address region = getHeapSpaceFast(size);  
    return VM_ObjectModel.initializeArray(region, tib, numElements, size);
  }  // allocateArray


  // *************************************
  // implementation
  // *************************************

  static final int      TYPE = 7;

  /** May this collector move objects during collction? */
  static final boolean movesObjects = true;

  /** Does this collector require that compilers generate the write barrier? */
  static final boolean writeBarrier = false;

  /**
   * Size of a processor local region of the heap used for local allocation without
   * synchronization, also the size of the processor local chunks of ToSpace
   * acquired during GC for copying live objects
   */
  final static int     CHUNK_SIZE = 64 * 1024;

  /**
   * The boundary between "small" objects and "large" objects. For the copying
   * allocators/collectors like this one, this boundary is somewhat arbitrary,
   * as long as it is less than 4K, the unit of allocation in the large object heap.
   */
  static final int     SMALL_SPACE_MAX = 2048 + 1024 + 12;

  // size of buffer acquired to allow allocations while the system is crashing
  private final static int     CRASH_BUFFER_SIZE = 1024 * 1024;
  
  private static boolean outOfMemoryReported = false;
  private static volatile boolean initGCDone = false;
  private static volatile boolean gcDone = false;
  
  static VM_BootRecord	 bootrecord;
  
  private static int smallHeapSize;  // total size of small object heaps = 2 * fromHeap.size
  private static VM_Heap bootHeap             = new VM_Heap("Boot Image Heap");   
  private static VM_Heap fromHeap             = new VM_Heap("Small Object Heap 1");
  private static VM_Heap toHeap               = new VM_Heap("Small Object Heap 2");
  private static VM_ImmortalHeap immortalHeap = new VM_ImmortalHeap();
  private static VM_LargeHeap largeHeap       = new VM_LargeHeap(immortalHeap);
  private static VM_ProcessorLock lock        = new VM_ProcessorLock();      // for signalling out of memory

  // use matureCurrentAddress variables for synchronized allocations in ToSpace
  // by GC threads during collection
  static VM_Address matureCurrentAddress;         // current end of "ToSpace"
  static VM_Address addrMatureCurrentAddress;     // address of above (in the JTOC)
  
  // Area small objects are allocated from 
  static VM_Address areaCurrentAddress;
  static VM_Address areaEndAddress;
  static VM_Address addrAreaCurrentAddress;
  
  static boolean gcInProgress;      // true if collection in progress, initially false
  static int gcExternalCount = 0;   // number of calls from System.gc
  static int gcCount = 0;           // number of minor collections
  static int gcMajorCount = 0;      // number of major collections

  private static double gcStartTime = 0;
  private static double gcEndTime = 0;
  private static double gcMinorTime;             // for timing gc times

  // accumulated times & counts for sysExit callback printout
  private static double maxGCTime = 0.0;           
  private static double totalGCTime = 0.0;           
  private static double totalStartTime = 0.0;           
  private static long   totalBytesCopied = 0;
  private static long   maxBytesCopied = 0;
  private static long   curBytesCopied = 0;
  private static int    collisionCount = 0;

  private static VM_TimeStatistic startTime = new VM_TimeStatistic();
  private static VM_TimeStatistic initTime = new VM_TimeStatistic();
  private static VM_TimeStatistic rootTime = new VM_TimeStatistic();
  private static VM_TimeStatistic scanTime = new VM_TimeStatistic();
  private static VM_TimeStatistic finalizeTime = new VM_TimeStatistic();
  private static VM_TimeStatistic finishTime = new VM_TimeStatistic();

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
  private static void outOfMemory (int size) {

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
	  //        t.contextRegisters.gprs[FRAME_POINTER] = t.jniEnv.JNITopJavaFP;
	  t.contextRegisters.setInnermost( VM_Address.zero(), t.jniEnv.JNITopJavaFP);
	}

	if (PROCESSOR_LOCAL_MATURE_ALLOCATE) {
	  vp.localMatureCurrentAddress = VM_Address.zero();
	  vp.localMatureEndAddress = VM_Address.zero();
	}
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
        // Did not participate in GC. Reset VPs allocation pointers so subsequent
        // allocations will acquire a new local block from the new nursery
        vp.localCurrentAddress = VM_Address.zero();
        vp.localEndAddress     = VM_Address.zero();
      }
    }
  }


  /**
   * Perform a garbage collection.  Called from VM_CollectorThread run
   * method by each collector thread participating in a collection.
   */
  static void collect () {
    int i;
    boolean   selectedGCThread = false;  // indicates 1 thread to generate output
 
    // ASSUMPTIONS:
    // initGCDone flag is false before first GC thread enter collect
    // InitLock is reset before first GC thread enter collect
    //
 
    if (VM.BuildForEventLogging && VM.EventLoggingEnabled)
      VM_EventLogger.logGarbageCollectionEvent();
 
    int mypid = VM_Processor.getCurrentProcessorId();  // id of processor running on

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
      
      startTime.start(VM_CollectorThread.gcBarrier.rendezvousStartTime); // time since GC requested
      initTime.start(startTime);
      gcStartTime = initTime.lastStart;

      if (VM.VerifyAssertions) VM.assert( initGCDone == false );  

      gcCount++;

      if (verbose >= 2) VM.sysWriteln("Starting GC ", gcCount);

      // setup common workqueue for num VPs participating, used to be called once.
      // now count varies for each GC, so call for each GC   SES 050201
      //
      VM_GCWorkQueue.workQueue.initialSetup(VM_CollectorThread.numCollectors());
      
      // VM_GCWorkQueue.workQueue.reset(); // do initialsetup instead 050201
      
      if (verbose >= 2)	VM.sysWriteln("  GC initialization");

      gcInProgress = true;
      gcDone = false;
      
      // note: the variable named "matureCurrentAddress" is used to track
      // the current end of ToSpace
      matureCurrentAddress =  toHeap.start;
 
      // invert the mark_flag value, used for marking BootImage objects
      BOOT_MARK_VALUE = BOOT_MARK_VALUE ^ VM_CommonAllocatorHeader.GC_MARK_BIT_MASK;
 
      // Now prepare large space for collection - clears out mark array
      largeHeap.startCollect();

      if (VM.ParanoidGCCheck) toHeap.unprotect();
      
      // this gc thread copies own VM_Processor, resets processor register & processor
      // local allocation pointers (before copying first object to ToSpace)
      gc_initProcessor();
 
      // with the default jni implementation some RVM VM_Processors may
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
    // roots are (fromHeap) object refs in the jtoc or on the stack.
    // For each unmarked root object, it is marked, copied to toHeap, and
    // added to GC thread local work queue for later scanning. The root refs are updated.
    gc_scanProcessor();    // each gc threads scans its own processor object

    VM_ScanStatics.scanStatics();     // all threads scan JTOC in parallel

    gc_scanThreads();       // ALL GC threads process thread objects & scan their stacks

    // This synchronization is necessary to ensure all stacks have been scanned
    // and all internal save ip values have been updated before we scan copied
    // objects.  Because if we scan a VM_Method, and then update its code pointer
    // we can no longer compute old ip offsets for updating saved ip values
    //
    //    REQUIRED SYNCHRONIZATION - WAIT FOR ALL GC THREADS TO REACH HERE

    mylocal.rendezvousWaitTime += VM_CollectorThread.gcBarrier.rendezvous(RENDEZVOUS_TIMES || RENDEZVOUS_WAIT_TIME);
    
    // have processor 1 record timestame for end of scanning stacks & statics
    if (mylocal.gcOrdinal == 1)
	scanTime.start(rootTime);

    // each GC thread processes its own work queue until empty
    if (verbose >= 2) VM.sysWriteln("  Emptying work queue");
    gc_emptyWorkQueue();

    // have processor 1 record timestame for end of scan/mark/copy phase
    if (mylocal.gcOrdinal == 1)
	finalizeTime.start(scanTime);
    
    // If counting or timing in VM_GCWorkQueue, save current counter values
    //
    if (VM_GCWorkQueue.WORKQUEUE_COUNTS)   VM_GCWorkQueue.saveCounters(mylocal);
    if (VM_GCWorkQueue.MEASURE_WAIT_TIMES || VM_CollectorThread.MEASURE_WAIT_TIMES)
      VM_GCWorkQueue.saveWaitTimes(mylocal);

    // If there are not any objects with finalizers skip finalization phases
    //
    if (VM_Finalizer.existObjectsWithFinalizers()) {

      // Now handle finalization

      /*** The following reset() will wait for previous use of workqueue to finish
	   ie. all threads to leave.  So no rendezvous is necessary (we hope)
         Without the reset, a rendezvous is necessary because some "slow" gc threads may still be
         in emptyWorkQueue (in VM_GCWorkQueue.getBufferAndWait) and have not seen
         the completionFlag==true.  The following call to reset will reset that
         flag to false, possibly leaving the slow GC threads stuck.  This rendezvous
         ensures that all threads have left the previous emptyWorkQueue, before
         doing the reset. (We could make reset smarter, and have it wait until
         the threadsWaiting count returns to 0, before doing the reset - TODO)
      ***/

      if (mylocal.gcOrdinal == 1) {

	VM_GCWorkQueue.workQueue.reset();   // reset work queue shared control variables
	
	// one thread scans the hasFinalizer list for dead objects.  They are made live
	// again, and put into that threads work queue buffers.
	//
	VM_Finalizer.moveToFinalizable();
      }
      
      // ALL threads have to wait to see if any finalizable objects are found
      mylocal.rendezvousWaitTime += VM_CollectorThread.gcBarrier.rendezvous(RENDEZVOUS_TIMES || RENDEZVOUS_WAIT_TIME);
     
      if (VM_Finalizer.foundFinalizableObject) {

	// Some were found. Now ALL threads execute emptyWorkQueue again, this time
	// to mark and keep live all objects reachable from the new finalizable objects.
	//
	gc_emptyWorkQueue();

      }
    }  //  end of Finalization Processing

    if (verbose >= 2) VM.sysWriteln("  Finished finalizer processing");

    // gcDone flag has been set to false earlier
    //
    if ( VM_GCLocks.testAndSetFinishLock() ) {

      // set ending times for preceeding finalization or scanning phase
      // do here where a sync (below) will push value to memory
      finishTime.start(finalizeTime);
      
      // BEGIN SINGLE GC THREAD SECTION FOR END OF GC

      // make any access to discarded fromspace impossible
      if (VM.ParanoidGCCheck) {
	  VM.sysWriteln("  Protecting fromspace");
	  fromHeap.protect();
	  toHeap.paranoidScan(fromHeap, false);
      }

      // Reset all address cursors
      VM_Heap temp = fromHeap;  // swap spaces
      fromHeap = toHeap;
      toHeap = temp;
      // allocate in new from-space; round up start address to page to allow using efficient zeroPages()
      areaCurrentAddress = VM_Memory.roundUpPage(matureCurrentAddress); 
      areaEndAddress = fromHeap.end;  
      matureCurrentAddress = VM_Address.zero();   // not used during normal execution

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
      if (ZERO_NURSERY_IN_PARALLEL) 
	  ;   //  zero in parallel in later 
      else if ( !ZERO_BLOCKS_ON_ALLOCATION ) {
	// have one processor (the executing one) do all the zeroing
	VM_Memory.zeroPages(areaCurrentAddress, areaEndAddress.diff(areaCurrentAddress));
      }
      else {
	// if ZERO_BLOCKS_ON_ALLOCATION is on (others OFF!) then processors
	// zero there own processor local chunks when allocated, this 
	// requires that the allocator/collector be compiled with
	// PROCESSOR_LOCAL_ALLOCATE == true
	if (VM.VerifyAssertions) VM.assert(PROCESSOR_LOCAL_ALLOCATE == true);
      }

      selectedGCThread = true;  // have this thread generate verbose output below,
                                // after nursery has been zeroed
     
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

    if (verbose >= 2) VM.sysWriteln("  Doing statistics accounting");

    // ALL GC THREADS IN PARALLEL - AFTER COLLECTION

    // have each processor begin allocations in next mutator cycle in what
    // remains of the current chunk acquired for copying objects during GC.
    // Note. it must be zero'ed
    //
    if (PROCESSOR_LOCAL_ALLOCATE) {
      VM_Processor vp = VM_Processor.getCurrentProcessor();
      vp.localCurrentAddress = vp.localMatureCurrentAddress;
      vp.localEndAddress = vp.localMatureEndAddress;
      VM_Memory.zero(vp.localCurrentAddress,vp.localEndAddress);
    }
    
    // This non-generational collector, but can be compiled with generational
    // write barrier enabled (possibly to measure the cost of the barrier), 
    // causing entries to accumulate in the processor write buffers.  While these
    // are not used in the collection process, the entries must be discarded
    // and excess buffers freed, to avoid running out of memory.  This is done
    // by invokinge resetWriteBuffer.
    //
    if (ZERO_NURSERY_IN_PARALLEL) 
	fromHeap.zeroParallel(areaCurrentAddress,areaEndAddress);

    // Each GC thread increments adds its wait times for this collection
    // into its total wait time - for printSummaryStatistics output
    //
    if (VM_CollectorThread.MEASURE_WAIT_TIMES)
      mylocal.incrementWaitTimeTotals();

    // generate -verbosegc output, done here after zeroing nursery. 
    // only approx. since other gc threads are zeroing in parallel
    // this is done by the 1 gc thread that finished the preceeding GC
    //
    if ( selectedGCThread ) {
	finishTime.stop();
	gcEndTime = finishTime.lastStop;
	double GCTime = gcEndTime - gcStartTime;
	if ( GCTime > maxGCTime ) maxGCTime = GCTime;
	totalGCTime += GCTime;
	curBytesCopied = areaCurrentAddress.diff(fromHeap.start);
	totalBytesCopied += curBytesCopied;
	if (curBytesCopied > maxBytesCopied) maxBytesCopied = curBytesCopied;

	int total = fromHeap.getSize();
	int free = (int) freeMemory();
    
	printGCPhaseTimes();  	
	
	if ( verbose >= 1 ) {
	    printVerboseOutputLine();
	    if (VM_CollectorThread.MEASURE_WAIT_TIMES)
		VM_CollectorThread.printThreadWaitTimes();
	    else {
		if (VM_GCWorkQueue.MEASURE_WAIT_TIMES) {
		    VM.sysWrite("*** Wait Times for Scanning \n");
		    VM_GCWorkQueue.printAllWaitTimes();
		    VM_GCWorkQueue.saveAllWaitTimes();
		    VM.sysWrite("*** Wait Times for Finalization \n");
		    VM_GCWorkQueue.printAllWaitTimes();
		    VM_GCWorkQueue.resetAllWaitTimes();
		}
	    }
	    
	    if (VM_GCWorkQueue.WORKQUEUE_COUNTS) {
		VM.sysWrite("*** Work Queue Counts for Scanning \n");
		VM_GCWorkQueue.printAllCounters();
		VM_GCWorkQueue.saveAllCounters();
		VM.sysWrite("*** WorkQueue Counts for Finalization \n");
		VM_GCWorkQueue.printAllCounters();
		VM_GCWorkQueue.resetAllCounters();
	    }
	    
	    if (RENDEZVOUS_TIMES) VM_CollectorThread.gcBarrier.printRendezvousTimes();
	    
	}  // end of verboseGC
	
    }  // end of selectedThread

    return;
  }  // collect


  /**
   * Internal method called by collector threads during collection to
   * get space in ToSpace for a live object that needs to be copied.
   * Space is obtained from the processor local "chunk" if available,
   * otherwise space is obtained directly from ToSpace using 
   * atomic compare and swap instructions.
   */
  static VM_Address gc_getMatureSpace ( int size ) {

    VM_Address startAddress, newCurrentAddress;  

    // note: uses "matureCurrentAddress for allocations in toHeap

    // get space for object in ToSpace.  used by gc routines during collection
    // if compiled for processor local chunking of "mature space" attempt to allocate
    // in currently assigned region of mature space (other semi-space in simple semi-space scheme).
    // if not enough room, get another mature chunk.
    //
    if (PROCESSOR_LOCAL_MATURE_ALLOCATE == true) {
      VM_Processor st = VM_Processor.getCurrentProcessor();
      
      if (VM_GCWorkQueue.COUNT_GETS_AND_PUTS)
	VM_Magic.threadAsCollectorThread(st.activeThread).copyCount++;
      
      startAddress = st.localMatureCurrentAddress;
      newCurrentAddress = startAddress.add(size);
      if ( newCurrentAddress.LE(st.localMatureEndAddress) ) {
	st.localMatureCurrentAddress = newCurrentAddress;    // increment processor local pointer
	return startAddress;
      }
      else {
	startAddress = VM_Synchronization.fetchAndAddAddressWithBound(addrMatureCurrentAddress, 
								      CHUNK_SIZE, toHeap.end);
	if (!startAddress.isMax()) {
	  st.localMatureEndAddress = startAddress.add(CHUNK_SIZE);
	  st.localMatureCurrentAddress = startAddress.add(size);
	  return startAddress;
	}
	else { 
	  // the executing collector thread could not get another CHUNK from
	  // the next semi-space (ToSpace). This should not normally happen,
	  // and the other threads should have space in their CHUNKS. This could
	  // be fixed by making the callers of getMatureSpace handle NULL returns,
	  // but the heap is basically full anyway, so just give up now.
	  // Call outOfMemory to print message and exit
	  //
	  outOfMemory(-1);
	  return VM_Address.max();
	}
      }
    }
    else {
      // all gc threads compete for mature space using synchronized ops
      startAddress = VM_Synchronization.fetchAndAddAddress(addrMatureCurrentAddress, size);
      // assume successful, there should always be space
      return startAddress;
    }
  }  // getMatureSpace
     

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
      VM_Address region = gc_getMatureSpace(numBytes);
      toObj = VM_ObjectModel.moveObject(region, fromObj, numBytes, classType, forwardingPtr);
      toRef = VM_Magic.objectAsAddress(toObj);
    } else {
      VM_Array arrayType = type.asArray();
      int numElements = VM_Magic.getArrayLength(fromObj);
      int numBytes = VM_ObjectModel.bytesRequiredWhenCopied(fromObj, arrayType, numElements);
      VM_Address region = gc_getMatureSpace(numBytes);
      toObj = VM_ObjectModel.moveObject(region, fromObj, numBytes, arrayType, forwardingPtr);
      toRef = VM_Magic.objectAsAddress(toObj);
      if (arrayType == VM_Type.CodeType) {  // conservatively includes all int/byte arrays
	// must sync moved code instead of sync'ing chunks when full
	VM_Memory.sync(toRef, numBytes);
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

      if ( debugNative && t.isGCThread ) {
	VM_Scheduler.trace("scanThreads:","at GC thread for processor id =", t.processorAffinity.id);
	VM_Scheduler.trace("scanThreads:","                    gcOrdinal =", VM_Magic.threadAsCollectorThread(t).gcOrdinal);
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

    VM_Processor   st = VM_Processor.getCurrentProcessor();  // get current Processor Register

    // if compiled for processor local chunking of "mature space" reset processor local 
    // pointers, to cause first request to get a block (only reset on major collection
    // for minor collection, continue filling last/current mature buffer
    //
    if (PROCESSOR_LOCAL_MATURE_ALLOCATE) {
      st.localMatureCurrentAddress = VM_Address.zero();
      st.localMatureEndAddress = VM_Address.zero();
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
    // VM_Magic.setProcessorRegister(st);

    if (PROCESSOR_LOCAL_ALLOCATE) {
      // reset local heap pointers - to force exception upon attempt to allocate
      // during GC.  Will be set at end of collection.
      //
      st.localCurrentAddress = VM_Address.zero();
      st.localEndAddress     = VM_Address.zero();
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


  private static void printGCPhaseTimes () {

    // if invoked with -verbose:gc print output line for this last GC
    if (verbose >= 2) {
      VM.sysWrite("<GC ", gcCount, "> ");
      VM.sysWrite("startTime ", (int)(startTime.last*1000000.0), "(us) ");
      VM.sysWrite("init ", (int)(initTime.last*1000000.0), "(us) ");
      VM.sysWrite("stacks & statics ", (int)(rootTime.last*1000000.0), "(us) ");
      VM.sysWrite("scanning ", (int)(scanTime.last*1000.0), "(ms) ");
      VM.sysWrite("finalize ", (int)(finalizeTime.last*1000000.0), "(us) ");
      VM.sysWriteln("finish ",  (int)(finishTime.last*1000000.0), "(us) ");
    }
  }
       

  static void clearSummaryStatistics () {
    VM_ObjectModel.hashRequests = 0;
    VM_ObjectModel.hashTransition1 = 0;
    VM_ObjectModel.hashTransition2 = 0;

    VM_Processor st;
    for (int i = 1; i <= VM_Scheduler.numProcessors; i++) {
      st = VM_Scheduler.processors[i];
      st.totalBytesAllocated = 0;
      st.totalObjectsAllocated = 0;
      st.synchronizedObjectsAllocated = 0;
    }
  }

  private static void printVerboseOutputLine () {

      int gcTimeMs = (int) ((gcEndTime - gcStartTime)*1000.0);
      int total = fromHeap.getSize();
      int free = (int) allSmallFreeMemory();
      double freeFraction = free / (double) total;
      int copiedKb = (int) (curBytesCopied / 1024);

      VM.sysWrite("<GC ", gcCount, ">  ");
      VM.sysWrite(gcTimeMs, " ms ");
      VM.sysWrite("   small: ", copiedKb, " Kb copied     ");
      VM.sysWrite(free / 1024, " Kb free (");
      VM.sysWrite(freeFraction * 100.0); VM.sysWrite("%)   ");
      VM.sysWrite("rate = "); VM.sysWrite(((double) copiedKb) / gcTimeMs); VM.sysWrite("(Mb/s)      ");
      VM.sysWriteln("large: ", largeHeap.freeSpace() / 1024, " Kb free");
  }

  static void printSummaryStatistics () {

    if (VM_ObjectModel.HASH_STATS) {
      VM.sysWriteln("Hash operations:    ", VM_ObjectModel.hashRequests);
      VM.sysWriteln("Unhashed -> Hashed: ", VM_ObjectModel.hashTransition1);
      VM.sysWriteln("Hashed   -> Moved:  ", VM_ObjectModel.hashTransition2);
    }

    // produce summary system exit output if -verbose:gc was specified of if
    // compiled with measurement flags turned on
    //
    if ( ! (TIME_GC_PHASES || VM_CollectorThread.MEASURE_WAIT_TIMES || (verbose >= 1)) )
	return;     // not verbose, no flags on, so don't produce output
    
    // the bytesCopied counts count whole chunks. The last chunk acquired for
    // copying objects is partially full/empty, on avg. half full.  So we
    // subtrace from the average, half of space in a set of chunks
    //
    int np = VM_Scheduler.numProcessors;
    int avgBytesFreeInChunks = (VM_Scheduler.numProcessors * CHUNK_SIZE) >> 1;
    
    // showParameter();
    
    VM.sysWriteln("\nGC Summary:  ", gcCount, " Collections");
    if (gcCount != 0) {
	int avgTime = (int)( ((totalGCTime/(double)gcCount)*1000.0) );
	int avgBytes = ((int)(totalBytesCopied/gcCount)) - avgBytesFreeInChunks;
	VM.sysWrite("GC Summary:  Times   ");
	VM.sysWrite("total "); VM.sysWrite(totalGCTime); VM.sysWrite(" (s)    ");
	VM.sysWrite("avg ", avgTime, " (ms)    ");
	VM.sysWriteln("max ", (int)(maxGCTime*1000.0), " (ms)    ");
	VM.sysWrite("GC Summary:  Copied  ");
	VM.sysWrite("avg ", avgBytes / 1024, " (Kb)    ");
	VM.sysWriteln("max ", (int) maxBytesCopied / 1024, " (Kb)");
    }
    
    if (COUNT_COLLISIONS && (gcCount>0) && (np>1)) {
	VM.sysWriteln("GC Summary:  avg number of collisions per collection = ",
		      collisionCount/gcCount);
    }
    
    if (TIME_GC_PHASES && (gcCount>0)) {
	VM.sysWrite("Average Time in Phases of Collection:\n");
	VM.sysWrite("startTime ", startTime.avgUs(), "(us) init ");
	VM.sysWrite( initTime.avgUs(), "(us) stacks & statics ");
	VM.sysWrite( rootTime.avgUs(), "(us) scanning ");
	VM.sysWrite( scanTime.avgMs(), "(ms) finalize ");
	VM.sysWrite( finalizeTime.avgUs(), "(us) finish ");
	VM.sysWrite( finishTime.avgUs(), "(us)>\n\n");
    }
    
    if (VM_CollectorThread.MEASURE_WAIT_TIMES && (gcCount>0)) {
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
	avgBufferWait = ((int)((totalBufferWait/(double)gcCount)*1000000.0))/np;
	avgFinishWait = ((int)((totalFinishWait/(double)gcCount)*1000000.0))/np;
	avgRendezvousWait = ((int)((totalRendezvousWait/(double)gcCount)*1000000.0))/np;
	
	VM.sysWrite("Average Wait Times For Each Collector Thread In A Collection:\n");
	VM.sysWrite("Buffer Wait ", avgBufferWait, " (us) Finish Wait ");
	VM.sysWrite( avgFinishWait, " (us) Rendezvous Wait ");
	VM.sysWrite( avgRendezvousWait, " (us)\n\n");
    }

    if (COUNT_ALLOCATIONS) {
      long bytes = 0, objects = 0, syncObjects = 0;
      VM_Processor st;
      for (int i = 1; i <= VM_Scheduler.numProcessors; i++) {
        st = VM_Scheduler.processors[i];
        bytes += st.totalBytesAllocated;
        objects += st.totalObjectsAllocated;
        syncObjects += st.synchronizedObjectsAllocated;
      }
      VM.sysWrite(" Total No. of Objects Allocated in this run ");
      VM.sysWrite(Long.toString(objects));
      VM.sysWrite("\n Total No. of Synchronized Objects Allocated in this run ");
      VM.sysWrite(Long.toString(syncObjects));
      VM.sysWrite("\n Total No. of bytes Allocated in this run ");
      VM.sysWrite(Long.toString(bytes));
      VM.sysWrite("\n");
    }

  } // printSummaryStatistics


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

  // allocate buffer for allocates during traceback & call sysFail (gets stacktrace)
  // or sysWrite the message and sysExit (no traceback possible)
  // ...may not be needed if sysFail now does NOT do allocations
  //
  private static void crash (String err_msg) {

    VM.sysWriteln("VM_Allocator.crash:");
    if (PROCESSOR_LOCAL_ALLOCATE) {
      VM_Address tempbuffer = immortalHeap.allocateRawMemory(VM_Allocator.CRASH_BUFFER_SIZE);
      VM_Processor p = VM_Processor.getCurrentProcessor();
      p.localCurrentAddress = tempbuffer;
      p.localEndAddress = tempbuffer.add(VM_Allocator.CRASH_BUFFER_SIZE);
      VM_Memory.zeroTemp(tempbuffer, VM_Allocator.CRASH_BUFFER_SIZE);
    }
    VM.sysFail(err_msg);   // this is now supposed to complete without allocations
  }

  /*
   * Initialize a VM_Processor for allocation and collection.
   */
  static void setupProcessor (VM_Processor p) {
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
      int      storage        = immortalHeap.allocateRawMemory(size, VM_JavaHeader.TIB_ALIGNMENT, VM_JavaHeader.computeArrayHeaderSize(objectArray));
      Object[] newtib         = (Object[]) VM_ObjectModel.initializeArray(storage, objectArrayTIB, n, size);

      VM_AllocatorHeader.writeMarkBit(newtib, BOOT_MARK_VALUE);
      if (VM.VerifyAssertions) VM.assert((VM_Magic.objectAsAddress(newtib) & (VM_JavaHeader.TIB_ALIGNMENT-1)) == 0);
      return newtib;
  }
  //-#endif


  /**
   * Encapsulate debugging operations when storage is allocated.  Always inlined.
   * In production, all debug flags are false and this routine disappears.
   *   @param size Number of bytes to allocate
   *   @param tib Pointer to the Type Information Block for the object type 
   */
  static void debugAlloc (int size, Object[] tib) {
      VM_Magic.pragmaInline();

      if (COUNT_ALLOCATIONS) {
	  VM_Processor st = VM_Processor.getCurrentProcessor();
	  st.totalBytesAllocated += size;
	  st.totalObjectsAllocated++;
          VM_Type t = VM_Magic.objectAsType(tib[0]);
          if (t.thinLockOffset != -1) {
            st.synchronizedObjectsAllocated++;
          }
      }
  }

}   // VM_Allocator
