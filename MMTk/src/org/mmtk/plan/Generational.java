/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.plan;

import org.mmtk.policy.CopySpace;
import org.mmtk.policy.ImmortalSpace;
import org.mmtk.policy.Space;
import org.mmtk.utility.alloc.AllocAdvice;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.alloc.BumpPointer;
import org.mmtk.utility.CallSite;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.heap.*;
import org.mmtk.utility.Log;
import org.mmtk.utility.Options;
import org.mmtk.utility.deque.*;
import org.mmtk.utility.scan.*;
import org.mmtk.utility.statistics.*;
import org.mmtk.vm.Assert;
import org.mmtk.vm.Barriers;
import org.mmtk.vm.Collection;
import org.mmtk.vm.Memory;
import org.mmtk.vm.ObjectModel;
import org.mmtk.vm.Plan;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

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
  implements Uninterruptible {
  public static final String Id = "$Id$"; 

  /****************************************************************************
   *
   * Class variables
   */
  public static final boolean NEEDS_WRITE_BARRIER = true;
  public static final boolean MOVES_OBJECTS = true;
  public static final int GC_HEADER_BITS_REQUIRED = CopySpace.LOCAL_GC_BITS_REQUIRED;
  public static final int GC_HEADER_BYTES_REQUIRED = CopySpace.GC_HEADER_BYTES_REQUIRED;
  public static final boolean IGNORE_REMSET = false;    // always do full trace

  // Global pool for shared remset queue
  private static SharedDeque arrayRemsetPool = new SharedDeque(metaDataSpace, 2);

  // GC state
  protected static boolean fullHeapGC = false; // Will next GC be full heap?
  protected static boolean lastGCFull = false; // Was last GC full heap?
  protected static Space activeMatureSpace;  // initialized by subclass

  // Allocators
  protected static final int ALLOC_NURSERY = ALLOC_DEFAULT;
  protected static final int ALLOC_MATURE = BASE_ALLOCATORS;
  public static final int ALLOCATORS = ALLOC_MATURE + 1;

  // Miscellaneous constants
  protected static final float SURVIVAL_ESTIMATE = (float) 0.8; // est yield

  // Create the nursery
  protected static CopySpace nurserySpace = new CopySpace("nursery", 
							  DEFAULT_POLL_FREQUENCY, 
							  (float) 0.15, true, 
							  false);
  protected static final int NS = nurserySpace.getID();
  protected static final Address NURSERY_START = nurserySpace.getStart();

  // Statistics
  protected static EventCounter wbFast;
  protected static EventCounter wbSlow;
  protected static BooleanCounter fullHeap;
  protected static SizeCounter nurseryMark;
  protected static SizeCounter nurseryCons;
  private static Timer fullHeapTime = new Timer("majorGCTime", false, true);

  /****************************************************************************
   *
   * Instance variables
   */

  // allocators
  protected BumpPointer nursery;

  // write buffer (remembered set)
  protected WriteBuffer remset;
  protected AddressPairDeque arrayRemset;

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
    fullHeap = new BooleanCounter("majorGC", true, true);
    if (GATHER_WRITE_BARRIER_STATS) {
      wbFast = new EventCounter("wbFast");
      wbSlow = new EventCounter("wbSlow");
    }
    if (Stats.GATHER_MARK_CONS_STATS) {
      nurseryMark = new SizeCounter("nurseryMark", true, true);
      nurseryCons = new SizeCounter("nurseryCons", true, true);
    }
  }
  
  /**
   * Constructor
   */
  public Generational() {
    nursery = new BumpPointer(nurserySpace);
    remset = new WriteBuffer(remsetPool);
    arrayRemset = new AddressPairDeque(arrayRemsetPool);
    arrayRemsetPool.newClient();
  }

  /**
   * The boot method is called early in the boot process before any
   * allocation
   */
  public static final void boot() throws InterruptiblePragma {
    StopTheWorldGC.boot();
  }


  /****************************************************************************
   *
   * Allocation
   */
  abstract Address matureAlloc(int bytes, int align, int offset);
  abstract Address matureCopy(int bytes, int align, int offset);
  abstract void maturePostAlloc(Address object);

  /**
   * Allocate space (for an object)
   *
   * @param bytes The size of the space to be allocated (in bytes)
   * @param align The requested alignment.
   * @param offset The alignment offset.
   * @param allocator The allocator number to be used for this allocation
   * @return The address of the first byte of the allocated region
   */
  public final Address alloc(int bytes, int align, int offset, int allocator)
    throws InlinePragma {
    switch (allocator) {
    case  ALLOC_NURSERY: if (Stats.GATHER_MARK_CONS_STATS) nurseryCons.inc(bytes);
                         return nursery.alloc(bytes, align, offset);
    case   ALLOC_MATURE: return matureAlloc(bytes, align, offset);
    case ALLOC_IMMORTAL: return immortal.alloc(bytes, align, offset);
    case      ALLOC_LOS: return los.alloc(bytes, align, offset);
    default:
      if (Assert.VERIFY_ASSERTIONS) Assert.fail("No such allocator"); 
      return Address.zero();
    }
  }

  /**
   * Perform post-allocation actions.  For many allocators none are
   * required.
   *
   * @param object The newly allocated object
   * @param typeRef The type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   * @param allocator The allocator number to be used for this allocation
   */
  public final void postAlloc(Address object, Address typeRef, int bytes,
                              int allocator)
    throws InlinePragma {
    switch (allocator) {
    case  ALLOC_NURSERY: return;
    case   ALLOC_MATURE: maturePostAlloc(object); return;
    case ALLOC_IMMORTAL: ImmortalSpace.postAlloc(object); return;
    case      ALLOC_LOS: loSpace.initializeHeader(object); return;
    default:
      if (Assert.VERIFY_ASSERTIONS) Assert.fail("No such allocator"); 
    }
  }

  /**
   * Allocate space for copying an object (this method <i>does not</i>
   * copy the object, it only allocates space)
   *
   * @param original A reference to the original object
   * @param bytes The size of the space to be allocated (in bytes)
   * @param align The requested alignment.
   * @param offset The alignment offset.
   * @return The address of the first byte of the allocated region
   */
  public final Address allocCopy(Address original, int bytes,
                                    int align, int offset) 
    throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(bytes <= LOS_SIZE_THRESHOLD);
    if (Stats.GATHER_MARK_CONS_STATS) {
      if (Space.isInSpace(NS, original)) nurseryMark.inc(bytes);
    }
    return matureCopy(bytes, align, offset);
  }

  /**
   * Return the space into which an allocator is allocating.  This
   * particular method will match against those spaces defined at this
   * level of the class hierarchy.  Subclasses must deal with spaces
   * they define and refer to superclasses appropriately.  This exists
   * to support {@link BasePlan#getOwnAllocator(Allocator)}.
   *
   * @see BasePlan#getOwnAllocator(Allocator)
   * @param a An allocator
   * @return The space into which <code>a</code> is allocating, or
   * <code>null</code> if there is no space associated with
   * <code>a</code>.
   */
  protected Space getSpaceFromAllocator(Allocator a) {
    if (a == nursery) return nurserySpace;
    return super.getSpaceFromAllocator(a);
  }

  /**
   * Return the allocator instance associated with a space
   * <code>space</code>, for this plan instance.  This exists
   * to support {@link BasePlan#getOwnAllocator(Allocator)}.
   *
   * @see BasePlan#getOwnAllocator(Allocator)
   * @param space The space for which the allocator instance is desired.
   * @return The allocator instance associated with this plan instance
   * which is allocating into <code>space</code>, or <code>null</code>
   * if no appropriate allocator can be established.
   */
  protected Allocator getAllocatorFromSpace(Space space) {
    if (space == nurserySpace) return nursery;
    return super.getAllocatorFromSpace(space);
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
   * @see org.mmtk.policy.Space#acquire(int)
   * @param mustCollect if <code>true</code> then a collection is
   * required and must be triggered.  Otherwise a collection is only
   * triggered if we deem it necessary.
   * @param space the space that triggered the polling (i.e. the space
   * into which an allocation is about to occur).
   * @return True if a collection has been triggered
   */
  public final boolean poll(boolean mustCollect, Space space) 
    throws LogicallyUninterruptiblePragma {
    if (collectionsInitiated > 0 || !initialized || space == metaDataSpace)
      return false;
    mustCollect |= stressTestGCRequired();
    boolean heapFull = getPagesReserved() > getTotalPages();
    boolean nurseryFull = nurserySpace.reservedPages() > Options.maxNurseryPages;
    if (mustCollect || heapFull || nurseryFull) {
      required = space.reservedPages() - space.committedPages();
      if (space == nurserySpace || (Plan.COPY_MATURE() && (space == activeMatureSpace)))
        required = required<<1;  // must account for copy reserve
      int nurseryYield = ((int)((float) nurserySpace.committedPages() * SURVIVAL_ESTIMATE))<<1;
      fullHeapGC = mustCollect || (nurseryYield < required) || fullHeapGC;
      Collection.triggerCollection(Collection.RESOURCE_GC_TRIGGER);
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
  public static void userTriggeredGC() throws UninterruptiblePragma {
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
    nurserySpace.prepare(true);
    lastGCFull = fullHeapGC;
    if (fullHeapGC || IGNORE_REMSET) {
      if (fullHeapGC) fullHeapTime.start();
      if (Stats.gatheringStats()) fullHeap.set();
      // prepare each of the collected regions
      loSpace.prepare();
      immortalSpace.prepare();
      globalMaturePrepare();

      // we can throw away the remsets for a full heap GC
      remsetPool.clearDeque(1);
      arrayRemsetPool.clearDeque(2);
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
    nursery.rebind(nurserySpace);
    if (fullHeapGC || IGNORE_REMSET) {
      threadLocalMaturePrepare(count);
      los.prepare();
      // we can throw away remsets for a full heap GC
      remset.resetLocal();  
      arrayRemset.resetLocal();
    } else if (count == NON_PARTICIPANT)
      flushRememberedSets();
  }

  /**
   * Flush any remembered sets pertaining to the current collection.
   */
  protected final void flushRememberedSets() {
    arrayRemset.flushLocal();
    while (!arrayRemset.isEmpty()) {
      Address start = arrayRemset.pop1();
      Address guard = arrayRemset.pop2();
      while (start.LT(guard)) {
       	remset.insert(start);
	start = start.add(BYTES_IN_ADDRESS);
      }
    }
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
    if (fullHeapGC || IGNORE_REMSET) { 
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
    nurserySpace.release();
    remsetPool.clearDeque(1); // flush any remset entries collected during GC
    if (fullHeapGC || IGNORE_REMSET) {
      loSpace.release();
      globalMatureRelease();
      immortalSpace.release();
      if (fullHeapGC) fullHeapTime.stop();
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
   * @param object The object reference to be traced.  This is <i>NOT</i> an
   * interior pointer.
   * @return The possibly moved reference.
   */
  public static final Address traceObject(Address object) {
    if (object.isZero()) 
      return object;
    else if (object.GE(NURSERY_START))
      return CopySpace.forwardAndScanObject(object);
    else if (!fullHeapGC && !IGNORE_REMSET)
      return object;
    else
      return Plan.traceMatureObject(object);  
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
  public static final Address traceObject(Address obj, boolean root)
    throws InlinePragma {
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
  public static final void forwardObjectLocation(Address location) 
    throws InlinePragma {
    Address object = location.loadAddress();
    if (!object.isZero()) {
      if (Space.isInSpace(NS, object))
        location.store(CopySpace.forwardObject(object));
      else if (fullHeapGC) 
        Plan.forwardMatureObjectLocation(location, object);
    }
  }

  /**
   * Scan an object that was previously forwarded but not scanned.
   * The separation between forwarding and scanning is necessary for
   * the "pre-copying" mechanism to function properly.
   *
   * @param object The object to be scanned.
   */
  protected final void scanForwardedObject(Address object) {
    Scan.scanObject(object);
  }

  /**
   * If the object in question has been forwarded, return its
   * forwarded value.<p>
   *
   * @param object The object which may have been forwarded.
   * @return The forwarded value for <code>object</code>.
   */
  public static final Address getForwardedReference(Address object) {
    if (!object.isZero()) {
      if (Space.isInSpace(NS, object)) {
        if (Assert.VERIFY_ASSERTIONS)
	  Assert._assert(CopySpace.isForwarded(object));
        return CopySpace.getForwardingPointer(object);
      } else if (fullHeapGC)
        return Plan.getForwardedMatureReference(object);
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
   * @param metaDataA A field used by the VM to create a correct store.
   * @param metaDataB A field used by the VM to create a correct store.
   * @param mode The mode of the store (eg putfield, putstatic etc)
   */
  public final void writeBarrier(Address src, Address slot,
                                 Address tgt, int metaDataA, 
                                 int metaDataB, int mode) 
    throws InlinePragma {
    if (GATHER_WRITE_BARRIER_STATS) wbFast.inc();
    if (slot.LT(NURSERY_START) && tgt.GE(NURSERY_START)) {
      if (GATHER_WRITE_BARRIER_STATS) wbSlow.inc();
      remset.insert(slot);
    }
    Barriers.performWriteInBarrier(src, slot, tgt, metaDataA, metaDataB, mode);
  }

  /**
   * A number of references are about to be copied from object
   * <code>src</code> to object <code>dst</code> (as in an array
   * copy).  Thus, <code>dst</code> is the mutated object.  Take
   * appropriate write barrier actions.<p>
   *
   * In this case, we remember the mutated source address range and
   * will scan that address range at GC time.
   *
   * @param src The source of the values to copied
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
  public final boolean writeBarrier(Address src, int srcOffset,
				    Address dst, int dstOffset,
				    int bytes) 
    throws InlinePragma {
    if (dst.LT(NURSERY_START))
      arrayRemset.insert(dst.add(dstOffset), dst.add(dstOffset + bytes));
    return false;
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
      + nurserySpace.reservedPages()
      + (Plan.COPY_MATURE() ? activeMatureSpace.reservedPages() : 0);
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
    int pages = nurserySpace.reservedPages();
    pages += activeMatureSpace.reservedPages();
    pages += loSpace.reservedPages();
    pages += immortalSpace.reservedPages();
    pages += metaDataSpace.reservedPages();
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
    int copyReserved = nurserySpace.reservedPages();
    int nonCopyReserved = loSpace.reservedPages() + immortalSpace.reservedPages() + metaDataSpace.reservedPages();
    if (Plan.COPY_MATURE())
      copyReserved += activeMatureSpace.reservedPages();
    else
      nonCopyReserved += activeMatureSpace.reservedPages();

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
