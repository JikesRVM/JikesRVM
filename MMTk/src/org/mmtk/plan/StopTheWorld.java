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
import org.mmtk.utility.sanitychecker.SanityChecker;
import org.mmtk.utility.statistics.Stats;
import org.mmtk.utility.statistics.Timer;
import org.mmtk.vm.VM;


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
 * @author Steve Blackburn
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
  private static final Timer refTypeTime = new Timer("refType", false, true);
  private static final Timer scanTime = new Timer("scan", false, true);
  private static final Timer finalizeTime = new Timer("finalize", false, true);

  /* Phases */
  public static final int INITIATE            = new SimplePhase("initiate", null,                 Phase.GLOBAL_FIRST  ).getId();
  public static final int INITIATE_MUTATOR    = new SimplePhase("initiate-mutator",               Phase.MUTATOR_ONLY  ).getId();
  public static final int PREPARE             = new SimplePhase("prepare",                        Phase.GLOBAL_FIRST  ).getId();
  public static final int PREPARE_MUTATOR     = new SimplePhase("prepare-mutator",                Phase.MUTATOR_ONLY  ).getId();
  public static final int PRECOPY             = new SimplePhase("precopy",                        Phase.COLLECTOR_ONLY).getId();
  public static final int ROOTS               = new SimplePhase("root",                           Phase.GLOBAL_LAST       ).getId();
  public static final int BOOTIMAGE_ROOTS     = new SimplePhase("bootimage-root",                 Phase.COLLECTOR_ONLY).getId();
  public static final int START_CLOSURE       = new SimplePhase("start-closure",    scanTime,     Phase.COLLECTOR_ONLY).getId();
  public static final int SOFT_REFS           = new SimplePhase("soft-ref",         refTypeTime,  Phase.COLLECTOR_ONLY).getId();
  public static final int COMPLETE_CLOSURE    = new SimplePhase("complete-closure", scanTime,     Phase.COLLECTOR_ONLY).getId();
  public static final int WEAK_REFS           = new SimplePhase("weak-ref",         refTypeTime,  Phase.COLLECTOR_ONLY).getId();
  public static final int FINALIZABLE         = new SimplePhase("finalize",         finalizeTime, Phase.COLLECTOR_ONLY).getId();
  public static final int WEAK_TRACK_REFS     = new SimplePhase("weak-track-ref",   refTypeTime,  Phase.PLACEHOLDER       ).getId();
  public static final int PHANTOM_REFS        = new SimplePhase("phantom-ref",      refTypeTime,  Phase.COLLECTOR_ONLY).getId();
  public static final int FORWARD             = new SimplePhase("forward",                        Phase.PLACEHOLDER       ).getId();
  public static final int FORWARD_REFS        = new SimplePhase("forward-ref",      refTypeTime,  Phase.COLLECTOR_ONLY).getId();
  public static final int FORWARD_FINALIZABLE = new SimplePhase("forward-finalize", finalizeTime, Phase.COLLECTOR_ONLY).getId();
  public static final int RELEASE             = new SimplePhase("release",                        Phase.GLOBAL_LAST   ).getId();
  public static final int RELEASE_MUTATOR     = new SimplePhase("release-mutator",                Phase.MUTATOR_ONLY  ).getId();
  public static final int COMPLETE            = new SimplePhase("complete",  null,                Phase.GLOBAL_LAST   ).getId();

  /* Sanity placeholder */
  public static final int SANITY_PLACEHOLDER  = new SimplePhase("sanity-placeholder", null,       Phase.PLACEHOLDER       ).getId();

  /* Sanity phases */
  public static final int SANITY_PREPARE      = new SimplePhase("sanity-prepare",     null,       Phase.GLOBAL_FIRST      ).getId();
  public static final int SANITY_ROOTS        = new SimplePhase("sanity-roots",       null,       Phase.GLOBAL_LAST       ).getId();
  public static final int SANITY_CHECK        = new SimplePhase("sanity",             null,       Phase.COLLECTOR_ONLY        ).getId();
  public static final int SANITY_RELEASE      = new SimplePhase("sanity-release",     null,       Phase.GLOBAL_LAST       ).getId();
  public static final int SANITY_FORWARD      = new SimplePhase("sanity-forward",     null,       Phase.COLLECTOR_ONLY        ).getId();

  /* Sanity forwarding piggy-back */
  private static final int sanityForwardPhase = new ComplexPhase("sanity-forward-cf", null, new int[] {
      FORWARD,
      SANITY_FORWARD,
  }).getId();

  /* Sanity check phase sequence */
  private static final int sanityPhase = new ComplexPhase("sanity-check", null, new int[] {
      SANITY_PREPARE,
      SANITY_ROOTS,
      SANITY_CHECK,
      SANITY_RELEASE
  }).getId();

  /**
   * Start the collection, including preparation for any collected spaces.
   */
  protected static final int initPhase = new ComplexPhase("init", new int[] {
      INITIATE,
      INITIATE_MUTATOR,
      SANITY_PLACEHOLDER,
      }).getId();

  /**
   * Perform the initial determination of liveness from the roots.
   */
  protected static final int rootClosurePhase = new ComplexPhase("initial-closure", null, new int[] {
      PREPARE,
      PREPARE_MUTATOR,
      PRECOPY,
      BOOTIMAGE_ROOTS,
      ROOTS,
      START_CLOSURE}).getId();

  /**
   * Complete closure including reference types and finalizable objects.
   */
  protected static final int refTypeClosurePhase = new ComplexPhase("refType-closure", null, new int[] {
      SOFT_REFS,    COMPLETE_CLOSURE,
      WEAK_REFS,
      FINALIZABLE,  COMPLETE_CLOSURE,
      WEAK_TRACK_REFS,
          PHANTOM_REFS }).getId();

  /**
   * Ensure that all references in the system are correct.
   */
  protected static final int forwardPhase = new ComplexPhase("forward-all", null, new int[] {
      /* Finish up */
      FORWARD,
      FORWARD_REFS,
      FORWARD_FINALIZABLE}).getId();

  /**
   * Complete closure including reference types and finalizable objects.
   */
  protected static final int completeClosurePhase = new ComplexPhase("refType-closure", null, new int[] {
      RELEASE_MUTATOR,
      RELEASE,
      }).getId();


  /**
   * The collection scheme - this is a small tree of complex phases.
   */
  protected static final int finishPhase = new ComplexPhase("finish", new int[] {
      SANITY_PLACEHOLDER,
      COMPLETE}).getId();

  /**
   * This is the phase that is executed to perform a collection.
   */
  public ComplexPhase collection = new ComplexPhase("collection", null, new int[] {
      initPhase,
      rootClosurePhase,
      refTypeClosurePhase,
      forwardPhase,
      completeClosurePhase,
      finishPhase});

  /* Basic GC sanity checker */
  private SanityChecker sanityChecker = new SanityChecker();

  /****************************************************************************
   * Collection
   */

  /**
   * The boot method is called early in the boot process before any
   * allocation.
   */
  public void postBoot() throws InterruptiblePragma {
    super.postBoot();

    if (Options.sanityCheck.getValue()) {
      if (getSanityChecker() == null || 
          VM.activePlan.collector().getSanityChecker() == null) {
        Log.writeln("Collector does not support sanity checking!");
      } else {
        Log.writeln("Collection sanity checking enabled.");
        collection.replacePhase(SANITY_PLACEHOLDER, sanityPhase);
        collection.replacePhase(FORWARD, sanityForwardPhase);
      }
    }
  }

  /**
   * @return Return the current sanity checker.
   */
  public SanityChecker getSanityChecker() {
    return sanityChecker;
  }

  /**
   * Perform a (global) collection phase.
   * 
   * @param phaseId The unique of the phase to perform. 
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
      VM.memory.globalPrepareVMSpace();
      return;
    }

    if (phaseId == ROOTS) {
      VM.scanning.resetThreadCounter();
      Plan.setGCStatus(GC_PROPER);
      return;
    }

    if (phaseId == RELEASE) {
      loSpace.release();
      immortalSpace.release();
      VM.memory.globalReleaseVMSpace();
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

    if (Options.sanityCheck.getValue() &&
        getSanityChecker().collectionPhase(phaseId)) {
      return;
    }

    Log.write("Global phase "); Log.write(Phase.getName(phaseId)); 
    Log.writeln(" not handled.");
    VM.assertions.fail("Global phase not handled!");
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
