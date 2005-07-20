/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package org.mmtk.plan.refcount;

import org.mmtk.plan.StopTheWorld;
import org.mmtk.policy.RefCountSpace;
import org.mmtk.policy.Space;
import org.mmtk.utility.deque.*;
import org.mmtk.utility.options.*;
import org.mmtk.utility.statistics.*;

import org.mmtk.vm.ActivePlan;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements a base functionality of a simple
 * non-concurrent reference counting collector.
 *
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 */
public abstract class RCBase extends StopTheWorld implements Uninterruptible {

  /****************************************************************************
   *
   * Class variables
   */
  public static final boolean REF_COUNT_CYCLE_DETECTION = true;
  public static final boolean WITH_COALESCING_RC = true;

  // Spaces
  public static RefCountSpace rcSpace = new RefCountSpace("rc", DEFAULT_POLL_FREQUENCY, (float) 0.5);
  public static final int RC = rcSpace.getDescriptor();

  // Counters
  public static EventCounter wbFast;
  public static EventCounter wbSlow;

  // shared queues
  protected SharedDeque decPool;
  protected SharedDeque modPool;
  protected SharedDeque rootPool;

  // GC state
  public int previousMetaDataPages;  // meta-data pages after last GC
  public int lastRCPages = 0; // pages at end of last GC

 /****************************************************************************
   *
   * Initialization
   */

  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time). This is where key <i>global</i> instances
   * are allocated.  These instances will be incorporated into the
   * boot image by the build process.
   */
  static {
    Options.gcTimeCap = new GCTimeCap();

    if (GATHER_WRITE_BARRIER_STATS) {
      wbFast = new EventCounter("wbFast");
      wbSlow = new EventCounter("wbSlow");
    }
  }

  /**
   * Constructor
   */
  public RCBase() {
    //  instantiate shared queues
    if (WITH_COALESCING_RC) {
      modPool = new SharedDeque(metaDataSpace, 1);
      modPool.newClient();
    }
    decPool = new SharedDeque(metaDataSpace, 1);
    decPool.newClient();
    rootPool = new SharedDeque(metaDataSpace, 1);
    rootPool.newClient();
  }


  /****************************************************************************
   *
   * RC methods
   */

  /**
   * Return true if the object resides within the RC space
   *
   * @param object An object reference
   * @return True if the object resides within the RC space
   */
  public static final boolean isRCObject(ObjectReference object)
    throws InlinePragma {
    if (object.isNull())
      return false;
    else return (Space.isInSpace(RC, object) || Space.isInSpace(LOS, object));
  }

  /**
   * Perform any required initialization of the GC portion of the header.
   * Called for objects created at boot time.
   *
   * @param ref the object ref to the storage to be initialized
   * @param typeRef the type reference for the instance being created
   * @param size the number of bytes allocated by the GC system for
   * this object.
   * @param status the initial value of the status word
   * @return The new value of the status word
   */
  public Word setBootTimeGCBits(Offset ref, ObjectReference typeRef,
                                              int size, Word status)
    throws UninterruptiblePragma, InlinePragma {
    if (WITH_COALESCING_RC) status = status.or(RefCountSpace.UNLOGGED);
    return status;
  }

  /****************************************************************************
  *
  * Space management
  */

  /**
   * Return the number of pages reserved for use given the pending
   * allocation.
   *
   * @return The number of pages reserved given the pending
   * allocation.
   */
  public int getPagesUsed() {
    return rcSpace.reservedPages() + super.getPagesUsed();
  }
  
  /**
   * @return the active PlanLocal as an RCBaseLocal
   */
  public static final RCBaseLocal local() {
    return ((RCBaseLocal)ActivePlan.local());
  }
}
