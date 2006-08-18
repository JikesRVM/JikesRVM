/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2006
 */
package org.mmtk.plan;

import org.mmtk.policy.ImmortalLocal;
import org.mmtk.utility.sanitychecker.SanityCheckerLocal;
import org.mmtk.utility.alloc.BumpPointer;
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
 * kernel threads).<p>
 * 
 * Collector operations are separated into <i>per-collector thread</i>
 * operations (the bulk of the GC), and <i>per-mutator thread</i> operations
 * (important in flushing and restoring per-mutator state such as allocator
 * state and write buffer/remset state).  <code>SimplePhase</code>
 * ensures that per-collector thread GC phases are performed by each
 * collector thread, and that the <i>M</i> per-mutator thread operations
 * are multiplexed across the <i>N</i> active collector threads
 * (@see SimplePhase#delegatePhase).<p>
 * 
 * MMTk assumes that the VM instantiates instances of CollectorContext
 * in thread local storage (TLS) for each thread participating in 
 * collection.  Accesses to this state are therefore assumed to be 
 * low-cost at GC time.<p> 
 * 
 * MMTk explicitly separates thread-local (this class) and global
 * operations (@see Plan), so that syncrhonization is localized
 * and explicit, and thus hopefully minimized (@see Plan). Gloabl (Plan) 
 * and per-thread (this class) state are also explicitly separated.
 * Operations in this class (and its children) are therefore strictly
 * local to each collector thread, and synchronized operations always
 * happen via access to explicitly global classes such as Plan and its
 * children.<p>
 * 
 * This class (and its children) therefore typically implement per-collector
 * thread structures such as collection work queues.
 * 
 * @see SimplePhase#delegatePhase
 * @see MutatorContext
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
public abstract class CollectorContext implements Uninterruptible, Constants {

  /****************************************************************************
   * Instance fields
   */
  /** Unique collector identifier */
  protected int id = VM.activePlan.registerCollector(this);

  /** Used for printing log information in a thread safe manner */
  protected Log log = new Log();

  /** Per-collector allocator into the immortal space */
  protected BumpPointer immortal = new ImmortalLocal(Plan.immortalSpace);

  
  /****************************************************************************
   * 
   * Initialization
   */
  protected CollectorContext() {}

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
   * @param ref The newly allocated object
   * @param typeRef the type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
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
  public int copyCheckAllocator(ObjectReference from, int bytes,
      int align, int allocator)
  throws InlinePragma {
    return allocator;
  }

  /****************************************************************************
   * Collection.
   */

  /** Perform a garbage collection */
  public abstract void collect();

  /**
   * Perform a (local) collection phase.
   * 
   * @param phaseId The unique phase identifier
   * @param primary Should this thread be used to execute any single-threaded
   * local operations?
   */
  public abstract void collectionPhase(int phaseId, boolean primary);

  /** @return The current trace instance. */
  public abstract TraceLocal getCurrentTrace();

  /** @return Return the current sanity checker. */
  public SanityCheckerLocal getSanityChecker() {
    return null;
  }

  /****************************************************************************
   * Miscellaneous.
   */

  /** @return the <code>Log</code> instance for this PlanLocal */
  public final Log getLog() {
    return log;
  }

  /** @return the unique identifier for this collector context. */
  public int getId() throws InlinePragma { return id; }
}
