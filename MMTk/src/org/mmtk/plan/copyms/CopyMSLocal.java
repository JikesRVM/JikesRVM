/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan.copyms;

import org.mmtk.plan.*;
import org.mmtk.policy.CopyLocal;
import org.mmtk.policy.MarkSweepLocal;
import org.mmtk.policy.Space;

import org.mmtk.utility.alloc.Allocator;
import org.mmtk.vm.ActivePlan;
import org.mmtk.vm.Assert;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements the global state of a full-heap collector
 * with a copying nursery and mark-sweep mature space.  Unlike a full 
 * generational collector, there is no write barrier, no remembered set, and 
 * every collection is full-heap.
 *
 * All plans make a clear distinction between <i>global</i> and
 * <i>thread-local</i> activities, and divides global and local state
 * into separate class hierarchies.  Global activities must be
 * synchronized, whereas no synchronization is required for
 * thread-local activities.  There is a single instance of Plan (or the
 * appropriate sub-class), and a 1:1 mapping of PlanLocal to "kernel
 * threads" (aka CPUs or in Jikes RVM, VM_Processors).  Thus instance
 * methods of PlanLocal allow fast, unsychronized access to functions such as
 * allocation and collection.
 *
 * The local instance provides fast unsynchronized access to thread-local
 * structures, calling up to the slow, synchronized global class where
 * necessary. This mapping of threads to instances is crucial to understanding
 * the correctness and performance properties of MMTk plans.
 *
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 */
public class CopyMSLocal extends StopTheWorldLocal implements Uninterruptible {

  /**
   * @return the active global plan as an <code>MS</code> instance.
   */
  private static final CopyMS global() throws InlinePragma {
    return (CopyMS)ActivePlan.global();
  }

  /****************************************************************************
   * Instance variables
   */

  private MarkSweepLocal ms;
  private CopyLocal nursery;
  private CopyMSTraceLocal trace;

  /**
   * Create a new (local) instance. 
   */
  public CopyMSLocal() {
    ms = new MarkSweepLocal(CopyMS.msSpace);
    nursery = new CopyLocal(CopyMS.nurserySpace);
    trace = new CopyMSTraceLocal(global().trace);
  }

  /**
   * @return The current trace instance.
   */
  public final TraceLocal getCurrentTrace() {
    return trace;
  }

  /****************************************************************************
  *
  * Allocation
  */

  /**
   * Allocate memory for an object.  This class handles the default allocator
   * from the mark sweep space, and delegates everything else to the
   * superclass.
   *
   * @param bytes The number of bytes required for the object.
   * @param align Required alignment for the object.
   * @param offset Offset associated with the alignment.
   * @param allocator The allocator associated with this request.
   * @return The low address of the allocated memory.
   */
  public Address alloc(int bytes, int align, int offset, int allocator)
    throws InlinePragma {
    if (allocator == CopyMS.ALLOC_DEFAULT)
      return nursery.alloc(bytes, align, offset);
    if (allocator == CopyMS.ALLOC_MS)
      return ms.alloc(bytes, align, offset, false);

    return super.alloc(bytes, align, offset, allocator);
  }

  /**
   * Perform post-allocation actions.  Initialize the object header for
   * objects in the mark-sweep space, and delegate to the superclass for
   * other objects.
   *
   * @param ref The newly allocated object
   * @param typeRef the type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   * @param allocator The allocator number to be used for this allocation
   */
  public void postAlloc(ObjectReference ref, ObjectReference typeRef,
                              int bytes, int allocator) throws InlinePragma {
    if (allocator == CopyMS.ALLOC_DEFAULT)
      return;
    else if (allocator == CopyMS.ALLOC_MS)
      CopyMS.msSpace.initializeHeader(ref);
    else
      super.postAlloc(ref, typeRef, bytes, allocator);
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
                                    int align, int offset, int allocator)
    throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) {
      Assert._assert(bytes <= Plan.LOS_SIZE_THRESHOLD);
      Assert._assert(allocator == CopyMS.ALLOC_MS);
    }
    return ms.alloc(bytes, align, offset, CopyMS.msSpace.inMSCollection());
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
    CopyMS.msSpace.writeMarkBit(object);
    MarkSweepLocal.liveObject(object);
  }

  /**
   * Perform a (local) collection phase.
   *
   * @param phaseId The collection phase to perform
   * @param participating Is this thread participating in collection
   *        (as opposed to blocked in a JNI call)
   * @param primary Use this thread for single-threaded local activities.
   */
  public final void collectionPhase(int phaseId, boolean participating, boolean primary)
    throws InlinePragma {
    if (phaseId == CopyMS.PREPARE) {
      super.collectionPhase(phaseId, participating, primary);
      ms.prepare();
      trace.prepare();
      return;
    }

    if (phaseId == CopyMS.START_CLOSURE) {
      trace.startTrace();
      return;
    }

    if (phaseId == CopyMS.COMPLETE_CLOSURE) {
      trace.completeTrace();
      return;
    }

    if (phaseId == CopyMS.RELEASE) {
      nursery.reset();
      ms.release();
      trace.release();
      super.collectionPhase(phaseId, participating, primary);
      return;
    }

    super.collectionPhase(phaseId, participating, primary);
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
    if (a == nursery) return CopyMS.nurserySpace;
    if (a == ms) return CopyMS.msSpace;
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
    if (space == CopyMS.nurserySpace) return nursery;
    if (space == CopyMS.msSpace) return ms;
    return super.getAllocatorFromSpace(space);
  }
}
