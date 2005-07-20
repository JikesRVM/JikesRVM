/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan;

import org.mmtk.policy.Space;
import org.mmtk.utility.Constants;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.Log;
import org.mmtk.utility.options.*;
import org.mmtk.utility.statistics.Stats;
import org.mmtk.utility.statistics.Timer;
import org.mmtk.vm.Scanning;

import org.mmtk.vm.Assert;
import org.mmtk.vm.Memory;

import org.vmmagic.pragma.*;

/**
 * This abstract class implments the core functionality for
 * stop-the-world collectors.  Stop-the-world collectors should
 * inherit from this class.<p>
 *
 * This class defines the collection phases, and provides base
 * level implementations of them.  Subclasses should provide
 * implementations for the spaces that they introduce, and
 * delegate up the class hierarchy.<p>
 *
 * For details of the split between global and thread-local operations
 * @see org.mmtk.plan.Plan
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
public abstract class StopTheWorld extends Plan
  implements Uninterruptible, Constants {
  /****************************************************************************
   * Constants
   */

  /* Shared Timers */
  private static final Timer refTypeTime  = new Timer("refType",  false, true);
  private static final Timer scanTime     = new Timer("scan",     false, true);
  private static final Timer finalizeTime = new Timer("finalize", false, true);

  /* Phases */
  public static final int INITIATE            = new SimplePhase("initiate", null,                 Phase.GLOBAL_FIRST, true).getId();
  public static final int PREPARE             = new SimplePhase("prepare",                        Phase.GLOBAL_FIRST, true).getId();
  public static final int PRECOPY             = new SimplePhase("precopy",                        Phase.LOCAL_ONLY        ).getId();
  public static final int ROOTS               = new SimplePhase("root",                           Phase.GLOBAL_LAST       ).getId();
  public static final int START_CLOSURE       = new SimplePhase("start-closure",    scanTime,     Phase.LOCAL_ONLY        ).getId();
  public static final int SOFT_REFS           = new SimplePhase("soft-ref",         refTypeTime,  Phase.LOCAL_ONLY        ).getId();
  public static final int COMPLETE_CLOSURE    = new SimplePhase("complete-closure", scanTime,     Phase.LOCAL_ONLY        ).getId();
  public static final int WEAK_REFS           = new SimplePhase("weak-ref",         refTypeTime,  Phase.LOCAL_ONLY        ).getId();
  public static final int FINALIZABLE         = new SimplePhase("finalize",         finalizeTime, Phase.LOCAL_ONLY        ).getId();
  public static final int WEAK_TRACK_REFS     = new SimplePhase("weak-track-ref",   refTypeTime,  Phase.PLACEHOLDER       ).getId();
  public static final int PHANTOM_REFS        = new SimplePhase("phantom-ref",      refTypeTime,  Phase.LOCAL_ONLY        ).getId();
  public static final int FORWARD             = new SimplePhase("forward",                        Phase.PLACEHOLDER       ).getId();
  public static final int FORWARD_REFS        = new SimplePhase("forward-ref",      refTypeTime,  Phase.LOCAL_ONLY        ).getId();
  public static final int FORWARD_FINALIZABLE = new SimplePhase("forward-finalize", finalizeTime, Phase.LOCAL_ONLY        ).getId();
  public static final int RELEASE             = new SimplePhase("release",                        Phase.GLOBAL_LAST,  true).getId();
  public static final int COMPLETE            = new SimplePhase("complete",  null,                Phase.GLOBAL_LAST,  true).getId();

  /**
   * Start the collection, including preparation for any collected spaces.
   */
  private static final int initPhase = new ComplexPhase("init", new int[] {
      INITIATE,
      PREPARE,
      PRECOPY}).getId();

  /**
   * Perform the initial determination of liveness from the roots.
   */
  private static final int rootClosurePhase = new ComplexPhase("initial-closure", null, new int[] {
      ROOTS,
      START_CLOSURE}).getId();

  /**
   *  Complete closure including referece types and finalizable objects.
   */
  private static final int refTypeClosurePhase = new ComplexPhase("refType-closure", null, new int[] {
      SOFT_REFS,    COMPLETE_CLOSURE,
      WEAK_REFS,
      FINALIZABLE,  COMPLETE_CLOSURE,
      WEAK_TRACK_REFS,
      PHANTOM_REFS}).getId();

  /**
   * Ensure that all references in the system are correct.
   */
  private static final int forwardPhase = new ComplexPhase("forward-all", null, new int[] {
      /* Finish up */
      FORWARD,
      FORWARD_REFS,
      FORWARD_FINALIZABLE}).getId();

  /**
   * The collection scheme - this is a small tree of complex phases.
   */
  private static final int finishPhase = new ComplexPhase("finish", new int[] {
      RELEASE,
      COMPLETE}).getId();

  /**
   * This is the phase that is executed to perform a collection.
   */
  public Phase collection = new ComplexPhase("collection", null, new int[] {
      initPhase,
      rootClosurePhase,
      refTypeClosurePhase,
      forwardPhase,
      finishPhase});

  /****************************************************************************
   * Collection
   */

  public void collectionPhase(int phaseId) throws InlinePragma {
    if (phaseId == INITIATE) {
      if (Stats.gatheringStats()) {
        Stats.startGC();
        printPreStats();
      }
      Plan.setGCStatus(GC_PREPARE);
      return;
    }

    if (phaseId == PREPARE) {
      loSpace.prepare();
      immortalSpace.prepare();
      Memory.globalPrepareVMSpace();
      return;
    }

    if (phaseId == ROOTS) {
      Scanning.resetThreadCounter();
      Plan.setGCStatus(GC_PROPER);
      return;
    }

    if (phaseId == RELEASE) {
      loSpace.release();
      immortalSpace.release();
      Memory.globalReleaseVMSpace();
      return;
    }

    if (phaseId == COMPLETE) {
      Plan.setGCStatus(NOT_IN_GC);
      if (Stats.gatheringStats()) {
        Stats.endGC();
        printPostStats();
      }
      return;
    }

    Log.write("Global phase "); Log.write(Phase.getName(phaseId)); 
    Log.writeln(" not handled.");
    Assert.fail("Global phase not handled!");
  }

  /**
   * Print out statistics at the start of a GC
   */
  public void printPreStats() {
    if ((Options.verbose.getValue() == 1) ||
        (Options.verbose.getValue() == 2)) {
      Log.write("[GC "); Log.write(Stats.gcCount());
      if (Options.verbose.getValue() == 1) {
        Log.write(" Start ");
        Plan.totalTime.printTotalSecs();
        Log.write(" s");
      } else {
        Log.write(" Start ");
        Plan.totalTime.printTotalMillis();
        Log.write(" ms");
      }
      Log.write("   ");
      Log.write(Conversions.pagesToKBytes(getPagesUsed()));
      Log.write("KB ");
      Log.flush();
    }
    if (Options.verbose.getValue() > 2) {
      Log.write("Collection "); Log.write(Stats.gcCount());
      Log.write(":        ");
      printUsedPages();
      Log.write("  Before Collection: ");
      Space.printUsageMB();
      if (Options.verbose.getValue() >= 4) {
        Log.write("                     ");
        Space.printUsagePages();
      }
    }
  }

  /**
   * Print out statistics at the end of a GC
   */
  public final void printPostStats() {
    if ((Options.verbose.getValue() == 1) ||
        (Options.verbose.getValue() == 2)) {
      Log.write("-> ");
      Log.writeDec(Conversions.pagesToBytes(getPagesUsed()).toWord().rshl(10));
      Log.write(" KB   ");
      if (Options.verbose.getValue() == 1) {
        totalTime.printLast();
        Log.writeln(" ms]");
      } else {
        Log.write("End ");
        totalTime.printTotal();
        Log.writeln(" ms]");
      }
    }
    if (Options.verbose.getValue() > 2) {
      Log.write("   After Collection: ");
      Space.printUsageMB();
      if (Options.verbose.getValue() >= 4) {
          Log.write("                     ");
          Space.printUsagePages();
      }
      Log.write("                     ");
      printUsedPages();
      Log.write("    Collection time: ");
      totalTime.printLast();
      Log.writeln(" seconds");
    }
  }

  public final void printUsedPages() {
    Log.write("reserved = ");
    Log.write(Conversions.pagesToMBytes(getPagesReserved()));
    Log.write(" MB (");
    Log.write(getPagesReserved());
    Log.write(" pgs)");
    Log.write("      total = ");
    Log.write(Conversions.pagesToMBytes(getTotalPages()));
    Log.write(" MB (");
    Log.write(getTotalPages());
    Log.write(" pgs)");
    Log.writeln();
  }
}
