/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * Copying Generational Collector/Allocator with Variable Size Nursery.
 * <p>
 * This is a variation of the generational collector described by Andrew Appel
 * in "Simple Generational Garbage Collection" (include reference)
 * <p>
 * Uses a writebarrier which puts references to objects, which had internal references
 * modified, into processor local writebuffers.  For minor collections, objects in
 * the writebuffers become part of the root set for the collection.
 * (The RVM compilers generate the barrier code when the static final
 * constant "writeBarrier" is set to true.)
 * <p>
 *  Small Object Heap Layout (with mature space on left):
 * <pre>
 *  +-------------+  +----------------+---------------+--------------+
 *  | BootImage   |  | MatureSpace    |   RESERVED    | Nursery      | 
 *  +-------------+  +----------------+---------------+--------------+
 *          heapStart^             heapMiddle^                heapEnd^ 
 *
 * During Minor Collections:
 *  - set "FromSpace" to be the current Nursery
 *  - set roots = references in thread stacks, static variables & write buffers
 *  - copy all objects in FromSpace (Nursery) reachable from roots to mature space
 *  - divide space not in mature space in half, half being the next Nursery
 *    and half reserved for future mature objects
 * </pre>
 * If after a minor collection, mature space crosses the mid-point of the heap
 * then immediately perform a major collection.
 * <pre>
 * During Major Collections:
 *  - set "FromSpace" to be the current Mature Space
 *  - set MatureSpace to start from the other end of the heap, and reverse
 *    the direction of allocation
 *  - set roots = references in thread stacks, static variables (NOT write buffers)
 *  - copy all objects in FromSpace (old Mature Space) reachable from roots to mature space
 *  - divide space not in mature space in half, half being the next Nursery
 *    and half reserved for future mature objects
 * </pre>
 * The heap layout and the direction of allocation reverses after each Major Collection.
 *
 * @see VM_WriteBarrier
 * @see VM_WriteBuffer
 * @see VM_GCWorkQueue
 *
 * @author Stephen Smith
 *
 * @modified by Perry Cheng  Heavily re-written to factor out common code and add VM_Address
 */
public class VM_Allocator
  implements VM_Constants, VM_GCConstants, VM_Uninterruptible, VM_Callbacks.ExitMonitor {
  
  /**
   * When true (the default), VM_Processors acquire chunks of space from
   * the shared Small Object Heap, and then allocate from within their
   * local chunks.
   */
  static final boolean PROCESSOR_LOCAL_ALLOCATE = true; 
  
  /**
   * When true (the default), Collector Threads acquire chunks of space
   * from ToSpace during collection, and allocate space for copying
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
   * Instead, each VM_Processor zeros the chunks of heap it acquires inorder
   * to satisfy allocation requests.
   */
  static final boolean ZERO_BLOCKS_ON_ALLOCATION = true;


  static int verbose = 0;  // since this is NOT final, make sure it is not being checked too often
  
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
  static final boolean RENDEZVOUS_WAIT_TIME = VM_CollectorThread.MEASURE_WAIT_TIMES;

  /**
   * When true, causes -verbosegc option to print per thread entry
   * and exit times for the first 3 rendezvous during collection.
   */
  static final boolean RENDEZVOUS_TIMES = false;

  /** count times parallel GC threads attempt to mark the same object */
  private static final boolean COUNT_COLLISIONS = false;

  /**
   * Initialize for boot image.
   */
  static void init () {
    
    VM_GCLocks.init();          // to alloc lock fields used during GC (in bootImage)
    VM_GCWorkQueue.init();      // to alloc shared work queue      
    VM_CollectorThread.init();  // to alloc its rendezvous arrays, if necessary
    
  }
  
  /**
   * Initialize for execution.
   */
  static void boot (VM_BootRecord thebootrecord) {
    
    bootrecord = thebootrecord;	
    verbose = bootrecord.verboseGC;

    int appelSize = 2 * VM_Memory.roundUpPage(bootrecord.smallSpaceSize / 2);
    int largeSize = bootrecord.largeSpaceSize;
    int immortalSize = VM_Memory.roundUpPage((4 * largeSize / VM_Memory.getPagesize()) + 4 * VM_Memory.getPagesize());

    VM_Heap.boot(bootHeap, bootrecord);
    immortalHeap.attach(immortalSize);
    largeHeap.attach(largeSize);
    appelHeap.attach(appelSize);

    appelMiddleAddress = appelHeap.start.add(appelHeap.size / 2);
    
    // major collections are initiated when mature space get within 
    // some "delta" of the middle of the heap.
    // TODO: dynamically set this based on percentage of nursery being kept live
    majorCollectionDelta = appelHeap.size / 8;
    
    // still refer to region available for allocations as "FromSpace"
    // For first cycle FromSpace starts at the middle and extends to heapEnd  
    fromStartAddress = appelMiddleAddress; 
    fromEndAddress = appelHeap.end;
    
    // initialize pointers used for allocation
    areaCurrentAddress = fromStartAddress;
    areaEndAddress     = fromEndAddress;
    
    // no mature objects, start mature space at beginning of heap
    matureCurrentAddress = appelHeap.start;
    
    // need address of areaCurrentAddress (in JTOC) for atomic fetchAndAdd()
    // when JTOC moves, this must be reset
    // offset of areaCurrentAddress in JTOC is set (in JDK side) in VM_EntryPoints 
    // likewise for matureCurrentAddress
    addrAreaCurrentAddress = VM_Magic.getTocPointer().add(VM_Entrypoints.areaCurrentAddressField.getOffset());
    addrMatureCurrentAddress = VM_Magic.getTocPointer().add(VM_Entrypoints.matureCurrentAddressField.getOffset());
    

    if (COMPILE_FOR_TIMING_RUN) {
	largeHeap.touchPages();
	appelHeap.touchPages();
    }

    VM_GCUtil.boot();

    VM_Finalizer.setup();
    
    VM_Callbacks.addExitMonitor(new VM_Allocator());

    if (verbose >= 1) showParameter();
  }  // boot
    
  static void showParameter() {
      VM.sysWrite("\n");
      VM.sysWrite("Generational Copying Collector with variable sized Nursery:\n");
      bootHeap.show(); 
      immortalHeap.show(); 
      appelHeap.show(); 
      largeHeap.show(); 

      if (ZERO_BLOCKS_ON_ALLOCATION)       VM.sysWriteln("  Compiled with ZERO_BLOCKS_ON_ALLOCATION on ");
      if (ZERO_NURSERY_IN_PARALLEL)        VM.sysWriteln("  Compiled with ZERO_NURSERY_IN_PARALLEL on ");
      if (PROCESSOR_LOCAL_ALLOCATE)        VM.sysWriteln("  Compiled with PROCESSOR_LOCAL_ALLOCATE on ");
      if (PROCESSOR_LOCAL_MATURE_ALLOCATE) VM.sysWriteln("  Compiled with PROCESSOR_LOCAL_MATURE_ALLOCATE on");
  }

  /**
   * To be called when the VM is about to exit.
   * @param value the exit value
   */
  public void notifyExit(int value) {
    printSummaryStatistics();
  }
  
  /**
   * Force a garbage collection. Supports System.gc() called from
   * application programs.
   */
  public static void gc () {
    forceMajorCollection = true;    // to force a major collection
    gc1("GC triggered by external call to gc()", 0);
  }
  
  /**
   * VM internal method to initiate a collection
   */
  static void  gc1 (String why, int size) {
    // if here and in a GC thread doing GC then it is a system error,
    //  GC thread must have attempted to allocate.
    if (verbose >= 1) VM.sysWrite(why, size);
    if ( VM_Thread.getCurrentThread().isGCThread ) {
      VM.sysFail("VM_Allocator: Garbage Collection Failure: GC Thread attempting to allocate during GC");
      //      crash("VM_Allocator: Garbage Collection Failure: GC Thread asking for GC");
    }
    
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
  public static long totalMemory() {
    return appelHeap.size + largeHeap.size;
  }
  
  /**
   * Get the number of bytes currently available for object allocation.
   * In this collector, returns bytes available in the current semi-space,
   * and does not include large object space available.
   *
   * @return number of bytes available
   */
  public static long freeMemory () {
      return areaEndAddress.diff(areaCurrentAddress) +
	     (matureAllocationIncreasing ? 
	       (appelMiddleAddress.diff(matureCurrentAddress)) :
	      (matureCurrentAddress.diff(appelMiddleAddress)));
  }

  /**
   * Print OutOfMemoryError message and exit.
   * TODO: make it possible to throw an exception, but this will have
   * to be done without doing further allocations (or by using temp space)
   */
  private static void outOfMemory (String why) {

    // First thread to be out of memory will write out the message,
    // and issue the shutdown. Others just spinwait until the end.

    lock.lock();
    if (!outOfMemoryReported) {
      outOfMemoryReported = true;
      VM_Processor.getCurrentProcessor().disableThreadSwitching();
      VM.sysWriteln("\nOutOfMemoryError: ", why);
      VM.sysWrite("Insufficient heap size for Generational (Variable Nursery) Collector\n");
      VM.sysWrite("Current heap size = ");
      VM.sysWrite(appelHeap.size, false);
      VM.sysWrite("\nSpecify a larger heap using -X:h=nnn command line argument\n");
      // call shutdown while holding the processor lock
      VM.shutdown(-5);
    }
    else {
      lock.release();
      while( outOfMemoryReported == true );  // spin until VM shuts down
    }
  }

  /**
   * Get space for a new object or array. If compiled to allocate from processor
   * local chunks, it will attempt to allocate from the local chunk first, and if 
   * insufficient space in the current chunk, acquires another chunk from the
   * shared small object heap. If not compiled for processor local allocations
   * all allocations (of "small" objects) are from the shared heap.
   *
   * If the size is greater than SMALL_SPACE_MAX, then space is allocated
   * from the shared large object heap.
   * 
   * A collection is initiated if space of the requested size is not available.
   * After the collection, it will retry acquiring the space, and if it fails
   * again, the system will exit with an "OUT OF MEMORY" error message.
   * (A TODO is to throw a proper java Exception)
   *
   * @param size         the number of bytes needed
   *
   * @return the address of the first byte of the allocated region
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

  public static VM_Address getHeapSpaceSlow (int size) {

    if (VM.VerifyAssertions) {
	VM_Thread t = VM_Thread.getCurrentThread();
	VM.assert( gcInProgress == false );
	VM.assert( (t.disallowAllocationsByThisThread == false)
		   && ((size & 3) == 0) );
    }

    VM_Address addr;
    
    // if large, allocate from large object space
    if (size > SMALL_SPACE_MAX) {
      addr = largeHeap.allocate(size);
      if (addr.isZero()) {
	forceMajorCollection = true;  // force a major collection to reclaim more large space
	gc1("GC triggered by large object request",size);
	addr = largeHeap.allocate(size);
	if (addr.isZero()) {
	  // out of space...REALLY...or maybe NOT ?
	  // maybe other user threads got the free space first, after the GC
	  //
	    largeHeap.outOfMemory( size );
	}
      }
      return addr;
    }  // end of - (size > SMALL_SPACE_MAX)

    // now handle normal allocation of small objects in heap
    
    if (PROCESSOR_LOCAL_ALLOCATE) {
      VM_Processor st = VM_Processor.getCurrentProcessor();
      if ( st.localCurrentAddress.add(size).LE(st.localEndAddress) ) {
	addr = st.localCurrentAddress;
	st.localCurrentAddress = st.localCurrentAddress.add(size);
      }
      else { // not enough space in local chunk, get the next chunk for allocation
	addr = VM_Synchronization.fetchAndAddAddressWithBound(addrAreaCurrentAddress, 
							      CHUNK_SIZE, areaEndAddress);
	if ( !addr.isMax() ) {
	  st.localEndAddress = addr.add(CHUNK_SIZE);
	  st.localCurrentAddress = addr.add(size);
	  if (ZERO_BLOCKS_ON_ALLOCATION)
	    VM_Memory.zeroPages(addr,CHUNK_SIZE);
	}
	else { // no space in system thread and no more chunks, do garbage collection
	  gc1("GC triggered by request for small space CHUNK", size);
	  // retry request for space
	  // NOTE! may now be running on a DIFFERENT SYSTEM THREAD than before GC
	  //
	  st = VM_Processor.getCurrentProcessor();
	  if ( st.localCurrentAddress.add(size).LE(st.localEndAddress) ) {
	    addr = st.localCurrentAddress;
	    st.localCurrentAddress = st.localCurrentAddress.add(size);
	  }
	  else {
	    // not enough space in local chunk, get the next chunk for allocation
	    //
	    addr = VM_Synchronization.fetchAndAddAddressWithBound(addrAreaCurrentAddress, 
								  CHUNK_SIZE, areaEndAddress );
	    if ( !addr.isMax() ) {
	      st.localEndAddress = addr.add(CHUNK_SIZE);
	      st.localCurrentAddress = addr.add(size);
	      if (ZERO_BLOCKS_ON_ALLOCATION)
		VM_Memory.zeroPages(addr,CHUNK_SIZE);
	    }
	    else {
	       // Unable to get chunk, after GC. Maybe should retry GC again, some
	       // number of times. For now, call outOfMemory to print message and exit
	       //
	       outOfMemory("Cannot satisfy allocation after GC");
	    } 
	  }
	}  // else do gc
      }  // else get new chunk from global heap
    }
    else { // OLD CODE - all allocates from global heap 
      
      addr = VM_Synchronization.fetchAndAddAddressWithBound(addrAreaCurrentAddress, 
							    size, areaEndAddress );
      if ( addr.isMax() ) {
	// do garbage collection, check if get space for object
	gc1("GC triggered by small object request", size);
	addr = VM_Synchronization.fetchAndAddAddressWithBound(addrAreaCurrentAddress, size, areaEndAddress );
	if ( addr.isMax() ) {
  	   // out of space...REALLY
  	   // BUT, maybe other user threads got the free space first, after the GC
	   // For now, just give up, call outOfMemory to print message and exit
	   //
	   outOfMemory("Cannoy satisfy small request after GC");
	}
      }
    }  // end of ! PROCESSOR_LOCAL_ALLOCATE (OLD CODE)
    
    // addr -> beginning of allocated region
    
    // if from space was filled with strange bits, then must zero now
    // UNLESS we are allocating blocks to processors, and those block are
    // being zeroed when allocated 
    if (! ZERO_BLOCKS_ON_ALLOCATION)
      VM_Memory.zero(addr, addr.add(size));
    
    return addr;
  }  // getHeapSpaceSLow
  
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

    VM_Magic.pragmaInline();	// make sure this method is inlined
    
    VM_Address region = getHeapSpaceFast(size);
    Object newObj = VM_ObjectModel.initializeScalar(region, tib, size);
    if (size >= SMALL_SPACE_MAX) resetObjectBarrier(newObj);
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
    
    VM_Magic.pragmaInline();	// make sure this method is inlined

    // note: array size might not be a word multiple,
    //       must preserve alignment of future allocations
    size = VM_Memory.align(size, WORDSIZE);

    VM_Address region = getHeapSpaceFast(size);
    Object newObj = VM_ObjectModel.initializeArray(region, tib, numElements, size);
    if (size >= SMALL_SPACE_MAX) resetObjectBarrier(newObj);
    return newObj;
  }
  
  // *************************************
  // implementation
  // *************************************
  
  static final int      TYPE = 5;
  
  /** Declares that this collector may move objects during collction */
  static final boolean movesObjects = true;
  
  /** Declares that this collector requires that compilers generate the write barrier */
  static final boolean writeBarrier = true;
  
  // VM_Type of int[], to detect arrays that (may) contain code
  // and will thus require a d-cache flush before the code is executed.
  static VM_Type arrayOfIntType;
  
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
  
  private final static int     CRASH_BUFFER_SIZE = 1024 * 1024;  // size of buf to get before sysFail
  
  private static boolean forceMajorCollection = false; // forces major collection after a minor
  
  private static boolean outOfMemoryReported = false;
  private static boolean majorCollection = false;
  private static volatile boolean initGCDone = false;
  private static volatile boolean minorGCDone = false;
  private static volatile boolean majorGCDone = false;
  private static boolean matureAllocationIncreasing = true;
  
  // Various heaps
  private static VM_Heap bootHeap = new VM_Heap("Boot Image Heap");   
  private static VM_Heap appelHeap = new VM_Heap("Appel-style Heap");
  private static VM_ImmortalHeap immortalHeap = new VM_ImmortalHeap();
  private static VM_LargeHeap largeHeap = new VM_LargeHeap(immortalHeap);

  private static VM_ProcessorLock lock  = new VM_ProcessorLock();      // for signalling out of memory

  static VM_BootRecord	 bootrecord;

  static VM_Address areaCurrentAddress;
  static VM_Address addrAreaCurrentAddress;
  static VM_Address areaEndAddress;

  private static VM_Address appelMiddleAddress;
  private static int majorCollectionDelta;  
  private static VM_Address fromStartAddress;
  private static VM_Address fromEndAddress;
  private static VM_Address minFromRef;
  private static VM_Address maxFromRef;
  private static int nurserySize;             // varies - half of remaining space
  private static VM_Address matureCurrentAddress;    // current end of mature space
  private static VM_Address addrMatureCurrentAddress;    // address of above (in the JTOC)
  private static VM_Address matureSaveAddress;       // end of mature space at beginning of major GC
  private static VM_Address matureBeforeMinor;       // ...for debugging to see closeness to appelMiddleAddress  
  
  static boolean gcInProgress;      // true if collection in progress, initially false
  static int gcCount = 0;           // number of minor collections
  static int gcMajorCount = 0;      // number of major collections
  
  private static double gcStartTime = 0;
  private static double gcEndTime = 0;
  private static double gcTimeBeforeZeroing = 0;
  private static double gcMinorTime;             // for timing gc times
  private static double gcMajorTime;             // for timing gc times
  private static double gcTotalTime = 0;         // for timing gc times

  static double maxMajorTime = 0.0;         // for timing gc times
  static double maxMinorTime = 0.0;         // for timing gc times

  private static double totalStartTime = 0.0;    // accumulated stopping time
  private static double totalMinorTime = 0.0;    // accumulated minor gc time
  private static double totalMajorTime = 0.0;    // accumulated major gc time
  private static long   totalMajorBytesCopied = 0;    // accumulated major gc bytes copied
  private static long   maxMajorBytesCopied = 0;      // max major gc bytes copied
  private static long   totalMinorBytesCopied = 0;    // accumulated minor gc bytes copied
  private static int    maxMinorBytesCopied = 0;      // max minor gc bytes copied
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
  
  static final boolean debugNative = false;
  private static final boolean GCDEBUG = false;
  private static final boolean GCDEBUG_SCANTHREADS = false;
  private static final boolean TRACE_STACKS = false;
  private static final boolean GCDEBUG_CHECKWB = false;   // checks for writebuffer entries during GC
  
  // FromSpace object are "marked" if mark bit in statusword == MARK_VALUE
  // if "marked" == 0, then storing aa aligned forwarding ptr also "marks" the
  // original FromSpace copy of the object (ie. GC's go faster
  // if "marked" == 1, then allocation of new objects do not require a store
  // to set the markbit on (unmarked), but returning a forwarding ptr requires
  // masking out the mark bit ( ie allocation faster, GC slower )
  //
  // This collector only supports MARK_VALUE = 1. The semi-space
  // collector supports optional builds where MARK_VALUE = 0.
  //
  static final int MARK_VALUE = 1;        // DO NOT CHANGE !!
  
  private static int BOOT_MARK_VALUE = 0;   // to mark bootimage objects during major GCs
  
  // ------- End of Statics --------
  
  static void gcSetup ( int numSysThreads ) {
    VM_GCWorkQueue.workQueue.initialSetup(numSysThreads);
  }
  
  private static void prepareNonParticipatingVPsForGC() {
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
	  t.contextRegisters.setInnermost( VM_Address.zero(), t.jniEnv.JNITopJavaFP );
	}

	if (PROCESSOR_LOCAL_MATURE_ALLOCATE) {
	  vp.localMatureCurrentAddress = VM_Address.zero();
	  vp.localMatureEndAddress = VM_Address.zero();
	}

	// move the processors writebuffer entries into the executing collector
	// threads work buffers so the referenced objects will be scanned.
	VM_WriteBuffer.moveToWorkQueue(vp);
	}
      }
  
    // in case (actually doubtful) native processors have writebuffer
    // entries, move them also.
    for (int i = 1; i <= VM_Processor.numberNativeProcessors; i++) {
      VM_Processor vp = VM_Processor.nativeProcessors[i];
      VM_WriteBuffer.moveToWorkQueue(vp);
      // check that native processors have not done allocations
      if (VM.VerifyAssertions) {
	if (!vp.localCurrentAddress.isZero()) {
	  VM_Scheduler.trace("prepareNonParticipatingVPsForGC:",
			     "native processor with non-zero allocation ptr, id =",vp.id);
	  vp.dumpProcessorState();
	  VM.assert(vp.localCurrentAddress.isZero());
	}
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

    int       i,temp,bytes;
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
    
    if (verbose >= 2) VM_Scheduler.trace("VM_Allocator","in collect starting GC");
    
    // BEGIN SINGLE THREAD SECTION - GC INITIALIZATION
    
    double tempStart = 0.0, tempEnd = 0.0;

    if ( VM_GCLocks.testAndSetInitLock() ) {
      
      gcStartTime = VM_Time.now();         // start time for GC
      totalStartTime += gcStartTime - VM_CollectorThread.gcBarrier.rendezvousStartTime; //time since GC requested
      
      if (VM.VerifyAssertions) VM.assert( initGCDone == false );  
      
      gcCount++;

      // setup common workqueue for num VPs participating, used to be called once.
      // now count varies for each GC, so call for each GC   SES 050201
      //
      VM_GCWorkQueue.workQueue.initialSetup(VM_CollectorThread.numCollectors());
      
      // VM_GCWorkQueue.workQueue.reset(); // do initialsetup instead 050201
      
      if (verbose >= 2)
	VM_Scheduler.trace("VM_Allocator", "gc initialization for gcCount", gcCount);
      
      gcInProgress = true;
      majorCollection = false;
      minorGCDone = false;
      majorGCDone = false;
      
      minFromRef = VM_ObjectModel.minimumObjectRef(fromStartAddress);
      maxFromRef = VM_ObjectModel.maximumObjectRef(fromEndAddress);
      
      // remember current end of mature space
      matureSaveAddress = matureCurrentAddress;
      matureBeforeMinor = matureCurrentAddress;
  
      // Now initialize the large object space mark array
      if (verbose >= 2) VM.sysWriteln("Preparing large space");
      largeHeap.startCollect();
      
      // this gc thread copies own VM_Processor, resets processor register & processor
      // local allocation pointers (before copying first object to ToSpace)
      if (verbose >= 2) VM.sysWriteln("Copying VM_Processor's");
      gc_initProcessor();

      // with the default jni implementation some RVM VM_Processors may
      // be blocked in native C and not participating in a collection.
      prepareNonParticipatingVPsForGC();
      
      // precopy new VM_Thread objects, updating schedulers threads array
      // here done by one thread. could divide among multiple collector threads
      if (verbose >= 2) VM.sysWriteln("Copying VM_Thread's");
      gc_copyThreads();
      
      VM_GCLocks.resetFinishLock();  // for singlethread'ing end of minor collections
      
      // must sync memory changes so GC threads on other processors see above changes
      // sync before setting initGCDone flag to allow other GC threads to proceed
      VM_Magic.sync();

      if (TIME_GC_PHASES)  gcInitDoneTime = VM_Time.now();
      
      // set Done flag to allow other GC threads to begin processing
      initGCDone = true;
      
    } // END SINGLE GC THREAD SECTION - GC INITIALIZATION
    
    else {
      // Each GC thread must wait here until initialization is complete
      // this should be short, if necessary at all, so we spin instead of sysYiel
      //
      // It is NOT required that all GC threads reach here before any can proceed
      //
      tempStart = RENDEZVOUS_WAIT_TIME ? VM_Time.now() : 0.0;
      while( initGCDone == false ); // spin until initialization finished
      VM_Magic.isync();             // prevent following inst. from moving infront of waitloop
      tempEnd = RENDEZVOUS_WAIT_TIME ? VM_Time.now() : 0.0;
	    
      // each gc thread copies own VM_Processor, resets processor register & processor
      // local allocation pointers
      gc_initProcessor();
    }
    
    // ALL GC THREADS IN PARALLEL...
    
    // each GC threads acquires ptr to its thread object, for accessing thread local counters
    // and workqueue pointers.  If the thread object needs to be moved, it has been, in copyThreads
    // above, and its ref in the threads array (in copyThreads) and the activeThread field of the
    // current processors VM_Processor (in initProcessor) have been updated  This means using either
    // of those fields to get "currentThread" get the copied thread object.
    //
    VM_CollectorThread mylocal = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());
    if (RENDEZVOUS_WAIT_TIME) 
	mylocal.rendezvousWaitTime += VM_CollectorThread.gcBarrier.rendezvousRecord(tempStart, tempEnd);

    // following seems to be necessary when PROCESSOR_LOCAL_MATURE_ALLOCATE==true
    // not sure why, otherwise fail in assertion in yield done by collectorthread.run()
    // maybe processor object is moved while enableSwitching count is being modified???
    //
    mylocal.rendezvousWaitTime += VM_CollectorThread.gcBarrier.rendezvous(RENDEZVOUS_TIMES || RENDEZVOUS_WAIT_TIME);
    
    // Begin finding roots for this collection.
    // Roots are object refs in static variables (JTOC) or on thread stacks 
    // that point into FromSpace & references in the write buffers (they contain
    // references for old objects modified during the last mutator cycle).
    // For each unmarked root object, it is marked, copied to mature space if currently in
    // FromSpace, and added to GC thread local work queue for later scanning.
    
    // scan VM_Processor object, causing referenced objects to be copied. 
    // Early in the implementation, write buffers associated with each
    // VM_Processor (arrays of ints) might be copied, and special code 
    // was required to update interior pointers into these buffers.
    // This is not the case at the current time because the write buffer
    // is large enough to be in non-moving large object space.
    //
    if (verbose >= 2) VM.sysWriteln("Scanning Processors");
    gc_scanProcessor();  // each gc threads scans its own processor object

    if (verbose >= 2) VM.sysWriteln("Scanning statics");
    VM_ScanStatics.scanStatics();     // GC threads scan JTOC in parallel

    if (verbose >= 2) VM.sysWriteln("Scanning Threads");
    gc_scanThreads();    // GC threads process thread objects & scan their stacks
    
    // This synchronization is necessary to ensure all stacks have been scanned
    // and all internal save ip values have been updated before we scan copied
    // objects.  Because if we scan a VM_Method, and then update its code pointer
    // we can no longer compute old ip offsets for updating saved ip values
    //
    // there may be a less expensive way to know that all GC threads have completed
    // processing of stacks...maybe just a counter
    //
    //    REQUIRED SYNCHRONIZATION - WAIT FOR ALL GC THREADS TO REACH HERE

    mylocal.rendezvousWaitTime += VM_CollectorThread.gcBarrier.rendezvous(RENDEZVOUS_TIMES || RENDEZVOUS_WAIT_TIME);
    
    // ALL GC THREADS IN PARALLEL
    
    if (TIME_GC_PHASES && (mylocal.gcOrdinal == 1))
      gcStacksAndStaticsDoneTime = VM_Time.now();  // for time scanning stacks & statics
    
    if (verbose >= 2) VM.sysWriteln("Processing write buffers");
    gc_processWriteBuffers();  // each GC thread processes its own writeBuffers
    
    if (verbose >= 2) VM.sysWriteln("Emptying Work Queue");
    gc_emptyWorkQueue();  // each GC thread processes its own work queue buffers
    
    // have processor 1 record timestame for end of scan/mark/copy phase
    if (TIME_GC_PHASES && (mylocal.gcOrdinal == 1))
      gcScanningDoneTime = VM_Time.now();

    if (GCDEBUG_CHECKWB)
      // all write buffers were reset to empty earlier, check that still empty
      gc_checkWriteBuffers();

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
	// reset shared work queue - wait for all threads to leave previous emptyWorkQueue
	VM_GCWorkQueue.workQueue.reset();
	
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
      mylocal.rendezvousWaitTime += VM_CollectorThread.gcBarrier.rendezvous(RENDEZVOUS_TIMES || RENDEZVOUS_WAIT_TIME);

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
    
    //
    // minorGCDone flag has been set to false earlier, during initialization
    //
    if ( VM_GCLocks.testAndSetFinishLock() ) {
      
      // BEGIN SINGLE GC THREAD SECTION - MINOR END

      // set ending times for preceeding finalization or scanning phase
      // do here where a sync (below) will push value to memory
      if (TIME_GC_PHASES)  gcFinalizeDoneTime = VM_Time.now();
      
      if (verbose >= 2) VM_Scheduler.trace("VM_Allocator", "finishing minor collection");
      
      // If GC was initiated by an outside call to gc(), then forceMajorCollection was set
      // to cause us here to do a major collection.
      if (forceMajorCollection) {
	majorCollection = true;
	forceMajorCollection = false;   // must reset sometime before starting mutators
      }
      else {
	// if mature space is too close or beyond mid point decide to do major collection
	// get smarter here...
	//
	if (matureAllocationIncreasing) {
	  if (matureCurrentAddress.GE(appelMiddleAddress.sub(majorCollectionDelta)) )
	    majorCollection = true;
	}
	else {  // matureAllocationDecreasing
	  if ( matureCurrentAddress.LT(appelMiddleAddress.add(majorCollectionDelta)) )
	    majorCollection = true;
	}
      }
      
      if (majorCollection) {   // decided major collection necessary
	
	// must do major collection before starting mutators
	// Single GC thread running here does setup for a major collection
	// before letting other GC threads proceed.
	
	// NOTE: even when we have write barriers and remembered sets to use for minor
	// collections, this major collection requires a full scan of all live objects
	// starting from roots
	
	gcMajorCount++;
	
	if (verbose >= 2) VM_Scheduler.trace("VM_Allocator","initialize for MAJOR collection",gcMajorCount);

	gcEndTime = VM_Time.now();
	gcMinorTime = gcEndTime - gcStartTime;
	gcTotalTime = gcTotalTime + gcMinorTime;
	totalMinorTime += gcMinorTime;
	if (gcMinorTime > maxMinorTime) maxMinorTime = gcMinorTime;
	if (matureAllocationIncreasing)
	  bytes = matureCurrentAddress.diff(matureSaveAddress);
	else
	  bytes = matureSaveAddress.diff(matureCurrentAddress);
	if (bytes > maxMinorBytesCopied) maxMinorBytesCopied = bytes;
	totalMinorBytesCopied += bytes;

	// print -verbose output
	  
	if (verbose >= 1) printVerboseOutputLine( 2 /*MINOR before MAJOR*/ );

	// add current GC phase times into totals, print if verbose on
	if (TIME_GC_PHASES) accumulateGCPhaseTimes( false );  	

	if (verbose >= 1) printWaitTimesAndCounts();

	// reset gcStartTime timestamp for measuring major collection times
	gcStartTime = VM_Time.now();
	
	// remember current end of matureHeap.  This point has crossed into the opposite 
	// side of the heap.  Live mature objects will be copied to that side, growing towards
	// the middle, and this save point, and we must check that the we do not reach this
	// point when allocating mature space.
	matureSaveAddress = matureCurrentAddress;
	
	// setup regions for major collection, set bounds of FromSpace to identify the current
	// mature region including the objects just copied during the just finished minor
	// collection.  The major collection will copy the live mature objects to the opposite
	// end of the heap, and switch "matureAllocation direction" to go in the opposite
	// direction, again growing towards the middle of the heap.
	
	if ( matureAllocationIncreasing ) {
	  // set bounds of FromSpace to point to the mature semi-space to be collected.  
	  minFromRef = VM_ObjectModel.minimumObjectRef(appelHeap.start);
	  maxFromRef = VM_ObjectModel.maximumObjectRef(matureCurrentAddress);
	  
	  // set mature space to the (now empty) end of the heap
	  matureCurrentAddress = appelHeap.end;
	  
	  matureAllocationIncreasing = false;
	} else {
	  // set bounds of FromSpace to point to the mature semi-space to be collected.  
	  minFromRef = VM_ObjectModel.minimumObjectRef(matureCurrentAddress);
	  maxFromRef = VM_ObjectModel.maximumObjectRef(appelHeap.end);
	  
	  // set mature space to the (now empty) beginning of the heap
	  matureCurrentAddress = appelHeap.start;
	  
	  matureAllocationIncreasing = true;
	}
	
	VM_GCWorkQueue.workQueue.reset(); // reset shared common work queue shared data
	
	// during major collections we do a full mark-sweep, and mark and scan live
	// bootImage objects. invert sense of mark flag in boot objects so that the
	// objects marked during the last major collection now appear "unmarked"
	
	BOOT_MARK_VALUE = BOOT_MARK_VALUE ^ VM_AllocatorHeader.GC_MARK_BIT_MASK; 
	
	// Now initialize the large object space mark array
	if (verbose >= 2) VM_Scheduler.trace("VM_Allocator", "preparing large space",gcMajorCount);
	largeHeap.startCollect();
	
	// this gc thread copies own VM_Processor, resets processor register & processor
	// local allocation pointers (before copying first object to ToSpace)
	gc_initProcessor();
	
	// precopy VM_Thread objects, updating schedulers threads array
	// here done by one thread. could divide among multiple collector threads
	gc_copyThreads();
	
	// reset locks so they can be used for synchronization during Major GC
	// ...except the lockword protecting this section, the "FinishLock"
	VM_GCLocks.reset();
	
	if (TIME_GC_PHASES) gcInitDoneTime = VM_Time.now();
	
      }  // End of setup for Major GC
      
      else {
	// Major GC not needed, GC DONE, reset allocation pointers etc 
	gc_finish();
	
	selectedGCThread = true;  // have this thread generate verbose output below,
	// after nursery has been zeroed
      }
      
      // must sync memory changes so GC threads on other processors see above changes
      VM_Magic.sync();
      
      minorGCDone = true;  // lets spinning GC threads continue
      
      // must sync memory changes so GC threads on other processors see minorGCDone = true
      VM_Magic.sync();     // Al says we dont need this one, need one above XXX
      
    }  // END OF SINGLE THREAD SECTION
    
    else {
      // other GC threads spin until above is complete & majorCollection flag set
      while( minorGCDone == false );   // spin till above section finished
      VM_Magic.isync();    // prevent following inst. from moving infront of waitloop
      
      if ( majorCollection ) {
	// each gc thread copies own VM_Processor, resets processor register & processor
	// local allocation pointers
	gc_initProcessor();
      }
    }
    
    // All GC THREADS IN PARALLEL
    
    // if major GC not need, then finished, all GC threads return
    if ( !majorCollection ) {
      
      // each gc thread/processor zeros memory range assigned in finish()
      if (ZERO_NURSERY_IN_PARALLEL) {
	  appelHeap.zeroParallel(areaCurrentAddress,areaEndAddress);
      }
      
      // generate -verbosegc output.
      // this is done by the 1 gc thread that finished the preceeding GC
      //
      if ( selectedGCThread ) {

	// get time spent in minor GC (including time to zero nursery, if done)
	gcEndTime = VM_Time.now();
	gcMinorTime = gcEndTime - gcStartTime;
	gcTotalTime = gcTotalTime + gcMinorTime;
	totalMinorTime += gcMinorTime;
	if (gcMinorTime > maxMinorTime) maxMinorTime = gcMinorTime;
	if (matureAllocationIncreasing)
	  bytes = matureCurrentAddress.diff(matureSaveAddress);
	else
	  bytes = matureSaveAddress.diff(matureCurrentAddress);
	if (bytes > maxMinorBytesCopied) maxMinorBytesCopied = bytes;
	totalMinorBytesCopied += bytes;
	
	// print verbose output

	if (verbose >= 1) printVerboseOutputLine( 1 /* MINOR */ );

	// add current GC phase times into totals, print if verbose on
	if (TIME_GC_PHASES) accumulateGCPhaseTimes( false );  	

	if (verbose >= 1) printWaitTimesAndCounts();

      }  // end selectedThread
      
      // DONE: after Minor Collection: all gc threads return here
      return;
    }
    
    //
    // ALL GC THREADS START MAJOR GC
    //
    
    mylocal = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());

    if (VM_CollectorThread.MEASURE_WAIT_TIMES)
      mylocal.resetWaitTimers();         // reset for measuring major GC wait times
    
    // following seems to be necessary when PROCESSOR_LOCAL_MATURE_ALLOCATE==true
    // not sure why, otherwise fail in assertion in yield done by collectorthread.run()
    // maybe processor object is moved while enableSwitching count is being modified???
    if ( PROCESSOR_LOCAL_MATURE_ALLOCATE == true ) 
	mylocal.rendezvousWaitTime += VM_CollectorThread.gcBarrier.rendezvous(RENDEZVOUS_TIMES || RENDEZVOUS_WAIT_TIME);

    
    if (verbose >= 2) VM_Scheduler.trace("VM_Allocator", "starting major collection", gcMajorCount);
    
    gc_scanProcessor();   // each gc threads scans its own processor object

    if (verbose >= 2) VM.sysWriteln("scanning statics");
    VM_ScanStatics.scanStatics();     // GC threads scan JTOC in parallel

    if (verbose >= 2) VM.sysWriteln("scanning threads");
    gc_scanThreads();    // GC threads process thread objects & scan their stacks
    
    if (GCDEBUG_CHECKWB) {
      VM_Scheduler.trace("---checking writebuffer","after scanStatics");
      // all write buffers were reset to empty earlier, check that still empty
      gc_checkWriteBuffers();
    }
    
    // This synchronization is necessary to ensure all stacks have been scanned
    // and all internal save ip values have been updated before we scan copied
    // objects.  Because if we scan a VM_Method, and then update its code pointer
    // we can no longer compute old ip offsets for updating saved ip values
    //
    // WAIT FOR ALL GC THREADS TO REACH HERE
    
    mylocal.rendezvousWaitTime += VM_CollectorThread.gcBarrier.rendezvous(RENDEZVOUS_TIMES || RENDEZVOUS_WAIT_TIME);
    
    // have processor 1 record timestame for end of scanning stacks & statics
    if (TIME_GC_PHASES && (mylocal.gcOrdinal == 1))
      gcStacksAndStaticsDoneTime = VM_Time.now(); // for time scanning stacks & statics

    if (verbose >= 2) VM.sysWriteln("Emptying work queue");
    gc_emptyWorkQueue();  // each GC thread processes its own work queue buffers
    
    // have processor 1 record timestame for end of scan/mark/copy phase
    if (TIME_GC_PHASES && (mylocal.gcOrdinal == 1))
      gcScanningDoneTime = VM_Time.now();

   if (GCDEBUG_CHECKWB) {
      VM_Scheduler.trace("---checking writebuffer","after emptyWorkQueue");
      // all write buffers were reset to empty earlier, check that still empty
      gc_checkWriteBuffers();
    }

    // If counting or timing in VM_GCWorkQueue, save current counter values
    //
    if (VM_GCWorkQueue.WORKQUEUE_COUNTS)   VM_GCWorkQueue.saveCounters(mylocal);
    if (VM_GCWorkQueue.MEASURE_WAIT_TIMES || VM_CollectorThread.MEASURE_WAIT_TIMES)
      VM_GCWorkQueue.saveWaitTimes(mylocal);
    
    // If there are not any objects with finalizers skip finalization phases
    //
    if (VM_Finalizer.existObjectsWithFinalizers()) {

      // Now handle finalization
      //  reset below serves as sync point - see reset above for comment
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
      mylocal.rendezvousWaitTime += VM_CollectorThread.gcBarrier.rendezvous(RENDEZVOUS_TIMES || RENDEZVOUS_WAIT_TIME);
     
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
    
    // majorGCDone flag has been set to false earlier
    
    if ( VM_GCLocks.testAndSetFinishMajorLock() ) {

      // set ending times for preceeding finalization phase
      // do here where a sync (below) will push value to memory
      if (TIME_GC_PHASES)  gcFinalizeDoneTime = VM_Time.now();
      
      if (verbose >= 2) VM_Scheduler.trace("VM_Allocator", "(major collection) doing gc_finish");
      
      gc_finish();  // reset heap allocation area, reset GC locks, isync, etc
      
      selectedGCThread = true;  // have this thread generate verbose output below,
      // after nursery has been zeroed
      
      VM_Magic.sync();
      
      majorGCDone = true;  // lets spinning GC threads continue
      
    }  // END OF SINGLE THREAD SECTION
    
    else {
      while( majorGCDone == false );   // losing threads spin till above section finished
      VM_Magic.isync();                // prevent following inst. from moving infront of waitloop
    }
    
    // ALL GC THREADS IN PARALLEL - AFTER MAJOR COLLECTION
    
    if (ZERO_NURSERY_IN_PARALLEL) {
	appelHeap.zeroParallel(areaCurrentAddress,areaEndAddress);
    }

    // generate -verbosegc output, done here after (possibly) zeroing nursery. 
    // this is done by the 1 gc thread that finished the preceeding GC
    //
    if ( selectedGCThread ) {

      gcEndTime  = VM_Time.now();
      // get time spent in major GC (including time to zero nursery, if done now)
      gcMajorTime = gcEndTime - gcStartTime;
      gcTotalTime = gcTotalTime + gcMajorTime;
      totalMajorTime += gcMajorTime;
      if (gcMajorTime > maxMajorTime) maxMajorTime = gcMajorTime;
      if ( matureAllocationIncreasing )
	bytes = matureCurrentAddress.diff(appelHeap.start);
      else
	bytes = appelHeap.end.diff(matureCurrentAddress);
      totalMajorBytesCopied += bytes;
      if (bytes > maxMajorBytesCopied) maxMajorBytesCopied = bytes;

      // print verbose output

      if (verbose >= 1) printVerboseOutputLine( 3 /* MAJOR*/ );

      // add current GC phase times into totals, print if verbose on
      if (TIME_GC_PHASES) accumulateGCPhaseTimes( true );  	
      
      if (verbose >= 1) printWaitTimesAndCounts();

    }  // end selectedThread
    
    // following checkwritebuffer call is necessary to remove inadvertent entries
    // that are recorded during major GC, and which would crash the next minor GC
    //
    gc_checkWriteBuffers();
    
    // all GC threads return, having completed Major collection
    return;
  }  // collect
  

    public static void XXXXXX () {

    if ( matureAllocationIncreasing ) {
	int temp = matureCurrentAddress.toInt();
	temp &= ~2047; // (VM_Memory.getPagesize() - 1);
	fromStartAddress = VM_Address.fromInt(temp);
    } else {
	fromStartAddress = appelHeap.start;
    }
    
    areaCurrentAddress = fromStartAddress;

  }  // XXXXX



  // reset heap pointers, reset locks for next GC
  // executed at the end of Minor collections (if major collection not needed)
  // and at the end  of Major Collections.  Only executed by ONE of the
  // participating GC threads.
  //
  private static void gc_finish () {

    short[] shorttemp;
    
    if (verbose >= 1) {
      gcTimeBeforeZeroing = VM_Time.now();
    }
    
    // redivide empty portion of heap in half, setup one of halves as allocation
    // area (FromSpace) for next allocation cycle
    if ( matureAllocationIncreasing ) {
      // mature objects now occupy left/lower end of heap
      // Use upper half of space from matureSpace to end of heap
      int remaining = appelHeap.end.diff(matureCurrentAddress);
      fromStartAddress = VM_Memory.roundDownPage(matureCurrentAddress.add(remaining/2));
      fromEndAddress = appelHeap.end;
    } else {
      // mature objects now occupy upper/right end of heap
      // use left/lower half of space from start of heap to end of mature objects
      int remaining = matureCurrentAddress.diff(appelHeap.start);
      fromStartAddress = appelHeap.start;
      fromEndAddress = VM_Memory.roundDownPage(fromStartAddress.add(remaining / 2));
    }
    
    // now set allocation pointers to new nursery/FromSpace
    areaCurrentAddress = fromStartAddress;
    areaEndAddress = fromEndAddress;
    nurserySize = fromEndAddress.diff(fromStartAddress);
    
    // The remainder of the current semi-space must be zero'ed before allowing
    // This collector can be compiled to zero the new Nursery/FromSpace
    // in either of three ways:
    //     - 1 GC threads zeros it all (the executing thread) (BAD !!)
    //     - All threads zero chunks in parallel (the executing thread
    //       determines the per thread regions to be zero'ed
    //     - Zeroing is deferred until processors allocate processor
    //       local chunks, while mutators are running (BEST ??)
    //
    if (ZERO_NURSERY_IN_PARALLEL) {
      // !! The following partitioning assumes that ALL processors are
      // participating in the collection.  This used to be true, but there are
      // now variations of the threading package (currently under revision) 
      // where only a subset of the processors participate. In which case, the
      // following should assign work only to those participating processors.
      //
      // Modify fromHeap.zeroParallel
    }
    else if ( ! ZERO_BLOCKS_ON_ALLOCATION ) {
      // have one processor (the executing one) do all the zeroing
      if (VM.ParanoidGCCheck)
	  VM_Heap.clobber( areaCurrentAddress, areaEndAddress );
      else
	// zero the new from space
	VM_Memory.zeroPages( areaCurrentAddress, areaEndAddress.diff(areaCurrentAddress) );
    }
    else {
      // if ZERO_BLOCKS_ON_ALLOCATION is on (others OFF!) then processors
      // zero there own processor local chunks when allocated, this 
      // requires that the allocator/collector be compiled with
      // PROCESSOR_LOCAL_ALLOCATE == true
      if (VM.VerifyAssertions) VM.assert(PROCESSOR_LOCAL_ALLOCATE == true);
      
      if (VM.ParanoidGCCheck)
	  VM_Heap.clobber( areaCurrentAddress, areaEndAddress );
    }
    
    if (majorCollection) 
	largeHeap.endCollect();
    
    prepareNonParticipatingVPsForAllocation();

    gcInProgress = false;
    
    // reset lock for next GC before starting mutators
    VM_GCLocks.reset();
    
    // reset the flag used to make GC threads wait until GC initialization
    // completed....for the next GC 
    initGCDone = false;
    
    return;
  }  // gc_finish
  
  /**
   * Internal method called by collector threads during collection to
   * get space in ToSpace for a live object that needs to be copied.
   * Space is obtained from the processor local "chunk" if available,
   * otherwise space is obtained directly from ToSpace using 
   * atomic compare and swap instructions.
   */
  static VM_Address gc_getMatureSpace ( int size ) {

    VM_Address startAddress, newCurrentAddress;  
    
    // if compiled for processor local chunking of "mature space" attempt to allocate
    // in currently assigned region of mature space (other semi-space in simple semi-space scheme).
    // if not enough room, get another mature chunk using lwarx_stwcx
    //
    if (PROCESSOR_LOCAL_MATURE_ALLOCATE == true) {
      VM_Processor st = VM_Processor.getCurrentProcessor();

      if (VM_GCWorkQueue.COUNT_GETS_AND_PUTS)
	VM_Magic.threadAsCollectorThread(st.activeThread).copyCount++;

      startAddress = st.localMatureCurrentAddress;
      newCurrentAddress = startAddress.add(size);
      if ( newCurrentAddress.LE(st.localMatureEndAddress) ) {
	st.localMatureCurrentAddress = newCurrentAddress;    // increment processor local pointer
	if (VM.VerifyAssertions) {
	    if (!appelHeap.addrInHeap(startAddress.add(size).sub(1))) {
		VM.sysWriteln();
		appelHeap.show();
		VM.sysWriteln("st.localMatureEndAddress = ", st.localMatureEndAddress);
		VM.sysWriteln("startAddress = ", startAddress);
		VM.sysWriteln("newCurrentAddress = ", newCurrentAddress);
	    }
	    VM.assert(appelHeap.addrInHeap(startAddress));
	    VM.assert(appelHeap.addrInHeap(startAddress.add(size).sub(1)));
	}
	return startAddress;
      }
      else {
	if (matureAllocationIncreasing) {
	    startAddress = VM_Synchronization.fetchAndAddAddress(addrMatureCurrentAddress, CHUNK_SIZE);
	    if (majorCollection && matureCurrentAddress.GT(matureSaveAddress))
		outOfMemory("Out of Memory during Major Collection - Increase Major GC Threshold\n");
	}
	else { 
	    startAddress = VM_Synchronization.fetchAndAddAddress(addrMatureCurrentAddress,
								 - CHUNK_SIZE).sub(CHUNK_SIZE);
	    if (majorCollection && matureCurrentAddress.LT(matureSaveAddress))
	      outOfMemory("Out of Memory during Major Collection - Increase Major GC Threshold\n");
	}
	// startAddress = beginning of new mature space chunk for this processor
	st.localMatureEndAddress = startAddress.add(CHUNK_SIZE);
	st.localMatureCurrentAddress = startAddress.add(size);
	if (VM.VerifyAssertions) VM.assert(appelHeap.addrInHeap(startAddress));
	if (VM.VerifyAssertions) VM.assert(appelHeap.addrInHeap(startAddress.add(size).sub(1)));
	return startAddress;
      }
    } // end of chunking logic
    
    // else old non chunking logic, use single mature space ptr
    else {
      if (matureAllocationIncreasing) {
	  startAddress = VM_Synchronization.fetchAndAddAddress(addrMatureCurrentAddress, size);
	  if (majorCollection && matureCurrentAddress.GT(matureSaveAddress))
	      outOfMemory("Out of Memory during Major Collection - Increase Major GC Threshold\n");
      }
      else { 
	startAddress = VM_Synchronization.fetchAndAddAddress(addrMatureCurrentAddress, - size).sub(size);
	if (majorCollection && matureCurrentAddress.LT(matureSaveAddress))
	    outOfMemory("Out of Memory during Major Collection - Increase Major GC Threshold\n");
      }
      if (VM.VerifyAssertions) VM.assert(appelHeap.addrInHeap(startAddress));
      if (VM.VerifyAssertions) VM.assert(appelHeap.addrInHeap(startAddress.add(size)));
      return startAddress;
    } // end old non-chunking logic
    
  }  // getMatureSpace
  
  
  /**
   * Processes live objects in FromSpace that need to be marked, copied and
   * forwarded during collection.  Returns the new address of the object
   * in ToHeap.  If the object was not previously marked, then the
   * invoking collector thread will do the copying and enqueue the
   * on the work queue of objects to be scanned.
   *
   * @param fromObj Object in FromSpace to be processed
   * @param scan should the object be scanned?
   * @return the address of the Object in ToSpace (as a reference)
   */
  static VM_Address copyAndScanObject (VM_Address fromRef, boolean scan) {

    if (VM.VerifyAssertions) VM.assert(validFromRef( fromRef ));

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
    forwardingPtr |= VM_AllocatorHeader.GC_BARRIER_BIT_MASK;     // set barrier bit 
    if (VM.VerifyAssertions) VM.assert(VM_GCUtil.validObject(type));
    Object toObj;
    VM_Address toRef;
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
      if (arrayType == VM_Type.CodeType) {
	// sync all arrays of ints - must sync moved code instead of sync'ing chunks when full
	VM_Memory.sync(toRef, numBytes);
      }
    }

    VM_ObjectModel.initializeAvailableByte(toObj); // make it safe for write barrier to access barrier bit non-atmoically

    VM_Magic.sync(); // make changes viewable to other processors 
    
    VM_AllocatorHeader.setForwardingPointer(fromObj, toObj);

    if (scan) VM_GCWorkQueue.putToWorkBuffer(toRef);

    return toRef;

  }


  // Turn on the writeBarrier bit in the object header.
  static void resetObjectBarrier(Object objRef) {
    // Need to turn back on barrier bit *always*
    VM_ObjectModel.initializeAvailableByte(objRef); // make it safe for write barrier to change bit non-atomically
    VM_AllocatorHeader.setBarrierBit(objRef);
   }
  
  // process writeBuffer attached to the running GC threads current processor
  //
  static void gc_processWriteBuffers () {
    VM_WriteBuffer.processWriteBuffer(VM_Processor.getCurrentProcessor());
  }
  
  // check that writeBuffer attached to the running GC threads current processor
  // is empty, if not print diagnostics & reset
  //
  static void gc_checkWriteBuffers () {
    VM_WriteBuffer.checkForEmpty(VM_Processor.getCurrentProcessor());
  }
  
  
  // called by ONE gc/collector thread to copy ALL "new" thread and processor objects
  // copies but does NOT enqueue for scanning
  //
  static void gc_copyThreads ()  {

    for (int i=0; i<VM_Scheduler.threads.length; i++ ) {
      VM_Thread t = VM_Scheduler.threads[i];
      if ( t == null ) continue;
      VM_Address ta = VM_Magic.objectAsAddress(t);
      if ( ta.GE(minFromRef) && ta.LE(maxFromRef) ) {
	ta = copyAndScanObject(ta, false);
	t = VM_Magic.objectAsThread(VM_Magic.addressAsObject(ta));
	// change entry in threads array to point to new copy of thread
	VM_Magic.setMemoryAddress( VM_Magic.objectAsAddress(VM_Scheduler.threads).add(i*4), ta);
      }
    }
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

    int[]      oldstack;
    
    // get ID of running GC thread
    int myThreadId = VM_Thread.getCurrentThread().getIndex();
    
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
	if (VM.VerifyAssertions) VM.assert( !(ta.GE(minFromRef) && ta.LE(maxFromRef)) );
	
	if (VM.VerifyAssertions) oldstack = t.stack;  // for verifying  gc stacks not moved
	VM_ScanObject.scanObjectOrArray(ta);              // will copy copy stacks, reg arrays, etc.
	if (VM.VerifyAssertions) VM.assert(oldstack == t.stack);
	
	if (t.jniEnv != null) VM_ScanObject.scanObjectOrArray(t.jniEnv);
	VM_ScanObject.scanObjectOrArray(t.contextRegisters);
	VM_ScanObject.scanObjectOrArray(t.hardwareExceptionRegisters);
	
	if (GCDEBUG_SCANTHREADS) VM.sysWriteln("Collector Thread scanning own stack",i);
	VM_ScanStack.scanStack( t, VM_Address.zero(), true );

	
	continue;
      }

      if ( debugNative && t.isGCThread ) {
	VM_Scheduler.trace("scanThreads:","at GC thread for processor id =",
			   t.processorAffinity.id);
	VM_Scheduler.trace("scanThreads:","                    gcOrdinal =",
			   VM_Magic.threadAsCollectorThread(t).gcOrdinal);
      }

      // skip other collector threads participating (have ordinal number) in this GC
      if ( t.isGCThread && (VM_Magic.threadAsCollectorThread(t).gcOrdinal > 0) )
	continue;
      
      // have mutator thread, compete for it with other GC threads
      if ( VM_GCLocks.testAndSetThreadLock(i) ) {
	
	if (debugNative || GCDEBUG_SCANTHREADS) VM_Scheduler.trace("VM_Allocator","processing mutator thread",i);
	
	// all threads should have been copied out of fromspace earlier
	if (VM.VerifyAssertions) VM.assert( !(ta.GE(minFromRef) && ta.LE(maxFromRef)) );
	
	// scan thread object to force "interior" objects to be copied, marked, and
	// queued for later scanning.
	oldstack = t.stack;    // remember old stack address before scanThread
	VM_ScanObject.scanObjectOrArray(ta);
	
	// if stack moved, adjust interior stack pointers
	if ( oldstack != t.stack ) {
	  if (GCDEBUG_SCANTHREADS) VM_Scheduler.trace("VM_Allocator","...adjusting mutator stack",i);
	  t.fixupMovedStack(VM_Magic.objectAsAddress(t.stack).diff(VM_Magic.objectAsAddress(oldstack)));
	}
	
	// the above scan of VM_Thread will have marked and copied the threads JNIEnvironment object,
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

	//-#if RVM_WITH_OLD_CODE

	  VM_JNIEnvironment env = t.jniEnv;
	  VM_Address fp = env.JNITopJavaFP; 
	  
	  if (debugNative && VM.verboseGC)
	    VM_Scheduler.trace("VM_Allocator:processing thread running in NATIVE"," fp = ",fp);
	  
	  if ( fp == -1 ) {
	    // have a thread that previouly entered the VM via jniCreate of jniAttach
	    // but now does not have any java frames, ie has returned back to native.
	    // There are no java frames to scan (we don't know where the top of the
	    // stack is, so can not scan even if we wanted to) but the threads jniEnv
	    // jniRefs stack may have references that have been returned back to the
	    // native code (via jniFunctions) and we report those for GC here.

	    if (debugNative && VM.verboseGC) 
	      VM_Scheduler.trace("VM_Allocator:scanThreads - NATIVE with JNITopJavaFP ==","-1");

	    // There should only be the "bottom" "frame" on the JNIEnv jniRefs stack
	    if (VM.VerifyAssertions) VM.assert( env.JNIRefsSavedFP == 0 );

	    int jniRefBase = VM_Magic.objectAsAddress(env.JNIRefs);
	    int jniRefOffset = env.JNIRefsTop;
	    while ( jniRefOffset > 0  /*env.JNIRefsSavedFP*/ ) {
	      gc_processPtrField( VM_Magic.addressAsObject(0), jniRefBase + jniRefOffset );
	      jniRefOffset -= 4;
	    }
	    continue;
	  }

	  //-#endif


	if (verbose >= 3) VM_Scheduler.trace("VM_Allocator","scanning stack for thread",i);
	VM_ScanStack.scanStack( t, VM_Address.zero(), true /*relocate_code*/ );

      }  // (if true) we seized got the thread to process

      else continue;  // some other gc thread has seized this thread
      
    }  // end of loop over threads[]
    
  }  // gc_scanThreads
  
  // initProcessor is called by each GC thread to copy the processor object of the
  // processor it is running on, and reset it processor register, and update its
  // entry in the scheduler processors array and reset its local allocation pointers
  //
  static void gc_initProcessor ()  {

    VM_Processor st = VM_Processor.getCurrentProcessor();
    VM_Address sta = VM_Magic.objectAsAddress(st);
    VM_Thread activeThread = st.activeThread;
    int tid = activeThread.getIndex();
    
    if (VM.VerifyAssertions) VM.assert(tid == VM_Thread.getCurrentThread().getIndex());
    
    // if compiled for processor local chunking of "mature space" reset processor local 
    // pointers, to cause first request to get a block (only reset on major collection
    // for minor collection, continue filling last/current mature buffer
    //
    if (PROCESSOR_LOCAL_MATURE_ALLOCATE) {
      if (majorCollection) {
	st.localMatureCurrentAddress = VM_Address.zero();
	st.localMatureEndAddress  = VM_Address.zero();
      }
    }
    
    // if Processor is in fromSpace, copy and update array entry
    if ( sta.GE(minFromRef) && sta.LE(maxFromRef) ) {
	sta = copyAndScanObject(sta, false);   // copy processor object, do not queue for scanning
	st = VM_Magic.objectAsProcessor(VM_Magic.addressAsObject(sta));
	// change entry in system threads array to point to copied sys thread
	VM_Magic.setMemoryAddress( VM_Magic.objectAsAddress(VM_Scheduler.processors).add(st.id*4), sta);
    }
    
    // each gc thread updates its PROCESSOR_REGISTER after copying its VM_Processor object
    VM_Magic.setProcessorRegister(st);
    
    if (PROCESSOR_LOCAL_ALLOCATE) {
      //  reset local heap pointers .. causes first mutator allocate to
      //   get a locak Chunk from the shared heap
      //
      st.localCurrentAddress = VM_Address.zero();
      st.localEndAddress     = VM_Address.zero();
    }
    
    // if Processors activethread (should be current, gc, thread) is in fromSpace, copy and
    // update activeThread field and threads array entry to make sure BOTH ways of computing
    // getCurrentThread return the new copy of the thread
    VM_Address ata = VM_Magic.objectAsAddress(activeThread);
    if ( ata.GE(minFromRef) && ata.LE(maxFromRef) ) {
      // copy thread object, do not queue for scanning
      ata = copyAndScanObject(ata, false);
      activeThread = VM_Magic.objectAsThread(VM_Magic.addressAsObject(ata));
      st.activeThread = activeThread;
      // change entry in system threads array to point to copied sys thread
      VM_Magic.setMemoryAddress(VM_Magic.objectAsAddress(VM_Scheduler.threads).add(tid*4), ata);
    }
    
    // setup the work queue buffers for this gc thread
    VM_GCWorkQueue.resetWorkQBuffers();
    
  }  // gc_initProcessor
  
  // scan a VM_Processor object to force "interior" objects to be copied, marked,
  // and queued for later scanning. adjusts write barrier pointers, if
  // write buffer is moved.
  //
  static void gc_scanProcessor ()  {
    
      VM_Processor st = VM_Processor.getCurrentProcessor();
      VM_Address   sta = VM_Magic.objectAsAddress(st);
    
    if (PROCESSOR_LOCAL_ALLOCATE) {
      // local heap pointer set in initProcessor, should still be 0, ie no allocates yet
      if (VM.VerifyAssertions) VM.assert(st.localCurrentAddress.isZero());
    }
    
    if (VM.VerifyAssertions) {
      // processor should already be copied, ie NOT in FromSpace
      VM.assert(!(sta.GE(minFromRef) && sta.LE(maxFromRef)));
      // and its processor array entry updated
      VM.assert(sta.EQ(VM_Magic.objectAsAddress(VM_Scheduler.processors[st.id])));
    }
    
    // scan system thread object to force "interior" objects to be copied, marked, and
    // queued for later scanning.
    VM_Address oldbuffer = VM_Magic.objectAsAddress(st.modifiedOldObjects);
    VM_ScanObject.scanObjectOrArray(sta);
    
    // if writebuffer moved, adjust interior pointers
    VM_Address newbuffer = VM_Magic.objectAsAddress(st.modifiedOldObjects);
    if (oldbuffer.NE(newbuffer)) {
      st.modifiedOldObjectsMax = newbuffer.add(st.modifiedOldObjectsMax.diff(oldbuffer));
      st.modifiedOldObjectsTop = newbuffer.add(st.modifiedOldObjectsTop.diff(oldbuffer));
    }
    
  }  // scanProcessor
  
  
  // Process references in work queue buffers until empty.
  //
  static void gc_emptyWorkQueue () {

    if (VM_GCWorkQueue.WORKQUEUE_COUNTS) {
      VM_CollectorThread myThread = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());
      myThread.rootWorkCount = myThread.putWorkCount;
    }
    
    VM_Address ref = VM_GCWorkQueue.getFromWorkBuffer();
    while ( !ref.isZero() ) {
      VM_ScanObject.scanObjectOrArray( ref );	   
      ref = VM_GCWorkQueue.getFromWorkBuffer();
    }
  }  // gc_emptyWorkQueue

  static boolean validFromRef ( VM_Address ref ) {
      return ( ref.GE(minFromRef) && ref.LE(maxFromRef) );
  }
  

  static boolean validForwardingPtr ( VM_Address ref ) {

    if ( majorCollection ) {
      if ( matureAllocationIncreasing ) {
	// copying mature objs to left/lower part of heap 	
	  return ( ref.GE(appelHeap.start) && ref.LE(matureCurrentAddress.add(4)) );
      } 
      else {
	// copying mature objs to right/upper part of heap 	
	return ( ref.GE(matureCurrentAddress.add(4)) && ref.LE(appelHeap.end.add(4)) );
      }
    }
    else {
      if ( matureAllocationIncreasing ) 
	  return ( ref.GE(VM_ObjectModel.minimumObjectRef(matureSaveAddress))  &&
		   ref.LE(VM_ObjectModel.maximumObjectRef(matureCurrentAddress)) );
      else 
	  return  ( ref.GE(VM_ObjectModel.minimumObjectRef(matureCurrentAddress)) &&
		    ref.LE(VM_ObjectModel.maximumObjectRef(matureSaveAddress)) );
    }
  }	
  
  static boolean validWorkQueuePtr ( VM_Address ref ) {
    if ( bootHeap.refInHeap(ref) ) return true;
    if ( largeHeap.refInHeap(ref) ) return true;
    return validForwardingPtr( ref );
  }
  
  static void dumpThreadsArray () {

    VM.sysWrite("VM_Scheduler.threads[]:\n");
    for ( int i=0; i<VM_Scheduler.threads.length; i++ ) {
      VM.sysWrite(" i = ");
      VM.sysWrite(i);
      VM_Thread t = VM_Scheduler.threads[i];
      if (t==null) {
	VM.sysWrite(" t is NULL");
	VM.sysWrite("\n");
      }
      else {
	//	  VM.sysWrite(", Id = ");
	//	  VM.sysWrite(t.id);
	VM.sysWrite(", addr = ");
	VM.sysWrite(VM_Magic.objectAsAddress(t));
	VM.sysWrite(", stack = ");
	VM.sysWrite(VM_Magic.objectAsAddress(t.stack));
	//	  if (t.isEmptyThread)
	//	    VM.sysWrite(" (isEmptyThread)");	  
	if (t.isGCThread)
	  VM.sysWrite(" (isGCThread)");	  
	if (t.isDaemon)
	  VM.sysWrite(" (isDaemon)");	  
	VM.sysWrite("\n");	  
      }
      
    }
  }
  
  static void dumpProcessorsArray () {
    VM_Processor st;
    VM.sysWrite("VM_Scheduler.processors[]:\n");
    for (int i = 0; ++i <= VM_Scheduler.numProcessors;) {
      st = VM_Scheduler.processors[i];
      VM.sysWrite(" i = ");
      VM.sysWrite(i);
      if (st==null) 
	VM.sysWrite(" st is NULL");
      else {
	VM.sysWrite(", id = ");
	VM.sysWrite(st.id);
	VM.sysWrite(", address = ");
	VM.sysWrite(VM_Magic.objectAsAddress(st));
	VM.sysWrite(", buffer = ");
	VM.sysWrite(VM_Magic.objectAsAddress(st.modifiedOldObjects));
	VM.sysWrite(", top = ");
	VM.sysWrite(st.modifiedOldObjectsTop);
      }
      VM.sysWrite("\n");
    }
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
  //
  private static void crash (String err_msg) {
    VM.sysWrite("VM_Allocator.crash:\n");
    if (PROCESSOR_LOCAL_ALLOCATE) {
	VM_Address tempbuffer = VM_Address.fromInt(VM.sysCall1(bootrecord.sysMallocIP,
							      VM_Allocator.CRASH_BUFFER_SIZE));
      if (tempbuffer.isZero()) {
	VM.sysWrite("VM_ALLOCATOR.crash() sysMalloc returned 0 \n");
	VM.shutdown(1800);
      }
      VM_Processor p = VM_Processor.getCurrentProcessor();
      p.localCurrentAddress = tempbuffer;
      p.localEndAddress = tempbuffer.add(VM_Allocator.CRASH_BUFFER_SIZE);
      VM_Memory.zero(tempbuffer, tempbuffer.add(VM_Allocator.CRASH_BUFFER_SIZE));
    }
    VM.sysFail(err_msg);
  }
  
  // Called from VM_Processor constructor: 
  // Must alloc & initialize write buffer, allocation pointers already zero
  static void setupProcessor (VM_Processor p) {
    VM_WriteBuffer.setupProcessor(p);
  }
  
  // following referenced by refcountGC methods (but not called)
  static void gc_scanStacks () { VM.assert(false); }
  
  private static void resetThreadLocalCounters ( VM_CollectorThread mylocal ) {

    mylocal.timeInRendezvous = 0;
    
    // following for measuring work queue with local work buffers
    mylocal.putWorkCount = 0;
    mylocal.getWorkCount = 0;
    mylocal.swapBufferCount = 0;
    mylocal.putBufferCount = 0;
    mylocal.getBufferCount = 0;
  }
  
  private static void accumulateGCPhaseTimes ( boolean afterMajor ) {

    double start = afterMajor ? 0.0 : gcStartTime - VM_CollectorThread.gcBarrier.rendezvousStartTime;
    double init     = gcInitDoneTime - gcStartTime;
    double stacksAndStatics = gcStacksAndStaticsDoneTime - gcInitDoneTime;
    double scanning = gcScanningDoneTime - gcStacksAndStaticsDoneTime;
    double finalize = gcFinalizeDoneTime - gcScanningDoneTime;
    double finish   = gcEndTime - gcFinalizeDoneTime;

    // add current GC times into totals for summary output
    //    totalStartTime += start;   // always measured in ge initialization
    if (!afterMajor) {
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
      VM.sysWrite("<GC ");
      VM.sysWrite(gcCount,false);
      if (!afterMajor) {
	VM.sysWrite(" startTime ");
	VM.sysWrite( (int)(start*1000000.0), false);
	VM.sysWrite("(us)");
      }
      VM.sysWrite(" init ");
      VM.sysWrite( (int)(init*1000000.0), false);
      VM.sysWrite("(us) stacks & statics ");
      VM.sysWrite( (int)(stacksAndStatics*1000000.0), false);
      VM.sysWrite("(us) scanning ");
      VM.sysWrite( (int)(scanning*1000.0), false );
      VM.sysWrite("(ms) finalize ");
      VM.sysWrite( (int)(finalize*1000000.0), false);
      VM.sysWrite("(us) finish ");
      VM.sysWrite( (int)(finish*1000000.0), false);
      VM.sysWrite("(us)>\n");
    }
  }  // accumulateGCPhaseTimes

  static void printSummaryStatistics () {
    int np = VM_Scheduler.numProcessors;

    // produce summary system exit output if -verbose:gc was specified of if
    // compiled with measurement flags turned on
    //
    if ( ! (TIME_GC_PHASES || VM_CollectorThread.MEASURE_WAIT_TIMES || verbose >= 1) )
      return;     // not verbose, no flags on, so don't produce output

    // the bytesCopied counts count whole chunks. The last chunk acquired for
    // copying objects is partially full/empty, on avg. half full.  So we
    // subtrace from the average, half of space in a set of chunks
    //
    int avgBytesFreeInChunks = (VM_Scheduler.numProcessors * CHUNK_SIZE) >> 1;

    VM.sysWrite("\nGC stats: Copying Generational Collector - Variable Nursery (");
    VM.sysWriteln(np," Collector Threads )");
    VM.sysWrite("  Small Object Heap Size = ", appelHeap.size / 1024, " Kb");
    VM.sysWriteln("    Large Object Heap Size = ", largeHeap.size / 1024, " Kb");

    if (gcCount == 0)
      VM.sysWrite("0 MinorCollections");
    else {
      VM.sysWrite(gcCount,false);
      VM.sysWrite(" MinorCollections: avgTime ");
      VM.sysWrite( (int)( ((totalMinorTime/(double)gcCount)*1000.0) ),false);
      VM.sysWrite(" (ms) maxTime ");
      VM.sysWrite( (int)(maxMinorTime*1000.0),false);
      VM.sysWrite(" (ms) avgBytesCopied ");
      VM.sysWrite((int)(totalMinorBytesCopied/gcCount)-avgBytesFreeInChunks,false);
      VM.sysWrite(" maxBytesCopied ");
      VM.sysWrite(maxMinorBytesCopied,false);
      VM.sysWrite("\n");
    }

    VM.sysWrite("  ");
    if (gcMajorCount == 0)
      VM.sysWrite("0 MajorCollections\n");
    else {
      VM.sysWrite(gcMajorCount,false);
      VM.sysWrite(" MajorCollections: avgTime ");
      VM.sysWrite( (int)( ((totalMajorTime/(double)gcMajorCount)*1000.0) ),false);
      VM.sysWrite(" (ms) maxTime ");
      VM.sysWrite( (int)(maxMajorTime*1000.0),false);
      VM.sysWrite(" (ms) avgBytesCopied ");
      VM.sysWrite((int)(totalMajorBytesCopied/gcMajorCount)-avgBytesFreeInChunks,false);
      VM.sysWrite(" maxBytesCopied ");
      VM.sysWrite((int)maxMajorBytesCopied,false);
      VM.sysWrite("\n");
    }

    VM.sysWrite("  Total Collection Time ");
    VM.sysWrite( (int)(gcTotalTime*1000.0),false);
    VM.sysWrite(" (ms)\n\n");

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
      VM.sysWrite("Buffer Wait ");
      VM.sysWrite( avgBufferWait, false);
      VM.sysWrite(" (us) Finish Wait ");
      VM.sysWrite( avgFinishWait, false);
      VM.sysWrite(" (us) Rendezvous Wait ");
      VM.sysWrite( avgRendezvousWait, false);
      VM.sysWrite(" (us)\n\n");
    }

  }  // printSummaryStatistics
  
  private static void printWaitTimesAndCounts () {

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
    
    if (RENDEZVOUS_TIMES)  VM_CollectorThread.gcBarrier.printRendezvousTimes();
    
  }  // printWaitTimesAndCounts

  private static void printVerboseOutputLine (int phase) {
    int bytes;
    
    if ( phase == 1 ) {
      VM.sysWrite("\n<GC ");
      VM.sysWrite(gcCount,false);
      VM.sysWrite(" (MINOR) time ");
      VM.sysWrite( (int)(gcMinorTime*1000.0), false );
      if (ZERO_BLOCKS_ON_ALLOCATION)
	VM.sysWrite(" ms (no zeroing) ");
      else {
	VM.sysWrite(" ms (zeroing ");
	VM.sysWrite( (int)(((gcEndTime - gcTimeBeforeZeroing)*1000.0)), false );
	VM.sysWrite(") ");
      }
      VM.sysWrite(" copied ");
      if (matureAllocationIncreasing)
	VM.sysWrite(matureCurrentAddress.diff(matureSaveAddress),false);
      else
	VM.sysWrite(matureSaveAddress.diff(matureCurrentAddress),false);
      VM.sysWrite(" mature ");
      if ( matureAllocationIncreasing ) {
	VM.sysWrite("(inc) " );
	bytes = matureCurrentAddress.diff(appelHeap.start);
      }
      else {
	VM.sysWrite("(dec) " );
	bytes = appelHeap.end.diff(matureCurrentAddress);
      }
      VM.sysWrite(bytes,false);
      VM.sysWrite(" nurserySize " );
      VM.sysWrite(nurserySize,false);
      VM.sysWrite(">\n");
    }
    else if (phase == 2) {
      VM.sysWrite("\n<GC ");
      VM.sysWrite(gcCount,false);
      VM.sysWrite(" (MINOR_before_MAJOR) time ");
      VM.sysWrite( (int)(gcMinorTime*1000.0), false );
      VM.sysWrite(" (ms) (no zeroing) ");
      VM.sysWrite(" copied ");
      if (matureAllocationIncreasing)
	VM.sysWrite(matureCurrentAddress.diff(matureSaveAddress),false);
      else
	VM.sysWrite(matureSaveAddress.diff(matureCurrentAddress),false);
      VM.sysWrite(" mature ");
      if ( matureAllocationIncreasing ) {
	VM.sysWrite("(inc) " );
	bytes = matureCurrentAddress.diff(appelHeap.start);
      }
      else {
	VM.sysWrite("(dec) " );
	bytes = appelHeap.end.diff(matureCurrentAddress);
      }
      VM.sysWrite(bytes,false);
      VM.sysWrite(">\n");
    }
    else if (phase == 3) {
      VM.sysWrite("\n<GC ");
      VM.sysWrite(gcCount,false);
      VM.sysWrite(" (MAJOR_after_MINOR) time ");
      VM.sysWrite( (int)(gcMajorTime*1000.0), false );
      if (ZERO_BLOCKS_ON_ALLOCATION)
	VM.sysWrite(" ms (no zeroing) ");
      else {
	VM.sysWrite(" ms (zeroing ");
	VM.sysWrite( (int)(((gcEndTime - gcTimeBeforeZeroing)*1000.0)), false );
	VM.sysWrite(") ");
      }
      VM.sysWrite(" mature ");
      if ( matureAllocationIncreasing ) {
	VM.sysWrite("(inc) " );
	bytes = matureCurrentAddress.diff(appelHeap.start);
      }
      else {
	VM.sysWrite("(dec) " );
	bytes = appelHeap.end.diff(matureCurrentAddress);
      }
      VM.sysWrite(bytes,false);
      VM.sysWrite(" nurserySize " );
      VM.sysWrite(nurserySize,false);
      VM.sysWrite(">\n");
    }
  }	 
  
  
  // Check if the "integer" pointer points to a dead or live object.
  // If live, and in the FromSpace (ie has been marked and forwarded),
  // then update the integer pointer to the objects new location.
  // If dead, then force it live, copying it if in the FromSpace, marking it
  // and putting it on the workqueue for scanning.
  //
  // in this collector (copyGCgen) allocated objects with finalizers can
  // only be in mature space, the nursery, or large space...and so far, only
  // arrays exist in large space, and they do not have finalizers...but we
  // allow for large space objects anyway
  //
  // Called by ONE GC collector thread at the end of collection, after
  // all reachable object are marked and forwarded
  //
  static boolean processFinalizerListElement (VM_FinalizerListElement le) {

    VM_Address ref = le.value;
    Object objRef = VM_Magic.addressAsObject(ref);

    // Processing for object in "FromSpace" is same for minor & major collections.
    // For minor GCs, FromSpace is the Nursery, for major GCs it is the old mature space.
    //
    if ( ref.GE(minFromRef) && ref.LE(maxFromRef) ) {
      if (VM_AllocatorHeader.isForwarded(objRef)) {
	    le.move(VM_Magic.objectAsAddress(VM_AllocatorHeader.getForwardingPointer(objRef)));
	    return true;
	}
      else {
	  le.finalize(copyAndScanObject(ref, true)); 
	  return false;
      }
    }
    
    // for minor collections, objects in mature space are assumed live.
    // they are not moved, and le.value is OK
    if ( ! majorCollection ) {
      if ( matureAllocationIncreasing ) {
	if ( ref.GT(appelHeap.start) && ref.LE(matureSaveAddress.add(4)) ) return true;
      }
      else { /* matureAllocationDecreasing */
	if ( ref.GT(matureSaveAddress) && ref.LE(appelHeap.end.add(4)) ) return true;
      }
    }
    
    // if here, for minor & major collections, le.value should be for an object
    // in large space
    //
    if (largeHeap.refInHeap(ref)) {
	if (!majorCollection) return true;
	if (largeHeap.isLive(ref)) return true;
	largeHeap.mark(ref);  // dead but resuscitate
	VM_GCWorkQueue.putToWorkBuffer(ref);
	le.finalize(ref);                                // unchanged but still need to finalize
	return false;
    }

    if (bootHeap.refInHeap(ref)) return true;
    if (immortalHeap.refInHeap(ref)) return true;

    VM.sysWriteln("Bad finalizer element in unknown heap: address = ", ref);
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
   * @param location  address of a reference field
   */
  static VM_Address  processPtrValue ( VM_Address ref ) {
    
    if (ref.isZero()) return ref;
    
    if ( appelHeap.refInHeap(ref) ) {
	// always process objects in the Nursery (forward if not already forwarded)
	if (ref.GE(minFromRef) && ref.LE(maxFromRef) )
	    return copyAndScanObject(ref, true);  // return new reference
	return ref;  // old object - leave alone
    }
    
    // if a major collection, mark and scan all bootimage and large objects
    if ( bootHeap.refInHeap(ref) || immortalHeap.refInHeap(ref) ) {

	if ( !majorCollection ) return ref;
	if (!VM_AllocatorHeader.testAndMark(VM_Magic.addressAsObject(ref), BOOT_MARK_VALUE) )
	    return ref;   // object already marked with current mark value
	// marked a previously unmarked object, put to work queue for later scanning
	VM_GCWorkQueue.putToWorkBuffer( ref );
	return ref;
    }
    
    // large objects processed only on major GC but "new" objects always processed
    //    For now, just all large objects.
    if (largeHeap.refInHeap (ref)) {
	resetObjectBarrier(ref);
	if (!largeHeap.mark(ref)) 
	    VM_GCWorkQueue.putToWorkBuffer( ref ); 	// we marked it, so put to workqueue
	return ref;
    }

    VM.sysWriteln("processPtrValue encountered bad reference = ", ref);
    VM.assert(false);
    return null;

  } // procesPtrValue
  
}   // VM_Allocator
