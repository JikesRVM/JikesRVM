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
 *  +-------------+----------------+---------------+--------------+
 *  | BootImage   | MatureSpace    |   RESERVED    | Nursery      | 
 *  +-------------+----------------+---------------+--------------+
 *       heapStart^             heapMiddle^                heapEnd^ 
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

  /** When true, print heap configuration when starting */
  static final boolean DISPLAY_OPTIONS_AT_BOOT = VM_CollectorThread.DISPLAY_OPTIONS_AT_BOOT;
  
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
  static void
    init () {
    
    VM_GCLocks.init();          // to alloc lock fields used during GC (in bootImage)
    VM_GCWorkQueue.init();      // to alloc shared work queue      
    VM_CollectorThread.init();  // to alloc its rendezvous arrays, if necessary
    
    // initialize large object heap
    sysLockLarge       = new VM_ProcessorLock();   // serializes access to large space
    largeSpaceAlloc = new short[GC_INITIAL_LARGE_SPACE_PAGES];
    large_last_allocated = 0;
    largeSpacePages = GC_INITIAL_LARGE_SPACE_PAGES;
    largeSpaceHiWater = 0;
    
    countLargeAlloc = new int[GC_LARGE_SIZES];
    
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
    
    bootrecord = thebootrecord;	
    minBootRef = bootrecord.startAddress-OBJECT_HEADER_OFFSET;   // first ref in bootimage
    maxBootRef = bootrecord.freeAddress+4;      // last ref in bootimage
    // set start of heap, round up to page boundary
    heapStartAddress = (bootrecord.freeAddress + 4095) & ~4095;
    // end of heap, round down to page boundary
    heapEndAddress    = bootrecord.endAddress & ~4095; 
    smallHeapSize     = heapEndAddress - heapStartAddress;
    heapMiddleAddress = (heapStartAddress + smallHeapSize/2) & ~4095;
    
    // major collections are innitiated when mature space get within 
    // some "delta" of the middle of the heap.
    // TODO: dynamically set this based on percentage of nursery being kept live
    majorCollectionDelta = smallHeapSize/8;
    
    minHeapRef = heapStartAddress - OBJECT_HEADER_OFFSET;
    maxHeapRef = heapEndAddress + 4;
    
    // still refer to region available for allocations as "FromSpace"
    // For first cycle FromSpace starts at the middle and extends to heapEnd  
    fromStartAddress = heapMiddleAddress; 
    fromEndAddress = heapEndAddress;
    
    // initialize pointers used for allocation
    areaCurrentAddress = fromStartAddress;
    areaEndAddress     = fromEndAddress;
    
    // no mature objects, start mature space at beginning of heap
    matureCurrentAddress = heapStartAddress;
    
    // need address of areaCurrentAddress (in JTOC) for atomic fetchAndAdd()
    // when JTOC moves, this must be reset
    // offset of areaCurrentAddress in JTOC is set (in JDK side) in VM_EntryPoints 
    addrAreaCurrentAddress = VM_Magic.getTocPointer() + VM_Entrypoints.areaCurrentAddressOffset;
    // likewise for matureCurrentAddress
    addrMatureCurrentAddress = VM_Magic.getTocPointer() + VM_Entrypoints.matureCurrentAddressOffset;
    
    if (COMPILE_FOR_TIMING_RUN) 
      // touch all heap pages, to avoid pagefaults overhead during timing runs
      for (int i = heapEndAddress - 4096; i >= heapStartAddress; i = i - 4096)
	VM_Magic.setMemoryWord(i, 0);
    
    
    // setup large object space
    largeHeapStartAddress = bootrecord.largeStart;
    largeHeapEndAddress = bootrecord.largeStart + bootrecord.largeSize;
    largeHeapSize = largeHeapEndAddress - largeHeapStartAddress;
    largeSpacePages = bootrecord.largeSize/4096;
    minLargeRef = largeHeapStartAddress-OBJECT_HEADER_OFFSET;   // first ref in large space
    maxLargeRef = largeHeapEndAddress+4;      // last ref in large space
    
    // Get the (full sized) arrays that control large object space
    largeSpaceMark  = new short[bootrecord.largeSize/4096 + 1];
    largeSpaceGen   = new byte[bootrecord.largeSize/4096 + 1];
    short[] temp  = new short[bootrecord.largeSize/4096 + 1];
    // copy any existing large object allocations into new alloc array
    // ...with this simple allocator/collector there may be none
    for (int i = 0; i < GC_INITIAL_LARGE_SPACE_PAGES; i++)
      temp[i] = largeSpaceAlloc[i];
    largeSpaceAlloc = temp;
    
    arrayOfIntType = VM_Array.getPrimitiveArrayType( 10 /*code for INT*/ ); // for sync'ing arrays of code
    
    VM_GCUtil.boot();

    VM_Finalizer.setup();
    
    VM_Callbacks.addExitMonitor(new VM_Allocator());

    if (DISPLAY_OPTIONS_AT_BOOT) {
      VM.sysWrite("\n");
      VM.sysWrite("Generational Copying Collector with variable sized Nursery:\n");
      VM.sysWrite("Small Object Heap Size = ");
      VM.sysWrite(heapEndAddress-heapStartAddress);
      VM.sysWrite("\n");
      VM.sysWrite(" heapStartAddress = "); VM.sysWriteHex(heapStartAddress);
      VM.sysWrite("\n");
      VM.sysWrite(" heapEndAddress   = "); VM.sysWriteHex(heapEndAddress);
      VM.sysWrite("\n");
      VM.sysWrite(" heapMiddleAddress     = "); VM.sysWriteHex(heapMiddleAddress);
      VM.sysWrite("\n");
      VM.sysWrite("LargeHeapSize = "); VM.sysWrite(largeHeapSize);
      VM.sysWrite("\n");
      VM.sysWrite(" largeHeapStartAddress = "); VM.sysWriteHex(largeHeapStartAddress);
      VM.sysWrite("\n");
      VM.sysWrite(" largeHeapEndAddress   = "); VM.sysWriteHex(largeHeapEndAddress);
      VM.sysWrite("\n");
      
      VM.sysWrite("Compiled with ZERO_BLOCKS_ON_ALLOCATION ");
      if (ZERO_BLOCKS_ON_ALLOCATION)
	VM.sysWrite("ON \n");
      else
	VM.sysWrite("OFF \n");
      
      VM.sysWrite("Compiled with ZERO_NURSERY_IN_PARALLEL ");
      if (ZERO_NURSERY_IN_PARALLEL)
	VM.sysWrite("ON \n");
      else
	VM.sysWrite("OFF \n");
      
      VM.sysWrite("Compiled with PROCESSOR_LOCAL_ALLOCATE ");
      if (PROCESSOR_LOCAL_ALLOCATE)
	VM.sysWrite("ON \n");
      else
	VM.sysWrite("OFF \n");
      
      VM.sysWrite("Compiled with PROCESSOR_LOCAL_MATURE_ALLOCATE ");	  
      if (PROCESSOR_LOCAL_MATURE_ALLOCATE)
	VM.sysWrite("ON \n");
      else
	VM.sysWrite("OFF \n");
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
    forceMajorCollection = true;    // to force a major collection
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
      //      crash("VM_Allocator: Garbage Collection Failure: GC Thread asking for GC");
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
    totalMemory() {
    return (heapEndAddress-heapStartAddress + largeHeapSize);    
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
    return ((areaEndAddress - areaCurrentAddress)
	    + (matureAllocationIncreasing ? (heapMiddleAddress - matureCurrentAddress) :
	       (matureCurrentAddress - heapMiddleAddress))
	    + freeLargeSpace() );
  }

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
      VM.sysWrite("Insufficient heap size for Generational (Variable Nursery) Collector\n");
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
  public static int
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
	forceMajorCollection = true;  // force a major collection to reclaim more large space
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
      
      addr = VM_Synchronization.fetchAndAddWithBound(VM_Magic.addressAsObject(addrAreaCurrentAddress), 0, size, areaEndAddress );
      if ( addr == -1 ) {
	// do garbage collection, check if get space for object
	if (GC_TRIGGERGC) VM.sysWrite("GC triggered by small object request\n");
	gc1();
	addr = VM_Synchronization.fetchAndAddWithBound(VM_Magic.addressAsObject(addrAreaCurrentAddress), 0, size, areaEndAddress );
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
	// initial value of status word is 0 (unmarked)
	if( hasFinalizer )  VM_Finalizer.addElement(new_ref);
	return new_ref;
      }
      else
	return cloneScalar( size, tib, null );
    }
    else { // NON CHUNKING CODE - all allocates from global heap 
      int firstByte = getHeapSpace(size);
      VM_Magic.setObjectAtOffset(new_ref, OBJECT_TIB_OFFSET, tib); // set .tib field
      // initial value of status word is 0 (unmarked)
      if( hasFinalizer )  VM_Finalizer.addElement(new_ref);
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
    
    // initial value of status work is 0 (unmarked)
    
    // initialize object fields with data from passed in object to clone
    //
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
	  // initial value of status word is 0 (unmarked)
	  // set .length field
	  VM_Magic.setIntAtOffset(objAddress, ARRAY_LENGTH_OFFSET, numElements);
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
      // initial value of status word is 0 (unmarked)
      // set .length field
      VM_Magic.setIntAtOffset(objAddress, ARRAY_LENGTH_OFFSET, numElements);
      
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
    
    size = (size + 3) & ~3;            // round up request to word multiple
    
    int firstByte = getHeapSpace(size);
    
    Object objRef = VM_Magic.addressAsObject(firstByte - OBJECT_HEADER_OFFSET);
    
    VM_Magic.setObjectAtOffset(objRef, OBJECT_TIB_OFFSET, tib);
    
    // initial value of status word is 0 (unmarked)
    
    VM_Magic.setIntAtOffset(objRef, ARRAY_LENGTH_OFFSET, numElements);
    
    // initialize array elements
    //
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
  
  static int areaCurrentAddress;
  static int addrAreaCurrentAddress;
  static int areaEndAddress;
  
  private static boolean forceMajorCollection = false; // forces major collection after a minor
  
  private static boolean outOfMemoryReported = false;
  private static boolean majorCollection = false;
  private static volatile boolean initGCDone = false;
  private static volatile boolean minorGCDone = false;
  private static volatile boolean majorGCDone = false;
  private static boolean matureAllocationIncreasing = true;
  
  // following for managing large object space
  private static VM_ProcessorLock sysLockLarge;        // serializes access to large space
  private final static int GC_LARGE_SIZES = 20;           // for statistics  
  private final static int GC_INITIAL_LARGE_SPACE_PAGES = 100; // for early allocation of large objs
  private static int           largeHeapStartAddress;
  private static int           largeHeapEndAddress;
  private static int           largeSpacePages;
  private static int           largeHeapSize;
  private static int		largeSpaceHiWater;      // start of last object in largeSpace
  private static int		large_last_allocated;   // where to start search for free space
  private static short[]	largeSpaceAlloc;	// used to allocate in large space
  private static short[]	largeSpaceMark;		// used to mark large objects
  private static byte[]	largeSpaceGen;		// generation numbers for large objects
  private static int[]	countLargeAlloc;	//  - count sizes of large objects alloc'ed
  private static int marklarge_count = 0;	// counter of large objects marked
  private static int largerefs_count = 0;
  private static int minLargeRef;
  private static int maxLargeRef;
  private static int minBootRef;
  private static int maxBootRef;
  private static int minHeapRef;
  private static int maxHeapRef;
  private static int minFromRef;
  private static int maxFromRef;
  
  static VM_BootRecord	 bootrecord;
  
  private static int heapStartAddress;
  private static int heapEndAddress;
  private static int heapMiddleAddress;
  private static int smallHeapSize;
  private static int majorCollectionDelta;  
  private static int fromStartAddress;
  private static int fromEndAddress;
  private static int nurserySize;             // varies - half of remaining space
  private static int matureCurrentAddress;    // current end of mature space
  private static int addrMatureCurrentAddress;    // address of above (in the JTOC)
  private static int matureSaveAddress;       // end of mature space at beginning of major GC
  private static int matureBeforeMinor;       // ...for debugging to see closeness to heapMiddleAddress  
  
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
  
  static final boolean debugNative = false;
  private static final boolean GC_TRIGGERGC = false;   // prints what triggered each GC
  private static final boolean GCDEBUG = false;
  private static final boolean TRACE = false;
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
	if (vp.localCurrentAddress != 0) {
	  VM_Scheduler.trace("prepareNonParticipatingVPsForGC:",
			     "native processor with non-zero allocation ptr, id =",vp.id);
	  vp.dumpProcessorState();
	  VM.assert(vp.localCurrentAddress == 0);
	}
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
      if ((vpStatus == VM_Processor.BLOCKED_IN_NATIVE) || (vpStatus == VM_Processor.IN_SIGWAIT)) {
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
    int       i,temp,bytes;
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
    
    // BEGIN SINGLE THREAD SECTION - GC INITIALIZATION
    
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
      
      if (TRACE)
	VM_Scheduler.trace("VM_Allocator", "gc initialization for gcCount", gcCount);
      
      gcInProgress = true;
      majorCollection = false;
      minorGCDone = false;
      majorGCDone = false;
      
      // set bounds of possible FromSpace refs (of objects to be copied)
      minFromRef = fromStartAddress - OBJECT_HEADER_OFFSET;
      maxFromRef = fromEndAddress + 4;
      
      // remember current end of mature space
      matureSaveAddress = matureCurrentAddress;
      matureBeforeMinor = matureCurrentAddress;
  
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
      
      VM_GCLocks.resetFinishLock();  // for singlethread'ing end of minor collections
      
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
      while( initGCDone == false ); // spin until initialization finished
      VM_Magic.isync();             // prevent following inst. from moving infront of waitloop
      
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
    
    // following seems to be necessary when PROCESSOR_LOCAL_MATURE_ALLOCATE==true
    // not sure why, otherwise fail in assertion in yield done by collectorthread.run()
    // maybe processor object is moved while enableSwitching count is being modified???
    //
    if ( PROCESSOR_LOCAL_MATURE_ALLOCATE == true ) {
      if (RENDEZVOUS_WAIT_TIME) tempTime = VM_Time.now();
      if (RENDEZVOUS_TIMES) rendezvous1in[mypid] = (int)((VM_Time.now() - gcStartTime)*1000000);
      VM_CollectorThread.gcBarrier.rendezvous();
      if (RENDEZVOUS_TIMES) rendezvous1out[mypid] = (int)((VM_Time.now() - gcStartTime)*1000000);
      if (RENDEZVOUS_WAIT_TIME) mylocal.timeInRendezvous += (int)((VM_Time.now() - tempTime)*1000.0);
    }
    
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
    gc_scanProcessor();  // each gc threads scans its own processor object

    //gc_scanStatics();    // ALL GC threads process JTOC in parallel
    VM_ScanStatics.scanStatics();     // GC threads scan JTOC in parallel

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
    
    if (RENDEZVOUS_WAIT_TIME) tempTime = VM_Time.now();
    if (RENDEZVOUS_TIMES) rendezvous2in[mypid] = (int)((VM_Time.now() - gcStartTime)*1000000);
    VM_CollectorThread.gcBarrier.rendezvous();
    if (RENDEZVOUS_TIMES) rendezvous2out[mypid] = (int)((VM_Time.now() - gcStartTime)*1000000);
    if (RENDEZVOUS_WAIT_TIME) mylocal.timeInRendezvous += (int)((VM_Time.now() - tempTime)*1000.0);
    
    // ALL GC THREADS IN PARALLEL
    
    if (TIME_GC_PHASES && (mylocal.gcOrdinal == 1))
      gcStacksAndStaticsDoneTime = VM_Time.now();  // for time scanning stacks & statics
    
    gc_processWriteBuffers();  // each GC thread processes its own writeBuffers
    
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
      if (RENDEZVOUS_WAIT_TIME) tempTime = VM_Time.now();
      VM_CollectorThread.gcBarrier.rendezvous();
      if (RENDEZVOUS_WAIT_TIME) mylocal.rendezvousWaitTime += VM_Time.now() - tempTime;
     
      if (VM_Finalizer.foundFinalizableObject) {

	// Some were found. Now ALL threads execute emptyWorkQueue again, this time
	// to mark and keep live all objects reachable from the new finalizable objects.
	//
	gc_emptyWorkQueue();

	/***  not needed
	if (RENDEZVOUS_WAIT_TIME) tempTime = VM_Time.now();
	VM_CollectorThread.gcBarrier.rendezvous();
	if (RENDEZVOUS_WAIT_TIME) mylocal.rendezvousWaitTime += VM_Time.now() - tempTime;
	***/
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
      
      if (TRACE) VM_Scheduler.trace("VM_Allocator", "finishing minor collection");
      
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
	  if (matureCurrentAddress >= (heapMiddleAddress - majorCollectionDelta) )
	    majorCollection = true;
	}
	else {  // matureAllocationDecreasing
	  if ( matureCurrentAddress < (heapMiddleAddress + majorCollectionDelta) )
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
	
	if (TRACE)
	  VM_Scheduler.trace("VM_Allocator","initialize for MAJOR collection",gcMajorCount);

	gcEndTime = VM_Time.now();
	gcMinorTime = gcEndTime - gcStartTime;
	gcTotalTime = gcTotalTime + gcMinorTime;
	totalMinorTime += gcMinorTime;
	if (gcMinorTime > maxMinorTime) maxMinorTime = gcMinorTime;
	if (matureAllocationIncreasing)
	  bytes = matureCurrentAddress - matureSaveAddress;
	else
	  bytes = matureSaveAddress - matureCurrentAddress;
	if (bytes > maxMinorBytesCopied) maxMinorBytesCopied = bytes;
	totalMinorBytesCopied += bytes;

	// print -verbose output
	  
	if ( VM.verboseGC ) printVerboseOutputLine( 2 /*MINOR before MAJOR*/ );

	// add current GC phase times into totals, print if verbose on
	if (TIME_GC_PHASES) accumulateGCPhaseTimes( false );  	

	if ( VM.verboseGC ) printWaitTimesAndCounts();

	// reset gcStartTime timestamp for measuring major collection times
	gcStartTime = VM_Time.now();
	
	// remember current end of matureSpace.  This point has crossed into the opposite 
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
	  minFromRef = heapStartAddress - OBJECT_HEADER_OFFSET;   // start + header size
	  maxFromRef = matureCurrentAddress + 4;   
	  
	  // set mature space to the (now empty) end of the heap
	  matureCurrentAddress = heapEndAddress;
	  
	  matureAllocationIncreasing = false;
	} else {
	  // set bounds of FromSpace to point to the mature semi-space to be collected.  
	  minFromRef = matureCurrentAddress - OBJECT_HEADER_OFFSET;   // start + header size
	  maxFromRef = heapEndAddress + 4;   
	  
	  // set mature space to the (now empty) beginning of the heap
	  matureCurrentAddress = heapStartAddress;
	  
	  matureAllocationIncreasing = true;
	}
	
	VM_GCWorkQueue.workQueue.reset(); // reset shared common work queue shared data
	
	// during major collections we do a full mark-sweep, and mark and scan live
	// bootImage objects. invert sense of mark flag in boot objects so that the
	// objects marked during the last major collection now appear "unmarked"
	
	BOOT_MARK_VALUE = BOOT_MARK_VALUE ^ OBJECT_GC_MARK_MASK; 
	
	// Now initialize the large object space mark array
	VM_Memory.zero(VM_Magic.objectAsAddress(largeSpaceMark), 
		       VM_Magic.objectAsAddress(largeSpaceMark) + 2*largeSpaceMark.length);
	
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
	if (VM.AllocatorZapFromSpace) 
	  // fill from space with 0x01010101, then zero on each allocation
	  VM_Memory.fill(  zeroStart[VM_Processor.getCurrentProcessorId()],
			   (byte)1,
			   zeroBytes[VM_Processor.getCurrentProcessorId()] );
	else
	  // each processor zeros its assigned region of the new nursery
	  VM_Memory.zeroPages( zeroStart[VM_Processor.getCurrentProcessorId()],
			       zeroBytes[VM_Processor.getCurrentProcessorId()] );
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
	  bytes = matureCurrentAddress - matureSaveAddress;
	else
	  bytes = matureSaveAddress - matureCurrentAddress;
	if (bytes > maxMinorBytesCopied) maxMinorBytesCopied = bytes;
	totalMinorBytesCopied += bytes;
	
	// print verbose output

	if ( VM.verboseGC ) printVerboseOutputLine( 1 /* MINOR */ );

	// add current GC phase times into totals, print if verbose on
	if (TIME_GC_PHASES) accumulateGCPhaseTimes( false );  	

	if ( VM.verboseGC ) printWaitTimesAndCounts();

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
    if ( PROCESSOR_LOCAL_MATURE_ALLOCATE == true ) {
      if (RENDEZVOUS_WAIT_TIME) tempTime = VM_Time.now();
      if (RENDEZVOUS_TIMES) rendezvous1in[mypid] = (int)((VM_Time.now() - gcStartTime)*1000000);
      VM_CollectorThread.gcBarrier.rendezvous();
      if (RENDEZVOUS_TIMES) rendezvous1out[mypid] = (int)((VM_Time.now() - gcStartTime)*1000000);
      if (RENDEZVOUS_WAIT_TIME) mylocal.timeInRendezvous += (int)((VM_Time.now() - tempTime)*1000.0);
    }
    
    if (TRACE) VM_Scheduler.trace("VM_Allocator", "starting major collection", gcMajorCount);
    
    gc_scanProcessor();   // each gc threads scans its own processor object
    
    //gc_scanStatics();    // ALL GC threads process JTOC in parallel
    VM_ScanStatics.scanStatics();     // GC threads scan JTOC in parallel

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
    
    if (RENDEZVOUS_WAIT_TIME) tempTime = VM_Time.now();
    if (RENDEZVOUS_TIMES) rendezvous2in[mypid] = (int)((VM_Time.now() - gcStartTime)*1000000);
    VM_CollectorThread.gcBarrier.rendezvous();
    if (RENDEZVOUS_TIMES) rendezvous2out[mypid] = (int)((VM_Time.now() - gcStartTime)*1000000);
    if (RENDEZVOUS_WAIT_TIME) mylocal.timeInRendezvous += (int)((VM_Time.now() - tempTime)*1000.0);
    
    // have processor 1 record timestame for end of scanning stacks & statics
    if (TIME_GC_PHASES && (mylocal.gcOrdinal == 1))
      gcStacksAndStaticsDoneTime = VM_Time.now(); // for time scanning stacks & statics

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

      /*** no longer needed - see Minor Collection Finalization for commentary
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

	// following resets barrier bits in objects modified by moveToFinalizable
	// write buffer entries generated during GC will be discarded, and these
	// object may not get scanned in the next collection (hard to find bug) 
	//
	VM_WriteBuffer.resetBarrierBits(VM_Processor.getCurrentProcessor());
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

	/***
	if (RENDEZVOUS_WAIT_TIME) tempTime = VM_Time.now();
	VM_CollectorThread.gcBarrier.rendezvous();
	if (RENDEZVOUS_WAIT_TIME) mylocal.rendezvousWaitTime += VM_Time.now() - tempTime;
	***/
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
      
      if (TRACE) VM_Scheduler.trace("VM_Allocator", "(major collection) doing gc_finish");
      
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
      if (VM.AllocatorZapFromSpace) 
	// fill from space with 0x01010101, then zero on each allocation
	VM_Memory.fill(  zeroStart[VM_Processor.getCurrentProcessorId()],
			 (byte)1,
			 zeroBytes[VM_Processor.getCurrentProcessorId()] );
      else
	// each processor zeros its assigned region of the new nursery
	VM_Memory.zeroPages( zeroStart[VM_Processor.getCurrentProcessorId()],
			     zeroBytes[VM_Processor.getCurrentProcessorId()] );
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
	bytes = matureCurrentAddress - heapStartAddress;
      else
	bytes = heapEndAddress - matureCurrentAddress;
      totalMajorBytesCopied += bytes;
      if (bytes > maxMajorBytesCopied) maxMajorBytesCopied = bytes;

      // print verbose output

      if ( VM.verboseGC ) printVerboseOutputLine( 3 /* MAJOR*/ );

      // add current GC phase times into totals, print if verbose on
      if (TIME_GC_PHASES) accumulateGCPhaseTimes( true );  	
      
      if ( VM.verboseGC ) printWaitTimesAndCounts();

    }  // end selectedThread
    
    // following checkwritebuffer call is necessary to remove inadvertent entries
    // that are recorded during major GC, and which would crash the next minor GC
    //
    gc_checkWriteBuffers();
    
    // all GC threads return, having completed Major collection
    return;
  }  // collect
  
  // reset heap pointers, reset locks for next GC
  // executed at the end of Minor collections (if major collection not needed)
  // and at the end  of Major Collections.  Only executed by ONE of the
  // participating GC threads.
  //
  private static void
    gc_finish () {
    short[] shorttemp;
    
    if (VM.verboseGC) {
      gcTimeBeforeZeroing = VM_Time.now();
    }
    
    // redivide empty portion of heap in half, setup one of halves as allocation
    // area (FromSpace) for next allocation cycle
    if ( matureAllocationIncreasing ) {
      // mature objects now occupy left/lower end of heap
      // Use upper half of space from matureSpace to end of heap
      fromStartAddress = (matureCurrentAddress + ((heapEndAddress-matureCurrentAddress)/2) + 4095) & ~4095;
      fromEndAddress = heapEndAddress;
    } else {
      // mature objects now occupy upper/right end of heap
      // use left/lower half of space from start of heap to end of mature objects
      fromStartAddress = heapStartAddress;
      fromEndAddress = (heapStartAddress + ((matureCurrentAddress - heapStartAddress)/2) + 4095) & ~4095;
    }
    
    // now set allocation pointers to new nursery/FromSpace
    areaCurrentAddress = fromStartAddress;
    areaEndAddress = fromEndAddress;
    nurserySize = fromEndAddress - fromStartAddress;
    
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
      // determine amount for each processor to zero, round down to page multiple
      int np = VM_Scheduler.numProcessors;
      int zeroChunk = ((areaEndAddress - areaCurrentAddress)/np) & ~4095;
      int zeroBegin = areaCurrentAddress;
      for (int i=1; i<np; i++) {
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
      if (VM.AllocatorZapFromSpace) 
	// fill from space with 0x01010101, then zero on each allocation
	VM_Memory.fill( areaCurrentAddress, (byte)1, areaEndAddress-areaCurrentAddress );
      else
	// zero the new from space
	VM_Memory.zeroPages( areaCurrentAddress, areaEndAddress - areaCurrentAddress );
    }
    else {
      // if ZERO_BLOCKS_ON_ALLOCATION is on (others OFF!) then processors
      // zero there own processor local chunks when allocated, this 
      // requires that the allocator/collector be compiled with
      // PROCESSOR_LOCAL_ALLOCATE == true
      if (VM.VerifyAssertions) VM.assert(PROCESSOR_LOCAL_ALLOCATE == true);
      
      if (VM.AllocatorZapFromSpace) 
	// fill from space with 0x01010101, then zero on each allocation
	VM_Memory.fill( areaCurrentAddress, (byte)1, areaEndAddress-areaCurrentAddress );
    }
    
    // in minor collections mark OLD large objects, to keep until next major collection 
    // in major collections following just resets largeSpaceGen numbers of free pages
    gc_markOldLargeObjects();
    
    // exchange largeSpaceAlloc and largeSpaceMark
    shorttemp       = largeSpaceAlloc;
    largeSpaceAlloc = largeSpaceMark;
    largeSpaceMark  = shorttemp;
    large_last_allocated = 0;
    
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
  static int
    gc_getMatureSpace ( int size ) {
    int startAddress, newCurrentAddress;  
    
    // if compiled for processor local chunking of "mature space" attempt to allocate
    // in currently assigned region of mature space (other semi-space in simple semi-space scheme).
    // if not enough room, get another mature chunk using lwarx_stwcx
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
	if (matureAllocationIncreasing) {
	  startAddress = VM_Synchronization.fetchAndAdd(VM_Magic.addressAsObject(addrMatureCurrentAddress), 0, CHUNK_SIZE );
	  if (majorCollection && matureCurrentAddress > matureSaveAddress) {
	    if (GCDEBUG) 
	      VM.sysWrite("Out of Memory during Major Collection - Increase Major GC Threshold\n");
	    outOfMemory();
	  }
	}
	else { 
	  startAddress = VM_Synchronization.fetchAndDecrement(VM_Magic.addressAsObject(addrMatureCurrentAddress), 0, CHUNK_SIZE) - CHUNK_SIZE;
	  if (majorCollection && matureCurrentAddress < matureSaveAddress) {
	    if (GCDEBUG) 
	      VM.sysWrite("Out of Memory during Major Collection - Increase Major GC Threshold\n");
	    outOfMemory();
	  }
	}
	// startAddress = beginning of new mature space chunk for this processor
	st.localMatureEndAddress = startAddress + CHUNK_SIZE;
	st.localMatureCurrentAddress = startAddress + size;
	return startAddress;
      }
    } // end of chunking logic
    
    // else old non chunking logic, use single mature space ptr
    else {
      if (matureAllocationIncreasing) {
	startAddress = VM_Synchronization.fetchAndAdd(VM_Magic.addressAsObject(addrMatureCurrentAddress), 0, size );
	if (majorCollection && matureCurrentAddress > matureSaveAddress) {
	    if (GCDEBUG) 
	      VM.sysWrite("Out of Memory during Major Collection - Increase Major GC Threshold\n");
	    outOfMemory();
	}
      }
      else { 
	startAddress = VM_Synchronization.fetchAndDecrement(VM_Magic.addressAsObject(addrMatureCurrentAddress), 0, size) - size;
	if (majorCollection && matureCurrentAddress < matureSaveAddress) {
	    if (GCDEBUG) 
	      VM.sysWrite("Out of Memory during Major Collection - Increase Major GC Threshold\n");
	    outOfMemory();
	}
      }
      return startAddress;
    } // end old non-chunking logic
    
  }  // getMatureSpace
  
  
  // following used in this collector for marking bootimage & largespace objects
  // during major collections when reached/live boot objects are marked and scanned
  //
  static void
    gc_markObject ( int ref ) {
    int statusWord;
    
    if ( ref >= minBootRef && ref <= maxBootRef ) {
      
      if (  ! VM_Synchronization.testAndMark(VM_Magic.addressAsObject(ref), OBJECT_STATUS_OFFSET, BOOT_MARK_VALUE) )
	return;   // object already marked with current mark value
      
      // marked a previously unmarked object, put to work queue for later scanning
      VM_GCWorkQueue.putToWorkBuffer( ref );
    }
    else if ( ref >= minLargeRef ) {  // large object
      if (!gc_setMarkLarge(ref + OBJECT_HEADER_OFFSET)) {
	// we marked it, so put to workqueue
	VM_GCWorkQueue.putToWorkBuffer( ref );
      }
    }
  }  // gc_markObject
  
  
  /**
   * Processes live objects in FromSpace that need to be marked, copied and
   * forwarded during collection.  Returns the new address of the object
   * in ToSpace.  If the object was not previously marked, then the
   * invoking collectot thread will do the copying and enqueue the
   * on the work queue of objects to be scanned.
   *
   * @param fromRef Reference to object in FromSpace
   *
   * @return the address of the Object in ToSpace
   */
  static int
    gc_copyAndScanObject ( int fromRef ) {
    VM_Type type;
    int     full_size;
    int     statusWord;   // original status word from header of object to be copied
    int     toRef;        // address/ref of object in MatureSpace (as int)
    int     toAddress;    // address of header of object in MatureSpace (as int)
    int     fromAddress;  // address of header of object in FromSpace (as int)
    boolean assertion;
    
    if (VM.VerifyAssertions) VM.assert(validFromRef( fromRef ));
    
    toRef = VM_Synchronization.fetchAndMarkBusy(VM_Magic.addressAsObject(fromRef), OBJECT_STATUS_OFFSET);
    VM_Magic.isync();   // prevent instructions moving infront of fetchAndMark
    
    // if toRef is "marked" then object has been or is being copied
    if ( (toRef & OBJECT_GC_MARK_MASK) == MARK_VALUE ) {
      // if forwarding ptr == "busy pattern" object is being copied by another
      // GC thread, and wait (should be very short) for valid ptr to be set
      if (COUNT_COLLISIONS && (toRef == BEING_FORWARDED_PATTERN ))
	collisionCount++;
      while ( toRef == BEING_FORWARDED_PATTERN ) {
	toRef = VM_Magic.getMemoryWord(fromRef+OBJECT_STATUS_OFFSET);
      }
      // prevent following instructions from being moved in front of waitloop
      VM_Magic.isync();
      
      if (VM.VerifyAssertions) VM.assert( ((toRef & 3)==1) && validRef(toRef & ~3) );

      return toRef & ~3;   // mask out markbit
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
    type = VM_Magic.getObjectType(VM_Magic.addressAsObject(fromRef));
    if (VM.VerifyAssertions) VM.assert(validRef(VM_Magic.objectAsAddress(type)));
    if ( type.isClassType() ) {
      full_size = type.asClass().getInstanceSize();
      toAddress = gc_getMatureSpace(full_size);
      // position toref to 4 beyond end of object
      toRef = toAddress + full_size - SCALAR_HEADER_SIZE - OBJECT_HEADER_OFFSET;
      // position from to start of object data in FromSpace
      // remember, header is to right, ref is 4 bytes beyond header
      fromAddress = fromRef + OBJECT_HEADER_OFFSET + SCALAR_HEADER_SIZE - full_size;
      
      // now copy object (including the overwritten status word)
      VM_Memory.aligned32Copy( toAddress, fromAddress, full_size );
    }
    else {
      if (VM.VerifyAssertions) VM.assert(type.isArrayType());
      int num_elements = VM_Magic.getArrayLength(VM_Magic.addressAsObject(fromRef));
      full_size = type.asArray().getInstanceSize(num_elements);
      full_size = (full_size + 3) & ~3;;  //need Magic to roundup
      toAddress = gc_getMatureSpace(full_size);
      toRef = toAddress - OBJECT_HEADER_OFFSET;
      fromAddress = fromRef+OBJECT_HEADER_OFFSET;
      
      // now copy object(array) (including the overwritten status word)
      VM_Memory.aligned32Copy( toAddress, fromAddress, full_size );
      
      // sync all arrays of ints - must sync moved code instead of sync'ing chunks when full
      // changed 11/03/00 to fix ExecuteOptCode failure (GC executing just moved code)
      if (type == arrayOfIntType)
	VM_Memory.sync(toAddress, full_size);
    }
    
    // replace status word in copied object, forcing writebarrier bit on (bit 30)
    // markbit in orig. statusword should be 0 (unmarked)
    VM_Magic.setMemoryWord(toRef + OBJECT_STATUS_OFFSET, statusWord | OBJECT_BARRIER_MASK );
    
    VM_Magic.sync(); // make changes viewable to other processors 
    
    // set status word in old/from object header to forwarding address with
    // the low order markbit set to "marked". This store will overwrite
    // the BEING_FORWARDED_PATTERN and let other waiting/spinning GC threads proceed.
    VM_Magic.setMemoryWord(fromRef+OBJECT_STATUS_OFFSET, toRef | OBJECT_GC_MARK_MASK);
    
    // following sync is optional, not needed for correctness
    // VM_Magic.sync(); // make changes viewable to other processors 
    
    // add copied object to GC work queue, so it will be scanned later
    VM_GCWorkQueue.putToWorkBuffer( toRef );
    
    return toRef;
  }  // gc_copyAndScanObject
  
  
  // process writeBuffer attached to the running GC threads current processor
  //
  static void
    gc_processWriteBuffers () {
    VM_WriteBuffer.processWriteBuffer(VM_Processor.getCurrentProcessor());
  }
  
  // check that writeBuffer attached to the running GC threads current processor
  // is empty, if not print diagnostics & reset
  //
  static void
    gc_checkWriteBuffers () {
    VM_WriteBuffer.checkForEmpty(VM_Processor.getCurrentProcessor());
  }
  

  // for copying objects (NOT arrays) and not putting on queue for later scanning
  // used for VM_Thread & VM_Processor objects which are scanned explicitly so that
  // interior pointers can be updated (stacks, writeBuffers,..)
  //
  static int
    gc_copyObject ( int fromRef ) {
    VM_Type type;
    int     full_size;
    int     statusWord;   // original status word from header of object to be copied
    int     toRef;        // address/ref of object in MatureSpace (as int)
    int     toAddress;    // address of header of object in MatureSpace (as int)
    int     fromAddress;  // address of header of object in FromSpace (as int)
    boolean assertion;
    
    statusWord = VM_Synchronization.fetchAndMarkBusy(VM_Magic.addressAsObject(fromRef), OBJECT_STATUS_OFFSET);
    VM_Magic.isync();   // prevent instructions moving infront of fetchAndMark
    
    // if statusWord is "marked" then object has been or is being copied
    if ( (statusWord & OBJECT_GC_MARK_MASK) == MARK_VALUE ) {
      
      // if forwarding ptr == "busy pattern" object is being copied by another
      // GC thread, and wait (should be very short) for valid ptr to be set
      if (COUNT_COLLISIONS && (statusWord == BEING_FORWARDED_PATTERN ))
	collisionCount++;
      while ( statusWord == BEING_FORWARDED_PATTERN ) {
	statusWord = VM_Magic.getMemoryWord(fromRef+OBJECT_STATUS_OFFSET);
      }
      // prevent following instructions from being moved in front of waitloop
      VM_Magic.isync();
      
      if (VM.VerifyAssertions) VM.assert( ((statusWord & 3)==1) && validRef(statusWord & ~3) );
      
      return statusWord & ~3;   // mask off markbit & busy bit
    }
    
    // statusWord is the original status word - copy, set forwarding ptr, and mark
    // If have multiple gc threads &
    // fetchAndMarkBusy returned a word NOT marked busy, then it has returned
    // the original status word (ie lock bits, thread id etc) and replaced it
    // with the the BEING_FORWARDED_PATTERN (which has the mark bit set).
    // If here, we must do the forwarding/copying, setting the real forwarding
    // pointer in the status word ASAP
    
    type = VM_Magic.getObjectType(VM_Magic.addressAsObject(fromRef));
    if (VM.VerifyAssertions) VM.assert(validRef(VM_Magic.objectAsAddress(type)));
    if (VM.VerifyAssertions) VM.assert(type.isClassType());
    full_size = type.asClass().getInstanceSize();
    toAddress = gc_getMatureSpace(full_size);
    // position toref to 4 beyond end of object
    toRef = toAddress + full_size - SCALAR_HEADER_SIZE - OBJECT_HEADER_OFFSET;
    // position from to start of object data in FromSpace
    // remember, header is to right, ref is 4 bytes beyond header
    fromAddress = fromRef + OBJECT_HEADER_OFFSET + SCALAR_HEADER_SIZE - full_size;
    
    // copy object...before status word modified
    VM_Memory.aligned32Copy( toAddress, fromAddress, full_size );
    
    // replace status word in copied object, forcing writebarrier bit on (bit 30)
    // markbit in orig. statusword should be 0 (unmarked)
    VM_Magic.setMemoryWord(toRef + OBJECT_STATUS_OFFSET, statusWord | OBJECT_BARRIER_MASK );
    
    // sync here to ensure copied object is intact, before setting forwarding ptr
    VM_Magic.sync(); // make changes viewable to other processors 
    
    // set status word in old/from object header to forwarding address with
    // the low order markbit set to "marked". This store will overwrite
    // the BEING_FORWARDED_PATTERN and let other waiting/spinning GC threads proceed.
    VM_Magic.setMemoryWord(fromRef+OBJECT_STATUS_OFFSET, toRef | OBJECT_GC_MARK_MASK);
    
    // following sync is optional, not needed for correctness
    // VM_Magic.sync(); // make changes viewable to other processors 
    
    return toRef;
  }  // copyObject
  
  
  // called by ONE gc/collector thread to copy ALL "new" thread and processor objects
  // copies but does NOT enqueue for scanning
  //
  static void 
    gc_copyThreads ()  {
    int          i, ta, vpa, thread_count, processor_count;
    VM_Thread    t;
    VM_Processor vp;
    
    for ( i=0; i<VM_Scheduler.threads.length; i++ ) {
      t = VM_Scheduler.threads[i];
      if ( t == null ) continue;
      
      ta = VM_Magic.objectAsAddress(t);
      if ( ta >= minFromRef && ta <= maxFromRef ) {
	ta = gc_copyObject(ta);
	// change entry in threads array to point to new copy of thread
	VM_Magic.setMemoryWord( VM_Magic.objectAsAddress(VM_Scheduler.threads)+(i*4), ta);
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
	
	if (VM.VerifyAssertions) oldstack = t.stack;  // for verifying  gc stacks not moved
	VM_ScanObject.scanObjectOrArray(ta);              // will copy copy stacks, reg arrays, etc.
	if (VM.VerifyAssertions) VM.assert(oldstack == t.stack);
	
	if (t.jniEnv != null) VM_ScanObject.scanObjectOrArray(t.jniEnv);
	VM_ScanObject.scanObjectOrArray(t.contextRegisters);
	VM_ScanObject.scanObjectOrArray(t.hardwareExceptionRegisters);
	
	if (GCDEBUG_SCANTHREADS) VM_Scheduler.trace("VM_Allocator","Collector Thread scanning own stack",i);
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
      
      // have mutator thread, compete for it with other GC threads
      if ( VM_GCLocks.testAndSetThreadLock(i) ) {
	
	if (debugNative || GCDEBUG_SCANTHREADS) VM_Scheduler.trace("VM_Allocator","processing mutator thread",i);
	
	// all threads should have been copied out of fromspace earlier
	if (VM.VerifyAssertions) VM.assert( !(ta >= minFromRef && ta <= maxFromRef) );
	
	// scan thread object to force "interior" objects to be copied, marked, and
	// queued for later scanning.
	oldstack = t.stack;    // remember old stack address before scanThread
	VM_ScanObject.scanObjectOrArray(ta);
	
	// if stack moved, adjust interior stack pointers
	if ( oldstack != t.stack ) {
	  if (GCDEBUG_SCANTHREADS) VM_Scheduler.trace("VM_Allocator","...adjusting mutator stack",i);
	  t.fixupMovedStack(VM_Magic.objectAsAddress(t.stack) - VM_Magic.objectAsAddress(oldstack));
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
	  fp = env.JNITopJavaFP; 
	  
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


	if (TRACE) VM_Scheduler.trace("VM_Allocator","scanning stack for thread",i);
	//gc_scanStack(t,fp);
	VM_ScanStack.scanStack( t, VM_NULL, true /*relocate_code*/ );

	//-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS
	// alternate implementation of jni
	// if this thread has an associated native VP, then move its writebuffer entries 
	// in the workqueue for later scanning
	//
	if ( t.nativeAffinity != null ) 
	  VM_WriteBuffer.moveToWorkQueue(t.nativeAffinity);
	//-#else
	// default implementation of jni
	//  do nothing here, write buffer entries moved in prepare...ForGC()
	//-#endif
	
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
    int            sta;
    VM_Processor   st;
    VM_Thread      activeThread;
    int            tid;   // id of active thread
    
    st = VM_Processor.getCurrentProcessor();
    sta = VM_Magic.objectAsAddress(st);
    activeThread = st.activeThread;
    tid = activeThread.getIndex();
    
    if (VM.VerifyAssertions) VM.assert(tid == VM_Thread.getCurrentThread().getIndex());
    
    
    // if compiled for processor local chunking of "mature space" reset processor local 
    // pointers, to cause first request to get a block (only reset on major collection
    // for minor collection, continue filling last/current mature buffer
    //
    if (PROCESSOR_LOCAL_MATURE_ALLOCATE) {
      if (majorCollection) {
	st.localMatureCurrentAddress = 0;
	st.localMatureEndAddress = 0;
      }
    }
    
    // if Processor is in fromSpace, copy and update array entry
    if ( sta >= minFromRef && sta <= maxFromRef ) {
      sta = gc_copyObject(sta);   // copy thread object, do not queue for scanning
      // change entry in system threads array to point to copied sys thread
      VM_Magic.setMemoryWord( VM_Magic.objectAsAddress(VM_Scheduler.processors)+(st.id*4), sta);
      // should have Magic to recast addressAsProcessor, instead 
      // reload st from just modified array entry
      st = VM_Scheduler.processors[st.id];
    }
    
    // each gc thread updates its PROCESSOR_REGISTER after copying its VM_Processor object
    VM_Magic.setProcessorRegister(st);
    
    if (PROCESSOR_LOCAL_ALLOCATE) {
      //  reset local heap pointers .. causes first mutator allocate to
      //   get a locak Chunk from the shared heap
      //
      st.localCurrentAddress = 0;
      st.localEndAddress     = 0;
    }
    
    // if Processors activethread (should be current, gc, thread) is in fromSpace, copy and
    // update activeThread field and threads array entry to make sure BOTH ways of computing
    // getCurrentThread return the new copy of the thread
    int ata = VM_Magic.objectAsAddress(activeThread);
    if ( ata >= minFromRef && ata <= maxFromRef ) {
      // copy thread object, do not queue for scanning
      ata = gc_copyObject(ata);
      st.activeThread = VM_Magic.addressAsThread(ata);
      // change entry in system threads array to point to copied sys thread
      VM_Magic.setMemoryWord( VM_Magic.objectAsAddress(VM_Scheduler.threads)+(tid*4), ata);
    }
    
    // setup the work queue buffers for this gc thread
    VM_GCWorkQueue.resetWorkQBuffers();
    
  }  // gc_initProcessor
  
  // scan a VM_Processor object to force "interior" objects to be copied, marked,
  // and queued for later scanning. adjusts write barrier pointers, if
  // write buffer is moved.
  //
  static void 
    gc_scanProcessor ()  {
    int               sta, oldbuffer, newbuffer;
    VM_Processor   st;
    
    st = VM_Processor.getCurrentProcessor();
    sta = VM_Magic.objectAsAddress(st);
    
    if (PROCESSOR_LOCAL_ALLOCATE) {
      // local heap pointer set in initProcessor, should still be 0, ie no allocates yet
      if (VM.VerifyAssertions) VM.assert(st.localCurrentAddress == 0);
    }
    
    if (VM.VerifyAssertions) {
      // processor should already be copied, ie NOT in FromSpace
      VM.assert(!(sta >= minFromRef && sta <= maxFromRef));
      // and its processor array entry updated
      VM.assert(sta == VM_Magic.objectAsAddress(VM_Scheduler.processors[st.id]));
    }
    
    // scan system thread object to force "interior" objects to be copied, marked, and
    // queued for later scanning.
    oldbuffer = VM_Magic.objectAsAddress(st.modifiedOldObjects);
    VM_ScanObject.scanObjectOrArray(sta);
    
    // if writebuffer moved, adjust interior pointers
    newbuffer = VM_Magic.objectAsAddress(st.modifiedOldObjects);
    if (oldbuffer != newbuffer) {
      st.modifiedOldObjectsMax = newbuffer + (st.modifiedOldObjectsMax - oldbuffer);
      st.modifiedOldObjectsTop = newbuffer + (st.modifiedOldObjectsTop - oldbuffer);
    }
    
  }  // scanProcessor
  
  
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
    int i,num_pages,countLargeOld,countLargeNew;
    int contiguousFreePages,maxContiguousFreePages;
    
    for (i =  0; i < GC_LARGE_SIZES; i++) countLargeAlloc[i] = 0;
    countLargeNew = countLargeOld = contiguousFreePages = maxContiguousFreePages = 0;
    
    for (i =  0; i < largeSpacePages;) {
      num_pages = largeSpaceAlloc[i];
      if (VM.VerifyAssertions) VM.assert(num_pages >= 0);
      if (num_pages == 0) {     // no large object found here
	countLargeAlloc[0]++;   // count free pages in entry[0]
	contiguousFreePages++;
	i++;
      }
      else {    // at beginning of a large object
	if (num_pages < GC_LARGE_SIZES-1) countLargeAlloc[num_pages]++;
	else countLargeAlloc[GC_LARGE_SIZES - 1]++;
	if (largeSpaceGen[i] == 0)
	  countLargeNew++;
	else
	  countLargeOld++;
	if ( contiguousFreePages > maxContiguousFreePages )
	  maxContiguousFreePages = contiguousFreePages;
	contiguousFreePages = 0;
	i = i + num_pages;       // skip to next object or free page
      }
    }
    if ( contiguousFreePages > maxContiguousFreePages )
      maxContiguousFreePages = contiguousFreePages;
    
    VM.sysWrite("\n*** Large Objects Allocated - by num pages ***\n");
    for (i = 0; i < GC_LARGE_SIZES-1; i++) {
      VM.sysWrite("pages ");
      VM.sysWrite(i);
      VM.sysWrite(" count ");
      VM.sysWrite(countLargeAlloc[i]);
      VM.sysWrite("\n");
    }
    VM.sysWrite(countLargeAlloc[GC_LARGE_SIZES-1],false);
    VM.sysWrite(" large objects ");
    VM.sysWrite(GC_LARGE_SIZES-1,false);
    VM.sysWrite(" pages or more.\n");
    VM.sysWrite(countLargeOld,false);
    VM.sysWrite(" allocated large objects are OLD.\n");
    VM.sysWrite(countLargeNew,false);
    VM.sysWrite(" allocated large objects are NEW.\n");
    VM.sysWrite(countLargeAlloc[0],false);
    VM.sysWrite(" Large Object Space pages are FREE.\n");
    VM.sysWrite(maxContiguousFreePages,false);
    VM.sysWrite(" is largest block of contiguous free pages.\n");
    VM.sysWrite("*** End of Large Object Counts ***\n\n");
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

  /**
   * Mark a large space object, if not already marked
   *
   * @return  true if already marked, false if not marked & this invocation marked it.
   */
  static boolean
    gc_setMarkLarge (int tref) { 
    int ij, temp, temp1;
    int page_num = (tref - largeHeapStartAddress ) >> 12;
    boolean result = (largeSpaceMark[page_num] != 0);
    if (result) return true;	// fast, no synch case
       
    sysLockLarge.lock();		// get sysLock for large objects
    result = (largeSpaceMark[page_num] != 0);
    if (result) {	// need to recheck
      sysLockLarge.release();
      return true;	
    }
    temp = largeSpaceAlloc[page_num];
    if (temp == 1) {
      if (largeSpaceGen[page_num] <= GC_OLD )
	largeSpaceGen[page_num]++;
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
	   
      // increment Gen number of live Large Space object
      if (largeSpaceGen[ij] <= GC_OLD) {
	largeSpaceGen[ij]++;              // Gen number is stored at both 
	largeSpaceGen[page_num]++;        // ends of hte allocated interval
      }
    }
       
    // Need to turn back on barrier bit *always*
    do {
      temp1 = VM_Magic.prepare(VM_Magic.addressAsObject(tref),
			       -(OBJECT_HEADER_OFFSET - OBJECT_STATUS_OFFSET));
      temp = temp1 | OBJECT_BARRIER_MASK;
    } while (!VM_Magic.attempt(VM_Magic.addressAsObject(tref),
			       -(OBJECT_HEADER_OFFSET - OBJECT_STATUS_OFFSET), temp1, temp));
       
    sysLockLarge.unlock();	// INCLUDES sync()

    return false;
  }  // gc_setMarkLarge

  /**
   * Update Large Space Mark and Generation numbers after Major & Minor collections.
   */
  private static void
    gc_markOldLargeObjects () { 
    int i,j,ii;
    
    for (i =  0; i <= largeSpaceHiWater;) {
      ii = largeSpaceMark[i];
      
      if (VM.VerifyAssertions) VM.assert( ii >= 0 );  
      
      if (ii == 0) {		// no live object found here
	j = largeSpaceGen[i]; // now check for old object
	if (j == 0) {
	  i++;
	  continue; // was not live before this collection
	}
	else {	// was live; either new object became garbage, or old
	  ii = largeSpaceAlloc[i];	// tells us size
	  if (j >= GC_OLD) {  // an old object 
	    if (!majorCollection) {	// this is not a full collection
	      largeSpaceMark[i + ii -1] = (short)(-ii);
	      largeSpaceMark[i] = (short)ii;
	      i = i + ii ;
	      continue;
	    }
	  }
	  largeSpaceGen[i] = 0;   // an old (if a full collection) 
	  // or middle-aged object became garbage
	  largeSpaceGen[i + ii - 1] = 0;   // and the other end              
	  i = i + ii ;		// do correct increment of loop
	}
      }
      else i = i + ii ;
    }
  }  // gc_markOldLargeObjects
  
  /**
   * Allocate space for a "large" object in the Large Object Space
   *
   * @param size  size in bytes needed for the large object
   * @return  address of first byte of the region allocated or 
   *          -2 if not enough space.
   */
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
	       
	if (first_free > largeSpaceHiWater) 
	  largeSpaceHiWater = first_free;

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
  
  static boolean
    validRef ( int ref ) {
    if ( ref >= minBootRef && ref <= maxHeapRef ) return true;
    if ( ref >= minLargeRef && ref <= maxLargeRef ) return true;
    else return false;
  }	
  
  static boolean
    validFromRef ( int ref ) {
    if ( ref >= minFromRef && ref <= maxFromRef ) return true;
    else return false;
  }	
  
  static boolean
    validMatureRef ( int ref ) {
    if ( majorCollection ) {
      // "mature" during a major collection == only BootImage
      if ( ref >= minBootRef && ref <= maxBootRef ) return true;
      else return false;
    }
    else {
      if ( matureAllocationIncreasing ) {
	if ( ref >= minBootRef && ref <= matureSaveAddress+4 ) return true;
	else return false;
      } 
      else {
	if ( ( ref >= minBootRef && ref <= maxBootRef) ||
	     ( ref >= matureSaveAddress && ref <= heapEndAddress+4) )
	  return true;
	else
	  return false;
      }
    }
  }	
  
  static boolean
    validForwardingPtr ( int ref ) {
    if ( majorCollection ) {
      if ( matureAllocationIncreasing ) {
	// copying mature objs to left/lower part of heap 	
	if ( ref >= minHeapRef && ref <= matureCurrentAddress+4 ) return true;
	else return false;
      } 
      else {
	// copying mature objs to right/upper part of heap 	
	if ( ref >= matureCurrentAddress+4 && ref <= maxHeapRef ) return true;
	else return false;
      }
    }
    else {
      if ( matureAllocationIncreasing ) {
	if ( ref >= matureSaveAddress-OBJECT_HEADER_OFFSET  &&
	     ref <= matureCurrentAddress+4 ) return true;
	else return false;
      } 
      else {
	if ( ref >= matureCurrentAddress-OBJECT_HEADER_OFFSET  &&
	     ref <= matureSaveAddress+4 )  return true;
	else return false;
      }
    }
  }	
  
  static boolean
    validWorkQueuePtr ( int ref ) {
    if ( ref >= minBootRef && ref <= maxBootRef ) return true;
    if ( ref >= minLargeRef && ref <= maxLargeRef ) return true;
    else return validForwardingPtr( ref );
  }
  
  static void
    dumpThreadsArray () {
    VM_Thread t;
    VM.sysWrite("VM_Scheduler.threads[]:\n");
    for ( int i=0; i<VM_Scheduler.threads.length; i++ ) {
      VM.sysWrite(" i = ");
      VM.sysWrite(i);
      t = VM_Scheduler.threads[i];
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
  
  static void
    dumpProcessorsArray () {
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
  private static void
    fail ()
  {
    VM.sysWrite("vm error: allocator/collector called within critical section\n");
    VM.assert(false);
  }
  
  // allocate buffer for allocates during traceback & call sysFail (gets stacktrace)
  // or sysWrite the message and sysExit (no traceback possible)
  //
  private static void
    crash (String err_msg) {
    int tempbuffer;
    VM.sysWrite("VM_Allocator.crash:\n");
    if (PROCESSOR_LOCAL_ALLOCATE) {
      if ((tempbuffer = VM.sysCall1(bootrecord.sysMallocIP,
				    VM_Allocator.CRASH_BUFFER_SIZE)) == 0) {
	VM.sysWrite("VM_ALLOCATOR.crash() sysMalloc returned 0 \n");
	VM.shutdown(1800);
      }
      VM_Processor p = VM_Processor.getCurrentProcessor();
      p.localCurrentAddress = tempbuffer;
      p.localEndAddress = tempbuffer + VM_Allocator.CRASH_BUFFER_SIZE;
      VM_Memory.zero(tempbuffer, tempbuffer + VM_Allocator.CRASH_BUFFER_SIZE);
      VM.sysFail(err_msg);
    }
    else {
      VM.sysFail(err_msg);
    }
  }
  
  static int
    getnewblockx (int ndx) {
    return -1;
  }
  
  // Called from VM_Processor constructor: 
  // Must alloc & initialize write buffer, allocation pointers already zero
  static void
    setupProcessor (VM_Processor p) {
    VM_WriteBuffer.setupProcessor(p);
  }
  
  // following referenced by refcountGC methods (but not called)
  static void
    gc_scanStacks ()
  {
  }
  
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
    printRendezvousTimes (boolean majorFlag) {
    
    VM.sysWrite("RENDEZVOUS ENTRANCE & EXIT TIMES (microsecs) rendevous 1, 2 & 3");
    if (majorFlag)
      VM.sysWrite(" (MAJOR COLLECTION)\n");
    else 
      VM.sysWrite(" (MINOR COLLECTION)\n");
    
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
  
  private static void
  accumulateGCPhaseTimes ( boolean afterMajor ) {
    double start = 0.0;
    if (!afterMajor) 
      start    = gcStartTime - VM_CollectorThread.startTime;
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
    if (VM.verboseGC) {
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

  static void
  printSummaryStatistics () {
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

    VM.sysWrite("\nGC stats: Copying Generational Collector - Variable Nursery (");
    VM.sysWrite(np,false);
    VM.sysWrite(" Collector Threads ):\n");
    VM.sysWrite("          Heap Size ");
    VM.sysWrite(smallHeapSize,false);
    VM.sysWrite("  Large Object Heap Size ");
    VM.sysWrite(largeHeapSize,false);
    VM.sysWrite("\n");

    VM.sysWrite("  ");
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
    
    if (RENDEZVOUS_TIMES)  printRendezvousTimes(false);
    
  }  // printWaitTimesAndCounts

  private static void
    printVerboseOutputLine (int phase) {
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
	VM.sysWrite(matureCurrentAddress-matureSaveAddress,false);
      else
	VM.sysWrite(matureSaveAddress-matureCurrentAddress,false);
      VM.sysWrite(" mature ");
      if ( matureAllocationIncreasing ) {
	VM.sysWrite("(inc) " );
	bytes = matureCurrentAddress - heapStartAddress;
      }
      else {
	VM.sysWrite("(dec) " );
	bytes = heapEndAddress - matureCurrentAddress;
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
	VM.sysWrite(matureCurrentAddress-matureSaveAddress,false);
      else
	VM.sysWrite(matureSaveAddress-matureCurrentAddress,false);
      VM.sysWrite(" mature ");
      if ( matureAllocationIncreasing ) {
	VM.sysWrite("(inc) " );
	bytes = matureCurrentAddress - heapStartAddress;
      }
      else {
	VM.sysWrite("(dec) " );
	bytes = heapEndAddress - matureCurrentAddress;
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
	bytes = matureCurrentAddress - heapStartAddress;
      }
      else {
	VM.sysWrite("(dec) " );
	bytes = heapEndAddress - matureCurrentAddress;
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
  static boolean
    processFinalizerListElement (VM_FinalizerListElement le) {
    int ref = le.value;
    
    // Processing for object in "FromSpace" is same for minor & major collections.
    // For minor GCs, FromSpace is the Nursery, for major GCs it is the old mature space.
    //
    if ( ref >= minFromRef && ref <= maxFromRef ) {
      int statusword = VM_Magic.getMemoryWord(ref + OBJECT_STATUS_OFFSET);
      if ( (statusword & OBJECT_GC_MARK_MASK) == MARK_VALUE ) {
	// live, set le.value to forwarding address
	le.value = statusword & ~3;
	return true;
      }
      else {
	// dead, mark, copy, and enque for scanning, and set le.pointer
	le.pointer = VM_Magic.addressAsObject(gc_copyAndScanObject(ref));
	le.value = -1;
	return false;
      }
    }
    
    // for minor collections, objects in mature space are assumed live.
    // they are not moved, and le.value is OK
    if ( ! majorCollection ) {
      if ( matureAllocationIncreasing ) {
	if ( ref > heapStartAddress && ref <= matureSaveAddress+4 ) return true;
      }
      else { /* matureAllocationDecreasing */
	if ( ref > matureSaveAddress && ref <= heapEndAddress+4) return true;
      }
    }
    
    // if here, for minor & major collections, le.value should be for an object
    // in large space
    //
    if (VM.VerifyAssertions) VM.assert(ref >= minLargeRef);
    int tref = ref + OBJECT_HEADER_OFFSET;
    int page_num = (tref - largeHeapStartAddress ) >> 12;
    if (largeSpaceMark[page_num] != 0)
      return true;   // marked, still live, le.value is OK
    
    // for minor collections, old large objects are considered live
    if (!majorCollection && (largeSpaceGen[page_num] >= GC_OLD))
      return true;   // not marked, but old, le.value is OK
    
    // if here, have garbage large object, mark live, and enqueue for scanning
    gc_setMarkLarge(tref);
    VM_GCWorkQueue.putToWorkBuffer(ref);
    
    le.pointer = VM_Magic.addressAsObject(ref);
    le.value = -1;
    return false;
    
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
   *
   * @param location  address of a reference field
   */
  static void
  processPtrField ( int location ) {
    int tref, page_num;
    int ref = VM_Magic.getMemoryWord( location );
    
    if (ref == VM_NULL) return;
    
    // always process objects in the Nursery (forward if not already forwarded)
    if ( ref >= minFromRef && ref <= maxFromRef ) {
      VM_Magic.setMemoryWord( location, gc_copyAndScanObject( ref ) );
      return;
    }
    
    // if a major collection, mark and scan all bootimage and large objects
    if ( majorCollection ) {
      gc_markObject( ref );
      return;
    }
    
    // a minor collection: mark and scan (and age) only NEW large objects
    if ( ref >= minLargeRef ) {
      if (VM.VerifyAssertions) VM.assert(ref <= maxLargeRef);
      tref = ref + OBJECT_HEADER_OFFSET;
      page_num = (tref - largeHeapStartAddress  ) >> 12;
      if ( largeSpaceGen[page_num] == 0 ) {  // new large object
	if (!gc_setMarkLarge(tref))
	  // we marked it, so put to workqueue
	  VM_GCWorkQueue.putToWorkBuffer( ref );
      }
    }
  } // processPtrField

  /**
   * Process an object reference (value) during collection.
   *
   * @param location  address of a reference field
   */
  static int
  processPtrValue ( int ref ) {
    int tref, page_num;
    
    if (ref == VM_NULL) return ref;
    
    // always process objects in the Nursery (forward if not already forwarded)
    if ( ref >= minFromRef && ref <= maxFromRef ) {
      return gc_copyAndScanObject(ref);  // return new reference
    }
    
    // if a major collection, mark and scan all bootimage and large objects
    if ( majorCollection ) {
      gc_markObject( ref );
      return ref;     // return original (unmoved) ref
    }
    
    // a minor collection: mark and scan (and age) only NEW large objects
    if ( ref >= minLargeRef ) {
      if (VM.VerifyAssertions) VM.assert(ref <= maxLargeRef);
      tref = ref + OBJECT_HEADER_OFFSET;
      page_num = (tref - largeHeapStartAddress  ) >> 12;
      if ( largeSpaceGen[page_num] == 0 ) {  // new large object
	if (!gc_setMarkLarge(tref))
	  // we marked it, so put to workqueue
	  VM_GCWorkQueue.putToWorkBuffer( ref );
      }
    }

    return ref;    // return original (unmoved) ref
  }
  
}   // VM_Allocator
