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
