/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.plan;

import org.mmtk.policy.CopySpace;
import org.mmtk.policy.CopyLocal;
import org.mmtk.policy.ImmortalSpace;
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
 * This class implements a simple semi-space collector. See the Jones
 * & Lins GC book, section 2.2 for an overview of the basic
 * algorithm. This implementation also includes a large object space
 * (LOS), and an uncollected "immortal" space.<p>
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
 * @author Perry Cheng
 *
 * @version $Revision$
 * @date $Date$
 */
public class SemiSpaceBase extends StopTheWorldGC implements Uninterruptible {

  /****************************************************************************
   *
   * Class variables
   */
  public static final boolean MOVES_OBJECTS = true;
  public static final int GC_HEADER_BITS_REQUIRED = CopySpace.LOCAL_GC_BITS_REQUIRED;
  public static final int GC_HEADER_BYTES_REQUIRED = CopySpace.GC_HEADER_BYTES_REQUIRED;

  // GC state
  protected static boolean hi = false; // True if allocing to "higher" semispace

  // Allocators
  protected static final int ALLOC_SS = ALLOC_DEFAULT;
  public static final int ALLOCATORS = BASE_ALLOCATORS;

  // Spaces
  protected static CopySpace copySpace0 = new CopySpace("ss0", DEFAULT_POLL_FREQUENCY, (float) 0.35, false);
  protected static CopySpace copySpace1 = new CopySpace("ss1", DEFAULT_POLL_FREQUENCY, (float) 0.35, true);
  protected static final int SS0 = copySpace0.getDescriptor();
  protected static final int SS1 = copySpace1.getDescriptor();

  /****************************************************************************
   *
   * Instance variables
   */

  // allocators
  public CopyLocal ss;

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
  public SemiSpaceBase() {
    ss = new CopyLocal(copySpace0);
  }

  /**
   * The boot method is called early in the boot process before any
   * allocation.
   */
  public static void boot() throws InterruptiblePragma {
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
    case  ALLOC_DEFAULT: return ss.alloc(bytes, align, offset);
    case ALLOC_IMMORTAL: return immortal.alloc(bytes, align, offset);
    case      ALLOC_LOS: return los.alloc(bytes, align, offset);
    default: 
      if (Assert.VERIFY_ASSERTIONS) Assert.fail("No such allocator");
      return Address.zero();
    }
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
    if (a == ss) return (hi) ? copySpace1 : copySpace0;
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
    if (space == copySpace0 || space == copySpace1) return ss;
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
  public boolean poll(boolean mustCollect, Space space) 
    throws LogicallyUninterruptiblePragma {
    if (collectionsInitiated > 0 || !initialized || space == metaDataSpace)
      return false;
    mustCollect |= stressTestGCRequired();
    if (mustCollect || getPagesReserved() > getTotalPages()) {
      required = space.reservedPages() - space.committedPages();
      if (space == copySpace0 || space == copySpace1)
        required = required<<1; // must account for copy reserve
      Collection.triggerCollection(Collection.RESOURCE_GC_TRIGGER);
      return true;
    }
    return false;
  }

  /****************************************************************************
   *
   * Collection
   */
  // Important notes:
  //   . Global actions are executed by only one thread
  //   . Thread-local actions are executed by all threads
  //   . The following order is guaranteed by BasePlan, with each
  //     separated by a synchronization barrier.:
  //      1. globalPrepare()
  //      2. threadLocalPrepare()
  //      3. threadLocalRelease()
  //      4. globalRelease()
  //

  /**
   * Perform operations with <i>global</i> scope in preparation for a
   * collection.  This is called by <code>StopTheWorld</code>, which will
   * ensure that <i>only one thread</i> executes this.<p>
   *
   * In this case, it means flipping semi-spaces, resetting the
   * semi-space memory resource, and preparing each of the collectors.
   */
  protected void globalPrepare() {
    hi = !hi;        // flip the semi-spaces
    //    ssMR.reset();    // reset the semispace memory resource, and
    // prepare each of the collected regions
    copySpace0.prepare(hi);
    copySpace1.prepare(!hi);
    immortalSpace.prepare();
    loSpace.prepare();
  }

  /**
   * Perform operations with <i>thread-local</i> scope in preparation
   * for a collection.  This is called by <code>StopTheWorld</code>, which
   * will ensure that <i>all threads</i> execute this.<p>
   *
   * In this case, it means resetting the semi-space and large object
   * space allocators.
   */
  protected void threadLocalPrepare(int count) {
    // rebind the semispace bump pointer to the appropriate semispace.
    ss.rebind(((hi) ? copySpace1 : copySpace0)); 
    los.prepare();
  }

  /**
   * Perform operations with <i>thread-local</i> scope to clean up at
   * the end of a collection.  This is called by
   * <code>StopTheWorld</code>, which will ensure that <i>all threads</i>
   * execute this.<p>
   *
   * In this case, it means releasing the large object space (which
   * triggers the sweep phase of the treadmill collector used by the
   * LOS).
   */
  protected void threadLocalRelease(int count) {
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
  protected void globalRelease() {
    // release each of the collected regions
    loSpace.release();
    (hi ? copySpace0 : copySpace1).release();
    immortalSpace.release();
    progress = (getPagesReserved() + required < getTotalPages());
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
  public static ObjectReference traceObject(ObjectReference object) 
    throws InlinePragma {
    if (object.isNull())
      return object;
    else if (Space.isInSpace(SS0, object))
      return hi ? copySpace0.forwardAndScanObject(object) : object;
    else if (Space.isInSpace(SS1, object))
      return hi ? object: copySpace1.forwardAndScanObject(object);
    else
      return Space.getSpaceForObject(object).traceObject(object);
  }

  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.
   *
   * @param object The object reference to be traced.  This is
   * <i>NOT</i> an interior pointer.
   * @param root True if this reference to <code>obj</code> was held
   * in a root.
   * @return The possibly moved reference.
   */
  public static ObjectReference traceObject(ObjectReference object,
                                            boolean root) {
    return traceObject(object);  // root or non-root is of no consequence here
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
    if (!object.isNull()) {
      if ((hi && Space.isInSpace(SS0, object)) || 
          (!hi && Space.isInSpace(SS1, object)))
        location.store(CopySpace.forwardObject(object));
    }
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
      if ((hi && Space.isInSpace(SS0, object)) || 
          (!hi && Space.isInSpace(SS1, object))) {
        if (Assert.VERIFY_ASSERTIONS) 
          Assert._assert(CopySpace.isForwarded(object));
        return CopySpace.getForwardingPointer(object);
      }
    }
    return object;
  }

  /**
   * Return true if the given reference is to an object that is within
   * one of the semi-spaces.
   *
   * @param object The object in question
   * @return True if the given reference is to an object that is within
   * one of the semi-spaces.
   */
  public static final boolean isSemiSpaceObject(ObjectReference object) {
    return Space.isInSpace(SS0, object) || Space.isInSpace(SS1, object);
  }

  /**
   * Return true if <code>obj</code> is a live object.
   *
   * @param object The object in question
   * @return True if <code>obj</code> is a live object.
   */
  public static boolean isLive(ObjectReference object) {
    if (object.isNull()) return false;
    Space space = Space.getSpaceForObject(object);
    if (space == copySpace0)
      return copySpace0.isLive(object);
    else if (space == copySpace1)
      return copySpace1.isLive(object);
    else if (space == loSpace)
      return loSpace.isLive(object);
    else if (space == null) {
      if (Assert.VERIFY_ASSERTIONS) {
        Log.write("space failure: "); Log.writeln(object);
    }
  }
    return true;
 }

  /**
   * Return true if the object is either forwarded or being forwarded
   *
   * @param object
   * @return True if the object is either forwarded or being forwarded
   */
  public static boolean isForwardedOrBeingForwarded(ObjectReference object) 
    throws InlinePragma {
    if (isSemiSpaceObject(object))
      return CopySpace.isForwardedOrBeingForwarded(object);
    else
      return false;
  }

  // XXX Missing Javadoc comment.
  public static boolean willNotMove (ObjectReference object) {
    return (hi && !Space.isInSpace(SS0, object))
       || (!hi && !Space.isInSpace(SS1, object));  
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
    // we must account for the number of pages required for copying,
    // which equals the number of semi-space pages reserved
    return (hi ? copySpace1 : copySpace0).reservedPages() + getPagesUsed();
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
    int pages = (hi ? copySpace1 : copySpace0).reservedPages();
    pages += loSpace.reservedPages();
    pages += immortalSpace.reservedPages();
    pages += metaDataSpace.reservedPages();
    return pages;
  }

  /**
   * Return the number of pages available for allocation, <i>assuming
   * all future allocation is to the semi-space</i>.
   *
   * @return The number of pages available for allocation, <i>assuming
   * all future allocation is to the semi-space</i>.
   */
  protected static final int getPagesAvail() {
    int semispaceTotal = getTotalPages() - loSpace.reservedPages() 
      - immortalSpace.reservedPages();
    return (semispaceTotal>>1) - (hi ? copySpace1 : copySpace0).reservedPages();
  }


  /****************************************************************************
   *
   * Miscellaneous
   */

  /**
   * Show the status of each of the allocators.
   */
  public final void show() {
    ss.show();
    los.show();
    immortal.show();
  }
}
