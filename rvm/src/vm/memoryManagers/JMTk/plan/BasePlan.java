/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Statistics;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Offset;
import com.ibm.JikesRVM.VM_Extent;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;

/**
 * This abstract class implments the core functionality for all memory
 * management schemes.  All JMTk plans should inherit from this
 * class.<p>
 *
 * All plans make a clear distinction between <i>global</i> and
 * <i>thread-local</i> activities.  Global activities must be
 * synchronized, whereas no synchronization is required for
 * thread-local activities.  Instances of Plan map 1:1 to "kernel
 * threads" (aka CPUs or in Jikes RVM, VM_Processors).  Thus instance
 * methods allow fast, unsychronized access to Plan utilities such as
 * allocation and collection.  Each instance rests on static resources
 * (such as memory and virtual memory resources) which are "global"
 * and therefore "static" members of Plan.  This mapping of threads to
 * instances is crucial to understanding the correctness and
 * performance proprties of this plan.
 * 
 * @author Perry Cheng
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public abstract class BasePlan 
  implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  /****************************************************************************
   *
   * Class variables
   */
  public static final boolean NEEDS_WRITE_BARRIER = false;
  public static final boolean NEEDS_PUTSTATIC_WRITE_BARRIER = false;
  public static final boolean NEEDS_TIB_STORE_WRITE_BARRIER = false;
  public static final boolean REF_COUNT_CYCLE_DETECTION = false;
  public static final boolean REF_COUNT_SANITY_TRACING = false;
  public static final boolean SUPPORTS_PARALLEL_GC = true;
  public static final boolean MOVES_TIBS = false;
  public static final boolean STEAL_NURSERY_SCALAR_GC_HEADER = false;

  private static final int MAX_PLANS = 100;
  protected static Plan [] plans = new Plan[MAX_PLANS];
  protected static int planCount = 0;        // Number of plan instances in existence

  public static final int NOT_IN_GC = 0;   // this must be zero for C code
  public static final int GC_PREPARE = 1;  // before setup and obtaining root
  public static final int GC_PROPER = 2;

  // GC state and control variables
  protected static boolean initialized = false;
  protected static boolean awaitingCollection = false;
  protected static int collectionsInitiated = 0;
  private static int gcStatus = NOT_IN_GC; // shared variable
  protected static int exceptionReserve = 0;

  // Timing variables
  protected static long bootTime;

  // Meta data resources
  private static MonotoneVMResource metaDataVM;
  protected static MemoryResource metaDataMR;
  protected static RawPageAllocator metaDataRPA;
  public static MonotoneVMResource bootVM;
  public static MemoryResource bootMR;
  public static MonotoneVMResource immortalVM;
  protected static MemoryResource immortalMR;
  //-if RVM_WITH_GCSPY
  public static MonotoneVMResource gcspyVM;
  protected static MemoryResource gcspyMR;
  //-endif
  // 
  // Space constants
  private static final String[] spaceNames = new String[128];
  public static final byte UNUSED_SPACE = 127;
  public static final byte BOOT_SPACE = 126;
  public static final byte META_SPACE = 125;
  public static final byte IMMORTAL_SPACE = 124;
  //-if RVM_WITH_GCSPY
  // We might as well use immortal space for GCSpy stuff since there's a
  // boot-strapping problem: objects are allocated in immortal space before
  // GCspy is ready.
  public static final byte GCSPY_SPACE = IMMORTAL_SPACE;
  //-endif

  // Miscellaneous constants
  public static final int DEFAULT_POLL_FREQUENCY = (128<<10)>>LOG_BYTES_IN_PAGE;
  private static final int META_DATA_POLL_FREQUENCY = DEFAULT_POLL_FREQUENCY;
  protected static final int DEFAULT_LOS_SIZE_THRESHOLD = 16 * 1024;
  public    static final int NON_PARTICIPANT = 0;
  protected static final boolean GATHER_WRITE_BARRIER_STATS = false;

  protected static final int DEFAULT_MIN_NURSERY = (256*1024)>>LOG_BYTES_IN_PAGE;
  protected static final int DEFAULT_MAX_NURSERY = MAX_INT;

  // Memory layout constants
  protected static final VM_Extent     SEGMENT_SIZE = VM_Extent.fromIntZeroExtend(0x10000000);
  public    static final VM_Address      BOOT_START = VM_Interface.bootImageAddress;
  protected static final VM_Extent        BOOT_SIZE = SEGMENT_SIZE;
  protected static final VM_Address  IMMORTAL_START = BOOT_START.add(BOOT_SIZE);
  protected static final VM_Extent    IMMORTAL_SIZE = VM_Extent.fromIntZeroExtend(32 * 1024 * 1024);
  protected static final VM_Address    IMMORTAL_END = IMMORTAL_START.add(IMMORTAL_SIZE);
  protected static final VM_Address META_DATA_START = IMMORTAL_END;
  protected static final VM_Extent  META_DATA_SIZE  = VM_Extent.fromIntZeroExtend(32 * 1024 * 1024);
  protected static final VM_Address   META_DATA_END = META_DATA_START.add(META_DATA_SIZE);  
  protected static final VM_Address      PLAN_START = META_DATA_END;

  /****************************************************************************
   *
   * Instance variables
   */
  private int id = 0;                     // Zero-based id of plan instance
  public BumpPointer immortal;
  Log log;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time).  This is where key <i>global</i>
   * instances are allocated.  These instances will be incorporated
   * into the boot image by the build process.
   */
  static {
    metaDataMR = new MemoryResource("meta", META_DATA_POLL_FREQUENCY);
    metaDataVM = new MonotoneVMResource(META_SPACE, "Meta data", metaDataMR, META_DATA_START, META_DATA_SIZE, VMResource.META_DATA);
    metaDataRPA = new RawPageAllocator(metaDataVM, metaDataMR);

    bootMR = new MemoryResource("boot", META_DATA_POLL_FREQUENCY);
    bootVM = new ImmortalVMResource(BOOT_SPACE, "Boot", bootMR, BOOT_START, BOOT_SIZE);

    immortalMR = new MemoryResource("imm", DEFAULT_POLL_FREQUENCY);
    immortalVM = new ImmortalVMResource(IMMORTAL_SPACE, "Immortal", immortalMR, IMMORTAL_START, IMMORTAL_SIZE);

    addSpace(UNUSED_SPACE, "Unused");
    addSpace(BOOT_SPACE, "Boot");
    addSpace(META_SPACE, "Meta");
    addSpace(IMMORTAL_SPACE, "Immortal");
  }

  /**
   * Constructor
   */
  BasePlan() {
    id = planCount++;
    plans[id] = (Plan) this;
    immortal = new BumpPointer(immortalVM);
    log = new Log();
  }

  /**
   * The boot method is called early in the boot process before any
   * allocation.
   */
  public static void boot() throws VM_PragmaInterruptible {
    bootTime = VM_Interface.cycles();
  }

  /**
   * The boot method is called by the runtime immediately after
   * command-line arguments are available.  Note that allocation must
   * be supported prior to this point because the runtime
   * infrastructure may require allocation in order to parse the
   * command line arguments.  For this reason all plans should operate
   * gracefully on the default minimum heap size until the point that
   * boot is called.
   */
  public static void postBoot() {
    if (Options.verbose > 2) VMResource.showAll();
  }

  public static void fullyBooted() {
    initialized = true;
    exceptionReserve = (int) (getTotalPages() * (1 - VM_Interface.OUT_OF_MEMORY_THRESHOLD));
  }

  /****************************************************************************
   *
   * Allocation
   */

  protected byte getSpaceFromAllocator (Allocator a) {
    if (a == immortal) return IMMORTAL_SPACE;
    return UNUSED_SPACE;
  }

  static byte getSpaceFromAllocatorAnyPlan (Allocator a) {
    for (int i=0; i<plans.length; i++) {
      byte space = plans[i].getSpaceFromAllocator(a);
      if (space != UNUSED_SPACE)
        return space;
    }
    return UNUSED_SPACE;
  }

  protected Allocator getAllocatorFromSpace (byte s) {
    if (s == BOOT_SPACE) VM_Interface.sysFail("BasePlan.getAllocatorFromSpace given boot space");
    if (s == META_SPACE) VM_Interface.sysFail("BasePlan.getAllocatorFromSpace given meta space");
    if (s == IMMORTAL_SPACE) return immortal;
    VM_Interface.sysFail("BasePlan.getAllocatorFromSpace given unknown space");
    return null;
  }

  static Allocator getOwnAllocator (Allocator a) {
    byte space = getSpaceFromAllocatorAnyPlan(a);
    if (space == UNUSED_SPACE)
      VM_Interface.sysFail("BasePlan.getOwnAllocator could not obtain space");
    Plan plan = VM_Interface.getPlan();
    return plan.getAllocatorFromSpace(space);
  }

  /****************************************************************************
   *
   * Object processing and tracing
   */

  /**
   * Add a gray object
   *
   * @param obj The object to be enqueued
   */
  public static final void enqueue(VM_Address obj)
    throws VM_PragmaInline {
    VM_Interface.getPlan().values.push(obj);
  }

  /**
   * Add an unscanned, forwarded object for subseqent processing.
   * This mechanism is necessary for "pre-copying".
   *
   * @param obj The object to be enqueued
   */
  public static final void enqueueForwardedUnscannedObject(VM_Address obj)
    throws VM_PragmaInline {
    VM_Interface.getPlan().forwardedObjects.push(obj);
  }

  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.
   *
   * @param objLoc The location containing the object reference to be
   * traced.  The object reference is <i>NOT</i> an interior pointer.
   * @param root True if <code>objLoc</code> is within a root.
   */
  public static final void traceObjectLocation(VM_Address objLoc, boolean root)
    throws VM_PragmaInline {
    VM_Address obj = VM_Magic.getMemoryAddress(objLoc);
    VM_Address newObj = Plan.traceObject(obj, root);
    VM_Magic.setMemoryAddress(objLoc, newObj);
  }

  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.  This reference is presumed <i>not</i>
   * to be from a root.
   *
   * @param objLoc The location containing the object reference to be
   * traced.  The object reference is <i>NOT</i> an interior pointer.
   */
  public static final void traceObjectLocation(VM_Address objLoc)
    throws VM_PragmaInline {
    traceObjectLocation(objLoc, false);
  }

  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.
   *
   * @param obj The object reference to be traced.  This is <i>NOT</i> an
   * interior pointer.
   * @param interiorRef The interior reference inside obj that must be traced.
   * @param root True if the reference to <code>obj</code> was held in a root.
   * @return The possibly moved interior reference.
   */
  public static final VM_Address traceInteriorReference(VM_Address obj,
                                                        VM_Address interiorRef,
                                                        boolean root) {
    VM_Offset offset = interiorRef.diff(obj);
    VM_Address newObj = Plan.traceObject(obj, root);
    if (VM_Interface.VerifyAssertions) {
      if (offset.sLT(VM_Offset.zero()) || offset.sGT(VM_Offset.fromIntSignExtend(1<<24))) {  // There is probably no object this large
        Log.writeln("ERROR: Suspiciously large delta of interior pointer from object base");
        Log.write("       object base = "); Log.writeln(obj);
        Log.write("       interior reference = "); Log.writeln(interiorRef);
        Log.write("       delta = "); Log.writeln(offset);
        VM_Interface._assert(false);
      }
    }
    return newObj.add(offset);
  }

  /**
   * A pointer location has been enumerated by ScanObject.  This is
   * the callback method, allowing the plan to perform an action with
   * respect to that location.  By default nothing is done.
   *
   * @param location An address known to contain a pointer.  The
   * location is within the object being scanned by ScanObject.
   */
  public void enumeratePointerLocation(VM_Address location) {}

  // XXX Javadoc comment missing.
  public static boolean willNotMove(VM_Address obj) {
    return !VMResource.refIsMovable(obj);
  }

  /**
   * Forward the object referred to by a given address and update the
   * address if necessary.  This <i>does not</i> enqueue the referent
   * for processing; the referent must be explicitly enqueued if it is
   * to be processed.<p>
   *
   * <i>Non-copying collectors do nothing, copying collectors must
   * override this method.</i>
   *
   * @param location The location whose referent is to be forwarded if
   * necessary.  The location will be updated if the referent is
   * forwarded.
   */
  static void forwardObjectLocation(VM_Address location) {
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(!Plan.MOVES_OBJECTS);
  }

  /**
   * If the object in question has been forwarded, return its
   * forwarded value.<p>
   *
   * <i>Non-copying collectors do nothing, copying collectors must
   * override this method.</i>
   *
   * @param object The object which may have been forwarded.
   * @return The forwarded value for <code>object</code>.  <i>In this
   * case return <code>object</code>, copying collectors must override
   * this method.
   */
  static VM_Address getForwardedReference(VM_Address object) {
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(!Plan.MOVES_OBJECTS);
    return object;
  }

  /**
   * Make alive an object that was not otherwise known to be alive.
   * This is used by the ReferenceProcessor, for example.
   *
   * @param object The object which is to be made alive.
   */
  static void makeAlive(VM_Address object) {
    Plan.traceObject(object);
  }
 
  /**
   * An object is unreachable and is about to be added to the
   * finalizable queue.  The collector must ensure the object is not
   * collected (despite being otherwise unreachable), and should
   * return its forwarded address if keeping the object alive involves
   * forwarding.<p>
   *
   * <i>For many collectors these semantics relfect those of
   * <code>traceObject</code>, which is implemented here.  Other
   * collectors must override this method.</i>
   *
   * @param object The object which may have been forwarded.
   * @return The forwarded value for <code>object</code>.  <i>In this
   * case return <code>object</code>, copying collectors must override
   * this method.
   */
  static VM_Address retainFinalizable(VM_Address object) {
    return Plan.traceObject(object);
  }

  /**
   * Return true if an object is ready to move to the finalizable
   * queue, i.e. it has no regular references to it.  This method may
   * (and in some cases is) be overridden by subclasses.
   *
   * @param object The object being queried.
   * @return <code>true</code> if the object has no regular references
   * to it.
   */
  static boolean isFinalizable(VM_Address object) {
    return !Plan.isLive(object);
  }

  /****************************************************************************
   *
   * Read and write barriers.  By default do nothing, override if
   * appropriate.
   */

  /**
   * A new reference is about to be created by a putfield bytecode.
   * Take appropriate write barrier actions.<p>
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param src The address of the object containing the source of a
   * new reference.
   * @param offset The offset into the source object where the new
   * reference resides (the offset is in bytes and with respect to the
   * object address).
   * @param tgt The target of the new reference
   */
  public void putFieldWriteBarrier(VM_Address src, int offset,
                                   VM_Address tgt) {
    // Either: barriers are used and this is overridden, or 
    //         barriers are not used and this is never called
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(false);
  }

  /**
   * A new reference is about to be created by a aastore bytecode.
   * Take appropriate write barrier actions.<p>
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param src The address of the array containing the source of a
   * new reference.
   * @param index The index into the array where the new reference
   * resides (the index is the "natural" index into the array,
   * i.e. a[index]).
   * @param tgt The target of the new reference
   */
  public void arrayStoreWriteBarrier(VM_Address ref, int index, 
                                     VM_Address value) {
    // Either: write barriers are used and this is overridden, or 
    //         write barriers are not used and this is never called
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(false);
  }

  /**
   * A new reference is about to be created by a putStatic bytecode.
   * Take appropriate write barrier actions.<p>
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param slot The location into which the new reference will be
   * stored (the address of the static field being stored into).
   * @param tgt The target of the new reference
   */
  public final void putStaticWriteBarrier(VM_Address slot, VM_Address tgt) {
    // Either: write barriers are used and this is overridden, or 
    //         write barriers are not used and this is never called
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(false);
  }

  /**
   * A reference is about to be read by a getField bytecode.  Take
   * appropriate read barrier action.<p>
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param tgt The address of the object containing the pointer
   * about to be read
   * @param offset The offset from tgt of the field to be read from
   */
  public final void getFieldReadBarrier(VM_Address tgt, int offset) {
    // getfield barrier currently unimplemented
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(false);
  }

  /**
   * A reference is about to be read by a getStatic bytecode. Take
   * appropriate read barrier action.<p>
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param slot The location from which the reference will be read
   * (the address of the static field being read).
   */
  public final void getStaticReadBarrier(VM_Address slot) {
    // getstatic barrier currently unimplemented
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(false);
  }

  /****************************************************************************
   *
   * Space management
   */

  static public void addSpace (byte sp, String name) throws VM_PragmaInterruptible {
    if (spaceNames[sp] != null) VM_Interface.sysFail("addSpace called on already registed space");
    spaceNames[sp] = name;
  }

  static public String getSpaceName (byte sp) {
    if (spaceNames[sp] == null) VM_Interface.sysFail("getSpace called on unregisted space");
    return spaceNames[sp];
  }

  /**
   * Return the amount of <i>free memory</i>, in bytes (where free is
   * defined as not in use).  Note that this may overstate the amount
   * of <i>available memory</i>, which must account for unused memory
   * that is held in reserve for copying, and therefore unavailable
   * for allocation.
   *
   * @return The amount of <i>free memory</i>, in bytes (where free is
   * defined as not in use).
   */
  public static long freeMemory() throws VM_PragmaUninterruptible {
    return totalMemory() - usedMemory();
  }

  /**
   * Return the amount of <i>memory in use</i>, in bytes.  Note that
   * this excludes unused memory that is held in reserve for copying,
   * and therefore unavailable for allocation.
   *
   * @return The amount of <i>memory in use</i>, in bytes.
   */
  public static long usedMemory() throws VM_PragmaUninterruptible {
    return Conversions.pagesToBytes(Plan.getPagesUsed()).toLong();
  }


  /**
   * Return the amount of <i>memory in use</i>, in bytes.  Note that
   * this includes unused memory that is held in reserve for copying,
   * and therefore unavailable for allocation.
   *
   * @return The amount of <i>memory in use</i>, in bytes.
   */
  public static long reservedMemory() throws VM_PragmaUninterruptible {
    return Conversions.pagesToBytes(Plan.getPagesReserved()).toLong();
  }

  /**
   * Return the total amount of memory managed to the memory
   * management system, in bytes.
   *
   * @return The total amount of memory managed to the memory
   * management system, in bytes.
   */
  public static long totalMemory() throws VM_PragmaUninterruptible {
    return HeapGrowthManager.getCurrentHeapSize();
  }

  /**
   * Return the total amount of memory managed to the memory
   * management system, in pages.
   *
   * @return The total amount of memory managed to the memory
   * management system, in pages.
   */
  public static int getTotalPages() throws VM_PragmaUninterruptible { 
    return Conversions.bytesToPages((int) totalMemory()); 
  }

  /**
   * @return Whether last GC is a full GC.
   */
  public static boolean isLastGCFull () {
    return true;
  }

  /****************************************************************************
   *
   * Collection
   */

  /**
   * Check whether an asynchronous collection is pending.<p>
   *
   * This is decoupled from the poll() mechanism because the
   * triggering of asynchronous collections can trigger write
   * barriers, which can trigger an asynchronous collection.  Thus, if
   * the triggering were tightly coupled with the request to alloc()
   * within the write buffer code, then inifinite regress could
   * result.  There is no race condition in the following code since
   * there is no harm in triggering the collection more than once,
   * thus it is unsynchronized.
   */
  public static void checkForAsyncCollection() {
    if (awaitingCollection && VM_Interface.noThreadsInGC()) {
      awaitingCollection = false;
      VM_Interface.triggerAsyncCollection();
    }
  }

  /**
   * A collection has been initiated.  Increment the collectionInitiated
   * state variable appropriately.
   */
  public static void collectionInitiated() throws VM_PragmaUninterruptible {
    collectionsInitiated++;
  }

  /**
   * A collection has fully completed.  Decrement the collectionInitiated
   * state variable appropriately.
   */
  public static void collectionComplete() throws VM_PragmaUninterruptible {
    if (VM_Interface.VerifyAssertions) 
      VM_Interface._assert(collectionsInitiated > 0);
    collectionsInitiated--;
  }

  /**
   * Return true if a collection is in progress.
   *
   * @return True if a collection is in progress.
   */
  public static boolean gcInProgress() {
    return gcStatus != NOT_IN_GC;
  }

  /**
   * Return true if a collection is in progress and past the preparatory stage.
   *
   * @return True if a collection is in progress and past the preparatory stage.
   */
  public static boolean gcInProgressProper () {
    return gcStatus == GC_PROPER;
  }

  /**
   * Return true if a collection is in progress.
   *
   * @return True if a collection is in progress.
   */
  protected static void setGcStatus (int s) {
    VM_Magic.isync();
    gcStatus = s;
    VM_Magic.sync();
  }

  /**
   * A user-triggered GC has been initiated.  By default, do nothing,
   * but this may be overridden.
   */
  public static void userTriggeredGC() throws VM_PragmaUninterruptible {
  }

  /****************************************************************************
   *
   * Miscellaneous
   */

  /**
   * Generic hook to allow benchmarks to be harnessed.  A plan may use
   * this to perform certain actions prior to the commencement of a
   * benchmark, such as a full heap collection, turning on
   * instrumentation, etc.  By default do nothing.  Subclasses may
   * override.
   */
  public static void harnessBegin() {
  }

  /**
   * Generic hook to allow benchmarks to be harnessed.  A plan may use
   * this to perform certain actions after the completion of a
   * benchmark, such as a full heap collection, turning off
   * instrumentation, etc.  By default do nothing.  Subclasses may
   * override.
   */
  public static void harnessEnd() {
  }

  /**
   * This method should be called whenever an error is encountered.
   *
   * @param str A string describing the error condition.
   */
  public void error(String str) {
    MemoryResource.showUsage(PAGES);
    MemoryResource.showUsage(MB);
    VM_Interface.sysFail(str);
  }

  /**
   * Return the GC count (the count is incremented at the start of
   * each GC).
   *
   * @return The GC count (the count is incremented at the start of
   * each GC).
   */
  public static int gcCount() { 
    return Statistics.gcCount;
  }

  /**
   * Return the <code>RawPageAllocator</code> being used.
   *
   * @return The <code>RawPageAllocator</code> being used.
   */
  public static RawPageAllocator getMetaDataRPA() {
    return metaDataRPA;
  }

  /**
   * The VM is about to exit.  Perform any clean up operations.
   *
   * @param value The exit value
   */
  public void notifyExit(int value) {
    if (Options.verbose == 1) {
      Log.write("[End "); 
      Log.write(VM_Interface.cyclesToSecs(VM_Interface.cycles() - bootTime));
      Log.writeln(" s]");
    } else if (Options.verbose == 2) {
      Log.write("[End "); 
      Log.write(VM_Interface.cyclesToMillis(VM_Interface.cycles() - bootTime));
      Log.writeln(" ms]");
    }
    if (Options.verboseTiming) printDetailedTiming(true);
    planExit(value);
  }

  protected void printDetailedTiming(boolean totals) {}

  /**
   * The VM is about to exit.  Perform any plan-specific clean up
   * operations.
   *
   * @param value The exit value
   */
  protected void planExit(int value) {}


  /****************************************************************************
   *
   * Miscellaneous
   */

  final static int PAGES = 0;
  final static int MB = 1;
  final static int PAGES_MB = 2;
  final static int MB_PAGES = 3;

  /**
   * Print out the number of pages and or megabytes, depending on the mode.
   * A prefix string is outputted first.
   *
   * @param prefix A prefix string
   * @param pages The number of pages
   */
  public static void writePages(int pages, int mode) {
    double mb = Conversions.pagesToBytes(pages).toWord().rshl(20).toInt();
    switch (mode) {
      case PAGES: Log.write(pages); Log.write(" pgs"); break; 
      case MB:    Log.write(mb); Log.write(" Mb"); break;
      case PAGES_MB: Log.write(pages); Log.write(" pgs ("); Log.write(mb); Log.write(" Mb)"); break;
    case MB_PAGES: Log.write(mb); Log.write(" Mb ("); Log.write(pages); Log.write(" pgs)"); break;
      default: VM_Interface.sysFail("writePages passed illegal printing mode");
    }
  }

  /**
   * Print a failure message for the case where an object in an
   * unknown space is traced.
   *
   * @param obj The object being traced
   * @param space The space with which the object is associated
   * @param source Information about the source of the problem
   */
  protected static void spaceFailure(VM_Address obj, byte space, 
                                     String source) {
    VM_Address addr = VM_Interface.refToAddress(obj);
    Log.write(source);
    Log.write(": obj "); Log.write(obj);
    Log.write(" or addr "); Log.write(addr);
    Log.write(" of page "); Log.write(Conversions.addressToPagesDown(addr));
    Log.write(" is in unknown space ");
    Log.writeln(space);
    Log.write("Type = ");
    Log.write(VM_Interface.getTypeDescriptor(obj));
    Log.writeln();
    Log.write(source);
    VM_Interface.sysFail(": unknown space");
  }

  /**
   * Return the <code>Log</code> instance for this plan.
   *
   * @return the <code>Log</code> instance
   */
  Log getLog() {
    return log;
  }

    //-if RVM_WITH_GCSPY
  /**
   * Start the GCSpy server
   *
   * @param wait Whether to wait
   * @param port The port to talk to the GCSpy client (e.g. visualiser)
   */
  protected static void startGCSpyServer(int port, boolean wait) {}

  /**
   * Prepare GCSpy for a collection
   * Order of operations is guaranteed by StopTheWorld plan
   *	1. globalPrepare()
   *	2. threadLocalPrepare()
   *	3. gcspyPrepare()
   *	4. gcspyPreRelease()
   *	5. threadLocalRelease()
   *	6. gcspyRelease()
   *	7. globalRelease()
   *
   * Typically, zero gcspy's buffers
   */
  protected void gcspyPrepare() {}

  /**
   * Deal with root locations
   *
   */
  protected void gcspyRoots(AddressDeque rootLocations, AddressPairDeque interiorRootLocations) {}

  /**
   * Before thread-local release
   *
   */
  protected void gcspyPreRelease() {}

  /**
   * After thread-local release
   *
   */
  protected void gcspyPostRelease() {}  
  
  /**
   * After VMResource release
   * @param start the start of the released resource
   * @param bytes the number of bytes released
   */
  protected static void releaseVMResource(VM_Address start, VM_Extent bytes) {} 
  
  /**
   * After VMResource acquisition
   * @param start the start of the acquired resource
   * @param bytes the number of bytes acquired
   */
  protected static void acquireVMResource(VM_Address start, VM_Address end, VM_Extent bytes) {} 
  //-endif

}
