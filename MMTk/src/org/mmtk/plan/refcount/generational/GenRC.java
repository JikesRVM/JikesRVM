/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.plan.refcount.generational;

import org.mmtk.plan.refcount.RCBase;
import org.mmtk.policy.CopySpace;
import org.mmtk.policy.Space;
import org.mmtk.utility.options.Options;
import org.mmtk.vm.Collection;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements the global state of a simple reference counting
 * collector.
 * 
 * All plans make a clear distinction between <i>global</i> and
 * <i>thread-local</i> activities, and divides global and local state
 * into separate class hierarchies.  Global activities must be
 * synchronized, whereas no synchronization is required for
 * thread-local activities.  There is a single instance of Plan (or the
 * appropriate sub-class), and a 1:1 mapping of PlanLocal to "kernel
 * threads" (aka CPUs or in Jikes RVM, VM_Processors).  Thus instance
 * methods of PlanLocal allow fast, unsychronized access to functions such as
 * allocation and collection.
 *
 * The global instance defines and manages static resources
 * (such as memory and virtual memory resources).  This mapping of threads to
 * instances is crucial to understanding the correctness and
 * performance properties of MMTk plans.
 * 
 *
 */
@Uninterruptible public class GenRC extends RCBase {
  
  /****************************************************************************
   *
   * Class variables
   */
  
  /** The nursery space, where all new objects are allocated by default. */
  public static CopySpace nurserySpace = new CopySpace("nursery", DEFAULT_POLL_FREQUENCY, (float) 0.15, true, false);
  
  public static final int NS = nurserySpace.getDescriptor();
  
  // Allocators
  public static final int ALLOC_NURSERY = ALLOC_DEFAULT;  

  /****************************************************************************
   * Instance variables
   */
  
  /**
   * Constructor.
   * 
   */
  public GenRC() {
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(WITH_COALESCING_RC);
    }
  }
  
  /*****************************************************************************
   * 
   * Collection
   */
  
  
  /**
   * Perform a (global) collection phase.
   * 
   * @param phaseId Collection phase to execute.
   */
  @NoInline
  public void collectionPhase(int phaseId) { 
    if (phaseId == PREPARE) {
      nurserySpace.prepare(true);
    }
    
    if (phaseId == RELEASE) {
      nurserySpace.release();
    }
    
    super.collectionPhase(phaseId);
  }
  
  /**
   * This method is called periodically by the allocation subsystem
   * (by default, each time a page is consumed), and provides the
   * collector with an opportunity to collect.<p>
   *
   * We trigger a collection whenever an allocation request is made
   * that would take the number of pages in use (committed for use)
   * beyond the number of pages available.  Collections are triggered
   * through the runtime, and ultimately call the
   * <code>collect()</code> method of this class or its superclass.<p>
   *
   * This method is clearly interruptible since it can lead to a GC.
   * However, the caller is typically uninterruptible and this fiat allows
   * the interruptibility check to work.  The caveat is that the caller
   * of this method must code as though the method is interruptible.
   * In practice, this means that, after this call, processor-specific
   * values must be reloaded.
   *
   * @see org.mmtk.policy.Space#acquire(int)
   * @param mustCollect if <code>true</code> then a collection is
   * required and must be triggered.  Otherwise a collection is only
   * triggered if we deem it necessary.
   * @param space the space that triggered the polling (i.e. the space
   * into which an allocation is about to occur).
   * @return True if a collection has been triggered
   */
  @LogicallyUninterruptible
  public boolean poll(boolean mustCollect, Space space) { 
    if (getCollectionsInitiated() > 0 || !isInitialized()) return false;
    mustCollect |= stressTestGCRequired();
    boolean heapFull = getPagesReserved() > getTotalPages();
    boolean nurseryFull = nurserySpace.reservedPages() >
                          Options.nurserySize.getMaxNursery();
    boolean metaDataFull = metaDataSpace.reservedPages() >
                           META_DATA_FULL_THRESHOLD;
    int newMetaDataPages = metaDataSpace.committedPages() - 
                           previousMetaDataPages;
    if (mustCollect || heapFull || nurseryFull || metaDataFull ||
        (progress && (newMetaDataPages > Options.metaDataLimit.getPages()))) {
      if (space == metaDataSpace) {
        setAwaitingCollection();
        return false;
      }
      required = space.reservedPages() - space.committedPages();
      // account for copy reserve
      if (space == nurserySpace) required = required<<1;
      VM.collection.triggerCollection(Collection.RESOURCE_GC_TRIGGER);
      return true;
    }
    return false;
  }
  
  /**
   * Return the number of pages available for allocation, <i>assuming
   * all future allocation is to the nursery</i>.
   *
   * @return The number of pages available for allocation, <i>assuming
   * all future allocation is to the nursery</i>.
   */
  public int getPagesAvail() {
    return super.getPagesAvail() >> 1;
  }


  /**
   * Return the number of pages reserved for copying.
   *
   * @return The number of pages reserved given the pending
   * allocation, including space reserved for copying.
   */
  public final int getCopyReserve() {
    return nurserySpace.reservedPages() + super.getCopyReserve();
  }

  /**
   * Return the number of pages in use given the pending
   * allocation.  Simply add the nursery's contribution to that of
   * the superclass.
   *
   * @return The number of pages reserved given the pending
   * allocation, excluding space reserved for copying.
   */

  public final int getPagesUsed() {
    return super.getPagesUsed() + nurserySpace.reservedPages();
  }
  
  /**
   * @see org.mmtk.plan.Plan#objectCanMove
   * 
   * @param object Object in question
   * @return False if the object will never move
   */
  @Override
  public boolean objectCanMove(ObjectReference object) {
    if (Space.isInSpace(NS, object))
      return true;
    return super.objectCanMove(object);
  }

}
