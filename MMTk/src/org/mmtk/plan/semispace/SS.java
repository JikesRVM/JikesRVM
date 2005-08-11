/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.plan.semispace;

import org.mmtk.policy.CopySpace;
import org.mmtk.policy.Space;
import org.mmtk.vm.Collection;
import org.mmtk.plan.*;

import org.vmmagic.pragma.*;

/**
 * This class implements a simple semi-space collector. See the Jones
 * & Lins GC book, section 2.2 for an overview of the basic
 * algorithm. This implementation also includes a large object space
 * (LOS), and an uncollected "immortal" space.<p>
 *
 * All plans make a clear distinction between <i>global</i> and
 * <i>thread-local</i> activities.  Global activities must be
 * synchronized, whereas no synchronization is required for
 * thread-local activities.  Instances of Plan map 1:1 to "kernel
 * threads" (aka CPUs or in Jikes RVM, VM_Processors).  Thus instance
 * methods allow fast, unsychronized access to Plan utilities such as
 * allocation and collection.  Each instance rests on static resources
 * (such as memory and virtual memory resources) which are "global"
 * and therefore "static" members of Plan.  This mapping of threads to
 * instances is crucial to understanding the correctness and
 * performance proprties of this plan.
 *
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Perry Cheng
 * @author Robin Garner
 * @author Daniel Frampton
 *
 * @version $Revision$
 * @date $Date$
 */
public class SS extends StopTheWorld implements Uninterruptible {

  /****************************************************************************
   *
   * Class variables
   */

  // GC state
  public static boolean hi = false; // True if allocing to "higher" semispace

  /** One of the two semi spaces that alternate roles at each collection */
  public static CopySpace copySpace0 = new CopySpace("ss0", DEFAULT_POLL_FREQUENCY, (float) 0.35, false);
  
  /** One of the two semi spaces that alternate roles at each collection */
  public static CopySpace copySpace1 = new CopySpace("ss1", DEFAULT_POLL_FREQUENCY, (float) 0.35, true);
  
  public static final int SS0 = copySpace0.getDescriptor();
  public static final int SS1 = copySpace1.getDescriptor();

  public final Trace ssTrace;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Class variables
   */
  protected static final int ALLOC_SS = Plan.ALLOC_DEFAULT;

  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time).  This is where key <i>global</i>
   * instances are allocated.  These instances will be incorporated
   * into the boot image by the build process.
   */
  static {}

  /**
   * Constructor
   */
  public SS() {
    ssTrace = new Trace(metaDataSpace);
  }

  /**
   * @return The to space for the current collection.
   */
  public static final CopySpace toSpace() throws InlinePragma {
    return hi ? copySpace1 : copySpace0;
  }

  /**
   * @return The from space for the current collection.
   */
  public static final CopySpace fromSpace() throws InlinePragma {
    return hi ? copySpace0 : copySpace1;
  }


  /****************************************************************************
   *
   * Collection
   */

  /**
   * Perform a (global) collection phase.
   *
   * @param phaseId Collection phase
   */
  public void collectionPhase(int phaseId) throws InlinePragma {
    if (phaseId == SS.PREPARE) {
      hi = !hi;        // flip the semi-spaces
      // prepare each of the collected regions
      copySpace0.prepare(hi);
      copySpace1.prepare(!hi);
      super.collectionPhase(phaseId);
      return;
    }
    if (phaseId == SS.RELEASE) {
      // release the collected region
      fromSpace().release();

      super.collectionPhase(phaseId);
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
  public boolean poll(boolean mustCollect, Space space)
    throws LogicallyUninterruptiblePragma {
    if (getCollectionsInitiated() > 0 || !isInitialized() || space == metaDataSpace)
      return false;
    
    mustCollect |= stressTestGCRequired();
  
    boolean heapFull = getPagesReserved() > getTotalPages();
    if (mustCollect || heapFull) {
      required = space.reservedPages() - space.committedPages();
      if (space == copySpace0 || space == copySpace1)
        required = required<<1; // must account for copy reserve
      Collection.triggerCollection(Collection.RESOURCE_GC_TRIGGER);
      return true;
    }
    return false;
  }


  /****************************************************************************
  *
  * Accounting
  */

  /**
   * Return the number of pages reserved for copying.
   *
   * @return The number of pages reserved given the pending
   * allocation, including space reserved for copying.
   */
  public final int getCopyReserve() {
    // we must account for the number of pages required for copying,
    // which equals the number of semi-space pages reserved
    return toSpace().reservedPages() + super.getCopyReserve();
  }

  /**
   * Return the number of pages reserved for use given the pending
   * allocation.  This is <i>exclusive of</i> space reserved for
   * copying.
   *
   * @return The number of pages reserved given the pending
   * allocation, excluding space reserved for copying.
   */
  public final int getPagesUsed() {
    return super.getPagesUsed() + toSpace().reservedPages();
  }

  /**
   * Return the number of pages available for allocation, <i>assuming
   * all future allocation is to the semi-space</i>.
   *
   * @return The number of pages available for allocation, <i>assuming
   * all future allocation is to the semi-space</i>.
   */
  public final int getPagesAvail() {
    return (getTotalPages() - getPagesReserved()) >> 1;
  }


}
