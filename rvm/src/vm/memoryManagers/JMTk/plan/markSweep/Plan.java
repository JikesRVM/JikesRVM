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
 * This class implements a simple mark-sweep collector.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public final class Plan extends BasePlan implements VM_Uninterruptible { // implements Constants 
  public final static String Id = "$Id$"; 

  public static final boolean needsWriteBarrier = false;
  public static final boolean needsRefCountWriteBarrier = false;
  public static final boolean refCountCycleDetection = false;
  public static final boolean movesObjects = false;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Public static methods (aka "class methods")
  //
  // Static methods and fields of Plan are those with global scope,
  // such as virtual memory and memory resources.  This stands in
  // contrast to instance methods which are for fast, unsychronized
  // access to thread-local structures such as bump pointers and
  // remsets.
  //

  public static void boot()
    throws VM_PragmaInterruptible {
    BasePlan.boot();
  }

  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.
   *
   * @param obj The object reference to be traced.  This is <i>NOT</i> an
   * interior pointer.
   * @return The possibly moved reference.
   */
  public static VM_Address traceObject(VM_Address obj, boolean root) {
    return traceObject(obj);
  }
  public static VM_Address traceObject(VM_Address obj) {
    VM_Address addr = VM_Interface.refToAddress(obj);
    if (addr.LE(HEAP_END)) {
      if (addr.GE(MS_START))
	return msCollector.traceObject(obj);
      else if (addr.GE(IMMORTAL_START))
	return Immortal.traceObject(obj);
    } // else this is not a heap pointer
    return obj;
  }

  public static boolean isLive(VM_Address obj) {
    VM_Address addr = VM_ObjectModel.getPointerInMemoryRegion(obj);
    if (addr.LE(HEAP_END)) {
      if (addr.GE(MS_START))
	return msCollector.isLive(obj);
      else if (addr.GE(IMMORTAL_START))
	return true;
    } 
    return false;
  }

  public static void showPlans() {
    for (int i=0; i<VM_Scheduler.processors.length; i++) {
      VM_Processor p = VM_Scheduler.processors[i];
      if (p == null) continue;
      VM.sysWrite(i, ": ");
      p.mmPlan.show();
    }
  }

  public static void showUsage() {
      VM.sysWrite("used pages = ", getPagesUsed());
      VM.sysWrite(" ("); VM.sysWrite(Conversions.pagesToBytes(getPagesUsed()) >> 20, " Mb) ");
      VM.sysWrite("= (ms) ", msMR.reservedPages());
      VM.sysWriteln(" + (imm) ", immortalMR.reservedPages());
  }

  public static int getInitialHeaderValue(int size) {
    return msCollector.getInitialHeaderValue(size);
  }

  public static int resetGCBitsForCopy(VM_Address fromObj, int forwardingPtr,
				       int bytes) {
    if (VM.VerifyAssertions)
      VM._assert(false);  // this is not a copying collector!
    return forwardingPtr;
  }

  public static final long freeMemory() throws VM_PragmaUninterruptible {
    return totalMemory() - usedMemory();
  }

  public static final long usedMemory() throws VM_PragmaUninterruptible {
    return Conversions.pagesToBytes(getPagesUsed());
  }

  public static final long totalMemory() throws VM_PragmaUninterruptible {
    return Conversions.pagesToBytes(getPagesAvail());
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Public instance methods
  //
  // Instances of Plan map 1:1 to "kernel threads" (aka CPUs or in
  // Jikes RVM, VM_Processors).  Thus instance methods allow fast,
  // unsychronized access to Plan utilities such as allocation and
  // collection.  Each instance rests on static resources (such as
  // memory and virtual memory resources) which are "global" and
  // therefore "static" members of Plan.
  //

  /**
   * Constructor
   */
  public Plan() {
    ms = new MarkSweepAllocator(msCollector);
    immortal = new BumpPointer(immortalVM);
  }

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
    VM_Address region;
    switch (allocator) {
      case       MS_ALLOCATOR: region = ms.alloc(isScalar, bytes); break;
      case IMMORTAL_ALLOCATOR: region = immortal.alloc(isScalar, bytes); break;
      default:                 region = VM_Address.zero(); VM.sysFail("No such allocator");
    }
    if (VM.VerifyAssertions) VM._assert(Memory.assertIsZeroed(region, bytes));
    return region;
  }
  
  public final void postAlloc(Object ref, Object[] tib, int size,
			      boolean isScalar, int allocator)
    throws VM_PragmaInline {
  }
  public final void postCopy(Object ref, Object[] tib, int size,
			     boolean isScalar) {
  }

  public final void show() {
    ms.show();
  }

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
				    boolean isScalar) throws VM_PragmaInline {
    if (VM.VerifyAssertions) VM._assert(false);
    return null;
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
    return MS_ALLOCATOR;
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

  /**
   * This method is called periodically by the allocation subsystem
   * (by default, each time a page is consumed), and provides the
   * collector with an opportunity to collect.<p>
   *
   * We trigger a collection whenever an allocation request is made
   * that would take the number of pages in use (committed for use)
   * beyond the number of pages available.  Collections are triggered
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

  public boolean poll(boolean mustCollect, MemoryResource mr)
    throws VM_PragmaLogicallyUninterruptible {
    if (gcInProgress) return false;
    if (mustCollect || getPagesReserved() > getTotalPages()) {
      required = mr.reservedPages() - mr.committedPages();
      VM_Interface.triggerCollection();
      return true;
    }
    return false;
  }
  
  public final MarkSweepCollector getMS() {
    return msCollector;
  }

  public final boolean hasMoved(VM_Address obj) {
    return true;
  }

  /**
   * Perform a collection.
   */
  public void collect () {
    prepare();
    super.collect();
    release();
  }

  /* We reset the state for a GC thread that is not participating in this GC
   */
  public void prepareNonParticipating() {
    allPrepare(NON_PARTICIPANT);
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Private class methods
  //

  /**
   * Return the number of pages reserved for use.
   *
   * @return The number of pages reserved given the pending allocation
   */
  private static int getPagesReserved() {

    int pages = msMR.reservedPages();
    pages += immortalMR.reservedPages();
    return pages;
  }


  private static int getPagesUsed() {
    int pages = msMR.reservedPages();
    pages += immortalMR.reservedPages();
    return pages;
  }

  // Assuming all future allocation comes from mark-sweep
  //
  private static int getPagesAvail() {
    return getTotalPages() - msMR.reservedPages() - immortalMR.reservedPages();
  }

  private static final String allocatorToString(int type) {
    switch (type) {
      case MS_ALLOCATOR: return "Mark-sweep";
      case IMMORTAL_ALLOCATOR: return "Immortal";
      default: return "Unknown";
   }
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Private and protected instance methods
  //

  /**
   * Prepare for a collection.  Called by BasePlan which will make
   * sure only one thread executes this.
   */
  protected void singlePrepare() {
    if (verbose > 0) {
      VM.sysWrite("Collection ", gcCount);
      VM.sysWrite(":      reserved = ", getPagesReserved());
      VM.sysWrite(" (", Conversions.pagesToBytes(getPagesReserved()) / ( 1 << 20)); 
      VM.sysWrite(" Mb) ");
      VM.sysWrite("      trigger = ", getTotalPages());
      VM.sysWrite(" (", Conversions.pagesToBytes(getTotalPages()) / ( 1 << 20)); 
      VM.sysWriteln(" Mb) ");
      VM.sysWrite("  Before Collection: ");
      showUsage();
    }
    msCollector.prepare(msVM, msMR);
    Immortal.prepare(immortalVM, null);
  }

  protected void allPrepare(int count) {
    ms.prepare();
  }

  protected void allRelease(int count) {
    ms.release();
  }

  /**
   * Clean up after a collection.
   */
  protected void singleRelease() {
    // release each of the collected regions
    msCollector.release();
    Immortal.release(immortalVM, null);
    if (verbose > 0) {
      VM.sysWrite("   After Collection: ");
      showUsage();
    }
    if (getPagesReserved() + required >= getTotalPages()) {
      if (!progress)
	VM.sysFail("Out of memory");
      progress = false;
    } else
      progress = true;
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Instance variables
  //
  private MarkSweepAllocator ms;
  private BumpPointer immortal;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Class variables
  //
  // virtual memory regions
  private static MarkSweepCollector msCollector;

  private static FreeListVMResource msVM;
  private static ImmortalVMResource immortalVM;

  // memory resources
  private static MemoryResource msMR;
  private static MemoryResource immortalMR;

  // GC state
  private static boolean progress = true;  // are we making progress?
  private static int required;  // how many pages must this GC yeild?

  //
  // Final class variables (aka constants)
  //
  private static final VM_Address       MS_START = PLAN_START;
  private static final EXTENT            MS_SIZE = 1024 * 1024 * 1024;              // size of each space
  private static final VM_Address         MS_END = MS_START.add(MS_SIZE);
  private static final VM_Address       HEAP_END = MS_END;

  private static final int POLL_FREQUENCY = DEFAULT_POLL_FREQUENCY;

  public static final int MS_ALLOCATOR = 0;
  public static final int IMMORTAL_ALLOCATOR = 1;
  public static final int DEFAULT_ALLOCATOR = MS_ALLOCATOR;


  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time).
   */
  static {

    // memory resources
    msMR = new MemoryResource(POLL_FREQUENCY);
    immortalMR = new MemoryResource(POLL_FREQUENCY);

    // virtual memory resources
    msVM       = new FreeListVMResource("MS",       MS_START,   MS_SIZE, VMResource.MOVABLE);
    immortalVM = new ImmortalVMResource("Immortal", immortalMR, IMMORTAL_START, IMMORTAL_SIZE, BOOT_END);

    // collectors
    msCollector = new MarkSweepCollector(msVM, msMR);
  }

}

