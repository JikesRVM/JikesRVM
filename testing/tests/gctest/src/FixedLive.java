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

import org.vmmagic.pragma.NoInline;

class FixedLive {

  static int liveSize = 0;  // in megabytes
  static int checkFreq = 128 * 1024; // in bytes
  static Node2I2A root;
  static Node2I2A junk;

  public static void main(String[] args)  throws Throwable {
    boolean base = true;
    if (args.length == 0) {
      System.out.println("No argument.  Assuming base");
    } else if (args[0].compareTo("opt") == 0 || args[0].compareTo("perf") == 0) {
      base = false;
    }
    liveSize = base ? 30 : 100;
    exclude = base ? 0 : 2;
    sampleCount = -exclude;
    if (liveSize < 0)
      System.out.println("Amount of live data must be positive");
    runTest();

    System.exit(0);
  }

  static double setupTime = 0.0;
  static double sumTraceTime = 0.0;
  static double sumAllocTime = 0.0;
  static double sumTraceRate = 0.0;
  static double squaredSumTraceRate = 0.0;
  static double sumAllocRate = 0.0;
  static double squaredSumAllocRate = 0.0;
  public static int exclude;  // skips first two GCs
  static int sampleCount;

  public static void addSample(double traceElapsed, double allocElapsed,
                               double traceRate, double allocRate) {
    sampleCount++;
    System.out.print("GC occurred (" + traceElapsed + " s) after " + allocElapsed + "s : tracing rate = " + traceRate + " Mb/s");
    System.out.print("   allocation rate = " + allocRate + " Mb/s");
    if (sampleCount < 1) {
      System.out.println("  <--- Skipping");
    } else {
      System.out.println();
      sumTraceTime += traceElapsed;
      sumAllocTime += allocElapsed;
      sumTraceRate += traceRate;
      sumAllocRate += allocRate;
      squaredSumTraceRate += traceRate * traceRate;
      squaredSumAllocRate += allocRate * allocRate;
    }
  }

    static double avgTraceRate;
    static double avgAllocRate;
    static double diffSquaredSumTraceRate;
    static double diffSquaredSumAllocRate;
    static double rmsTraceRate;
    static double rmsAllocRate;
    static double zTraceRate;
    static double zAllocRate;


  public static double chop(double x) {
    return ((int) (1000 * x) + 0.5) / 1000;
  }

  public static void updateStats() {
    setupTime = chop(setupTime);
    sumAllocTime = chop(sumAllocTime);
    sumTraceTime = chop(sumTraceTime);
    avgTraceRate = sumTraceRate / sampleCount;
    avgAllocRate = sumAllocRate / sampleCount;
    diffSquaredSumTraceRate = squaredSumTraceRate + sampleCount * (avgTraceRate * avgTraceRate) -
      2 * avgTraceRate * sumTraceRate;
    diffSquaredSumAllocRate = squaredSumAllocRate + sampleCount * (avgAllocRate * avgAllocRate) -
      2 * avgAllocRate * sumAllocRate;
    rmsTraceRate = Math.sqrt(diffSquaredSumTraceRate / sampleCount);
    rmsAllocRate = Math.sqrt(diffSquaredSumAllocRate / sampleCount);
    zTraceRate = rmsTraceRate / avgTraceRate;
    zAllocRate = rmsAllocRate / avgAllocRate;
    avgTraceRate = chop(avgTraceRate);
    avgAllocRate = chop(avgAllocRate);
    rmsTraceRate = chop(rmsTraceRate);
    rmsAllocRate = chop(rmsAllocRate);
    zTraceRate = chop(zTraceRate);
    zAllocRate = chop(zAllocRate);
  }

  public static void showResults() {
    updateStats();
    System.out.println();
    System.out.println("         Tracing rate: " + avgTraceRate + " Mb/s, sigma " + rmsTraceRate + " Mb/s, z-score " + zTraceRate);
    System.out.println("      Allocation rate: " + avgAllocRate + " Mb/s, sigma " + rmsAllocRate + " Mb/s, z-score " + zAllocRate);
    System.out.println();
    System.out.println("     Total Setup Time: " + setupTime + " s");
    System.out.println("Total Allocation Time: " + sumAllocTime + " s");
    System.out.println("   Total Tracing Time: " + sumTraceTime + " s");
    System.out.println();
    System.out.println("Overall: SUCCESS");
  }

  // Allocate until either maxGC GC's have occurred or maxMb megabytes have been allocated
  //
  @NoInline
  public static void allocateLoop(int count) {
    for (int i=0; i<count; i++)
      junk = new Node2I2A();
  }

  public static void allocateDie(int maxGC, int maxMb, double maxTime) {

    int count = maxMb * ((1 << 20) / Node2I2A.objectSize);
    long firstStart = System.currentTimeMillis();
    System.out.println("Allocating " + maxMb + " Mb or until " + maxGC + " GCs have occurred or until we would exceed " + maxTime + " secs.");
    System.out.println("First " + exclude + "GC's are excluded from overall statistics\n");

    int checkFreqObj = checkFreq / Node2I2A.objectSize;
    long last = System.currentTimeMillis();
    double allocatedSize = 0;
    for (int i=0; i< count / checkFreqObj && sampleCount < maxGC; i++) {
      long start = System.currentTimeMillis();
      allocateLoop(checkFreqObj);
      allocatedSize += checkFreq;
      long end = System.currentTimeMillis();
      double traceElapsed = (end - start) / 1000.0;
      double allocElapsed = (start - last) / 1000.0;
      double totalElapsed = traceElapsed + allocElapsed;
      if (traceElapsed > 0.1) {
        double traceRate = chop(liveSize / traceElapsed); // Mb/s
        double allocRate = chop((allocatedSize / 1e6) / allocElapsed); // Mb/s
        addSample(traceElapsed, allocElapsed, traceRate, allocRate);
        allocatedSize = 0;
        last = end;
      }
      if (0.001 * (end - firstStart) + totalElapsed > maxTime)
        break;
    }
    showResults();
  }


  public static void runTest() throws Throwable {

    System.out.println("FixedLive running with " + liveSize + " Mb fixed live data\n");

    long start = System.currentTimeMillis();
    Node2I2A.computeObjectSize();
    System.out.println("Estimated object size of a 4-field object (2 int, 2 ref) is " + Node2I2A.objectSize + " bytes");
    System.out.println("Header size is probably " + (Node2I2A.objectSize - 16) + " bytes");
    System.out.println("Note that the results of this test are not too meaningful for a generational collector");

    int count = (liveSize << 20) / Node2I2A.objectSize;
    System.out.println("Creating live data: tree with " + count + " nodes");
    root = Node2I2A.createTree(count);
    long end = System.currentTimeMillis();
    setupTime = (end - start) / 1000.0;

    allocateDie(5, 2048, 200.0);
  }



}
