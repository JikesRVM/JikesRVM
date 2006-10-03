/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author unascribed
 */
class TestVolatiles extends Thread {

  public static void main (String args[]) throws Exception {
    System.out.println("TestVolatiles"); 
    for (int i=0; i<5; i++) {
      TestVolatiles tv = new TestVolatiles(i);
      tv.start();
    }
  }
  
  // static /*volatile*/ long vl = 0;
  static volatile long vl = 0;
  static volatile int  vi =0;
 
  int n;
  long l;
   
  TestVolatiles(int i) {
    n = i;
    l = (((long) n) << 32) + n;
  }

  public void run() {
    int errors = 0;
    for (int i=0; i<1000000; i++) {
      long tl = vl;
      vl = l;
      int n0 = (int) tl;
      int n1 = (int) (tl >> 32);
      if (n0 != n1) errors++;
      int ti = vi;
      vi = n;
    }
    System.out.println(errors + " errors found by thread " + n);
  }
  
}
