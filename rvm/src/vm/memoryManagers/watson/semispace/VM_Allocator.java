/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$


package com.ibm.JikesRVM.memoryManagers.watson;

import com.ibm.JikesRVM.memoryManagers.vmInterface.*;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Constants;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_JavaHeader;
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
 *	               |                              |
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
public class VM_Allocator extends VM_GCStatistics
  implements VM_Constants, 
	     VM_GCConstants {

  /**
   * Control chattering during progress of GC
   */
  static int verbose = 0;

  /**
   * Initialize for boot image.
   */
  public static void init () throws VM_PragmaInterruptible {
    VM_CollectorThread.init();   // to alloc its rendezvous arrays, if necessary
  }

  /**
   * Initialize for execution.
   */
  public static void boot () throws VM_PragmaInterruptible {

    verbose = VM_Interface.verbose();

    // smallHeapSize might not originally have been an even number of pages
    smallHeapSize = VM_Interface.smallHeapSize();
    int oneHeapSize = VM_Memory.roundUpPage(smallHeapSize / 2);
    int largeSize = VM_Interface.largeHeapSize();
    int ps = VM_Memory.getPagesize();
    int immortalSize = VM_Memory.roundUpPage(1024 * 1024 + (4 * largeSize / ps) + 4 * ps);

    if (verbose >= 2) VM.sysWriteln("Attaching heaps");
    VM_Heap.boot(bootHeap, /* no malloc heap in this collector */null);
    fromHeap.attach(oneHeapSize);
    toHeap.attach(oneHeapSize);
    immortalHeap.attach(immortalSize);
    largeHeap.attach(largeSize);

    fromHeap.reset();

    if (verbose >= 1) showParameter();
  }

  static void showParameter() throws VM_PragmaUninterruptible {
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
   * Force a garbage collection. Supports System.gc() called from
   * application programs.
   */
  public static void gc () throws VM_PragmaUninterruptible {
    gcExternalCount++;
    gc1("GC triggered by external call to gc() ", 0);
  }

  /**
   * VM internal method to initiate a collection
   */
  private static void gc1 (String why, int size) throws VM_PragmaUninterruptible {
    if (verbose >= 2) VM.sysWriteln("\n", why, size);

    if (VM_Thread.getCurrentThread().isGCThread) 
      VM.sysFail("VM_Allocator: Garbage Collection Failure: GC Thread attempting to allocate during GC");
  
    VM_CollectorThread.collect(VM_CollectorThread.collect);
  }

  /**
   * Is a GC currently in progress?
   */
  public static boolean gcInProgress() throws VM_PragmaUninterruptible {
    return gcInProgress;
  }

  public long totalSmallHeapMemory() {
    return smallHeapSize;
  }

  public long totalSmallHeapSemispaceMemory() {
    return smallHeapSize / 2;
  }

  long freeSmallHeapSemispaceMemory()  { // free memory in the current semispace
    return fromHeap.freeMemory();
  }

  long totalLargeHeapMemory() {
    return largeHeap.size;
  }

  long freeLargeHeapMemory() {  // free memory in the large heap
    return largeHeap.freeSpace();
  }

  /**
   * Get total amount of memory.  Includes both full size of the
   * small object heap and the size of the large object heap.
   *
   * @return the number of bytes
   */
  public static long totalMemory () throws VM_PragmaUninterruptible {
      return smallHeapSize;
      // + largeHeap.size;    
  }
  
  /**
   * Get the number of bytes currently available for object allocation.
   * Includes shared pool of small object heap.
   *
   * @return number of bytes available
   */
  public static long freeMemory () throws VM_PragmaUninterruptible {
    return fromHeap.freeMemory();
  }

  /**
   *  Includes freeMemory and per-processor local storage
   */
  public static long allSmallFreeMemory () throws VM_PragmaUninterruptible {
    return freeMemory() + VM_Chunk.freeMemoryChunk1();
  }

  public static long allSmallUsableMemory () throws VM_PragmaUninterruptible {
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
  public static Object allocateScalar(int size, Object[] tib)
    throws OutOfMemoryError, VM_PragmaInline, VM_PragmaUninterruptible {
    
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
  public static Object allocateArray(int numElements, int size, Object[] tib)
    throws OutOfMemoryError, VM_PragmaInline, VM_PragmaUninterruptible {

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
  private static VM_Address allocateRawMemory(int size) 
      throws OutOfMemoryError, VM_PragmaInline, VM_PragmaUninterruptible {
    if (PROCESSOR_LOCAL_ALLOCATE) {
      return VM_Chunk.allocateChunk1(size);
    } else {
      return globalAllocateRawMemory(size);
    }
  }
  private static VM_Address globalAllocateRawMemory(int size) 
      throws VM_PragmaNoInline, VM_PragmaUninterruptible {
    for (int count = 0; true; count++) {
      VM_Address addr = fromHeap.allocateZeroedMemory(size);
      if (!addr.isZero()) {
	if (ZERO_CHUNKS_ON_ALLOCATION) VM_Memory.zero(addr, size);
	return addr;
      } 
      heapExhausted(fromHeap, size, count);
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
    if (heap == fromHeap) {
      if (count>3) VM_GCUtil.outOfMemory("small object space", smallHeapSize, "-X:h=nnn");
      gc1("GC triggered by object request of ", size);
    } else if (heap == largeHeap) {
      if (count>3) VM_GCUtil.outOfMemory("large object space", heap.getSize(), "-X:lh=nnn");
      gc1("GC triggered by large object request of ", size);
    } else if (heap == toHeap) {
      VM_GCUtil.outOfMemory("toHeap during collection!!!", smallHeapSize, "-X:h=nnn");
    } else {
      VM.sysFail("unexpected heap");
    }
  }

  // *************************************
  // implementation
  // *************************************

  /** May this collector move objects during collction? */
  public static final boolean movesObjects = true;

  /** Does this collector require that compilers generate the write barrier? */
  public static final boolean writeBarrier = false;

  /**
   * The boundary between "small" objects and "large" objects. For the copying
   * allocators/collectors like this one, this boundary is somewhat arbitrary,
   * as long as it is less than 4K, the unit of allocation in the large object heap.
   */
  private static final int     SMALL_SPACE_MAX = 2048 + 1024 + 12;

  private static volatile boolean initGCDone = false;
  private static volatile boolean gcDone = false;
  
  private static int smallHeapSize;  // total size of small object heaps = 2 * fromHeap.size
  private static VM_BootHeap bootHeap         = new VM_BootHeap();
  private static VM_ContiguousHeap fromHeap   = new VM_ContiguousHeap("Small Object Heap 1");
  private static VM_ContiguousHeap toHeap     = new VM_ContiguousHeap("Small Object Heap 2");
  public  static VM_ImmortalHeap immortalHeap = new VM_ImmortalHeap();
  private static VM_LargeHeap largeHeap       = new VM_LargeHeap(immortalHeap);

  static boolean gcInProgress;      // true if collection in progress, initially false

  // ------- End of Statics --------

  public static void gcSetup ( int numSysThreads ) {
    VM_GCWorkQueue.workQueue.initialSetup(numSysThreads);
  }

  private static void  prepareNonParticipatingVPsForGC() throws VM_PragmaUninterruptible {
    // include NativeDaemonProcessor in following loop over processors
    for (int i = 1; i <= VM_Scheduler.numProcessors+1; i++) {
      VM_Processor vp = VM_Scheduler.processors[i];
      if (vp == null) continue;   // the last VP (nativeDeamonProcessor) may be null
      int vpStatus = VM_Processor.vpStatus[vp.vpStatusIndex];
      if ((vpStatus == VM_Processor.BLOCKED_IN_NATIVE) || (vpStatus == VM_Processor.BLOCKED_IN_SIGWAIT)) {
	if (vpStatus == VM_Processor.BLOCKED_IN_NATIVE) { 
	  // processor & its running thread are blocked in C for this GC.  
	  // Its stack needs to be scanned, starting from the "top" java frame, which has
	  // been saved in the running threads JNIEnv.  Put the saved frame pointer
	  // into the threads saved context regs, which is where the stack scan starts.
	  //
	  VM_Thread t = vp.activeThread;
	  t.contextRegisters.setInnermost(VM_Address.zero(), t.jniEnv.JNITopJavaFP);
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

  private static void prepareNonParticipatingVPsForAllocation() throws VM_PragmaUninterruptible {
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
  public static void collect () throws VM_PragmaUninterruptible {

    VM_Interface.logGarbageCollection();
 
    // set running threads context regs so that a scan of its stack
    // will start at the caller of collect (ie. VM_CollectorThread.run)
    //
    VM_Address fp = VM_Magic.getFramePointer();
    VM_Address caller_ip = VM_Magic.getReturnAddress(fp);
    VM_Address caller_fp = VM_Magic.getCallerFramePointer(fp);
    VM_Thread.getCurrentThread().contextRegisters.setInnermost(caller_ip, caller_fp);
 
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
      if (VM.VerifyAssertions) VM._assert( initGCDone == false );  
      gcInProgress = true;
      gcDone = false;
      gcCount++;
      if (verbose >= 2) VM.sysWriteln("Starting GC ", gcCount);

      // Set up common workqueue for num VPs participating which varies from GC to GC.
      VM_GCWorkQueue.workQueue.initialSetup(VM_CollectorThread.numCollectors());

      gcInProgress = true;
      gcDone = false;

      // Prepare the other spaces for collection
      bootHeap.startCollect();
      immortalHeap.startCollect();
      largeHeap.startCollect();

      if (VM.ParanoidGCCheck) toHeap.unprotect();

      // DebugHelp.reset();
      
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
      tempStart = VM_CollectorThread.MEASURE_RENDEZVOUS_TIMES ? VM_Time.now() : 0.0;
      while( initGCDone == false ); // spin until initialization finished
      VM_Magic.isync();             // prevent following inst. from moving infront of waitloop
      tempEnd = VM_CollectorThread.MEASURE_RENDEZVOUS_TIMES ? VM_Time.now() : 0.0;

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
    mylocal.rendezvousRecord(tempStart, tempEnd);

    // following rendezvous seems to be necessary, we are not sure why. Without it,
    // some processors proceed into finding roots, before all gc threads have
    // executed the above gc_initProcessor, and this seems related to the failure.
    // 
    mylocal.rendezvous();
         
    // Begin finding roots for this collection.
    // Root refs are updated and the copied object is enqueued for later scanning.
    gc_scanProcessor();                            // each gc threads scans its own processor object
    ScanStatics.scanStatics();                  // all threads scan JTOC in parallel
    VM_CopyingCollectorUtil.scanThreads(fromHeap); // all GC threads process thread objects & scan their stacks

    // This synchronization is necessary to ensure all stacks have been scanned
    // and all internal save ip values have been updated before we scan copied
    // objects.  Because if we scan a VM_Method, and then update its code pointer
    // we can no longer compute old ip offsets for updating saved ip values
    //
    //    REQUIRED SYNCHRONIZATION - WAIT FOR ALL GC THREADS TO REACH HERE
    mylocal.rendezvous();
    if (mylocal.getGCOrdinal() == 1)	scanTime.start(rootTime);

    // each GC thread processes its own work queue until empty
    if (verbose >= 2) VM.sysWriteln("  Emptying work queue");
    gc_emptyWorkQueue();
    mylocal.rendezvous();
    if (mylocal.getGCOrdinal() == 1)	finalizeTime.start(scanTime);

    // Do finalization now.
    if (mylocal.getGCOrdinal() == 1) {
      VM_GCWorkQueue.workQueue.reset();   // reset work queue shared control variables
      VM_Finalizer.moveToFinalizable();
    }
    mylocal.rendezvous();   // the prevents a speedy thread from thinking there is no work
    gc_emptyWorkQueue();
    mylocal.rendezvous();
    if (verbose >= 2) VM.sysWriteln("  Finished finalizer processing");
    if (mylocal.getGCOrdinal() == 1) finishTime.start(finalizeTime);
      
    // gcDone flag has been set to false earlier
    //
    if ( VM_GCLocks.testAndSetFinishLock() ) {

      // BEGIN SINGLE GC THREAD SECTION FOR END OF GC

      // check graph isomorphism
      // DebugHelp.checkLog();

      // make any access to discarded fromspace impossible
      if (VM.ParanoidGCCheck) {
	  VM.sysWriteln("  Protecting fromspace");
	  fromHeap.clobber();
	  fromHeap.protect();
	  toHeap.paranoidScan(fromHeap, false);
      }

      // Swap sense of toHeap and fromHeap
      VM_ContiguousHeap temp = fromHeap;
      fromHeap = toHeap;
      toHeap = temp;

      // toHeap is ready to be used for the next collection
      toHeap.reset();

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
      double start = VM_CollectorThread.MEASURE_RENDEZVOUS_TIMES ? VM_Time.now() : 0.0;
      while(!gcDone) ;
      VM_Magic.isync();           // prevent from moving infront of waitloop
      if (VM_CollectorThread.MEASURE_RENDEZVOUS_TIMES) 
	  mylocal.rendezvousRecord(start, VM_Time.now());
    }

    // ALL GC THREADS IN PARALLEL - AFTER COLLECTION

    if (PROCESSOR_LOCAL_ALLOCATE) {
      if (PROCESSOR_LOCAL_MATURE_ALLOCATE) {
	// have each processor begin allocations in next mutator cycle in what
	// remains of the current chunk acquired for copying objects during GC.
	// NOTE: promoteChunk2 handles zeroing the rest of the chunk if necessary
	VM_Chunk.promoteChunk2(VM_Processor.getCurrentProcessor());
      } else {
	VM_Chunk.resetChunk1(VM_Processor.getCurrentProcessor(), fromHeap, false);
      }
    }

    if (ZERO_NURSERY_IN_PARALLEL) {
      fromHeap.zeroFreeSpaceParallel();
      mylocal.rendezvous();
    }

    // Each GC thread increments adds its wait times for this collection
    // into its total wait time - for printSummaryStatistics output
    //
    if (VM_CollectorThread.MEASURE_WAIT_TIMES)
      mylocal.incrementWaitTimeTotals();
    if (mylocal.getGCOrdinal() == 1) {
	finishTime.stop();
	GCTime.stop(finishTime.lastStop);
    }

    // generate -verbosegc output, done here after zeroing nursery. 
    // only approx. since other gc threads may be zeroing in parallel
    // this is done by the 1 gc thread that finished the preceeding GC
    //
    if (mylocal.getGCOrdinal() == 1) {
	updateGCStats(DEFAULT, fromHeap.current().diff(fromHeap.start).toInt());
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
  static VM_Address gc_getMatureSpace(int size) throws VM_PragmaUninterruptible {
    if (PROCESSOR_LOCAL_MATURE_ALLOCATE) {
      return VM_Chunk.allocateChunk2(size);
    } else {
      VM_Address addr = toHeap.allocateZeroedMemory(size);
      if (addr.isZero()) {
	VM_GCUtil.outOfMemory("toHeap during collection!!!", smallHeapSize, "-X:h=nnn");
      }
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
  static VM_Address copyAndScanObject(VM_Address fromRef, boolean scan) throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) VM._assert(fromHeap.refInHeap(fromRef));
    return VM_CopyingCollectorUtil.copyAndScanObject(fromRef, scan);
  }

  // called by ONE gc/collector thread to copy and "new" thread objects
  // copies but does NOT enqueue for scanning
  //
  private static void gc_copyThreads () throws VM_PragmaUninterruptible {
    
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
     
  
  // initProcessor is called by each GC thread to copy the processor object of the
  // processor it is running on, and reset it processor register, and update its
  // entry in the scheduler processors array and reset its local allocation pointers
  //
  static void gc_initProcessor ()  throws VM_PragmaUninterruptible {

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
  }


  // scan a VM_Processor object to force "interior" objects to be copied, marked,
  // and queued for later scanning. adjusts write barrier pointers, if
  // write buffer is moved.
  //
  static void gc_scanProcessor () throws VM_PragmaUninterruptible {
    VM_Processor   st = VM_Processor.getCurrentProcessor();
    // verify that processor copied out of FromSpace earlier
    if (VM.VerifyAssertions) VM._assert(!fromHeap.refInHeap(VM_Magic.objectAsAddress(st)));

    VM_Address oldbuffer = VM_Magic.objectAsAddress(st.modifiedOldObjects);
    VM_ScanObject.scanObjectOrArray(st);
    // if writebuffer moved, adjust interior pointers
    VM_Address newbuffer = VM_Magic.objectAsAddress(st.modifiedOldObjects);
    if (oldbuffer.NE(newbuffer)) {
      if (verbose >= 2) VM.sysWriteln("  Write buffer copied ",st.id);
      st.modifiedOldObjectsMax = newbuffer.add(st.modifiedOldObjectsMax.diff(oldbuffer));
      st.modifiedOldObjectsTop = newbuffer.add(st.modifiedOldObjectsTop.diff(oldbuffer));
    }
  }
 

  // Process references in work queue buffers until empty.
  //
  static void gc_emptyWorkQueue () throws VM_PragmaUninterruptible {

    VM_Address ref = VM_GCWorkQueue.getFromWorkBuffer();

    if (VM_GCWorkQueue.WORKQUEUE_COUNTS) {
      VM_CollectorThread myThread = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());
      myThread.rootWorkCount = myThread.putWorkCount;
    }
    
    while (!ref.isZero()) {
      VM_ScanObject.scanObjectOrArray( ref );	   
      ref = VM_GCWorkQueue.getFromWorkBuffer();
    }
  }


  static boolean validForwardingPtr (VM_Address ref ) throws VM_PragmaUninterruptible {
      return toHeap.refInHeap(ref);
  }

  /*
   * Initialize a VM_Processor for allocation and collection.
   */
  public static void setupProcessor (VM_Processor p) throws VM_PragmaInterruptible {
    if (PROCESSOR_LOCAL_ALLOCATE) 
      VM_Chunk.resetChunk1(p, fromHeap, false);
    if (writeBarrier)
      VM_WriteBuffer.setupProcessor(p);
  }

  // Given an object in fromSpace, return the live object in to-space.
  // In other spaces, objects don't move so return passed argument.
  //
  public static Object getLiveObject (Object obj) throws VM_PragmaUninterruptible {
    if (fromHeap.refInHeap(VM_Magic.objectAsAddress(obj)))
      return VM_AllocatorHeader.getForwardingPointer(obj);
    return obj;
  }


  // Check if the object address points to a dead or live object.
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
  // Returns true if the object died and needs finalization.
  // In either case, the object has been copied and its new location can be obtained cia getLiveObject()
  //
  public static boolean processFinalizerCandidate (VM_Address ref) throws VM_PragmaUninterruptible {
    if (fromHeap.refInHeap(ref)) {
      Object obj = VM_Magic.addressAsObject(ref);
      if (VM_AllocatorHeader.isForwarded(obj)) {
	return false;  // already live so no finalization
      } else {
	// dead, so copy/scan object and notify caller that it has been revived
	copyAndScanObject(ref, true);
	return true;
      }
    } else {
      if (VM.VerifyAssertions) VM._assert(largeHeap.refInHeap(ref));
      if (largeHeap.isLive(ref)) {
	return false;
      } else {
	largeHeap.mark(ref);
	VM_GCWorkQueue.putToWorkBuffer(ref);
	return true;
      }
    }
  } 
  
  // Called from WriteBuffer code for generational collectors.
  // Argument is a modified old object which needs to be scanned
  //
  static void processWriteBufferEntry (VM_Address ref) throws VM_PragmaUninterruptible {
    VM_ScanObject.scanObjectOrArray(ref);
  }
  
  /**
   * Process an object reference field during collection.
   * Called from GC utility classes like VM_ScanStack.
   *
   * @param location  address of a reference field
   */
  public static void processPtrField (VM_Address location) throws VM_PragmaUninterruptible {
    VM_Magic.setMemoryAddress(location, processPtrValue(VM_Magic.getMemoryAddress(location)));
  }

  /**
   * Process an object reference (value) during collection.
   * Called from GC utility classes like ScanStack.
   *
   * @param location  address of a reference field
   */
  public static VM_Address processPtrValue (VM_Address ref ) throws VM_PragmaUninterruptible {
  
    if (ref.isZero()) return ref;
  
    // in FromSpace, if not marked, mark, copy & queue for scanning
    if (fromHeap.refInHeap(ref)) {
      return copyAndScanObject(ref, true);
    }

    if (bootHeap.refInHeap(ref)) {
      if (bootHeap.mark(ref)) VM_GCWorkQueue.putToWorkBuffer(ref);
      return ref;
    }

    if (immortalHeap.refInHeap(ref)) {
      if (immortalHeap.mark(ref)) VM_GCWorkQueue.putToWorkBuffer(ref);
      return ref;
    }

    if (largeHeap.refInHeap(ref)) {
      if (largeHeap.mark(ref)) VM_GCWorkQueue.putToWorkBuffer(ref);
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
  static Object[] newTIB (int n) throws VM_PragmaUninterruptible {
    if (VM.runningVM) {
      Object[] newtib = (Object[])immortalHeap.allocateAlignedArray(VM_Type.JavaLangObjectArrayType, n, VM_JavaHeader.TIB_ALIGNMENT);
      if (VM.VerifyAssertions) VM._assert((VM_Magic.objectAsAddress(newtib).toInt() & (VM_JavaHeader.TIB_ALIGNMENT-1)) == 0);
      return newtib;
    } else {
      return new Object[n];
    }
  }
  //-#endif

}   // VM_Allocator
