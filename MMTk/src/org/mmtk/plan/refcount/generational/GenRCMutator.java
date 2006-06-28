/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package org.mmtk.plan.refcount.generational;

import org.mmtk.plan.refcount.RCBaseMutator;
import org.mmtk.plan.refcount.fullheap.RC;
import org.mmtk.policy.Space;
import org.mmtk.policy.CopyLocal;
import org.mmtk.policy.RefCountLocal;
import org.mmtk.policy.RefCountSpace;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.scan.*;
import org.mmtk.utility.Constants;

import org.mmtk.vm.ActivePlan;
import org.mmtk.vm.Assert;
import org.mmtk.vm.Barriers;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

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
 * See {@link GenRC} for a description of the generational reference counting
 * algorithm.<p>
 * 
 * FIXME Currently GenRC does not properly separate mutator and collector
 * behaviors, so most of the collection logic in GenRCMutator should really
 * be per-collector thread, not per-mutator thread.
 * 
 * @see RCBaseMutator
 * @see GenRC
 * @see GenRCCollector
 * @see org.mmtk.plan.StopTheWorldMutator
 * @see org.mmtk.plan.MutatorContext
 * @see org.mmtk.plan.SimplePhase#delegatePhase
 * 
 * $Id$
 * 
 * @author Steve Blackburn
 * @author Robin Garner
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class GenRCMutator extends RCBaseMutator implements Uninterruptible, Constants {

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
   * @param allocator The allocator number to be used for this allocation
   * @param bytes The size of the space to be allocated (in bytes)
   * @param align The requested alignment
   * @param offset The alignment offset
   * @return The address of the first byte of the allocated region
   */
  public final Address alloc(int bytes, int align, int offset, int allocator)
      throws InlinePragma {
    // TODO: STEAL NURSERY GC HEADER
    switch (allocator) {
    case GenRC.ALLOC_NURSERY: return nursery.alloc(bytes, align, offset);
    case      GenRC.ALLOC_RC: return rc.alloc(bytes, align, offset, false);
    case     GenRC.ALLOC_LOS: return los.alloc(bytes, align, offset);
    default:                  return super.alloc(bytes,align,offset,allocator);
    }
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
  public final void postAlloc(ObjectReference ref, ObjectReference typeRef,
      int bytes, int allocator)
  throws InlinePragma {
    switch (allocator) {
    case GenRC.ALLOC_NURSERY: return;
    case GenRC.ALLOC_RC:
      RefCountLocal.unsyncLiveObject(ref);
    case GenRC.ALLOC_LOS:
      modBuffer.push(ref);
      RefCountSpace.initializeHeader(ref, typeRef, true);
      decBuffer.push(ref);
      if (RefCountSpace.RC_SANITY_CHECK) RefCountLocal.sanityAllocCount(ref);
      return;
    case GenRC.ALLOC_IMMORTAL:
      if (RefCountSpace.RC_SANITY_CHECK) rc.addImmortalObject(ref);
      modBuffer.push(ref);
      return;
    default:
      super.postAlloc(ref, typeRef, bytes, allocator);
      return;
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
   *         <code>null</code> if there is no space associated with
   *         <code>a</code>.
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
  public final void writeBarrier(ObjectReference src, Address slot,
      ObjectReference tgt, Offset metaDataA,
      int metaDataB, int mode)
      throws InlinePragma {
    if (GenRC.GATHER_WRITE_BARRIER_STATS) GenRC.wbFast.inc();
    if (RefCountSpace.logRequired(src))
      writeBarrierSlow(src);
    Barriers.performWriteInBarrier(src, slot, tgt, metaDataA, metaDataB, mode);
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
  public final boolean writeBarrier(ObjectReference src, Offset srcOffset,
      ObjectReference dst, Offset dstOffset,
      int bytes)
  throws InlinePragma {
    if (GenRC.GATHER_WRITE_BARRIER_STATS) GenRC.wbFast.inc();
    if (RefCountSpace.logRequired(dst))
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
  private final void writeBarrierSlow(ObjectReference src)
      throws NoInlinePragma {
    if (Assert.VERIFY_ASSERTIONS)
      Assert._assert(!Space.isInSpace(GenRC.NS, src));
    if (RefCountSpace.attemptToLog(src)) {
      if (GenRC.GATHER_WRITE_BARRIER_STATS) GenRC.wbSlow.inc();
      modBuffer.push(src);
      Scan.enumeratePointers(src, decEnum);
      RefCountSpace.makeLogged(src);
    }
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
  public void collectionPhase(int phaseId, boolean primary) {
    if (phaseId == RC.PREPARE_MUTATOR) {
      rc.prepare(primary);
      nursery.rebind(GenRC.nurserySpace);
      return;
    }

    if (phaseId == RC.RELEASE_MUTATOR) {
      rc.release(this, primary);
      return;
    }

    super.collectionPhase(phaseId, primary);
  }

  /****************************************************************************
   * 
   * Miscellaneous
   */

  /** ************************************************************************ */
  /** ************************************************************************ */
  /** ************************************************************************ */
  /** ************************************************************************ */
  // FIXME The remainder should be part of the collector!
  /** ************************************************************************ */
  /** ************************************************************************ */
  /** ************************************************************************ */
  /** ************************************************************************ */

  
  /****************************************************************************
   * 
   * Pointer enumeration
   */

  /**
  * A field of an object rememebered in the modified objects buffer
  * is being enumerated by ScanObject.  If the field points to the
  * nursery, then add the field address to the locations buffer.  If
  * the field points to the RC space, increment the count of the
  * referent object.
   * 
  * @param objLoc The address of a reference field with an object
  * being enumerated.
   */
  public final void enumerateModifiedPointerLocation(Address objLoc)
      throws InlinePragma {
    ObjectReference object = objLoc.loadObjectReference();
    if (!object.isNull()) {
      if (Space.isInSpace(GenRC.NS, object)) {
        // FIXME. THIS ONLY EXISTS BECUASE MUTATOR & COLLECTOR ARE CONFUSED!
       ((GenRCCollector) ActivePlan.collector()).trace.traceObjectLocation(objLoc);
      } else if (GenRC.isRCObject(object))
        RefCountSpace.incRC(object);
    }
  }

  
}
