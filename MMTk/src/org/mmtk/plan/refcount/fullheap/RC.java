/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package org.mmtk.plan.refcount.fullheap;

import org.mmtk.plan.refcount.RCBase;
import org.mmtk.plan.Trace;
import org.mmtk.policy.Space;
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
public class RC extends RCBase implements Uninterruptible {

  /****************************************************************************
   *
   * Class variables
   */
  static final boolean INLINE_WRITE_BARRIER = WITH_COALESCING_RC;

  public static final int ALLOC_RC = ALLOC_DEFAULT;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time).  This is where key <i>global</i>
   * instances are allocated.  These instances will be incorporated
   * into the boot image by the build process.
   */
  static { }

  public final Trace trace;

  /**
   * Constructor
   */
  public RC() {
    trace = new Trace(metaDataSpace);
  }


  public void collectionPhase(int phaseId) throws NoInlinePragma {
    if (phaseId == PREPARE) {
      timeCap = Statistics.cycles() +
                Statistics.millisToCycles(Options.gcTimeCap.getMilliseconds());
      immortalSpace.prepare();
      Memory.globalPrepareVMSpace();
      rcSpace.prepare();
      // loSpace doesn't require prepare() as rcSpace takes care of it.
      trace.prepare();
      return;
    }

    if (phaseId == RELEASE) {
      trace.release();
      rcSpace.release();
      // loSpace doesn't require release() as rcSpace takes care of it.
      immortalSpace.release();
      Memory.globalReleaseVMSpace();
      lastRCPages = rcSpace.committedPages();
      return;
    }

    super.collectionPhase(phaseId);
  }

  /****************************************************************************
   *
   * Space management
   */

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
    if (mustCollect || getPagesReserved() > getTotalPages() ||
        (progress &&
         ((rcSpace.committedPages() - lastRCPages) >
          Options.nurserySize.getMaxNursery() ||
         metaDataSpace.committedPages() > Options.metaDataLimit.getPages()))) {
      if (space == metaDataSpace) {
        setAwaitingCollection();
        return false;
      }
      required = space.reservedPages() - space.committedPages();
      Collection.triggerCollection(Collection.RESOURCE_GC_TRIGGER);
      return true;
    }
    return false;
  }
}

