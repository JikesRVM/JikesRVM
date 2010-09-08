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

import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.Constants;
import org.mmtk.utility.Log;

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
@Uninterruptible
public abstract class CollectorContext implements Constants {

  /****************************************************************************
   * Instance fields
   */

  /** Unique identifier. */
  private int id;

  /** Used for printing log information in a thread safe manner */
  protected final Log log = new Log();

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Notify that the collector context is registered and ready to execute.
   *
   * @param id The id of this collector context.
   */
  @Interruptible
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
  public Address allocCopy(ObjectReference original, int bytes, int align, int offset, int allocator) {
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
  public void postCopy(ObjectReference ref, ObjectReference typeRef, int bytes, int allocator) {
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
  public int copyCheckAllocator(ObjectReference from, int bytes, int align, int allocator) {
    boolean large = Allocator.getMaximumAlignedSize(bytes, align) > Plan.MAX_NON_LOS_COPY_BYTES;
    return large ? Plan.ALLOC_LOS : allocator;
  }

  /****************************************************************************
   * Collection.
   */

  /**
   * Entry point for the collector context.
   */
  @Unpreemptible
  public abstract void run();

  /**
   * The number of parallel workers currently executing with this collector
   * context. This can be queried from anywhere within a collector context
   * to determine how best to perform load-balancing.
   *
   * @return The number of parallel workers.
   */
  public int parallelWorkerCount() {
    return 1;
  }

  /**
   * The ordinal of the current worker. This is in the range of 0 to the result
   * of parallelWorkerCount() exclusive.
   *
   * @return The ordinal of this collector context, starting from 0.
   */
  public int parallelWorkerOrdinal() {
    return 0;
  }

  /**
   * Get the executing context to rendezvous with other contexts working
   * in parallel.
   *
   * @return The order this context reached the rendezvous, starting from 0.
   */
  public int rendezvous() {
    return 0;
  }

  /****************************************************************************
   * Miscellaneous.
   */

  /** @return the <code>Log</code> instance for this collector context. */
  public final Log getLog() {
    return log;
  }

  /**
   * @return The unique identifier for this collector context.
   */
  @Inline
  public int getId() {
    return id;
  }
}
