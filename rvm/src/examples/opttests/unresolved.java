/*
 * (C) Copyright IBM Corp. 2001
 */
// unresolved static invocation test.

import java.io.*;

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
