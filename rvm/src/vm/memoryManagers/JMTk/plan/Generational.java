/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.plan;

import org.mmtk.policy.CopySpace;
import org.mmtk.policy.ImmortalSpace;
import org.mmtk.policy.TreadmillSpace;
import org.mmtk.policy.TreadmillLocal;
import org.mmtk.utility.AllocAdvice;
import org.mmtk.utility.Allocator;
import org.mmtk.utility.BumpPointer;
import org.mmtk.utility.CallSite;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.FreeListVMResource;
import org.mmtk.utility.HeapGrowthManager;
import org.mmtk.utility.Log;
import org.mmtk.utility.Memory;
import org.mmtk.utility.MemoryResource;
import org.mmtk.utility.MonotoneVMResource;
import org.mmtk.utility.MMType;
import org.mmtk.utility.Options;
import org.mmtk.utility.Scan;
import org.mmtk.utility.statistics.*;
import org.mmtk.utility.WriteBuffer;
import org.mmtk.utility.VMResource;
import org.mmtk.vm.VM_Interface;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Extent;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;

/**
 * This abstract class implements the core functionality of generic
 * two-generationa copying collectors.  Nursery collections occur when
 * either the heap is full or the nursery is full.  The nursery size
 * is determined by an optional command line argument.  If undefined,
 * the nursery size is "infinite", so nursery collections only occur
 * when the heap is full (this is known as a flexible-sized nursery
 * collector).  Thus both fixed and flexible nursery sizes are
 * supported.  Full heap collections occur when the nursery size has
 * dropped to a statically defined threshold,
 * <code>NURSERY_THRESHOLD</code><p>
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

  /****************************************************************************
   *
   * Class variables
   */
  public static final boolean NEEDS_WRITE_BARRIER = true;
  public static final boolean MOVES_OBJECTS = true;

  // virtual memory resources
  protected static MonotoneVMResource nurseryVM;
  protected static FreeListVMResource losVM;

  // memory resources
  protected static MemoryResource nurseryMR;
  protected static MemoryResource matureMR;
  protected static MemoryResource losMR;

  // large object space (LOS) collector
  protected static TreadmillSpace losSpace;

  // GC state
  protected static boolean fullHeapGC = false;  // Whether next GC will be full - set at end of last GC
  protected static boolean lastGCFull = false;  // Whether previous GC was full - set during full GC

  protected static EventCounter wbFast;
  protected static EventCounter wbSlow;
  protected static BooleanCounter fullHeap;
  protected static SizeCounter nurseryMark;
  protected static SizeCounter nurseryCons;

  // Allocators
  protected static final byte NURSERY_SPACE = 0;
  protected static final byte MATURE_SPACE = 1;
  protected static final byte LOS_SPACE = 2;
  public static final byte DEFAULT_SPACE = NURSERY_SPACE;
  public static final byte TIB_SPACE = DEFAULT_SPACE;


  // Miscellaneous constants
  protected static final int POLL_FREQUENCY = DEFAULT_POLL_FREQUENCY;
  protected static final float SURVIVAL_ESTIMATE = (float) 0.8; // est yield
  protected static final int LOS_SIZE_THRESHOLD = 8 * 1024; // largest size supported by MS

  // Memory layout constants
  // Note: The write barrier depends on the nursery being the highest
  // memory region.
  public    static final long           AVAILABLE = VM_Interface.MAXIMUM_MAPPABLE.diff(PLAN_START).toLong();
  protected static final VM_Extent MATURE_SS_SIZE = Conversions.roundDownMB(VM_Extent.fromIntZeroExtend((int)(AVAILABLE / 3.3)));
  protected static final VM_Extent   NURSERY_SIZE = MATURE_SS_SIZE;
  protected static final VM_Extent       LOS_SIZE = Conversions.roundDownMB(VM_Extent.fromIntZeroExtend((int)(AVAILABLE / 3.3 * 0.3)));
  public    static final VM_Extent       MAX_SIZE = MATURE_SS_SIZE.add(MATURE_SS_SIZE);
  protected static final VM_Address     LOS_START = PLAN_START;
  protected static final VM_Address       LOS_END = LOS_START.add(LOS_SIZE);
  protected static final VM_Address  MATURE_START = LOS_END;
  protected static final VM_Extent    MATURE_SIZE = MATURE_SS_SIZE.add(MATURE_SS_SIZE);
  protected static final VM_Address    MATURE_END = MATURE_START.add(MATURE_SIZE);
  protected static final VM_Address NURSERY_START = MATURE_END;
  protected static final VM_Address   NURSERY_END = NURSERY_START.add(NURSERY_SIZE);
  protected static final VM_Address      HEAP_END = NURSERY_END;

  /****************************************************************************
   *
   * Instance variables
   */

  // allocators
  protected BumpPointer nursery;
  protected TreadmillLocal los;

  // write buffer (remembered set)
  protected WriteBuffer remset;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time). This is where key <i>global</i> instances
   * are allocated.  These instances will be incorporated into the
   * boot image by the build process.
   */
  static {
    nurseryMR = new MemoryResource("nur", POLL_FREQUENCY);
    matureMR = new MemoryResource("mat", POLL_FREQUENCY);
    nurseryVM  = new MonotoneVMResource(NURSERY_SPACE, "Nursery", nurseryMR,   NURSERY_START, NURSERY_SIZE, VMResource.MOVABLE);
    addSpace(NURSERY_SPACE, "Nursery");
    addSpace(MATURE_SPACE, "Mature Space");

    losMR = new MemoryResource("los", POLL_FREQUENCY);
    losVM = new FreeListVMResource(LOS_SPACE, "LOS", LOS_START, LOS_SIZE, VMResource.IN_VM);
    losSpace = new TreadmillSpace(losVM, losMR);
    addSpace(LOS_SPACE, "LOS Space");

    fullHeap = new BooleanCounter("majorGC", true, true);
    if (GATHER_WRITE_BARRIER_STATS) {
      wbFast = new EventCounter("wbFast");
      wbSlow = new EventCounter("wbSlow");
    }
    if (GATHER_MARK_CONS_STATS) {
      nurseryMark = new SizeCounter("nurseryMark", true, true);
      nurseryCons = new SizeCounter("nurseryCons", true, true);
    }
  }
  
  /**
   * Constructor
   */
  public Generational() {
    nursery = new BumpPointer(nurseryVM);
    los = new TreadmillLocal(losSpace);
    remset = new WriteBuffer(remsetPool);
  }

  /**
   * The boot method is called early in the boot process before any
   * allocation
   */
  public static final void boot() throws VM_PragmaInterruptible {
    StopTheWorldGC.boot();
  }


  /****************************************************************************
   *
   * Allocation
   */
  abstract VM_Address matureAlloc(boolean isScalar, int bytes);
  abstract VM_Address matureCopy(boolean isScalar, int bytes);

  /**
   * Allocate space (for an object)
   *
   * @param bytes The size of the space to be allocated (in bytes)
   * @param isScalar True if the object occupying this space will be a scalar
   * @param allocator The allocator number to be used for this allocation
   * @param advice Statically-generated allocation advice for this allocation
   * @return The address of the first byte of the allocated region
   */
  public final VM_Address alloc(int bytes, boolean isScalar, int allocator,
                                AllocAdvice advice)
    throws VM_PragmaInline {
    if (GATHER_MARK_CONS_STATS) nurseryCons.inc(bytes);
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(bytes == (bytes & (~(BYTES_IN_ADDRESS-1))));
    VM_Address region;
    if (allocator == NURSERY_SPACE && bytes > LOS_SIZE_THRESHOLD) {
      region = los.alloc(isScalar, bytes);
    } else {
      switch (allocator) {
      case  NURSERY_SPACE: region = nursery.alloc(isScalar, bytes); break;
      case   MATURE_SPACE: region = matureAlloc(isScalar, bytes); break;
      case IMMORTAL_SPACE: region = immortal.alloc(isScalar, bytes); break;
      case      LOS_SPACE: region = los.alloc(isScalar, bytes); break;
      default:
        if (VM_Interface.VerifyAssertions) 
          VM_Interface.sysFail("No such allocator");
        region = VM_Address.zero();
      }
    }
    if (VM_Interface.VerifyAssertions) Memory.assertIsZeroed(region, bytes);
    return region;
  }

  /**
   * Perform post-allocation actions.  For many allocators none are
   * required.
   *
   * @param ref The newly allocated object
   * @param tib The TIB of the newly allocated object
   * @param bytes The size of the space to be allocated (in bytes)
   * @param isScalar True if the object occupying this space will be a scalar
   * @param allocator The allocator number to be used for this allocation
   */
  public final void postAlloc(VM_Address ref, Object[] tib, int bytes,
                              boolean isScalar, int allocator)
    throws VM_PragmaInline {
    if (allocator == NURSERY_SPACE && bytes > LOS_SIZE_THRESHOLD) {
      Header.initializeLOSHeader(ref, tib, bytes, isScalar);
    } else {
      switch (allocator) {
      case  NURSERY_SPACE: return;
      case   MATURE_SPACE: if (!Plan.copyMature) Header.initializeMarkSweepHeader(ref, tib, bytes, isScalar); return;
      case IMMORTAL_SPACE: ImmortalSpace.postAlloc(ref); return;
      case      LOS_SPACE: Header.initializeMarkSweepHeader(ref, tib, bytes, isScalar); return;
      default:
        if (VM_Interface.VerifyAssertions)
          VM_Interface.sysFail("No such allocator");
      }
    }
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
  public final VM_Address allocCopy(VM_Address original, int bytes,
                                    boolean isScalar) 
    throws VM_PragmaInline {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(bytes <= LOS_SIZE_THRESHOLD);
    if (GATHER_MARK_CONS_STATS) {
      cons.inc(bytes);
      if (fullHeapGC) mark.inc(bytes);
      if (original.GE(NURSERY_START)) nurseryMark.inc(bytes);
    }
    return matureCopy(isScalar, bytes);
  }

  /**  
   * Perform any post-copy actions.  In this case nothing is required.
   *
   * @param ref The newly allocated object
   * @param tib The TIB of the newly allocated object
   * @param bytes The size of the space to be allocated (in bytes)
   * @param isScalar True if the object occupying this space will be a scalar
   */
  public final void postCopy(VM_Address ref, Object[] tib, int size,
                             boolean isScalar) {} // do nothing

  protected byte getSpaceFromAllocator (Allocator a) {
    if (a == nursery) return NURSERY_SPACE;
    if (a == los) return LOS_SPACE;
    return super.getSpaceFromAllocator(a);
  }

  protected Allocator getAllocatorFromSpace (byte s) {
    if (s == NURSERY_SPACE) return nursery;
    if (s == LOS_SPACE) return los;
    return super.getAllocatorFromSpace(s);
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
  public final AllocAdvice getAllocAdvice(MMType type, int bytes,
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
   * <code>collect()</code> method of this class or its superclass.<p>
   *
   * This method is clearly interruptible since it can lead to a GC.
   * However, the caller is typically uninterruptible and this fiat allows 
   * the interruptibility check to work.  The caveat is that the caller 
   * of this method must code as though the method is interruptible. 
   * In practice, this means that, after this call, processor-specific
   * values must be reloaded.
   *
   * @param mustCollect True if a this collection is forced.
   * @param mr The memory resource that triggered this collection.
   * @return True if a collection is triggered
   */
  public final boolean poll(boolean mustCollect, MemoryResource mr) 
    throws VM_PragmaLogicallyUninterruptible {
    if (collectionsInitiated > 0 || !initialized || mr == metaDataMR)
      return false;
    mustCollect |= stressTestGCRequired();
    boolean heapFull = getPagesReserved() > getTotalPages();
    boolean nurseryFull = nurseryMR.reservedPages() > Options.maxNurseryPages;
    if (mustCollect || heapFull || nurseryFull) {
      required = mr.reservedPages() - mr.committedPages();
      if (mr == nurseryMR || (Plan.copyMature && (mr == matureMR)))
        required = required<<1;  // must account for copy reserve
      int nurseryYield = ((int)((float) nurseryMR.committedPages() * SURVIVAL_ESTIMATE))<<1;
      fullHeapGC = mustCollect || (nurseryYield < required) || fullHeapGC;
      VM_Interface.triggerCollection(VM_Interface.RESOURCE_GC_TRIGGER);
      return true;
    }
    return false;
  }

  /****************************************************************************
   *
   * Collection
   *
   * Important notes:
   *   . Global actions are executed by only one thread
   *   . Thread-local actions are executed by all threads
   *   . The following order is guaranteed by BasePlan, with each
   *     separated by a synchronization barrier.:
   *      1. globalPrepare()
   *      2. threadLocalPrepare()
   *      3. threadLocalRelease()
   *      4. globalRelease()
   */

  /**
   * A user triggered GC has been initated.  If the fullHeapSystemGC
   * option is true, then force a full heap collection.  Call the
   * corresponding method on our superclass.
   */
  public static void userTriggeredGC() throws VM_PragmaUninterruptible {
    fullHeapGC |= Options.fullHeapSystemGC;
    StopTheWorldGC.userTriggeredGC();
  }
  
  /**
   * Perform a collection.
   */
  public final void collect () {
    if ((Options.verbose >= 1) && (fullHeapGC)) Log.write("[Full heap]");
    super.collect();
  }

  abstract void globalMaturePrepare();
  /**
   * Perform operations with <i>global</i> scope in preparation for a
   * collection.  This is called by <code>BasePlan</code>, which will
   * ensure that <i>only one thread</i> executes this.<p>
   *
   * In this case, it means flipping semi-spaces, resetting the
   * semi-space memory resource, and preparing each of the collectors.
   */
  protected final void globalPrepare() {
    nurseryMR.reset(); // reset the nursery
    lastGCFull = fullHeapGC;
    if (fullHeapGC) {
      if (Stats.gatheringStats()) fullHeap.set();
      // prepare each of the collected regions
      losSpace.prepare(losVM, losMR);
      globalMaturePrepare();
      ImmortalSpace.prepare(immortalVM, null);

      // we can throw away the remsets for a full heap GC
      remsetPool.clearDeque(1);
    }
  }

  abstract void threadLocalMaturePrepare(int count);
  /**
   * Perform operations with <i>thread-local</i> scope in preparation
   * for a collection.  This is called by <code>BasePlan</code>, which
   * will ensure that <i>all threads</i> execute this.<p>
   *
   * In this case, it means flushing the remsets, rebinding the
   * nursery, and if a full heap collection, preparing the mature
   * space and LOS.
   */
  protected final void threadLocalPrepare(int count) {
    nursery.rebind(nurseryVM);
    if (fullHeapGC) {
      threadLocalMaturePrepare(count);
      los.prepare();
      remset.resetLocal();  // we can throw away remsets for a full heap GC
    } else if (count == NON_PARTICIPANT)
      flushRememberedSets();
  }

  /**
   * Flush any remembered sets pertaining to the current collection.
   */
  protected final void flushRememberedSets() {
    remset.flushLocal();
  }

  abstract void threadLocalMatureRelease(int count);
  /**
   * Perform operations with <i>thread-local</i> scope to clean up at
   * the end of a collection.  This is called by
   * <code>BasePlan</code>, which will ensure that <i>all threads</i>
   * execute this.<p>
   *
   * In this case, it means flushing the remsets, and if a full heap
   * GC, releasing the large object space (which triggers the sweep
   * phase of the mark-sweep collector used by the LOS), and releasing
   * the mature space.
   */
  protected final void threadLocalRelease(int count) {
    if (fullHeapGC) { 
      los.release();
      threadLocalMatureRelease(count);
    }
    remset.flushLocal(); // flush any remset entries collected during GC
  }

  abstract void globalMatureRelease();
  /**
   * Perform operations with <i>global</i> scope to clean up at the
   * end of a collection.  This is called by <code>BasePlan</code>,
   * which will ensure that <i>only one</i> thread executes this.<p>
   *
   * In this case, it means releasing each of the spaces, determining
   * whether the next GC will be a full heap GC, and checking whether
   * the GC made progress.
   */
  protected void globalRelease() {
    // release each of the collected regions
    nurseryVM.release();
    remsetPool.clearDeque(1); // flush any remset entries collected during GC
    if (fullHeapGC) {
      losSpace.release();
      globalMatureRelease();
      ImmortalSpace.release(immortalVM, null);
    }
    fullHeapGC = (getPagesAvail() < Options.minNurseryPages);
    if (getPagesReserved() + required >= getTotalPages()) {
      progress = false;
    } else
      progress = true;
  }

  /**
   * @return Whether last GC is a full GC.
   */
  public static boolean isLastGCFull () {
    return lastGCFull;
  }

  /****************************************************************************
   *
   * Object processing and tracing
   */

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
    if (obj.isZero()) return obj;
    VM_Address addr = VM_Interface.refToAddress(obj);
    byte space = VMResource.getSpace(addr);
    if (space == NURSERY_SPACE)
      return CopySpace.traceObject(obj);
    if (!fullHeapGC)
      return obj;
    switch (space) {
    case LOS_SPACE:      return losSpace.traceObject(obj);
    case IMMORTAL_SPACE: return ImmortalSpace.traceObject(obj);
    case BOOT_SPACE:     return ImmortalSpace.traceObject(obj);
    case META_SPACE:     return obj;
    default:
      return Plan.traceMatureObject(space, obj, addr);
    }
  }

  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.
   *
   * @param obj The object reference to be traced.  This is <i>NOT</i>
   * an interior pointer.
   * @param root True if this reference to <code>obj</code> was held
   * in a root.
   * @return The possibly moved reference.
   */
  public static final VM_Address traceObject(VM_Address obj, boolean root)
    throws VM_PragmaInline {
    return traceObject(obj);  // root or non-root is of no consequence here
  }


  /**
   * Forward the object referred to by a given address and update the
   * address if necessary.  This <i>does not</i> enqueue the referent
   * for processing; the referent must be explicitly enqueued if it is
   * to be processed.
   *
   * @param location The location whose referent is to be forwarded if
   * necessary.  The location will be updated if the referent is
   * forwarded.
   */
  public static final void forwardObjectLocation(VM_Address location) 
    throws VM_PragmaInline {
    VM_Address obj = VM_Magic.getMemoryAddress(location);
    if (!obj.isZero()) {
      VM_Address addr = VM_Interface.refToAddress(obj);
      byte space = VMResource.getSpace(addr);
      if (space == NURSERY_SPACE) 
        VM_Magic.setMemoryAddress(location, CopySpace.forwardObject(obj));
      else if (fullHeapGC) 
        Plan.forwardMatureObjectLocation(location, obj, space);
    }
  }

  /**
   * Scan an object that was previously forwarded but not scanned.
   * The separation between forwarding and scanning is necessary for
   * the "pre-copying" mechanism to function properly.
   *
   * @param object The object to be scanned.
   */
  protected final void scanForwardedObject(VM_Address object) {
    Scan.scanObject(object);
  }

  /**
   * If the object in question has been forwarded, return its
   * forwarded value.<p>
   *
   * @param object The object which may have been forwarded.
   * @return The forwarded value for <code>object</code>.
   */
  public static final VM_Address getForwardedReference(VM_Address object) {
    if (!object.isZero()) {
      VM_Address addr = VM_Interface.refToAddress(object);
      byte space = VMResource.getSpace(addr);
      if (space == NURSERY_SPACE) {
        if (VM_Interface.VerifyAssertions) 
          VM_Interface._assert(CopyingHeader.isForwarded(object));
        return CopyingHeader.getForwardingPointer(object);
      } else if (fullHeapGC)
        return Plan.getForwardedMatureReference(object, space);
    }
    return object;
  }

  /****************************************************************************
   *
   * Write barriers. 
   */

  /**
   * A new reference is about to be created.  Take appropriate write
   * barrier actions.<p> 
   *
   * In this case, we remember the address of the source of the
   * pointer if the new reference points into the nursery from
   * non-nursery space.
   *
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be
   * stored.
   * @param tgt The target of the new reference
   * @param mode The mode of the store (eg putfield, putstatic etc)
   */
  public final void writeBarrier(VM_Address src, VM_Address slot,
                                 VM_Address tgt, int mode) 
    throws VM_PragmaInline {
    if (GATHER_WRITE_BARRIER_STATS) wbFast.inc();
    if (slot.LT(NURSERY_START) && tgt.GE(NURSERY_START)) {
      if (GATHER_WRITE_BARRIER_STATS) wbSlow.inc();
      remset.insert(slot);
    }
    VM_Magic.setMemoryAddress(slot, tgt);
  }

  /****************************************************************************
   *
   * Space management
   */

  /**
   * Return the number of pages reserved for use given the pending
   * allocation.  This <i>includes</i> space reserved for copying.
   *
   * @return The number of pages reserved given the pending
   * allocation, including space reserved for copying.
   */
  protected static final int getPagesReserved() {
    return getPagesUsed()
      + nurseryMR.reservedPages()
      + (Plan.copyMature ? matureMR.reservedPages() : 0);
  }

  /**
   * Return the number of pages reserved for use given the pending
   * allocation.  This is <i>exclusive of</i> space reserved for
   * copying.
   *
   * @return The number of pages reserved given the pending
   * allocation, excluding space reserved for copying.
   */
  protected static final int getPagesUsed() {
    int pages = nurseryMR.reservedPages();
    pages += matureMR.reservedPages();
    pages += losMR.reservedPages();
    pages += immortalMR.reservedPages();
    pages += metaDataMR.reservedPages();
    return pages;
  }

  /**
   * Return the number of pages available for allocation, <i>assuming
   * all future allocation is to the nursery</i>.
   *
   * @return The number of pages available for allocation, <i>assuming
   * all future allocation is to the nursery</i>.
   */
  protected static final int getPagesAvail() {
    int copyReserved = nurseryMR.reservedPages();
    int nonCopyReserved = losMR.reservedPages() + immortalMR.reservedPages() + metaDataMR.reservedPages();
    if (Plan.copyMature)
      copyReserved += matureMR.reservedPages();
    else
      nonCopyReserved += matureMR.reservedPages();

    return ((getTotalPages() - nonCopyReserved)>>1) - copyReserved;
  }


  /****************************************************************************
   *
   * Miscellaneous
   */

  abstract void showMature();
  /**
   * Show the status of each of the allocators.
   */
  public final void show() {
    nursery.show();
    showMature();
    los.show();
    immortal.show();
  }
}
