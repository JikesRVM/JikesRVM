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
 */
public class VM_Allocator
  implements VM_Constants, VM_GCConstants, VM_Uninterruptible,
  VM_Callbacks.ExitMonitor, VM_Callbacks.AppRunStartMonitor
  {

  static final boolean IGNORE_EXPLICIT_GC_CALLS = false;

  static final VM_Array intArrayType  = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("[I")).asArray();
  static final VM_Array byteArrayType = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("[B")).asArray();
  private static final int byteArrayHeaderSize = VM_ObjectModel.computeArrayHeaderSize(byteArrayType);
  static Object[] byteArrayTIB;


  static final boolean GCDEBUG_PARTIAL = false; 
  static final boolean DEBUG_FREEBLOCKS = false; 
  // Debug free space calculation
  static final boolean GCDEBUG_FREESPACE = false; 

  // Debug free slot management
  static final boolean DEBUG_FREE = false;

  // to allow runtime check for which allocator/collector is running
  static final int    TYPE =11;

  // DEBUG WORD TO FIND WHO POINTS TO SOME POINTER
  static int interesting_ref = 0;
  static int the_array     = 0;

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
//static boolean VM.verboseGC              = VM.verboseGC;   // for knowing why GC triggered
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
  static VM_ProcessorLock sysLockLarge;
  static VM_ProcessorLock sysLockFree;
  static VM_ProcessorLock sysLockBlock;

  // temporary...following fields only for copying collector, 
  // but field offsets are taken
  // in VM_Entrypoints (unconditionally), so for now include the fields here also
  static int areaCurrentAddress;
  static int matureCurrentAddress;

  // following included because referenced by refcountGC files (but not used)
  static int bufCount;

  static final boolean  Debug = false;  
  static final boolean  Debug_torture = false;  
  static final boolean  DebugLarge = false;  
  static final boolean  DebugInterest = false;  
  static final boolean  DebugLink = false;
  static final boolean  GC_CLOBBERFREE = false;  
  static final boolean  Report = false;
  static final boolean debug2Threads = false ;
	// statistics reporting
	//
	static final boolean REPORT_BLOCKS = false;
	static boolean flag2nd = false;
	static int[] accum;
	static int[] total;

  // 1 VM_BlockControl per GC-SIZES for initial use, before heap setup
  static VM_BlockControl[]  init_blocks;  // VM_BlockControl[] 1 per BLKSIZE block of the heap
  static int[]            blocks;  // 1 per BLKSIZE block of the heap
        
  static int    allocCount  = 0; // updated every entry to allocate<x>
  static int    fastAllocCount  = 0; // updated every entry to allocate<x>
  static int    gcCount  = 0; // updated every entry to collect
  // major collections in generational scheme
  static int    gcMajorCount = 0; 
  static boolean gc_collect_now = false; // flag to do a collection (new logic)

  static int         smallHeapStartAddress;
  static int         smallHeapEndAddress;
  static int         largeHeapStartAddress;
  static int         largeHeapEndAddress;
  static int         largeSpacePages;
  static int         smallHeapSize;
  static int         largeHeapSize;
  static int    num_blocks;    // number of blocks in the heap
  static int    first_freeblock;  // number of first available block
  static int    highest_block;    // number of highest available block
  static int    blocks_available;  // number of free blocks for small obj's

  static int    large_last_allocated;

  static int[]       old_objects;            // array to track old objects
  static int         nextOldObject;          // next entry in old_objects to use
  static short[]  largeSpaceAlloc;  // used to allocate 
  static short[]  largeSpaceMark;    // used to mark

  static int[]  countLive;    // for stats - count # live objects in bin
  static int[]  countLargeAlloc;  //  - count sizes of large objects alloc'ed
  static int[]  countLargeLive;    //  - count sizes of large objects live
  static int[] countSmallFree;        // bytes allocated by size
  static int[] countSmallBlocksAlloc;  // blocks allocated by size
  static int[] countByteSizes;    // objects allocated by bytesize

  // following used when RENDEZVOUS_TIMES is on: for timing waits in rendezvous
  // copied from copyingGC
  static int rendezvous1in[] = null;
  static int rendezvous1out[] = null;
  static int rendezvous2in[] = null;
  static int rendezvous2out[] = null;
  static int rendezvous3in[] = null;
  static int rendezvous3out[] = null;
  static int rendezvous4in[] = null;
  static int rendezvous4out[] = null;


  static VM_BootRecord   bootrecord;
 
  static int markboot_count = 0;  // counter of objects marked in the boot image
  static int total_markboot_count = 0;  // counter of times markboot called

  static int marklarge_count = 0;  // counter of large objects marked
  static int largerefs_count = 0;  // counter of large objects marked
  static int bootStartAddress;
  static int bootEndAddress;
  static int minBootRef;
  static int maxBootRef;

  static boolean gcInProgress = false;
  static int OBJECT_GC_MARK_VALUE = 0;  // changes between this and 0

  static int[]       partialBlockList;         // GSC
// value below is a tuning parameter: for single threaded appl, on multiple processors
  static int         numBlocksToKeep = 10;     // GSC 
  static final boolean GSC_TRACE = false;			 // GSC

  /** "getter" function for gcInProgress
  */
  
  static boolean
  gcInProgress() {
		return gcInProgress;
	}

  static  void
  init () {
  int i, ii;

  VM_GCLocks.init();  
  VM_Processor st = VM_Scheduler.processors[VM_Scheduler.PRIMORDIAL_PROCESSOR_ID];
  sysLockLarge        = new VM_ProcessorLock();
  sysLockBlock      = new VM_ProcessorLock();
  // create instance of Common work queue object
  VM_GCWorkQueue.workQueue = new VM_GCWorkQueue();

  partialBlockList = new int[GC_SIZES];          // GSC
	for (i = 0; i < GC_SIZES; i++) partialBlockList[i] = OUT_OF_BLOCKS;

  st.sizes = new VM_SizeControl[GC_SIZES];
  init_blocks = new VM_BlockControl[GC_SIZES];
  if (GC_COUNT_BYTE_SIZES) countByteSizes = new int[2048 + 1];

  // if timing rendezvous:
  if (RENDEZVOUS_TIMES) {
    rendezvous1in =  new int[ 1 + VM_Scheduler.MAX_PROCESSORS];
    rendezvous1out =  new int[ 1 + VM_Scheduler.MAX_PROCESSORS];
    rendezvous2in =  new int[ 1 + VM_Scheduler.MAX_PROCESSORS];
    rendezvous2out =  new int[ 1 + VM_Scheduler.MAX_PROCESSORS];
    rendezvous3in =  new int[ 1 + VM_Scheduler.MAX_PROCESSORS];
    rendezvous3out =  new int[ 1 + VM_Scheduler.MAX_PROCESSORS];
    rendezvous4in =  new int[ 1 + VM_Scheduler.MAX_PROCESSORS];
    rendezvous4out =  new int[ 1 + VM_Scheduler.MAX_PROCESSORS];
  }

  VM_CollectorThread.init();   // to alloc its rendezvous arrays, if necessary
  														 // if doing detailed gc timing

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

  countLargeAlloc = new int[GC_LARGE_SIZES];
  countLargeLive  = new int[GC_LARGE_SIZES];
  countSmallFree = new int[GC_SIZES];
  countSmallBlocksAlloc = new int[GC_SIZES];

  for (i = 0; i < GC_LARGE_SIZES; i++) {
    countLargeAlloc[i]  = 0;
    countLargeLive[i]   = 0;
  }

  if (GC_STATISTICS) for (i = 0; i < GC_SIZES; i++) {
    countLive[i]  = 0;
  }

  largeSpaceAlloc = new short[GC_INITIAL_LARGE_SPACE_PAGES];
  for (i = 0; i < GC_INITIAL_LARGE_SPACE_PAGES; i++) largeSpaceAlloc[i] = 0;
  large_last_allocated = 0;
  largeSpacePages = GC_INITIAL_LARGE_SPACE_PAGES;
  
  }      // init(): all this done in bootimagebuilder context


  static void
  boot (VM_BootRecord thebootrecord) {
  int i;
  int blocks_storage, blocks_array_storage;
  VM_Processor st = VM_Scheduler.processors[VM_Scheduler.PRIMORDIAL_PROCESSOR_ID];
    blocks = VM_Magic.addressAsIntArray(VM_Magic.objectAsAddress(init_blocks));
  bootrecord = thebootrecord;  

  // first ref in bootimage
  minBootRef = VM_ObjectModel.minimumObjectRef(bootrecord.startAddress);
  maxBootRef = VM_ObjectModel.maximumObjectRef(bootrecord.freeAddress);    // last ref in bootimage

  bootStartAddress = bootrecord.startAddress;  // start of boot image
  bootEndAddress = bootrecord.freeAddress;    // end of boot image
  smallHeapStartAddress = ((bootEndAddress + GC_BLOCKALIGNMENT - 1)/
    GC_BLOCKALIGNMENT)*GC_BLOCKALIGNMENT;
  smallHeapEndAddress = (bootrecord.endAddress/GC_BLOCKALIGNMENT)* GC_BLOCKALIGNMENT;

  // now touch all the pages in the heap, when 
  // preparing for a timing run to avoid cost of page fault during timing

  if (COMPILE_FOR_TIMING_RUN) 
    for (i = smallHeapEndAddress - 4096; i >= smallHeapStartAddress; i = i - 4096)
    VM_Magic.setMemoryWord(i, 0);
 
  largeHeapStartAddress = bootrecord.largeStart;
  largeHeapEndAddress = bootrecord.largeStart + bootrecord.largeSize;
  smallHeapSize = smallHeapEndAddress - smallHeapStartAddress;
  largeHeapSize = largeHeapEndAddress - largeHeapStartAddress;
      
  // Now set the beginning address of each block into each VM_BlockControl
  // Note that init_blocks is in the boot image, but heap pages are controlled by it

  for (i =0; i < GC_SIZES; i++)  {
    init_blocks[i].baseAddr = smallHeapStartAddress + i * GC_BLOCKSIZE;
    build_list_for_new_block(init_blocks[i], st.sizes[i]);
  }

  // Get the three arrays that control large object space
  short[] temp  = new short[bootrecord.largeSize/4096 + 1];

  for (i = 0; i < GC_INITIAL_LARGE_SPACE_PAGES; i++)
    temp[i] = largeSpaceAlloc[i];

  // At this point temp contains the up-to-now allocation information
  // for large objects; so it now becomes largeSpaceAlloc
  largeSpaceAlloc = temp;
  largeSpacePages = bootrecord.largeSize/4096;
  if (Report) {
    VM_Scheduler.trace("LOADBALANCED COMMON WORK QUEUE","buffer size =", 
      VM_GCWorkQueue.WORK_BUFFER_SIZE);

    VM.sysWrite(bootStartAddress);
    VM.sysWrite("  is the bootStartAddress \n");
    VM.sysWrite(bootEndAddress);
    VM.sysWrite("  is the bootEndAddress \n");

    VM.sysWrite(smallHeapStartAddress);
    VM.sysWrite("  is the smallHeapStartAddress \n");

    VM.sysWrite(smallHeapEndAddress);
    VM.sysWrite("  is the smallHeapEndAddress \n");

    VM.sysWrite(largeHeapStartAddress);
    VM.sysWrite("  is the largeHeapStartAddress \n");

    VM.sysWrite(largeHeapEndAddress);
    VM.sysWrite("  is the largeHeapEndAddress \n");

    VM.sysWrite(smallHeapSize);
    VM.sysWrite("  is the smallHeapSize \n");

  }

  // At this point it is possible to allocate 
  // (1 GC_BLOCKSIZE worth of) objects for each size

  byteArrayTIB = byteArrayType.getTypeInformationBlock();
  Object[] intArrayTIB = intArrayType.getTypeInformationBlock();

  VM_BlockControl.boot();

  // Now allocate the blocks array - which will be used to allocate blocks to sizes

  num_blocks = smallHeapSize/GC_BLOCKSIZE;
  large_last_allocated = 0;

  // GET STORAGE FOR BLOCKS ARRAY FROM OPERATING SYSTEM
  //    storage for entries in blocks array: 4 bytes/ ref
  int blocks_array_size = intArrayType.getInstanceSize(num_blocks);
  if ((blocks_array_storage = malloc(blocks_array_size)) == 0) {
    VM.sysWrite(" In boot, call to sysMalloc returned 0 \n");
    VM.sysExit(1800);
  }

  if ((blocks_storage = malloc((num_blocks-GC_SIZES) * VM_BlockControl.getInstanceSize())) == 0) {
     VM.sysWrite(" In boot, call to sysMalloc returned 0 \n");
     VM.sysExit(1900);
  }

  blocks = (int[])(VM_ObjectModel.initializeArray(blocks_array_storage, intArrayTIB, num_blocks, blocks_array_size));

  // index for highest page in heap
  highest_block = num_blocks -1;
  blocks_available = highest_block - GC_SIZES;   // available to allocate
  
  // Now fill in blocks with values from blocks_init
  for (i = 0; i < GC_SIZES; i++) {
    // NOTE: if blocks are identified by index, st.sizes[] need not be changed; if
    // blocks are identified by address, then updates st.sizes[0-GC_SIZES] here
    blocks[i]      = VM_Magic.objectAsAddress(init_blocks[i]);
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

  for (i = GC_SIZES; i < num_blocks; i++) {
    int bcAddress = blocks_storage + (i - GC_SIZES) * bcSize;
    blocks[i] = VM_Magic.objectAsAddress(VM_ObjectModel.initializeScalar(bcAddress, bcTIB, bcSize));
    VM_Magic.addressAsBlockControl(blocks[i]).baseAddr = 
      smallHeapStartAddress + i * GC_BLOCKSIZE; 
    VM_Magic.addressAsBlockControl(blocks[i]).nextblock = i + 1;
  // set alloc pointer = 0 here
    VM_Magic.addressAsBlockControl(blocks[i]).mark = null;
  }
  
  VM_Magic.addressAsBlockControl(blocks[num_blocks -1]).nextblock = OUT_OF_BLOCKS;
  
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

  largeSpaceMark  = new short[bootrecord.largeSize/4096 + 1];

  VM_AllocatorHeader.boot(bootStartAddress, bootEndAddress);

  VM_Callbacks.addExitMonitor(new VM_Allocator());
  VM_Callbacks.addAppRunStartMonitor(new VM_Allocator());

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
      int objaddr = allocateRawMemory(size, tib, hasFinalizer);
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
  public static int allocateRawMemory (int size, Object[] tib, boolean hasFinalizer) {
      VM_Magic.pragmaInline();

      debugAlloc(size, tib, hasFinalizer); // debug: usually inlined away to nothing

      int objaddr;
      if (size <= GC_MAX_SMALL_SIZE) {
	  // Use magic to avoid spurious array bounds check on common case allocation path.
  	  int rs = VM_Magic.getIntAtOffset(VM_Processor.getCurrentProcessor().GC_INDEX_ARRAY, size << 2);
	  VM_SizeControl the_size = VM_Magic.addressAsSizeControl(rs);
	  if (the_size.next_slot != 0)
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
  static int allocateSlotFast (VM_SizeControl the_size) {
      VM_Magic.pragmaInline();

      if (VM.VerifyAssertions) VM.assert(the_size.next_slot != 0);
      if (GC_COUNT_FAST_ALLOC) fastAllocCount++;

      // Get the next object from the head of the list
      int objaddr = the_size.next_slot;
      if (DebugLink) {
	  if (!isValidSmallHeapPtr(objaddr)) VM.sysFail("Bad ptr");
	  if (!isPtrInBlock(objaddr, the_size)) VM.sysFail("Pointer out of block");
      }

      // Update the next pointer
      the_size.next_slot = VM_Magic.getMemoryWord(objaddr);
      if (DebugLink && (the_size.next_slot != 0)) {
	  if (!isValidSmallHeapPtr(the_size.next_slot)) VM.sysFail("Bad ptr");
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
  static int allocateSlot (VM_SizeControl the_size, int size)
  {
      VM_Magic.pragmaNoInline(); // make sure this method is not inlined

      for (int control = 0; control < GC_RETRY_COUNT; control++) {
	  int objaddr = allocateSlotFromBlocks(the_size, size);
	  if (objaddr != 0)
	      return objaddr;

	  if (VM.verboseGC) 
	      VM_Scheduler.trace(" garbage collection triggered by", " small scalar request \n", size);
	  if (control > 0)
	      flag2nd = true;

	  gc1();

	  // reset the_size in case we are on a different processor after GC
	  the_size  = VM_Processor.getCurrentProcessor().GC_INDEX_ARRAY[size];

	  if (DEBUG_FREE && the_size.current_block != the_size.first_block) 
	      VM_Scheduler.trace(" After gc, current_block not reset ", "AS1", the_size.ndx);

	  // At this point allocation might or might not succeed, since the
	  // thread which originally requested the collection is usually not
	  // the one to run after gc is finished; therefore failing here is
	  // not a permanent failure necessarily

	  if (the_size.next_slot != 0)  // try fast path again
	      return allocateSlotFast(the_size);
      }

      outOfMemory(size, 1300);
      return 0; // never reached: outOfMemory() does not return
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
  static int allocateSlotFromBlocks (VM_SizeControl the_size, int size) {
      VM_BlockControl the_block = VM_Magic.addressAsBlockControl(blocks[the_size.current_block]);

      if (VM.VerifyAssertions) VM.assert(gcCount != 0 || the_block.nextblock == OUT_OF_BLOCKS);	// If no GC yet, no blocks after current

      // First, look for a slot in the blocks on the existing list
      while (the_block.nextblock != OUT_OF_BLOCKS) {
	  the_size.current_block = the_block.nextblock;
	  the_block = VM_Magic.addressAsBlockControl(blocks[the_block.nextblock]);
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
	  the_block = VM_Magic.addressAsBlockControl(blocks[the_block.nextblock]);

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
	  build_list_for_new_block(VM_Magic.addressAsBlockControl(blocks[the_size.current_block]), the_size);

	  return allocateSlotFast(the_size);
      }
  
      // All attempts failed; time to GC
      return 0;
  } 



  /**
   * Allocate a large object; if none available collect garbage and try again.
   *   @param size Size in bytes to allocate
   *   @return Address of zero-filled free storage
   */
  static int allocateLarge (int size) 
  {
      VM_Magic.pragmaNoInline(); // make sure this method is not inlined

      for (int control = 0; control < GC_RETRY_COUNT; control++) {
	  int objaddr = getlargeobj(size);
	  if (objaddr >= 0)
	      return objaddr;

	  if (VM.verboseGC) 
	      VM_Scheduler.trace(" Garbage collection triggered by large request \n", "XX");
	  if (control > 0)
	      flag2nd = true;
	  gc1();
      }

      outOfMemory(size, 2300);
      return 0; // never executed
  }

  // build, in the block, the list of free slot pointers, and update the
  // associated VM_SizeControl.next_slot; return true if a free slot was found,
  // or false if not
  //
  static boolean
  build_list (VM_BlockControl the_block, VM_SizeControl the_size) 
  {
  byte[] the_mark = the_block.mark;
  int first_free = 0, i = 0, j, current, next;
    
  if (VM.VerifyAssertions) VM.assert(the_mark != null);
  if (false && the_mark == null) {
      VM.sysWriteln("mark = ", VM_Magic.objectAsAddress(the_mark));
      VM.sysWriteln("size = ", GC_SIZEVALUES[the_size.ndx]);
  }


  for (; i < the_mark.length ; i++) 
    if (the_mark[i] == 0) break;

  if ( i == the_mark.length ) { // no free slot was found

    if (DebugLink) 
       VM_Scheduler.trace("build_list: ", "found a full block", the_block.slotsize);

    // Reset control info for this block, for next collection 
    VM_Memory.zero(VM_Magic.objectAsAddress(the_mark),
    VM_Magic.objectAsAddress(the_mark) + the_mark.length);
    the_block.live = false;
    return false;  // no free slots in this block
  }
  // here is the first
  else current = the_block.baseAddr + i * the_block.slotsize;  
  VM_Memory.zero(current + 4, current + the_block.slotsize);
  the_size.next_slot = current;
  if (DebugLink && (the_size.next_slot != 0)) {
    if (!isValidSmallHeapPtr(the_size.next_slot)) VM.sysFail("Bad ptr");
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
    VM_Magic.objectAsAddress(the_mark) + the_mark.length);
    the_block.live = false;
    return true;
  }

  next = the_block.baseAddr + i * the_block.slotsize;
  VM_Magic.setMemoryWord(current, next);
  current = next; 
  VM_Memory.zero(current + 4, current + the_block.slotsize);

  // build the rest of the list; there is at least 1 more free slot
  for (i = i + 1; i < the_mark.length ; i++) {
    if (the_mark[i] == 0) {  // This slot is free
      next = the_block.baseAddr + i * the_block.slotsize;
      VM_Magic.setMemoryWord(current, next);  // enter list pointer
      current = next;
      VM_Memory.zero(current + 4, current + the_block.slotsize);
    }
  }

  VM_Magic.setMemoryWord(current,0);    // set the end of the list
  if (DebugLink) do_check(the_block, the_size);
  // Reset control info for this block, for next collection 
  VM_Memory.zero(VM_Magic.objectAsAddress(the_mark),
  VM_Magic.objectAsAddress(the_mark) + the_mark.length);
  the_block.live = false;
  return true;
  } 

  // A debugging routine: called to validate the result of build_list 
  // and build_list_for_new_block
  // 
  private static void
  do_check (VM_BlockControl the_block, VM_SizeControl the_size) 
  {
  int count = 0;
  if (VM_Magic.addressAsBlockControl(blocks[the_size.current_block]) != the_block) {
    VM_Scheduler.trace("do_check", "BlockControls don't match");
    VM.sysFail("BlockControl Inconsistency");
  }
  if (the_size.next_slot == 0) VM_Scheduler.trace("do_check", "no free slots in block");
  int temp = the_size.next_slot;
  while (temp != 0) {
    if ((temp < the_block.baseAddr) || (temp > the_block.baseAddr + GC_BLOCKSIZE))  {
      VM_Scheduler.trace("do_check: TILT:", "invalid slot ptr", temp);
      VM.sysFail("Bad freelist");
    }
  count++;
  temp = VM_Magic.getMemoryWord(temp);
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
  static void
  build_list_for_new_block (VM_BlockControl the_block, VM_SizeControl the_size)
  {
  byte[] the_mark = the_block.mark;
  int i, current, delta;
  current = the_block.baseAddr;
  VM_Memory.zero(current, current + GC_BLOCKSIZE);
  delta  = the_block.slotsize;
  the_size.next_slot = current ;  // next one to allocate
  if (DebugLink && (the_size.next_slot != 0)) {
    if (!isValidSmallHeapPtr(the_size.next_slot)) VM.sysFail("Bad ptr");
    if (!isPtrInBlock(the_size.next_slot, the_size)) VM.sysFail("Pointer out of block");
  }
  for (i = 0; i < the_mark.length -1; i++) {
    VM_Magic.setMemoryWord(current, current + delta);
    current += delta;
  }
  // last slot does not point forward - already zeroed
  //  
  if (DebugLink) do_check(the_block, the_size);
  // Reset control info for this block, for next collection 
  VM_Memory.zero(VM_Magic.objectAsAddress(the_mark),
  VM_Magic.objectAsAddress(the_mark) + the_mark.length);
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

    boolean hasFinalizer = VM_Magic.addressAsType(VM_Magic.getMemoryWord(VM_Magic.objectAsAddress(tib))).hasFinalizer();
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

      int objaddr = allocateRawMemory(size, tib, false);
      Object objptr = VM_ObjectModel.initializeArray(objaddr, tib, numElements, size);

      if (DebugInterest && VM_Magic.objectAsAddress(objptr) == the_array) 
          VM.sysWrite (" Allocating the_array in new page \n");

      return objptr;
  }


  /**
   * Allocate an array of the given size and the clone the data from
   * the given source array into the newly allocated array.
   *   @param numElements Number of array elements
   *   @param size Size of the object in bytes, including the header
   *   @param tib Pointer to the Type Information Block for the object type
   *   @param cloneSrc Array to clone into the newly allocated object
   *   @return Initialized, cloned array
   */
  public static Object cloneArray (int numElements, int size, Object[] tib, Object cloneSrc)
      throws OutOfMemoryError
  {
      if (DebugLink) VM_Scheduler.trace("cloneArray", "called");

      Object objref = allocateArray(numElements, size, tib);
      VM_ObjectModel.initializeArrayClone(objref, cloneSrc, size);
      return objref;
  }


  // made public so it could be called from VM_WriteBuffer
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

      if (GC_STATISTICS) {
        // increment count of large objects alloc'd
        if (num_pages < GC_LARGE_SIZES) countLargeAlloc[num_pages]++;  
        else countLargeAlloc[GC_LARGE_SIZES - 1]++;
      }

    sysLockLarge.unlock();  //release lock *and synch changes*
    int target = largeHeapStartAddress + 4096 * first_free;
    // zero space before return
    VM_Memory.zero(target, target + size);
    return target;
    
    }  // found space for the new object without skipping any space    

    else {  // first free area did not contain enough contig. pages
      first_free = i + largeSpaceAlloc[i]; 
      while (largeSpaceAlloc[first_free] != 0) 
        first_free += largeSpaceAlloc[first_free];
    }
  }    // go to top and try again

  // fall through if reached the end of large space without finding 
  // enough space
  sysLockLarge.release();  //release lock: won't keep change to large_last_alloc'd
  return -2;  // reached end of largeHeap w/o finding numpages
  }

  
  // A routine to obtain a free VM_BlockControl and return it
  // to the caller.  First use is for the VM_Processor constructor: 
  static int
  getnewblockx (int ndx) {
  int location;
  sysLockBlock.lock();
  if (first_freeblock == OUT_OF_BLOCKS) {
  if (VM.verboseGC) 
    VM_Scheduler.trace(" gc collection triggered by getnewblockx call ", "XX");
  gc1();
  }  
  
  VM_BlockControl alloc_block = VM_Magic.addressAsBlockControl(blocks[first_freeblock]);
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
      free(VM_Magic.objectAsAddress(alloc_block.mark) - byteArrayHeaderSize);
    }
  }
  // get space for alloc arrays from AIX.
  int mark_array_size = getByteArrayInstanceSize(size);
  if ((location = malloc(mark_array_size)) == 0) {
    VM.sysWrite(" In getnewblockx, call to sysMalloc returned 0 \n");
    VM.sysExit(1800);
  }

  if (VM.VerifyAssertions) VM.assert((location & 3) == 0);// check word alignment

  alloc_block.mark = VM_Magic.objectAsByteArray(VM_ObjectModel.initializeArray(location, byteArrayTIB, size, mark_array_size));

  return theblock;
  }


  private static int
  getPartialBlock (int ndx) {

    VM_Processor st = VM_Processor.getCurrentProcessor();
    VM_SizeControl this_size = st.sizes[ndx];
    VM_BlockControl currentBlock =
      VM_Magic.addressAsBlockControl(blocks[this_size.current_block]);

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
    VM_BlockControl allocBlock =
      VM_Magic.addressAsBlockControl(blocks[partialBlockList[ndx]]);
    partialBlockList[ndx] = allocBlock.nextblock;
    allocBlock.nextblock = OUT_OF_BLOCKS;

    if (GSC_TRACE) {
      VM_Processor.getCurrentProcessor().disableThreadSwitching();
      VM.sysWrite("getPartialBlock: ndx = "); VM.sysWrite(ndx,false);
      VM.sysWrite(" allocating "); VM.sysWrite(currentBlock.nextblock,false);
      VM.sysWrite(" baseAddr "); VM.sysWriteHex(allocBlock.baseAddr);
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
  int i, save, size, location;
  VM_Processor st = VM_Processor.getCurrentProcessor();
  VM_SizeControl this_size = st.sizes[ndx];
  VM_BlockControl alloc_block = 
    VM_Magic.addressAsBlockControl(blocks[this_size.current_block]);
  // some debugging code in generational collector available if needed 
   
  // return -1 to indicate small object triggered gc.
  sysLockBlock.lock();
  if (first_freeblock == OUT_OF_BLOCKS) {
  if (VM.verboseGC) 
    VM_Scheduler.trace(" gc collection triggered by getnewblock call ", "XX");
    sysLockBlock.release();
    return -1;
  }

  alloc_block.nextblock = first_freeblock;
  alloc_block = VM_Magic.addressAsBlockControl(blocks[first_freeblock]);
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

  int temp;
  if (alloc_block.mark != null) {
    if (size <= alloc_block.alloc_size) {
      VM_ObjectModel.setArrayLength(alloc_block.mark, size);
      return 0;
    }
    else {    // free the existing array space
      free(VM_Magic.objectAsAddress(alloc_block.mark) - byteArrayHeaderSize);
    }
  }

  int mark_array_size = getByteArrayInstanceSize(size);
  if ((location = malloc(mark_array_size)) == 0) {
    VM.sysWrite(" In getnewblock, call to sysMalloc returned 0 \n");
    VM.sysExit(1800);
  }

  if (VM.VerifyAssertions) VM.assert((location & 3) == 0);// check full wd

  alloc_block.alloc_size = size;  // remember allocated size
  alloc_block.mark = VM_Magic.objectAsByteArray(VM_ObjectModel.initializeArray(location, byteArrayTIB, size, mark_array_size));

  return 0;
  }


  private static int  
  getndx(int size) {
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
   gc_processPtrFieldValue() is the routine that processes a pointer, marks 
   the object as live, then scans the object pointed to for pointer,
   and calls gc_processPtrFieldValue()
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
	  thr.contextRegisters.setInnermost( 0 /*ip*/, thr.jniEnv.JNITopJavaFP );
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
       vp.modifiedOldObjectsTop = VM_Magic.objectAsAddress(vp.modifiedOldObjects) - 4;
		   // at this point we do _not_ attempt to reclaim free blocks - an
       // expensive operation - for non participating vps, since this would
       // not be done in parallel; we assume that, in general, in the next
       // collection non-participating vps do participate
       setupallocation(vp);
     }
    }
    //-#endif
  }

  private static void
  setupallocation(VM_Processor st) {
    for (int i = 0; i < GC_SIZES; i++) {
      VM_BlockControl this_block = VM_Magic.addressAsBlockControl(blocks[st.sizes[i].first_block]);
      VM_SizeControl this_size  = st.sizes[i];
      // begin scan in 1st block again
      this_size.current_block = this_size.first_block;
      if (!build_list(this_block, this_size)) this_size.next_slot = 0;
		}
	}


  private static void
  zeromarks(VM_Processor st) 
  {
    int block_count = 0;
    for (int i = 0; i < GC_SIZES; i++) {

      //  NEED TO INITIALIZE THE BLOCK AFTER CURRENT_BLOCK, FOR
      //  EACH SIZE, SINCE THIS WAS NOT DONE DURING MUTATOR EXECUTION
      VM_BlockControl this_block = VM_Magic.addressAsBlockControl(blocks[st.sizes[i].current_block]);

      int next = this_block.nextblock;
      if (GC_TRACEALLOCATOR) block_count++;
      while (next != OUT_OF_BLOCKS) {
        if (GC_TRACEALLOCATOR) block_count++;
        this_block = VM_Magic.addressAsBlockControl(blocks[next]);
        if (Debug && (this_block.mark == null))
          VM.sysWrite(" In collect, found block with no mark \n");
        VM_Memory.zero(VM_Magic.objectAsAddress(this_block.mark),
           VM_Magic.objectAsAddress(this_block.mark) + this_block.mark.length);
        this_block.live = false;
        next = this_block.nextblock;
      }
    }
  }


  static void
  collect () {
    int i, ii;
    byte[]  blocktemp;  // used to exchange alloc and mark
    byte[]  bytetemp;    // used to exchange Alloc and Mark
    short[]  shorttemp;    // used to exchange Alloc and Mark

    // following just for timing GC time
    double tempTime;        // in milliseconds

    int start, end;
    VM_BlockControl this_block;
    VM_BlockControl next_block;
    VM_SizeControl this_size;
    if (!gc_collect_now) {
			VM_Scheduler.trace(" gc entered with collect_now off","xx");
			return;  // to avoid cascading gc
		}
    if (debug2Threads) {
      VM_Scheduler.trace("Entering gc", "XXXXXXXXXXXXXXXXXXXXXX");
    }

    VM_CollectorThread mylocal = 
      VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());
    int mypid = VM_Processor.getCurrentProcessorId();  // id of processor running on


    // set pointer in processor block to first word in writebuffer - 4
    // since store with update will increment first
    // then store
    VM_Processor st = VM_Processor.getCurrentProcessor();
    st.modifiedOldObjectsTop =
       VM_Magic.objectAsAddress(st.modifiedOldObjects) - 4;

    //   SYNCHRONIZATION CODE for parallel gc
    if (VM_GCLocks.testAndSetInitLock()) {

    if (DebugLarge) freeLargeSpaceDetail();

    if (flag2nd) {
			VM_Scheduler.trace(" collectstart:", "flag2nd on");
			freeSmallSpaceDetails(true);
		}

    gcStartTime = VM_Time.now();         // start time for GC
    totalStartTime += gcStartTime - VM_CollectorThread.startTime; //time since GC requested

    gcCount++;

    if (GC_TRACEALLOCATOR) VM_Scheduler.trace(" Inside Mutex1", "xx");

    // setup common workqueue for num VPs participating, used to be called once.
    // now count varies for each GC, so call for each GC   SES 050201
    //
    VM_GCWorkQueue.workQueue.initialSetup(VM_CollectorThread.numCollectors());

    blocks_available = 0;    // not decremented during allocation

    if (debug2Threads) {
      VM_Scheduler.trace("Allocator: gcCounts ", "  ", gcCount);
      VM_Scheduler.trace("Allocator: allocates", "  ", allocCount);
    }

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
    VM_Memory.zero(VM_Magic.objectAsAddress(largeSpaceMark), 
      VM_Magic.objectAsAddress(largeSpaceMark) + 2*largeSpaceMark.length);

    // zero mark arrays in global partial blocks list
    //
    if (GSC_TRACE) VM.sysWrite("\nZeroing partial block mark arrays\n");

    for (i = 0; i < GC_SIZES; i++) {
      int counter = 0;
      int index = partialBlockList[i];
      while ( index != OUT_OF_BLOCKS ) {
    if (GSC_TRACE) counter++;
	  this_block = VM_Magic.addressAsBlockControl(blocks[index]);
        VM_Memory.zero(VM_Magic.objectAsAddress(this_block.mark),
		       VM_Magic.objectAsAddress(this_block.mark) + this_block.mark.length);
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

    if (RENDEZVOUS_TIMES) rendezvous1in[mypid] = 
     (int)((VM_Time.now() - gcStartTime)*1000000);
    if (RENDEZVOUS_WAIT_TIME) tempTime = VM_Time.now();
    VM_CollectorThread.gcBarrier.rendezvous();
    if (RENDEZVOUS_WAIT_TIME) mylocal.rendezvousWaitTime += VM_Time.now() - tempTime;
    if (RENDEZVOUS_TIMES) rendezvous1out[mypid] = 
		 (int)((VM_Time.now() - gcStartTime)*1000000);

    // reset collector thread local work queue buffers
    VM_GCWorkQueue.resetWorkQBuffers();

    // reset live bits in all blocks ....is this what following does??
    int block_count = 0;

    for (i = 0; i < GC_SIZES; i++) {

      //  NEED TO INITIALIZE THE BLOCK AFTER CURRENT_BLOCK, FOR 
      //  EACH SIZE, SINCE THIS WAS NOT DONE DURING MUTATOR EXECUTION
      this_block = VM_Magic.addressAsBlockControl(blocks[st.sizes[i].current_block]);

      int next = this_block.nextblock;
      if (GC_TRACEALLOCATOR) block_count++;
      while (next != OUT_OF_BLOCKS) {
        if (GC_TRACEALLOCATOR) block_count++;
        this_block = VM_Magic.addressAsBlockControl(blocks[next]);
        if (Debug && (this_block.mark == null)) 
          VM.sysWrite(" In collect, found block with no mark \n");
        VM_Memory.zero(VM_Magic.objectAsAddress(this_block.mark),
		       VM_Magic.objectAsAddress(this_block.mark) + this_block.mark.length);
        this_block.live = false;
        next = this_block.nextblock;
      }
    }

    if (GC_TRACEALLOCATOR) VM_Scheduler.trace(" Blocks zeroed", " = ", block_count);

    // ALL GC THREADS IN PARALLEL


    if (TIME_GC_PHASES && (mylocal.gcOrdinal == 1))
      gcInitDoneTime = VM_Time.now();

    //   SYNCHRONIZATION CODE
    
    if (RENDEZVOUS_WAIT_TIME) tempTime = VM_Time.now();
    if (RENDEZVOUS_TIMES) rendezvous2in[mypid] = (int)((VM_Time.now() - gcStartTime)*1000000);
    VM_CollectorThread.gcBarrier.rendezvous();
    if (RENDEZVOUS_TIMES) rendezvous2out[mypid] = (int)((VM_Time.now() - gcStartTime)*1000000);
    if (RENDEZVOUS_WAIT_TIME) mylocal.rendezvousWaitTime += VM_Time.now() - tempTime;
    
    //   END OF SYNCHRONIZING CODE


    VM_ScanStatics.scanStatics();     // all threads scan JTOC in parallel

    gc_scanStacks();
    if (debug2Threads) 
      VM_Scheduler.trace("Allocator: after scanStacks ", "XXXXXXXX");

    gc_emptyWorkQueue();

    // have processor 1 record timestamp for end of scan/mark/copy phase
    if (TIME_GC_PHASES && (mylocal.gcOrdinal == 1))
      gcScanningDoneTime = VM_Time.now();

    if (Debug) 
      VM_Scheduler.trace("Allocator: after emptyworkqueue", "XXXXXXXXX");

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

      if (RENDEZVOUS_WAIT_TIME) tempTime = VM_Time.now();
      if (RENDEZVOUS_TIMES) rendezvous3in[mypid] = 
			     (int)((VM_Time.now() - gcStartTime)*1000000);
      VM_CollectorThread.gcBarrier.rendezvous();
      if (RENDEZVOUS_TIMES) rendezvous3out[mypid] = 
			     (int)((VM_Time.now() - gcStartTime)*1000000);
      if (RENDEZVOUS_WAIT_TIME) mylocal.rendezvousWaitTime += VM_Time.now() - tempTime;

      // Now handle finalization 
      if (mylocal.gcOrdinal == 1) {
	      //setup work queue -shared data
	      VM_GCWorkQueue.workQueue.reset();
	      VM_Finalizer.moveToFinalizable();
      }

      if (RENDEZVOUS_WAIT_TIME) tempTime = VM_Time.now();
      if (RENDEZVOUS_TIMES) rendezvous4in[mypid] = 
			     (int)((VM_Time.now() - gcStartTime)*1000000);
      VM_CollectorThread.gcBarrier.rendezvous();
      if (RENDEZVOUS_WAIT_TIME) mylocal.rendezvousWaitTime += VM_Time.now() - tempTime;
      
      // Now test if following stanza is needed - iff
      // garbage objects were moved to the finalizer queue
      if (VM_Finalizer.foundFinalizableObject) {

	gc_emptyWorkQueue();

	if (RENDEZVOUS_WAIT_TIME) tempTime = VM_Time.now();
	VM_CollectorThread.gcBarrier.rendezvous();
	if (RENDEZVOUS_TIMES) rendezvous4out[mypid] = 
				(int)((VM_Time.now() - gcStartTime)*1000000);
        if (RENDEZVOUS_WAIT_TIME) mylocal.rendezvousWaitTime += VM_Time.now() - tempTime;
	
      }	// if foundFinalizableObject
      else {
	if (RENDEZVOUS_TIMES) rendezvous4out[mypid] =
				(int)((VM_Time.now() - gcStartTime)*1000000);
      }
    }	// if existObjectsWithFinalizers
      
    if (TIME_GC_PHASES && (mylocal.gcOrdinal==1))  gcFinalizeDoneTime = VM_Time.now();

    // done
    if (GC_CLOBBERFREE) clobberfree();

    // Following local variables for free_block logic
    int local_first_free_ndx = OUT_OF_BLOCKS; 
    int local_blocks_available = 0; 
		int temp;
    VM_BlockControl local_first_free_block = VM_Magic.addressAsBlockControl(VM_NULL);

    for (i = 0; i < GC_SIZES; i++) {
      this_block = VM_Magic.addressAsBlockControl(blocks[st.sizes[i].first_block]);
      this_size  = st.sizes[i];
      // begin scan in 1st block again
      this_size.current_block = this_size.first_block; 
      if (!build_list(this_block, this_size)) this_size.next_slot = 0;
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

        next_block = VM_Magic.addressAsBlockControl(blocks[next]);
        if (!next_block.live) {
          if (local_first_free_block == VM_Magic.addressAsBlockControl(VM_NULL)) 
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
      			this_size.lastBlockToKeep = 
  					(this_block.baseAddr - smallHeapStartAddress)/GC_BLOCKSIZE;

          this_block = next_block;
        }
        next  = this_block.nextblock; 
      }
      // this_block -> last block in list, with next==0. remember its
      // index for possible moving of partial blocks to global lists below
      //
      this_size.last_allocated =
 (this_block.baseAddr - smallHeapStartAddress)/GC_BLOCKSIZE;

    }
		if (DEBUG_FREEBLOCKS) 
			VM_Scheduler.trace(" Found assigned", " freeblocks", local_blocks_available);

//  VM_CollectorThread.gcBarrier.rendezvous();

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
      this_block = VM_Magic.addressAsBlockControl(blocks[partialBlockList[i]]);
			int id = 0;
			temp = this_block.nextblock;
			while (!this_block.live) {
        local_blocks_available++;
				if (GCDEBUG_PARTIAL) VM_Scheduler.trace(" Found an empty block at",
					" head of partial list", partialBlockList[i]);
        if (Debug) if (id++ == 500000) 
          VM.sysFail(" Loop in block controls in first of partial list");
        if (local_first_free_block == VM_Magic.addressAsBlockControl(VM_NULL))  
					{
     			  if (VM.VerifyAssertions) VM.assert(local_first_free_ndx == OUT_OF_BLOCKS);
						local_first_free_block = this_block;
					}
				temp = this_block.nextblock;
				this_block.nextblock = local_first_free_ndx;
				local_first_free_ndx = (this_block.baseAddr - smallHeapStartAddress)/GC_BLOCKSIZE;
				partialBlockList[i] = temp;
				if (temp == OUT_OF_BLOCKS) break;
  		  this_block = VM_Magic.addressAsBlockControl(blocks[temp]);
			}

			if (temp == OUT_OF_BLOCKS) continue;
			int next = this_block.nextblock;
			id = 0;
      while (next != OUT_OF_BLOCKS) {
        if (Debug) if (id++ == 500000) {
          VM.sysFail(" Loop in block controls in partial list");
        }
        next_block = VM_Magic.addressAsBlockControl(blocks[next]);
        if (!next_block.live) {
				  if (GCDEBUG_PARTIAL) VM_Scheduler.trace(" Found an empty block ",
					" in partial list", next);
        // In this stanza, we make the next's next the next of this_block, and put
        // original next on the freelist
          if (local_first_free_block == VM_Magic.addressAsBlockControl(VM_NULL))
					  {
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
    VM_CollectorThread.gcBarrier.rendezvous();

    sysLockFree.lock();   // serialize access to global block data

    // If this processor found empty blocks, add them to global free list
    //
    if (local_first_free_block != VM_Magic.addressAsBlockControl(VM_NULL)) {
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
  VM_BlockControl lastToKeep = VM_Magic.addressAsBlockControl(blocks[this_size.lastBlockToKeep]);
  int firstToGiveUp = lastToKeep.nextblock;
  if (firstToGiveUp != OUT_OF_BLOCKS) {
    VM_Magic.addressAsBlockControl(blocks[ this_size.last_allocated ]).nextblock =
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
    if (RENDEZVOUS_WAIT_TIME) tempTime = VM_Time.now();
    VM_CollectorThread.gcBarrier.rendezvous();
    if (RENDEZVOUS_WAIT_TIME) mylocal.rendezvousWaitTime += VM_Time.now() - tempTime;

    // Each GC thread increments adds its wait times for this collection
    // into its total wait time - for printSummaryStatistics output
    //
    if (VM_CollectorThread.MEASURE_WAIT_TIMES)
      mylocal.incrementWaitTimeTotals();

    if (mylocal.gcOrdinal != 1) return;

    shorttemp      = largeSpaceAlloc;
    largeSpaceAlloc  = largeSpaceMark;
    largeSpaceMark  =  shorttemp;

    if (GC_TRACEALLOCATOR) {
      //  report last value of large_last_allocated
      VM.sysWrite(large_last_allocated);
      VM.sysWrite("  was the base page number of the last_allocated large object \n");
    }

		prepareNonParticipatingVPsForAllocation();

    //  reset value large_last_allocated
    large_last_allocated  = 0;

    gc_collect_now  = false;  // reset flag
    gcInProgress    = false;
    VM_GCLocks.reset();

    // done with collection...except for measurement counters, diagnostic output etc

    if (GC_COUNT_BYTE_SIZES) {
      for (i = 0; i <= 2048; i++) {
         if (countByteSizes[i] != 0) {
          VM.sysWrite(i);
          VM.sysWrite("       ");
          VM.sysWrite(countByteSizes[i]);
          VM.sysWrite("\n");
          countByteSizes[i]  = 0;
        }
      }
    }

    if (GC_COUNT_LIVE_OBJECTS) {
      int  small = 0;
      int  large = 0;
      for (i = 1; i < VM_Scheduler.numProcessors + 1; i++) {
        small  += VM_Scheduler.processors[i].small_live; 
        VM_Scheduler.processors[i].small_live  = 0; 
        large  += VM_Scheduler.processors[i].large_live; 
        VM_Scheduler.processors[i].large_live  = 0; 
      }
      VM_Scheduler.trace("AFTER  GC", "small live =", small);
      VM_Scheduler.trace("AFTER  GC", "large live =", large);
    }
    
    //TODO: complete the support for these data
    if (GC_STATISTICS) {
    //  report  high-water marks, then reset hints to the freeblock scanner 
      for (i = 0; i < GC_SIZES; i++) {
        VM.sysWrite(countLive[i]);
        VM.sysWrite("  objects alive in pool \n");
        VM.sysWrite("  \n");
      }

      for (i = 0; i < GC_LARGE_SIZES; i++) {
        VM.sysWrite(countLargeLive[i]);
        VM.sysWrite("  large objects live after this cycle \n \n");
      }

      VM.sysWrite("VM_Allocator:  markbootcount      = " );
      VM.sysWrite(  markboot_count); 
      VM.sysWrite("\nVM_Allocator:  total_markbootcount  = " );
      VM.sysWrite(  total_markboot_count );
      VM.sysWrite("\nVM_Allocator:  total_marklargecount = ");
      VM.sysWrite(  marklarge_count );
      VM.sysWrite("\nVM_Allocator:  total_largerefs    = " );
      VM.sysWrite(  largerefs_count );
      VM.sysWrite("  \n");
      //  report current value of available blocks
      VM.sysWrite(blocks_available);
      VM.sysWrite("  blocks available after this collection \n");
      //  report number of collections so far
      VM.sysWrite(gcCount);
      VM.sysWrite("th  collection just completed \n");
    
      for (i = 0; i < GC_LARGE_SIZES; i++) {
        countLargeAlloc[i]  = 0;
        countLargeLive[i]  = 0;
      }

      markboot_count  = 0;
      total_markboot_count  = 0;
      marklarge_count  = 0;
      largerefs_count  = 0;
    }  // end of statistics report  

    // always get time - for printSummaryStatistics
    gcEndTime = VM_Time.now();
    gcMinorTime  = gcEndTime - gcStartTime;
    gcTotalTime  += gcMinorTime;
    if (gcMinorTime > gcMaxTime) gcMaxTime = gcMinorTime;

    if (VM.verboseGC||GC_TIMING) {
      if (VM.verboseGC) {
        VM.sysWrite("\n<GC ");
        VM.sysWrite(gcCount,false);
        VM.sysWrite("  time ");
        VM.sysWrite((int)(gcMinorTime * 1000), false);
        VM.sysWrite(" (ms)>\n"); 
      }
    }

    if (TIME_GC_PHASES) accumulateGCPhaseTimes();  	

    if (VM.verboseGC && VM_CollectorThread.MEASURE_WAIT_TIMES)
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

    if (RENDEZVOUS_TIMES) printRendezvousTimes();

    if (Report) {
      VM.sysWrite(gcCount);
      VM.sysWrite("  collections ");
      VM.sysWrite(highest_block);
      VM.sysWrite("  is the highest_block \n");
      VM.sysWrite(blocks_available);
      VM.sysWrite("  blocks are available \n");
    }    
    if (GC_COUNT_FAST_ALLOC) {
      VM.sysWrite(allocCount);
      VM.sysWrite("  total allocations \n");
      VM.sysWrite(fastAllocCount);
      VM.sysWrite("  total fastAllocations \n");
    }

    if (DebugLarge) freeLargeSpaceDetail();
    if (GC_COUNT_BY_TYPES && VM.verboseGC) printCountsByType();

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

  static  void
  gc_scanStacks () {
    VM_Thread  t, mythread;
    int  fp;
    //  get thread ptr of running (the GC) thread
    mythread  = VM_Thread.getCurrentThread();  
    for (int i = 0; i < VM_Scheduler.threads.length; i++) {
      t  = VM_Scheduler.threads[i];
      //  exclude GCThreads and "Empty" threads
      if ( (t != null) && !t.isGCThread ) 
			{
      // get control of this thread
      //  SYNCHRONIZING STATEMENT
      if (VM_GCLocks.testAndSetThreadLock(i)) 
      {
        if (debug2Threads) VM_Scheduler.trace(
          "Allocator: Selecting thread", "XXXXXX", i);

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
  
          VM_ScanStack.scanStack( t, VM_NULL, false /*relocate_code*/ );
        }
        else continue;  // some other gc thread has seized this thread
      }  // not a null pointer, not a gc thread
    }    // scan all threads in the system
  }      // scanStacks()


  /**  a routine to make sure a pointer is into the heap
   */
  private  static boolean
  isValidSmallHeapPtr (int slotAddress) {
      return addressInSmallHeap(slotAddress);
  }


  //  a debugging routine: to make sure a pointer is into the give block
  private  static boolean
  isPtrInBlock (int ptr, VM_SizeControl the_size) 
  {
    VM_BlockControl  the_block =  
      VM_Magic.addressAsBlockControl(blocks[the_size.current_block]);
    int  base = the_block.baseAddr;
    int  offset = ptr - base;
    int  endofslot = ptr + the_block.slotsize;
    if (offset%the_block.slotsize != 0) VM.sysFail("Ptr not to beginning of slot");
    int  bound = base + GC_BLOCKSIZE;
    if ((ptr >= base) && (endofslot <= bound)) return true;
    else  return false;
  }


  static  void
  gc_scanObjectOrArray (int objRef ) {
    VM_Type    type;

    //  First process the header
    //  The header has one pointer in it - 
    //  namely the pointer to the TIB (type info block).
    //
    /// following call not needed for noncopying: all tibs found through JTOC
//  gc_processPtrFieldValue(VM_Magic.getMemoryWord(objRef  + OBJECT_TIB_OFFSET));

    type  = VM_Magic.getObjectType(VM_Magic.addressAsObject(objRef));

    if ( type.isClassType() ) { 
      int[]  referenceOffsets = type.asClass().getReferenceOffsets();
      for (int i = 0, n = referenceOffsets.length; i < n; ++i) {
        if (DebugInterest) {
          if (VM_Magic.getMemoryWord(objRef + referenceOffsets[i])
            == interesting_ref) {
            VM_Scheduler.trace("  In a ref", " found int. ref in ", objRef);
            printclass(objRef);
            VM_Scheduler.trace("  is the type of the pointing ref ", " ");
          }
        }
        gc_processPtrFieldValue( VM_Magic.getMemoryWord(objRef + 
          referenceOffsets[i])  );
      }
    }
    else  if ( type.isArrayType() ) {
      if (type.asArray().getElementType().isReferenceType()) {
        int  num_elements = VM_Magic.getArrayLength(VM_Magic.addressAsObject(objRef));
        int  location = objRef;   // for arrays = address of [0] entry
        int  end    = objRef + num_elements * 4;
        while ( location < end ) {
          if (DebugInterest) {
            if (VM_Magic.getMemoryWord(location) == interesting_ref) {
            VM_Scheduler.trace("  In array of refs", " found int. ref in ", objRef);
            VM.sysWrite(type.getDescriptor());
            }
          }
          gc_processPtrFieldValue(VM_Magic.getMemoryWord(location));
          //  USING  "4" where should be using "size_of_pointer" (for 64-bits)
          location  = location + 4;
        }
      }
    }
    else  {
      VM.sysWrite("VM_Allocator.gc_scanObjectOrArray: type not Array or Class");
      VM.sysExit(1000);
    }
  }  //  gc_scanObjectOrArray

    
  static  boolean
  gc_setMarkLarge (int tref) { 
    int  ij;
    int  page_num = (tref - largeHeapStartAddress ) >> 12;
    if (GC_STATISTICS) largerefs_count++;
    boolean  result = (largeSpaceMark[page_num] != 0);
    if (result) return true;  // fast, no synch case
    
    sysLockLarge.lock();    //  get sysLock for large objects
    result  = (largeSpaceMark[page_num] != 0);
    if (result) {  // need to recheck
      sysLockLarge.release();
      return true;  
    }

    if (GC_STATISTICS) {
      marklarge_count++;  // count large objects marked
      ij = largeSpaceAlloc[page_num];
      if (ij < 0) ij = -ij;
      if (ij < GC_LARGE_SIZES) countLargeLive[ij]++;
      else countLargeLive[GC_LARGE_SIZES -1]++;
    }

    int  temp = largeSpaceAlloc[page_num];
    if (temp == 1) {
      largeSpaceMark[page_num] = 1;
    }
    else  {
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

    sysLockLarge.unlock();  //  INCLUDES sync()
    if (GC_COUNT_LIVE_OBJECTS)
      VM_Processor.getCurrentProcessor().large_live++;
    return  false;
  }  //  gc_setMarkLarge

  
  /**  given an address in the small objec heap (as an int), 
  *  set the corresponding mark byte on
  */
  static  boolean
  gc_setMarkSmall (int tref) {
    boolean  result; 
    int  blkndx, slotno, size, ij;
    blkndx  = (tref - smallHeapStartAddress) >> LOG_GC_BLOCKSIZE ;
    VM_BlockControl  this_block = VM_Magic.addressAsBlockControl(blocks[blkndx]);
    int  offset   = tref - this_block.baseAddr; 
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


  static  void
  gc_processPtrField (int refloc) {
    gc_processPtrFieldValue(VM_Magic.getMemoryWord(refloc));
  }


  static  void
  gc_processPtrFieldValue (int ref) 
  {
  if (ref == 0) return;  // TEST FOR NULL POINTER

  if (Debug) {
    // DEBUG AID: try to filter out non-object pointers into the heap
    int tibptr = VM_Magic.objectAsAddress(VM_ObjectModel.getTIB(ref));  // check that TIB pointer really is
    if (! inHeap(tibptr)) {
      VM.sysWrite(ref);
      VM.sysWrite("  is  bogus pointer into heap, was found \n");
      return;
    }
  }  //  Debug

  //  accommodate that ref might be outside space
  int  tref = VM_ObjectModel.getPointerInMemoryRegion(ref);

  if (referenceInSmallHeap(ref)) {
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
    return;
  }  

  if (referenceInLargeHeap(ref)) {
    //  object allocated in small object runtime heap
    if (!gc_setMarkLarge(tref)) 
      VM_GCWorkQueue.putToWorkBuffer(ref);
    return;
  }

  if (VM_GCUtil.referenceInBootImage(ref)) {
    if (VM_AllocatorHeader.testAndMark(VM_Magic.addressAsObject(ref), OBJECT_GC_MARK_VALUE))
      VM_GCWorkQueue.putToWorkBuffer(ref);
    return;
  }

  if (referenceInMallocedStorage(ref)) {
      return;
  }

    VM.sysWrite(tref);
    VM.sysFail(" gc_processPtrFieldValue ptr:  not in heap or boot image \n");
  }  //  gc_processPtrFieldValue   


  //  process objects in the work queue buffers until no more buffers to process
  //
  static  void
  gc_emptyWorkQueue()  {
  int  ref = VM_GCWorkQueue.getFromWorkBuffer();

  if (VM_GCWorkQueue.WORKQUEUE_COUNTS) {
    VM_CollectorThread myThread = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());
    myThread.rootWorkCount = myThread.putWorkCount;
  }

  //    VM.sysWrite(" gc_emptyworkqueue first ref =");   VM.sysWrite(ref);   VM.sysWrite("\n");

  while ( ref != 0 ) {
    gc_scanObjectOrArray( ref );
    ref = VM_GCWorkQueue.getFromWorkBuffer();
  }
  }    // gc_emptyWorkQueue


  //  To be able to be called from java/lang/runtime, or internally
  //
  public  static void
  gc ()  {
    if (IGNORE_EXPLICIT_GC_CALLS) {
	if (Debug) VM.sysWrite("Skipped external call to collect garbage.\n");
	return;
    }

    if (VM.verboseGC)
      VM_Scheduler.trace("  gc triggered by external call to gc() \n \n", "XX");

    gc1();
  }


  public static void
  gc1 ()
  {
    double  time;
    gc_collect_now  = true;
    if (GC_MUTATOR_TIMES) time = VM_Time.now();

    int mypid_before, mypid_after;

    //  Tell gc thread to reclaim space, then wait for it to complete its work.
    //  The gc thread will do its work by calling collect(), below.
    //
    VM_CollectorThread.collect(VM_CollectorThread.collect);

    if (GC_MUTATOR_TIMES) {
      time = VM_Time.now() - time;
      VM_Scheduler.trace("GC time at mutator","(millisec)",(int)(time*1000.0));
      VM_Scheduler.trace("GC - time","     (millisec)",(int)(gcMinorTime*1000.0));
      VM_Scheduler.trace("GC TOTAL - time"," (millisec)",(int)(gcTotalTime*1000.0));
      VM_Scheduler.trace("GC TOTAL COLLECTIONS = ", "XXX", gcCount);
    }

  }  //  gc1


  static void
  dumpblocks (VM_Processor st)  {
    VM.sysWrite("\n-- Processor ");
		VM.sysWrite(st.id, false);
		VM.sysWrite(" --\n");
    for (int i = 0; i < GC_SIZES; i++) {
			VM.sysWrite(" Size ");
		  VM.sysWrite(GC_SIZEVALUES[i], false);
			VM.sysWrite("  ");
			VM_BlockControl the_block = VM_Magic.addressAsBlockControl(blocks[st.sizes[i].first_block]);
			VM.sysWrite(st.sizes[i].first_block, false);
			while (true) {
				VM.sysWrite("  ");
				VM.sysWrite(the_block.nextblock, false);
				if (the_block.nextblock == OUT_OF_BLOCKS) break;
				the_block = VM_Magic.addressAsBlockControl(blocks[the_block.nextblock]);
			}		
			VM.sysWrite("\n");
		}
	}

  static  void
  dumpblocks () {
    VM_Processor  st = VM_Processor.getCurrentProcessor();
    VM.sysWrite(first_freeblock);
    VM.sysWrite("  is the first freeblock index \n");
    for (int iii = 0; iii < GC_SIZES; iii++) {
    VM.sysWrite(iii);
    VM.sysWrite("th VM_SizeControl first_block = " );
    VM.sysWrite( st.sizes[iii].first_block);
    VM.sysWrite(" current_block = "); 
    VM.sysWrite(st.sizes[iii].current_block);
    VM.sysWrite("\n\n");
    }
    
    for (int iii = 0; iii < num_blocks; iii++) {
      VM.sysWrite(iii);
      VM.sysWrite("th VM_BlockControl   ");
      if (VM_Magic.addressAsBlockControl(blocks[iii]).live) VM.sysWrite("   live"); 
      else VM.sysWrite("not live");
      VM.sysWrite("  "); 
      VM.sysWrite(" \nbaseaddr = "); 
      VM.sysWrite(VM_Magic.addressAsBlockControl(blocks[iii]).baseAddr);
      VM.sysWrite(" \nnextblock = "); 
      VM.sysWrite(VM_Magic.addressAsBlockControl(blocks[iii]).nextblock);
      VM.sysWrite("\n");
    }
    
  }  //  dumpblocks


  static  void
  clobber (int addr, int length) {
    int value = 0xdeaddead;
    int i;
    for (i = 0; i + 3 < length; i = i+4) VM_Magic.setMemoryWord(addr + i, value);
  }


  static  void
  clobberfree () {
    VM_Processor  st = VM_Processor.getCurrentProcessor();
    for (int i = 0; i < GC_SIZES; i ++) {
      VM_BlockControl this_block = 
        VM_Magic.addressAsBlockControl(blocks[st.sizes[i].first_block]);  
      byte[] this_alloc      = this_block.mark;
      for (int ii = 0; ii < this_alloc.length; ii ++) {
        if (this_alloc[ii] == 0) clobber(this_block.baseAddr + 
          ii * GC_SIZEVALUES[i], GC_SIZEVALUES[i]);
      }
      int next = this_block.nextblock;
      while (next != OUT_OF_BLOCKS) {
        this_block = VM_Magic.addressAsBlockControl(blocks[next]);
        this_alloc      = this_block.mark;
        for (int ii = 0; ii < this_alloc.length; ii ++) {
          if (this_alloc[ii] == 0)
            clobber(this_block.baseAddr  + ii * GC_SIZEVALUES[i], GC_SIZEVALUES[i]);
        }
        next = this_block.nextblock;
      }
    }

    // Now clobber the free list
    sysLockBlock.lock();
    VM_BlockControl block;
    for (int freeIndex = first_freeblock; freeIndex != OUT_OF_BLOCKS; freeIndex = block.nextblock) {
	block = VM_Magic.addressAsBlockControl(blocks[freeIndex]);
	clobber(block.baseAddr, GC_BLOCKSIZE);
    }
    sysLockBlock.unlock();
  }



  public  static long
  totalMemory () {
    return smallHeapSize + largeHeapSize;
  }

/*** REPLACE THIS WITH VERSION BELOW; KEEP OLD FOR NOW
  public  static long
  freeMemory () {
    
    total_blocks_in_use  = 0;     
    long  total = 0;
    for (int i = 1; i <= VM_Scheduler.numProcessors; i++) 
      total = total + freeSmallSpace(VM_Scheduler.processors[i]);
    
    return (freeLargeSpace() + total + (highest_block - total_blocks_in_use) * 
      GC_BLOCKSIZE);
  }  //  freeMemory
***/

  public static long
  freeMemory () {
    return freeBlocks() * GC_BLOCKSIZE;
  }


  public  static long
  freeLargeSpace () {

    int  total = 0;
    for (int i = 0 ; i < largeSpacePages;) {
      if (largeSpaceAlloc[i] == 0) {
        total++;
        i++;
      }
      else i = i + largeSpaceAlloc[i]; // negative value in largeSpA
    }
    
    return (total * 4096);     // number of bytes free in largespace
  }  //  freeLargeSpace

  public  static void
  freeLargeSpaceDetail () {
  
  int  total = 0;
  int  largelarge = 0;
  int  largesize = 0;
  int  i,templarge = 0;
  VM.sysWrite(largeHeapSize);
  VM.sysWrite("  is the large object heap size in bytes \n");
  for (i = 0 ; i < largeSpacePages;) {
    if (largeSpaceAlloc[i] == 0) {
      templarge++;
      if (templarge > largesize) largesize = templarge;
      total++;
      i++;
    }
    else  {
      templarge  = 0;
      int  temp = largeSpaceAlloc[i];
      if (temp < GC_LARGE_SIZES) countLargeLive[temp]++;
      else  {
        VM.sysWrite(temp);
        VM.sysWrite("  pages of a very large object \n");
        largelarge++;
      }
      i  = i + largeSpaceAlloc[i]; // negative value in largeSpA
    }
  }
  
  VM.sysWrite(total);
  VM.sysWrite("  pages free in large space \n ");
  VM.sysWrite(largesize);
  VM.sysWrite("  is largest block in pages available \n");

  for (i = 0; i < GC_LARGE_SIZES; i++) {
    if (countLargeLive[i] > 0) {
      VM.sysWrite(countLargeLive[i]);
      VM.sysWrite("  large objects of size ");
      VM.sysWrite(i);
      VM.sysWrite("  live \n");
      countLargeLive[i]  = 0;  // for next time
    }
  }
  VM.sysWrite(largelarge);
  VM.sysWrite("  very large objects live \n ");
  
  }  //  freeLargeSpaceDetail

  static  int total_blocks_in_use; // count blocks in use during this calculation

  public  static long
  freeSmallSpace (VM_Processor st) {

  int  total = 0;
  int  i, next, temp;
  VM_BlockControl  this_block;
	int current_pointer;

  for (i = 0; i < GC_SIZES; i++) {
    total_blocks_in_use+= blocksToCurrent(st.sizes[i]);
    this_block  = VM_Magic.addressAsBlockControl(blocks[st.sizes[i].current_block]);
		current_pointer = st.sizes[i].next_slot;
    total+= emptyOfCurrentBlock(this_block, current_pointer); 
    next  = this_block.nextblock;
    while (next != OUT_OF_BLOCKS) {
    	if (GCDEBUG_FREESPACE) 
			  VM_Scheduler.trace(" In freesmallspace ", "next = ", next);
      this_block  = VM_Magic.addressAsBlockControl(blocks[next]);
      total_blocks_in_use++;
    
      total  += emptyof(i, this_block.mark);
      next  = this_block.nextblock;
    }
  }
  
  return  total;
  }

  private static int
  emptyOfCurrentBlock(VM_BlockControl the_block, int current_pointer) {
    int i = current_pointer;
    int sum = 0;
    while (i != 0) {
      sum += the_block.slotsize;
      i = VM_Magic.getMemoryWord(i);
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
			the_block = VM_Magic.addressAsBlockControl(blocks[next]); 
			next = the_block.nextblock;
		}
		return count;
	}

	// Count the blocks in the size_control input from first to current
	// 
  private static int
	blocksToCurrent (VM_SizeControl the_size) {
		int count = 1;
		if (the_size.first_block == the_size.current_block) return 1;
		VM_BlockControl the_block = VM_Magic.addressAsBlockControl(blocks[the_size.first_block]);	
		int next = the_block.nextblock;
		while (next != the_size.current_block) {
      if (GCDEBUG_FREESPACE) {
        VM_Scheduler.trace(" In blocksToCurrent", "next = ", next);
        VM_Scheduler.trace("                   ", "firs = ", the_size.first_block);
        VM_Scheduler.trace("                   ", "curr = ", the_size.current_block);
			}
			count++;
			the_block = VM_Magic.addressAsBlockControl(blocks[next]);
			next = the_block.nextblock;
		}
		return count;
	}
	
  static  void
  printRendezvousTimes()  {

  VM.sysWrite("RENDEZVOUS ENTRANCE & EXIT TIMES (microsecs) rendezvous 1, 2 & 3\n");

  for (int i = 1; i <= VM_Scheduler.numProcessors; i++) {
    VM.sysWrite(i,false);
    VM.sysWrite("  R1 in ");
    VM.sysWrite(rendezvous1in[i],false);
    VM.sysWrite("  out ");
    VM.sysWrite(rendezvous1out[i],false);
    VM.sysWrite("  R2 in ");
    VM.sysWrite(rendezvous2in[i],false);
    VM.sysWrite("  out ");
    VM.sysWrite(rendezvous2out[i],false);
    VM.sysWrite("  for ");
    VM.sysWrite(rendezvous2out[i]  - rendezvous2in[i], false);
    VM.sysWrite("  R3 in ");
    VM.sysWrite(rendezvous3in[i],false);
    VM.sysWrite("  out ");
    VM.sysWrite(rendezvous3out[i],false);
    VM.sysWrite("  for ");
    VM.sysWrite(rendezvous3out[i]  - rendezvous3in[i], false);
    VM.sysWrite(" R4 in ");
    VM.sysWrite(rendezvous4in[i],false);
    VM.sysWrite(" out ");
    VM.sysWrite(rendezvous4out[i],false);
    VM.sysWrite("  for ");
    VM.sysWrite(rendezvous4out[i]  - rendezvous4in[i], false);
    VM.sysWrite("\n");
  }
  }

  private static void
  accumulateGCPhaseTimes () {
    double start    = gcStartTime - VM_CollectorThread.startTime;
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
    if (VM.verboseGC) {
      VM.sysWrite("<GC ");
      VM.sysWrite(gcCount,false);
      VM.sysWrite(" startTime ");
      VM.sysWrite( (int)(start*1000000.0), false);
      VM.sysWrite("(us) init ");
      VM.sysWrite( (int)(init*1000000.0), false);
      VM.sysWrite("(us) scanning ");
      VM.sysWrite( (int)(scanning*1000.0), false );
      VM.sysWrite("(ms) finalize ");
      VM.sysWrite( (int)(finalize*1000000.0), false);
      VM.sysWrite("(us) finish ");
      VM.sysWrite( (int)(finish*1000000.0), false);
      VM.sysWrite("(us)>\n");
    }
  }


  /** Following routine required for compilation
   *  compatibility with generational collectors
   */
  static  boolean
  gc_isOldObject (int dummy) {
  VM.assert(NOT_REACHED);
  return  false;
  }  

  static  boolean
  gc_isLive (int ref) {
    int  tref = VM_ObjectModel.getPointerInMemoryRegion(ref);

    if (VM_GCUtil.referenceInBootImage(ref)) {
      if (GC_STATISTICS) total_markboot_count++;  

      //  test mark bit to see if already marked
      return VM_AllocatorHeader.testMarkBit(VM_Magic.addressAsObject(ref), OBJECT_GC_MARK_VALUE);
    }

    if (referenceInSmallHeap(ref)) {
      //  check for live small object
      int  blkndx, slotno, size, ij;
      blkndx  = (tref - smallHeapStartAddress) >> LOG_GC_BLOCKSIZE ;
      VM_BlockControl  this_block = VM_Magic.addressAsBlockControl(blocks[blkndx]);
      int  offset   = tref - this_block.baseAddr;
      int  slotndx  = offset/this_block.slotsize;
      return (this_block.mark[slotndx] != 0);
    }

    if (referenceInLargeHeap(ref)) {   // check for live large object
      int  page_num = (tref - largeHeapStartAddress ) >> 12;
      return (largeSpaceMark[page_num] != 0);
    }

    if (referenceInMallocedStorage(ref)) {
	return true;
    }

    VM.sysFail("gc_isLive: pointer not in a vaid heap region");
    return false;
  }


  static  boolean
  validRef ( int ref ) {
  VM.assert(NOT_REACHED);
  return  false;
  }
  

  // called from constructor of VM_Processor, also called a second time
  // for the PRIMORDIAL processor during VM.boot
  static  void
  setupProcessor (VM_Processor st) 
  {
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
    build_list_for_new_block(VM_Magic.addressAsBlockControl(blocks[ii]),
          st.sizes[i]);
  }

  st.GC_INDEX_ARRAY  = new VM_SizeControl[GC_MAX_SMALL_SIZE + 1];
  st.GC_INDEX_ARRAY[0]  = st.sizes[0];  // for size = 0
  //  set up GC_INDEX_ARRAY for this Processor
  int  j = 1;
  for (int i = 0; i < GC_SIZES; i++) 
    for (; j <= GC_SIZEVALUES[i]; j++) st.GC_INDEX_ARRAY[j] = st.sizes[i];
  }


/**  A routine to report number of live objects by type: numbers
*  collected in processPtrFieldValue at return from gc_setMarkSmall()
*/
  private  static void
  printCountsByType()  {

  VM.sysWrite("  Counts of live objects by type \n");
  int  nextId = VM_TypeDictionary.getNumValues();
  int  totalObjects = 0, totalSpace = 0;
  for (int i = 1; i < nextId; i++) {
    VM_Type type = VM_TypeDictionary.getValue(i);
    if (type.liveCount == 0) continue;
    VM.sysWrite(type.liveCount, false);
    totalObjects += type.liveCount;
    if (type.liveCount < 10) VM.sysWrite   ("      ");
    else if (type.liveCount < 100) VM.sysWrite ("     ");
    else if (type.liveCount < 1000) VM.sysWrite ("    ");
    else if (type.liveCount < 10000) VM.sysWrite ("   ");
    else if (type.liveCount < 100000) VM.sysWrite ("  ");
    VM.sysWrite(type.getDescriptor());
    if (type.isClassType() && type.isResolved()) {
      int size = type.asClass().getInstanceSize();
      VM.sysWrite ("  space is ");
      VM.sysWrite(size * type.liveCount, false);
      totalSpace += size * type.liveCount;
    }
    else {
      VM.sysWrite("   space is ");
      VM.sysWrite(type.liveSpace, false);
      totalSpace += type.liveSpace;
    }
    VM.sysWrite("\n");
    type.liveCount = 0;  // reset
    type.liveSpace = 0;  // reset
  }
  VM.sysWrite(totalObjects, false);
  if (totalObjects < 100) VM.sysWrite ("     ");
  else if (totalObjects < 1000) VM.sysWrite ("    ");
  else if (totalObjects < 10000) VM.sysWrite ("   ");
  else if (totalObjects < 100000) VM.sysWrite ("  ");
  else VM.sysWrite (" ");
  VM.sysWrite ("Live objects   space is "+totalSpace+"\n");
  VM.sysWrite("  End of counts of live objects by type \n");
  }

  public  static void
  printclass (int ref) {
    VM_Type  type = VM_Magic.getObjectType(VM_Magic.addressAsObject(ref));
    VM.sysWrite(type.getDescriptor());
  }

/************  
  Print Summaries routine removed: if needed, consult previous version
**************/

  //  called during Finalization for copying collectors
  static  int    gc_makeLive (int foo) { return 0;}

  private  static int
  gc_copyObject(int  foo) { return -1;}


  // Called from WriteBuffer code for generational collectors.
  // Argument is a modified old object which needs to be scanned
  //
  static void
  processWriteBufferEntry (int ref) {
    VM_ScanObject.scanObjectOrArray(ref);
  }

  static  boolean
  processFinalizerListElement(VM_FinalizerListElement  le) {
    boolean is_live = gc_isLive(le.value);
    if ( !is_live ) {
      // now set pointer field of list element, which will keep 
      // the object alive in subsequent collections, until the 
      // FinalizerThread executes the objects finalizer
      //
      le.pointer = VM_Magic.addressAsObject(le.value);
      // process the ref, to mark it live & enqueue for scanning
      VM_Allocator.gc_processPtrFieldValue(le.value);	
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


  static void
  printSummaryStatistics () {
    int avgTime=0, avgStartTime=0;
    int np = VM_Scheduler.numProcessors;

    // produce summary system exit output if -verbose:gc was specified of if
    // compiled with measurement flags turned on
    //
    if ( ! (TIME_GC_PHASES || VM_CollectorThread.MEASURE_WAIT_TIMES || VM.verboseGC) )
      return;     // not verbose, no flags on, so don't produce output

    VM.sysWrite("\nGC stats: Mark Sweep Collector (");
    VM.sysWrite(np,false);
    VM.sysWrite(" Collector Threads ):\n");
    VM.sysWrite("          Heap Size ");
    VM.sysWrite(smallHeapSize,false);
    VM.sysWrite("  Large Object Heap Size ");
    VM.sysWrite(largeHeapSize,false);
    VM.sysWrite("\n");

    if (gcCount>0) {
      avgTime = (int)( ((gcTotalTime/(double)gcCount)*1000.0) );
      avgStartTime = (int)( ((totalStartTime/(double)gcCount)*1000000.0) );
    }
    VM.sysWrite(gcCount,false);
    VM.sysWrite(" Collections: avgTime ");
    VM.sysWrite(avgTime,false);
    VM.sysWrite(" (ms) maxTime ");
    VM.sysWrite((int)(gcMaxTime*1000.0),false);
    VM.sysWrite(" (ms) totalTime ");
    VM.sysWrite((int)(gcTotalTime*1000.0),false);
    VM.sysWrite(" (ms) avgStartTime ");
    VM.sysWrite(avgStartTime,false);
    VM.sysWrite(" (us)\n\n");

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
      VM.sysWrite("startTime ");
      VM.sysWrite( avgStart, false);
      VM.sysWrite("(us) init ");
      VM.sysWrite( avgInit, false);
      VM.sysWrite("(us) scanning ");
      VM.sysWrite( avgScan, false );
      VM.sysWrite("(ms) finalize ");
      VM.sysWrite( avgFinalize, false);
      VM.sysWrite("(us) finish ");
      VM.sysWrite( avgFinish, false);
      VM.sysWrite("(us)>\n\n");
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
      VM.sysWrite("Buffer Wait ");
      VM.sysWrite( avgBufferWait, false);
      VM.sysWrite(" (us) Finish Wait ");
      VM.sysWrite( avgFinishWait, false);
      VM.sysWrite(" (us) Rendezvous Wait ");
      VM.sysWrite( avgRendezvousWait, false);
      VM.sysWrite(" (us)\n\n");

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
  private static void
	freeSmallSpaceDetails (boolean block_count) {
		int i, next;
		int blocks_in_use = 0, blocks_in_partial = 0;
		VM_Processor st;
		for (i = 1; i <= VM_Scheduler.numProcessors; i++) {
			st = VM_Scheduler.processors[i];
			VM.sysWrite(" \n Details of small space usage: \n Processor");
			VM.sysWrite(i, false);
			VM.sysWrite(" \n");
			blocks_in_use = blocks_in_use + freeSmallSpaceDetail(st);
		}
		if (block_count) {
			VM.sysWrite("\n Blocks alloc'd to procs = ");
			VM.sysWrite(blocks_in_use, false);
			for (i = 0; i < GC_SIZES; i++) {
				if (partialBlockList[i] == OUT_OF_BLOCKS) continue;
	      VM_BlockControl this_block = VM_Magic.addressAsBlockControl(blocks[partialBlockList[i]]);
				if (this_block == null) continue;
				blocks_in_partial++;
				next = this_block.nextblock;
				while (next != OUT_OF_BLOCKS) {
					blocks_in_partial++;
				  this_block = VM_Magic.addressAsBlockControl(blocks[next]);
					next = this_block.nextblock;
				}
			}
			VM.sysWrite("\n Blocks part'l lists = ");
			VM.sysWrite(blocks_in_partial, false);
			VM.sysWrite("\n Total blocks not free = ");
			VM.sysWrite(blocks_in_use + blocks_in_partial, false);
			VM.sysWrite(" Total blocks in sys = ");
			VM.sysWrite(num_blocks, false);
			VM.sysWrite("\n");
    }
		VM.sysWrite("Number of Free Blocks is ");
		VM.sysWrite(freeBlocks(), false);
		VM.sysWrite("\n");
	}
			
  private static int
	freeSmallSpaceDetail (VM_Processor st) {
		int blocks_in_use = 0;
	  for (int i = 0; i < GC_SIZES; i++) {
			VM_BlockControl this_block = VM_Magic.addressAsBlockControl(blocks[st.sizes[i].first_block]);
			blocks_in_use++;
			int temp = emptyOfCurrentBlock(this_block, st.sizes[i].next_slot);
			int next = this_block.nextblock;
			while (next != OUT_OF_BLOCKS) {
				this_block = VM_Magic.addressAsBlockControl(blocks[next]);
				blocks_in_use++;
				temp += emptyof(i, this_block.mark);
				next = this_block.nextblock;
			}
			VM.sysWrite(GC_SIZEVALUES[i], false);
			VM.sysWrite(" sized slots have ");
			VM.sysWrite(temp/GC_SIZEVALUES[i], false);
			VM.sysWrite(" slots free in ");
			VM.sysWrite(blocksInChain(VM_Magic.addressAsBlockControl(blocks[st.sizes[i].first_block])), false);
			VM.sysWrite(" alloc'd blocks\n");
		}
		return blocks_in_use;
	}

  static int
  freeBlocks () {
    sysLockBlock.lock();
	  if (first_freeblock == OUT_OF_BLOCKS) {
			sysLockBlock.unlock();
			return 0;
		}
    VM_BlockControl the_block = VM_Magic.addressAsBlockControl(blocks[first_freeblock]);
		int i = 1;
		int next = the_block.nextblock;
		while (next != OUT_OF_BLOCKS) {
			the_block = VM_Magic.addressAsBlockControl(blocks[next]);
			i++;
			next = the_block.nextblock;
	   }
     sysLockBlock.unlock();

		 return i;
	}

  private static void
	reportBlocks() {
		int i, j, next, sum = 0;
		VM_Processor st;
		for (j = 0; j < GC_SIZES; j++) total[j] = 0;  
		for (i = 1; i <= VM_Scheduler.numProcessors; i++) {
			VM.sysWrite(" Processor ");
			VM.sysWrite(i);
			VM.sysWrite("\n");
		  st = VM_Scheduler.processors[i];
			for (j = 0; j < GC_SIZES; j++) {
				VM_SizeControl the_size = st.sizes[j];
				accum[j] = 1;		// count blocks allocated to this size
				VM_BlockControl the_block = VM_Magic.addressAsBlockControl(blocks[the_size.first_block]);
				next = the_block.nextblock;
				while (next != OUT_OF_BLOCKS) {
					accum[j]++;
					the_block = VM_Magic.addressAsBlockControl(blocks[next]);	
					next = the_block.nextblock;
				}
				total[j] += accum[j];
				VM.sysWrite(" blocksize = ");
				VM.sysWrite(GC_SIZEVALUES[j]);
				VM.sysWrite(" allocated blocks = ");
				VM.sysWrite(accum[j]);
				VM.sysWrite("\n");
				accum[j] = 0;
			}
		}	// all processors
		VM.sysWrite("\n");
		for (j = 0; j < GC_SIZES; j++) {
			VM.sysWrite(" blocksize = ");
			VM.sysWrite(GC_SIZEVALUES[j]);
			VM.sysWrite(" total allocated blocks = ");
			VM.sysWrite(total[j]);
			VM.sysWrite("\n");
			sum += total[j];
		}
		
		VM.sysWrite(" Total blocks allocated = ");
		VM.sysWrite(sum);
		VM.sysWrite(" total blocks in system = ");
		VM.sysWrite(num_blocks);
		VM.sysWrite(" available blocks = ");
		VM.sysWrite(blocks_available);
		VM.sysWrite("\n");
	}

  private static void
  outOfMemory (int size, int code) {
    VM_Scheduler.trace(" Out of memory", " could not satisfy bytes = ", size);
    VM_Scheduler.trace("              ", " code =                    ", code);
    VM_Scheduler.trace(" Current small object heapsize ", "=", smallHeapSize);
    VM_Scheduler.trace(" Specify more space with", " -X:h=");
    VM.shutdown(-5);
  }

  private static void
  outOfLargeMemory (int size, int code) {
    VM_Scheduler.trace(" Out of largeobjectspace", " could not satisfy bytes = ", size);
    VM_Scheduler.trace("  ", " code =  ", code);
    VM_Scheduler.trace(" Current large object heapsize ", "=", largeHeapSize);
    VM_Scheduler.trace(" Specify more space with", " -X:lh=");
    VM.shutdown(-5);
  }

  /**
   * Process an object reference field during collection.
   *
   * @param location  address of a reference field
   */
  static void
  processPtrField ( int location ) {
    gc_processPtrFieldValue(VM_Magic.getMemoryWord( location));
  }

  /**
   * Process an object reference (value) during collection.
   *
   * @param ref  object reference (value)
   */
  static int
  processPtrValue ( int ref ) {
    gc_processPtrFieldValue(ref);
    return ref;
  }



      static int malloc (int size) {
	  return VM.sysCall1(bootrecord.sysMallocIP, size);
      }


      static void free (int address) {
	  VM.sysCall1(bootrecord.sysFreeIP, address);
      }



      static boolean inHeap (int objptr) {
	  return addrInHeap(VM_ObjectModel.getPointerInMemoryRegion(objptr));
      }

      static boolean addrInHeap (int tref) {
	  return VM_GCUtil.addressInBootImage(tref) || addressInSmallHeap(tref) || addressInLargeHeap(tref);
      }



      static boolean referenceInSmallHeap (int objptr) {
	  return addressInSmallHeap(VM_ObjectModel.getPointerInMemoryRegion(objptr));
      }

      static boolean addressInSmallHeap (int tref) {
	  return tref >= smallHeapStartAddress && tref < smallHeapEndAddress;
      }


      static boolean referenceInLargeHeap (int objptr) {
	  return addressInLargeHeap(VM_ObjectModel.getPointerInMemoryRegion(objptr));
      }

      static boolean addressInLargeHeap (int tref) {
	  return tref >= largeHeapStartAddress && tref < largeHeapEndAddress;
      }


    // MASSIVE KLUDGE UNTIL WE GET PERRY'S FIXES FOR MALLOCED HEAP.
    //-#if RVM_FOR_AIX
      static final int mallocHeapStartAddress = 0x20000000;
      static final int mallocHeapEndAddress   = 0x30000000;
    //-#else
      static final int mallocHeapStartAddress = 0x08000000;
      static final int mallocHeapEndAddress   = 0x10000000;
    //-#endif

      static boolean referenceInMallocedStorage (int objptr) {
	  return addressInMallocedStorage(VM_ObjectModel.getPointerInMemoryRegion(objptr));
      }

      static boolean addressInMallocedStorage (int tref) {
	  return tref >= mallocHeapStartAddress && tref < mallocHeapEndAddress;
      }


      static int getByteArrayInstanceSize (int numelts) {
	  int bytes = byteArrayHeaderSize + numelts;
	  int round = (bytes + (WORDSIZE - 1)) & ~(WORDSIZE - 1);
	  return round;
      }

}
