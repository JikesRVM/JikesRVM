/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_Extent;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;

/**
 * This class implements a simple mark-sweep collector.
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
  public final static String Id = "$Id$"; 

  /****************************************************************************
   *
   * Class variables
   */
  public static final boolean MOVES_OBJECTS = false;

  // virtual memory resources
  private static FreeListVMResource msVM;
  private static FreeListVMResource losVM;

  // memory resources
  private static MemoryResource msMR;
  private static MemoryResource losMR;

  // MS collector
  private static MarkSweepSpace msSpace;
  private static TreadmillSpace losSpace;

  // GC state
  private static int msReservedPages;
  private static int availablePreGC;

  // Allocators
  public static final byte MS_SPACE = 0;
  private static final byte LOS_SPACE = 1;
  public static final byte DEFAULT_SPACE = MS_SPACE;

  // Miscellaneous constants
  // XXX 512<<10 should be a named constant
  private static final int MS_PAGE_RESERVE = (512<<10)>>>LOG_BYTES_IN_PAGE; // 1M
  private static final double MS_RESERVE_FRACTION = 0.1;
  private static final int POLL_FREQUENCY = DEFAULT_POLL_FREQUENCY;
  // XXX Can 8 * 1024 be calculated from other constants?
  private static final int LOS_SIZE_THRESHOLD = 8 * 1024; // largest size supported by MS

  // Memory layout constants
  public  static final long            AVAILABLE = VM_Interface.MAXIMUM_MAPPABLE.diff(PLAN_START).toLong();
  private static final VM_Extent        LOS_SIZE = Conversions.roundDownMB(VM_Extent.fromIntZeroExtend((int)(AVAILABLE * 0.3)));
  private static final VM_Extent         MS_SIZE = Conversions.roundDownMB(VM_Extent.fromIntZeroExtend((int)(AVAILABLE * 0.7)));
  public  static final VM_Extent        MAX_SIZE = MS_SIZE;

  private static final VM_Address      LOS_START = PLAN_START;
  private static final VM_Address        LOS_END = LOS_START.add(LOS_SIZE);
  private static final VM_Address       MS_START = LOS_END;
  private static final VM_Address         MS_END = MS_START.add(MS_SIZE);
  private static final VM_Address       HEAP_END = MS_END;

  /****************************************************************************
   *
   * Instance variables
   */

  // allocators
  private MarkSweepLocal ms;
  private TreadmillLocal los;

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
    msMR = new MemoryResource("ms", POLL_FREQUENCY);
    losMR = new MemoryResource("los", POLL_FREQUENCY);
    msVM = new FreeListVMResource(MS_SPACE, "MS",       MS_START,   MS_SIZE, VMResource.IN_VM);
    losVM = new FreeListVMResource(LOS_SPACE, "LOS", LOS_START, LOS_SIZE, VMResource.IN_VM);
    msSpace = new MarkSweepSpace(msVM, msMR);
    losSpace = new TreadmillSpace(losVM, losMR);

    addSpace(MS_SPACE, "Mark-sweep Space");
    addSpace(LOS_SPACE, "LOS Space");
  }

  /**
   * Constructor
   */
  public Plan() {
    ms = new MarkSweepLocal(msSpace, this);
    los = new TreadmillLocal(losSpace);
  }

  /**
   * The boot method is called early in the boot process before any
   * allocation.
   */
  public static final void boot()
    throws VM_PragmaInterruptible {
    StopTheWorldGC.boot();
    msReservedPages = (int) (getTotalPages() * MS_RESERVE_FRACTION);
  }


  /****************************************************************************
   *
   * Allocation
   */

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
    if (GATHER_MARK_CONS_STATS) cons.inc(bytes);
    if (VM_Interface.VerifyAssertions) 
      VM_Interface._assert(bytes == (bytes & (~(BYTES_IN_ADDRESS-1))));
    VM_Address region;
    if (allocator == DEFAULT_SPACE && bytes > LOS_SIZE_THRESHOLD) {
      region = los.alloc(isScalar, bytes);
    } else {
      switch (allocator) {
      case       MS_SPACE: region = ms.alloc(isScalar, bytes, false); break;
      case      LOS_SPACE: region = los.alloc(isScalar, bytes); break;
      case IMMORTAL_SPACE: region = immortal.alloc(isScalar, bytes); break;
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
  public final void postAlloc(VM_Address ref, Object[] tib, int size,
                              boolean isScalar, int allocator)
    throws VM_PragmaInline {
    if (allocator == DEFAULT_SPACE && size > LOS_SIZE_THRESHOLD) {
      Header.initializeLOSHeader(ref, tib, size, isScalar);
    } else {
      switch (allocator) {
      case       MS_SPACE: return;
      case      LOS_SPACE: Header.initializeLOSHeader(ref, tib, size, isScalar); return;
      case IMMORTAL_SPACE: ImmortalSpace.postAlloc(ref); return;
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
                                    boolean isScalar) throws VM_PragmaInline {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(false);
    // return VM_Address.zero();  this trips some Intel assembler bug
    return VM_Address.max();
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
   * Return the initial header value for a newly allocated instance.
   *
   * @param bytes The size of the newly created instance in bytes.
   * @return The inital header value for the new instance.
   */
  public static final VM_Word getInitialHeaderValue(int size) 
    throws VM_PragmaInline {
    if (size > LOS_SIZE_THRESHOLD)
      return losSpace.getInitialHeaderValue(size);
    else
      return msSpace.getInitialHeaderValue();  // NEW MS
  }

  protected final byte getSpaceFromAllocator (Allocator a) {
    if (a == ms) return DEFAULT_SPACE;
    if (a == los) return LOS_SPACE;
    return super.getSpaceFromAllocator(a);
  }

  protected final Allocator getAllocatorFromSpace (byte s) {
    if (s == DEFAULT_SPACE) return ms;
    if (s == LOS_SPACE) return los;
    return super.getAllocatorFromSpace(s);
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
   * XXX No Javadoc params.
   *
   * @return Whether a collection is triggered
   */
  public final boolean poll(boolean mustCollect, MemoryResource mr)
    throws VM_PragmaLogicallyUninterruptible {
    if (collectionsInitiated > 0 || !initialized || mr == metaDataMR)
      return false;
    mustCollect |= stressTestGCRequired() || ms.mustCollect();
    availablePreGC = getTotalPages() - getPagesReserved();
    int reserve = (mr == msMR) ? msReservedPages : 0;
    if (mustCollect || availablePreGC <= reserve) {
      required = mr.reservedPages() - mr.committedPages();
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
   * Perform operations with <i>global</i> scope in preparation for a
   * collection.  This is called by <code>StopTheWorld</code>, which will
   * ensure that <i>only one thread</i> executes this.<p>
   *
   * In this case, it means preparing each of the collectors.
   */
  protected final void globalPrepare() {
    msSpace.prepare(msVM, msMR);
    ImmortalSpace.prepare(immortalVM, null);
    losSpace.prepare(losVM, losMR);
  }

  /**
   * Perform operations with <i>thread-local</i> scope in preparation
   * for a collection.  This is called by <code>StopTheWorld</code>, which
   * will ensure that <i>all threads</i> execute this.<p>
   *
   * In this case, it means resetting mark sweep allocator.
   * XXX No Javadoc params.
   */
  protected final void threadLocalPrepare(int count) {
    ms.prepare();
    los.prepare();
  }

  /**
   * Perform operations with <i>thread-local</i> scope to clean up at
   * the end of a collection.  This is called by
   * <code>StopTheWorld</code>, which will ensure that <i>all threads</i>
   * execute this.<p>
   *
   * In this case, it means releasing the mark sweep space (which
   * triggers the sweep phase of the mark-sweep collector).
   * XXX No Javadoc params.
   */
  protected final void threadLocalRelease(int count) {
    ms.release();
    los.release();
  }

  /**
   * Perform operations with <i>global</i> scope to clean up at the
   * end of a collection.  This is called by <code>StopTheWorld</code>,
   * which will ensure that <i>only one</i> thread executes this.<p>
   *
   * In this case, it means releasing each of the spaces and checking
   * whether the GC made progress.
   */
  protected final void globalRelease() {
    // release each of the collected regions
    losSpace.release();
    msSpace.release();
    ImmortalSpace.release(immortalVM, null);
    int available = getTotalPages() - getPagesReserved();

    progress = (available > availablePreGC) && (available > exceptionReserve);
    if (progress) {
      msReservedPages = (int) (available * MS_RESERVE_FRACTION);
      int threshold = 2 * exceptionReserve;
      if (threshold < MS_PAGE_RESERVE) threshold = MS_PAGE_RESERVE;
      if (msReservedPages < threshold) 
        msReservedPages = threshold;
    } else {
      msReservedPages = msReservedPages/2;
    }
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
    switch (space) {
    case MS_SPACE:        return msSpace.traceObject(obj, VMResource.getTag(addr));
    case LOS_SPACE:       return losSpace.traceObject(obj);
    case IMMORTAL_SPACE:  return ImmortalSpace.traceObject(obj);
    case BOOT_SPACE:      return ImmortalSpace.traceObject(obj);
    case META_SPACE:      return obj;
    default:
      if (VM_Interface.VerifyAssertions) 
        spaceFailure(obj, space, "Plan.traceObject()");
      return obj;
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
  public static final VM_Address traceObject(VM_Address obj, boolean root) {
    return traceObject(obj);  // root or non-root is of no consequence here
  }


  /**
   * Return true if <code>obj</code> is a live object.
   *
   * @param obj The object in question
   * @return True if <code>obj</code> is a live object.
   */
  public static final boolean isLive(VM_Address obj) {
    if (obj.isZero()) return false;
    VM_Address addr = VM_Interface.refToAddress(obj);
    byte space = VMResource.getSpace(addr);
    switch (space) {
    case MS_SPACE:        return msSpace.isLive(obj);
    case LOS_SPACE:       return losSpace.isLive(obj);
    case IMMORTAL_SPACE:  return true;
    case BOOT_SPACE:      return true;
    case META_SPACE:      return true;
    default:
      if (VM_Interface.VerifyAssertions) 
        spaceFailure(obj, space, "Plan.isLive()");
      return false;
    }
  }

  /**
   * Reset the GC bits in the header word of an object that has just
   * been copied.  This may, for example, involve clearing a write
   * barrier bit.  <i>This is not a copying collector, so this method
   * should never be called</i>.
   *
   * @param fromObj The original (uncopied) object
   * @param forwardingWord The integer containing the GC bits, which is the GC word
   * of the original object, and typically encodes some GC state as
   * well as pointing to the copied object.
   * @param bytes The size of the copied object in bytes.
   * @return The updated GC word (in this case unchanged).
   */
  public static final VM_Word resetGCBitsForCopy(VM_Address fromObj, 
					     VM_Word forwardingWord, int bytes) {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(false);  // not a copying collector!
    return forwardingWord;
  }


  /****************************************************************************
   *
   * Space management
   */

  /**
   * Return the number of pages reserved for use given the pending
   * allocation.
   *
   * @return The number of pages reserved given the pending
   * allocation, including space reserved for copying.
   */
  protected static final int getPagesReserved() {
    return getPagesUsed();
  }

  /**
   * Return the number of pages reserved for use given the pending
   * allocation.
   *
   * @return The number of pages reserved given the pending
   * allocation, excluding space reserved for copying.
   */
  protected static final int getPagesUsed() {
    int pages = msMR.reservedPages();
    pages += losMR.reservedPages();
    pages += immortalMR.reservedPages();
    pages += metaDataMR.reservedPages();
    return pages;
  }

  /**
   * Return the number of pages available for allocation.
   *
   * @return The number of pages available for allocation.
   */
  protected static final int getPagesAvail() {
    return getTotalPages() - msMR.reservedPages() - losMR.reservedPages() - immortalMR.reservedPages();
  }


  /****************************************************************************
   *
   * Miscellaneous
   */

  /**
   * Show the status of each of the allocators.
   */
  public final void show() {
    ms.show();
    los.show();
    immortal.show();
  }

  protected final void planExit(int value) {
    ms.exit();
  }
}
