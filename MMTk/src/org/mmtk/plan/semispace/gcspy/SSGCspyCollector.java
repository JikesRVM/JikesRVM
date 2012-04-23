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
package org.mmtk.plan.semispace.gcspy;

import org.mmtk.plan.Phase;
import org.mmtk.plan.semispace.SS;
import org.mmtk.plan.semispace.SSCollector;
import org.mmtk.policy.CopySpace;
import org.mmtk.utility.Log;
import org.mmtk.utility.gcspy.GCspy;
import org.mmtk.utility.gcspy.drivers.LinearSpaceDriver;
import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements <i>per-collector thread</i> behavior and state for the
 * <i>SSGCspy</i> plan.<p>
 *
 * See {@link SSGCspy} for an overview of the GC-spy mechanisms.<p>
 *
 * @see SSCollector
 * @see SSGCspy
 * @see SSGCspyMutator
 * @see org.mmtk.plan.StopTheWorldCollector
 * @see org.mmtk.plan.CollectorContext
 * @see org.mmtk.plan.SimplePhase
 */
@Uninterruptible public class SSGCspyCollector extends SSCollector {

  /****************************************************************************
   *
   * Initialization
   */

  /*****************************************************************************
   * Instance fields
   */

  private static final boolean DEBUG = false;

  /**
   * Constructor
   */
  public SSGCspyCollector() {
    super(new SSGCspyTraceLocal(global().ssTrace));
  }


  /****************************************************************************
   *
   * Data gathering
   */

  /**
   * Perform a (local) collection phase.
   * Before a collection, we need to discover
   * <ul>
   * <li>the tospace objects copied by the collector in the last GC cycle
   * <li>the semispace objects allocated since by the mutator.
   * <li>all immortal objects allocated by the mutator
   * <li>all large objects allocated by the mutator
   * </ul>
   * After the semispace has been copied, we need to discover
   * <ul>
   * <li>the tospace objects copied by the collector
   * <li>all immortal objects allocated by the mutator
   * <li>all large objects allocated by the mutator
   * </ul>
   */
  @Override
  @Inline
  public final void collectionPhase(short phaseId, boolean primary) {
    if (DEBUG) { Log.write("--Phase Collector."); Log.writeln(Phase.getName(phaseId)); }

    //TODO do we need to worry any longer about primary??
    if (phaseId == SS.PREPARE) {
      //if (primary)
        gcspyGatherData(SSGCspy.BEFORE_COLLECTION);
      super.collectionPhase(phaseId, primary);
      return;
    }

    if (phaseId == SS.FORWARD_FINALIZABLE) {
      super.collectionPhase(phaseId, primary);
      //if (primary)
        gcspyGatherData(SSGCspy.SEMISPACE_COPIED);
      return;
    }

    if (phaseId == SS.RELEASE) {
      //if (primary)
        //gcspyGatherData(SSGCspy.SEMISPACE_COPIED);
      super.collectionPhase(phaseId, primary);
      //if (primary)
        gcspyGatherData(SSGCspy.AFTER_COLLECTION);
      return;
    }

    super.collectionPhase(phaseId, primary);
  }

  /**
   * Gather data for GCspy for the semispaces only.
   * <p>
   * This method sweeps the semispace under consideration to gather data.
   * Alternatively and more efficiently, 'used space' can obviously be
   * discovered in constant time simply by comparing the start and the end
   * addresses of the semispace. However, per-object information can only be
   * gathered by sweeping through the space and we do this here for tutorial
   * purposes.
   *
   * @param event
   *          The event, either BEFORE_COLLECTION, SEMISPACE_COPIED or
   *          AFTER_COLLECTION
   */
  private void gcspyGatherData(int event) {
    if(DEBUG) {
      Log.writeln("SSGCspyCollector.gcspyGatherData, event=", event);
      Log.writeln("SSGCspyCollector.gcspyGatherData, port=", GCspy.getGCspyPort());
    }

    // If port = 0 there can be no GCspy client connected
    if (GCspy.getGCspyPort() == 0)
      return;

    // If the server is connected to a client that is interested in this
    // event, then we gather data. But first we start a timer to
    // compensate for the time spent gathering data here.
    if (GCspy.server.isConnected(event)) {

      if (DEBUG) {
        if (SSGCspy.hi)
          Log.write("\nCollector Examining Lowspace (event ", event);
        else
          Log.write("\nCollector Examining Highspace (event ", event);
        Log.write(")");
        SSGCspy.reportSpaces(); Log.writeln();
      }

      if (event == SSGCspy.BEFORE_COLLECTION) {
        if (DEBUG) debugSpaces(SSGCspy.fromSpace());

        // Just send the old values again
        if (DEBUG) {
          Log.write("SSGCspyCollector.gcspyGatherData transmit driver, ");
          Log.writeln(SSGCspy.fromSpace().getName());
        }

        fromSpaceDriver().transmit(event);
        // Mutator.gcspyGatherData follows so leave safepoint to there.
      } else if (event == SSGCspy.SEMISPACE_COPIED) {
        if (DEBUG) debugSpaces(SSGCspy.toSpace());

        // We need to reset, scan and send values for tospace
        // We'll leave resetting fromspace to AFTER_COLLECTION
        if (DEBUG) {
          Log.write("SSGCspyCollector.gcspyGatherData reset, gather and transmit driver ");
          Log.writeln(SSGCspy.toSpace().getName());
        }

        GCspy.server.startCompensationTimer();
        toSpaceDriver().resetData();
        ss.gcspyGatherData(toSpaceDriver(), SSGCspy.toSpace());
        GCspy.server.stopCompensationTimer();
        toSpaceDriver().transmit(event);

        // We'll leave the safepoint to RELEASE
      } else if (event == SSGCspy.AFTER_COLLECTION) {
        if (DEBUG) {
          Log.write("SSGCspyCollector.gcspyGatherData transmit toSpaceDriver, ");
          Log.writeln(SSGCspy.toSpace().getName());
          Log.write("SSGCspyCollector.gcspyGatherData reset fromSpaceDriver, ");
          Log.writeln(SSGCspy.fromSpace().getName());
        }

        toSpaceDriver().transmit(event);

        // Here we reset fromspace data
        fromSpaceDriver().resetData();
        Address start = SSGCspy.fromSpace().getStart();
        fromSpaceDriver().setRange(start, start);
        fromSpaceDriver().transmit(event);
      }

    }
    // else Log.write("not transmitting...");
  }

  /**
   * Print some debugging info
   * @param scannedSpace
   */
  private void debugSpaces(CopySpace scannedSpace) {
    Log.write("SSGCspyCollector.gcspyGatherData: gather data for active semispace ");
    Log.write(scannedSpace.getStart()); Log.write("-",ss.getCursor()); Log.flush();
    Log.write(". The space is: "); Log.writeln(ss.getSpace().getName());
    Log.write("scannedSpace is "); Log.writeln(scannedSpace.getName());
    Log.write("The range is "); Log.write(ss.getSpace().getStart());
    Log.write(" to "); Log.writeln(ss.getCursor());
    SSGCspy.reportSpaces();
  }

  /**
   * Reset all root streams.<p>
   */
  void resetRootStreams() {
    SSGCspy.ss0Driver.resetRootsStream();
    SSGCspy.ss1Driver.resetRootsStream();
    SSGCspy.immortalDriver.resetRootsStream();
    SSGCspy.losNurseryDriver.resetRootsStream();
    SSGCspy.losDriver.resetRootsStream();
    SSGCspy.plosNurseryDriver.resetRootsStream();
    SSGCspy.plosDriver.resetRootsStream();
    ss.getCursor();
  }

  /**
   * Pass a root to all drivers.<p>
   * @param addr The Address of the object to be checked
   */
  protected void checkAllDriversForRootAddress(Address addr) {
    if(addr.isZero())
      return;

    SSGCspy.ss0Driver.handleRoot(addr);
    SSGCspy.ss1Driver.handleRoot(addr);
    SSGCspy.immortalDriver.handleRoot(addr);
    SSGCspy.losNurseryDriver.handleRoot(addr);
    SSGCspy.losDriver.handleRoot(addr);
    SSGCspy.plosNurseryDriver.handleRoot(addr);
    SSGCspy.plosDriver.handleRoot(addr);
  }

  /****************************************************************************
   *
   * Miscellaneous
   */

  /** @return The active global plan as an <code>SSGCspy</code> instance. */
  @Inline
  private static SSGCspy global() {
    return (SSGCspy) VM.activePlan.global();
  }

  /** @return the driver for toSpace */
  private LinearSpaceDriver toSpaceDriver() {
    return SSGCspy.hi ? SSGCspy.ss1Driver : SSGCspy.ss0Driver;
  }

  /** @return the driver for fromSpace */
  private LinearSpaceDriver fromSpaceDriver() {
    return SSGCspy.hi ? SSGCspy.ss0Driver : SSGCspy.ss1Driver;
  }
}
