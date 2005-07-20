package org.mmtk.plan.generational.copying;

import org.mmtk.plan.generational.GenLocal;
import org.mmtk.plan.Plan;
import org.mmtk.plan.TraceLocal;
import org.mmtk.policy.CopyLocal;
import org.mmtk.policy.CopySpace;
import org.mmtk.policy.Space;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.vm.ActivePlan;
import org.mmtk.vm.Assert;

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
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 */
public class GenCopyLocal extends GenLocal implements Uninterruptible {

  /******************************************************************
   * Instance variables
   */
  
  /**
   * The allocator for the copying mature space
   */
  private CopyLocal mature;
  
  /**
   * The trace object for full-heap collections
   */
  private GenCopyMatureTraceLocal matureTrace;
  
  /**
   * Constructor
   */
  public GenCopyLocal() {
    mature = new CopyLocal(GenCopy.toSpace());
    matureTrace = new GenCopyMatureTraceLocal(global().matureTrace, this);
  }
  
  /**
   * @return The active global plan as a <code>GenCopy</code> instance.
   */
  private static final GenCopy global() { 
    return (GenCopy) ActivePlan.global(); 
  }

  /****************************************************************************
   *
   * Allocation
   */
  
  /**
   * Allocate space (for an object) in the specified space
   *
   * @param bytes The size of the space to be allocated (in bytes)
   * @param align The requested alignment.
   * @param offset The alignment offset.
   * @param allocator The allocator to allocate from
   * @return The address of the first byte of the allocated region
   */
  public final Address alloc(int bytes, int align, int offset, int allocator)
  throws InlinePragma {
    if (allocator == GenCopy.ALLOC_MATURE) { 
      return mature.alloc(bytes, align, offset);
    }
    return super.alloc(bytes, align, offset, allocator);
  }
  
  /**
   * Perform post-allocation initialization of an object
   *
   * @param object The newly allocated object
   * @param typeRef the type reference for the instance being created
   * @param allocator The allocator to allocate from
   * @param bytes The size of the space allocated (in bytes)
   */
  public final void postAlloc(ObjectReference object, ObjectReference typeRef, 
      int bytes, int allocator) 
    throws InlinePragma {
    // nothing to be done
    if (allocator == GenCopy.ALLOC_MATURE) return;
    super.postAlloc(object,typeRef,bytes,allocator);
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
  public Address allocCopy(ObjectReference original, int bytes,
                           int align, int offset, int allocator)
    throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) {
      Assert._assert(bytes <= Plan.LOS_SIZE_THRESHOLD);
      Assert._assert(allocator == GenCopy.ALLOC_MATURE);
    }

    Address result = mature.alloc(bytes, align, offset);
    return result;
  }


  /**  
   * Perform any post-copy actions.  In this case we clear any bits used 
   * for this object's GC metadata.
   *
   * @param object The newly allocated object
   * @param typeRef the type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   * @param allocator The allocator to allocate from
   */
  public final void postCopy(ObjectReference object, ObjectReference typeRef,
      int bytes, int allocator) throws InlinePragma {
    CopySpace.clearGCBits(object);
    if (GenCopy.IGNORE_REMSETS) 
      CopySpace.markObject(getCurrentTrace(),object, GenCopy.immortalSpace.getMarkState());
  }

  /**
   * Return the space into which an allocator is allocating.  This
   * particular method will match against those spaces defined at this
   * level of the class hierarchy.  Subclasses must deal with spaces
   * they define and refer to superclasses appropriately.  This exists
   * to support {@link org.mmtk.plan.PlanLocal#getOwnAllocator(Allocator)}.
   *
   * @see org.mmtk.plan.PlanLocal#getOwnAllocator(Allocator)
   * @param a An allocator
   * @return The space into which <code>a</code> is allocating, or
   * <code>null</code> if there is no space associated with
   * <code>a</code>.
   */
  public final Space getSpaceFromAllocator(Allocator a) {
    if (a == mature) return GenCopy.toSpace();
    return super.getSpaceFromAllocator(a);
  }
  
  /**
   * Return the allocator instance associated with a space
   * <code>space</code>, for this plan instance.  This exists
   * to support {@link org.mmtk.plan.PlanLocal#getOwnAllocator(Allocator)}.
   *
   * @see org.mmtk.plan.PlanLocal#getOwnAllocator(Allocator)
   * @param space The space for which the allocator instance is desired.
   * @return The allocator instance associated with this plan instance
   * which is allocating into <code>space</code>, or <code>null</code>
   * if no appropriate allocator can be established.
   */
  public final Allocator getAllocatorFromSpace(Space space) {
    if (space == GenCopy.matureSpace0 || space == GenCopy.matureSpace1) return mature;
    return super.getAllocatorFromSpace(space);
  }

  /*****************************************************************************
   * 
   * Collection
   */

  /**
   * Execute a (local) collection phase.
   *
   * @param phaseId The phase to execute.
   * @param participating Is the thread that owns this local participating?
   * @param primary True if this thread should peform local single-threaded
   * actions.
   */
  public void collectionPhase(int phaseId, boolean participating, 
                              boolean primary) {
    if (global().collectMatureSpace()) {
      if (phaseId == GenCopy.PREPARE) {
        super.collectionPhase(phaseId, participating, primary);
        if (global().gcFullHeap) mature.rebind(GenCopy.toSpace());       
      }
      if (phaseId == GenCopy.START_CLOSURE) {
        matureTrace.startTrace();
        return;
      }
      
      if (phaseId == GenCopy.COMPLETE_CLOSURE) {
        matureTrace.completeTrace();
        return;
      }
      if (phaseId == GenCopy.RELEASE) {
        matureTrace.release();
        super.collectionPhase(phaseId, participating, primary);
        return;
      }
    }
    super.collectionPhase(phaseId, participating, primary);
  }

  /**
   * Show the status of the mature allocator.
   */
  protected final void showMature() {
    mature.show();
  }

  public final TraceLocal getFullHeapTrace() { return matureTrace; }
  
  
}
