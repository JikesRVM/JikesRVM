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
  implements VM_Constants, VM_Uninterruptible {

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
    if (verbose >= 2) VM.sysWriteln(why, size);

    // if here and in a GC thread doing GC then it is a system error, insufficient
    // extra space for allocations during GC
    if (VM_Thread.getCurrentThread().isGCThread) {
      VM.sysFail("VM_Allocator: Garbage Collection Failure: GC Thread attempting to allocate during GC");
    }

    // notify GC threads to initiate collection, wait until done
    VM_CollectorThread.collect(VM_CollectorThread.collect);
  } 


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

    if (size >= SMALL_SPACE_MAX) {
      return largeHeap.allocateScalar(size, tib);
    } else {
      VM_Address region = allocateRawMemory(size);
      profileAlloc(region, size, tib); 
      return VM_ObjectModel.initializeScalar(region, tib, size);
    }
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
    if (size >= SMALL_SPACE_MAX) {
      return largeHeap.allocateArray(numElements, size, tib);
    } else {
      VM_Address region = allocateRawMemory(size);  
      profileAlloc(region, size, tib); 
      return VM_ObjectModel.initializeArray(region, tib, numElements, size);
    }
  } 


  /**
   * Get space for a new object or array from small object space.
   *
   * @param size number of bytes to allocate
   * @return the address of the first byte of the allocated zero-filled region
   */
  private static VM_Address allocateRawMemory(int size) throws OutOfMemoryError {
    if (PROCESSOR_LOCAL_ALLOCATE) {
      VM_Magic.pragmaInline();
      return VM_Chunk.allocateChunk1(size);
    } else {
      for (int count = 0; true; count++) {
	VM_Address addr = nurseryHeap.allocateZeroedMemory(size);
	if (!addr.isZero()) {
	  if (ZERO_CHUNKS_ON_ALLOCATION) VM_Memory.zeroTemp(addr, size);
	  return addr;
	} 
	heapExhausted(nurseryHeap, size, count);
      }
    }
  }

  /**
   * Handle heap exhaustion.
   * 
   * @param heap the exhausted heap
   * @param size number of bytes requested in the failing allocation
   * @param count the retry count for the failing allocation.
   */
  public static void heapExhausted(VM_Heap heap, int size, int count) {
    if (heap == nurseryHeap) {
      if (count>3) VM_GCUtil.outOfMemory("nursery space", heap.getSize(), "-X:nh=nnn");
      gc1("GC triggered by object request of ", size);
    } else if (heap == largeHeap) {
      if (count>3) VM_GCUtil.outOfMemory("large object space", heap.getSize(), "-X:lh=nnn");
      outOfLargeSpaceFlag = true;
      gc1("GC triggered by large object request of ", size);
    } else if (heap == smallHeap) {
      VM_GCUtil.outOfMemory("small object heap during collection!!!", heap.getSize(), "-X:h=nnn");
    } else {
      VM.sysFail("unexpected heap");
    }
  }


  // **************************
  // Implementation
  // **************************

  static final boolean  writeBarrier = true;      // MUST BE TRUE FOR THIS STORAGE MANAGER
  static final boolean  movesObjects = true;

  static final int MARK_VALUE = 1;               // designates "marked" objects in Nursery

  static final int SMALL_SPACE_MAX = 2048;       // largest object in small heap

  static int verbose = 0;

  private static final boolean GC_CHECKWRITEBUFFER = false;   // buffer entries during gc

  private static volatile boolean initGCDone = false;

  static int     majorCollectionThreshold;   // minimum # blocks before major collection
  static boolean gcInProgress = false;
  static boolean majorCollection = false;
  static boolean outOfLargeSpaceFlag = false;

  private static VM_BootHeap bootHeap            = new VM_BootHeap();
  private static VM_ContiguousHeap nurseryHeap   = new VM_ContiguousHeap("Nursery Heap");
  private static VM_ImmortalHeap immortalHeap    = new VM_ImmortalHeap();
  private static VM_LargeHeap largeHeap          = new VM_LargeHeap(immortalHeap);
  private static VM_MallocHeap mallocHeap        = new VM_MallocHeap();
  private static VM_SegregatedListHeap smallHeap = new VM_SegregatedListHeap("Small Object Heap", mallocHeap);

  static int OBJECT_GC_MARK_VALUE = 0;   // toggles between 0 and VM_AllocatorHeader.GC_MARK_BIT_MASK;

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
   * for live Nursery objects being copied to Mature Space.
   */
  public static VM_Address gc_getMatureSpace(int size) throws OutOfMemoryError {
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
   */
  static void gcCollectMinor () {
    // ASSUMPTIONS:
    // initGCDone flag is false before first GC thread enter gcCollectMinor
    // InitLock is reset before first GC thread enter gcCollectMinor
    //
    if (VM.BuildForEventLogging && VM.EventLoggingEnabled) VM_EventLogger.logGarbageCollectionEvent();

    double tempStart = 0.0;
    double tempEnd = 0.0;

    if (VM_GCLocks.testAndSetInitLock()) {
      // BEGIN SINGLE GC THREAD SECTION - GC INITIALIZATION
       
      startTime.start(VM_CollectorThread.gcBarrier.rendezvousStartTime); 
      initTime.start(startTime);
      minorGCTime.start(initTime.lastStart);
      
      if (VM.VerifyAssertions) VM.assert( initGCDone == false );  

      gcCount++;

      if (verbose >= 2) VM.sysWriteln("Starting minor collection ", gcCount);

      // setup common workqueue for num VPs participating, used to be called once.
      // now count varies for each GC, so call for each GC   SES 050201
      //
      VM_GCWorkQueue.workQueue.initialSetup(VM_CollectorThread.numCollectors());
      
      gcInProgress = true;
      majorCollection = false;

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

      rootTime.start(initTime);

      // set Done flag to allow other GC threads to begin processing
      initGCDone = true;

    } else {
      // Each GC thread must wait here until initialization is complete
      // this should be short, if necessary at all, so we spin instead of sysYield
      //
      // It is NOT required that all GC threads reach here before any can proceed
      //
      tempStart = VM_CollectorThread.MEASURE_RENDEZVOUS_TIMES ? VM_Time.now() : 0.0;
      while(!initGCDone); // spin until initialization finished
      VM_Magic.isync();             // prevent following inst. from moving infront of waitloop
      tempEnd = VM_CollectorThread.MEASURE_RENDEZVOUS_TIMES ? VM_Time.now() : 0.0;

      // each gc thread copies own VM_Processor, resets processor register & processor
      // local allocation pointers
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

    // add in initialization spin wait time to accumulated collection rendezvous time
    mylocal.rendezvous();

    // following rendezvous seems to be necessary, we are not sure why. Without it,
    // some processors proceed into finding roots, before all gc threads have
    // executed the above gc_initProcessor, and this seems related to the failure.
    // 
    mylocal.rendezvous();
         
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

    VM_CopyingCollectorUtil.scanThreads(nurseryHeap);    // ALL GC threads process thread objects & scan their stacks

    // This synchronization is necessary to ensure all stacks have been scanned
    // and all internal saved ip values have been updated before we scan copied
    // objects.  Because if we scan a VM_Method, and then update its code pointer
    // we can no longer compute old ip offsets for updating saved ip values
    //
    mylocal.rendezvous();

    if (mylocal.gcOrdinal == 1)	scanTime.start(rootTime);

    // scan modified old objects for refs->nursery objects
    gc_processWriteBuffers();

    // each GC thread processes work queue buffers until empty
    if (verbose >= 2) VM.sysWriteln("Emptying work queue");
    gc_emptyWorkQueue();

    if (mylocal.gcOrdinal == 1)	finalizeTime.start(scanTime);

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
      mylocal.rendezvous();
     
      if (VM_Finalizer.foundFinalizableObject) {

	// Some were found. Now ALL threads execute emptyWorkQueue again, this time
	// to mark and keep live all objects reachable from the new finalizable objects.
	//
	gc_emptyWorkQueue();

      }
    }  //  end of Finalization Processing



    // Each GC thread increments adds its wait times for this collection
    // into its total wait time - for printSummaryStatistics output
    //
    if (VM_CollectorThread.MEASURE_WAIT_TIMES)
      mylocal.incrementWaitTimeTotals();
    
    if (VM.VerifyAssertions) VM.assert(!majorCollection);

    if ( VM_GCLocks.testAndSetFinishLock() ) {
      // BEGIN SINGLE GC THREAD SECTION - MINOR END
      finishTime.start(finalizeTime);

      // reset allocation pointers to the empty nursery area
      nurseryHeap.reset();

      if (VM.ParanoidGCCheck) nurseryHeap.clobber();

      prepareNonParticipatingVPsForAllocation( false /*minor*/);

      gcInProgress = false;

      // reset lock for next GC before starting mutators
      VM_GCLocks.reset();

      // reset the flag used during GC initialization, for next GC
      initGCDone = false;

      finishTime.stop();
      minorGCTime.stop();
      updateGCStats(MINOR, 0);
      printGCStats(MINOR);

      // must sync memory changes so GC threads on other processors see above changes
      VM_Magic.sync();

    }  // END OF SINGLE THREAD SECTION
  }  // gcCollectMinor


  /**
   * Do a Major Collection.  Does a full scan in which all objects reachable from
   * roots (stacks & statics) are marked.  Triggered after a Minor collection
   * when the count of available free blocks falls below a specified threshold.
   * Because Minor collection must complete successfully, this threshold is
   * conservatively set to the number of blocks in the Nursery.
   */
  static void gcCollectMajor () {
    if (verbose >= 2) VM.sysWriteln("Starting major collection");

    VM_CollectorThread mylocal = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());

    if (VM_CollectorThread.MEASURE_WAIT_TIMES)
      mylocal.resetWaitTimers();         // reset for measuring major GC wait times

    // Begin single thread initialization
    //
    if (VM_GCLocks.testAndSetInitLock()) {

      startTime.start(VM_CollectorThread.gcBarrier.rendezvousStartTime); 
      initTime.start(startTime);
      majorGCTime.start(initTime.lastStart);
       
      gcMajorCount++;
      majorCollection = true;
       
      // setup common workqueue for num VPs participating, used to be called once.
      // now count varies for each GC, so call for each GC
      //
      VM_GCWorkQueue.workQueue.initialSetup(VM_CollectorThread.numCollectors());

      // invert the mark_flag value, used for marking BootImage objects
      if (OBJECT_GC_MARK_VALUE == 0)
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

    mylocal.rendezvous();

     VM_GCWorkQueue.resetWorkQBuffers();  // reset thread local work queue buffers

    // Each participating processor clears the mark array for the blocks it owns
    smallHeap.zeromarks(VM_Processor.getCurrentProcessor());

    mylocal.rendezvous();

    if (mylocal.gcOrdinal == 1)  rootTime.start(initTime);

    VM_ScanStatics.scanStatics();     // all threads scan JTOC in parallel

    VM_CopyingCollectorUtil.scanThreads(nurseryHeap);    // ALL GC threads process thread objects & scan their stacks

    // have processor 1 record timestamp for end of scanning stacks & statics
    // ...this will be approx. because there is not a rendezvous after scanning thread stacks.
    if (mylocal.gcOrdinal == 1) scanTime.start(rootTime);

    gc_emptyWorkQueue();

    if (mylocal.gcOrdinal == 1) finalizeTime.start(scanTime);
    
    // If counting or timing in VM_GCWorkQueue, save current counter values
    //
    if (VM_GCWorkQueue.WORKQUEUE_COUNTS)   VM_GCWorkQueue.saveCounters(mylocal);
    if (VM_GCWorkQueue.MEASURE_WAIT_TIMES || VM_CollectorThread.MEASURE_WAIT_TIMES)
      VM_GCWorkQueue.saveWaitTimes(mylocal);

    // Now handle finalization
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
      mylocal.rendezvous();
     
      if (VM_Finalizer.foundFinalizableObject) {
	// Some were found. Now ALL threads execute emptyWorkQueue again, this time
	// to mark and keep live all objects reachable from the new finalizable objects.
	//
	gc_emptyWorkQueue();
      }
    }

    if (mylocal.gcOrdinal == 1) finishTime.start(finalizeTime);

    if (VM.ParanoidGCCheck) smallHeap.clobberfree();

    // Reclaim large heap space
    if (mylocal.gcOrdinal == 1) {
      largeHeap.endCollect();
    }

    // Sweep small heap
    smallHeap.sweep(mylocal);

    // Added this Rendezvous to prevent mypid==1 from proceeding before all others
    // have completed the above, especially if mypid=1 did NOT free any blocks
    mylocal.rendezvous();

    // Each GC thread increments adds its wait times for this collection
    // into its total wait time - for printSummaryStatistics output
    //
    if (VM_CollectorThread.MEASURE_WAIT_TIMES)
      mylocal.incrementWaitTimeTotals();

    // Do single-threaded cleanup
    if (mylocal.gcOrdinal == 1) {
      prepareNonParticipatingVPsForAllocation( true /*major*/);

      VM_GCLocks.reset();

      finishTime.stop();
      majorGCTime.stop();
      updateGCStats(MAJOR, 0);
    }	// if mylocal.gcOrdinal == 1
  }


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

    // set running threads context regs so that a scan of its stack
    // will start at the caller of collect (ie. VM_CollectorThread.run)
    //
    VM_Address fp = VM_Magic.getFramePointer();
    VM_Address caller_ip = VM_Magic.getReturnAddress(fp);
    VM_Address caller_fp = VM_Magic.getCallerFramePointer(fp);
    VM_Thread.getCurrentThread().contextRegisters.setInnermost( caller_ip, caller_fp );

    gcCollectMinor();

    VM_CollectorThread myThread = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());
    myThread.rendezvous();
  
    if (verbose >= 2 && (myThread.gcOrdinal == 1))
      VM_Scheduler.trace("collect: after Minor Collection","smallHeap free memory = ",(int)smallHeap.freeMemory());

    if (outOfLargeSpaceFlag || (smallHeap.freeBlocks() < majorCollectionThreshold)) {
      if (verbose >= 1 && myThread.gcOrdinal == 1) {
	if (outOfLargeSpaceFlag)
	  VM_Scheduler.trace("Major Collection Necessary:", "To reclaim Large Space");
	else
	  VM_Scheduler.trace("Major Collection Necessary:", "smallHeap free memory = ",(int)smallHeap.freeMemory());
      }

      gcCollectMajor();	

      myThread.rendezvous();

      if (myThread.gcOrdinal == 1) {
	if (verbose >= 2) {
	  VM.sysWriteln((smallHeap.freeBlocks() < majorCollectionThreshold) ? 
			"After Major Collection: VERY LITTLE smallHeap free memory = " :
			"After Major Collection: smallHeap free memory = ",
			smallHeap.freeMemory());
	}
	majorCollection = false;
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
  static VM_Address copyAndScanObject (VM_Address fromRef, boolean scan) {
    if (VM.VerifyAssertions) VM.assert(nurseryHeap.refInHeap(fromRef));
    return VM_CopyingCollectorUtil.copyAndScanObject(fromRef, scan);
  }

  /**
   * scan object or array for references
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
    
      // for minor collections, non-nursery objects are assumed live.
      if (VM.VerifyAssertions) {
	VM.assert(smallHeap.refInHeap(ref) || largeHeap.refInHeap(ref));
      }
      return true;
    } else { 
      // Major Collection procsssing of Nursery & Mature Space

      // should never see an object in the Nursery during Major Collections
      if (VM.VerifyAssertions) VM.assert(!nurseryHeap.refInHeap(ref));

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
    }

    if (VM.VerifyAssertions) VM.assert(false);
    return true; 
  }  
       
  // Called from WriteBuffer code for generational collectors.
  // Argument is a modified old object which needs to be scanned
  //
  static void processWriteBufferEntry (VM_Address ref) {
      VM_ScanObject.scanObjectOrArray(ref);
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
