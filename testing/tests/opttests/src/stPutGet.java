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
public class stPutGet {
  static boolean run() {
    int i = test(6000);
    System.out.println("stPutGet returned: " + i);
    return true;
  }

  public static int test(int n) {
    TestC2.tval += n;
    return TestC2.tval;
  }
}

class TestC2 {
  static int tval = 1000;
}
