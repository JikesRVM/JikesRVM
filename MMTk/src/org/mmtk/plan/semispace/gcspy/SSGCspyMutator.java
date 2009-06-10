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
import org.mmtk.plan.semispace.SSMutator;
import org.mmtk.policy.CopySpace;
import org.mmtk.policy.Space;
import org.mmtk.policy.ImmortalLocal;
import org.mmtk.utility.Log;
import org.mmtk.utility.alloc.BumpPointer;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.gcspy.GCspy;
import org.mmtk.utility.gcspy.drivers.LinearSpaceDriver;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements <i>per-mutator thread</i> behavior and state for the
 * <i>SSGCspy</i> plan.
 *
 * See {@link SSGCspy} for an overview of the GC-spy mechanisms.
 * <p>
 *
 * @see SSMutator
 * @see SSGCspy
 * @see SSGCspyCollector
 * @see org.mmtk.plan.StopTheWorldMutator
 * @see org.mmtk.plan.MutatorContext
 */
@Uninterruptible public class SSGCspyMutator extends SSMutator {

  /*****************************************************************************
   * Instance fields
   */

  private static final boolean DEBUG = false;

  private static final boolean LOS_TOSPACE = true;    // gather from tospace
  private static final boolean LOS_FROMSPACE = false; // gather from fromspace

  /** Per-mutator allocator into GCspy's space */
  private BumpPointer gcspy = new ImmortalLocal(SSGCspy.gcspySpace);



  /*****************************************************************************
   *
   * Mutator-time allocation
   */

  /**
   * Allocate space (for an object)
   *
   * @param bytes The size of the space to be allocated (in bytes)
   * @param align The requested alignment.
   * @param offset The alignment offset.
   * @param allocator The allocator number to be used for this allocation
   * @param site Allocation site
   * @return The address of the first byte of the allocated region
   */
  @Inline
  public Address alloc(int bytes, int align, int offset, int allocator, int site) {
    if (allocator == SSGCspy.ALLOC_GCSPY)
      return gcspy.alloc(bytes, align, offset);
    else
      return super.alloc(bytes, align, offset, allocator, site);
  }

  /**
   * Perform post-allocation actions. For many allocators none are required.
   *
   * @param object The newly allocated object
   * @param typeRef The type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   * @param allocator The allocator number to be used for this allocation
   */
  @Inline
  public void postAlloc(ObjectReference object, ObjectReference typeRef,
                        int bytes, int allocator) {
    if (allocator == SSGCspy.ALLOC_GCSPY)
      SSGCspy.gcspySpace.initializeHeader(object);
    else
      super.postAlloc(object, typeRef, bytes, allocator);
  }

  /**
   * Return the allocator instance associated with a space
   * <code>space</code>, for this plan instance.
   *
   * @param space The space for which the allocator instance is desired.
   * @return The allocator instance associated with this plan instance
   * which is allocating into <code>space</code>, or <code>null</code>
   * if no appropriate allocator can be established.
   */
  public Allocator getAllocatorFromSpace(Space space) {
    if (space == SSGCspy.gcspySpace) return gcspy;
    return super.getAllocatorFromSpace(space);
  }

  /*****************************************************************************
   *
   * Collection
   */

  /**
   * Perform a per-mutator collection phase.
   * Before a collection, we need to discover
   * <ul>
   * <li>the tospace objects copied by the collector in the last GC cycle
   * <li>the ojects allocated since by the mutator.
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
  @Inline
  public final void collectionPhase(short phaseId, boolean primary) {
    if (DEBUG) { Log.write("--Phase Mutator."); Log.writeln(Phase.getName(phaseId)); }

    // TODO do we need to worry any longer about primary??
    if (phaseId == SSGCspy.PREPARE) {
      //if (primary)
        gcspyGatherData(SSGCspy.BEFORE_COLLECTION);
      super.collectionPhase(phaseId, primary);
      return;
    }

    if (phaseId == SSGCspy.RELEASE) {
      //if (primary)
        gcspyGatherData(SSGCspy.SEMISPACE_COPIED);
      super.collectionPhase(phaseId, primary);
      //if (primary)
        gcspyGatherData(SSGCspy.AFTER_COLLECTION);
      return;
    }

    super.collectionPhase(phaseId, primary);
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
      Log.writeln("SSGCspyMutator.gcspyGatherData, event=", event);
      Log.writeln("SSGCspyMutator.gcspyGatherData, port=", GCspy.getGCspyPort());
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
          Log.write("\nMutator Examining Lowspace (event ", event);
        else
          Log.write("\nMutator Examining Highspace (event ", event);
        Log.write(")");
        SSGCspy.reportSpaces(); Log.writeln();
      }

      if (event == SSGCspy.BEFORE_COLLECTION) {
        // Before the flip
        // Mutator has not rebound toSpace yet
        GCspy.server.startCompensationTimer();

        // -- Handle the semispaces
        // Here I need to scan newly allocated objects
        if (DEBUG) {
          //debugSpaces(SSGCspy.fromSpace());
          debugSpaces(SSGCspy.toSpace());
          Log.write("SSGCspyMutator.gcspyGatherData reset, gather and transmit driver ");
          //Log.writeln(SSGCspy.fromSpace().getName());
          Log.writeln(SSGCspy.toSpace().getName());
        }
        //ss.gcspyGatherData(fromSpaceDriver(), SSGCspy.fromSpace());
        ss.gcspyGatherData(toSpaceDriver(), SSGCspy.toSpace());

        // -- Handle the immortal space --
        gatherImmortal(event);

        // -- Handle the LOSes

        // reset, collect and scan los data for the nursery and tospace
        SSGCspy.losNurseryDriver.resetData();
        los.gcspyGatherData(event, SSGCspy.losNurseryDriver);
        SSGCspy.losDriver.resetData();
        los.gcspyGatherData(event, SSGCspy.losDriver, LOS_TOSPACE);

        // transmit the data
        GCspy.server.stopCompensationTimer();
        //fromSpaceDriver().transmit(event);
        toSpaceDriver().transmit(event);
        SSGCspy.immortalDriver.transmit(event);
        SSGCspy.losNurseryDriver.transmit(event);
        SSGCspy.losDriver.transmit(event);
        SSGCspy.plosNurseryDriver.transmit(event);
        SSGCspy.plosDriver.transmit(event);

        // As this follows Collector.gcspyGatherData, I'll safepoint here
        // This is a safepoint for the server, i.e. it is a point at which
        // the server can pause.
        GCspy.server.serverSafepoint(event);
      } else if (event == SSGCspy.SEMISPACE_COPIED) {
        // We have flipped
        // toSpace still has not been rebound

        // -- Handle the semispaces
        if (DEBUG) {
          //debugSpaces(SSGCspy.toSpace());
          debugSpaces(SSGCspy.fromSpace());
          Log.writeln("SSGCspyMutator.gcspyGatherData: do nothing");
        }

        // -- Handle the immortal space --
        GCspy.server.startCompensationTimer();
        gatherImmortal(event);

        // reset, scan and send the los for the nursery and tospace
        // and fromspace as well if full heap collection
        SSGCspy.losNurseryDriver.resetData();
        los.gcspyGatherData(event, SSGCspy.losNurseryDriver);
        SSGCspy.losDriver.resetData();
        los.gcspyGatherData(event, SSGCspy.losDriver, LOS_FROMSPACE);
        los.gcspyGatherData(event, SSGCspy.losDriver, LOS_TOSPACE);

        // transmit
        GCspy.server.stopCompensationTimer();
        SSGCspy.immortalDriver.transmit(event);
        SSGCspy.losNurseryDriver.transmit(event);
        SSGCspy.losDriver.transmit(event);
        SSGCspy.plosNurseryDriver.transmit(event);
        SSGCspy.plosDriver.transmit(event);

        // As this follows Collector.gcspyGatherData, I'll safepoint here
        // This is a safepoint for the server, i.e. it is a point at which
        // the server can pause.
        GCspy.server.serverSafepoint(event);
      } else if (event == SSGCspy.AFTER_COLLECTION) {
        // We have flipped
        // And toSpace has been rebound

        GCspy.server.startCompensationTimer();

        // -- Handle the semispaces
        if (DEBUG) debugSpaces(SSGCspy.toSpace());

        // -- Handle the immortal space --
        gatherImmortal(event);

        // -- Handle the LOSes

        // reset, scan and send the los
        SSGCspy.losNurseryDriver.resetData();
        SSGCspy.losDriver.resetData();
        // no need to scan empty nursery
        los.gcspyGatherData(event, SSGCspy.losDriver, LOS_TOSPACE);

        //transmit
        GCspy.server.stopCompensationTimer();
        SSGCspy.immortalDriver.transmit(event);
        SSGCspy.losNurseryDriver.transmit(event);
        SSGCspy.losDriver.transmit(event);
        SSGCspy.plosNurseryDriver.transmit(event);
        SSGCspy.plosDriver.transmit(event);

        // Reset fromspace
        if (DEBUG) {
          Log.write("SSGCspyMutator.gcspyGatherData: reset and zero range for driver ");
          Log.write(SSGCspy.toSpace().getName());
        }
      }

    }
    // else Log.write("not transmitting...");
  }

  /**
   * Gather data for the immortal space
   * @param event
   * The event, either BEFORE_COLLECTION, SEMISPACE_COPIED or
   *          AFTER_COLLECTION
   */
  private void gatherImmortal(int event) {
    // We want to do this at every GCspy event
    if (DEBUG) {
      Log.write("SSGCspyMutator.gcspyGatherData: gather data for immortal space ");
      Log.write(SSGCspy.immortalSpace.getStart()); Log.writeln("-",immortal.getCursor());
    }
    SSGCspy.immortalDriver.resetData();
    immortal.gcspyGatherData(SSGCspy.immortalDriver);
    if (DEBUG) Log.writeln("Finished immortal space.");
  }

  /**
   * Debugging info for the semispaces
   * @param scannedSpace the space to output debug for.
   */
  private void debugSpaces(CopySpace scannedSpace) {
    Log.write("SSGCspyMutator.gcspyGatherData: gather data for active semispace ");
    Log.write(scannedSpace.getStart()); Log.write("-",ss.getCursor()); Log.flush();
    Log.write(". The space is: "); Log.writeln(ss.getSpace().getName());
    Log.write("scannedSpace is "); Log.writeln(scannedSpace.getName());
    Log.write("The range is "); Log.write(ss.getSpace().getStart());
    Log.write(" to "); Log.writeln(ss.getCursor());
    SSGCspy.reportSpaces();
  }

  /** @return the driver for toSpace */
  private LinearSpaceDriver toSpaceDriver() {
    return SSGCspy.hi ? SSGCspy.ss1Driver : SSGCspy.ss0Driver;
  }

}
