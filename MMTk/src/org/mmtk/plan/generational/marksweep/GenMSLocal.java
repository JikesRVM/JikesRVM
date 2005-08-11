/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan.generational.marksweep;

import org.mmtk.plan.Plan;
import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.generational.*;
import org.mmtk.policy.MarkSweepLocal;
import org.mmtk.policy.Space;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.statistics.Stats;

import org.mmtk.vm.ActivePlan;
import org.mmtk.vm.Assert;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

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
 *
 * For general comments about the global/local distinction among classes refer
 * to Plan.java and PlanLocal.java.
 *
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 */
public abstract class GenMSLocal extends GenLocal implements Uninterruptible {

  /**
   * @return The active global plan as a <code>GenMS</code> instance.
   */
  private static final GenMS global() throws InlinePragma {
    return (GenMS)ActivePlan.global();
  }

  /*****************************************************************************
   *
   * Instance fields
   */

  private final MarkSweepLocal mature;
  private final GenMSMatureTraceLocal matureTrace;

  /**
   * Constructor
   *
   */
  public GenMSLocal() {
    mature = new MarkSweepLocal(GenMS.msSpace);
    matureTrace = new GenMSMatureTraceLocal(global().matureTrace, this);
  }

  /****************************************************************************
   *
   * Allocation
   */

  /**
   * Allocate memory for an object.
   *
   * @param bytes The number of bytes required for the object.
   * @param align Required alignment for the object.
   * @param offset Offset associated with the alignment.
   * @param allocator The allocator associated with this request.
   * @return The low address of the allocated memory.
   */
  public final Address alloc(int bytes, int align, int offset, int allocator)
    throws InlinePragma {
    if (allocator == GenMS.ALLOC_MATURE) { 
      return mature.alloc(bytes, align, offset, false);
    }
    return super.alloc(bytes, align, offset, allocator);
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
    if (allocator == GenMS.ALLOC_MATURE) {
      GenMS.msSpace.initializeHeader(ref);
    } else {
      super.postAlloc(ref, typeRef, bytes, allocator);
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
   * @param allocator The allocator to use.
   * @return The address of the first byte of the allocated region
   */
  public final Address allocCopy(ObjectReference original, int bytes,
                                 int align, int offset, int allocator)
    throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) {
      Assert._assert(bytes <= Plan.LOS_SIZE_THRESHOLD);
      Assert._assert(allocator == GenMS.ALLOC_MATURE);
    }
    if (Stats.GATHER_MARK_CONS_STATS) {
      if (Space.isInSpace(GenMS.NURSERY, original)) GenMS.nurseryMark.inc(bytes);
    }
    return mature.alloc(bytes, align, offset, GenMS.msSpace.inMSCollection());
  }

  /**
   * Perform any post-copy actions.
   *
   * @param object The newly allocated object
   * @param typeRef the type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   */
  public final void postCopy(ObjectReference object, ObjectReference typeRef,
                             int bytes, int allocator)
  throws InlinePragma {
    GenMS.msSpace.writeMarkBit(object);
    MarkSweepLocal.liveObject(object);
  }

  /**
   * Return the space into which an allocator is allocating.  This
   * particular method will match against those spaces defined at this
   * level of the class hierarchy.  Subclasses must deal with spaces
   * they define and refer to superclasses appropriately.
   *
   * @param a An allocator
   * @return The space into which <code>a</code> is allocating, or
   * <code>null</code> if there is no space associated with
   * <code>a</code>.
   */
  public Space getSpaceFromAllocator(Allocator a) {
    if (a == mature) return GenMS.msSpace;

    // a does not belong to this plan instance
    return super.getSpaceFromAllocator(a);
  }

  /**
   * Return the allocator instance associated with a space
   * <code>space</code>, for this plan instance.
   *
   * @param space The space for which the allocator instance is desired.
   * @return The allocator instance associated with this plan instance
   * which is allocating into <code>space</code>, or <code>null</code>
   * if no appropriate allocator can be established.
   */
  public Allocator getAllocatorFromSpace(Space space) {
    if (space == GenMS.msSpace) return mature;
    return super.getAllocatorFromSpace(space);
  }

  /*****************************************************************************
   *
   * Collection
   */

  /**
   * Perform a (local) collection phase.
   *
   * @param phaseId Collection phase to perform
   * @param participating Is this thread participating or blocked in JNI
   * @param primary Is this thread to do the one-off thread-local tasks
   */
  public void collectionPhase(int phaseId, boolean participating,
                              boolean primary)
    throws NoInlinePragma {
    if (global().collectMatureSpace()) {
      if (phaseId == GenMS.PREPARE) {
        super.collectionPhase(phaseId, participating, primary);
        matureTrace.prepare();
        if (global().gcFullHeap) mature.prepare();
        return;
      }

      if (phaseId == GenMS.START_CLOSURE) {
        matureTrace.startTrace();
        return;
      }

      if (phaseId == GenMS.COMPLETE_CLOSURE) {
        matureTrace.completeTrace();
        return;
      }

      if (phaseId == GenMS.RELEASE) {
        matureTrace.release();
        if (global().gcFullHeap) mature.release();
        super.collectionPhase(phaseId, participating, primary);
        return;
      }
    }

    super.collectionPhase(phaseId, participating, primary);
  }

  public final TraceLocal getFullHeapTrace() throws InlinePragma {
    return matureTrace;
  }
}
