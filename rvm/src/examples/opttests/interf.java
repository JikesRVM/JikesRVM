/*
 * (C) Copyright IBM Corp. 2001
 */

import java.io.*;

public class interf {
  static boolean run() {
    int i = test(10000);
    System.out.println("Interf returned: " + i);
    return true;
  }

  static int f1 = 0;

  public static int test(int n) {

    vTest4 vt = new vTest4();

    f1 = intfTest((abc) vt, n);
 
    return f1;
  }

  static int intfTest(abc tst, int n) {

     tst.putVal(n);

     return tst.getVal();

  }

}

class vTest4 implements abc {

  int tval = 1000;

  public int getVal() { return tval; }

  public void putVal(int val) {
    tval += val; 
  }

}

interface abc {

  public int getVal();

  public void putVal(int val);
}
