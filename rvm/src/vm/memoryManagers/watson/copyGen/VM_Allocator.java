/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Copying Generational Collector/Allocator.
 * <p>
 * Uses a writebarrier which puts references to objects, which had internal 
 * references modified, into processor local writebuffers.  For minor 
 * collections, objects in the writebuffers become part of the root set 
 * for the collection.  (The RVM compilers generate the barrier code when 
 * the static final constant "writeBarrier" is set to true.)
 * <p>
 * Divides the heap into 2 mature semi-spaces and a nursery.
 * The nursery can either be a fixed size, set on the command line by 
 * specifying "-X:nh=xxx" where xxx is the nursery size in megabytes, 
 * or a variable size (Appel-style).  
 * The nursery size is subtracted from the small object heap size (-X:h=xxx) 
 * and the remainder is divided into 2 equal sized mature semi-spaces.
 * <pre>
 * Small Object Heap Layout: 
 *  + ------------+ +---------------------+ +------------------------+ +---------+
 *  | BootImage   | | Mature Space 1      | | Mature Space 2         | | Nursery |
 *  +-------------+ +---------------------+ +------------------------+ +---------+
 *       
 * </pre>
 * At any point in time, one of the mature spaces is being used (is "current")
 * and the other is reserved.  Minor collections collect the Nursery.  Objects
 * in the Nursery reachable from the Roots are copied into the current mature
 * space.  If at the end of a Minor collection, the mature space pointer 
 * reaches some selected "delta" of the other mature space, then a Major 
 * collection is performed immediately.  Major collections collect the current
 * mature space.  Objects reachable from the Roots (not including the 
 * writebuffers) are copied to the other mature space, which then becomes the 
 * new "current" mature space.
 * <pre>
 * Space in mature and nursery spaces is allocated left to right.
 *
 * @see VM_WriteBuffer
 * @see VM_Barrier
 * @see VM_GCWorkQueue
 *
 * @author Janice Shepherd
 * @author Stephen Smith
 *
 * @modified by Perry Cheng  Heavily re-written to factor out common code and adding VM_Address
 */  
package MM;

import VM;
import VM_BootRecord;
import VM_Constants;
import VM_Address;
import VM_Magic;
import VM_ObjectModel;
import VM_JavaHeader;
import VM_ClassLoader;
import VM_SystemClassLoader;
import VM_Atom;
import VM_Type;
import VM_Class;
import VM_Array;
import VM_Method;
import VM_PragmaInline;
import VM_PragmaNoInline;
import VM_PragmaInterruptible;
import VM_PragmaUninterruptible;
import VM_PragmaLogicallyUninterruptible;
import VM_Processor;
import VM_ProcessorLocalState;
import VM_Scheduler;
import VM_Registers;
import VM_Thread;
import VM_Memory;
import VM_Time;
import VM_Entrypoints;
import VM_Reflection;
import VM_Synchronization;
import VM_Synchronizer;
import VM_EventLogger;
import VM_Callbacks;

public class VM_Allocator
  extends VM_GCStatistics
  implements VM_Constants, VM_GCConstants {

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
  
  /**
   * When true, causes time spent in each phase of collection to be measured.
   * Forces summary statistics to be generated. See VM_CollectorThread.TIME_GC_PHASES.
   */
  static final boolean TIME_GC_PHASES  = VM_CollectorThread.TIME_GC_PHASES;

  /** count times parallel GC threads attempt to mark the same object */
  private static final boolean COUNT_COLLISIONS = true;

  static final int LOCAL_MATURE_EXHAUSTED = -1;
  static final int MATURE_EXHAUSTED = -2;

  /**
   * Initialize for boot image.
   */
  static void init () throws VM_PragmaInterruptible {
    VM_CollectorThread.init();   // to alloc its rendezvous arrays, if necessary
  }
  
  /**
   * Initialize for execution.
   */
  static void boot (VM_BootRecord bootrecord) throws VM_PragmaInterruptible { 
    verbose = bootrecord.verboseGC;

    smallHeapSize = bootrecord.smallSpaceSize;
    int largeSize = bootrecord.largeSpaceSize;
    int immortalSize = VM_Memory.roundUpPage(1024 * 1024 + (4 * largeSize / VM_Memory.getPagesize()) + 4 * VM_Memory.getPagesize());
    int nurserySize = bootrecord.nurserySize; 
    int oneSpaceSize = VM_Memory.roundUpPage(smallHeapSize / 2);
   
    // set delta for triggering major gc to .25 of nursery size (this is a guess)
    if (nurserySize > 20*1024*1024) {
      MAJOR_GC_DELTA = nurserySize/4;   // .25 of nursery for large nursery
      // for measurement runs with LARGE nursery sizes, limit delta to fixed 10MB
      if (MAJOR_GC_DELTA > 10*1024*1024) MAJOR_GC_DELTA = 10*1024*1024;
    }
    else
      MAJOR_GC_DELTA = nurserySize/2;   // .50 of nursery for small nursery
    
    VM_Heap.boot(bootHeap, /* no malloc heap with this collector*/ null, bootrecord);
    immortalHeap.attach(immortalSize);
    largeHeap.attach(largeSize);
    if (variableNursery) {
	appelHeap.attach(smallHeapSize, nurseryHeap, fromHeap, toHeap);
    }
    else {
	appelHeap = null;
	nurseryHeap.attach(nurserySize);
	fromHeap.attach(oneSpaceSize);
	toHeap.attach(oneSpaceSize);
    }

    if (COMPILE_FOR_TIMING_RUN) {
	largeHeap.touchPages();
	toHeap.touchPages();
	fromHeap.touchPages();
	nurseryHeap.touchPages();
    }
    
    if (VM.ParanoidGCCheck)
	toHeap.protect();

    // initialize pointers used for allocation in the nursery
    VM_Processor st = VM_Processor.getCurrentProcessor();
    if (PROCESSOR_LOCAL_ALLOCATE)
	VM_Chunk.resetChunk1(st, nurseryHeap, false);
    if (PROCESSOR_LOCAL_MATURE_ALLOCATE)
	VM_Chunk.resetChunk2(st, fromHeap, false);
    
    // check for inconsistent heap & nursery sizes
    if (smallHeapSize < nurserySize) {
      VM.sysWrite("\nNursery size is too large for the specified Heap size:\n");
      VM.sysWrite("  Small Object Heap Size = ");
      VM.sysWrite(smallHeapSize,false); VM.sysWrite("\n");
      VM.sysWrite("  Nursery Size = ");
      VM.sysWrite(nurserySize,false); VM.sysWrite("\n");
      VM.sysWrite("Use -X:h=nnn & -X:nh=nnn to specify a heap size at least twice as big as the nursery\n");
      VM.sysWrite("Remember, the nursery is subtracted from the specified heap size\n");
      VM.shutdown(-5);
    }
    
    VM_GCUtil.boot();

    if (verbose >= 1) showParameter();
  }

  static void showParameter() throws VM_PragmaUninterruptible {

      if (variableNursery)
	  VM.sysWriteln("\nGenerational Copying Collector with Variable Sized Nursery");
      else
	  VM.sysWriteln("\nGenerational Copying Collector with Fixed Sized Nursery");
      bootHeap.show(); 
      immortalHeap.show();
      largeHeap.show();
      if (variableNursery) 
	  appelHeap.show();
      else {
	  nurseryHeap.show();
	  fromHeap.show(); 
	  toHeap.show();
      }
      VM.sysWrite("  DELTA for triggering major GC = "); VM.sysWrite(MAJOR_GC_DELTA / 1024); VM.sysWriteln(" Kb");

      if (VM.ParanoidGCCheck)              VM.sysWriteln("  Compiled with ParanoidGCCheck on ");      
      if (ZERO_CHUNKS_ON_ALLOCATION)       VM.sysWriteln("  Compiled with ZERO_CHUNKS_ON_ALLOCATION");
      if (PROCESSOR_LOCAL_ALLOCATE)        VM.sysWriteln("  Compiled with PROCESSOR_LOCAL_ALLOCATE on ");
      if (PROCESSOR_LOCAL_MATURE_ALLOCATE) VM.sysWriteln("  Compiled with PROCESSOR_LOCAL_MATURE_ALLOCATE on ");	  
  }

  /**
   * Force a garbage collection. Supports System.gc() called from
   * application programs.
   */
  public static void gc () throws VM_PragmaUninterruptible {

    forceMajorCollection = true;    // to force a major collection
    gc1("External Trigger ", 0);
  }
  
  /**
   * VM internal method to initiate a collection
   */
  static void gc1 (String why, int size) throws VM_PragmaUninterruptible {
    if (verbose >= 1)
      VM.sysWriteln("Garbage collection: ", why, size);
    // if here and in a GC thread doing GC then it is a system error,
    //  GC thread must have attempted to allocate.
    if ( VM_Thread.getCurrentThread().isGCThread ) {
      VM.sysFail("VM_Allocator: Garbage Collection Failure: GC Thread attempting to allocate during GC");
    }
    // notify GC threads to initiate collection, wait until done
    VM_CollectorThread.collect(VM_CollectorThread.collect);
  }  // gc1
  
  public static boolean gcInProgress() throws VM_PragmaUninterruptible {
    return gcInProgress;
  }

  /**
   * Get total amount of memory.  Includes both full size of the
   * small object heap and the size of the large object heap.
   *
   * @return the number of bytes
   */
  public static long totalMemory () throws VM_PragmaUninterruptible {
    return (smallHeapSize + largeHeap.size + nurseryHeap.size);
  }
  
  /**
   * Get the number of bytes currently available for object allocation.
   * In this collector, returns bytes available in the current semi-space.
   * (Does NOT include space available in large object space.)
   *
   * @return number of bytes available
   */
  public static long freeMemory () throws VM_PragmaUninterruptible {
    // remaining space in nursery & in current mature semi-space  ???????
    return (nurseryHeap.freeMemory() + fromHeap.freeMemory());
  }

  /*
   *  Includes freeMemory and per-processor local storage
   */
  public static long allSmallFreeMemory () throws VM_PragmaUninterruptible {
      return freeMemory() + VM_Chunk.freeMemoryChunk1();
  }

  public static long allSmallUsableMemory () throws VM_PragmaUninterruptible {
      return nurseryHeap.getSize() + fromHeap.getSize();
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
      Object newObj = VM_ObjectModel.initializeScalar(region, tib, size);
      return newObj;
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
    throws OutOfMemoryError, VM_PragmaInline, VM_PragmaUninterruptible {
    
    
    // note: array size might not be a word multiple,
    //       must preserve alignment of future allocations
    size = VM_Memory.align(size, WORDSIZE);

    if (size >= SMALL_SPACE_MAX) {
      return largeHeap.allocateArray(numElements, size, tib);
    } else {
      VM_Address region = allocateRawMemory(size);
      profileAlloc(region, size, tib);
      Object newObj = VM_ObjectModel.initializeArray(region, tib, numElements, size);
      return newObj;
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
      VM_Address addr = VM_Chunk.allocateChunk1(size);
      if (VM.VerifyAssertions && variableNursery) VM.assert(appelHeap.addrInHeap(addr));
      return addr;
    } else {
      return globalAllocateRawMemory(size);
    }
  }
  private static VM_Address globalAllocateRawMemory(int size) 
      throws VM_PragmaNoInline, VM_PragmaUninterruptible  {
    for (int count = 0; true; count++) {
      VM_Address addr = fromHeap.allocateZeroedMemory(size);
      if (!addr.isZero()) {
	if (ZERO_CHUNKS_ON_ALLOCATION) VM_Memory.zeroTemp(addr, size);
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
    if (heap == nurseryHeap) {
      if (count>3) VM_GCUtil.outOfMemory("nursery space", smallHeapSize, "-X:nh=nnn");
      gc1("GC triggered by object request of ", size);
    } else if (heap == largeHeap) {
      if (count>3) VM_GCUtil.outOfMemory("large object space", heap.getSize(), "-X:lh=nnn");
      forceMajorCollection = true;
      gc1("GC triggered by large object request of ", size);
    } else if (heap == toHeap || heap == fromHeap) {
      VM_GCUtil.outOfMemory("(mature) small object space", smallHeapSize, "-X:h=nnn");
    } else {
      VM.sysFail("unexpected heap");
    }
  }

  // *************************************
  // implementation
  // *************************************
  
  /** Declares that this collector may move objects during collction */
  static final boolean movesObjects = true;
  
  /** Declares that this collector requires that compilers generate the write barrier */
  static final boolean writeBarrier = true;
  
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
  

  static int nurserySize;    // Set at command line with "-X:nh=nnn" where nnn is in mega-bytes
  static int smallHeapSize;  // Equal to heap size (specified with "-X:h=nnn") less nursery size
  
  // MAJOR_GC_DELTA: if after a minor collection there is less than MAJOR_GC_DELTA left
  // in the mature space, then it is time to do a major collection.
  // Reset to a fraction of the Nursery size during startup 
  private static int MAJOR_GC_DELTA = 512*1024; 
  
  // forces major collection, even when not necessary
  private static boolean forceMajorCollection = false;
  
  private static boolean majorCollection = false;
  private static volatile boolean initGCDone = false;
  private static volatile boolean minorGCDone = false;
  private static volatile boolean majorGCDone = false;

  // Various heaps
  private static VM_BootHeap bootHeap = new VM_BootHeap();   
  private static VM_ContiguousHeap fromHeap = new VM_ContiguousHeap("Mature Small Object Heap 1");
  private static VM_ContiguousHeap toHeap   = new VM_ContiguousHeap("Mature Small Object Heap 2");
  private static VM_ContiguousHeap nurseryHeap = new VM_ContiguousHeap("Nursery Heap");
  public  static VM_ImmortalHeap immortalHeap = new VM_ImmortalHeap();
  private static VM_LargeHeap largeHeap = new VM_LargeHeap(immortalHeap);
  private static VM_AppelHeap appelHeap = new VM_AppelHeap("Appel-style container heap");
  //-#if RVM_WITH_VARIABLE_NURSERY
  private static boolean variableNursery = true;
  //-#else
  private static boolean variableNursery = false;
  //-#endif

  static boolean gcInProgress;      // true if collection in progress, initially false
  
  private static int    collisionCount = 0;      // counts attempts to mark same object
  
  static final boolean debugNative = false;             // temp - debugging JNI Native C
  static int verbose = 0;
  private static final boolean GCDEBUG = false;
  private static final boolean GCDEBUG_SCANTHREADS = false;
  private static final boolean TRACE_STACKS = false;
  private static final boolean GCDEBUG_CHECKWB = false;   // causes repeated checks of writebuffer
  
  // ------- End of Statics --------
  
  static void gcSetup ( int numSysThreads ) throws VM_PragmaUninterruptible {
    VM_GCWorkQueue.workQueue.initialSetup(numSysThreads);
  }
  
  private static void prepareNonParticipatingVPsForGC() throws VM_PragmaUninterruptible {
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

	// force exception if it comes back and tries to participate
	if (PROCESSOR_LOCAL_MATURE_ALLOCATE) 
	  VM_Chunk.resetChunk2(vp, null, false);

	// move the processors writebuffer entries into the executing collector
	// threads work buffers so the referenced objects will be scanned.
	VM_WriteBuffer.moveToWorkQueue(vp);
	}
    }
  
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
	  VM_Chunk.resetChunk1(vp, nurseryHeap, false);
      }
    }

    // nothing for native processors which cannot allocate

  }


  /**
   * Perform a garbage collection.  Called from VM_CollectorThread run
   * method by each collector thread participating in a collection.
   */
  static void collect () throws VM_PragmaUninterruptible {

    if (VM.BuildForEventLogging && VM.EventLoggingEnabled)
      VM_EventLogger.logGarbageCollectionEvent();
    
    // set running threads context regs so that a scan of its stack
    // will start at the caller of collect (ie. VM_CollectorThread.run)
    //
    VM_Address fp = VM_Magic.getFramePointer();
    VM_Address caller_ip = VM_Magic.getReturnAddress(fp);
    VM_Address caller_fp = VM_Magic.getCallerFramePointer(fp);
    VM_Thread.getCurrentThread().contextRegisters.setInnermost( caller_ip, caller_fp );

    // ASSUMPTIONS:
    // initGCDone flag is false before first GC thread enter collect
    // InitLock is reset before first GC thread enter collect
    //
    int       i,temp,bytes;
    boolean   selectedGCThread = false;  // indicates 1 thread to generate output
    
    if (verbose >= 1) VM_Scheduler.trace("VM_Allocator","starting minor GC");
    
    // BEGIN SINGLE GC THREAD SECTION - GC INITIALIZATION
    
    double tempStart = 0.0, tempEnd = 0.0;

    if ( VM_GCLocks.testAndSetInitLock() ) {
      
        startTime.start(VM_CollectorThread.gcBarrier.rendezvousStartTime); 
	initTime.start(startTime);
	minorGCTime.start(initTime.lastStart);
    
      
      if (VM.VerifyAssertions) VM.assert( initGCDone == false );  
      
      gcCount++;

      // setup common workqueue for num VPs participating, used to be called once.
      // now count varies for each GC, so call for each GC   SES 050201
      //
      VM_GCWorkQueue.workQueue.initialSetup(VM_CollectorThread.numCollectors());
      
      // VM_GCWorkQueue.workQueue.reset(); // do initialsetup instead 050201
      
      if (verbose >= 1) VM_Scheduler.trace("VM_Allocator", "initialization for gcCount", gcCount);
      
      gcInProgress = true;
      majorCollection = false;
      minorGCDone = false;
      majorGCDone = false;
      
      fromHeap.recordSaved();
      
      // Now prepare large space for collection - clears out mark array
      largeHeap.startCollect();
      if (variableNursery) appelHeap.minorStart();

      if (verbose >= 2 && variableNursery) appelHeap.show();

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
      
      rootTime.start(initTime);
      
      // set Done flag to allow other GC threads to begin processing
      initGCDone = true;
      
    } // END SINGLE GC THREAD SECTION - GC INITIALIZATION
    
    else {
      // Each GC thread must wait here until initialization is complete
      // this should be short, if necessary at all, so we spin instead of sysYiel
      //
      // It is NOT required that all GC threads reach here before any can proceed
      //
      tempStart = VM_CollectorThread.MEASURE_RENDEZVOUS_TIMES ? VM_Time.now() : 0.0;
      while( initGCDone == false ); // spin until initialization finished
      VM_Magic.isync();             // prevent following inst. from moving infront of waitloop
      tempEnd = VM_CollectorThread.MEASURE_RENDEZVOUS_TIMES ? VM_Time.now() : 0.0;

      // each gc thread copies own VM_Processor, resets processor register & processor
      // local allocation pointers & resets GC threads work queue buffers
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
    mylocal.rendezvousRecord(tempStart, tempEnd);

    // following rendezvous seems to be necessary, we are not sure why. Without it,
    // some processors proceed into finding roots, before all gc threads have
    // executed the above gc_initProcessor, and this seems related to the failure/
    // 
    mylocal.rendezvous();
         
    // Begin finding roots for this collection.
    // Roots are object refs in static variables (JTOC) or on thread stacks 
    // that point into FromSpace & references in the write buffers (they contain
    // references for old objects modified during the last mutator cycle).
    // For each unmarked root object, it is marked, copied to mature space if currently in
    // FromSpace, and added to GC thread local work queue for later scanning.
    
    // scan VM_Processor object, causing referenced objects to be copied.  When write buffers are
    // implemented as objects it is thus copied, and special code updates interior pointers 
    // (declared as ints) into the writebuffers.
    //
    gc_scanProcessor();  // each gc threads scans its own processor object

    VM_ScanStatics.scanStatics();     // GC threads scan JTOC in parallel
    
    VM_CopyingCollectorUtil.scanThreads(nurseryHeap);    // ALL GC threads process thread objects & scan their stacks
    
    // This synchronization is necessary to ensure all stacks have been scanned
    // and all internal save ip values have been updated before we scan copied
    // objects.  Because if we scan a VM_Method, and then update its code pointer
    // we can no longer compute old ip offsets for updating saved ip values
    //
    //    REQUIRED SYNCHRONIZATION - WAIT FOR ALL GC THREADS TO REACH HERE

    mylocal.rendezvous();
    
    // have processor 1 record timestame for end of scanning stacks & statics
    
    if (mylocal.gcOrdinal == 1) scanTime.start(rootTime);
    
    gc_processWriteBuffers();  // each GC thread processes its own writeBuffers
    
    gc_emptyWorkQueue();  // each GC thread processes its own work queue buffers

    // have processor 1 record timestame for end of scan/mark/copy phase
    if (mylocal.gcOrdinal == 1) scanTime.stop();
    
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

	finalizeTime.start();
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
      if (mylocal.gcOrdinal == 1) finalizeTime.stop();
    }  //  end of Finalization Processing

    // Each GC thread increments adds its wait times for this collection
    // into its total wait time - for printSummaryStatistics output
    //
    if (VM_CollectorThread.MEASURE_WAIT_TIMES)
      mylocal.incrementWaitTimeTotals();

    //
    // minorGCDone flag has been set to false earlier
    //
    if ( VM_GCLocks.testAndSetFinishLock() ) {

       finishTime.start();
      
      // BEGIN SINGLE GC THREAD SECTION - MINOR END
      
      // set ending times for preceeding finalization or scanning phase
      // do here where a sync (below) will push value to memory
      
      if (verbose >= 1) VM_Scheduler.trace("VM_Allocator", "finishing minor collection");
      if (verbose >= 2 && variableNursery) appelHeap.show();

      // If GC was initiated by an outside call to gc(), then forceMajorCollection was set
      // to cause us here to do a major collection.
      if (forceMajorCollection) {
	majorCollection = true;
	forceMajorCollection = false;   // must reset sometime before starting mutators
      }
      else {
	// if after a minor collection, the end of mature objects is too close to end of
	// current mature space, must do a major collection
	if ((variableNursery && (fromHeap.usedMemory() + MAJOR_GC_DELTA > appelHeap.size / 2)) ||
	    (!variableNursery && (fromHeap.freeMemory() < MAJOR_GC_DELTA)))
	    majorCollection = true;
      }

      if (majorCollection) {   // decided major collection necessary
	
	// must do major collection before starting mutators
	// Single GC thread running here does setup for a major collection
	// before letting other GC threads proceed.
	
	// NOTE: even when we have write barriers and remembered sets to use for minor
	// collections, this major collection requires a full scan of all live objects
	// starting from roots
	
	gcMajorCount++;
	
	if (verbose >= 1) VM_Scheduler.trace("VM_Allocator", "initialize for MAJOR collection",gcMajorCount);
	
	if (variableNursery) appelHeap.minorEnd();
	finishTime.stop();
	minorGCTime.stop();
	majorGCTime.start();
	bytes = fromHeap.allocatedFromSaved();
	updateGCStats(MINOR,bytes);
	printGCStats(MINOR);
	fromHeap.recordSaved();

	if (variableNursery) appelHeap.majorStart();

	initTime.start();	
	if (VM.ParanoidGCCheck)
	    toHeap.unprotect();

	if (verbose >= 1) VM_Scheduler.trace("VM_Allocator", "major collection - workQueue resetting", gcMajorCount);
	if (verbose >= 2 && variableNursery) appelHeap.show();

	VM_GCWorkQueue.workQueue.reset();  // setup shared common work queue -shared data
	
	// initialize the other heaps for collection
	bootHeap.startCollect();
	immortalHeap.startCollect();
	
	// re-initialize the large object space mark array
	if (verbose >= 1) VM_Scheduler.trace("VM_Allocator", "preparing large space",gcMajorCount);
	largeHeap.startCollect();
	
	// this gc thread copies own VM_Processor, resets processor register & processor
	// local allocation pointers (before copying first object to ToSpace)
	if (verbose >= 1) VM_Scheduler.trace("VM_Allocator", "copying own VM_Processor",gcMajorCount);
	gc_initProcessor();
	
	// precopy VM_Thread objects, updating schedulers threads array
	// here done by one thread. could divide among multiple collector threads
	if (verbose >= 1) VM_Scheduler.trace("VM_Allocator", "copying VM_Thread objs",gcMajorCount);
	gc_copyThreads();

	// reset locks so they can be used for synchronization during Major GC
	// ...except the lockword protecting this section, the "FinishLock"
	if (verbose >= 1) VM_Scheduler.trace("VM_Allocator", "resetting locks",gcMajorCount);
	VM_GCLocks.reset();
	
	rootTime.start(initTime);
	
      }  // End of setup for Major GC
      
      else {
	// Major GC not needed, GC DONE, reset allocation pointers etc 
	gc_finish();
	
	if (verbose >= 2 && variableNursery) appelHeap.show();

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
	// local allocation pointers & reset GC threads work queue buffers
	gc_initProcessor();
      }
    }
    
    // All GC THREADS IN PARALLEL
    if (PROCESSOR_LOCAL_ALLOCATE)
	VM_Chunk.resetChunk1(VM_Processor.getCurrentProcessor(), nurseryHeap, false);
    
    // if major GC not need, then finished, all GC threads return
    if ( !majorCollection ) {
      
      // generate -verbosegc output.
      // this is done by the 1 gc thread that finished the preceeding GC
      //
      if ( selectedGCThread ) {

	// get time spent in minor GC (including time to zero nursery, if done)
	finishTime.stop();
	minorGCTime.stop();
	bytes = fromHeap.allocatedFromSaved();
	updateGCStats(MINOR, bytes);
	printGCStats(MINOR);

      }  // end selectedThread
      
      // DONE: after Minor Collection: all gc threads return here
      return;
    }
    
    //
    // ALL GC THREADS START MAJOR GC
    //
    if (verbose >= 1) VM_Scheduler.trace("VM_Allocator", "starting parallel major GC",gcMajorCount);
    mylocal = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());

    if (VM_CollectorThread.MEASURE_WAIT_TIMES)
      mylocal.resetWaitTimers();         // reset for measuring major GC wait times

    // following rendezvous seems to be necessary, we are not sure why. Without it,
    // some processors proceed into finding roots, before all gc threads have
    // executed the above gc_initProcessor, and this seems related to the failure/
    // 
    mylocal.rendezvous();
         
    if (verbose >= 1) VM_Scheduler.trace("VM_Allocator", "starting major collection", gcMajorCount);
    gc_scanProcessor();  // each gc threads scans its own processor object
    
    if (verbose >= 1) VM_Scheduler.trace("VM_Allocator", "scanning statics", gcMajorCount);
    VM_ScanStatics.scanStatics();     // GC threads scan JTOC in parallel
    
    if (verbose >= 1) VM_Scheduler.trace("VM_Allocator", "scanning threads", gcMajorCount);
    VM_CopyingCollectorUtil.scanThreads(nurseryHeap);    // ALL GC threads process thread objects & scan their stacks
    
    // This synchronization is necessary to ensure all stacks have been scanned
    // and all internal save ip values have been updated before we scan copied
    // objects.  Because if we scan a VM_Method, and then update its code pointer
    // we can no longer compute old ip offsets for updating saved ip values
    //
    //    REQUIRED SYNCHRONIZATION - WAIT FOR ALL GC THREADS TO REACH HERE

    mylocal.rendezvous();
    
    // have processor 1 record timestame for end of scanning stacks & statics
    if (mylocal.gcOrdinal == 1) scanTime.start(rootTime);
    
    if (verbose >= 1) VM_Scheduler.trace("VM_Allocator", "emptying work queue", gcMajorCount);
    gc_emptyWorkQueue();  // each GC thread processes its own work queue buffers
    
    // have processor 1 record timestame for end of scan/mark/copy phase
    if (mylocal.gcOrdinal == 1) scanTime.stop();
    
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

      if (mylocal.gcOrdinal == 1) {

	finalizeTime.start();
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
    if (mylocal.gcOrdinal == 1) finalizeTime.stop();
    }  //  end of Finalization Processing

    if (mylocal.gcOrdinal == 1) 
    // Each GC thread increments adds its wait times for this collection
    // into its total wait time - for printSummaryStatistics output
    //
    if (VM_CollectorThread.MEASURE_WAIT_TIMES)
      mylocal.incrementWaitTimeTotals();

    // majorGCDone flag has been set to false earlier
    
    if ( VM_GCLocks.testAndSetFinishMajorLock() ) {
      
      // set ending times for preceeding finalization phase
      // do here where a sync (below) will push value to memory

      finishTime.start();
      gc_finish();  // reset heap allocation area, reset GC locks, maybe zero nursery, etc

      if (verbose >= 1) VM_Scheduler.trace("VM_Allocator", "finished major collection");
      if (verbose >= 2 && variableNursery) appelHeap.show();
      
      bytes = fromHeap.current().diff(fromHeap.start);
      updateGCStats(MAJOR, bytes);

      selectedGCThread = true;  // have this thread generate verbose output below,
      // after nursery has been zeroed

      finishTime.stop();
      majorGCTime.stop();
      
      VM_Magic.sync();
      
      majorGCDone = true;  // lets spinning GC threads continue
      
    }  // END OF SINGLE THREAD SECTION
    
    else {
      while( majorGCDone == false );   // losing threads spin till above section finished
      VM_Magic.isync();                // prevent following inst. from moving infront of waitloop
    }
    
    // ALL GC THREADS IN PARALLEL - AFTER MAJOR COLLECTION
    
    if (PROCESSOR_LOCAL_ALLOCATE)
	VM_Chunk.resetChunk1(VM_Processor.getCurrentProcessor(), nurseryHeap, false);

    // generate -verbosegc output, done here after (possibly) zeroing nursery. 
    // this is done by the 1 gc thread that finished the preceeding GC
    //
    if ( selectedGCThread ) 
      printGCStats(MAJOR);
    
    // following checkwritebuffer call is necessary to remove inadvertent entries
    // that are recorded during major GC, and which would crash the next minor GC
    //
    gc_checkWriteBuffers();
    
    // all GC threads return, having completed Major collection
    return;
  }  // collect
  

  // Reset shared heap pointers, large space allocation arrays.
  // Executed by 1 Collector thread at the end of collection.
  //
  static void gc_finish () {
    
    // for this collector "zapFromSpace" means zap the nursery and protect a mature space
    if (VM.ParanoidGCCheck) {
	nurseryHeap.clobber();
	fromHeap.paranoidScan(nurseryHeap, false);
	largeHeap.paranoidScan(nurseryHeap, false);
	if (majorCollection) 
	    toHeap.paranoidScan(nurseryHeap, false);
	if (majorCollection) 
	    fromHeap.protect();
    }
    if ( ! ZERO_CHUNKS_ON_ALLOCATION ) {
	// let the one processor executing gc_finish zero the nursery
	VM_Memory.zeroPages( nurseryHeap.start, nurseryHeap.size );
    }
    else {
	// if ZERO_BLOCKS_ON_ALLOCATION is on (others OFF!) then there
	// is no zeroing of nursery during gc, each block is zeroed by the
	// processor that allocates it...and we must be doing processor
	// local allocates
	if (VM.VerifyAssertions) VM.assert(PROCESSOR_LOCAL_ALLOCATE == true);
    }
    
    if (majorCollection) {
	largeHeap.endCollect();
	if (variableNursery) 
	    appelHeap.majorEnd();
	else {
	    fromHeap.reset();
	    VM_ContiguousHeap temp = fromHeap;  // swap spaces
	    fromHeap = toHeap;
	    toHeap = temp;
	}
    }
    else {
	if (variableNursery) appelHeap.minorEnd();
    }

    nurseryHeap.reset();
    
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
  static VM_Address gc_getMatureSpace ( int size ) throws VM_PragmaUninterruptible {
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
  private static VM_Address copyAndScanObject(VM_Address fromRef, boolean scan) throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) VM.assert(validFromRef(fromRef));
    return VM_CopyingCollectorUtil.copyAndScanObject(fromRef, scan);
  }

  // turn on the barrier bit
   static void resetObjectBarrier(Object objRef) {
    VM_ObjectModel.initializeAvailableByte(objRef); // make it safe for write barrier to change bit non-atomically
    VM_AllocatorHeader.setBarrierBit(objRef);
   }


  // Process writeBuffer attached to the executing processor. Executed by
  // each collector thread during Minor Collections.
  //
  static void gc_processWriteBuffers () throws VM_PragmaUninterruptible {
    VM_WriteBuffer.processWriteBuffer(VM_Processor.getCurrentProcessor());
  }
  
  // For Debugging - Checks that writeBuffer attached to the running GC threads
  // current processor is empty, if not print diagnostics & reset
  //
  static void gc_checkWriteBuffers () throws VM_PragmaUninterruptible {
    VM_WriteBuffer.checkForEmpty(VM_Processor.getCurrentProcessor());
  }
  

  // called by ONE gc/collector thread to copy and "new" thread objects
  // copies but does NOT enqueue for scanning
  //
  static void gc_copyThreads ()  throws VM_PragmaUninterruptible {
    for (int i=0; i<VM_Scheduler.threads.length; i++ ) {
      Object t = VM_Scheduler.threads[i];
      if ( t == null ) continue;
      VM_Address ta = VM_Magic.objectAsAddress(t);
      if ( nurseryHeap.refInHeap(ta) ||
	   (fromHeap.refInHeap(ta) && majorCollection)) {
	ta = copyAndScanObject(ta, false);
	t = VM_Magic.addressAsObject(ta);
	// change entry in threads array to point to new copy of thread
	VM_Magic.setObjectAtOffset(VM_Scheduler.threads, i*4, t);
      }
    }
  }

  // initProcessor is called by each GC thread to copy the processor object of the
  // processor it is running on, and reset it processor register, and update its
  // entry in the scheduler processors array and reset its local allocation pointers
  //
  static void gc_initProcessor ()  throws VM_PragmaUninterruptible {

    VM_Processor st = VM_Processor.getCurrentProcessor();
    VM_Address   sta = VM_Magic.objectAsAddress(st);
    VM_Thread    activeThread = st.activeThread;
    int          tid = activeThread.getIndex();
    
    if (VM.VerifyAssertions) VM.assert(tid == VM_Thread.getCurrentThread().getIndex());
    
    // if compiled for processor local chunking of "mature space" reset processor local 
    // pointers, to cause first request to get a block (only reset on major collection
    // for minor collection, continue filling last/current mature buffer
    //
    if (PROCESSOR_LOCAL_MATURE_ALLOCATE) {
      // no allocation during GC
      VM_Chunk.resetChunk1(st, null, false);
      if (majorCollection) 
	  VM_Chunk.resetChunk2(st, toHeap, false);
      else
	  VM_Chunk.resetChunk2(st, fromHeap, false);
    }
    
    // Cannot use procesPtrField here since work buffers not available yet
    if ( nurseryHeap.refInHeap(sta) ||
	 (fromHeap.refInHeap(sta) && majorCollection) ) {
	sta = copyAndScanObject(sta, false);
	// change entry in system threads array to point to copied sys thread
	VM_Magic.setMemoryAddress( VM_Magic.objectAsAddress(VM_Scheduler.processors).add(st.id*4), sta);
	// should have Magic to recast addressAsProcessor, instead 
	// reload st from just modified array entry
	st = VM_Scheduler.processors[st.id];
    }


    // each gc thread updates its PROCESSOR_REGISTER after copying its VM_Processor object
    VM_Magic.setProcessorRegister(st);
    
    if (PROCESSOR_LOCAL_ALLOCATE) {
      // reset local heap pointers .. causes first mutator allocate to
      // get a new local Chunk from the shared heap
      //
      VM_Chunk.resetChunk1(st, nurseryHeap, false);
    }
    
    // if Processors activethread (should be current, gc, thread) is in fromHeap, copy and
    // update activeThread field and threads array entry to make sure BOTH ways of computing
    // getCurrentThread return the new copy of the thread
    VM_Address ata = VM_Magic.objectAsAddress(activeThread);
    if ( nurseryHeap.refInHeap(ata) ||
	 (fromHeap.refInHeap(ata) && majorCollection) ) {
      // copy thread object, do not queue for scanning
      ata = copyAndScanObject(ata, false);
      st.activeThread = VM_Magic.objectAsThread(VM_Magic.addressAsObject(ata));
      // change entry in system threads array to point to copied sys thread
      VM_Magic.setMemoryAddress( VM_Magic.objectAsAddress(VM_Scheduler.threads).add(tid*4), ata);
    }
    
    // setup the work queue buffers for this gc thread
    VM_GCWorkQueue.resetWorkQBuffers();
    
  } // gc_initProcessor
  
  // scan a VM_Processor object to force "interior" objects to be copied, marked,
  // and queued for later scanning. adjusts write barrier pointers, if
  // write buffer is moved.
  //
  static void gc_scanProcessor () throws VM_PragmaUninterruptible  {

    VM_Processor st = VM_Processor.getCurrentProcessor();
    VM_Address sta = VM_Magic.objectAsAddress(st);
    VM_Address oldbuffer, newbuffer;
    
    if (PROCESSOR_LOCAL_ALLOCATE) {
      // local heap pointer set in initProcessor, should still be 0, ie no allocates yet
      if (VM.VerifyAssertions) VM.assert(VM_Chunk.unusedChunk1(st));
    }
    
    if (VM.VerifyAssertions) {
      // processor should already be copied, ie NOT in FromSpace
      VM.assert(!nurseryHeap.refInHeap(sta));
      // and its processor array entry updated
      VM.assert(sta.EQ(VM_Magic.objectAsAddress(VM_Scheduler.processors[st.id])));
    }
    
    oldbuffer = VM_Magic.objectAsAddress(st.modifiedOldObjects);
    VM_ScanObject.scanObjectOrArray(sta);
    // if writebuffer moved, adjust interior pointers
    newbuffer = VM_Magic.objectAsAddress(st.modifiedOldObjects);
    if (oldbuffer.NE(newbuffer)) {
      st.modifiedOldObjectsMax = newbuffer.add(st.modifiedOldObjectsMax.diff(oldbuffer));
      st.modifiedOldObjectsTop = newbuffer.add(st.modifiedOldObjectsTop.diff(oldbuffer));
    }
  }  // scanProcessor

  
  /**
   * Process references in work queue buffers until empty.
   */
  static void gc_emptyWorkQueue() throws VM_PragmaUninterruptible {

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

  
    

  static boolean validFromRef ( VM_Address ref ) throws VM_PragmaUninterruptible {
    return ( nurseryHeap.refInHeap(ref) ||
	     (fromHeap.refInHeap(ref) && majorCollection));
  }

  static boolean validForwardingPtr ( VM_Address ref ) throws VM_PragmaUninterruptible {
    if ( majorCollection ) 
	return toHeap.refInHeap(ref);
    else
	return fromHeap.refInHeap(ref);
  }

  
  static boolean validWorkQueuePtr ( VM_Address ref ) throws VM_PragmaUninterruptible {
    if ( bootHeap.refInHeap(ref) ) return true;
    if ( immortalHeap.refInHeap(ref) ) return true;
    if ( largeHeap.refInHeap(ref) ) return true;
    return validForwardingPtr( ref );
  }
  
  
  // Called from VM_Processor constructor: 
  // Must alloc & initialize write buffer
  // allocation chunk associated with nursery
  static void setupProcessor (VM_Processor p) throws VM_PragmaInterruptible {
    VM_WriteBuffer.setupProcessor(p);
    if (PROCESSOR_LOCAL_ALLOCATE) 
      VM_Chunk.resetChunk1(p, nurseryHeap, false);
  }
  
  // following referenced by refcountGC methods (but not called)
  static void gc_scanStacks () throws VM_PragmaUninterruptible { VM.assert(false); }
  
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
  static boolean processFinalizerListElement (VM_FinalizerListElement le) throws VM_PragmaUninterruptible {

    VM_Address ref = le.value;

    if (nurseryHeap.refInHeap(ref) ||
	fromHeap.refInHeap(ref)) {
	if (fromHeap.refInHeap(ref) && !majorCollection) 
	    return true;
	Object objRef = VM_Magic.addressAsObject(ref);
	if (VM_AllocatorHeader.isForwarded(objRef)) {  // is live?
	    le.move(VM_Magic.objectAsAddress(VM_AllocatorHeader.getForwardingPointer(objRef)));
	    return true;
	}
	else {
	    le.finalize(copyAndScanObject(ref, true));  
	    return false;
	}
    }

    if (largeHeap.refInHeap(ref)) {
	if (!majorCollection)       // in a minor gc, we must assume it is still live
	    return true;
	if (largeHeap.isLive(ref)) // in a major gc, might still be live
	    return true;
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
  static void processWriteBufferEntry (VM_Address ref) throws VM_PragmaUninterruptible {
    VM_ScanObject.scanObjectOrArray(ref);
  }
  
  /**
   * Process an object reference field during collection.
   *
   * @param location  address of a reference field
   */
  public static void processPtrField ( VM_Address location ) throws VM_PragmaUninterruptible {
    VM_Magic.setMemoryAddress(location, processPtrValue(VM_Magic.getMemoryAddress(location)));

  } // processPtrField

  /**
   * Process an object reference (value) during collection.
   *
   * @param location  address of a reference field
   */
  static VM_Address processPtrValue ( VM_Address ref ) throws VM_PragmaUninterruptible {
    
    if (ref.isZero()) return ref;
    
    // always process objects in the Nursery (forward if not already forwarded)
    if (nurseryHeap.refInHeap(ref)) 
      return copyAndScanObject(ref, true);  // return new reference

    // fromspace objects processed only on major GC
    if (fromHeap.refInHeap(ref)) {
      if (!majorCollection) return ref;
      return copyAndScanObject(ref, true);  // return new reference
    }

    // bootHeap objects processed only on major GC
    if (bootHeap.refInHeap(ref)) {
      if (majorCollection && bootHeap.mark(ref)) VM_GCWorkQueue.putToWorkBuffer(ref);
      return ref;
    }

    // immortalHeap objects processed only on major GC
    if (immortalHeap.refInHeap(ref)) {
      if (majorCollection && immortalHeap.mark(ref)) VM_GCWorkQueue.putToWorkBuffer(ref);
      return ref;
    }

    // large objects processed only on major GC
    if (largeHeap.refInHeap(ref)) {
      if (majorCollection && largeHeap.mark(ref)) VM_GCWorkQueue.putToWorkBuffer(ref);
      return ref;
    }

    if (toHeap.refInHeap (ref)) return ref;

    VM.sysWriteln("processPtrValue encountered bad reference = ", ref);
    VM.assert(false);
    return null;
  }


} 
