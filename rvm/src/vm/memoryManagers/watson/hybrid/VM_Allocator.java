/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Hybrid Generational Collector/Allocator with Fixed Size Nursery
 * <p>
 * The hybrid collector divides the heap into 2 spaces, a small Nursery
 * region where objects are allocated, and a Mature Space for objects
 * that survive one or more collections. The Nursery is a
 * VM_Contiguous heap. The Mature Space is a VM_SegregatedListHeap.
 * <p>
 * Minor collections of the Nursery are performed like Minor collections
 * in the copying generational collectors, except that space for objects
 * being copied into the Mature Space is obtained using the allocate
 * routines of the mark-sweep allocator.  Major collections are performed
 * using the mark-sweep code.  Major collections are triggerred when
 * the number of free blocks in Mature Space falls below a threshold.
 * <p>
 * The nursery size can be set on the command line by specifying
 * "-X:nh=xxx" where xxx is the nursery size in megabytes.  The nursery size
 * is subtracted from the small object heap size (-X:h=xxx) and the remainder
 * becomes the Mature Space.
 * <p>
 * The Hybrid collector uses the default RVM writebarrier
 * which puts references to objects, which had internal references
 * modified, into processor local writebuffers.  For minor collections, objects in
 * the writebuffers become part of the root set for the collection.
 * (The RVM compilers generate the barrier code when the static final
 * constant "writeBarrier" is set to true.)
 * 
 * @see VM_Chunk
 * @see VM_ContiguousHeap
 * @see VM_SegregatedListHeap
 * @see VM_GCWorkQueue
 * @see VM_WriteBuffer
 * @see VM_CollectorThread
 *
 * @author Dick Attanasio
 * @author Tony Cocchi
 * @author Stephen Smith
 * @author Dave Grove
 * 
 * @modified by Perry Cheng  Heavily re-written to factor out common code and adding VM_Address
 */  
public class VM_Allocator extends VM_GCStatistics
  implements VM_Constants, VM_Uninterruptible, VM_Callbacks.ExitMonitor {

  static final boolean GCDEBUG_SCANTHREADS = false;
  /**
   * When true, causes time spent in each phase of collection to be measured.
   * Forces summary statistics to be generated. See VM_CollectorThread.TIME_GC_PHASES.
   */
  static final boolean TIME_GC_PHASES  = VM_CollectorThread.TIME_GC_PHASES;

  /**
   * When true, causes each gc thread to measure accumulated wait times
   * during collection. Forces summary statistics to be generated.
   * See VM_CollectorThread.MEASURE_WAIT_TIMES.
   */
  static final boolean RENDEZVOUS_TIMES = VM_CollectorThread.MEASURE_WAIT_TIMES;

  /** count times parallel GC threads attempt to mark the same object */
  private static final boolean COUNT_COLLISIONS = true;

  /**
   * Initialize for boot image - executed when bootimage is being build
   */
  static  void init () {
    VM_GCLocks.init();	
    VM_GCWorkQueue.init();      // to alloc shared work queue0
    VM_CollectorThread.init();  // to alloc rendezvous arrays, if necessary
    smallHeap.init(VM_Scheduler.processors[VM_Scheduler.PRIMORDIAL_PROCESSOR_ID]);
  } 

  /**
   * Initialize for execution - executed when VM starts up.
   */
  static void boot (VM_BootRecord bootrecord) {
    verbose = bootrecord.verboseGC;

    int smallHeapSize = bootrecord.smallSpaceSize;
    smallHeapSize = (smallHeapSize / GC_BLOCKALIGNMENT) * GC_BLOCKALIGNMENT;
    smallHeapSize = VM_Memory.roundUpPage(smallHeapSize);
    int immortalSize = VM_Memory.roundUpPage(4 * (bootrecord.largeSpaceSize / VM_Memory.getPagesize()) + 
					   ((int) (0.05 * smallHeapSize)) + 
					   4 * VM_Memory.getPagesize());
    int nurserySize = bootrecord.nurserySize;
    
    VM_Heap.boot(bootHeap, bootrecord);
    immortalHeap.attach(immortalSize);
    largeHeap.attach(bootrecord.largeSpaceSize);
    nurseryHeap.attach(bootrecord.nurserySize);
    smallHeap.attach(smallHeapSize);
    mallocHeap.attach(bootrecord);

    nurseryHeap.reset();

    VM_Processor st = VM_Scheduler.processors[VM_Scheduler.PRIMORDIAL_PROCESSOR_ID];
    smallHeap.boot(st, immortalHeap);

    // when preparing for a timing run touch all the pages in the Nursery
    // and Small Object Heap, to avoid overhead of page fault
    // during the timing run
    if (COMPILE_FOR_TIMING_RUN) {
      largeHeap.touchPages();
      smallHeap.touchPages();
      nurseryHeap.touchPages();
    }
        
    // check for inconsistent heap & nursery sizes
    if (smallHeap.size <= nurseryHeap.size) {
      VM.sysWrite("\nNursery size is too large for the specified Heap size:\n");
      VM.sysWriteln("  Small Object Heap Size = ", smallHeap.size / 1024, " Kb");
      VM.sysWriteln("  Nursery Size = ", nurseryHeap.size / 1024, " Kb");
      VM.sysWrite("Use -X:h=nnn & -X:nh=nnn to specify a heap size at least twice as big as the nursery\n");
      VM.sysWrite("Remember, the nursery is subtracted from the specified heap size\n");
      VM.shutdown(-5);
    }

    // set free block count for triggering major collection
    majorCollectionThreshold = nurserySize/GC_BLOCKSIZE;

    VM_GCUtil.boot();

    // create the finalizer object
    VM_Finalizer.setup();

    VM_Callbacks.addExitMonitor(new VM_Allocator());

    if (verbose >= 1) showParameter();
  }

  static void showParameter() {
    VM.sysWriteln("Generational Hybrid Collector/Allocator (verbose = ", verbose, ")");
    bootHeap.show();
    immortalHeap.show();
    nurseryHeap.show();
    smallHeap.show();
    largeHeap.show();
    VM.sysWriteln("  Work queue buffer size = ", VM_GCWorkQueue.WORK_BUFFER_SIZE);
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
   * Force a garbage collection. Supports System.gc() called from
   * application programs.
   */
  public static void gc ()  {
    gc1("GC triggered by external call to gc()", 0);
  }

  /**
   * VM internal method to initiate a collection
   */
  public static void gc1 (String why, int size) {

    if (verbose >= 1) VM.sysWriteln(why, size);

    double time;

    // if here and in a GC thread doing GC then it is a system error, insufficient
    // extra space for allocations during GC
    if ( VM_Thread.getCurrentThread().isGCThread ) {
      VM.sysFail("VM_Allocator: Garbage Collection Failure: GC Thread attempting to allocate during GC");
    }

    // notify GC threads to initiate collection, wait until done
    VM_CollectorThread.collect(VM_CollectorThread.collect);

  }  // gc1


  /**
   * Get total amount of memory.  Includes both full size of the
   * small object heap and the size of the large object heap.
   *
   * @return the number of bytes
   */
  public static long totalMemory () {
    return smallHeap.size + largeHeap.size + nurseryHeap.size;
  }
  
  /**
   * Get the number of bytes currently available for object allocation.
   * In this collector only includes the totally free space
   * (free space in nursery and freeBlocks in smallHeap).
   * Does NOT include space available in large object space.
   *
   * @return number of bytes available
   */
  public static long freeMemory () {
    return nurseryHeap.freeMemory() + smallHeap.freeMemory();
  }

  /*
   * Includes freeMemory and per-processor local storage
   * and partial blocks in small heap.
   */
  public static long allSmallFreeMemory () {
    return freeMemory() + VM_Chunk.freeMemoryChunk1() + smallHeap.partialBlockFreeMemory();
  }

  public static long allSmallUsableMemory () {
    return nurseryHeap.getSize() + smallHeap.getSize();
  }

  // START NURSERY ALLOCATION ROUTINES HERE 

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
    
    VM_Address region = allocateRawMemory(size);

    profileAlloc(region, size, tib); // profile/debug: usually inlined away to nothing

    Object newObj = VM_ObjectModel.initializeScalar(region, tib, size);
    if (size >= SMALL_SPACE_MAX) {
      // We're allocating directly into largeHeap, so must set BarrierBit on allocation
      VM_ObjectModel.initializeAvailableByte(newObj); 
      VM_AllocatorHeader.setBarrierBit(newObj);
    }
    return newObj;
  }
  

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

    // note: array size might not be a word multiple,
    //       must preserve alignment of future allocations
    size = VM_Memory.align(size, WORDSIZE);

    VM_Address region = allocateRawMemory(size);  

    profileAlloc(region, size, tib); // profile/debug: usually inlined away to nothing

    Object newObj = VM_ObjectModel.initializeArray(region, tib, numElements, size);
    if (size >= SMALL_SPACE_MAX) {
      // We're allocating directly into largeHeap, so must set BarrierBit on allocation
      VM_ObjectModel.initializeAvailableByte(newObj); 
      VM_AllocatorHeader.setBarrierBit(newObj);
    }
    return newObj;
  } 


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
	outOfLargeSpaceFlag = true;  // forces a major collection to reclaim more large space
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
    VM_Address addr = nurseryHeap.allocate(size);
    if (addr.isZero()) {
      for (int i=0; i<3; i++) {
	// There's a possible race condition where other Java threads
	// chew up all the small heap (and some of it becomes garbage)
	// before this thread gets to run again. 
	// So, we try a couple times before giving up.
	// This isn't a 100% solution, but it may handle it in practice.
	VM_Allocator.gc1("GC triggered by small object request of ", size);
	addr = nurseryHeap.allocate(size);
	if (!addr.isZero()) return addr;
      }
      outOfMemory(size);
    }
    return addr;
  }

  // END OF NURSERY ALLOCATION ROUTINES HERE

  /**
   * Handle heap exhaustion.
   * 
   * @param size number of bytes requested in the failing allocation
   */
  public static void heapExhausted(VM_Heap heap, int size, int count) {
    if (count > 3) outOfMemory(size);
    if (heap == nurseryHeap) {
      gc1("GC triggered by object request of ", size);
    } else {
      VM.sysFail("unexpected heap");
    }
  }

  /**
   * Print OutOfMemoryError message and exit.
   * TODO: make it possible to throw an exception, but this will have
   * to be done without doing further allocations (or by using temp space)
   */
  private static void outOfMemory (int size) {
    lock.lock();
    if (!outOfMemoryReported) {
      outOfMemoryReported = true;
      VM_Processor.getCurrentProcessor().disableThreadSwitching();
      VM.sysWriteln("\nOutOfMemoryError");
      VM.sysWriteln("Insufficient heap size for hybrid collector");
      VM.sysWriteln("Current heap size = ", smallHeap.size / 1024, " Kb");
      VM.sysWriteln("Specify a larger heap using -X:h=nnn command line argument");
      // call shutdown while holding the processor lock
      VM.shutdown(-5);
    } else {
      lock.release();
      while(outOfMemoryReported == true);  // spin until VM shuts down
    }
  }


  // **************************
  // Implementation
  // **************************

  static final int      TYPE = 1;	// IDENTIFIES HYBRID
  static final boolean  writeBarrier = true;      // MUST BE TRUE FOR THIS STORAGE MANAGER
  static final boolean  movesObjects = true;

  static final int  MARK_VALUE = 1;               // designates "marked" objects in Nursery

  static final int  SMALL_SPACE_MAX = 2048;       // largest object in small heap

  static final boolean COMPILE_FOR_TIMING_RUN = true;      // touch heap in boot

  static int verbose = 0;

  static final boolean GC_CHECKWRITEBUFFER	    = false;   // buffer entries during gc

  static double gcMinorTime;             // for timing gc times
  static double gcMajorTime;             // for timing gc times
  static double gcStartTime;             // for timing gc times
  static double gcEndTime;               // for timing gc times
  static double gcTotalTime = 0.0;         // for timing gc times
  static double maxMajorTime = 0.0;         // for timing gc times
  static double maxMinorTime = 0.0;         // for timing gc times

  private static double totalStartTime = 0.0;    // accumulated stopping time
  private static double totalMinorTime = 0.0;    // accumulated minor gc time
  private static double totalMajorTime = 0.0;    // accumulated major gc time
  private static int    collisionCount = 0;      // counts attempts to mark same object

  // timestamps and accumulators for TIME_GC_PHASES output
  private static double totalInitTime;
  private static double totalStacksAndStaticsTime;
  private static double totalScanningTime;
  private static double totalFinalizeTime;
  private static double totalFinishTime;
  private static double totalInitTimeMajor;
  private static double totalStacksAndStaticsTimeMajor;
  private static double totalScanningTimeMajor;
  private static double totalFinalizeTimeMajor;
  private static double totalFinishTimeMajor;
  
  private static double gcInitDoneTime = 0;
  private static double gcStacksAndStaticsDoneTime = 0;    
  private static double gcScanningDoneTime = 0;
  private static double gcFinalizeDoneTime = 0;
  
  static VM_ProcessorLock  lock = new VM_ProcessorLock();
  static volatile boolean           initGCDone = false;

  static int     gcCount      = 0;  // updated every entry to collect
  static int     gcMajorCount = 0;  // major collections
  static int     majorCollectionThreshold;   // minimum # blocks before major collection
  static boolean gcInProgress = false;
  static boolean outOfSmallHeapSpace = false;
  static boolean majorCollection = false;
  static boolean outOfLargeSpaceFlag = false;
  static boolean outOfMemoryReported = false;  // to make only 1 thread report OutOfMemory

  private static VM_Heap bootHeap                = new VM_Heap("Boot Image Heap");   
  private static VM_ContiguousHeap nurseryHeap   = new VM_ContiguousHeap("Nursery Heap");
  private static VM_ImmortalHeap immortalHeap    = new VM_ImmortalHeap();
  private static VM_LargeHeap largeHeap          = new VM_LargeHeap(immortalHeap);
  private static VM_MallocHeap mallocHeap        = new VM_MallocHeap();
  private static VM_SegregatedListHeap smallHeap = new VM_SegregatedListHeap("Small Object Heap", mallocHeap);

  static int OBJECT_GC_MARK_VALUE = 0;   // changes between this and 0

  /**
  * getter function for gcInProgress
  */
  static boolean gcInProgress() {
    return gcInProgress;
  }

  /**
   * Setup for Collection. 
   * Sets number of collector threads participating in collection.
   * Called from CollectorThread.boot().
   *
   * @param numThreads   number of collector threads participating
   */
  static void gcSetup (int numThreads) {
    VM_GCWorkQueue.workQueue.initialSetup(numThreads);

    majorCollectionThreshold = nurseryHeap.size/GC_BLOCKSIZE + numThreads*GC_SIZES;
  }

  /**
   * gc_getMatureSpace is called during Minor (Nursery) collections to get space
   * for live Nursery objects being copied to Mature Space.  This is basically
   * the allocate method of the non-copying mark-sweep allocator.
   * return value = 0 ==> new chunk could not be obtained. This means we could not
   * complete a minor collection & will cause an OutOfMemory error shutdown.
   * <p>
   * We Could Be Smarter Here, note that other collector threads may have space!!!
   */
  public static VM_Address gc_getMatureSpace (int size) throws OutOfMemoryError {
    return VM_SegregatedListHeap.allocateFastPath(size);
  }
    

  // called by ONE gc/collector thread to copy any "new" thread objects
  // copies but does NOT enqueue for scanning
  static void gc_copyThreads ()  {
    for (int i=0; i<VM_Scheduler.threads.length; i++ ) {
      VM_Thread t = VM_Scheduler.threads[i];
      VM_Address ta = VM_Magic.objectAsAddress(t);
      if ( nurseryHeap.refInHeap(ta) ) {
	ta = copyAndScanObject(ta, false);
	t = VM_Magic.objectAsThread(VM_Magic.addressAsObject(ta));
	VM_Magic.setMemoryAddress( VM_Magic.objectAsAddress(VM_Scheduler.threads).add(i*4), ta);
      }
    }  // end of loop over threads[]
  } 


  /**
   * initProcessor is called by each GC thread to copy the processor object of the
   * processor it is running on, and reset it processor register, and update its
   * entry in the scheduler processors array and reset its local allocation pointers
   */
  static void gc_initProcessor ()  {
    VM_Processor st = VM_Processor.getCurrentProcessor();
    VM_Address sta = VM_Magic.objectAsAddress(st);
    VM_Thread activeThread = st.activeThread;
    int tid = activeThread.getIndex();

    if (VM.VerifyAssertions) VM.assert(tid == VM_Thread.getCurrentThread().getIndex());

    // if Processor is in fromSpace, copy and update array entry
    if (nurseryHeap.refInHeap(sta)) {
      sta = copyAndScanObject(sta, false);   // copy thread object, do not queue for scanning
      st = VM_Magic.objectAsProcessor(VM_Magic.addressAsObject(sta));
      // change entry in system threads array to point to copied sys thread
      VM_Magic.setMemoryAddress(VM_Magic.objectAsAddress(VM_Scheduler.processors).add(st.id*4), sta);
    }

    // each gc thread updates its PROCESSOR_REGISTER, after copying its VM_Processor object
    VM_Magic.setProcessorRegister(st);

    // Invalidate chunk1 to detect allocations during GC.
    VM_Chunk.resetChunk1(st, null, false);

    // if Processor's activethread (should be current, gc, thread) is in fromSpace, copy and
    // update activeThread field and threads array entry to make sure BOTH ways of computing
    // getCurrentThread return the new copy of the thread
    VM_Address ata = VM_Magic.objectAsAddress(activeThread);
    if (nurseryHeap.refInHeap(ata)) {
      // copy thread object, do not queue for scanning
      ata = copyAndScanObject(ata, false);
      activeThread = VM_Magic.objectAsThread(VM_Magic.addressAsObject(ata));
      st.activeThread = activeThread;
      // change entry in system threads array to point to copied sys thread
      VM_Magic.setMemoryAddress(VM_Magic.objectAsAddress(VM_Scheduler.threads).add(tid*4), ata);
    }

    // setup the work queue buffers for this gc thread
    VM_GCWorkQueue.resetWorkQBuffers();
  }  // initProcessor



  /**
   * Do Minor collection of Nursery, copying live nursery objects
   * into Mature/Fixed Space.  All collector threads execute in parallel.
   * If any attempt to get space (see getMatureSpace) fails,
   * outOfSmallHeapSpace is set and the Minor collection will be
   * incomplete.  In this simple version of hybrid, this will cause
   * an Out_Of_Memory failure.
   */
  static void gcCollectMinor () {
    // ASSUMPTIONS:
    // initGCDone flag is false before first GC thread enter gcCollectMinor
    // InitLock is reset before first GC thread enter gcCollectMinor
    //
    if (VM.BuildForEventLogging && VM.EventLoggingEnabled) VM_EventLogger.logGarbageCollectionEvent();

    if ( VM_GCLocks.testAndSetInitLock() ) {
      // BEGIN SINGLE GC THREAD SECTION - GC INITIALIZATION
       
      gcStartTime = VM_Time.now();         // start time for GC
      totalStartTime += gcStartTime - VM_CollectorThread.gcBarrier.rendezvousStartTime; //time since GC requested

      if (VM.VerifyAssertions) VM.assert( initGCDone == false );  

      gcCount++;
      if (verbose >= 1) VM.sysWriteln("Starting minor collection ", gcCount);

      // setup common workqueue for num VPs participating, used to be called once.
      // now count varies for each GC, so call for each GC   SES 050201
      //
      VM_GCWorkQueue.workQueue.initialSetup(VM_CollectorThread.numCollectors());
      
      gcInProgress = true;
      majorCollection = false;
      outOfSmallHeapSpace = false;

      // this gc thread copies own VM_Processor, resets processor register & processor
      // local allocation pointers (before copying first object to ToSpace)
      gc_initProcessor();
   
      // some RVM VM_Processors may be blocked in native C and not participating in a collection.
      prepareNonParticipatingVPsForGC( false /*minor*/);
      
      // precopy new VM_Thread objects, updating schedulers threads array
      // here done by one thread. could divide among multiple collector threads
      gc_copyThreads();

      VM_GCLocks.resetFinishLock();  // for singlethread'ing end of minor collections

      // must sync memory changes so GC threads on other processors see above changes
      // sync before setting initGCDone flag to allow other GC threads to proceed
      VM_Magic.sync();

      if (TIME_GC_PHASES)  gcInitDoneTime = VM_Time.now();

      // set Done flag to allow other GC threads to begin processing
      initGCDone = true;

    } else {
      // Each GC thread must wait here until initialization is complete
      // this should be short, if necessary at all, so we spin instead of sysYield
      //
      // It is NOT required that all GC threads reach here before any can proceed
      //
      while(!initGCDone);   // spin until initialization finished
      VM_Magic.isync();             // prevent following inst. from moving infront of waitloop

      // each gc thread copies own VM_Processor, resets processor register & processor
      // local allocation pointers
      gc_initProcessor();
    }

    if (outOfSmallHeapSpace) {
      VM_Scheduler.trace(" gcCollectMinor:", "outOfSmallHeapSpace after initProcessor, gcCount = ", gcCount);
      return;
    }

    // ALL GC THREADS IN PARALLEL

    // each GC threads acquires ptr to its thread object, for accessing thread local counters
    // and workqueue pointers.  If the thread object needs to be moved, it has been, in copyThreads
    // above, and its ref in the threads array (in copyThreads) and the activeThread field of the
    // current processors VM_Processor (in initProcessor) have been updated  This means using either
    // of those fields to get "currentThread" get the copied thread object.
    //
    VM_CollectorThread mylocal = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());

    // This rendezvous appears to be required, else pBOB fails
    // See copyingGC.VM_Allocator...
    //
    VM_CollectorThread.gcBarrier.rendezvous(RENDEZVOUS_TIMES);
          
    // Begin finding roots for this collection.
    // roots are (fromSpace) object refs in the jtoc or on the stack or in writebuffer
    // objects.  For each unmarked root object, it is marked, copied to mature space, and
    // added to thread local work queue buffer for later scanning. The root refs are updated.
     
    // scan VM_Processor object, causing referenced object to be copied.  When write buffers are
    // implemented as objects it is thus copied, and special code updates interior pointers 
    // (declared as ints) into the writebuffers.
    //
    gc_scanProcessor();        // each gc threads scans its own processor object

    VM_ScanStatics.scanStatics();     // all GC threads scan JTOC in parallel

    gc_scanThreads();          // ALL GC threads compete to scan threads & stacks

    if (outOfSmallHeapSpace) {
      VM_Scheduler.trace("gcCollectMinor:", "out of memory after scanning statics & threads");
      return;
    }

    // This synchronization is necessary to ensure all stacks have been scanned
    // and all internal saved ip values have been updated before we scan copied
    // objects.  Because if we scan a VM_Method, and then update its code pointer
    // we can no longer compute old ip offsets for updating saved ip values
    //
    VM_CollectorThread.gcBarrier.rendezvous(RENDEZVOUS_TIMES);

    // have processor 1 record timestamp for end of scanning stacks & statics
    if (TIME_GC_PHASES && (mylocal.gcOrdinal == 1))
      gcStacksAndStaticsDoneTime = VM_Time.now(); // for time scanning stacks & statics

    // scan modified old objects for refs->nursery objects
    gc_processWriteBuffers();

    if (outOfSmallHeapSpace) {
      VM_Scheduler.trace("gcCollectMinor:", "out of memory after processWriteBuffers");
      return;
    }

    // each GC thread processes work queue buffers until empty
    if (verbose >= 1) VM.sysWriteln("Emptying work queue");
    gc_emptyWorkQueue();

    // have processor 1 record timestamp for end of scan/mark/copy phase
    if (TIME_GC_PHASES && (mylocal.gcOrdinal == 1))
      gcScanningDoneTime = VM_Time.now();

    if (outOfSmallHeapSpace) {
      VM_Scheduler.trace("gcCollectMinor:", "out of memory after emptyWorkQueue");
      return;
    }

    // If counting or timing in VM_GCWorkQueue, save current counter values
    //
    if (VM_GCWorkQueue.WORKQUEUE_COUNTS)   VM_GCWorkQueue.saveCounters(mylocal);
    if (VM_GCWorkQueue.MEASURE_WAIT_TIMES || VM_CollectorThread.MEASURE_WAIT_TIMES)
      VM_GCWorkQueue.saveWaitTimes(mylocal);
     
    // all write buffers were reset to empty earlier, check that still empty
    if (GC_CHECKWRITEBUFFER) gc_checkWriteBuffers();

    // If there are not any objects with finalizers skip finalization phases
    //
    if (VM_Finalizer.existObjectsWithFinalizers()) {
      if (mylocal.gcOrdinal == 1) {

	VM_GCWorkQueue.workQueue.reset();   // reset work queue shared control variables
	
	// one thread scans the hasFinalizer list for dead objects.  They are made live
	// again, and put into that threads work queue buffers.
	//
	VM_Finalizer.moveToFinalizable();

	// following resets barrier bits in objects modified by moveToFinalizable
	// write buffer entries generated during GC will be discarded, and these
	// object may not get scanned in the next collection (hard to find bug) 
	//
	VM_WriteBuffer.resetBarrierBits(VM_Processor.getCurrentProcessor());
      }
      
      // ALL threads have to wait to see if any finalizable objects are found
      VM_CollectorThread.gcBarrier.rendezvous(RENDEZVOUS_TIMES);
     
      if (VM_Finalizer.foundFinalizableObject) {

	// Some were found. Now ALL threads execute emptyWorkQueue again, this time
	// to mark and keep live all objects reachable from the new finalizable objects.
	//
	gc_emptyWorkQueue();

      }

      if (outOfSmallHeapSpace) {
	VM_Scheduler.trace("gcCollectMinor:", "out of memory after finalization");
	return;
      }

    }  //  end of Finalization Processing

    // Each GC thread increments adds its wait times for this collection
    // into its total wait time - for printSummaryStatistics output
    //
    if (VM_CollectorThread.MEASURE_WAIT_TIMES)
      mylocal.incrementWaitTimeTotals();
    
    if (VM.VerifyAssertions) VM.assert(!majorCollection);

    //
    if ( VM_GCLocks.testAndSetFinishLock() ) {

      // BEGIN SINGLE GC THREAD SECTION - MINOR END

      // set ending times for preceeding finalization or scanning phase
      // do here where a sync (below) will push value to memory
      if (TIME_GC_PHASES)  gcFinalizeDoneTime = VM_Time.now();

      // reset allocation pointers to the empty nursery area
      nurseryHeap.reset();

      if (VM.ParanoidGCCheck) 
	nurseryHeap.clobber();

      prepareNonParticipatingVPsForAllocation( false /*minor*/);

      gcInProgress = false;

      // reset lock for next GC before starting mutators
      VM_GCLocks.reset();

      // reset the flag used during GC initialization, for next GC
      initGCDone = false;

      gcEndTime = VM_Time.now();
      gcMinorTime = gcEndTime - gcStartTime;
      gcTotalTime += gcMinorTime;
      if (gcMinorTime > maxMinorTime) maxMinorTime = gcMinorTime;
      totalMinorTime += gcMinorTime;

      if ( verbose >= 1 ) {
	VM.sysWrite("\n<GC ");
	VM.sysWrite(gcCount,false);
	VM.sysWrite(" (MINOR) time ");
	VM.sysWrite( (int)(gcMinorTime*1000.0), false );
	VM.sysWrite(" (ms)  smallHeap freeMemory = ", (int)smallHeap.freeMemory());
	VM.sysWrite("  found finalizable = ");
	VM.sysWrite(VM_Finalizer.foundFinalizableCount,false);
	VM.sysWrite("\n");
      }

      // add current GC phase times into totals, print if verbose on
      if (TIME_GC_PHASES) accumulateGCPhaseTimes();  	

      if ( verbose >= 1 ) printWaitTimesAndCounts();

      // must sync memory changes so GC threads on other processors see above changes
      VM_Magic.sync();

    }  // END OF SINGLE THREAD SECTION

    // all GC threads return to collect
    return;

  }  // gcCollectMinor


  /**
   * Do a Major Collection.  Does a full scan in which all objects reachable from
   * roots (stacks & statics) are marked.  Triggered after a Minor collection
   * when the count of available free blocks falls below a specified threshold.
   * Because Minor collection must complete successfully, this threshold is
   * conservatively set to the number of blocks in the Nursery.
   */
  static void gcCollectMajor () {
    if (verbose >= 1) VM.sysWriteln("Starting major collection");

    VM_CollectorThread mylocal = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());

    if (VM_CollectorThread.MEASURE_WAIT_TIMES)
      mylocal.resetWaitTimers();         // reset for measuring major GC wait times

    // Begin single thread initialization
    //
    if ( VM_GCLocks.testAndSetInitLock()) {
      gcStartTime = VM_Time.now();    // reset for measuring major collections
       
      gcMajorCount++;
      majorCollection = true;
       
      if (verbose >= 1) VM.sysWriteln("Initialization for major GC ",gcMajorCount);

      // setup common workqueue for num VPs participating, used to be called once.
      // now count varies for each GC, so call for each GC
      //
      VM_GCWorkQueue.workQueue.initialSetup(VM_CollectorThread.numCollectors());

      // invert the mark_flag value, used for marking BootImage objects
      if ( OBJECT_GC_MARK_VALUE == 0 )
	OBJECT_GC_MARK_VALUE = VM_AllocatorHeader.GC_MARK_BIT_MASK;
      else
	OBJECT_GC_MARK_VALUE = 0;

      // Now initialize the large object space mark array
      largeHeap.startCollect();

      // Initialize collection in the small heap
      smallHeap.startCollect();

      // some RVM VM_Processors may
      // be blocked in native C and not participating in a collection.
      prepareNonParticipatingVPsForGC( true /*major*/);
    }

    // ALL COLLECTOR THREADS IN PARALLEL

    VM_CollectorThread.gcBarrier.rendezvous(RENDEZVOUS_TIMES);

    VM_GCWorkQueue.resetWorkQBuffers();  // reset thread local work queue buffers

    // Each participating processor clears the mark array for the blocks it owns
    smallHeap.zeromarks(VM_Processor.getCurrentProcessor());

    VM_CollectorThread.gcBarrier.rendezvous(RENDEZVOUS_TIMES);

    // have processor 1 record timestamp for end of scanning stacks & statics
    if (TIME_GC_PHASES && (mylocal.gcOrdinal == 1))
      gcInitDoneTime = VM_Time.now(); // for time scanning stacks & statics

    VM_ScanStatics.scanStatics();     // all threads scan JTOC in parallel

    gc_scanThreads();          // ALL GC threads compete to scan threads & stacks

    // have processor 1 record timestame for end of scanning stacks & statics
    // ...this will be approx. because there is not a rendezvous after scanning thread stacks.
    if (TIME_GC_PHASES && (mylocal.gcOrdinal == 1))
      gcStacksAndStaticsDoneTime = VM_Time.now(); // for time scanning stacks & statics

    gc_emptyWorkQueue();

    // have processor 1 record timestame for end of scan/mark/copy phase
    if (TIME_GC_PHASES && (mylocal.gcOrdinal == 1))
      gcScanningDoneTime = VM_Time.now();

    // If counting or timing in VM_GCWorkQueue, save current counter values
    //
    if (VM_GCWorkQueue.WORKQUEUE_COUNTS)   VM_GCWorkQueue.saveCounters(mylocal);
    if (VM_GCWorkQueue.MEASURE_WAIT_TIMES || VM_CollectorThread.MEASURE_WAIT_TIMES)
      VM_GCWorkQueue.saveWaitTimes(mylocal);

    // If there are not any objects with finalizers skip finalization phases
    //
    if (VM_Finalizer.existObjectsWithFinalizers()) {

      // Now handle finalization

      if (mylocal.gcOrdinal == 1) {

	VM_GCWorkQueue.workQueue.reset();   // reset work queue shared control variables
	
	// one thread scans the hasFinalizer list for dead objects.  They are made live
	// again, and put into that threads work queue buffers.
	//
	VM_Finalizer.moveToFinalizable();

	// following resets barrier bits in objects modified by moveToFinalizable
	// write buffer entries generated during GC will be discarded, and these
	// object may not get scanned in the next collection (hard to find bug) 
	//
	VM_WriteBuffer.resetBarrierBits(VM_Processor.getCurrentProcessor());
      }
      
      // ALL threads have to wait to see if any finalizable objects are found
      VM_CollectorThread.gcBarrier.rendezvous(RENDEZVOUS_TIMES);
     
      if (VM_Finalizer.foundFinalizableObject) {

	// Some were found. Now ALL threads execute emptyWorkQueue again, this time
	// to mark and keep live all objects reachable from the new finalizable objects.
	//
	gc_emptyWorkQueue();

      }
    }

    if (TIME_GC_PHASES && (mylocal.gcOrdinal == 1))
      gcFinalizeDoneTime = VM_Time.now();

    if (VM.ParanoidGCCheck) smallHeap.clobberfree();

    // Reclaim large heap space
    if (mylocal.gcOrdinal == 1) {
      largeHeap.endCollect();
    }

    // Sweep small heap
    smallHeap.sweep(mylocal);

    // Added this Rendezvous to prevent mypid==1 from proceeding before all others
    // have completed the above, especially if mypid=1 did NOT free any blocks
    VM_CollectorThread.gcBarrier.rendezvous(RENDEZVOUS_TIMES);

    // Each GC thread increments adds its wait times for this collection
    // into its total wait time - for printSummaryStatistics output
    //
    if (VM_CollectorThread.MEASURE_WAIT_TIMES)
      mylocal.incrementWaitTimeTotals();

    // Do single-threaded cleanup
    if (mylocal.gcOrdinal == 1) {
      prepareNonParticipatingVPsForAllocation( true /*major*/);

      VM_GCLocks.reset();

      // DONE except for verbose output, measurements etc..

      gcEndTime = VM_Time.now();
      gcMajorTime = gcEndTime - gcStartTime;
      gcTotalTime += gcMajorTime;
      totalMajorTime += gcMajorTime;
      if (gcMajorTime > maxMajorTime) maxMajorTime = gcMajorTime;
	 
      if (verbose >= 1) {
	  VM.sysWrite("<GC ", gcCount, "> ");
	  VM.sysWrite(" (MAJOR) time ", (int)(gcMajorTime * 1000), " (ms) ");
	  VM.sysWrite(" smallHeap free Memory = ", smallHeap.freeMemory());
	  VM.sysWriteln("  found finalizable = ", VM_Finalizer.foundFinalizableCount);
      }

      // add current GC phase times into totals, print if verbose on
      if (TIME_GC_PHASES) accumulateGCPhaseTimes();  	

      if ( verbose >= 1 ) printWaitTimesAndCounts();

      //	      if (verbose >= 1 && VM_CollectorThread.MEASURE_WAIT_TIMES)
      //		VM_CollectorThread.printThreadWaitTimes();

    }	// if mylocal.gcOrdinal == 1

    // ALL collector threads return to caller (without a rendezvous)
      
  }  // gcCollectMajor


  /**
   * process objects in the work queue buffers until no more buffers to process
   * Minor collections
   */
  static void gc_emptyWorkQueue () {
    VM_Address ref = VM_GCWorkQueue.getFromWorkBuffer();
    while (!ref.isZero()) {
      gc_scanObjectOrArray(ref);
      ref = VM_GCWorkQueue.getFromWorkBuffer();
    }
  }

  private static void prepareNonParticipatingVPsForGC(boolean major) {
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
	  t.contextRegisters.setInnermost( VM_Address.zero(), t.jniEnv.JNITopJavaFP );
	}

	if (major) {
	  smallHeap.zeromarks(vp);
	} else {
	  // for minor collections:
	  // move the processors writebuffer entries into the executing collector
	  // threads work buffers so the referenced objects will be scanned.
	  VM_WriteBuffer.moveToWorkQueue(vp);
	}
      }
    }
  
    if ( !major ) {
      // in case native processors have writebuffer entries, move them also.
      for (int i = 1; i <= VM_Processor.numberNativeProcessors; i++) {
	VM_Processor vp = VM_Processor.nativeProcessors[i];
	VM_WriteBuffer.moveToWorkQueue(vp);
	// check that native processors have not done allocations
	if (VM.VerifyAssertions) {
	  if (!vp.startChunk1.isZero()) {
	    VM_Scheduler.trace("prepareNonParticipatingVPsForGC:",
			       "native processor with non-zero allocation ptr, id =",vp.id);
	    vp.dumpProcessorState();
	    VM.assert(false);
	  }
	}
      }
    }
  }  // prepareNonParticipatingVPsForGC

  private static void prepareNonParticipatingVPsForAllocation(boolean major) {
    // include NativeDaemonProcessor in following loop over processors
    for (int i = 1; i <= VM_Scheduler.numProcessors+1; i++) {
      VM_Processor vp = VM_Scheduler.processors[i];
      if (vp == null) continue;   // the last VP (nativeDeamonProcessor) may be null
      int vpStatus = VM_Processor.vpStatus[vp.vpStatusIndex];
      if ((vpStatus == VM_Processor.BLOCKED_IN_NATIVE) || (vpStatus == VM_Processor.BLOCKED_IN_SIGWAIT)) {
	if (major) {
	  smallHeap.setupallocation(vp);
	} else {
	  if (PROCESSOR_LOCAL_ALLOCATE) 
	    VM_Chunk.resetChunk1(vp, nurseryHeap, false);
	}
      }
    }
  }

  /**
   * called from CollectorThread.run() to perform a collection.
   * Does a Minor collection followed by a Major collection if the
   * count of available blocks goes too low.
   * All GC threads execute in parallel.
   */
  public static void collect () {
    VM_CollectorThread myThread = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());

    long freeBefore = smallHeap.freeMemory();

    // set running threads context regs so that a scan of its stack
    // will start at the caller of collect (ie. VM_CollectorThread.run)
    //
    VM_Address fp = VM_Magic.getFramePointer();
    VM_Address caller_ip = VM_Magic.getReturnAddress(fp);
    VM_Address caller_fp = VM_Magic.getCallerFramePointer(fp);
    VM_Thread.getCurrentThread().contextRegisters.setInnermost( caller_ip, caller_fp );

    gcCollectMinor();

    VM_CollectorThread.gcBarrier.rendezvous(RENDEZVOUS_TIMES);
  
    if (verbose >= 1 && (myThread.gcOrdinal == 1))
      VM_Scheduler.trace("collect: after Minor Collection","smallHeap free memory = ",(int)smallHeap.freeMemory());

    if (outOfSmallHeapSpace) {
      if (myThread.gcOrdinal == 1) {
	VM_Scheduler.trace("collect:","Out Of Memory - could not complete Minor Collection");
	VM_Scheduler.trace("collect:","smallHeap free memory (before) = ", (int)freeBefore);
	VM_Scheduler.trace("collect:","smallHeap free memory (after)  = ", (int)smallHeap.freeMemory());
	smallHeap.reportBlocks();
	outOfMemory(-1);
      }
      else return;   // quit - error
    }
    
    if (outOfLargeSpaceFlag || (smallHeap.freeBlocks() < majorCollectionThreshold)) {
      if (verbose >= 1 && myThread.gcOrdinal == 1) {
	if (outOfLargeSpaceFlag)
	  VM_Scheduler.trace("Major Collection Necessary:", "To reclaim Large Space");
	else
	  VM_Scheduler.trace("Major Collection Necessary:", "smallHeap free memory = ",(int)smallHeap.freeMemory());
      }

      gcCollectMajor();	

      VM_CollectorThread.gcBarrier.rendezvous(RENDEZVOUS_TIMES);

      if (myThread.gcOrdinal == 1) {
	if (verbose >= 1) {
	  VM.sysWriteln((smallHeap.freeBlocks() < majorCollectionThreshold) ? 
			"After Major Collection: VERY LITTLE smallHeap free memory = " :
			"After Major Collection: smallHeap free memory = ",
			smallHeap.freeMemory());
	}
	majorCollection = false;
	outOfSmallHeapSpace = false;
	outOfLargeSpaceFlag = false;
	gcInProgress    = false;
	initGCDone      = false;
      } 
    }

    // Enable allocation now that collection is complete.
    VM_Chunk.resetChunk1(VM_Processor.getCurrentProcessor(), nurseryHeap, false);
  }


  /**
   * Process write buffers for the current processor. called by
   * each collector thread during Minor collections.
   */
  static void gc_processWriteBuffers () {
    VM_WriteBuffer.processWriteBuffer(VM_Processor.getCurrentProcessor());
  }

  /**
   * check that write buffers still empty, if not print diagnostics & reset
   * ...we seem to get some entries after major collections ????
   */
  static void gc_checkWriteBuffers () {
    VM_WriteBuffer.checkForEmpty(VM_Processor.getCurrentProcessor());
  }


  /**
   * Processes live objects in Nursery that need to be marked,
   * copied and forwarded during Minor collection.  Returns the new address
   * of the object in Mature Space.  If the object was not previously
   * marked, then the invoking collector thread will do the copying and
   * enqueue the object on the work queue of objects to be scanned.
   *
   * @param fromRef Reference to object in Nursery
   * @param scan should the copied object be enqueued for scanning?
   * @return the address of the Object in Mature Space
   */
  static VM_Address copyAndScanObject ( VM_Address fromRef, boolean scan ) {

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
      if (VM.VerifyAssertions && !(VM_AllocatorHeader.stateIsForwarded(forwardingPtr) && validRef(toRef))) {
	VM_Scheduler.traceHex("copyAndScanObject", "invalid forwarding ptr =",forwardingPtr);
	VM.assert(false);  
      }
      return toRef;
    }

    // We are the GC thread that must copy the object, so do it.
    VM_Address toRef;
    Object toObj;
    Object[] tib = VM_ObjectModel.getTIB(fromObj);
    VM_Type type = VM_Magic.objectAsType(tib[TIB_TYPE_INDEX]);
    if (VM.VerifyAssertions) VM.assert(VM_GCUtil.validObject(type));
    if (type.isClassType()) {
      VM_Class classType = type.asClass();
      int numBytes = VM_ObjectModel.bytesRequiredWhenCopied(fromObj, classType);
      VM_Address toAddress = gc_getMatureSpace(numBytes);
      if (toAddress.isZero()) {
	// reach here means that no space was available for this thread
        // in mature, noncopying space, therefore 
	// 1. turn on outOfSmallHeapSpace
        // 2. reset availablebits word to original value, which will be unmarked, and 
        // 3. return original fromRef value - so calling code is unchanged.
        // XXXX Might need sync here, but we don't think so.
	VM_ObjectModel.writeAvailableBitsWord(fromObj, forwardingPtr);
	outOfSmallHeapSpace = true;
	return fromRef;
      }
      forwardingPtr |= VM_AllocatorHeader.GC_BARRIER_BIT_MASK;     // set barrier bit 
      toObj = VM_ObjectModel.moveObject(toAddress, fromObj, numBytes, classType, forwardingPtr);
      toRef = VM_Magic.objectAsAddress(toObj);
    } else {
      VM_Array arrayType = type.asArray();
      int numElements = VM_Magic.getArrayLength(fromObj);
      int numBytes = VM_ObjectModel.bytesRequiredWhenCopied(fromObj, arrayType, numElements);
      VM_Address toAddress = gc_getMatureSpace(numBytes);
      if (toAddress.isZero()) {
	// reach here means that no space was available for this thread
        // in mature, noncopying space, therefore
	// 1. turn on outOfSmallHeapSpace
        // 2. reset availablebits word to original value, which will be unmarked, and 
        // 3. return original fromRef value - so calling code is unchanged.
        // XXXX Might need sync here, but we don't think so.
	VM_ObjectModel.writeAvailableBitsWord(fromObj, forwardingPtr);
	outOfSmallHeapSpace = true;
	return fromRef;
      }
      forwardingPtr |= VM_AllocatorHeader.GC_BARRIER_BIT_MASK;     // set barrier bit 
      toObj = VM_ObjectModel.moveObject(toAddress, fromObj, numBytes, arrayType, forwardingPtr);
      toRef = VM_Magic.objectAsAddress(toObj);
      if (arrayType == VM_Type.CodeType) {
	// sync all arrays of ints - must sync moved code instead of sync'ing chunks when full
	VM_Memory.sync(toAddress, numBytes);
      }
    }

    VM_ObjectModel.initializeAvailableByte(toObj); // make it safe for write barrier to access barrier bit non-atmoically

    VM_Magic.sync(); // make changes viewable to other processors 
    
    VM_AllocatorHeader.setForwardingPointer(fromObj, toObj);

    if (scan) VM_GCWorkQueue.putToWorkBuffer(toRef);

    return toRef;
  } 


  /**
   * scan object or array for references - Minor Collections
   */
  static void gc_scanObjectOrArray ( VM_Address objRef ) {
    if (!majorCollection) {
      // For this collector, we only have to process the TIB in a minor collection.
      // In a major collection, the TIB is not going to move and is reachable from the JTOC.
      VM_ObjectModel.gcProcessTIB(objRef);
    }

    VM_Type type = VM_Magic.getObjectType(VM_Magic.addressAsObject(objRef));
    if (type.isClassType()) {
      int[] referenceOffsets = type.asClass().getReferenceOffsets();
      for(int i = 0, n=referenceOffsets.length; i < n; i++) {
	processPtrField(objRef.add(referenceOffsets[i]));
      }
    } else {
      VM_Type elementType = type.asArray().getElementType();
      if (elementType.isReferenceType()) {
	int num_elements = VM_Magic.getArrayLength(VM_Magic.addressAsObject(objRef));
	for (int i=0; i<num_elements; i++) 
	  processPtrField( objRef.add(4 * i) );
      }
    }
  }


  // scan a VM_Processor object to force "interior" objects to be copied, marked,
  // and queued for later scanning. adjusts write barrier pointers, if
  // write buffer is moved.
  //
  static void gc_scanProcessor ()  {

    VM_Processor st = VM_Processor.getCurrentProcessor();
    VM_Address sta = VM_Magic.objectAsAddress(st);
    
    if (PROCESSOR_LOCAL_ALLOCATE) {
      // local heap pointer set in initProcessor, should still be 0, ie no allocates yet
      if (VM.VerifyAssertions) VM.assert(VM_Chunk.unusedChunk1(st));
    }
    
    if (VM.VerifyAssertions) {
      // processor should already be copied, ie NOT in nursery
      VM.assert(!nurseryHeap.refInHeap(sta));
      // and its processor array entry updated
      VM.assert(sta.EQ(VM_Magic.objectAsAddress(VM_Scheduler.processors[st.id])));
    }
    
    VM_Address oldbuffer = VM_Magic.objectAsAddress(st.modifiedOldObjects);
    VM_ScanObject.scanObjectOrArray(sta);
    // if writebuffer moved, adjust interior pointers
    VM_Address newbuffer = VM_Magic.objectAsAddress(st.modifiedOldObjects);
    if (oldbuffer.NE(newbuffer)) {
      st.modifiedOldObjectsMax = newbuffer.add(st.modifiedOldObjectsMax.diff(oldbuffer));
      st.modifiedOldObjectsTop = newbuffer.add(st.modifiedOldObjectsTop.diff(oldbuffer));
    }
  }  // scanProcessor


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

    VM_Thread  t;
    int[]      oldstack;
    
    // get ID of running GC thread
    int myThreadId = VM_Thread.getCurrentThread().getIndex();
    
    for (int i=0; i<VM_Scheduler.threads.length; i++ ) {
      t = VM_Scheduler.threads[i];
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
	if (VM.VerifyAssertions) VM.assert( !(nurseryHeap.refInHeap(ta)) );
	
	if (VM.VerifyAssertions) oldstack = t.stack; // for verifying  gc stacks not moved
	VM_ScanObject.scanObjectOrArray(ta);             // will copy copy stacks, reg arrays, etc.
	if (VM.VerifyAssertions) VM.assert(oldstack == t.stack);
	
	if (t.jniEnv != null) VM_ScanObject.scanObjectOrArray(t.jniEnv);
	VM_ScanObject.scanObjectOrArray(t.contextRegisters);
	VM_ScanObject.scanObjectOrArray(t.hardwareExceptionRegisters);
	
	if (GCDEBUG_SCANTHREADS) VM_Scheduler.trace("VM_Allocator","Collector Thread scanning own stack",i);
	VM_ScanStack.scanStack( t, VM_Address.zero(), true /*relocate_code*/ );
	continue;
      }

      // skip other collector threads participating (have ordinal number) in this GC
      if ( t.isGCThread && (VM_Magic.threadAsCollectorThread(t).gcOrdinal > 0) )
	continue;
      
      // have mutator thread, compete for it with other GC threads
      if ( VM_GCLocks.testAndSetThreadLock(i) ) {

	if (GCDEBUG_SCANTHREADS) VM_Scheduler.trace("VM_Allocator","processing mutator thread",i);
	
	// all threads should have been copied out of fromspace earlier
	if (VM.VerifyAssertions) VM.assert( !(nurseryHeap.refInHeap(ta)) );
	
	// scan thread object to force "interior" objects to be copied, marked, and
	// queued for later scanning.
	oldstack = t.stack;    // remember old stack address before scanThread
	VM_ScanObject.scanObjectOrArray(ta);
	
	// if stack moved, adjust interior stack pointers
	if ( oldstack != t.stack ) {
	  if (GCDEBUG_SCANTHREADS) VM_Scheduler.trace("VM_Allocator","...adjusting mutator stack",i);
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
	//
	if (verbose >= 3) VM.sysWriteln("Scanning stack for thread ",i);
	VM_ScanStack.scanStack( t, VM_Address.zero(), true /*relocate_code*/ );

      }  // (if true) we seized got the thread to process
      
      else continue;  // some other gc thread has seized this thread
      
    }  // end of loop over threads[]
    
  }  // gc_scanThreads
  

  static boolean validRef ( VM_Address ref ) {
      return bootHeap.refInHeap(ref) || smallHeap.refInHeap(ref) || largeHeap.refInHeap(ref) ||
	  immortalHeap.refInHeap(ref) || nurseryHeap.refInHeap(ref) || mallocHeap.refInHeap(ref);
  }

  // setupProcessor is called from the constructor of VM_Processor
  // to alloc allocation structs and collection write buffers
  // for the PRIMORDIAL processor, allocation structs are built
  // in init, and setupProcessor is called a second time from VM.boot.
  // this second call must cause writebuffer pointers to be initialized
  // see VM_WriteBuffer.setupProcessor().
  //
  static void setupProcessor (VM_Processor st) {
    VM_WriteBuffer.setupProcessor( st );

    if (PROCESSOR_LOCAL_ALLOCATE) 
      VM_Chunk.resetChunk1(st, nurseryHeap, false);

    // for the PRIMORDIAL processor allocation of sizes, etc occurs
    // during init(), nothing more needs to be done
    //
    if (st.id > VM_Scheduler.PRIMORDIAL_PROCESSOR_ID) 
      smallHeap.setupProcessor(st);
  } 


  /**
   * processFinalizerListElement is called from VM_Finalizer.moveToFinalizable
   * to process a FinalizerListElement (FLE) on the hasFinalizer list.  
   * <pre> 
   *   -if the FLE interger pointer -> to a marked/live object return true.
   *        For minor collections, if the object is in the Nursery, use
   *        its forwarding pointer to update the FLE integer pointer to 
   *        point to its new mature space location. For minor collections
   *        all "old" objects are considered "live"
   *   -if the FLE integer pointer -> to an unmarked/dead object:
   *        1. make it live again, copying to mature space if necessary
   *        2. set the FLE reference pointer to point to the object (to keep it live)
   *        3. enqueue the object for scanning, so that the finalization
   *           scan phase fill make live all objects reachable from the object
   *        return false.
   * </pre> 
   * Executed by ONE GC collector thread at the end of collection, after
   * all reachable object are marked and forwarded.
   *  
   * @param le  VM_FinalizerListElement to be processed
   */
  static boolean processFinalizerListElement (VM_FinalizerListElement le) {

    VM_Address ref = le.value;
    
    // For Minor Collections look for entries pointing to unreached Nursery objects,
    // copy the objects to mature space & and 
    // For minor GCs, FromSpace is the Nursery.
    //

    if (!majorCollection) {
      // Minor Collection procsssing of Nursery & Mature Space
      if (nurseryHeap.refInHeap(ref)) {
	Object objRef = VM_Magic.addressAsObject(ref);
	if (VM_AllocatorHeader.isForwarded(objRef)) {
	  le.move(VM_Magic.objectAsAddress(VM_AllocatorHeader.getForwardingPointer(objRef)));
	  return true;
	} else {
	  // dead, mark, copy, and enque for scanning, and set le.pointer
	  le.finalize(copyAndScanObject(ref, true));
	  return false;
	}
      }
    
      // for minor collections, objects in mature space are assumed live.
      // they are not moved, and le.value is OK
      if (smallHeap.refInHeap(ref)) return true;
      
    } else { 
      // Major Collection procsssing of Nursery & Mature Space

      // should never see an object in the Nursery during Major Collections
      if (nurseryHeap.refInHeap(ref))
	VM.assert(NOT_REACHED);

      if (smallHeap.refInHeap(ref)) {
	if (smallHeap.isLive(ref)) {
	  return true;
	} else {
	  smallHeap.mark(ref);
	  VM_GCWorkQueue.putToWorkBuffer(ref);
	  le.finalize(ref);
	  return false;
	}
      }
    }

    if (largeHeap.refInHeap(ref)) {
      if (largeHeap.isLive(ref)) {
	return true;
      } else {
	largeHeap.mark(ref);
	VM_GCWorkQueue.putToWorkBuffer(ref);
	le.finalize(ref);
	return false;
      }
    }

    VM.assert(false);
    return false; 
  }  // processFinalizerListElement
       
  // Called from WriteBuffer code for generational collectors.
  // Argument is a modified old object which needs to be scanned
  //
  static void processWriteBufferEntry (VM_Address ref) {
      VM_ScanObject.scanObjectOrArray(ref);
  }
        
  /**
   * update times used when TIME_GC_PHASES is on
   */
  private static void accumulateGCPhaseTimes () {

    double start = 0.0;
    if (!majorCollection) 
      start    = gcStartTime - VM_CollectorThread.gcBarrier.rendezvousStartTime;
    double init     = gcInitDoneTime - gcStartTime;
    double stacksAndStatics = gcStacksAndStaticsDoneTime - gcInitDoneTime;
    double scanning = gcScanningDoneTime - gcStacksAndStaticsDoneTime;
    double finalize = gcFinalizeDoneTime - gcScanningDoneTime;
    double finish   = gcEndTime - gcFinalizeDoneTime;

    // add current GC times into totals for summary output
    //    totalStartTime += start;   // always measured in ge initialization
    if (!majorCollection) {
      totalInitTime += init;
      totalStacksAndStaticsTime += stacksAndStatics;
      totalScanningTime += scanning;
      totalFinalizeTime += finalize;
      totalFinishTime += finish;
    }
    else {
      totalInitTimeMajor += init;
      totalStacksAndStaticsTimeMajor += stacksAndStatics;
      totalScanningTimeMajor += scanning;
      totalFinalizeTimeMajor += finalize;
      totalFinishTimeMajor += finish;
    }

    // if invoked with -verbose:gc print output line for this last GC
    if (verbose >= 1) {
      VM.sysWrite("<GC ", gcCount, ">");
      if (!majorCollection) 
	  VM.sysWrite(" startTime ", (int)(start*1000000.0), "(us)");
      VM.sysWrite(" init ", (int)(init*1000000.0), "(us)");
      VM.sysWrite(" stacks & statics ", (int)(stacksAndStatics*1000000.0), "(us)");
      VM.sysWrite(" scanning ", (int)(scanning*1000.0), "(ms)");
      VM.sysWrite(" finalize ", (int)(finalize*1000000.0), "(us)");
      VM.sysWriteln(" finish ", (int)(finish*1000000.0), "(us)");
    }
  }

  /**
   * Generate summary statistics when VM exits. invoked via sysExit callback.
   */
  static void printSummaryStatistics () {

    int np = VM_Scheduler.numProcessors;

    // produce summary system exit output if -verbose:gc was specified of if
    // compiled with measurement flags turned on
    //
    if ( ! (TIME_GC_PHASES || VM_CollectorThread.MEASURE_WAIT_TIMES || verbose >= 1) )
      return;     // not verbose, no flags on, so don't produce output

    VM.sysWriteln("\nGC stats: Hybrid Collector (", np, " Collector Threads ):");
    VM.sysWrite("          Heap Size ", smallHeap.size / 1024, " Kb");
    VM.sysWrite("  Nursery Size ", nurseryHeap.size / 1024, " Kb");
    VM.sysWriteln("  Large Object Heap Size ", largeHeap.size / 1024, " Kb");

    VM.sysWrite("  ");
    if (gcCount == 0)
      VM.sysWrite("0 MinorCollections");
    else {
      VM.sysWrite(gcCount,false);
      VM.sysWrite(" MinorCollections: avgTime ");
      VM.sysWrite( (int)( ((totalMinorTime/(double)gcCount)*1000.0) ),false);
      VM.sysWrite(" (ms) maxTime ");
      VM.sysWrite( (int)(maxMinorTime*1000.0),false);
      VM.sysWrite(" (ms) AvgStartTime ");
      VM.sysWrite( (int)( ((totalStartTime/(double)gcCount)*1000000.0) ),false);
      VM.sysWrite(" (us)\n");
      VM.sysWrite("  ");
    }
    if (gcMajorCount == 0)
      VM.sysWrite("0 MajorCollections\n");
    else {
      VM.sysWrite(gcMajorCount,false);
      VM.sysWrite(" MajorCollections: avgTime ");
      VM.sysWrite( (int)( ((totalMajorTime/(double)gcMajorCount)*1000.0) ),false);
      VM.sysWrite(" (ms) maxTime ");
      VM.sysWrite( (int)(maxMajorTime*1000.0),false);
      VM.sysWrite(" (ms)\n");
      VM.sysWrite("  Total Collection Time ");
      VM.sysWrite( (int)(gcTotalTime*1000.0),false);
      VM.sysWrite(" (ms)\n\n");
    }

    if (COUNT_COLLISIONS && (gcCount>0) && (np>1)) {
      VM.sysWrite("  avg number of collisions per collection = ");
      VM.sysWrite(collisionCount/gcCount,false);
      VM.sysWrite("\n\n");
    }

    if (TIME_GC_PHASES && (gcCount>0)) {
      int avgStart=0, avgInit=0, avgStacks=0, avgScan=0, avgFinalize=0, avgFinish=0;

      avgStart = (int)((totalStartTime/(double)gcCount)*1000000.0);
      avgInit = (int)((totalInitTime/(double)gcCount)*1000000.0);
      avgStacks = (int)((totalStacksAndStaticsTime/(double)gcCount)*1000000.0);
      avgScan = (int)((totalScanningTime/(double)gcCount)*1000.0);
      avgFinalize = (int)((totalFinalizeTime/(double)gcCount)*1000000.0);
      avgFinish = (int)((totalFinishTime/(double)gcCount)*1000000.0);

      VM.sysWrite("Average Time in Phases of Collection:\n");
      VM.sysWrite("Minor: startTime ");
      VM.sysWrite( avgStart, false);
      VM.sysWrite("(us) init ");
      VM.sysWrite( avgInit, false);
      VM.sysWrite("(us) stacks & statics ");
      VM.sysWrite( avgStacks, false);
      VM.sysWrite("(us) scanning ");
      VM.sysWrite( avgScan, false );
      VM.sysWrite("(ms) finalize ");
      VM.sysWrite( avgFinalize, false);
      VM.sysWrite("(us) finish ");
      VM.sysWrite( avgFinish, false);
      VM.sysWrite("(us)\n");

      if (gcMajorCount>0) {
	avgInit = (int)((totalInitTimeMajor/(double)gcMajorCount)*1000000.0);
	avgStacks = (int)((totalStacksAndStaticsTimeMajor/(double)gcMajorCount)*1000000.0);
	avgScan = (int)((totalScanningTimeMajor/(double)gcMajorCount)*1000.0);
	avgFinalize = (int)((totalFinalizeTimeMajor/(double)gcMajorCount)*1000000.0);
	avgFinish = (int)((totalFinishTimeMajor/(double)gcMajorCount)*1000000.0);

	VM.sysWrite("Major: (no startTime) init ");
	VM.sysWrite( avgInit, false);
	VM.sysWrite("(us) stacks & statics ");
	VM.sysWrite( avgStacks, false);
	VM.sysWrite("(us) scanning ");
	VM.sysWrite( avgScan, false );
	VM.sysWrite("(ms) finalize ");
	VM.sysWrite( avgFinalize, false);
	VM.sysWrite("(us) finish ");
	VM.sysWrite( avgFinish, false);
	VM.sysWrite("(us)\n\n");
      }
    }

    if (VM_CollectorThread.MEASURE_WAIT_TIMES && (gcCount>0)) {
      double totalBufferWait = 0.0;
      double totalFinishWait = 0.0;
      double totalRendezvousWait = 0.0;
      int avgBufferWait=0, avgFinishWait=0, avgRendezvousWait=0;
      double collections = (double)(gcCount + gcMajorCount);

      VM_CollectorThread ct;
      for (int i=1; i <= np; i++ ) {
	ct = VM_CollectorThread.collectorThreads[VM_Scheduler.processors[i].id];
	totalBufferWait += ct.totalBufferWait;
	totalFinishWait += ct.totalFinishWait;
	totalRendezvousWait += ct.totalRendezvousWait;
      }

      avgBufferWait = ((int)((totalBufferWait/collections)*1000000.0))/np;
      avgFinishWait = ((int)((totalFinishWait/collections)*1000000.0))/np;
      avgRendezvousWait = ((int)((totalRendezvousWait/collections)*1000000.0))/np;

      VM.sysWrite("Average Wait Times For Each Collector Thread In A Collection:\n");
      VM.sysWrite("Buffer Wait ", avgBufferWait, " (us) Finish Wait ");
      VM.sysWrite( avgFinishWait, " (us) Rendezvous Wait ");
      VM.sysWriteln( avgRendezvousWait, " (us)");
    }

  }  // printSummaryStatistics

  /**
   * Generate output for measurement flags turned on in VM_GCWorkQueue
   */
  private static void
    printWaitTimesAndCounts () {

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
  }  // printWaitTimesAndCounts


  /**
   * Process an object reference field during collection.
   *
   * @param location  address of a reference field
   */
  static void processPtrField ( VM_Address location ) {
      VM_Magic.setMemoryAddress(location, processPtrValue(VM_Magic.getMemoryAddress(location)));
  }

  /**
   * Process an object reference.
   * During minor collections only references that point to nursery objects
   * need to be processed.  
   * During major collections all references are processed.
   * 
   * @param ref  reference to process
   */
  static VM_Address processPtrValue (VM_Address ref) {
    if (ref.isZero()) return ref;  

    if (majorCollection) {
      // Major collection; we should have already evacuated the nursery.
      if (VM.VerifyAssertions) VM.assert(!nurseryHeap.refInHeap(ref));
      
      if (smallHeap.refInHeap(ref)) {
	if (!smallHeap.mark(ref)) VM_GCWorkQueue.putToWorkBuffer(ref);
	return ref;
      } 

      if (largeHeap.refInHeap(ref) ) {
	if (!largeHeap.mark(ref)) VM_GCWorkQueue.putToWorkBuffer(ref);
	return ref;
      } 

      if (bootHeap.refInHeap(ref) || immortalHeap.refInHeap(ref) ) {
	if (VM_AllocatorHeader.testAndMark(VM_Magic.addressAsObject(ref), OBJECT_GC_MARK_VALUE)) {
	  VM_GCWorkQueue.putToWorkBuffer(ref);
	}
	return ref;
      }

      // If its not in the malloc heap, then it is a bad reference
      if (VM.VerifyAssertions) {
	if (!mallocHeap.refInHeap(ref)) {
	  VM.sysWriteln("processPtrValue encountered bad reference = ", ref);
	  VM.assert(false);
	}
      }
      
      return ref;
    } else {
      // In minor collection any ref not in the nursery heap doesn't need to be processed.
      if (nurseryHeap.refInHeap(ref)) {
	return copyAndScanObject(ref, true);  // return new reference
      }
      
      if (VM.VerifyAssertions) {
	if (!(smallHeap.refInHeap(ref) || largeHeap.refInHeap(ref) ||
	      bootHeap.refInHeap(ref) || immortalHeap.refInHeap(ref) ||
	      mallocHeap.refInHeap(ref))) {
	  VM.sysWriteln("processPtrValue encountered bad reference = ", ref);
	  VM.assert(false);
	}
      }
      return ref; 
    }
  } 
} 
