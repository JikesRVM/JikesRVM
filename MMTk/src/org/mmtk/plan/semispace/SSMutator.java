/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2006
 */
package org.mmtk.plan.semispace;

import org.mmtk.plan.*;
import org.mmtk.policy.CopyLocal;
import org.mmtk.policy.Space;
import org.mmtk.utility.alloc.AllocAdvice;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.CallSite;
import org.mmtk.utility.scan.*;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements <i>per-mutator thread</i> behavior 
 * and state for the <i>SS</i> plan, which implements a full-heap
 * semi-space collector.<p>
 * 
 * Specifically, this class defines <i>SS</i> mutator-time allocation
 * and per-mutator thread collection semantics (flushing and restoring
 * per-mutator allocator state).<p>
 * 
 * See {@link SS} for an overview of the semi-space algorithm.<p>
 * 
 * @see SS
 * @see SSCollector
 * @see StopTheWorldMutator
 * @see MutatorContext
 * @see SimplePhase#delegatePhase
 * 
 * $Id$
 * 
 * @author Steve Blackburn
 * @author Perry Cheng
 * @author Robin Garner
 * @author Daniel Frampton
 * 
 * @version $Revision$
 * @date $Date$
 */
public class SSMutator extends StopTheWorldMutator implements Uninterruptible {
  /****************************************************************************
   * Instance fields
   */
  protected final CopyLocal ss;

  /****************************************************************************
   * 
   * Initialization
   */

  /**
   * Constructor
   */
  public SSMutator() {
    ss = new CopyLocal(SS.copySpace0);
  }

  /****************************************************************************
   * 
   * Mutator-time allocation
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
    super.postAlloc(object, typeRef, bytes, allocator);
  }

  /**
   * Return the space into which an allocator is allocating.  This
   * particular method will match against those spaces defined at this
   * level of the class hierarchy.  Subclasses must deal with spaces
   * they define and refer to superclasses appropriately.  This exists
   * to support {@link MutatorContext#getOwnAllocator(Allocator)}.
   * 
   * @see MutatorContext#getOwnAllocator(Allocator)
   * @param a An allocator
   * @return The space into which <code>a</code> is allocating, or
   *         <code>null</code> if there is no space associated with
   *         <code>a</code>.
   */
  public final Space getSpaceFromAllocator(Allocator a) {
    if (a == ss) return SS.toSpace();
    return super.getSpaceFromAllocator(a);
  }

  /**
   * Return the allocator instance associated with a space
   * <code>space</code>, for this plan instance.  This exists
   * to support {@link MutatorContext#getOwnAllccator(Allocator)}.
   * 
   * @see MutatorContext#getOwnAllccator(Allocator)
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
  public final AllocAdvice getAllocAdvice(MMType type, int bytes, CallSite callsite, AllocAdvice hint) {
    return null;
  }

  
  /****************************************************************************
   * 
   * Collection
   */

  /**
   * Perform a per-mutator collection phase.
   * 
   * @param phaseId The collection phase to perform
   * @param primary Perform any single-threaded activities using this thread.
   */
  public void collectionPhase(int phaseId, boolean primary)
  throws InlinePragma {
    if (phaseId == SS.PREPARE_MUTATOR) {
      // rebind the allocation bump pointer to the appropriate semispace.
      ss.rebind(SS.toSpace());
      super.collectionPhase(phaseId, primary);
      return;
    }

    if (phaseId == SS.RELEASE_MUTATOR) {
      super.collectionPhase(phaseId, primary);
      return;
    }

    super.collectionPhase(phaseId, primary);
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
