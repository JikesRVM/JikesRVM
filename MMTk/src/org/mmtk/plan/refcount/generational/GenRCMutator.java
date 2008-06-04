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
package org.mmtk.plan.refcount.generational;

import org.mmtk.plan.refcount.RCBaseMutator;
import org.mmtk.plan.refcount.RCHeader;
import org.mmtk.policy.CopyLocal;
import org.mmtk.policy.Space;

import org.mmtk.utility.Constants;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements <i>per-mutator thread</i> behavior and
 * state for the <i>GenRC</i> plan, a generational reference
 * counting collector.<p>
 *
 * Specifically, this class defines mutation-time allocation (allocation
 * into the nursery and "pre-tenuring" into the mature space), write barriers
 * and per-mutator collection semantics (such as flushing rememberd sets
 * flushing and initializing allocators).<p>
 *
 * @see GenRC for a description of the generational reference counting
 * algorithm.<p>
 *
 * FIXME Currently GenRC does not properly separate mutator and collector
 * behaviors, so most of the collection logic in GenRCMutator should really
 * be per-collector thread, not per-mutator thread.
 *
 * @see org.mmtk.plan.refcount.RCBaseMutator
 * @see GenRC
 * @see GenRCCollector
 * @see org.mmtk.plan.StopTheWorldMutator
 * @see org.mmtk.plan.MutatorContext
 */
@Uninterruptible
public class GenRCMutator extends RCBaseMutator implements Constants {
  /****************************************************************************
   * Instance fields
   */

  public CopyLocal nursery = new CopyLocal(GenRC.nurserySpace);

  /****************************************************************************
   *
   * Mutator-time allocation
   */

  /**
   * Allocate space (for an object)
   *
   * @param bytes The size of the space to be allocated (in bytes)
   * @param align The requested alignment
   * @param offset The alignment offset
   * @param allocator The allocator number to be used for this allocation
   * @param site Allocation site.
   * @return The address of the first byte of the allocated region
   */
  @Inline
  public final Address alloc(int bytes, int align, int offset, int allocator, int site) {
    if (allocator == GenRC.ALLOC_NURSERY) {
      return nursery.alloc(bytes, align, offset);
    }
    return super.alloc(bytes,align,offset,allocator, site);
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
  public final void postAlloc(ObjectReference ref, ObjectReference typeRef,
      int bytes, int allocator) {
    if (allocator != GenRC.ALLOC_NURSERY) {
      super.postAlloc(ref,typeRef,bytes,allocator);
    }
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
   * <code>null</code> if there is no space associated with
   * <code>a</code>.
   */
  public final Space getSpaceFromAllocator(Allocator a) {
    if (a == nursery) return GenRC.nurserySpace;
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
   * @param primary Perform any single-threaded activities using this thread.
   */
  @Inline
  public void collectionPhase(short phaseId, boolean primary) {

    if (phaseId == GenRC.PREPARE) {
      nursery.rebind(GenRC.nurserySpace);
    }

    super.collectionPhase(phaseId, primary);
  }

  /****************************************************************************
   *
   * Write barriers.
   */

  /**
   * A new reference is about to be created.  Perform appropriate
   * write barrier action.<p>
   *
   * In this case, we remember the address of the source of the
   * pointer if the new reference points into the nursery from
   * non-nursery space.
   *
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be
   * stored.
   * @param tgt The target of the new reference
   * @param metaDataA An int that assists the host VM in creating a store
   * @param metaDataB An int that assists the host VM in creating a store
   * @param mode The mode of the store (eg putfield, putstatic etc)
   */
  @Inline
  public final void writeBarrier(ObjectReference src, Address slot,
                                 ObjectReference tgt, Offset metaDataA,
                                 int metaDataB, int mode) {
    if (GenRC.GATHER_WRITE_BARRIER_STATS) GenRC.wbFast.inc();
    if (RCHeader.logRequired(src))
      writeBarrierSlow(src);
    VM.barriers.performWriteInBarrier(src, slot, tgt, metaDataA, metaDataB, mode);
  }

  /**
   * Attempt to atomically exchange the value in the given slot
   * with the passed replacement value. If a new reference is
   * created, we must then take appropriate write barrier actions.<p>
   *
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be
   * stored.
   * @param old The old reference to be swapped out
   * @param tgt The target of the new reference
   * @param metaDataA An int that assists the host VM in creating a store
   * @param metaDataB An int that assists the host VM in creating a store
   * @param mode The context in which the store occured
   * @return True if the swap was successful.
   */
  public boolean tryCompareAndSwapWriteBarrier(ObjectReference src, Address slot,
      ObjectReference old, ObjectReference tgt, Offset metaDataA,
      int metaDataB, int mode) {
    if (RCHeader.logRequired(src))
      writeBarrierSlow(src);
    return VM.barriers.tryCompareAndSwapWriteInBarrier(src, slot, old, tgt, metaDataA, metaDataB, mode);
  }

  /**
   * A number of references are about to be copied from object
   * <code>src</code> to object <code>dst</code> (as in an array
   * copy).  Thus, <code>dst</code> is the mutated object.  Take
   * appropriate write barrier actions.<p>
   *
   * In this case, we simply remember the mutated source object.
   *
   * @param src The source of the values to copied
   * @param srcOffset The offset of the first source address, in
   * bytes, relative to <code>src</code> (in principle, this could be
   * negative).
   * @param dst The mutated object, i.e. the destination of the copy.
   * @param dstOffset The offset of the first destination address, in
   * bytes relative to <code>tgt</code> (in principle, this could be
   * negative).
   * @param bytes The size of the region being copied, in bytes.
   * @return True if the update was performed by the barrier, false if
   * left to the caller (always false in this case).
   */
  @Inline
  public final boolean writeBarrier(ObjectReference src, Offset srcOffset,
                                    ObjectReference dst, Offset dstOffset,
                                    int bytes) {
    if (GenRC.GATHER_WRITE_BARRIER_STATS) GenRC.wbFast.inc();
    if (RCHeader.logRequired(dst))
      writeBarrierSlow(dst);
    return false;
  }

  /**
   * This object <i>may</i> need to be logged because we <i>may</i>
   * have been the first to update it.  We can't be sure because of
   * the (delibrate) lack of synchronization in the
   * <code>logRequired()</code> method, which can generate a race
   * condition.  So, we now use an atomic operation to arbitrate the
   * race.  If we successful, we will log the object, enumerating its
   * pointers with the decrement enumerator and marking it as logged.
   *
   * @param src The object being mutated.
   */
  @NoInline
  private void writeBarrierSlow(ObjectReference src) {
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(!Space.isInSpace(GenRC.NS, src));
    }
    if (RCHeader.attemptToLog(src)) {
      if (GenRC.GATHER_WRITE_BARRIER_STATS) GenRC.wbSlow.inc();
      modBuffer.push(src);
      decBuffer.processChildren(src);
      RCHeader.makeLogged(src);
    }
  }
}
