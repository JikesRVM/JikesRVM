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
    long start = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    FixedLive head = new FixedLive();
    FixedLive cur = head;
    for (int i=0; i<estimateSize; i++) {
      cur.cdr = new FixedLive();
      cur = cur.cdr;
    }
    long end = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
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
    int checkFreq = 1000;
    for (int i=0; i<count / checkFreq; i++) {
      long start = System.currentTimeMillis();
      for (int j=0; j<checkFreq; j++) 
	junk = new FixedLive();
      long end = System.currentTimeMillis();
      double elapsed = (end - start) / 1000.0;
      if (elapsed > 0.1) {
	double rate = liveSize / elapsed; // Mb/s
	System.out.println("GC (probably) occurred (" + elapsed + " s) : copying rate = " + rate + " Mb/s");
      }
    }
  }

  public static void runTest() throws Throwable {

    System.out.println("FixedLive running with " + liveSize + " Mb fixed live data");
    
    double objSizeEstimate = computeObjectSize();
    int objSize = (int) (objSizeEstimate + 0.5);
    System.out.print("Estimated object size of a 4-field object is " + objSizeEstimate + " bytes.  ");
    System.out.println("Rounded to " + objSize + " bytes");

    int count = (int) (liveSize << 20) / objSize;
    System.out.println("Creating live tree with " + count + " nodes");
    root = createTree(count);
    int spinSize = 1 << 10; // in megabytes
    int spinCount = (spinSize << 20) / objSize;
    System.out.println("Allocating " + spinSize + " Mb or " + spinCount + " nodes which immediately die");
    allocateDie(spinCount);
  }



}
