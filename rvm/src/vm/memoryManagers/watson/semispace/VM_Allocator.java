/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Simple Semi-Space Copying Allocator/Collector
 * <p>
 * Heap Layout:
 * <pre>
 *  + ------------+-----------------------------+----------------------+
 *  | BootImage   |   first semispace           |   second semispace   | 
 *  +-------------+-----------------------------+----------------------+
 *        heapStart^                   heapMiddle^               heapEnd^
 * </pre>
 * Allocation (with PROCESSOR_LOCAL_ALLOCATE = true - the default):
 * <p>
 * Allocation if from 1 of the 2 semi-spaces, labeled "FromSpace".
 * Virtual Processors acquire processor local "chunks" from FromSpace, and
 * allocate sequentially within their local chunks.
 * <pre>
 *                |           FromSpace                      |   ToSpace            |
 *  +-------------+------------------------------------------+----------------------+
 *  | BootImage   | chunk | chunk | ...|->                   | reserved             |
 *  +-------------+------------------------------------------+----------------------+
 *                    areaCurrentAddress^          areaEndAddr^   
 * </pre>
 * Collection (with PROCESSOR_LOCAL_MATURE_ALLOCATE=true - the default):
 * <p>
 * Collection is initiated when a processor cannot acquire an new chunk.
 * The other semi-space is labeled "ToSpace". During collection, live
 * objects from FromSpace are copied to ToSpace. Collector threads
 * participating in the collection acquire space in ToSpace in chunks, and
 * allocate sequentially within their local chunks.
 * <pre>
 *                |            FromSpace              |        ToSpace              |
 *  +-------------+-----------------------------------+-----------------------------+
 *  | BootImage   |obj|obj| ...|obj|obj|obj|.....|obj|| chunk | chunk | ...|->      |
 *  +-------------+-----------------------------------+-----------------------------+
 *	                          |         areaEndAddr^         ^        
 *	                          |         toStartAddr^         |          ^toCurrentAddr
 *			          +---->----> copied obj ---->---+        
 *
 *  ProcessPointer(p) {
 *     if p->FromSpace { 
 *        attempt to mark p
 *        if (not previously marked) 
 *           copy object, set forwarding pointer, add to WorkQueue
 *        change p to point to ToSpace copy of object
 *     }
 *     else (p->BootImage || p->LargeObjectSpace) {
 *        attempt to mark p
 *        if (not previously marked) add to WorkQueue
 *     }
 *  }
 *
 *  Approximate Collection Process:
 *    Find Root References by scanning thread stacks & static variables (JTOC)
 *    For each root reference do ProcessPointer()
 *    Process WorkQueue references until empty, for each entry:
 *      1. scan the pointed to object for pointer fields
 *      2. execute ProcessPointer for each pointer field
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
 */
public class VM_Allocator
  implements VM_Constants, VM_GCConstants, VM_Uninterruptible, VM_Callbacks.ExitMonitor {

  private static final boolean debugNative = false;   // temporary flag for debugging new threads pkg

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
   * Instead, each VM_Processor zeros the chunks of heap it acquires inorder
   * to satisfy allocation requests.
   */
  static final boolean ZERO_BLOCKS_ON_ALLOCATION = true;

  /**
   * When true, causes -verbose:gc option to include bytes copied and free
   * in the current semi-space.
   */
  static final boolean VERBOSE_WITH_BYTES_FREE = true;
  
  /** When true, causes -verbose:gc option to include bytes free in large object space. */
  static final boolean VERBOSE_WITH_LARGESPACE_FREE = false;
  
  /** When true, print heap configuration when starting */
  static final boolean DISPLAY_OPTIONS_AT_BOOT = VM_CollectorThread.DISPLAY_OPTIONS_AT_BOOT;

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

  /** count times parallel GC threads attempt to mark the same object */
  private static final boolean COUNT_COLLISIONS = false;

  /**
   * When true, causes -verbosegc option to print per thread entry
   * and exit times for the first 3 rendezvous during collection.
   */
  static final boolean RENDEZVOUS_TIMES = false;

  /**
   * Initialize for boot image.
   */
  static void
    init () {
    gc_serialize = new Object();
    
    VM_GCLocks.init();    // to alloc lock fields used during GC (in bootImage)
    VM_GCWorkQueue.init();       // to alloc shared work queue
    VM_CollectorThread.init();   // to alloc its rendezvous arrays, if necessary
    
    if (ZERO_NURSERY_IN_PARALLEL) {
      zeroStart = new int[ 1 + VM_Scheduler.MAX_PROCESSORS];
      zeroBytes = new int[ 1 + VM_Scheduler.MAX_PROCESSORS];
    }
    
    // initialization for large object heap
    sysLockLarge         = new VM_ProcessorLock();      // serializes access to large space
    countLargeAlloc = new int[GC_LARGE_SIZES];
    largeSpaceAlloc = new short[GC_INITIAL_LARGE_SPACE_PAGES];
    large_last_allocated = 0;
    largeSpacePages = GC_INITIAL_LARGE_SPACE_PAGES;
    
    if (RENDEZVOUS_TIMES) {
      rendezvous1in =  new int[ 1 + VM_Scheduler.MAX_PROCESSORS];
      rendezvous1out =  new int[ 1 + VM_Scheduler.MAX_PROCESSORS];
      rendezvous2in =  new int[ 1 + VM_Scheduler.MAX_PROCESSORS];
      rendezvous2out =  new int[ 1 + VM_Scheduler.MAX_PROCESSORS];
      rendezvous3in =  new int[ 1 + VM_Scheduler.MAX_PROCESSORS];
      rendezvous3out =  new int[ 1 + VM_Scheduler.MAX_PROCESSORS];
    }
  }

  /**
   * Initialize for execution.
   */
  static void
    boot (VM_BootRecord thebootrecord) {
    int i;

    bootrecord = thebootrecord;	
    minBootRef = bootrecord.startAddress-OBJECT_HEADER_OFFSET;   // first ref in bootimage
    maxBootRef = bootrecord.freeAddress+4;      // last ref in bootimage
     
    smallHeapStartAddress = (bootrecord.freeAddress + 4095) & ~4095;  // start of heap, round to page
    smallHeapEndAddress = bootrecord.endAddress & ~4095;       // end of heap, round down to page
    smallHeapSize = smallHeapEndAddress - smallHeapStartAddress;
    
    heapMiddleAddress = ( smallHeapStartAddress + (smallHeapEndAddress - smallHeapStartAddress)/2 + 4095) & ~4095;
    
    // set FromSpace pointers to bounds of first semi-space
    fromStartAddress = smallHeapStartAddress;
    fromEndAddress = heapMiddleAddress;
    
     // initialize pointers used for allocation in the current semi-space
    areaCurrentAddress = fromStartAddress;
    areaEndAddress     = fromEndAddress;
    
    // use "matureCurrentAddress" for "toSpace" allocations during collection
    matureCurrentAddress = heapMiddleAddress;
    
    // need address of areaCurrentAddress (in JTOC) for atomic fetchAndAdd()
    // when JTOC moves, this must be reset
    // offset of areaCurrentAddress in JTOC is set (in JDK side) in VM_EntryPoints 
    addrAreaCurrentAddress = VM_Magic.getTocPointer() + VM_Entrypoints.areaCurrentAddressOffset;
    // likewise for matureCurrentAddress
    addrMatureCurrentAddress = VM_Magic.getTocPointer() + VM_Entrypoints.matureCurrentAddressOffset;
    
    if (COMPILE_FOR_TIMING_RUN) {
      // touch all heap pages, to avoid pagefaults overhead during timing runs
      for (i = smallHeapEndAddress - 4096; i >= smallHeapStartAddress; i = i - 4096)
    	 VM_Magic.setMemoryWord(i, 0);
    }
    
    // setup large object space
    largeHeapStartAddress = bootrecord.largeStart;
    largeHeapEndAddress = bootrecord.largeStart + bootrecord.largeSize;
    largeHeapSize = largeHeapEndAddress - largeHeapStartAddress;
    largeSpacePages = bootrecord.largeSize/4096;
    minLargeRef = largeHeapStartAddress-OBJECT_HEADER_OFFSET;   // first ref in large space
    maxLargeRef = largeHeapEndAddress+4;      // last ref in large space
    
    // Get the (full sized) arrays that control large object space
    largeSpaceMark  = new short[bootrecord.largeSize/4096 + 1];
    short[] temp  = new short[bootrecord.largeSize/4096 + 1];
    // copy any existing large object allocations into new alloc array
    // ...with this simple allocator/collector there may be none
    for (i = 0; i < GC_INITIAL_LARGE_SPACE_PAGES; i++)
      temp[i] = largeSpaceAlloc[i];
    largeSpaceAlloc = temp;
    
    maxHeapRef = maxLargeRef;                 // only used for debugging tests
    
    // to identify "int[]", for sync'ing arrays of ints that (may) contain code
    arrayOfIntType = VM_Array.getPrimitiveArrayType( 10 /*code for INT*/ );

    VM_GCUtil.boot();

    VM_Finalizer.setup();
    
    VM_Callbacks.addExitMonitor(new VM_Allocator());

    if (DISPLAY_OPTIONS_AT_BOOT) {
      VM.sysWrite("\n");
      VM.sysWrite("Semi-Space Copying Collector: Small Heap Size = ");
      VM.sysWrite(smallHeapEndAddress-smallHeapStartAddress);
      VM.sysWrite("\n");
      VM.sysWrite(" smallHeapStartAddress = "); VM.sysWriteHex(smallHeapStartAddress);
      VM.sysWrite("\n");
      VM.sysWrite(" smallHeapEndAddress   = "); VM.sysWriteHex(smallHeapEndAddress);
      VM.sysWrite("\n");
      VM.sysWrite(" heapMiddleAddress     = "); VM.sysWriteHex(heapMiddleAddress);
      VM.sysWrite("\n");
      VM.sysWrite("LargeHeapSize = "); VM.sysWrite(largeHeapSize);
      VM.sysWrite("\n");
      VM.sysWrite(" largeHeapStartAddress = "); VM.sysWriteHex(largeHeapStartAddress);
      VM.sysWrite("\n");
      VM.sysWrite(" largeHeapEndAddress   = "); VM.sysWriteHex(largeHeapEndAddress);
      VM.sysWrite("\n");

      if (ZERO_NURSERY_IN_PARALLEL)
      	 VM.sysWrite("Compiled with ZERO_NURSERY_IN_PARALLEL on\n");
      if (ZERO_BLOCKS_ON_ALLOCATION)
      	 VM.sysWrite("Compiled with ZERO_BLOCKS_ON_ALLOCATION on\n");
      if (PROCESSOR_LOCAL_ALLOCATE)
      	 VM.sysWrite("Compiled with PROCESSOR_LOCAL_ALLOCATE on\n");
      if (PROCESSOR_LOCAL_MATURE_ALLOCATE)
      	 VM.sysWrite("Compiled with PROCESSOR_LOCAL_MATURE_ALLOCATE on\n");
      if ( writeBarrier )
	VM.sysWrite("WARNING - semi-space collector compiled with write barriers\n");
    }
  }  // boot()

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
  public static void
  gc () {
    if (GC_TRIGGERGC)
      VM_Scheduler.trace("VM_Allocator","GC triggered by external call to gc()");
    gc1();
  }

  /**
   * VM internal method to initiate a collection
   */
  static void
  gc1 () {
    // if here and in a GC thread doing GC then it is a system error,
    //  GC thread must have attempted to allocate.
    if ( VM_Thread.getCurrentThread().isGCThread ) {
      VM.sysFail("VM_Allocator: Garbage Collection Failure: GC Thread attempting to allocate during GC");
      // crash("VM_Allocator: Garbage Collection Failure: GC Thread asking for GC");
    }
  
    // notify GC threads to initiate collection, wait until done
    VM_CollectorThread.collect(VM_CollectorThread.collect);
  }  // gc1

  public static boolean
  gcInProgress() {
    return gcInProgress;
  }

  /**
   * Get total amount of memory.  Includes both full size of the
   * small object heap and the size of the large object heap.
   *
   * @return the number of bytes
   */
  public static long
  totalMemory () {
    return (smallHeapEndAddress-smallHeapStartAddress + largeHeapSize);    
  }
  
  /**
   * Get the number of bytes currently available for object allocation.
   * In this collector, returns bytes available in the current semi-space,
   * and does not include large object space available.
   *
   * @return number of bytes available
   */
  public static long
  freeMemory () {
    return (areaEndAddress - areaCurrentAddress);
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
   * @return the address of the first byte of the allocated region
   */
  static int
  getHeapSpace ( int size ) {
    int addr;
    VM_Thread t;
    
    if (VM.VerifyAssertions) {
      t = VM_Thread.getCurrentThread();
      VM.assert( gcInProgress == false );
      VM.assert( (t.disallowAllocationsByThisThread == false)
  		  && ((size & 3) == 0) );
    }
    
    // if large, allocate from large object space
    if (size > SMALL_SPACE_MAX) {
      addr = getlargeobj(size);
      if (addr == -2) {  // insufficient large space, try a GC
	if (GC_TRIGGERGC) VM_Scheduler.trace("VM_Allocator","GC triggered by large object request",size);
	gc1();
	addr = getlargeobj(size);     // try again after GC
	if ( addr == -2 ) {
	  // out of space...REALLY...or maybe NOT ?
	  // maybe other user threads got the free space first, after the GC
	  //
	  outOfLargeSpace( size );
	}
      }
      return addr;
    }  // end of - (size > SMALL_SPACE_MAX)

    // now handle normal allocation of small objects in heap
    
    if (PROCESSOR_LOCAL_ALLOCATE) {
      VM_Processor st = VM_Processor.getCurrentProcessor();
      if ( (st.localCurrentAddress + size ) <= st.localEndAddress ) {
  	 addr = st.localCurrentAddress;
  	 st.localCurrentAddress = st.localCurrentAddress + size;
      }
      else { // not enough space in local chunk, get the next chunk for allocation
  	 addr = VM_Synchronization.fetchAndAddWithBound(VM_Magic.addressAsObject(addrAreaCurrentAddress), 0, CHUNK_SIZE, areaEndAddress );
  	 if ( addr != -1 ) {
  	   st.localEndAddress = addr + CHUNK_SIZE;
  	   st.localCurrentAddress = addr + size;
  	   if (ZERO_BLOCKS_ON_ALLOCATION)
  	     VM_Memory.zeroPages(addr,CHUNK_SIZE);
  	 }
  	 else { // no space in system thread and no more chunks, do garbage collection
  	   if (GC_TRIGGERGC) VM_Scheduler.trace("VM_Allocator","GC triggered by request for small space CHUNK");
  	   gc1();
  	   
  	   // retry request for space
  	   // NOTE! may now be running on a DIFFERENT SYSTEM THREAD than before GC
  	   //
  	   st = VM_Processor.getCurrentProcessor();
  	   if ( (st.localCurrentAddress + size ) <= st.localEndAddress ) {
  	     addr = st.localCurrentAddress;
  	     st.localCurrentAddress = st.localCurrentAddress + size;
  	   }
  	   else {
  	     // not enough space in local chunk, get the next chunk for allocation
  	     //
  	     addr = VM_Synchronization.fetchAndAddWithBound(VM_Magic.addressAsObject(addrAreaCurrentAddress), 0, CHUNK_SIZE, areaEndAddress );
  	     if ( addr != -1 ){
  	       st.localEndAddress = addr + CHUNK_SIZE;
  	       st.localCurrentAddress = addr + size;
  	       if (ZERO_BLOCKS_ON_ALLOCATION)
  		 VM_Memory.zeroPages(addr,CHUNK_SIZE);
  	     }
  	     else {
	       // Unable to get chunk, after GC. Maybe should retry GC again, some
	       // number of times. For now, call outOfMemory to print message and exit
	       //
	       outOfMemory();
  	     } 
  	   }
  	 }  // else do gc
      }  // else get new chunk from global heap
    }
    else { // OLD CODE - all allocates from global heap 
      
      addr =  VM_Synchronization.fetchAndAddWithBound(VM_Magic.addressAsObject(addrAreaCurrentAddress), 0, size, areaEndAddress );
      if ( addr == -1 ) {
  	 // do garbage collection, check if get space for object
  	 if (GC_TRIGGERGC) VM.sysWrite("GC triggered by small object request\n");
  	 gc1();
  	 addr =  VM_Synchronization.fetchAndAddWithBound(VM_Magic.addressAsObject(addrAreaCurrentAddress), 0, size, areaEndAddress );
  	 if ( addr == -1 ) {
  	   // out of space...REALLY
  	   // BUT, maybe other user threads got the free space first, after the GC
	   // For now, just give up, call outOfMemory to print message and exit
	   //
	   outOfMemory();
  	 }
      }
    }  // end of ! PROCESSOR_LOCAL_ALLOCATE (OLD CODE)
    
    // addr -> beginning of allocated region
    
    // if from space was filled with strange bits, then must zero now
    // UNLESS we are allocating blocks to processors, and those block are
    // being zeroed when allocated 
    if (VM.AllocatorZapFromSpace && ! ZERO_BLOCKS_ON_ALLOCATION)
      VM_Memory.zero(addr, addr+size);
    
    return addr;
  }  // getHeapSpace

  
  /**
   * Allocate a scalar object. Fills in the header for the object,
   * and set all data fields to zero.
   *
   * @param size         size of object (including header), in bytes
   * @param tib          type information block for object
   * @param hasFinalizer hasFinalizer flag
   *
   * @return the reference for the allocated object
   */
  public static Object
  allocateScalar (int size, Object[] tib, boolean hasFinalizer)
    throws OutOfMemoryError {
  
    Object new_ref;
  
    VM_Magic.pragmaInline();	// make sure this method is inlined
    
    if (VM.BuildForEventLogging && VM.EventLoggingEnabled)
      VM_EventLogger.logObjectAllocationEvent();
  
    // assumption: collector has previously zero-filled the space
    // assumption: object sizes are always a word multiple,
    // so we don't need to worry about address alignment or rounding
    //
    //  |<--------------------size---------------->|
    //  .                            |<--hdr size->|
    //  .                            |<--- hdr offset--->|
    //  +-------------------+--------+------+------+-----+-----+
    //  |         ...field1 | field0 | tib  |status| free| free|
    //  +-------------------+--------+------+------+-----+-----+
    //                      (new) areaCurrentAddress^     ^new_ref
    //   ^(prevoius) areaCurrentAddress
  
    // if compiled for processor local "chunks", assume size is "small" and attempt to
    // allocate locally, if the local allocation fails, call the heavyweight allocate
    if (PROCESSOR_LOCAL_ALLOCATE == true) {
      VM_Processor st = VM_Processor.getCurrentProcessor();

      int new_current = st.localCurrentAddress + size;
  	  
      if ( new_current <= st.localEndAddress ) {
	st.localCurrentAddress = new_current;   // increment allocation pointer
  	// note - ref for an object is 4 bytes beyond the object
  	new_ref = VM_Magic.addressAsObject(new_current - (SCALAR_HEADER_SIZE + OBJECT_HEADER_OFFSET));
  	VM_Magic.setObjectAtOffset(new_ref, OBJECT_TIB_OFFSET, tib);
  	// set mark bit in status word, if initial (unmarked) value is not 0      
  	if (MARK_VALUE==0) VM_Magic.setIntAtOffset(new_ref, OBJECT_STATUS_OFFSET, 1 );
  	if( hasFinalizer )  VM_Finalizer.addElement(new_ref);
	if (VM_Configuration.BuildWithRedirectSlot) 
	    VM_Magic.setObjectAtOffset(new_ref, OBJECT_REDIRECT_OFFSET, new_ref);
  	return new_ref;
      }
      else
	return cloneScalar( size, tib, null );
    }
    else { // OLD CODE - all allocates from global heap 
      int firstByte = getHeapSpace(size);
      new_ref = VM_Magic.addressAsObject(firstByte + size - (SCALAR_HEADER_SIZE + OBJECT_HEADER_OFFSET));
      VM_Magic.setObjectAtOffset(new_ref, OBJECT_TIB_OFFSET, tib); // set .tib field
      if (MARK_VALUE==0) VM_Magic.setIntAtOffset(new_ref, OBJECT_STATUS_OFFSET, 1 );
      if( hasFinalizer )  VM_Finalizer.addElement(new_ref);
      if (VM_Configuration.BuildWithRedirectSlot) 
	  VM_Magic.setObjectAtOffset(new_ref, OBJECT_REDIRECT_OFFSET, new_ref);
      return new_ref;
    }
  }   // end of allocateScalar() with finalizer flag
  
  /**
   * Allocate a scalar object & optionally clone another object.
   * Fills in the header for the object.  If a clone is specified,
   * then the data fields of the clone are copied into the new
   * object.  Otherwise, the data fields are set to 0.
   *
   * @param size     size of object (including header), in bytes
   * @param tib      type information block for object
   * @param cloneSrc object from which to copy field values
   *                 (null --> set all fields to 0/null)
   *
   * @return the reference for the allocated object
   */
  public static Object
  cloneScalar (int size, Object[] tib, Object cloneSrc)
    throws OutOfMemoryError {
    
    boolean hasFinalizer;

    VM_Magic.pragmaNoInline();	// prevent inlining - this is the infrequent slow allocate
    
    hasFinalizer = VM_Magic.addressAsType(VM_Magic.getMemoryWord(VM_Magic.objectAsAddress(tib))).hasFinalizer();
  
    if (VM.BuildForEventLogging && VM.EventLoggingEnabled) VM_EventLogger.logObjectAllocationEvent();
  
    int firstByte = getHeapSpace(size);
  
    Object objRef = VM_Magic.addressAsObject(firstByte + size - SCALAR_HEADER_SIZE - OBJECT_HEADER_OFFSET);
    
    VM_Magic.setObjectAtOffset(objRef, OBJECT_TIB_OFFSET, tib);
    
    // set mark bit in status word, if initial (unmarked) value is not 0      
    if (MARK_VALUE==0) VM_Magic.setIntAtOffset(objRef, OBJECT_STATUS_OFFSET, 1);
    
    if (VM_Configuration.BuildWithRedirectSlot) 
	VM_Magic.setObjectAtOffset(objRef, OBJECT_REDIRECT_OFFSET, objRef);

    // initialize object fields with data from passed in object to clone
    if (cloneSrc != null) {
      int cnt = size - SCALAR_HEADER_SIZE;
      int src = VM_Magic.objectAsAddress(cloneSrc) + OBJECT_HEADER_OFFSET - cnt;
      int dst = VM_Magic.objectAsAddress(objRef) + OBJECT_HEADER_OFFSET - cnt;
      VM_Memory.aligned32Copy(dst, src, cnt);
    }
    
    if( hasFinalizer )  VM_Finalizer.addElement(objRef);
    
    return objRef; // return object reference
  }  // cloneScalar

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
  public static Object
  allocateArray (int numElements, int size, Object[] tib)
    throws OutOfMemoryError {
  
     VM_Magic.pragmaInline();	// make sure this method is inlined

     Object objAddress;
  
     if (VM.BuildForEventLogging && VM.EventLoggingEnabled)
       VM_EventLogger.logObjectAllocationEvent();
  
     // assumption: collector has previously zero-filled the space
     //
     //  |<--------------------size---------------->|
     //  |<-----hdr size---->|                      .
     //  |<-----hdr offset-->|                      .
     //  +------+------+-----+------+---------------+----+
     //  | tib  |status| len | elt0 |     ...       |free|
     //  +------+------+-----+------+---------------+----+
     //   ^memAddr             ^objAddress           ^areaCurrentAddress
     //
  
     // note: array size might not be a word multiple,
     // so we must round up size to preserve alignment for future allocations
  
     size = (size + 3) & ~3;     // round up request to word multiple
  
     // if compiled for processor local "chunks", and size is "small", attempt to
     // allocate locally, if the local allocation fails, call the heavyweight allocate
     if (PROCESSOR_LOCAL_ALLOCATE == true) {
       if (size <= SMALL_SPACE_MAX) {
  	 VM_Processor st = VM_Processor.getCurrentProcessor();
  	 int new_current = st.localCurrentAddress + size;
  	 if ( new_current <= st.localEndAddress ) {
  	   objAddress = VM_Magic.addressAsObject(st.localCurrentAddress - OBJECT_HEADER_OFFSET);  // ref for new array
  	   st.localCurrentAddress = new_current;            // increment processor allocation pointer
  	   // set tib field in header
  	   VM_Magic.setObjectAtOffset(objAddress, OBJECT_TIB_OFFSET, tib);
  	   // set status field, only if marking with 0 (ie new object is unmarked == 1)
  	   if (MARK_VALUE==0) VM_Magic.setIntAtOffset(objAddress, OBJECT_STATUS_OFFSET, 1 );
  	   // set .length field
  	   VM_Magic.setIntAtOffset(objAddress, ARRAY_LENGTH_OFFSET, numElements);
	   if (VM_Configuration.BuildWithRedirectSlot) 
	       VM_Magic.setObjectAtOffset(objAddress, OBJECT_REDIRECT_OFFSET, objAddress);
  	   return objAddress;
	 }
       }
       // if size too large, or not space in current chunk, call heavyweight allocate
       return cloneArray( numElements, size, tib, null );
     }
     else {	  // old non chunking code...
       int memAddr = getHeapSpace( size );  // start of new object
       objAddress = VM_Magic.addressAsObject(memAddr - OBJECT_HEADER_OFFSET);
       // set .tib field
       VM_Magic.setObjectAtOffset(objAddress, OBJECT_TIB_OFFSET, tib);
       // set mark bit in status word, if initial (unmarked) value is not 0
       if (MARK_VALUE==0) VM_Magic.setIntAtOffset(objAddress, OBJECT_STATUS_OFFSET, 1);
       // set .length field
       VM_Magic.setIntAtOffset(objAddress, ARRAY_LENGTH_OFFSET, numElements);
       if (VM_Configuration.BuildWithRedirectSlot) 
	   VM_Magic.setObjectAtOffset(objAddress, OBJECT_REDIRECT_OFFSET, objAddress);
       return objAddress;	 // return object reference
     }
  }  // allocateArray

  /**
   * Allocate an array object and optionally clone another array.
   * Fills in the header for the object and sets the array length
   * to the specified length.  If an object to clone is specified,
   * then the data elements of the clone are copied into the new
   * array.  Otherwise, the elements are set to zero.
   *
   * @param numElements  number of array elements
   * @param size         size of array object (including header), in bytes
   * @param tib          type information block for array object
   * @param cloneSrc     object from which to copy field values
   *                     (null --> set all fields to 0/null)
   *
   * @return the reference for the allocated array object 
   */
  public static Object
  cloneArray (int numElements, int size, Object[] tib, Object cloneSrc)
    throws OutOfMemoryError {
  
    if (VM.BuildForEventLogging && VM.EventLoggingEnabled)
      VM_EventLogger.logObjectAllocationEvent();

    VM_Magic.pragmaNoInline();	// prevent inlining - this is the infrequent slow allocate
  
     size = (size + 3) & ~3;                         // round up request to word multiple
  
     int firstByte = getHeapSpace(size);
  
     Object objRef = VM_Magic.addressAsObject(firstByte - OBJECT_HEADER_OFFSET);
  
     VM_Magic.setObjectAtOffset(objRef, OBJECT_TIB_OFFSET, tib);
  
     if (MARK_VALUE==0) VM_Magic.setIntAtOffset(objRef, OBJECT_STATUS_OFFSET, 1);
  
     VM_Magic.setIntAtOffset(objRef, ARRAY_LENGTH_OFFSET, numElements);
  
     if (VM_Configuration.BuildWithRedirectSlot) 
	 VM_Magic.setObjectAtOffset(objRef, OBJECT_REDIRECT_OFFSET, objRef);

     // initialize array elements
     if (cloneSrc != null) {
       int cnt = size - ARRAY_HEADER_SIZE;
       int src = VM_Magic.objectAsAddress(cloneSrc);
       int dst = VM_Magic.objectAsAddress(objRef);
       VM_Memory.aligned32Copy(dst, src, cnt);
     }

     return objRef;  // return reference for allocated array
  }  // cloneArray

  // *************************************
  // implementation
  // *************************************

  static final int      TYPE = 7;

  /** Declares that this collector may move objects during collction */
  static final boolean movesObjects = true;

  /** Declares that this collector requires that compilers generate the write barrier */
  static final boolean writeBarrier = false;

  // VM_Type of int[], to detect arrays that (may) contain code
  // and will thus require a d-cache flush before the code is executed.
  static VM_Type arrayOfIntType;  // VM_Type of int[], to detect code objects for sync'ing
  
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
  
  // following for managing large object space
  private static VM_ProcessorLock sysLockLarge;        // serializes access to large space
  private final static int GC_LARGE_SIZES = 20;           // for statistics  
  private final static int GC_INITIAL_LARGE_SPACE_PAGES = 200; // for early allocation of large objs
  private static int           largeHeapStartAddress;
  private static int           largeHeapEndAddress;
  private static int           largeSpacePages;
  private static int           largeHeapSize;
  private static int		large_last_allocated;   // where to start search for free space
  private static short[]	largeSpaceAlloc;	// used to allocate in large space
  private static short[]	largeSpaceMark;		// used to mark large objects
  private static int[]	countLargeAlloc;	//  - count sizes of large objects alloc'ed
  private static int minLargeRef;
  private static int maxLargeRef;

  private static int minBootRef;
  private static int maxBootRef;
    //   static int minHeapRef;
  private static int maxHeapRef;
  
  static VM_BootRecord	 bootrecord;
  
  private static int smallHeapStartAddress;
  private static int smallHeapEndAddress;
  private static int heapMiddleAddress;
  private static int smallHeapSize;

  private static int fromStartAddress;
  private static int fromEndAddress;
  private static int toStartAddress;
  private static int toEndAddress;
  
  // use matureCurrentAddress variables for synchronized allocations in ToSpace
  // by GC threads during collection
  static int matureCurrentAddress;         // current end of "ToSpace"
  static int addrMatureCurrentAddress;     // address of above (in the JTOC)
  
  private static int minFromRef;
  private static int maxFromRef;
  
  static int areaCurrentAddress;
  static int addrAreaCurrentAddress;
  static int areaEndAddress;
  
  static boolean gcInProgress;      // true if collection in progress, initially false
  static int gcCount = 0;           // number of minor collections
  static int gcMajorCount = 0;      // number of major collections
  
  private static double gcStartTime = 0;
  private static double gcEndTime = 0;
  private static double gcTimeBeforeZeroing = 0;
  private static double gcMinorTime;             // for timing gc times

  // accumulated times & counts for sysExit callback printout
  private static double maxGCTime = 0.0;           
  private static double totalGCTime = 0.0;           
  private static double totalStartTime = 0.0;           
  private static long   totalBytesCopied = 0;
  private static long   maxBytesCopied = 0;
  private static int    collisionCount = 0;

  private static double totalInitTime;
  private static double totalStacksAndStaticsTime;
  private static double totalScanningTime;
  private static double totalFinalizeTime;
  private static double totalFinishTime;
  
  // timestamps for TIME_GC_PHASES output
  private static double gcInitDoneTime = 0;
  private static double gcStacksAndStaticsDoneTime = 0;    
  private static double gcScanningDoneTime = 0;
  private static double gcFinalizeDoneTime = 0;
  
  private static Object                   gc_serialize = null;   // allocated in bootImage in init()
  
  // following used when RENDEZVOUS_TIMES is on
  private static int rendezvous1in[] = null;
  private static int rendezvous1out[] = null;
  private static int rendezvous2in[] = null;
  private static int rendezvous2out[] = null;
  private static int rendezvous3in[] = null;
  private static int rendezvous3out[] = null;
  
  // following only used when ZERO_NURSERY_IN_PARALLEL is on
  private static int zeroStart[] = null;     // start of nursery region for processors to zero
  private static int zeroBytes[] = null;     // number of bytes to zero
  
  private static final boolean GC_TRIGGERGC = false;      // prints what triggered each GC
  private static final boolean TRACE = false;
  private static final boolean TRACE_STACKS = false;

  // FromSpace object are "marked" if mark bit in statusword == MARK_VALUE
  // if "marked" == 0, then storing an aligned forwarding ptr also "marks" the
  // original FromSpace copy of the object (ie. GC's go faster
  // if "marked" == 1, then allocation of new objects do not require a store
  // to set the markbit on (unmarked), but returning a forwarding ptr requires
  // masking out the mark bit ( ie allocation faster, GC slower )
  //
  static final int MARK_VALUE = 1;        // to mark new/young objects already forwarded
  
  // following is bit pattern written into status word during forwarding
  // right bit should indicate "marked" next bit indicates "busy"
  // if (MARK_VALUE==0) use -2 which has low-order 2 bits (10)
  // if (MARK_VALUE==1) use -5 which has low-order 2 bits (11)
  // ...could use -1, but that is too common, -5 will be more recognizable  
  // following produces -2 or -5
  static final int BEING_FORWARDED_PATTERN = -2 - (3*MARK_VALUE);
  
  private static int BOOT_MARK_VALUE = 0;   // to mark bootimage objects during major GCs
  
  // ------- End of Statics --------

  static void
  gcSetup ( int numSysThreads ) {
    VM_GCWorkQueue.workQueue.initialSetup(numSysThreads);
  } // gcSetup

  /**
   * Print OutOfMemoryError message and exit.
   * TODO: make it possible to throw an exception, but this will have
   * to be done without doing further allocations (or by using temp space)
   */
  private static void
  outOfMemory () {

    // First thread to be out of memory will write out the message,
    // and issue the shutdown. Others just spinwait until the end.

    sysLockLarge.lock();
    if (!outOfMemoryReported) {
      outOfMemoryReported = true;
      VM_Processor.getCurrentProcessor().disableThreadSwitching();
      VM.sysWrite("\nOutOfMemoryError\n");
      VM.sysWrite("Insufficient heap size for semi-space collector\n");
      VM.sysWrite("Current heap size = ");
      VM.sysWrite(smallHeapSize, false);
      VM.sysWrite("\nSpecify a larger heap using -X:h=nnn command line argument\n");
      // call shutdown while holding the processor lock
      VM.shutdown(-5);
    }
    else {
      sysLockLarge.release();
      while( outOfMemoryReported == true );  // spin until VM shuts down
    }
  }

  /**
   * Print OutOfMemoryError message and exit.
   * TODO: make it possible to throw an exception, but this will have
   * to be done without doing further allocations (or by using temp space)
   */

  private static void
  outOfLargeSpace ( int size ) {

    // First thread to be out of memory will write out the message,
    // and issue the shutdown. Others just spinwait until the end.

    sysLockLarge.lock();
    if (!outOfMemoryReported) {
      outOfMemoryReported = true;
      VM_Processor.getCurrentProcessor().disableThreadSwitching();
      VM.sysWrite("\nOutOfMemoryError - Insufficient Large Object Space\n");
      VM.sysWrite("Unable to allocate large object of size = ");
      VM.sysWrite(size, false);
      VM.sysWrite("\nCurrent Large Space Size = ");
      VM.sysWrite(largeHeapSize, false);
      VM.sysWrite("\nSpecify a bigger large object heap using -X:lh=nnn command line argument\n");
      // call shutdown while holding the processor lock
      VM.shutdown(-5);
    }
    else {
      sysLockLarge.release();
      while( outOfMemoryReported == true );  // spin until VM shuts down
    }
  }


  private static void
  prepareNonParticipatingVPsForGC() {

    //-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS
    // alternate implementation of jni
    // all RVM VM_Processors participate in every collection
    return;
    //-#else

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
	  t.contextRegisters.setInnermost( 0 /*ip*/, t.jniEnv.JNITopJavaFP );
	}

	if (PROCESSOR_LOCAL_MATURE_ALLOCATE) {
	  vp.localMatureCurrentAddress = 0;
	  vp.localMatureEndAddress = 0;
	}

	// If writebarrier is being generated, presumably for measurement purposes, since
	// it is not used in this non-generational collector, then reset the write buffers
	// to empty so they don't overflow
	//
	if (writeBarrier)
	  vp.modifiedOldObjectsTop = VM_Magic.objectAsAddress(vp.modifiedOldObjects) - 4;
      }
    }
    //-#endif
  }

  private static void
  prepareNonParticipatingVPsForAllocation() {

    //-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS
    // alternate implementation of jni
    // all RVM VM_Processors participate in every collection
    return;
    //-#else

    // include NativeDaemonProcessor in following loop over processors
    for (int i = 1; i <= VM_Scheduler.numProcessors+1; i++) {
      VM_Processor vp = VM_Scheduler.processors[i];

      if (vp == null) continue;   // the last VP (nativeDeamonProcessor) may be null

      int vpStatus = VM_Processor.vpStatus[vp.vpStatusIndex];
      if ((vpStatus == VM_Processor.BLOCKED_IN_NATIVE) || (vpStatus == VM_Processor.BLOCKED_IN_SIGWAIT)) {
        // Did not participate in GC. Reset VPs allocation pointers so subsequent
        // allocations will acquire a new local block from the new nursery
        vp.localCurrentAddress = 0;
        vp.localEndAddress     = 0;
      }
    }
    //-#endif
  }


  /**
   * Perform a garbage collection.  Called from VM_CollectorThread run
   * method by each collector thread participating in a collection.
   */
  static void
  collect () {
    int i;
    short[]	shorttemp;	// used to exchange large object Alloc and Mark
    boolean   selectedGCThread = false;  // indicates 1 thread to generate output
 
    // ASSUMPTIONS:
    // initGCDone flag is false before first GC thread enter collect
    // InitLock is reset before first GC thread enter collect
    //
 
    // following just for timing GC time
    double tempTime;        // in milliseconds
 
    if (VM.BuildForEventLogging && VM.EventLoggingEnabled)
      VM_EventLogger.logGarbageCollectionEvent();
 
    int mypid = VM_Processor.getCurrentProcessorId();  // id of processor running on

    // set running threads context regs so that a scan of its stack
    // will start at the caller of collect (ie. VM_CollectorThread.run)
    //
    int fp = VM_Magic.getFramePointer();
    int caller_ip = VM_Magic.getReturnAddress(fp);
    int caller_fp = VM_Magic.getCallerFramePointer(fp);
    VM_Thread.getCurrentThread().contextRegisters.setInnermost( caller_ip, caller_fp );
 
    if (TRACE) VM_Scheduler.trace("VM_Allocator","in collect starting GC");
 
    // BEGIN SINGLE GC THREAD SECTION - GC INITIALIZATION
 
    if ( VM_GCLocks.testAndSetInitLock() ) {
      
      gcStartTime = VM_Time.now();         // start time for GC
      totalStartTime += gcStartTime - VM_CollectorThread.startTime; //time since GC requested
      
      if (VM.VerifyAssertions) VM.assert( initGCDone == false );  
      
      gcCount++;

      // setup common workqueue for num VPs participating, used to be called once.
      // now count varies for each GC, so call for each GC   SES 050201
      //
      VM_GCWorkQueue.workQueue.initialSetup(VM_CollectorThread.numCollectors());
      
      // VM_GCWorkQueue.workQueue.reset(); // do initialsetup instead 050201
      
      if (TRACE||GC_TRIGGERGC)
	VM_Scheduler.trace("VM_Allocator", "gc initialization for gcCount", gcCount);

      gcInProgress = true;
      gcDone = false;
      
      // set bounds of possible FromSpace refs (of objects to be copied)
      minFromRef = fromStartAddress - OBJECT_HEADER_OFFSET;
      maxFromRef = fromEndAddress + 4;
      
      // setup ToSpace to be the other semi-space
      // note: the variable named "matureCurrentAddress" is used to track
      // the current end of ToSpace
      if ( fromStartAddress == smallHeapStartAddress ) {
        toStartAddress = matureCurrentAddress =  heapMiddleAddress;
        toEndAddress = smallHeapEndAddress;
      }
      else {
        toStartAddress = matureCurrentAddress =  smallHeapStartAddress;
        toEndAddress = heapMiddleAddress;
      }
 
      // invert the mark_flag value, used for marking BootImage objects
      BOOT_MARK_VALUE = BOOT_MARK_VALUE ^ OBJECT_GC_MARK_MASK; 
 
      // Now initialize the large object space mark array
      VM_Memory.zero(VM_Magic.objectAsAddress(largeSpaceMark), 
		     VM_Magic.objectAsAddress(largeSpaceMark) + 2*largeSpaceMark.length);
      
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
    
      if (TIME_GC_PHASES)  gcInitDoneTime = VM_Time.now();
      if (RENDEZVOUS_WAIT_TIME) tempTime = 0.0;    // 0 time in initialization spinwait

      // set Done flag to allow other GC threads to begin processing
      initGCDone = true;
      
    } // END SINGLE GC THREAD SECTION - GC INITIALIZATION

    else {
      // Each GC thread must wait here until initialization is complete
      // this should be short, if necessary at all, so we spin instead of sysYiel
      //
      // It is NOT required that all GC threads reach here before any can proceed
      //
      if (RENDEZVOUS_WAIT_TIME) tempTime = VM_Time.now();
      while( initGCDone == false ); // spin until initialization finished
      VM_Magic.isync();             // prevent following inst. from moving infront of waitloop
      if (RENDEZVOUS_WAIT_TIME) tempTime = VM_Time.now() - tempTime;  // time in initialization spin wait    
      
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

    // add in initialization spin wait time to accumulated collection rendezvous time
    if (RENDEZVOUS_WAIT_TIME) mylocal.rendezvousWaitTime += tempTime;

    // following rendezvous seems to be necessary, we are not sure why. Without it,
    // some processors proceed into finding roots, before all gc threads have
    // executed the above gc_initProcessor, and this seems related to the failure.
    // 
    if (RENDEZVOUS_WAIT_TIME) tempTime = VM_Time.now();
    if (RENDEZVOUS_TIMES) rendezvous1in[mypid] = (int)((VM_Time.now() - gcStartTime)*1000000);
    VM_CollectorThread.gcBarrier.rendezvous();
    if (RENDEZVOUS_TIMES) rendezvous1out[mypid] = (int)((VM_Time.now() - gcStartTime)*1000000);
    if (RENDEZVOUS_WAIT_TIME) mylocal.rendezvousWaitTime += VM_Time.now() - tempTime;
         
    // Begin finding roots for this collection.
    // roots are (fromSpace) object refs in the jtoc or on the stack.
    // For each unmarked root object, it is marked, copied to toSpace, and
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

    if (TRACE) VM_Scheduler.trace("VM_Allocator", "at barrier before emptyWorkQueue");
    if (RENDEZVOUS_WAIT_TIME) tempTime = VM_Time.now();
    if (RENDEZVOUS_TIMES) rendezvous2in[mypid] = (int)((VM_Time.now() - gcStartTime)*1000000);
    VM_CollectorThread.gcBarrier.rendezvous();
    if (RENDEZVOUS_TIMES) rendezvous2out[mypid] = (int)((VM_Time.now() - gcStartTime)*1000000);
    if (RENDEZVOUS_WAIT_TIME) mylocal.rendezvousWaitTime += VM_Time.now() - tempTime;
    
    // have processor 1 record timestame for end of scanning stacks & statics
    if (TIME_GC_PHASES && (mylocal.gcOrdinal == 1))
      gcStacksAndStaticsDoneTime = VM_Time.now(); // for time scanning stacks & statics

    // each GC thread processes its own work queue until empty
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

      /*** following reset() will wait for previous use of workqueue to finish
	   ie. all threads to leave.  So this rendezvous is not necessary (we hope)
      // This rendezvous is necessary because some "slow" gc threads may still be
      // in emptyWorkQueue (in VM_GCWorkQueue.getBufferAndWait) and have not seen
      // the completionFlag==true.  The following call to reset will reset that
      // flag to false, possibly leaving the slow GC threads stuck.  This rendezvous
      // ensures that all threads have left the previous emptyWorkQueue, before
      // doing the reset. (We could make reset smarter, and have it wait until
      // the threadsWaiting count returns to 0, before doing the reset - TODO)
      //
      if (RENDEZVOUS_WAIT_TIME) tempTime = VM_Time.now();
      if (RENDEZVOUS_TIMES) rendezvous3in[mypid] = (int)((VM_Time.now() - gcStartTime)*1000000);
      VM_CollectorThread.gcBarrier.rendezvous();
      if (RENDEZVOUS_TIMES) rendezvous3out[mypid] = (int)((VM_Time.now() - gcStartTime)*1000000);
      if (RENDEZVOUS_WAIT_TIME) mylocal.rendezvousWaitTime += VM_Time.now() - tempTime;
      ***/

      if (mylocal.gcOrdinal == 1) {

	VM_GCWorkQueue.workQueue.reset();   // reset work queue shared control variables
	
	// one thread scans the hasFinalizer list for dead objects.  They are made live
	// again, and put into that threads work queue buffers.
	//
	VM_Finalizer.moveToFinalizable();
      }
      
      // ALL threads have to wait to see if any finalizable objects are found
      if (RENDEZVOUS_WAIT_TIME) tempTime = VM_Time.now();
      VM_CollectorThread.gcBarrier.rendezvous();
      if (RENDEZVOUS_WAIT_TIME) mylocal.rendezvousWaitTime += VM_Time.now() - tempTime;
     
      if (VM_Finalizer.foundFinalizableObject) {

	// Some were found. Now ALL threads execute emptyWorkQueue again, this time
	// to mark and keep live all objects reachable from the new finalizable objects.
	//
	gc_emptyWorkQueue();

	//	if (RENDEZVOUS_WAIT_TIME) tempTime = VM_Time.now();
	//	VM_CollectorThread.gcBarrier.rendezvous();
	//	if (RENDEZVOUS_WAIT_TIME) mylocal.rendezvousWaitTime += VM_Time.now() - tempTime;
      }
    }  //  end of Finalization Processing

    //
    // gcDone flag has been set to false earlier
    //
    if ( VM_GCLocks.testAndSetFinishLock() ) {

      // set ending times for preceeding finalization or scanning phase
      // do here where a sync (below) will push value to memory
      if (TIME_GC_PHASES)  gcFinalizeDoneTime = VM_Time.now();
      
      // BEGIN SINGLE GC THREAD SECTION FOR END OF GC

      int saveFromStartAddress =  fromStartAddress;

      if (VM.AllocatorZapFromSpace) 
	// fill vacated semi-space with 0x01010101
	VM_Memory.fill( fromStartAddress, (byte)1, fromEndAddress-fromStartAddress );
    
      // DONE. setup FromSpace, and addresses used by allocate routines
      fromStartAddress = toStartAddress;
      fromEndAddress = toEndAddress;

      // set up remainder of ToSpace for subsequent allocations
      areaEndAddress = toEndAddress;
      // round up start address to page to allow using efficient zeroPages()
      areaCurrentAddress = (matureCurrentAddress + 4095) & ~4095;

      // exchange largeSpaceAlloc and largeSpaceMark
      shorttemp       = largeSpaceAlloc;
      largeSpaceAlloc = largeSpaceMark;
      largeSpaceMark  = shorttemp;
      large_last_allocated = 0;

      if (VM.verboseGC) {
	// get a timestamp before zeroing inorder to measure zeroing time
	gcTimeBeforeZeroing = VM_Time.now();
      }

      // The remainder of the current semi-space must be zero'ed before allowing
      // any allocations.  This collector can be compiled to do this in either 
      // of three ways:
      //     - 1 GC threads zeros it all (the executing thread) (BAD !!)
      //     - All threads zero chunks in parallel (the executing thread
      //       determines the per thread regions to be zero'ed
      //     - Zeroing is deferred until processors allocate processor
      //       local chunks, while mutators are running (BEST ??)
      //
      if (ZERO_NURSERY_IN_PARALLEL) {
	// determine amount for each processor to zero, round down to page multiple
	int np = VM_CollectorThread.numCollectors();
	int zeroChunk = ((areaEndAddress - areaCurrentAddress)/np) & ~4095;
	int zeroBegin = areaCurrentAddress;
	for (i=1; i<np; i++) {
	  zeroStart[i] = zeroBegin;
	  zeroBytes[i] = zeroChunk;
	  zeroBegin += zeroChunk;
	}
	// last processor zeros remainder
	zeroStart[np] = zeroBegin;
	zeroBytes[np] = areaEndAddress - zeroBegin;
      }
      else if ( ! ZERO_BLOCKS_ON_ALLOCATION ) {
	// have one processor (the executing one) do all the zeroing
	VM_Memory.zeroPages( areaCurrentAddress, areaEndAddress - areaCurrentAddress );
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

      if (PROCESSOR_LOCAL_ALLOCATE) {
	int smallChunks = 0;
	for (i=1; i<=VM_Scheduler.numProcessors; i++) {
	  VM_Processor vp = VM_Scheduler.processors[i];
	  if ( (vp.localMatureEndAddress - vp.localMatureCurrentAddress) < 512) smallChunks++;
	}
	// verify that sufficient heap free for processors with almost full
	// chunks to get another chunk
	if ( (areaEndAddress-areaCurrentAddress) < (smallChunks * CHUNK_SIZE) ) {
	  VM_Scheduler.trace("Warning:","After Collection System Is Low On Memeory");
	}	       
      }

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
      if (RENDEZVOUS_WAIT_TIME) tempTime = VM_Time.now();
      while( gcDone == false );
      VM_Magic.isync();           // prevent from moving infront of waitloop
      if (RENDEZVOUS_WAIT_TIME) mylocal.rendezvousWaitTime += VM_Time.now() - tempTime;
    }

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
    if (writeBarrier)  gc_resetWriteBuffer(VM_Processor.getCurrentProcessor());
    
    if (ZERO_NURSERY_IN_PARALLEL) {
      // each processor zeros its assigned region of the new nursery
      VM_Memory.zeroPages( zeroStart[mylocal.gcOrdinal],
			   zeroBytes[mylocal.gcOrdinal] );
    }

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

      gcEndTime = VM_Time.now();
      tempTime = gcEndTime - gcStartTime;               // time for this GC
      if ( tempTime > maxGCTime ) maxGCTime = tempTime;
      totalGCTime += tempTime;
      int bytesCopied = matureCurrentAddress - toStartAddress;
      totalBytesCopied += bytesCopied;
      if (bytesCopied > maxBytesCopied) maxBytesCopied = bytesCopied;

    if ( VM.verboseGC ) printVerboseOutputLine();
    
    // add current GC phase times into totals, print if verbose on
    if (TIME_GC_PHASES) accumulateGCPhaseTimes();  	

    if ( VM.verboseGC ) {

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
	
      if (RENDEZVOUS_TIMES) printRendezvousTimes();
	
    }  // end of verboseGC

    }  // end of selectedThread

    // all GC threads return, having completed collection
    return;
  }  // collect


  /**
   * Internal method called by collector threads during collection to
   * get space in ToSpace for a live object that needs to be copied.
   * Space is obtained from the processor local "chunk" if available,
   * otherwise space is obtained directly from ToSpace using 
   * atomic compare and swap instructions.
   */
  static int
  gc_getMatureSpace ( int size ) {
    int startAddress, newCurrentAddress;  

    // note: uses "matureCurrentAddress for allocations in toSpace

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
      newCurrentAddress = startAddress + size;
      if ( newCurrentAddress <= st.localMatureEndAddress ) {
	st.localMatureCurrentAddress = newCurrentAddress;    // increment processor local pointer
	return startAddress;
      }
      else {
	startAddress = VM_Synchronization.fetchAndAddWithBound(VM_Magic.addressAsObject(addrMatureCurrentAddress), 0, CHUNK_SIZE, toEndAddress );
	if ( startAddress != -1 ) {
	  st.localMatureEndAddress = startAddress + CHUNK_SIZE;
	  st.localMatureCurrentAddress = startAddress + size;
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
	  outOfMemory();
	  return -1;
	}
      }
    }
    else {
      // all gc threads compete for mature space using synchronized ops
      startAddress = VM_Synchronization.fetchAndAdd(VM_Magic.addressAsObject(addrMatureCurrentAddress), 0, size );
      // assume successful, there should always be space
      return startAddress;
    }
  }  // getMatureSpace
     

  // following used marking bootimage & large space objects.
  // if object was not previously marked, it is queued for scanning
  //
  static void
  gc_markAndScanObject ( Object ref ) {
    int statusWord;
  
    // ignore refs outside boot image...could print warning
     
    if ( VM_Magic.objectAsAddress(ref) >= minBootRef && VM_Magic.objectAsAddress(ref) <= maxBootRef ) {
  
      if (  !VM_Synchronization.testAndMark(ref, OBJECT_STATUS_OFFSET, BOOT_MARK_VALUE) )
	return;   // object already marked with current mark value
  	
      // if here we marked a previously unmarked object
      // add object ref to GC work queue
      VM_GCWorkQueue.putToWorkBuffer( VM_Magic.objectAsAddress(ref) );
    }  // end of bootimage objects
  
    // following for large space objects
    else if (VM_Magic.objectAsAddress(ref) >= minLargeRef) {
      if (!gc_setMarkLarge(ref)) {
	// we marked it, so put to workqueue
	VM_GCWorkQueue.putToWorkBuffer( VM_Magic.objectAsAddress(ref) );
      }
    }
  }  // gc_markAndScanObject

  /**
   * Processes live objects in FromSpace that need to be marked, copied and
   * forwarded during collection.  Returns the new address of the object
   * in ToSpace.  If the object was not previously marked, then the
   * invoking collectot thread will do the copying and enqueue the
   * on the work queue of objects to be scanned.
   *
   * @param fromObj Object in FromSpace to be processed
   *
   * @return the address of the Object in ToSpace (as a reference)
   */
  static Object
  gc_copyAndScanObject ( Object fromObj ) {
    VM_Type type;
    int     full_size;
    int     statusWord;   // original status word from header of object to be copied
    int     toRef;        // address/ref of object in MatureSpace(toSpace) (as int)
    Object  toObj;        // object in MatureSpace(toSpace)
    int     toAddress;    // address of header of object in MatureSpace(toSpace) (as int)
    int     fromAddress;  // address of header of object in FromSpace (as int)
    boolean assertion;

    if (VM.VerifyAssertions) VM.assert(validFromRef( fromObj ));

    toRef = VM_Synchronization.fetchAndMarkBusy(fromObj, OBJECT_STATUS_OFFSET);
    VM_Magic.isync();   // prevent instructions moving infront of fetchAndMark

    // if toRef is "marked" then object has been or is being copied
    if ( (toRef & OBJECT_GC_MARK_MASK) == MARK_VALUE ) {
      // if forwarding ptr == "busy pattern" object is being copied by another
      // GC thread, and wait (should be very short) for valid ptr to be set
      if (COUNT_COLLISIONS && (toRef == BEING_FORWARDED_PATTERN ))
	collisionCount++;
      while ( toRef == BEING_FORWARDED_PATTERN ) {
	 toRef = VM_Magic.getIntAtOffset(fromObj, OBJECT_STATUS_OFFSET);
      }
      // prevent following instructions from being moved in front of waitloop
      VM_Magic.isync();
      
      if (VM.VerifyAssertions) {
	if (MARK_VALUE == 0)
	  assertion = ((toRef & 3)==0) && validRef(toRef);
	else
	  assertion = ((toRef & 3)==1) && validRef(toRef & ~3);
	if (!assertion)
	  VM_Scheduler.traceHex("copyAndScanObject", "invalid forwarding ptr =",toRef);
	VM.assert(assertion);  
      }
      if (MARK_VALUE == 0)
	return VM_Magic.addressAsObject(toRef);
      else
	return VM_Magic.addressAsObject(toRef & ~3);   // mask out markbit
    }

    // toRef is the original status word - copy, set forwarding ptr, and mark
    // (in this collector "marked" == 0, so an aligned pointer is "marked"
    // If have multiple gc threads &
    // fetchAndMarkBusy returned a word NOT marked busy, then it has returned
    // the original status word (ie lock bits, thread id etc) and replaced it
    // with the the BEING_FORWARDED_PATTERN (which has the mark bit set).
    // If here, we must do the forwarding/copying, setting the real forwarding
    // pointer in the status word ASAP
    statusWord = toRef;
    type = VM_Magic.getObjectType(fromObj);
    if (VM.VerifyAssertions) VM.assert(validRef(type));
    
    if ( type.isClassType() ) {
      full_size = type.asClass().getInstanceSize();
      toAddress = gc_getMatureSpace(full_size);
      // position toref to 4 beyond end of object
      toObj = VM_Magic.addressAsObject(toAddress + full_size - SCALAR_HEADER_SIZE - OBJECT_HEADER_OFFSET);
      // position from to start of object data in FromSpace
      // remember, header is to right, ref is 4 bytes beyond header
      fromAddress = VM_Magic.objectAsAddress(fromObj) + OBJECT_HEADER_OFFSET + SCALAR_HEADER_SIZE - full_size;
      
      // now copy object (including the overwritten status word)
      VM_Memory.aligned32Copy( toAddress, fromAddress, full_size );

      if (VM_Configuration.BuildWithRedirectSlot) 
	VM_Magic.setObjectAtOffset(toObj, OBJECT_REDIRECT_OFFSET, toObj);
    }
    else {
      if (VM.VerifyAssertions) VM.assert(type.isArrayType());
      int num_elements = VM_Magic.getArrayLength(fromObj);
      full_size = type.asArray().getInstanceSize(num_elements);
      full_size = (full_size + 3) & ~3;;  //need Magic to roundup
      
      toAddress = gc_getMatureSpace(full_size);
      toObj = VM_Magic.addressAsObject(toAddress - OBJECT_HEADER_OFFSET);
      fromAddress = VM_Magic.objectAsAddress(fromObj)+OBJECT_HEADER_OFFSET;
      
      // now copy object(array) (including the overwritten status word)
      VM_Memory.aligned32Copy( toAddress, fromAddress, full_size );
      
      if (VM_Configuration.BuildWithRedirectSlot) 
	VM_Magic.setObjectAtOffset(toObj, OBJECT_REDIRECT_OFFSET, toObj);

      // sync all arrays of ints - must sync moved code instead of sync'ing chunks when full
      // changed 11/03/00 to fix ExecuteOptCode failure (GC executing just moved code)
      if (type == arrayOfIntType)
	VM_Memory.sync(toAddress, full_size);
    }

    // replace status word in copied object, which now contains the "busy pattern",
    // with original status word, which should be "unmarked" (markbit = 0)
    // in this non-generational collector, the write barrier bit is not normally set,
    // but this "instrummented" version allows building with writeBarrier ON
    //
    if (MARK_VALUE == 0) {
      // "marked" = 0, set markbit on to designate "unmarked"
      if (writeBarrier)
	VM_Magic.setIntAtOffset(toObj, OBJECT_STATUS_OFFSET,
				statusWord | (OBJECT_BARRIER_MASK | OBJECT_GC_MARK_MASK) );
      else
	VM_Magic.setIntAtOffset(toObj, OBJECT_STATUS_OFFSET,
				statusWord | OBJECT_GC_MARK_MASK );
    }
    else { 
      // "marked" = 1, markbit in orig. statusword should be 0
      if (VM.VerifyAssertions) VM.assert((statusWord & OBJECT_GC_MARK_MASK) == 0);
      if (writeBarrier)
	VM_Magic.setIntAtOffset(toObj, OBJECT_STATUS_OFFSET,
				statusWord | OBJECT_BARRIER_MASK );
      else
	VM_Magic.setIntAtOffset(toObj, OBJECT_STATUS_OFFSET, statusWord );
    }
    
    VM_Magic.sync(); // make changes viewable to other processors 
    
    // set status word in old/from object header to forwarding address with
    // the low order markbit set to "marked", if MARK_VALUE=0, storing an aligned
    // pointer will make it "marked".  If multiple GC threads, this store will overwrite
    // the BEING_FORWARDED_PATTERN and let other waiting/spinning GC threads proceed.
    if ( MARK_VALUE == 0 )
      VM_Magic.setObjectAtOffset(fromObj, OBJECT_STATUS_OFFSET, toObj);
    else
      VM_Magic.setIntAtOffset(fromObj, OBJECT_STATUS_OFFSET,
			      VM_Magic.objectAsAddress(toObj) | OBJECT_GC_MARK_MASK);
    
    // following sync is optional, not needed for correctness
    // VM_Magic.sync(); // make changes viewable to other processors 
    
    // add copied object to GC work queue, so it will be scanned later
    VM_GCWorkQueue.putToWorkBuffer( VM_Magic.objectAsAddress(toObj) );
    
    return toObj;
  }  // gc_copyAndScanObject


  // for copying semispace collector, write buffer is NOT USED, but if compiled for 
  // writebuffers, then the compiled code will be filling the buffer anyway.
  // gc_resetWriteBuffer resets the buffer pointers to the beginning of the buffer
  // TODO: free excess buffers...but this is only for measuring write barrier
  // overhead, so why bother
  //
  private static void
  gc_resetWriteBuffer(VM_Processor st) {
    int wbref,wbstatus,wbtib,count;
    VM_Type type;
    int end   = st.modifiedOldObjectsTop; // end = last occuppied slot in buffer
    int start = VM_Magic.objectAsAddress(st.modifiedOldObjects);
  
    if ( end > (start-4) ) {
      // reset end ptr to beginning of buffer
      st.modifiedOldObjectsTop = VM_Magic.objectAsAddress(st.modifiedOldObjects) - 4;
    }
  }


  // for copying VM_Thread & VM_Processor objects, object is NOT queued for scanning
  // does NOT assume exclusive access to object
  private static Object
  gc_copyObject ( Object fromRef ) {
    VM_Type type;
    int     full_size;
    int     statusWord;   // original status word from header of object to be copied
    Object  toRef;        // address/ref of object in MatureSpace (as int)
    int     toAddress;    // address of header of object in MatureSpace (as int)
    int     fromAddress;  // address of header of object in FromSpace (as int)
    boolean assertion;
  
    statusWord = VM_Synchronization.fetchAndMarkBusy(fromRef, OBJECT_STATUS_OFFSET);
    VM_Magic.isync();   // prevent instructions moving infront of fetchAndMark
  
    // if statusWord is "marked" then object has been or is being copied
    if ( (statusWord & OBJECT_GC_MARK_MASK) == MARK_VALUE ) {
  
      // if forwarding ptr == "busy pattern" object is being copied by another
      // GC thread, and wait (should be very short) for valid ptr to be set
      if (COUNT_COLLISIONS && (statusWord == BEING_FORWARDED_PATTERN ))
	collisionCount++;
      while ( statusWord == BEING_FORWARDED_PATTERN ) {
	statusWord = VM_Magic.getIntAtOffset(fromRef, OBJECT_STATUS_OFFSET);
      }
      // prevent following instructions from being moved in front of waitloop
      VM_Magic.isync();
  
      if (VM.VerifyAssertions) {
	if (MARK_VALUE==0)
	  assertion = ((statusWord & 3)==0) && validRef(statusWord);
	else
	  assertion = ((statusWord & 3)==1) && validRef(statusWord & ~3);
	if (!assertion)
	  VM_Scheduler.traceHex("copyObject", "invalid forwarding ptr =",statusWord);
	VM.assert(assertion);  
      }
      if (MARK_VALUE==0)
	return VM_Magic.addressAsObject(statusWord);
      else
	return VM_Magic.addressAsObject(statusWord & ~3);   // mask off markbit & busy bit
    }
    
    // statusWord is the original status word - copy, set forwarding ptr, and mark
    // If have multiple gc threads &
    // fetchAndMarkBusy returned a word NOT marked busy, then it has returned
    // the original status word (ie lock bits, thread id etc) and replaced it
    // with the the BEING_FORWARDED_PATTERN (which has the mark bit set).
    // If here, we must do the forwarding/copying, setting the real forwarding
    // pointer in the status word ASAP
    
    type = VM_Magic.getObjectType(fromRef);
    if (VM.VerifyAssertions) VM.assert(validRef(type));
    if (VM.VerifyAssertions) VM.assert(type.isClassType());
    full_size = type.asClass().getInstanceSize();
    toAddress = gc_getMatureSpace(full_size);
    // position toref to 4 beyond end of object
    toRef = VM_Magic.addressAsObject(toAddress + full_size - SCALAR_HEADER_SIZE - OBJECT_HEADER_OFFSET);
    // position from to start of object data in FromSpace
    // remember, header is to right, ref is 4 bytes beyond header
    fromAddress = VM_Magic.objectAsAddress(fromRef) + OBJECT_HEADER_OFFSET + SCALAR_HEADER_SIZE - full_size;

    // copy object...before status word modified
    VM_Memory.aligned32Copy( toAddress, fromAddress, full_size );

    if (VM_Configuration.BuildWithRedirectSlot) 
	VM_Magic.setObjectAtOffset(toRef, OBJECT_REDIRECT_OFFSET, toRef);
    
    // replace status word in copied object, which now contains the "busy pattern",
    // with original status word, which should be "unmarked" (markbit = 0)
    // in this non-generational collector, the write barrier bit is not normally set,
    // but this "instrummented" version allows building with writeBarrier ON
    //
    if (MARK_VALUE == 0) {
      // "marked" = 0, set markbit on to designate "unmarked"
      if (writeBarrier)
	VM_Magic.setIntAtOffset(toRef, OBJECT_STATUS_OFFSET,
				statusWord | (OBJECT_BARRIER_MASK | OBJECT_GC_MARK_MASK) );
      else
	VM_Magic.setIntAtOffset(toRef, OBJECT_STATUS_OFFSET,
				statusWord | OBJECT_GC_MARK_MASK );
    }
    else { 
      // "marked" = 1, markbit in orig. statusword should be 0
      if (VM.VerifyAssertions) VM.assert((statusWord & OBJECT_GC_MARK_MASK) == 0);
      if (writeBarrier)
	VM_Magic.setIntAtOffset(toRef, OBJECT_STATUS_OFFSET,
				statusWord | OBJECT_BARRIER_MASK );
      else
	VM_Magic.setIntAtOffset(toRef, OBJECT_STATUS_OFFSET, statusWord );
    }
    
    VM_Magic.sync(); // make changes viewable to other processors 
    
    // set status word in old/from object header to forwarding address with
    // the low order markbit set to "marked", if MARK_VALUE=0, storing an aligned
    // pointer will make it "marked".  If multiple GC threads, this store will overwrite
    // the BEING_FORWARDED_PATTERN and let other waiting/spinning GC threads proceed.
    if ( MARK_VALUE == 0 )
      VM_Magic.setIntAtOffset(fromRef, OBJECT_STATUS_OFFSET, VM_Magic.objectAsAddress(toRef));
    else
      VM_Magic.setIntAtOffset(fromRef, OBJECT_STATUS_OFFSET,
			      VM_Magic.objectAsAddress(toRef) | OBJECT_GC_MARK_MASK);
    
    // following sync is optional, not needed for correctness
    // VM_Magic.sync(); // make changes viewable to other processors 
    
    return toRef;
  }  // copyObject
     
     
  // called by ONE gc/collector thread to copy and "new" thread objects
  // copies but does NOT enqueue for scanning
  //
  private static void 
  gc_copyThreads () {
    int          i, vpa, thread_count, processor_count;
    VM_Thread    t;
    VM_Processor vp;
    
    for ( i=0; i<VM_Scheduler.threads.length; i++ ) {
      t = VM_Scheduler.threads[i];
      if (t == null) continue;
      
      if ( VM_Magic.objectAsAddress(t) >= minFromRef && 
	   VM_Magic.objectAsAddress(t) <= maxFromRef ) {
	t = VM_Magic.objectAsThread(gc_copyObject(t));
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
  static void 
  gc_scanThreads ()  {
    int        i, ta, myThreadId, fp;
    VM_Thread  t;
    int[]      oldstack;
    
    // get ID of running GC thread
    myThreadId = VM_Thread.getCurrentThread().getIndex();
    
    for ( i=0; i<VM_Scheduler.threads.length; i++ ) {
      t = VM_Scheduler.threads[i];
      ta = VM_Magic.objectAsAddress(t);
      
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
	if (VM.VerifyAssertions) VM.assert( !(ta >= minFromRef && ta <= maxFromRef) );
	
	if (VM.VerifyAssertions) oldstack = t.stack;    // for verifying  gc stacks not moved
	//	gc_scanThread(t);     // will copy copy stacks, reg arrays, etc.
	VM_ScanObject.scanObjectOrArray(t);
	if (VM.VerifyAssertions) VM.assert(oldstack == t.stack);
	
	if (t.jniEnv != null) VM_ScanObject.scanObjectOrArray(t.jniEnv);

	VM_ScanObject.scanObjectOrArray(t.contextRegisters);

	VM_ScanObject.scanObjectOrArray(t.hardwareExceptionRegisters);

	if (TRACE) VM_Scheduler.trace("VM_Allocator","Collector Thread scanning own stack",i);
	VM_ScanStack.scanStack( t, VM_NULL, true /*relocate_code*/ );
	
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

      // have thread to be processed, compete for it with other GC threads
      if ( VM_GCLocks.testAndSetThreadLock(i) ) {

	if (TRACE) VM_Scheduler.trace("VM_Allocator","processing mutator thread",i);
	
	// all threads should have been copied out of fromspace earlier
	if (VM.VerifyAssertions) VM.assert( !(ta >= minFromRef && ta <= maxFromRef) );
	
	// scan thread object to force "interior" objects to be copied, marked, and
	// queued for later scanning.
	oldstack = t.stack;    // remember old stack address before scanThread
	// gc_scanThread(t);
	VM_ScanObject.scanObjectOrArray(t);
	
	// if stack moved, adjust interior stack pointers
	if ( oldstack != t.stack ) {
	  if (TRACE) VM_Scheduler.trace("VM_Allocator","...adjusting mutator stack",i);
	  t.fixupMovedStack(VM_Magic.objectAsAddress(t.stack) - VM_Magic.objectAsAddress(oldstack));
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

	if (TRACE) VM_Scheduler.trace("VM_Allocator","scanning stack for thread",i);
	VM_ScanStack.scanStack( t, VM_NULL, true /*relocate_code*/ );
	
      }  // (if true) we seized got the thread to process
      
      else continue;  // some other gc thread has seized this thread
      
    }  // end of loop over threads[]
    
  }  // gc_scanThreads
  
  
  // initProcessor is called by each GC thread to copy the processor object of the
  // processor it is running on, and reset it processor register, and update its
  // entry in the scheduler processors array and reset its local allocation pointers
  //
  static void 
  gc_initProcessor ()  {
    VM_Processor   st;
    VM_Thread      activeThread;
    
    st = VM_Processor.getCurrentProcessor();  // get current Processor Register

    // if compiled for processor local chunking of "mature space" reset processor local 
    // pointers, to cause first request to get a block (only reset on major collection
    // for minor collection, continue filling last/current mature buffer
    //
    if (PROCESSOR_LOCAL_MATURE_ALLOCATE) {
      st.localMatureCurrentAddress = 0;
      st.localMatureEndAddress = 0;
    }
  
    // if Processor is in fromSpace, copy and update array entry
    if ( VM_Magic.objectAsAddress(st) >= minFromRef && 
	 VM_Magic.objectAsAddress(st) <= maxFromRef ) {
      st = VM_Magic.objectAsProcessor(gc_copyObject(st));   // copies object, does not queue for scanning
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
      st.localCurrentAddress = 0;
      st.localEndAddress     = 0;
    }
  
    // if Processors activethread (should be current, gc, thread) field -> in FromSpace,
    // ie. still points to old copy of VM_Thread, make it point to the copied ToSpace
    // copy. All threads copied, and the VM_Scheduler.threads array updated in GC initialization.
    // We want BOTH ways of computing getCurrentThread to return the new copy.
    //
    activeThread = st.activeThread;
    if ( VM_Magic.objectAsAddress(activeThread) >= minFromRef && 
	 VM_Magic.objectAsAddress(activeThread) <= maxFromRef ) {
      // following gc_copyObject call will return new address of the thread object
      st.activeThread = VM_Magic.objectAsThread(gc_copyObject(activeThread));
    }
  
    // setup the work queue buffers for this gc thread
    VM_GCWorkQueue.resetWorkQBuffers();
    
  }  //gc_initProcessor


  // scan a VM_Processor object to force "interior" objects to be copied, marked,
  // and queued for later scanning. adjusts write barrier pointers, if
  // write buffer is moved.
  //
  static void 
  gc_scanProcessor () {
    int            oldbuffer, newbuffer;
    VM_Processor   st;
	
    st = VM_Processor.getCurrentProcessor();

    // verify that processor copied out of FromSpace earlier
    if (VM.VerifyAssertions) VM.assert( ! (VM_Magic.objectAsAddress(st) >= minFromRef && 
					    VM_Magic.objectAsAddress(st) <= maxFromRef ) );
    oldbuffer = VM_Magic.objectAsAddress(st.modifiedOldObjects);
    VM_ScanObject.scanObjectOrArray(st);
    // if writebuffer moved, adjust interior pointers
    newbuffer = VM_Magic.objectAsAddress(st.modifiedOldObjects);
    if (oldbuffer != newbuffer) {
      if (TRACE) VM_Scheduler.trace("VM_Allocator","write buffer copied",st.id);
      st.modifiedOldObjectsMax = newbuffer + (st.modifiedOldObjectsMax - oldbuffer);
      st.modifiedOldObjectsTop = newbuffer + (st.modifiedOldObjectsTop - oldbuffer);
    }
  }  // gc_scanProcessor
 

  // Process references in work queue buffers until empty.
  //
  static void  
  gc_emptyWorkQueue () {
    int ref = VM_GCWorkQueue.getFromWorkBuffer();

    if (VM_GCWorkQueue.WORKQUEUE_COUNTS) {
      VM_CollectorThread myThread = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());
      myThread.rootWorkCount = myThread.putWorkCount;
    }
    
    while ( ref != 0 ) {
      VM_ScanObject.scanObjectOrArray( ref );	   
      ref = VM_GCWorkQueue.getFromWorkBuffer();
    }
  }  // gc_emptyWorkQueue 

  // START OF LARGE OBJECT SPACE METHODS

  private static void
  countLargeObjects () {
    int i,num_pages,countLargeOld;
    int contiguousFreePages,maxContiguousFreePages;

    for (i =  0; i < GC_LARGE_SIZES; i++) countLargeAlloc[i] = 0;
    countLargeOld = contiguousFreePages = maxContiguousFreePages = 0;

    for (i =  0; i < largeSpacePages;) {
      num_pages = largeSpaceAlloc[i];
      if (num_pages == 0) {     // no large object found here
	countLargeAlloc[0]++;   // count free pages in entry[0]
	contiguousFreePages++;
	i++;
      }
      else {    // at beginning of a large object
	if (num_pages < GC_LARGE_SIZES-1) countLargeAlloc[num_pages]++;
	else countLargeAlloc[GC_LARGE_SIZES - 1]++;
	if ( contiguousFreePages > maxContiguousFreePages )
	  maxContiguousFreePages = contiguousFreePages;
	contiguousFreePages = 0;
	i = i + num_pages;       // skip to next object or free page
      }
    }
    if ( contiguousFreePages > maxContiguousFreePages )
      maxContiguousFreePages = contiguousFreePages;

    VM.sysWrite("Large Objects Allocated - by num pages\n");
    for (i = 0; i < GC_LARGE_SIZES-1; i++) {
      VM.sysWrite("pages ");
      VM.sysWrite(i);
      VM.sysWrite(" count ");
      VM.sysWrite(countLargeAlloc[i]);
      VM.sysWrite("\n");
    }
    VM.sysWrite(countLargeAlloc[GC_LARGE_SIZES-1]);
    VM.sysWrite(" large objects ");
    VM.sysWrite(GC_LARGE_SIZES-1);
    VM.sysWrite(" pages or more.\n");
    VM.sysWrite(countLargeAlloc[0]);
    VM.sysWrite(" Large Object Space pages are free.\n");
    VM.sysWrite(maxContiguousFreePages);
    VM.sysWrite(" is largest block of contiguous free pages.\n");
    VM.sysWrite(countLargeOld);
    VM.sysWrite(" large objects are old.\n");
    
  }  // countLargeObjects()

  private static int
  freeLargeSpace () {
    int total = 0;
    for (int i = 0 ; i < largeSpacePages;) {
      if (largeSpaceAlloc[i] == 0) {
	total++;
	i++;
      }
      else i = i + largeSpaceAlloc[i];
    }
    return (total * 4096);       // number of bytes free in largespace
  }
    

  // Mark a large space object, if not already marked
  //
  // return  true if already marked, false if not marked & this invocation marked it.
  //
  private static boolean
  gc_setMarkLarge (Object obj) {
    int tref = VM_Magic.objectAsAddress(obj) + OBJECT_HEADER_OFFSET;
    int ij;
    int page_num = ( VM_Magic.objectAsAddress(obj) - largeHeapStartAddress ) >>> 12;

    boolean result = (largeSpaceMark[page_num] != 0);
    if (result) return true;	// fast, no synch case
    
    sysLockLarge.lock();		// get sysLock for large objects
    result = (largeSpaceMark[page_num] != 0);
    if (result) {	// need to recheck
      sysLockLarge.release();
      return true;	
    }
    int temp = largeSpaceAlloc[page_num];
    if (temp == 1) {
      largeSpaceMark[page_num] = 1;
    }
    else {
      // mark entries for both ends of the range of allocated pages
      if (temp > 0) {
	ij = page_num + temp -1;
	largeSpaceMark[ij] = (short)-temp;
      }
      else {
	ij = page_num + temp + 1;
	largeSpaceMark[ij] = (short)-temp;
      }
      largeSpaceMark[page_num] = (short)temp;
    }

    sysLockLarge.unlock();	// INCLUDES sync()
    return false;
  } // gc_setMarkLarge


  // Allocate space for a "large" object in the Large Object Space
  //
  // param size  size in bytes needed for the large object
  // return  address of first byte of the region allocated or 
  //          -2 if not enough space.
  //
  public static int
  getlargeobj (int size) {
    int i, num_pages, num_blocks, first_free, start, temp, result;
    int last_possible;
    num_pages = (size + 4095)/4096;    // Number of pages needed
    last_possible = largeSpacePages - num_pages;
    sysLockLarge.lock();

    while (largeSpaceAlloc[large_last_allocated] != 0)
      large_last_allocated += largeSpaceAlloc[large_last_allocated];

    first_free = large_last_allocated;

    while (first_free <= last_possible) {
      // Now find contiguous pages for this object
      // first find the first available page
      // i points to an available page: remember it
      for (i = first_free + 1; i < first_free + num_pages ; i++) 
	if (largeSpaceAlloc[i] != 0) break;
      if (i == (first_free + num_pages )) {  
	// successful: found num_pages contiguous pages
	// mark the newly allocated pages
	// mark the beginning of the range with num_pages
	// mark the end of the range with -num_pages
	// so that when marking (ref is input) will know which extreme 
	// of the range the ref identifies, and then can find the other

	largeSpaceAlloc[first_free + num_pages - 1] = (short)(-num_pages);
	largeSpaceAlloc[first_free] = (short)(num_pages);
	       
	sysLockLarge.unlock();  //release lock *and synch changes*
	int target = largeHeapStartAddress + 4096 * first_free;
	VM_Memory.zero(target, target + size);  // zero space before return
	return target;
      }  // found space for the new object without skipping any space    

      else {  // free area did not contain enough contig. pages
	first_free = i + largeSpaceAlloc[i]; 
	while (largeSpaceAlloc[first_free] != 0) 
	  first_free += largeSpaceAlloc[first_free];
      }
    }    // go to top and try again

    // fall through if reached the end of large space without finding 
    // enough space
    sysLockLarge.release();  //release lock: won't keep change to large_last_alloc'd
    return -2;  // reached end of largeHeap w/o finding numpages
  }  // getLargeObj

  // END OF LARGE OBJECT SPACE METHODS

  private static void
  resetThreadLocalCounters ( VM_CollectorThread mylocal ) {
    // reset GC thread local counters
    mylocal.localcount1 = 0;                     // this just for GC statistics
    mylocal.localcount2 = 0;
    mylocal.localcount3 = 0;
    mylocal.timeInRendezvous = 0;
    
    // following for measuring work queue with local work buffers
    mylocal.putWorkCount = 0;
    mylocal.getWorkCount = 0;
    mylocal.swapBufferCount = 0;
    mylocal.putBufferCount = 0;
    mylocal.getBufferCount = 0;
  }

  private static void
  accumulateGCPhaseTimes () {
    double start    = gcStartTime - VM_CollectorThread.startTime;
    double init     = gcInitDoneTime - gcStartTime;
    double stacksAndStatics = gcStacksAndStaticsDoneTime - gcInitDoneTime;
    double scanning = gcScanningDoneTime - gcStacksAndStaticsDoneTime;
    double finalize = gcFinalizeDoneTime - gcScanningDoneTime;
    double finish   = gcEndTime - gcFinalizeDoneTime;

    // add current GC times into totals for summary output
    //    totalStartTime += start;   // always measured in ge initialization
    totalInitTime += init;
    totalStacksAndStaticsTime += stacksAndStatics;
    totalScanningTime += scanning;
    totalFinalizeTime += finalize;
    totalFinishTime += finish;

    // if invoked with -verbose:gc print output line for this last GC
    if (VM.verboseGC) {
      VM.sysWrite("<GC ");
      VM.sysWrite(gcCount,false);
      VM.sysWrite(" startTime ");
      VM.sysWrite( (int)(start*1000000.0), false);
      VM.sysWrite("(us) init ");
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
  }
       
  private static void
  printVerboseOutputLine () {
    VM.sysWrite("\n<GC ");
    VM.sysWrite(gcCount,false);
    VM.sysWrite(" time ");
    VM.sysWrite((int)(((gcEndTime - gcStartTime)*1000.0)),false);
    if (ZERO_BLOCKS_ON_ALLOCATION)
      VM.sysWrite(" ms (no zeroing) ");
    else {
      VM.sysWrite(" ms (zeroing ");
      VM.sysWrite((int)(((gcEndTime - gcTimeBeforeZeroing)*1000.0)),false);
      VM.sysWrite(") ");
    }
    if (VERBOSE_WITH_BYTES_FREE) {
      VM.sysWrite(" bytes copied ");
      VM.sysWrite(matureCurrentAddress-toStartAddress,false);
      VM.sysWrite(" semi-space bytes free ");
      VM.sysWrite(areaEndAddress-areaCurrentAddress,false);
    }
    if (VERBOSE_WITH_LARGESPACE_FREE) {
      VM.sysWrite(" large space bytes free ");
      VM.sysWrite(freeLargeSpace(),false);
    }
    VM.sysWrite(">\n");
  }

  static void
  printSummaryStatistics () {
    int avgTime=0, avgBytes=0;
    int np = VM_Scheduler.numProcessors;

    // produce summary system exit output if -verbose:gc was specified of if
    // compiled with measurement flags turned on
    //
    if ( ! (TIME_GC_PHASES || VM_CollectorThread.MEASURE_WAIT_TIMES || VM.verboseGC) )
      return;     // not verbose, no flags on, so don't produce output

    // the bytesCopied counts count whole chunks. The last chunk acquired for
    // copying objects is partially full/empty, on avg. half full.  So we
    // subtrace from the average, half of space in a set of chunks
    //
    int avgBytesFreeInChunks = (VM_Scheduler.numProcessors * CHUNK_SIZE) >> 1;

    VM.sysWrite("\nGC stats: Semi-Space Copying Collector (");
    VM.sysWrite(np,false);
    VM.sysWrite(" Collector Threads ):\n");
    VM.sysWrite("          Heap Size ");
    VM.sysWrite(smallHeapSize,false);
    VM.sysWrite("  Large Object Heap Size ");
    VM.sysWrite(largeHeapSize,false);
    VM.sysWrite("\n");

    VM.sysWrite("  ");
    if (gcCount == 0)
      VM.sysWrite("0 Collections");
    else {
      avgTime = (int)( ((totalGCTime/(double)gcCount)*1000.0) );
      avgBytes = ((int)(totalBytesCopied/gcCount)) - avgBytesFreeInChunks;

      VM.sysWrite(gcCount,false);
      VM.sysWrite(" Collections: avgTime ");
      VM.sysWrite(avgTime,false);
      VM.sysWrite(" (ms) maxTime ");
      VM.sysWrite((int)(maxGCTime*1000.0),false);
      VM.sysWrite(" (ms) avgBytesCopied ");
      VM.sysWrite(avgBytes,false);
      VM.sysWrite(" maxBytesCopied ");
      VM.sysWrite((int)maxBytesCopied,false);
      VM.sysWrite("\n\n");
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
      VM.sysWrite("startTime ");
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
      VM.sysWrite("(us)>\n\n");
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
      VM.sysWrite("Buffer Wait ");
      VM.sysWrite( avgBufferWait, false);
      VM.sysWrite(" (us) Finish Wait ");
      VM.sysWrite( avgFinishWait, false);
      VM.sysWrite(" (us) Rendezvous Wait ");
      VM.sysWrite( avgRendezvousWait, false);
      VM.sysWrite(" (us)\n\n");
      /***
      VM.sysWrite("Sum of Wait Times For All Collector Threads ( ");
      VM.sysWrite(np,false);
      VM.sysWrite(" Collector Threads ):\n");
      VM.sysWrite("Buffer Wait ");
      VM.sysWrite( (int)(totalBufferWait*1000.0), false);
      VM.sysWrite(" (ms) Finish Wait ");
      VM.sysWrite( (int)(totalFinishWait*1000.0), false);
      VM.sysWrite(" (ms) Rendezvous Wait ");
      VM.sysWrite( (int)(totalRendezvousWait*1000.0), false);
      VM.sysWrite(" (ms) TOTAL ");
      VM.sysWrite( (int)((totalRendezvousWait+totalFinishWait+totalBufferWait)*1000.0), false);
      VM.sysWrite(" (ms)\n");
      ***/
    }

  }

  // DEBUGGING 

  // Purely for backward compatibility.  Axe me after fixing all collectors, VM_WriteBuffer
  static boolean
  validRef ( int ref ) {
    //      return validRef(VM_Magic.addressAsObject(ref));
    if ( ref < minBootRef || ref > maxHeapRef ) {
      VM.sysWrite("validRef:ref outside heap, ref = ");
      VM.sysWrite(ref);
      VM.sysWrite("\n");
      return false;
    }
    int type = VM_Magic.objectAsAddress(VM_Magic.getObjectType(VM_Magic.addressAsObject(ref)));
    if ( type < minBootRef || type > maxHeapRef ) {
      VM.sysWrite("validRef:ref with bad type, ref = ");
      VM.sysWrite(ref);
      VM.sysWrite("\n");
      VM.sysWrite("                           type = ");
      VM.sysWrite(type);
      VM.sysWrite("\n");
      return false;
    }
    return true;
  }


  static boolean
  validRef ( Object ref ) {
    if ( VM_Magic.objectAsAddress(ref) < minBootRef || 
	 VM_Magic.objectAsAddress(ref) > maxHeapRef ) {
      VM_Scheduler.trace("validRef:", "ref outside heap, ref = ", VM_Magic.objectAsAddress(ref));
      return false;
    }
    VM_Type type = VM_Magic.getObjectType(ref);
    int i_type = VM_Magic.objectAsAddress(type);
    if ( i_type < minBootRef || i_type > maxHeapRef ) {
      VM_Scheduler.trace("validRef:", "type for ref outside heap, type = ", i_type);
      VM_Scheduler.trace("validRef:", "                        bad ref = ", VM_Magic.objectAsAddress(ref));
      return false;
    }
    return true;
  }	


  static boolean
  validFromRef ( Object ref ) {
    if ( VM_Magic.objectAsAddress(ref) >= minFromRef && 
	 VM_Magic.objectAsAddress(ref) <= maxFromRef ) return true;
    else return false;
  }	


  static boolean
  validForwardingPtr ( int ref ) {
    if ( fromStartAddress == smallHeapStartAddress ) {
      if ( ref >= heapMiddleAddress-OBJECT_HEADER_OFFSET  &&
	   ref <= smallHeapEndAddress+4 ) return true;
    } 
    else {
      if ( ref >= smallHeapStartAddress-OBJECT_HEADER_OFFSET  &&
	   ref <= heapMiddleAddress+4 ) return true;
    } 
    return false;
  }
    

  static boolean
  validWorkQueuePtr ( Object obj ) {
    int ref = VM_Magic.objectAsAddress(obj);
    if ( ref >= minBootRef && ref <= maxBootRef ) return true;
    if ( ref >= minLargeRef && ref <= maxLargeRef ) return true;
    else return validForwardingPtr( ref );
  }

  private static void
  printRendezvousTimes () {

    VM.sysWrite("RENDEZVOUS ENTRANCE & EXIT TIMES (microsecs) rendevous 1, 2 & 3\n");

    for (int i = 1; i <= VM_Scheduler.numProcessors; i++) {
      VM.sysWrite(i,false);
      VM.sysWrite(" R1 in ");
      VM.sysWrite(rendezvous1in[i],false);
      VM.sysWrite(" out ");
      VM.sysWrite(rendezvous1out[i],false);
      VM.sysWrite(" R2 in ");
      VM.sysWrite(rendezvous2in[i],false);
      VM.sysWrite(" out ");
      VM.sysWrite(rendezvous2out[i],false);
      VM.sysWrite(" R3 in ");
      VM.sysWrite(rendezvous3in[i],false);
      VM.sysWrite(" out ");
      VM.sysWrite(rendezvous3out[i],false);
      VM.sysWrite("\n");
    }
  }

  // Somebody tried to allocate an object within a block
  // of code guarded by VM.disableGC() / VM.enableGC().
  //
  private static void
  fail () {
    VM.sysWrite("vm error: allocator/collector called within critical section\n");
    VM.assert(false);
  }

  // allocate buffer for allocates during traceback & call sysFail (gets stacktrace)
  // or sysWrite the message and sysExit (no traceback possible)
  // ...may not be needed if sysFail now does NOT do allocations
  //
  private static void
  crash (String err_msg) {
    int tempbuffer;
    VM.sysWrite("VM_Allocator.crash:\n");
    if (PROCESSOR_LOCAL_ALLOCATE) {
      if ((tempbuffer = VM.sysCall1(bootrecord.sysMallocIP,
				    VM_Allocator.CRASH_BUFFER_SIZE)) == 0) {
	VM_Scheduler.trace("VM_ALLOCATOR.crash()","sysMalloc returned 0");
	VM.shutdown(1800);
      }
      VM_Processor p = VM_Processor.getCurrentProcessor();
      p.localCurrentAddress = tempbuffer;
      p.localEndAddress = tempbuffer + VM_Allocator.CRASH_BUFFER_SIZE;
      VM_Memory.zero(tempbuffer, tempbuffer + VM_Allocator.CRASH_BUFFER_SIZE);
      VM.sysFail(err_msg);
    }
    else {
      VM.sysFail(err_msg);   // this is now supposed to complete without allocations
    }
  }

  /*
   * Initialize a VM_Processor for allocation and collection.
   */
  static void
  setupProcessor (VM_Processor p) {
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
  static boolean
  processFinalizerListElement (VM_FinalizerListElement le) {
    int ref = le.value;
    if ( ref >= minFromRef && ref <= maxFromRef ) {
      int statusword = VM_Magic.getMemoryWord(ref + OBJECT_STATUS_OFFSET);
      if ( (statusword & OBJECT_GC_MARK_MASK) == MARK_VALUE ) {
	// live, set le.value to forwarding address
	le.value = statusword & ~3;
	return true;
      }
      else {
	// dead, mark, copy, and enque for scanning, and set le.pointer
	le.pointer = gc_copyAndScanObject( VM_Magic.addressAsObject(ref) );
	le.value = -1;
	return false;
      }
    }
    else {
      if (VM.VerifyAssertions) VM.assert(ref >= minLargeRef);
      int page_num = ((ref + OBJECT_HEADER_OFFSET) - largeHeapStartAddress ) >> 12;
      if (largeSpaceMark[page_num] != 0)
	return true;   // still live, le.value is OK
      else {
	// dead, mark it live, and enqueue for scanning
	gc_setMarkLarge(VM_Magic.addressAsObject(ref));
	VM_GCWorkQueue.putToWorkBuffer(ref);
	le.pointer = VM_Magic.addressAsObject(ref);
	le.value = -1;
	return false;
      }
    }
  }  // processFinalizerListElement
  
  // Called from WriteBuffer code for generational collectors.
  // Argument is a modified old object which needs to be scanned
  //
  static void
  processWriteBufferEntry (int ref) {
    VM_ScanObject.scanObjectOrArray(ref);
  }
  
  /**
   * Process an object reference field during collection.
   * Called from GC utility classes like VM_ScanStack.
   *
   * @param location  address of a reference field
   */
  static void
  processPtrField ( int location ) {
    int ref = VM_Magic.getMemoryWord( location );
  
    if (ref == VM_NULL) return;
  
    if ( ref >= minFromRef && ref <= maxFromRef ) {
      // in FromSpace, if not marked, mark, copy & queue for scanning
      // set the reference field to new reference
      VM_Magic.setMemoryWord( location,
	  VM_Magic.objectAsAddress(gc_copyAndScanObject(VM_Magic.addressAsObject(ref))) );
    }
    else
      // bootimage & largespace, if not marked, mark & queue for scanning
      gc_markAndScanObject( VM_Magic.addressAsObject(ref) );
  }

  /**
   * Process an object reference (value) during collection.
   * Called from GC utility classes like VM_ScanStack.
   *
   * @param location  address of a reference field
   */
  static int
  processPtrValue ( int ref ) {
  
    if (ref == VM_NULL) return ref;
  
    if ( ref >= minFromRef && ref <= maxFromRef ) 
      // in FromSpace, if not marked, mark, copy & queue for scanning
      // return its new reference
      return VM_Magic.objectAsAddress(gc_copyAndScanObject(VM_Magic.addressAsObject(ref)));
    else {
      // bootimage & largespace, if not marked, mark & queue for scanning
      gc_markAndScanObject( VM_Magic.addressAsObject(ref) );
      return ref;
    }
  }


}   // VM_Allocator
