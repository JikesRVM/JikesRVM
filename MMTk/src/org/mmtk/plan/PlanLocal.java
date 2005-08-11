/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan;

import org.mmtk.policy.Space;
import org.mmtk.policy.ImmortalLocal;
import org.mmtk.policy.LargeObjectLocal;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.alloc.BumpPointer;
import org.mmtk.utility.Constants;
import org.mmtk.utility.Log;

import org.mmtk.vm.ActivePlan;
import org.mmtk.vm.Assert;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * Local state for an MMTk plan.  Instances of this class have a 1:1
 * relationship with kernel threads, and provide fast unsynchronised
 * access to per-thread data structures.
 *
 * $Id$
 *
 * @author Perry Cheng
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 * @see Plan
 */
public abstract class PlanLocal implements Uninterruptible, Constants {

  /****************************************************************************
   * Instance fields
   */
  /** Unique plan identifier */
  protected int id = ActivePlan.registerLocal(this);
  
  /** Used for printing log information in a thread safe manner */ 
  protected Log log = new Log();
  
  /** Thread local allocator into the immortal space */
  protected BumpPointer immortal = new ImmortalLocal(Plan.immortalSpace);
  
  /** Thread local allocator into the large object space */
  protected LargeObjectLocal los = new LargeObjectLocal(Plan.loSpace);

  /****************************************************************************
   * Constructor
   */
  protected PlanLocal() {}


  /****************************************************************************
   * Collection.
   */

  /** 
   * Perform a garbage collection 
   */
  public abstract void collect();

  /** 
   * Perform a (local) collection phase. 
   * 
   * @param phaseId The unique phase identifier
   * @param participating Is this thread participating in the collection? 
   * @param primary Should this thread be used to execute any single-threaded
   * local operations?
   */
  public abstract void collectionPhase(int phaseId, boolean participating,
                                       boolean primary);

  /** 
   * @return The current trace instance. 
   */
  public abstract TraceLocal getCurrentTrace();

  /****************************************************************************
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
    else
      return allocator;
  }

  /**
   * Allocate memory for an object.
   *
   * @param bytes The number of bytes required for the object.
   * @param align Required alignment for the object.
   * @param offset Offset associated with the alignment.
   * @param allocator The allocator associated with this request.
   * @return The low address of the allocated chunk.
   */
  public Address alloc(int bytes, int align, int offset, int allocator)
    throws InlinePragma {
    switch (allocator) {
    case      Plan.ALLOC_LOS: return los.alloc(bytes, align, offset);
    case Plan.ALLOC_IMMORTAL: return immortal.alloc(bytes, align, offset);
    default:
      if (Assert.VERIFY_ASSERTIONS) Assert.fail("No such allocator");
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
    case      Plan.ALLOC_LOS: Plan.loSpace.initializeHeader(ref); return;
    case Plan.ALLOC_IMMORTAL: Plan.immortalSpace.postAlloc(ref);  return;
    default:
      if (Assert.VERIFY_ASSERTIONS) Assert.fail("No such allocator");
    }
  }

  /**
   * Run-time check of the allocator to use for a given copy allocation
   *
   * At the moment this method assumes that allocators will use the simple
   * (worst) method of aligning to determine if the object is a large object
   * to ensure that no objects are larger than other allocators can handle.
   *
   * @param from The object that is being copied.
   * @param bytes The number of bytes to be allocated.
   * @param align The requested alignment.
   * @param allocator The allocator statically assigned to this allocation.
   * @return The allocator dyncamically assigned to this allocation.
   */
  public int copyCheckAllocator(ObjectReference from, int bytes,
                                int align, int allocator)
    throws InlinePragma {
    return allocator;
  }


  /**
   * Allocate memory when copying an object.
   *
   * @param original The object that is being copied.
   * @param bytes The number of bytes required for the copy.
   * @param align Required alignment for the copy.
   * @param offset Offset associated with the alignment.
   * @param allocator The allocator associated with this request.
   * @return The address of the newly allocated region.
   */
  public Address allocCopy(ObjectReference original, int bytes,
                           int align, int offset, int allocator) {
    Assert.fail("Collector has not implemented allocCopy");
    return Address.max();
  }

  /**
   * Perform any post-copy actions.
   *
   * @param ref The newly allocated object
   * @param typeRef the type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   */
  public void postCopy(ObjectReference ref, ObjectReference typeRef,
                       int bytes, int allocator) {
    Assert.fail("Collector has not implemented postCopy");
  }

  /****************************************************************************
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
   * org.mmtk.utility.alloc.Allocator#allocSlowBody(int, int, int,
   * boolean) Allocator.allocSlowBody()}).
   *
   * @see org.mmtk.utility.alloc.Allocator
   * @see org.mmtk.utility.alloc.Allocator#allocSlowBody(int, int,
   * int, boolean)
   *
   * @param a An allocator instance.
   * @return An allocator instance associated with <i>this plan
   * instance</i> that allocates into the same space as <code>a</code>
   * (this may in fact be <code>a</code>).
   */
  public final Allocator getOwnAllocator(Allocator a) {
    Space space = Plan.getSpaceFromAllocatorAnyLocal(a);
    if (space == null)
      Assert.fail("PlanLocal.getOwnAllocator could not obtain space");
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
   * <code>null</code> if there is no space associated with
   * <code>a</code>.
   */
  public Space getSpaceFromAllocator(Allocator a) {
    if (a == immortal) return Plan.immortalSpace;
    if (a == los)      return Plan.loSpace;

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

    // Invalid request has been made
    if (space == Plan.metaDataSpace) {
      Assert.fail("PlanLocal.getAllocatorFromSpace given meta space");
    } else if (space != null) {
      Assert.fail("PlanLocal.getAllocatorFromSpace given invalid space");
    } else {
      Assert.fail("PlanLocal.getAllocatorFromSpace given null space");
    }

    return null;
  }

  /****************************************************************************
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
    //         write barriers are not used and this is never called
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(false);
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
    //         write barriers are not used and this is never called
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(false);
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
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(false);
    return Address.max();
  }

  /****************************************************************************
   * Miscellaneous.
   */

  /** @return the <code>Log</code> instance for this PlanLocal */
  public final Log getLog() {
    return log;
  }
}
