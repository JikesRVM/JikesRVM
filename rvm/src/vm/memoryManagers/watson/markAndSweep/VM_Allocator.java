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
 *    Both copying and noncopying versions of VM_Allocator
 *     provide identical "interfaces":
 * <pre>
 *        init()
 *        boot()
 *        allocateScalar()
 *        allocateArray()
 *        cloneScalar()
 *        cloneArray()
 * </pre>
 * Selection of copying vs. noncopying allocators is a choice
 * made at boot time by specifying appropriate directory in CLASSPATH.
 *
 * @author Dick Attanasio
 * @modified by Stephen Smith
 * @modified by David F. Bacon
 * 
 * @modified by Perry Cheng  Heavily re-written to factor out common code and adding VM_Address
 */
public class VM_Allocator
  implements VM_Constants, VM_GCConstants, VM_Uninterruptible,
  VM_Callbacks.ExitMonitor, VM_Callbacks.AppRunStartMonitor
  {

  static final boolean IGNORE_EXPLICIT_GC_CALLS = false;

  static final VM_Array BCArrayType  = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("[LVM_BlockControl;")).asArray();
  static final VM_Array byteArrayType = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("[B")).asArray();
  private static final int byteArrayHeaderSize = VM_ObjectModel.computeArrayHeaderSize(byteArrayType);
  static Object[] byteArrayTIB;  // we cache this because the code to get this is interruptible

  static final boolean GCDEBUG_PARTIAL = false; 
  static final boolean DEBUG_FREEBLOCKS = false; 
  // Debug free space calculation
  static final boolean GCDEBUG_FREESPACE = false; 

  // Debug free slot management
  static final boolean DEBUG_FREE = false;

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

  final static int OUT_OF_BLOCKS =  -1;

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
  static final boolean GC_USE_LARX_STCX          = true;
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

  //
  // Data Fields that control the allocation of memory
  //
  // Modifications by CRA; add subpools to the heap; allocate from 
  // fixed size blocks in subpools; never-copying collector

  // Here are the new fields for the dynamic version: blocks are allocated to
  // pools dynamically

  static VM_ProcessorLock sysLockSmall;
  static VM_ProcessorLock sysLockFree;
  static VM_ProcessorLock sysLockBlock;

  // following included because referenced by refcountGC files (but not used)
  static int bufCount;

  static int verbose = 1;

  static final boolean  Debug = false;  
  static final boolean  Debug_torture = false;  
  static final boolean  DebugInterest = false;  
  static final boolean  DebugLink = false;
  static final boolean  GC_CLOBBERFREE = false;  

	// statistics reporting
	//
	static final boolean REPORT_BLOCKS = false;
	static boolean flag2nd = false;
	static int[] accum;
	static int[] total;

  // 1 VM_BlockControl per GC-SIZES for initial use, before heap setup
  static VM_BlockControl[]  init_blocks;  // VM_BlockControl[] 1 per BLKSIZE block of the heap
  static VM_BlockControl[]  blocks;  // 1 per BLKSIZE block of the heap
        
  static int    allocCount  = 0; // updated every entry to allocate<x>
  static int    fastAllocCount  = 0; // updated every entry to allocate<x>
  static int    gcCount  = 0; // updated every entry to collect
  // major collections in generational scheme
  static int    gcMajorCount = 0; 
  static boolean gc_collect_now = false; // flag to do a collection (new logic)

  private static VM_Heap bootHeap             = new VM_Heap("Boot Image Heap");   
  private static VM_Heap smallHeap            = new VM_Heap("Small Object Heap");
  private static VM_ImmortalHeap immortalHeap = new VM_ImmortalHeap();
  private static VM_LargeHeap largeHeap       = new VM_LargeHeap(immortalHeap);
  private static VM_MallocHeap mallocHeap     = new VM_MallocHeap();

  // These two fields are unused but unconditionally needed in VM_EntryPoints.
  static VM_Address areaCurrentAddress;    
  static VM_Address matureCurrentAddress;  

  static int    num_blocks;    // number of blocks in the heap
  static int    first_freeblock;  // number of first available block
  static int    highest_block;    // number of highest available block
  static int    blocks_available;  // number of free blocks for small obj's

  static int[]       old_objects;            // array to track old objects
  static int         nextOldObject;          // next entry in old_objects to use

  static int[] countLive;
  static int[] countSmallFree;        // bytes allocated by size
  static int[] countSmallBlocksAlloc;  // blocks allocated by size
  static int[] countByteSizes;    // objects allocated by bytesize

  static VM_BootRecord   bootrecord;
 
  static int markboot_count = 0;  // counter of objects marked in the boot image
  static int total_markboot_count = 0;  // counter of times markboot called

  static int marklarge_count = 0;  // counter of large objects marked

  static boolean gcInProgress = false;
  static int OBJECT_GC_MARK_VALUE = 0;  // changes between this and 0

  static int[]       partialBlockList;         // GSC
// value below is a tuning parameter: for single threaded appl, on multiple processors
  static int         numBlocksToKeep = 10;     // GSC 
  static final boolean GSC_TRACE = false;			 // GSC

  /** "getter" function for gcInProgress
  */
  
  static boolean  gcInProgress() {
      return gcInProgress;
  }

  static  void init () {

  int i, ii;
  VM_GCLocks.init();  
  VM_Processor st = VM_Scheduler.processors[VM_Scheduler.PRIMORDIAL_PROCESSOR_ID];
  sysLockBlock      = new VM_ProcessorLock();
  // create instance of Common work queue object
  VM_GCWorkQueue.workQueue = new VM_GCWorkQueue();

  partialBlockList = new int[GC_SIZES];          // GSC
  for (i = 0; i < GC_SIZES; i++) partialBlockList[i] = OUT_OF_BLOCKS;

  st.sizes = new VM_SizeControl[GC_SIZES];
  init_blocks = new VM_BlockControl[GC_SIZES];
  if (GC_COUNT_BYTE_SIZES) countByteSizes = new int[2048 + 1];

  VM_CollectorThread.init();   // to alloc its rendezvous arrays, if necessary

  // On the jdk side, we allocate an array of VM_SizeControl Blocks, 
  // one for each size.
  // We also allocate init_blocks array within the boot image.  
  // At runtime we allocate the rest of the BLOCK_CONTROLS, whose number 
  // depends on the heapsize, and copy the contents of init_blocks 
  // into the first GC_SIZES of them.

  for (i = 0; i < GC_SIZES; i++) {
    st.sizes[i] = new VM_SizeControl();
    init_blocks[i] = new VM_BlockControl();
    st.sizes[i].first_block = i;  // 1 block/size initially
    st.sizes[i].current_block = i;
    st.sizes[i].ndx = i;
    init_blocks[i].mark = new byte[GC_BLOCKSIZE/GC_SIZEVALUES[i] ];
    for (ii = 0; ii < GC_BLOCKSIZE/GC_SIZEVALUES[i]; ii++) {
      init_blocks[i].mark[ii]  = 0;
    }
    init_blocks[i].nextblock= OUT_OF_BLOCKS;
    init_blocks[i].slotsize = GC_SIZEVALUES[i];
  }

  // set up GC_INDEX_ARRAY for this Processor
  st.GC_INDEX_ARRAY = new VM_SizeControl[GC_MAX_SMALL_SIZE + 1];
  st.GC_INDEX_ARRAY[0] = st.sizes[0];  // for size = 0
  int j = 1;
  for (i = 0; i < GC_SIZES; i++) 
      for (; j <= GC_SIZEVALUES[i]; j++) st.GC_INDEX_ARRAY[j] = st.sizes[i];

  if (GC_STATISTICS) countLive  = new int[GC_SIZES];

  countSmallFree = new int[GC_SIZES];
  countSmallBlocksAlloc = new int[GC_SIZES];

  if (GC_STATISTICS) 
      for (i = 0; i < GC_SIZES; i++) 
	  countLive[i]  = 0;

  }      // init(): all this done in bootimagebuilder context


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
  blocks = (VM_BlockControl []) init_blocks;

  // Now set the beginning address of each block into each VM_BlockControl
  // Note that init_blocks is in the boot image, but heap pages are controlled by it

  for (int i=0; i < GC_SIZES; i++)  {
    init_blocks[i].baseAddr = smallHeap.start.add(i * GC_BLOCKSIZE);
    build_list_for_new_block(init_blocks[i], st.sizes[i]);
  }


  VM_BlockControl.boot();

  // Now allocate the blocks array - which will be used to allocate blocks to sizes


  // GET STORAGE FOR BLOCKS ARRAY 
  //    storage for entries in blocks array: 4 bytes/ ref
  num_blocks = smallHeap.size / GC_BLOCKSIZE;
  int blocks_array_size = BCArrayType.getInstanceSize(num_blocks);
  VM_Address blocks_array_storage = immortalHeap.allocateRawMemory(blocks_array_size);
  VM_Address blocks_storage = immortalHeap.allocateRawMemory((num_blocks-GC_SIZES) * VM_BlockControl.getInstanceSize());

  // available from before
  Object[] BCArrayTIB = BCArrayType.getTypeInformationBlock();
  blocks = (VM_BlockControl []) (VM_ObjectModel.initializeArray(blocks_array_storage, BCArrayTIB, num_blocks, blocks_array_size));

  // index for highest page in heap
  highest_block = num_blocks -1;
  blocks_available = highest_block - GC_SIZES;   // available to allocate
  
  // Now fill in blocks with values from blocks_init
  for (int i = 0; i < GC_SIZES; i++) {
    // NOTE: if blocks are identified by index, st.sizes[] need not be changed; if
    // blocks are identified by address, then updates st.sizes[0-GC_SIZES] here
    blocks[i] = init_blocks[i];
  }

  // At this point we have assigned the first GC_SIZES blocks, 
  // 1 per, to each GC_SIZES bin
  // and are prepared to allocate from such, or from large object space:
  // large objects are allocated from the top of the heap downward; 
  // small object blocks are allocated from the bottom of the heap upward.  
  // VM_BlockControl blocks are not used to manage large objects - 
  // they are unavailable by special logic for allocation of small objs
  //
  first_freeblock = GC_SIZES;    // next to be allocated
  init_blocks    = null;        // these are currently live through blocks

  // Now allocate the rest of the VM_BlockControls
  int bcSize = VM_BlockControl.getInstanceSize();
  Object[] bcTIB = VM_BlockControl.getTIB();
  for (int i = GC_SIZES; i < num_blocks; i++) {
      VM_Address bcAddress = blocks_storage.add((i - GC_SIZES) * bcSize);
      VM_BlockControl bc = (VM_BlockControl) VM_ObjectModel.initializeScalar(bcAddress, bcTIB, bcSize);
      blocks[i] = bc;
      bc.baseAddr = smallHeap.start.add(i * GC_BLOCKSIZE); 
      bc.nextblock = (i == num_blocks - 1) ? OUT_OF_BLOCKS : i + 1;
      // set alloc pointer = 0 here
      bc.mark = null;
  }
  
  // create synchronization objects
  sysLockSmall = new VM_ProcessorLock();
  sysLockFree   = new VM_ProcessorLock();

  VM_GCUtil.boot();

  // create the finalizer object 
  VM_Finalizer.setup();

  // statistics arrays for blocks usage
  if (REPORT_BLOCKS) {
      total = new int[GC_SIZES];
      accum = new int[GC_SIZES];
  }

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
   * Allocate a "scalar" (non-array) Java object.  Ideally, both the size 
   * and the hasFinalizer parameters are compile-time constants, allowing most 
   * of the tests and code to be optimized away.  Note that the routines
   * on the hot path through this method are all inlined.
   *   @param size Size of the object in bytes, including the header
   *   @param tib Pointer to the Type Information Block for the object type
   *   @param hasFinalizer Does the object have a finalizer method?
   *   @return Initialized object reference
   */
  public static Object allocateScalar (int size, Object[] tib, boolean hasFinalizer)
      throws OutOfMemoryError
  {
      VM_Magic.pragmaInline();  // make sure this method is inlined

      // Allocate the memory
      VM_Address objaddr = allocateRawMemory(size, tib, hasFinalizer);
      // Initialize the object header
      Object ret = VM_ObjectModel.initializeScalar(objaddr, tib, size);
      // If it has a finalizer, put it on the queue
      if (hasFinalizer) 
	  VM_Finalizer.addElement(VM_Magic.objectAsAddress(ret));

      return ret;
  }


  /**
   * Allocate a chunk of memory of a given size.  Always inlined.  All allocation 
   * passes through this routine.
   *   @param size Number of bytes to allocate
   *   @param tib Pointer to the Type Information Block for the object type (debug only)
   *   @param hasFinalizer Does the object have a finalizer method? (debug only)
   *   @return Address of allocated storage
   */
  public static VM_Address allocateRawMemory (int size, Object[] tib, boolean hasFinalizer) {
      VM_Magic.pragmaInline();

      debugAlloc(size, tib, hasFinalizer); // debug: usually inlined away to nothing

      VM_Address objaddr;
      if (size <= GC_MAX_SMALL_SIZE) {
	  // Use magic to avoid spurious array bounds check on common case allocation path.
	  // NOTE: This code sequence is carefully written to generate
	  //       optimal code when inlined by the optimzing compiler.  
	  //       If you change it you must verify that the efficient 
	  //       inlined allocation sequence isn't hurt! --dave
	  VM_Address loc = VM_Magic.objectAsAddress(VM_Processor.getCurrentProcessor().GC_INDEX_ARRAY).add(size << 2);
  	  VM_Address rs = VM_Magic.getMemoryAddress(loc);
	  VM_SizeControl the_size = VM_Magic.addressAsSizeControl(rs);
	  if (!the_size.next_slot.isZero()) 
	      objaddr = allocateSlotFast(the_size); // inlined: get available memory
	  else
	      objaddr = allocateSlot(the_size, size); // slow path: find a new block to allocate from
      }
      else 
	  objaddr = allocateLarge(size); // slow path: allocate a large object
      
      return objaddr;
  }


  /**
   * Encapsulate debugging operations when storage is allocated.  Always inlined.
   * In production, all debug flags are false and this routine disappears.
   *   @param size Number of bytes to allocate
   *   @param tib Pointer to the Type Information Block for the object type 
   *   @param hasFinalizer Does the object have a finalizer method?
   */
  static void debugAlloc (int size, Object[] tib, boolean hasFinalizer) {
      VM_Magic.pragmaInline();

      if (Debug_torture && VM_Scheduler.allProcessorsInitialized) {
	  gcCount++;
	  if ((gcCount % 100) == 0) {
	      VM.sysWrite(" gc count no. ");
	      VM.sysWrite(gcCount);
	      VM.sysWrite("\n");
	  }
	  gc_collect_now = true;
	  VM_CollectorThread.collect(VM_CollectorThread.collect);
      }
		
      if (GC_COUNT_FAST_ALLOC) allocCount++;

      if (Debug) {		// Give it its own debug flag?
	  VM_Processor st = VM_Processor.getCurrentProcessor();
	  if (st.processorMode == VM_Processor.NATIVE) {
	      VM_Scheduler.trace("VM_Allocator:"," About to quit", st.id);
	      VM_Scheduler.dumpVirtualMachine();
	      VM_Scheduler.traceback(" traceback coming");
	      VM.sysExit(1234);
	  }
      }

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


  /**
   * Allocate a fixed-size small chunk when we know one is available.  Always inlined.
   *   @param the_size Header record for the given slot size
   *   @return Address of free, zero-filled storage
   */
  static VM_Address allocateSlotFast (VM_SizeControl the_size) {
      VM_Magic.pragmaInline();

      if (VM.VerifyAssertions) VM.assert(!the_size.next_slot.isZero());
      if (GC_COUNT_FAST_ALLOC) fastAllocCount++;

      // Get the next object from the head of the list
      VM_Address objaddr = the_size.next_slot;
      if (DebugLink) {
	  if (!smallHeap.addrInHeap(objaddr)) VM.sysFail("Bad ptr");
	  if (!isPtrInBlock(objaddr, the_size)) VM.sysFail("Pointer out of block");
      }

      // Update the next pointer
      the_size.next_slot = VM_Magic.getMemoryAddress(objaddr);
      if (DebugLink && (!the_size.next_slot.isZero())) {
	  if (!smallHeap.addrInHeap(the_size.next_slot)) VM.sysFail("Bad ptr");
	  if (!isPtrInBlock(the_size.next_slot, the_size)) VM.sysFail("Pointer out of block");
      }

      // Zero out the old next pointer [NOTE: Possible MP bug]
      VM_Magic.setMemoryWord(objaddr, 0);

      // Return zero-filled storage
      return objaddr;
  }


  /**
   * Allocate a fixed-size small chunk when we have run off the end of the 
   * current block.  This will either use a partially filled block of the 
   * given size, or a completely empty block, or it may trigger GC.
   *   @param the_size Header record for the given slot size
   *   @param size Size in bytes to allocate
   *   @return Address of free, zero-filled storage
   */
  static VM_Address allocateSlot (VM_SizeControl the_size, int size)
  {
      VM_Magic.pragmaNoInline(); // make sure this method is not inlined

      for (int control = 0; control < GC_RETRY_COUNT; control++) {

	  VM_Address objaddr = allocateSlotFromBlocks(the_size, size);
	  if (!objaddr.isZero())
	      return objaddr;

	  if (control > 0)
	      flag2nd = true;

	  gc1("Garbage collection triggered by small scalar request of ", size);

	  // reset the_size in case we are on a different processor after GC
	  the_size  = VM_Processor.getCurrentProcessor().GC_INDEX_ARRAY[size];

	  if (DEBUG_FREE && the_size.current_block != the_size.first_block) 
	      VM_Scheduler.trace(" After gc, current_block not reset ", "AS1", the_size.ndx);

	  // At this point allocation might or might not succeed, since the
	  // thread which originally requested the collection is usually not
	  // the one to run after gc is finished; therefore failing here is
	  // not a permanent failure necessarily

	  if (!the_size.next_slot.isZero())  // try fast path again
	      return allocateSlotFast(the_size);
      }

      outOfMemory(size, "Fail to allocateSlot after GC");
      return VM_Address.zero(); // never reached: outOfMemory() does not return
  }


  /**
   * Find a new block to use for the given slot size, format the 
   * free list, and allocate an object from that list.  First tries to
   * allocate from the processor-local list for the given size, then from 
   * the global list of partially filled blocks of the given size, and
   * finally tries to get an empty block and format it to the given size.
   *   @param the_size Header record for the given slot size
   *   @param size Size in bytes to allocate
   *   @return Address of free storage or 0 if none is available
   */
  static VM_Address allocateSlotFromBlocks (VM_SizeControl the_size, int size) {

      VM_BlockControl the_block = blocks[the_size.current_block];

      if (VM.VerifyAssertions) VM.assert(gcCount != 0 || the_block.nextblock == OUT_OF_BLOCKS);	// If no GC yet, no blocks after current

      // First, look for a slot in the blocks on the existing list
      while (the_block.nextblock != OUT_OF_BLOCKS) {
	  the_size.current_block = the_block.nextblock;
	  the_block = blocks[the_block.nextblock];
	  if (build_list(the_block, the_size))
	      return allocateSlotFast(the_size);
      }
    
      // Next, try to get a partially filled block of the given size from the global pool
      while (getPartialBlock(the_size.ndx) == 0) {
	  if (GSC_TRACE) {
	      VM_Processor.getCurrentProcessor().disableThreadSwitching();
	      VM.sysWrite("allocatex: adding partial block: ndx "); VM.sysWrite(the_size.ndx,false);
	      VM.sysWrite(" current was "); VM.sysWrite(the_size.current_block,false);
	      VM.sysWrite(" new current is "); VM.sysWrite(the_block.nextblock,false);
	      VM.sysWrite("\n");
	      VM_Processor.getCurrentProcessor().enableThreadSwitching();
	  }

	  the_size.current_block = the_block.nextblock;
	  the_block = blocks[the_block.nextblock];

	  if (build_list(the_block, the_size))
	      return allocateSlotFast(the_size);

	  if (GSC_TRACE) {
	      VM_Processor.getCurrentProcessor().disableThreadSwitching();
	      VM.sysWrite("allocatey: partial block was full\n");
	      VM_Processor.getCurrentProcessor().enableThreadSwitching();
	  }
      }

      // Finally, try to allocate a free block and format it to the given size
      if (getnewblock(the_size.ndx) == 0) {
	  the_size.current_block = the_block.nextblock;
	  build_list_for_new_block(blocks[the_size.current_block], the_size);

	  return allocateSlotFast(the_size);
      }
  
      // All attempts failed; time to GC
      return VM_Address.zero();
  } 



  /**
   * Allocate a large object; if none available collect garbage and try again.
   *   @param size Size in bytes to allocate
   *   @return Address of zero-filled free storage
   */
  static VM_Address allocateLarge (int size) 
  {
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

  // build, in the block, the list of free slot pointers, and update the
  // associated VM_SizeControl.next_slot; return true if a free slot was found,
  // or false if not
  //
  static boolean build_list (VM_BlockControl the_block, VM_SizeControl the_size) {

  byte[] the_mark = the_block.mark;
  int first_free = 0, i = 0, j;
  VM_Address current, next;
    
  if (VM.VerifyAssertions && the_mark == null) {
      VM.sysWriteln("mark = ", VM_Magic.objectAsAddress(the_mark));
      VM.sysWriteln("size = ", GC_SIZEVALUES[the_size.ndx]);
  }

  if (VM.VerifyAssertions) VM.assert(the_mark != null);

  for (; i < the_mark.length ; i++) 
    if (the_mark[i] == 0) break;

  if ( i == the_mark.length ) { // no free slot was found

    if (DebugLink) 
       VM_Scheduler.trace("build_list: ", "found a full block", the_block.slotsize);

    // Reset control info for this block, for next collection 
    VM_Memory.zero(VM_Magic.objectAsAddress(the_mark),
		   VM_Magic.objectAsAddress(the_mark).add(the_mark.length));
    the_block.live = false;
    return false;  // no free slots in this block
  }
  // here is the first
  else current = the_block.baseAddr.add(i * the_block.slotsize);
  VM_Memory.zero(current.add(4), current.add(the_block.slotsize));
  the_size.next_slot = current;
  if (DebugLink && (!the_size.next_slot.isZero())) {
    if (!smallHeap.addrInHeap(the_size.next_slot)) VM.sysFail("Bad ptr");
    if (!isPtrInBlock(the_size.next_slot, the_size)) VM.sysFail("Pointer out of block");
  }

  // now find next free slot
  i++;   
  for (; i < the_mark.length ; i++) 
    if (the_mark[i] == 0) break;
  if (i == the_mark.length ) {    // this block has only 1 free slot, so..
    VM_Magic.setMemoryWord(current, 0);  // null pointer to next

    if (DebugLink) 
      VM_Scheduler.trace("build_list: ", "found blk w 1 free slot", the_block.slotsize);
    if (DebugLink) do_check(the_block, the_size);

    // Reset control info for this block, for next collection 
    VM_Memory.zero(VM_Magic.objectAsAddress(the_mark),
		   VM_Magic.objectAsAddress(the_mark).add(the_mark.length));
    the_block.live = false;
    return true;
  }

  next = the_block.baseAddr.add(i * the_block.slotsize);
  VM_Magic.setMemoryAddress(current, next);
  current = next; 
  VM_Memory.zero(current.add(4), current.add(the_block.slotsize));

  // build the rest of the list; there is at least 1 more free slot
  for (i = i + 1; i < the_mark.length ; i++) {
    if (the_mark[i] == 0) {  // This slot is free
      next = the_block.baseAddr.add(i * the_block.slotsize);
      VM_Magic.setMemoryAddress(current, next);  // enter list pointer
      current = next;
      VM_Memory.zero(current.add(4), current.add(the_block.slotsize));
    }
  }

  VM_Magic.setMemoryWord(current,0);    // set the end of the list
  if (DebugLink) do_check(the_block, the_size);
  // Reset control info for this block, for next collection 
  VM_Memory.zero(VM_Magic.objectAsAddress(the_mark),
		 VM_Magic.objectAsAddress(the_mark).add(the_mark.length));
  the_block.live = false;
  return true;
  } 

    // A debugging routine: called to validate the result of build_list 
    // and build_list_for_new_block
    // 
    private static void do_check (VM_BlockControl the_block, VM_SizeControl the_size) {
	
	int count = 0;
	if (blocks[the_size.current_block] != the_block) {
	    VM_Scheduler.trace("do_check", "BlockControls don't match");
	    VM.sysFail("BlockControl Inconsistency");
	}
	if (the_size.next_slot.isZero()) VM_Scheduler.trace("do_check", "no free slots in block");
	VM_Address temp = the_size.next_slot;
	while (!temp.isZero()) {
	    if ((temp.LT(the_block.baseAddr)) || (temp.GT(the_block.baseAddr.add(GC_BLOCKSIZE))))  {
		VM_Scheduler.trace("do_check: TILT:", "invalid slot ptr", temp);
		VM.sysFail("Bad freelist");
	    }
	    count++;
	    temp = VM_Magic.getMemoryAddress(temp);
	}
	
	if (count > the_block.mark.length)  {
	    VM_Scheduler.trace("do_check: TILT:", "too many slots in block");
	    VM.sysFail("too many slots");
	}
    }
      
    // Input: a VM_BlockControl that was just assigned to a size; the VM_SizeControl
    // associated with the block
    // return: the address of the first slot in the block
    //
    static void build_list_for_new_block (VM_BlockControl the_block, VM_SizeControl the_size) {
	  
	byte[] the_mark = the_block.mark;
	int i, delta;
	VM_Address current = the_block.baseAddr;
	VM_Memory.zero(current, current.add(GC_BLOCKSIZE));
	delta  = the_block.slotsize;
	the_size.next_slot = current ;  // next one to allocate
	if (DebugLink && (!the_size.next_slot.isZero())) {
	    if (!smallHeap.addrInHeap(the_size.next_slot)) VM.sysFail("Bad ptr");
	    if (!isPtrInBlock(the_size.next_slot, the_size)) VM.sysFail("Pointer out of block");
	}
	for (i = 0; i < the_mark.length -1; i++) {
	    VM_Magic.setMemoryAddress(current, current.add(delta));
	    current = current.add(delta);
	}
	// last slot does not point forward - already zeroed
	//  
	if (DebugLink) do_check(the_block, the_size);
	// Reset control info for this block, for next collection 
	VM_Memory.zero(VM_Magic.objectAsAddress(the_mark),
		       VM_Magic.objectAsAddress(the_mark).add(the_mark.length));
	the_block.live = false;
	return ;
    }
  
    
  /**
   * Allocate an object of the given size and the clone the data from
   * the given source object into the newly allocated object.
   *   @param size Size of the object in bytes, including the header
   *   @param tib Pointer to the Type Information Block for the object type
   *   @param cloneSrc Object to clone into the newly allocated object
   *   @return Initialized, cloned object 
   */
  public static Object cloneScalar (int size, Object[] tib, Object cloneSrc)
    throws OutOfMemoryError {
    if (DebugLink) VM_Scheduler.trace("cloneScalar", "called");

    VM_Type type = VM_Magic.addressAsType(VM_Magic.getMemoryAddress(VM_Magic.objectAsAddress(tib)));
    boolean hasFinalizer = type.hasFinalizer();
    Object objref = allocateScalar(size, tib, hasFinalizer);
    VM_ObjectModel.initializeScalarClone(objref, cloneSrc, size);

    return objref;
  }


  /**
   * Allocate an array object.  Ideally, the size is a compile-time constant,
   * allowing most of the tests and code to be optimized away.  Note that 
   * the routines on the hot path through this method are all inlined.
   *   @param numElements Number of elements in the array
   *   @param size Size of the object in bytes, including the header
   *   @param tib Pointer to the Type Information Block for the object type
   *   @return Initialized array reference
   */
  public static Object allocateArray (int numElements, int size, Object[] tib)
      throws OutOfMemoryError
  {
      VM_Magic.pragmaInline();  // make sure this method is inlined

      VM_Address objaddr = allocateRawMemory(size, tib, false);
      Object objptr = VM_ObjectModel.initializeArray(objaddr, tib, numElements, size);

      if (DebugInterest && VM_Magic.objectAsAddress(objptr).EQ(the_array) )
          VM.sysWrite (" Allocating the_array in new page \n");

      return objptr;
  }


  /*
   * Allocate an array of the given size and the clone the data from
   * the given source array into the newly allocated array.
   *   @param numElements Number of array elements
   *   @param size Size of the object in bytes, including the header
   *   @param tib Pointer to the Type Information Block for the object type
   *   @param cloneSrc Array to clone into the newly allocated object
   *   @return Initialized, cloned array
   */
  public static Object cloneArray (int numElements, int size, Object[] tib, Object cloneSrc)
      throws OutOfMemoryError {

      if (DebugLink) VM_Scheduler.trace("cloneArray", "called");

      Object objref = allocateArray(numElements, size, tib);
      VM_ObjectModel.initializeArrayClone(objref, cloneSrc, size);
      return objref;
  }

  
  // A routine to obtain a free VM_BlockControl and return it
  // to the caller.  First use is for the VM_Processor constructor: 
  static int getnewblockx (int ndx) {

  sysLockBlock.lock();
  if (first_freeblock == OUT_OF_BLOCKS) {
  gc1("GC collection triggered by getnewblockx call ", 0);
  }  
  
  VM_BlockControl alloc_block = blocks[first_freeblock];
  int theblock = first_freeblock;
  first_freeblock = alloc_block.nextblock;
  sysLockBlock.unlock();
  alloc_block.nextblock = OUT_OF_BLOCKS;  // this is last block in list for thissize
  alloc_block.slotsize  = GC_SIZEVALUES[ndx];
  int size = GC_BLOCKSIZE/GC_SIZEVALUES[ndx] ;    

  if (alloc_block.mark != null)  {
    if (size <= alloc_block.alloc_size) {
      VM_ObjectModel.setArrayLength(alloc_block.mark, size);
      return theblock;
    }
    else {    // free the existing array space
	mallocHeap.free(VM_Magic.objectAsAddress(alloc_block.mark).sub(byteArrayHeaderSize));
    }
  }

  // get space for alloc arrays 
  int mark_array_size = getByteArrayInstanceSize(size);
  byteArrayTIB = byteArrayType.getTypeInformationBlock();
  VM_Address region = mallocHeap.allocate(mark_array_size);
  alloc_block.mark = VM_Magic.objectAsByteArray(VM_ObjectModel.initializeArray(region, byteArrayTIB, size, mark_array_size));

  return theblock;
  }


  private static int getPartialBlock (int ndx) {

    VM_Processor st = VM_Processor.getCurrentProcessor();
    VM_SizeControl this_size = st.sizes[ndx];
    VM_BlockControl currentBlock = blocks[this_size.current_block];

    sysLockBlock.lock();

    if (partialBlockList[ndx] == OUT_OF_BLOCKS) {
      //      if (GSC_TRACE) {
      //  VM_Processor.getCurrentProcessor().disableThreadSwitching();
      //  VM.sysWrite("getPartialBlock: ndx = "); VM.sysWrite(ndx,false);
      //  VM.sysWrite(" returning -1\n");
      //  VM_Processor.getCurrentProcessor().enableThreadSwitching();
      //      }
      sysLockBlock.release();
      return -1;
    }

    // get first partial block of same slot size
    //
    currentBlock.nextblock = partialBlockList[ndx];
    VM_BlockControl allocBlock = blocks[partialBlockList[ndx]];

    partialBlockList[ndx] = allocBlock.nextblock;
    allocBlock.nextblock = OUT_OF_BLOCKS;

    if (GSC_TRACE) {
      VM_Processor.getCurrentProcessor().disableThreadSwitching();
      VM.sysWrite("getPartialBlock: ndx = "); VM.sysWrite(ndx,false);
      VM.sysWrite(" allocating "); VM.sysWrite(currentBlock.nextblock);
      VM.sysWrite(" baseAddr "); VM.sysWrite(allocBlock.baseAddr);
      VM.sysWrite("\n");
      if (VM.VerifyAssertions) VM.assert(allocBlock.slotsize==GC_SIZEVALUES[ndx]);
      VM_Processor.getCurrentProcessor().enableThreadSwitching();
    }

    sysLockBlock.unlock();
    return 0;
  }  // getPartialBlock

 
  private static int
  getnewblock (int ndx) 
  {
  int i, save, size;
  VM_Processor st = VM_Processor.getCurrentProcessor();

  VM_SizeControl this_size = st.sizes[ndx];
  VM_BlockControl alloc_block = blocks[this_size.current_block];

  // some debugging code in generational collector available if needed 
   
  // return -1 to indicate small object triggered gc.
  sysLockBlock.lock();
  if (first_freeblock == OUT_OF_BLOCKS) {
  if (verbose >= 1)
    VM_Scheduler.trace(" gc collection triggered by getnewblock call", "");
    sysLockBlock.release();
    return -1;
  }

  alloc_block.nextblock = first_freeblock;
  alloc_block = blocks[first_freeblock];
  first_freeblock = alloc_block.nextblock;  // new first_freeblock
  sysLockBlock.unlock();

  alloc_block.nextblock = OUT_OF_BLOCKS;  // this is last block in list for thissize
  alloc_block.slotsize  = GC_SIZEVALUES[ndx];
  // size = GC_BLOCKSIZE/GC_SIZEVALUES[ndx];  // No. of entries in each array
  // No. of entries in each array 
  size = GC_BLOCKSIZE/GC_SIZEVALUES[ndx] ;  

  // on first assignment of this block, get space from AIX
  // for alloc array, for the size requested.  
  // If not first assignment, if the existing array is large enough for 
  // the new size, use them; else free the existing one, and get space 
  // for new one.  Keep the size for the currently allocated array in
  // alloc_block.alloc_size.  This value only goes up during the running
  // of the VM.

  if (alloc_block.mark != null) {
    if (size <= alloc_block.alloc_size) {
      VM_ObjectModel.setArrayLength(alloc_block.mark, size);
      return 0;
    }
    else {    // free the existing array space
	mallocHeap.free(VM_Magic.objectAsAddress(alloc_block.mark).sub(byteArrayHeaderSize));
    }
  }

  int mark_array_size = getByteArrayInstanceSize(size);
  VM_Address location = mallocHeap.allocate(mark_array_size);
  alloc_block.alloc_size = size;  // remember allocated size
  alloc_block.mark = VM_Magic.objectAsByteArray(VM_ObjectModel.initializeArray(location, byteArrayTIB, size, mark_array_size));

  return 0;
  }


  private static int getndx(int size) {
  if (size <= GC_SIZEVALUES[0]) return 0;  // special case most common
  if (size <= GC_SIZEVALUES[1]) return 1;  // special case most common
  if (size <= GC_SIZEVALUES[2]) return 2;  // special case most common
  if (size <= GC_SIZEVALUES[3]) return 3;  // special case most common
  if (size <= GC_SIZEVALUES[4]) return 4;  // special case most common
  if (size <= GC_SIZEVALUES[5]) return 5;  // special case most common
  if (size <= GC_SIZEVALUES[6]) return 6;  // special case most common
  if (size <= GC_SIZEVALUES[7]) return 7;  // special case most common
  for (int i =8; i < GC_SIZES; i++) 
    if (size <= GC_SIZEVALUES[i]) return i;
  return -1;
  }

/**
 Garbage Collection routines begin here.
   scan jtoc for pointers to objects.
   then scan stack for pointers to objects
   processPtrValue() is the routine that processes a pointer, marks 
   the object as live, then scans the object pointed to for pointer,
   and calls processPtrValue()
*/

  // following is called from VM_CollectorThread.boot() - to set the number
  // of system threads into the synchronization object; this number
  // is not yet available at Allocator.boot() time
  static void
  gcSetup (int numSysThreads ) {
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
	  //        thr.contextRegisters.gprs[FRAME_POINTER] = thr.jniEnv.JNITopJavaFP;
	  thr.contextRegisters.setInnermost( VM_Address.zero(), thr.jniEnv.JNITopJavaFP );
	}

	zeromarks(vp);		// reset mark bits for nonparticipating vps

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

			if (vp == null) continue;	// allow for BuildForSingleVirtualProcessor

      int vpStatus = VM_Processor.vpStatus[vp.vpStatusIndex];
      if ((vpStatus == VM_Processor.BLOCKED_IN_NATIVE) || 
				(vpStatus == VM_Processor.BLOCKED_IN_SIGWAIT))
      {
       vp.modifiedOldObjectsTop = VM_Magic.objectAsAddress(vp.modifiedOldObjects).sub(4);
		   // at this point we do _not_ attempt to reclaim free blocks - an
       // expensive operation - for non participating vps, since this would
       // not be done in parallel; we assume that, in general, in the next
       // collection non-participating vps do participate
       setupallocation(vp);
     }
    }
    //-#endif
  }

  private static void  setupallocation(VM_Processor st) {
      for (int i = 0; i < GC_SIZES; i++) {
	  VM_BlockControl this_block = blocks[st.sizes[i].first_block];
	  VM_SizeControl this_size  = st.sizes[i];
	  // begin scan in 1st block again
	  this_size.current_block = this_size.first_block;
	  if (!build_list(this_block, this_size)) this_size.next_slot = VM_Address.zero();
      }
  }


  private static void
  zeromarks(VM_Processor st) 
  {
    int block_count = 0;
    for (int i = 0; i < GC_SIZES; i++) {

      //  NEED TO INITIALIZE THE BLOCK AFTER CURRENT_BLOCK, FOR
      //  EACH SIZE, SINCE THIS WAS NOT DONE DURING MUTATOR EXECUTION
      VM_BlockControl this_block = blocks[st.sizes[i].current_block];

      int next = this_block.nextblock;
      if (GC_TRACEALLOCATOR) block_count++;
      while (next != OUT_OF_BLOCKS) {
        if (GC_TRACEALLOCATOR) block_count++;
        this_block = blocks[next];
        if (Debug && (this_block.mark == null))
          VM.sysWrite(" In collect, found block with no mark \n");
        VM_Memory.zero(VM_Magic.objectAsAddress(this_block.mark),
		       VM_Magic.objectAsAddress(this_block.mark).add(this_block.mark.length));
        this_block.live = false;
        next = this_block.nextblock;
      }
    }
  }


  static void collect () {

    int i;

    int start, end;
    VM_BlockControl this_block;
    VM_BlockControl next_block;
    VM_SizeControl this_size;
    if (!gc_collect_now) {
	VM_Scheduler.trace(" gc entered with collect_now off", "");
	return;  // to avoid cascading gc
    }

    VM_CollectorThread mylocal = 
      VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());
    int mypid = VM_Processor.getCurrentProcessorId();  // id of processor running on


    // set pointer in processor block to first word in writebuffer - 4
    // since store with update will increment first
    // then store
    VM_Processor st = VM_Processor.getCurrentProcessor();
    st.modifiedOldObjectsTop = VM_Magic.objectAsAddress(st.modifiedOldObjects).sub(4);

    //   SYNCHRONIZATION CODE for parallel gc
    if (VM_GCLocks.testAndSetInitLock()) {

    if (flag2nd) {
			VM_Scheduler.trace(" collectstart:", "flag2nd on");
			freeSmallSpaceDetails(true);
		}

    gcStartTime = VM_Time.now();         // start time for GC
    totalStartTime += gcStartTime - VM_CollectorThread.gcBarrier.rendezvousStartTime; //time since GC requested

    gcCount++;

    if (GC_TRACEALLOCATOR) VM_Scheduler.trace(" Inside Mutex1", "");

    // setup common workqueue for num VPs participating, used to be called once.
    // now count varies for each GC, so call for each GC   SES 050201
    //
    VM_GCWorkQueue.workQueue.initialSetup(VM_CollectorThread.numCollectors());

    blocks_available = 0;    // not decremented during allocation

    if ( gcInProgress ) {
      VM.sysWrite("VM_Allocator: Garbage Collection entered recursively \n");
      VM.sysExit(1000);
    }
    else gcInProgress = true;

    if (GC_TRACEALLOCATOR) 
      VM.sysWrite("VM_Allocator: Garbage Collection Beginning \n");

    // invert the mark_flag value, used for marking BootImage objects
    if ( OBJECT_GC_MARK_VALUE == 0 )
      OBJECT_GC_MARK_VALUE = VM_CommonAllocatorHeader.GC_MARK_BIT_MASK;
    else
      OBJECT_GC_MARK_VALUE = 0;

    // Now initialize the large object space mark array
    largeHeap.startCollect();

    // zero mark arrays in global partial blocks list
    //
    if (GSC_TRACE) VM.sysWrite("\nZeroing partial block mark arrays\n");

    for (i = 0; i < GC_SIZES; i++) {
      int counter = 0;
      int index = partialBlockList[i];
      while ( index != OUT_OF_BLOCKS ) {
    if (GSC_TRACE) counter++;
	  this_block = blocks[index];
        VM_Memory.zero(VM_Magic.objectAsAddress(this_block.mark),
		       VM_Magic.objectAsAddress(this_block.mark).add(this_block.mark.length));
        this_block.live = false;
        index = this_block.nextblock;
      }
      if (GSC_TRACE) {
	VM.sysWrite(" size = "); VM.sysWrite(i,false);
	VM.sysWrite(" first = "); VM.sysWrite(partialBlockList[i],false);
	VM.sysWrite(" count = "); VM.sysWrite(counter,false); VM.sysWrite("\n");
      }
    }

			// perform per vp processing for non-participating vps
      prepareNonParticipatingVPsForGC();

    }
    //   END OF SYNCHRONIZED INITIALIZATION BLOCK


    VM_CollectorThread.gcBarrier.rendezvous(RENDEZVOUS_TIMES || RENDEZVOUS_WAIT_TIME);

    // reset collector thread local work queue buffers
    VM_GCWorkQueue.resetWorkQBuffers();

    // reset live bits in all blocks ....is this what following does??
    int block_count = 0;

    for (i = 0; i < GC_SIZES; i++) {

      //  NEED TO INITIALIZE THE BLOCK AFTER CURRENT_BLOCK, FOR 
      //  EACH SIZE, SINCE THIS WAS NOT DONE DURING MUTATOR EXECUTION
      this_block = blocks[st.sizes[i].current_block];

      int next = this_block.nextblock;
      if (GC_TRACEALLOCATOR) block_count++;
      while (next != OUT_OF_BLOCKS) {
        if (GC_TRACEALLOCATOR) block_count++;
        this_block = blocks[next];
        if (Debug && (this_block.mark == null)) 
          VM.sysWrite(" In collect, found block with no mark \n");
        VM_Memory.zero(VM_Magic.objectAsAddress(this_block.mark),
		       VM_Magic.objectAsAddress(this_block.mark).add(this_block.mark.length));
        this_block.live = false;
        next = this_block.nextblock;
      }
    }

    if (GC_TRACEALLOCATOR) VM_Scheduler.trace(" Blocks zeroed", " = ", block_count);

    // ALL GC THREADS IN PARALLEL


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
	
      }	// if foundFinalizableObject
      else {
      }
    }	// if existObjectsWithFinalizers
      
    if (TIME_GC_PHASES && (mylocal.gcOrdinal==1))  gcFinalizeDoneTime = VM_Time.now();

    // done
    if (GC_CLOBBERFREE) clobberfree();

    // Following local variables for free_block logic
    int local_first_free_ndx = OUT_OF_BLOCKS; 
    int local_blocks_available = 0; 
    int temp;
    VM_BlockControl local_first_free_block = null;



    for (i = 0; i < GC_SIZES; i++) {
	this_block = blocks[st.sizes[i].first_block];
	this_size  = st.sizes[i];
	// begin scan in 1st block again
	this_size.current_block = this_size.first_block; 
	if (!build_list(this_block, this_size)) this_size.next_slot = VM_Address.zero();
	int next = this_block.nextblock;
	
	this_size.lastBlockToKeep = -1;      // GSC
	int blockCounter = 0;               // GSC
	
	int counter = 0;
	while (next != OUT_OF_BLOCKS) {
	    /*DEBUG CODE - catch loop of blocks */
	    if (Debug) if (counter++ == 500000) {
		dumpblocks();  // dump size controls and blocks
		VM.sysExit(2000);
	    }
	    
	    next_block = blocks[next];
	    if (!next_block.live) {
		if (local_first_free_block == null)
		    local_first_free_block  = next_block;
		//  In this stanza, we make the next's next the next of this_block, and put
		//  original next on the freelist
		this_block.nextblock = next_block.nextblock;  // out of live list
		next_block.nextblock  = local_first_free_ndx;
		local_first_free_ndx  = next;
		local_blocks_available++;
	    }
	    else  {  // found that next block is live
		if (++blockCounter == numBlocksToKeep)            // GSC
				//TESTING:
		    //    			this_size.lastBlockToKeep = next;             // GSC
		    this_size.lastBlockToKeep = this_block.baseAddr.diff(smallHeap.start) / GC_BLOCKSIZE;
		
		this_block = next_block;
	    }
	    next  = this_block.nextblock; 
	}
	// this_block -> last block in list, with next==0. remember its
	// index for possible moving of partial blocks to global lists below
	//
	this_size.last_allocated = this_block.baseAddr.diff(smallHeap.start) / GC_BLOCKSIZE;
	
    }

    if (DEBUG_FREEBLOCKS) 
	VM_Scheduler.trace(" Found assigned", " freeblocks", local_blocks_available);

    // Now scan through all blocks on the partial blocks list
    // putting empty ones onto the local list of free blocks
    //
    for (i = mylocal.gcOrdinal - 1; i < GC_SIZES; 
	 i+= VM_CollectorThread.numCollectors()) {
	if (GCDEBUG_PARTIAL) {
	    VM_Scheduler.trace("Allocator: doing partials"," index", i);
	    VM_Scheduler.trace("   value in ", "list = ", partialBlockList[i]);
    	}
	if (partialBlockList[i] == OUT_OF_BLOCKS) continue;
	this_block = blocks[partialBlockList[i]];
	temp = this_block.nextblock;
	while (!this_block.live) {
	    local_blocks_available++;
	    if (GCDEBUG_PARTIAL) VM_Scheduler.trace(" Found an empty block at",
						    " head of partial list", partialBlockList[i]);
	    if (local_first_free_block == null) {
		if (VM.VerifyAssertions) VM.assert(local_first_free_ndx == OUT_OF_BLOCKS);
		local_first_free_block = this_block;
	    }
	    temp = this_block.nextblock;
	    this_block.nextblock = local_first_free_ndx;
	    local_first_free_ndx = (this_block.baseAddr.diff(smallHeap.start))/GC_BLOCKSIZE;
	    partialBlockList[i] = temp;
	    if (temp == OUT_OF_BLOCKS) break;
	    this_block = blocks[temp];
	}
	
	if (temp == OUT_OF_BLOCKS) continue;
	int next = this_block.nextblock;
	while (next != OUT_OF_BLOCKS) {
	    next_block = blocks[next];
	    if (!next_block.live) {
		if (GCDEBUG_PARTIAL) VM_Scheduler.trace(" Found an empty block ",
							" in partial list", next);
		// In this stanza, we make the next's next the next of this_block, and put
		// original next on the freelist
		if (local_first_free_block == null) {
		    if (VM.VerifyAssertions) VM.assert(local_first_free_ndx == OUT_OF_BLOCKS);
		    local_first_free_block = next_block;
		}
		this_block.nextblock = next_block.nextblock; // out of live list
		
		next_block.nextblock = local_first_free_ndx;
		local_first_free_ndx = next;
		local_blocks_available++;
		
	    }
	    else this_block = next_block;  // live block done
	    next = this_block.nextblock;
	}
    }
    if (DEBUG_FREEBLOCKS) 
	VM_Scheduler.trace(" Found partial ", " freeblocks", local_blocks_available);
    
    // Rendezvous here because below and above, partialBlocklist can be modified
    VM_CollectorThread.gcBarrier.rendezvous(RENDEZVOUS_TIMES || RENDEZVOUS_WAIT_TIME);

    sysLockFree.lock();   // serialize access to global block data

    // Sweep large space
    if (mylocal.gcOrdinal == 1) {
	if (verbose >= 1) VM.sysWrite("Sweeping large space");
	largeHeap.endCollect();
    }

    // If this processor found empty blocks, add them to global free list
    //
    if (local_first_free_block != null) {
	if (DEBUG_FREEBLOCKS) if (local_first_free_ndx == OUT_OF_BLOCKS) 
	    VM_Scheduler.trace(" LFFB not NULL", "LFFI = out_of_Blocks");
	local_first_free_block.nextblock = first_freeblock;
	first_freeblock = local_first_free_ndx;
	blocks_available += local_blocks_available;
    }
    
    // Add excess partially full blocks (maybe full ???) blocks
    // of each size to the global list for that size
    //
    if (GSC_TRACE) VM.sysWrite("\nAdding to global partial block lists\n");
    
    for (i = 0; i < GC_SIZES; i++) {
	
	this_size = st.sizes[i];
	if (this_size.lastBlockToKeep != -1 ) {
	    VM_BlockControl lastToKeep = blocks[this_size.lastBlockToKeep];
	    int firstToGiveUp = lastToKeep.nextblock;
	    if (firstToGiveUp != OUT_OF_BLOCKS) {
		blocks[ this_size.last_allocated].nextblock =
		    partialBlockList[i];
		partialBlockList[i] = firstToGiveUp;
		lastToKeep.nextblock = OUT_OF_BLOCKS;
	    }
	}
	if (GSC_TRACE) {
	    VM.sysWrite(" size = "); VM.sysWrite(i,false);
	    VM.sysWrite(" new first = "); VM.sysWrite(partialBlockList[i],false);
	    VM.sysWrite("\n");
	}
    }
    
    sysLockFree.unlock();  // release lock on global block data


    // I ADDED THIS 03/18 to prevent mypid==1 from proceeding before all others
    // have completed the above, especially if mypid=1 did NOT free any blocks
    // SES 03/18
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

    if (GC_COUNT_BYTE_SIZES) {
      for (i = 0; i <= 2048; i++) {
         if (countByteSizes[i] != 0) {
          VM.sysWriteln(i, "       ", countByteSizes[i]);
          countByteSizes[i]  = 0;
        }
      }
    }

    if (GC_COUNT_LIVE_OBJECTS) {
      int  small = 0;
      for (i = 1; i < VM_Scheduler.numProcessors + 1; i++) {
        small  += VM_Scheduler.processors[i].small_live; 
        VM_Scheduler.processors[i].small_live  = 0; 
      }
      VM_Scheduler.trace("AFTER  GC", "small live =", small);
    }
    
    //TODO: complete the support for these data
    if (GC_STATISTICS) {
    //  report  high-water marks, then reset hints to the freeblock scanner 
      for (i = 0; i < GC_SIZES; i++) 
        VM.sysWriteln(countLive[i], "  objects alive in pool \n");

      VM.sysWriteln("VM_Allocator:  markbootcount      = ", markboot_count); 
      VM.sysWriteln("VM_Allocator:  total_markbootcount  = ", total_markboot_count );
      //  report current value of available blocks
      VM.sysWriteln(blocks_available, "  blocks available after this collection");
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
      VM.sysWrite(highest_block, "  is the highest_block \n");
      VM.sysWrite(blocks_available, "  blocks are available \n");
    }    
    if (GC_COUNT_FAST_ALLOC) {
      VM.sysWrite(allocCount, "  total allocations \n");
      VM.sysWrite(fastAllocCount, "  total fastAllocations \n");
    }

    if (verbose >= 1 && GC_COUNT_BY_TYPES) printCountsByType();

    if (REPORT_BLOCKS) reportBlocks();
	  if (DEBUG_FREEBLOCKS || flag2nd) {
			if (flag2nd) VM_Scheduler.trace(" End of gc:", "flag2nd on");
			else VM_Scheduler.trace(" End of gc:", ".. debugging..");
			freeSmallSpaceDetails(true);
			flag2nd = false;
			VM_Scheduler.trace(" End of gc:", "blocks_available = ", blocks_available);
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
	VM_Address fp;
	VM_Thread t  = VM_Scheduler.threads[i];
      //  exclude GCThreads and "Empty" threads
      if ( (t != null) && !t.isGCThread ) 
			{
      // get control of this thread
      //  SYNCHRONIZING STATEMENT
      if (VM_GCLocks.testAndSetThreadLock(i)) 
      {
	  // with use of generic scanStack in VM_ScanStack (08/01/01 SES)
	  // all "running" threads BLOCKED_IN_SIGWAIT set their contextRegisters
	  // ip & fp so that scanning their stacks will start at the "top" java frame.
	  // for threads blocked in native, this was done earlier, when blocking the
	  // native processors (VM_CollectorThread I think)
	  //
	  // So...none of the following getting of a proper fp should be needed
	  //
    //-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS
    // alternate implementation of jni

          // find if thread is in native vproc: either it's
          // the NativeIdleThread in sysWait (if in yield, 
          // no special processing needed) or it's a mutator
          // thread running C-code; in both cases, need to
          // do the scan from the last Java frame to stacktop
          //
          if ((t.isNativeIdleThread &&
           ((VM_NativeIdleThread)t).inSysWait)  ||
           ((t.nativeAffinity  != null) && 
            (t.nativeAffinity.activeThread == t)))
            fp  = t.jniEnv.JNITopJavaFP;

          else fp = t.contextRegisters.getInnermostFramePointer();
		//-#else
		// default implementaton of jni

	  fp = t.contextRegisters.getInnermostFramePointer();
		//-#endif
  
          VM_ScanStack.scanStack( t, VM_Address.zero(), false /*relocate_code*/ );
        }
        else continue;  // some other gc thread has seized this thread
      }  // not a null pointer, not a gc thread
    }    // scan all threads in the system
  }      // scanStacks()


  //  a debugging routine: to make sure a pointer is into the give block
  private  static boolean isPtrInBlock (VM_Address ptr, VM_SizeControl the_size) {

    VM_BlockControl  the_block = blocks[the_size.current_block]; 
    VM_Address base = the_block.baseAddr;
    int  offset = ptr.diff(base);
    VM_Address  endofslot = ptr.add(the_block.slotsize);
    if (offset%the_block.slotsize != 0) VM.sysFail("Ptr not to beginning of slot");
    VM_Address  bound = base.add(GC_BLOCKSIZE);
    return  ptr.GE(base) && endofslot.LE(bound);
  }


  static void gc_scanObjectOrArray (VM_Address objRef ) {

    //  First process the header
    //  The header has one pointer in it - 
    //  namely the pointer to the TIB (type info block).
    //

    // following call not needed for noncopying: all tibs found through JTOC
    //  processPtrValue(getTib(objRef))

    VM_Type type  = VM_Magic.getObjectType(VM_Magic.addressAsObject(objRef));

    if ( type.isClassType() ) { 
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
    }
    else  if ( type.isArrayType() ) {
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
    }
    else  {
      VM.sysWrite("VM_Allocator.gc_scanObjectOrArray: type not Array or Class");
      VM.sysExit(1000);
    }
  }  //  gc_scanObjectOrArray

    
  /**  given an address in the small objec heap (as an int), 
  *  set the corresponding mark byte on
  */
  static  boolean  gc_setMarkSmall (VM_Address tref) {

    boolean  result; 
    int  blkndx, slotno, size, ij;
    blkndx  = (tref.diff(smallHeap.start)) >> LOG_GC_BLOCKSIZE ;
    VM_BlockControl  this_block = blocks[blkndx];
    int  offset   = tref.diff(this_block.baseAddr); 
    int  slotndx  = offset/this_block.slotsize;
    result  = (this_block.mark[slotndx] != 0);

    if (result) return true;    // avoid synchronization
    
    else if (!GC_USE_LARX_STCX) this_block.mark[slotndx] = 1;
      else {
        //  Use larx/stcx logic
        byte  tbyte;
        int  temp, temp1;
        do  {
	  // get word with proper byte from map
          temp1 = VM_Magic.prepare(this_block.mark, ((slotndx>>2)<<2));
          if (this_block.mark[slotndx] != 0) return true;
          tbyte  = (byte)( 1);         // create mask bit
	  //-#if RVM_FOR_IA32    
          int index = slotndx%4; // get byte in word - little Endian
	  //-#else 
          int index = 3 - (slotndx%4); // get byte in word - big Endian
	  //-#endif
          int mask = tbyte << (index * 8); // convert to bit in word
	        temp  = temp1 | mask;        // merge into existing word
	}  while (!VM_Magic.attempt(this_block.mark, ((slotndx>>2)<<2), temp1, temp));
      }

    if (GC_STATISTICS) countLive[getndx(size)]++; // maintain count of live objects
    if (GC_COUNT_LIVE_OBJECTS) VM_Processor.getCurrentProcessor().small_live++;

    this_block.live  = true;
    return  false;
  }  //  gc_setMarkSmall



  //  process objects in the work queue buffers until no more buffers to process
  //
  static  void gc_emptyWorkQueue()  {

      VM_Address ref = VM_GCWorkQueue.getFromWorkBuffer();
      
      if (VM_GCWorkQueue.WORKQUEUE_COUNTS) {
	  VM_CollectorThread myThread = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());
	  myThread.rootWorkCount = myThread.putWorkCount;
      }
      
      while ( !ref.isZero() ) {
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


  static void dumpblocks (VM_Processor st)  {

      VM.sysWriteln("\n-- Processor ", st.id, " --");
      for (int i = 0; i < GC_SIZES; i++) {
	  VM.sysWrite(" Size ", GC_SIZEVALUES[i], "  ");
	  VM_BlockControl the_block = blocks[st.sizes[i].first_block];
	  VM.sysWrite(st.sizes[i].first_block, false);
	  while (true) {
	      VM.sysWrite("  ", the_block.nextblock);
	      if (the_block.nextblock == OUT_OF_BLOCKS) break;
	      the_block = blocks[the_block.nextblock];
	  }		
	  VM.sysWriteln();
      }
  }

  static  void dumpblocks () {

    VM_Processor  st = VM_Processor.getCurrentProcessor();
    VM.sysWrite(first_freeblock, "  is the first freeblock index \n");
    for (int i = 0; i < GC_SIZES; i++) {
	VM.sysWrite(i, "th VM_SizeControl first_block = ", st.sizes[i].first_block);
	VM.sysWrite(" current_block = ", st.sizes[i].current_block, "\n\n");
    }
    
    for (int i = 0; i < num_blocks; i++) {
	VM.sysWriteln(i, "th VM_BlockControl   ", (blocks[i].live) ? "   live  " : "not live  ");
	VM.sysWriteln("baseaddr = ", blocks[i].baseAddr);
	VM.sysWriteln("nextblock = ", blocks[i].nextblock);
    }
    
  }  //  dumpblocks


  static  void clobber (VM_Address addr, int length) {
      int value = 0xdeaddead;
      int i;
      for (i = 0; i + 3 < length; i = i+4) 
	  VM_Magic.setMemoryWord(addr.add(i), value);
  }


  static  void clobberfree () {

    VM_Processor  st = VM_Processor.getCurrentProcessor();
    for (int i = 0; i < GC_SIZES; i ++) {
      VM_BlockControl this_block = blocks[st.sizes[i].first_block];  
      byte[] this_alloc      = this_block.mark;
      for (int ii = 0; ii < this_alloc.length; ii ++) {
	  if (this_alloc[ii] == 0) clobber(this_block.baseAddr.add(ii * GC_SIZEVALUES[i]), GC_SIZEVALUES[i]);
      }
      int next = this_block.nextblock;
      while (next != OUT_OF_BLOCKS) {
        this_block = blocks[next];
        this_alloc      = this_block.mark;
        for (int ii = 0; ii < this_alloc.length; ii ++) {
          if (this_alloc[ii] == 0)
            clobber(this_block.baseAddr.add(ii * GC_SIZEVALUES[i]), GC_SIZEVALUES[i]);
        }
        next = this_block.nextblock;
      }
    }

    // Now clobber the free list
    sysLockBlock.lock();
    VM_BlockControl block;
    for (int freeIndex = first_freeblock; freeIndex != OUT_OF_BLOCKS; freeIndex = block.nextblock) {
	block = blocks[freeIndex];
	clobber(block.baseAddr, GC_BLOCKSIZE);
    }
    sysLockBlock.unlock();
  }



  public  static long totalMemory () {
    return smallHeap.size + largeHeap.size;
  }


  public static long freeMemory () {
    return freeBlocks() * GC_BLOCKSIZE;
  }


  static  int total_blocks_in_use; // count blocks in use during this calculation

    public  static long  freeSmallSpace (VM_Processor st) {

	int  total = 0;

	for (int i = 0; i < GC_SIZES; i++) {
	    total_blocks_in_use += blocksToCurrent(st.sizes[i]);
	    VM_BlockControl this_block  = blocks[st.sizes[i].current_block];
	    VM_Address current_pointer = st.sizes[i].next_slot;
	    total += emptyOfCurrentBlock(this_block, current_pointer); 
	    int next  = this_block.nextblock;
	    while (next != OUT_OF_BLOCKS) {
		if (GCDEBUG_FREESPACE) 
		    VM_Scheduler.trace(" In freesmallspace ", "next = ", next);
		this_block  = blocks[next];
		total_blocks_in_use++;
		
		total  += emptyof(i, this_block.mark);
		next  = this_block.nextblock;
	    }
	}
	
	return  total;
    }

  private static int emptyOfCurrentBlock(VM_BlockControl the_block, VM_Address current_pointer) {

    int sum = 0;
    while (!current_pointer.isZero()) {
      sum += the_block.slotsize;
      current_pointer = VM_Magic.getMemoryAddress(current_pointer);
    }
    return sum;
  }


  //  calculate the number of free bytes in a block of slotsize size
  private  static int
  emptyof (int size, byte[] alloc) {
  int  total = 0;
  int  i;
  for (i = 0; i < alloc.length; i++) {
    if (alloc[i] == 0) total += GC_SIZEVALUES[size];
  }
  return  total;
  }

	// Count all VM_blocks in the chain from the input to the end
	//
  private static int
  blocksInChain(VM_BlockControl the_block) {
		int next = the_block.nextblock;
		int count = 1;
		while (next != OUT_OF_BLOCKS) {
      if (GCDEBUG_FREESPACE) VM_Scheduler.trace(" In blocksinChain", "next = ", next); 
			count++; 
			the_block = blocks[next]; 
			next = the_block.nextblock;
		}
		return count;
	}

    // Count the blocks in the size_control input from first to current
    // 
    private static int blocksToCurrent (VM_SizeControl the_size) {

	int count = 1;
	if (the_size.first_block == the_size.current_block) return 1;
	VM_BlockControl the_block = blocks[the_size.first_block];	
	int next = the_block.nextblock;
	while (next != the_size.current_block) {
	    if (GCDEBUG_FREESPACE) {
		VM_Scheduler.trace(" In blocksToCurrent", "next = ", next);
		VM_Scheduler.trace("                   ", "firs = ", the_size.first_block);
        VM_Scheduler.trace("                   ", "curr = ", the_size.current_block);
	    }
	    count++;
	    the_block = blocks[next];
	    next = the_block.nextblock;
	}
	return count;
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



  static  boolean gc_isLive (VM_Address ref) {

    VM_Address tref = VM_ObjectModel.getPointerInMemoryRegion(ref);
    Object obj = VM_Magic.addressAsObject(ref);

    if ( bootHeap.refInHeap(ref) ||
	 immortalHeap.refInHeap(ref) ) {
	return VM_AllocatorHeader.testMarkBit(obj, OBJECT_GC_MARK_VALUE);
    }

    if ( smallHeap.refInHeap(ref) ) {
      //  check for live small object
      int  blkndx, slotno, size, ij;
      blkndx  = (tref.diff(smallHeap.start)) >> LOG_GC_BLOCKSIZE ;
      VM_BlockControl  this_block = blocks[blkndx];
      int  offset   = tref.diff(this_block.baseAddr);
      int  slotndx  = offset/this_block.slotsize;
      return (this_block.mark[slotndx] != 0);
    }
  
    if (largeHeap.refInHeap(ref))
	return largeHeap.isLive(ref);

     VM.sysWrite("gc_isLive: ref not in any known heap: ", ref);
     VM.assert(false);
     return false;
  }

  

    // Normally called from constructor of VM_Processor
    // Also called a second time for the PRIMORDIAL processor during VM.boot
    //
    static  void setupProcessor (VM_Processor st) {

     // for the PRIMORDIAL processor allocation of sizes, etc occurs
     // during init(), nothing more needs to be done
     //
	if (st.id <= VM_Scheduler.PRIMORDIAL_PROCESSOR_ID) 
	  return;
	
     //  Get VM_SizeControl array 
	st.sizes  =  new VM_SizeControl[GC_SIZES];
	for (int i = 0; i < GC_SIZES; i++) {
	    st.sizes[i] = new VM_SizeControl();
	    int ii = VM_Allocator.getnewblockx(i);
	    st.sizes[i].first_block = ii;   // 1 block/size initially
	    st.sizes[i].current_block = ii;
	    st.sizes[i].ndx = i;    // to fit into old code
	    build_list_for_new_block(blocks[ii], st.sizes[i]);
	}
	
	st.GC_INDEX_ARRAY  = new VM_SizeControl[GC_MAX_SMALL_SIZE + 1];
	st.GC_INDEX_ARRAY[0]  = st.sizes[0];  // for size = 0
	//  set up GC_INDEX_ARRAY for this Processor
	int  j = 1;
	for (int i = 0; i < GC_SIZES; i++) 
	    for (; j <= GC_SIZEVALUES[i]; j++) 
		st.GC_INDEX_ARRAY[j] = st.sizes[i];
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
      
    // This routine scans all blocks for all sizes for all processors,
    // beginning with current block; at the end of gc, this gives a
    // correct count of allocated blocks; at the beginning of gc, it
    // does not, since the scan starts from current-block always.(at
    // the end, current_block == first_block
    //

    private static void	freeSmallSpaceDetails (boolean block_count) {

    if ( ! (TIME_GC_PHASES || VM_CollectorThread.MEASURE_WAIT_TIMES || verbose >= 1) )
      return;     // not verbose, no flags on, so don't produce output

	int next;
	int blocks_in_use = 0, blocks_in_partial = 0;
	for (int i = 1; i <= VM_Scheduler.numProcessors; i++) {
	    VM_Processor st = VM_Scheduler.processors[i];
	    VM.sysWriteln(" \nDetails of small space usage: Processor", i);
	    blocks_in_use = blocks_in_use + freeSmallSpaceDetail(st);
	}
	if (block_count) {
	    VM.sysWrite("\n Blocks allocated to procs = ", blocks_in_use);
	    for (int i = 0; i < GC_SIZES; i++) {
		if (partialBlockList[i] == OUT_OF_BLOCKS) continue;
		VM_BlockControl this_block = blocks[partialBlockList[i]];
		if (this_block == null) continue;
		blocks_in_partial++;
		next = this_block.nextblock;
		while (next != OUT_OF_BLOCKS) {
		    blocks_in_partial++;
		    this_block = blocks[next];
		    next = this_block.nextblock;
		}
	    }
	    VM.sysWriteln("\nBlocks partial lists = ", blocks_in_partial);
	    VM.sysWrite("Total blocks not free = ", blocks_in_use + blocks_in_partial);
	    VM.sysWriteln("Total blocks in sys = ", num_blocks);
	}
	VM.sysWriteln("Number of Free Blocks is ", freeBlocks());
    }
			
   private static int freeSmallSpaceDetail (VM_Processor st) {
       int blocks_in_use = 0;
       for (int i = 0; i < GC_SIZES; i++) {
	   VM_BlockControl this_block = blocks[st.sizes[i].first_block];
	   blocks_in_use++;
	   int temp = emptyOfCurrentBlock(this_block, st.sizes[i].next_slot);
	   int next = this_block.nextblock;
	   while (next != OUT_OF_BLOCKS) {
	       this_block = blocks[next];
	       blocks_in_use++;
	       temp += emptyof(i, this_block.mark);
	       next = this_block.nextblock;
	   }
	   VM.sysWrite(GC_SIZEVALUES[i]," sized slots have ");
	   VM.sysWrite(temp/GC_SIZEVALUES[i]," slots free in ");
	   VM.sysWriteln(blocksInChain(blocks[st.sizes[i].first_block]), " allocated blocks");
       }
       return blocks_in_use;
   }

      static int freeBlocks () {
	sysLockBlock.lock();
	if (first_freeblock == OUT_OF_BLOCKS) {
	    sysLockBlock.unlock();
	    return 0;
	}
	VM_BlockControl the_block = blocks[first_freeblock];
	int i = 1;
	int next = the_block.nextblock;
	while (next != OUT_OF_BLOCKS) {
	    the_block = blocks[next];
	    i++;
	    next = the_block.nextblock;
	}
	sysLockBlock.unlock();
	
	return i;
      }
      
    private static void reportBlocks() {

	for (int j = 0; j < GC_SIZES; j++) 
	    total[j] = 0;  
	for (int i = 1; i <= VM_Scheduler.numProcessors; i++) {
	    VM.sysWriteln(" Processor ", i);
	    VM_Processor st = VM_Scheduler.processors[i];
	    for (int j = 0; j < GC_SIZES; j++) {
		VM_SizeControl the_size = st.sizes[j];
		accum[j] = 1;		// count blocks allocated to this size
		VM_BlockControl the_block = blocks[the_size.first_block];
		int next = the_block.nextblock;
		while (next != OUT_OF_BLOCKS) {
		    accum[j]++;
		    the_block = blocks[next];
		    next = the_block.nextblock;
		}
	      total[j] += accum[j];
	      VM.sysWrite(" blocksize = ", GC_SIZEVALUES[j]);
	      VM.sysWriteln(" allocated blocks = ", accum[j]);
	      accum[j] = 0;
	    }
	}	// all processors
	VM.sysWriteln();
	int sum = 0;
	for (int j = 0; j < GC_SIZES; j++) {
	    VM.sysWrite(" blocksize = ", GC_SIZEVALUES[j]);
	    VM.sysWriteln(" total allocated blocks = ", total[j]);
	    sum += total[j];
	}
	
	VM.sysWrite(" Total blocks allocated = ", sum);
	VM.sysWrite(" total blocks in system = ", num_blocks);
	VM.sysWriteln(" available blocks = ", blocks_available);
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

     //  accommodate that ref might be outside space
     VM_Address tref = VM_ObjectModel.getPointerInMemoryRegion(ref);

     if ( smallHeap.refInHeap(ref) ) {
	 // object allocated in small object runtime heap
	 if (!gc_setMarkSmall(tref)) {
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

     if ( largeHeap.refInHeap(ref) ) {
	 //  object allocated in small object runtime heap
	 if (!largeHeap.mark(ref))
	     VM_GCWorkQueue.putToWorkBuffer(ref);
	 return ref;
     }

     if ( bootHeap.refInHeap(ref) ||
	  immortalHeap.refInHeap(ref) ) {
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


      static int getByteArrayInstanceSize (int numelts) {
	  int bytes = byteArrayHeaderSize + numelts;
	  int round = (bytes + (WORDSIZE - 1)) & ~(WORDSIZE - 1);
	  return round;
      }

}
