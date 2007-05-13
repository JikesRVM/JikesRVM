/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2006
 */
package org.mmtk.plan.refcount.fullheap;

import org.mmtk.plan.refcount.RCBase;
import org.mmtk.plan.refcount.RCBaseMutator;
import org.mmtk.plan.refcount.RCHeader;
import org.mmtk.policy.Space;

import org.mmtk.utility.Constants;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements <i>per-mutator thread</i> behavior 
 * and state for the <i>MS</i> plan, which implements a full-heap
 * mark-sweep collector.<p>
 * 
 * Specifically, this class defines <i>MS</i> mutator-time allocation
 * and per-mutator thread collection semantics (flushing and restoring
 * per-mutator allocator state).<p>
 * 
 * @see org.mmtk.plan.markcompact.MC for an overview of the mark-compact algorithm.<p>
 * 
 * FIXME The SegregatedFreeList class (and its decendents such as
 * MarkSweepLocal) does not properly separate mutator and collector
 * behaviors, so the ms field below should really not exist in
 * this class as there is no collection-time allocation in this
 * collector.
 * 
 * @see RC
 * @see RCCollector
 * @see org.mmtk.plan.StopTheWorldMutator
 * @see org.mmtk.plan.MutatorContext
 * @see org.mmtk.plan.SimplePhase#delegatePhase
 */
@Uninterruptible public abstract class RCMutator extends RCBaseMutator implements Constants {
  /****************************************************************************
   * 
   * Mutator-time allocation
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
   * @param site Allocation site.
   * @return The low address of the allocated memory.
   */
  @Inline
  public Address alloc(int bytes, int align, int offset, int allocator, int site) { 
    if (allocator == RC.ALLOC_DEFAULT) {
      // The default allocator for full heap RC is ALLOC_RC
      allocator = RC.ALLOC_RC;
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
    if (allocator == RC.ALLOC_DEFAULT) {
      // The default allocator for full heap RC is ALLOC_RC
      allocator = RC.ALLOC_RC;
    }
    super.postAlloc(ref, typeRef, bytes, allocator);
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
  @Inline
  public final void writeBarrier(ObjectReference src, Address slot,
                                 ObjectReference tgt, Offset metaDataA,
                                 int metaDataB, int mode) { 
    if (VM.VERIFY_ASSERTIONS) {
      // TODO VM.assertions._assert(!Plan.gcInProgress());
    }
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
  @Inline
  private void writeBarrierInternal(ObjectReference src, Address slot,
                                          ObjectReference tgt, Offset metaDataA,
                                          int metaDataB, int mode) { 
    if (RC.GATHER_WRITE_BARRIER_STATS) RC.wbFast.inc();
    if (RC.WITH_COALESCING_RC) {
      if (RCHeader.logRequired(src)) {
        coalescingWriteBarrierSlow(src);
      }
      VM.barriers.performWriteInBarrier(src,slot,tgt,metaDataA,metaDataB,mode);
    } else {
      ObjectReference old = VM.barriers.
      performWriteInBarrierAtomic(src,slot,tgt,metaDataA,metaDataB,mode);
      
      if (Space.isInSpace(RCBase.VM_SPACE, src)) return;
      if (RC.isRCObject(old)) decBuffer.pushOOL(old);
      if (RC.isRCObject(tgt)) RCHeader.incRCOOL(tgt);
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
  @NoInline
  private void writeBarrierInternalOOL(ObjectReference src, Address slot,
                                             ObjectReference tgt,
                                             Offset metaDataA, int metaDataB,
                                             int mode) { 
    if (RC.GATHER_WRITE_BARRIER_STATS) RC.wbFast.inc();
    if (RC.WITH_COALESCING_RC) {
      if (RCHeader.logRequired(src)) {
        coalescingWriteBarrierSlow(src);
      }
      VM.barriers.performWriteInBarrier(src,slot,tgt, metaDataA, metaDataB, mode);
    } else {
      ObjectReference old = VM.barriers.
      performWriteInBarrierAtomic(src,slot,tgt,metaDataA,metaDataB,mode);
      
      if (Space.isInSpace(RCBase.VM_SPACE, src)) return;
      if (RC.isRCObject(old)) decBuffer.push(old);
      if (RC.isRCObject(tgt)) RCHeader.incRC(tgt);
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
  @Inline
  public final boolean writeBarrier(ObjectReference src, Offset srcOffset,
                                    ObjectReference dst, Offset dstOffset, int bytes) { 
    if (VM.VERIFY_ASSERTIONS) {
      // TODO VM.assertions._assert(!Plan.gcInProgress());
    }
    if (RC.GATHER_WRITE_BARRIER_STATS) RC.wbFast.inc();
    if (RC.WITH_COALESCING_RC) {
      if (RCHeader.logRequired(dst))
        coalescingWriteBarrierSlow(dst);
      return false;
    } else {
      if (Space.isInSpace(RCBase.VM_SPACE, dst)) return false;
      Address s = src.toAddress().plus(srcOffset);
      Address d = dst.toAddress().plus(dstOffset);
      while (bytes > 0) {
        ObjectReference tgt = s.loadObjectReference();
        ObjectReference old;
        do {
          old = d.prepareObjectReference();
        } while (!d.attempt(old, tgt));
        
        if (RC.isRCObject(old)) decBuffer.push(old);
        if (RC.isRCObject(tgt)) RCHeader.incRC(tgt);
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
  @NoInline
  private void coalescingWriteBarrierSlow(ObjectReference srcObj) {
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(RC.WITH_COALESCING_RC);
      VM.assertions._assert(RCBase.isRCObject(srcObj));
    }
    if (RC.GATHER_WRITE_BARRIER_STATS) RC.wbSlow.inc();
    if (RCHeader.attemptToLog(srcObj)) {
      modBuffer.push(srcObj);
      decBuffer.processChildren(srcObj);
      RCHeader.makeLogged(srcObj);
    }
  }
}
