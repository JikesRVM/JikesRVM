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
 * This class implements a simple semi-space collector. See the Jones
 * & Lins GC book, section 2.2 for an overview of the basic algorithm.
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
  public static final boolean movesObjects = true;

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
    ss = new BumpPointer(ss0VM);
    los = new MarkSweepAllocator(losCollector);
    immortal = new BumpPointer(immortalVM);
  }

  static public void boot() throws VM_PragmaInterruptible {
    BasePlan.boot();
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
  public VM_Address alloc(EXTENT bytes, boolean isScalar, int allocator, AllocAdvice advice) throws VM_PragmaInline {
    if (VM.VerifyAssertions) VM._assert(bytes == (bytes & (~3)));
    if (allocator == SS_ALLOCATOR && bytes > LOS_SIZE_THRESHOLD) 
      allocator = LOS_ALLOCATOR;
    VM_Address region;
    switch (allocator) {
      case       SS_ALLOCATOR: region = ss.alloc(isScalar, bytes); break;
      case IMMORTAL_ALLOCATOR: region = immortal.alloc(isScalar, bytes); break;
      case      LOS_ALLOCATOR: region = los.alloc(isScalar, bytes); break;
      default:                 region = VM_Address.zero(); VM.sysFail("No such allocator");
    }
    return region;
  }

  //  public void postAlloc(EXTENT bytes, Object obj, int allocator) throws VM_PragmaInline {
  public void postAlloc(Object ref, Object[] tib, int size,
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
			     boolean isScalar) { } // do nothing

  public void show() {
    ss.show();
  }

  static public void showPlans() {
    for (int i=0; i<VM_Scheduler.processors.length; i++) {
      VM_Processor p = VM_Scheduler.processors[i];
      if (p == null) continue;
      VM.sysWrite(i, ": ");
      p.mmPlan.show();
    }
  }

  static public void showUsage() {
      VM.sysWrite("used pages = ", getPagesUsed());
      VM.sysWrite(" ("); VM.sysWrite(Conversions.pagesToBytes(getPagesUsed()) >> 20, " Mb) ");
      VM.sysWrite("= (ss) ", ssMR.reservedPages());  
      VM.sysWrite(" + (los) ", losMR.reservedPages());
      VM.sysWriteln(" + (imm) ", immortalMR.reservedPages());
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
  public VM_Address allocCopy(VM_Address original, EXTENT bytes, boolean isScalar) throws VM_PragmaInline {
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
  public int getAllocator(Type type, EXTENT bytes, CallSite callsite, AllocAdvice hint) {
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
  final public AllocAdvice getAllocAdvice(Type type, EXTENT bytes,
					  CallSite callsite, AllocAdvice hint) { 
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
      if (mr == ssMR)
	required = required<<1;  // must account for copy reserve
      VM_Interface.triggerCollection();
      return true;
    }
    return false;
  }
  
  public static boolean isSemiSpaceObject(Object base) {
    VM_Address addr =VM_Interface.refToAddress(VM_Magic.objectAsAddress(base));
    return (addr.GE(SS_START) && addr.LE(HEAP_END));
  }

  public static boolean isLive(VM_Address obj) {
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

  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.
   *
   * @param obj The object reference to be traced.  This is <i>NOT</i> an
   * interior pointer.
   * @return The possibly moved reference.
   */
  static public VM_Address traceObject(VM_Address obj) {
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
  static public VM_Address traceObject(VM_Address obj, boolean root) {
    return traceObject(obj);  // root or non-root is of no consequence here
  }

  public boolean hasMoved(VM_Address obj) {
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

  /**
   * Perform a collection.
   */
  public void collect () {
    prepare();
    super.collect();
    release();
  }

  public static int getInitialHeaderValue(int size) {
    return losCollector.getInitialHeaderValue(size);
  }

  public static int resetGCBitsForCopy(VM_Address fromObj, int forwardingPtr,
				       int bytes) {
    return forwardingPtr; // a no-op for this collector
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
  // Private methods
  //

  /**
   * Return the number of pages reserved for use.
   *
   * @return The number of pages reserved given the pending allocation
   */
  private static int getPagesReserved() {

    int pages = ssMR.reservedPages();
    // we must account for the worst case number of pages required
    // for copying, which equals the number of semi-space pages in
    // use plus a fudge factor which accounts for fragmentation
    pages += pages + COPY_FUDGE_PAGES;
    
    pages += losMR.reservedPages();
    pages += immortalMR.reservedPages();
    return pages;
  }


  private static int getPagesUsed() {
    int pages = ssMR.reservedPages();
    pages += losMR.reservedPages();
    pages += immortalMR.reservedPages();
    return pages;
  }

  // Assuming all future allocation comes from semispace
  //
  private static int getPagesAvail() {
    int semispaceTotal = getTotalPages() - losMR.reservedPages() - immortalMR.reservedPages();
    return (semispaceTotal / 2) - ssMR.reservedPages();
  }

  /**
   * Prepare for a collection.  In this case, it means flipping
   * semi-spaces and preparing each of the collectors.
   * Called by BasePlan which will make sure only one thread executes this.
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
    hi = !hi;          // flip the semi-spaces
    ssMR.reset();    // reset the semispace memory resource, and
    // prepare each of the collected regions
    Copy.prepare(((hi) ? ss0VM : ss1VM), ssMR);
    Immortal.prepare(immortalVM, null);
    losCollector.prepare(losVM, losMR);
  }

  protected void allPrepare(int count) {
    // rebind the semispace bump pointer to the appropriate semispace.
    ss.rebind(((hi) ? ss1VM : ss0VM)); 
    los.prepare();
  }

  /* We reset the state for a GC thread that is not participating in this GC
   */
  public void prepareNonParticipating() {
    allPrepare(NON_PARTICIPANT);
  }

  protected void allRelease(int count) {
    los.release();
  }

  /**
   * Clean up after a collection.
   */
  protected void singleRelease() {
    // release each of the collected regions
    losCollector.release();
    ((hi) ? ss0VM : ss1VM).release();
    Copy.release(((hi) ? ss0VM : ss1VM), ssMR);
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
  private BumpPointer ss;
  private BumpPointer immortal;
  private MarkSweepAllocator los;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Class variables
  //
  private static MarkSweepCollector losCollector;

  // virtual memory regions
  private static FreeListVMResource losVM;
  private static MonotoneVMResource ss0VM;
  private static MonotoneVMResource ss1VM;
  private static ImmortalVMResource immortalVM;

  // memory resources
  private static MemoryResource ssMR;
  private static MemoryResource losMR;
  private static MemoryResource immortalMR;

  // GC state
  private static boolean hi = false;   // If true, we are allocating from the "higher" semispace.
  private static boolean progress = true;  // are we making progress?
  private static int required;  // how many pages must this GC yeild?


  //
  // Final class variables (aka constants)
  //
  private static final VM_Address      LOS_START = PLAN_START;
  private static final EXTENT           LOS_SIZE = 128 * 1024 * 1024;
  private static final VM_Address        LOS_END = LOS_START.add(LOS_SIZE);
  private static final VM_Address       SS_START = LOS_END;
  private static final EXTENT            SS_SIZE = 256 * 1024 * 1024;              // size of each space
  private static final VM_Address   LOW_SS_START = SS_START;
  private static final VM_Address  HIGH_SS_START = SS_START.add(SS_SIZE);
  private static final VM_Address         SS_END = HIGH_SS_START.add(SS_SIZE);
  private static final VM_Address       HEAP_END = SS_END;
  private static final EXTENT LOS_SIZE_THRESHOLD = DEFAULT_LOS_SIZE_THRESHOLD;

  private static final int COPY_FUDGE_PAGES = 1;  // Steve - fix this

  private static final int POLL_FREQUENCY = DEFAULT_POLL_FREQUENCY;

  public static final int SS_ALLOCATOR = 0;
  public static final int LOS_ALLOCATOR = 1;
  public static final int IMMORTAL_ALLOCATOR = 2;
  public static final int DEFAULT_ALLOCATOR = SS_ALLOCATOR;
  public static final int TIB_ALLOCATOR = DEFAULT_ALLOCATOR;

  private static final String[] allocatorNames = { "Semispace", "LOS", "Immortal" };

  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time).
   */
  static {

    // memory resources
    ssMR = new MemoryResource(POLL_FREQUENCY);
    losMR = new MemoryResource(POLL_FREQUENCY);
    immortalMR = new MemoryResource(POLL_FREQUENCY);

    // virtual memory resources
    ss0VM      = new MonotoneVMResource("Lower SS", ssMR,       LOW_SS_START,   SS_SIZE, VMResource.MOVABLE);
    ss1VM      = new MonotoneVMResource("Upper SS", ssMR,       HIGH_SS_START,  SS_SIZE, VMResource.MOVABLE);
    losVM      = new FreeListVMResource("LOS",   LOS_START,     LOS_SIZE,       VMResource.MOVABLE);
    immortalVM = new ImmortalVMResource("Immortal", immortalMR, IMMORTAL_START, IMMORTAL_SIZE, BOOT_END);
    
    losCollector = new MarkSweepCollector(losVM, losMR);

  }

}
   
