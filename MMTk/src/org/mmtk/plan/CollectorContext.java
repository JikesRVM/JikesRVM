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
package org.mmtk.plan;

import org.mmtk.policy.ImmortalLocal;
import org.mmtk.utility.sanitychecker.SanityCheckerLocal;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.alloc.BumpPointer;
import org.mmtk.utility.Constants;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class (and its sub-classes) implement <i>per-collector thread</i>
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
 * kernel threads).</p>
 *
 * <p>Collector operations are separated into <i>per-collector thread</i>
 * operations (the bulk of the GC), and <i>per-mutator thread</i> operations
 * (important in flushing and restoring per-mutator state such as allocator
 * state and write buffer/remset state).  {@link SimplePhase}
 * ensures that per-collector thread GC phases are performed by each
 * collector thread, and that the <i>M</i> per-mutator thread operations
 * are multiplexed across the <i>N</i> active collector threads.</p>
 *
 * <p>MMTk assumes that the VM instantiates instances of {@link CollectorContext}
 * in thread local storage (TLS) for each thread participating in
 * collection.  Accesses to this state are therefore assumed to be
 * low-cost at GC time.<p>
 *
 * <p>MMTk explicitly separates thread-local (this class) and global
 * operations (See {@link Plan}), so that syncrhonization is localized
 * and explicit, and thus hopefully minimized (See {@link Plan}). Global (Plan)
 * and per-thread (this class) state are also explicitly separated.
 * Operations in this class (and its children) are therefore strictly
 * local to each collector thread, and synchronized operations always
 * happen via access to explicitly global classes such as Plan and its
 * children.</p>
 *
 * <p>This class (and its children) therefore typically implement per-collector
 * thread structures such as collection work queues.</p>
 *
 * @see MutatorContext
 * @see org.mmtk.vm.ActivePlan
 * @see Plan
 */
@Uninterruptible public abstract class CollectorContext implements Constants {

  /****************************************************************************
   * Instance fields
   */
  /** Unique collector identifier */
  private int id;

  /** Per-collector allocator into the immortal space */
  protected final BumpPointer immortal = new ImmortalLocal(Plan.immortalSpace);

  /** Used for aborting concurrent phases pre-empted by stop the world collection */
  protected boolean resetConcurrentWork;

  /** Used for sanity checking */
  protected final SanityCheckerLocal sanityLocal = new SanityCheckerLocal();

  /****************************************************************************
   *
   * Initialization
   */
  protected CollectorContext() {
  }

  /**
   * Notify that the collector context is registered and ready to execute.
   *
   * @param id The id of this collector context.
   */
  public void initCollector(int id) {
    this.id = id;
  }

  /****************************************************************************
   * Collection-time allocation.
   */

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
    VM.assertions.fail("Collector has not implemented allocCopy");
    return Address.max();
  }

  /**
   * Perform any post-copy actions.
   *
   * @param ref The newly allocated object.
   * @param typeRef the type reference for the instance being created.
   * @param bytes The size of the space to be allocated (in bytes).
   * @param allocator The allocator statically assigned to this allocation.
   */
  public void postCopy(ObjectReference ref, ObjectReference typeRef,
      int bytes, int allocator) {
    VM.assertions.fail("Collector has not implemented postCopy");
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
  @Inline
  public int copyCheckAllocator(ObjectReference from, int bytes,
      int align, int allocator) {
      boolean large = Allocator.getMaximumAlignedSize(bytes, align) > Plan.MAX_NON_LOS_COPY_BYTES;
      return large ? Plan.ALLOC_LOS : allocator;
  }

  /****************************************************************************
   * Collection.
   */

  /** Perform a garbage collection */
  public abstract void collect();

  /** Perform some concurrent garbage collection */
  public abstract void concurrentCollect();

  /**
   * Perform a (local) collection phase.
   *
   * @param phaseId The unique phase identifier
   * @param primary Should this thread be used to execute any single-threaded
   * local operations?
   */
  public abstract void collectionPhase(short phaseId, boolean primary);

  /**
   * Perform some concurrent collection work.
   *
   * @param phaseId The unique phase identifier
   */
  public abstract void concurrentCollectionPhase(short phaseId);

  /** @return The current trace instance. */
  public abstract TraceLocal getCurrentTrace();

  /**
   * Abort concurrent work due to pre-empt by stop the world collection.
   */
  protected void resetConcurrentWork() {
    resetConcurrentWork = true;
  }

  /**
   * Allow concurrent work to continue.
   */
  protected void clearResetConcurrentWork() {
    resetConcurrentWork = false;
  }

  /****************************************************************************
   * Miscellaneous.
   */

  /** @return the unique identifier for this collector context. */
  @Inline
  public int getId() { return id; }
}
