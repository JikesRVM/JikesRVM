/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.plan.semispace;

import org.mmtk.plan.*;
import org.mmtk.policy.CopySpace;
import org.mmtk.policy.CopyLocal;
import org.mmtk.policy.Space;
import org.mmtk.utility.alloc.AllocAdvice;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.CallSite;
import org.mmtk.utility.scan.*;
import org.mmtk.vm.ActivePlan;
import org.mmtk.vm.Assert;

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
 * @author Robin Garner
 * @author Daniel Frampton
 *
 * @version $Revision$
 * @date $Date$
 */
public class SSLocal extends StopTheWorldLocal implements Uninterruptible {

  /****************************************************************************
   *
   * Instance variables
   */

  // allocators
  protected final CopyLocal ss;
  protected final SSTraceLocal trace;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * @return The active global plan as an <code>SS</code> instance.
   */
  private static final SS global() throws InlinePragma {
    return (SS)ActivePlan.global();
  }

  /**
   * Constructor
   */
  public SSLocal() {
    ss = new CopyLocal(SS.copySpace0);
    trace = new SSTraceLocal(global().ssTrace);
  }

  /**
   * Return the current trace object.
   */
  public TraceLocal getCurrentTrace() {
    return trace;
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
  public Address alloc(int bytes, int align, int offset, int allocator)
    throws InlinePragma {
    if (allocator == SS.ALLOC_SS)
      return ss.alloc(bytes, align, offset);
    else
      return super.alloc(bytes, align, offset, allocator);
  }

  /**
   * Perform post-allocation actions.  For many allocators none are
   * required.
   *
   * @param object The newly allocated object
   * @param typeRef The type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   * @param allocator The allocator number to be used for this allocation
   */
  public void postAlloc(ObjectReference object, ObjectReference typeRef,
                        int bytes, int allocator)
    throws InlinePragma {
    if (allocator == SS.ALLOC_SS) return;
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
      Assert._assert(allocator == SS.ALLOC_SS);
    }

    return ss.alloc(bytes, align, offset);
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
    CopySpace.clearGCBits(object);
  }

  /**
   * Return the space into which an allocator is allocating.  This
   * particular method will match against those spaces defined at this
   * level of the class hierarchy.  Subclasses must deal with spaces
   * they define and refer to superclasses appropriately.  This exists
   * to support {@link PlanLocal#getOwnAllocator(Allocator)}.
   *
   * @see PlanLocal#getOwnAllocator(Allocator)
   * @param a An allocator
   * @return The space into which <code>a</code> is allocating, or
   * <code>null</code> if there is no space associated with
   * <code>a</code>.
   */
  public final Space getSpaceFromAllocator(Allocator a) {
    if (a == ss) return SS.toSpace();
    return super.getSpaceFromAllocator(a);
  }

  /**
   * Return the allocator instance associated with a space
   * <code>space</code>, for this plan instance.  This exists
   * to support {@link PlanLocal#getOwnAllocator(Allocator)}.
   *
   * @see PlanLocal#getOwnAllocator(Allocator)
   * @param space The space for which the allocator instance is desired.
   * @return The allocator instance associated with this plan instance
   * which is allocating into <code>space</code>, or <code>null</code>
   * if no appropriate allocator can be established.
   */
  public final Allocator getAllocatorFromSpace(Space space) {
    if (space == SS.copySpace0 || space == SS.copySpace1) return ss;
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
   * Perform a (local) collection phase.
   */
  public void collectionPhase(int phaseId, boolean participating,
                              boolean primary)
    throws InlinePragma {
    if (phaseId == SS.PREPARE) {
      // rebind the semispace bump pointer to the appropriate semispace.
      ss.rebind(SS.toSpace());
      super.collectionPhase(phaseId, participating,primary);
      return;
    }

    if (phaseId == SS.START_CLOSURE) {
      trace.startTrace();
      return;
    }

    if (phaseId == SS.COMPLETE_CLOSURE) {
      trace.completeTrace();
      return;
    }

    if (phaseId == SS.RELEASE) {
      trace.release();
      super.collectionPhase(phaseId, participating, primary);
      return;
    }

    super.collectionPhase(phaseId, participating, primary);
  }

  /****************************************************************************
   *
   * Object processing and tracing
   */


  /**
   * Return true if the given reference is to an object that is within
   * one of the semi-spaces.
   *
   * @param object The object in question
   * @return True if the given reference is to an object that is within
   * one of the semi-spaces.
   */
  public static final boolean isSemiSpaceObject(ObjectReference object) {
    return Space.isInSpace(SS.SS0, object) || Space.isInSpace(SS.SS1, object);
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
