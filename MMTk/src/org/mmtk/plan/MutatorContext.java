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
package org.mmtk.plan;

import org.mmtk.policy.MarkSweepLocal;
import org.mmtk.policy.Space;
import org.mmtk.policy.ImmortalLocal;
import org.mmtk.policy.LargeObjectLocal;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.alloc.BumpPointer;
import org.mmtk.utility.Constants;
import org.mmtk.utility.Log;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class (and its sub-classes) implement <i>per-mutator thread</i>
 * behavior.  We assume <i>N</i> collector threads and <i>M</i>
 * mutator threads, where <i>N</i> is often equal to the number of
 * available processors, P (for P-way parallelism at GC-time), and
 * <i>M</i> may simply be the number of mutator (application) threads.
 * Both <i>N</i> and <i>M</i> are determined by the VM, not MMTk.  In
 * the case where a VM uses posix threads (pthreads) for each mutator
 * ("1:1" threading), <i>M</i> will typically be equal to the number of
 * mutator threads.  When a uses "green threads" or a hybrid threading
 * scheme (such as Jikes RVM), <i>M</i> will typically be equal to the
 * level of <i>true</i> parallelism (ie the number of underlying
 * kernel threads).<p>
 *
 * MMTk assumes that the VM instantiates instances of MutatorContext
 * in thread local storage (TLS) for each thread participating in
 * collection.  Accesses to this state are therefore assumed to be
 * low-cost during mutator time.<p>
 *
 * This class (and its children) is therefore used for unsynchronized
 * per-mutator operations such as <i>allocation</i> and <i>write barriers</i>.
 * The semantics and necessary state for these operations are therefore
 * specified in the GC-specific subclasses of this class.
 *
 * MMTk explicitly separates thread-local (this class) and global
 * operations (@see Plan), so that syncrhonization is localized
 * and explicit, and thus hopefully minimized (@see Plan). Gloabl (Plan)
 * and per-thread (this class) state are also explicitly separated.
 * Operations in this class (and its children) are therefore strictly
 * local to each mutator thread, and synchronized operations always
 * happen via access to explicitly global classes such as Plan and its
 * children.  Therefore only <i>"fast path"</i> (unsynchronized)
 * allocation and barrier semantics are defined in MutatorContext and
 * its subclasses.  These call out to <i>"slow path"</i> (synchronize(d)
 * methods which have global state and are globally synchronized.  For
 * example, an allocation fast path may bump a pointer without any
 * syncrhonization (the "fast path") until a limit is reached, at which
 * point the "slow path" is called, and more memory is aquired from a
 * global resource.<p>
 *
 * As the super-class of all per-mutator contexts, this class implements
 * basic per-mutator behavior common to all MMTk collectors, including
 * support for immortal and large object space allocation, as well as
 * empty stubs for write barriers (to be overridden by sub-classes as
 * needed).
 *
 * @see CollectorContext
 * @see org.mmtk.vm.ActivePlan
 * @see Plan
 */
@Uninterruptible
public abstract class MutatorContext implements Constants {

  /****************************************************************************
   * Initialization
   */


  /**
   * Notify that the mutator context is registered and ready to execute. From
   * this point it will be included in iterations over mutators.
   *
   * @param id The id of this mutator context.
   */
  public void initMutator(int id) {
    this.id = id;
  }

  /**
   * The mutator is about to be cleaned up, make sure all local data is returned.
   */
  public void deinitMutator() {
    flush();
  }

  /****************************************************************************
   * Instance fields
   */

  /** Unique mutator identifier */
  private int id;

  /** Used for printing log information in a thread safe manner */
  protected final Log log = new Log();

  /** Per-mutator allocator into the immortal space */
  protected final BumpPointer immortal = new ImmortalLocal(Plan.immortalSpace);

  /** Per-mutator allocator into the large object space */
  protected final LargeObjectLocal los = new LargeObjectLocal(Plan.loSpace);

  /** Per-mutator allocator into the small code space */
  private final MarkSweepLocal smcode = Plan.USE_CODE_SPACE ? new MarkSweepLocal(Plan.smallCodeSpace) : null;

  /** Per-mutator allocator into the large code space */
  private final LargeObjectLocal lgcode = Plan.USE_CODE_SPACE ? new LargeObjectLocal(Plan.largeCodeSpace) : null;

  /** Per-mutator allocator into the non moving space */
  private final MarkSweepLocal nonmove = new MarkSweepLocal(Plan.nonMovingSpace);


  /****************************************************************************
   *
   * Collection.
   */

  /**
   * Perform a per-mutator collection phase.
   *
   * @param phaseId The unique phase identifier
   * @param primary Should this thread be used to execute any single-threaded
   * local operations?
   */
  public abstract void collectionPhase(short phaseId, boolean primary);

  /****************************************************************************
   *
   * Allocation.
   */

  /**
   * Run-time check of the allocator to use for a given allocation
   *
   * At the moment this method assumes that allocators will use the simple
   * (worst) method of aligning to determine if the object is a large object
   * to ensure that no objects are larger than other allocators can handle.
   *
   * @param bytes The number of bytes to be allocated
   * @param align The requested alignment.
   * @param allocator The allocator statically assigned to this allocation
   * @return The allocator dynamically assigned to this allocation
   */
  @Inline
  public int checkAllocator(int bytes, int align, int allocator) {
    int maxBytes = Allocator.getMaximumAlignedSize(bytes, align);
    if (allocator == Plan.ALLOC_DEFAULT) {
      return (maxBytes > Plan.MAX_NON_LOS_DEFAULT_ALLOC_BYTES) ? Plan.ALLOC_LOS : allocator;
    }

    if (Plan.USE_CODE_SPACE && allocator == Plan.ALLOC_CODE) {
      return (maxBytes > Plan.MAX_NON_LOS_NONMOVING_ALLOC_BYTES) ? Plan.ALLOC_LARGE_CODE : allocator;
    }

    if (allocator == Plan.ALLOC_NON_REFERENCE) {
      return (maxBytes > Plan.MAX_NON_LOS_DEFAULT_ALLOC_BYTES) ? Plan.ALLOC_LOS : Plan.ALLOC_DEFAULT;
    }

    if (allocator == Plan.ALLOC_NON_MOVING) {
      return (maxBytes > Plan.MAX_NON_LOS_NONMOVING_ALLOC_BYTES) ? Plan.ALLOC_LOS : allocator;
    }

    return allocator;
  }

  /**
   * Allocate memory for an object.
   *
   * @param bytes The number of bytes required for the object.
   * @param align Required alignment for the object.
   * @param offset Offset associated with the alignment.
   * @param allocator The allocator associated with this request.
   * @param site Allocation site
   * @return The low address of the allocated chunk.
   */
  @Inline
  public Address alloc(int bytes, int align, int offset, int allocator, int site) {
    switch (allocator) {
    case      Plan.ALLOC_LOS: return los.alloc(bytes, align, offset);
    case      Plan.ALLOC_IMMORTAL: return immortal.alloc(bytes, align, offset);
    case      Plan.ALLOC_CODE: return smcode.alloc(bytes, align, offset);
    case      Plan.ALLOC_LARGE_CODE: return lgcode.alloc(bytes, align, offset);
    case      Plan.ALLOC_NON_MOVING: return nonmove.alloc(bytes, align, offset);
    default:
      VM.assertions.fail("No such allocator");
      return Address.zero();
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
  @Inline
  public void postAlloc(ObjectReference ref, ObjectReference typeRef,
      int bytes, int allocator) {
    switch (allocator) {
    case           Plan.ALLOC_LOS: Plan.loSpace.initializeHeader(ref, true); return;
    case      Plan.ALLOC_IMMORTAL: Plan.immortalSpace.initializeHeader(ref);  return;
    case          Plan.ALLOC_CODE: Plan.smallCodeSpace.initializeHeader(ref, true); return;
    case    Plan.ALLOC_LARGE_CODE: Plan.largeCodeSpace.initializeHeader(ref, true); return;
    case    Plan.ALLOC_NON_MOVING: Plan.nonMovingSpace.initializeHeader(ref, true); return;
    default:
      VM.assertions.fail("No such allocator");
    }
  }

  /****************************************************************************
   *
   * Space - Allocator mapping.
   */

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
    if (space == Plan.immortalSpace)  return immortal;
    if (space == Plan.loSpace)        return los;
    if (space == Plan.nonMovingSpace) return nonmove;
    if (Plan.USE_CODE_SPACE && space == Plan.smallCodeSpace) return smcode;
    if (Plan.USE_CODE_SPACE && space == Plan.largeCodeSpace) return lgcode;

    // Invalid request has been made
    if (space == Plan.metaDataSpace) {
      VM.assertions.fail("MutatorContext.getAllocatorFromSpace given meta space");
    } else if (space != null) {
      VM.assertions.fail("MutatorContext.getAllocatorFromSpace given invalid space");
    } else {
      VM.assertions.fail("MutatorContext.getAllocatorFromSpace given null space");
    }

    return null;
  }

  /****************************************************************************
   *
   * Write and read barriers. By default do nothing, override if
   * appropriate.
   */

  /**
   * Read a reference type. In a concurrent collector this may
   * involve adding the referent to the marking queue.
   *
   * @param referent The referent being read.
   * @return The new referent.
   */
  @Inline
  public ObjectReference javaLangReferenceReadBarrier(ObjectReference referent) {
    // Either: read barriers are used and this is overridden, or
    // read barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return ObjectReference.nullReference();
  }

  /**
   * Write a boolean. Take appropriate write barrier actions.<p>
   *
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be
   * stored.
   * @param value The value of the new boolean
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   */
  public void booleanWrite(ObjectReference src, Address slot, boolean value, Word metaDataA, Word metaDataB, int mode) {
    // Either: write barriers are used and this is overridden, or
    // write barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
  }

  /**
   * Read a boolean. Take appropriate read barrier action, and
   * return the value that was read.<p> This is a <b>substituting<b>
   * barrier.  The call to this barrier takes the place of a load.<p>
   *
   * @param src The object reference holding the field being read.
   * @param slot The address of the slot being read.
   * @param metaDataA A value that assists the host VM in creating a load
   * @param metaDataB A value that assists the host VM in creating a load
   * @param mode The context in which the load occurred
   * @return The boolean that was read.
   */
  @Inline
  public boolean booleanRead(ObjectReference src, Address slot, Word metaDataA, Word metaDataB, int mode) {
    // Either: read barriers are used and this is overridden, or
    // read barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return false;
  }

  /**
   * A number of booleans are about to be copied from object
   * <code>src</code> to object <code>dst</code> (as in an array
   * copy).  Thus, <code>dst</code> is the mutated object.  Take
   * appropriate write barrier actions.<p>
   *
   * @param src The source array
   * @param srcOffset The starting source offset
   * @param dst The destination array
   * @param dstOffset The starting destination offset
   * @param bytes The number of bytes to be copied
   * @return True if the update was performed by the barrier, false if
   * left to the caller (always false in this case).
   */
  public boolean booleanBulkCopy(ObjectReference src, Offset srcOffset, ObjectReference dst, Offset dstOffset, int bytes) {
    // Either: bulk copy is supported and this is overridden, or
    // write barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return false;
  }

  /**
   * Write a byte. Take appropriate write barrier actions.<p>
   *
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be
   * stored.
   * @param value The value of the new byte
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   */
  public void byteWrite(ObjectReference src, Address slot, byte value, Word metaDataA, Word metaDataB, int mode) {
    // Either: write barriers are used and this is overridden, or
    // write barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
  }

  /**
   * Read a byte. Take appropriate read barrier action, and
   * return the value that was read.<p> This is a <b>substituting<b>
   * barrier.  The call to this barrier takes the place of a load.<p>
   *
   * @param src The object reference holding the field being read.
   * @param slot The address of the slot being read.
   * @param metaDataA A value that assists the host VM in creating a load
   * @param metaDataB A value that assists the host VM in creating a load
   * @param mode The context in which the load occurred
   * @return The byte that was read.
   */
  @Inline
  public byte byteRead(ObjectReference src, Address slot, Word metaDataA, Word metaDataB, int mode) {
    // Either: read barriers are used and this is overridden, or
    // read barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return 0;
  }

  /**
   * A number of bytes are about to be copied from object
   * <code>src</code> to object <code>dst</code> (as in an array
   * copy).  Thus, <code>dst</code> is the mutated object.  Take
   * appropriate write barrier actions.<p>
   *
   * @param src The source array
   * @param srcOffset The starting source offset
   * @param dst The destination array
   * @param dstOffset The starting destination offset
   * @param bytes The number of bytes to be copied
   * @return True if the update was performed by the barrier, false if
   * left to the caller (always false in this case).
   */
  public boolean byteBulkCopy(ObjectReference src, Offset srcOffset, ObjectReference dst, Offset dstOffset, int bytes) {
    // Either: bulk copy is supported and this is overridden, or
    // write barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return false;
  }

  /**
   * Write a char. Take appropriate write barrier actions.<p>
   *
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be
   * stored.
   * @param value The value of the new char
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   */
  public void charWrite(ObjectReference src, Address slot, char value, Word metaDataA, Word metaDataB, int mode) {
    // Either: write barriers are used and this is overridden, or
    // write barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
  }

  /**
   * Read a char. Take appropriate read barrier action, and
   * return the value that was read.<p> This is a <b>substituting<b>
   * barrier.  The call to this barrier takes the place of a load.<p>
   *
   * @param src The object reference holding the field being read.
   * @param slot The address of the slot being read.
   * @param metaDataA A value that assists the host VM in creating a load
   * @param metaDataB A value that assists the host VM in creating a load
   * @param mode The context in which the load occurred
   * @return The char that was read.
   */
  @Inline
  public char charRead(ObjectReference src, Address slot, Word metaDataA, Word metaDataB, int mode) {
    // Either: read barriers are used and this is overridden, or
    // read barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return 0;
  }

  /**
   * A number of chars are about to be copied from object
   * <code>src</code> to object <code>dst</code> (as in an array
   * copy).  Thus, <code>dst</code> is the mutated object.  Take
   * appropriate write barrier actions.<p>
   *
   * @param src The source array
   * @param srcOffset The starting source offset
   * @param dst The destination array
   * @param dstOffset The starting destination offset
   * @param bytes The number of bytes to be copied
   * @return True if the update was performed by the barrier, false if
   * left to the caller (always false in this case).
   */
  public boolean charBulkCopy(ObjectReference src, Offset srcOffset, ObjectReference dst, Offset dstOffset, int bytes) {
    // Either: bulk copy is supported and this is overridden, or
    // write barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return false;
  }

  /**
   * Write a short. Take appropriate write barrier actions.<p>
   *
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be
   * stored.
   * @param value The value of the new short
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   */
  public void shortWrite(ObjectReference src, Address slot, short value, Word metaDataA, Word metaDataB, int mode) {
    // Either: write barriers are used and this is overridden, or
    // write barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
  }

  /**
   * Read a short. Take appropriate read barrier action, and
   * return the value that was read.<p> This is a <b>substituting<b>
   * barrier.  The call to this barrier takes the place of a load.<p>
   *
   * @param src The object reference holding the field being read.
   * @param slot The address of the slot being read.
   * @param metaDataA A value that assists the host VM in creating a load
   * @param metaDataB A value that assists the host VM in creating a load
   * @param mode The context in which the load occurred
   * @return The short that was read.
   */
  @Inline
  public short shortRead(ObjectReference src, Address slot, Word metaDataA, Word metaDataB, int mode) {
    // Either: read barriers are used and this is overridden, or
    // read barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return 0;
  }

  /**
   * A number of shorts are about to be copied from object
   * <code>src</code> to object <code>dst</code> (as in an array
   * copy).  Thus, <code>dst</code> is the mutated object.  Take
   * appropriate write barrier actions.<p>
   *
   * @param src The source array
   * @param srcOffset The starting source offset
   * @param dst The destination array
   * @param dstOffset The starting destination offset
   * @param bytes The number of bytes to be copied
   * @return True if the update was performed by the barrier, false if
   * left to the caller (always false in this case).
   */
  public boolean shortBulkCopy(ObjectReference src, Offset srcOffset, ObjectReference dst, Offset dstOffset, int bytes) {
    // Either: bulk copy is supported and this is overridden, or
    // write barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return false;
  }


  /**
   * Write a int. Take appropriate write barrier actions.<p>
   *
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be
   * stored.
   * @param value The value of the new int
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   */
  public void intWrite(ObjectReference src, Address slot, int value, Word metaDataA, Word metaDataB, int mode) {
    // Either: write barriers are used and this is overridden, or
    // write barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
  }

  /**
   * Read a int. Take appropriate read barrier action, and
   * return the value that was read.<p> This is a <b>substituting<b>
   * barrier.  The call to this barrier takes the place of a load.<p>
   *
   * @param src The object reference holding the field being read.
   * @param slot The address of the slot being read.
   * @param metaDataA A value that assists the host VM in creating a load
   * @param metaDataB A value that assists the host VM in creating a load
   * @param mode The context in which the load occurred
   * @return The int that was read.
   */
  @Inline
  public int intRead(ObjectReference src, Address slot, Word metaDataA, Word metaDataB, int mode) {
    // Either: read barriers are used and this is overridden, or
    // read barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return 0;
  }

  /**
   * A number of ints are about to be copied from object
   * <code>src</code> to object <code>dst</code> (as in an array
   * copy).  Thus, <code>dst</code> is the mutated object.  Take
   * appropriate write barrier actions.<p>
   *
   * @param src The source array
   * @param srcOffset The starting source offset
   * @param dst The destination array
   * @param dstOffset The starting destination offset
   * @param bytes The number of bytes to be copied
   * @return True if the update was performed by the barrier, false if
   * left to the caller (always false in this case).
   */
  public boolean intBulkCopy(ObjectReference src, Offset srcOffset, ObjectReference dst, Offset dstOffset, int bytes) {
    // Either: bulk copy is supported and this is overridden, or
    // write barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return false;
  }

  /**
   * Attempt to atomically exchange the value in the given slot
   * with the passed replacement value.
   *
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param src The object into which the value will be stored
   * @param slot The address into which the value will be
   * stored.
   * @param old The old int to be swapped out
   * @param value The new int
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   * @return True if the swap was successful.
   */
  public boolean intTryCompareAndSwap(ObjectReference src, Address slot, int old, int value, Word metaDataA, Word metaDataB, int mode) {
    // Either: write barriers are used and this is overridden, or
    // write barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return false;
  }

  /**
   * Write a long. Take appropriate write barrier actions.<p>
   *
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be
   * stored.
   * @param value The value of the new long
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   */
  public void longWrite(ObjectReference src, Address slot, long value, Word metaDataA, Word metaDataB, int mode) {
    // Either: write barriers are used and this is overridden, or
    // write barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
  }

  /**
   * Read a long. Take appropriate read barrier action, and
   * return the value that was read.<p> This is a <b>substituting<b>
   * barrier.  The call to this barrier takes the place of a load.<p>
   *
   * @param src The object reference holding the field being read.
   * @param slot The address of the slot being read.
   * @param metaDataA A value that assists the host VM in creating a load
   * @param metaDataB A value that assists the host VM in creating a load
   * @param mode The context in which the load occurred
   * @return The long that was read.
   */
  @Inline
  public long longRead(ObjectReference src, Address slot, Word metaDataA, Word metaDataB, int mode) {
    // Either: read barriers are used and this is overridden, or
    // read barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return 0;
  }

  /**
   * A number of longs are about to be copied from object
   * <code>src</code> to object <code>dst</code> (as in an array
   * copy).  Thus, <code>dst</code> is the mutated object.  Take
   * appropriate write barrier actions.<p>
   *
   * @param src The source array
   * @param srcOffset The starting source offset
   * @param dst The destination array
   * @param dstOffset The starting destination offset
   * @param bytes The number of bytes to be copied
   * @return True if the update was performed by the barrier, false if
   * left to the caller (always false in this case).
   */
  public boolean longBulkCopy(ObjectReference src, Offset srcOffset, ObjectReference dst, Offset dstOffset, int bytes) {
    // Either: bulk copy is supported and this is overridden, or
    // write barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return false;
  }

  /**
   * Attempt to atomically exchange the value in the given slot
   * with the passed replacement value.
   *
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param src The object into which the value will be stored
   * @param slot The address into which the value will be
   * stored.
   * @param old The old long to be swapped out
   * @param value The new long
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   * @return True if the swap was successful.
   */
  public boolean longTryCompareAndSwap(ObjectReference src, Address slot, long old, long value, Word metaDataA, Word metaDataB, int mode) {
    // Either: write barriers are used and this is overridden, or
    // write barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return false;
  }

  /**
   * Write a float. Take appropriate write barrier actions.<p>
   *
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be
   * stored.
   * @param value The value of the new float
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   */
  public void floatWrite(ObjectReference src, Address slot, float value, Word metaDataA, Word metaDataB, int mode) {
    // Either: write barriers are used and this is overridden, or
    // write barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
  }

  /**
   * Read a float. Take appropriate read barrier action, and
   * return the value that was read.<p> This is a <b>substituting<b>
   * barrier.  The call to this barrier takes the place of a load.<p>
   *
   * @param src The object reference holding the field being read.
   * @param slot The address of the slot being read.
   * @param metaDataA A value that assists the host VM in creating a load
   * @param metaDataB A value that assists the host VM in creating a load
   * @param mode The context in which the load occurred
   * @return The float that was read.
   */
  @Inline
  public float floatRead(ObjectReference src, Address slot, Word metaDataA, Word metaDataB, int mode) {
    // Either: read barriers are used and this is overridden, or
    // read barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return 0;
  }

  /**
   * A number of floats are about to be copied from object
   * <code>src</code> to object <code>dst</code> (as in an array
   * copy).  Thus, <code>dst</code> is the mutated object.  Take
   * appropriate write barrier actions.<p>
   *
   * @param src The source array
   * @param srcOffset The starting source offset
   * @param dst The destination array
   * @param dstOffset The starting destination offset
   * @param bytes The number of bytes to be copied
   * @return True if the update was performed by the barrier, false if
   * left to the caller (always false in this case).
   */
  public boolean floatBulkCopy(ObjectReference src, Offset srcOffset, ObjectReference dst, Offset dstOffset, int bytes) {
    // Either: bulk copy is supported and this is overridden, or
    // write barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return false;
  }

  /**
   * Write a double. Take appropriate write barrier actions.<p>
   *
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be
   * stored.
   * @param value The value of the new double
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   */
  public void doubleWrite(ObjectReference src, Address slot, double value, Word metaDataA, Word metaDataB, int mode) {
    // Either: write barriers are used and this is overridden, or
    // write barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
  }

  /**
   * Read a double. Take appropriate read barrier action, and
   * return the value that was read.<p> This is a <b>substituting<b>
   * barrier.  The call to this barrier takes the place of a load.<p>
   *
   * @param src The object reference holding the field being read.
   * @param slot The address of the slot being read.
   * @param metaDataA A value that assists the host VM in creating a load
   * @param metaDataB A value that assists the host VM in creating a load
   * @param mode The context in which the load occurred
   * @return The double that was read.
   */
  @Inline
  public double doubleRead(ObjectReference src, Address slot, Word metaDataA, Word metaDataB, int mode) {
    // Either: read barriers are used and this is overridden, or
    // read barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return 0;
  }

  /**
   * A number of doubles are about to be copied from object
   * <code>src</code> to object <code>dst</code> (as in an array
   * copy).  Thus, <code>dst</code> is the mutated object.  Take
   * appropriate write barrier actions.<p>
   *
   * @param src The source array
   * @param srcOffset The starting source offset
   * @param dst The destination array
   * @param dstOffset The starting destination offset
   * @param bytes The number of bytes to be copied
   * @return True if the update was performed by the barrier, false if
   * left to the caller (always false in this case).
   */
  public boolean doubleBulkCopy(ObjectReference src, Offset srcOffset, ObjectReference dst, Offset dstOffset, int bytes) {
    // Either: bulk copy is supported and this is overridden, or
    // write barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return false;
  }

  /**
   * Write a Word. Take appropriate write barrier actions.<p>
   *
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be
   * stored.
   * @param value The value of the new Word
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   */
  public void wordWrite(ObjectReference src, Address slot, Word value, Word metaDataA, Word metaDataB, int mode) {
    // Either: write barriers are used and this is overridden, or
    // write barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
  }

  /**
   * Read a Word. Take appropriate read barrier action, and
   * return the value that was read.<p> This is a <b>substituting<b>
   * barrier.  The call to this barrier takes the place of a load.<p>
   *
   * @param src The object reference holding the field being read.
   * @param slot The address of the slot being read.
   * @param metaDataA A value that assists the host VM in creating a load
   * @param metaDataB A value that assists the host VM in creating a load
   * @param mode The context in which the load occurred
   * @return The Word that was read.
   */
  @Inline
  public Word wordRead(ObjectReference src, Address slot, Word metaDataA, Word metaDataB, int mode) {
    // Either: read barriers are used and this is overridden, or
    // read barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return Word.zero();
  }

  /**
   * A number of Words are about to be copied from object
   * <code>src</code> to object <code>dst</code> (as in an array
   * copy).  Thus, <code>dst</code> is the mutated object.  Take
   * appropriate write barrier actions.<p>
   *
   * @param src The source array
   * @param srcOffset The starting source offset
   * @param dst The destination array
   * @param dstOffset The starting destination offset
   * @param bytes The number of bytes to be copied
   * @return True if the update was performed by the barrier, false if
   * left to the caller (always false in this case).
   */
  public boolean wordBulkCopy(ObjectReference src, Offset srcOffset, ObjectReference dst, Offset dstOffset, int bytes) {
    // Either: bulk copy is supported and this is overridden, or
    // write barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return false;
  }

  /**
   * Attempt to atomically exchange the value in the given slot
   * with the passed replacement value.
   *
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be
   * stored.
   * @param old The old Word to be swapped out
   * @param value The new Word
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   * @return True if the swap was successful.
   */
  public boolean wordTryCompareAndSwap(ObjectReference src, Address slot, Word old, Word value, Word metaDataA, Word metaDataB, int mode) {
    // Either: write barriers are used and this is overridden, or
    // write barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return false;
  }

  /**
   * Write an Address. Take appropriate write barrier actions.<p>
   *
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param src The object into which the Word will be stored
   * @param slot The address into which the Word will be
   * stored.
   * @param value The value of the new Address
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   */
  public void addressWrite(ObjectReference src, Address slot, Address value, Word metaDataA, Word metaDataB, int mode) {
    // Either: write barriers are used and this is overridden, or
    // write barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
  }

  /**
   * Read an Address. Take appropriate read barrier action, and
   * return the value that was read.<p> This is a <b>substituting<b>
   * barrier.  The call to this barrier takes the place of a load.<p>
   *
   * @param src The object reference holding the field being read.
   * @param slot The address of the slot being read.
   * @param metaDataA A value that assists the host VM in creating a load
   * @param metaDataB A value that assists the host VM in creating a load
   * @param mode The context in which the load occurred
   * @return The Address that was read.
   */
  @Inline
  public Address addressRead(ObjectReference src, Address slot, Word metaDataA, Word metaDataB, int mode) {
    // Either: read barriers are used and this is overridden, or
    // read barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return Address.zero();
  }

  /**
   * A number of Addresse's are about to be copied from object
   * <code>src</code> to object <code>dst</code> (as in an array
   * copy).  Thus, <code>dst</code> is the mutated object.  Take
   * appropriate write barrier actions.<p>
   *
   * @param src The source array
   * @param srcOffset The starting source offset
   * @param dst The destination array
   * @param dstOffset The starting destination offset
   * @param bytes The number of bytes to be copied
   * @return True if the update was performed by the barrier, false if
   * left to the caller (always false in this case).
   */
  public boolean addressBulkCopy(ObjectReference src, Offset srcOffset, ObjectReference dst, Offset dstOffset, int bytes) {
    // Either: bulk copy is supported and this is overridden, or
    // write barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return false;
  }

  /**
   * Attempt to atomically exchange the value in the given slot
   * with the passed replacement value.
   *
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param src The object into which the Address will be stored
   * @param slot The address into which the Address will be
   * stored.
   * @param old The old Address to be swapped out
   * @param value The new Address
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   * @return True if the swap was successful.
   */
  public boolean addressTryCompareAndSwap(ObjectReference src, Address slot, Address old, Address value, Word metaDataA, Word metaDataB, int mode) {
    // Either: write barriers are used and this is overridden, or
    // write barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return false;
  }

  /**
   * Write an Extent. Take appropriate write barrier actions.<p>
   *
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be
   * stored.
   * @param value The value of the new Extent
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   */
  public void extentWrite(ObjectReference src, Address slot, Extent value, Word metaDataA, Word metaDataB, int mode) {
    // Either: write barriers are used and this is overridden, or
    // write barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
  }

  /**
   * Read an Extent. Take appropriate read barrier action, and
   * return the value that was read.<p> This is a <b>substituting<b>
   * barrier.  The call to this barrier takes the place of a load.<p>
   *
   * @param src The object reference holding the field being read.
   * @param slot The address of the slot being read.
   * @param metaDataA A value that assists the host VM in creating a load
   * @param metaDataB A value that assists the host VM in creating a load
   * @param mode The context in which the load occurred
   * @return The Extent that was read.
   */
  @Inline
  public Extent extentRead(ObjectReference src, Address slot, Word metaDataA, Word metaDataB, int mode) {
    // Either: read barriers are used and this is overridden, or
    // read barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return Extent.zero();
  }

  /**
   * A number of Extents are about to be copied from object
   * <code>src</code> to object <code>dst</code> (as in an array
   * copy).  Thus, <code>dst</code> is the mutated object.  Take
   * appropriate write barrier actions.<p>
   *
   * @param src The source array
   * @param srcOffset The starting source offset
   * @param dst The destination array
   * @param dstOffset The starting destination offset
   * @param bytes The number of bytes to be copied
   * @return True if the update was performed by the barrier, false if
   * left to the caller (always false in this case).
   */
  public boolean extentBulkCopy(ObjectReference src, Offset srcOffset, ObjectReference dst, Offset dstOffset, int bytes) {
    // Either: bulk copy is supported and this is overridden, or
    // write barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return false;
  }

  /**
   * Write an Offset. Take appropriate write barrier actions.<p>
   *
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be
   * stored.
   * @param value The value of the new Offset
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   */
  public void offsetWrite(ObjectReference src, Address slot, Offset value, Word metaDataA, Word metaDataB, int mode) {
    // Either: write barriers are used and this is overridden, or
    // write barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
  }

  /**
   * Read an Offset. Take appropriate read barrier action, and
   * return the value that was read.<p> This is a <b>substituting<b>
   * barrier.  The call to this barrier takes the place of a load.<p>
   *
   * @param src The object reference holding the field being read.
   * @param slot The address of the slot being read.
   * @param metaDataA A value that assists the host VM in creating a load
   * @param metaDataB A value that assists the host VM in creating a load
   * @param mode The context in which the load occurred
   * @return The Offset that was read.
   */
  @Inline
  public Offset offsetRead(ObjectReference src, Address slot, Word metaDataA, Word metaDataB, int mode) {
    // Either: read barriers are used and this is overridden, or
    // read barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return Offset.zero();
  }

  /**
   * A number of Offsets are about to be copied from object
   * <code>src</code> to object <code>dst</code> (as in an array
   * copy).  Thus, <code>dst</code> is the mutated object.  Take
   * appropriate write barrier actions.<p>
   *
   * @param src The source array
   * @param srcOffset The starting source offset
   * @param dst The destination array
   * @param dstOffset The starting destination offset
   * @param bytes The number of bytes to be copied
   * @return True if the update was performed by the barrier, false if
   * left to the caller (always false in this case).
   */
  public boolean offsetBulkCopy(ObjectReference src, Offset srcOffset, ObjectReference dst, Offset dstOffset, int bytes) {
    // Either: bulk copy is supported and this is overridden, or
    // write barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return false;
  }

  /**
   * Write an object reference. Take appropriate write barrier actions.<p>
   *
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be
   * stored.
   * @param value The value of the new reference
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   */
  public void objectReferenceWrite(ObjectReference src, Address slot, ObjectReference value, Word metaDataA, Word metaDataB, int mode) {
    // Either: write barriers are used and this is overridden, or
    // write barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
  }

  /**
   * Read an object reference. Take appropriate read barrier action, and
   * return the value that was read.<p> This is a <b>substituting<b>
   * barrier.  The call to this barrier takes the place of a load.<p>
   *
   * @param src The object reference holding the field being read.
   * @param slot The address of the slot being read.
   * @param metaDataA A value that assists the host VM in creating a load
   * @param metaDataB A value that assists the host VM in creating a load
   * @param mode The context in which the load occurred
   * @return The reference that was read.
   */
  @Inline
  public ObjectReference objectReferenceRead(ObjectReference src, Address slot, Word metaDataA, Word metaDataB, int mode) {
    // Either: read barriers are used and this is overridden, or
    // read barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return ObjectReference.nullReference();
  }

  /**
   * A number of references are about to be copied from object
   * <code>src</code> to object <code>dst</code> (as in an array
   * copy).  Thus, <code>dst</code> is the mutated object.  Take
   * appropriate write barrier actions.<p>
   *
   * @param src The source array
   * @param srcOffset The starting source offset
   * @param dst The destination array
   * @param dstOffset The starting destination offset
   * @param bytes The number of bytes to be copied
   * @return True if the update was performed by the barrier, false if
   * left to the caller (always false in this case).
   */
  public boolean objectReferenceBulkCopy(ObjectReference src, Offset srcOffset, ObjectReference dst, Offset dstOffset, int bytes) {
    // Either: bulk copy is supported and this is overridden, or
    // write barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return false;
  }


  /**
   * A new reference is about to be created in a location that is not
   * a regular heap object.  Take appropriate write barrier actions.<p>
   *
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param slot The address into which the new reference will be
   * stored.
   * @param tgt The target of the new reference
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   */
  public void objectReferenceNonHeapWrite(Address slot, ObjectReference tgt, Word metaDataA, Word metaDataB) {
    // Either: write barriers are used and this is overridden, or
    // write barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
  }

  /**
   * Read an object reference. Take appropriate read barrier action, and
   * return the value that was read.<p> This is a <b>substituting<b>
   * barrier.  The call to this barrier takes the place of a load.<p>
   *
   * @param slot The address of the slot being read.
   * @param metaDataA A value that assists the host VM in creating a load
   * @param metaDataB A value that assists the host VM in creating a load
   * @return The reference that was read.
   */
  @Inline
  public ObjectReference objectReferenceNonHeapRead(Address slot, Word metaDataA, Word metaDataB) {
    // Either: read barriers are used and this is overridden, or
    // read barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return ObjectReference.nullReference();
  }

  /**
   * Attempt to atomically exchange the value in the given slot
   * with the passed replacement value. If a new reference is
   * created, we must then take appropriate write barrier actions.<p>
   *
   * <b>By default do nothing, override if appropriate.</b>
   *
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be
   * stored.
   * @param old The old reference to be swapped out
   * @param tgt The target of the new reference
   * @param metaDataA A value that assists the host VM in creating a store
   * @param metaDataB A value that assists the host VM in creating a store
   * @param mode The context in which the store occurred
   * @return True if the swap was successful.
   */
  public boolean objectReferenceTryCompareAndSwap(ObjectReference src, Address slot, ObjectReference old, ObjectReference tgt, Word metaDataA, Word metaDataB, int mode) {
    // Either: write barriers are used and this is overridden, or
    // write barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return false;
  }

  /**
   * Flush mutator context, in response to a requestMutatorFlush.
   * Also called by the default implementation of deinitMutator.
   */
  public void flush() {
    flushRememberedSets();
    smcode.flush();
    nonmove.flush();
  }

  /**
   * Flush per-mutator remembered sets into the global remset pool.
   */
  public void flushRememberedSets() {
    // Either: write barriers are used and this is overridden, or
    // write barriers are not used and this is a no-op
  }

  /**
   * Assert that the remsets have been flushed.  This is critical to
   * correctness.  We need to maintain the invariant that remset entries
   * do not accrue during GC.  If the host JVM generates barrier entires
   * it is its own responsibility to ensure that they are flushed before
   * returning to MMTk.
   */
  public void assertRemsetsFlushed() {
    // Either: write barriers are used and this is overridden, or
    // write barriers are not used and this is a no-op
  }

  /***********************************************************************
   *
   * Miscellaneous
   */

  /** @return the <code>Log</code> instance for this PlanLocal */
  public final Log getLog() {
    return log;
  }

  /** @return the unique identifier for this mutator context. */
  @Inline
  public int getId() { return id; }

}
