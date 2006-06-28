/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package org.mmtk.plan.refcount.fullheap;

import org.mmtk.plan.refcount.RCBaseCollector;
import org.mmtk.plan.TraceLocal;
import org.mmtk.utility.Constants;
import org.mmtk.vm.ActivePlan;

import org.vmmagic.pragma.*;

/**
 * This class implements <i>per-collector thread</i> behavior and
 * state for the <i>RC</i> plan, a simple full-heap reference
 * counting collector.<p>
 * 
 * See {@link RC} for a description of the full-heap reference counting
 * algorithm.<p>
 * 
 * FIXME Currently RC does not properly separate mutator and collector
 * behaviors, so most of the collection logic in RCMutator should really
 * be per-collector thread, not per-mutator thread.
 * 
 * @see RCBaseCollector
 * @see RC
 * @see RCMutator
 * @see org.mmtk.plan.StopTheWorldCollector
 * @see org.mmtk.plan.CollectorContext
 * @see org.mmtk.plan.SimplePhase#delegatePhase
 * 
 * $Id$
 * 
 * @author Steve Blackburn
 * @author Robin Garner
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class RCCollector extends RCBaseCollector
implements Uninterruptible, Constants {
  /****************************************************************************
   * Instance fields
   */
  public final RCTraceLocal trace;

  
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

  /**
   * Constructor
   */
  public RCCollector() {
    trace = new RCTraceLocal(global().trace);
  }

  /****************************************************************************
   * 
   * Collection
   */

  /**
   * Perform a per-collector collection phase.
   * 
   * @param phaseId The collection phase to perform
   * @param primary Perform any single-threaded activities using this thread.
   */
  public void collectionPhase(int phaseId, boolean primary) {
    if (phaseId == RC.PREPARE) {
      // rc.prepare(primary);
      // if (RC.WITH_COALESCING_RC) processModBufs();
      return;
    }

    if (phaseId == RC.START_CLOSURE) {
      trace.startTrace();
      return;
    }

    if (phaseId == RC.COMPLETE_CLOSURE) {
      trace.completeTrace();
      return;
    }

    if (phaseId == RC.RELEASE) {
      // rc.release(this, primary);
      // if (Options.verbose.getValue() > 2) rc.printStats();
      return;
    }

    super.collectionPhase(phaseId, primary);
  }

  
  /****************************************************************************
   * 
   * Pointer enumeration
   */

  /**
   * A field of an object in the modified buffer is being enumerated
   * by ScanObject. If the field points to the RC space, increment the
   * count of the referent object.
   *
   * @param objLoc The address of a reference field with an object
   * being enumerated.
   */
  /*  public final void enumerateModifiedPointerLocation(Address objLoc)
   throws InlinePragma {
   if (Assert.VERIFY_ASSERTIONS) Assert._assert(RC.WITH_COALESCING_RC);
   ObjectReference object = objLoc.loadObjectReference();
   if (RC.isRCObject(object)) RefCountSpace.incRC(object);
   }
   */

  /****************************************************************************
   * 
   * Miscellaneous
   */

  public final TraceLocal getCurrentTrace() {
    return trace;
  }

  /** @return The active global plan as an <code>RC</code> instance. */
  private static final RC global() throws InlinePragma {
    return (RC) ActivePlan.global();
  }

}

