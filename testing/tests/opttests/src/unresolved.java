/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
/**
 * unresolved static invocation test.
 *
 * @author unascribed
 */

public class unresolved {
  static int[] num = new int[4];
  static int cnt;

  int f1, f2;

  public static int test(int n) {
    return TestClass.tRet(n);
  }
  static boolean run() {
    int i = test(20);
    System.out.println("Unresolved returned: " + i);
    return true;
  }

}

class TestClass {

 static int tval = 100;

  static int tRet(int i) {
    //    System.out.println(" received = " + i);
    tval += i;
    //    System.out.println(" returned = " + tval);
    return tval;
  }
}
