/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.AllocAdvice;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Type;
import com.ibm.JikesRVM.memoryManagers.vmInterface.CallSite;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;

/**
 * This class implements a simple mark-sweep collector.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 *
 * @version $Revision$
 * @date $Date$
 */
public class Plan extends StopTheWorldGC implements VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  ////////////////////////////////////////////////////////////////////////////
  //
  // Class variables
  //
  public static final boolean needsWriteBarrier = false;
  public static final boolean needsRefCountWriteBarrier = false;
  public static final boolean refCountCycleDetection = false;
  public static final boolean movesObjects = false;

  // virtual memory resources
  private static FreeListVMResource msVM;
  private static ImmortalVMResource immortalVM;

  // memory resources
  private static MemoryResource msMR;
  private static MemoryResource immortalMR;

  // MS collector
  private static MarkSweepCollector msCollector;

  // Allocators
  private static final int MS_ALLOCATOR = 0;
  public static final int IMMORTAL_ALLOCATOR = 1;
  public static final int DEFAULT_ALLOCATOR = MS_ALLOCATOR;
  public static final int TIB_ALLOCATOR = DEFAULT_ALLOCATOR;
  private static final String[] allocatorNames = { "Mark sweep", "Immortal" };

  // Miscellaneous constants
  private static final int POLL_FREQUENCY = DEFAULT_POLL_FREQUENCY;

  // Memory layout constants
  private static final VM_Address       MS_START = PLAN_START;
  private static final EXTENT            MS_SIZE = 1024 * 1024 * 1024;
  private static final VM_Address         MS_END = MS_START.add(MS_SIZE);
  private static final VM_Address       HEAP_END = MS_END;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Instance variables
  //

  // allocators
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
    msMR = new MemoryResource(POLL_FREQUENCY);
    immortalMR = new MemoryResource(POLL_FREQUENCY);
    msVM       = new FreeListVMResource("MS",       MS_START,   MS_SIZE, VMResource.MOVABLE);
    immortalVM = new ImmortalVMResource("Immortal", immortalMR, IMMORTAL_START, IMMORTAL_SIZE, BOOT_END);
    msCollector = new MarkSweepCollector(msVM, msMR);
  }

  /**
   * Constructor
   */
  public Plan() {
    ms = new MarkSweepAllocator(msCollector);
    immortal = new BumpPointer(immortalVM);
  }

  public static final void boot()
    throws VM_PragmaInterruptible {
    StopTheWorldGC.boot();
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
    throws VM_PragmaInline {} // do nothing
  public final void postCopy(Object ref, Object[] tib, int size,
			     boolean isScalar) {} // do nothing

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
    // return VM_Address.zero();  this trips some Intel assembler bug
    return VM_Address.max();
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

  public static final int getInitialHeaderValue(int size) {
    return msCollector.getInitialHeaderValue(size);
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
  public final boolean poll(boolean mustCollect, MemoryResource mr)
    throws VM_PragmaLogicallyUninterruptible {
    if (gcInProgress) return false;
    if (mustCollect || getPagesReserved() > getTotalPages()) {
      required = mr.reservedPages() - mr.committedPages();
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
    msCollector.prepare(msVM, msMR);
    Immortal.prepare(immortalVM, null);
  }

  protected final void threadLocalPrepare(int count) {
    ms.prepare();
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
  public static final VM_Address traceObject(VM_Address obj) {
    VM_Address addr = VM_Interface.refToAddress(obj);
    if (addr.LE(HEAP_END)) {
      if (addr.GE(MS_START))
	return msCollector.traceObject(obj);
      else if (addr.GE(IMMORTAL_START))
	return Immortal.traceObject(obj);
    } // else this is not a heap pointer
    return obj;
  }

  public static final VM_Address traceObject(VM_Address obj, boolean root) {
    return traceObject(obj);  // root or non-root is of no consequence here
  }

  public final boolean willNotMove(VM_Address obj) {
    return true;  // this is a non-copying collector: nothing moves
  }

  public static final boolean isLive(VM_Address obj) {
    VM_Address addr = VM_ObjectModel.getPointerInMemoryRegion(obj);
    if (addr.LE(HEAP_END)) {
      if (addr.GE(MS_START))
	return msCollector.isLive(obj);
      else if (addr.GE(IMMORTAL_START))
	return true;
    } 
    return false;
  }

  public static final int resetGCBitsForCopy(VM_Address fromObj, 
					     int forwardingPtr, int bytes) {
    if (VM.VerifyAssertions) VM._assert(false);  // not a copying collector!
    return forwardingPtr;
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
    int pages = msMR.reservedPages();
    pages += immortalMR.reservedPages();
    pages += metaDataMR.reservedPages();
    return pages;
  }

  protected static final int getPagesUsed() {
    int pages = msMR.reservedPages();
    pages += immortalMR.reservedPages();
    pages += metaDataMR.reservedPages();
    return pages;
  }

  // Assuming all future allocation comes from mark-sweep
  //
  protected static final int getPagesAvail() {
    return getTotalPages() - msMR.reservedPages() - immortalMR.reservedPages();
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Miscellaneous
  //

  public final void show() {
    ms.show();
  }

  public static final void showUsage() {
      VM.sysWrite("used pages = ", getPagesUsed());
      VM.sysWrite(" ("); VM.sysWrite(Conversions.pagesToBytes(getPagesUsed()) >> 20, " Mb) ");
      VM.sysWrite("= (ms) ", msMR.reservedPages());
      VM.sysWriteln(" + (imm) ", immortalMR.reservedPages());
  }
}
