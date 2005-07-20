/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.plan.semispace.gcspy;

import org.mmtk.plan.semispace.SS;
import org.mmtk.plan.semispace.SSLocal;
import org.mmtk.policy.Space;
import org.mmtk.policy.CopySpace;
import org.mmtk.utility.alloc.BumpPointer;
import org.mmtk.utility.alloc.LinearScan;
import org.mmtk.utility.gcspy.drivers.ContiguousSpaceDriver;
import org.mmtk.utility.gcspy.GCspy;

import org.mmtk.vm.Assert;
import org.mmtk.vm.gcspy.ServerInterpreter;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class extends a simple semi-space collector to instrument it for
 * GCspy. It makes use of Daniel Frampton's LinearScan patches to allow
 * CopySpaces to be scanned linearly (rather than recording allocations
 * with an ObjectMap as in prior versions.<p>
 *
 * See the Jones & Lins GC book, section 2.2 for an overview of the basic
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
 * @author Daniel Frampton
 * @author Robin Garner
 * @author <a href="http://www.cs.ukc.ac.uk/~rej">Richard Jones</a>
 *
 * @version $Revision$
 * @date $Date$
 */
public class SSGCspyLocal extends SSLocal implements Uninterruptible {

  /****************************************************************************
   *
   * Data gathering
   */

  /**
   * Perform a (local) collection phase.
   */
  public final void collectionPhase(int phaseId, boolean participating,
                                    boolean primary)
    throws InlinePragma {
    if (phaseId == SS.PREPARE) {
      if (primary) gcspyGatherData(SSGCspy.BEFORE_COLLECTION);
      super.collectionPhase(phaseId, participating,primary);
      return;
    }

    if (phaseId == SS.RELEASE) {
      gcspyGatherData(SSGCspy.SEMISPACE_COPIED);
      super.collectionPhase(phaseId, participating, primary);
      gcspyGatherData(SSGCspy.AFTER_COLLECTION);
      return;
    }

    super.collectionPhase(phaseId, participating, primary);
  }

  /**
   * Gather data for GCspy
   * This method sweeps the semispace under consideration to gather data.
   * Used space can obviously be discovered in constant time simply by comparing
   * the start and the end addresses of the semispace, but per-object
   * information needs to be gathered by sweeping through the space.
   *
   * @param event The event, either BEFORE_COLLECTION, SEMISPACE_COPIED or
   *              AFTER_COLLECTION
   */
  private void gcspyGatherData(int event) {
    // Port = 0 means no gcspy
    if (GCspy.getGCspyPort() == 0)
      return;

    if (ServerInterpreter.shouldTransmit(event)) {
      ServerInterpreter.startCompensationTimer();

      // -- Handle the semispace collector --
      // BEFORE_COLLECTION: hi has not yet been flipped by globalPrepare()
      // SEMISPACE_COPIED:  hi has been flipped
      // AFTER_COLLECTION:  hi has been flipped
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

      if (SSGCspy.hi) {
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
      los.gcspyGatherData(event, SSGCspy.losDriver, false); // read fromspace
      if (event == SSGCspy.SEMISPACE_COPIED)
        los.gcspyGatherData(event, SSGCspy.losDriver, true);  // read tospace

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
      //Log.write("not transmitting...");
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
