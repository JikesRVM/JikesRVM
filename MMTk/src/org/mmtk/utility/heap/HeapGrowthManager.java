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
package org.mmtk.utility.heap;

import org.mmtk.plan.Plan;
import org.mmtk.utility.*;
import org.mmtk.utility.options.Options;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class is responsible for growing and shrinking the
 * heap size by observing heap utilization and GC load.
 */
@Uninterruptible public abstract class HeapGrowthManager implements Constants {

  /**
   * The initial heap size (-Xms) in bytes
   */
  private static Extent initialHeapSize;

  /**
   * The maximum heap size (-Xms) in bytes
   */
  private static Extent maxHeapSize;

  /**
   * The current heap size in bytes
   */
  private static Extent currentHeapSize;


  private static final double[][] generationalFunction =    {{0.00, 0.00, 0.10, 0.30, 0.60, 0.80, 1.00},
      { 0.00, 0.90, 0.90, 0.95, 1.00, 1.00, 1.00 },
      { 0.01, 0.90, 0.90, 0.95, 1.00, 1.00, 1.00 },
      { 0.02, 0.95, 0.95, 1.00, 1.00, 1.00, 1.00 },
      { 0.07, 1.00, 1.00, 1.10, 1.15, 1.20, 1.20 },
      { 0.15, 1.00, 1.00, 1.20, 1.25, 1.35, 1.30 },
      { 0.40, 1.00, 1.00, 1.25, 1.30, 1.50, 1.50 },
      { 1.00, 1.00, 1.00, 1.25, 1.30, 1.50, 1.50 } };

  private static final double[][] nongenerationalFunction = {{0.00, 0.00, 0.10, 0.30, 0.60, 0.80, 1.00},
      { 0.00, 0.90, 0.90, 0.95, 1.00, 1.00, 1.00 },
      { 0.02, 0.90, 0.90, 0.95, 1.00, 1.00, 1.00 },
      { 0.05, 0.95, 0.95, 1.00, 1.00, 1.00, 1.00 },
      { 0.15, 1.00, 1.00, 1.10, 1.15, 1.20, 1.20 },
      { 0.30, 1.00, 1.00, 1.20, 1.25, 1.35, 1.30 },
      { 0.50, 1.00, 1.00, 1.25, 1.30, 1.50, 1.50 },
      { 1.00, 1.00, 1.00, 1.25, 1.30, 1.50, 1.50 } };

  /**
   * An encoding of the function used to manage heap size.
   * The xaxis represents the live ratio at the end of a major collection.
   * The yaxis represents the GC load (GC time/total time).
   * The interior of the matrix represents a ratio to shrink or grow
   * the heap for a given pair of live ratio and GC load.
   * The constraints on the matrix are:
   * <ul>
   * <li> function[0][0] is ignored.
   * <li> All numbers in the first row must monotonically increase and
   *      must be in the range from 0 to 1 inclusive.</li>
   * <li> All numbers in the first column must monotonically increase
   *      and must be in the range from 0 to 1 inclusive.</li>
   * <li> There must be 0 and 1 values specified in both dimensions.
   * <li> For all interior points in the matrix, the value must be
   *      greater than the liveRatio for that column.</li>
   * </ul>
   */
  private static final double[][] function =
    VM.activePlan.constraints().generational() ? generationalFunction : nongenerationalFunction;

  private static long endLastMajorGC;
  private static double accumulatedGCTime;

  /**
   * Initialize heap size parameters and the mechanisms
   * used to adaptively change heap size.
   */
  public static void boot(Extent initial, Extent max) {
    initialHeapSize = initial;
    maxHeapSize = max;
    if (initialHeapSize.GT(maxHeapSize))
      maxHeapSize = initialHeapSize;
    currentHeapSize = initialHeapSize;
    VM.events.heapSizeChanged(currentHeapSize);
    if (VM.VERIFY_ASSERTIONS) sanityCheck();
    endLastMajorGC = VM.statistics.nanoTime();
  }

  /**
   * @return the current heap size in bytes
   */
  public static Extent getCurrentHeapSize() {
    return currentHeapSize;
  }

  /**
   * Return the max heap size in bytes (as set by -Xmx).
   *
   * @return The max heap size in bytes (as set by -Xmx).
   */
  public static Extent getMaxHeapSize() {
    return maxHeapSize;
  }

  /**
   * Return the initial heap size in bytes (as set by -Xms).
   *
   * @return The initial heap size in bytes (as set by -Xms).
   */
  public static Extent getInitialHeapSize() {
    return initialHeapSize;
  }

  /**
   * Forcibly grow the heap by the given number of bytes.
   * Used to provide headroom when handling an OutOfMemory
   * situation.
   * @param size number of bytes to grow the heap
   */
  public static void overrideGrowHeapSize(Extent size) {
    currentHeapSize = currentHeapSize.plus(size);
    VM.events.heapSizeChanged(currentHeapSize);
  }

  /**
   * Record the time taken by the current GC;
   * used to compute gc load, one of the inputs
   * into the heap size management function
   */
  public static void recordGCTime(double time) {
    accumulatedGCTime += time;
  }

  /**
   * Reset timers used to compute gc load
   */
  public static void reset() {
    endLastMajorGC = VM.statistics.nanoTime();
    accumulatedGCTime = 0;
  }

  /**
   * Decide how to grow/shrink the heap to respond
   * to application's memory usage.
   * @return true if heap size was changed, false otherwise
   */
  public static boolean considerHeapSize() {
    Extent oldSize = currentHeapSize;
    Extent reserved = Plan.reservedMemory();
    double liveRatio = reserved.toLong() / ((double) currentHeapSize.toLong());
    double ratio = computeHeapChangeRatio(liveRatio);
    Extent newSize = Word.fromIntSignExtend((int)(ratio * (double) (oldSize.toLong()>>LOG_BYTES_IN_MBYTE))).lsh(LOG_BYTES_IN_MBYTE).toExtent(); // do arith in MB to avoid overflow
    if (newSize.LT(reserved)) newSize = reserved;
    newSize = newSize.plus(BYTES_IN_MBYTE - 1).toWord().rshl(LOG_BYTES_IN_MBYTE).lsh(LOG_BYTES_IN_MBYTE).toExtent(); // round to next megabyte
    if (newSize.GT(maxHeapSize)) newSize = maxHeapSize;
    if (newSize.NE(oldSize) && newSize.GT(Extent.zero())) {
      // Heap size is going to change
      currentHeapSize = newSize;
      if (Options.verbose.getValue() >= 2) {
        Log.write("GC Message: Heap changed from "); Log.writeDec(oldSize.toWord().rshl(LOG_BYTES_IN_KBYTE));
        Log.write("KB to "); Log.writeDec(newSize.toWord().rshl(LOG_BYTES_IN_KBYTE));
        Log.writeln("KB");
      }
      VM.events.heapSizeChanged(currentHeapSize);
      return true;
    } else {
      return false;
    }
  }

  private static double computeHeapChangeRatio(double liveRatio) {
    // (1) compute GC load.
    long totalNanos = VM.statistics.nanoTime() - endLastMajorGC;
    double totalTime = VM.statistics.nanosToMillis(totalNanos);
    double gcLoad = accumulatedGCTime / totalTime;

    if (liveRatio > 1) {
      // Perhaps indicates bad bookkeeping in MMTk?
      Log.write("GCWarning: Live ratio greater than 1: ");
      Log.writeln(liveRatio);
      liveRatio = 1;
    }
    if (gcLoad > 1) {
      if (gcLoad > 1.0001) {
        Log.write("GC Error: GC load was greater than 1!! ");
        Log.writeln(gcLoad);
        Log.write("GC Error:\ttotal time (ms) "); Log.writeln(totalTime);
        Log.write("GC Error:\tgc time (ms) "); Log.writeln(accumulatedGCTime);
      }
      gcLoad = 1;
    }
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(liveRatio >= 0);
    if (VM.VERIFY_ASSERTIONS && gcLoad < -0.0) {
      Log.write("gcLoad computed to be "); Log.writeln(gcLoad);
      Log.write("\taccumulateGCTime was (ms) "); Log.writeln(accumulatedGCTime);
      Log.write("\ttotalTime was (ms) "); Log.writeln(totalTime);
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    }

    if (Options.verbose.getValue() > 2) {
      Log.write("Live ratio "); Log.writeln(liveRatio);
      Log.write("GCLoad     "); Log.writeln(gcLoad);
    }

    // (2) Find the 4 points surrounding gcLoad and liveRatio
    int liveRatioUnder = 1;
    int liveRatioAbove = function[0].length - 1;
    int gcLoadUnder = 1;
    int gcLoadAbove = function.length - 1;
    if (liveRatio >= 1.0) {
      // liveRatio has maxed out
      liveRatioUnder = liveRatioAbove;
    } else {
      while (true) {
        if (function[0][liveRatioUnder+1] > liveRatio) break;
        liveRatioUnder++;
      }
      while (true) {
        if (function[0][liveRatioAbove-1] <= liveRatio) break;
        liveRatioAbove--;
      }
    }
    if (gcLoad >= 1.0) {
      // gcRatio has maxed out
      gcLoadUnder = gcLoadAbove;
    } else {
      while (true) {
        if (function[gcLoadUnder+1][0] > gcLoad) break;
        gcLoadUnder++;
      }
      while (true) {
        if (function[gcLoadAbove-1][0] <= gcLoad) break;
        gcLoadAbove--;
      }
    }

    // (3) Compute the heap change ratio
    double factor = function[gcLoadUnder][liveRatioUnder];
    if (liveRatioUnder != liveRatioAbove) {
      // interpolate for liveRatio values in between two specified values in function table
      double liveRatioFraction =
        (liveRatio - function[0][liveRatioUnder]) /
        (function[0][liveRatioAbove] - function[0][liveRatioUnder]);
      double liveRatioDelta =
        function[gcLoadUnder][liveRatioAbove] - function[gcLoadUnder][liveRatioUnder];
      factor += (liveRatioFraction * liveRatioDelta);
    }
    if (gcLoadUnder != gcLoadAbove) {
      // interpolate for gcLoad values in between two specified values in function table
      double gcLoadFraction =
        (gcLoad - function[gcLoadUnder][0]) /
        (function[gcLoadAbove][0] - function[gcLoadUnder][0]);
      double gcLoadDelta =
        function[gcLoadAbove][liveRatioUnder] - function[gcLoadUnder][liveRatioUnder];
      factor += (gcLoadFraction * gcLoadDelta);
    }
    if (Options.verbose.getValue() > 2) {
      Log.write("Heap adjustment factor is ");
      Log.writeln(factor);
    }
    return factor;
  }

  /**
   * Check that function satisfies the invariants
   */
  private static void sanityCheck() {
    // Check live ratio
    double[] liveRatio = function[0];
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(liveRatio[1] == 0);
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(liveRatio[liveRatio.length-1] == 1);
    for (int i = 2; i < liveRatio.length; i++) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(liveRatio[i-1] < liveRatio[i]);
      for (int j = 1; j < function.length; j++) {
        if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(function[j][i] >= 1 || function[j][i] > liveRatio[i]);
      }
    }

    // Check GC load
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(function[1][0] == 0);
    int len = function.length;
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(function[len-1][0] == 1);
    for (int i = 2; i < len; i++) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(function[i-1][0] < function[i][0]);
    }

    // Check that we have a rectangular matrix
    for (int i = 1; i < function.length; i++) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(function[i-1].length == function[i].length);
    }
  }
}
