/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package org.mmtk.plan;

import org.mmtk.policy.CopySpace;
import org.mmtk.policy.CopyLocal;
import org.mmtk.policy.ImmortalSpace;
import org.mmtk.policy.RefCountSpace;
import org.mmtk.policy.RefCountLocal;
import org.mmtk.policy.RefCountLOSLocal;
import org.mmtk.policy.Space;
import org.mmtk.utility.alloc.AllocAdvice;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.CallSite;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.heap.*;
import org.mmtk.utility.Memory;
import org.mmtk.utility.Options;
import org.mmtk.utility.deque.*;
import org.mmtk.utility.scan.*;
import org.mmtk.utility.statistics.*;
import org.mmtk.vm.Assert;
import org.mmtk.vm.Barriers;
import org.mmtk.vm.Collection;
import org.mmtk.vm.ObjectModel;
import org.mmtk.vm.Plan;
import org.mmtk.vm.Statistics;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements a generational-reference counting hybrid
 * using the "Ulterior RC" algorithm described by Blackburn and
 * McKinley.<p>
 *
 * See S.M. Blackburn and K.S. McKinley, "Ulterior Reference Counting:
 * Fast Garbage Collection Without A Long Wait", OOPSLA, October 2003.
 *
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public class GenRC extends RefCountBase implements Uninterruptible {

  /****************************************************************************
   *
   * Class variables
   */
  public static final boolean MOVES_OBJECTS = true;
  public static final int GC_HEADER_BITS_REQUIRED = CopySpace.LOCAL_GC_BITS_REQUIRED;
  public static final boolean STEAL_NURSERY_GC_HEADER = false;

  // GC state
  private static int previousMetaDataPages;  // meta-data pages after last GC

  // Allocators
  public static final int ALLOC_NURSERY = ALLOC_DEFAULT;
  public static final int ALLOC_RC = BASE_ALLOCATORS;
  public static final int ALLOCATORS = ALLOC_RC + 1;

  // Spaces
  protected static CopySpace nurserySpace = new CopySpace("nursery", DEFAULT_POLL_FREQUENCY, (float) 0.15, true, false);
  protected static final int NS = nurserySpace.getDescriptor();

  /****************************************************************************
   *
   * Instance variables
   */

  // allocators
  protected CopyLocal nursery;

  // counters
  private int incCounter;
  private int decCounter;
  private int modCounter;

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
  static {}

  /**
   * Constructor
   */
  public GenRC() {
    nursery = new CopyLocal(nurserySpace);
  }

  /****************************************************************************
   *
   * Allocation
   */

  /**
   * Allocate space (for an object)
   *
   * @param allocator The allocator number to be used for this allocation
   * @param bytes The size of the space to be allocated (in bytes)
   * @param align The requested alignment
   * @param offset The alignment offset
   * @return The address of the first byte of the allocated region
   */
  public final Address alloc(int bytes, int align, int offset, int allocator)
    throws InlinePragma {
    if (STEAL_NURSERY_GC_HEADER
	&& allocator == ALLOC_NURSERY) {
      // this assertion is unguarded so will even fail in FastAdaptive!
      // we need to abstract the idea of stealing nursery header bytes,
      // but we want to wait for the forward object model first...
      if (Assert.VERIFY_ASSERTIONS) Assert._assert(false);
    }
    switch (allocator) {
    case  ALLOC_NURSERY: return nursery.alloc(bytes, align, offset);
    case       ALLOC_RC: return rc.alloc(bytes, align, offset, false);
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
   * @param ref The newly allocated object
   * @param typeRef the type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   * @param allocator The allocator number to be used for this allocation
   */
  public final void postAlloc(ObjectReference ref, ObjectReference typeRef,
			      int bytes, int allocator)
    throws InlinePragma {
    switch (allocator) {
    case ALLOC_NURSERY: return;
    case ALLOC_RC:
      RefCountLocal.unsyncLiveObject(ref);
    case ALLOC_LOS:
      modBuffer.push(ref);
      RefCountSpace.initializeHeader(ref, typeRef, true);
      decBuffer.push(ref);
      if (RefCountSpace.RC_SANITY_CHECK) RefCountLocal.sanityAllocCount(ref); 
      return;
    case ALLOC_IMMORTAL: 
      if (RefCountSpace.RC_SANITY_CHECK) rc.addImmortalObject(ref);
      modBuffer.push(ref);
      return;
    default:
      if (Assert.VERIFY_ASSERTIONS) Assert.fail("No such allocator"); 
      return;
    } 
  }

  /**
   * Allocate space for copying an object (this method <i>does not</i>
   * copy the object, it only allocates space)
   *
   * @param original A reference to the original object
   * @param bytes The size of the space to be allocated (in bytes)
   * @param align The requested alignment
   * @param offset The alignment offset
   * @return The address of the first byte of the allocated region
   */
  public final Address allocCopy(ObjectReference original, int bytes,
                                    int align, int offset) throws InlinePragma {
    return rc.alloc(bytes, align, offset, false);  // FIXME is this right???
  }

  /**  
   * Perform any post-copy actions.  In this case nothing is required.
   *
   * @param object The newly allocated object
   * @param typeRef the type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   */
  public final void postCopy(ObjectReference object, ObjectReference typeRef,
			     int bytes) throws InlinePragma {
    CopySpace.clearGCBits(object);
    RefCountSpace.initializeHeader(object, typeRef, false);
    RefCountSpace.makeUnlogged(object);
    RefCountLocal.unsyncLiveObject(object);
    if (RefCountSpace.RC_SANITY_CHECK) {
      RefCountLocal.sanityAllocCount(object); 
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
  protected final Allocator getAllocatorFromSpace(Space space) {
    if (space == nurserySpace) return nursery;
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
    if (collectionsInitiated > 0 || !initialized) return false;
    mustCollect |= stressTestGCRequired();
    boolean heapFull = getPagesReserved() > getTotalPages();
    boolean nurseryFull = nurserySpace.reservedPages() > Options.maxNurseryPages;
    int newMetaDataPages = metaDataSpace.committedPages() - previousMetaDataPages;
    if (mustCollect || heapFull || nurseryFull ||
        (progress && (newMetaDataPages > Options.metaDataPages))) {
      if (space == metaDataSpace) {
        awaitingCollection = true;
        return false;
      }
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
   * In this case, it means flipping semi-spaces, resetting the
   * semi-space memory resource, and preparing each of the collectors.
   */
  protected final void globalPrepare() {
    timeCap = Statistics.cycles() + Statistics.millisToCycles(Options.gcTimeCap);
    nurserySpace.prepare(true);
    rcSpace.prepare();
    immortalSpace.prepare();
  }

  /**
   * Perform operations with <i>thread-local</i> scope in preparation
   * for a collection.  This is called by <code>StopTheWorld</code>, which
   * will ensure that <i>all threads</i> execute this.<p>
   *
   * In this case, it means resetting the semi-space and large object
   * space allocators.
   */
  protected final void threadLocalPrepare(int count) {
    rc.prepare(Options.verboseTiming && count==1);
    nursery.rebind(nurserySpace);
  }

  /**
   * Perform operations with <i>thread-local</i> scope to clean up at
   * the end of a collection.  This is called by
   * <code>StopTheWorld</code>, which will ensure that <i>all threads</i>
   * execute this.<p>
   *
   * In this case, it means releasing the large object space (which
   * triggers the sweep phase of the mark-sweep collector used by the
   * LOS).
   */
  protected final void threadLocalRelease(int count) {
    rc.release(this, count);
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
    nurserySpace.release();
    rcSpace.release();
    immortalSpace.release();
    if (Options.verbose > 2) rc.printStats();
    progress = (getPagesReserved() + required < getTotalPages());
    previousMetaDataPages = metaDataSpace.committedPages();
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
  public static final ObjectReference traceObject(ObjectReference object) 
    throws InlinePragma {
    return traceObject(object, false);
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
  public static final ObjectReference traceObject(ObjectReference object, 
						  boolean root) {
    if (object.isNull()) return object;
    if (RefCountSpace.RC_SANITY_CHECK && root) 
      Plan.getInstance().rc.incSanityTraceRoot(object);
    if (Space.isInSpace(NS, object)) {
      ObjectReference rtn = CopySpace.forwardAndScanObject(object);
      // every incoming reference to the from-space object must inc the
      // ref count of forwarded (to-space) object...
      if (root) {
	if (RefCountSpace.INC_DEC_ROOT) {
	  RefCountSpace.incRC(rtn);
	  Plan.getInstance().addToRootSet(rtn);
	} else if (RefCountSpace.setRoot(rtn)) {
	  Plan.getInstance().addToRootSet(rtn);
	}
      } else
	RefCountSpace.incRC(rtn);
      return rtn;
    } else if (isRCObject(object)) {
      if (root)
	return rcSpace.traceObject(object);
      else
	RefCountSpace.incRC(object);
    }
    // else this is not a rc heap pointer
    return object;
  }

  /**
   * Trace a reference during an increment sanity traversal.  This is
   * only used as part of the ref count sanity check, and it forms the
   * basis for a transitive closure that assigns a reference count to
   * each object.
   *
   * @param object The object being traced
   * @param location The location from which this object was
   * reachable, null if not applicable.
   * @param root <code>true</code> if the object is being traced
   * directly from a root.
   */
  public final void incSanityTrace(ObjectReference object, Address location,
                                   boolean root) {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(!object.isNull());        
    // if nursery, then get forwarded RC object
    if (Space.isInSpace(NS, object)) {
      if (Assert.VERIFY_ASSERTIONS) Assert._assert(CopySpace.isForwarded(object));        
      object = CopySpace.getForwardingPointer(object);
    }

    if (isRCObject(object)) {
      if (RefCountSpace.incSanityRC(object, root))
        Scan.enumeratePointers(object, sanityEnum);
    } else if (RefCountSpace.markSanityRC(object))
      Scan.enumeratePointers(object, sanityEnum);
  }
  
  /**
   * Trace a reference during a check sanity traversal.  This is only
   * used as part of the ref count sanity check, and it forms the
   * basis for a transitive closure that checks reference counts
   * against sanity reference counts.  If the counts are not matched,
   * an error is raised.
   *
   * @param object The object being traced
   * @param location The location from which this object was
   * reachable, null if not applicable.
   * @param root <code>true</code> if the object is being traced
   * directly from a root.
   */
  public final void checkSanityTrace(ObjectReference object, 
				     Address location) {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(!object.isNull());        
    // if nursery, then get forwarded RC object
    if (Space.isInSpace(NS, object)) {
      if (Assert.VERIFY_ASSERTIONS) Assert._assert(CopySpace.isForwarded(object));        
      object = CopySpace.getForwardingPointer(object);
    }

   if (isRCObject(object)) {
     if (RefCountSpace.checkAndClearSanityRC(object)) {
       Scan.enumeratePointers(object, sanityEnum);
       rc.addLiveSanityObject(object);
     }
   } else if (RefCountSpace.unmarkSanityRC(object)) {
     Scan.enumeratePointers(object, sanityEnum);
   }
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
    if (!object.isNull() && Space.isInSpace(NS, object)) {
      if (Assert.VERIFY_ASSERTIONS) Assert._assert(!object.isNull());
      location.store(CopySpace.forwardObject(object));
    }
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
    if (RefCountSpace.INC_DEC_ROOT) {
      RefCountSpace.incRC(object);
      addToRootSet(object);
    } else if (RefCountSpace.setRoot(object)) {
      addToRootSet(object);
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
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(!object.isNull());
    if (Space.isInSpace(NS, object)) {
      if (Assert.VERIFY_ASSERTIONS) Assert._assert(CopySpace.isForwarded(object));
      return CopySpace.getForwardingPointer(object);
    } else
      return object;
  }

  /**
   * Return true if <code>object</code> is a live object.
   *
   * @param object The object in question
   * @return True if <code>object</code> is a live object.
   */
  public static final boolean isLive(ObjectReference object) {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(!object.isNull());
    if (Space.isInSpace(NS, object))
      return nurserySpace.isLive(object);
    else if (isRCObject(object))
        return RefCountSpace.isLiveRC(object);
    else if (Space.isInSpace(META, object))
    return false;
    else
      return true;
  }

  /**
   * Return true if an object is ready to move to the finalizable
   * queue, i.e. it has no regular references to it.
   *
   * @param object The object being queried.
   * @return <code>true</code> if the object has no regular references
   * to it.
   */
  public static final boolean isFinalizable(ObjectReference object) {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(!object.isNull());
    if (Space.isInSpace(NS, object))
      return !nurserySpace.isLive(object);
    else if (isRCObject(object))
        return RefCountSpace.isFinalizable(object);
    else if (!Space.isInSpace(META, object))
      return true;
    else
    return false;
  }

  /**
   * An object has just been moved to the finalizable queue.  No need
   * to forward because no copying is performed in this GC, but should
   * clear the finalizer bit of the object so that its reachability
   * now is soley determined by the finalizer queue from which it is
   * now reachable.
   *
   * @param object The object being queried.
   * @return The object (no copying is performed).
   */
  public static ObjectReference retainFinalizable(ObjectReference object) {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(!object.isNull());
    if (Space.isInSpace(NS, object))
      return nurserySpace.traceObject(object);
    else if (isRCObject(object))
        RefCountSpace.clearFinalizer(object);
    return object;
  }

  public static boolean willNotMove(ObjectReference object) {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(!object.isNull());
    return !(Space.isInSpace(NS, object));
  }

  /****************************************************************************
   *
   * Write barriers. 
   */

  /**
   * A new reference is about to be created.  Perform appropriate
   * write barrier action.<p>
   *
   * In this case, we remember the address of the source of the
   * pointer if the new reference points into the nursery from
   * non-nursery space.
   *
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be
   * stored.
   * @param tgt The target of the new reference
   * @param metaDataA An int that assists the host VM in creating a store 
   * @param metaDataB An int that assists the host VM in creating a store 
   * @param mode The mode of the store (eg putfield, putstatic etc)
   */
  public final void writeBarrier(ObjectReference src, Address slot, 
				 ObjectReference tgt, int metaDataA, 
				 int metaDataB, int mode) throws InlinePragma {
    if (GATHER_WRITE_BARRIER_STATS) wbFast.inc();
    if (RefCountSpace.logRequired(src))
      writeBarrierSlow(src);
    Barriers.performWriteInBarrier(src, slot, tgt, metaDataA, metaDataB, mode);
  }

  /**
   * A number of references are about to be copied from object
   * <code>src</code> to object <code>dst</code> (as in an array
   * copy).  Thus, <code>dst</code> is the mutated object.  Take
   * appropriate write barrier actions.<p>
   *
   * In this case, we simply remember the mutated source object.
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
  public final boolean writeBarrier(ObjectReference src, int srcOffset,
				    ObjectReference dst, int dstOffset,
				    int bytes) 
    throws InlinePragma {
    if (GATHER_WRITE_BARRIER_STATS) wbFast.inc();
    if (RefCountSpace.logRequired(dst))
      writeBarrierSlow(dst);
    return false;
  }

  /**
   * This object <i>may</i> need to be logged because we <i>may</i>
   * have been the first to update it.  We can't be sure because of
   * the (delibrate) lack of synchronization in the
   * <code>logRequired()</code> method, which can generate a race
   * condition.  So, we now use an atomic operation to arbitrate the
   * race.  If we successful, we will log the object, enumerating its
   * pointers with the decrement enumerator and marking it as logged.
   *
   * @param src The object being mutated.
   */
  private final void writeBarrierSlow(ObjectReference src) 
    throws NoInlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(!isNurseryObject(src));
    if (RefCountSpace.attemptToLog(src)) {
      if (GATHER_WRITE_BARRIER_STATS) wbSlow.inc();
      modBuffer.push(src);
      Scan.enumeratePointers(src, decEnum);
      RefCountSpace.makeLogged(src);
    }
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
    return getPagesUsed() + nurserySpace.reservedPages();  // copy reserve
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
    pages += rcSpace.reservedPages();
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
  public static int getPagesAvail() {
    int nurseryTotal = getTotalPages() - rcSpace.reservedPages() - immortalSpace.reservedPages() - metaDataSpace.reservedPages();
    return (nurseryTotal>>1) - nurserySpace.reservedPages();
  }


  /****************************************************************************
   *
   * Pointer enumeration
   */

  /**
   * A field of an object rememebered in the modified objects buffer
   * is being enumerated by ScanObject.  If the field points to the
   * nursery, then add the field address to the locations buffer.  If
   * the field points to the RC space, increment the count of the
   * referent object.
   *
   * @param objLoc The address of a reference field with an object
   * being enumerated.
   */
  public final void enumerateModifiedPointerLocation(Address objLoc)
    throws InlinePragma {
    ObjectReference object = objLoc.loadObjectReference();
    if (!object.isNull()) {
      if (Space.isInSpace(NS, object))
        remset.push(objLoc);
      else if (isRCObject(object))
        RefCountSpace.incRC(object);
    }
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
    rc.show();
    los.show();
    immortal.show();
  }

  /**
   * Return true if the object resides within the nursery
   *
   * @param object An object reference
   * @return True if the object resides within the nursery
   */
  static final boolean isNurseryObject(ObjectReference object)
    throws InlinePragma {
    if (object.isNull()) 
      return false;
    else 
      return Space.isInSpace(NS, object);
  }
}
