/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.plan.semispace.gcspy;

import org.mmtk.plan.semispace.SS;
import org.mmtk.plan.semispace.SSCollector;
import org.mmtk.policy.Space;
import org.mmtk.policy.CopySpace;
import org.mmtk.utility.alloc.BumpPointer;
import org.mmtk.utility.alloc.LinearScan;
import org.mmtk.utility.gcspy.drivers.ContiguousSpaceDriver;
import org.mmtk.utility.gcspy.GCspy;

import org.mmtk.vm.ActivePlan;
import org.mmtk.vm.Assert;
import org.mmtk.vm.gcspy.ServerInterpreter;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements <i>per-collector thread</i> behavior and state for the
 * <i>SSGCSpy</i> plan.<p>
 * 
 * See {@link SSGCspy} for an overview of the GC-spy mechanisms.<p>
 * 
 * @see SSCollector
 * @see SSGCSpy
 * @see SSGCSpyMutator
 * @see org.mmtk.plan.StopTheWorldCollector
 * @see org.mmtk.plan.CollectorContext
 * @see org.mmtk.plan.SimplePhase#delegatePhase
 * 
 * $Id$
 * 
 * @author Steve Blackburn
 * @author Perry Cheng
 * @author Daniel Frampton
 * @author Robin Garner
 * @author <a href="http://www.cs.ukc.ac.uk/~rej">Richard Jones</a>
 * 
 * @version $Revision$
 * @date $Date$
 */
public class SSGCspyCollector extends SSCollector implements Uninterruptible {

  /****************************************************************************
   * 
   * Data gathering
   */

  /**
   * Perform a (local) collection phase.
   */
  public final void collectionPhase(int phaseId, boolean primary)
      throws InlinePragma {
    if (phaseId == SS.PREPARE) {
      if (primary) gcspyGatherData(SSGCspy.BEFORE_COLLECTION);
      super.collectionPhase(phaseId, primary);
      return;
    }

    if (phaseId == SS.RELEASE) {
      gcspyGatherData(SSGCspy.SEMISPACE_COPIED);
      super.collectionPhase(phaseId, primary);
      gcspyGatherData(SSGCspy.AFTER_COLLECTION);
      return;
    }

    super.collectionPhase(phaseId, primary);
  }

  /**
   * Gather data for GCspy
   * This method sweeps the semispace under consideration to gather data.
   * Used space can obviously be discovered in constant time simply by comparing
   * the start and the end addresses of the semispace, but per-object
   * information needs to be gathered by sweeping through the space.
   * 
   * @param event The event, either BEFORE_COLLECTION, SEMISPACE_COPIED or
   *          AFTER_COLLECTION
   */
  private void gcspyGatherData(int event) {
    // Port = 0 means no gcspy
    if (GCspy.getGCspyPort() == 0)
      return;

    if (ServerInterpreter.shouldTransmit(event)) {
      ServerInterpreter.startCompensationTimer();

      // -- Handle the semispace collector --
      // BEFORE_COLLECTION: hi has not yet been flipped by globalPrepare()
      // SEMISPACE_COPIED: hi has been flipped
      // AFTER_COLLECTION: hi has been flipped
      CopySpace scannedSpace = null;
      CopySpace otherSpace = null;
      ContiguousSpaceDriver driver = null;
      ContiguousSpaceDriver otherDriver = null;

      /*
      if (hi) Log.write("\nExamining Highspace (", event);
      else    Log.write("\nExamining Lowspace (", event);
      Log.write(")");
      reportSpaces();
       */

      if ((event == SSGCspy.BEFORE_COLLECTION && !SSGCspy.hi) || 
          (event != SSGCspy.BEFORE_COLLECTION && SSGCspy.hi)) {
        scannedSpace = SSGCspy.copySpace1;
        otherSpace = SSGCspy.copySpace0;
        driver = SSGCspy.ss1Driver;
        otherDriver = SSGCspy.ss0Driver;
      } else {
        scannedSpace = SSGCspy.copySpace0;
        otherSpace = SSGCspy.copySpace1;
        driver = SSGCspy.ss0Driver;
        otherDriver = SSGCspy.ss1Driver;
      }
      gcspyGatherData(driver, scannedSpace, ss);

      if (event == SSGCspy.AFTER_COLLECTION) {
        otherDriver.zero();
        // FIXME As far as I can see, release() only resets a CopySpace's
        // cursor (and optionally zeroes or mprotects the pages); it doesn't
        // make them available to other spaces.
        // If it does release pages then need to change
        // ContiguousSpaceDriver.setRange
        Address start = otherSpace.getStart();
        otherDriver.setRange(start, start);
      }

      // -- Handle the LargeObjectSpace --
      SSGCspy.losDriver.zero();
      // FIXME This code needs to be part of the mutator, not the collector.  This hack should disappear with a refactoring. 
      ((SSGCspyMutator) ActivePlan.mutator()).getLOS().gcspyGatherData(event, SSGCspy.losDriver, false); // read fromspace
      if (event == SSGCspy.SEMISPACE_COPIED)
        ((SSGCspyMutator) ActivePlan.mutator()).getLOS().gcspyGatherData(event, SSGCspy.losDriver, true);  // read tospace

      // -- Handle the immortal space --
      SSGCspy.immortalDriver.zero();
      gcspyGatherData(SSGCspy.immortalDriver, SSGCspy.immortalSpace, immortal);

      // Transmit the data
      ServerInterpreter.stopCompensationTimer();
      driver.finish(event);
      if (event == SSGCspy.AFTER_COLLECTION) otherDriver.finish(event);
      SSGCspy.immortalDriver.finish(event);
      SSGCspy.losDriver.finish(event);

    } else {
      // Log.write("not transmitting...");
    }
    ServerInterpreter.serverSafepoint(event);
  }

  /**
   * Gather data for GCspy
   * @param driver the Driver.
   * @param space the ContiguousSpace.
   * @param bp the BumpPointer for this space.
   */
  private void gcspyGatherData(ContiguousSpaceDriver driver,
                               Space space, BumpPointer bp) {
    if (Assert.VERIFY_ASSERTIONS)
      Assert._assert(bp.getSpace() == space, "Space / BumpPointer mismatch");
    Address start = space.getStart();
    driver.zero();
    driver.setRange(start, bp.getCursor());
    LinearScan scanner = driver.getScanner();
    bp.linearScan(scanner);
  }

}
