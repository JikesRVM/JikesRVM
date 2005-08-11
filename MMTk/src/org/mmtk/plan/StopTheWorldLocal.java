/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002, 2005
 */
package org.mmtk.plan;

import org.mmtk.utility.Finalizer;
import org.mmtk.utility.Log;
import org.mmtk.utility.ReferenceProcessor;
import org.mmtk.utility.options.Options;

import org.mmtk.vm.ActivePlan;
import org.mmtk.vm.Assert;
import org.mmtk.vm.Collection;
import org.mmtk.vm.Scanning;
import org.mmtk.vm.Memory;

import org.vmmagic.pragma.*;

/**
 * This abstract class implments the core functionality for
 * stop-the-world collectors.  Stop-the-world collectors should
 * inherit from this class.<p>
 *
 * This class provides the thread-local components.
 * @see StopTheWorld for more details.<p>
 *
 * $Id$
 *
 * @author Perry Cheng
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 */
public abstract class StopTheWorldLocal extends PlanLocal
  implements Uninterruptible {

  /**
   * @return The active global plan as a <code>StopTheWorld</code>
   * instance.
   */
  private static final StopTheWorld global() throws InlinePragma {
    return (StopTheWorld)ActivePlan.global();
  }

  /****************************************************************************
   * Collection
   */

  public void collect() {
    Phase.delegatePhase(global().collection);
  }

  /** 
   * Perform a (local) collection phase. 
   * 
   * @param phaseId The unique phase identifier
   * @param participating Is this thread participating in the collection? 
   * @param primary Should this thread be used to execute any single-threaded
   * local operations?
   */
  public void collectionPhase(int phaseId, boolean participating,
                              boolean primary)
    throws InlinePragma {
    if (phaseId == StopTheWorld.INITIATE) {
      if (participating) {
        Collection.prepareParticipating(this);
      } else {
        Collection.prepareNonParticipating(this);
      }
      return;
    }

    if (phaseId == StopTheWorld.PREPARE) {
      los.prepare();
      Memory.localPrepareVMSpace();
      return;
    }

    if (phaseId == StopTheWorld.PRECOPY) {
      if (ActivePlan.constraints().movesObjects()) {
        Scanning.preCopyGCInstances(getCurrentTrace());
      }
      return;
    }

    if (phaseId == StopTheWorld.ROOTS) {
      Scanning.computeAllRoots(getCurrentTrace());
      return;
    }

    if (phaseId == StopTheWorld.SOFT_REFS) {
      if (primary && !Options.noReferenceTypes.getValue())
        ReferenceProcessor.processSoftReferences(
            global().isCurrentGCNursery());
      return;
    }

    if (phaseId == StopTheWorld.WEAK_REFS) {
      if (primary && !Options.noReferenceTypes.getValue())
        ReferenceProcessor.processWeakReferences(
          global().isCurrentGCNursery());
      return;
    }

    if (phaseId == StopTheWorld.FINALIZABLE) {
      if (primary) {
        if (Options.noFinalizer.getValue())
          Finalizer.kill();
        else
          Finalizer.moveToFinalizable(getCurrentTrace());
      }
      return;
    }

    if (phaseId == StopTheWorld.PHANTOM_REFS) {
      if (primary && !Options.noReferenceTypes.getValue())
        ReferenceProcessor.processPhantomReferences(
            global().isCurrentGCNursery());
      return;
    }

    if (phaseId == StopTheWorld.FORWARD_REFS) {
      if (primary && !Options.noReferenceTypes.getValue() &&
          ActivePlan.constraints().needsForwardAfterLiveness()) {
        ReferenceProcessor.forwardReferences();
      }
      return;
    }

    if (phaseId == StopTheWorld.FORWARD_FINALIZABLE) {
      if (primary && !Options.noFinalizer.getValue() &&
          ActivePlan.constraints().needsForwardAfterLiveness()) {
        Finalizer.forward(getCurrentTrace());
      }
      return;
    }

    if (phaseId == StopTheWorld.RELEASE) {
      los.release();
      Memory.localReleaseVMSpace();
      return;
    }

    if (phaseId == StopTheWorld.COMPLETE) {
      // Nothing to do
      return;
    }

    Log.write("Local phase "); Log.write(Phase.getName(phaseId)); 
    Log.writeln(" not handled.");
    Assert.fail("Local phase not handled!");
  }
}
