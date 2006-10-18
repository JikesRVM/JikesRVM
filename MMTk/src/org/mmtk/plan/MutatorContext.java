/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2006
 */
package org.mmtk.plan;

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
 * @see SimplePhase#delegatePhase
 * @see CollectorContext
 * @see org.mmtk.vm.ActivePlan
 * @see Plan
 * 
 * $Id$
 * 
 * @author Perry Cheng
 * @author Steve Blackburn
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 */

public abstract class MutatorContext implements Uninterruptible, Constants {
  /****************************************************************************
   * Instance fields
   */

  /** Unique mutator identifier */
  protected int id = VM.activePlan.registerMutator(this);

  /** Used for printing log information in a thread safe manner */
  protected Log log = new Log();

  /** Per-mutator allocator into the immortal space */
  protected BumpPointer immortal = new ImmortalLocal(Plan.immortalSpace);

  /** Per-mutator allocator into the large object space */
  protected LargeObjectLocal los = new LargeObjectLocal(Plan.loSpace);

  /** Per-mutator allocator into the primitive large object space */
  protected LargeObjectLocal plos = new LargeObjectLocal(Plan.ploSpace);
  
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
  public abstract void collectionPhase(int phaseId, boolean primary);

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
   * @return The allocator dyncamically assigned to this allocation
   */
  public int checkAllocator(int bytes, int align, int allocator)
      throws InlinePragma {
    if (allocator == Plan.ALLOC_DEFAULT &&
        Allocator.getMaximumAlignedSize(bytes, align) > Plan.LOS_SIZE_THRESHOLD) 
      return Plan.ALLOC_LOS;
    else if (allocator == Plan.ALLOC_NON_REFERENCE) {
        if (Allocator.getMaximumAlignedSize(bytes, align) > Plan.LOS_SIZE_THRESHOLD)
          return Plan.ALLOC_PRIMITIVE_LOS;
    else
          return Plan.ALLOC_DEFAULT;
    } else
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
  public Address alloc(int bytes, int align, int offset, int allocator, int site)
      throws InlinePragma {
    switch (allocator) {
    case      Plan.ALLOC_LOS: return los.alloc(bytes, align, offset, false);
    case      Plan.ALLOC_PRIMITIVE_LOS: return plos.alloc(bytes, align, offset, false);
    case Plan.ALLOC_IMMORTAL: return immortal.alloc(bytes, align, offset, false);
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
  public void postAlloc(ObjectReference ref, ObjectReference typeRef,
      int bytes, int allocator) throws InlinePragma {
    switch (allocator) {
    case           Plan.ALLOC_LOS: Plan.loSpace.initializeHeader(ref, false); return;
    case Plan.ALLOC_PRIMITIVE_LOS: Plan.ploSpace.initializeHeader(ref, true); return;
    case      Plan.ALLOC_IMMORTAL: Plan.immortalSpace.initializeHeader(ref);  return;
    default:
      VM.assertions.fail("No such allocator");
    }
  }

  /****************************************************************************
   * 
   * Space - Allocator mapping. See description for getOwnAllocator that
   * describes why this is important.
   */

  /**
   * Given an allocator, <code>a</code>, determine the space into
   * which <code>a</code> is allocating and then return an allocator
   * (possibly <code>a</code>) associated with <i>this plan
   * instance</i> which is allocating into the same space as
   * <code>a</code>.<p>
   *
   * The need for the method is subtle.  The problem arises because
   * application threads may change their affinity with
   * processors/posix threads, and this may happen during a GC (at the
   * point at which the scheduler performs thread switching associated
   * with the GC). At the end of a GC, the thread that triggered the
   * GC may now be bound to a different processor and thus the
   * allocator instance on its stack may be no longer be valid
   * (i.e. it may pertain to a different plan instance).<p>
   *
   * This method allows the correct allocator instance to be
   * established and associated with the thread (see {@link
   * org.mmtk.utility.alloc.Allocator#allocSlow(int, int, int,
   * boolean) Allocator.allocSlow()}).
   * 
   * @see org.mmtk.utility.alloc.Allocator
   * @see org.mmtk.utility.alloc.Allocator#allocSlow(int, int, int, boolean)
   * 
   * @param a An allocator instance.
   * @return An allocator instance associated with <i>this plan
   * instance</i> that allocates into the same space as <code>a</code>
   * (this may in fact be <code>a</code>).
   */
  public final Allocator getOwnAllocator(Allocator a) {
    Space space = Plan.getSpaceFromAllocatorAnyLocal(a);
    if (space == null)
      VM.assertions.fail("PlanLocal.getOwnAllocator could not obtain space");
    return getAllocatorFromSpace(space);
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
    if (a == immortal) return Plan.immortalSpace;
    if (a == los)      return Plan.loSpace;
    if (a == plos)     return Plan.ploSpace;

    // a does not belong to this plan instance
    return null;
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
    if (space == Plan.immortalSpace) return immortal;
    if (space == Plan.loSpace)       return los;
    if (space == Plan.ploSpace)      return plos;

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
   * A new reference is about to be created. Take appropriate write
   * barrier actions.<p>
   * 
   * <b>By default do nothing, override if appropriate.</b>
   * 
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be
   * stored.
   * @param tgt The target of the new reference
   * @param metaDataA An int that assists the host VM in creating a store
   * @param metaDataB An int that assists the host VM in creating a store
   * @param mode The context in which the store occured
   */
  public void writeBarrier(ObjectReference src, Address slot,
      ObjectReference tgt, Offset metaDataA,
      int metaDataB, int mode) {
    // Either: write barriers are used and this is overridden, or
    // write barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
  }

  /**
   * A number of references are about to be copied from object
   * <code>src</code> to object <code>dst</code> (as in an array
   * copy).  Thus, <code>dst</code> is the mutated object.  Take
   * appropriate write barrier actions.<p>
   *
   * @param src The source of the values to be copied
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
  public boolean writeBarrier(ObjectReference src, Offset srcOffset,
      ObjectReference dst, Offset dstOffset,
      int bytes) {
    // Either: write barriers are used and this is overridden, or
    // write barriers are not used and this is never called
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return false;
  }

  /**
   * Read a reference. Take appropriate read barrier action, and
   * return the value that was read.<p> This is a <b>substituting<b>
   * barrier.  The call to this barrier takes the place of a load.<p>
   *
   * @param src The object reference being read.
   * @param context The context in which the read arose (getfield, for example)
   * @return The reference that was read.
   */
  public Address readBarrier(ObjectReference src, Address slot,
      int context)
      throws InlinePragma {
    // read barrier currently unimplemented
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    return Address.max();
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

  /** @return the unique identifier for this mutator context. */
  public int getId() throws InlinePragma { return id; }
}
