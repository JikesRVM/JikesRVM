/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/*
 * @author Perry Cheng
 */

import com.ibm.JikesRVM.VM_PragmaNoInline;

class FixedLive {

  static int liveSize = 0;  // in megabytes
  static Node2I2A root;
  static Node2I2A junk;

  public static void main(String args[])  throws Throwable {
    if (args.length != 1)
      System.out.println("Usage: Node2I2A <live data in megabytes>");
    liveSize = Integer.parseInt(args[0]);
    if (liveSize < 0)
      System.out.println("Amount of live data must be positive");
    runTest();
  }

  static double sumTraceRate = 0.0;
  static double squaredSumTraceRate = 0.0;
  static double sumAllocRate = 0.0;
  static double squaredSumAllocRate = 0.0;
  public static int exclude = 2;  // skips first two GCs
  static int sampleCount = -exclude; 

  public static void addSample(double traceElapsed, double traceRate, double allocRate) {
    sampleCount++;
    System.out.print("GC occurred (" + traceElapsed + " s) : tracing rate = " + traceRate + " Mb/s");
    System.out.print("   allocation rate = " + allocRate + " Mb/s");
    if (sampleCount < 1) {
      System.out.println("  <--- Skipping");
    }
    else {
      System.out.println();
      sumTraceRate += traceRate;
      sumAllocRate += allocRate;
      squaredSumTraceRate += traceRate * traceRate;
      squaredSumAllocRate += allocRate * allocRate;
    }
  }

  public static void showResults() {
    double avgTraceRate = sumTraceRate / sampleCount;
    double avgAllocRate = sumAllocRate / sampleCount;
    double diffSquaredSumTraceRate = squaredSumTraceRate + sampleCount * (avgTraceRate * avgTraceRate) 
      - 2 * avgTraceRate * sumTraceRate;
    double rmsTraceRate = Math.sqrt(diffSquaredSumTraceRate / sampleCount);
    double diffSquaredSumAllocRate = squaredSumAllocRate + sampleCount * (avgAllocRate * avgAllocRate) 
      - 2 * avgAllocRate * sumAllocRate;
    double rmsAllocRate = Math.sqrt(diffSquaredSumAllocRate / sampleCount);
    avgTraceRate = ((int) (10000 * avgTraceRate) + 0.5) / 10000;
    avgAllocRate = ((int) (10000 * avgAllocRate) + 0.5) / 10000;
    rmsTraceRate = ((int) (10000 * rmsTraceRate) + 0.5) / 10000;
    rmsAllocRate = ((int) (10000 * rmsAllocRate) + 0.5) / 10000;
    System.out.print("Overall Rate:           tracing  rate = " + avgTraceRate + " Mb/s");
    System.out.println("   allocation rate = " + avgAllocRate + " Mb/s");
    System.out.print("Standard Deviation:     tracing sigma = " + rmsTraceRate + " Mb/s");
    System.out.println("   allocation sigma = " + rmsAllocRate + " Mb/s");
  }

  // Allocate until either maxGC GC's have occurred or maxMb megabytes have been allocated
  //
  public static void allocateLoop(int count) throws VM_PragmaNoInline {
    for (int i=0; i<count; i++) 
      junk = new Node2I2A();
  }

  public static void allocateDie(int maxGC, int maxMb) {

    int count = maxMb * ((1 << 20) / Node2I2A.objectSize);
    System.out.println("Allocating " + maxMb + " Mb or until " + maxGC + " GCs have occurred.  First " + exclude + "GCs are excluded");

    int checkFreq = 64000 / Node2I2A.objectSize;
    long last = System.currentTimeMillis();
    double allocatedSize = 0;
    for (int i=0; i< count / checkFreq && sampleCount < maxGC; i++) {
      long start = System.currentTimeMillis();
      allocateLoop(checkFreq);
      allocatedSize += checkFreq * Node2I2A.objectSize;
      long end = System.currentTimeMillis();
      double traceElapsed = (end - start) / 1000.0;
      double allocElapsed = (start - last) / 1000.0;
      if (traceElapsed > 0.1) {
	double traceRate = liveSize / traceElapsed; // Mb/s
	double allocRate = (allocatedSize / 1e6) / allocElapsed; // Mb/s
	addSample(traceElapsed, traceRate, allocRate);
	allocatedSize = 0;
	last = end;
      }
    }
    showResults();
  }

  public static void runTest() throws Throwable {

    System.out.println("FixedLive running with " + liveSize + " Mb fixed live data");
    
    Node2I2A.computeObjectSize();
    System.out.println("Estimated object size of a 4-field object (2 int, 2 ref) is " + Node2I2A.objectSize + " bytes");

    int count = (int) (liveSize << 20) / Node2I2A.objectSize;
    System.out.println("Creating tree with " + count + " nodes");
    root = Node2I2A.createTree(count);

    allocateDie(5, 2048);
  }



}
