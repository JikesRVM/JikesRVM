/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package org.mmtk.plan.refcount.generational;

import org.mmtk.plan.refcount.RCBaseLocal;
import org.mmtk.plan.refcount.fullheap.RC;
import org.mmtk.plan.TraceLocal;
import org.mmtk.policy.Space;
import org.mmtk.policy.CopySpace;
import org.mmtk.policy.CopyLocal;
import org.mmtk.policy.RefCountLocal;
import org.mmtk.policy.RefCountSpace;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.options.Options;
import org.mmtk.utility.scan.*;
import org.mmtk.utility.Constants;

import org.mmtk.vm.ActivePlan;
import org.mmtk.vm.Assert;
import org.mmtk.vm.Barriers;
import org.mmtk.vm.Memory;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements a simple non-concurrent reference counting
 * collector.
 *
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Robin Garner
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class GenRCLocal extends RCBaseLocal
  implements Uninterruptible, Constants {

  /**
   * @return The active global plan as a <code>GenRC</code> instance.
   */
  private static final GenRC global() throws InlinePragma {
    return (GenRC)ActivePlan.global();
  }

  /****************************************************************************
  *
  * Instance variables
  */

  // allocators
  public CopyLocal nursery = new CopyLocal(GenRC.nurserySpace);
  public GenRCTraceLocal trace = new GenRCTraceLocal(global().trace);

  public GenRCLocal() {
    global().remsetPool.newClient();
  }

  public void collectionPhase(int phaseId, boolean participating,
                              boolean primary) {
    if (phaseId == RC.PREPARE) {
      rc.prepare(primary);
      nursery.rebind(GenRC.nurserySpace);
      Memory.localPrepareVMSpace();
      return;
    }

    if (phaseId == RC.START_CLOSURE) {
      trace.startTrace();
      return;
    }

    if (phaseId == RC.COMPLETE_CLOSURE) {
      trace.completeTrace();
      return;
    }

    if (phaseId == RC.RELEASE) {
      rc.release(this, primary);
      Memory.localReleaseVMSpace();
      if (Options.verbose.getValue() > 2) rc.printStats();
      return;
    }

    super.collectionPhase(phaseId, participating, primary);
  }

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
      super.postAlloc(ref,typeRef,bytes,allocator);
      return;
    }
  }

  /**
   * Allocate space for copying an object (this method <i>does not</i>
   * copy the object, it only allocates space)
   *
   * @param original A reference to the original object
   * @param bytes The size of the space to be allocated (in bytes)
   * @param align The requested alignment
   * @param offset The alignment offset
   * @return The address of the first byte of the allocated region
   */
  public final Address allocCopy(ObjectReference original, int bytes,
                                    int align, int offset, int allocator)
  throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(allocator == GenRC.ALLOC_RC);
    return rc.alloc(bytes, align, offset, false);  // FIXME is this right???
  }

  /**
   * Perform any post-copy actions.  In this case nothing is required.
   *
   * @param object The newly allocated object
   * @param typeRef the type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   */
  public final void postCopy(ObjectReference object, ObjectReference typeRef,
                             int bytes, int allocator) throws InlinePragma {
    CopySpace.clearGCBits(object);
    RefCountSpace.initializeHeader(object, typeRef, false);
    RefCountSpace.makeUnlogged(object);
    RefCountLocal.unsyncLiveObject(object);
    if (RefCountSpace.RC_SANITY_CHECK) {
      RefCountLocal.sanityAllocCount(object);
    }
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
    if (a == nursery) return GenRC.nurserySpace;
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
    if (space == GenRC.nurserySpace) return nursery;
    return super.getAllocatorFromSpace(space);
  }


  /**
   * Trace a reference during an increment sanity traversal.  This is
   * only used as part of the ref count sanity check, and it forms the
   * basis for a transitive closure that assigns a reference count to
   * each object.
   *
   * @param object The object being traced
   * @param location The location from which this object was
   * reachable, null if not applicable.
   * @param root <code>true</code> if the object is being traced
   * directly from a root.
   */
  public final void incSanityTrace(ObjectReference object, Address location,
                                   boolean root) {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(!object.isNull());
    // if nursery, then get forwarded RC object
    if (Space.isInSpace(GenRC.NS, object)) {
      if (Assert.VERIFY_ASSERTIONS)
        Assert._assert(CopySpace.isForwarded(object));
      object = CopySpace.getForwardingPointer(object);
    }

    if (GenRC.isRCObject(object)) {
      if (RefCountSpace.incSanityRC(object, root))
        Scan.enumeratePointers(object, sanityEnum);
    } else if (RefCountSpace.markSanityRC(object))
      Scan.enumeratePointers(object, sanityEnum);
  }

  /**
   * Trace a reference during a check sanity traversal.  This is only
   * used as part of the ref count sanity check, and it forms the
   * basis for a transitive closure that checks reference counts
   * against sanity reference counts.  If the counts are not matched,
   * an error is raised.
   *
   * @param object The object being traced
   * @param location The location from which this object was
   * reachable, null if not applicable.
   */
  public final void checkSanityTrace(ObjectReference object,
                                     Address location) {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(!object.isNull());
    // if nursery, then get forwarded RC object
    if (Space.isInSpace(GenRC.NS, object)) {
      if (Assert.VERIFY_ASSERTIONS)
        Assert._assert(CopySpace.isForwarded(object));
      object = CopySpace.getForwardingPointer(object);
    }

   if (GenRC.isRCObject(object)) {
     if (RefCountSpace.checkAndClearSanityRC(object)) {
       Scan.enumeratePointers(object, sanityEnum);
       rc.addLiveSanityObject(object);
     }
   } else if (RefCountSpace.unmarkSanityRC(object)) {
     Scan.enumeratePointers(object, sanityEnum);
   }
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

  public final TraceLocal getCurrentTrace() {
    return trace;
  }

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
     if (Space.isInSpace(GenRC.NS, object))
       trace.traceObjectLocation(objLoc);
     else if (GenRC.isRCObject(object))
       RefCountSpace.incRC(object);
   }
 }

}

