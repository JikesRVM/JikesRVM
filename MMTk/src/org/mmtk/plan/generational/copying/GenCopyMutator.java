/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2006
 */
package org.mmtk.plan.generational.copying;

import org.mmtk.plan.generational.GenMutator;
import org.mmtk.policy.CopyLocal;
import org.mmtk.policy.Space;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.vm.ActivePlan;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements <i>per-mutator thread</i> behavior and state for
 * the <code>GenCopy</code> two-generational copying collector.<p>
 * 
 * Specifically, this class defines mutator-time semantics specific to the
 * mature generation (<code>GenMutator</code> defines nursery semantics).
 * In particular the mature space allocator is defined (for mutator-time
 * allocation into the mature space via pre-tenuring), and the mature space
 * per-mutator thread collection time semantics are defined (rebinding
 * the mature space allocator).<p>
 * 
 * @see GenCopy for a description of the <code>GenCopy</code> algorithm.
 * 
 * @see GenCopy
 * @see GenCopyCollector
 * @see GenMutator
 * @see org.mmtk.plan.StopTheWorldMutator
 * @see org.mmtk.plan.MutatorContext
 * @see org.mmtk.plan.SimplePhase#delegatePhase
 * 
 * $Id$
 * 
 * @author Steve Blackburn
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 */
public class GenCopyMutator extends GenMutator implements Uninterruptible {
  /******************************************************************
   * Instance fields
   */

  /**
   * The allocator for the copying mature space (the mutator may
   * "pretenure" objects into this space otherwise used only by
   * the collector)
   */
  private CopyLocal mature;

  /****************************************************************************
   * 
   * Initialization
   */

  /**
   * Constructor
   */
  public GenCopyMutator() {
    mature = new CopyLocal(GenCopy.toSpace());
  }

  /****************************************************************************
   * 
   * Mutator-time allocation
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
    super.postAlloc(object, typeRef, bytes, allocator);
  }

  /**
   * Return the space into which an allocator is allocating.  This
   * particular method will match against those spaces defined at this
   * level of the class hierarchy.  Subclasses must deal with spaces
   * they define and refer to superclasses appropriately.  This exists
   * to support {@link org.mmtk.plan.MutatorContext#getOwnAllocator(Allocator)}.
   * 
   * @see org.mmtk.plan.MutatorContext#getOwnAllocator(Allocator)
   * @param a An allocator
   * @return The space into which <code>a</code> is allocating, or
   *         <code>null</code> if there is no space associated with
   *         <code>a</code>.
   */
  public final Space getSpaceFromAllocator(Allocator a) {
    if (a == mature) return GenCopy.toSpace();
    return super.getSpaceFromAllocator(a);
  }

  /**
   * Return the allocator instance associated with a space
   * <code>space</code>, for this plan instance.  This exists
   * to support {@link org.mmtk.plan.MutatorContext#getOwnAllocator(Allocator)}.
   * 
   * @see org.mmtk.plan.MutatorContext#getOwnAllocator(Allocator)
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
   * Execute a per-mutator collection phase.
   * 
   * @param phaseId The phase to execute.
   * @param primary True if this thread should peform local single-threaded
   * actions.
   */
  public void collectionPhase(int phaseId, boolean primary) {
    if (global().traceFullHeap()) {
      if (phaseId == GenCopy.PREPARE_MUTATOR) {
        super.collectionPhase(phaseId, primary);
        if (global().gcFullHeap) mature.rebind(GenCopy.toSpace());       
        return;
      }
    }
    
    super.collectionPhase(phaseId, primary);
  }

  /*****************************************************************************
   * 
   * Miscellaneous
   */

  /** @return The active global plan as a <code>GenCopy</code> instance. */
  private static final GenCopy global() {
    return (GenCopy) ActivePlan.global();
  }

}
