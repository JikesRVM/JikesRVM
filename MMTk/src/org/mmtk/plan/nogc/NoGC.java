/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan.nogc;

import org.mmtk.plan.Plan;
import org.mmtk.plan.Trace;
import org.mmtk.policy.ImmortalSpace;
import org.mmtk.policy.Space;
import org.mmtk.vm.Assert;

import org.vmmagic.pragma.*;


/**
 * This class implements the global state of a a simple allocator
 * without a collector.
 *
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 */
public class NoGC extends Plan implements Uninterruptible {

  /*****************************************************************************
   *
   * Class fields
   */
  public static final ImmortalSpace defSpace
    = new ImmortalSpace("default", DEFAULT_POLL_FREQUENCY, (float) 0.6);
  public static final int DEF = defSpace.getDescriptor();

  /*****************************************************************************
   *
   * Instance fields
   */
  public final Trace trace;

  /**
   * Constructor
   */
  public NoGC() {
    trace = new Trace(metaDataSpace);
  }

  /*****************************************************************************
   *
   * Collection
   */

  /**
   * Perform a (global) collection phase.
   *
   * @param phaseId Collection phase
   */
  public final void collectionPhase(int phaseId) {
    Assert._assert(false);
//    if (phaseId == PREPARE) {
//    }
//    if (phaseID == RELEASE) {
//    }
//    super.collectionPhase(phaseId);
  }

  /**
   * Poll for a collection
   *
   * @param mustCollect Force a collection.
   * @param space The space that caused the poll.
   * @return True if a collection is required.
   */
  public final boolean poll(boolean mustCollect, Space space) {
	  if (getPagesReserved() > getTotalPages()) {
	  Assert.fail("GC Triggered in NoGC Plan due to memory exhaustion.");
    }
	  return false;
  }

  /*****************************************************************************
   *
   * Accounting
   */

  /**
   * Return the number of pages used given the pending
   * allocation.
   *
   * @return The number of pages reserved given the pending
   * allocation, excluding space reserved for copying.
   */
  public int getPagesUsed() {
    return (defSpace.reservedPages() + super.getPagesUsed());
  }

}
