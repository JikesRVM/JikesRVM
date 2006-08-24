/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.lang.ref.*;
//import com.ibm.JikesRVM.*;

/**
 * A test to test java.lang.ref.*.
 * 
 * @author Perry Cheng
 */
class ReferenceTest {


  public static double checkReferenceArray (Reference [] ra, ReferenceQueue rq) {
    // Verify that all references on rq belong to ra and that if so they are cleared
    for (Reference r = rq.poll(); r != null; r = rq.poll()) {
      int i;
      for (i=0; i<ra.length; i++)
          if (ra[i] == r)
            break;
      if (i == ra.length) return -2.0;
    }
    // Return fraction of ra array whose references are not cleared
    int count = 0;
    for (int i=0; i<ra.length; i++)
      if (ra[i].get() != null)
        count++;
    return count / ((double) ra.length);
  }

  static int microUnitSize = 100;     // conservative upper bound for small object
  static int allocateUnitSize = 5000;

  public static int MBtoUnits (double amt) {  // in Mbytes
      return (int) ((amt * (1 << 20)) / allocateUnitSize);
  }

  public static Object[] allocateUnit () {
    int t = allocateUnitSize / microUnitSize;
    Object[] result = new Object[t];
    for (int i=0; i<t; i++)
      result[i] = new byte[microUnitSize - 4];  // 4 bytes of header
    return result;
  }

  private static Object dummy;  
  public static void allocateDiscard (double amt) { // amt in Mb
    int rounds = MBtoUnits(amt);
    for (int i=0; i<rounds; i++)  
        dummy = allocateUnit();
  }

  public static Object allocateHold (double amt) { // amt in Mb
    int rounds = MBtoUnits(amt);
    Object [] a = new Object[rounds];
    for (int i=0; i<rounds; i++)  
        a[i] = allocateUnit();
    return a;
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

  public static Reference [] allocateReferenceArray (int type, double amt, ReferenceQueue rq) { // amt in Mb
    int rounds = MBtoUnits(amt);
    Reference [] ra = new Reference[rounds];
    for (int i=0; i<rounds; i++)  
      ra[i] = (type == WEAK) ? (Reference) new WeakReference(allocateUnit(), rq) :
              ((type == SOFT) ? (Reference) new SoftReference(allocateUnit(), rq) : null);
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
  private final static int NO_QUALITY = 0;
  private final static int GOOD = 1;
  private final static int POOR = 2;

  private static void check (String msg, boolean correct, int quality) {
      System.out.print(msg + "     ");
      System.out.print(correct ? "PASS" : "FAIL");
      if (correct) {
        if (quality == GOOD) System.out.print("   GOOD");
        if (quality == POOR) System.out.print("   POOR");
      }
      else 
        failCount++;
      System.out.println();
  }

  private static void check (String msg, boolean correct) {
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
      ReferenceQueue wrq = new ReferenceQueue();
      allocateUntilNextGC();
      Reference [] wra = allocateReferenceArray(WEAK, 0.5 * initialHeapSize, wrq);
      double weakAvail = checkReferenceArray(wra, wrq);
      check("Fraction of weak references before GC still live = " + weakAvail, (weakAvail == 1.0));
      allocateDiscard(0.75 * initialHeapSize);
      weakAvail = checkReferenceArray(wra, wrq);
      check("Fraction of weak references after  GC still live = " + weakAvail, (weakAvail == 0.0));

      // ------ Test soft references -----------
      System.out.println("\nChecking soft references and reference queue");
      ReferenceQueue srq = new ReferenceQueue();
      allocateUntilNextGC();
      Reference [] sra = allocateReferenceArray(SOFT, 0.75 * initialHeapSize, srq);
      double softAvail = checkReferenceArray(sra, srq);
      check("Fraction of soft references before GC still live = " + softAvail, (softAvail == 1.0));
      allocateHold(0.5 * initialHeapSize);
      softAvail = checkReferenceArray(sra, srq);
      check("Fraction of soft references after  GC still live = " + softAvail, 
            (softAvail >= 0.00) && (softAvail <= 0.67),
            ((softAvail >= 0.33) && (softAvail <= 0.66)) ? GOOD : POOR);



      // ------ Finish up -----------
      System.out.println();
      double finalHeapSize = getHeapSize();
      System.out.print("\nOverall: ");
      System.out.println((failCount == 0) ? "SUCCESS" : (failCount + " FAILURES"));
  }

}
