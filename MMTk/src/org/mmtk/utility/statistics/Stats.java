/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */

package org.mmtk.utility.statistics;

import org.mmtk.utility.Log;
import org.mmtk.utility.options.PrintPhaseStats;
import org.mmtk.vm.Plan;
import org.mmtk.vm.Assert;

import org.vmmagic.pragma.*;

/**
 * This class implements basic statistics functionality
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 * $Id$
 */
public class Stats implements Uninterruptible {

  /****************************************************************************
   *
   * Class variables
   */

  public static final boolean GATHER_MARK_CONS_STATS = false;

  /** Maximum number of gc/mutator phases that can be counted */
  static final int MAX_PHASES = 1<<12;
  /** Maximum number of counters that can be in operation */
  static final int MAX_COUNTERS = 100;

  static private int counters = 0;
  static private Counter counter[];
  static int phase = 0;
  static private int gcCount = 0;
  static boolean gatheringStats = false;
  static PrintPhaseStats printPhaseStats;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time).  This is where key <i>global</i>
   * instances are allocated.  These instances will be incorporated
   * into the boot image by the build process.
   */
  static {
    counter = new Counter[MAX_COUNTERS];
    printPhaseStats = new PrintPhaseStats();
  }

  /**
   * Add a new counter to the set of managed counters.
   * 
   * @param ctr The counter to be added.
   */
  static void newCounter(Counter ctr) throws InterruptiblePragma {
    if (counters < (MAX_COUNTERS - 1)) {
      counter[counters++] = ctr;
    } else {
      Log.writeln("Warning: number of stats counters exceeds maximum");
    }
  }
  
  /**
   * Start a new GC phase.  This means notifying each counter of the
   * phase change.
   */
  public static void startGC() {
    gcCount++;
    if (!gatheringStats) return;
    if (phase < MAX_PHASES - 1) {
      for (int c = 0; c < counters; c++) {
	counter[c].phaseChange(phase);
      }
      phase++;
    } else {
      Log.writeln("Warning: number of GC phases exceeds MAX_PHASES");
    }
  }

  /**
   * End a GC phase.  This means notifying each counter of the phase
   * change.
   */
  public static void endGC() {
    if (!gatheringStats) return;
    if (phase < MAX_PHASES - 1) {
      for (int c = 0; c < counters; c++) {
	counter[c].phaseChange(phase);
      }
      phase++;
    } else {
      Log.writeln("Warning: number of GC phases exceeds MAX_PHASES");
    } 
  }

  /**
   * Start all implicitly started counters (i.e. those for whom
   * <code>start == true</code>).
   */
  public static void startAll() {
    if (gatheringStats) {
      Log.writeln("Error: calling Stats.startAll() while stats running");
      Log.writeln("       verbosity > 0 and the harness mechanism may be conflicitng");
      if (Assert.VERIFY_ASSERTIONS) Assert._assert(false);
    }
    gatheringStats = true;
    for (int c = 0; c < counters; c++) {
      if (counter[c].getStart()) 
	counter[c].start();
    }
  }

  /**
   * Stop all counters
   */
  public static void stopAll() {
    for (int c = 0; c < counters; c++) {
      if (counter[c].getStart()) 
	counter[c].stop();
    }
    gatheringStats = false;
  }

  /**
   * Print out statistics
   */
  public static void printStats() {
    if (printPhaseStats.getValue())
      printPhases();
    printTotals();
  }
  
  /**
   * Print out statistics totals
   */
  public static void printTotals() {
    Log.writeln("============================ MMTk Statistics Totals ============================");
    printColumnNames();
    Log.write((phase/2)+1); Log.write("\t");
    for (int c = 0; c < counters; c++) {
      if (counter[c].mergePhases()) {
	counter[c].printTotal(); Log.write("\t");
      } else {
	counter[c].printTotal(true); Log.write("\t");
        counter[c].printTotal(false); Log.write("\t");
      }
    }
    Log.writeln();
    Log.write("Total time: "); 
    Plan.totalTime.printTotal(); Log.writeln(" ms");
    Log.writeln("------------------------------ End MMTk Statistics -----------------------------");
  }

  /**
   * Print out statistics for each mutator/gc phase
   */
  public static void printPhases() {
    Log.writeln("--------------------- MMTk Statistics Per GC/Mutator Phase ---------------------");
    printColumnNames();
    for (int p = 0; p <= phase; p += 2) {
      Log.write((p/2)+1); Log.write("\t");
      for (int c = 0; c < counters; c++) {
        if (counter[c].mergePhases()) {
	  counter[c].printCount(p); Log.write("\t");
        } else {
	  counter[c].printCount(p); Log.write("\t");
          counter[c].printCount(p+1); Log.write("\t");
        }
      }
      Log.writeln();
    }
  }

  /**
   * Print out statistics column names
   */
  private static void printColumnNames() {
    Log.write("GC\t");
    for (int c = 0; c < counters; c++) {
      if (counter[c].mergePhases()) {
	Log.write(counter[c].getName());
	Log.write("\t");
      } else {
	Log.write(counter[c].getName());
	Log.write(".mu\t");
	Log.write(counter[c].getName());
	Log.write(".gc\t");
      }
    }
    Log.writeln();
  }

  /** @return The GC count (inclusive of any in-progress GC) */
  public static int gcCount() { return gcCount; }

  /** @return True if currently gathering stats */
  public static boolean gatheringStats() { return gatheringStats; }
}
