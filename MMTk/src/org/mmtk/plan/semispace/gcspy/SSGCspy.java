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

import org.mmtk.plan.GCspyPlan;
import org.mmtk.plan.Phase;
import org.mmtk.plan.TransitiveClosure;
import org.mmtk.plan.semispace.SS;
import org.mmtk.policy.CopySpace;
import org.mmtk.policy.LargeObjectSpace;
import org.mmtk.utility.gcspy.drivers.AbstractDriver;
import org.mmtk.utility.gcspy.drivers.LinearSpaceDriver;
import org.mmtk.utility.gcspy.drivers.ImmortalSpaceDriver;
import org.mmtk.utility.gcspy.drivers.TreadmillDriver;
import org.mmtk.utility.gcspy.GCspy;
import org.mmtk.utility.Log;
import org.mmtk.utility.options.Options;

import org.vmmagic.pragma.*;

/**
 * This class extends a simple semi-space collector to instrument it for
 * GCspy. <p>
 *
 * See the Jones & Lins GC book, section 2.2 for an overview of the basic
 * algorithm. This implementation also includes a large object space
 * (LOS), and an uncollected "immortal" space.<p>
 *
 * All plans make a clear distinction between <i>global</i> and
 * <i>thread-local</i> activities.  Global activities must be
 * synchronized, whereas no synchronization is required for
 * thread-local activities.  Instances of Plan map 1:1 to "kernel
 * threads" (aka CPUs).  Thus instance
 * methods allow fast, unsychronized access to Plan utilities such as
 * allocation and collection.  Each instance rests on static resources
 * (such as memory and virtual memory resources) which are "global"
 * and therefore "static" members of Plan.  This mapping of threads to
 * instances is crucial to understanding the correctness and
 * performance proprties of this plan.
 *
 * FIXME This seems to have changed
 * The order of phases and GCspy actions is important here. It is:
 *   PREPARE phase
 *      SSGCspyMutator.gcspyGatherData(SSGCspy.BEFORE_COLLECTION); // safepoint
 *      SSMutator.PREPARE // FIXME DOES NOT ss.rebind(SS.toSpace());
 *
 *   PREPARE phase
 *      SS.PREPARE // flip semispaces
 *      gcspySpace.prepare();
 *      SSGCspyCollector.gcspyGatherData(SSGCspy.BEFORE_COLLECTION);
 *      SSCollector.PREPARE // ss.rebind(SS.toSpace());
 *
 *
 *   FORWARD_FINALIZABLE phase
 *      SSCollector.FORWARD_FINALIZABLE
 *      SSGCspyCollector.gcspyGatherData(SSGCspy.SEMISPACE_COPIED);
 *
 *   RELEASE phase
 *      SSGCspyMutator.gcspyGatherData(SSGCspy.SEMISPACE_COPIED); // safepoint
 *      SSMutator.RELEASE // FIXME ss.rebind(SS.toSpace());
 *      SSGCspyMutator.gcspyGatherData(SSGCspy.AFTER_COLLECTION);
 *
 *   RELEASE phase
 *      SSCollector.RELEASE
 *      SSGCspyCollector.gcspyGatherData(SSGCspy.AFTER_COLLECTION);
 *      SS.RELEASE
 *      gcspySpace.release();
 *      SSGCspy.gcspyGatherData(); // safepoint
 *
 * Note that SSMutator has changed the point at which it rebinds toSpace
 * from PREPARE (2.4.6) to after RELEASE (3.x.x).
 *
 --Phase Collector.initiate
 --Phase Mutator.initiate-mutator
 --Phase Mutator.prepare-mutator
     SSGCspyMutator.gcspyGatherData, event=0
 --Phase Plan.prepare
 --Phase Collector.prepare
     SSGCspyCollector.gcspyGatherData, event=0
 --Phase Collector.bootimage-root
 --Phase Collector.root
 --Phase Plan.root
 --Phase Collector.start-closure
 --Phase Collector.soft-ref
 --Phase Collector.complete-closure
 --Phase Collector.weak-ref
 --Phase Collector.finalize
 --Phase Collector.complete-closure
 --Phase Collector.phantom-ref
 --Phase Collector.forward-ref
 --Phase Collector.forward-finalize
     SSGCspyCollector.gcspyGatherData, event=1
 --Phase Mutator.release-mutator
     SSGCspyMutator.gcspyGatherData, event=1
     SSGCspyMutator.gcspyGatherData, event=2
 --Phase Collector.release
     SSGCspyCollector.gcspyGatherData, event=2
 --Phase Plan.release
     SSGCspy.gcspyGatherData, event=2
 --Phase Collector.complete
 --Phase Plan.complete
 */
@Uninterruptible public class SSGCspy extends SS implements GCspyPlan {

  /****************************************************************************
   *
   * Class variables
   */

  // The events, BEFORE_COLLECTION, SEMISPACE_COPIED or AFTER_COLLECTION

  /**
   * Before a collection has started,
   * i.e. before SS.collectionPhase(SS.PREPARE,..).
   */
  static final int BEFORE_COLLECTION = 0;

  /**
   * After the semispace has been copied and the large object space has been traced
   * At this time the Large Object Space has not been swept.
   */
  static final int SEMISPACE_COPIED  = BEFORE_COLLECTION + 1;

  /**
   * The collection is complete,
   * i.e. immediately after SS.collectionPhase(SS.RELEASE,..).
   * The Large Object Space has been swept.
   */
  static final int AFTER_COLLECTION  = SEMISPACE_COPIED + 1;

  static int gcspyEvent_ = BEFORE_COLLECTION;

  // The specific drivers for this collector
  static LinearSpaceDriver ss0Driver;
  static LinearSpaceDriver ss1Driver;
  static ImmortalSpaceDriver immortalDriver;
  static TreadmillDriver losNurseryDriver;
  static TreadmillDriver losDriver;
  static TreadmillDriver plosNurseryDriver;
  static TreadmillDriver plosDriver;

  private static final boolean DEBUG = false;


  static {
    GCspy.createOptions();
  }

  /**
   * Start the server and wait if necessary.
   * This method has the following responsibilities.
   * <ol>
   * <li> Create and initialise the GCspy server by calling.
   *      <pre>server = ServerInterpreter.init(name, portNumber, verbose);</pre>
   * <li> Add each event to the ServerInterpreter
   *      <pre>server.addEvent(eventID, eventName);</pre>
   * <li> Set some general information about the server (e.g. name of the collector, build, etc).
   *      <pre>server.setGeneralInfo(info); </pre>
   * <li> Create new drivers for each component to be visualised.
   *      <pre>myDriver = new MyDriver(server, args...);</pre>
   *      Drivers extend AbstractDriver and register their spce with the
   *      ServerInterpreter. In addition to the server, drivers will take as
   *      arguments the name of the space, the MMTk space, the tilesize, and
   *      whether this space is to be the main space in the visualiser.
   * </ol>
   *
   * WARNING: allocates memory.
   * @param wait Whether to wait
   * @param port The port to talk to the GCspy client (e.g. visualiser)
   */
  @Override
  @Interruptible
  public final void startGCspyServer(int port, boolean wait) {
    GCspy.server.init("SemiSpaceServerInterpreter", port, true/*verbose*/);
    if (DEBUG) Log.writeln("SSGCspy: ServerInterpreter initialised");

    GCspy.server.addEvent(BEFORE_COLLECTION, "Before collection");
    GCspy.server.addEvent(SEMISPACE_COPIED, "Semispace copied; LOS traced");
    GCspy.server.addEvent(AFTER_COLLECTION, "After collection; LOS swept");
    GCspy.server.setGeneralInfo(
        "SSGCspy\n\nRichard Jones, October 2006\\http://www.cs.kent.ac.uk/~rej/");
    if (DEBUG) Log.writeln("SSGCspy: events added to ServerInterpreter");

    // Initialise each driver
    ss0Driver      = newLinearSpaceDriver("Semispace 0 Space", copySpace0, true);
    ss1Driver      = newLinearSpaceDriver("Semispace 1 Space", copySpace1, false);
    immortalDriver = new ImmortalSpaceDriver(
                         GCspy.server,  "Immortal Space", immortalSpace,
                         Options.gcspyTileSize.getValue(), false);
    losNurseryDriver  = newTreadmillDriver("LOS Nursery", loSpace);
    losDriver         = newTreadmillDriver("LOS", loSpace);

    if (DEBUG) Log.write("SemiServerInterpreter initialised\n");

    // Register drivers to allow immortal space to notify direct references
    immortalDriver.registerDriversForReferenceNotification(
      new AbstractDriver[] {ss0Driver, ss1Driver, immortalDriver,
                            losNurseryDriver, losDriver,
                            plosNurseryDriver, plosDriver});
    if (DEBUG) Log.writeln("SSGCspy: registered drivers");

    gcspyEvent_ = BEFORE_COLLECTION;

    // Start the server
    GCspy.server.startServer(wait);
  }

  /**
   * Create a new LinearSpaceDriver
   * TODO is this the best name or should we call it LargeObjectSpaceDriver?
   * @param name Name of the space
   * @param space The space
   * @return A new GCspy driver for this space
   */
  @Interruptible
  private LinearSpaceDriver newLinearSpaceDriver(String name, CopySpace space, boolean mainSpace) {
    // TODO What if tileSize is too small (i.e. too many tiles for GCspy buffer)
    // TODO stop the GCspy spaces in the visualiser from fluctuating in size
    // so much as we resize them.
    return new LinearSpaceDriver(GCspy.server, name, space,
            Options.gcspyTileSize.getValue(), mainSpace);
  }

  /**
   * Create a new TreadmillDriver
   * TODO is this the best name or should we call it LargeObjectSpaceDriver?
   * @param name Name of the space
   * @param space The space
   * @return A new GCspy driver for this space
   */
  @Interruptible
  private TreadmillDriver newTreadmillDriver(String name, LargeObjectSpace space) {
    return new TreadmillDriver(GCspy.server, name, space,
            Options.gcspyTileSize.getValue(), MAX_NON_LOS_COPY_BYTES, false);
  }

  /****************************************************************************
   *
   * Collection
   */

  /**
   * Perform a (global) collection phase.
   *
   * @param phaseId Collection phase
   */
  @Override
  @Inline
  public void collectionPhase(short phaseId) {
    if (DEBUG) { Log.write("--Phase Plan."); Log.writeln(Phase.getName(phaseId)); }

    if (phaseId == SSGCspy.PREPARE) {
      super.collectionPhase(phaseId);
      gcspySpace.prepare();
      return;
    }

    if (phaseId == SSGCspy.RELEASE) {
      super.collectionPhase(phaseId);
      gcspySpace.release();
      //if (primary)
       gcspyGatherData(SSGCspy.AFTER_COLLECTION);
      return;
    }

    super.collectionPhase(phaseId);
  }

  /**
   * Gather data for GCspy for the semispaces, the immortal space and the large
   * object space.
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
      Log.writeln("SSGCspy.gcspyGatherData, event=", event);
      Log.writeln("SSGCspy.gcspyGatherData, port=", GCspy.getGCspyPort());
    }

    // If port = 0 there can be no GCspy client connected
    if (GCspy.getGCspyPort() == 0)
      return;

    // This is a safepoint for the server, i.e. it is a point at which
    // the server can pause.
    // The Mutator is called after the Collector so the Mutator must set the safepoint
    if(DEBUG) Log.writeln("SSGCspy safepoint");
    GCspy.server.serverSafepoint(event);
  }

  /****************************************************************************
   *
   * Accounting
   */

  /**
   * Return the number of pages reserved for use given the pending
   * allocation.  This is <i>exclusive of</i> space reserved for
   * copying.
   *
   * @return The number of pages reserved given the pending
   * allocation, excluding space reserved for copying.
   */
  @Override
  public final int getPagesUsed() {
    return super.getPagesUsed() + gcspySpace.reservedPages();
  }


  /**
   * Report information on the semispaces
   */
  static void reportSpaces() {
    Log.write("\n  Low semispace:  ");
    Log.write(SSGCspy.copySpace0.getStart());
    Log.write(" - ");
    Log.write(SSGCspy.copySpace0.getStart()
        .plus(SSGCspy.copySpace0.getExtent()));
    Log.write("\n  High semispace: ");
    Log.write(SSGCspy.copySpace1.getStart());
    Log.write(" - ");
    Log.write(SSGCspy.copySpace1.getStart()
        .plus(SSGCspy.copySpace1.getExtent()));
    Log.flush();
  }

  /**
   * Register specialized methods.
   */
  @Override
  @Interruptible
  protected void registerSpecializedMethods() {
    super.registerSpecializedMethods();
    TransitiveClosure.registerSpecializedScan(SCAN_SS, SSGCspyTraceLocal.class);
  }
}
