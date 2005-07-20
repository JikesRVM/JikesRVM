/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan.marksweep;

import org.mmtk.plan.*;
import org.mmtk.policy.MarkSweepLocal;
import org.mmtk.policy.Space;

import org.mmtk.utility.alloc.Allocator;
import org.mmtk.vm.ActivePlan;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * Thread-local state and methods for a full-heap mark-sweep collector.
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
public class MSLocal extends StopTheWorldLocal implements Uninterruptible {

  /** @return The active global plan as an <code>MS</code> instance. */
  private static final MS global() throws InlinePragma {
    return (MS)ActivePlan.global();
  }

  /****************************************************************************
   * Instance variables
   */

  private MarkSweepLocal ms;
  private MSTraceLocal trace;

  public MSLocal() {
    ms = new MarkSweepLocal(MS.msSpace);
    trace = new MSTraceLocal(global().msTrace);
  }

  /** @return The current trace instance. */
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
    if (allocator == MS.ALLOC_DEFAULT) {
      return ms.alloc(bytes, align, offset, false);
    }
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
    if (allocator == MS.ALLOC_DEFAULT)
      MS.msSpace.initializeHeader(ref);
    else
      super.postAlloc(ref, typeRef, bytes, allocator);
  }

  /**
   * Perform a (local) collection phase.
   *
   * @param phaseId The collection phase to perform
   * @param participating Is this thread participating in collection
   *        (as opposed to blocked in a JNI call)
   * @param primary Perform any single-threaded activities using this thread.
   */
  public final void collectionPhase(int phaseId, boolean participating,
                                    boolean primary)
    throws InlinePragma {
    if (phaseId == MS.PREPARE) {
      super.collectionPhase(phaseId, participating, primary);
      ms.prepare();
      trace.prepare();
      return;
    }

    if (phaseId == MS.START_CLOSURE) {
      trace.startTrace();
      return;
    }

    if (phaseId == MS.COMPLETE_CLOSURE) {
      trace.completeTrace();
      return;
    }

    if (phaseId == MS.RELEASE) {
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
    if (a == ms) return MS.msSpace;
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
    if (space == MS.msSpace) return ms;
    return super.getAllocatorFromSpace(space);
  }
}
