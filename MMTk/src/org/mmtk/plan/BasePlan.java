/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.plan;

import org.mmtk.policy.ImmortalSpace;
import org.mmtk.utility.AddressDeque;
import org.mmtk.utility.AddressPairDeque;
import org.mmtk.utility.Allocator;
import org.mmtk.utility.BumpPointer;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.HeapGrowthManager;
import org.mmtk.utility.ImmortalVMResource;
import org.mmtk.utility.Log;
import org.mmtk.utility.MemoryResource;
import org.mmtk.utility.MonotoneVMResource;
import org.mmtk.utility.Options;
import org.mmtk.utility.RawPageAllocator;
import org.mmtk.utility.statistics.*;
import org.mmtk.utility.TraceGenerator;
import org.mmtk.utility.VMResource;
import org.mmtk.vm.VM_Interface;
import org.mmtk.vm.Constants;

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
  public static final boolean STEAL_NURSERY_GC_HEADER = false;
  public static final boolean GENERATE_GC_TRACE = false;

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
  protected static boolean insideHarness = false;

  // Meta data resources
  private static MonotoneVMResource metaDataVM;
  protected static MemoryResource metaDataMR;
  protected static RawPageAllocator metaDataRPA;
  public static MonotoneVMResource bootVM;
  public static MemoryResource bootMR;
  public static MonotoneVMResource immortalVM;
  protected static MemoryResource immortalMR;
  public static MonotoneVMResource gcspyVM;
  protected static MemoryResource gcspyMR;
  // 
  // Space constants
  private static final String[] spaceNames = new String[128];
  public static final byte UNUSED_SPACE = 127;
  public static final byte BOOT_SPACE = 126;
  public static final byte META_SPACE = 125;
  public static final byte IMMORTAL_SPACE = 124;
  public static final byte GCSPY_SPACE = IMMORTAL_SPACE;
  public static final byte LOS_SPACE = 123;

  // Statistics
  public static Timer totalTime;
  public static SizeCounter mark;
  public static SizeCounter cons;

  // Miscellaneous constants
  public static final int DEFAULT_POLL_FREQUENCY = (128<<10)>>LOG_BYTES_IN_PAGE;
  protected static final int META_DATA_POLL_FREQUENCY = DEFAULT_POLL_FREQUENCY;
  protected static final int LOS_SIZE_THRESHOLD = 8 * 1024;
  public    static final int NON_PARTICIPANT = 0;
  protected static final boolean GATHER_WRITE_BARRIER_STATS = false;
  public static final boolean GATHER_MARK_CONS_STATS = false;

  public static final int DEFAULT_MIN_NURSERY = (256*1024)>>LOG_BYTES_IN_PAGE;
  public static final int DEFAULT_MAX_NURSERY = MAX_INT;

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

    totalTime = new Timer("time");
    if (GATHER_MARK_CONS_STATS) {
      mark = new SizeCounter("mark", true, true);
      cons = new SizeCounter("cons", true, true);
    }
    
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
    if (Plan.GENERATE_GC_TRACE)
      TraceGenerator.boot(BOOT_START);
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
    if (Options.verbose > 0) Stats.startAll();
  }

  public static void fullyBooted() {
    initialized = true;
    exceptionReserve = (int) (getTotalPages() * (1 - VM_Interface.OUT_OF_MEMORY_THRESHOLD));
  }

  /****************************************************************************
   *
   * Allocation
   */

  /**
   * Run-time check of the allocator to use for a given allocation
   * 
   * At the moment this method assumes that allocators will use the simple 
   * (worst) method of aligning to determine if the object is a large object
   * to ensure that no objects are larger than other allocators can handle. 
   * 
   * @param bytes The number of bytes to be allocated
   * @param align The requested alignment.
   * @param allocator The allocator statically assigned to this allocation
   * @return The allocator dyncamically assigned to this allocation
   */
  public static int checkAllocator(int bytes, int align, int allocator) 
    throws VM_PragmaInline {
    if (allocator == Plan.DEFAULT_SPACE && 
        Allocator.getMaximumAlignedSize(bytes, align) > LOS_SIZE_THRESHOLD)
      return LOS_SPACE;
    else 
      return allocator;
  }

  protected byte getSpaceFromAllocator(Allocator a) {
    if (a == immortal) return IMMORTAL_SPACE;
    return UNUSED_SPACE;
  }

  public static byte getSpaceFromAllocatorAnyPlan(Allocator a) {
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

  public static Allocator getOwnAllocator (Allocator a) {
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
  public static void forwardObjectLocation(VM_Address location) {
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
  public static VM_Address getForwardedReference(VM_Address object) {
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
  public static void makeAlive(VM_Address object) {
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
  public static VM_Address retainFinalizable(VM_Address object) {
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
  public static boolean isFinalizable(VM_Address object) {
    return !Plan.isLive(object);
  }

  /****************************************************************************
   *
   * Read and write barriers.  By default do nothing, override if
   * appropriate.
   */

  /**
   * A new reference is about to be created. Take appropriate write
   * barrier actions.<p> 
   *
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be
   * stored.
   * @param tgt The target of the new reference
   * @param metaDataA An int that assists the host VM in creating a store 
   * @param metaDataB An int that assists the host VM in creating a store 
   * @param mode The context in which the store occured
   */
  public void writeBarrier(VM_Address src, VM_Address slot,
                           VM_Address tgt, int metaDataA, int metaDataB, int mode) {
    // Either: write barriers are used and this is overridden, or 
    //         write barriers are not used and this is never called
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(false);
  }

  /**
   * A number of references are about to be copied from object
   * <code>src</code> to object <code>dst</code> (as in an array
   * copy).  Thus, <code>dst</code> is the mutated object.  Take
   * appropriate write barrier actions.<p>
   *
   * @param src The source of the values to be copied
   * @param srcOffset The offset of the first source address, in
   * bytes, relative to <code>src</code> (in principle, this could be
   * negative).
   * @param dst The mutated object, i.e. the destination of the copy.
   * @param dstOffset The offset of the first destination address, in
   * bytes relative to <code>tgt</code> (in principle, this could be
   * negative).
   * @param bytes The size of the region being copied, in bytes.
   * @return True if the update was performed by the barrier, false if
   * left to the caller (always false in this case).
   */
  public boolean writeBarrier(VM_Address src, int srcOffset,
			      VM_Address dst, int dstOffset,
			      int bytes) {
    // Either: write barriers are used and this is overridden, or 
    //         write barriers are not used and this is never called
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(false);
    return false;
  }

  /**
   * Read a reference. Take appropriate read barrier action, and
   * return the value that was read.<p> This is a <b>substituting<b>
   * barrier.  The call to this barrier takes the place of a load.<p>
   *
   * @param src The object being read.
   * @param src The address being read.
   * @param context The context in which the read arose (getfield, for example)
   * @return The reference that was read.
   */
  public final VM_Address readBarrier(VM_Address src, VM_Address slot,
                                      int context)
    throws VM_PragmaInline {
    // read barrier currently unimplemented
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(false);
    return VM_Address.max();
  }

  /****************************************************************************
   *
   * GC trace generation support methods
   */

  /**
   * Return true if <code>obj</code> is in a space known to the class and
   * is reachable.
   *  
   * <i> For this method to be accurate, collectors must override this method
   * to define results for the spaces they create.</i>
   *
   * @param obj The object in question
   * @return True if <code>obj</code> is a reachable object in a space known by
   *         the class; unreachable objects may still be live, however.  False 
   *         will be returned if it cannot be determined if the object is 
   *         reachable (e.g., resides in a space unknown to the class).
   */
  public boolean isReachable(VM_Address obj) {
    if (obj.isZero()) return false;
    VM_Address addr = VM_Interface.refToAddress(obj);
    byte space = VMResource.getSpace(addr);
    switch (space) {
    case IMMORTAL_SPACE:  return ImmortalSpace.isReachable(obj);
    case BOOT_SPACE:      return ImmortalSpace.isReachable(obj);
    default:
      if (VM_Interface.VerifyAssertions) {
	VM_Interface.sysFail("BasePlan.isReachable given object from unknown space");
      }
      return false;
    }
  }

  /**
   * Follow a reference during GC.  This involves determining which
   * collection policy applies and getting the final location of the object
   *
   * <i> For this method to be accurate, collectors must override this method
   * to define results for the spaces they create.</i>   
   *
   * @param obj The object reference to be followed.  This is <i>NOT</i> an
   * interior pointer.
   * @return The possibly moved reference.
   */
  public static VM_Address followObject(VM_Address obj) {
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(!Plan.MOVES_OBJECTS);
    return VM_Address.zero();
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
    // FIXME The following will probably break async GC.  A better fix
    // is needed
    collectionsInitiated = 0;
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
  public static void harnessBegin() throws VM_PragmaInterruptible {
    Options.fullHeapSystemGC = true;
    System.gc();
    Options.fullHeapSystemGC = false;
 
    insideHarness = true;
    Stats.startAll();
  }

  /**
   * Generic hook to allow benchmarks to be harnessed.  A plan may use
   * this to perform certain actions after the completion of a
   * benchmark, such as a full heap collection, turning off
   * instrumentation, etc.  By default do nothing.  Subclasses may
   * override.
   */
  public static void harnessEnd() {
    Stats.stopAll();
    Stats.printStats();
    insideHarness = false;
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
    return Stats.gcCount();
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
      totalTime.printTotalSecs();
      Log.writeln(" s]");
    } else if (Options.verbose == 2) {
      Log.write("[End ");
      totalTime.printTotalMillis();
      Log.writeln(" ms]");
    }
    if (Options.verboseTiming) printDetailedTiming(true);
    planExit(value);
    if (Plan.GENERATE_GC_TRACE)
      TraceGenerator.notifyExit(value);
  }

  protected void printDetailedTiming(boolean totals) {}

  /**
   * The VM is about to exit.  Perform any plan-specific clean up
   * operations.
   *
   * @param value The exit value
   */
  protected void planExit(int value) {}

  /**
   * Specify if the plan has been fully initialized
   *
   * @return True if the plan has been initialized
   */
  public static boolean initialized() {
    return initialized;
  }

  /****************************************************************************
   *
   * Miscellaneous
   */

  final static int PAGES = 0;
  public final static int MB = 1;
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
  public Log getLog() {
    return log;
  }

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
  public static void releaseVMResource(VM_Address start, VM_Extent bytes) {} 
  
  /**
   * After VMResource acquisition
   * @param start the start of the acquired resource
   * @param bytes the number of bytes acquired
   */
  public static void acquireVMResource(VM_Address start, VM_Address end, VM_Extent bytes) {} 

}
