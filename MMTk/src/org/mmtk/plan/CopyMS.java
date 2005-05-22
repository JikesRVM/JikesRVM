/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.plan;

import org.mmtk.policy.CopySpace;
import org.mmtk.policy.CopyLocal;
import org.mmtk.policy.ImmortalSpace;
import org.mmtk.policy.MarkSweepSpace;
import org.mmtk.policy.MarkSweepLocal;
import org.mmtk.policy.Space;
import org.mmtk.utility.alloc.AllocAdvice;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.CallSite;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.heap.*;
import org.mmtk.utility.Log;
import org.mmtk.utility.scan.*;
import org.mmtk.vm.Assert;
import org.mmtk.vm.Memory;
import org.mmtk.vm.ObjectModel;
import org.mmtk.vm.Collection;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

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
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 *
 * @version $Revision$
 * @date $Date$
 */
public class CopyMS extends StopTheWorldGC implements Uninterruptible {

  /****************************************************************************
   *
   * Class variables
   */
  public static final boolean MOVES_OBJECTS = true;
  public static final int GC_HEADER_BITS_REQUIRED = CopySpace.LOCAL_GC_BITS_REQUIRED;
  public static final int GC_HEADER_WORDS_REQUIRED = CopySpace.GC_HEADER_WORDS_REQUIRED;

  // Allocators
  private static final int ALLOC_NURSERY = ALLOC_DEFAULT;
  private static final int ALLOC_MS = BASE_ALLOCATORS;
  public static final int ALLOCATORS = ALLOC_MS + 1;

  // spaces
  private static final float MS_FRACTION= (float)0.50;
  private static final float NURSERY_FRACTION= (float)0.15;

  private static MarkSweepSpace msSpace = new MarkSweepSpace("ms", DEFAULT_POLL_FREQUENCY, MS_FRACTION);
  private static final int MS = msSpace.getDescriptor();
  protected static CopySpace nurserySpace = new CopySpace("nursery", DEFAULT_POLL_FREQUENCY, NURSERY_FRACTION, true, false);
  protected static final int NS = nurserySpace.getDescriptor();

  /****************************************************************************
   *
   * Instance variables
   */

  // allocators
  private CopyLocal nursery;
  private MarkSweepLocal ms;

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
  static {}

  /**
   * Constructor
   */
  public CopyMS() {
    nursery = new CopyLocal(nurserySpace);
    ms = new MarkSweepLocal(msSpace);
  }

  /**
   * The boot method is called early in the boot process before any
   * allocation.
   */
  public static final void boot()
    throws InterruptiblePragma {
    StopTheWorldGC.boot();
  }


  /****************************************************************************
   *
   * Allocation
   */

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
    case  ALLOC_NURSERY: return nursery.alloc(bytes, align, offset);
    case       ALLOC_MS: return ms.alloc(bytes, align, offset, false);
    case      ALLOC_LOS: return los.alloc(bytes, align, offset);
    case ALLOC_IMMORTAL: return immortal.alloc(bytes, align, offset);
    default:
      if (Assert.VERIFY_ASSERTIONS) Assert.fail("No such allocator"); 
      return Address.zero();
    }
  }
  
  /**
   * Perform post-allocation actions.  For many allocators none are
   * required.
   *
   * @param ref The newly allocated object
   * @param typeRef the type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   * @param allocator The allocator number to be used for this allocation
   */
  public final void postAlloc(ObjectReference ref, ObjectReference typeRef, 
                              int bytes, int allocator) throws InlinePragma {
    switch (allocator) {
    case  ALLOC_NURSERY: return;
    case      ALLOC_LOS: loSpace.initializeHeader(ref); return;
    case       ALLOC_MS: msSpace.initializeHeader(ref); return;
    case ALLOC_IMMORTAL: immortalSpace.postAlloc(ref); return;
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
  public final Address allocCopy(ObjectReference original, int bytes,
                                 int align, int offset) throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(bytes <= LOS_SIZE_THRESHOLD);
    return ms.alloc(bytes, align, offset, true);
  }

  /**  
   * Perform any post-copy actions.  Need to set the mark bit.
   *
   * @param ref The newly allocated object
   * @param typeRef the type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   */
  public final void postCopy(ObjectReference ref, ObjectReference typeRef,
                             int bytes) throws InlinePragma {
    msSpace.writeMarkBit(ref);
    MarkSweepLocal.liveObject(ref);
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
  protected final Space getSpaceFromAllocator(Allocator a) {
    if (a == nursery) return nurserySpace;
    else if (a == ms) return msSpace;
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
  protected final Allocator getAllocatorFromSpace(Space space) {
    if (space == nurserySpace) return nursery;
    else if (space == msSpace) return ms;
    return super.getAllocatorFromSpace(space);
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
    boolean nurseryFull = nurserySpace.reservedPages() > nurserySize.getMaxNursery();
    if (mustCollect || heapFull || nurseryFull) {
      required = space.reservedPages() - space.committedPages();
      if (space == nurserySpace) required = required<<1;  // account for copy reserve
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
   * Perform operations with <i>global</i> scope in preparation for a
   * collection.  This is called by <code>StopTheWorld</code>, which will
   * ensure that <i>only one thread</i> executes this.<p>
   *
   * In this case, it means resetting the nursery memory resource and
   * preparing each of the collectors.
   */
  protected final void globalPrepare() {
    nurserySpace.prepare(true);
    msSpace.prepare();
    commonGlobalPrepare();
  }

  /**
   * Perform operations with <i>thread-local</i> scope in preparation
   * for a collection.  This is called by <code>StopTheWorld</code>, which
   * will ensure that <i>all threads</i> execute this.<p>
   *
   * In this case, it means rebinding the nursery allocator and
   * preparing the mark sweep allocator.
   */
  protected final void threadLocalPrepare(int count) {
    nursery.reset();
    ms.prepare();
    commonLocalPrepare();
  }

  /**
   * Perform operations with <i>thread-local</i> scope to clean up at
   * the end of a collection.  This is called by
   * <code>StopTheWorld</code>, which will ensure that <i>all threads</i>
   * execute this.<p>
   *
   * In this case, it means releasing the mark sweep space (which
   * triggers the sweep phase of the mark-sweep collector).
   */
  protected final void threadLocalRelease(int count) {
    ms.release();
    commonLocalRelease();
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
    nurserySpace.release();
    msSpace.release();
    commonGlobalRelease();
    if (getPagesReserved() + required >= getTotalPages()) {
      progress = false;
    } else
      progress = true;
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
  public static final ObjectReference traceObject(ObjectReference object) {
    if (object.isNull()) 
      return object;
    else
      return Space.getSpaceForObject(object).traceObject(object);
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
  public static final ObjectReference traceObject(ObjectReference obj,
                                                  boolean root) {
    return traceObject(obj);  // root or non-root is of no consequence here
  }

  /**
   * Scan an object that was previously forwarded but not scanned.
   * The separation between forwarding and scanning is necessary for
   * the "pre-copying" mechanism to function properly.
   *
   * @param object The object to be scanned.
   */
  protected final void scanForwardedObject(ObjectReference object) {
    Scan.scanObject(object);
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
  public static void forwardObjectLocation(Address location) 
    throws InlinePragma {
    ObjectReference object = location.loadObjectReference();
    if (!object.isNull() && Space.isInSpace(NS, object))
      location.store(CopySpace.forwardObject(object));
  }

  /**
   * If the object in question has been forwarded, return its
   * forwarded value.<p>
   *
   * @param object The object which may have been forwarded.
   * @return The forwarded value for <code>object</code>.
   */
  public static final ObjectReference getForwardedReference(ObjectReference object) {
    if (!object.isNull()) {
      if (Space.isInSpace(NS, object)) {
        if (Assert.VERIFY_ASSERTIONS) Assert._assert(CopySpace.isForwarded(object));
        return CopySpace.getForwardingPointer(object);
      }
    }
    return object;
  }

  /**
   * Return true if <code>obj</code> is a live object.
   *
   * @param object The object in question
   * @return True if <code>obj</code> is a live object.
   */
  public static final boolean isLive(ObjectReference object) {
    if (object.isNull()) return false;
    Space space = Space.getSpaceForObject(object);
    if (space == nurserySpace)
      return nurserySpace.isLive(object);
    else if (space == msSpace)
      return msSpace.isLive(object);
    else if (space == loSpace)
      return loSpace.isLive(object);
    else if (space == null) {
      if (Assert.VERIFY_ASSERTIONS) {
        Log.write("space failure: "); Log.writeln(object);
    }
  }
    return true;
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
    return getPagesUsed() + nurserySpace.reservedPages();
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
    int pages = getCommonPagesReserved();
    pages += nurserySpace.reservedPages();
    pages += msSpace.reservedPages();
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
    int nurseryPages = getTotalPages() - msSpace.reservedPages() 
      - getCommonPagesReserved();
    return (nurseryPages>>1) - nurserySpace.reservedPages();
  }


  /****************************************************************************
   *
   * Miscellaneous
   */

  /**
   * Show the status of each of the allocators.
   */
  public final void show() {
    nursery.show();
    ms.show();
  }
}
