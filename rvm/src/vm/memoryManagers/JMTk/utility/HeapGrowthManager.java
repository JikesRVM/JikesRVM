/*
 * (C) Copyright IBM Corp. 2003
 */
//$Id$
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;

/**
 * This class is responsible for growing and shrinking the 
 * heap size by observing heap utilization and GC load.
 * 
 * @author Perry Cheng
 * @author Dave Grove
 */
public abstract class HeapGrowthManager {

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
  private static final double[][] function = {{0.00, 0.00, 0.30, 0.60, 0.90, 1.00},
					      {0.00, 0.90, 0.90, 0.95, 1.00, 1.00},
					      {0.05, 0.90, 0.90, 0.95, 1.00, 1.00},
					      {0.15, 1.00, 1.00, 1.10, 1.20, 1.20},
					      {0.30, 1.20, 1.20, 1.25, 1.35, 1.30},
					      {0.50, 1.25, 1.25, 1.30, 1.50, 1.50},
					      {1.00, 1.25, 1.25, 1.30, 1.50, 1.50}};
  
  private static double endLastMajorGC;
  private static double accumulatedGCTime;

  static {
    if (VM_Interface.VerifyAssertions) sanityCheck();
  }

  public static void boot() {
    endLastMajorGC = VM_Interface.now();
  }

  public static void recordGCTime(double time) {
    accumulatedGCTime += time;
  }

  public static double computeHeapChangeRatio(double liveRatio) {
    // (1) compute GC load.
    double totalTime = VM_Interface.now() - endLastMajorGC;
    double gcLoad = accumulatedGCTime / totalTime;
    
    if (Options.verbose > 2) {
      VM_Interface.sysWriteln("Live ratio ",liveRatio);
      VM_Interface.sysWriteln("GCLoad     ",gcLoad);
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

    if (Options.verbose > 2) VM_Interface.sysWriteln("After gcLoad adjustment factor is ",factor);

    return factor;
  }

  public static void reset() {
    endLastMajorGC = VM_Interface.now();
    accumulatedGCTime = 0;
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
