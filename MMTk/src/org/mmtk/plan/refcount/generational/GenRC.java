/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package org.mmtk.plan.refcount.generational;

import org.mmtk.plan.refcount.RCBase;
import org.mmtk.plan.Trace;
import org.mmtk.policy.Space;
import org.mmtk.policy.CopySpace;
import org.mmtk.utility.deque.SharedDeque;
import org.mmtk.utility.options.*;

import org.mmtk.vm.Collection;
import org.mmtk.vm.Memory;
import org.mmtk.vm.Statistics;

import org.vmmagic.pragma.*;

/**
 * This class implements a simple non-concurrent reference counting
 * collector.
 *
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 */
public class GenRC extends RCBase implements Uninterruptible {

  /****************************************************************************
  *
  * Class variables
  */

  /** The nursery space, where all new objects are allocated by default. */
  public static CopySpace nurserySpace = new CopySpace("nursery", DEFAULT_POLL_FREQUENCY, (float) 0.15, true, false);
  
  public static final int NS = nurserySpace.getDescriptor();

  // Allocators
  public static final int ALLOC_NURSERY = ALLOC_DEFAULT;
  public static final int ALLOC_RC = RCBase.ALLOCATORS;
  public static final int ALLOCATORS = ALLOC_RC + 1;

  // Trace
  public Trace trace = new Trace(metaDataSpace);

  // Remset
  public final SharedDeque remsetPool = new SharedDeque(metaDataSpace, 1);

  public void collectionPhase(int phaseId) throws NoInlinePragma {
    if (phaseId == PREPARE) {
      timeCap = Statistics.cycles() +
                Statistics.millisToCycles(Options.gcTimeCap.getMilliseconds());
      nurserySpace.prepare(true);
      immortalSpace.prepare();
      Memory.globalPrepareVMSpace();
      rcSpace.prepare();
      trace.prepare();
      return;
    }

    if (phaseId == RELEASE) {
      trace.release();
      rcSpace.release();
      immortalSpace.release();
      nurserySpace.release();
      Memory.globalReleaseVMSpace();
      lastRCPages = rcSpace.committedPages();
      return;
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
  public final boolean poll(boolean mustCollect, Space space)
  throws LogicallyUninterruptiblePragma {
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
      Collection.triggerCollection(Collection.RESOURCE_GC_TRIGGER);
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

}

