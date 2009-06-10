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
package org.mmtk.utility.sanitychecker;

import org.mmtk.plan.Plan;
import org.mmtk.plan.Simple;
import org.mmtk.utility.Constants;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;

/**
 * This class performs sanity checks for Simple collectors.
 */
@Uninterruptible
public final class SanityCheckerLocal implements Constants {

  /* Trace */
  final SanityRootTraceLocal rootTraceLocal;

  /****************************************************************************
   * Constants
   */
  public SanityCheckerLocal() {
    rootTraceLocal = new SanityRootTraceLocal(Plan.sanityChecker.rootTrace);
  }

  /**
   * Perform any sanity checking collection phases.
   *
   * @param phaseId The id to proces
   * @param primary Perform local single threaded actions on this thread
   * @return True if the phase was handled.
   */
  @NoInline
  public boolean collectionPhase(int phaseId, boolean primary) {
    if (phaseId == Simple.SANITY_PREPARE) {
      rootTraceLocal.prepare();
      return true;
    }

    if (phaseId == Simple.SANITY_ROOTS) {
      VM.scanning.computeGlobalRoots(rootTraceLocal);
      VM.scanning.computeThreadRoots(rootTraceLocal);
      VM.scanning.computeStaticRoots(rootTraceLocal);
      if (Plan.SCAN_BOOT_IMAGE) {
        VM.scanning.computeBootImageRoots(rootTraceLocal);
      }
      rootTraceLocal.flush();
      return true;
    }

    if (phaseId == Simple.SANITY_COPY_ROOTS) {
      if (primary) {
        rootTraceLocal.copyRootValuesTo(Plan.sanityChecker.checkTraceLocal);
      }
      return true;
    }

    if (phaseId == Simple.SANITY_RELEASE) {
      rootTraceLocal.release();
      return true;
    }

    return false;
  }
}
