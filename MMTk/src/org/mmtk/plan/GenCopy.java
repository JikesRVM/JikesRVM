/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.plan;

import org.mmtk.policy.CopySpace;
import org.mmtk.policy.CopyLocal;
import org.mmtk.policy.ImmortalSpace;
import org.mmtk.policy.Space;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.Log;
import org.mmtk.utility.scan.MMType;
import org.mmtk.vm.Assert;
import org.mmtk.vm.ObjectModel;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements the functionality of a standard
 * two-generation copying collector.  Nursery collections occur when
 * either the heap is full or the nursery is full.  The nursery size
 * is determined by an optional command line argument.  If undefined,
 * the nursery size is "infinite", so nursery collections only occur
 * when the heap is full (this is known as a flexible-sized nursery
 * collector).  Thus both fixed and flexible nursery sizes are
 * supported.  Full heap collections occur when the nursery size has
 * dropped to a statically defined threshold,
 * <code>NURSERY_THRESHOLD</code><p>
 *
 * See the Jones & Lins GC book, chapter 7 for a detailed discussion
 * of generational collection and section 7.3 for an overview of the
 * flexible nursery behavior ("The Standard ML of New Jersey
 * collector"), or go to Appel's paper "Simple generational garbage
 * collection and fast allocation." SP&E 19(2):171--183, 1989.<p>
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
 * @version $Revision$
 * @date $Date$
 */
public class GenCopy extends Generational implements Uninterruptible {

  /****************************************************************************
   *
   * Class variables
   */
  protected static final boolean COPY_MATURE() { return true; }

  // GC state
  private static boolean hi = false; // True if copying to "higher" semispace 

  // spaces
  private static CopySpace matureSpace0 = new CopySpace("ss0", DEFAULT_POLL_FREQUENCY, (float) 0.25, false);
  private static CopySpace matureSpace1 = new CopySpace("ss1", DEFAULT_POLL_FREQUENCY, (float) 0.25, true);
  private static final int MS0 = matureSpace0.getDescriptor();
  private static final int MS1 = matureSpace1.getDescriptor();


  /****************************************************************************
   *
   * Instance variables
   */
  private CopyLocal mature;


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
    activeMatureSpace = matureSpace0; // initially alloc into this space
  }

  /**
   * Constructor
   */
  public GenCopy() {
    super();
    mature = new CopyLocal(matureSpace0);
  }


  /****************************************************************************
   *
   * Allocation
   */

  /**
   * Allocate space (for an object) in the mature space
   *
   * @param bytes The size of the space to be allocated (in bytes)
   * @param align The requested alignment.
   * @param offset The alignment offset.
   * @return The address of the first byte of the allocated region
   */
  protected final Address matureAlloc(int bytes, int align, int offset) 
    throws InlinePragma {
    return mature.alloc(bytes, align, offset);
  }

  /**
   * Allocate space for copying an object in the mature space (this
   * method <i>does not</i> copy the object, it only allocates space)
   *
   * @param bytes The size of the space to be allocated (in bytes)
   * @param align The requested alignment.
   * @param offset The alignment offset.
   * @return The address of the first byte of the allocated region
   */
  protected final Address matureCopy(int bytes, int align, int offset) 
    throws InlinePragma {
    return mature.alloc(bytes, align, offset);
  }

  /**
   * Perform post-allocation initialization of an object
   *
   * @param object The newly allocated object
   */
  protected final void maturePostAlloc(ObjectReference object) 
    throws InlinePragma {
    // nothing to be done
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
    if (a == mature) return (hi) ? matureSpace1 : matureSpace0;
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
    if (space == matureSpace0 || space == matureSpace1) return mature;
    return super.getAllocatorFromSpace(space);
  }


  /****************************************************************************
   *
   * Collection
   */

  /**
   * Perform operations pertaining to the mature space with
   * <i>global</i> scope in preparation for a collection.  This is
   * called by <code>Generational</code>, which will ensure that
   * <i>only one thread</i> executes this.
   */
  protected final void globalMaturePrepare() {
    if (fullHeapGC) {
      hi = !hi;         // flip the semi-spaces
      activeMatureSpace = (hi ? matureSpace1 : matureSpace0);
      matureSpace0.prepare(hi);
      matureSpace1.prepare(!hi);
    }
  }

  /**
   * Perform operations pertaining to the mature space with
   * <i>thread-local</i> scope in preparation for a collection.  This
   * is called by <code>Generational</code>, which will ensure that
   * <i>all threads</i> execute this.
   */
  protected final void threadLocalMaturePrepare(int count) {
    if (fullHeapGC) mature.rebind(((hi) ? matureSpace1 : matureSpace0)); 
  }

  /**
   * Perform operations pertaining to the mature space with
   * <i>thread-local</i> scope to clean up at the end of a collection.
   * This is called by <code>Generational</code>, which will ensure
   * that <i>all threads</i> execute this.<p>
   */
  protected final void threadLocalMatureRelease(int count) {
  } // do nothing

  /**
   * Perform operations pertaining to the mature space with
   * <i>global</i> scope to clean up at the end of a collection.  This
   * is called by <code>Generational</code>, which will ensure that
   * <i>only one</i> thread executes this.<p>
   */
  protected final void globalMatureRelease() {
    if (fullHeapGC) ((hi) ? matureSpace0 : matureSpace1).release();
  }


  /****************************************************************************
   *
   * Object processing and tracing
   */

  /**
   * Forward the mature space object referred to by a given address
   * and update the address if necessary.  This <i>does not</i>
   * enqueue the referent for processing; the referent must be
   * explicitly enqueued if it is to be processed.<p>
   *
   * @param location The location whose referent is to be forwarded if
   * necessary.  The location will be updated if the referent is
   * forwarded.
   * @param object The referent object.
   * @param space The space in which the referent object resides.
   */
  protected static final void forwardMatureObjectLocation(Address location,
                                                          ObjectReference object) {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(fullHeapGC);
    if ((hi && Space.isInSpace(MS0, object)) || 
        (!hi && Space.isInSpace(MS1, object)))
      location.store(CopySpace.forwardObject(object));
  }

  /**
   * If the object in question has been forwarded, return its
   * forwarded value.<p>
   *
   * @param object The object which may have been forwarded.
   * @param space The space in which the object resides.
   * @return The forwarded value for <code>object</code>.
   */
  public static final ObjectReference getForwardedMatureReference(ObjectReference object) {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(fullHeapGC);
    if ((hi && Space.isInSpace(MS0, object)) || 
        (!hi && Space.isInSpace(MS1, object))) {
      if (Assert.VERIFY_ASSERTIONS)
	Assert._assert(CopySpace.isForwarded(object));
      return CopySpace.getForwardingPointer(object);
    } else
      return object;
    }

  /**
   * Trace a reference into the mature space during GC.  This involves
   * determining whether the instance is in from space, and if so,
   * calling the <code>traceObject</code> method of the Copy
   * collector.
   *
   * @param object The object reference to be traced.  This is <i>NOT</i> an
   * interior pointer.
   * @return The possibly moved reference.
   */
  protected static final ObjectReference traceMatureObject(ObjectReference object) {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(fullHeapGC || IGNORE_REMSET);
    if (IGNORE_REMSET && !fullHeapGC &&
	(Space.isInSpace(MS0, object) || Space.isInSpace(MS1, object))) {
      CopySpace.markObject(object, ImmortalSpace.immortalMarkState);
      return object;
    } else
      return Space.getSpaceForObject(object).traceObject(object);
  }

  /**  
   * Perform any post-copy actions.
   *
   * @param object The newly allocated object
   * @param typeRef the type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   */
  public final void postCopy(ObjectReference object, ObjectReference typeRef,
                             int size) throws InlinePragma {
    CopySpace.clearGCBits(object);
    if (IGNORE_REMSET) CopySpace.markObject(object, ImmortalSpace.immortalMarkState);
  }

  /**
   * Return true if <code>obj</code> is a live object.
   *
   * @param object The object in question
   * @return True if <code>obj</code> is a live object.
   */
  public static final boolean isLive(ObjectReference object) {
    if (object.isNull()) return false;
    if (!fullHeapGC) {
      if (object.toAddress().GE(NURSERY_START))
	return nurserySpace.isLive(object);
      else
	return true;
    } else {
      Space space = Space.getSpaceForObject(object);
      if (space == nurserySpace)
	return nurserySpace.isLive(object);
      else if (space == matureSpace0)
	return matureSpace0.isLive(object);
      else if (space == matureSpace1)
	return matureSpace1.isLive(object);
      else if (space == loSpace)
	return loSpace.isLive(object);
      else if (space == null) {
	if (Assert.VERIFY_ASSERTIONS) {
	  Log.write("space failure: "); Log.writeln(object);
	}
      }
      return true;
    }
  }

  /**
   * Return true if this object is guaranteed not to move during this
   * collection (i.e. this object is defintely not an unforwarded
   * object).
   *
   * @param object
   * @return True if this object is guaranteed not to move during this
   * collection.
   */
  public static boolean willNotMove(ObjectReference object) {
    if (fullHeapGC) {
      if (hi)
        return !(Space.isInSpace(NS, object) || Space.isInSpace(MS0, object));
      else
        return !(Space.isInSpace(NS, object) || Space.isInSpace(MS1, object));
    } else
      return object.toAddress().LT(NURSERY_START);
  }


  /****************************************************************************
   *
   * Miscellaneous
   */

  /**
   * Show the status of the mature allocator.
   */
  protected final void showMature() {
    mature.show();
  }
}
