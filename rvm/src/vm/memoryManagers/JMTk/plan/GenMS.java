/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.plan;

import org.mmtk.policy.CopySpace;
import org.mmtk.policy.MarkSweepLocal;
import org.mmtk.policy.MarkSweepSpace;
import org.mmtk.policy.Space;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.Log;
import org.mmtk.vm.Assert;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements the functionality of a two-generation copying
 * collector where <b>the higher generation is a mark-sweep space</b>
 * (free list allocation, mark-sweep collection).  Nursery collections
 * occur when either the heap is full or the nursery is full.  The
 * nursery size is determined by an optional command line argument.
 * If undefined, the nursery size is "infinite", so nursery
 * collections only occur when the heap is full (this is known as a
 * flexible-sized nursery collector).  Thus both fixed and flexible
 * nursery sizes are supported.  Full heap collections occur when the
 * nursery size has dropped to a statically defined threshold,
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
public class GenMS extends Generational implements Uninterruptible {

  /****************************************************************************
   *
   * Class variables
   */
  protected static final boolean COPY_MATURE() { return false; }
  
  // mature space
  private static MarkSweepSpace matureSpace= new MarkSweepSpace("ms", DEFAULT_POLL_FREQUENCY, (float) 0.6);
  private static final int MS = matureSpace.getDescriptor();

  /****************************************************************************
   *
   * Instance variables
   */

  // allocators
  private MarkSweepLocal mature;

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
    activeMatureSpace = matureSpace;
  }

  /**
   * Constructor
   */
  public GenMS() {
    super();
    mature = new MarkSweepLocal(matureSpace);
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
    return mature.alloc(bytes, align, offset, false);
  }

  /**
   * Perform post-allocation initialization of an object
   *
   * @param object The newly allocated object
   */
  protected final void maturePostAlloc(Address object) 
    throws InlinePragma {
    matureSpace.initializeHeader(object);
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
    return mature.alloc(bytes, align, offset, matureSpace.inMSCollection());
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
    if (a == mature) return matureSpace;
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
    if (space == matureSpace) return mature;
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
    matureSpace.prepare();
  }

  /**
   * Perform operations pertaining to the mature space with
   * <i>thread-local</i> scope in preparation for a collection.  This
   * is called by <code>Generational</code>, which will ensure that
   * <i>all threads</i> execute this.
   */
  protected final void threadLocalMaturePrepare(int count) {
    if (fullHeapGC) mature.prepare();
  }

  /**
   * Perform operations pertaining to the mature space with
   * <i>thread-local</i> scope to clean up at the end of a collection.
   * This is called by <code>Generational</code>, which will ensure
   * that <i>all threads</i> execute this.<p>
   */
  protected final void threadLocalMatureRelease(int count) {
    if (fullHeapGC) mature.release();
  }

  /**
   * Perform operations pertaining to the mature space with
   * <i>global</i> scope to clean up at the end of a collection.  This
   * is called by <code>Generational</code>, which will ensure that
   * <i>only one</i> thread executes this.<p>
   */
  protected final void globalMatureRelease() {
    matureSpace.release();
  }

  /****************************************************************************
   *
   * Object processing and tracing
   */

  /**
   * Trace a reference into the mature space during GC.  This simply
   * involves the <code>traceObject</code> method of the mature
   * collector.
   *
   * @param object The object reference to be traced.  This is <i>NOT</i> an
   * interior pointer.
   * @return The possibly moved reference.
   */
  protected static final Address traceMatureObject(Address object) {
    if (Space.isInSpace(MS, object))
      return matureSpace.traceObject(object);
    else
      return Space.getSpaceForObject(object).traceObject(object);
  }

  /**  
   * Perform any post-copy actions.  In this case set the mature space
   * mark bit.
   *
   * @param object The newly allocated object
   * @param typeRef the type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   */
  public final void postCopy(Address object, Address typeRef, int bytes)
    throws InlinePragma {
    matureSpace.writeMarkBit(object);
    MarkSweepLocal.liveObject(object);
  }

  /**
   * Forward the mature space object referred to by a given address
   * and update the address if necessary.  This <i>does not</i>
   * enqueue the referent for processing; the referent must be
   * explicitly enqueued if it is to be processed.<p>
   *
   * <i>In this case do nothing since the mature space is non-copying.</i>
   *
   * @param location The location whose referent is to be forwarded if
   * necessary.  The location will be updated if the referent is
   * forwarded.
   * @param object The referent object.
   * @param space The space in which the referent object resides.
   */
  protected static void forwardMatureObjectLocation(Address location,
                                                    Address object) {}

  /**
   * If the object in question has been forwarded, return its
   * forwarded value.  Mature objects are never forwarded in this
   * collector, so this method is a no-op.<p>
   *
   * @param object The object which may have been forwarded.
   * @param space The space in which the object resides.
   * @return The forwarded value for <code>object</code>.
   */
  public static final Address getForwardedMatureReference(Address object) {
    return object;
  }

  /**
   * Return true if the object resides in a copying space (in this
   * case only nursery objects are in a copying space).
   *
   * @param object The object in question
   * @return True if the object resides in a copying space.
   */
  public final static boolean isCopyObject(Address object) {
    return object.GE(NURSERY_START);
  }

  /**
   * Return true if <code>obj</code> is a live object.
   *
   * @param object The object in question
   * @return True if <code>obj</code> is a live object.
   */
  public final static boolean isLive(Address object) {
    if (object.isZero()) return false;
    if (!fullHeapGC) {
      if (object.GE(NURSERY_START))
 	return nurserySpace.isLive(object);
      else
 	return true;
    } else {
      Space space = Space.getSpaceForObject(object);
      if (space == nurserySpace)
 	return nurserySpace.isLive(object);
      else if (space == matureSpace)
 	return matureSpace.isLive(object);
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
