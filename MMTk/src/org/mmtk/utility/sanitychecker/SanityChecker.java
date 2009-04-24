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
import org.mmtk.plan.Trace;
import org.mmtk.plan.Simple;
import org.mmtk.plan.TraceLocal;
import org.mmtk.policy.Space;
import org.mmtk.utility.Constants;
import org.mmtk.utility.Log;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class performs sanity checks for Simple collectors.
 */
@Uninterruptible
public final class SanityChecker implements Constants {

  /* Counters */
  public static long referenceCount;
  public static long rootReferenceCount;
  public static long danglingReferenceCount;
  public static long nullReferenceCount;
  public static long liveObjectCount;

  public static final int DEAD = -2;
  public static final int ALIVE = -1;
  public static final int UNSURE = 0;

  public static final int LOG_SANITY_DATA_SIZE = 24;

  /* Tracing */
  public Trace rootTrace;
  public Trace checkTrace;
  private final SanityDataTable sanityTable;
  private boolean preGCSanity;

  /* Local, but we only run the check trace single-threaded. */
  final SanityTraceLocal checkTraceLocal;

  /* Linear scanning */
  private final SanityLinearScan scanner = new SanityLinearScan(this);

  /****************************************************************************
   * Constants
   */
  public SanityChecker() {
    sanityTable = new SanityDataTable(Plan.sanitySpace, LOG_SANITY_DATA_SIZE);
    checkTrace = new Trace(Plan.sanitySpace);
    rootTrace = new Trace(Plan.sanitySpace);
    checkTraceLocal = new SanityTraceLocal(checkTrace, this);
  }

  /**
   * Perform any sanity checking collection phases.
   *
   * @param phaseId The id to proces
   * @return True if the phase was handled.
   */
  @NoInline
  public boolean collectionPhase(int phaseId) {
    if (phaseId == Simple.SANITY_SET_PREGC) {
      preGCSanity = true;
      return true;
    }

    if (phaseId == Simple.SANITY_SET_POSTGC) {
      preGCSanity = false;
      return true;
    }

    if (phaseId == Simple.SANITY_PREPARE) {
      Log.writeln("");
      Log.write("============================== GC Sanity Checking ");
      Log.writeln("==============================");
      Log.writeln(preGCSanity ? "Performing Pre-GC Sanity Checks..." : "Performing Post-GC Sanity Checks...");

      // Reset counters
      referenceCount = 0;
      nullReferenceCount = 0;
      liveObjectCount = 0;
      danglingReferenceCount = 0;
      rootReferenceCount = 0;

      // Clear data space
      sanityTable.acquireTable();

      // Root trace
      rootTrace.prepareNonBlocking();

      // Checking trace
      checkTrace.prepareNonBlocking();
      checkTraceLocal.prepare();
      return true;
    }

    if (phaseId == Simple.SANITY_ROOTS) {
      VM.scanning.resetThreadCounter();
      return true;
    }

    if (phaseId == Simple.SANITY_BUILD_TABLE) {
      // Trace, checking for dangling pointers
      checkTraceLocal.completeTrace();
      return true;
    }

    if (phaseId == Simple.SANITY_CHECK_TABLE) {
      // Iterate over the reachable objects.
      Address curr = sanityTable.getFirst();
      while (!curr.isZero()) {
        ObjectReference ref = SanityDataTable.getObjectReference(curr);
        int normalRC = SanityDataTable.getNormalRC(curr);
        int rootRC = SanityDataTable.getRootRC(curr);
        if (!preGCSanity) {
          int expectedRC = VM.activePlan.global().sanityExpectedRC(ref, rootRC);
          switch (expectedRC) {
          case SanityChecker.ALIVE:
          case SanityChecker.UNSURE:
            // Always ok.
            break;
          case SanityChecker.DEAD:
            // Never ok.
            Log.write("ERROR: SanityRC = ");
            Log.write(normalRC);
            Log.write(", SpaceRC = 0 ");
            SanityChecker.dumpObjectInformation(ref);
            break;
          default:
            // A mismatch in an RC space
            if (normalRC != expectedRC && VM.activePlan.global().lastCollectionFullHeap()) {
              Log.write("WARNING: SanityRC = ");
              Log.write(normalRC);
              Log.write(", SpaceRC = ");
              Log.write(expectedRC);
              Log.write(" ");
              SanityChecker.dumpObjectInformation(ref);
              break;
            }
          }
        }
        curr = sanityTable.getNext(curr);
      }

      if (!preGCSanity && VM.activePlan.global().lastCollectionFullHeap()) {
        VM.activePlan.global().sanityLinearScan(scanner);
      }
      return true;
    }

    if (phaseId == Simple.SANITY_RELEASE) {
      checkTrace.release();
      sanityTable.releaseTable();
      checkTraceLocal.release();

      Log.writeln("roots\tobjects\trefs\tnull");
      Log.write(rootReferenceCount);Log.write("\t");
      Log.write(liveObjectCount);Log.write("\t");
      Log.write(referenceCount);Log.write("\t");
      Log.writeln(nullReferenceCount);

      Log.write("========================================");
      Log.writeln("========================================");

      return true;
    }

    return false;
  }

  /**
   * Process an object during a linear scan of the heap. We have already checked
   * all objects in the table. So we are only interested in objects that are not in
   * the sanity table here. We are therefore only looking for leaks here.
   *
   * @param object The object being scanned.
   */
  public void scanProcessObject(ObjectReference object) {
    if (sanityTable.getEntry(object, false).isZero()) {
      // Is this a leak?
      int expectedRC = VM.activePlan.global().sanityExpectedRC(object, 0);

      if (expectedRC == SanityChecker.UNSURE) {
        // Probably not.
        return;
      }

      // Possibly
      Log.write("WARNING: Possible leak, SpaceRC = ");
      Log.write(expectedRC);
      Log.write(" ");
      SanityChecker.dumpObjectInformation(object);
    }
  }

  /**
   * Process an object during sanity checking, validating data,
   * incrementing counters and enqueuing if this is the first
   * visit to the object.
   *
   * @param object The object to mark.
   * @param root True If the object is a root.
   */
  public void processObject(TraceLocal trace, ObjectReference object, boolean root) {
    SanityChecker.referenceCount++;
    if (root) SanityChecker.rootReferenceCount++;

    if (object.isNull()) {
      SanityChecker.nullReferenceCount++;
      return;
    }

    if (Plan.SCAN_BOOT_IMAGE && Space.isInSpace(Plan.VM_SPACE, object)) {
      return;
    }

    // Get the table entry.
    Address tableEntry = sanityTable.getEntry(object, true);

    if (SanityDataTable.incRC(tableEntry, root)) {
      SanityChecker.liveObjectCount++;
      trace.processNode(object);
    }
  }

  /**
   * Print out object information (used for warning and error messages)
   *
   * @param object The object to dump info for.
   */
  public static void dumpObjectInformation(ObjectReference object) {
    Log.write(object);
    Log.write(" [");
    Log.write(Space.getSpaceForObject(object).getName());
    Log.write("] ");
    Log.writeln(VM.objectModel.getTypeDescriptor(object));
  }
}
