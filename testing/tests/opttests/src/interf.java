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

  int getVal();

  void putVal(int val);
}
