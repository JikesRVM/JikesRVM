/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.*;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_Thread;
import com.ibm.JikesRVM.VM_Time;
import com.ibm.JikesRVM.VM_Processor;

/**
 * This class implements a simple non-generational copying, mark-sweep
 * hybrid.  All allocation goes to the copying space.  Whenever the
 * heap is full, both spaces are collected, with survivors in the
 * copying space copied to the mark-sweep space.  This collector is
 * more space efficient than a simple semi-space collector (it does
 * not require a copy reserve for the non-copying space) and, like the
 * semi-space collector, it does not require a write barrier.
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
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 *
 * @version $Revision$
 * @date $Date$
 */
public class Plan extends StopTheWorldGC implements VM_Uninterruptible {
  public static final String Id = "$Id$"; 

  ////////////////////////////////////////////////////////////////////////////
  //
  // Class variables
  //
  public static final boolean needsWriteBarrier = false;
  public static final boolean needsRefCountWriteBarrier = false;
  public static final boolean refCountCycleDetection = false;
  public static final boolean movesObjects = true;

  // virtual memory resources
  private static MonotoneVMResource nurseryVM;
  private static FreeListVMResource msVM;
  private static ImmortalVMResource immortalVM;

  // memory resources
  private static MemoryResource nurseryMR;
  private static MemoryResource msMR;
  private static MemoryResource immortalMR;

  // Mark-sweep collector (mark-sweep space, large objects)
  private static MarkSweepCollector msCollector;

  // Allocators
  private static final int NURSERY_ALLOCATOR = 0;
  private static final int MS_ALLOCATOR = 1;
  public static final int IMMORTAL_ALLOCATOR = 2;
  public static final int DEFAULT_ALLOCATOR = NURSERY_ALLOCATOR;
  public static final int TIB_ALLOCATOR = DEFAULT_ALLOCATOR;
  private static final String[] allocatorNames = { "Nursery", "Mark-sweep",
						   "Immortal" };

  // Miscellaneous constants
  private static final int POLL_FREQUENCY = DEFAULT_POLL_FREQUENCY;
  private static final EXTENT LOS_SIZE_THRESHOLD = DEFAULT_LOS_SIZE_THRESHOLD;

  // Memory layout constants
  private static final VM_Address       MS_START = PLAN_START;
  private static final EXTENT            MS_SIZE = 512 * 1024 * 1024;
  private static final VM_Address         MS_END = MS_START.add(MS_SIZE);
  private static final VM_Address  NURSERY_START = MS_END;
  private static final EXTENT       NURSERY_SIZE = 64 * 1024 * 1024;
  private static final VM_Address    NURSERY_END = NURSERY_START.add(NURSERY_SIZE);
  private static final VM_Address       HEAP_END = NURSERY_END;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Instance variables
  //
  private BumpPointer nursery;
  private MarkSweepAllocator ms;
  private BumpPointer immortal;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Initialization
  //

  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time).
   */
  static {
    nurseryMR = new MemoryResource(POLL_FREQUENCY);
    msMR = new MemoryResource(POLL_FREQUENCY);
    immortalMR = new MemoryResource(POLL_FREQUENCY);
    nurseryVM  = new MonotoneVMResource("Nursery", nurseryMR,   NURSERY_START, NURSERY_SIZE, VMResource.MOVABLE);
    msVM       = new FreeListVMResource("MS",   MS_START,      MS_SIZE,      VMResource.MOVABLE);
    immortalVM = new ImmortalVMResource("Immortal", immortalMR, IMMORTAL_START, IMMORTAL_SIZE, BOOT_END);
    msCollector = new MarkSweepCollector(msVM, msMR);
  }

  /**
   * Constructor
   */
  public Plan() {
    nursery = new BumpPointer(nurseryVM);
    ms = new MarkSweepAllocator(msCollector);
    immortal = new BumpPointer(immortalVM);
  }

  public final static void boot()
    throws VM_PragmaInterruptible {
    BasePlan.boot();
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Allocation
  //

  /**
   * Allocate space (for an object)
   *
   * @param allocator The allocator number to be used for this allocation
   * @param bytes The size of the space to be allocated (in bytes)
   * @param isScalar True if the object occupying this space will be a scalar
   * @param advice Statically-generated allocation advice for this allocation
   * @return The address of the first byte of the allocated region
   */
  public final VM_Address alloc(EXTENT bytes, boolean isScalar, int allocator, 
				AllocAdvice advice)
    throws VM_PragmaInline {
    if (VM.VerifyAssertions) VM._assert(bytes == (bytes & (~(WORD_SIZE-1))));
    if (allocator == NURSERY_ALLOCATOR && bytes > LOS_SIZE_THRESHOLD)
      allocator = MS_ALLOCATOR;
    VM_Address region;
    switch (allocator) {
      case  NURSERY_ALLOCATOR: region = nursery.alloc(isScalar, bytes); break;
      case       MS_ALLOCATOR: region = ms.alloc(isScalar, bytes); break;
      case IMMORTAL_ALLOCATOR: region = immortal.alloc(isScalar, bytes); break;
      default:                 region = VM_Address.zero();
	                       VM.sysFail("No such allocator");
    }
    if (VM.VerifyAssertions) VM._assert(Memory.assertIsZeroed(region, bytes));
    return region;
  }
  
  public final void postAlloc(Object ref, Object[] tib, int size,
			      boolean isScalar, int allocator)
    throws VM_PragmaInline {
    if ((allocator == NURSERY_ALLOCATOR && size > LOS_SIZE_THRESHOLD) ||
	(allocator == MS_ALLOCATOR))
      Header.initializeMarkSweepHeader(ref, tib, size, isScalar);
  }

  public final void postCopy(Object ref, Object[] tib, int size,
			     boolean isScalar) {}

  /**
   * Allocate space for copying an object (this method <i>does not</i>
   * copy the object, it only allocates space)
   *
   * @param original A reference to the original object
   * @param bytes The size of the space to be allocated (in bytes)
   * @param isScalar True if the object occupying this space will be a scalar
   * @return The address of the first byte of the allocated region
   */
  public final VM_Address allocCopy(VM_Address original, EXTENT bytes,
				    boolean isScalar) 
    throws VM_PragmaInline {
    return ms.allocCopy(isScalar, bytes);
  }

  /**
   * Advise the compiler/runtime which allocator to use for a
   * particular allocation.  This should be called at compile time and
   * the returned value then used for the given site at runtime.
   *
   * @param type The type id of the type being allocated
   * @param bytes The size (in bytes) required for this object
   * @param callsite Information identifying the point in the code
   * where this allocation is taking place.
   * @param hint A hint from the compiler as to which allocator this
   * site should use.
   * @return The allocator number to be used for this allocation.
   */
  public final int getAllocator(Type type, EXTENT bytes, CallSite callsite,
				AllocAdvice hint) {
    return (bytes >= LOS_SIZE_THRESHOLD) ? MS_ALLOCATOR : NURSERY_ALLOCATOR;
  }

  /**
   * Give the compiler/runtime statically generated alloction advice
   * which will be passed to the allocation routine at runtime.
   *
   * @param type The type id of the type being allocated
   * @param bytes The size (in bytes) required for this object
   * @param callsite Information identifying the point in the code
   * where this allocation is taking place.
   * @param hint A hint from the compiler as to which allocator this
   * site should use.
   * @return Allocation advice to be passed to the allocation routine
   * at runtime
   */
  public final AllocAdvice getAllocAdvice(Type type, EXTENT bytes,
					  CallSite callsite,
					  AllocAdvice hint) { 
    return null;
  }

  public static final int getInitialHeaderValue(int size) {
    return msCollector.getInitialHeaderValue(size);
  }

  /**
   * This method is called periodically by the allocation subsystem
   * (by default, each time a page is consumed), and provides the
   * collector with an opportunity to collect.<p>
   *
   * We trigger a collection whenever an allocation request is made
   * that would take the number of blocks in use (committed for use)
   * beyond the number of blocks available.  Collections are triggered
   * through the runtime, and ultimately call the
   * <code>collect()</code> method of this class or its superclass.
   *
   * This method is clearly interruptible since it can lead to a GC.
   * However, the caller is typically uninterruptible and this fiat allows 
   * the interruptibility check to work.  The caveat is that the caller 
   * of this method must code as though the method is interruptible. 
   * In practice, this means that, after this call, processor-specific
   * values must be reloaded.
   *
   * @return Whether a collection is triggered
   */

  public final boolean poll(boolean mustCollect, MemoryResource mr)
    throws VM_PragmaLogicallyUninterruptible {
    if (gcInProgress) return false;
    if (mustCollect || getPagesReserved() > getTotalPages()) {
      if (VM.VerifyAssertions) VM._assert(mr != metaDataMR);
      required = mr.reservedPages() - mr.committedPages();
      if (mr == nurseryMR)
	required = required<<1;  // must account for copy reserve
      VM_Interface.triggerCollection("GC triggered due to resource exhaustion");
      return true;
    }
    return false;
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Collection
  //

  /**
   * Perform a collection.
   */
  public final void collect () {
    prepare();
    super.collect();
    release();
  }

  /**
   * Prepare for a collection.  Called by BasePlan which will make
   * sure only one thread executes this.
   */
  protected final void globalPrepare() {
    nurseryMR.reset();
    msCollector.prepare(msVM, msMR);
    Immortal.prepare(immortalVM, null);
  }

  protected final void threadLocalPrepare(int count) {
    ms.prepare();
    nursery.rebind(nurseryVM);
  }

  /* We reset the state for a GC thread that is not participating in this GC
   */
  public final void prepareNonParticipating() {
    threadLocalPrepare(NON_PARTICIPANT);
  }

  protected final void threadLocalRelease(int count) {
    ms.release();
  }

  /**
   * Clean up after a collection.
   */
  protected final void globalRelease() {
    // release each of the collected regions
    nurseryVM.release();
    msCollector.release();
    Immortal.release(immortalVM, null);
    if (getPagesReserved() + required >= getTotalPages()) {
      if (!progress)
	VM.sysFail("Out of memory");
      progress = false;
    } else
      progress = true;
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Object processing and tracing
  //

  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.
   *
   * @param obj The object reference to be traced.  This is <i>NOT</i> an
   * interior pointer.
   * @return The possibly moved reference.
   */
  public final static VM_Address traceObject(VM_Address obj, boolean root) {
    return traceObject(obj);
  }
  public final static VM_Address traceObject(VM_Address obj) {
    VM_Address addr = VM_Interface.refToAddress(obj);
    if (addr.LE(HEAP_END)) {
      if (addr.GE(NURSERY_START))
	return Copy.traceObject(obj);
      else if (addr.GE(MS_START))
	return msCollector.traceObject(obj);
      else if (addr.GE(IMMORTAL_START))
	return Immortal.traceObject(obj);
    } // else this is not a heap pointer
    return obj;
  }

  public final boolean willNotMove(VM_Address obj) {
    VM_Address addr = VM_Interface.refToAddress(obj);
    if (addr.LE(HEAP_END)) {
      if (addr.GE(NURSERY_START))
	return nurseryVM.inRange(addr);
    }
    return true;
  }

  public final static boolean isNurseryObject(Object base) {
    VM_Address addr =VM_Interface.refToAddress(VM_Magic.objectAsAddress(base));
    return (addr.GE(NURSERY_START) && addr.LE(HEAP_END));
  }

  public final static boolean isLive(VM_Address obj) {
    VM_Address addr = VM_ObjectModel.getPointerInMemoryRegion(obj);
    if (addr.LE(HEAP_END)) {
      if (addr.GE(NURSERY_START))
	return Copy.isLive(obj);
      else if (addr.GE(MS_START))
	return msCollector.isLive(obj);
      else if (addr.GE(IMMORTAL_START))
	return true;
    } 
    return false;
  }

  public static final int resetGCBitsForCopy(VM_Address fromObj,
					     int forwardingPtr, int bytes) {
    return (forwardingPtr & ~HybridHeader.GC_BITS_MASK) | msCollector.getInitialHeaderValue(bytes);
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Space management
  //

  /**
   * Return the number of pages reserved for use.
   *
   * @return The number of pages reserved given the pending allocation
   */
  protected static final int getPagesReserved() {
    int pages = nurseryMR.reservedPages();
    pages += pages;

    pages += msMR.reservedPages();
    pages += immortalMR.reservedPages();
    return pages;
  }

  protected static final int getPagesUsed() {
    int pages = nurseryMR.reservedPages();
    pages += msMR.reservedPages();
    pages += immortalMR.reservedPages();
    return pages;
  }

  // Assuming all future allocation comes from nursery
  //
  protected static final int getPagesAvail() {
    int nurseryPages = getTotalPages() - msMR.reservedPages() - immortalMR.reservedPages();
    return (nurseryPages>>1) - nurseryMR.reservedPages();
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Miscellaneous
  //
  public final MarkSweepCollector getMS() {
    return msCollector;
  }

  public final void show() {
    nursery.show();
    ms.show();
  }

  public final static void showUsage() {
      VM.sysWrite("used pages = ", getPagesUsed());
      VM.sysWrite(" ("); VM.sysWrite(Conversions.pagesToBytes(getPagesUsed()) >> 20, " Mb) ");
      VM.sysWrite("= (nursery) ", nurseryMR.reservedPages());  
      VM.sysWrite("= (ms) ", msMR.reservedPages());
      VM.sysWriteln(" + (imm) ", immortalMR.reservedPages());
  }
}

