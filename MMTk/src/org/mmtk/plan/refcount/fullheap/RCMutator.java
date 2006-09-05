/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2006
 */

package org.mmtk.plan.refcount.fullheap;

import org.mmtk.plan.refcount.RCBaseMutator;
import org.mmtk.policy.RefCountSpace;
import org.mmtk.policy.RefCountLocal;
import org.mmtk.utility.Constants;
import org.mmtk.utility.scan.*;
import org.mmtk.utility.options.Options;
import org.mmtk.vm.VM;


import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements <i>per-mutator thread</i> behavior and
 * state for the <i>RC</i> plan, a simple full-heap reference
 * counting collector.<p>
 * 
 * Specifically, this class implements mutator-time allocation and
 * write barrier semantics.  Allocation and write barriers are all
 * performed with respect to thread local storage, to maximize locality
 * and minimze synchronization.<p>
 * 
 * See {@link RC} for a description of the full-heap reference counting
 * algorithm.<p>
 * 
 * FIXME Currently RC does not properly separate mutator and collector
 * behaviors, so most of the collection logic in RCMutator should really
 * be per-collector thread, not per-mutator thread.
 * 
 * @see RCBaseMutator
 * @see RC
 * @see RCCollector
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
public abstract class RCMutator extends RCBaseMutator implements Uninterruptible, Constants {
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
  public Address alloc(int bytes, int align, int offset, int allocator, int site)
      throws InlinePragma {
    switch (allocator) {
    case  RC.ALLOC_RC: return rc.alloc(bytes, align, offset, false);
    case RC.ALLOC_LOS: return los.alloc(bytes, align, offset, false);
    default:           return super.alloc(bytes,align,offset,allocator, site);
    }
  }

  /**
   * Perform post-allocation actions.  For many allocators none are
   * required.
   * 
   * @param object The newly allocated object
   * @param typeRef the type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   * @param allocator The allocator number to be used for this allocation
   */
  public void postAlloc(ObjectReference object, ObjectReference typeRef,
      int bytes, int allocator)
  throws NoInlinePragma {
    switch (allocator) {
    case RC.ALLOC_RC:
      RefCountLocal.unsyncLiveObject(object);
    case RC.ALLOC_LOS:
      if (RC.WITH_COALESCING_RC) modBuffer.push(object);
      decBuffer.push(object);
      if (RefCountSpace.RC_SANITY_CHECK) RefCountLocal.sanityAllocCount(object);
      RefCountSpace.initializeHeader(object, typeRef, true);
      return;
    case RC.ALLOC_IMMORTAL:
      if (RC.WITH_COALESCING_RC)
        modBuffer.push(object);
      else
        super.postAlloc(object, typeRef, bytes, allocator);
      return;
    default:
      super.postAlloc(object, typeRef, bytes, allocator);
      return;
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
   * non-nursery space.  This method is <b>inlined</b> by the
   * optimizing compiler, and the methods it calls are forced out of
   * line.
   *
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be
   * stored.
   * @param tgt The target of the new reference
   * @param metaDataA An int that assists the host VM in creating a store
   * @param metaDataB An int that assists the host VM in creating a store
   * @param mode The mode of the store (eg putfield, putstatic)
   */
  public final void writeBarrier(ObjectReference src, Address slot,
      ObjectReference tgt, Offset metaDataA,
      int metaDataB, int mode)
      throws InlinePragma {
    if (RC.INLINE_WRITE_BARRIER)
      writeBarrierInternal(src, slot, tgt, metaDataA, metaDataB, mode);
    else
      writeBarrierInternalOOL(src, slot, tgt, metaDataA, metaDataB, mode);
  }

  /**
   * A new reference is about to be created.  Perform appropriate
   * write barrier action.<p>
   *
   * In this case, we remember the address of the source of the
   * pointer if the new reference points into the nursery from
   * non-nursery space.  This method is <b>inlined</b> by the
   * optimizing compiler, and the methods it calls are forced out of
   * line.
   *
   * @param src The object being mutated.
   * @param slot The address of the word (slot) being mutated.
   * @param tgt The target of the new reference (about to be stored into src).
   * @param metaDataA An int that assists the host VM in creating a store
   * @param metaDataB An int that assists the host VM in creating a store
   * @param mode The mode of the store (eg putfield, putstatic)
   */
  private final void writeBarrierInternal(ObjectReference src, Address slot,
      ObjectReference tgt, Offset metaDataA,
      int metaDataB, int mode)
      throws InlinePragma {
    if (RC.GATHER_WRITE_BARRIER_STATS) RC.wbFast.inc();
    if (RC.WITH_COALESCING_RC) {
      if (RefCountSpace.logRequired(src)) {
        coalescingWriteBarrierSlow(src);
      }
      VM.barriers.performWriteInBarrier(src,slot,tgt,metaDataA,metaDataB,mode);
    } else {
      ObjectReference old = VM.barriers.
      performWriteInBarrierAtomic(src,slot,tgt,metaDataA,metaDataB,mode);
      if (RC.isRCObject(old)) decBuffer.pushOOL(old);
      if (RC.isRCObject(tgt)) RefCountSpace.incRCOOL(tgt);
    }
  }

  /**
   * An out of line version of the write barrier.  This method is
   * forced <b>out of line</b> by the optimizing compiler, and the
   * methods it calls are forced out of inline.
   *
   * @param src The object being mutated.
   * @param slot The address of the word (slot) being mutated.
   * @param tgt The target of the new reference (about to be stored into src).
   * @param metaDataA An int that assists the host VM in creating a store
   * @param metaDataB An int that assists the host VM in creating a store
   * @param mode The mode of the store (eg putfield, putstatic)
   */
  private final void writeBarrierInternalOOL(ObjectReference src, Address slot,
      ObjectReference tgt,
      Offset metaDataA, int metaDataB,
      int mode)
      throws NoInlinePragma {
    if (RC.GATHER_WRITE_BARRIER_STATS) RC.wbFast.inc();
    if (RC.WITH_COALESCING_RC) {
      if (RefCountSpace.logRequired(src)) {
        coalescingWriteBarrierSlow(src);
      }
      VM.barriers.performWriteInBarrier(src,slot,tgt, metaDataA, metaDataB, mode);
    } else {
      ObjectReference old = VM.barriers.
      performWriteInBarrierAtomic(src,slot,tgt,metaDataA,metaDataB,mode);
      if (RC.isRCObject(old)) decBuffer.push(old);
      if (RC.isRCObject(tgt)) RefCountSpace.incRC(tgt);
    }
  }

  /**
   * A number of references are about to be copied from object
   * <code>src</code> to object <code>dst</code> (as in an array
   * copy).  Thus, <code>dst</code> is the mutated object.  Take
   * appropriate write barrier actions.<p>
   *
   * In this case, we simply remember the mutated source object, or we
   * enumerate the copied pointers and perform appropriate actions on
   * each.
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
   * left to the caller (this depends on which style of barrier is
   * being used).
   */
  public boolean writeBarrier(ObjectReference src, Offset srcOffset,
      ObjectReference dst, Offset dstOffset, int bytes)
  throws InlinePragma {
    if (RC.GATHER_WRITE_BARRIER_STATS) RC.wbFast.inc();
    if (RC.WITH_COALESCING_RC) {
      if (RefCountSpace.logRequired(dst))
        coalescingWriteBarrierSlow(dst);
      return false;
    } else {
      Address s = src.toAddress().plus(srcOffset);
      Address d = dst.toAddress().plus(dstOffset);
      while (bytes > 0) {
        ObjectReference tgt = s.loadObjectReference();
        ObjectReference old;
        do {
          old = d.prepareObjectReference();
        } while (!d.attempt(old, tgt));
        if (RC.isRCObject(old)) decBuffer.push(old);
        if (RC.isRCObject(tgt)) RefCountSpace.incRC(tgt);
        s = s.plus(BYTES_IN_ADDRESS);
        d = d.plus(BYTES_IN_ADDRESS);
        bytes -= BYTES_IN_ADDRESS;
      }
      return true;
    }
  }

  /**
   * Slow path of the coalescing write barrier.
   * 
   * <p> Attempt to log the source object. If successful in racing for
   * the log bit, push an entry into the modified buffer and add a
   * decrement buffer entry for each referent object (in the RC space)
   * before setting the header bit to indicate that it has finished
   * logging (allowing others in the race to continue).
   * 
   * @param srcObj The object being mutated
   */
  private final void coalescingWriteBarrierSlow(ObjectReference srcObj)
      throws NoInlinePragma {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(RC.WITH_COALESCING_RC);
    if (RC.GATHER_WRITE_BARRIER_STATS) RC.wbSlow.inc();
    if (RefCountSpace.attemptToLog(srcObj)) {
      modBuffer.push(srcObj);
      Scan.enumeratePointers(srcObj, decEnum);
      RefCountSpace.makeLogged(srcObj);
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
      if (RC.WITH_COALESCING_RC) processModBufs();
      VM.memory.collectorPrepareVMSpace();
      return;
    }

    if (phaseId == RC.RELEASE_MUTATOR) {
      rc.release(this, primary);
      if (Options.verbose.getValue() > 2) rc.printStats();
      VM.memory.collectorReleaseVMSpace();
      return;
    }

    super.collectionPhase(phaseId, primary);
  }

  /****************************************************************************
   * 
   * Miscellaneous
   */

  /** Show the status of each of the allocators. */
  public final void show() {
    rc.show();
    los.show();
    immortal.show();
  }

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
   * A field of an object in the modified buffer is being enumerated
   * by ScanObject. If the field points to the RC space, increment the
   * count of the referent object.
   * 
   * @param objLoc The address of a reference field with an object
   * being enumerated.
   */
  public final void enumerateModifiedPointerLocation(Address objLoc)
      throws InlinePragma {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(RC.WITH_COALESCING_RC);
    ObjectReference object = objLoc.loadObjectReference();
    if (RC.isRCObject(object)) RefCountSpace.incRC(object);
  }

}
