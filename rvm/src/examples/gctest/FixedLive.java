/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/*
 * @author Perry Cheng
 */

class FixedLive {

  int data1;
  int data2;
  FixedLive car;
  FixedLive cdr;

  static int liveSize = 0;  // in megabytes
  static int objectSize = 0; // in bytes
  static FixedLive root;
  static FixedLive junk;

  public static void main(String args[])  throws Throwable {
    if (args.length != 1)
      System.out.println("Usage: FixedLive <live data in megabytes>");
    liveSize = Integer.parseInt(args[0]);
    if (liveSize < 0)
      System.out.println("Amount of live data must be positive");
    runTest();
  }

  public static double computeObjectSize() {
    int estimateSize = 200000;
    // System.out.println("totalMemory = " + Runtime.getRuntime().totalMemory());
    // System.out.println("freeMemory = " + Runtime.getRuntime().freeMemory());
    long start = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    System.out.println("start = " + start);
    FixedLive head = new FixedLive();
    FixedLive cur = head;
    for (int i=0; i<estimateSize; i++) {
      cur.cdr = new FixedLive();
      cur = cur.cdr;
    }
    // System.out.println("totalMemory = " + Runtime.getRuntime().totalMemory());
    // System.out.println("freeMemory = " + Runtime.getRuntime().freeMemory());
    long end = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    System.out.println("end = " + end);
    return (end - start) / ((double) estimateSize);
  }

  public static FixedLive createTree(int nodes) {
    if (nodes == 0) return null;
    int children = nodes - 1;
    int left = children / 2;
    int right = children - left;
    FixedLive self = new FixedLive();
    self.car = createTree(left);
    self.cdr = createTree(right);
    return self;
  }

  public static void allocateDie(int count) {

    double cumulativeTraceRate = 0.0;
    double cumulativeAllocRate = 0.0;
    int cumulativeCount = -1;

    int checkFreq = 64000 / objectSize;
    long last = System.currentTimeMillis();
    double allocatedSize = 0;
    for (int i=0; i< count / checkFreq; i++) {
      long start = System.currentTimeMillis();
      for (int j=0; j<checkFreq; j++) 
	junk = new FixedLive();
      allocatedSize += checkFreq * objectSize;
      long end = System.currentTimeMillis();
      double traceElapsed = (end - start) / 1000.0;
      double allocElapsed = (start - last) / 1000.0;
      if (traceElapsed > 0.1) {
	double traceRate = liveSize / traceElapsed; // Mb/s
	double allocRate = (allocatedSize / 1e6) / allocElapsed; // Mb/s
	cumulativeCount++;
	if (cumulativeCount == 0) 
	  System.out.println("Skipping first iteration in cumulative numbers");
	else {
	  cumulativeTraceRate += traceRate;
	  cumulativeAllocRate += allocRate;
	}
	System.out.println("GC (probably) occurred (" + traceElapsed + " s) : copying rate = " + traceRate + " Mb/s");
	System.out.println("                                allocation rate = " + allocRate + " Mb/s");
	allocatedSize = 0;
	last = end;
      }
    }
    System.out.print("Overall Rate: tracing = " + (cumulativeTraceRate / cumulativeCount) + " Mb/s");
    System.out.println("   allocating = " + (cumulativeAllocRate / cumulativeCount) + " Mb/s");
  }

  public static void runTest() throws Throwable {

    System.out.println("FixedLive running with " + liveSize + " Mb fixed live data");
    
    double objSizeEstimate = computeObjectSize();
    objectSize = (int) (objSizeEstimate + 0.5);
    System.out.print("Estimated object size of a 4-field object is " + objSizeEstimate + " bytes.  ");
    System.out.println("Rounded to " + objectSize + " bytes");

    int count = (int) (liveSize << 20) / objectSize;
    System.out.println("Creating live tree with " + count + " nodes");
    root = createTree(count);
    int spinSize = 1 << 10; // in megabytes
    int spinCount = (spinSize << 20) / objectSize;
    System.out.println("Allocating " + spinSize + " Mb or " + spinCount + " nodes which immediately die");
    allocateDie(spinCount);
  }



}
