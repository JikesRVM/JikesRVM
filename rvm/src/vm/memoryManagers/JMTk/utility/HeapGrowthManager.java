/*
 * (C) Copyright IBM Corp. 2003
 */
//$Id$
package org.mmtk.utility.heap;

import org.mmtk.plan.Plan;
import org.mmtk.utility.*;
import org.mmtk.vm.VM_Interface;

import org.vmmagic.pragma.*;

/**
 * This class is responsible for growing and shrinking the 
 * heap size by observing heap utilization and GC load.
 * 
 * @author Perry Cheng
 * @author Dave Grove
 */
public abstract class HeapGrowthManager implements Uninterruptible {

  // TODO: These really need to become longs.
  /**
   * The initial heap size (-Xms) in bytes
   */
  private static int initialHeapSize; 

  /**
   * The maximum heap size (-Xms) in bytes
   */
  private static int maxHeapSize;     

  /**
   * The current heap size in bytes
   */
  private static int currentHeapSize; 


  private final static double[][] generationalFunction =    {{0.00, 0.00, 0.10, 0.30, 0.60, 0.80, 1.00},
                                                             {0.00, 0.90, 0.90, 0.95, 1.00, 1.00, 1.00},
                                                             {0.01, 0.90, 0.90, 0.95, 1.00, 1.00, 1.00},
                                                             {0.02, 0.95, 0.95, 1.00, 1.00, 1.00, 1.00},
                                                             {0.07, 1.00, 1.00, 1.10, 1.15, 1.20, 1.20},
                                                             {0.15, 1.00, 1.00, 1.20, 1.25, 1.35, 1.30},
                                                             {0.40, 1.00, 1.00, 1.25, 1.30, 1.50, 1.50},
                                                             {1.00, 1.00, 1.00, 1.25, 1.30, 1.50, 1.50}};

  private final static double[][] nongenerationalFunction = {{0.00, 0.00, 0.10, 0.30, 0.60, 0.80, 1.00},
                                                             {0.00, 0.90, 0.90, 0.95, 1.00, 1.00, 1.00},
                                                             {0.02, 0.90, 0.90, 0.95, 1.00, 1.00, 1.00},
                                                             {0.05, 0.95, 0.95, 1.00, 1.00, 1.00, 1.00},
                                                             {0.15, 1.00, 1.00, 1.10, 1.15, 1.20, 1.20},
                                                             {0.30, 1.00, 1.00, 1.20, 1.25, 1.35, 1.30},
                                                             {0.50, 1.00, 1.00, 1.25, 1.30, 1.50, 1.50},
                                                             {1.00, 1.00, 1.00, 1.25, 1.30, 1.50, 1.50}};

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
  private static final double[][] function = Plan.NEEDS_WRITE_BARRIER ? generationalFunction : nongenerationalFunction;
 
  private static long endLastMajorGC;
  private static double accumulatedGCTime;

  /**
   * Initialize heap size parameters and the mechanisms
   * used to adaptively change heap size.
   */
  public static void boot(int initial, int max) {
    initialHeapSize = initial;
    maxHeapSize = max;
    if (initialHeapSize > maxHeapSize) 
      maxHeapSize = initialHeapSize;
    currentHeapSize = initialHeapSize;
    if (VM_Interface.VerifyAssertions) sanityCheck();
    endLastMajorGC = VM_Interface.cycles();
  }

  /**
   * @return the current heap size in bytes
   */
  public static int getCurrentHeapSize() {
    return currentHeapSize;
  }

  /**
   * Return the max heap size in bytes (as set by -Xmx).
   *
   * @return The max heap size in bytes (as set by -Xmx).
   */
  public static int getMaxHeapSize() {
    return maxHeapSize;
  }

  /**
   * Return the initial heap size in bytes (as set by -Xms).
   *
   * @return The initial heap size in bytes (as set by -Xms).
   */
  public static int getInitialHeapSize() {
    return initialHeapSize;
  }

  /**
   * Forcibly grow the heap by the given number of bytes.
   * Used to provide headroom when handling an OutOfMemory
   * situation.
   * @param size number of bytes to grow the heap
   */
  public static void overrideGrowHeapSize(int size) {
    currentHeapSize += size;
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
    endLastMajorGC = VM_Interface.cycles();
    accumulatedGCTime = 0;
  }

  /** 
   * Decide how to grow/shrink the heap to respond
   * to application's memory usage.
   * @return true if heap size was changed, false otherwise
   */
  public static boolean considerHeapSize() {
    int oldSize = currentHeapSize;
    long reserved = Plan.reservedMemory();
    double liveRatio = reserved / ((double) currentHeapSize);
    double ratio = computeHeapChangeRatio(liveRatio);
    long newSize = (long) (ratio * (double)oldSize);
    if (newSize < reserved) newSize = reserved;
    newSize = (newSize + (1<<20)) >> 20 << 20; // round to next megabyte
    if (newSize > maxHeapSize) newSize = maxHeapSize;
    if (newSize != oldSize) {
      // Heap size is going to change
      currentHeapSize = (int) newSize;
      if (Options.verbose >= 2) { 
        Log.write("GC Message: Heap changed from "); Log.write((int) (oldSize / 1024)); 
        Log.write("KB to "); Log.write((int) (newSize / 1024)); 
        Log.writeln("KB"); 
      } 
      return true;
    } else {
      return false;
    }
  }

  private static double computeHeapChangeRatio(double liveRatio) {
    // (1) compute GC load.
    long totalCycles = VM_Interface.cycles() - endLastMajorGC;
    double totalTime = VM_Interface.cyclesToMillis(totalCycles);
    double gcLoad = accumulatedGCTime / totalTime;

    if (liveRatio > 1) {
      // Perhaps indicates bad bookkeeping in JMTk?
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
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(liveRatio >= 0);
    if (VM_Interface.VerifyAssertions && gcLoad < 0) {
      Log.write("gcLoad computed to be "); Log.writeln(gcLoad);
      Log.write("\taccumulateGCTime was (ms) "); Log.writeln(accumulatedGCTime);
      Log.write("\ttotalTime was (ms) "); Log.writeln(totalTime);
      VM_Interface._assert(false);
    }
    
    if (Options.verbose > 2) {
      Log.write("Live ratio "); Log.writeln(liveRatio);
      Log.write("GCLoad     "); Log.writeln(gcLoad);
    }
    
    // (2) Find the 4 points surrounding gcLoad and liveRatio
    int liveRatioUnder = 1;
    int liveRatioAbove = function[0].length-1;
    int gcLoadUnder = 1;
    int gcLoadAbove = function.length-1;
    while (true) {
      if (function[0][liveRatioUnder+1] >= liveRatio) break;
      liveRatioUnder++;
    }
    while (true) {
      if (function[0][liveRatioAbove-1] <= liveRatio) break;
      liveRatioAbove--;
    }
    while (true) {
      if (function[gcLoadUnder+1][0] >= gcLoad) break;
      gcLoadUnder++;
    }
    while (true) {
      if (function[gcLoadAbove-1][0] <= gcLoad) break;
      gcLoadAbove--;
    }

    // (3) Compute the heap change ratio
    double factor = function[gcLoadUnder][liveRatioUnder];
    double liveRatioFraction = 
      (liveRatio - function[0][liveRatioUnder]) /
      (function[0][liveRatioAbove] - function[0][liveRatioUnder]);
    double liveRatioDelta = 
      function[gcLoadUnder][liveRatioAbove] - function[gcLoadUnder][liveRatioUnder];
    factor += (liveRatioFraction * liveRatioDelta);
    double gcLoadFraction = 
      (gcLoad - function[gcLoadUnder][0]) / 
      (function[gcLoadAbove][0] - function[gcLoadUnder][0]);
    double gcLoadDelta = 
      function[gcLoadAbove][liveRatioUnder] - function[gcLoadUnder][liveRatioUnder];
    factor += (gcLoadFraction * gcLoadDelta);

    if (Options.verbose > 2) {
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
    VM_Interface._assert(liveRatio[1] == 0);
    VM_Interface._assert(liveRatio[liveRatio.length-1] == 1);
    for (int i=2; i<liveRatio.length; i++) {
      VM_Interface._assert(liveRatio[i-1] < liveRatio[i]);
      for (int j=1; j<function.length; j++) {
        VM_Interface._assert(function[j][i] >= 1 ||
                             function[j][i] > liveRatio[i]);
      }
    }

    // Check GC load
    VM_Interface._assert(function[1][0] == 0);
    int len = function.length;
    VM_Interface._assert(function[len-1][0] == 1);
    for (int i=2; i<len; i++) {
      VM_Interface._assert(function[i-1][0] < function[i][0]);
    }

    // Check that we have a rectangular matrix
    for (int i=1; i<function.length; i++) {
      VM_Interface._assert(function[i-1].length == function[i].length);
    }
  }


}
