/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.plan;

import org.mmtk.utility.Log;
import org.mmtk.utility.options.Options;

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
 */
@Uninterruptible
public abstract class SimpleCollector extends CollectorContext {

  /****************************************************************************
   * Instance fields
   */

  /****************************************************************************
   *
   * Collection
   */

  /**
   * Perform a per-collector collection phase.
   *
   * @param phaseId The unique phase identifier
   * @param primary Should this thread be used to execute any single-threaded
   * local operations?
   */
  @Inline
  public void collectionPhase(short phaseId, boolean primary) {
    if (phaseId == Simple.PREPARE_STACKS) {
      if (!Plan.stacksPrepared()) {
        VM.collection.prepareCollector(this);
      }
      return;
    }

    if (phaseId == Simple.PREPARE) {
      // Nothing to do
      return;
    }

    if (phaseId == Simple.PRECOPY) {
      if (VM.activePlan.constraints().movesObjects()) {
        VM.scanning.preCopyGCInstances(getCurrentTrace());
      }
      return;
    }

    if (phaseId == Simple.STACK_ROOTS) {
      VM.scanning.computeThreadRoots(getCurrentTrace());
      return;
    }

    if (phaseId == Simple.ROOTS) {
      VM.scanning.computeGlobalRoots(getCurrentTrace());
      VM.scanning.computeStaticRoots(getCurrentTrace());
      if (Plan.SCAN_BOOT_IMAGE) {
        VM.scanning.computeBootImageRoots(getCurrentTrace());
      }
      return;
    }

    if (phaseId == Simple.SOFT_REFS) {
      if (primary) {
        if (Options.noReferenceTypes.getValue())
          VM.softReferences.clear();
        else
          VM.softReferences.scan(getCurrentTrace(),global().isCurrentGCNursery());
      }
      return;
    }

    if (phaseId == Simple.WEAK_REFS) {
      if (primary) {
        if (Options.noReferenceTypes.getValue())
          VM.weakReferences.clear();
        else
          VM.weakReferences.scan(getCurrentTrace(),global().isCurrentGCNursery());
      }
      return;
    }

    if (phaseId == Simple.FINALIZABLE) {
      if (primary) {
        if (Options.noFinalizer.getValue())
          VM.finalizableProcessor.clear();
        else
          VM.finalizableProcessor.scan(getCurrentTrace(),global().isCurrentGCNursery());
      }
      return;
    }

    if (phaseId == Simple.PHANTOM_REFS) {
      if (primary) {
        if (Options.noReferenceTypes.getValue())
          VM.phantomReferences.clear();
        else
          VM.phantomReferences.scan(getCurrentTrace(),global().isCurrentGCNursery());
      }
      return;
    }

    if (phaseId == Simple.FORWARD_REFS) {
      if (primary && !Options.noReferenceTypes.getValue() &&
          VM.activePlan.constraints().needsForwardAfterLiveness()) {
        VM.softReferences.forward(getCurrentTrace(),global().isCurrentGCNursery());
        VM.weakReferences.forward(getCurrentTrace(),global().isCurrentGCNursery());
        VM.phantomReferences.forward(getCurrentTrace(),global().isCurrentGCNursery());
      }
      return;
    }

    if (phaseId == Simple.FORWARD_FINALIZABLE) {
      if (primary && !Options.noFinalizer.getValue() &&
          VM.activePlan.constraints().needsForwardAfterLiveness()) {
        VM.finalizableProcessor.forward(getCurrentTrace(),global().isCurrentGCNursery());
      }
      return;
    }

    if (phaseId == Simple.COMPLETE) {
      // Nothing to do
      return;
    }

    if (phaseId == Simple.RELEASE) {
      // Nothing to do
      return;
    }

    if (Options.sanityCheck.getValue() && sanityLocal.collectionPhase(phaseId, primary)) {
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

  /** @return The active global plan as a <code>Simple</code> instance. */
  @Inline
  private static Simple global() {
    return (Simple) VM.activePlan.global();
  }
}
