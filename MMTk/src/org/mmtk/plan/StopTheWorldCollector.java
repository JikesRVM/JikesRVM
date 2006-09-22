/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002, 2005, 2006
 */
package org.mmtk.plan;

import org.mmtk.utility.Finalizer;
import org.mmtk.utility.Log;
import org.mmtk.utility.ReferenceProcessor;
import org.mmtk.utility.options.Options;
import org.mmtk.utility.sanitychecker.SanityCheckerLocal;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;

/**
 * This class (and its sub-classes) implement <i>per-collector thread</i>
 * behavior and state.
 * 
 * MMTk assumes that the VM instantiates instances of CollectorContext
 * in thread local storage (TLS) for each thread participating in 
 * collection.  Accesses to this state are therefore assumed to be 
 * low-cost during mutator time.<p>
 * 
 * @see CollectorContext
 * @see SimplePhase#delegatePhase
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
public abstract class StopTheWorldCollector extends CollectorContext
implements Uninterruptible {

  /****************************************************************************
   * Instance fields
   */

  /** Basic sanity checker */
  private SanityCheckerLocal sanityChecker = new SanityCheckerLocal();

  /****************************************************************************
   * 
   * Collection
   */

  public void collect() {
    Phase.delegatePhase(global().collection);
  }

  /**
   * Perform a per-collector collection phase.
   * 
   * @param phaseId The unique phase identifier
   * @param primary Should this thread be used to execute any single-threaded
   * local operations?
   */
  public void collectionPhase(int phaseId, boolean primary)
  throws InlinePragma {
    if (phaseId == StopTheWorld.INITIATE) {
      VM.collection.prepareCollector(this);
      return;
    }

    if (phaseId == StopTheWorld.PREPARE) {
      // Nothing to do
      return;
    }

    if (phaseId == StopTheWorld.PRECOPY) {
      if (VM.activePlan.constraints().movesObjects()) {
        VM.scanning.preCopyGCInstances(getCurrentTrace());
      }
      return;
    }

    if (phaseId == StopTheWorld.ROOTS) {
      VM.scanning.computeAllRoots(getCurrentTrace());
      return;
    }

    if (phaseId == StopTheWorld.BOOTIMAGE_ROOTS) {
      if (Plan.SCAN_BOOT_IMAGE)
        VM.scanning.computeBootImageRoots(getCurrentTrace());
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
          VM.activePlan.constraints().needsForwardAfterLiveness()) {
        ReferenceProcessor.forwardReferences();
      }
      return;
    }

    if (phaseId == StopTheWorld.FORWARD_FINALIZABLE) {
      if (primary && !Options.noFinalizer.getValue() &&
          VM.activePlan.constraints().needsForwardAfterLiveness()) {
        Finalizer.forward(getCurrentTrace());
      }
      return;
    }

    if (phaseId == StopTheWorld.COMPLETE) {
      // Nothing to do
      return;
    }

    if (phaseId == StopTheWorld.RELEASE) {
      // Nothing to do
      return;
    }

    if (Options.sanityCheck.getValue() &&
        getSanityChecker().collectionPhase(phaseId, primary)) {
      return;
    }

    Log.write("Per-collector phase "); Log.write(Phase.getName(phaseId)); 
    Log.writeln(" not handled.");
    VM.assertions.fail("Per-collector phase not handled!");
  }

  /****************************************************************************
   * 
   * Miscellaneous.
   */

  /** @return The active global plan as a <code>StopTheWorld</code> instance. */
  private static final StopTheWorld global() throws InlinePragma {
    return (StopTheWorld) VM.activePlan.global();
  }

  /** @return The current sanity checker. */
  public SanityCheckerLocal getSanityChecker() {
    return sanityChecker;
  }
}
