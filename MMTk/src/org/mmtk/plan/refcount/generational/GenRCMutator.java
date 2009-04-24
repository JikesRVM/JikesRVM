/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.plan.refcount.generational;

import org.mmtk.plan.refcount.RCBase;
import org.mmtk.plan.refcount.RCBaseMutator;
import org.mmtk.policy.CopyLocal;
import org.mmtk.policy.Space;
import org.mmtk.utility.alloc.Allocator;
import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;

/**
 * This class implements the mutator context for a simple reference counting collector.
 */
@Uninterruptible
public class GenRCMutator extends RCBaseMutator {
  /************************************************************************
   * Instance fields
   */
  private final CopyLocal nursery;

  public GenRCMutator() {
    nursery = new CopyLocal(GenRC.nurserySpace);
  }

  /****************************************************************************
   *
   * Mutator-time allocation
   */

  /**
   * Allocate memory for an object.
   *
   * @param bytes The number of bytes required for the object.
   * @param align Required alignment for the object.
   * @param offset Offset associated with the alignment.
   * @param allocator The allocator associated with this request.
   * @param site Allocation site
   * @return The address of the newly allocated memory.
   */
  @Inline
  public Address alloc(int bytes, int align, int offset, int allocator, int site) {
    if (allocator == GenRC.ALLOC_NURSERY) {
      return nursery.alloc(bytes, align, offset);
    }
    return super.alloc(bytes, align, offset, allocator, site);
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
  @Inline
  public void postAlloc(ObjectReference ref, ObjectReference typeRef, int bytes, int allocator) {
    if (allocator == GenRC.ALLOC_NURSERY) {
      return;
    }
    super.postAlloc(ref, typeRef, bytes, allocator);
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
    if (space == GenRC.nurserySpace) return nursery;

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
   * @param primary perform any single-threaded local activities.
   */
  public final void collectionPhase(short phaseId, boolean primary) {
    if (phaseId == RCBase.PREPARE) {
      nursery.rebind(GenRC.nurserySpace);
      super.collectionPhase(phaseId, primary);
      return;
    }

    super.collectionPhase(phaseId, primary);
  }
}
