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
 * This class implements a simple semi-space collector. See the Jones
 * & Lins GC book, section 2.2 for an overview of the basic algorithm.
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
 * @author Perry Cheng
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
  public static final boolean movesObjects = true;

  // virtual memory resources
  private static FreeListVMResource losVM;
  private static MonotoneVMResource ss0VM;
  private static MonotoneVMResource ss1VM;
  private static ImmortalVMResource immortalVM;

  // memory resources
  private static MemoryResource ssMR;
  private static MemoryResource losMR;
  private static MemoryResource immortalMR;

  // LOS collector
  private static MarkSweepCollector losCollector;

  // GC state
  private static boolean hi = false; // True if allocing to "higher" semispace

  // Allocators
  private static final int SS_ALLOCATOR = 0;
  private static final int LOS_ALLOCATOR = 1;
  public static final int IMMORTAL_ALLOCATOR = 2;
  public static final int DEFAULT_ALLOCATOR = SS_ALLOCATOR;
  public static final int TIB_ALLOCATOR = DEFAULT_ALLOCATOR;
  private static final String[] allocatorNames = { "Semispace",
						   "LOS", "Immortal" };

  // Miscellaneous constants
  private static final int POLL_FREQUENCY = DEFAULT_POLL_FREQUENCY;
  private static final EXTENT LOS_SIZE_THRESHOLD = DEFAULT_LOS_SIZE_THRESHOLD;
  
  // Memory layout constants
  private static final VM_Address      LOS_START = PLAN_START;
  private static final EXTENT           LOS_SIZE = 128 * 1024 * 1024;
  private static final VM_Address        LOS_END = LOS_START.add(LOS_SIZE);
  private static final VM_Address       SS_START = LOS_END;
  private static final EXTENT            SS_SIZE = 256 * 1024 * 1024;
  private static final VM_Address   LOW_SS_START = SS_START;
  private static final VM_Address  HIGH_SS_START = SS_START.add(SS_SIZE);
  private static final VM_Address         SS_END = HIGH_SS_START.add(SS_SIZE);
  private static final VM_Address       HEAP_END = SS_END;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Instance variables
  //

  // allocators
  private BumpPointer ss;
  private BumpPointer immortal;
  private MarkSweepAllocator los;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Initialization
  //

  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time).
   */
  static {
    ssMR = new MemoryResource(POLL_FREQUENCY);
    losMR = new MemoryResource(POLL_FREQUENCY);
    immortalMR = new MemoryResource(POLL_FREQUENCY);
    ss0VM      = new MonotoneVMResource("Lower SS", ssMR, LOW_SS_START, SS_SIZE, VMResource.MOVABLE);
    ss1VM      = new MonotoneVMResource("Upper SS", ssMR, HIGH_SS_START, SS_SIZE, VMResource.MOVABLE);
    losVM      = new FreeListVMResource("LOS", LOS_START, LOS_SIZE, VMResource.MOVABLE);
    immortalVM = new ImmortalVMResource("Immortal", immortalMR, IMMORTAL_START, IMMORTAL_SIZE, BOOT_END);
    losCollector = new MarkSweepCollector(losVM, losMR);
  }

  /**
   * Constructor
   */
  public Plan() {
    ss = new BumpPointer(ss0VM);
    los = new MarkSweepAllocator(losCollector);
    immortal = new BumpPointer(immortalVM);
  }

  public static final void boot() throws VM_PragmaInterruptible {
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
    if (allocator == SS_ALLOCATOR && bytes > LOS_SIZE_THRESHOLD) 
      allocator = LOS_ALLOCATOR;
    VM_Address region;
    switch (allocator) {
    case       SS_ALLOCATOR: region = ss.alloc(isScalar, bytes); break;
    case IMMORTAL_ALLOCATOR: region = immortal.alloc(isScalar, bytes); break;
    case      LOS_ALLOCATOR: region = los.alloc(isScalar, bytes); break;
    default:                 region = VM_Address.zero(); 
                             VM.sysFail("No such allocator");
    }
    return region;
  }

  public final void postAlloc(Object ref, Object[] tib, int size,
			      boolean isScalar, int allocator)
    throws VM_PragmaInline {
    if (allocator == SS_ALLOCATOR && size > LOS_SIZE_THRESHOLD) allocator = LOS_ALLOCATOR;
    switch (allocator) {
    case       SS_ALLOCATOR: return;
    case IMMORTAL_ALLOCATOR: return;
    case      LOS_ALLOCATOR: Header.initializeMarkSweepHeader(ref, tib, size, isScalar); return;
    default:                 VM.sysFail("No such allocator");
    }
  }

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
				    boolean isScalar) 
    throws VM_PragmaInline {
    if (VM.VerifyAssertions) VM._assert(bytes < LOS_SIZE_THRESHOLD);
    return ss.alloc(isScalar, bytes);
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
    return (bytes >= LOS_SIZE_THRESHOLD) ? LOS_ALLOCATOR : SS_ALLOCATOR;
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
    return losCollector.getInitialHeaderValue(size);
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
      if (mr == ssMR)
	required = required<<1;  // must account for copy reserve
      VM_Interface.triggerCollection(" memory resource exhaustion");
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
   * Prepare for a collection.  In this case, it means flipping
   * semi-spaces and preparing each of the collectors.
   * Called by BasePlan which will make sure only one thread executes this.
   */
  protected final void globalPrepare() {
    hi = !hi;        // flip the semi-spaces
    ssMR.reset();    // reset the semispace memory resource, and
    // prepare each of the collected regions
    Copy.prepare(((hi) ? ss0VM : ss1VM), ssMR);
    Immortal.prepare(immortalVM, null);
    losCollector.prepare(losVM, losMR);
  }

  protected final void threadLocalPrepare(int count) {
    // rebind the semispace bump pointer to the appropriate semispace.
    ss.rebind(((hi) ? ss1VM : ss0VM)); 
    los.prepare();
  }

  /* We reset the state for a GC thread that is not participating in this GC
   */
  public final void prepareNonParticipating() {
    threadLocalPrepare(NON_PARTICIPANT);
  }

  protected final void threadLocalRelease(int count) {
    los.release();
  }

  /**
   * Clean up after a collection.
   */
  protected final void globalRelease() {
    // release each of the collected regions
    losCollector.release();
    ((hi) ? ss0VM : ss1VM).release();
    Copy.release(((hi) ? ss0VM : ss1VM), ssMR);
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
      if (addr.GE(SS_START)) {
	if ((hi && addr.LT(HIGH_SS_START)) ||
	    (!hi && addr.GE(HIGH_SS_START)))
	  return Copy.traceObject(obj);
	else
	  return obj;
      }
      else if (addr.GE(LOS_START))
	return losCollector.traceObject(obj);
      else if (addr.GE(IMMORTAL_START))
	return Immortal.traceObject(obj);
    } // else this is not a heap pointer
    return obj;
  }

  public static final VM_Address traceObject(VM_Address obj, boolean root) {
    return traceObject(obj);  // root or non-root is of no consequence here
  }

  public final boolean willNotMove(VM_Address obj) {
    VM_Address addr = VM_Interface.refToAddress(obj);
    if (addr.LE(HEAP_END)) {
      if (addr.GE(SS_START)) 
	return (hi ? ss1VM : ss0VM).inRange(addr);
      else if (addr.GE(LOS_START))
	return true;
      else if (addr.GE(IMMORTAL_START))
	return true;
    } 
    return true;
  }

  public static final boolean isSemiSpaceObject(Object base) {
    VM_Address addr =VM_Interface.refToAddress(VM_Magic.objectAsAddress(base));
    return (addr.GE(SS_START) && addr.LE(HEAP_END));
  }

  public static final boolean isLive(VM_Address obj) {
    VM_Address addr = VM_ObjectModel.getPointerInMemoryRegion(obj);
    if (addr.LE(HEAP_END)) {
      if (addr.GE(SS_START))
	return Copy.isLive(obj);
      else if (addr.GE(LOS_START))
	return losCollector.isLive(obj);
      else if (addr.GE(IMMORTAL_START))
	return true;
    } 
    return false;
  }

  public static final int resetGCBitsForCopy(VM_Address fromObj,
					     int forwardingPtr, int bytes) {
    return forwardingPtr; // a no-op for this collector
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
    int pages = ssMR.reservedPages();
    // we must account for the worst case number of pages required
    // for copying, which equals the number of semi-space pages in
    // use
    pages += pages;
    
    pages += losMR.reservedPages();
    pages += immortalMR.reservedPages();
    pages += metaDataMR.reservedPages();
    return pages;
  }

  protected static final int getPagesUsed() {
    int pages = ssMR.reservedPages();
    pages += losMR.reservedPages();
    pages += immortalMR.reservedPages();
    pages += metaDataMR.reservedPages();
    return pages;
  }

  // Assuming all future allocation comes from semispace
  //
  protected static final int getPagesAvail() {
    int semispaceTotal = getTotalPages() - losMR.reservedPages() 
      - immortalMR.reservedPages();
    return (semispaceTotal>>1) - ssMR.reservedPages();
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Miscellaneous
  //

  public final void show() {
    ss.show();
    los.show();
    immortal.show();
  }

  public static final void showUsage() {
      VM.sysWrite("used pages = ", getPagesUsed());
      VM.sysWrite(" ("); VM.sysWrite(Conversions.pagesToBytes(getPagesUsed()) >> 20, " Mb) ");
      VM.sysWrite("= (ss) ", ssMR.reservedPages());  
      VM.sysWrite(" + (los) ", losMR.reservedPages());
      VM.sysWriteln(" + (imm) ", immortalMR.reservedPages());
  }
}
