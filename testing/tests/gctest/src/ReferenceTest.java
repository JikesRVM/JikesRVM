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

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
//import org.jikesrvm.*;

/**
 * A test to test java.lang.ref.*.
 */
class ReferenceTest {


  public static <T> double checkReferenceArray(Reference<T> [] ra, ReferenceQueue<T> rq) {
    // Verify that all references on rq belong to ra and that if so they are cleared
    for (Reference<?> r = rq.poll(); r != null; r = rq.poll()) {
      int i;
      for (i=0; i<ra.length; i++)
          if (ra[i] == r)
            break;
      if (i == ra.length) return -2.0;
    }
    // Return fraction of ra array whose references are not cleared
    int count = 0;
    for (Reference<T> aRa : ra)
      if (aRa.get() != null)
        count++;
    return count / ((double) ra.length);
  }

  static int microUnitSize = 100;     // conservative upper bound for small object
  static int allocateUnitSize = 5000;

  public static int MBtoUnits(double amt) {  // in Mbytes
      return (int) ((amt * (1 << 20)) / allocateUnitSize);
  }

  public static Object[] allocateUnit() {
    int t = allocateUnitSize / microUnitSize;
    Object[] result = new Object[t];
    for (int i=0; i<t; i++)
      result[i] = new byte[microUnitSize - 4];  // 4 bytes of header
    return result;
  }

  static Object dummy;
  public static void allocateDiscard(double amt) { // amt in Mb
    int rounds = MBtoUnits(amt);
    for (int i=0; i<rounds; i++)
        dummy = allocateUnit();
  }

  private static Object outOfMemoryHandle;

  public static void allocateUntilOOM() {
    try {
      while(true) {
        Object[] myArray = new Object[10000];
        myArray[0] = outOfMemoryHandle;
        outOfMemoryHandle = myArray;
      }
    } catch (OutOfMemoryError oome) {
      outOfMemoryHandle = null;
      System.out.println("Caught OutOfMemoryError");
    }
  }

  public static double allocateUntilNextGC() {
      int count = 0;
      long lastFree = Runtime.getRuntime().freeMemory();
      while (true) {
        allocateDiscard(0.1);
        long curFree = Runtime.getRuntime().freeMemory();
        count++;
        if (curFree > lastFree)
            return (count * 0.1);
        lastFree = curFree;
      }
  }



    static final int WEAK = 0;
    static final int SOFT = 1;

  public static Reference<Object[]> [] allocateReferenceArray(int type, double amt, ReferenceQueue<Object[]> rq) { // amt in Mb
    int rounds = MBtoUnits(amt);
    @SuppressWarnings("unchecked")
    Reference<Object[]> [] ra = new Reference[rounds];
    for (int i=0; i<rounds; i++) {
      final Reference<Object[]> reference;
      if(type == WEAK) {
        reference = new WeakReference<Object[]>(allocateUnit(), rq);
      } else if(type == SOFT) {
        reference = new SoftReference<Object[]>(allocateUnit(), rq);
      } else {
        reference = null;
      }
      ra[i] = reference;
    }
    return ra;
  }

  public static double getHeapSize() {
      allocateUntilNextGC(); // clear current heap twice
      allocateUntilNextGC();
      double size = allocateUntilNextGC();
      System.out.print("ReferenceTest checking available size of heap (Mb): ");
      System.out.println(size);
      return size;
  }

  private static int failCount = 0;
  private static final int NO_QUALITY = 0;
  private static final int GOOD = 1;
  private static final int POOR = 2;

  private static void check(String msg, boolean correct, int quality) {
      System.out.print(msg + "     ");
      System.out.print(correct ? "PASS" : "FAIL");
      if (correct) {
        if (quality == GOOD) System.out.print("   GOOD");
        if (quality == POOR) System.out.print("   POOR");
      } else {
        failCount++;
      }
      System.out.println();
  }

  private static void check(String msg, boolean correct) {
      check(msg, correct, NO_QUALITY);
  }

  /**
   * Force compilation of each of the methods and report on the size
   * of the generated machine code.
   */
  public static void main(String[] args) throws Exception {

      // ------ Compute initial heap size (inaccuracy subject to compilation) --------
      double initialHeapSize = getHeapSize();

      // ------ Test weak references -----------
      System.out.println("\nChecking weak references and reference queue");
      ReferenceQueue<Object[]> wrq = new ReferenceQueue<Object[]>();
      allocateUntilNextGC();
      Reference<Object[]> [] wra = allocateReferenceArray(WEAK, 0.5 * initialHeapSize, wrq);
      double weakAvail = checkReferenceArray(wra, wrq);
      check("Fraction of weak references before GC still live = " + weakAvail, (weakAvail == 1.0));
      allocateDiscard(0.75 * initialHeapSize);
      weakAvail = checkReferenceArray(wra, wrq);
      check("Fraction of weak references after  GC still live = " + weakAvail, (weakAvail == 0.0));

      // ------ Test soft references -----------
      System.out.println("\nChecking soft references and reference queue");
      ReferenceQueue<Object[]> srq = new ReferenceQueue<Object[]>();
      allocateUntilNextGC();
      Reference<Object[]> [] sra = allocateReferenceArray(SOFT, 0.75 * initialHeapSize, srq);
      double softAvail = checkReferenceArray(sra, srq);
      check("Fraction of soft references before GC still live = " + softAvail, (softAvail == 1.0));
      allocateUntilOOM();
      softAvail = checkReferenceArray(sra, srq);
      check("Fraction of soft references after  GC still live = " + softAvail,
            (softAvail >= 0.00) && (softAvail <= 0.67),
            ((softAvail >= 0.33) && (softAvail <= 0.66)) ? GOOD : POOR);



      // ------ Finish up -----------
      System.out.println();
      getHeapSize();
      System.out.print("\nOverall: ");
      System.out.println((failCount == 0) ? "SUCCESS" : (failCount + " FAILURES"));
  }

}
