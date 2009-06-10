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
package org.mmtk.utility.statistics;

import org.mmtk.plan.Plan;
import org.mmtk.utility.Log;
import org.mmtk.utility.options.Options;
import org.mmtk.utility.options.PrintPhaseStats;
import org.mmtk.utility.options.XmlStats;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;

/**
 * This class implements basic statistics functionality
 */
@Uninterruptible public class Stats {

  /****************************************************************************
   *
   * Class variables
   */

  public static final boolean GATHER_MARK_CONS_STATS = false;

  /** Maximum number of gc/mutator phases that can be counted */
  static final int MAX_PHASES = 1 << 12;
  /** Maximum number of counters that can be in operation */
  static final int MAX_COUNTERS = 100;

  private static int counters = 0;
  private static Counter[] counter;
  static int phase = 0;
  private static int gcCount = 0;
  static boolean gatheringStats = false;
  static boolean exceededPhaseLimit = false;

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
    Options.printPhaseStats = new PrintPhaseStats();
    Options.xmlStats = new XmlStats();
  }

  /**
   * Add a new counter to the set of managed counters.
   *
   * @param ctr The counter to be added.
   */
  @Interruptible
  static void newCounter(Counter ctr) {
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
    } else if (!exceededPhaseLimit) {
      Log.writeln("Warning: number of GC phases exceeds MAX_PHASES");
      exceededPhaseLimit = true;
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
    } else if (!exceededPhaseLimit) {
      Log.writeln("Warning: number of GC phases exceeds MAX_PHASES");
      exceededPhaseLimit = true;
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
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    }
    gatheringStats = true;
    for (int c = 0; c < counters; c++) {
      if (counter[c].getStart())
        counter[c].start();
    }
    if (Options.xmlStats.getValue()) {
      Xml.begin();
      Xml.openTag("mmtk-stats");
      Xml.end();
    }
  }

  /**
   * Stop all counters
   */
  @Interruptible
  public static void stopAll() {
    stopAllCounters();
    Stats.printStats();
    if (Options.xmlStats.getValue()) {
      Xml.begin();
      Xml.closeTag("mmtk-stats");
      Xml.end();
    }
  }

  /**
   * Stop all counters
   */
  private static void stopAllCounters() {
    for (int c = 0; c < counters; c++) {
      if (counter[c].getStart())
        counter[c].stop();
    }
    gatheringStats = false;
  }

  @Interruptible
  public static void printStats() {
    if (exceededPhaseLimit) {
      Log.writeln("Warning: number of GC phases exceeds MAX_PHASES.  Statistics are truncated.");
    }
    if (Options.xmlStats.getValue())
      printStatsXml();
    else
      printStatsPlain();
  }

  /**
   * Print out statistics
   */
  @Interruptible
  public static void printStatsPlain() {
    if (Options.printPhaseStats.getValue())
      printPhases();
    printTotals();
  }

  /**
   * Print out statistics totals
   */
  @Interruptible
  public static void printTotals() {
    Log.writeln("============================ MMTk Statistics Totals ============================");
    printColumnNames();
    Log.write(phase/2); Log.write("\t");
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
  @Interruptible
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
  @Interruptible
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

  /* ****************************************************************
   *
   *              Statistics output in xml format
 */

  /**
   * Print command-line options and statistics in XML format
   */
  @Interruptible
  public static void printStatsXml() {
    Xml.begin();
    Options.set.logXml();
    VM.config.printConfigXml();
    if (Options.printPhaseStats.getValue())
      printPhasesXml();
    printTotalsXml();
    Xml.end();
  }

  private static void openStatXml(String name) {
    Xml.openMinorTag("stat");
    Xml.attribute("name", name);
  }

  private static void closeStatXml() {
    Xml.closeMinorTag();
  }

  enum Phase {
    MUTATOR("mu"), GC("gc"), COMBINED("all");

    private final String name;
    Phase(String name) {
      this.name = name;
    }
    public String toString() { return name; }
  }

  /**
   * Print out statistics totals in Xml format
   */
  @Interruptible
  public static void printTotalsXml() {
    Xml.openTag("mmtk-stats-totals");
    Xml.singleValue("gc", phase/2);
    for (int c = 0; c < counters; c++) {
     if (!counter[c].isComplex())
      if (counter[c].mergePhases()) {
        printTotalXml(counter[c],Phase.COMBINED);
      } else {
        printTotalXml(counter[c],Phase.MUTATOR);
        printTotalXml(counter[c],Phase.GC);
      }
    }
    Xml.singleValue("total-time",Plan.totalTime.getTotalMillis(),"ms");
    Xml.closeTag("mmtk-stats-totals");
  }

  /**
   * Print a single total in an xml tag
   *
   * @param c The counter
   * @param phase The phase
   */
  @Interruptible
  private static void printTotalXml(Counter c, Phase phase) {
    openStatXml(c.getName());
    Xml.openAttribute("value");
    if (phase == Phase.COMBINED) {
      c.printTotal();
    } else {
      c.printTotal(phase == Phase.MUTATOR);
      Xml.closeAttribute();
      Xml.openAttribute("phase");
      Log.write(phase.toString());
    }
    Xml.closeAttribute();
    closeStatXml();
  }

  /**
   * Print a single phase counter in an xml tag
   *
   * @param c The counter
   * @param p The phase number
   * @param phase The phase (null, "mu" or "gc")
   */
  @Interruptible
  private static void printPhaseStatXml(Counter c, int p, Phase phase) {
    openStatXml(c.getName());
    Xml.openAttribute("value");
    if (phase == Phase.COMBINED) {
      c.printCount(p);
    } else {
      c.printCount(p);
      Xml.closeAttribute();
      Xml.openAttribute("phase");
      Log.write(phase.name);
   }
    Xml.closeAttribute();
    closeStatXml();
  }

  /**
   * Print out statistics for each mutator/gc phase in Xml format
   */
  @Interruptible
  public static void printPhasesXml() {
    Xml.openTag("mmtk-stats-per-gc");
    for (int p = 0; p <= phase; p += 2) {
      Xml.openTag("phase",false);
      Xml.attribute("gc",(p/2)+1);
      Xml.closeMinorTag();
      for (int c = 0; c < counters; c++) {
       if (!counter[c].isComplex())
        if (counter[c].mergePhases()) {
          printPhaseStatXml(counter[c],p,Phase.COMBINED);
        } else {
          printPhaseStatXml(counter[c],p,Phase.MUTATOR);
          printPhaseStatXml(counter[c],p,Phase.GC);
        }
      }
      Xml.closeTag("phase");
    }
    Xml.closeTag("mmtk-stats-per-gc");
  }

  /** @return The GC count (inclusive of any in-progress GC) */
  public static int gcCount() { return gcCount; }

  /** @return True if currently gathering stats */
  public static boolean gatheringStats() { return gatheringStats; }
}
