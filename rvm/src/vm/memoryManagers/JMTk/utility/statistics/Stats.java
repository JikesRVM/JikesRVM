/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */

package com.ibm.JikesRVM.memoryManagers.JMTk.utility.statistics;

import com.ibm.JikesRVM.memoryManagers.JMTk.Log;
import com.ibm.JikesRVM.memoryManagers.JMTk.Plan;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;

/**
 * This class implements basic statistics functionality
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public class Stats implements VM_Uninterruptible {

  /****************************************************************************
   *
   * Class variables
   */

  /** Maximum number of gc/mutator phases that can be counted */
  static final int MAX_PHASES = 1<<12;
  /** Maximum number of counters that can be in operation */
  static final int MAX_COUNTERS = 100;

  static private int counters = 0;
  static private Counter counter[];
  static int phase = 0;
  static private int gcCount = 0;
  static boolean gatheringStats = false;

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
  }

  /**
   * Add a new counter to the set of managed counters.
   * 
   * @param ctr The counter to be added.
   */
  static void newCounter(Counter ctr) throws VM_PragmaInterruptible {
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
   * Start all counters
   */
  public static void startAll() {
    if (gatheringStats) {
      Log.writeln("Error: calling Stats.startAll() while stats running");
      Log.writeln("       verbosity > 0 and the harness mechanism may be conflicitng");
      if (VM_Interface.VerifyAssertions) VM_Interface._assert(false);
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
    printHeader();
    for (int p = 0; p <= phase; p += 2) {
      Log.write((p/2)+1); Log.write("\t");
      for (int c = 0; c < counters; c++) {
	if (!counter[c].gcOnly()) {
	  counter[c].printCount(p);
	  Log.write("\t");
	}
	counter[c].printCount(p+1); Log.write("\t");
      }
      Log.writeln();
    }
    // print totals
    printFooter("================");
    Log.write((phase/2)+1); Log.write("\t");
    for (int c = 0; c < counters; c++) {
      if (!counter[c].gcOnly()) {
	counter[c].printTotal(true);
	Log.write("\t");
      }
      counter[c].printTotal(false); Log.write("\t");
    }
    Log.writeln();
    Log.write("Total time: "); 
    Plan.totalTime.printTotal(); Log.writeln(" ms");
    printFooter("----------------");
  }
  
  /**
   * Print out statistics header
   */
  private static void printHeader() {
    for (int c = 0; c <= counters; c++) {
      Log.write("----------------");
    }
    Log.writeln();
    Log.write("GC\t");
    for (int c = 0; c < counters; c++) {
      if (counter[c].gcOnly()) {
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

  /**
   * Print out statistics footer
   */
  private static void printFooter(String s) {
    for (int c = 0; c <= counters; c++) {
      Log.write(s);
    }
    Log.writeln();
  }

  /** @return The GC count (inclusive of any in-progress GC) */
  public static int gcCount() { return gcCount; }

  /** @return True if currently gathering stats */
  public static boolean gatheringStats() { return gatheringStats; }
}
