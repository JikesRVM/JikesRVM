/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package org.mmtk.plan;

import org.mmtk.policy.ImmortalSpace;
import org.mmtk.policy.RefCountSpace;
import org.mmtk.policy.RefCountLocal;
import org.mmtk.policy.RefCountLOSLocal;
import org.mmtk.policy.Space;
import org.mmtk.utility.alloc.AllocAdvice;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.alloc.BumpPointer;
import org.mmtk.utility.CallSite;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.deque.*;
import org.mmtk.utility.heap.*;
import org.mmtk.utility.Memory;
import org.mmtk.utility.Options;
import org.mmtk.utility.scan.*;
import org.mmtk.utility.statistics.*;
import org.mmtk.vm.Assert;
import org.mmtk.vm.Barriers;
import org.mmtk.vm.Collection;
import org.mmtk.vm.Plan;
import org.mmtk.vm.Statistics;
import org.mmtk.vm.ObjectModel;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements a simple non-concurrent reference counting
 * collector.
 *
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public class RefCount extends RefCountBase implements Uninterruptible {

  /****************************************************************************
   *
   * Class variables
   */
  public static final boolean MOVES_OBJECTS = false;
  public static final int GC_HEADER_BITS_REQUIRED = RefCountSpace.LOCAL_GC_BITS_REQUIRED;
  private static final boolean INLINE_WRITE_BARRIER = WITH_COALESCING_RC;

  public static final int ALLOC_RC = ALLOC_DEFAULT;
  public static final int ALLOCATORS = BASE_ALLOCATORS;

  protected static int lastRCPages = 0; // pages at end of last GC

  /****************************************************************************
   *
   * Instance variables
   */

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
  static { }

  /**
   * Constructor
   */
  public RefCount() {}

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
   * @param object The newly allocated object
   * @param typeRef the type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   * @param allocator The allocator number to be used for this allocation
   */
  public final void postAlloc(Address object, Address typeRef, int bytes,
                              int allocator)
    throws NoInlinePragma {
    switch (allocator) {
    case ALLOC_RC:
      RefCountLocal.unsyncLiveObject(object);
    case ALLOC_LOS: 
      if (WITH_COALESCING_RC) modBuffer.push(object);
      decBuffer.push(object);
      if (RefCountSpace.RC_SANITY_CHECK) RefCountLocal.sanityAllocCount(object); 
      RefCountSpace.initializeHeader(object, typeRef, true);
      return;
    case ALLOC_IMMORTAL: 
      if (WITH_COALESCING_RC)
	modBuffer.push(object);
      else
        ImmortalSpace.postAlloc(object);
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
   * @param align The requested alignment.
   * @param offset The alignment offset.
   * @return The address of the first byte of the allocated region
   */
  public final Address allocCopy(Address original, int bytes,
				 int align, int offset) throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(false);
    // return Address.zero();  this trips some Intel assembler bug
    return Address.max();
  }

  /**  
   * Perform any post-copy actions.  In this case nothing is required.
   *
   * @param ref The newly allocated object
   * @param typeRef The type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   */
  public final void postCopy(Address ref, Address typeRef, int bytes) {}

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
    if (mustCollect || getPagesReserved() > getTotalPages() ||
        (progress &&
         ((rcSpace.committedPages() - lastRCPages) > Options.maxNurseryPages ||
          metaDataSpace.committedPages() > Options.metaDataPages))) {
      if (space == metaDataSpace) {
        awaitingCollection = true;
        return false;
      }
      required = space.reservedPages() - space.committedPages();
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
    immortalSpace.prepare();
    rcSpace.prepare();
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
    if (WITH_COALESCING_RC) processModBufs();    
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
    // release each of the collected regions
    rcSpace.release();
    immortalSpace.release();
    if (Options.verbose > 2) rc.printStats();
    lastRCPages = rcSpace.committedPages();
  }

  /****************************************************************************
   *
   * Object processing and tracing
   */

  /**
   * Trace a reference during GC.  In this case we do nothing.  We
   * only trace objects that are known to be root reachable.
   *
   * @param obj The object reference to be traced.  This is <i>NOT</i> an
   * interior pointer.
   * @return The possibly moved reference.
   */
   public static final Address traceObject(Address object) 
     throws InlinePragma {
     return object;
   }
  
  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.  We do not trace objects that are not
   * roots.
   *
   * @param obj The object reference to be traced.  This is <i>NOT</i>
   * an interior pointer.
   * @param root True if this reference to <code>obj</code> was held
   * in a root.
   * @return The possibly moved reference.
   */
  public static final Address traceObject(Address object, boolean root) {
    if (object.isZero() || !root) 
      return object;
    if (RefCountSpace.RC_SANITY_CHECK) 
      Plan.getInstance().rc.incSanityTraceRoot(object);

    if (isRCObject(object))
      return rcSpace.traceObject(object);
    
    // else this is not an rc heap pointer
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
  public final void incSanityTrace(Address object, Address location,
                            boolean root) {
    if (isRCObject(object)) {
      if (RefCountSpace.incSanityRC(object, root))
        Scan.enumeratePointers(object, sanityEnum);
    } else if (RefCountSpace.markSanityRC(object))
      Scan.enumeratePointers(object, sanityEnum);
  }
  
  /**
   * Trace a reference during an check sanity traversal.  This is only
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
  public final void checkSanityTrace(Address object, Address location) {
    if (isRCObject(object)) {
      if (RefCountSpace.checkAndClearSanityRC(object))
        Scan.enumeratePointers(object, sanityEnum);
    } else if (RefCountSpace.unmarkSanityRC(object))
      Scan.enumeratePointers(object, sanityEnum);
  }
  
  /**
   * Return true if <code>obj</code> is a live object.
   *
   * @param obj The object in question
   * @return True if <code>obj</code> is a live object.
   */
  public static final boolean isLive(Address object) {
    if (isRCObject(object))
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
  public static boolean isFinalizable(Address object) {
    if (isRCObject(object))
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
  public static Address retainFinalizable(Address object) {
    if (isRCObject(object))
      RefCountSpace.clearFinalizer(object);
    return object;
  }

  public static boolean willNotMove (Address obj) {
    return true;
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
   * non-nursery space.  This method is <b>inlined</b> by the
   * optimizing compiler, and the methods it calls are forced out of
   * line.
   *
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be
   * stored.
   * @param tgt The target of the new reference
   * @param metaDataA An int that assists the host VM in creating a store 
   * @param metaDataB An int that assists the host VM in creating a store 
   * @param mode The mode of the store (eg putfield, putstatic)
   */
  public final void writeBarrier(Address src, Address slot, Address tgt,
				 int metaDataA, int metaDataB, int mode) 
    throws InlinePragma {
    if (INLINE_WRITE_BARRIER)
      writeBarrierInternal(src, slot, tgt, metaDataA, metaDataB, mode);
    else 
      writeBarrierInternalOOL(src, slot, tgt, metaDataA, metaDataB, mode);
  }

  /**
   * A new reference is about to be created.  Perform appropriate
   * write barrier action.<p>
   *
   * In this case, we remember the address of the source of the
   * pointer if the new reference points into the nursery from
   * non-nursery space.  This method is <b>inlined</b> by the
   * optimizing compiler, and the methods it calls are forced out of
   * line.
   *
   * @param src The object being mutated.
   * @param slot The address of the word (slot) being mutated.
   * @param tgt The target of the new reference (about to be stored into src).
   * @param metaDataA An int that assists the host VM in creating a store 
   * @param metaDataB An int that assists the host VM in creating a store 
   * @param mode The mode of the store (eg putfield, putstatic)
   */
  private final void writeBarrierInternal(Address src, Address slot,
                                  Address tgt, int metaDataA, 
                                  int metaDataB, int mode) 
    throws InlinePragma {
    if (GATHER_WRITE_BARRIER_STATS) wbFast.inc();
    if (WITH_COALESCING_RC) {
      if (RefCountSpace.logRequired(src)) {
        coalescingWriteBarrierSlow(src);
      }
      Barriers.performWriteInBarrier(src, slot, tgt, metaDataA, metaDataB, mode);
    } else {      
      Address old = Barriers.performWriteInBarrierAtomic(src, slot, tgt, metaDataA, metaDataB, mode);
      if (isRCObject(old)) decBuffer.pushOOL(old);
      if (isRCObject(tgt)) RefCountSpace.incRCOOL(tgt);
    }
  }

  /**
   * An out of line version of the write barrier.  This method is
   * forced <b>out of line</b> by the optimizing compiler, and the
   * methods it calls are forced out of inline.
   *
   * @param src The object being mutated.
   * @param slot The address of the word (slot) being mutated.
   * @param tgt The target of the new reference (about to be stored into src).
   * @param metaDataA An int that assists the host VM in creating a store 
   * @param metaDataB An int that assists the host VM in creating a store 
   * @param mode The mode of the store (eg putfield, putstatic)
   */
  private final void writeBarrierInternalOOL(Address src, Address slot,
                                     Address tgt, int metaDataA,
                                     int metaDataB, int mode) 
    throws NoInlinePragma {
    if (GATHER_WRITE_BARRIER_STATS) wbFast.inc();
    if (WITH_COALESCING_RC) {
      if (RefCountSpace.logRequired(src)) {
        coalescingWriteBarrierSlow(src);
      }
      Barriers.performWriteInBarrier(src, slot, tgt, metaDataA, metaDataB, mode);
    } else {
      Address old = Barriers.performWriteInBarrierAtomic(src, slot, tgt, metaDataA, metaDataB, mode);
      if (isRCObject(old)) decBuffer.push(old);
      if (isRCObject(tgt)) RefCountSpace.incRC(tgt);
    }
  }

  /**
   * A number of references are about to be copied from object
   * <code>src</code> to object <code>dst</code> (as in an array
   * copy).  Thus, <code>dst</code> is the mutated object.  Take
   * appropriate write barrier actions.<p>
   *
   * In this case, we simply remember the mutated source object, or we
   * enumerate the copied pointers and perform appropriate actions on
   * each.
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
   * left to the caller (this depends on which style of barrier is
   * being used).
   */
  public boolean writeBarrier(Address src, int srcOffset, Address dst, 
			      int dstOffset, int bytes) {
    if (GATHER_WRITE_BARRIER_STATS) wbFast.inc();
    if (WITH_COALESCING_RC) {
      if (RefCountSpace.logRequired(dst))
	coalescingWriteBarrierSlow(dst);
      return false;
    } else {
      while (bytes > 0) {
	Address tgt = src.loadAddress();
	Address old;
	do {
	  old = dst.prepareAddress();
	} while (!dst.attempt(old, tgt));
	if (isRCObject(old)) decBuffer.push(old);
	if (isRCObject(tgt)) RefCountSpace.incRC(tgt);
	src = src.add(BYTES_IN_ADDRESS);
	dst = dst.add(BYTES_IN_ADDRESS);
	bytes -= BYTES_IN_ADDRESS;
      }
      return true;
    }
  }

  /**
   * Slow path of the coalescing write barrier.
   *
   * <p> Attempt to log the source object. If successful in racing for
   * the log bit, push an entry into the modified buffer and add a
   * decrement buffer entry for each referent object (in the RC space)
   * before setting the header bit to indicate that it has finished
   * logging (allowing others in the race to continue).
   *
   * @param srcObj The object being mutated
   */
  private final void coalescingWriteBarrierSlow(Address srcObj) 
    throws NoInlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(WITH_COALESCING_RC);
    if (GATHER_WRITE_BARRIER_STATS) wbSlow.inc();
    if (RefCountSpace.attemptToLog(srcObj)) {
      modBuffer.push(srcObj);
      Scan.enumeratePointers(srcObj, decEnum);
      RefCountSpace.makeLogged(srcObj);
    }
  }


  /****************************************************************************
   *
   * Pointer enumeration
   */

  /**
   * A field of an object in the modified buffer is being enumerated
   * by ScanObject. If the field points to the RC space, increment the
   * count of the referent object.
   *
   * @param objLoc The address of a reference field with an object
   * being enumerated.
   */
  public final void enumerateModifiedPointerLocation(Address objLoc)
    throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(WITH_COALESCING_RC);
    Address object = objLoc.loadAddress();
    if (isRCObject(object)) RefCountSpace.incRC(object);
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
  protected static int getPagesReserved() {
    return getPagesUsed();
  }

  /**
   * Return the number of pages reserved for use given the pending
   * allocation. 
   *
   * @return The number of pages reserved given the pending
   * allocation.
   */
  protected static int getPagesUsed() {
    int pages = rcSpace.reservedPages();
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
  public static int getPagesAvail() {
    return getTotalPages() - getPagesUsed();
  }


  /****************************************************************************
   *
   * Miscellaneous
   */

  /**
   * Show the status of each of the allocators.
   */
  public final void show() {
    rc.show();
    los.show();
    immortal.show();
  }
}

