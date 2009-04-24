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
class LargeAlloc {

  static long allocSize = 0;  // in megabytes
  static int itemSize = 16 * 1024;
  static int sizeCount = 10;
  static double sizeRatio = 1.5;
  static double timeLimit = 120;
  public static byte [] junk;

  public static void main(String[] args)  throws Throwable {
    boolean base = true;
    if (args.length == 0) {
      System.out.println("No argument.  Assuming base");
    }
    if (args[0].compareTo("opt") == 0 || args[0].compareTo("perf") == 0) {
      base = false;
      timeLimit = 600;
    }
    allocSize = base ? 500 : 3000;
    runTest();

    System.exit(0);
  }

  public static void runTest() throws Throwable {
    System.out.println("LargeAlloc running with " + allocSize + " Mb of allocation");
    System.out.println("Run with verbose GC on and make sure space accounting is not leaking");
    System.out.println();

    long lastUsed = 0;
    long used = 0;
    long limit = allocSize * 1024 * 1024;
    long start = System.currentTimeMillis();
    System.gc();
    long startUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    while (used < limit) {
      int curSize = itemSize;
      for (int i=0; i<sizeCount; i++) {
        junk = new byte[curSize];
        used += itemSize;
        curSize = (int) (curSize * sizeRatio);
      }
      if (used - lastUsed > 100 * 1024 * 1024) {
        long cur = System.currentTimeMillis();
        System.out.println("Allocated " + (used >> 20) + " Mb at time " + ((cur - start) / 1000.0) + " sec");
        lastUsed = used;
      }
      long cur = System.currentTimeMillis();
      double elapsed = (cur - start) / 1000.0;
      if (elapsed > timeLimit) {
        System.out.println("Exitting because exceeded time limit of " + timeLimit + " seconds");
        break;
      }
    }
    System.gc();
    long endUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    System.out.print("\nAfter allocation, usedMemory has increased by ");
    System.out.println(((endUsed - startUsed) / (1024.0 * 1024.0)) + " Mb");
    System.out.println("Overall: SUCCESS");
  }
}
