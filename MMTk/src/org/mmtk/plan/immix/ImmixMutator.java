/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.plan.immix;

import static org.mmtk.policy.immix.ImmixConstants.TMP_SEPARATE_HOT_ALLOC_SITES;
import static org.mmtk.policy.immix.ImmixConstants.TMP_EXACT_ALLOC_TIME_STRADDLE_CHECK;

import org.mmtk.plan.*;
import org.mmtk.policy.Space;
import org.mmtk.policy.immix.MutatorLocal;

import org.mmtk.utility.alloc.Allocator;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements <i>per-mutator thread</i> behavior
 * and state for the <i>Immix</i> plan, which implements a full-heap
 * immix collector.<p>
 *
 * Specifically, this class defines <i>Immix</i> mutator-time allocation
 * and per-mutator thread collection semantics (flushing and restoring
 * per-mutator allocator state).<p>
 *
 * @see Immix
 * @see CollectorLocal
 * @see StopTheWorldMutator
 * @see MutatorContext
 */
@Uninterruptible
public class ImmixMutator extends StopTheWorldMutator {

  /****************************************************************************
   * Instance fields
   */
  protected final MutatorLocal immix;
  protected final MutatorLocal immixHot;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   */
  public ImmixMutator() {
    if (VM.VERIFY_ASSERTIONS && TMP_EXACT_ALLOC_TIME_STRADDLE_CHECK && TMP_SEPARATE_HOT_ALLOC_SITES)
      VM.assertions._assert(false); // above two options cannot co-exist
    immix = new org.mmtk.policy.immix.MutatorLocal(Immix.immixSpace, false);
    immixHot = (TMP_SEPARATE_HOT_ALLOC_SITES ? new org.mmtk.policy.immix.MutatorLocal(Immix.immixSpace, false) : null);
  }

  /****************************************************************************
   *
   * MutatorLocal-time allocation
   */

  /**
   * Allocate memory for an object. This class handles the default allocator
   * from the mark sweep space, and delegates everything else to the
   * superclass.
   *
   * @param bytes The number of bytes required for the object.
   * @param align Required alignment for the object.
   * @param offset Offset associated with the alignment.
   * @param allocator The allocator associated with this request.
   * @return The low address of the allocated memory.
   */
  @Inline
  public Address alloc(int bytes, int align, int offset, int allocator, int site) {
    if (allocator == Immix.ALLOC_DEFAULT) {
      if (TMP_SEPARATE_HOT_ALLOC_SITES && site < 0)
        return immixHot.alloc(bytes, align, offset);
      else
        return immix.alloc(bytes, align, offset);
    }
    return super.alloc(bytes, align, offset, allocator, site);
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
  @Inline
  public void postAlloc(ObjectReference ref, ObjectReference typeRef,
      int bytes, int allocator) {
    if (allocator == Immix.ALLOC_DEFAULT)
      Immix.immixSpace.postAlloc(ref, bytes, TMP_EXACT_ALLOC_TIME_STRADDLE_CHECK ? immix.getLastAllocLineStraddle() : false);
    else
      super.postAlloc(ref, typeRef, bytes, allocator);
  }

  /**
   * Return the space into which an allocator is allocating.  This
   * particular method will match against those spaces defined at this
   * level of the class hierarchy.  Subclasses must deal with spaces
   * they define and refer to superclasses appropriately.
   *
   * @param a An allocator
   * @return The space into which <code>a</code> is allocating, or
   *         <code>null</code> if there is no space associated with
   *         <code>a</code>.
   */
  public Space getSpaceFromAllocator(Allocator a) {
    if (a == immix || (TMP_SEPARATE_HOT_ALLOC_SITES && a == immixHot)) return Immix.immixSpace;
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
    if (space == Immix.immixSpace) return immix;  // FIXME is it not a problem that we have a 2:1 mapping?
    return super.getAllocatorFromSpace(space);
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
  @Inline
  public void collectionPhase(short phaseId, boolean primary) {

    if (phaseId == Immix.PREPARE) {
      super.collectionPhase(phaseId, primary);
      immix.prepare();
      if (TMP_SEPARATE_HOT_ALLOC_SITES)
        immixHot.prepare();
      return;
    }

    if (phaseId == Immix.RELEASE) {
      immix.release();
      if (TMP_SEPARATE_HOT_ALLOC_SITES)
        immixHot.release();
      super.collectionPhase(phaseId, primary);
      return;
    }

    super.collectionPhase(phaseId, primary);
  }
}
