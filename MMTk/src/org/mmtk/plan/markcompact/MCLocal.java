/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan.markcompact;

import org.mmtk.plan.*;
import org.mmtk.policy.MarkCompactLocal;
import org.mmtk.policy.Space;

import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.sanitychecker.SanityCheckerLocal;
import org.mmtk.vm.ActivePlan;
import org.mmtk.vm.Assert;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * Thread-local state and methods for a full-heap mark-compact collector.
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
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class MCLocal extends StopTheWorldLocal implements Uninterruptible {

  /** @return The active global plan as an <code>MC</code> instance. */
  private static final MC global() throws InlinePragma {
    return (MC)ActivePlan.global();
  }

  /****************************************************************************
   * Instance variables
   */

  private static final boolean TRACE_MARK = false;
  private static final boolean TRACE_FORWARD = true;
  
  private MarkCompactLocal mc;
  private MCMarkTraceLocal markTrace;
  private MCForwardTraceLocal forwardTrace;
  private boolean currentTrace;

  // Sanity checking
  private MCSanityCheckerLocal sanityChecker;

  public MCLocal() {
    mc = new MarkCompactLocal(MC.mcSpace);
    markTrace = new MCMarkTraceLocal(global().markTrace);
    forwardTrace = new MCForwardTraceLocal(global().forwardTrace);
    sanityChecker = new MCSanityCheckerLocal();
  }

  /** @return The current trace instance. */
  public final TraceLocal getCurrentTrace() {
    if (currentTrace == TRACE_MARK) {
      return markTrace;
    } else {
      return forwardTrace;
    }
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
    if (allocator == MC.ALLOC_DEFAULT) {
      return mc.alloc(bytes, align, offset);
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
    if (allocator == MC.ALLOC_DEFAULT)
      MC.mcSpace.initializeHeader(ref);
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
  public Address allocCopy(ObjectReference original, int bytes,
                           int align, int offset, int allocator)
    throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) {
      Assert._assert(allocator == MC.ALLOC_IMMORTAL);
    }

    return immortal.alloc(bytes, align, offset);
  }

  /**
   * Perform any post-copy actions.
   *
   * @param object The newly allocated object
   * @param typeRef the type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   */
  public void postCopy(ObjectReference object, ObjectReference typeRef,
                       int bytes, int allocator)
  throws InlinePragma {
    MC.immortalSpace.postAlloc(object);
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
    if (phaseId == MC.PREPARE) {
      currentTrace = TRACE_MARK;
      super.collectionPhase(phaseId, participating, primary);
      markTrace.prepare();
      return;
    }

    if (phaseId == MC.START_CLOSURE) {
      markTrace.startTrace();
      return;
    }

    if (phaseId == MC.COMPLETE_CLOSURE) {
      markTrace.completeTrace();
      return;
    }

    if (phaseId == MC.RELEASE) {
      markTrace.release();
      super.collectionPhase(phaseId, participating, primary);
      return;
    }
    
    if (phaseId == MC.CALCULATE_FP) {
      mc.calculateForwardingPointers();
      return;
    }

    if (phaseId == MC.COMPACT) {
      mc.compact();
      return;
    }

    if (phaseId == MC.PREPARE_FORWARD) {
      currentTrace = TRACE_FORWARD;
      super.collectionPhase(MC.PREPARE, participating, primary);
      forwardTrace.prepare();
      return;
    }

    if (phaseId == MC.FORWARD_CLOSURE) {
      forwardTrace.startTrace();
      forwardTrace.completeTrace();
      return;
    }

    if (phaseId == MC.RELEASE_FORWARD) {
      forwardTrace.release();
      super.collectionPhase(MC.RELEASE, participating, primary);
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
    if (a == mc) return MC.mcSpace;
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
    if (space == MC.mcSpace) return mc;
    return super.getAllocatorFromSpace(space);
  }
  
  /**
   * @return Return the current sanity checker.
   */
  public SanityCheckerLocal getSanityChecker() {
    return sanityChecker;
  }
}
