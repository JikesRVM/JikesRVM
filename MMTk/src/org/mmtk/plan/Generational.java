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
public abstract class Generational extends StopTheWorldGC 
  implements VM_Uninterruptible {
  public static final String Id = "$Id$"; 

  ////////////////////////////////////////////////////////////////////////////
  //
  // Class variables
  //
  public static final boolean needsWriteBarrier = true;
  public static final boolean needsRefCountWriteBarrier = false;
  public static final boolean refCountCycleDetection = false;
  public static final boolean movesObjects = true;

  // virtual memory resources
  protected static MonotoneVMResource nurseryVM;
  protected static FreeListVMResource losVM;
  protected static ImmortalVMResource immortalVM;

  // memory resources
  protected static MemoryResource nurseryMR;
  protected static MemoryResource matureMR;
  protected static MemoryResource losMR;
  protected static MemoryResource immortalMR;

  // large object space collector
  protected static MarkSweepCollector losCollector;

  // GC state
  protected static boolean fullHeapGC = false;

  // Allocators
  protected static final int NURSERY_ALLOCATOR = 0;
  protected static final int MATURE_ALLOCATOR = 1;
  protected static final int LOS_ALLOCATOR = 2;
  public static final int IMMORTAL_ALLOCATOR = 3;
  public static final int DEFAULT_ALLOCATOR = NURSERY_ALLOCATOR;
  public static final int TIB_ALLOCATOR = DEFAULT_ALLOCATOR;
  private static final String[] allocatorNames = { "Nursery", "Mature", "LOS",
						   "Immortal" };

  // Miscellaneous constants
  protected static final int POLL_FREQUENCY = DEFAULT_POLL_FREQUENCY;
  protected static final int NURSERY_THRESHOLD = (512*1024)>>LOG_PAGE_SIZE;
  protected static final EXTENT LOS_SIZE_THRESHOLD = DEFAULT_LOS_SIZE_THRESHOLD;

  // Memory layout constants
  protected static final VM_Address     LOS_START = PLAN_START;
  protected static final EXTENT          LOS_SIZE = 128 * 1024 * 1024;
  protected static final VM_Address       LOS_END = LOS_START.add(LOS_SIZE);
  protected static final VM_Address  MATURE_START = LOS_END;
  protected static final EXTENT    MATURE_SS_SIZE = 256 * 1024 * 1024;
  protected static final EXTENT       MATURE_SIZE = MATURE_SS_SIZE<<1;
  protected static final VM_Address    MATURE_END = MATURE_START.add(MATURE_SIZE);
  protected static final VM_Address NURSERY_START = MATURE_END;
  protected static final EXTENT      NURSERY_SIZE = 64 * 1024 * 1024;
  protected static final VM_Address   NURSERY_END = NURSERY_START.add(NURSERY_SIZE);
  protected static final VM_Address      HEAP_END = NURSERY_END;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Instance variables
  //
  protected BumpPointer nursery;
  protected MarkSweepAllocator los;
  protected BumpPointer immortal;

  // write buffer (remembered set)
  protected WriteBuffer remset;

  protected int wbFastPathCounter = 0;
  protected int wbSlowPathCounter = 0;

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
    matureMR = new MemoryResource(POLL_FREQUENCY);
    immortalMR = new MemoryResource(POLL_FREQUENCY);
    nurseryVM  = new MonotoneVMResource("Nursery", nurseryMR,   NURSERY_START, NURSERY_SIZE, VMResource.MOVABLE);
    immortalVM = new ImmortalVMResource("Immortal", immortalMR, IMMORTAL_START, IMMORTAL_SIZE, BOOT_END);
    if (Plan.usesLOS) {
      losMR = new MemoryResource(POLL_FREQUENCY);
      losVM = new FreeListVMResource("LOS", LOS_START, LOS_SIZE, VMResource.MOVABLE);
      losCollector = new MarkSweepCollector(losVM, losMR);
    }
  }

  /**
   * Constructor
   */
  public Generational() {
    nursery = new BumpPointer(nurseryVM);
    if (Plan.usesLOS) los = new MarkSweepAllocator(losCollector);
    immortal = new BumpPointer(immortalVM);
    remset = new WriteBuffer(locationPool);
  }

  public final static void boot() throws VM_PragmaInterruptible {
    StopTheWorldGC.boot();
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Allocation
  //
  abstract VM_Address matureAlloc(boolean isScalar, EXTENT bytes);
  abstract VM_Address matureCopy(boolean isScalar, EXTENT bytes);

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
      allocator = (Plan.usesLOS) ? LOS_ALLOCATOR : MATURE_ALLOCATOR;
    if (VM.VerifyAssertions) VM._assert(Plan.usesLOS || allocator != LOS_ALLOCATOR);
    VM_Address region;
    switch (allocator) {
      case  NURSERY_ALLOCATOR: region = nursery.alloc(isScalar, bytes); break;
      case   MATURE_ALLOCATOR: region = matureAlloc(isScalar, bytes); break;
      case IMMORTAL_ALLOCATOR: region = immortal.alloc(isScalar, bytes); break;
      case      LOS_ALLOCATOR: region = los.alloc(isScalar, bytes); break;
      default:                 region = VM_Address.zero();
	                       VM.sysFail("No such allocator");
    }
    if (VM.VerifyAssertions) VM._assert(Memory.assertIsZeroed(region, bytes));
    return region;
  }

  public final void postAlloc(Object ref, Object[] tib, int size,
			      boolean isScalar, int allocator)
    throws VM_PragmaInline {
    if (allocator == NURSERY_ALLOCATOR && size > LOS_SIZE_THRESHOLD)
      allocator = (Plan.usesLOS) ? LOS_ALLOCATOR : MATURE_ALLOCATOR;
    if (VM.VerifyAssertions) VM._assert(Plan.usesLOS || allocator != LOS_ALLOCATOR);
    switch (allocator) {
      case  NURSERY_ALLOCATOR: return;
      case   MATURE_ALLOCATOR: if (!Plan.copyMature) Header.initializeMarkSweepHeader(ref, tib, size, isScalar); return;
      case IMMORTAL_ALLOCATOR: return;
      case      LOS_ALLOCATOR: Header.initializeMarkSweepHeader(ref, tib, size, isScalar); return;
      default:                 VM.sysFail("No such allocator");
    }
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
    return (bytes >= LOS_SIZE_THRESHOLD) ? (Plan.usesLOS ? LOS_ALLOCATOR : MATURE_ALLOCATOR) : NURSERY_ALLOCATOR;
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
    return matureCopy(isScalar, bytes);
  }

  public final void postCopy(Object ref, Object[] tib, int size,
			     boolean isScalar) {} // do nothing

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
    if (mustCollect ||
	getPagesReserved() > getTotalPages() ||
	nurseryMR.reservedPages() > Options.nurseryPages) {
      if (VM.VerifyAssertions)	VM._assert(mr != metaDataMR);
      required = mr.reservedPages() - mr.committedPages();
      if (mr == nurseryMR || (Plan.copyMature && (mr == matureMR)))
	required = required<<1;  // must account for copy reserve
      fullHeapGC = mustCollect || fullHeapGC;
      VM_Interface.triggerCollection("GC due to resource exhaustion");
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

  abstract void globalMaturePrepare();
  /**
   * Prepare for a collection.  In this case, it means flipping
   * semi-spaces and preparing each of the collectors.
   * Called by BasePlan which will make sure only one thread executes this.
   */
  protected final void globalPrepare() {
    nurseryMR.reset(); // reset the nursery
    if (fullHeapGC) {
      // prepare each of the collected regions
      if (Plan.usesLOS) losCollector.prepare(losVM, losMR);
      globalMaturePrepare();
      Immortal.prepare(immortalVM, null);
    }
  }

  abstract void threadLocalMaturePrepare(int count);
  protected final void threadLocalPrepare(int count) {
    remset.flushLocal();
    nursery.rebind(nurseryVM);
    if (fullHeapGC) {
      threadLocalMaturePrepare(count);
      if (Plan.usesLOS) los.prepare();
    }
  }
  /**
   * Clean up after a collection.
   */
  abstract void threadLocalMatureRelease(int count);
  protected final void threadLocalRelease(int count) {
    if (GATHER_WRITE_BARRIER_STATS) { 
      // This is printed independently of the verbosity so that any
      // time someone sets the GATHER_WRITE_BARRIER_STATS flags they
      // will know---it will have a noticable performance hit...
      VM.sysWrite("<GC ", gcCount); VM.sysWrite(" "); 
      VM.sysWrite(wbFastPathCounter, false); VM.sysWrite(" wb-fast, ");
      VM.sysWrite(wbSlowPathCounter, false); VM.sysWrite(" wb-slow>\n");
      wbFastPathCounter = wbSlowPathCounter = 0;
    }
    remset.flushLocal(); // flush any remset entries collected during GC
    if (fullHeapGC) { 
      if (Plan.usesLOS) los.release();
      threadLocalMatureRelease(count);
    }
  }
  abstract void globalMatureRelease();
  protected void globalRelease() {
    // release each of the collected regions
    nurseryVM.release();
    locationPool.flushQueue(1); // flush any remset entries collected during GC
    if (fullHeapGC) {
      if (Plan.usesLOS) losCollector.release();
      globalMatureRelease();
      Immortal.release(immortalVM, null);
    }
    fullHeapGC = (getPagesAvail() < NURSERY_THRESHOLD);
    if (getPagesReserved() + required >= getTotalPages()) {
      if (!progress)
 	VM.sysFail("Out of memory");
      progress = false;
    } else
      progress = true;
  }

  /* We reset the state for a GC thread that is not participating in this GC
   */
  final public void prepareNonParticipating() {
    threadLocalPrepare(NON_PARTICIPANT);
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Object processing and tracing
  //
  public static final VM_Address traceObject(VM_Address obj, boolean root)
    throws VM_PragmaInline {
    return traceObject(obj);
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
  public static final VM_Address traceObject(VM_Address obj) {
    VM_Address addr = VM_Interface.refToAddress(obj);
    if (addr.LE(HEAP_END)) {
      if (addr.GE(NURSERY_START))
	return Copy.traceObject(obj);
      else if (fullHeapGC) {
	if (addr.GE(MATURE_START))
	  return Plan.traceMatureObject(obj, addr);
	else if (Plan.usesLOS && addr.GE(LOS_START))
	  return losCollector.traceObject(obj);
	else if (addr.GE(IMMORTAL_START))
	  return Immortal.traceObject(obj);
      }
    } // else this is not a heap pointer
    return obj;
  }
  abstract boolean willNotMoveMature(VM_Address addr);
  public final boolean willNotMove(VM_Address obj) {
    VM_Address addr = VM_Interface.refToAddress(obj);
    if (addr.LE(HEAP_END)) {
      if (addr.GE(NURSERY_START))
	return nurseryVM.inRange(addr);
      else if (addr.GE(MATURE_START)) 
	return willNotMoveMature(addr);
    } 
    return true;
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Write barriers. 
  //
  public final void putFieldWriteBarrier(VM_Address src, int offset, VM_Address tgt)
    throws VM_PragmaInline {
    writeBarrier(src.add(offset), tgt);
  }

  public final void arrayStoreWriteBarrier(VM_Address src, int index, VM_Address tgt)
    throws VM_PragmaInline {
    writeBarrier(src.add(index<<LOG_WORD_SIZE), tgt);
  }

  public final void arrayCopyWriteBarrier(VM_Address src, int startIndex, 
				    int endIndex)
    throws VM_PragmaInline {
    src = src.add(startIndex<<LOG_WORD_SIZE);
    for (int idx = startIndex; idx <= endIndex; idx++) {
      VM_Address tgt = VM_Magic.getMemoryAddress(src);
      writeBarrier(src, tgt);
      src = src.add(WORD_SIZE);
    }
  }

  private final void writeBarrier(VM_Address src, VM_Address tgt) 
    throws VM_PragmaInline {
    if (GATHER_WRITE_BARRIER_STATS) wbFastPathCounter++;
    if (src.LT(NURSERY_START) && tgt.GE(NURSERY_START)) {
      if (GATHER_WRITE_BARRIER_STATS) wbSlowPathCounter++;
      remset.insert(src);
    }
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

    int pages = nurseryMR.reservedPages()<<1;
    pages += matureMR.reservedPages()<<(Plan.copyMature ? 1 : 0);
    
    pages += (Plan.usesLOS) ? losMR.reservedPages() : 0;
    pages += immortalMR.reservedPages();
    pages += metaDataMR.reservedPages();
    return pages;
  }

  protected static final int getPagesUsed() {
    int pages = nurseryMR.reservedPages();
    pages += matureMR.reservedPages();
    pages += (Plan.usesLOS) ? losMR.reservedPages() : 0;
    pages += immortalMR.reservedPages();
    pages += metaDataMR.reservedPages();
    return pages;
  }

  // Assuming all future allocation comes from copying space
  //
  protected static final int getPagesAvail() {
    int copyReserved = nurseryMR.reservedPages();
    int nonCopyReserved = ((Plan.usesLOS) ? losMR.reservedPages() : 0) +immortalMR.reservedPages() + metaDataMR.reservedPages();
    if (Plan.copyMature)
      copyReserved += matureMR.reservedPages();
    else
      nonCopyReserved += matureMR.reservedPages();

    return (getTotalPages() - nonCopyReserved)>>1 - copyReserved;
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Miscellaneous
  //
  abstract void showMature();
  public final void show() {
    nursery.show();
    showMature();
    if (Plan.usesLOS) los.show();
    immortal.show();
  }

  public static void showUsage() {
      VM.sysWrite("used pages = ", Plan.getPagesUsed());
      VM.sysWrite(" ("); VM.sysWrite(Conversions.pagesToBytes(Plan.getPagesUsed()) >> 20, " Mb) ");
      VM.sysWrite("= (nursery) ", nurseryMR.reservedPages());  
      VM.sysWrite(" + (mature) ", matureMR.reservedPages());  
      if (Plan.usesLOS) VM.sysWrite(" + (los) ", losMR.reservedPages());
      VM.sysWrite(" + (imm) ", immortalMR.reservedPages());
      VM.sysWriteln(" + (md)",  metaDataMR.reservedPages());
  }
}

